package com.example.dsfinal.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.dsfinal.language.Language;
import com.example.dsfinal.language.LanguageRegistry;
import com.example.dsfinal.model.ScorerInfo;
import com.example.dsfinal.profile.Profile;
import com.example.dsfinal.profile.ProfileRegistry;
import com.example.dsfinal.service.SearchService;

/**
 * 後端 API：
 *   GET /api/languages                     → 所有語言和預設語言（前端用來產生語言選單）
 *   GET /api/profiles?lang=語言            → 所有身份，名稱和關鍵字換成該語言（前端用來產生切換按鈕）
 *   GET /api/scorers                       → 所有計分算法（前端用來產生選單，預設的排第一個）
 *   GET /api/search?q=關鍵字&profile=身份id&scorer=算法id&lang=語言
 *                                          → {"results": [...], "warnings": [...], "searchedQuery": "...",
 *                                             "scorer": "...", "profile": "...", "language": "..."}
 *                                            全部來源都失敗時回傳 {"error": "原因"}
 */
@RestController
public class SearchController {

    private final SearchService searchService;
    private final ProfileRegistry profiles;
    private final LanguageRegistry languages;

    public SearchController(SearchService searchService, ProfileRegistry profiles, LanguageRegistry languages) {
        this.searchService = searchService;
        this.profiles = profiles;
        this.languages = languages;
    }

    /** {"default": "zh-TW", "languages": [{"code": "zh-TW", "label": "繁體中文", ...}, ...]} */
    @GetMapping("/api/languages")
    public Map<String, Object> languages() {
        return Map.of(
                "default", languages.defaultLanguage().code(),
                "languages", languages.all());
    }

    @GetMapping("/api/profiles")
    public List<Profile> profiles(@RequestParam(name = "lang", required = false) String languageCode) {
        Language language = languages.get(languageCode);
        return profiles.all(language.code());
    }

    @GetMapping("/api/scorers")
    public List<ScorerInfo> scorers() {
        return searchService.scorers();
    }

    @GetMapping("/api/search")
    public ResponseEntity<?> search(@RequestParam(name = "q", defaultValue = "") String query,
                                    @RequestParam(name = "profile", required = false) String profileId,
                                    @RequestParam(name = "scorer", required = false) String scorerId,
                                    @RequestParam(name = "lang", required = false) String languageCode) {
        try {
            return ResponseEntity.ok(searchService.search(query, profileId, scorerId, languageCode));
        } catch (Exception e) {
            Map<String, String> error = Map.of("error", String.valueOf(e.getMessage()));
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error);
        }
    }
}
