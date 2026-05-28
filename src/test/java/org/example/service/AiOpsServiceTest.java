package org.example.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiOpsServiceTest {

    @Test
    void normalizeFinalReportConvertsPlannerControlJsonToReadableReport() {
        AiOpsService service = new AiOpsService();

        String report = service.normalizeFinalReport("""
                {
                  "decision": "FINISH",
                  "step": "已完成全部告警根因分析。",
                  "tool": "none",
                  "context": "payment-service CPU 过高，order-service 存在内存泄漏风险。"
                }
                """);

        assertThat(report).contains("告警分析报告");
        assertThat(report).contains("## 根因分析");
        assertThat(report).contains("payment-service CPU 过高");
        assertThat(report).doesNotContain("\"decision\"");
    }
}
