package org.example.service;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.example.aiops.ReplanDecision;
import org.example.aiops.ReplanDecisionEvaluator;
import org.example.aiops.ReplanReason;
import org.example.trace.TraceContext;
import org.example.trace.TraceContextHolder;
import org.example.trace.model.AgentSessionTrace;
import org.example.trace.service.AgentTraceService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiOpsServiceTraceTest {

    @Test
    void startAiOpsTraceSessionDelegatesToTraceService() {
        AiOpsService service = new AiOpsService();
        AgentTraceService traceService = mock(AgentTraceService.class);
        ReflectionTestUtils.setField(service, "agentTraceService", traceService);

        service.startAiOpsTraceSession("trace-1", "session-1", "task prompt");

        verify(traceService).startSession("trace-1", "session-1", "AIOPS_AGENT", "task prompt");
        verify(traceService).recordStep("trace-1", "session-1", "supervisor_agent", "AI Ops trace session started", "RUNNING");
    }

    @Test
    void evaluateToolDecisionDelegatesToEvaluator() {
        AiOpsService service = new AiOpsService();
        ReflectionTestUtils.setField(service, "replanDecisionEvaluator", new ReplanDecisionEvaluator());

        ReplanDecision decision = service.evaluateToolDecision(
                "queryLogs",
                null,
                "connection refused",
                2
        );

        assertThat(decision.action()).isEqualTo(ReplanDecision.Action.REPLAN);
    }

    @Test
    void recordWorkflowReplanDelegatesToTraceService() {
        AiOpsService service = new AiOpsService();
        AgentTraceService traceService = mock(AgentTraceService.class);
        ReflectionTestUtils.setField(service, "agentTraceService", traceService);

        service.recordWorkflowReplan(
                "trace-1",
                "session-1",
                "planner_agent",
                ReplanReason.EMPTY_RESULT,
                "planner final report missing"
        );

        verify(traceService).recordReplan(
                "trace-1",
                "session-1",
                "planner_agent",
                "EMPTY_RESULT",
                "planner final report missing"
        );
    }

    @Test
    void createReplanChildContextCompletesParentAndStartsChildSession() {
        AiOpsService service = new AiOpsService();
        AgentTraceService traceService = mock(AgentTraceService.class);
        ReflectionTestUtils.setField(service, "agentTraceService", traceService);

        AgentSessionTrace child = new AgentSessionTrace();
        child.setTraceId("trace-child");
        child.setSessionId("session-child");
        when(traceService.startChildSessionFromParent(
                "trace-parent",
                "AIOPS_AGENT",
                "REPLAN",
                "planner final report missing"
        )).thenReturn(child);

        TraceContext childContext = service.createReplanChildContext(
                new TraceContext("trace-parent", "session-parent", "req-1", "user-1"),
                "planner_agent",
                ReplanReason.EMPTY_RESULT,
                "planner final report missing"
        );

        verify(traceService).recordReplan(
                "trace-parent",
                "session-parent",
                "planner_agent",
                "EMPTY_RESULT",
                "planner final report missing"
        );
        verify(traceService).completeSession(
                "trace-parent",
                null,
                "REPLAN",
                "planner final report missing"
        );
        assertThat(childContext.traceId()).isEqualTo("trace-child");
        assertThat(childContext.sessionId()).isEqualTo("session-child");
        assertThat(childContext.requestId()).isEqualTo("req-1");
    }

    @Test
    void executeAiOpsWorkflowReplansEmptyFirstAttemptIntoChildTrace() {
        TestableAiOpsService service = new TestableAiOpsService();
        TraceContext parent = new TraceContext("trace-parent", "session-parent", "req-1", "user-1");
        TraceContext child = new TraceContext("trace-child", "session-child", "req-1", "user-1");
        service.nextChildContext = child;
        service.attemptResults.add(Optional.empty());
        service.attemptResults.add(Optional.of(mock(OverAllState.class)));
        service.reportResults.add(Optional.of("child final report"));

        AiOpsService.AiOpsWorkflowOutcome outcome = service.executeAiOpsWorkflow(null, new org.springframework.ai.tool.ToolCallback[0], parent);

        assertThat(outcome.replanned()).isTrue();
        assertThat(outcome.traceContext().traceId()).isEqualTo("trace-child");
        assertThat(outcome.finalReport()).contains("child final report");
        assertThat(service.traceIdsSeen).containsExactly("trace-parent", "trace-child");
    }

    private static class TestableAiOpsService extends AiOpsService {
        private final List<Optional<OverAllState>> attemptResults = new ArrayList<>();
        private final List<Optional<String>> reportResults = new ArrayList<>();
        private final List<String> traceIdsSeen = new ArrayList<>();
        private TraceContext nextChildContext;

        @Override
        Optional<OverAllState> runWorkflowAttempt(
                com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel chatModel,
                org.springframework.ai.tool.ToolCallback[] toolCallbacks,
                TraceContext traceContext) {
            traceIdsSeen.add(traceContext.traceId());
            TraceContextHolder.set(traceContext);
            return attemptResults.remove(0);
        }

        @Override
        public Optional<String> extractFinalReport(OverAllState state) {
            return reportResults.remove(0);
        }

        @Override
        TraceContext createReplanChildContext(
                TraceContext parentContext,
                String triggerStep,
                ReplanReason reason,
                String summary) {
            return nextChildContext;
        }
    }
}
