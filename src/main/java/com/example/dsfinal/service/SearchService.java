package com.example.dsfinal.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
 *   1. 同時向所有已啟用的 SearchProvider 搜尋（provider 套件）
 *   2. 同一個網址出現在多個來源就合併成一筆
 *   3. 依使用者選的身份（profile），用設定的 ResultScorer 算分數（Goo狗 公式寫在 MyScorer）
 *   4. 依分數由高到低排序；同分時先比查詢詞是否出現在標題，再比原始名次
 */
@Service
public class SearchService {

    private final List<SearchProvider> providers;
    private final ResultScorer scorer;
    private final ProfileRegistry profiles;
    private final String globalPrefix;
    private final String globalSuffix;

    public SearchService(List<SearchProvider> providers,
                         Map<String, ResultScorer> scorers,
                         ProfileRegistry profiles,
                         @Value("${search.scorer:my}") String scorerName,
                         @Value("${search.query-prefix:}") String globalPrefix,
                         @Value("${search.query-suffix:}") String globalSuffix) {
        this.globalPrefix = globalPrefix.trim();
        this.globalSuffix = globalSuffix.trim();
        this.providers = providers;
        this.profiles = profiles;
        this.scorer = scorers.get(scorerName);
        if (this.scorer == null) {
            throw new IllegalStateException("search.scorer=" + scorerName + " 找不到，可用的有：" + scorers.keySet());
        }
    }

    public SearchResponse search(String query, String profileId) {
        if (query == null || query.isBlank()) {
            return new SearchResponse(List.of(), List.of(), "");
        }
        String q = query.trim();
        SearchQuery parsed = SearchQuery.parse(q);
        Profile profile = profiles.get(profileId);
        ScoringContext ctx = new ScoringContext(parsed, profile);
        // 實際送給搜尋來源的查詢：全域前綴 + 身份前綴 + 使用者輸入 + 身份後綴 + 全域後綴
        //（計分仍然只看使用者輸入的 q，強制附加的字不會讓每筆結果都多拿 Q 分）
        String sent = buildSentQuery(q, profile);

        List<SearchProvider> enabled = providers.stream().filter(SearchProvider::isEnabled).toList();
        if (enabled.isEmpty()) {
            throw new IllegalStateException("沒有可用的搜尋來源：請設定 SERPAPI_KEY 或 BRAVE_API_KEY（見 .env.example）");
        }
        
        // 1. 所有來源同時搜尋
        List<CompletableFuture<List<SearchResult>>> futures = enabled.stream()
                .map(p -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return p.search(sent);
                    } catch (Exception e) {
                        throw new RuntimeException(p.name() + "：" + e.getMessage(), e);
                    }
                }))
                .toList();

        // 2. 收集結果，同一個網址合併
        Map<String, SearchResult> merged = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            try {
                for (SearchResult r : futures.get(i).join()) {
                    merged.merge(normalizeUrl(r.url()), r, SearchService::combine);
                }
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                warnings.add(cause.getMessage() != null ? cause.getMessage() : enabled.get(i).name() + " 搜尋失敗");
            }
        }
        if (merged.isEmpty() && warnings.size() == enabled.size()) {
            throw new IllegalStateException(String.join("；", warnings));
        }

        // 3. 算分數（Exact 不符合的排除）  4. 排序
        List<SearchResult> results = new ArrayList<>();
        // 計分時可能要下載網頁，所以用 parallelStream 同時處理多筆，比較快
        merged.values().parallelStream()
                .filter(r -> scorer.accept(ctx, r))
                .map(r -> r.withScore(scorer.score(ctx, r)))
                .forEachOrdered(results::add);
        results.sort((a, b) -> {
            int c = Double.compare(b.score(), a.score());                                    // 分數高的先
            if (c != 0) return c;
            c = Boolean.compare(parsed.anyIn(b.title()), parsed.anyIn(a.title()));          // 標題有查詢詞的先
            if (c != 0) return c;
            return Integer.compare(a.rank(), b.rank());                                     // 原始名次好的先
        });

        return new SearchResponse(results, warnings, sent);
    }

    private String buildSentQuery(String q, Profile profile) {
        StringBuilder sb = new StringBuilder();
        for (String part : List.of(globalPrefix, profile.queryPrefix(), q, profile.querySuffix(), globalSuffix)) {
            if (!part.isBlank()) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(part.trim());
            }
        }
        return sb.toString();
    }

    /** 兩個來源有同一個網址：保留先拿到的標題，摘要取比較長的，來源合併，名次取比較好的 */
    private static SearchResult combine(SearchResult a, SearchResult b) {
        List<String> sources = new ArrayList<>(a.sources());
        for (String s : b.sources()) {
            if (!sources.contains(s)) {
                sources.add(s);
            }
        }
        String snippet = b.snippet().length() > a.snippet().length() ? b.snippet() : a.snippet();
        return new SearchResult(a.title(), a.url(), snippet, sources, Math.min(a.rank(), b.rank()), 0);
    }

    /** 比對用：忽略 http/https、www、結尾的 / */
    private static String normalizeUrl(String url) {
        return url.replaceFirst("^https?://(www\\.)?", "").replaceFirst("/$", "").toLowerCase();
    }
}
