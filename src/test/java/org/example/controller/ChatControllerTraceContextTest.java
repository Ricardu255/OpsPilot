package org.example.controller;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.example.service.ChatService;
import org.example.trace.TraceContext;
import org.example.trace.TraceContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class ChatControllerTraceContextTest {

    @AfterEach
    void clearTraceContext() {
        TraceContextHolder.clear();
    }

    @Test
    void getOrCreateTraceContextBackfillsMissingSessionId() {
        ChatController controller = new ChatController();
        TraceContextHolder.set(new TraceContext("trace-1", null, "request-1", "user-1"));

        TraceContext context = (TraceContext) ReflectionTestUtils.invokeMethod(
                controller,
                "getOrCreateTraceContext",
                "session-123"
        );

        assertThat(context).isNotNull();
        assertThat(context.traceId()).isEqualTo("trace-1");
        assertThat(context.requestId()).isEqualTo("request-1");
        assertThat(context.sessionId()).isEqualTo("session-123");
    }

    @Test
    void chatSetsTraceContextUsingResolvedSessionId() throws Exception {
        ChatController controller = new ChatController();
        ChatService chatService = mock(ChatService.class);
        DashScopeApi dashScopeApi = mock(DashScopeApi.class);
        DashScopeChatModel chatModel = mock(DashScopeChatModel.class);
        ReactAgent reactAgent = mock(ReactAgent.class);

        ReflectionTestUtils.setField(controller, "chatService", chatService);

        when(chatService.createDashScopeApi()).thenReturn(dashScopeApi);
        when(chatService.createStandardChatModel(dashScopeApi)).thenReturn(chatModel);
        when(chatService.buildSystemPrompt(org.mockito.ArgumentMatchers.anyList())).thenReturn("prompt");
        when(chatService.createReactAgent(chatModel, "prompt")).thenReturn(reactAgent);
        when(chatService.executeChat(reactAgent, "hello")).thenReturn("world");
        doNothing().when(chatService).logAvailableTools();

        TraceContextHolder.set(new TraceContext("trace-1", null, "request-1", "user-1"));

        ChatController.ChatRequest request = new ChatController.ChatRequest();
        request.setId(null);
        request.setQuestion("hello");

        ResponseEntity<ChatController.ApiResponse<ChatController.ChatResponse>> response = controller.chat(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(TraceContextHolder.get()).isNotNull();
        assertThat(TraceContextHolder.get().traceId()).isEqualTo("trace-1");
        assertThat(TraceContextHolder.get().sessionId()).isNotBlank();
    }

    @Test
    void sseTraceReferenceCarriesTraceAndSessionIds() {
        ChatController.SseMessage message = ChatController.SseMessage.traceReference("trace-77", "session-88");

        assertThat(message.getType()).isEqualTo("trace_ref");
        assertThat(message.getData()).contains("trace-77");
        assertThat(message.getData()).contains("session-88");
    }

    @Test
    void chatFallsBackToLocalOnlyAgentWhenExternalMcpTransportFails() throws Exception {
        ChatController controller = new ChatController();
        ChatService chatService = mock(ChatService.class);
        DashScopeApi dashScopeApi = mock(DashScopeApi.class);
        DashScopeChatModel chatModel = mock(DashScopeChatModel.class);
        ReactAgent primaryAgent = mock(ReactAgent.class);
        ReactAgent fallbackAgent = mock(ReactAgent.class);

        ReflectionTestUtils.setField(controller, "chatService", chatService);

        when(chatService.createDashScopeApi()).thenReturn(dashScopeApi);
        when(chatService.createStandardChatModel(dashScopeApi)).thenReturn(chatModel);
        when(chatService.buildSystemPrompt(anyList())).thenReturn("prompt");
        when(chatService.createReactAgent(chatModel, "prompt")).thenReturn(primaryAgent);
        when(chatService.createLocalOnlyReactAgent(chatModel, "prompt")).thenReturn(fallbackAgent);
        when(chatService.isExternalToolTransportFailure(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(chatService.executeChat(primaryAgent, "hello"))
                .thenThrow(new RuntimeException("400 Bad Request from POST http://localhost:3000/messages?sessionId=test"));
        when(chatService.executeChat(fallbackAgent, "hello")).thenReturn("fallback world");
        doNothing().when(chatService).logAvailableTools();

        ChatController.ChatRequest request = new ChatController.ChatRequest();
        request.setId("fallback-session");
        request.setQuestion("hello");

        ResponseEntity<ChatController.ApiResponse<ChatController.ChatResponse>> response = controller.chat(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        ChatController.ChatResponse chatResponse = response.getBody().getData();
        assertThat(chatResponse.isSuccess()).isTrue();
        assertThat(chatResponse.getAnswer()).isEqualTo("fallback world");
        org.mockito.Mockito.verify(chatService, times(2)).executeChat(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("hello"));
    }

    @Test
    void chatStoresHistoryUnderProvidedSessionId() throws Exception {
        ChatController controller = new ChatController();
        ChatService chatService = mock(ChatService.class);
        DashScopeApi dashScopeApi = mock(DashScopeApi.class);
        DashScopeChatModel chatModel = mock(DashScopeChatModel.class);
        ReactAgent reactAgent = mock(ReactAgent.class);

        ReflectionTestUtils.setField(controller, "chatService", chatService);

        when(chatService.createDashScopeApi()).thenReturn(dashScopeApi);
        when(chatService.createStandardChatModel(dashScopeApi)).thenReturn(chatModel);
        when(chatService.buildSystemPrompt(anyList())).thenReturn("prompt");
        when(chatService.createReactAgent(chatModel, "prompt")).thenReturn(reactAgent);
        when(chatService.executeChat(reactAgent, "hello")).thenReturn("world");
        doNothing().when(chatService).logAvailableTools();

        ChatController.ChatRequest request = new ChatController.ChatRequest();
        request.setId("session-fixed");
        request.setQuestion("hello");

        ResponseEntity<ChatController.ApiResponse<ChatController.ChatResponse>> chatResponse = controller.chat(request);
        ResponseEntity<ChatController.ApiResponse<ChatController.SessionInfoResponse>> sessionResponse =
                controller.getSessionInfo("session-fixed");

        assertThat(chatResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(sessionResponse.getBody()).isNotNull();
        assertThat(sessionResponse.getBody().getCode()).isEqualTo(200);
        assertThat(sessionResponse.getBody().getData().getSessionId()).isEqualTo("session-fixed");
        assertThat(sessionResponse.getBody().getData().getMessagePairCount()).isEqualTo(1);
    }
}
