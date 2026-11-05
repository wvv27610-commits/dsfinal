package com.example.dsfinal.crawler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * 下載網頁 HTML，取出標題、內文文字和所有連結。
 *
 * - 內文只取「看得到的文字」（去掉 script、style）。
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

    /** 取得網頁內容（第一次會下載，之後從 cache 拿） */
    public PageContent fetch(String url) {
        return cache.computeIfAbsent(url, this::download);
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
            return new PageContent(doc.title(), text, extractLinks(doc));
        } catch (Exception e) {
            // 打不開（403、逾時、不是 HTML 等）→ 當作空內容
            return PageContent.EMPTY;
        }
    }

    /** 網頁裡所有 http(s) 連結，最多 MAX_LINKS 個 */
    private static List<Link> extractLinks(Document doc) {
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
        return links;
    }
}
