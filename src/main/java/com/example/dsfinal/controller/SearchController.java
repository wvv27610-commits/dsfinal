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
 * GET /api/search?q=關鍵字  →  成功時回傳 {"results": [...], "warnings": [...]}，全部來源都失敗時回傳 {"error": "原因"}
 */
@RestController
public class SearchController {

    private final SearchService searchService;
    private final ProfileRegistry profiles;

    public SearchController(SearchService searchService, ProfileRegistry profiles) {
        this.searchService = searchService;
        this.profiles = profiles;
    }

    /** 前端用來產生身份切換按鈕 */
    @GetMapping("/api/profiles")
    public List<Profile> profiles() {
        return profiles.all();
    }

    @GetMapping("/api/search")
    public ResponseEntity<?> search(@RequestParam(name = "q", defaultValue = "") String q,
                                    @RequestParam(name = "profile", required = false) String profile) {
        try {
            return ResponseEntity.ok(searchService.search(q, profile));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }
}
