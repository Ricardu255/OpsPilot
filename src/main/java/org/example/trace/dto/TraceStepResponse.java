package org.example.trace.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * 执行步骤响应
 */
public class TraceStepResponse {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("trace_id")
    private String traceId;

    @JsonProperty("step_name")
    private String stepName;

    @JsonProperty("step_content")
    private String stepContent;

    @JsonProperty("step_output")
    private String stepOutput;

    @JsonProperty("status")
    private String status;

    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("start_time")
    private LocalDateTime startTime;

    @JsonProperty("end_time")
    private LocalDateTime endTime;

    @JsonProperty("cost_ms")
    private Long costMs;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public String getStepName() { return stepName; }
    public void setStepName(String stepName) { this.stepName = stepName; }

    public String getStepContent() { return stepContent; }
    public void setStepContent(String stepContent) { this.stepContent = stepContent; }

    public String getStepOutput() { return stepOutput; }
    public void setStepOutput(String stepOutput) { this.stepOutput = stepOutput; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public Long getCostMs() { return costMs; }
    public void setCostMs(Long costMs) { this.costMs = costMs; }
}
