package com.example.dsfinal.crawler;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.dsfinal.crawler.HtmlKeywordExtractor.Link;
import com.example.dsfinal.crawler.HtmlKeywordExtractor.PageContent;
import com.example.dsfinal.profile.Profile;
import com.example.dsfinal.scoring.KeywordMatcher;

/**
 * 依網頁裡的 <a> 連結，建立同網站的連結樹（提案第 3 節 LinkTreeBuilder）。
 *
 * 用 BFS（廣度優先）一層一層展開：
 *   - Queue：放「等著被展開」的節點，先進先出，所以會先處理完第 1 層才處理第 2 層
 *   - HashSet visited：記錄看過的網址，同一個網址只出現一次，連回首頁或互相連結也不會繞圈
 *
 * 為了避免下載量成倍增加（提案 Challenges 第 3 點）：
 *   - 最多展開 max-depth 層（預設 2）
 *   - 每頁最多記錄 max-links-per-page 個連結
 *   - 每個節點最多下載 expand-per-page 個子網頁去找下一層；最底層只看連結文字，不下載
 *   - 只收同網站（同網域）的網頁連結，圖片、PDF 等檔案跳過
 *
 * 樹的結構（有哪些連結）跟身份無關，但「哪些連結相關」要看身份關鍵字，
 * 所以快取的 key 是 網址 + 身份 + 語言。快取用 LRU：超過上限時丟掉最久沒用的。
 */
@Component
public class LinkTreeBuilder {

    /** 這些副檔名的連結是檔案，不是網頁 */
    private static final Pattern FILE_LINK = Pattern.compile(
            ".*\\.(jpe?g|png|gif|webp|svg|ico|pdf|zip|rar|7z|mp3|mp4|mov|avi|css|js|xml|json|docx?|xlsx?|pptx?)$",
            Pattern.CASE_INSENSITIVE);

    private final HtmlKeywordExtractor extractor;
    private final int maxDepth;
    private final int maxLinksPerPage;
    private final int expandPerPage;
    private final Map<String, LinkTree> cache;

    public LinkTreeBuilder(HtmlKeywordExtractor extractor,
                           @Value("${scoring.tree.max-depth:2}") int maxDepth,
                           @Value("${scoring.tree.max-links-per-page:30}") int maxLinksPerPage,
                           @Value("${scoring.tree.expand-per-page:3}") int expandPerPage,
                           @Value("${scoring.tree.cache-size:256}") int cacheSize) {
        this.extractor = extractor;
        this.maxDepth = maxDepth;
        this.maxLinksPerPage = maxLinksPerPage;
        this.expandPerPage = expandPerPage;
        this.cache = lruCache(cacheSize);
    }

    /** 建立（或從快取拿）這個網址在這個身份下的連結樹 */
    public LinkTree build(String url, Profile profile) {
        String key = cacheKey(url, profile);
        synchronized (cache) {
            LinkTree cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
        }
        LinkTree tree = buildTree(url, profile);
        synchronized (cache) {
            cache.put(key, tree);
        }
        return tree;
    }

    /** 只從快取拿，沒建過就回傳 null（SearchService 用來把樹附在搜尋結果上） */
    public LinkTree cached(String url, Profile profile) {
        synchronized (cache) {
            return cache.get(cacheKey(url, profile));
        }
    }

    // ===== BFS 建樹 =====

