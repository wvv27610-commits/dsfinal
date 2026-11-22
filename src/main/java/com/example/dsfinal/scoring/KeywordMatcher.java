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
 *
 * 英文詞要「整個單字」才算：
 *   「cat」不會在 category、vacation 裡被算到；「dog」不會在 hotdog 裡被算到。
 *   英文字母結尾的詞允許加 s / es（dog → dogs、vaccine → vaccines）。
 * 中文沒有空白分隔，所以中文詞照舊用最長匹配，不檢查邊界。
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
            Match match = longestMatchAt(lowerText, i, longest, lowerToOriginal);
            if (match == null) {
                i++;
            } else {
                counts.merge(lowerToOriginal.get(match.word()), 1, Integer::sum);
                i += match.length();
            }
        }
        return counts;
    }

    /** 找到的詞，以及它在內文裡實際佔了幾個字（含 s / es） */
    private record Match(String word, int length) {
    }

    /** 從位置 start 開始，找字典裡最長、且英文字邊界正確的詞；找不到回傳 null */
    private static Match longestMatchAt(String text, int start, int longest, Map<String, String> dictionary) {
        int maxLen = Math.min(longest, text.length() - start);
        for (int len = maxLen; len >= 1; len--) {
            String candidate = text.substring(start, start + len);
            if (!dictionary.containsKey(candidate)) {
                continue;
            }
            int length = matchedLength(text, start, candidate);
            if (length > 0) {
                return new Match(candidate, length);
            }
        }
        return null;
    }

    /**
     * 檢查英文字邊界，回傳這個詞實際佔了幾個字；邊界不對回傳 0。
     *   - 詞的開頭是英文字母或數字 → 前一個字不能是英文字母或數字
     *   - 詞的結尾是英文字母或數字 → 後一個字不能是英文字母或數字（英文字母結尾可先吃掉 s / es）
     */
    private static int matchedLength(String text, int start, String word) {
        if (isWordChar(word.charAt(0)) && start > 0 && isWordChar(text.charAt(start - 1))) {
            return 0;
        }
        int end = start + word.length();
        char last = word.charAt(word.length() - 1);
        if (!isWordChar(last)) {
            return word.length();
        }
        if (!isWordCharAt(text, end)) {
            return word.length();
        }
        if (Character.isLetter(last)) {
            if (text.startsWith("s", end) && !isWordCharAt(text, end + 1)) {
                return word.length() + 1;
            }
            if (text.startsWith("es", end) && !isWordCharAt(text, end + 2)) {
                return word.length() + 2;
            }
        }
        return 0;
    }

    /** 英文字母或阿拉伯數字（內文已轉小寫） */
    private static boolean isWordChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }

    private static boolean isWordCharAt(String text, int index) {
        return index < text.length() && isWordChar(text.charAt(index));
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
