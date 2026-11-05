package com.example.dsfinal.scoring;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把使用者輸入的查詢拆成搜尋語法：
 *
 *   柴犬 飼料            → 兩個查詢詞（空白 = AND）
 *   柴犬 OR 柯基         → 一個 OR 群組，任一詞出現即成立
 *   "黃金獵犬 掉毛"      → Exact：內文必須有完整詞組，沒有就排除
 *   -貓                  → NOT：當作這次查詢的負權重關鍵字
 *
 * @param raw    原始輸入
 * @param groups 查詢詞群組（n = groups.size()；一個 OR 群組算一個詞）
 * @param exact  必須完整出現的詞組
 * @param not    排除詞
 */
public record SearchQuery(String raw, List<List<String>> groups, List<String> exact, List<String> not) {

    /** 一個 token：可加 - 的 "引號詞組"，或一段不含空白的字 */
    private static final Pattern TOKEN = Pattern.compile("-?\"[^\"]+\"|\\S+");

    public static SearchQuery parse(String raw) {
        List<List<String>> groups = new ArrayList<>();
        List<String> exact = new ArrayList<>();
        List<String> not = new ArrayList<>();
        boolean joinWithPrevious = false;   // 上一個 token 是 OR

        Matcher tokens = TOKEN.matcher(raw == null ? "" : raw.trim());
        while (tokens.find()) {
            String token = tokens.group();

            if (token.equals("OR") || token.equals("|")) {
                joinWithPrevious = !groups.isEmpty();
                continue;
            }
            if (token.equals("AND")) {
                continue;   // 空白本來就是 AND
            }
            if (token.startsWith("-") && token.length() > 1) {
                not.add(unquote(token.substring(1)));
                continue;
            }

            String word = unquote(token);
            if (word.isEmpty()) {
                continue;
            }
            if (token.startsWith("\"")) {
                exact.add(word);
            }
            if (joinWithPrevious) {
                groups.get(groups.size() - 1).add(word);
                joinWithPrevious = false;
            } else {
                groups.add(new ArrayList<>(List.of(word)));
            }
        }
        return new SearchQuery(raw, groups, exact, not);
    }

    /** text 裡有沒有任一個查詢詞（同分時用來比較標題） */
    public boolean anyIn(String text) {
        for (List<String> group : groups) {
            if (groupIn(group, text)) {
                return true;
            }
        }
        return false;
    }

    /** 群組裡任一詞出現在 text 裡就算成立（不分大小寫） */
    public static boolean groupIn(List<String> group, String text) {
        String lowerText = text == null ? "" : text.toLowerCase(Locale.ROOT);
        for (String word : group) {
            if (lowerText.contains(word.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String unquote(String s) {
        return s.replace("\"", "").trim();
    }
}
