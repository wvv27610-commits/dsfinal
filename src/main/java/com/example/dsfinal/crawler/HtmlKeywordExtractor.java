package com.example.dsfinal.crawler;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import com.example.dsfinal.scoring.TextUtil;

/**
 * 讀取網頁 HTML，並統計網頁內容裡關鍵字出現的次數。
 *
 * 用法：
 *   Map<String, Integer> counts = extractor.countKeywords(url, List.of("java", "教學"));
 *   // → {java=12, 教學=3}
 *
 * - 用 Jsoup 下載並解析 HTML，只取「看得到的文字」（會去掉 script、style、HTML 標籤）。
 * - 同一個網址只會下載一次（存在 cache 裡）。
 * - 網頁打不開或逾時，就當作內容是空的（次數都是 0），不會讓整個搜尋失敗。
 */
@Component
public class HtmlKeywordExtractor {

    private static final int TIMEOUT_MS = 5000;
    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;   // 最多讀 2MB
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36";

    /** 網頁內容：標題 + 內文文字 */
    public record PageContent(String title, String text) {
        public static final PageContent EMPTY = new PageContent("", "");
    }

    private final Map<String, PageContent> cache = new ConcurrentHashMap<>();

    /** 下載網頁並取出標題和純文字內容 */
    public PageContent fetch(String url) {
        return cache.computeIfAbsent(url, this::download);
    }

    /** 統計每個關鍵字在網頁（標題 + 內文）出現幾次 */
    public Map<String, Integer> countKeywords(String url, Collection<String> keywords) {
        PageContent page = fetch(url);
        String all = page.title() + " " + page.text();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String k : keywords) {
            counts.put(k, TextUtil.count(all, k));
        }
        return counts;
    }

    /** 統計單一關鍵字在網頁出現幾次 */
    public int countKeyword(String url, String keyword) {
        PageContent page = fetch(url);
        return TextUtil.count(page.title() + " " + page.text(), keyword);
    }

    private PageContent download(String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .maxBodySize(MAX_BODY_BYTES)
                    .followRedirects(true)
                    .get();
            doc.select("script, style, noscript").remove();
            String text = doc.body() == null ? "" : doc.body().text();
            return new PageContent(doc.title(), text);
        } catch (Exception e) {
            // 打不開（403、逾時、不是 HTML 等）→ 當作空內容
            return PageContent.EMPTY;
        }
    }
}
