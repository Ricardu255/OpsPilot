package org.example.reliability.error;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class AiErrorClassifier {

    public AiErrorClassification classifyException(Throwable error) {
        String message = error == null ? "" : String.valueOf(error.getMessage()).toLowerCase(Locale.ROOT);
        if (message.contains("timeout")
                || message.contains("timed out")
                || message.contains("connection refused")
                || message.contains("connection reset")
                || message.contains("too many requests")
                || message.contains("429")
                || message.contains("502")
                || message.contains("503")
                || message.contains("504")
                || message.contains("temporarily")) {
            return new AiErrorClassification(AiErrorCategory.RETRYABLE_TOOL_ERROR, true);
        }
        return new AiErrorClassification(AiErrorCategory.NON_RETRYABLE_TOOL_ERROR, false);
    }

    public AiErrorClassification classifyResult(String result) {
        if (result == null || result.isBlank()) {
            return new AiErrorClassification(AiErrorCategory.EMPTY_RESULT, false);
        }
        return null;
    }
}
