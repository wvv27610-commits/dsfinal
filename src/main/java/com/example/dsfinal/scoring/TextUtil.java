package com.example.dsfinal.scoring;

import java.util.Locale;

/** 計分時常用的小工具 */
public final class TextUtil {

    private TextUtil() {
    }

    /** keyword 在 text 裡出現幾次（不分大小寫） */
    public static int count(String text, String keyword) {
        if (text == null || keyword == null || keyword.isEmpty()) {
            return 0;
        }
        String t = text.toLowerCase(Locale.ROOT);
        String k = keyword.toLowerCase(Locale.ROOT);
        int n = 0;
        for (int i = t.indexOf(k); i >= 0; i = t.indexOf(k, i + k.length())) {
            n++;
        }
        return n;
    }
}
