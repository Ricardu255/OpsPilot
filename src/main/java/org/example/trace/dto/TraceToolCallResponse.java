package org.example.trace.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * 工具调用响应
 */
public class TraceToolCallResponse {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("trace_id")
    private String traceId;

    @JsonProperty("tool_name")
    private String toolName;

    @JsonProperty("tool_params")
    private String toolParams;

    @JsonProperty("tool_result")
    private String toolResult;

    @JsonProperty("status")
    private String status;

    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("error_category")
    private String errorCategory;

    @JsonProperty("retry_count")
    private Integer retryCount;

    @JsonProperty("started_at")
    private LocalDateTime startedAt;

    @JsonProperty("finished_at")
    private LocalDateTime finishedAt;

    @JsonProperty("cost_ms")
    private Long costMs;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }

    public String getToolParams() { return toolParams; }
    public void setToolParams(String toolParams) { this.toolParams = toolParams; }

    public String getToolResult() { return toolResult; }
    public void setToolResult(String toolResult) { this.toolResult = toolResult; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getErrorCategory() { return errorCategory; }
    public void setErrorCategory(String errorCategory) { this.errorCategory = errorCategory; }

    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }

    public Long getCostMs() { return costMs; }
    public void setCostMs(Long costMs) { this.costMs = costMs; }
}
