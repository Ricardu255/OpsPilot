package org.example.trace;

import org.example.reliability.error.AiErrorClassification;
import org.example.reliability.error.AiErrorClassifier;
import org.example.reliability.error.RetryExecutor;
import org.example.tool.pipeline.ToolExecutionPipeline;
import org.example.tool.pipeline.ToolExecutionRequest;
import org.example.tool.pipeline.ToolExecutionResult;
import org.example.aiops.ReplanDecision;
import org.example.aiops.ReplanDecisionEvaluator;
import org.example.trace.service.AgentTraceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Component
public class ToolTraceHelper {

    private static final Logger logger = LoggerFactory.getLogger(ToolTraceHelper.class);
    @Value("${agent.trace.max-result-length:2000}")
    private int maxResultLength;

    @Value("${agent.trace.max-error-length:500}")
    private int maxErrorLength;

    private static final String TRUNCATED_SUFFIX = "...[truncated]";

    @Autowired(required = false)
    private AgentTraceService agentTraceService;

    @Autowired(required = false)
    private ReplanDecisionEvaluator replanDecisionEvaluator;

    @Autowired(required = false)
    private AiErrorClassifier errorClassifier = new AiErrorClassifier();

    @Autowired(required = false)
    private RetryExecutor retryExecutor = new RetryExecutor();

    @Autowired(required = false)
    private ToolExecutionPipeline toolExecutionPipeline;

    private final Map<String, Integer> failureCounters = new ConcurrentHashMap<>();

    public String executeAndTrace(String toolName, String params, Supplier<String> toolAction) {
        LocalDateTime startTime = LocalDateTime.now();
        ToolExecutionResult executionResult = executePipeline(toolName, params, toolAction);
        if ("FAILED".equals(executionResult.status())) {
            logger.error("[ToolTraceHelper] Tool {} failed: {}", toolName, executionResult.errorMessage());
        }

        evaluateAndRecordReplan(toolName, executionResult.status(), executionResult.result(), executionResult.errorMessage());
        recordTrace(
                toolName,
                params,
                executionResult.result(),
                executionResult.status(),
                executionResult.errorMessage(),
                startTime,
                executionResult.errorCategory(),
                executionResult.retryCount()
        );
        return executionResult.result();
    }

    public void clearTraceState(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return;
        }
        String prefix = traceId + "::";
        failureCounters.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private ToolExecutionResult executePipeline(String toolName, String params, Supplier<String> toolAction) {
        ToolExecutionPipeline pipeline = toolExecutionPipeline;
        if (pipeline == null) {
            pipeline = new ToolExecutionPipeline(errorClassifier, retryExecutor);
        }
        return pipeline.execute(new ToolExecutionRequest(toolName, params, toolAction));
    }

    private void evaluateAndRecordReplan(String toolName, String status, String result, String errorMessage) {
        if (replanDecisionEvaluator == null || agentTraceService == null) {
            return;
        }

        TraceContext traceContext = TraceContextHolder.get();
        if (!hasPersistableTraceContext(traceContext, "replan trace")) {
            return;
        }

        String failureKey = traceContext.traceId() + "::" + toolName;
        if ("SUCCESS".equals(status)) {
            failureCounters.remove(failureKey);
            return;
        }

        int failureCount = failureCounters.merge(failureKey, 1, Integer::sum);
        String normalizedResult = "EMPTY".equals(status) ? "" : result;
        ReplanDecision decision = replanDecisionEvaluator.evaluateToolResult(
                toolName,
                normalizedResult,
                errorMessage,
                failureCount
        );

        if (decision.action() == ReplanDecision.Action.REPLAN && decision.reason() != null) {
            try {
                agentTraceService.recordReplan(
                        traceContext.traceId(),
                        traceContext.sessionId(),
                        toolName,
                        decision.reason().name(),
                        decision.message()
                );
            } catch (Exception e) {
                logger.warn("[ToolTraceHelper] Failed to record replan trace: {}", e.getMessage());
            }
        }
    }

    private void recordTrace(
            String toolName,
            String params,
            String result,
            String status,
            String errorMessage,
            LocalDateTime startTime,
            String errorCategory,
            int retryCount) {
        if (agentTraceService == null) {
            return;
        }

        TraceContext traceContext = TraceContextHolder.get();
        if (!hasPersistableTraceContext(traceContext, "tool trace")) {
            return;
        }

        try {
            LocalDateTime endTime = LocalDateTime.now();
            long costMs = Duration.between(startTime, endTime).toMillis();
            agentTraceService.recordToolCall(
                    traceContext.traceId(),
                    traceContext.sessionId(),
                    toolName,
                    params,
                    truncate(result, maxResultLength),
                    status,
                    errorMessage,
                    startTime,
                    endTime,
                    costMs,
                    errorCategory,
                    retryCount
            );
        } catch (Exception e) {
            logger.warn("[ToolTraceHelper] Failed to record tool trace: {}", e.getMessage());
        }
    }

    private boolean hasPersistableTraceContext(TraceContext traceContext, String purpose) {
        if (traceContext == null) {
            return false;
        }
        if (traceContext.traceId() == null || traceContext.traceId().isBlank()) {
            logger.warn("[ToolTraceHelper] Skipping {} because traceId is missing", purpose);
            return false;
        }
        if (traceContext.sessionId() == null || traceContext.sessionId().isBlank()) {
            logger.warn("[ToolTraceHelper] Skipping {} because sessionId is missing for traceId={}",
                    purpose,
                    traceContext.traceId());
            return false;
        }
        return true;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + TRUNCATED_SUFFIX;
    }
}
