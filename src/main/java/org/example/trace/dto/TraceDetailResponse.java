package org.example.trace.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Trace 完整链路明细响应
 * 聚合 session + steps + toolCalls + replans
 */
public class TraceDetailResponse {

    @JsonProperty("trace_id")
    private String traceId;

    @JsonProperty("session")
    private TraceSessionSummaryResponse session;

    @JsonProperty("steps")
    private List<TraceStepResponse> steps;

    @JsonProperty("tool_calls")
    private List<TraceToolCallResponse> toolCalls;

    @JsonProperty("replans")
    private List<TraceReplanResponse> replans;

    @JsonProperty("child_traces")
    private List<TraceSessionSummaryResponse> childTraces;

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public TraceSessionSummaryResponse getSession() { return session; }
    public void setSession(TraceSessionSummaryResponse session) { this.session = session; }

    public List<TraceStepResponse> getSteps() { return steps; }
    public void setSteps(List<TraceStepResponse> steps) { this.steps = steps; }

    public List<TraceToolCallResponse> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<TraceToolCallResponse> toolCalls) { this.toolCalls = toolCalls; }

    public List<TraceReplanResponse> getReplans() { return replans; }
    public void setReplans(List<TraceReplanResponse> replans) { this.replans = replans; }

    public List<TraceSessionSummaryResponse> getChildTraces() { return childTraces; }
    public void setChildTraces(List<TraceSessionSummaryResponse> childTraces) { this.childTraces = childTraces; }
}
