package com.example.dsfinal.model;

import java.util.List;

/**
 * 一筆搜尋結果。
 *
 * @param title   標題
 * @param url     連結
 * @param snippet 摘要
 * @param sources 這筆結果是從哪些搜尋來源拿到的（例如 ["google", "brave"]），同一個網址出現在多個來源會合併成一筆
 * @param rank    在原本搜尋來源裡的名次（1 = 第一名；出現在多個來源時取最好的名次）
 * @param score   排序分數，由 scoring 套件裡的 ResultScorer 算出來
 */
public record SearchResult(String title, String url, String snippet, List<String> sources, int rank, double score) {

    /** 搜尋來源用的建構方式：只有一個來源、分數先設 0 */
    public static SearchResult of(String title, String url, String snippet, String source, int rank) {
        return new SearchResult(title, url, snippet, List.of(source), rank, 0);
    }

    public SearchResult withScore(double newScore) {
        return new SearchResult(title, url, snippet, sources, rank, newScore);
    }
}
