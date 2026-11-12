package com.example.dsfinal.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.dsfinal.model.ScorerInfo;
import com.example.dsfinal.profile.Profile;
import com.example.dsfinal.profile.ProfileRegistry;
import com.example.dsfinal.service.SearchService;

/**
 * 後端 API：
 *   GET /api/profiles                      → 所有身份（前端用來產生切換按鈕）
 *   GET /api/scorers                       → 所有計分算法（前端用來產生選單，預設的排第一個）
 *   GET /api/search?q=關鍵字&profile=身份id&scorer=算法id
 *                                          → {"results": [...], "warnings": [...], "searchedQuery": "...", "scorer": "..."}
 *                                            全部來源都失敗時回傳 {"error": "原因"}
 */
@RestController
public class SearchController {

    private final SearchService searchService;
    private final ProfileRegistry profiles;

    public SearchController(SearchService searchService, ProfileRegistry profiles) {
        this.searchService = searchService;
        this.profiles = profiles;
    }

    @GetMapping("/api/profiles")
    public List<Profile> profiles() {
        return profiles.all();
    }

    @GetMapping("/api/scorers")
    public List<ScorerInfo> scorers() {
        return searchService.scorers();
    }

    @GetMapping("/api/search")
    public ResponseEntity<?> search(@RequestParam(name = "q", defaultValue = "") String query,
                                    @RequestParam(name = "profile", required = false) String profileId,
                                    @RequestParam(name = "scorer", required = false) String scorerId) {
        try {
            return ResponseEntity.ok(searchService.search(query, profileId, scorerId));
        } catch (Exception e) {
            Map<String, String> error = Map.of("error", String.valueOf(e.getMessage()));
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error);
        }
    }
}
