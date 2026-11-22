package com.example.dsfinal.language;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.boot.json.JsonParserFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 讀取 languages.json，提供所有支援的語言。
 *
 * 要新增一種語言（例如日文 ja）：
 *   1. 在 languages.json 的 languages 陣列加一筆
 *   2. 新增 src/main/resources/profiles.ja.json（各身份的日文名稱和關鍵字，可以只翻譯一部分）
 *   3. 新增 src/main/resources/static/i18n/ja.json（網頁上的文字）
 */
@Component
public class LanguageRegistry {

    private static final String FILE = "languages.json";

    private final Map<String, Language> languages = new LinkedHashMap<>();
    private final String defaultCode;

    public LanguageRegistry() throws IOException {
        Map<String, Object> root = JsonParserFactory.getJsonParser().parseMap(readFile());
        for (Object item : (List<?>) root.get("languages")) {
            Map<?, ?> json = (Map<?, ?>) item;
            Language language = new Language(
                    text(json.get("code")),
                    text(json.get("label")),
                    text(json.get("googleHl")),
                    text(json.get("googleGl")),
                    text(json.get("braveLang")),
                    text(json.get("braveCountry")));
            languages.put(language.code(), language);
        }
        if (languages.isEmpty()) {
            throw new IllegalStateException(FILE + " 裡至少要有一種語言");
        }
        String configuredDefault = text(root.get("default"));
        this.defaultCode = languages.containsKey(configuredDefault)
                ? configuredDefault
                : languages.keySet().iterator().next();
    }

    /** 所有語言（照 languages.json 的順序） */
    public List<Language> all() {
        return List.copyOf(languages.values());
    }

    /** 預設語言（profiles.json 本身用的語言） */
    public Language defaultLanguage() {
        return languages.get(defaultCode);
    }

    /**
     * 用代碼找語言，不分大小寫；只給主要語言也可以（例如 en-US → en）。
     * 找不到或沒給就用預設語言。
     */
    public Language get(String code) {
        if (code == null || code.isBlank()) {
            return defaultLanguage();
        }
        String wanted = code.trim().toLowerCase(Locale.ROOT);
        for (Language language : languages.values()) {
            if (language.code().toLowerCase(Locale.ROOT).equals(wanted)) {
                return language;
            }
        }
        String primary = wanted.split("[-_]")[0];
        for (Language language : languages.values()) {
            if (language.code().toLowerCase(Locale.ROOT).split("[-_]")[0].equals(primary)) {
                return language;
            }
        }
        return defaultLanguage();
    }

    private static String readFile() throws IOException {
        try (InputStream in = new ClassPathResource(FILE).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
