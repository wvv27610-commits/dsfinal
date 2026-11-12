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

/** 原本的簡單計分：標題出現一次 +3、摘要出現一次 +1。設定 search.scorer=default 使用。 */
@Component("Weight")
public class WeightScorer implements ResultScorer {

    
    private final HtmlKeywordExtractor extractor;
    private final int minTextLength;      // T_min
    private final double maxPerKeyword;   // C
    private final double maxLinkScore;    // L_max
    private final double notWeight;

    public WeightScorer(HtmlKeywordExtractor extractor,
                    @Value("${scoring.t-min:200}") int minTextLength,
                    @Value("${scoring.c:3}") double maxPerKeyword,
                    @Value("${scoring.l-max:2}") double maxLinkScore,
                    @Value("${scoring.not-weight:-3}") double notWeight) {
        this.extractor = extractor;
        this.minTextLength = minTextLength;
        this.maxPerKeyword = maxPerKeyword;
        this.maxLinkScore = maxLinkScore;
        this.notWeight = notWeight;
    }

    @Override
    public double score(ScoringContext ctx, SearchResult r) {
        String body = bodyText(r);
        return density(ctx, body);
    }

    @Override
    public String displayName() {
        return "只算權重";
    }

    @Override
    public String description() {
        return "只算權重";
    }

    double density(ScoringContext ctx, String body) {
        Map<String, Double> weights = dictionary(ctx);
        Map<String, Integer> counts = KeywordMatcher.count(body, weights.keySet());
        double textLength = Math.max(KeywordMatcher.textLength(body), minTextLength);   // T′

        double total = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            int n = entry.getValue();
            double w = weights.get(entry.getKey());
            if (w == 0) {
                continue;   // 排除詞：只用來避免誤算，不加不扣
            }
            double perHundredWords = n * w / textLength * 100;
            total += clamp(perHundredWords, -maxPerKeyword, maxPerKeyword);
        }
        return total;
    }

    private Map<String, Double> dictionary(ScoringContext ctx) {
        Map<String, Double> weights = new LinkedHashMap<>(ctx.profile().keywords());
        for (String stopword : ctx.profile().stopwords()) {
            weights.putIfAbsent(stopword, 0.0);
        }
        for (String excluded : ctx.query().not()) {
            weights.put(excluded, notWeight);
        }
        return weights;
    }
    
        // ===== 小工具 =====

    /** 內文：下載得到網頁就用網頁文字，下載失敗就退回用摘要 */
    private String bodyText(SearchResult r) {
        PageContent page = extractor.fetch(r.url());
        return page.text().isEmpty() ? r.snippet() : page.text();
    }

    private static double clamp(double x, double min, double max) {
        return Math.max(min, Math.min(max, x));
    }

    private static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
    
}
