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

import com.example.dsfinal.language.Language;
import com.example.dsfinal.language.LanguageRegistry;

/**
 * 讀取 profiles.json（預設語言）和 profiles.<語言>.json（翻譯），提供所有身份。
 * 改完 json 存檔，devtools 會自動重新啟動，前端就會出現新的身份按鈕。
 *
 * 翻譯檔的格式：以身份 id 為 key，只寫要翻譯的欄位即可
 *   { "pet-owner": { "name": "Dog owner", "querySuffix": "dog", "keywords": { "corgi": 3 } } }
 * 翻譯檔不存在、或沒有某個身份的翻譯，那個身份就顯示預設語言的內容。
 *
 * 分組：profiles.json 的 "groups": [ {"id": "daily", "name": "日常照顧"}, ... ]，
 * 每個身份用 "group": "daily" 指定組別；翻譯檔用 "groups": { "daily": {"name": "Daily care"} } 翻譯組名。
 */
@Component
public class ProfileRegistry {

    private static final String FILE = "profiles.json";

    /** 預設語言的身份（profiles.json 的內容） */
    private final Map<String, Profile> profiles = new LinkedHashMap<>();
    /** 分組（預設語言，照 profiles.json 的順序） */
    private final List<ProfileGroup> groups = new ArrayList<>();
    /** 語言代碼 → 身份 id → 翻譯區塊 */
    private final Map<String, Map<?, ?>> translations = new LinkedHashMap<>();
    private final LanguageRegistry languages;

    public ProfileRegistry(LanguageRegistry languages) throws IOException {
        this.languages = languages;
        String defaultCode = languages.defaultLanguage().code();

        Map<String, Object> root = JsonParserFactory.getJsonParser().parseMap(readFile(FILE));
        if (root.get("groups") instanceof List<?> list) {
            for (Object item : list) {
                Map<?, ?> json = (Map<?, ?>) item;
                groups.add(new ProfileGroup(ProfileLocalizer.text(json.get("id")), ProfileLocalizer.text(json.get("name"))));
            }
        }
        for (Object item : (List<?>) root.get("profiles")) {
            Profile profile = toProfile((Map<?, ?>) item, defaultCode);
            profiles.put(profile.id(), profile);
        }
        if (profiles.isEmpty()) {
            throw new IllegalStateException(FILE + " 裡至少要有一個身份");
        }

        for (Language language : languages.all()) {
            if (language.code().equals(defaultCode)) {
                continue;
            }
            String file = "profiles." + language.code() + ".json";
            if (new ClassPathResource(file).exists()) {
                translations.put(language.code(), JsonParserFactory.getJsonParser().parseMap(readFile(file)));
            }
        }
    }

    /** 所有身份（預設語言，照 profiles.json 的順序） */
    public List<Profile> all() {
        return List.copyOf(profiles.values());
    }

    /** 所有身份，換成指定語言 */
    public List<Profile> all(String languageCode) {
        List<Profile> list = new ArrayList<>();
        for (Profile profile : profiles.values()) {
            list.add(localize(profile, languageCode));
        }
        return list;
    }

    /** 所有分組，組名換成指定語言（沒翻譯的沿用預設語言） */
    public List<ProfileGroup> groups(String languageCode) {
        Language language = languages.get(languageCode);
        Map<?, ?> byId = translations.get(language.code());
        Object translatedGroups = byId == null ? null : byId.get("groups");
        List<ProfileGroup> list = new ArrayList<>();
        for (ProfileGroup group : groups) {
            String name = group.name();
            if (translatedGroups instanceof Map<?, ?> map && map.get(group.id()) instanceof Map<?, ?> block) {
                String translated = ProfileLocalizer.text(block.get("name"));
                if (!translated.isEmpty()) {
                    name = translated;
                }
            }
            list.add(new ProfileGroup(group.id(), name));
        }
        return list;
    }

    /** 用 id 找身份；找不到或沒給就用第一個（預設語言） */
    public Profile get(String id) {
        return get(id, null);
    }

    /** 用 id 找身份並換成指定語言；id 找不到就用第一個 */
    public Profile get(String id, String languageCode) {
        Profile base = (id != null && profiles.containsKey(id))
                ? profiles.get(id)
                : profiles.values().iterator().next();
        return localize(base, languageCode);
    }

    private Profile localize(Profile base, String languageCode) {
        Language language = languages.get(languageCode);
        if (language.code().equals(languages.defaultLanguage().code())) {
            return base;
        }
        Map<?, ?> byId = translations.get(language.code());
        Object block = byId == null ? null : byId.get(base.id());
        return ProfileLocalizer.localize(base, block instanceof Map<?, ?> map ? map : null, language.code());
    }

    // ===== 讀取 JSON =====

    private static String readFile(String file) throws IOException {
        try (InputStream in = new ClassPathResource(file).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Profile toProfile(Map<?, ?> json, String defaultCode) {
        return new Profile(
                ProfileLocalizer.text(json.get("id")),
                ProfileLocalizer.text(json.get("name")),
                ProfileLocalizer.text(json.get("description")),
                ProfileLocalizer.readKeywords(json.get("keywords")),
                ProfileLocalizer.readStopwords(json.get("stopwords")),
                ProfileLocalizer.text(json.get("queryPrefix")),
                ProfileLocalizer.text(json.get("querySuffix")),
                ProfileLocalizer.text(json.get("group")),
                defaultCode,
                true);
    }
}
