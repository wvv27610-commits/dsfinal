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
import com.example.dsfinal.provider.SearchProvider;
import com.example.dsfinal.scoring.ResultScorer;

/**
 * 搜尋流程：
 *   1. 同時向所有已啟用的 SearchProvider 搜尋（provider 套件）
 *   2. 同一個網址出現在多個來源就合併成一筆
 *   3. 用設定的 ResultScorer 算分數（scoring 套件，自己的算法寫在 MyScorer）
 *   4. 依分數由高到低排序
 */
@Service
public class SearchService {

    private final List<SearchProvider> providers;
    private final ResultScorer scorer;

    public SearchService(List<SearchProvider> providers,
                         Map<String, ResultScorer> scorers,
                         @Value("${search.scorer:my}") String scorerName) {
        this.providers = providers;
        this.scorer = scorers.get(scorerName);
        if (this.scorer == null) {
            throw new IllegalStateException("search.scorer=" + scorerName + " 找不到，可用的有：" + scorers.keySet());
        }
    }

    public SearchResponse search(String query) {
        if (query == null || query.isBlank()) {
            return new SearchResponse(List.of(), List.of());
        }
        String q = query.trim();

        List<SearchProvider> enabled = providers.stream().filter(SearchProvider::isEnabled).toList();
        if (enabled.isEmpty()) {
            throw new IllegalStateException("沒有可用的搜尋來源：請設定 SERPAPI_KEY 或 BRAVE_API_KEY（見 .env.example）");
        }

        // 1. 所有來源同時搜尋
        List<CompletableFuture<List<SearchResult>>> futures = enabled.stream()
                .map(p -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return p.search(q);
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

        // 3. 算分數  4. 排序
        List<SearchResult> results = new ArrayList<>();
        // 計分時可能要下載網頁，所以用 parallelStream 同時處理多筆，比較快
        merged.values().parallelStream()
                .map(r -> r.withScore(scorer.score(q, r)))
                .forEachOrdered(results::add);
        results.sort((a, b) -> Double.compare(b.score(), a.score()));

        return new SearchResponse(results, warnings);
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
