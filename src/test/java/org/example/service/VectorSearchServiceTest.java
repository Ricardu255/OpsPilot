package org.example.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VectorSearchServiceTest {

    @Test
    void returnsEmptyResultsWhenMilvusDisabled() {
        VectorSearchService service = new VectorSearchService();
        ReflectionTestUtils.setField(service, "milvusEnabled", false);

        List<VectorSearchService.SearchResult> results = service.searchSimilarDocuments("deployment timeout", 3);

        assertThat(results).isEmpty();
    }
}
