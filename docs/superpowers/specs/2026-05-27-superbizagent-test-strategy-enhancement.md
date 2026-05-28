# SuperBizAgent 测试设计增强方案

**Goal:** 在现有 `JUnit + Python 接口自动化` 基础上，进一步完善 `SuperBizAgent` 的测试分层、用例设计、执行策略与报告规范，使测试体系更适合 `SSE / AIOps / Trace / MCP / Milvus` 这类项目特性。

**Scope:** 本文档聚焦测试设计，不涉及 Java 主业务代码改造；重点覆盖 Java 单测/集成测试职责、Python 接口自动化职责、测试用例分层、Allure 报告规范与建议新增用例。

---

## 1. 背景与目标

当前项目已经具备两类测试基础：

1. **Java 侧 JUnit 测试**
   - 已覆盖 `Trace`、`AiOpsService`、`ToolTraceHelper`、`VectorSearchService`、`Milvus disabled` 等核心逻辑。
   - 优点是离业务代码近，适合验证服务层、控制层与 Spring 配置行为。
   - 短板是对 `HTTP + SSE + 外部依赖联调 + 报告展示` 的覆盖还不够完整。

2. **独立 Python 接口自动化设计**
   - 已规划为 `pytest + allure + YAML` 模式。
   - 已明确要覆盖 `chat / chat_stream / ai_ops / upload / trace / milvus health` 等接口。
   - 目前更偏“框架与目录规划”，测试分层、断言规范和报告颗粒度还可以再收紧。

本次增强的目标是：

- 让 **JUnit** 和 **pytest** 职责边界更清楚；
- 让接口测试从“按接口散放”升级为“按目标分层”；
- 让 `AIOps / Trace / SSE / MCP / Milvus` 成为重点覆盖对象；
- 让 `Allure` 报告更适合回归、联调和排障。

---

## 2. 参考案例中值得借鉴的模式

结合参考文章与同类 `pytest + requests + allure + YAML` 案例，适合当前项目吸收的公共模式主要有以下几类：

1. **测试框架分层**
   - 配置层、请求封装层、断言层、数据层、用例层、报告层职责分离。
   - 用例只关心“输入、期望、依赖提取”，不承载过多底层实现。

2. **YAML 数据驱动**
   - 将同一接口下的多条用例集中管理。
   - 适合做“正常 / 异常 / 边界”三类用例的统一编排。

3. **fixture 统一前后置**
   - 用 `pytest fixture` 处理会话初始化、环境探测、清理动作、提取变量复用。
   - 比把初始化逻辑散落在各个测试函数里更稳定。

4. **参数化与场景测试并存**
   - 单接口用例适合参数化。
   - 复杂场景（如上传后问答、AIOps replan、SSE 流式消费）要保留场景测试入口。

5. **Allure 报告增强**
   - 不只展示 pass/fail，还要附请求、响应、上下文变量、环境信息。
   - 对 `SSE`、文件上传、接口依赖传值场景尤其重要。

这些思路与 `SuperBizAgent` 已有的 Python 自动化规划是一致的，但需要针对本项目的 AI 能力做进一步定制。

---

## 3. 推荐测试体系

建议正式采用“两层测试体系”：

### 3.1 Java 项目内测试：JUnit 负责代码级验证

职责：

- `Service`、`Trace`、`Tool`、`Retry`、`Replan` 等核心业务逻辑；
- `Controller` 行为与 Spring MVC 返回结构；
- 配置开关、降级逻辑、Milvus 开关、MCP 兜底逻辑；
- H2/Flyway 下的轻量集成测试。

不建议它承担的职责：

- 大量 API 回归编排；
- `SSE` 流式消费体验验证；
- Allure 报告展示；
- 与外部 Prometheus / CLS / MCP 的长链路回归。

### 3.2 独立接口自动化仓库：pytest + Allure 负责接口回归

职责：

- `HTTP / SSE` 接口回归；
- 文件上传与会话串联；
- `AIOps` 业务流程验证；
- `Trace Console` 相关接口验证；
- 真实依赖联调冒烟；
- 回归报告和附件沉淀。

建议结论：

- **不引入 TestNG**
- **Java 侧继续统一使用 JUnit 5**
- **接口回归统一由 Python 仓库承担**

---

## 4. 测试分层设计

相比“按接口散放”的方式，推荐将 Python 自动化用例拆成以下五层：

### 4.1 `smoke`

目标：确认服务基本可用，适合开发自检和提交前快速验证。

建议覆盖：

- `GET /actuator/health`
- `GET /milvus/health`
- `POST /api/chat`
- `GET /api/trace/stats`

