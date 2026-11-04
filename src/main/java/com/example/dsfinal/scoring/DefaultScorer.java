package com.example.dsfinal.scoring;

import org.springframework.stereotype.Component;

import com.example.dsfinal.model.SearchResult;

/** 原本的簡單計分：標題出現一次 +3、摘要出現一次 +1。設定 search.scorer=default 使用。 */
@Component("default")
public class DefaultScorer implements ResultScorer {

    @Override
    public double score(ScoringContext ctx, SearchResult r) {
        String query = ctx.query().raw().trim();
        return TextUtil.count(r.title(), query) * 3 + TextUtil.count(r.snippet(), query);
    }
}
