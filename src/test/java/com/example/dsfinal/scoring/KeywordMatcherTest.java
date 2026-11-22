package com.example.dsfinal.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** KeywordMatcher：中文最長匹配、英文整字比對 */
class KeywordMatcherTest {

    @Test
    void chineseLongestMatchStillWorks() {
        Map<String, Integer> counts = KeywordMatcher.count("我家的黃金獵犬很愛吃熱狗，狗狗好可愛",
                Set.of("黃金獵犬", "犬", "狗", "狗狗", "熱狗"));
        assertEquals(1, counts.get("黃金獵犬"));
        assertFalse(counts.containsKey("犬"));      // 「黃金獵犬」不會再拆出「犬」
        assertEquals(1, counts.get("熱狗"));
        assertEquals(1, counts.get("狗狗"));
        assertFalse(counts.containsKey("狗"));
    }

    @Test
    void englishWordsMustBeWholeWords() {
        Map<String, Integer> counts = KeywordMatcher.count(
                "My dog loves hotdogs. Category: vacation. The cat sleeps.",
                Set.of("dog", "cat"));
        assertEquals(1, counts.get("dog"));          // hotdogs 不算
        assertEquals(1, counts.get("cat"));          // category、vacation 不算
    }

    @Test
    void englishPluralsCount() {
        Map<String, Integer> counts = KeywordMatcher.count(
                "Dogs need vaccines. One vaccine a year. Doggo is not a dog.",
                Set.of("dog", "vaccine"));
        assertEquals(2, counts.get("dog"));          // Dogs + dog；Doggo 不算
        assertEquals(2, counts.get("vaccine"));      // vaccines + vaccine
    }

    @Test
    void englishPhrasesAndStopwords() {
        Map<String, Integer> counts = KeywordMatcher.count(
                "A golden retriever ate a hot dog. Hot dogs are not dog food.",
                Set.of("golden retriever", "hot dog", "dog", "dog food"));
        assertEquals(1, counts.get("golden retriever"));
        assertEquals(2, counts.get("hot dog"));      // hot dog + hot dogs，不會被算成 dog
        assertEquals(1, counts.get("dog food"));
        assertFalse(counts.containsKey("dog"));
    }

    @Test
    void mixedChineseAndEnglish() {
        Map<String, Integer> counts = KeywordMatcher.count("這款飼料通過AAFCO標準，柴犬Shiba很愛",
                Set.of("aafco", "shiba", "柴犬"));
        assertEquals(1, counts.get("aafco"));        // 前後是中文，算整字
        assertEquals(1, counts.get("shiba"));
        assertEquals(1, counts.get("柴犬"));
    }
}