要求：

- 执行时间短；
- 不依赖复杂前置；
- 尽量避免真实外部依赖失败造成高噪声。

### 4.2 `core`

目标：覆盖核心接口主路径。

建议覆盖：

- `chat`
- `chat_stream`
- `clear`
- `session`
- `trace detail / trace list / trace stats`

要求：

- 至少包含正常输入、空输入、非法参数三类；
- 断言不仅看状态码，还要看返回字段结构。

### 4.3 `integration`

目标：覆盖跨接口、跨模块的业务流程。

建议覆盖：

- 文件上传后再进行问答；
- AIOps 触发工具调用；
- AIOps 触发 replan；
- Trace 中可查到对应 `session / step / tool call / replan`；
- Milvus 开关下的行为差异。

### 4.4 `external`

目标：验证真实外部依赖联通性。

建议覆盖：

- 真实 Prometheus 指标查询；
- 真实 CLS MCP 接入；
- 真实 Milvus 检查；
- mock/real 模式切换行为。

要求：

- 与常规回归分开执行；
- 明确标记依赖环境；
- 报告中必须附当前环境状态。

### 4.5 `e2e`

目标：验证完整业务闭环。

建议覆盖：

1. 发起会话；
2. 触发聊天或 AIOps；
3. 消费 SSE 或拿到同步返回；
4. 查询会话与 trace；
5. 验证最终结果、链路与统计。

这类用例不求多，但必须足够代表项目能力。

---

## 5. 用例组织方式

建议将用例组织从“纯按接口文件名”升级成“按业务域 + 用例类型”。

推荐目录示意：

```text
testcase/SuperBizAgent/
  smoke/
    health.yaml
    milvus_health.yaml
    chat_smoke.yaml
    trace_stats_smoke.yaml
  core/
    chat.yaml
    chat_stream.yaml
    clear_chat.yaml
    session.yaml
    trace_detail.yaml
    trace_list.yaml
    trace_stats.yaml
  integration/
    upload_then_chat.yaml
    ai_ops_workflow.yaml
    ai_ops_replan.yaml
    trace_after_aiops.yaml
  external/
    prometheus_real.yaml
    cls_mcp_real.yaml
    milvus_real.yaml
  e2e/
    ai_ops_trace_e2e.yaml
```

如果暂时不希望调整目录层级，也至少应在现有 YAML 内显式声明：

- `case_type: smoke / core / integration / external / e2e`
- `priority: P0 / P1 / P2`
- `requires: mock / real-prometheus / real-cls / real-milvus`

---

## 6. 单接口用例设计规范

每个核心接口至少覆盖以下三组：

1. **正常路径**
   - 合法参数；
   - 断言关键字段、状态和业务结果。

2. **异常路径**
   - 缺参数；
   - 参数类型不合法；
   - 非法枚举值；
   - 空字符串、null、超长文本。

3. **边界路径**
   - 上限/下限；
   - 特殊字符；
   - 中文、英文、空格、换行；
   - 大 payload；
   - SSE 超时、空流、提前结束。

推荐最少覆盖矩阵：

| 模块 | 正常 | 异常 | 边界 |
|---|---:|---:|---:|
| chat | 是 | 是 | 是 |
| chat_stream | 是 | 是 | 是 |
| ai_ops | 是 | 是 | 是 |
| trace | 是 | 是 | 是 |
| upload | 是 | 是 | 是 |
| milvus health | 是 | 否 | 否 |

---

## 7. 场景测试设计规范

`SuperBizAgent` 的价值不在单接口 CRUD，而在链路。因此必须有一批场景测试。

建议优先场景：

1. **普通问答链路**
   - 发起对话；
   - 返回正常内容；
   - 会话可查询。

2. **流式问答链路**
   - 发起 `chat_stream`；
   - 收到多段事件；
   - 正常结束；
   - 不出现乱码和异常中断。

3. **上传后问答链路**
   - 上传文件；
   - 发起问答；
   - 验证能引用上传内容。

4. **AIOps 分析链路**
   - 发起 AIOps；
   - 验证报告结构；
   - 查询 trace；
   - 验证工具调用与 step。

5. **AIOps replan 子链路**
   - 构造触发 replan 的输入；
   - 验证存在 parent trace 和 child trace；
   - 验证 child trace 继续执行。

6. **CLS MCP 兜底场景**
   - 真实模式下工具未注册；
   - 验证返回结构化错误；
   - 验证错误码和诊断字段。

---

## 8. fixture 与前后置设计

建议在 Python 自动化中统一设计以下 fixture：

