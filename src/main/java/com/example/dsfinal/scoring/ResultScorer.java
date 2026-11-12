package com.example.dsfinal.scoring;

import com.example.dsfinal.model.SearchResult;

/**
 * 計分算法的接口：給一筆結果，回傳它的分數，分數越高排越前面。
 *
 * 寫一個 class 實作這個接口並加上 @Component("名字")，前端的「計分算法」選單就會自動出現它。
 * 預設用哪一個，在 application.properties 設定 search.scorer=名字。
 */
public interface ResultScorer {

    /**
     * @param ctx    這次的查詢（已拆好搜尋語法）和使用者選的身份
     * @param result 一筆搜尋結果，可以用 title() / url() / snippet() / sources() / rank()
     */
    double score(ScoringContext ctx, SearchResult result);

    /** 這筆結果要不要保留（例如 Exact 詞組沒出現就排除）。預設全部保留。 */
    default boolean accept(ScoringContext ctx, SearchResult result) {
        return true;
    }

    /** 前端選單上顯示的名字。預設用 class 名稱。 */
    default String displayName() {
        return getClass().getSimpleName();
    }

    /** 前端選單下方的說明文字。預設空白。 */
    default String description() {
        return "";
    }
}
