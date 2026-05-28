package org.example.tool.pipeline;

import java.util.function.Supplier;

public record ToolExecutionRequest(
        String toolName,
        String params,
        Supplier<String> action) {
}
