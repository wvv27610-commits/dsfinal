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
 * @param groups 查詢詞群組（n = groups.size()；一個 OR 群組算一個詞）
 * @param exact  必須完整出現的詞組
 * @param not    排除詞
 */
public record SearchQuery(String raw, List<List<String>> groups, List<String> exact, List<String> not) {

    private static final Pattern TOKEN = Pattern.compile("-?\"[^\"]+\"|\\S+");

    public static SearchQuery parse(String raw) {
        List<List<String>> groups = new ArrayList<>();
        List<String> exact = new ArrayList<>();
        List<String> not = new ArrayList<>();
        boolean orNext = false;

        Matcher m = TOKEN.matcher(raw == null ? "" : raw.trim());
        while (m.find()) {
            String tok = m.group();
            if (tok.equals("OR") || tok.equals("|")) {
                orNext = !groups.isEmpty();
                continue;
            }
            if (tok.equals("AND")) {
                continue;   // 空白本來就是 AND
            }
            if (tok.startsWith("-") && tok.length() > 1) {
                not.add(unquote(tok.substring(1)));
                continue;
            }
            String word = unquote(tok);
            if (word.isEmpty()) {
                continue;
            }
            if (tok.startsWith("\"")) {
                exact.add(word);
            }
            if (orNext) {
                groups.get(groups.size() - 1).add(word);
                orNext = false;
            } else {
                groups.add(new ArrayList<>(List.of(word)));
            }
        }
        return new SearchQuery(raw, groups, exact, not);
    }

    /** 標題裡有沒有任一查詢詞（同分時用來比較） */
    public boolean anyIn(String text) {
        String t = text == null ? "" : text.toLowerCase(Locale.ROOT);
        for (List<String> g : groups) {
            for (String w : g) {
                if (t.contains(w.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 群組裡任一詞出現在 text 裡就算成立 */
    public static boolean groupIn(List<String> group, String text) {
        String t = text == null ? "" : text.toLowerCase(Locale.ROOT);
        for (String w : group) {
            if (t.contains(w.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String unquote(String s) {
        return s.replace("\"", "").trim();
    }
}
