package com.example.dsfinal.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.dsfinal.crawler.HtmlKeywordExtractor;
import com.example.dsfinal.model.SearchResponse;
import com.example.dsfinal.model.SearchResult;
import com.example.dsfinal.profile.Profile;
import com.example.dsfinal.profile.ProfileRegistry;
import com.example.dsfinal.provider.SearchProvider;
import com.example.dsfinal.scoring.ResultScorer;
import com.example.dsfinal.scoring.ScoringContext;
import com.example.dsfinal.scoring.SearchQuery;

/**
 * 搜尋流程：
 *   1. 組出實際要搜尋的字串（加上強制前後綴）
 *   2. 同時向所有已啟用的 SearchProvider 搜尋
 *   3. 同一個網址出現在多個來源就合併成一筆
 *   4. 依使用者選的身份計分（Exact 不符合的排除）
 *   5. 排序：分數高的先 → 標題有查詢詞的先 → 原始名次好的先
 */
@Service
public class SearchService {

    private final List<SearchProvider> providers;
    private final ResultScorer scorer;
    private final ProfileRegistry profiles;
    private final HtmlKeywordExtractor pageFetcher;
    private final String globalPrefix;
    private final String globalSuffix;

    public SearchService(List<SearchProvider> providers,
                         Map<String, ResultScorer> scorers,
                         ProfileRegistry profiles,
                         HtmlKeywordExtractor pageFetcher,
                         @Value("${search.scorer:my}") String scorerName,
                         @Value("${search.query-prefix:}") String globalPrefix,
                         @Value("${search.query-suffix:}") String globalSuffix) {
        this.providers = providers;
        this.profiles = profiles;
        this.pageFetcher = pageFetcher;
        this.globalPrefix = globalPrefix.trim();
        this.globalSuffix = globalSuffix.trim();

        this.scorer = scorers.get(scorerName);
        if (this.scorer == null) {
            throw new IllegalStateException("search.scorer=" + scorerName + " 找不到，可用的有：" + scorers.keySet());
        }
    }

    public SearchResponse search(String query, String profileId) {
        if (query == null || query.isBlank()) {
            return new SearchResponse(List.of(), List.of(), "");
        }
        String userQuery = query.trim();
        Profile profile = profiles.get(profileId);
        SearchQuery parsedQuery = SearchQuery.parse(userQuery);
        ScoringContext ctx = new ScoringContext(parsedQuery, profile);

        // 1. 實際送出的查詢會加上前後綴；計分只看使用者輸入的部分
        String searchedQuery = buildSearchedQuery(userQuery, profile);

        // 2 + 3. 所有來源同時搜尋，並合併重複網址
        List<String> warnings = new ArrayList<>();
        List<SearchResult> merged = searchAllProviders(searchedQuery, warnings);

        // 4 + 5. 計分、排序
        List<SearchResult> results = scoreAndFilter(merged, ctx);
        results.sort(rankingOrder(parsedQuery));

        return new SearchResponse(results, warnings, searchedQuery);
    }

    // ===== 1. 組查詢字串 =====

    /** 全域前綴 + 身份前綴 + 使用者輸入 + 身份後綴 + 全域後綴（空的部分略過） */
    private String buildSearchedQuery(String userQuery, Profile profile) {
        List<String> parts = new ArrayList<>();
        for (String part : List.of(globalPrefix, profile.queryPrefix(), userQuery, profile.querySuffix(), globalSuffix)) {
            if (!part.isBlank()) {
                parts.add(part.trim());
            }
        }
        return String.join(" ", parts);
    }

    // ===== 2 + 3. 搜尋並合併 =====

    /**
     * 所有啟用的來源同時搜尋。
     * 單一來源失敗只會加一則警告；全部失敗才丟出例外。
     */
    private List<SearchResult> searchAllProviders(String searchedQuery, List<String> warnings) {
        List<SearchProvider> enabled = providers.stream().filter(SearchProvider::isEnabled).toList();
        if (enabled.isEmpty()) {
            throw new IllegalStateException("沒有可用的搜尋來源：請設定 SERPAPI_KEY、BRAVE_API_KEY 或 TAVILY_API_KEY（見 .env.example）");
        }

        List<CompletableFuture<List<SearchResult>>> tasks = new ArrayList<>();
        for (SearchProvider provider : enabled) {
            tasks.add(CompletableFuture.supplyAsync(() -> searchOne(provider, searchedQuery)));
        }

        Map<String, SearchResult> byUrl = new LinkedHashMap<>();
        for (int i = 0; i < tasks.size(); i++) {
            try {
                for (SearchResult r : tasks.get(i).join()) {
                    byUrl.merge(normalizeUrl(r.url()), r, SearchService::combine);
                }
            } catch (Exception e) {
                warnings.add(errorMessage(e, enabled.get(i)));
            }
        }

        if (byUrl.isEmpty() && warnings.size() == enabled.size()) {
            throw new IllegalStateException(String.join("；", warnings));
        }
        return new ArrayList<>(byUrl.values());
    }

    private static List<SearchResult> searchOne(SearchProvider provider, String query) {
        try {
            return provider.search(query);
        } catch (Exception e) {
            throw new RuntimeException(provider.name() + "：" + e.getMessage(), e);
        }
    }

    private static String errorMessage(Exception e, SearchProvider provider) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        return cause.getMessage() != null ? cause.getMessage() : provider.name() + " 搜尋失敗";
    }

    /** 兩個來源有同一個網址：保留先拿到的標題，摘要取比較長的，來源合併，名次取比較好的 */
    private static SearchResult combine(SearchResult a, SearchResult b) {
        List<String> sources = new ArrayList<>(a.sources());
        for (String source : b.sources()) {
            if (!sources.contains(source)) {
                sources.add(source);
            }
        }
        String longerSnippet = b.snippet().length() > a.snippet().length() ? b.snippet() : a.snippet();
        int bestRank = Math.min(a.rank(), b.rank());
        return new SearchResult(a.title(), a.url(), longerSnippet, sources, bestRank, 0);
    }

    /** 比對用：忽略 http/https、www、結尾的 / */
    private static String normalizeUrl(String url) {
        return url.replaceFirst("^https?://(www\\.)?", "").replaceFirst("/$", "").toLowerCase();
    }

    // ===== 4 + 5. 計分與排序 =====

    private List<SearchResult> scoreAndFilter(List<SearchResult> results, ScoringContext ctx) {
        // 計分要用到網頁內容：先「同時」把所有網頁下載好，下面的迴圈就不用一個一個等
        results.parallelStream().forEach(r -> pageFetcher.fetch(r.url()));

        List<SearchResult> scored = new ArrayList<>();
        for (SearchResult r : results) {
            if (!scorer.accept(ctx, r)) {
                continue;   // 例如 Exact 詞組沒出現 → 排除
            }
            double score = scorer.score(ctx, r);
            scored.add(r.withScore(score));
        }
        return scored;
    }

    private static Comparator<SearchResult> rankingOrder(SearchQuery query) {
        Comparator<SearchResult> byScore = Comparator.comparingDouble(SearchResult::score).reversed();
        Comparator<SearchResult> byTitleHit = Comparator.comparing((SearchResult r) -> query.anyIn(r.title())).reversed();
        Comparator<SearchResult> byOriginalRank = Comparator.comparingInt(SearchResult::rank);
        return byScore.thenComparing(byTitleHit).thenComparing(byOriginalRank);
    }
}
