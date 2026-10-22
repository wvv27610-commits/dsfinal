package com.example.dsfinal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.dsfinal.model.SearchResponse;
import com.example.dsfinal.model.SearchResult;
import com.example.dsfinal.provider.SearchProvider;
import com.example.dsfinal.scoring.MyScorer;

class SearchServiceTest {

    /** 假的搜尋來源，不用連網路 */
    record FakeProvider(String name, List<SearchResult> results) implements SearchProvider {
        public boolean isEnabled() { return true; }
        public List<SearchResult> search(String query) { return results; }
    }

    record FailingProvider(String name) implements SearchProvider {
        public boolean isEnabled() { return true; }
        public List<SearchResult> search(String query) { throw new IllegalStateException("壞掉了"); }
    }

    @Test
    void mergesSameUrlAndSortsByScore() {
        SearchProvider a = new FakeProvider("a", List.of(
                SearchResult.of("無關", "https://x.com/1", "", "a", 1),
                SearchResult.of("Java 教學", "https://www.y.com/", "Java", "a", 2)));
        SearchProvider b = new FakeProvider("b", List.of(
                SearchResult.of("Java", "http://y.com", "", "b", 1)));

        SearchService service = new SearchService(List.of(a, b, new FailingProvider("c")),
                Map.of("my", new MyScorer()), "my");
        SearchResponse res = service.search("java");

        assertEquals(2, res.results().size());
        SearchResult top = res.results().get(0);
        assertEquals("https://www.y.com/", top.url());
        assertEquals(List.of("a", "b"), top.sources());
        assertEquals(1, top.rank());
        assertEquals(List.of("c：壞掉了"), res.warnings());
    }
}
