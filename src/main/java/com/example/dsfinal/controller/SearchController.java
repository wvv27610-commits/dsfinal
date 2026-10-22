package com.example.dsfinal.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.dsfinal.service.SearchService;

/**
 * GET /api/search?q=關鍵字  →  成功時回傳 {"results": [...], "warnings": [...]}，全部來源都失敗時回傳 {"error": "原因"}
 */
@RestController
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/api/search")
    public ResponseEntity<?> search(@RequestParam(name = "q", defaultValue = "") String q) {
        try {
            return ResponseEntity.ok(searchService.search(q));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }
}
