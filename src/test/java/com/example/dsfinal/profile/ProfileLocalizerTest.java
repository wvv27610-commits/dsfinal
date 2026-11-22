package com.example.dsfinal.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** 身份翻譯的合併規則 */
class ProfileLocalizerTest {

    private final Profile base = new Profile("pet-owner", "寵物主人", "照顧狗狗的日常",
            Map.of("柴犬", 3.0, "狗", 2.0), List.of("熱狗"), "", "狗", "daily", "zh-TW", true);

    @Test
    void translationReplacesTextsAndMergesKeywords() {
        Map<String, Object> en = Map.of(
                "name", "Dog owner",
                "querySuffix", "dog",
                "keywords", Map.of("corgi", 3, "狗", 1),
                "stopwords", List.of("hot dog"));

        Profile p = ProfileLocalizer.localize(base, en, "en");

        assertEquals("Dog owner", p.name());
        assertEquals("照顧狗狗的日常", p.description());   // 沒翻譯的欄位沿用中文
        assertEquals("dog", p.querySuffix());               // 不會再加上中文的「狗」
        assertEquals(3.0, p.keywords().get("柴犬"));         // 中文關鍵字保留
        assertEquals(3.0, p.keywords().get("corgi"));        // 加上英文關鍵字
        assertEquals(1.0, p.keywords().get("狗"));           // 同一個詞以翻譯為準
        assertEquals(List.of("熱狗", "hot dog"), p.stopwords());
        assertEquals("daily", p.group());                     // 分組不跟著語言變
        assertEquals("en", p.language());
        assertTrue(p.translated());
    }

    @Test
    void missingTranslationKeepsBaseAndIsMarked() {
        Profile p = ProfileLocalizer.localize(base, null, "en");
        assertEquals("寵物主人", p.name());
        assertEquals("狗", p.querySuffix());
        assertFalse(p.translated());
    }
}
