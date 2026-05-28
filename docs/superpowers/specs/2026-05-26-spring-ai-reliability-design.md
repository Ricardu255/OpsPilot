# Spring AI Reliability Design

**Goal:** 为 `SuperBizAgent` 规划三项更适合当前 Spring AI / Spring AI Alibaba 架构的增强能力：`错误分类 + 自动重试`、`会话树模型`、`工具三阶段生命周期`。设计目标是在不推翻现有 `DashScopeChatModel + ReactAgent + ToolCallback + Trace` 主链路的前提下，提高可恢复性、可观测性和后续扩展能力。
**Scope:** 本设计只覆盖能力边界、模块职责、数据模型增量、调用链改造点、测试策略和实施顺序，不直接包含代码实现。

---

## 1. Background

当前项目已经具备以下基础：

- Spring AI / Spring AI Alibaba 依赖已接入：[pom.xml](/D:/code/Java/SuperBizAgent/SuperBizAgent/pom.xml:22)
- 主要对话与 AI Ops 链路以 `DashScopeChatModel + ReactAgent + ToolCallback` 为核心
- Trace 持久化、查询接口、前端控制台和 replan 记录骨架已经存在
- 工具调用已有统一追踪封装：[ToolTraceHelper.java](/D:/code/Java/SuperBizAgent/SuperBizAgent/src/main/java/org/example/trace/ToolTraceHelper.java:1)

当前痛点也比较明确：

1. **错误处理分散**  
   模型调用、工具调用、MCP 调用和外部依赖失败时，错误语义不统一，难以决定“重试 / 终止 / replan”。
2. **会话缺少正式分支语义**  
   已有 replan 记录，但 trace 之间没有正式的父子链路，无法稳定表达 `resume / fork / replan`。
3. **工具调用治理仍偏轻量**  
   现有 `ToolTraceHelper` 更接近“执行后记录”，但缺少执行前校验、执行后决策和统一生命周期挂点。

---

## 2. Design Principles

本设计遵循以下原则：

1. **保持现有主链路稳定**
   不替换现有 `ReactAgent`、不重写 Spring AI agent loop，只在外围加治理层。
2. **优先在应用层而不是框架内核做增强**
   避免为了控制中间 loop 而深度入侵框架内部。
3. **统一决策、分层落地**
   错误分类负责“判断”，重试负责“执行”，trace 负责“记录”，replan 负责“转向”。
4. **先做可恢复性，再做结构化表达**
   先补错误分类和工具生命周期，再沉淀会话树能力。
5. **让 Trace 成为唯一观察面**
   新增的重试、生命周期、父子分支，都应进入现有 Trace 查询体系。

---

## 3. Approaches Considered

### Approach A: 深入改造 Spring AI Agent 执行循环

- 在 agent 内部循环中插入重试、分支和生命周期控制点
- 优点：控制最细
- 缺点：侵入性高，依赖框架内部实现，升级风险大

### Approach B: 保持 Agent 不动，在外围加执行治理层

- 对模型调用、工具调用、workflow trace、replan 决策做统一包装
- 优点：和当前项目最兼容，改造面可控
- 缺点：无法精确控制 graph 内部每个隐式节点

### Approach C: 另起一套自定义 Agent Orchestrator

- 自己维护 planner / executor / supervisor 编排
- 优点：完全可控
- 缺点：成本最高，偏离 Spring AI 框架价值

**Recommendation:** 采用 **Approach B**。  
原因：这最符合当前项目现状，也最适合先做可靠性增强而不是重写 agent 运行时。

---

## 4. Target Architecture

### 4.1 高层结构

保留当前主链路：

`Controller -> ChatService / AiOpsService -> DashScopeChatModel / ReactAgent -> ToolCallback / @Tool -> Trace`

新增三层治理：

1. **错误分类层**
   - 负责把异常或失败结果归类为可重试、不可重试、可 replan、直接终止
2. **工具执行生命周期层**
   - 负责统一 `prepare -> execute -> finalize`
3. **会话树层**
   - 负责给 trace/session 补充父子分支关系

### 4.2 模块划分

建议新增或扩展以下模块：

- `org.example.reliability.error`
  - `AiErrorCategory`
  - `AiErrorClassifier`
  - `RetryPolicySelector`
  - `RetryExecutor`

- `org.example.trace.tree`
  - `TraceBranchType`
  - `TraceLinkService`
  - `TraceTreeQueryService`

- `org.example.tool.pipeline`
  - `ToolExecutionRequest`
  - `ToolExecutionResult`
  - `ToolExecutionPipeline`
  - `ToolPreparationHandler`
  - `ToolFinalizeHandler`

---

