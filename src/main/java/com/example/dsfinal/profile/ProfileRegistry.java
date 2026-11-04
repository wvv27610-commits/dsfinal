package com.example.dsfinal.profile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.json.JsonParserFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 讀取 profiles.json，提供所有身份。
 * 改完 profiles.json 存檔，devtools 會自動重新啟動，前端就會出現新的身份按鈕。
 */
@Component
public class ProfileRegistry {

    private static final String FILE = "profiles.json";

    private final Map<String, Profile> profiles = new LinkedHashMap<>();

    public ProfileRegistry() throws IOException {
        String json;
        try (InputStream in = new ClassPathResource(FILE).getInputStream()) {
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Map<String, Object> root = JsonParserFactory.getJsonParser().parseMap(json);
        for (Object o : (List<?>) root.get("profiles")) {
            Map<?, ?> m = (Map<?, ?>) o;
            String id = String.valueOf(m.get("id"));

            Map<String, Double> keywords = new LinkedHashMap<>();
            Object kw = m.get("keywords");
            if (kw instanceof Map<?, ?> km) {
                for (Map.Entry<?, ?> e : km.entrySet()) {
                    keywords.put(String.valueOf(e.getKey()), ((Number) e.getValue()).doubleValue());
                }
            }
            List<String> stopwords = new ArrayList<>();
            Object sw = m.get("stopwords");
            if (sw instanceof List<?> sl) {
                for (Object s : sl) {
                    stopwords.add(String.valueOf(s));
                }
            }
            profiles.put(id, new Profile(id, String.valueOf(m.get("name")),
                    m.get("description") == null ? "" : String.valueOf(m.get("description")),
                    keywords, stopwords, text(m.get("queryPrefix")), text(m.get("querySuffix"))));
        }
        if (profiles.isEmpty()) {
            throw new IllegalStateException(FILE + " 裡至少要有一個身份");
        }
    }

    private static String text(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    /** 所有身份（照 profiles.json 的順序） */
    public List<Profile> all() {
        return List.copyOf(profiles.values());
    }

    /** 用 id 找身份；找不到或沒給就用第一個 */
    public Profile get(String id) {
        if (id != null && profiles.containsKey(id)) {
            return profiles.get(id);
        }
        return profiles.values().iterator().next();
    }
}
