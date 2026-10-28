package org.example.trace.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.example.common.StringUtils;
import org.example.trace.dto.TraceDetailResponse;
import org.example.trace.dto.TraceReplanResponse;
import org.example.trace.dto.TraceSessionSummaryResponse;
import org.example.trace.dto.TraceStatsResponse;
import org.example.trace.dto.TraceStepResponse;
import org.example.trace.dto.TraceToolCallResponse;
import org.example.trace.model.AgentReplanRecord;
import org.example.trace.model.AgentSessionTrace;
import org.example.trace.model.AgentToolCallTrace;
import org.example.trace.model.AgentTraceStep;
import org.example.trace.repository.AgentReplanRecordRepository;
import org.example.trace.repository.AgentSessionTraceRepository;
import org.example.trace.repository.AgentToolCallTraceRepository;
import org.example.trace.repository.AgentTraceStepRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class TraceQueryService {

    @Value("${agent.trace.max-prompt-length:500}")
    private int promptMaxLength;

    @Value("${agent.trace.max-result-display-length:1000}")
    private int resultMaxLength;
    private static final String TRUNCATED_SUFFIX = "...[truncated]";
    private static final String MASKED_VALUE = "***";
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password",
            "passwd",
            "secret",
            "token",
            "authorization",
            "apikey",
            "api_key",
            "accesskey",
            "access_key",
            "secretkey",
            "secret_key"
    );

    private final AgentSessionTraceRepository sessionRepository;
    private final AgentTraceStepRepository stepRepository;
    private final AgentToolCallTraceRepository toolCallRepository;
    private final AgentReplanRecordRepository replanRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TraceQueryService(
            AgentSessionTraceRepository sessionRepository,
            AgentTraceStepRepository stepRepository,
            AgentToolCallTraceRepository toolCallRepository,
            AgentReplanRecordRepository replanRepository) {
        this.sessionRepository = sessionRepository;
        this.stepRepository = stepRepository;
        this.toolCallRepository = toolCallRepository;
        this.replanRepository = replanRepository;
    }

    public TraceDetailResponse getTraceDetail(String traceId) {
        AgentSessionTrace session = sessionRepository.findTopByTraceIdOrderByIdDesc(traceId)
                .orElseThrow(() -> new IllegalArgumentException("No trace session found for traceId=" + traceId));
        return buildTraceDetail(traceId, session);
    }

    public TraceSessionSummaryResponse getBySessionId(String sessionId) {
        AgentSessionTrace session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("No trace session found for sessionId=" + sessionId));
        return toSessionSummary(session);
    }

    public TraceDetailResponse getTraceDetailBySessionId(String sessionId) {
        AgentSessionTrace session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("No trace session found for sessionId=" + sessionId));
        return buildTraceDetail(session.getTraceId(), session);
    }

    public Page<TraceSessionSummaryResponse> listTraces(
            String agentType,
            String status,
            LocalDateTime startFrom,
            LocalDateTime startTo,
            int page,
            int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<AgentSessionTrace> sessionPage = sessionRepository.findByFilters(
                agentType, status, startFrom, startTo, pageable);

        return sessionPage.map(this::toSessionSummary);
    }

    public TraceStatsResponse getStats() {
        TraceStatsResponse stats = new TraceStatsResponse();

        stats.setTotalSessions(sessionRepository.count());
        stats.setSuccessCount(sessionRepository.countByStatus("SUCCESS"));
        stats.setFailedCount(sessionRepository.countByStatus("FAILED"));
        stats.setRunningCount(sessionRepository.countByStatus("RUNNING"));
        stats.setAverageCostMs(sessionRepository.averageCostMs());

        stats.setTotalToolCalls(toolCallRepository.count());
        stats.setFailedToolCalls(toolCallRepository.countByStatus("FAILED"));

        stats.setRecentFailures(sessionRepository.findTop10ByStatusOrderByStartTimeDesc("FAILED")
                .stream()
                .map(this::toSessionSummary)
                .toList());

        stats.setSlowestTraces(sessionRepository.findTop10ByOrderByCostMsDesc()
                .stream()
                .map(this::toSessionSummary)
                .toList());

        return stats;
    }

    private TraceDetailResponse buildTraceDetail(String traceId, AgentSessionTrace session) {
        List<AgentTraceStep> steps = stepRepository.findByTraceIdOrderByStartTimeAsc(traceId);
        List<AgentToolCallTrace> toolCalls = toolCallRepository.findByTraceIdOrderByStartedAtAsc(traceId);
        List<AgentReplanRecord> replans = replanRepository.findByTraceIdOrderByCreatedAtAsc(traceId);
        List<AgentSessionTrace> childSessions = sessionRepository.findByParentTraceIdOrderByStartTimeAsc(traceId);

        TraceDetailResponse response = new TraceDetailResponse();
        response.setTraceId(traceId);
        response.setSession(toSessionSummary(session));
        response.setSteps(steps.stream().map(this::toStepResponse).toList());
        response.setToolCalls(toolCalls.stream().map(this::toToolCallResponse).toList());
        response.setReplans(replans.stream().map(this::toReplanResponse).toList());
        response.setChildTraces(childSessions.stream().map(this::toSessionSummary).toList());
        return response;
    }

    private TraceSessionSummaryResponse toSessionSummary(AgentSessionTrace entity) {
        TraceSessionSummaryResponse dto = new TraceSessionSummaryResponse();
        dto.setId(entity.getId());
        dto.setTraceId(entity.getTraceId());
        dto.setSessionId(entity.getSessionId());
        dto.setAgentType(entity.getAgentType());
        dto.setUserInput(truncate(entity.getUserInput(), promptMaxLength));
        dto.setFinalAnswer(truncate(entity.getFinalAnswer(), resultMaxLength));
        dto.setStatus(entity.getStatus());
        dto.setRootTraceId(entity.getRootTraceId());
        dto.setParentTraceId(entity.getParentTraceId());
        dto.setBranchType(entity.getBranchType());
        dto.setBranchLabel(entity.getBranchLabel());
        dto.setErrorMessage(truncate(entity.getErrorMessage(), promptMaxLength));
        dto.setStartTime(entity.getStartTime());
        dto.setEndTime(entity.getEndTime());
        dto.setCostMs(entity.getCostMs());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    private TraceStepResponse toStepResponse(AgentTraceStep entity) {
        TraceStepResponse dto = new TraceStepResponse();
        dto.setId(entity.getId());
        dto.setTraceId(entity.getTraceId());
        dto.setStepName(entity.getStepName());
        dto.setStepContent(truncate(entity.getStepContent(), promptMaxLength));
        dto.setStepOutput(truncate(entity.getStepOutput(), resultMaxLength));
        dto.setStatus(entity.getStatus());
        dto.setErrorMessage(truncate(entity.getErrorMessage(), promptMaxLength));
        dto.setStartTime(entity.getStartTime());
        dto.setEndTime(entity.getEndTime());
        dto.setCostMs(entity.getCostMs());
        return dto;
    }

    private TraceToolCallResponse toToolCallResponse(AgentToolCallTrace entity) {
        TraceToolCallResponse dto = new TraceToolCallResponse();
        dto.setId(entity.getId());
        dto.setTraceId(entity.getTraceId());
        dto.setToolName(entity.getToolName());
        dto.setToolParams(truncate(maskSensitiveContent(entity.getToolParams()), promptMaxLength));
        dto.setToolResult(truncate(entity.getToolResult(), resultMaxLength));
        dto.setStatus(entity.getStatus());
        dto.setErrorMessage(entity.getErrorMessage());
        dto.setErrorCategory(entity.getErrorCategory());
        dto.setRetryCount(entity.getRetryCount());
        dto.setStartedAt(entity.getStartedAt());
        dto.setFinishedAt(entity.getFinishedAt());
        dto.setCostMs(entity.getCostMs());
        return dto;
    }

    private TraceReplanResponse toReplanResponse(AgentReplanRecord entity) {
        TraceReplanResponse dto = new TraceReplanResponse();
        dto.setId(entity.getId());
        dto.setTraceId(entity.getTraceId());
        dto.setTriggerStep(entity.getTriggerStep());
        dto.setReason(entity.getReason());
        dto.setSummary(truncate(entity.getSummary(), resultMaxLength));
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    private String truncate(String text, int maxLength) {
        return StringUtils.truncate(text, maxLength, TRUNCATED_SUFFIX);
    }

    private String maskSensitiveContent(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        try {
            JsonNode root = objectMapper.readTree(text);
            maskSensitiveNode(root);
            return objectMapper.writeValueAsString(root);
        } catch (Exception ignored) {
            return text;
        }
    }

    private void maskSensitiveNode(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            List<String> fieldNames = new ArrayList<>();
            Iterator<String> iterator = objectNode.fieldNames();
            while (iterator.hasNext()) {
                fieldNames.add(iterator.next());
            }

            for (String fieldName : fieldNames) {
                JsonNode child = objectNode.get(fieldName);
                if (isSensitiveField(fieldName)) {
                    objectNode.set(fieldName, TextNode.valueOf(MASKED_VALUE));
                } else {
                    maskSensitiveNode(child);
                }
            }
            return;
        }

        if (node instanceof ArrayNode arrayNode) {
            for (JsonNode child : arrayNode) {
                maskSensitiveNode(child);
            }
        }
    }

    private boolean isSensitiveField(String fieldName) {
        return SENSITIVE_FIELDS.contains(fieldName.toLowerCase(Locale.ROOT));
    }
}
