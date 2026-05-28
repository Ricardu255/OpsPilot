package org.example.trace.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Trace 基础统计响应
 */
public class TraceStatsResponse {

    @JsonProperty("total_sessions")
    private long totalSessions;

    @JsonProperty("success_count")
    private long successCount;

    @JsonProperty("failed_count")
    private long failedCount;

    @JsonProperty("running_count")
    private long runningCount;

    @JsonProperty("average_cost_ms")
    private double averageCostMs;

    @JsonProperty("total_tool_calls")
    private long totalToolCalls;

    @JsonProperty("failed_tool_calls")
    private long failedToolCalls;

    @JsonProperty("recent_failures")
    private List<TraceSessionSummaryResponse> recentFailures;

    @JsonProperty("slowest_traces")
    private List<TraceSessionSummaryResponse> slowestTraces;

    public long getTotalSessions() { return totalSessions; }
    public void setTotalSessions(long totalSessions) { this.totalSessions = totalSessions; }

    public long getSuccessCount() { return successCount; }
    public void setSuccessCount(long successCount) { this.successCount = successCount; }

    public long getFailedCount() { return failedCount; }
    public void setFailedCount(long failedCount) { this.failedCount = failedCount; }

    public long getRunningCount() { return runningCount; }
    public void setRunningCount(long runningCount) { this.runningCount = runningCount; }

    public double getAverageCostMs() { return averageCostMs; }
    public void setAverageCostMs(double averageCostMs) { this.averageCostMs = averageCostMs; }

    public long getTotalToolCalls() { return totalToolCalls; }
    public void setTotalToolCalls(long totalToolCalls) { this.totalToolCalls = totalToolCalls; }

    public long getFailedToolCalls() { return failedToolCalls; }
    public void setFailedToolCalls(long failedToolCalls) { this.failedToolCalls = failedToolCalls; }

    public List<TraceSessionSummaryResponse> getRecentFailures() { return recentFailures; }
    public void setRecentFailures(List<TraceSessionSummaryResponse> recentFailures) { this.recentFailures = recentFailures; }

    public List<TraceSessionSummaryResponse> getSlowestTraces() { return slowestTraces; }
    public void setSlowestTraces(List<TraceSessionSummaryResponse> slowestTraces) { this.slowestTraces = slowestTraces; }
}
