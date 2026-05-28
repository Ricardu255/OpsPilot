# SuperBizAgent Trace P0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 AI Ops 主链路补齐持久化基础设施、Trace 最小落库闭环、Replan 状态骨架，并为后续相似工单检索预留可扩展模型。

**Architecture:** 保持现有 `ChatController -> AiOpsService -> planner/executor/tools` 主链路不变，在外层增加请求级 Trace 上下文，在服务层增加 Trace 持久化服务，在工具与步骤执行点写入结构化记录。第一阶段只追踪 AI Ops 流程，不改普通聊天的状态存储方式。

**Tech Stack:** Spring Boot 3.2、Spring AI Alibaba、Spring Data JPA、MySQL、JUnit 5、Mockito、Milvus。

---

### Task 1: 持久化与 Trace 基础设施

**Files:**
- Create: `src/main/java/org/example/trace/TraceContext.java`
- Create: `src/main/java/org/example/trace/TraceContextHolder.java`
- Create: `src/main/java/org/example/trace/TraceInterceptor.java`
- Create: `src/main/java/org/example/trace/model/AgentSessionTrace.java`
- Create: `src/main/java/org/example/trace/model/AgentTraceStep.java`
- Create: `src/main/java/org/example/trace/model/AgentToolCallTrace.java`
- Create: `src/main/java/org/example/trace/model/AgentReplanRecord.java`
- Create: `src/main/java/org/example/trace/repository/AgentSessionTraceRepository.java`
- Create: `src/main/java/org/example/trace/repository/AgentTraceStepRepository.java`
- Create: `src/main/java/org/example/trace/repository/AgentToolCallTraceRepository.java`
- Create: `src/main/java/org/example/trace/repository/AgentReplanRecordRepository.java`
- Create: `src/main/java/org/example/trace/service/AgentTraceService.java`
- Modify: `pom.xml`
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/java/org/example/config/WebMvcConfig.java`
- Test: `src/test/java/org/example/trace/AgentTraceServiceTest.java`
- Test: `src/test/java/org/example/trace/TraceInterceptorTest.java`

- [ ] **Step 1: 写 Trace 服务失败测试**

```java
@Test
void startSessionCreatesRunningTraceRecord() {
    AgentSessionTraceRepository repository = mock(AgentSessionTraceRepository.class);
    AgentTraceService service = new AgentTraceService(repository, null, null, null);

    when(repository.save(any(AgentSessionTrace.class))).thenAnswer(invocation -> invocation.getArgument(0));

    AgentSessionTrace saved = service.startSession("trace-1", "session-1", "AIOPS_AGENT", "alert body");

    assertThat(saved.getTraceId()).isEqualTo("trace-1");
    assertThat(saved.getSessionId()).isEqualTo("session-1");
    assertThat(saved.getStatus()).isEqualTo("RUNNING");
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -Dtest=AgentTraceServiceTest,TraceInterceptorTest test`
Expected: 编译失败，提示 `AgentTraceService` / `TraceInterceptor` 不存在。

- [ ] **Step 3: 接入持久化依赖与最小配置**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/super_biz_agent?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
    username: root
    password: root
  jpa:
    hibernate:
      ddl-auto: update
    open-in-view: false
    properties:
      hibernate:
        format_sql: true

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    prometheus:
      enabled: true
```

- [ ] **Step 4: 实现最小 Trace 模型与服务**

```java
public record TraceContext(String traceId, String sessionId, String requestId, String userId) {
}
```

```java
public class TraceContextHolder {
    private static final ThreadLocal<TraceContext> CONTEXT = new ThreadLocal<>();

    public static void set(TraceContext context) { CONTEXT.set(context); }
    public static TraceContext get() { return CONTEXT.get(); }
    public static void clear() { CONTEXT.remove(); }
}
```

```java
public AgentSessionTrace startSession(String traceId, String sessionId, String agentType, String userInput) {
    AgentSessionTrace trace = new AgentSessionTrace();
    trace.setTraceId(traceId);
    trace.setSessionId(sessionId);
    trace.setAgentType(agentType);
    trace.setUserInput(userInput);
    trace.setStatus("RUNNING");
    trace.setStartTime(LocalDateTime.now());
    return agentSessionTraceRepository.save(trace);
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `mvn -q -Dtest=AgentTraceServiceTest,TraceInterceptorTest test`
Expected: PASS

### Task 2: AI Ops 主链路接入 Trace 与 Replan 规则骨架

**Files:**
- Create: `src/main/java/org/example/aiops/ReplanReason.java`
- Create: `src/main/java/org/example/aiops/ReplanDecision.java`
- Create: `src/main/java/org/example/aiops/ReplanDecisionEvaluator.java`
- Modify: `src/main/java/org/example/service/AiOpsService.java`
- Modify: `src/main/java/org/example/controller/ChatController.java`
- Test: `src/test/java/org/example/service/AiOpsServiceTraceTest.java`

- [ ] **Step 1: 写 AI Ops Trace 与 Replan 失败测试**

```java
@Test
void recordPlannerStepWhenAiOpsSessionStarts() {
    AgentTraceService traceService = mock(AgentTraceService.class);
    AiOpsService service = new AiOpsService();

    ReflectionTestUtils.setField(service, "agentTraceService", traceService);

    service.recordPlannerStepForTest("trace-1", "session-1", "input");

    verify(traceService).recordStep(eq("trace-1"), eq("session-1"), eq("planner_agent"), anyString(), eq("RUNNING"));
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -Dtest=AiOpsServiceTraceTest test`
Expected: 编译失败，提示新增方法或依赖不存在。

- [ ] **Step 3: 实现 Replan 骨架与 Trace 接入**

```java
public enum ReplanReason {
    TOOL_FAILED,
    EMPTY_RESULT,
    LOW_CONFIDENCE,
    UNKNOWN
}
```

```java
public ReplanDecision evaluateToolResult(String toolName, String result, String errorMessage, int failureCount) {
    if (errorMessage != null && !errorMessage.isBlank()) {
        return failureCount >= 2
                ? ReplanDecision.replan(ReplanReason.TOOL_FAILED, "连续失败，切换策略")
                : ReplanDecision.retry(ReplanReason.TOOL_FAILED, "工具失败，先重试");
    }
    if (result == null || result.isBlank()) {
        return ReplanDecision.replan(ReplanReason.EMPTY_RESULT, "工具返回空结果");
    }
    return ReplanDecision.continueExecution();
}
```

- [ ] **Step 4: 在 AI Ops 入口与 planner/executor 关键点写 Trace**

```java
String traceId = UUID.randomUUID().toString();
agentTraceService.startSession(traceId, traceSessionId, "AIOPS_AGENT", taskPrompt);
agentTraceService.recordStep(traceId, traceSessionId, "planner_agent", "开始规划", "RUNNING");
```

- [ ] **Step 5: 运行测试确认通过**

Run: `mvn -q -Dtest=AiOpsServiceTest,AiOpsServiceTraceTest test`
Expected: PASS

### Task 3: 工具调用 Trace 与相似工单模型预留

**Files:**
- Create: `src/main/java/org/example/ticket/model/TicketCase.java`
- Create: `src/main/java/org/example/ticket/model/TicketCaseEmbedding.java`
- Modify: `src/main/java/org/example/agent/tool/QueryMetricsTools.java`
- Modify: `src/main/java/org/example/agent/tool/QueryLogsTools.java`
- Modify: `src/main/java/org/example/agent/tool/InternalDocsTools.java`
- Modify: `src/main/java/org/example/service/VectorSearchService.java`
- Test: `src/test/java/org/example/trace/ToolTraceIntegrationTest.java`

- [ ] **Step 1: 写工具调用 Trace 测试**

```java
@Test
void internalDocsToolRecordsToolCallTraceOnSuccess() {
    AgentTraceService traceService = mock(AgentTraceService.class);
    VectorSearchService vectorSearchService = mock(VectorSearchService.class);
    InternalDocsTools tools = new InternalDocsTools(vectorSearchService);

    ReflectionTestUtils.setField(tools, "agentTraceService", traceService);
    when(vectorSearchService.searchSimilarDocuments("cpu high", 3)).thenReturn(List.of());

    tools.queryInternalDocs("cpu high");

    verify(traceService).recordToolCall(anyString(), eq("queryInternalDocs"), anyString(), anyString(), eq("SUCCESS"));
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -Dtest=ToolTraceIntegrationTest test`
Expected: 编译失败，提示 `agentTraceService` 或 `recordToolCall` 不存在。

- [ ] **Step 3: 实现工具调用 Trace 和工单模型**

```java
public void recordToolCall(String traceId, String toolName, String params, String result, String status) {
    AgentToolCallTrace trace = new AgentToolCallTrace();
    trace.setTraceId(traceId);
    trace.setToolName(toolName);
    trace.setToolParams(params);
    trace.setToolResult(result);
    trace.setStatus(status);
    trace.setCreatedAt(LocalDateTime.now());
    agentToolCallTraceRepository.save(trace);
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -q -Dtest=ToolTraceIntegrationTest test`
Expected: PASS

- [ ] **Step 5: 运行最小回归**

Run: `mvn -q test`
Expected: 全部测试通过
