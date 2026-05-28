package org.example.trace;

import org.example.trace.model.AgentSessionTrace;
import org.example.trace.model.AgentToolCallTrace;
import org.example.trace.model.AgentTraceStep;
import org.example.trace.repository.AgentReplanRecordRepository;
import org.example.trace.repository.AgentSessionTraceRepository;
import org.example.trace.repository.AgentToolCallTraceRepository;
import org.example.trace.repository.AgentTraceStepRepository;
import org.example.trace.service.AgentTraceService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTraceServiceTest {

    @Test
    void startSessionCreatesRunningTraceRecord() {
        AgentSessionTraceRepository sessionRepository = mock(AgentSessionTraceRepository.class);
        AgentTraceStepRepository stepRepository = mock(AgentTraceStepRepository.class);
        AgentToolCallTraceRepository toolCallRepository = mock(AgentToolCallTraceRepository.class);
        AgentReplanRecordRepository replanRecordRepository = mock(AgentReplanRecordRepository.class);
        AgentTraceService service = new AgentTraceService(
                sessionRepository,
                stepRepository,
                toolCallRepository,
                replanRecordRepository
        );

        when(sessionRepository.save(any(AgentSessionTrace.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AgentSessionTrace saved = service.startSession("trace-1", "session-1", "AIOPS_AGENT", "alert body");

        assertThat(saved.getTraceId()).isEqualTo("trace-1");
        assertThat(saved.getSessionId()).isEqualTo("session-1");
        assertThat(saved.getAgentType()).isEqualTo("AIOPS_AGENT");
        assertThat(saved.getStatus()).isEqualTo("RUNNING");
        assertThat(saved.getStartTime()).isNotNull();
    }

    @Test
    void recordStepPersistsStructuredTraceStep() {
        AgentSessionTraceRepository sessionRepository = mock(AgentSessionTraceRepository.class);
        AgentTraceStepRepository stepRepository = mock(AgentTraceStepRepository.class);
        AgentToolCallTraceRepository toolCallRepository = mock(AgentToolCallTraceRepository.class);
        AgentReplanRecordRepository replanRecordRepository = mock(AgentReplanRecordRepository.class);
        AgentTraceService service = new AgentTraceService(
                sessionRepository,
                stepRepository,
                toolCallRepository,
                replanRecordRepository
        );

        when(stepRepository.save(any(AgentTraceStep.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AgentTraceStep saved = service.recordStep(
                "trace-1",
                "session-1",
                "planner_agent",
                "开始规划",
                "RUNNING"
        );

        assertThat(saved.getTraceId()).isEqualTo("trace-1");
        assertThat(saved.getSessionId()).isEqualTo("session-1");
        assertThat(saved.getStepName()).isEqualTo("planner_agent");
        assertThat(saved.getStepContent()).isEqualTo("开始规划");
        assertThat(saved.getStatus()).isEqualTo("RUNNING");
    }

    @Test
    void recordToolCallPersistsTraceSummary() {
        AgentSessionTraceRepository sessionRepository = mock(AgentSessionTraceRepository.class);
        AgentTraceStepRepository stepRepository = mock(AgentTraceStepRepository.class);
        AgentToolCallTraceRepository toolCallRepository = mock(AgentToolCallTraceRepository.class);
        AgentReplanRecordRepository replanRecordRepository = mock(AgentReplanRecordRepository.class);
        AgentTraceService service = new AgentTraceService(
                sessionRepository,
                stepRepository,
                toolCallRepository,
                replanRecordRepository
        );

        when(toolCallRepository.save(any(AgentToolCallTrace.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AgentToolCallTrace saved = service.recordToolCall(
                "trace-1",
                "session-1",
                "queryPrometheusAlerts",
                "{\"scope\":\"alerts\"}",
                "{\"success\":true}",
                "SUCCESS"
        );

        assertThat(saved.getTraceId()).isEqualTo("trace-1");
        assertThat(saved.getSessionId()).isEqualTo("session-1");
        assertThat(saved.getToolName()).isEqualTo("queryPrometheusAlerts");
        assertThat(saved.getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void recordDetailedToolCallPersistsReliabilityMetadata() {
        AgentSessionTraceRepository sessionRepository = mock(AgentSessionTraceRepository.class);
        AgentTraceStepRepository stepRepository = mock(AgentTraceStepRepository.class);
        AgentToolCallTraceRepository toolCallRepository = mock(AgentToolCallTraceRepository.class);
        AgentReplanRecordRepository replanRecordRepository = mock(AgentReplanRecordRepository.class);
        AgentTraceService service = new AgentTraceService(
                sessionRepository,
                stepRepository,
                toolCallRepository,
                replanRecordRepository
        );

        when(toolCallRepository.save(any(AgentToolCallTrace.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AgentToolCallTrace saved = service.recordToolCall(
                "trace-1",
                "session-1",
                "queryLogs",
                "{\"query\":\"error\"}",
                "{\"status\":\"error\"}",
                "FAILED",
                "timeout",
                java.time.LocalDateTime.now().minusSeconds(1),
                java.time.LocalDateTime.now(),
                1000L,
                "RETRYABLE_TOOL_ERROR",
                1
        );

        assertThat(saved.getErrorCategory()).isEqualTo("RETRYABLE_TOOL_ERROR");
        assertThat(saved.getRetryCount()).isEqualTo(1);
    }

    @Test
    void startChildSessionPersistsTraceTreeMetadata() {
        AgentSessionTraceRepository sessionRepository = mock(AgentSessionTraceRepository.class);
        AgentTraceStepRepository stepRepository = mock(AgentTraceStepRepository.class);
        AgentToolCallTraceRepository toolCallRepository = mock(AgentToolCallTraceRepository.class);
        AgentReplanRecordRepository replanRecordRepository = mock(AgentReplanRecordRepository.class);
        AgentTraceService service = new AgentTraceService(
                sessionRepository,
                stepRepository,
                toolCallRepository,
                replanRecordRepository
        );

        when(sessionRepository.save(any(AgentSessionTrace.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AgentSessionTrace saved = service.startSession(
                "trace-child",
                "session-child",
                "AIOPS_AGENT",
                "replan input",
                "trace-root",
                "trace-parent",
                "REPLAN",
                "queryLogs failed"
        );

        assertThat(saved.getRootTraceId()).isEqualTo("trace-root");
        assertThat(saved.getParentTraceId()).isEqualTo("trace-parent");
        assertThat(saved.getBranchType()).isEqualTo("REPLAN");
        assertThat(saved.getBranchLabel()).isEqualTo("queryLogs failed");
    }

    @Test
    void startChildSessionFromParentCopiesParentTraceMetadata() {
        AgentSessionTraceRepository sessionRepository = mock(AgentSessionTraceRepository.class);
        AgentTraceStepRepository stepRepository = mock(AgentTraceStepRepository.class);
        AgentToolCallTraceRepository toolCallRepository = mock(AgentToolCallTraceRepository.class);
        AgentReplanRecordRepository replanRecordRepository = mock(AgentReplanRecordRepository.class);
        AgentTraceService service = new AgentTraceService(
                sessionRepository,
                stepRepository,
                toolCallRepository,
                replanRecordRepository
        );

        AgentSessionTrace parent = new AgentSessionTrace();
        parent.setTraceId("trace-parent");
        parent.setSessionId("session-parent");
        parent.setAgentType("AIOPS_AGENT");
        parent.setUserInput("original prompt");
        parent.setRootTraceId("trace-root");

        when(sessionRepository.findTopByTraceIdOrderByIdDesc("trace-parent"))
                .thenReturn(java.util.Optional.of(parent));
        when(sessionRepository.save(any(AgentSessionTrace.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AgentSessionTrace child = service.startChildSessionFromParent(
                "trace-parent",
                "AIOPS_AGENT",
                "REPLAN",
                "planner final report missing"
        );

        assertThat(child.getTraceId()).isNotBlank();
        assertThat(child.getSessionId()).isNotBlank();
        assertThat(child.getAgentType()).isEqualTo("AIOPS_AGENT");
        assertThat(child.getUserInput()).isEqualTo("original prompt");
        assertThat(child.getRootTraceId()).isEqualTo("trace-root");
        assertThat(child.getParentTraceId()).isEqualTo("trace-parent");
        assertThat(child.getBranchType()).isEqualTo("REPLAN");
        assertThat(child.getBranchLabel()).isEqualTo("planner final report missing");
    }
}
