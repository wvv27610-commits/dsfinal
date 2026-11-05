package com.example.dsfinal.scoring;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 用「最長匹配」統計字典裡每個詞出現的次數。
 *
 * 從左到右掃描，每個位置先試最長的詞：
 *   「黃金獵犬」算一次「黃金獵犬」，不會再拆出「犬」；
 *   「熱狗」若在字典裡（排除詞），就不會被算成「狗」。
 */
public final class KeywordMatcher {

    /** 一個「字」：一個中文字，或一串英文／數字 */
    private static final Pattern WORD = Pattern.compile("[\\p{IsHan}]|[A-Za-z0-9]+");

    private KeywordMatcher() {
    }

    /** 回傳 字典詞 → 出現次數（只列出現過的詞） */
    public static Map<String, Integer> count(String text, Set<String> dictionary) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (text == null || text.isEmpty()) {
            return counts;
        }

        // 小寫版本 → 原本的寫法（比對不分大小寫，回傳時用原本的詞）
        Map<String, String> lowerToOriginal = new HashMap<>();
        int longest = 0;
        for (String word : dictionary) {
            if (!word.isEmpty()) {
                lowerToOriginal.put(word.toLowerCase(Locale.ROOT), word);
                longest = Math.max(longest, word.length());
            }
        }
        if (longest == 0) {
            return counts;
        }

        String lowerText = text.toLowerCase(Locale.ROOT);
        int i = 0;
        while (i < lowerText.length()) {
            String match = longestMatchAt(lowerText, i, longest, lowerToOriginal);
            if (match == null) {
                i++;
            } else {
                counts.merge(lowerToOriginal.get(match), 1, Integer::sum);
                i += match.length();
            }
        }
        return counts;
    }

    /** 從位置 start 開始，找字典裡最長的詞；找不到回傳 null */
    private static String longestMatchAt(String text, int start, int longest, Map<String, String> dictionary) {
        int maxLen = Math.min(longest, text.length() - start);
        for (int len = maxLen; len >= 1; len--) {
            String candidate = text.substring(start, start + len);
            if (dictionary.containsKey(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /** 內文總字數 T：中文以字計、英文和數字以單字計 */
    public static int textLength(String text) {
        if (text == null) {
            return 0;
        }
        Matcher words = WORD.matcher(text);
        int n = 0;
        while (words.find()) {
            n++;
        }
        return n;
    }
}
