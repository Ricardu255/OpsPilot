# AI Ops Replan Child Execution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 AI Ops workflow 在触发 replan 时真正创建子 trace/session，并在子链路下继续完成下一轮执行。

**Architecture:** 保持现有 `ChatController -> AiOpsService -> supervisorAgent` 主链路不变，在 `AiOpsService` 外层增加一次性 replan 编排。首轮失败时关闭父 trace、创建 `REPLAN` 子 session、切换 `TraceContext` 后再执行一轮。

**Tech Stack:** Spring Boot, Spring AI Alibaba, Spring Data JPA, JUnit 5, Mockito

---

### Task 1: Pin the branch behavior with tests

**Files:**
- Modify: `src/test/java/org/example/service/AiOpsServiceTraceTest.java`
- Modify: `src/test/java/org/example/trace/AgentTraceServiceTest.java`

- [ ] Add a failing test for `executeAiOpsWorkflow(...)` proving an empty first attempt creates a child trace context and reruns on the child.
- [ ] Add a failing test for `createReplanChildContext(...)` proving the parent trace is marked `REPLAN` and child session metadata is derived from the parent.
- [ ] Add a failing test for `startChildSessionFromParent(...)` proving root/parent linkage is copied from the parent session.

### Task 2: Implement one-hop replan branching

**Files:**
- Modify: `src/main/java/org/example/service/AiOpsService.java`
- Modify: `src/main/java/org/example/trace/service/AgentTraceService.java`

- [ ] Add a child-session factory in `AgentTraceService`.
- [ ] Add an `AiOpsWorkflowOutcome` result object and a one-hop replan orchestration method in `AiOpsService`.
- [ ] Ensure parent trace completion uses status `REPLAN` before the child run starts.

### Task 3: Wire the SSE endpoint to the new workflow entrypoint

**Files:**
- Modify: `src/main/java/org/example/controller/ChatController.java`

- [ ] Replace the direct `executeAiOpsAnalysis(...)` call with the new workflow method.
- [ ] Complete the final active trace using the final report or failure message from the workflow outcome.

### Task 4: Verify focused regression coverage

**Files:**
- Test: `src/test/java/org/example/service/AiOpsServiceTraceTest.java`
- Test: `src/test/java/org/example/trace/AgentTraceServiceTest.java`

- [ ] Run the focused test set for `AiOpsServiceTraceTest` and `AgentTraceServiceTest`.
- [ ] Run the broader trace tests if the focused set passes cleanly.
