package org.example.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class VectorIndexServiceTest {

    @Test
    void skipsDirectoryIndexingWhenMilvusDisabled() {
        VectorIndexService service = new VectorIndexService();
        ReflectionTestUtils.setField(service, "milvusEnabled", false);

        VectorIndexService.IndexingResult result = service.indexDirectory("uploads");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getErrorMessage()).isEqualTo("Milvus disabled, directory indexing skipped");
    }

    @Test
    void skipsSingleFileIndexingWhenMilvusDisabled() {
        VectorIndexService service = new VectorIndexService();
        ReflectionTestUtils.setField(service, "milvusEnabled", false);

        assertThatCode(() -> service.indexSingleFile("missing-file.md"))
                .doesNotThrowAnyException();
    }
}
