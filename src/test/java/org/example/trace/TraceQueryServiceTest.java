package org.example.trace;

import org.example.trace.dto.TraceDetailResponse;
import org.example.trace.model.AgentReplanRecord;
import org.example.trace.model.AgentSessionTrace;
import org.example.trace.model.AgentToolCallTrace;
import org.example.trace.model.AgentTraceStep;
import org.example.trace.repository.AgentReplanRecordRepository;
import org.example.trace.repository.AgentSessionTraceRepository;
import org.example.trace.repository.AgentToolCallTraceRepository;
import org.example.trace.repository.AgentTraceStepRepository;
import org.example.trace.service.TraceQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TraceQueryServiceTest {

    @Test
    void getTraceDetailBySessionIdReturnsFullTraceChain() {
        AgentSessionTraceRepository sessionRepository = mock(AgentSessionTraceRepository.class);
        AgentTraceStepRepository stepRepository = mock(AgentTraceStepRepository.class);
        AgentToolCallTraceRepository toolCallRepository = mock(AgentToolCallTraceRepository.class);
        AgentReplanRecordRepository replanRepository = mock(AgentReplanRecordRepository.class);
        TraceQueryService service = new TraceQueryService(
                sessionRepository,
                stepRepository,
                toolCallRepository,
                replanRepository
        );
        ReflectionTestUtils.setField(service, "promptMaxLength", 500);
        ReflectionTestUtils.setField(service, "resultMaxLength", 1000);

        AgentSessionTrace session = new AgentSessionTrace();
        session.setTraceId("trace-1");
        session.setSessionId("session-1");
        session.setAgentType("AIOPS_AGENT");
        session.setUserInput("alert body");
        session.setStatus("SUCCESS");
        session.setRootTraceId("trace-1");
        session.setBranchType("MAIN");
        session.setStartTime(LocalDateTime.now().minusMinutes(2));
        session.setEndTime(LocalDateTime.now());

        AgentTraceStep step = new AgentTraceStep();
        step.setTraceId("trace-1");
        step.setSessionId("session-1");
        step.setStepName("planner_agent");
        step.setStepContent("planner content");
        step.setStatus("SUCCESS");
        step.setStartTime(LocalDateTime.now().minusMinutes(2));

        AgentToolCallTrace toolCall = new AgentToolCallTrace();
        toolCall.setTraceId("trace-1");
        toolCall.setSessionId("session-1");
        toolCall.setToolName("queryLogs");
        toolCall.setToolParams("{\"topic\":\"application-logs\"}");
        toolCall.setToolResult("{\"success\":true}");
        toolCall.setStatus("SUCCESS");
        toolCall.setStartedAt(LocalDateTime.now().minusMinutes(1));
        toolCall.setFinishedAt(LocalDateTime.now().minusMinutes(1).plusSeconds(1));
        toolCall.setCostMs(1000L);

        AgentReplanRecord replanRecord = new AgentReplanRecord();
        replanRecord.setTraceId("trace-1");
        replanRecord.setSessionId("session-1");
        replanRecord.setTriggerStep("executor_agent");
        replanRecord.setReason("TOOL_FAILED");
        replanRecord.setSummary("retry with another tool");

        AgentSessionTrace childSession = new AgentSessionTrace();
        childSession.setTraceId("trace-1-replan");
        childSession.setSessionId("session-1-replan");
        childSession.setAgentType("AIOPS_AGENT");
        childSession.setUserInput("child trace");
        childSession.setStatus("RUNNING");
        childSession.setRootTraceId("trace-1");
        childSession.setParentTraceId("trace-1");
        childSession.setBranchType("REPLAN");
        childSession.setBranchLabel("queryLogs failed");
        childSession.setStartTime(LocalDateTime.now().minusMinutes(1));

        when(sessionRepository.findBySessionId("session-1")).thenReturn(Optional.of(session));
        when(stepRepository.findByTraceIdOrderByStartTimeAsc("trace-1")).thenReturn(List.of(step));
        when(toolCallRepository.findByTraceIdOrderByStartedAtAsc("trace-1")).thenReturn(List.of(toolCall));
        when(replanRepository.findByTraceIdOrderByCreatedAtAsc("trace-1")).thenReturn(List.of(replanRecord));
        when(sessionRepository.findByParentTraceIdOrderByStartTimeAsc("trace-1")).thenReturn(List.of(childSession));

        TraceDetailResponse detail = service.getTraceDetailBySessionId("session-1");

        assertThat(detail.getTraceId()).isEqualTo("trace-1");
        assertThat(detail.getSession().getSessionId()).isEqualTo("session-1");
        assertThat(detail.getSession().getRootTraceId()).isEqualTo("trace-1");
        assertThat(detail.getSession().getBranchType()).isEqualTo("MAIN");
        assertThat(detail.getSteps()).hasSize(1);
        assertThat(detail.getToolCalls()).hasSize(1);
        assertThat(detail.getReplans()).hasSize(1);
        assertThat(detail.getChildTraces()).hasSize(1);
        assertThat(detail.getChildTraces().get(0).getParentTraceId()).isEqualTo("trace-1");
    }

    @Test
    void getTraceDetailBySessionIdMasksSensitiveToolParams() {
        AgentSessionTraceRepository sessionRepository = mock(AgentSessionTraceRepository.class);
        AgentTraceStepRepository stepRepository = mock(AgentTraceStepRepository.class);
        AgentToolCallTraceRepository toolCallRepository = mock(AgentToolCallTraceRepository.class);
        AgentReplanRecordRepository replanRepository = mock(AgentReplanRecordRepository.class);
        TraceQueryService service = new TraceQueryService(
                sessionRepository,
                stepRepository,
                toolCallRepository,
                replanRepository
        );
        ReflectionTestUtils.setField(service, "promptMaxLength", 500);
        ReflectionTestUtils.setField(service, "resultMaxLength", 1000);

        AgentSessionTrace session = new AgentSessionTrace();
        session.setTraceId("trace-2");
        session.setSessionId("session-2");
        session.setAgentType("AIOPS_AGENT");
        session.setUserInput("alert body");
        session.setStatus("SUCCESS");
        session.setRootTraceId("trace-2");
        session.setBranchType("MAIN");
        session.setStartTime(LocalDateTime.now().minusMinutes(2));

        AgentToolCallTrace toolCall = new AgentToolCallTrace();
        toolCall.setTraceId("trace-2");
        toolCall.setSessionId("session-2");
        toolCall.setToolName("queryLogs");
        toolCall.setToolParams("{\"password\":\"super-secret\",\"apiKey\":\"sk-123456\",\"nested\":{\"token\":\"abc123\"}}");
        toolCall.setToolResult("{\"success\":true}");
        toolCall.setStatus("SUCCESS");
        toolCall.setStartedAt(LocalDateTime.now().minusMinutes(1));
        toolCall.setFinishedAt(LocalDateTime.now().minusMinutes(1).plusSeconds(1));
        toolCall.setCostMs(1000L);

        when(sessionRepository.findBySessionId("session-2")).thenReturn(Optional.of(session));
        when(stepRepository.findByTraceIdOrderByStartTimeAsc("trace-2")).thenReturn(List.of());
        when(toolCallRepository.findByTraceIdOrderByStartedAtAsc("trace-2")).thenReturn(List.of(toolCall));
        when(replanRepository.findByTraceIdOrderByCreatedAtAsc("trace-2")).thenReturn(List.of());
        when(sessionRepository.findByParentTraceIdOrderByStartTimeAsc("trace-2")).thenReturn(List.of());

        TraceDetailResponse detail = service.getTraceDetailBySessionId("session-2");

        assertThat(detail.getToolCalls()).hasSize(1);
        assertThat(detail.getToolCalls().get(0).getToolParams()).doesNotContain("super-secret", "sk-123456", "abc123");
        assertThat(detail.getToolCalls().get(0).getToolParams()).contains("***");
    }
}
