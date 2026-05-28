package org.example.controller;

import org.example.trace.dto.TraceDetailResponse;
import org.example.trace.dto.TraceSessionSummaryResponse;
import org.example.trace.service.TraceQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TraceControllerTest {

    @Test
    void getBySessionIdReturnsTraceDetailPayload() {
        TraceQueryService traceQueryService = mock(TraceQueryService.class);
        TraceController controller = new TraceController(traceQueryService);

        TraceSessionSummaryResponse session = new TraceSessionSummaryResponse();
        session.setSessionId("session-1");
        TraceDetailResponse detail = new TraceDetailResponse();
        detail.setTraceId("trace-1");
        detail.setSession(session);

        when(traceQueryService.getTraceDetailBySessionId("session-1")).thenReturn(detail);

        ResponseEntity<?> response = controller.getBySessionId("session-1");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isInstanceOf(Map.class);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("code")).isEqualTo(200);
        assertThat(body.get("data")).isEqualTo(detail);
        verify(traceQueryService).getTraceDetailBySessionId("session-1");
    }
}
