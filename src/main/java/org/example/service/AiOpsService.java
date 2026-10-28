package org.example.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.aiops.ReplanDecision;
import org.example.aiops.ReplanDecisionEvaluator;
import org.example.aiops.ReplanReason;
import org.example.trace.TraceContext;
import org.example.trace.TraceContextHolder;
import org.example.trace.ToolTraceHelper;
import org.example.trace.model.AgentSessionTrace;
import org.example.trace.model.AgentTraceStep;
import org.example.trace.service.AgentTraceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * AI Ops multi-agent workflow service.
 */
@Service
public class AiOpsService {

    private static final Logger logger = LoggerFactory.getLogger(AiOpsService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private ChatService chatService;

    @Autowired(required = false)
    private AgentTraceService agentTraceService;

    @Autowired(required = false)
    private ReplanDecisionEvaluator replanDecisionEvaluator;

    @Autowired(required = false)
    private ToolTraceHelper toolTraceHelper;

    public record AiOpsWorkflowOutcome(
            TraceContext traceContext,
            Optional<OverAllState> state,
            Optional<String> finalReport,
            boolean replanned,
            String failureMessage) {
    }

    private record WorkflowAttemptResult(
            Optional<OverAllState> state,
            Optional<String> finalReport,
            ReplanReason replanReason,
            String triggerStep,
            String failureMessage) {
        boolean shouldReplan() {
            return replanReason != null;
        }
    }

    public Optional<OverAllState> executeAiOpsAnalysis(
            DashScopeChatModel chatModel,
            ToolCallback[] toolCallbacks) throws GraphRunnerException {
        return executeAiOpsAnalysis(chatModel, toolCallbacks, null, null);
    }

    public Optional<OverAllState> executeAiOpsAnalysis(
            DashScopeChatModel chatModel,
            ToolCallback[] toolCallbacks,
            String traceId,
            String traceSessionId) throws GraphRunnerException {
        logger.info("Starting AI Ops multi-agent workflow");

        ReactAgent plannerAgent = buildPlannerAgent(chatModel, toolCallbacks);
        ReactAgent executorAgent = buildExecutorAgent(chatModel, toolCallbacks);

        SupervisorAgent supervisorAgent = SupervisorAgent.builder()
                .name("ai_ops_supervisor")
                .description("Coordinate the planner and executor agents for AI Ops analysis.")
                .model(chatModel)
                .systemPrompt(buildSupervisorSystemPrompt())
                .subAgents(List.of(plannerAgent, executorAgent))
                .build();

        String taskPrompt = """
                你是企业级 SRE，接到自动化告警排查任务。
                请执行“规划 -> 执行 -> 再规划/结束”的闭环分析，严格基于工具返回的数据得出结论。
                如果多次查询失败或没有有效证据，请如实说明限制，不得编造。
                最终必须输出《告警分析报告》。
                """;

        startAiOpsTraceSession(traceId, traceSessionId, taskPrompt);
        recordStepSafely(traceId, traceSessionId, "planner_agent", "Planner agent prepared");
        recordStepSafely(traceId, traceSessionId, "executor_agent", "Executor agent prepared");
        AgentTraceStep supervisorStep = recordStepSafely(
                traceId,
                traceSessionId,
                "supervisor_agent",
                "Supervisor agent invoking multi-agent workflow"
        );

        logger.info("Invoking AI Ops supervisor agent");
        try {
            Optional<OverAllState> result = supervisorAgent.invoke(taskPrompt);
            completeStepSafely(supervisorStep, "SUCCESS", "Supervisor agent completed workflow", null);
            return result;
        } catch (GraphRunnerException e) {
            completeStepSafely(supervisorStep, "FAILED", null, e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            completeStepSafely(supervisorStep, "FAILED", null, e.getMessage());
            throw e;
        }
    }

    public AiOpsWorkflowOutcome executeAiOpsWorkflow(
            DashScopeChatModel chatModel,
            ToolCallback[] toolCallbacks,
            TraceContext initialTraceContext) {
        TraceContext activeContext = initialTraceContext;
        WorkflowAttemptResult firstAttempt = executeWorkflowAttempt(chatModel, toolCallbacks, activeContext);
        if (!firstAttempt.shouldReplan()) {
            return new AiOpsWorkflowOutcome(
                    activeContext,
                    firstAttempt.state(),
                    firstAttempt.finalReport(),
                    false,
                    firstAttempt.failureMessage()
            );
        }

        TraceContext childContext = createReplanChildContext(
                activeContext,
                firstAttempt.triggerStep(),
                firstAttempt.replanReason(),
                firstAttempt.failureMessage()
        );
        if (childContext == null || isBlank(childContext.traceId())
                || childContext.traceId().equals(activeContext.traceId())) {
            return new AiOpsWorkflowOutcome(
                    activeContext,
                    firstAttempt.state(),
                    firstAttempt.finalReport(),
                    false,
                    firstAttempt.failureMessage()
            );
        }

        WorkflowAttemptResult secondAttempt = executeWorkflowAttempt(chatModel, toolCallbacks, childContext);
        return new AiOpsWorkflowOutcome(
                childContext,
                secondAttempt.state(),
                secondAttempt.finalReport(),
                true,
                secondAttempt.failureMessage()
        );
    }

    public Optional<String> extractFinalReport(OverAllState state) {
        logger.info("Extracting final AI Ops report");

        Optional<AssistantMessage> plannerFinalOutput = state.value("planner_plan")
                .filter(AssistantMessage.class::isInstance)
                .map(AssistantMessage.class::cast);

        if (plannerFinalOutput.isPresent()) {
            String reportText = normalizeFinalReport(plannerFinalOutput.get().getText());
            logger.info("Final planner report extracted, length={}", reportText.length());
            return Optional.of(reportText);
        }

        logger.warn("No planner final report found");
        return Optional.empty();
    }

    void startAiOpsTraceSession(String traceId, String traceSessionId, String taskPrompt) {
        if (agentTraceService == null || isBlank(traceId)) {
            return;
        }
        agentTraceService.startSession(traceId, traceSessionId, "AIOPS_AGENT", taskPrompt);
        agentTraceService.recordStep(traceId, traceSessionId, "supervisor_agent", "AI Ops trace session started", "RUNNING");
    }

    public void completeAiOpsTraceSession(String traceId, String finalAnswer, String errorMessage) {
        if (agentTraceService == null || isBlank(traceId)) {
            return;
        }
        String status = isBlank(errorMessage) ? "SUCCESS" : "FAILED";
        try {
            agentTraceService.completeSession(traceId, finalAnswer, status, errorMessage);
        } catch (Exception e) {
            logger.warn("完成 AI Ops 会话 Trace 失败 (可能尚未开始): {}", e.getMessage());
        }
        if (toolTraceHelper != null) {
            toolTraceHelper.clearTraceState(traceId);
        }
        TraceContextHolder.clear();
    }

    public void completeAiOpsTraceSession(String traceId, String finalAnswer, String status, String errorMessage) {
        if (agentTraceService == null || isBlank(traceId)) {
            return;
        }
        try {
            agentTraceService.completeSession(traceId, finalAnswer, status, errorMessage);
        } catch (Exception e) {
            logger.warn("Failed to complete AI Ops trace session: {}", e.getMessage());
        }
        if (toolTraceHelper != null) {
            toolTraceHelper.clearTraceState(traceId);
        }
        TraceContextHolder.clear();
    }

    Optional<OverAllState> runWorkflowAttempt(
            DashScopeChatModel chatModel,
            ToolCallback[] toolCallbacks,
            TraceContext traceContext) throws GraphRunnerException {
        TraceContextHolder.set(traceContext);
        return executeAiOpsAnalysis(chatModel, toolCallbacks, traceContext.traceId(), traceContext.sessionId());
    }

    TraceContext createReplanChildContext(
            TraceContext parentContext,
            String triggerStep,
            ReplanReason reason,
            String summary) {
        if (parentContext == null || agentTraceService == null || reason == null) {
            return parentContext;
        }

        recordWorkflowReplan(
                parentContext.traceId(),
                parentContext.sessionId(),
                triggerStep,
                reason,
                summary
        );
        completeAiOpsTraceSession(parentContext.traceId(), null, "REPLAN", summary);

        AgentSessionTrace childSession = agentTraceService.startChildSessionFromParent(
                parentContext.traceId(),
                "AIOPS_AGENT",
                "REPLAN",
                summary
        );
        TraceContext childContext = new TraceContext(
                childSession.getTraceId(),
                childSession.getSessionId(),
                parentContext.requestId(),
                parentContext.userId()
        );
        TraceContextHolder.set(childContext);
        return childContext;
    }

    public void recordWorkflowReplan(
            String traceId,
            String traceSessionId,
            String triggerStep,
            ReplanReason reason,
            String summary) {
        if (agentTraceService == null || isBlank(traceId) || isBlank(traceSessionId) || reason == null) {
            return;
        }
        agentTraceService.recordReplan(
                traceId,
                traceSessionId,
                isBlank(triggerStep) ? "supervisor_agent" : triggerStep,
                reason.name(),
                summary
        );
    }

    ReplanDecision evaluateToolDecision(String toolName, String result, String errorMessage, int failureCount) {
        if (replanDecisionEvaluator == null) {
            return ReplanDecision.continueExecution();
        }
        return replanDecisionEvaluator.evaluateToolResult(toolName, result, errorMessage, failureCount);
    }

    private WorkflowAttemptResult executeWorkflowAttempt(
            DashScopeChatModel chatModel,
            ToolCallback[] toolCallbacks,
            TraceContext traceContext) {
        try {
            Optional<OverAllState> state = runWorkflowAttempt(chatModel, toolCallbacks, traceContext);
            if (state.isEmpty()) {
                return new WorkflowAttemptResult(
                        Optional.empty(),
                        Optional.empty(),
                        ReplanReason.EMPTY_RESULT,
                        "supervisor_agent",
                        "multi-agent workflow returned empty result"
                );
            }

            Optional<String> finalReport = extractFinalReport(state.get());
            if (finalReport.isEmpty()) {
                return new WorkflowAttemptResult(
                        state,
                        Optional.empty(),
                        ReplanReason.EMPTY_RESULT,
                        "planner_agent",
                        "planner final report missing"
                );
            }

            return new WorkflowAttemptResult(state, finalReport, null, null, null);
        } catch (GraphRunnerException e) {
            return new WorkflowAttemptResult(
                    Optional.empty(),
                    Optional.empty(),
                    ReplanReason.UNKNOWN,
                    "supervisor_agent",
                    defaultFailureMessage(e.getMessage())
            );
        } catch (RuntimeException e) {
            return new WorkflowAttemptResult(
                    Optional.empty(),
                    Optional.empty(),
                    ReplanReason.UNKNOWN,
                    "supervisor_agent",
                    defaultFailureMessage(e.getMessage())
            );
        }
    }

    String normalizeFinalReport(String reportText) {
        if (isBlank(reportText)) {
            return "告警分析报告\n\n未能生成有效报告内容。";
        }

        String trimmed = reportText.trim();
        if (!trimmed.startsWith("{")) {
            return reportText;
        }

        try {
            JsonNode root = objectMapper.readTree(trimmed);
            if (root.has("decision") || root.has("step") || root.has("context")) {
                return formatPlannerJsonAsReport(root);
            }
        } catch (Exception e) {
            logger.debug("Planner final output is not JSON, returning original text", e);
        }

        return reportText;
    }

    private String formatPlannerJsonAsReport(JsonNode root) {
        String decision = textValue(root, "decision");
        String step = textValue(root, "step");
        String tool = textValue(root, "tool");
        String context = textValue(root, "context");

        StringBuilder report = new StringBuilder();
        report.append("告警分析报告\n\n");
        report.append("---\n\n");
        report.append("## 执行状态\n\n");
        report.append("- 决策结果: ").append(isBlank(decision) ? "FINISH" : decision).append("\n");
        report.append("- 工具调用: ").append(isBlank(tool) ? "none" : tool).append("\n\n");

        report.append("## 根因分析\n\n");
        if (!isBlank(context)) {
            report.append(context).append("\n\n");
        } else if (!isBlank(step)) {
            report.append(step).append("\n\n");
        } else {
            report.append("未能从 Agent 输出中提取到根因上下文。\n\n");
        }

        report.append("## 处理结论\n\n");
        if (!isBlank(step)) {
            report.append(step).append("\n\n");
        } else {
            report.append("AI Ops 流程已结束，请结合上方证据继续人工确认。\n\n");
        }

        report.append("## 后续建议\n\n");
        report.append("1. 优先核对高 CPU、高内存和慢响应对应服务的实时指标。\n");
        report.append("2. 对涉及 OOM、慢 SQL、线程数异常的服务补充最近日志和变更记录。\n");
        report.append("3. 如需真实环境结论，请接入真实 Prometheus 与 CLS 数据源后重新运行。\n");
        return report.toString();
    }

    private String textValue(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private String defaultFailureMessage(String errorMessage) {
        return isBlank(errorMessage) ? "ai ops workflow failed without detailed error" : errorMessage;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private AgentTraceStep recordStepSafely(String traceId, String traceSessionId, String stepName, String stepContent) {
        if (agentTraceService == null || isBlank(traceId)) {
            return null;
        }
        return agentTraceService.recordStep(traceId, traceSessionId, stepName, stepContent, "RUNNING");
    }

    private void completeStepSafely(AgentTraceStep step, String status, String output, String errorMessage) {
        if (agentTraceService == null || step == null || step.getId() == null) {
            return;
        }
        agentTraceService.completeStep(step.getId(), status, output, errorMessage);
    }

    private ReactAgent buildPlannerAgent(DashScopeChatModel chatModel, ToolCallback[] toolCallbacks) {
        return ReactAgent.builder()
                .name("planner_agent")
                .description("负责拆解告警、规划步骤并在必要时重新规划")
                .model(chatModel)
                .systemPrompt(buildPlannerPrompt())
                .methodTools(chatService.buildMethodToolsArray())
                .tools(toolCallbacks)
                .outputKey("planner_plan")
                .build();
    }

    private ReactAgent buildExecutorAgent(DashScopeChatModel chatModel, ToolCallback[] toolCallbacks) {
        return ReactAgent.builder()
                .name("executor_agent")
                .description("负责执行 Planner 当前安排的首个步骤，并反馈证据")
                .model(chatModel)
                .systemPrompt(buildExecutorPrompt())
                .methodTools(chatService.buildMethodToolsArray())
                .tools(toolCallbacks)
                .outputKey("executor_feedback")
                .build();
    }

    private String buildPlannerPrompt() {
        return """
                你是 Planner Agent，同时承担 Replanner 角色，负责：
                1. 读取当前输入任务 {input} 和最近的执行反馈 {executor_feedback}。
                2. 结合指标、日志、内部文档等证据，制定可执行的下一步。
                3. 在执行阶段输出 JSON，至少包含 decision、step、tool、context。
                4. 严禁编造数据，只能引用真实工具结果。
                5. 如果同一方向连续多次失败或为空结果，需要停止该方向，并在结论中说明原因。

                规则：
                - decision 只能是 PLAN、EXECUTE、FINISH。
                - FINISH 时不要输出 JSON，要直接输出完整 Markdown《告警分析报告》。
                - 最终报告至少包含：活跃告警、根因分析、处理结论、后续建议。
                """;
    }

    private String buildExecutorPrompt() {
        return """
                你是 Executor Agent，负责读取 Planner 输出 {planner_plan}，只执行其中的第一步。
                要求：
                - 明确工具和参数，必要时优先使用默认 region。
                - 如果工具失败或结果为空，记录失败原因、参数和证据摘要。
                - 将结果以 JSON 返回到 executor_feedback，字段至少包含：
                  status, summary, evidence, nextHint
                - 严禁编造任何未实际查询到的内容。
                """;
    }

    private String buildSupervisorSystemPrompt() {
        return """
                你是 AI Ops Supervisor，负责调度 planner_agent 和 executor_agent：
                1. 需要拆解或重新规划时调用 planner_agent。
                2. planner_agent 给出 EXECUTE 时调用 executor_agent。
                3. 根据 executor_agent 的反馈决定是否再次调用 planner_agent。
                4. 当 decision=FINISH 时，直接输出完整《告警分析报告》。
                5. 如果连续多次失败或拿不到证据，必须停止流程并明确说明原因。
                只能在 planner_agent、executor_agent 和 FINISH 之间做选择。
                """;
    }
}
