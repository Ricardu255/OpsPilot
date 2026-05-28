package org.example.trace.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * Session 摘要响应（用于分页列表和单条查询）
 */
public class TraceSessionSummaryResponse {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("trace_id")
    private String traceId;

    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("agent_type")
    private String agentType;

    @JsonProperty("user_input")
    private String userInput;

    @JsonProperty("final_answer")
    private String finalAnswer;

    @JsonProperty("status")
    private String status;

    @JsonProperty("root_trace_id")
    private String rootTraceId;

    @JsonProperty("parent_trace_id")
    private String parentTraceId;

    @JsonProperty("branch_type")
    private String branchType;

    @JsonProperty("branch_label")
    private String branchLabel;

    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("start_time")
    private LocalDateTime startTime;

    @JsonProperty("end_time")
    private LocalDateTime endTime;

    @JsonProperty("cost_ms")
    private Long costMs;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getAgentType() { return agentType; }
    public void setAgentType(String agentType) { this.agentType = agentType; }

    public String getUserInput() { return userInput; }
    public void setUserInput(String userInput) { this.userInput = userInput; }

    public String getFinalAnswer() { return finalAnswer; }
    public void setFinalAnswer(String finalAnswer) { this.finalAnswer = finalAnswer; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRootTraceId() { return rootTraceId; }
    public void setRootTraceId(String rootTraceId) { this.rootTraceId = rootTraceId; }

    public String getParentTraceId() { return parentTraceId; }
    public void setParentTraceId(String parentTraceId) { this.parentTraceId = parentTraceId; }

    public String getBranchType() { return branchType; }
    public void setBranchType(String branchType) { this.branchType = branchType; }

    public String getBranchLabel() { return branchLabel; }
    public void setBranchLabel(String branchLabel) { this.branchLabel = branchLabel; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public Long getCostMs() { return costMs; }
    public void setCostMs(Long costMs) { this.costMs = costMs; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
