package org.example.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "milvus.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:milvus-disabled-it;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.ai.dashscope.api-key=test-key",
        "dashscope.api.key=test-key",
        "prometheus.mock-enabled=true",
        "cls.mock-enabled=true"
})
class MilvusDisabledIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointReportsMilvusDisabled() throws Exception {
        mockMvc.perform(get("/milvus/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("disabled"))
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void traceConsolePageLoadsWithoutMilvus() throws Exception {
        mockMvc.perform(get("/trace.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Trace 控制台")));
    }

    @Test
    void traceApiStatsLoadsWithoutMilvus() throws Exception {
        mockMvc.perform(get("/api/trace/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
