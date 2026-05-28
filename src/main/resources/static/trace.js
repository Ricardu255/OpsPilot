class TraceConsoleApp {
    constructor() {
        this.apiBase = "/api/trace";
        this.demoMode = this.resolveDemoMode();
        this.initialTarget = this.resolveInitialTarget();
        this.demoPayload = this.buildDemoPayload();
        this.state = {
            page: 0,
            size: 20,
            totalPages: 1,
            activeTraceId: null
        };
        this.toastTimer = null;
        this.cacheElements();
        this.bindEvents();
        this.bootstrap();
    }

    resolveDemoMode() {
        const params = new URLSearchParams(window.location.search);
        return params.get("demo") === "1" || window.location.protocol === "file:";
    }

    resolveInitialTarget() {
        const params = new URLSearchParams(window.location.search);
        return {
            traceId: (params.get("traceId") || "").trim(),
            sessionId: (params.get("sessionId") || "").trim()
        };
    }

    cacheElements() {
        this.refreshBtn = document.getElementById("refreshBtn");
        this.demoBadge = document.getElementById("demoBadge");
        this.statusFilter = document.getElementById("statusFilter");
        this.agentTypeFilter = document.getElementById("agentTypeFilter");
        this.startFromFilter = document.getElementById("startFromFilter");
        this.startToFilter = document.getElementById("startToFilter");
        this.pageSizeFilter = document.getElementById("pageSizeFilter");
        this.applyFiltersBtn = document.getElementById("applyFiltersBtn");
        this.resetFiltersBtn = document.getElementById("resetFiltersBtn");
        this.traceIdInput = document.getElementById("traceIdInput");
        this.sessionIdInput = document.getElementById("sessionIdInput");
        this.findTraceBtn = document.getElementById("findTraceBtn");
        this.findSessionBtn = document.getElementById("findSessionBtn");
        this.sessionList = document.getElementById("sessionList");
        this.resultMeta = document.getElementById("resultMeta");
        this.pageLabel = document.getElementById("pageLabel");
        this.prevPageBtn = document.getElementById("prevPageBtn");
        this.nextPageBtn = document.getElementById("nextPageBtn");
        this.detailTitle = document.getElementById("detailTitle");
        this.detailMeta = document.getElementById("detailMeta");
        this.detailOverview = document.getElementById("detailOverview");
        this.stepsTimeline = document.getElementById("stepsTimeline");
        this.toolCallsTableBody = document.getElementById("toolCallsTableBody");
        this.replanList = document.getElementById("replanList");
        this.stepsCount = document.getElementById("stepsCount");
        this.toolCallsCount = document.getElementById("toolCallsCount");
        this.replansCount = document.getElementById("replansCount");
        this.toast = document.getElementById("toast");
        this.metricTotalSessions = document.getElementById("metricTotalSessions");
        this.metricSuccessCount = document.getElementById("metricSuccessCount");
        this.metricFailedCount = document.getElementById("metricFailedCount");
        this.metricAverageCost = document.getElementById("metricAverageCost");
    }

    bindEvents() {
        this.refreshBtn.addEventListener("click", () => this.bootstrap());
        this.applyFiltersBtn.addEventListener("click", () => {
            this.state.page = 0;
            this.state.size = Number(this.pageSizeFilter.value || 20);
            this.loadTraces();
        });
        this.resetFiltersBtn.addEventListener("click", () => this.resetFilters());
        this.findTraceBtn.addEventListener("click", () => this.openTraceById());
        this.findSessionBtn.addEventListener("click", () => this.openTraceBySessionId());
        this.prevPageBtn.addEventListener("click", () => this.changePage(-1));
        this.nextPageBtn.addEventListener("click", () => this.changePage(1));
        this.traceIdInput.addEventListener("keydown", (event) => {
            if (event.key === "Enter") {
                this.openTraceById();
            }
        });
        this.sessionIdInput.addEventListener("keydown", (event) => {
            if (event.key === "Enter") {
                this.openTraceBySessionId();
            }
        });
    }

    async bootstrap() {
        if (this.demoBadge) {
            this.demoBadge.hidden = !this.demoMode;
        }

        await Promise.all([this.loadStats(), this.loadTraces()]);

        if (this.initialTarget.traceId) {
            this.traceIdInput.value = this.initialTarget.traceId;
            await this.openTraceById();
            return;
        }

        if (this.initialTarget.sessionId) {
            this.sessionIdInput.value = this.initialTarget.sessionId;
            await this.openTraceBySessionId();
            return;
        }

        if (this.demoMode) {
            const firstTrace = this.demoPayload.traces[0];
            if (firstTrace && !this.state.activeTraceId) {
                this.state.activeTraceId = firstTrace.trace_id;
                this.renderDetail(this.demoPayload.detailsByTraceId[firstTrace.trace_id]);
                this.highlightActiveTrace();
            }
            this.showToast("当前为演示数据模式。");
        }
    }

    async loadStats() {
        try {
            const payload = await this.fetchJson(`${this.apiBase}/stats`);
            const stats = payload.data || {};
            this.metricTotalSessions.textContent = this.formatNumber(stats.total_sessions);
            this.metricSuccessCount.textContent = this.formatSuccessRate(stats.success_count, stats.total_sessions);
            this.metricFailedCount.textContent = this.formatNumber(stats.failed_count);
            this.metricAverageCost.textContent = this.formatDuration(stats.average_cost_ms);
        } catch (error) {
            this.showToast(`统计加载失败：${error.message}`);
        }
    }

    async loadTraces() {
        this.resultMeta.textContent = "加载中...";
        this.sessionList.innerHTML = "";

        const query = new URLSearchParams();
        if (this.statusFilter.value) {
            query.set("status", this.statusFilter.value);
        }
        if (this.agentTypeFilter.value) {
            query.set("agentType", this.agentTypeFilter.value);
        }
        if (this.startFromFilter.value) {
            query.set("startFrom", new Date(this.startFromFilter.value).toISOString().slice(0, 19));
        }
        if (this.startToFilter.value) {
            query.set("startTo", new Date(this.startToFilter.value).toISOString().slice(0, 19));
        }
        query.set("page", String(this.state.page));
        query.set("size", String(this.state.size));

        try {
            const payload = await this.fetchJson(`${this.apiBase}?${query.toString()}`);
            const sessions = payload.data || [];
            this.state.totalPages = payload.totalPages || 1;
            this.renderSessionList(sessions, payload.total || 0);
        } catch (error) {
            this.resultMeta.textContent = "加载失败";
            this.sessionList.innerHTML = this.renderInlineEmpty("暂时无法加载链路数据。");
            this.showToast(`链路列表加载失败：${error.message}`);
        }
    }

    async openTraceById() {
        const traceId = this.traceIdInput.value.trim();
        if (!traceId) {
            this.showToast("请先输入 Trace ID。");
            return;
        }

        try {
            const payload = await this.fetchJson(`${this.apiBase}/${encodeURIComponent(traceId)}`);
            this.state.activeTraceId = payload.data.trace_id;
            this.renderDetail(payload.data);
            this.highlightActiveTrace();
        } catch (error) {
            this.showToast(`Trace 查询失败：${error.message}`);
        }
    }

    async openTraceBySessionId() {
        const sessionId = this.sessionIdInput.value.trim();
        if (!sessionId) {
            this.showToast("请先输入会话 ID。");
            return;
        }

        try {
            const payload = await this.fetchJson(`${this.apiBase}/session/${encodeURIComponent(sessionId)}`);
            this.state.activeTraceId = payload.data.trace_id;
            this.renderDetail(payload.data);
            this.highlightActiveTrace();
        } catch (error) {
            this.showToast(`Session 查询失败：${error.message}`);
        }
    }

    async openTraceFromRow(traceId) {
        try {
            const payload = await this.fetchJson(`${this.apiBase}/${encodeURIComponent(traceId)}`);
            this.state.activeTraceId = traceId;
            this.renderDetail(payload.data);
            this.highlightActiveTrace();
        } catch (error) {
            this.showToast(`打开链路失败：${error.message}`);
        }
    }

    renderSessionList(sessions, total) {
        this.resultMeta.textContent = `共 ${this.formatNumber(total)} 条`;
        this.pageLabel.textContent = `第 ${this.state.page + 1} / ${Math.max(this.state.totalPages, 1)} 页`;
        this.prevPageBtn.disabled = this.state.page === 0;
        this.nextPageBtn.disabled = this.state.page >= this.state.totalPages - 1;

        if (!sessions.length) {
            this.sessionList.innerHTML = this.renderInlineEmpty("当前筛选条件下没有链路。");
            return;
        }

        this.sessionList.innerHTML = "";
        sessions.forEach((session) => {
            const row = document.createElement("button");
            row.type = "button";
            row.className = "session-row";
            row.dataset.traceId = session.trace_id;

            const summary = session.error_message
                ? `异常：${this.compactText(session.error_message, 72)}`
                : this.compactText(session.user_input || "暂无请求内容", 76);

            row.innerHTML = `
                <div class="row-main">
                    <div>
                        <div class="row-title">${this.escapeHtml(session.session_id || "未命名会话")}</div>
                        <div class="trace-code">${this.escapeHtml(session.trace_id || "")}</div>
                    </div>
                    <span class="badge ${this.escapeHtml(session.status || "")}">${this.escapeHtml(this.getStatusLabel(session.status))}</span>
                </div>
                <div class="row-summary">${this.escapeHtml(summary)}</div>
                <div class="row-sub">
                    <span>${this.escapeHtml(this.formatDateTime(session.start_time))}</span>
                    <span class="mono">${this.escapeHtml(this.formatDuration(session.cost_ms))}</span>
                </div>
            `;

            row.addEventListener("click", () => this.openTraceFromRow(session.trace_id));
            this.sessionList.appendChild(row);
        });

        this.highlightActiveTrace();
    }

    renderDetail(detail) {
        const session = detail.session || {};
        const steps = detail.steps || [];
        const toolCalls = detail.tool_calls || [];
        const replans = detail.replans || [];

        this.detailTitle.textContent = session.session_id || detail.trace_id || "链路详情";
        this.detailMeta.textContent = `链路 ID：${detail.trace_id || "--"}`;
        this.stepsCount.textContent = `${steps.length} 项`;
        this.toolCallsCount.textContent = `${toolCalls.length} 项`;
        this.replansCount.textContent = `${replans.length} 项`;

        const overviewItems = [
            this.renderOverviewItem("当前状态", `<span class="badge ${this.escapeHtml(session.status || "")}">${this.escapeHtml(this.getStatusLabel(session.status))}</span>`, true),
            this.renderOverviewItem("总耗时", this.formatDuration(session.cost_ms)),
            this.renderOverviewItem("开始时间", this.formatDateTime(session.start_time)),
            this.renderOverviewItem("步骤数", String(steps.length)),
            this.renderOverviewItem("工具调用", String(toolCalls.length)),
            this.renderOverviewItem("重规划", String(replans.length))
        ];

        const noteCards = [];
        if (session.user_input) {
            noteCards.push(this.renderNoteCard("用户请求", this.compactText(session.user_input, 180)));
        }
        if (session.final_answer) {
            noteCards.push(this.renderNoteCard("最终结论", this.compactText(session.final_answer, 220)));
        }
        if (session.error_message) {
            noteCards.push(this.renderNoteCard("异常说明", session.error_message));
        }

        this.detailOverview.innerHTML = `
            <div class="overview-grid">
                ${overviewItems.join("")}
            </div>
            <div class="tool-pillars">
                ${this.renderOverviewItem("会话 ID", session.session_id, false, "code")}
                ${this.renderOverviewItem("结束时间", this.formatDateTime(session.end_time))}
                ${this.renderOverviewItem("Agent 类型", this.formatAgentLabel(session.agent_type))}
            </div>
            ${noteCards.length ? `<div class="overview-notes">${noteCards.join("")}</div>` : ""}
        `;

        this.stepsTimeline.innerHTML = steps.length
            ? steps.map((step) => `
                <article class="timeline-item">
                    <div class="timeline-head">
                        <div class="timeline-title">${this.escapeHtml(this.formatStepName(step.step_name))}</div>
                        <span class="badge ${this.escapeHtml(step.status || "")}">${this.escapeHtml(this.getStatusLabel(step.status))}</span>
                    </div>
                    <div class="timeline-body">${this.escapeHtml(this.compactText(step.step_output || step.step_content || "暂无步骤内容", 140))}</div>
                    <div class="timeline-body">${this.escapeHtml(this.formatDateTime(step.start_time))} · ${this.escapeHtml(this.formatDuration(step.cost_ms))}</div>
                </article>
            `).join("")
            : this.renderInlineEmpty("没有执行步骤。");

        if (steps.length) {
            Array.from(this.stepsTimeline.querySelectorAll(".timeline-item")).forEach((item, index) => {
                const step = steps[index] || {};
                const content = step.step_output || step.step_content || "暂无步骤内容";
                const bodies = item.querySelectorAll(".timeline-body");
                const contentNode = bodies[0];
                const metaNode = bodies[1];

                if (contentNode) {
                    contentNode.classList.add("timeline-text");
                    contentNode.textContent = this.compactText(content, 360);
                }

                if (metaNode) {
                    const meta = document.createElement("div");
                    meta.className = "timeline-meta";
                    meta.innerHTML = `
                        <span>${this.escapeHtml(this.formatDateTime(step.start_time))}</span>
                        <span class="mono">${this.escapeHtml(this.formatDuration(step.cost_ms))}</span>
                    `;
                    metaNode.replaceWith(meta);
                }
            });
        }

        this.toolCallsTableBody.innerHTML = toolCalls.length
            ? toolCalls.map((toolCall) => `
                <tr>
                    <td>
                        <span class="tool-name">${this.escapeHtml(this.formatToolName(toolCall.tool_name))}</span>
                        <div class="tool-meta">
                            <span class="mono">${this.escapeHtml(this.formatDateTime(toolCall.started_at))}</span>
                        </div>
                    </td>
                    <td><span class="badge ${this.escapeHtml(toolCall.status || "")}">${this.escapeHtml(this.getStatusLabel(toolCall.status))}</span></td>
                    <td class="mono">${this.escapeHtml(this.formatDuration(toolCall.cost_ms))}</td>
                    <td>${this.renderToolCallNote(toolCall)}</td>
                </tr>
            `).join("")
            : `<tr><td colspan="4"><div class="muted-block">没有工具调用。</div></td></tr>`;

        this.replanList.innerHTML = replans.length
            ? replans.map((replan) => `
                <article class="replan-item">
                    <div class="replan-head">
                        <div class="replan-title">${this.escapeHtml(this.formatStepName(replan.trigger_step || "trigger"))}</div>
                        <span class="badge REPLAN">${this.escapeHtml(this.getReplanReasonLabel(replan.reason))}</span>
                    </div>
                    <div class="replan-body">${this.escapeHtml(replan.summary || "暂无说明")}</div>
                    <div class="replan-body">${this.escapeHtml(this.formatDateTime(replan.created_at))}</div>
                </article>
            `).join("")
            : this.renderInlineEmpty("没有重规划记录。");
    }

    renderOverviewItem(label, value, rawHtml = false, tone = "") {
        return `
            <div class="overview-item ${tone ? `overview-item-${tone}` : ""}">
                <span>${this.escapeHtml(label)}</span>
                ${rawHtml
                    ? `<div>${value}</div>`
                    : tone === "code"
                        ? `<strong>${this.escapeHtml(this.compactCode(value || "-"))}</strong><small class="overview-inline-code">${this.escapeHtml(value || "-")}</small>`
                        : `<strong>${this.escapeHtml(value || "-")}</strong>`}
            </div>
        `;
    }

    renderNoteCard(title, text) {
        return `
            <article class="note-card">
                <h3>${this.escapeHtml(title)}</h3>
                <p>${this.escapeHtml(text || "-")}</p>
            </article>
        `;
    }

    renderToolCallNote(toolCall) {
        const summary = this.escapeHtml(this.summarizeToolCall(toolCall));
        const error = toolCall.error_message
            ? `<div class="tool-cell-block error-text">${this.escapeHtml(toolCall.error_message)}</div>`
            : "";
        const rawSections = [
            this.renderRawSection("请求参数", toolCall.tool_params),
            this.renderRawSection("原始结果", toolCall.tool_result)
        ].filter(Boolean).join("");
        const rawBlock = rawSections
            ? `<details class="raw-block"><summary>查看原始数据</summary>${rawSections}</details>`
            : "";
        return `<div class="tool-cell-block">${summary}</div>${error}${rawBlock}`;
    }

    renderRawSection(label, value) {
        if (!value) {
            return "";
        }
        return `<div><span class="raw-label">${this.escapeHtml(label)}</span><pre>${this.escapeHtml(value)}</pre></div>`;
    }

    summarizeToolCall(toolCall) {
        if (toolCall.error_message) {
            return `调用失败：${this.compactText(toolCall.error_message, 88)}`;
        }

        const result = (toolCall.tool_result || "").trim();
        if (!result) {
            return "没有返回结果。";
        }

        try {
            const parsed = JSON.parse(result);
                if (parsed && typeof parsed === "object") {
                    if (parsed.no_results === true) {
                    return "没有查到结果。";
                }
                if (typeof parsed.message === "string" && parsed.message) {
                    return this.compactText(parsed.message, 120);
                }
                const scalarEntries = Object.entries(parsed)
                    .filter(([, value]) => ["string", "number", "boolean"].includes(typeof value))
                    .slice(0, 2);
                if (scalarEntries.length) {
                    return scalarEntries
                        .map(([key, value]) => `${this.translateFieldName(key)}：${String(value)}`)
                        .join(" · ");
                }
            }
        } catch (error) {
            return this.compactText(result.replace(/\s+/g, " "), 120);
        }

        return this.compactText(result.replace(/\s+/g, " "), 120);
    }

    highlightActiveTrace() {
        const rows = this.sessionList.querySelectorAll(".session-row");
        rows.forEach((row) => {
            row.classList.toggle("active", row.dataset.traceId === this.state.activeTraceId);
        });
    }

    changePage(delta) {
        const nextPage = this.state.page + delta;
        if (nextPage < 0 || nextPage >= this.state.totalPages) {
            return;
        }
        this.state.page = nextPage;
        this.loadTraces();
    }

    resetFilters() {
        this.statusFilter.value = "";
        this.agentTypeFilter.value = "";
        this.startFromFilter.value = "";
        this.startToFilter.value = "";
        this.pageSizeFilter.value = "20";
        this.state.page = 0;
        this.state.size = 20;
        this.loadTraces();
    }

    async fetchJson(url) {
        if (this.demoMode) {
            return this.mockRequest(url);
        }

        const response = await fetch(url, {
            headers: {
                "Accept": "application/json"
            }
        });

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        const payload = await response.json();
        if (payload.code && payload.code !== 200) {
            throw new Error(payload.message || "Request failed");
        }
        return payload;
    }

    mockRequest(url) {
        const requestUrl = new URL(url, window.location.href);
        const pathname = requestUrl.pathname.replace(/\/+$/, "");

        if (pathname === `${this.apiBase}/stats`) {
            return Promise.resolve({ code: 200, data: this.demoPayload.stats });
        }

        if (pathname === this.apiBase) {
            return Promise.resolve(this.buildDemoListResponse(requestUrl.searchParams));
        }

        if (pathname.startsWith(`${this.apiBase}/session/`)) {
            const sessionId = decodeURIComponent(pathname.slice(`${this.apiBase}/session/`.length));
            const detail = this.demoPayload.detailsBySessionId[sessionId];
            if (!detail) {
                throw new Error("未找到会话");
            }
            return Promise.resolve({ code: 200, data: detail });
        }

        if (pathname.startsWith(`${this.apiBase}/`)) {
            const traceId = decodeURIComponent(pathname.slice(`${this.apiBase}/`.length));
            const detail = this.demoPayload.detailsByTraceId[traceId];
            if (!detail) {
                throw new Error("未找到链路");
            }
            return Promise.resolve({ code: 200, data: detail });
        }

        throw new Error("暂不支持的演示请求");
    }

    buildDemoListResponse(searchParams) {
        const status = searchParams.get("status");
        const agentType = searchParams.get("agentType");
        const startFrom = this.parseFilterDate(searchParams.get("startFrom"));
        const startTo = this.parseFilterDate(searchParams.get("startTo"));
        const page = Number(searchParams.get("page") || 0);
        const size = Number(searchParams.get("size") || 20);

        const filtered = this.demoPayload.traces.filter((trace) => {
            if (status && trace.status !== status) {
                return false;
            }
            if (agentType && trace.agent_type !== agentType) {
                return false;
            }

            const startTime = this.parseFilterDate(trace.start_time);
            if (startFrom && startTime && startTime < startFrom) {
                return false;
            }
            if (startTo && startTime && startTime > startTo) {
                return false;
            }
            return true;
        });

        const total = filtered.length;
        const totalPages = Math.max(1, Math.ceil(total / Math.max(size, 1)));
        const startIndex = Math.max(page, 0) * Math.max(size, 1);
        const data = filtered.slice(startIndex, startIndex + Math.max(size, 1));

        return {
            code: 200,
            data,
            total,
            page,
            size,
            totalPages
        };
    }

    buildDemoPayload() {
        const details = [
            {
                trace_id: "trace-20260526-003",
                session: {
                    trace_id: "trace-20260526-003",
                    session_id: "aiops-20260526-003",
                    agent_type: "AIOPS_AGENT",
                    user_input: "排查 payments 集群里反复出现的 API 超时告警。",
                    final_answer: null,
                    status: "RUNNING",
                    error_message: null,
                    start_time: "2026-05-26T10:18:00",
                    end_time: null,
                    cost_ms: 18500
                },
                steps: [
                    {
                        step_name: "planner_agent",
                        step_content: "将告警拆成指标、日志和依赖检查三部分。",
                        step_output: "优先检查 checkout 和 payment-gateway 服务。",
                        status: "SUCCESS",
                        start_time: "2026-05-26T10:18:01",
                        cost_ms: 1200
                    },
                    {
                        step_name: "executor_agent",
                        step_content: "查询受影响部署的当前负载指标。",
                        step_output: "等待更多工具证据返回。",
                        status: "RUNNING",
                        start_time: "2026-05-26T10:18:04",
                        cost_ms: 6200
                    }
                ],
                tool_calls: [
                    {
                        tool_name: "queryMetrics",
                        status: "SUCCESS",
                        cost_ms: 980,
                        tool_params: "{\"cluster\":\"payments-prod\",\"service\":\"checkout\"}",
                        tool_result: "{\"p95_latency_ms\":1420,\"error_rate\":0.07}",
                        error_message: null,
                        started_at: "2026-05-26T10:18:05"
                    }
                ],
                replans: []
            },
            {
                trace_id: "trace-20260526-002",
                session: {
                    trace_id: "trace-20260526-002",
                    session_id: "aiops-20260526-002",
                    agent_type: "AIOPS_AGENT",
                    user_input: "排查 inventory-service 的 CPU 告警，当前缺少最终总结。",
                    final_answer: null,
                    status: "FAILED",
                    error_message: "最终报告缺失",
                    start_time: "2026-05-26T09:42:00",
                    end_time: "2026-05-26T09:44:12",
                    cost_ms: 132000
                },
                steps: [
                    {
                        step_name: "planner_agent",
                        step_content: "先按指标优先的方式排查 inventory-service。",
                        step_output: "指标证据不充分，需要继续查日志。",
                        status: "SUCCESS",
                        start_time: "2026-05-26T09:42:03",
                        cost_ms: 2400
                    },
                    {
                        step_name: "supervisor_agent",
                        step_content: "在证据不完整时协调工具重试。",
                        step_output: null,
                        status: "FAILED",
                        start_time: "2026-05-26T09:43:00",
                        cost_ms: 54000
                    }
                ],
                tool_calls: [
                    {
                        tool_name: "queryMetrics",
                        status: "EMPTY",
                        cost_ms: 710,
                        tool_params: "{\"service\":\"inventory-service\",\"window\":\"15m\"}",
                        tool_result: "{\"no_results\":true}",
                        error_message: null,
                        started_at: "2026-05-26T09:42:18"
                    },
                    {
                        tool_name: "queryLogs",
                        status: "FAILED",
                        cost_ms: 1530,
                        tool_params: "{\"apiKey\":\"***\",\"topic\":\"inventory-app\"}",
                        tool_result: "{\"status\":\"error\",\"tool\":\"queryLogs\",\"message\":\"connection refused\"}",
                        error_message: "连接被拒绝",
                        started_at: "2026-05-26T09:42:35"
                    }
                ],
                replans: [
                    {
                        trigger_step: "queryLogs",
                        reason: "TOOL_FAILED",
                        summary: "queryLogs 连续失败，切换排查策略。",
                        created_at: "2026-05-26T09:42:40"
                    },
                    {
                        trigger_step: "planner_agent",
                        reason: "EMPTY_RESULT",
                        summary: "planner 未给出最终报告。",
                        created_at: "2026-05-26T09:44:11"
                    }
                ]
            },
            {
                trace_id: "trace-20260526-001",
                session: {
                    trace_id: "trace-20260526-001",
                    session_id: "aiops-20260526-001",
                    agent_type: "AIOPS_AGENT",
                    user_input: "分析 order-service 的高内存告警。",
                    final_answer: "内存压力来自缓存未命中突增，扩容后已经恢复。",
                    status: "SUCCESS",
                    error_message: null,
                    start_time: "2026-05-26T08:55:00",
                    end_time: "2026-05-26T08:57:06",
                    cost_ms: 126000
                },
                steps: [
                    {
                        step_name: "planner_agent",
                        step_content: "拆成指标查看、日志查看和处置总结三部分。",
                        step_output: "指标和日志都指向短时缓存抖动。",
                        status: "SUCCESS",
                        start_time: "2026-05-26T08:55:02",
                        cost_ms: 1800
                    },
                    {
                        step_name: "executor_agent",
                        step_content: "采集 order-service 的指标和日志证据。",
                        step_output: "发现缓存未命中升高，并伴随堆内存短时尖峰。",
                        status: "SUCCESS",
                        start_time: "2026-05-26T08:55:08",
                        cost_ms: 21400
                    },
                    {
                        step_name: "supervisor_agent",
                        step_content: "整理并输出本次会话报告。",
                        step_output: "会话已正常结束。",
                        status: "SUCCESS",
                        start_time: "2026-05-26T08:56:40",
                        cost_ms: 9800
                    }
                ],
                tool_calls: [
                    {
                        tool_name: "queryMetrics",
                        status: "SUCCESS",
                        cost_ms: 640,
                        tool_params: "{\"service\":\"order-service\",\"window\":\"30m\"}",
                        tool_result: "{\"max_heap_pct\":88,\"cache_miss_ratio\":0.32}",
                        error_message: null,
                        started_at: "2026-05-26T08:55:11"
                    },
                    {
                        tool_name: "queryLogs",
                        status: "SUCCESS",
                        cost_ms: 1180,
                        tool_params: "{\"topic\":\"order-app\",\"keyword\":\"cache miss\"}",
                        tool_result: "{\"matches\":34}",
                        error_message: null,
                        started_at: "2026-05-26T08:55:23"
                    },
                    {
                        tool_name: "queryInternalDocs",
                        status: "SUCCESS",
                        cost_ms: 420,
                        tool_params: "{\"query\":\"order service cache incident runbook\"}",
                        tool_result: "{\"matches\":2}",
                        error_message: null,
                        started_at: "2026-05-26T08:55:41"
                    }
                ],
                replans: []
            }
        ];

        const detailsByTraceId = {};
        const detailsBySessionId = {};

        details.forEach((detail) => {
            detailsByTraceId[detail.trace_id] = detail;
            detailsBySessionId[detail.session.session_id] = detail;
        });

        const traces = details
            .map((detail) => ({
                trace_id: detail.trace_id,
                session_id: detail.session.session_id,
                agent_type: detail.session.agent_type,
                user_input: detail.session.user_input,
                final_answer: detail.session.final_answer,
                status: detail.session.status,
                error_message: detail.session.error_message,
                start_time: detail.session.start_time,
                end_time: detail.session.end_time,
                cost_ms: detail.session.cost_ms
            }))
            .sort((left, right) => right.start_time.localeCompare(left.start_time));

        return {
            traces,
            detailsByTraceId,
            detailsBySessionId,
            stats: this.buildDemoStats(details)
        };
    }

    buildDemoStats(details) {
        const sessions = details.map((detail) => detail.session);
        const totalSessions = sessions.length;
        const successCount = sessions.filter((session) => session.status === "SUCCESS").length;
        const failedCount = sessions.filter((session) => session.status === "FAILED").length;
        const runningCount = sessions.filter((session) => session.status === "RUNNING").length;
        const totalToolCalls = details.reduce((sum, detail) => sum + detail.tool_calls.length, 0);
        const totalCost = sessions.reduce((sum, session) => sum + Number(session.cost_ms || 0), 0);
        const recentFailures = sessions
            .filter((session) => session.status === "FAILED")
            .map((session) => ({
                trace_id: session.trace_id,
                session_id: session.session_id,
                error_message: session.error_message,
                start_time: session.start_time
            }));

        return {
            total_sessions: totalSessions,
            success_count: successCount,
            failed_count: failedCount,
            running_count: runningCount,
            average_cost_ms: totalSessions ? Math.round(totalCost / totalSessions) : 0,
            total_tool_calls: totalToolCalls,
            recent_failures: recentFailures
        };
    }

    parseFilterDate(value) {
        if (!value) {
            return null;
        }
        const parsed = new Date(value);
        return Number.isNaN(parsed.getTime()) ? null : parsed;
    }

    compactText(value, max = 120) {
        if (!value) {
            return "";
        }
        return value.length > max ? `${value.slice(0, max)}...` : value;
    }

    formatNumber(value) {
        if (value === null || value === undefined || Number.isNaN(Number(value))) {
            return "--";
        }
        return new Intl.NumberFormat().format(Number(value));
    }

    formatSuccessRate(successCount, totalCount) {
        const success = Number(successCount || 0);
        const total = Number(totalCount || 0);
        if (!total) {
            return "--";
        }
        return `${Math.round((success / total) * 100)}%`;
    }

    formatDuration(value) {
        if (value === null || value === undefined || Number.isNaN(Number(value))) {
            return "--";
        }
        const ms = Number(value);
        if (ms < 1000) {
            return `${ms} ms`;
        }
        if (ms < 60000) {
            return `${(ms / 1000).toFixed(1)} s`;
        }
        return `${(ms / 60000).toFixed(1)} min`;
    }

    formatDateTime(value) {
        if (!value) {
            return "--";
        }
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return value;
        }
        const y = date.getFullYear();
        const m = date.getMonth() + 1;
        const d = date.getDate();
        const hh = String(date.getHours()).padStart(2, "0");
        const mm = String(date.getMinutes()).padStart(2, "0");
        const ss = String(date.getSeconds()).padStart(2, "0");
        return `${y}年${m}月${d}日 ${hh}:${mm}:${ss}`;
    }

    renderInlineEmpty(message) {
        return `<div class="empty-state"><div><h3>暂无内容</h3><p>${this.escapeHtml(message)}</p></div></div>`;
    }

    showToast(message) {
        clearTimeout(this.toastTimer);
        this.toast.textContent = message;
        this.toast.classList.add("visible");
        this.toastTimer = window.setTimeout(() => {
            this.toast.classList.remove("visible");
        }, 2800);
    }

    escapeHtml(value) {
        return String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll("\"", "&quot;")
            .replaceAll("'", "&#39;");
    }

    compactCode(value) {
        if (!value) {
            return "-";
        }
        return value.length > 18 ? `${value.slice(0, 18)}...` : value;
    }

    getStatusLabel(status) {
        const map = {
            RUNNING: "运行中",
            SUCCESS: "成功",
            FAILED: "失败",
            EMPTY: "无结果",
            RETRY: "重试中"
        };
        return map[status] || status || "未知";
    }

    getReplanReasonLabel(reason) {
        const map = {
            TOOL_FAILED: "工具失败",
            EMPTY_RESULT: "结果为空",
            FINAL_REPORT_MISSING: "报告缺失",
            WORKFLOW_ERROR: "流程异常"
        };
        return map[reason] || reason || "重规划";
    }

    formatAgentLabel(agentType) {
        const map = {
            AIOPS_AGENT: "AI Ops"
        };
        return map[agentType] || agentType || "--";
    }

    formatStepName(stepName) {
        const map = {
            planner_agent: "规划",
            executor_agent: "执行",
            supervisor_agent: "收敛",
            queryLogs: "日志查询",
            queryMetrics: "指标查询",
            queryInternalDocs: "文档查询"
        };
        return map[stepName] || stepName || "步骤";
    }

    formatToolName(toolName) {
        const map = {
            queryMetrics: "指标查询",
            queryLogs: "日志查询",
            queryInternalDocs: "文档查询"
        };
        return map[toolName] || toolName || "工具";
    }

    translateFieldName(name) {
        const map = {
            p95_latency_ms: "P95 延迟",
            error_rate: "错误率",
            max_heap_pct: "最大堆占用",
            cache_miss_ratio: "缓存未命中率",
            matches: "命中数",
            status: "状态",
            tool: "工具",
            message: "说明"
        };
        return map[name] || name;
    }
}

document.addEventListener("DOMContentLoaded", () => {
    new TraceConsoleApp();
});
