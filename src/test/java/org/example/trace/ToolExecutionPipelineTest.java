package org.example.trace;

import org.example.reliability.error.AiErrorClassifier;
import org.example.reliability.error.RetryExecutor;
import org.example.tool.pipeline.ToolExecutionPipeline;
import org.example.tool.pipeline.ToolExecutionRequest;
import org.example.tool.pipeline.ToolExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ToolExecutionPipelineTest {

    private ToolExecutionPipeline createPipeline() {
        RetryExecutor retryExecutor = new RetryExecutor();
        ReflectionTestUtils.setField(retryExecutor, "maxRetries", 1);
        return new ToolExecutionPipeline(new AiErrorClassifier(), retryExecutor);
    }

    @Test
    void prepareRejectsBlankToolName() {
        ToolExecutionPipeline pipeline = createPipeline();
        AtomicInteger attempts = new AtomicInteger();

        ToolExecutionResult result = pipeline.execute(new ToolExecutionRequest(
                " ",
                "{}",
                () -> {
                    attempts.incrementAndGet();
                    return "{\"success\":true}";
                }
        ));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCategory()).isEqualTo("NON_RETRYABLE_TOOL_ERROR");
        assertThat(result.errorMessage()).contains("toolName must not be blank");
        assertThat(attempts.get()).isZero();
    }

    @Test
    void executeRetriesRetryableFailureOnce() {
        ToolExecutionPipeline pipeline = createPipeline();
        AtomicInteger attempts = new AtomicInteger();

        ToolExecutionResult result = pipeline.execute(new ToolExecutionRequest(
                "queryLogs",
                "{}",
                () -> {
                    if (attempts.getAndIncrement() == 0) {
                        throw new RuntimeException("connection timeout");
                    }
                    return "{\"success\":true}";
                }
        ));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.retryCount()).isEqualTo(1);
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    void finalizeBuildsFailureResponseWhenExecutionThrows() {
        ToolExecutionPipeline pipeline = createPipeline();

        ToolExecutionResult result = pipeline.execute(new ToolExecutionRequest(
                "queryLogs",
                "{}",
                () -> {
                    throw new IllegalArgumentException("invalid query syntax");
                }
        ));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCategory()).isEqualTo("NON_RETRYABLE_TOOL_ERROR");
        assertThat(result.result()).contains("\"status\":\"error\"");
        assertThat(result.retryCount()).isZero();
    }
}
