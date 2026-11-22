package com.example.dsfinal.crawler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 連結樹的一個節點 = 一個網址。
 *
 *   深度 0：搜尋結果本身（根節點）
 *   深度 1：根節點網頁上的同網站連結
 *   深度 2：深度 1 網頁上的同網站連結
 *
 * 只有「有下載」的節點（fetched = true）才有內文，keywordCounts 才會有值；
 * 最底層的節點只看連結文字和網址，不另外下載（提案第 2 節第三點）。
 */
public final class LinkNode {

    private final String url;
    private final String text;
    private final int depth;
    private final List<String> matchedKeywords;
    private final List<LinkNode> children = new ArrayList<>();
    private boolean fetched;
    private Map<String, Integer> keywordCounts = new LinkedHashMap<>();

    /**
     * @param url             網址
     * @param text            連結文字（根節點用網頁標題）
     * @param depth           層數 d（根節點為 0）
     * @param matchedKeywords 連結文字或網址裡出現的身份關鍵字（正權重）；有任何一個就算「相關連結」
     */
    public LinkNode(String url, String text, int depth, List<String> matchedKeywords) {
        this.url = url;
        this.text = text;
        this.depth = depth;
        this.matchedKeywords = List.copyOf(matchedKeywords);
    }

    public String getUrl() {
        return url;
    }

    public String getText() {
        return text;
    }

    public int getDepth() {
        return depth;
    }

    /** 連結文字或網址含有身份的正權重關鍵字 */
    public boolean isRelated() {
        return !matchedKeywords.isEmpty();
    }

    public List<String> getMatchedKeywords() {
        return matchedKeywords;
    }

    public List<LinkNode> getChildren() {
        return children;
    }

    /** 這個網頁有沒有被下載（下載了才有 keywordCounts） */
    public boolean isFetched() {
        return fetched;
    }

    /** 這個網頁內文裡，各個身份關鍵字出現的次數（只列出現過的；最長匹配） */
    public Map<String, Integer> getKeywordCounts() {
        return keywordCounts;
    }

    void addChild(LinkNode child) {
        children.add(child);
    }

    void markFetched(Map<String, Integer> counts) {
        this.fetched = true;
        this.keywordCounts = counts;
    }
}
