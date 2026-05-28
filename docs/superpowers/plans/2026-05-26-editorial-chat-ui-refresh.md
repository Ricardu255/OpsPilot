# Editorial Chat UI Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将主聊天首页与 AIOps 结果区升级为更接近参考图的编辑感工作台界面，在不改接口和交互逻辑的前提下，统一布局、字体层级、留白与中文文案呈现。

**Architecture:** 保留现有 `index.html + styles.css + app.js` 的结构和行为，只做前端壳层重排与展示样式收敛。改版以“轻壳层、弱卡片、强版式、摘要优先”为原则，同时顺手清理首页静态乱码文案，并让聊天页与 AIOps 结果区共享同一套安静的视觉语言。

**Tech Stack:** Vanilla HTML, CSS, JavaScript, Spring Boot static resources, in-app browser verification

---

## Scope

- 包含：
  - 聊天首页整体壳层排版
  - 左侧会话栏信息密度与分组风格
  - 中央欢迎区与输入区重排
  - 聊天消息区与 AIOps 报告区的字体、宽度、层级、表格密度
  - 首页静态中文文案修复
- 不包含：
  - 后端接口调整
  - 会话逻辑改写
  - Trace Console 本轮深改
  - 新功能新增

## Target files

- Modify: `src/main/resources/static/index.html`
- Modify: `src/main/resources/static/styles.css`
- Modify: `src/main/resources/static/app.js`
- Verify: `src/main/resources/static/trace.html`
- Verify: `src/main/resources/static/trace.css`
- Verify: `src/main/resources/static/trace.js`

---

### Task 1: 固定改版基线与文案清理边界

**Files:**
- Modify: `src/main/resources/static/index.html`
- Review: `src/main/resources/static/styles.css`
- Review: `src/main/resources/static/app.js`

- [ ] 逐项确认首页现有结构中哪些区域保留：左侧会话栏、中央消息区、底部输入区、右上角 AI Ops 入口。
- [ ] 清点首页静态乱码文案，统一替换为正常中文，包括标题、按钮、占位文案、模式名称、加载提示。
- [ ] 明确本轮不改动的 JS 行为边界：发送消息、切换模式、历史会话、上传文件、AIOps 触发逻辑全部保持原样。
- [ ] 记录参考图映射关系，形成 1:1 视觉目标：
  - 左侧更轻的“会话目录”
  - 中央更空的“欢迎区”
  - 下方更克制的“工作台输入器”
  - 消息区更像文档而不是气泡墙

### Task 2: 重做页面壳层与留白结构

**Files:**
- Modify: `src/main/resources/static/index.html`
- Modify: `src/main/resources/static/styles.css`

- [ ] 将整体布局从“卡片包裹的双栏应用”收紧成“浅底画布 + 轻分栏”的结构，减少主容器边框、阴影、模糊感。
- [ ] 调整左侧栏宽度、内边距、分隔方式，让其更接近工作记录列表，而不是工具按钮堆叠面板。
- [ ] 重排主区域顶部与中部空间，让欢迎区在空会话时真正居中可感，而不是被聊天容器挤压。
- [ ] 保留 AI Ops 入口，但弱化其视觉侵入感，避免其在首屏压过输入区与欢迎文案。
- [ ] 控制主内容最大宽度，避免聊天与报告文本在宽屏下横向铺得过开。

### Task 3: 重做输入区与首页欢迎态

**Files:**
- Modify: `src/main/resources/static/index.html`
- Modify: `src/main/resources/static/styles.css`
- Modify: `src/main/resources/static/app.js`

- [ ] 把输入区改成更低调的工作台输入面板：更浅的边框、更小的按钮、更稳定的高度、更清晰的左右分区。
- [ ] 将欢迎区改成“标题 + 简短引导 + 当前工作上下文”的版式，减少空页面的廉价感。
- [ ] 收紧输入框占位文案、模式选择器、工具按钮和发送按钮的字号与边距，让底部操作条更精致。
- [ ] 保证空状态与有消息状态之间切换平顺，不出现欢迎区和消息区互相挤压或重叠。
- [ ] 维持现有事件绑定和 DOM id，避免改版误伤 `app.js` 的现有交互逻辑。

