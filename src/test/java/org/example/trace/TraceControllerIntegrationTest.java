package org.example.trace;

import io.milvus.client.MilvusServiceClient;
import org.example.trace.model.AgentReplanRecord;
import org.example.trace.model.AgentSessionTrace;
import org.example.trace.model.AgentToolCallTrace;
import org.example.trace.model.AgentTraceStep;
import org.example.trace.repository.AgentReplanRecordRepository;
import org.example.trace.repository.AgentSessionTraceRepository;
import org.example.trace.repository.AgentToolCallTraceRepository;
import org.example.trace.repository.AgentTraceStepRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:trace-it;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
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
class TraceControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AgentSessionTraceRepository sessionRepository;

    @Autowired
    private AgentTraceStepRepository stepRepository;

    @Autowired
    private AgentToolCallTraceRepository toolCallRepository;

    @Autowired
    private AgentReplanRecordRepository replanRepository;

    @MockBean
    private MilvusServiceClient milvusServiceClient;

    @BeforeEach
    void setUp() {
        replanRepository.deleteAll();
        toolCallRepository.deleteAll();
        stepRepository.deleteAll();
        sessionRepository.deleteAll();

        AgentSessionTrace successSession = new AgentSessionTrace();
        successSession.setTraceId("trace-1");
        successSession.setSessionId("session-1");
        successSession.setAgentType("AIOPS_AGENT");
        successSession.setUserInput("alert body");
        successSession.setFinalAnswer("resolved");
        successSession.setStatus("SUCCESS");
        successSession.setRootTraceId("trace-1");
        successSession.setBranchType("MAIN");
        successSession.setStartTime(LocalDateTime.now().minusMinutes(5));
        successSession.setEndTime(LocalDateTime.now().minusMinutes(4));
        successSession.setCostMs(60000L);
        sessionRepository.save(successSession);

        AgentTraceStep step = new AgentTraceStep();
        step.setTraceId("trace-1");
        step.setSessionId("session-1");
        step.setStepName("planner_agent");
        step.setStepContent("plan trace");
        step.setStepOutput("step output");
        step.setStatus("SUCCESS");
        step.setStartTime(LocalDateTime.now().minusMinutes(5));
        step.setEndTime(LocalDateTime.now().minusMinutes(5).plusSeconds(3));
        step.setCostMs(3000L);
        stepRepository.save(step);

        AgentToolCallTrace toolCall = new AgentToolCallTrace();
        toolCall.setTraceId("trace-1");
        toolCall.setSessionId("session-1");
        toolCall.setToolName("queryLogs");
        toolCall.setToolParams("{\"password\":\"super-secret\",\"apiKey\":\"sk-123456\",\"topic\":\"application-logs\"}");
        toolCall.setToolResult("{\"success\":true}");
        toolCall.setStatus("SUCCESS");
        toolCall.setStartedAt(LocalDateTime.now().minusMinutes(5));
        toolCall.setFinishedAt(LocalDateTime.now().minusMinutes(5).plusSeconds(1));
        toolCall.setCostMs(1000L);
        toolCallRepository.save(toolCall);

        AgentReplanRecord replan = new AgentReplanRecord();
        replan.setTraceId("trace-1");
        replan.setSessionId("session-1");
        replan.setTriggerStep("queryLogs");
        replan.setReason("TOOL_FAILED");
        replan.setSummary("queryLogs failed repeatedly, switch strategy");
        replanRepository.save(replan);

        AgentSessionTrace failedSession = new AgentSessionTrace();
        failedSession.setTraceId("trace-2");
        failedSession.setSessionId("session-2");
        failedSession.setAgentType("AIOPS_AGENT");
        failedSession.setUserInput("second alert");
        failedSession.setStatus("FAILED");
        failedSession.setErrorMessage("final report missing");
        failedSession.setRootTraceId("trace-1");
        failedSession.setParentTraceId("trace-1");
        failedSession.setBranchType("REPLAN");
        failedSession.setBranchLabel("planner final report missing");
        failedSession.setStartTime(LocalDateTime.now().minusMinutes(2));
        failedSession.setEndTime(LocalDateTime.now().minusMinutes(1));
        failedSession.setCostMs(45000L);
        sessionRepository.save(failedSession);
    }

    @Test
    void getTraceDetailBySessionIdReturnsFullTraceAndMaskedParams() throws Exception {
        mockMvc.perform(get("/api/trace/session/session-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.trace_id").value("trace-1"))
                .andExpect(jsonPath("$.data.session.session_id").value("session-1"))
                .andExpect(jsonPath("$.data.session.root_trace_id").value("trace-1"))
                .andExpect(jsonPath("$.data.session.branch_type").value("MAIN"))
                .andExpect(jsonPath("$.data.steps", hasSize(1)))
                .andExpect(jsonPath("$.data.tool_calls", hasSize(1)))
                .andExpect(jsonPath("$.data.replans", hasSize(1)))
                .andExpect(jsonPath("$.data.child_traces", hasSize(1)))
                .andExpect(jsonPath("$.data.child_traces[0].trace_id").value("trace-2"))
                .andExpect(jsonPath("$.data.tool_calls[0].tool_params", containsString("***")))
                .andExpect(jsonPath("$.data.tool_calls[0].tool_params", not(containsString("super-secret"))))
                .andExpect(jsonPath("$.data.tool_calls[0].tool_params", not(containsString("sk-123456"))));
    }

    @Test
    void listTracesSupportsStatusFilter() throws Exception {
        mockMvc.perform(get("/api/trace").param("status", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].trace_id").value("trace-2"))
                .andExpect(jsonPath("$.data[0].status").value("FAILED"));
    }

    @Test
    void getStatsAggregatesTraceAndToolMetrics() throws Exception {
        mockMvc.perform(get("/api/trace/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total_sessions").value(2))
                .andExpect(jsonPath("$.data.success_count").value(1))
                .andExpect(jsonPath("$.data.failed_count").value(1))
                .andExpect(jsonPath("$.data.total_tool_calls").value(1))
                .andExpect(jsonPath("$.data.failed_tool_calls").value(0))
                .andExpect(jsonPath("$.data.recent_failures", hasSize(1)));
    }
}
