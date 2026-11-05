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
 * Google 搜尋結果，透過 SerpApi（https://serpapi.com）取得。
 *
 * 直接用 HTTP 呼叫 SerpApi，它會回傳 Google 結果的 JSON。
 * 到 serpapi.com 註冊拿 API key（有免費額度），設定到環境變數 SERPAPI_KEY 或 .env 檔即可。
 */
@Component
public class SerpApiGoogleProvider implements SearchProvider {

    private static final String URL =
            "https://serpapi.com/search.json?engine=google&q={q}&hl={hl}&gl={gl}&num={num}&api_key={key}";

    /** 沒有結果時 SerpApi 也會放在 error 欄位，這種情況不算錯誤 */
    private static final String NO_RESULTS_MESSAGE = "Google hasn't returned any results for this query.";

    private final RestClient http = RestClient.create();
    private final String apiKey;
    private final int resultCount;

    public SerpApiGoogleProvider(@Value("${serpapi.key:}") String apiKey,
                                 @Value("${search.results-per-provider:10}") int resultCount) {
        this.apiKey = apiKey;
        this.resultCount = resultCount;
    }

    @Override
    public String name() {
        return "google";
    }

    @Override
    public boolean isEnabled() {
        return !apiKey.isBlank();
    }

    @Override
    public List<SearchResult> search(String query) {
        Map<String, Object> body = http.get()
                .uri(URL, query, "zh-tw", "tw", resultCount, apiKey)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        List<SearchResult> results = new ArrayList<>();
        if (body == null) {
            return results;
        }

        Object error = body.get("error");
        if (error != null && !NO_RESULTS_MESSAGE.equals(error)) {
            throw new IllegalStateException(String.valueOf(error));
        }

        if (body.get("organic_results") instanceof List<?> items) {
            int rank = 1;
            for (Object o : items) {
                if (o instanceof Map<?, ?> item && item.get("link") != null) {
                    results.add(SearchResult.of(
                            text(item.get("title")),
                            text(item.get("link")),
                            text(item.get("snippet")),
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
