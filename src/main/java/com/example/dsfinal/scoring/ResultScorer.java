package com.example.dsfinal.scoring;

import com.example.dsfinal.model.SearchResult;

/**
 * 計分算法的接口：給一筆結果，回傳它的分數，分數越高排越前面。
 *
 * 要用哪一個算法，在 application.properties 設定 search.scorer=名字，
 * 名字就是 class 上 @Component("名字") 括號裡的字。
 * 自己的算法直接改 MyScorer 就好。
 */
public interface ResultScorer {

    /**
     * @param query  使用者輸入的關鍵字（已去掉前後空白）
     * @param result 一筆搜尋結果，可以用 title() / url() / snippet() / sources() / rank()
     */
    double score(String query, SearchResult result);
}
