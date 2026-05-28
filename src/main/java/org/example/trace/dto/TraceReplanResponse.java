package org.example.trace.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * 重规划记录响应
 */
public class TraceReplanResponse {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("trace_id")
    private String traceId;

    @JsonProperty("trigger_step")
    private String triggerStep;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("summary")
    private String summary;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public String getTriggerStep() { return triggerStep; }
    public void setTriggerStep(String triggerStep) { this.triggerStep = triggerStep; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
