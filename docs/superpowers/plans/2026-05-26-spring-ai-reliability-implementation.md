# Spring AI Reliability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为当前 Spring AI AI Ops 链路补上统一的错误分类与自动重试、工具三阶段生命周期最小闭环，以及 trace 会话树最小字段与查询能力。

**Architecture:** 保持现有 `DashScopeChatModel + ReactAgent + ToolCallback + ToolTraceHelper + Trace` 主链路不动，在外围增加一个可靠性层。第一阶段将错误分类和重试能力嵌入 `ToolTraceHelper` 与 `AgentTraceService`；第二阶段把工具执行前后拆成 `prepare / execute / finalize`；第三阶段在 `agent_session` 增加父子链路字段，并扩展查询接口返回分支关系。

**Tech Stack:** Spring Boot 3.2, Spring AI Alibaba, Spring Retry, Spring Data JPA, Flyway, JUnit 5, Mockito, H2

---

### Task 1: 错误分类与自动重试

**Files:**
- Create: `src/main/java/org/example/reliability/error/AiErrorCategory.java`
- Create: `src/main/java/org/example/reliability/error/AiErrorClassification.java`
- Create: `src/main/java/org/example/reliability/error/AiErrorClassifier.java`
- Create: `src/main/java/org/example/reliability/error/RetryExecutor.java`
- Modify: `src/main/java/org/example/trace/ToolTraceHelper.java`
- Modify: `src/main/java/org/example/trace/service/AgentTraceService.java`
- Modify: `src/main/java/org/example/trace/model/AgentToolCallTrace.java`
- Modify: `src/main/resources/db/migration/V1__create_trace_tables.sql`
- Test: `src/test/java/org/example/trace/ToolTraceHelperTest.java`
- Test: `src/test/java/org/example/trace/AgentTraceServiceTest.java`

- [ ] 先写 tool trace 的失败分类与重试测试
- [ ] 跑测试确认失败
- [ ] 实现最小错误分类器与 retry executor
- [ ] 把 retry / error category 写入 tool trace
- [ ] 跑测试确认通过

### Task 2: 工具三阶段生命周期

**Files:**
- Create: `src/main/java/org/example/tool/pipeline/ToolExecutionRequest.java`
- Create: `src/main/java/org/example/tool/pipeline/ToolExecutionResult.java`
- Create: `src/main/java/org/example/tool/pipeline/ToolExecutionPipeline.java`
- Modify: `src/main/java/org/example/trace/ToolTraceHelper.java`
- Modify: `src/main/java/org/example/agent/tool/QueryMetricsTools.java`
- Modify: `src/main/java/org/example/agent/tool/QueryLogsTools.java`
- Modify: `src/main/java/org/example/agent/tool/InternalDocsTools.java`
- Test: `src/test/java/org/example/trace/ToolTraceHelperTest.java`

- [ ] 先写 prepare/execute/finalize 行为测试
- [ ] 跑测试确认失败
- [ ] 实现 pipeline 和 helper 整合
- [ ] 让三个核心工具统一走 pipeline
- [ ] 跑测试确认通过

### Task 3: 会话树最小模型

**Files:**
- Create: `src/main/resources/db/migration/V2__add_trace_tree_fields.sql`
- Modify: `src/main/java/org/example/trace/model/AgentSessionTrace.java`
- Modify: `src/main/java/org/example/trace/repository/AgentSessionTraceRepository.java`
- Modify: `src/main/java/org/example/trace/service/AgentTraceService.java`
- Modify: `src/main/java/org/example/trace/service/TraceQueryService.java`
- Modify: `src/main/java/org/example/trace/dto/TraceSessionSummaryResponse.java`
- Modify: `src/main/java/org/example/trace/dto/TraceDetailResponse.java`
- Modify: `src/main/java/org/example/controller/TraceController.java`
- Modify: `src/main/java/org/example/service/AiOpsService.java`
- Test: `src/test/java/org/example/trace/TraceQueryServiceTest.java`
- Test: `src/test/java/org/example/trace/TraceControllerIntegrationTest.java`
- Test: `src/test/java/org/example/service/AiOpsServiceTraceTest.java`

- [ ] 先写 root/parent/branch 查询测试
- [ ] 跑测试确认失败
- [ ] 实现最小表结构与实体字段
- [ ] 在 AI Ops replan / 新会话处补父子链路
- [ ] 扩展查询接口返回父子关系
- [ ] 跑测试确认通过

### Task 4: 回归验证

**Files:**
- Test: `src/test/java/org/example/trace/*.java`
- Test: `src/test/java/org/example/service/AiOpsServiceTraceTest.java`
- Test: `src/test/java/org/example/controller/TraceControllerTest.java`

- [ ] 跑可靠性相关测试集
- [ ] 跑 trace 查询相关测试集
- [ ] 记录剩余限制与后续项
