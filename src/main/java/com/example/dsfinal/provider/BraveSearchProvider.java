package com.example.dsfinal.provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.example.dsfinal.model.SearchResult;

/**
 * 搜尋來源：Brave Search API（https://brave.com/search/api/）。
 *
 * 設定環境變數 BRAVE_API_KEY 就會啟用；沒設定就自動略過。
 */
@Component
public class BraveSearchProvider implements SearchProvider {

    private static final String URL =
            "https://api.search.brave.com/res/v1/web/search?q={q}&count={count}&country=tw&search_lang=zh-hant";

    private static final int MAX_COUNT = 20;   // Brave 一次最多 20 筆

    private final RestClient http = RestClient.create();
    private final String apiKey;
    private final int resultCount;

    public BraveSearchProvider(@Value("${brave.key:}") String apiKey,
                               @Value("${search.results-per-provider:10}") int resultCount) {
        this.apiKey = apiKey;
        this.resultCount = Math.min(resultCount, MAX_COUNT);
    }

    @Override
    public String name() {
        return "brave";
    }

    @Override
    public boolean isEnabled() {
        return !apiKey.isBlank();
    }

    @Override
    public List<SearchResult> search(String query) {
        Map<String, Object> body = http.get()
                .uri(URL, query, resultCount)
                .header("Accept", "application/json")
                .header("X-Subscription-Token", apiKey)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        List<SearchResult> results = new ArrayList<>();
        if (body != null && body.get("web") instanceof Map<?, ?> web && web.get("results") instanceof List<?> items) {
            int rank = 1;
            for (Object o : items) {
                if (o instanceof Map<?, ?> item && item.get("url") != null) {
                    results.add(SearchResult.of(
                            stripTags(item.get("title")),
                            String.valueOf(item.get("url")),
                            stripTags(item.get("description")),
                            name(),
                            rank++));
                }
            }
        }
        return results;
    }

    /** Brave 的標題和摘要裡會有 <strong> 之類的標籤，拿掉只留文字 */
    private static String stripTags(Object value) {
        return value == null ? "" : String.valueOf(value).replaceAll("<[^>]+>", "");
    }
}
