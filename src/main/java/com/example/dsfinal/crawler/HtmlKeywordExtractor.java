package com.example.dsfinal.crawler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import com.example.dsfinal.scoring.TextUtil;

/**
 * 讀取網頁 HTML：取出標題、內文文字、所有連結，並可統計關鍵字出現次數。
 *
 * - 用 Jsoup 下載並解析 HTML，內文只取「看得到的文字」（去掉 script、style）。
 * - 同一個網址只會下載一次（存在 cache 裡）。
 * - 網頁打不開或逾時，就當作內容是空的，不會讓整個搜尋失敗。
 */
@Component
public class HtmlKeywordExtractor {

    private static final int TIMEOUT_MS = 5000;
    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;   // 最多讀 2MB
    private static final int MAX_LINKS = 300;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36";

    /** 網頁上的一個連結 */
    public record Link(String text, String href) {
    }

    /** 網頁內容：標題、內文文字、連結 */
    public record PageContent(String title, String text, List<Link> links) {
        public static final PageContent EMPTY = new PageContent("", "", List.of());
    }

    private final Map<String, PageContent> cache = new ConcurrentHashMap<>();

    /** 下載網頁並取出標題、純文字內容和連結 */
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

            List<Link> links = new ArrayList<>();
            for (Element a : doc.select("a[href]")) {
                if (links.size() >= MAX_LINKS) {
                    break;
                }
                String href = a.absUrl("href");
                if (href.startsWith("http")) {
                    links.add(new Link(a.text(), href));
                }
            }
            String text = doc.body() == null ? "" : doc.body().text();
            return new PageContent(doc.title(), text, links);
        } catch (Exception e) {
            // 打不開（403、逾時、不是 HTML 等）→ 當作空內容
            return PageContent.EMPTY;
        }
    }
}
