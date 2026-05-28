package org.example.tool.pipeline;

public record ToolExecutionResult(
        String result,
        String status,
        String errorMessage,
        String errorCategory,
        int retryCount) {
}
