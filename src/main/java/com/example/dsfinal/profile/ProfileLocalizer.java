package com.example.dsfinal.profile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把「預設語言的身份」和「某語言的翻譯」合併成那個語言要用的身份。
 *
 * 合併規則：
 *   - name、description：有翻譯就用翻譯，沒寫就沿用預設語言
 *   - queryPrefix、querySuffix：有翻譯區塊就用翻譯的（沒寫就是空字串），
 *     避免英文查詢後面被加上中文的「狗」
 *   - keywords：預設語言的關鍵字 + 翻譯的關鍵字（同一個詞以翻譯的權重為準）。
 *     兩種語言的詞都保留，因為網頁常常中英混寫
 *   - stopwords：兩邊合併
 *   - group：只看預設語言（分組的翻譯寫在翻譯檔的 groups 區塊）
 */
public final class ProfileLocalizer {

    private ProfileLocalizer() {
    }

    /**
     * @param base        profiles.json 裡的身份
     * @param translation profiles.<語言>.json 裡同一個 id 的區塊；null 代表沒有翻譯
     * @param language    目標語言代碼
     */
    public static Profile localize(Profile base, Map<?, ?> translation, String language) {
        if (translation == null) {
            return new Profile(base.id(), base.name(), base.description(),
                    base.keywords(), base.stopwords(),
                    base.queryPrefix(), base.querySuffix(), base.group(),
                    language, false);
        }

        Map<String, Double> keywords = new LinkedHashMap<>(base.keywords());
        keywords.putAll(readKeywords(translation.get("keywords")));

        List<String> stopwords = new ArrayList<>(base.stopwords());
        for (String word : readStopwords(translation.get("stopwords"))) {
            if (!stopwords.contains(word)) {
                stopwords.add(word);
            }
        }

        return new Profile(
                base.id(),
                orDefault(translation.get("name"), base.name()),
                orDefault(translation.get("description"), base.description()),
                keywords,
                stopwords,
                text(translation.get("queryPrefix")),
                text(translation.get("querySuffix")),
                base.group(),
                language,
                true);
    }

    // ===== 讀取 JSON 的小工具（ProfileRegistry 也會用） =====

    /** "keywords": { "柴犬": 3, ... } → Map<詞, 權重> */
    static Map<String, Double> readKeywords(Object json) {
        Map<String, Double> keywords = new LinkedHashMap<>();
        if (json instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getValue() instanceof Number weight) {
                    keywords.put(String.valueOf(entry.getKey()), weight.doubleValue());
                }
            }
        }
        return keywords;
    }

    /** "stopwords": [ "熱狗", ... ] → List<詞> */
    static List<String> readStopwords(Object json) {
        List<String> stopwords = new ArrayList<>();
        if (json instanceof List<?> list) {
            for (Object word : list) {
                stopwords.add(String.valueOf(word));
            }
        }
        return stopwords;
    }

    static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String orDefault(Object value, String fallback) {
        String s = text(value);
        return s.isEmpty() ? fallback : s;
    }
}
