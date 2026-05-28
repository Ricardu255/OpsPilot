package org.example.trace;

public record TraceContext(
        String traceId,
        String sessionId,
        String requestId,
        String userId
) {
}
