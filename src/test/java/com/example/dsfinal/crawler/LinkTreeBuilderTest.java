package com.example.dsfinal.crawler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import com.example.dsfinal.crawler.HtmlKeywordExtractor.Link;
import com.example.dsfinal.crawler.HtmlKeywordExtractor.PageContent;
import com.example.dsfinal.profile.Profile;

/**
 * 用一個假的小網站測試連結樹，不需要連網路。
 *
 *   首頁 https://dog.example.com/
 *   ├─ /shiba     「柴犬介紹」   相關（柴犬）
 *   │  ├─ /shiba/care   「柴犬照顧」 相關
 *   │  └─ /shiba/photos 「相簿」     不相關
 *   ├─ /about     「關於我們」   不相關
 *   │  └─ /contact      「聯絡」     不相關
 *   ├─ /food?id=1 「狗飼料」     相關（打不開，所以沒有下一層）
 *   ├─ /hotdog    「熱狗食譜」   不相關（熱狗是排除詞，不算「狗」）
 *   └─ /cat       「貓咪」       不相關（負權重不算相關）
 *   另外有：別的網站、圖片、連回首頁 → 都不放進樹
 *
 *   相關的 1/d 總和 = 1 + 1 + 1/2 = 2.5
 *   全部的 1/d 總和 = 5 × 1 + 3 × 1/2 = 6.5
 *   relatedRatio    = 2.5 ÷ 6.5 ≈ 0.3846
 */
class LinkTreeBuilderTest {

    private static final String ROOT = "https://dog.example.com/";

    private final Profile profile = new Profile("pet-owner", "寵物主人", "",
            Map.of("柴犬", 3.0, "狗", 2.0, "飼料", 1.0, "貓咪", -2.0), List.of("熱狗"),
            "", "", "", "zh-TW", true);

    /** 假的下載器：只認得下面寫好的網頁，並記錄下載過哪些網址 */
    private static class FakeSite extends HtmlKeywordExtractor {
        final Map<String, PageContent> pages = new HashMap<>();
        final Set<String> fetched = ConcurrentHashMap.newKeySet();

        FakeSite() {
            pages.put(ROOT, new PageContent("狗狗之家", "柴犬 狗 狗 熱狗", List.of(
                    new Link("柴犬介紹", "https://dog.example.com/shiba"),
                    new Link("關於我們", "https://www.dog.example.com/about"),
                    new Link("狗飼料", "https://dog.example.com/food?id=1#top"),
                    new Link("其他網站", "https://other.com/dog"),
                    new Link("照片", "https://dog.example.com/pic.jpg"),
                    new Link("首頁", "https://dog.example.com"),
                    new Link("熱狗食譜", "https://dog.example.com/hotdog"),
                    new Link("貓咪", "https://dog.example.com/cat"))));
            pages.put("https://dog.example.com/shiba", new PageContent("柴犬", "柴犬 柴犬", List.of(
                    new Link("柴犬照顧", "https://dog.example.com/shiba/care"),
                    new Link("關於我們", "https://dog.example.com/about/"),
                    new Link("相簿", "https://dog.example.com/shiba/photos"))));
            pages.put("https://www.dog.example.com/about", new PageContent("關於", "我們", List.of(
                    new Link("聯絡", "https://dog.example.com/contact"))));
        }

        @Override
        public PageContent fetch(String url) {
            fetched.add(url);
            return pages.getOrDefault(url, PageContent.EMPTY);
        }
    }

    private static LinkTreeBuilder builder(FakeSite site, int maxDepth) {
        return new LinkTreeBuilder(site, maxDepth, 30, 3, 16);
    }

