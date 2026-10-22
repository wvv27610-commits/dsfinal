package com.example.dsfinal.provider;

import java.util.List;

import com.example.dsfinal.model.SearchResult;

/**
 * 搜尋來源的接口。
 *
 * 要加一個新的搜尋來源，只要：
 *   1. 在 provider 套件裡新增一個 class，implements SearchProvider
 *   2. 在 class 上面加 @Component
 * SearchService 會自動找到所有的 SearchProvider，同時去搜尋，再把結果合併。
 * 範例可以參考 SerpApiGoogleProvider 和 BraveSearchProvider。
 */
public interface SearchProvider {

    /** 來源名稱，會顯示在結果上，也可以在 ResultScorer 裡拿來判斷（例如 "google"） */
    String name();

    /** 是否啟用。通常是「有設定 API key 才啟用」，沒設定的來源會被略過 */
    boolean isEnabled();

    /**
     * 用關鍵字搜尋，回傳結果。
     * 每筆請用 SearchResult.of(title, url, snippet, name(), 名次) 建立，名次從 1 開始。
     * 失敗時直接丟出例外即可，SearchService 會把它變成警告訊息，不會影響其他來源。
     */
    List<SearchResult> search(String query) throws Exception;
}