    private LinkTree buildTree(String url, Profile profile) {
        Map<String, Double> dictionary = dictionary(profile);
        PageContent rootPage = extractor.fetch(url);
        if (rootPage.text().isEmpty() && rootPage.links().isEmpty()) {
            return LinkTree.empty(url);
        }

        String host = host(url);
        LinkNode root = new LinkNode(url, rootPage.title(), 0, List.of());
        Set<String> visited = new HashSet<>();
        visited.add(visitKey(url));

        Queue<LinkNode> queue = new ArrayDeque<>();
        queue.add(root);

        while (!queue.isEmpty()) {
            // 這一層要展開的網頁先「同時」下載好，下面一個一個處理時就不用等
            List<LinkNode> level = new ArrayList<>(queue);
            queue.clear();
            level.parallelStream().forEach(node -> extractor.fetch(node.getUrl()));

            for (LinkNode node : level) {
                PageContent page = extractor.fetch(node.getUrl());
                if (page.text().isEmpty() && page.links().isEmpty()) {
                    continue;   // 打不開的子網頁：留在樹上，但沒有內文、也不再往下展開
                }
                node.markFetched(countKeywords(page.text(), dictionary));
                if (node.getDepth() >= maxDepth) {
                    continue;
                }
                int expanded = 0;
                for (Link link : page.links()) {
                    if (node.getChildren().size() >= maxLinksPerPage) {
                        break;
                    }
                    String childUrl = cleanUrl(link.href());
                    if (childUrl == null || !host.equals(host(childUrl)) || !visited.add(visitKey(childUrl))) {
                        continue;
                    }
                    LinkNode child = new LinkNode(childUrl, link.text(), node.getDepth() + 1,
                            matchedKeywords(link, childUrl, dictionary));
                    node.addChild(child);

                    // 還沒到最底層、這頁還有展開名額 → 放進 Queue，下一輪下載它找下一層
                    if (child.getDepth() < maxDepth && expanded < expandPerPage) {
                        queue.add(child);
                        expanded++;
                    }
                }
            }
        }
        return new LinkTree(root);
    }

    // ===== 關鍵字 =====

    /** 身份關鍵字 + 排除詞（權重 0，只用來避免「熱狗」被算成「狗」） */
    private static Map<String, Double> dictionary(Profile profile) {
        Map<String, Double> weights = new LinkedHashMap<>(profile.keywords());
        for (String stopword : profile.stopwords()) {
            weights.putIfAbsent(stopword, 0.0);
        }
        return weights;
    }

    /** 連結文字和網址（解碼後）裡出現的正權重關鍵字 */
    private static List<String> matchedKeywords(Link link, String url, Map<String, Double> dictionary) {
        String text = link.text() + " " + decode(url);
        List<String> matched = new ArrayList<>();
        for (String word : KeywordMatcher.count(text, dictionary.keySet()).keySet()) {
            if (dictionary.get(word) > 0) {
                matched.add(word);
            }
        }
        return matched;
    }

    /** 內文裡每個關鍵字的次數（排除詞不列出） */
    private static Map<String, Integer> countKeywords(String text, Map<String, Double> dictionary) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : KeywordMatcher.count(text, dictionary.keySet()).entrySet()) {
            if (dictionary.get(entry.getKey()) != 0) {
                counts.put(entry.getKey(), entry.getValue());
            }
        }
        return counts;
    }

    // ===== 網址處理 =====

    /** 拿掉 #錨點；不是 http(s) 或是檔案就回傳 null */
    static String cleanUrl(String href) {
        if (href == null || !href.startsWith("http")) {
            return null;
        }
        int hash = href.indexOf('#');
        String url = hash >= 0 ? href.substring(0, hash) : href;
        String path = url.contains("?") ? url.substring(0, url.indexOf('?')) : url;
        if (url.isEmpty() || FILE_LINK.matcher(path).matches()) {
            return null;
        }
        return url;
    }

    /** 網域（忽略 www.），比對是不是同一個網站 */
    static String host(String url) {
        try {
            String host = URI.create(url.replace(" ", "%20")).getHost();
            if (host == null) {
                return "";
            }
            host = host.toLowerCase(Locale.ROOT);
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    /** 判斷「看過了沒」用的 key：忽略 http/https、www、結尾的 / */
    static String visitKey(String url) {
        return url.replaceFirst("^https?://(www\\.)?", "").replaceFirst("/$", "").toLowerCase(Locale.ROOT);
    }

    private static String decode(String url) {
        try {
            return URLDecoder.decode(url, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return url;
        }
    }

    private static String cacheKey(String url, Profile profile) {
        return url + "|" + profile.id() + "|" + profile.language();
    }

    /** LinkedHashMap 設成「存取順序」，超過上限就移除最久沒用的那筆 */
    private static Map<String, LinkTree> lruCache(int maxSize) {
        return new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, LinkTree> eldest) {
                return size() > maxSize;
            }
        };
    }
}
