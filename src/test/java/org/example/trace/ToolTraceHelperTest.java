package org.example.trace;

import org.example.aiops.ReplanDecisionEvaluator;
import org.example.reliability.error.RetryExecutor;
import org.example.trace.service.AgentTraceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ToolTraceHelperTest {

    private ToolTraceHelper createHelper() {
        ToolTraceHelper helper = new ToolTraceHelper();
        RetryExecutor retryExecutor = new RetryExecutor();
        ReflectionTestUtils.setField(retryExecutor, "maxRetries", 1);
        ReflectionTestUtils.setField(helper, "retryExecutor", retryExecutor);
        ReflectionTestUtils.setField(helper, "maxResultLength", 2000);
        ReflectionTestUtils.setField(helper, "maxErrorLength", 500);
        return helper;
    }

    private ReplanDecisionEvaluator createEvaluator() {
        ReplanDecisionEvaluator evaluator = new ReplanDecisionEvaluator();
        ReflectionTestUtils.setField(evaluator, "failureThreshold", 2);
        return evaluator;
    }

    @AfterEach
    void tearDown() {
        TraceContextHolder.clear();
    }

    @Test
    void secondConsecutiveFailureRecordsReplan() {
        ToolTraceHelper helper = createHelper();
        AgentTraceService traceService = mock(AgentTraceService.class);
        ReflectionTestUtils.setField(helper, "agentTraceService", traceService);
        ReflectionTestUtils.setField(helper, "replanDecisionEvaluator", createEvaluator());
        TraceContextHolder.set(new TraceContext("trace-1", "session-1", "request-1", "user-1"));

        helper.executeAndTrace("queryLogs", "{}", () -> {
            throw new RuntimeException("connection refused");
        });
        helper.executeAndTrace("queryLogs", "{}", () -> {
            throw new RuntimeException("connection refused");
        });

        verify(traceService).recordReplan(
                eq("trace-1"),
                eq("session-1"),
                eq("queryLogs"),
                eq("TOOL_FAILED"),
                contains("failed repeatedly")
        );
    }

    @Test
    void successResetsFailureCounterForTool() {
        ToolTraceHelper helper = createHelper();
        AgentTraceService traceService = mock(AgentTraceService.class);
        ReflectionTestUtils.setField(helper, "agentTraceService", traceService);
        ReflectionTestUtils.setField(helper, "replanDecisionEvaluator", createEvaluator());
        TraceContextHolder.set(new TraceContext("trace-2", "session-2", "request-2", "user-2"));

        helper.executeAndTrace("queryLogs", "{}", () -> {
            throw new RuntimeException("timeout");
        });
        helper.executeAndTrace("queryLogs", "{}", () -> "{\"success\":true}");
        helper.executeAndTrace("queryLogs", "{}", () -> {
            throw new RuntimeException("timeout");
        });

        verify(traceService, never()).recordReplan(
                eq("trace-2"),
                eq("session-2"),
                eq("queryLogs"),
                eq("TOOL_FAILED"),
                contains("failed repeatedly")
        );
    }

    @Test
    void retryableFailureRetriesOnceBeforeSuccess() {
        ToolTraceHelper helper = createHelper();
        AgentTraceService traceService = mock(AgentTraceService.class);
        ReflectionTestUtils.setField(helper, "agentTraceService", traceService);
        ReflectionTestUtils.setField(helper, "replanDecisionEvaluator", createEvaluator());
        TraceContextHolder.set(new TraceContext("trace-3", "session-3", "request-3", "user-3"));

        AtomicInteger attempts = new AtomicInteger();

        helper.executeAndTrace("queryLogs", "{}", () -> {
            if (attempts.getAndIncrement() == 0) {
                throw new RuntimeException("connection timeout");
            }
            return "{\"success\":true}";
        });

        verify(traceService, times(1)).recordToolCall(
                eq("trace-3"),
                eq("session-3"),
                eq("queryLogs"),
                eq("{}"),
                eq("{\"success\":true}"),
                eq("SUCCESS"),
                isNull(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong(),
                isNull(),
                eq(1)
        );
    }

    @Test
    void nonRetryableFailureDoesNotRetry() {
        ToolTraceHelper helper = createHelper();
        AgentTraceService traceService = mock(AgentTraceService.class);
        ReflectionTestUtils.setField(helper, "agentTraceService", traceService);
        ReflectionTestUtils.setField(helper, "replanDecisionEvaluator", createEvaluator());
        TraceContextHolder.set(new TraceContext("trace-4", "session-4", "request-4", "user-4"));

        AtomicInteger attempts = new AtomicInteger();

        helper.executeAndTrace("queryLogs", "{}", () -> {
            attempts.incrementAndGet();
            throw new IllegalArgumentException("invalid query syntax");
        });

        verify(traceService, times(1)).recordToolCall(
                eq("trace-4"),
                eq("session-4"),
                eq("queryLogs"),
                eq("{}"),
                contains("\"status\":\"error\""),
                eq("FAILED"),
                eq("invalid query syntax"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong(),
                eq("NON_RETRYABLE_TOOL_ERROR"),
                eq(0)
        );
        verify(traceService, never()).recordReplan(
                eq("trace-4"),
                eq("session-4"),
                eq("queryLogs"),
                eq("TOOL_FAILED"),
                contains("failed repeatedly")
        );
        org.junit.jupiter.api.Assertions.assertEquals(1, attempts.get());
    }

    @Test
    void missingSessionIdSkipsToolTracePersistence() {
        ToolTraceHelper helper = createHelper();
        AgentTraceService traceService = mock(AgentTraceService.class);
        ReflectionTestUtils.setField(helper, "agentTraceService", traceService);
        TraceContextHolder.set(new TraceContext("trace-5", null, "request-5", "user-5"));

        helper.executeAndTrace("queryLogs", "{}", () -> "{\"success\":true}");

        verifyNoInteractions(traceService);
    }
}
