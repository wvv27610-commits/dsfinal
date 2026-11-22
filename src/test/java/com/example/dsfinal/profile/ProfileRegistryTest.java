package com.example.dsfinal.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.dsfinal.language.LanguageRegistry;

/** 讀取真正的 profiles.json、profiles.en.json、languages.json，確認格式正確 */
class ProfileRegistryTest {

    @Test
    void everyProfileHasAnEnglishTranslation() throws Exception {
        LanguageRegistry languages = new LanguageRegistry();
        ProfileRegistry registry = new ProfileRegistry(languages);

        List<Profile> english = registry.all("en");
        assertEquals(registry.all().size(), english.size());
        for (Profile p : english) {
            assertTrue(p.translated(), p.id() + " 還沒有英文翻譯（profiles.en.json）");
            assertTrue(!p.querySuffix().matches(".*\\p{IsHan}.*"), p.id() + " 的英文 querySuffix 不該有中文");
        }
    }

    @Test
    void languageCodesAreMatchedLoosely() throws Exception {
        LanguageRegistry languages = new LanguageRegistry();
        assertEquals("en", languages.get("en-US").code());
        assertEquals("zh-TW", languages.get("zh-tw").code());
        assertEquals("zh-TW", languages.get(null).code());
        assertEquals("zh-TW", languages.get("xx").code());
    }

    @Test
    void unknownProfileFallsBackToFirst() throws Exception {
        ProfileRegistry registry = new ProfileRegistry(new LanguageRegistry());
        assertEquals(registry.all().get(0).id(), registry.get("no-such-profile", "en").id());
        assertEquals("Dog owner", registry.get("pet-owner", "en").name());
        assertEquals("寵物主人", registry.get("pet-owner", "zh-TW").name());
    }
}
