package com.example.dsfinal.scoring;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.dsfinal.crawler.HtmlKeywordExtractor;
import com.example.dsfinal.crawler.HtmlKeywordExtractor.Link;
import com.example.dsfinal.crawler.HtmlKeywordExtractor.PageContent;
import com.example.dsfinal.model.SearchResult;

/**
 * Goo狗 評分公式（改良版）
 *
 *   Score = Σₖ clamp( Nₖ × Wₖ ÷ T′ × 100 , −C , C ) + Q + L
 *
 *   T′ = max(T, T_min)
 *   Q  = (1/n) × Σᵢ ( 2·tᵢ + bᵢ ) + a
 *   L  = L_max × 相關連結數 ÷ 全部連結數
 *
 * - Nₖ、Wₖ：身份字典裡第 k 個關鍵字的出現次數（最長匹配）和權重；NOT 排除詞的權重為 not-weight
 * - T：內文總字數（中文以字計、英文以單字計）
 * - n：查詢詞數（OR 群組算一個）；tᵢ、bᵢ：第 i 個查詢詞出現在標題、內文時為 1
 * - a：查詢有 2 個以上的詞且全部出現時為 1
 * - L：網頁裡連結文字或網址含有身份關鍵字（權重 > 0）的比例
 *
 * 參數都在 application.properties 的 scoring.* 調整。
 */
@Component("my")
public class MyScorer implements ResultScorer {

    private final HtmlKeywordExtractor extractor;
    private final int tMin;
    private final double c;
    private final double lMax;
    private final double notWeight;

    public MyScorer(HtmlKeywordExtractor extractor,
                    @Value("${scoring.t-min:200}") int tMin,
                    @Value("${scoring.c:3}") double c,
                    @Value("${scoring.l-max:2}") double lMax,
                    @Value("${scoring.not-weight:-3}") double notWeight) {
        this.extractor = extractor;
        this.tMin = tMin;
        this.c = c;
        this.lMax = lMax;
        this.notWeight = notWeight;
    }

    @Override
    public boolean accept(ScoringContext ctx, SearchResult r) {
        // Exact：內文（或標題、摘要）沒有完整詞組就排除
        String all = (r.title() + " " + r.snippet() + " " + body(r)).toLowerCase(Locale.ROOT);
        for (String phrase : ctx.query().exact()) {
            if (!all.contains(phrase.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public double score(ScoringContext ctx, SearchResult r) {
        String body = body(r);
        return density(ctx, body) + queryScore(ctx, r.title(), body) + linkScore(ctx, r.url());
    }

    /** 密度分數：Σₖ clamp( Nₖ × Wₖ ÷ T′ × 100 , −C , C ) */
    double density(ScoringContext ctx, String body) {
        Map<String, Double> weights = weights(ctx);
        Map<String, Integer> counts = KeywordMatcher.count(body, weights.keySet());
        double tPrime = Math.max(KeywordMatcher.textLength(body), tMin);

        double sum = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            double w = weights.get(e.getKey());
            if (w == 0) {
                continue;   // 排除詞：只用來避免誤算，不加不扣
            }
            sum += clamp(e.getValue() * w / tPrime * 100, -c, c);
        }
        return sum;
    }

    /** 查詢分數 Q = (1/n) × Σᵢ ( 2·tᵢ + bᵢ ) + a */
    double queryScore(ScoringContext ctx, String title, String body) {
        List<List<String>> groups = ctx.query().groups();
        int n = groups.size();
        if (n == 0) {
            return 0;
        }
        double sum = 0;
        boolean allPresent = true;
        for (List<String> g : groups) {
            int t = SearchQuery.groupIn(g, title) ? 1 : 0;
            int b = SearchQuery.groupIn(g, body) ? 1 : 0;
            sum += 2 * t + b;
            if (t == 0 && b == 0) {
                allPresent = false;
            }
        }
        int a = (n >= 2 && allPresent) ? 1 : 0;
        return sum / n + a;
    }

    /** 連結分數 L = L_max × 相關連結 ÷ 全部連結（只看這一頁上的連結，不另外下載子網頁） */
    double linkScore(ScoringContext ctx, String url) {
        List<Link> links = extractor.fetch(url).links();
        if (links.isEmpty()) {
            return 0;
        }
        int related = 0;
        for (Link link : links) {
            String text = (link.text() + " " + link.href()).toLowerCase(Locale.ROOT);
            for (Map.Entry<String, Double> e : ctx.profile().keywords().entrySet()) {
                if (e.getValue() > 0 && text.contains(e.getKey().toLowerCase(Locale.ROOT))) {
                    related++;
                    break;
                }
            }
        }
        return lMax * related / links.size();
    }

    /** 這次查詢用的字典：身份關鍵字 + 排除詞（權重 0）+ NOT 詞（負權重） */
    private Map<String, Double> weights(ScoringContext ctx) {
        Map<String, Double> w = new LinkedHashMap<>(ctx.profile().keywords());
        for (String s : ctx.profile().stopwords()) {
            w.putIfAbsent(s, 0.0);
        }
        for (String s : ctx.query().not()) {
            w.put(s, notWeight);
        }
        return w;
    }

    /** 內文：下載得到網頁就用網頁文字，下載失敗就退回用摘要 */
    private String body(SearchResult r) {
        PageContent page = extractor.fetch(r.url());
        return page.text().isEmpty() ? r.snippet() : page.text();
    }

    private static double clamp(double x, double lo, double hi) {
        return Math.max(lo, Math.min(hi, x));
    }
}
