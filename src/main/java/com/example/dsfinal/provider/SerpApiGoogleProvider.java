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
 * 不需要在電腦上開瀏覽器：直接用 HTTP 呼叫 SerpApi，它會回傳 Google 結果的 JSON。
 * 到 serpapi.com 註冊拿 API key（有免費額度），設定到環境變數 SERPAPI_KEY 或 .env 檔即可。
 */
@Component
public class SerpApiGoogleProvider implements SearchProvider {

    private static final String URL =
            "https://serpapi.com/search.json?engine=google&q={q}&hl={hl}&gl={gl}&num={num}&api_key={key}";

    private final RestClient http = RestClient.create();
    private final String apiKey;
    private final int num;

    public SerpApiGoogleProvider(@Value("${serpapi.key:}") String apiKey,
                                 @Value("${search.results-per-provider:10}") int num) {
        this.apiKey = apiKey;
        this.num = num;
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
                .uri(URL, query, "zh-tw", "tw", num, apiKey)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        List<SearchResult> results = new ArrayList<>();
        if (body == null) {
            return results;
        }
        if (body.get("error") != null && !"Google hasn't returned any results for this query.".equals(body.get("error"))) {
            throw new IllegalStateException(String.valueOf(body.get("error")));
        }
        if (body.get("organic_results") instanceof List<?> items) {
            int rank = 1;
            for (Object o : items) {
                if (o instanceof Map<?, ?> item && item.get("link") != null) {
                    results.add(SearchResult.of(str(item.get("title")), str(item.get("link")),
                            str(item.get("snippet")), name(), rank++));
                }
            }
        }
        return results;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
