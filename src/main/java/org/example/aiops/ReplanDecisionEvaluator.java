package org.example.aiops;

import org.springframework.stereotype.Component;

@Component
public class ReplanDecisionEvaluator {

    public ReplanDecision evaluateToolResult(
            String toolName,
            String result,
            String errorMessage,
            int failureCount
    ) {
        if (errorMessage != null && !errorMessage.isBlank()) {
            if (failureCount >= 2) {
                return ReplanDecision.replan(
                        ReplanReason.TOOL_FAILED,
                        toolName + " failed repeatedly, switch strategy"
                );
            }
            return ReplanDecision.retry(
                    ReplanReason.TOOL_FAILED,
                    toolName + " failed once, retry with the same step"
            );
        }

        if (result == null || result.isBlank()) {
            return ReplanDecision.replan(
                    ReplanReason.EMPTY_RESULT,
                    toolName + " returned an empty payload"
            );
        }

        return ReplanDecision.continueExecution();
    }
}
