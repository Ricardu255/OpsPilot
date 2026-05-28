package org.example.trace;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class TraceInterceptorTest {

    @AfterEach
    void tearDown() {
        TraceContextHolder.clear();
    }

    @Test
    void preHandleCreatesRequestScopedTraceContext() throws Exception {
        TraceInterceptor interceptor = new TraceInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Session-Id", "session-100");
        request.addHeader("X-User-Id", "user-9");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertThat(proceed).isTrue();
        assertThat(response.getHeader("X-Request-ID")).isNotBlank();

        TraceContext context = TraceContextHolder.get();
        assertThat(context).isNotNull();
        assertThat(context.sessionId()).isEqualTo("session-100");
        assertThat(context.userId()).isEqualTo("user-9");
        assertThat(context.requestId()).isEqualTo(response.getHeader("X-Request-ID"));
        assertThat(context.traceId()).isNotBlank();
    }

    @Test
    void afterCompletionClearsTraceContext() throws Exception {
        TraceInterceptor interceptor = new TraceInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest();
        HttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(TraceContextHolder.get()).isNull();
    }
}
