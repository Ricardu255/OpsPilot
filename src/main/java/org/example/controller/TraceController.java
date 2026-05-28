package org.example.controller;

import org.example.trace.dto.TraceDetailResponse;
import org.example.trace.dto.TraceSessionSummaryResponse;
import org.example.trace.dto.TraceStatsResponse;
import org.example.trace.service.TraceQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/trace")
public class TraceController {

    private static final Logger logger = LoggerFactory.getLogger(TraceController.class);

    private final TraceQueryService traceQueryService;

    public TraceController(TraceQueryService traceQueryService) {
        this.traceQueryService = traceQueryService;
    }

    @GetMapping("/{traceId}")
    public ResponseEntity<?> getTraceDetail(@PathVariable String traceId) {
        try {
            logger.info("Query trace detail by traceId: {}", traceId);
            TraceDetailResponse detail = traceQueryService.getTraceDetail(traceId);
            return ResponseEntity.ok(success(detail));
        } catch (IllegalArgumentException e) {
            logger.warn("Trace not found: {}", e.getMessage());
            return ResponseEntity.ok(error(404, e.getMessage()));
        } catch (Exception e) {
            logger.error("Failed to query trace detail", e);
            return ResponseEntity.ok(error(500, e.getMessage()));
        }
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<?> getBySessionId(@PathVariable String sessionId) {
        try {
            logger.info("Query trace detail by sessionId: {}", sessionId);
            TraceDetailResponse detail = traceQueryService.getTraceDetailBySessionId(sessionId);
            return ResponseEntity.ok(success(detail));
        } catch (IllegalArgumentException e) {
            logger.warn("Session not found: {}", e.getMessage());
            return ResponseEntity.ok(error(404, e.getMessage()));
        } catch (Exception e) {
            logger.error("Failed to query trace detail by sessionId", e);
            return ResponseEntity.ok(error(500, e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> listTraces(
            @RequestParam(required = false) String agentType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            logger.info("List traces with filters agentType={}, status={}, page={}, size={}",
                    agentType, status, page, size);
            Page<TraceSessionSummaryResponse> traces = traceQueryService.listTraces(
                    agentType, status, startFrom, startTo, page, size);

            Map<String, Object> body = success(traces.getContent());
            body.put("total", traces.getTotalElements());
            body.put("page", traces.getNumber());
            body.put("size", traces.getSize());
            body.put("totalPages", traces.getTotalPages());
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            logger.error("Failed to list traces", e);
            return ResponseEntity.ok(error(500, e.getMessage()));
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        try {
            logger.info("Query trace stats");
            TraceStatsResponse stats = traceQueryService.getStats();
            return ResponseEntity.ok(success(stats));
        } catch (Exception e) {
            logger.error("Failed to query trace stats", e);
            return ResponseEntity.ok(error(500, e.getMessage()));
        }
    }

    private Map<String, Object> success(Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 200);
        body.put("message", "success");
        body.put("data", data);
        return body;
    }

    private Map<String, Object> error(int code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        return body;
    }
}
