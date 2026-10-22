package com.example.dsfinal.scoring;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.dsfinal.model.SearchResult;

/**
 * ===== 在這裡寫你自己的計分算法 =====
 *
 * application.properties 裡 search.scorer=my 時會用這個 class（預設就是 my）。
 * 改完存檔，devtools 會自動重新啟動，重新搜尋就能看到新的排序。
 *
 * 可以用的資料：
 *   query          使用者輸入的關鍵字
 *   r.title()      標題
 *   r.snippet()    摘要
 *   r.url()        網址
 *   r.sources()    從哪些來源拿到的，例如 ["google", "brave"]
 *   r.rank()       在原本搜尋來源裡的名次（1 = 第一名）
 * 工具：TextUtil.count(文字, 關鍵字) → 出現次數
 */
@Component("my")
public class MyScorer implements ResultScorer {

    /** 自訂關鍵字和權重：出現一次就加上對應的分數（自己改、自己加） */
    private static final Map<String, Double> KEYWORD_WEIGHTS = new LinkedHashMap<>();
    static {
        // KEYWORD_WEIGHTS.put("教學", 2.0);
        // KEYWORD_WEIGHTS.put("廣告", -5.0);
    }

    @Override
    public double score(String query, SearchResult r) {
        double score = 0;

        // 1. 使用者的關鍵字：標題 ×3、摘要 ×1
        score += TextUtil.count(r.title(), query) * 3.0;
        score += TextUtil.count(r.snippet(), query) * 1.0;

        // 2. 自訂關鍵字權重
        for (Map.Entry<String, Double> e : KEYWORD_WEIGHTS.entrySet()) {
            String text = r.title() + " " + r.snippet();
            score += TextUtil.count(text, e.getKey()) * e.getValue();
        }

        // 3. 原本搜尋引擎的名次：第 1 名 +1、第 10 名 +0.1
        score += 1.0 / r.rank();

        // 4. 多個來源都找到同一個網址，代表比較可信
        score += (r.sources().size() - 1) * 2.0;

        return score;
    }
}
