package org.example.trace.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Agent 业务指标服务
 * 使用 Micrometer 注册自定义 Prometheus 指标
 */
@Service
public class AgentMetricsService {

    private final MeterRegistry meterRegistry;

    public AgentMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 会话计数 +1（按状态分组）
     */
    public void incrementSessionTotal(String status) {
        Counter.builder("agent_session_total")
                .description("Total number of agent sessions")
                .tag("status", status)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录会话耗时
     */
    public void recordSessionDuration(long costMs) {
        Timer.builder("agent_session_duration_ms")
                .description("Agent session duration in milliseconds")
                .register(meterRegistry)
                .record(costMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 工具调用计数 +1（按工具名和状态分组）
     */
    public void incrementToolCallTotal(String toolName, String status) {
        Counter.builder("agent_tool_call_total")
                .description("Total number of agent tool calls")
                .tag("tool_name", toolName)
                .tag("status", status)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 工具调用失败计数 +1
     */
    public void incrementToolCallFailed(String toolName) {
        Counter.builder("agent_tool_call_failed_total")
                .description("Total number of failed agent tool calls")
                .tag("tool_name", toolName)
                .register(meterRegistry)
                .increment();
    }
}
