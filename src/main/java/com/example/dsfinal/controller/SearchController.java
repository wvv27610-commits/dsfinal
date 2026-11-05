package com.example.dsfinal.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.dsfinal.profile.Profile;
import com.example.dsfinal.profile.ProfileRegistry;
import com.example.dsfinal.service.SearchService;

/**
 * 後端 API：
 *   GET /api/profiles                      → 所有身份（前端用來產生切換按鈕）
 *   GET /api/search?q=關鍵字&profile=身份id → {"results": [...], "warnings": [...], "searchedQuery": "..."}
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

    @GetMapping("/api/search")
    public ResponseEntity<?> search(@RequestParam(name = "q", defaultValue = "") String query,
                                    @RequestParam(name = "profile", required = false) String profileId) {
        try {
            return ResponseEntity.ok(searchService.search(query, profileId));
        } catch (Exception e) {
            Map<String, String> error = Map.of("error", String.valueOf(e.getMessage()));
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error);
        }
    }
}
