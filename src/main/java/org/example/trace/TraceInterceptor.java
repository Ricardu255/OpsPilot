package org.example.trace;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

@Component
public class TraceInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String requestId = headerOrRandom(request.getHeader("X-Request-ID"));
        String traceId = headerOrRandom(request.getHeader("X-Trace-ID"));
        String sessionId = blankToNull(request.getHeader("X-Session-Id"));
        String userId = blankToNull(request.getHeader("X-User-Id"));

        TraceContextHolder.set(new TraceContext(traceId, sessionId, requestId, userId));
        response.setHeader("X-Request-ID", requestId);
        response.setHeader("X-Trace-ID", traceId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TraceContextHolder.clear();
    }

    private String headerOrRandom(String headerValue) {
        return headerValue == null || headerValue.isBlank()
                ? UUID.randomUUID().toString()
                : headerValue;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
