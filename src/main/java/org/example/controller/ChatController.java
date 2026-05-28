package org.example.controller;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.Getter;
import lombok.Setter;
import org.example.aiops.ReplanReason;
import org.example.service.AiOpsService;
import org.example.service.ChatService;
import org.example.trace.TraceContext;
import org.example.trace.TraceContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 统一 API 控制器
 * 适配前端接口需求
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private AiOpsService aiOpsService;
    
    @Autowired
    private ChatService chatService;

    @Autowired(required = false)
    private ToolCallbackProvider tools;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    // 存储会话信息
    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();
    
    // 最大历史消息窗口大小（成对计算：用户消息+AI回复=1对）
    private static final int MAX_WINDOW_SIZE = 6;

    /**
     * 普通对话接口（支持工具调用）
     * 与 /chat_react 逻辑一致，但直接返回完整结果而非流式输出
     */
    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<ChatResponse>> chat(@RequestBody ChatRequest request) {
        try {
            logger.info("收到对话请求 - SessionId: {}, Question: {}", request.getId(), request.getQuestion());

            // 参数校验
            if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
                logger.warn("问题内容为空");
                return ResponseEntity.ok(ApiResponse.success(ChatResponse.error("问题内容不能为空")));
            }

            // 获取或创建会话
            SessionInfo session = getOrCreateSession(request.getId());
            TraceContextHolder.set(getOrCreateTraceContext(session.getSessionId()));
            logger.info("对话使用会话 SessionId: {}", session.getSessionId());
            
            // 获取历史消息
            List<Map<String, String>> history = session.getHistory();
            logger.info("会话历史消息对数: {}", history.size() / 2);

            // 创建 DashScope API 和 ChatModel
            DashScopeApi dashScopeApi = chatService.createDashScopeApi();
            DashScopeChatModel chatModel = chatService.createStandardChatModel(dashScopeApi);

            // 记录可用工具
            chatService.logAvailableTools();

            logger.info("开始 ReactAgent 对话（支持自动工具调用）");
            
            // 构建系统提示词（包含历史消息）
            String systemPrompt = chatService.buildSystemPrompt(history);
            
            // 创建 ReactAgent
            ReactAgent agent = chatService.createReactAgent(chatModel, systemPrompt);
            
            // 执行对话
            String fullAnswer;
            try {
                fullAnswer = chatService.executeChat(agent, request.getQuestion());
            } catch (Exception primaryError) {
                if (!chatService.isExternalToolTransportFailure(primaryError)) {
                    throw primaryError;
                }
                logger.warn("External MCP tool transport failed for /api/chat, retrying without external callbacks", primaryError);
                ReactAgent fallbackAgent = chatService.createLocalOnlyReactAgent(chatModel, systemPrompt);
                fullAnswer = chatService.executeChat(fallbackAgent, request.getQuestion());
            }
            
            // 更新会话历史
            session.addMessage(request.getQuestion(), fullAnswer);
            logger.info("已更新会话历史 - SessionId: {}, 当前消息对数: {}", 
                request.getId(), session.getMessagePairCount());
            
            return ResponseEntity.ok(ApiResponse.success(ChatResponse.success(fullAnswer)));

        } catch (Exception e) {
            logger.error("对话失败", e);
            return ResponseEntity.ok(ApiResponse.success(ChatResponse.error(e.getMessage())));
        }
    }

    /**
     * 清空会话历史
     */
    @PostMapping("/chat/clear")
    public ResponseEntity<ApiResponse<String>> clearChatHistory(@RequestBody ClearRequest request) {
        try {
            logger.info("收到清空会话历史请求 - SessionId: {}", request.getId());

            if (request.getId() == null || request.getId().isEmpty()) {
                return ResponseEntity.ok(ApiResponse.error("会话ID不能为空"));
            }

            SessionInfo session = sessions.get(request.getId());
            if (session != null) {
                session.clearHistory();
                return ResponseEntity.ok(ApiResponse.success("会话历史已清空"));
            } else {
                return ResponseEntity.ok(ApiResponse.error("会话不存在"));
            }

        } catch (Exception e) {
            logger.error("清空会话历史失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * ReactAgent 对话接口（SSE 流式模式，支持多轮对话，支持自动工具调用，例如获取当前时间，查询日志，告警等）
     * 支持 session 管理，保留对话历史
     */
    @PostMapping(value = "/chat_stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(300000L); // 5分钟超时

        // 参数校验
        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            logger.warn("问题内容为空");
            try {
                emitter.send(SseEmitter.event().name("message").data(SseMessage.error("问题内容不能为空"), MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        SessionInfo session = getOrCreateSession(request.getId());
        TraceContext asyncTraceContext = getOrCreateTraceContext(session.getSessionId());

        executor.execute(() -> {
            try {
                logger.info("收到 ReactAgent 对话请求 - SessionId: {}, Question: {}", request.getId(), request.getQuestion());

                // 获取或创建会话
                TraceContextHolder.set(asyncTraceContext);
                if (!sendSseMessage(
                        emitter,
                        SseMessage.traceReference(asyncTraceContext.traceId(), asyncTraceContext.sessionId()))) {
                    TraceContextHolder.clear();
                    return;
                }
                
                // 获取历史消息
                List<Map<String, String>> history = session.getHistory();
                logger.info("ReactAgent 会话历史消息对数: {}", history.size() / 2);

                // 创建 DashScope API 和 ChatModel
                DashScopeApi dashScopeApi = chatService.createDashScopeApi();
                DashScopeChatModel chatModel = chatService.createStandardChatModel(dashScopeApi);

                // 记录可用工具
                chatService.logAvailableTools();

                logger.info("开始 ReactAgent 流式对话（支持自动工具调用）");
                
                // 构建系统提示词（包含历史消息）
                String systemPrompt = chatService.buildSystemPrompt(history);
                
                // 创建 ReactAgent
                ReactAgent agent = chatService.createReactAgent(chatModel, systemPrompt);
                
                // 用于累积完整答案
                StringBuilder fullAnswerBuilder = new StringBuilder();
                
                // 使用 agent.stream() 进行流式对话
                Flux<NodeOutput> stream = agent.stream(request.getQuestion());
                
                stream.subscribe(
                    output -> {
                        try {
                            // 检查是否为 StreamingOutput 类型
                            if (output instanceof StreamingOutput streamingOutput) {
                                OutputType type = streamingOutput.getOutputType();
                                
                                // 处理模型推理的流式输出
                                if (type == OutputType.AGENT_MODEL_STREAMING) {
                                    // 流式增量内容，逐步显示
                                    String chunk = streamingOutput.message().getText();
                                    if (chunk != null && !chunk.isEmpty()) {
                                        fullAnswerBuilder.append(chunk);
                                        
                                        // 实时发送到前端
                                        emitter.send(SseEmitter.event()
                                                .name("message")
                                                .data(SseMessage.content(chunk), MediaType.APPLICATION_JSON));
                                        
                                        logger.info("发送流式内容: {}", chunk);
                                    }
                                } else if (type == OutputType.AGENT_MODEL_FINISHED) {
                                    // 模型推理完成
                                    logger.info("模型输出完成");
                                } else if (type == OutputType.AGENT_TOOL_FINISHED) {
                                    // 工具调用完成
                                    logger.info("工具调用完成: {}", output.node());
                                } else if (type == OutputType.AGENT_HOOK_FINISHED) {
                                    // Hook 执行完成
                                    logger.debug("Hook 执行完成: {}", output.node());
                                }
                            }
                        } catch (IOException e) {
                            logger.error("发送流式消息失败", e);
                            throw new RuntimeException(e);
                        }
                    },
                    error -> {
                        // 错误处理
                        if (fullAnswerBuilder.length() == 0 && chatService.isExternalToolTransportFailure(error)) {
                            logger.warn("External MCP tool transport failed for /api/chat_stream, retrying without external callbacks", error);
                            try {
                                ReactAgent fallbackAgent = chatService.createLocalOnlyReactAgent(chatModel, systemPrompt);
                                String fallbackAnswer = chatService.executeChat(fallbackAgent, request.getQuestion());
                                session.addMessage(request.getQuestion(), fallbackAnswer);
                                if (!sendSseMessage(emitter, SseMessage.content(fallbackAnswer))) {
                                    return;
                                }
                                sendSseMessage(emitter, SseMessage.done());
                                emitter.complete();
                            } catch (Exception fallbackError) {
                                logger.error("Fallback chat_stream execution failed", fallbackError);
                                try {
                                    emitter.send(SseEmitter.event()
                                            .name("message")
                                            .data(SseMessage.error(fallbackError.getMessage()), MediaType.APPLICATION_JSON));
                                } catch (IOException ex) {
                                    logger.error("发送错误消息失败", ex);
                                }
                                emitter.completeWithError(fallbackError);
                            }
                            return;
                        }
                        logger.error("ReactAgent 流式对话失败", error);
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("message")
                                    .data(SseMessage.error(error.getMessage()), MediaType.APPLICATION_JSON));
                        } catch (IOException ex) {
                            logger.error("发送错误消息失败", ex);
                        }
                        emitter.completeWithError(error);
                    },
                    () -> {
                        // 完成处理
                        try {
                            String fullAnswer = fullAnswerBuilder.toString();
                            logger.info("ReactAgent 流式对话完成 - SessionId: {}, 答案长度: {}", 
                                request.getId(), fullAnswer.length());
                            
                            // 更新会话历史
                            session.addMessage(request.getQuestion(), fullAnswer);
                            logger.info("已更新会话历史 - SessionId: {}, 当前消息对数: {}", 
                                request.getId(), session.getMessagePairCount());
                            
                            // 发送完成标记
                            emitter.send(SseEmitter.event()
                                    .name("message")
                                    .data(SseMessage.done(), MediaType.APPLICATION_JSON));
                            emitter.complete();
                        } catch (IOException e) {
                            logger.error("发送完成消息失败", e);
                            emitter.completeWithError(e);
                        }
                    }
                );

            } catch (Exception e) {
                if (isClientDisconnect(e)) {
                    logger.info("SSE client disconnected: {}", e.getMessage());
                    return;
                }
                logger.error("ReactAgent 对话初始化失败", e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("message")
                            .data(SseMessage.error(e.getMessage()), MediaType.APPLICATION_JSON));
                } catch (IOException ex) {
                    logger.error("发送错误消息失败", ex);
                }
                emitter.completeWithError(e);
            } finally {
                TraceContextHolder.clear();
            }
        });

        return emitter;
    }

    /**
     * AI 智能运维接口（SSE 流式模式）- 自动分析告警并生成运维报告
     * 无需用户输入，自动执行告警分析流程
     */
    @PostMapping(value = "/ai_ops", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter aiOps() {
        TraceContext asyncTraceContext = getOrCreateTraceContext("aiops-" + UUID.randomUUID());
        String traceId = asyncTraceContext.traceId();
        String traceSessionId = asyncTraceContext.sessionId();
        SseEmitter emitter = new SseEmitter(600000L); // 10分钟超时（告警分析可能较慢）

        executor.execute(() -> {
            try {
                logger.info("收到 AI 智能运维请求 - 启动多 Agent 协作流程");

                DashScopeApi dashScopeApi = chatService.createDashScopeApi();
                TraceContextHolder.set(asyncTraceContext);
                if (!sendSseMessage(emitter, SseMessage.traceReference(traceId, traceSessionId))) {
                    TraceContextHolder.clear();
                    return;
                }
                DashScopeChatModel chatModel = DashScopeChatModel.builder()
                        .dashScopeApi(dashScopeApi)
                        .defaultOptions(DashScopeChatOptions.builder()
                                .withModel(DashScopeChatModel.DEFAULT_MODEL_NAME)
                                .withTemperature(0.3)
                                .withMaxToken(8000)
                                .withTopP(0.9)
                                .build())
                        .build();

                ToolCallback[] toolCallbacks = getToolCallbacks();

                if (aiOpsService != null) {
                    AiOpsService.AiOpsWorkflowOutcome workflowOutcome;
                    try {
                        workflowOutcome = aiOpsService.executeAiOpsWorkflow(
                                chatModel,
                                toolCallbacks,
                                asyncTraceContext
                        );
                    } catch (Exception workflowError) {
                        if (!chatService.isExternalToolTransportFailure(workflowError) || toolCallbacks.length == 0) {
                            throw workflowError;
                        }
                        logger.warn("External MCP tool transport failed for /api/ai_ops, retrying without external callbacks", workflowError);
                        workflowOutcome = aiOpsService.executeAiOpsWorkflow(
                                chatModel,
                                new ToolCallback[0],
                                asyncTraceContext
                        );
                    }

                    if (workflowOutcome.replanned()) {
                        if (!sendSseMessage(
                                emitter,
                                SseMessage.traceReference(
                                        workflowOutcome.traceContext().traceId(),
                                        workflowOutcome.traceContext().sessionId()))) {
                            TraceContextHolder.clear();
                            return;
                        }
                        sendSseMessage(emitter, SseMessage.content("首轮分析未得到有效结论，已切换重规划分支继续分析...\n"));
                    }

                    if (workflowOutcome.finalReport().isPresent()) {
                        String finalReportText = workflowOutcome.finalReport().get();
                        aiOpsService.completeAiOpsTraceSession(workflowOutcome.traceContext().traceId(), finalReportText, null);
                        logger.info("Extracted final AI Ops report, length={}", finalReportText.length());

                        emitter.send(SseEmitter.event().name("message")
                                .data(SseMessage.content("\n\n" + "=".repeat(60) + "\n"), MediaType.APPLICATION_JSON));
                        emitter.send(SseEmitter.event().name("message")
                                .data(SseMessage.content("## 告警分析报告\n\n"), MediaType.APPLICATION_JSON));

                        int chunkSize = 500;
                        for (int i = 0; i < finalReportText.length(); i += chunkSize) {
                            int end = Math.min(i + chunkSize, finalReportText.length());
                            String chunk = finalReportText.substring(i, end);
                            if (!sendSseMessage(emitter, SseMessage.content(chunk))) {
                                return;
                            }
                        }

                        emitter.send(SseEmitter.event().name("message")
                                .data(SseMessage.content("\n" + "=".repeat(60) + "\n\n"), MediaType.APPLICATION_JSON));
                    } else {
                        String failureMessage = workflowOutcome.failureMessage();
                        aiOpsService.completeAiOpsTraceSession(
                                workflowOutcome.traceContext().traceId(),
                                null,
                                failureMessage
                        );
                        emitter.send(SseEmitter.event().name("message")
                                .data(SseMessage.error(
                                        failureMessage == null || failureMessage.isBlank()
                                                ? "多 Agent 流程未获取到有效结果"
                                                : failureMessage
                                ), MediaType.APPLICATION_JSON));
                        emitter.complete();
                        TraceContextHolder.clear();
                        return;
                    }

                    if (!sendSseMessage(emitter, SseMessage.done())) {
                        TraceContextHolder.clear();
                        return;
                    }
                    emitter.complete();
                    logger.info("AI Ops workflow completed");
                    return;
                }

                emitter.send(SseEmitter.event().name("message").data(SseMessage.content("正在读取告警并拆解任务...\n")));
                
                // 调用 AiOpsService 执行分析流程
                Optional<OverAllState> overAllStateOptional = aiOpsService.executeAiOpsAnalysis(
                        chatModel,
                        toolCallbacks,
                        traceId,
                        traceSessionId
                );

                if (overAllStateOptional.isEmpty()) {
                    aiOpsService.recordWorkflowReplan(
                            traceId,
                            traceSessionId,
                            "supervisor_agent",
                            ReplanReason.EMPTY_RESULT,
                            "multi-agent workflow returned empty result"
                    );
                    aiOpsService.completeAiOpsTraceSession(traceId, null, "multi-agent workflow returned empty result");
                    emitter.send(SseEmitter.event().name("message")
                            .data(SseMessage.error("多 Agent 编排未获取到有效结果"), MediaType.APPLICATION_JSON));
                    emitter.complete();
                    TraceContextHolder.clear();
                    return;
                }

                OverAllState state = overAllStateOptional.get();
                logger.info("AI Ops 编排完成，开始提取最终报告...");

                // 提取最终报告
                Optional<String> finalReportOptional = aiOpsService.extractFinalReport(state);

                // 输出最终报告
                if (finalReportOptional.isPresent()) {
                    String finalReportText = finalReportOptional.get();
                    aiOpsService.completeAiOpsTraceSession(traceId, finalReportText, null);
                    logger.info("提取到 Planner 最终报告，长度: {}", finalReportText.length());
                    
                    // 发送分隔线
                    emitter.send(SseEmitter.event().name("message")
                            .data(SseMessage.content("\n\n" + "=".repeat(60) + "\n"), MediaType.APPLICATION_JSON));
                    
                    // 发送完整的告警分析报告
                    emitter.send(SseEmitter.event().name("message")
                            .data(SseMessage.content("📋 **告警分析报告**\n\n"), MediaType.APPLICATION_JSON));
                    
                    int chunkSize = 500;
                    for (int i = 0; i < finalReportText.length(); i += chunkSize) {
                        int end = Math.min(i + chunkSize, finalReportText.length());
                        String chunk = finalReportText.substring(i, end);
                        
                        if (!sendSseMessage(emitter, SseMessage.content(chunk))) {
                            return;
                        }
                    }
                    
                    // 发送结束分隔线
                    emitter.send(SseEmitter.event().name("message")
                            .data(SseMessage.content("\n" + "=".repeat(60) + "\n\n"), MediaType.APPLICATION_JSON));
                    
                    logger.info("最终报告已完整输出");
                } else {
                    logger.warn("未能提取到 Planner 最终报告");
                    emitter.send(SseEmitter.event().name("message")
                            .data(SseMessage.content("⚠️ 多 Agent 流程已完成，但未能生成最终报告。"), MediaType.APPLICATION_JSON));
                }

                if (finalReportOptional.isEmpty()) {
                    aiOpsService.recordWorkflowReplan(
                            traceId,
                            traceSessionId,
                            "planner_agent",
                            ReplanReason.EMPTY_RESULT,
                            "planner final report missing"
                    );
                    aiOpsService.completeAiOpsTraceSession(traceId, null, "final report missing");
                }

                if (!sendSseMessage(emitter, SseMessage.done())) {
                    TraceContextHolder.clear();
                    return;
                }
                emitter.complete();
                logger.info("AI Ops 多 Agent 编排完成");

            } catch (Exception e) {
                aiOpsService.recordWorkflowReplan(
                        traceId,
                        traceSessionId,
                        "supervisor_agent",
                        ReplanReason.UNKNOWN,
                        e.getMessage()
                );
                aiOpsService.completeAiOpsTraceSession(traceId, null, e.getMessage());
                if (isClientDisconnect(e)) {
                    logger.info("AI Ops SSE client disconnected: {}", e.getMessage());
                    return;
                }
                logger.error("AI Ops 多 Agent 协作失败", e);
                try {
                    emitter.send(SseEmitter.event().name("message")
                            .data(SseMessage.error("AI Ops 流程失败: " + e.getMessage()), MediaType.APPLICATION_JSON));
                } catch (IOException ex) {
                    logger.error("发送错误消息失败", ex);
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }


    /**
     * 获取会话信息
     */
    @GetMapping("/chat/session/{sessionId}")
    public ResponseEntity<ApiResponse<SessionInfoResponse>> getSessionInfo(@PathVariable String sessionId) {
        try {
            logger.info("收到获取会话信息请求 - SessionId: {}", sessionId);

            SessionInfo session = sessions.get(sessionId);
            if (session != null) {
                SessionInfoResponse response = new SessionInfoResponse();
                response.setSessionId(sessionId);
                response.setMessagePairCount(session.getMessagePairCount());
                response.setCreateTime(session.createTime);
                return ResponseEntity.ok(ApiResponse.success(response));
            } else {
                return ResponseEntity.ok(ApiResponse.error("会话不存在"));
            }

        } catch (Exception e) {
            logger.error("获取会话信息失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== 辅助方法 ====================

    private SessionInfo getOrCreateSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        }
        return sessions.computeIfAbsent(sessionId, SessionInfo::new);
    }

    private ToolCallback[] getToolCallbacks() {
        if (tools == null) {
            logger.info("MCP tool callbacks are unavailable; continuing without external tool callbacks");
            return new ToolCallback[0];
        }
        return tools.getToolCallbacks();
    }

    private TraceContext getOrCreateTraceContext(String fallbackSessionId) {
        TraceContext existing = TraceContextHolder.get();
        if (existing != null) {
            if (existing.sessionId() == null || existing.sessionId().isEmpty()) {
                return new TraceContext(existing.traceId(), fallbackSessionId, existing.requestId(), existing.userId());
            }
            return existing;
        }
        return new TraceContext(
                UUID.randomUUID().toString(),
                fallbackSessionId,
                UUID.randomUUID().toString(),
                "anonymous"
        );
    }

    private boolean sendSseMessage(SseEmitter emitter, SseMessage message) {
        try {
            emitter.send(SseEmitter.event().name("message").data(message, MediaType.APPLICATION_JSON));
            return true;
        } catch (IOException | IllegalStateException e) {
            logger.info("SSE client disconnected: {}", e.getMessage());
            try {
                emitter.complete();
            } catch (IllegalStateException ignored) {
                // Already completed by the container.
            }
            return false;
        }
    }

    private boolean isClientDisconnect(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String className = current.getClass().getName();
            String message = current.getMessage();
            if (className.contains("ClientAbortException")) {
                return true;
            }
            if (current instanceof IOException && message != null
                    && (message.contains("中止") || message.contains("Broken pipe") || message.contains("Connection reset"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    // ==================== 内部类 ====================

    /**
     * 会话信息
     * 管理单个会话的历史消息，支持自动清理和线程安全
     */
    private static class SessionInfo {
        private final String sessionId;
        // 存储历史消息对：[{"role": "user", "content": "..."}, {"role": "assistant", "content": "..."}]
        private final List<Map<String, String>> messageHistory;
        private final long createTime;
        private final ReentrantLock lock;

        public SessionInfo(String sessionId) {
            this.sessionId = sessionId;
            this.messageHistory = new ArrayList<>();
            this.createTime = System.currentTimeMillis();
            this.lock = new ReentrantLock();
        }

        /**
         * 添加一对消息（用户问题 + AI回复）
         * 自动管理历史消息窗口大小
         */
        public void addMessage(String userQuestion, String aiAnswer) {
            lock.lock();
            try {
                // 添加用户消息
                Map<String, String> userMsg = new HashMap<>();
                userMsg.put("role", "user");
                userMsg.put("content", userQuestion);
                messageHistory.add(userMsg);

                // 添加AI回复
                Map<String, String> assistantMsg = new HashMap<>();
                assistantMsg.put("role", "assistant");
                assistantMsg.put("content", aiAnswer);
                messageHistory.add(assistantMsg);

                // 自动清理：保持最多 MAX_WINDOW_SIZE 对消息
                // 每对消息包含2条记录（user + assistant）
                int maxMessages = MAX_WINDOW_SIZE * 2;
                while (messageHistory.size() > maxMessages) {
                    // 成对删除最旧的消息（删除前2条）
                    messageHistory.remove(0); // 删除最旧的用户消息
                    if (!messageHistory.isEmpty()) {
                        messageHistory.remove(0); // 删除对应的AI回复
                    }
                }

                logger.debug("会话 {} 更新历史消息，当前消息对数: {}", 
                    sessionId, messageHistory.size() / 2);

            } finally {
                lock.unlock();
            }
        }

        /**
         * 获取历史消息（线程安全）
         * 返回副本以避免并发修改
         */
        public List<Map<String, String>> getHistory() {
            lock.lock();
            try {
                return new ArrayList<>(messageHistory);
            } finally {
                lock.unlock();
            }
        }

        /**
         * 清空历史消息
         */
        public void clearHistory() {
            lock.lock();
            try {
                messageHistory.clear();
                logger.info("会话 {} 历史消息已清空", sessionId);
            } finally {
                lock.unlock();
            }
        }

        /**
         * 获取当前消息对数
         */
        public int getMessagePairCount() {
            lock.lock();
            try {
                return messageHistory.size() / 2;
            } finally {
                lock.unlock();
            }
        }

        public String getSessionId() {
            return sessionId;
        }
    }

    /**
     * 聊天请求
     */
    @Setter
    @Getter
    public static class ChatRequest {
        @com.fasterxml.jackson.annotation.JsonProperty(value = "Id")
        @com.fasterxml.jackson.annotation.JsonAlias({"id", "ID"})
        private String Id;
        
        @com.fasterxml.jackson.annotation.JsonProperty(value = "Question")
        @com.fasterxml.jackson.annotation.JsonAlias({"question", "QUESTION"})
        private String Question;

    }

    /**
     * 清空会话请求
     */
    @Setter
    @Getter
    public static class ClearRequest {
        @com.fasterxml.jackson.annotation.JsonProperty(value = "Id")
        @com.fasterxml.jackson.annotation.JsonAlias({"id", "ID"})
        private String Id;
    }

    // ==================== 内部类 ====================

    /**
     * 会话信息响应
     */
    @Setter
    @Getter
    public static class SessionInfoResponse {
        private String sessionId;
        private int messagePairCount;
        private long createTime;
    }

    /**
     * 统一聊天响应格式
     * 适用于所有普通返回模式的对话接口
     */
    @Setter
    @Getter
    public static class ChatResponse {
        private boolean success;
        private String answer;
        private String errorMessage;

        public static ChatResponse success(String answer) {
            ChatResponse response = new ChatResponse();
            response.setSuccess(true);
            response.setAnswer(answer);
            return response;
        }

        public static ChatResponse error(String errorMessage) {
            ChatResponse response = new ChatResponse();
            response.setSuccess(false);
            response.setErrorMessage(errorMessage);
            return response;
        }
    }

    /**
     * 统一 SSE 流式消息格式
     * 适用于所有 SSE 流式返回模式的对话接口
     */
    @Setter
    @Getter
    public static class SseMessage {
        private String type;  // content: 内容块, error: 错误, done: 完成
        private String data;

        public static SseMessage content(String data) {
            SseMessage message = new SseMessage();
            message.setType("content");
            message.setData(data);
            return message;
        }

        public static SseMessage error(String errorMessage) {
            SseMessage message = new SseMessage();
            message.setType("error");
            message.setData(errorMessage);
            return message;
        }

        public static SseMessage done() {
            SseMessage message = new SseMessage();
            message.setType("done");
            message.setData(null);
            return message;
        }

        public static SseMessage traceReference(String traceId, String sessionId) {
            SseMessage message = new SseMessage();
            message.setType("trace_ref");
            message.setData(String.format(
                    "{\"traceId\":\"%s\",\"sessionId\":\"%s\"}",
                    escapeJson(traceId),
                    escapeJson(sessionId)
            ));
            return message;
        }

        private static String escapeJson(String value) {
            if (value == null) {
                return "";
            }
            return value
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"");
        }
    }


    @Getter
    @Setter
    public static class ApiResponse<T> {
        private int code;
        private String message;
        private T data;

        public static <T> ApiResponse<T> success(T data) {
            ApiResponse<T> response = new ApiResponse<>();
            response.setCode(200);
            response.setMessage("success");
            response.setData(data);
            return response;
        }

        public static <T> ApiResponse<T> error(String message) {
            ApiResponse<T> response = new ApiResponse<>();
            response.setCode(500);
            response.setMessage(message);
            return response;
        }

    }
}