## 5. Feature 1: 错误分类 + 自动重试

### 5.1 Why It Fits Spring AI

这一能力最适合当前 Spring AI 架构，因为它不要求改动 agent graph 内核，只要包裹这些入口：

- 模型调用入口
- 工具调用入口
- MCP / 外部依赖调用入口

这与 Spring 的 `RetryTemplate`、策略对象、异常分类器都天然契合。

### 5.2 分类目标

统一把失败分成四类：

- `RETRYABLE_MODEL_ERROR`
  - 429、5xx、socket timeout、临时网络错误
- `RETRYABLE_TOOL_ERROR`
  - MCP 暂时不可达、Prometheus/CLS 短时超时、外部连接抖动
- `NON_RETRYABLE_CONTEXT_ERROR`
  - prompt/context 超长、参数校验失败、schema 不匹配
- `NON_RETRYABLE_BUSINESS_ERROR`
  - 查询无结果、业务条件不满足、工具逻辑明确失败

可选扩展：

- `REPLAN_REQUIRED`
  - 多次重试后仍失败，或结果为空但需要切换调查方向

### 5.3 Recommended Flow

1. 调用模型或工具
2. 捕获异常或解析失败结果
3. 使用 `AiErrorClassifier` 分类
4. 根据分类从 `RetryPolicySelector` 获取策略
5. 由 `RetryExecutor` 决定：
   - 重试
   - 直接失败
   - 触发 replan
6. 将重试次数、最终分类、最终结果写入 trace

### 5.4 Integration Points

模型侧：

- [ChatService.java](/D:/code/Java/SuperBizAgent/SuperBizAgent/src/main/java/org/example/service/ChatService.java:165)
- [AiOpsService.java](/D:/code/Java/SuperBizAgent/SuperBizAgent/src/main/java/org/example/service/AiOpsService.java:62)

工具侧：

- [ToolTraceHelper.java](/D:/code/Java/SuperBizAgent/SuperBizAgent/src/main/java/org/example/trace/ToolTraceHelper.java:1)

### 5.5 Data To Record

建议为 trace / tool_call 增加以下可选字段：

- `error_category`
- `retry_count`
- `retry_exhausted`
- `last_failure_reason`

如果不想立刻改表，也可以先落在 trace step output 或 tool result metadata 中，作为第一阶段过渡。

### 5.6 Testing

至少覆盖：

- 429 / timeout 被归类为可重试
- 参数错误不重试
- 工具空结果不无限重试
- 重试耗尽后能正确落 trace

---

## 6. Feature 2: 会话树模型

### 6.1 Why It Fits Spring AI

这块本质是“会话结构化表达”，不强依赖 Spring AI 内部能力，尤其适合你们当前已有 trace/session/replan 表的现状。

### 6.2 Problem To Solve

当前 replan 是“记录发生过”，但不是“正式分支”。  
需要让系统能稳定表达：

- `resume`：继续已有链路
- `replan`：因失败或空结果转入子链路
- `fork`：从历史节点人工或系统分叉

### 6.3 Minimum Data Model

建议在 `agent_session_trace` 增加：

- `root_trace_id`
- `parent_trace_id`
- `branch_type`
- `branch_label`

建议枚举：

- `MAIN`
- `REPLAN`
- `RESUME`
- `FORK`

### 6.4 Branch Semantics

- **MAIN**
  - 主会话第一次进入
- **REPLAN**
  - 由失败、空结果、报告缺失等原因派生
- **RESUME**
  - 从中断会话继续
- **FORK**
  - 从历史链路人工创建新的调查分支

### 6.5 Query Surface

建议在现有 trace 查询接口基础上扩展：

- 获取当前 trace 的父链路摘要
- 获取当前 trace 的子分支列表
- 获取 root trace 下的全链路摘要

前端第一阶段不做复杂树图，只做：

- breadcrumb
- 父链路入口
- 子分支列表

### 6.6 Integration Points

- [AiOpsService.java](/D:/code/Java/SuperBizAgent/SuperBizAgent/src/main/java/org/example/service/AiOpsService.java:92)
- [TraceQueryService.java](/D:/code/Java/SuperBizAgent/SuperBizAgent/src/main/java/org/example/trace/service/TraceQueryService.java:35)
- [TraceController.java](/D:/code/Java/SuperBizAgent/SuperBizAgent/src/main/java/org/example/controller/TraceController.java:22)

### 6.7 Migration Strategy

第一阶段兼容旧数据：

- `root_trace_id` 为空时视为本身
- `parent_trace_id` 为空时视为根节点
- 历史链路全部默认 `branch_type=MAIN`

### 6.8 Testing

至少覆盖：

