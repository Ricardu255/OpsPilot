package org.example.reliability.error;

public record AiErrorClassification(AiErrorCategory category, boolean retryable) {
}
