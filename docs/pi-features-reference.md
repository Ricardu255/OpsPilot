# pi 六大功能参考 — Spring AI Alibaba 适配分析

## 总览

| # | 功能 | 核心逻辑行数 | 难度 | 适配程度 |
|---|---|---|---|---|
| 1 | 两层消息队列 (steer/follow-up) | ~80 | 低 | 不适合 — 框架 graph 不暴露内循环 |
| 2 | 错误分类 + 自动重试 | ~150 | 低 | 适合 — Spring RetryTemplate 直接可用 |
| 3 | 工具三阶段生命周期 | ~200 | 中 | 半适合 — 需 AOP 切 @Tool |
| 4 | 会话树模型 | ~500 | 中 | 适合 — 已有 JPA trace 表扩展 |
| 5 | 扩展/插件系统 | ~600 | 中高 | 半适合 — SPI + 自建事件总线 |
| 6 | Context 压缩 | ~700 | 高 | 不适合 — 框架不暴露 token 计数 |

---

## 功能详述

### 1. 两层消息队列（steer / follow-up）
- **steer**：正在执行 tool batch 时插入新指令，batch 完成后立即处理
- **follow-up**：agent 自然停止后才触发
- 框架的 graph-based ReAct agent 把 LLM 调用 + tool 执行封装成一个完整 graph，不暴露中间插入点

### 2. 错误分类 + 自动重试
- overload / rate-limited / 5xx → 指数退避重试
- context overflow → 不重试（走 compaction）
- Spring `RetryTemplate` + 正则匹配错误消息即可

### 3. 工具三阶段生命周期
```
prepare（参数校验 + 权限拦截）
  → execute（实际执行）
  → finalize（结果后处理 + terminate 决策）
```
- 默认批量并行，单 tool 可降级串行
- Spring `@Tool` 注解无拦截层，需 AOP 切入

### 4. 会话树模型
- 条目类型：message / model_change / compaction / branch_summary / label
- 树结构：`parentId` + `leafId` 指针
- 操作：fork（分支）、resume（恢复）、navigate（导航）
- 在现有 JPA trace 表加 `parent_id` 和分支字段即可

### 5. 扩展/插件系统
- 注册 tool / 命令 / 快捷键
- 拦截消息、修改 system prompt
- 贡献 skill 路径
- Java SPI `ServiceLoader` 加载，事件链自行设计

### 6. Context 压缩
- cut point 计算（从后往前累积 tokens）
- split-turn 检测（截断点在 turn 中间时生成两条摘要）
- 增量摘要（UPDATE 而非重新生成）
- 需绕过框架滑动窗口，直接调底层模型 API

---

## 实现建议

### 第一阶段（4 天）

| 功能 | 天数 | 做法 |
|---|---|---|
| 错误分类 + 重试 | 0.5 天 | Spring RetryTemplate + 正则分类 |
| 会话树模型 | 1.5 天 | 已有 JPA trace 表加字段 |
| 三阶段生命周期 | 2 天 | AOP 切 @Tool 方法 |

### 第二阶段（按需）

| 功能 | 天数 | 做法 |
|---|---|---|
| 两层消息队列 | 1 天 | 应用层维护队列，绕过框架 loop |
| Context 压缩 | 3 天 | 直接调底层模型 API |
| 扩展系统 | 4 天 | SPI + 自建事件总线 |