- 主链路创建时自动写入 `root_trace_id`
- replan 产生子 trace 时能正确挂父节点
- 通过 sessionId / traceId 查询能取到父子关系

---

## 7. Feature 3: 工具三阶段生命周期

### 7.1 Why It Fits Spring AI

Spring AI 的 `@Tool` / `ToolCallback` 很适合做“工具暴露”，但不直接提供统一的 `prepare / execute / finalize` 生命周期。  
因此最合适的做法不是改框架，而是在工具执行外面包一层统一 pipeline。

### 7.2 Lifecycle Definition

#### prepare

职责：

- 参数校验
- 权限校验
- 脱敏
- trace 开始
- 前置限流 / 熔断判断

#### execute

职责：

- 真正执行工具逻辑
- 调用 MCP / Prometheus / CLS / 内部文档等外部依赖

#### finalize

职责：

- 结果标准化
- 错误分类
- 重试决策
- replan 决策
- trace 收尾

### 7.3 Recommended Implementation

建议以当前 [ToolTraceHelper.java](/D:/code/Java/SuperBizAgent/SuperBizAgent/src/main/java/org/example/trace/ToolTraceHelper.java:1) 为基础演进，不直接对所有 `@Tool` 强行做 AOP 切面。

推荐结构：

```text
ToolExecutionPipeline
  -> prepare(request)
  -> execute(delegate)
  -> finalize(result, error)
```

工具层统一改成通过 pipeline 执行，而不是各自手写追踪逻辑。

### 7.4 Relationship With Retry

`finalize` 阶段不只是“记日志”，还应该负责：

- 调用 `AiErrorClassifier`
- 判断是否重试
- 判断是否触发 replan
- 写入统一的 tool trace metadata

也就是说：

- **错误分类** 决定失败类型
- **工具生命周期** 提供挂点
- **重试** 是 finalize 中触发的动作

### 7.5 Integration Points

适合优先落地在：

- [QueryMetricsTools.java](/D:/code/Java/SuperBizAgent/SuperBizAgent/src/main/java/org/example/agent/tool/QueryMetricsTools.java:1)
- [QueryLogsTools.java](/D:/code/Java/SuperBizAgent/SuperBizAgent/src/main/java/org/example/agent/tool/QueryLogsTools.java:1)
- [InternalDocsTools.java](/D:/code/Java/SuperBizAgent/SuperBizAgent/src/main/java/org/example/agent/tool/InternalDocsTools.java:1)

### 7.6 Testing

至少覆盖：

- prepare 校验失败时不进入 execute
- execute 抛异常时 finalize 仍然执行
- finalize 能触发重试或 replan
- trace 中能看到各阶段结果

---

## 8. Cross-Feature Data Flow

三项能力要形成统一闭环：

1. 工具进入 `prepare`
2. `prepare` 校验通过，进入 `execute`
3. `execute` 成功或失败后进入 `finalize`
4. `finalize` 调用错误分类器
5. 错误分类器给出：
   - 继续
   - 重试
   - replan
   - 终止
6. 如触发 replan，则创建带 `parent_trace_id` 的子链路

这样最终能把：

- 为什么失败
- 重试了几次
- 为什么切到新分支
- 新分支从哪条链路来

全部放进同一个 Trace 观察面。

---

## 9. Rollout Plan

### Phase 1

先做 `错误分类 + 自动重试`

原因：

- 见效最快
- 改造面最小
- 可以独立上线验证

### Phase 2

再做 `工具三阶段生命周期`

原因：

- 统一工具治理结构
- 为后续更多工具接入打底

### Phase 3

最后做 `会话树模型`

原因：

- 需要表结构和查询接口一起演进
- 更适合作为第二批前后端联动能力

---

## 10. Risks And Constraints

1. **不要把 retry 放进无限循环**
   必须有分类、次数和退避策略
2. **不要在第一阶段强依赖复杂树图 UI**
   先做数据模型和简单导航
3. **不要直接深切 Spring AI 内部 graph**
   当前项目更适合外围治理层
4. **工具生命周期不宜一开始全量切 AOP**
   先从核心工具显式接入，风险更低

---

## 11. Recommendation Summary

推荐正式实施顺序：

1. `错误分类 + 自动重试`
2. `工具三阶段生命周期`
3. `会话树模型`

推荐实现策略：

- 保持现有 `ReactAgent` 编排不动
- 在模型调用、工具调用、trace 持久化外围补治理层
- 让 Trace 成为三项能力的统一观察面

这样做的好处是：

- 和当前 Spring AI 架构最贴合
- 对现有代码侵入最小
- 后续继续扩展插件系统、更多工具、更多分支语义时也不会推倒重来
