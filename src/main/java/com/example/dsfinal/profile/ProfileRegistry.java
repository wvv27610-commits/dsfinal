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
        Map<String, Object> root = JsonParserFactory.getJsonParser().parseMap(readFile());
        for (Object item : (List<?>) root.get("profiles")) {
            Profile profile = toProfile((Map<?, ?>) item);
            profiles.put(profile.id(), profile);
        }
        if (profiles.isEmpty()) {
            throw new IllegalStateException(FILE + " 裡至少要有一個身份");
        }
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

    // ===== 讀取 JSON =====

    private static String readFile() throws IOException {
        try (InputStream in = new ClassPathResource(FILE).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Profile toProfile(Map<?, ?> json) {
        return new Profile(
                text(json.get("id")),
                text(json.get("name")),
                text(json.get("description")),
                readKeywords(json.get("keywords")),
                readStopwords(json.get("stopwords")),
                text(json.get("queryPrefix")),
                text(json.get("querySuffix")));
    }

    /** "keywords": { "柴犬": 3, ... } → Map<詞, 權重> */
    private static Map<String, Double> readKeywords(Object json) {
        Map<String, Double> keywords = new LinkedHashMap<>();
        if (json instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                keywords.put(String.valueOf(entry.getKey()), ((Number) entry.getValue()).doubleValue());
            }
        }
        return keywords;
    }

    /** "stopwords": [ "熱狗", ... ] → List<詞> */
    private static List<String> readStopwords(Object json) {
        List<String> stopwords = new ArrayList<>();
        if (json instanceof List<?> list) {
            for (Object word : list) {
                stopwords.add(String.valueOf(word));
            }
        }
        return stopwords;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
