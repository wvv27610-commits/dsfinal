package com.example.dsfinal.scoring;

import com.example.dsfinal.profile.Profile;

/** 計分時需要的資訊：這次的查詢 + 使用者選的身份 */
public record ScoringContext(SearchQuery query, Profile profile) {
}
