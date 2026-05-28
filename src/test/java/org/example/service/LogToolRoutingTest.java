package org.example.service;

import org.example.agent.tool.QueryLogsTools;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class LogToolRoutingTest {

    @Test
    void queryLogsReturnsStructuredDiagnosticWhenRealModeFallsBackToLocalTool() {
        QueryLogsTools tools = new QueryLogsTools();
        ReflectionTestUtils.setField(tools, "mockEnabled", false);
        ReflectionTestUtils.setField(tools, "mcpClientEnabled", true);
        ReflectionTestUtils.setField(tools, "mcpSseUrl", "http://localhost:3000/sse");
        tools.init();

        String result = tools.queryLogs("ap-guangzhou", "application-logs", "level:ERROR", 5);

        assertThat(result).contains("CLS_MCP_TOOL_UNAVAILABLE");
        assertThat(result).contains("\"provider_mode\" : \"MCP\"");
        assertThat(result).contains("http://localhost:3000/sse");
        assertThat(result).contains("MCP");
    }

    @Test
    void chatServiceOmitsLocalQueryLogsToolWhenMockModeIsDisabled() {
        ChatService service = new ChatService();
        QueryLogsTools logsTools = new QueryLogsTools();
        ReflectionTestUtils.setField(logsTools, "mockEnabled", false);
        ReflectionTestUtils.setField(service, "queryLogsTools", logsTools);

        Object[] tools = service.buildMethodToolsArray();

        assertThat(Arrays.stream(tools).filter(Objects::nonNull))
                .noneMatch(QueryLogsTools.class::isInstance);
    }

    @Test
    void chatServiceKeepsLocalQueryLogsToolWhenMockModeIsEnabled() {
        ChatService service = new ChatService();
        QueryLogsTools logsTools = new QueryLogsTools();
        ReflectionTestUtils.setField(logsTools, "mockEnabled", true);
        ReflectionTestUtils.setField(service, "queryLogsTools", logsTools);

        Object[] tools = service.buildMethodToolsArray();

        assertThat(Arrays.stream(tools).filter(Objects::nonNull))
                .anyMatch(QueryLogsTools.class::isInstance);
    }

    @Test
    void aiOpsServiceOmitsLocalQueryLogsToolWhenMockModeIsDisabled() {
        AiOpsService service = new AiOpsService();
        QueryLogsTools logsTools = new QueryLogsTools();
        ReflectionTestUtils.setField(logsTools, "mockEnabled", false);
        ReflectionTestUtils.setField(service, "queryLogsTools", logsTools);

        Object[] tools = (Object[]) ReflectionTestUtils.invokeMethod(service, "buildMethodToolsArray");

        assertThat(Arrays.stream(tools).filter(Objects::nonNull))
                .noneMatch(QueryLogsTools.class::isInstance);
    }
}
