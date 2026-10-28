package org.example.trace.service;

import org.example.trace.model.AgentReplanRecord;
import org.example.trace.model.AgentSessionTrace;
import org.example.trace.model.AgentToolCallTrace;
import org.example.trace.model.AgentTraceStep;
import org.example.trace.repository.AgentReplanRecordRepository;
import org.example.trace.repository.AgentSessionTraceRepository;
import org.example.trace.repository.AgentToolCallTraceRepository;
import org.example.trace.repository.AgentTraceStepRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AgentTraceService {

    private final AgentSessionTraceRepository agentSessionTraceRepository;
    private final AgentTraceStepRepository agentTraceStepRepository;
    private final AgentToolCallTraceRepository agentToolCallTraceRepository;
    private final AgentReplanRecordRepository agentReplanRecordRepository;

    private AgentMetricsService agentMetricsService;

    public AgentTraceService(
            AgentSessionTraceRepository agentSessionTraceRepository,
            AgentTraceStepRepository agentTraceStepRepository,
            AgentToolCallTraceRepository agentToolCallTraceRepository,
            AgentReplanRecordRepository agentReplanRecordRepository
    ) {
        this.agentSessionTraceRepository = agentSessionTraceRepository;
        this.agentTraceStepRepository = agentTraceStepRepository;
        this.agentToolCallTraceRepository = agentToolCallTraceRepository;
        this.agentReplanRecordRepository = agentReplanRecordRepository;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setAgentMetricsService(AgentMetricsService agentMetricsService) {
        this.agentMetricsService = agentMetricsService;
    }

    @Transactional
    public AgentSessionTrace startSession(String traceId, String sessionId, String agentType, String userInput) {
        return startSession(traceId, sessionId, agentType, userInput, traceId, null, "MAIN", null);
    }

    @Transactional
    public AgentSessionTrace startSession(
            String traceId,
            String sessionId,
            String agentType,
            String userInput,
            String rootTraceId,
            String parentTraceId,
            String branchType,
            String branchLabel) {
        AgentSessionTrace sessionTrace = new AgentSessionTrace();
        sessionTrace.setTraceId(traceId);
        sessionTrace.setSessionId(sessionId);
        sessionTrace.setAgentType(agentType);
        sessionTrace.setUserInput(userInput);
        sessionTrace.setStatus("RUNNING");
        sessionTrace.setRootTraceId(rootTraceId == null || rootTraceId.isBlank() ? traceId : rootTraceId);
        sessionTrace.setParentTraceId(parentTraceId);
        sessionTrace.setBranchType(branchType == null || branchType.isBlank() ? "MAIN" : branchType);
        sessionTrace.setBranchLabel(branchLabel);
        sessionTrace.setStartTime(LocalDateTime.now());
        AgentSessionTrace saved = agentSessionTraceRepository.save(sessionTrace);

        if (agentMetricsService != null) {
            agentMetricsService.incrementSessionTotal("RUNNING");
        }

        return saved;
    }

    @Transactional
    public AgentSessionTrace startChildSessionFromParent(
            String parentTraceId,
            String agentType,
            String branchType,
            String branchLabel) {
        AgentSessionTrace parentSession = agentSessionTraceRepository.findTopByTraceIdOrderByIdDesc(parentTraceId)
                .orElseThrow(() -> new IllegalArgumentException("No trace session found for traceId=" + parentTraceId));

        String rootTraceId = parentSession.getRootTraceId();
        if (rootTraceId == null || rootTraceId.isBlank()) {
            rootTraceId = parentSession.getTraceId();
        }

        String childAgentType = agentType;
        if (childAgentType == null || childAgentType.isBlank()) {
            childAgentType = parentSession.getAgentType();
        }

        return startSession(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                childAgentType,
                parentSession.getUserInput(),
                rootTraceId,
                parentSession.getTraceId(),
                branchType,
                branchLabel
        );
    }

    @Transactional
    public AgentSessionTrace completeSession(String traceId, String finalAnswer, String status, String errorMessage) {
        AgentSessionTrace sessionTrace = agentSessionTraceRepository.findTopByTraceIdOrderByIdDesc(traceId)
                .orElseThrow(() -> new IllegalArgumentException("No trace session found for traceId=" + traceId));
        LocalDateTime endTime = LocalDateTime.now();
        sessionTrace.setFinalAnswer(finalAnswer);
        sessionTrace.setStatus(status);
        sessionTrace.setErrorMessage(errorMessage);
        sessionTrace.setEndTime(endTime);
        long costMs = Duration.between(sessionTrace.getStartTime(), endTime).toMillis();
        sessionTrace.setCostMs(costMs);
        AgentSessionTrace saved = agentSessionTraceRepository.save(sessionTrace);

        if (agentMetricsService != null) {
            agentMetricsService.incrementSessionTotal(status);
            agentMetricsService.recordSessionDuration(costMs);
        }

        return saved;
    }

    @Transactional
    public AgentTraceStep recordStep(
            String traceId,
            String sessionId,
            String stepName,
            String stepContent,
            String status
    ) {
        AgentTraceStep step = new AgentTraceStep();
        step.setTraceId(traceId);
        step.setSessionId(sessionId);
        step.setStepName(stepName);
        step.setStepContent(stepContent);
        step.setStatus(status);
        step.setStartTime(LocalDateTime.now());
        return agentTraceStepRepository.save(step);
    }

    @Transactional
    public AgentTraceStep completeStep(Long stepId, String status, String output, String errorMessage) {
        AgentTraceStep step = agentTraceStepRepository.findById(stepId)
                .orElseThrow(() -> new IllegalArgumentException("No trace step found for id=" + stepId));
        LocalDateTime endTime = LocalDateTime.now();
        step.setStatus(status);
        step.setStepOutput(output);
        step.setErrorMessage(errorMessage);
        step.setEndTime(endTime);
        step.setCostMs(Duration.between(step.getStartTime(), endTime).toMillis());
        return agentTraceStepRepository.save(step);
    }

    /**
     * 记录工具调用（简化版，向后兼容）
     */
    @Transactional
    public AgentToolCallTrace recordToolCall(
            String traceId,
            String sessionId,
            String toolName,
            String toolParams,
            String toolResult,
            String status
    ) {
        LocalDateTime now = LocalDateTime.now();
        return recordToolCall(traceId, sessionId, toolName, toolParams, toolResult, status, null, now, now, 0L);
    }

    /**
     * 记录工具调用（完整版，含计时和错误信息）
     */
    @Transactional
    public AgentToolCallTrace recordToolCall(
            String traceId,
            String sessionId,
            String toolName,
            String toolParams,
            String toolResult,
            String status,
            String errorMessage,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            long costMs
    ) {
        return recordToolCall(
                traceId,
                sessionId,
                toolName,
                toolParams,
                toolResult,
                status,
                errorMessage,
                startedAt,
                finishedAt,
                costMs,
                null,
                0
        );
    }

    /**
     * 记录工具调用（完整版，含可靠性元数据）
     */
    @Transactional
    public AgentToolCallTrace recordToolCall(
            String traceId,
            String sessionId,
            String toolName,
            String toolParams,
            String toolResult,
            String status,
            String errorMessage,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            long costMs,
            String errorCategory,
            int retryCount
    ) {
        AgentToolCallTrace trace = new AgentToolCallTrace();
        trace.setTraceId(traceId);
        trace.setSessionId(sessionId);
        trace.setToolName(toolName);
        trace.setToolParams(toolParams);
        trace.setToolResult(toolResult);
        trace.setStatus(status);
        trace.setErrorMessage(errorMessage);
        trace.setErrorCategory(errorCategory);
        trace.setRetryCount(retryCount);
        trace.setStartedAt(startedAt);
        trace.setFinishedAt(finishedAt);
        trace.setCostMs(costMs);
        AgentToolCallTrace saved = agentToolCallTraceRepository.save(trace);

        if (agentMetricsService != null) {
            agentMetricsService.incrementToolCallTotal(toolName, status);
            if ("FAILED".equals(status)) {
                agentMetricsService.incrementToolCallFailed(toolName);
            }
        }

        return saved;
    }

    @Transactional
    public AgentReplanRecord recordReplan(
            String traceId,
            String sessionId,
            String triggerStep,
            String reason,
            String summary
    ) {
        AgentReplanRecord record = new AgentReplanRecord();
        record.setTraceId(traceId);
        record.setSessionId(sessionId);
        record.setTriggerStep(triggerStep);
        record.setReason(reason);
        record.setSummary(summary);
        return agentReplanRecordRepository.save(record);
    }
}
