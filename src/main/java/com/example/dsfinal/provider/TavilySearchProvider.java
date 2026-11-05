package com.example.dsfinal.provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.example.dsfinal.model.SearchResult;

/**
 * 搜尋來源：Tavily Search API（https://tavily.com）。
 *
 * 設定環境變數 TAVILY_API_KEY 就會啟用；沒設定就自動略過。
 * 呼叫方式：POST https://api.tavily.com/search，Header 帶 Authorization: Bearer <key>，
 * 回傳的 results 陣列裡每筆有 title、url、content（摘要）。
 */
@Component
public class TavilySearchProvider implements SearchProvider {

    private static final String URL = "https://api.tavily.com/search";

    private static final int MAX_COUNT = 20;   // Tavily 一次最多 20 筆

    private final RestClient http = RestClient.create();
    private final String apiKey;
    private final int resultCount;

    public TavilySearchProvider(@Value("${tavily.key:}") String apiKey,
                                @Value("${search.results-per-provider:10}") int resultCount) {
        this.apiKey = apiKey;
        this.resultCount = Math.min(resultCount, MAX_COUNT);
    }

    @Override
    public String name() {
        return "tavily";
    }

    @Override
    public boolean isEnabled() {
        return !apiKey.isBlank();
    }

    @Override
    public List<SearchResult> search(String query) {
        Map<String, Object> request = Map.of(
                "query", query,
                "max_results", resultCount,
                "search_depth", "basic",     // basic 每次搜尋花 1 點額度，advanced 花 2 點
                "include_answer", false);

        Map<String, Object> body = http.post()
                .uri(URL)
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        List<SearchResult> results = new ArrayList<>();
        if (body != null && body.get("results") instanceof List<?> items) {
            int rank = 1;
            for (Object o : items) {
                if (o instanceof Map<?, ?> item && item.get("url") != null) {
                    results.add(SearchResult.of(
                            text(item.get("title")),
                            String.valueOf(item.get("url")),
                            text(item.get("content")),
                            name(),
                            rank++));
                }
            }
        }
        return results;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
