package com.example.dsfinal.scoring;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 用「最長匹配」統計字典裡每個詞出現的次數。
 *
 * 從左到右掃描，每個位置先試最長的詞：「黃金獵犬」算一次「黃金獵犬」，不會再拆出「犬」；
 * 「熱狗」若在字典裡（排除詞），就不會被算成「狗」。
 */
public final class KeywordMatcher {

    private static final Pattern WORD = Pattern.compile("[\\p{IsHan}]|[A-Za-z0-9]+");

    private KeywordMatcher() {
    }

    /** 回傳 字典詞 → 出現次數（只列出現過的詞） */
    public static Map<String, Integer> count(String text, Set<String> dictionary) {
        Map<String, String> lower = new LinkedHashMap<>();
        int maxLen = 0;
        for (String w : dictionary) {
            if (!w.isEmpty()) {
                lower.put(w.toLowerCase(Locale.ROOT), w);
                maxLen = Math.max(maxLen, w.length());
            }
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (text == null || text.isEmpty() || maxLen == 0) {
            return counts;
        }
        String t = text.toLowerCase(Locale.ROOT);
        int i = 0;
        while (i < t.length()) {
            int matched = 0;
            for (int len = Math.min(maxLen, t.length() - i); len >= 1; len--) {
                String original = lower.get(t.substring(i, i + len));
                if (original != null) {
                    counts.merge(original, 1, Integer::sum);
                    matched = len;
                    break;
                }
            }
            i += matched > 0 ? matched : 1;
        }
        return counts;
    }

    /** 內文總字數 T：中文以字計、英文和數字以單字計 */
    public static int textLength(String text) {
        if (text == null) {
            return 0;
        }
        Matcher m = WORD.matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }
}