    @Test
    void buildsTwoLevelTreeWithBfs() {
        FakeSite site = new FakeSite();
        LinkTree tree = builder(site, 2).build(ROOT, profile);
        LinkNode root = tree.getRoot();

        List<String> level1 = root.getChildren().stream().map(LinkNode::getUrl).toList();
        assertEquals(List.of(
                "https://dog.example.com/shiba",
                "https://www.dog.example.com/about",
                "https://dog.example.com/food?id=1",     // #top 被拿掉
                "https://dog.example.com/hotdog",
                "https://dog.example.com/cat"), level1);  // 別的網站、圖片、首頁都不在

        assertEquals(8, tree.getLinkCount());
        assertEquals(3, tree.getRelatedCount());
        assertEquals(2.5 / 6.5, tree.getRelatedRatio(), 1e-9);
    }

    @Test
    void relatedLinksFollowProfileKeywords() {
        LinkNode root = builder(new FakeSite(), 2).build(ROOT, profile).getRoot();
        Map<String, LinkNode> byText = new HashMap<>();
        for (LinkNode child : root.getChildren()) {
            byText.put(child.getText(), child);
        }
        assertEquals(List.of("柴犬"), byText.get("柴犬介紹").getMatchedKeywords());
        assertTrue(byText.get("狗飼料").isRelated());
        assertFalse(byText.get("熱狗食譜").isRelated());   // 排除詞
        assertFalse(byText.get("貓咪").isRelated());       // 負權重
        assertFalse(byText.get("關於我們").isRelated());
    }

    @Test
    void countsKeywordsOnlyInDownloadedPages() {
        FakeSite site = new FakeSite();
        LinkNode root = builder(site, 2).build(ROOT, profile).getRoot();

        assertTrue(root.isFetched());
        assertEquals(Map.of("柴犬", 1, "狗", 2), root.getKeywordCounts());   // 熱狗不算狗

        LinkNode shiba = root.getChildren().get(0);
        assertTrue(shiba.isFetched());
        assertEquals(Map.of("柴犬", 2), shiba.getKeywordCounts());

        LinkNode food = root.getChildren().get(2);
        assertFalse(food.isFetched());                  // 打不開
        assertTrue(food.getChildren().isEmpty());

        LinkNode care = shiba.getChildren().get(0);
        assertEquals(2, care.getDepth());
        assertFalse(care.isFetched());                  // 最底層只看連結文字，不下載
        assertFalse(site.fetched.contains("https://dog.example.com/shiba/care"));
        assertFalse(site.fetched.contains("https://dog.example.com/hotdog"));   // 超過展開名額 3 個
    }

    @Test
    void visitedSetPreventsDuplicates() {
        LinkNode root = builder(new FakeSite(), 2).build(ROOT, profile).getRoot();
        LinkNode shiba = root.getChildren().get(0);
        // shiba 頁面也連到 /about/，但 /about 已經在第 1 層出現過 → 不再放一次
        assertEquals(List.of("https://dog.example.com/shiba/care", "https://dog.example.com/shiba/photos"),
                shiba.getChildren().stream().map(LinkNode::getUrl).toList());
    }

    @Test
    void maxDepthOneOnlyKeepsFirstLevel() {
        FakeSite site = new FakeSite();
        LinkTree tree = builder(site, 1).build(ROOT, profile);
        assertEquals(5, tree.getLinkCount());
        assertEquals(2.0 / 5.0, tree.getRelatedRatio(), 1e-9);
        assertEquals(Set.of(ROOT), site.fetched);       // 只下載首頁
    }

    @Test
    void brokenPageGivesEmptyTree() {
        LinkTree tree = builder(new FakeSite(), 2).build("https://dog.example.com/404", profile);
        assertEquals(0, tree.getLinkCount());
        assertEquals(0.0, tree.getRelatedRatio());
    }

    @Test
    void treesAreCachedPerProfile() {
        LinkTreeBuilder b = builder(new FakeSite(), 2);
        LinkTree first = b.build(ROOT, profile);
        assertTrue(first == b.build(ROOT, profile));
        assertTrue(first == b.cached(ROOT, profile));
        Profile other = new Profile("biology", "生物", "", Map.of("基因", 2.0), List.of(),
                "", "", "", "zh-TW", true);
        assertTrue(b.cached(ROOT, other) == null);
    }
}
