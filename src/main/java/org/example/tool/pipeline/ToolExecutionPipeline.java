package org.example.tool.pipeline;

import org.example.common.StringUtils;
import org.example.reliability.error.AiErrorClassification;
import org.example.reliability.error.AiErrorClassifier;
import org.example.reliability.error.RetryExecutor;
import org.springframework.stereotype.Component;

@Component
public class ToolExecutionPipeline {

    private static final int MAX_ERROR_LENGTH = 500;

    private final AiErrorClassifier errorClassifier;
    private final RetryExecutor retryExecutor;

    public ToolExecutionPipeline(AiErrorClassifier errorClassifier, RetryExecutor retryExecutor) {
        this.errorClassifier = errorClassifier;
        this.retryExecutor = retryExecutor;
    }

    public ToolExecutionResult execute(ToolExecutionRequest request) {
        try {
            prepare(request);
            return executeCore(request);
        } catch (RuntimeException ex) {
            return finalizeFailure(request.toolName(), ex, 0, errorClassifier.classifyException(ex));
        }
    }

    private void prepare(ToolExecutionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (request.toolName() == null || request.toolName().isBlank()) {
            throw new IllegalArgumentException("toolName must not be blank");
        }
        if (request.action() == null) {
            throw new IllegalArgumentException("tool action must not be null");
        }
    }

    private ToolExecutionResult executeCore(ToolExecutionRequest request) {
        RetryExecutor.RetryResult<String> execution = retryExecutor.execute(
                request.action(),
                errorClassifier::classifyException
        );

        if (execution.lastError() != null) {
            return finalizeFailure(
                    request.toolName(),
                    execution.lastError(),
                    execution.retryCount(),
                    execution.classification()
            );
        }

        return finalizeSuccess(execution.result(), execution.retryCount());
    }

    private ToolExecutionResult finalizeSuccess(String result, int retryCount) {
        AiErrorClassification resultClassification = errorClassifier.classifyResult(result);
        if (result == null || result.isBlank() || isEmptyResult(result)) {
            return new ToolExecutionResult(
                    result,
                    "EMPTY",
                    null,
                    resultClassification == null ? null : resultClassification.category().name(),
                    retryCount
            );
        }
        return new ToolExecutionResult(result, "SUCCESS", null, null, retryCount);
    }

    private ToolExecutionResult finalizeFailure(
            String toolName,
            RuntimeException ex,
            int retryCount,
            AiErrorClassification classification) {
        String errorMessage = StringUtils.truncate(ex.getMessage(), MAX_ERROR_LENGTH);
        String category = classification == null ? null : classification.category().name();
        String result = String.format(
                "{\"status\":\"error\",\"tool\":\"%s\",\"message\":\"%s\"}",
                toolName,
                errorMessage == null ? "unknown" : errorMessage.replace("\"", "'")
        );
        return new ToolExecutionResult(result, "FAILED", errorMessage, category, retryCount);
    }

    private boolean isEmptyResult(String result) {
        String lower = result.toLowerCase();
        return lower.contains("\"no_results\"") || lower.contains("\"success\":false");
    }

}
