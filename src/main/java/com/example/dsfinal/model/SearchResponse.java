package com.example.dsfinal.model;

import java.util.List;

/**
 * /api/search 回傳的內容。
 *
 * @param results       排序好的結果
 * @param warnings      某些搜尋來源失敗時的訊息（其他來源的結果照樣回傳）
 * @param searchedQuery 實際送給搜尋來源的查詢（含強制附加的前後綴）
 * @param scorer        這次實際使用的計分算法 id
 */
public record SearchResponse(List<SearchResult> results, List<String> warnings, String searchedQuery, String scorer) {
}