### 8.1 session 级

- 读取环境配置；
- 探测当前服务状态；
- 写入 Allure 环境信息；
- 检查 `mockEnabled / MCP / Milvus` 状态。

### 8.2 module 级

- 生成测试命名空间；
- 准备测试数据目录；
- 预置上传文件。

### 8.3 function 级

- 创建会话 ID；
- 清理聊天历史；
- 重置 `extract.yaml`；
- 记录请求上下文。

### 8.4 场景辅助 fixture

- `upload_file_fixture`
- `ai_ops_request_fixture`
- `trace_lookup_fixture`
- `sse_collector_fixture`

原则：

- 用例体只描述意图；
- 资源准备、清理、依赖透传尽量下沉到 fixture。

---

## 9. 断言设计规范

当前项目不适合只做 `status_code == 200` 断言，建议分三层断言。

### 9.1 基础断言

- HTTP 状态码；
- 返回可解析；
- SSE 事件格式合法；
- 必填字段存在。

### 9.2 业务断言

- `chat` 是否返回 `response/sessionId`；
- `trace` 是否返回 `session/steps/toolCalls`；
- `ai_ops` 是否返回完整报告结构；
- `replan` 后是否存在 `child_traces`。

### 9.3 可靠性断言

- 错误码是否符合预期；
- 重试次数、错误类别是否透出；
- mock/real 模式标识是否正确；
- CLS MCP 未注册时是否返回结构化错误；
- Milvus 关闭时是否返回 disabled 语义。

---

## 10. Allure 报告增强规范

Allure 在该项目里不应该只是“好看”，而应该能帮助定位问题。

建议每条用例附带以下附件：

1. 请求方法、URL、headers
2. 请求体
3. 响应体
4. SSE 原始事件文本
5. 提取变量快照
6. 环境快照
   - profile
   - mock 开关
   - MCP 地址
   - Milvus 状态

推荐使用的标签：

- `feature`: Chat / Trace / AIOps / Upload / Milvus
- `story`: stream / trace detail / replan / cls mcp / upload reuse
- `severity`: blocker / critical / normal / minor
- `tag`: smoke / integration / external / e2e

推荐增强点：

- 用例标题动态显示 YAML 中的 `case_name`
- 报告顺序与执行顺序一致
- 失败时自动附原始响应和日志摘要

---

## 11. 现阶段建议新增的重点用例

最值得优先补齐的是以下场景：

1. `chat_stream` 完整 SSE 结束事件
2. `ai_ops -> replan -> child trace`
3. `trace sessionId -> detail`
4. `CLS MCP 未注册 -> 结构化错误`
5. `Milvus enabled / disabled` 双模式
6. 文件上传后再问答
7. 真实 Prometheus / CLS / Milvus 联调冒烟

这些用例比普通接口 happy path 更能体现项目价值。

---

## 12. 执行策略建议

推荐统一执行矩阵：

### 本地开发

- Java：核心 JUnit 单测
- Python：`smoke`

### 提交前

- Java：相关模块单测 + 轻量集成测试
- Python：`smoke + core`

### 合并前

- Java：全量核心测试
- Python：`core + integration`

### 联调环境 / 每日回归

- Python：`external + e2e`
- 输出 Allure 报告

---

## 13. 对当前项目的落地结论

结合现状，推荐结论如下：

1. **不引入 TestNG**
   - 当前 Java 项目已统一使用 JUnit 5；
   - 没有必要并行维护两套 Java 测试框架。

2. **继续强化 JUnit，而不是替换它**
   - Java 侧重点继续放在业务逻辑、配置开关、Trace、重试、MCP 兜底等。

3. **将 Python 自动化作为接口回归主战场**
   - 尤其是 `SSE / AIOps / Trace / Upload / MCP / Milvus`。

4. **Allure 只在 Python 仓库作为主报告出口**
   - Java 项目暂不强行补 Allure；
   - 保持职责清晰。

5. **测试设计要从“按接口罗列”升级为“按场景分层”**
   - 这是当前最值得补的一步。

---

## 14. 下一步建议

如果继续推进，建议按下面顺序落地：

1. 调整 Python 自动化仓库的 `pytest marker` 和目录分层；
2. 先补 `smoke / core` 的标准 YAML 用例；
3. 增加 `AIOps replan`、`Trace detail`、`CLS MCP fallback` 三个代表性集成场景；
4. 完善 Allure 附件和环境信息；
5. 最后补 `external / e2e`。

这条路线能最快把现有测试设计从“可用”推进到“更像一套正式回归体系”。
