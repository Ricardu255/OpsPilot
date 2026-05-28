package org.example.aiops;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReplanDecisionEvaluatorTest {

    @Test
    void returnsRetryWhenToolFailsWithinRetryBudget() {
        ReplanDecisionEvaluator evaluator = new ReplanDecisionEvaluator();

        ReplanDecision decision = evaluator.evaluateToolResult(
                "queryPrometheusAlerts",
                null,
                "timeout",
                1
        );

        assertThat(decision.action()).isEqualTo(ReplanDecision.Action.RETRY);
        assertThat(decision.reason()).isEqualTo(ReplanReason.TOOL_FAILED);
    }

    @Test
    void returnsReplanWhenToolFailsRepeatedly() {
        ReplanDecisionEvaluator evaluator = new ReplanDecisionEvaluator();

        ReplanDecision decision = evaluator.evaluateToolResult(
                "queryLogs",
                null,
                "connection refused",
                2
        );

        assertThat(decision.action()).isEqualTo(ReplanDecision.Action.REPLAN);
        assertThat(decision.reason()).isEqualTo(ReplanReason.TOOL_FAILED);
    }

    @Test
    void returnsReplanWhenToolReturnsEmptyPayload() {
        ReplanDecisionEvaluator evaluator = new ReplanDecisionEvaluator();

        ReplanDecision decision = evaluator.evaluateToolResult(
                "queryInternalDocs",
                "   ",
                null,
                0
        );

        assertThat(decision.action()).isEqualTo(ReplanDecision.Action.REPLAN);
        assertThat(decision.reason()).isEqualTo(ReplanReason.EMPTY_RESULT);
    }
}
