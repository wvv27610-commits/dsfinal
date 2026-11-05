package com.example.dsfinal.scoring;

import com.example.dsfinal.model.SearchResult;

/**
 * 計分算法的接口：給一筆結果，回傳它的分數，分數越高排越前面。
 *
 * 要用哪一個算法，在 application.properties 設定 search.scorer=名字，
 * 名字就是 class 上 @Component("名字") 括號裡的字。
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
}