### Task 4: 统一聊天内容与 AIOps 报告的文档化呈现

**Files:**
- Modify: `src/main/resources/static/styles.css`
- Modify: `src/main/resources/static/app.js`

- [ ] 下调普通消息、系统提示、AIOps 结果块的标题字号、段落行高和表格间距，让输出更适合连续阅读。
- [ ] 将 AIOps 报告从“大标题 + 大段块”收成“文档式分析稿”，突出摘要、结论、关键表格和步骤。
- [ ] 控制代码块、引用块、表格和列表的宽度与字重，避免一屏里出现过多粗字和高对比块。
- [ ] 优化消息最大宽度和左右留白，让生成结果更像一页工作笔记，而不是巨型气泡。
- [ ] 保留 Markdown 渲染能力，但降低默认标题层级的视觉攻击性，尤其是 `h1/h2/table` 在 AIOps 场景中的尺寸。

### Task 5: 重做左侧会话栏的信息结构

**Files:**
- Modify: `src/main/resources/static/index.html`
- Modify: `src/main/resources/static/styles.css`
- Modify: `src/main/resources/static/app.js`

- [ ] 将“新建对话”和“Trace Console”入口与历史记录区重新分层，减少它们互相抢注意力。
- [ ] 把历史会话从“按钮列表”调整为“时间/状态感更强的目录项”，保留删除能力但默认弱化。
- [ ] 优化会话标题、时间辅助信息和 hover 态，使侧栏更像最近工作记录，而不是命令菜单。
- [ ] 如果现有数据结构不足以显示时间辅助信息，则先只调整视觉层，不扩展持久化格式。

### Task 6: 响应式与联调验收

**Files:**
- Verify: `src/main/resources/static/index.html`
- Verify: `src/main/resources/static/styles.css`
- Verify: `src/main/resources/static/app.js`

- [ ] 在桌面宽屏下验证首页空状态、消息状态、AIOps 长报告状态三种主场景。
- [ ] 在窄屏或较小窗口下验证侧栏、欢迎区、输入区、消息区不会互相遮挡。
- [ ] 验证输入区按钮、模式选择器、工具菜单、上传文件、发送消息、AIOps 触发都保持可用。
- [ ] 验证首页改版后，Trace Console 入口仍然清晰可达，且视觉风格不显得割裂。
- [ ] 运行最小静态校验命令并记录结果：
  - `node --check src/main/resources/static/app.js`
  - 浏览器手工验证 `http://127.0.0.1:9888/` 或当前本地启动地址

---

## Acceptance criteria

- 首页第一眼更接近“安静的工作台”，而不是“厚卡片聊天应用”。
- 左侧会话栏、中央欢迎区、底部输入区形成清楚主次。
- 中文静态文案不再出现乱码。
- AIOps 报告默认阅读压力明显降低，标题、表格、段落密度更平衡。
- 交互逻辑、接口调用、会话行为保持不变。

## Risks and controls

- 风险：只靠 CSS 调整可能被现有 DOM 结构限制。
  - 控制：仅在 `index.html` 做轻量级结构整理，保持关键 id/class 不变。
- 风险：AIOps 报告缩得过度，导致重点不够突出。
  - 控制：优先缩大标题与容器，不牺牲摘要、结论、关键告警表格。
- 风险：欢迎态与消息态切换时布局跳动。
  - 控制：统一主容器宽度和输入区定位策略，减少状态切换时的 reflow。

## Suggested execution order

1. 先修 `index.html` 静态乱码和欢迎态文案。
2. 再收页面壳层、侧栏、输入区的大结构。
3. 接着压聊天正文和 AIOps 报告层级。
4. 最后做响应式和浏览器验收。
