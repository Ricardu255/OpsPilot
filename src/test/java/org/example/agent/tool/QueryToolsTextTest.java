package org.example.agent.tool;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class QueryToolsTextTest {

    @Test
    void queryPrometheusAlertsReturnsReadableChineseMockText() {
        QueryMetricsTools tools = new QueryMetricsTools();
        ReflectionTestUtils.setField(tools, "mockEnabled", true);
        ReflectionTestUtils.setField(tools, "prometheusBaseUrl", "http://localhost:9090");
        ReflectionTestUtils.setField(tools, "timeout", 10);
        tools.init();

        String result = tools.queryPrometheusAlerts();

        assertThat(result).contains("CPU 使用率");
        assertThat(result).contains("payment-service");
        assertThat(result).doesNotContain("鍛");
    }

    @Test
    void queryLogsReturnsReadableChineseMockText() {
        QueryLogsTools tools = new QueryLogsTools();
        ReflectionTestUtils.setField(tools, "mockEnabled", true);
        tools.init();

        String result = tools.queryLogs("ap-guangzhou", "system-metrics", "cpu", 5);

        assertThat(result).contains("CPU使用率过高");
        assertThat(result).contains("payment-service");
        assertThat(result).doesNotContain("鏃");
    }
}
