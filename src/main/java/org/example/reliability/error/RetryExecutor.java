package org.example.reliability.error;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.function.Function;
import java.util.function.Supplier;

@Component
public class RetryExecutor {

    @Value("${agent.tool.retry.max-attempts:1}")
    private int maxRetries;

    public <T> RetryResult<T> execute(
            Supplier<T> action,
            Function<RuntimeException, AiErrorClassification> classifier) {
        int retries = 0;

        while (true) {
            try {
                return new RetryResult<>(action.get(), retries, null, null);
            } catch (RuntimeException ex) {
                AiErrorClassification classification = classifier.apply(ex);
                if (classification == null || !classification.retryable() || retries >= maxRetries) {
                    return new RetryResult<>(null, retries, classification, ex);
                }
                retries++;
            }
        }
    }

    public record RetryResult<T>(
            T result,
            int retryCount,
            AiErrorClassification classification,
            RuntimeException lastError) {
    }
}
