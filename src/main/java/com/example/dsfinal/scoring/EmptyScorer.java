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
@Component("Empty")
public class EmptyScorer implements ResultScorer {

    
    private final HtmlKeywordExtractor extractor;
    private final int minTextLength;      // T_min
    private final double maxPerKeyword;   // C
    private final double maxLinkScore;    // L_max
    private final double notWeight;

    public EmptyScorer(HtmlKeywordExtractor extractor,
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
        return 0;
    }

    @Override
    public String displayName() {
        return "不計分";
    }

    @Override
    public String description() {
        return "不對結果做處理";
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
