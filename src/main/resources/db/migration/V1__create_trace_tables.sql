-- =============================================================
-- V1: Create Trace Tables for AI Ops Agent Session Tracking
-- =============================================================

-- 1. agent_session —— AI Ops 会话主表
CREATE TABLE IF NOT EXISTS agent_session (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    trace_id        VARCHAR(64)   NOT NULL,
    session_id      VARCHAR(64)   NOT NULL,
    agent_type      VARCHAR(64)   NOT NULL DEFAULT 'UNKNOWN',
    user_input      CLOB          NOT NULL,
    final_answer    CLOB,
    status          VARCHAR(32)   NOT NULL DEFAULT 'RUNNING',
    error_message   CLOB,
    start_time      TIMESTAMP     NOT NULL,
    end_time        TIMESTAMP,
    cost_ms         BIGINT,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_session_trace_id    ON agent_session (trace_id);
CREATE INDEX idx_session_session_id  ON agent_session (session_id);
CREATE INDEX idx_session_agent_type  ON agent_session (agent_type);
CREATE INDEX idx_session_start_time  ON agent_session (start_time);
CREATE INDEX idx_session_status      ON agent_session (status);

-- 2. agent_trace_step —— 执行步骤明细
CREATE TABLE IF NOT EXISTS agent_trace_step (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    trace_id        VARCHAR(64)   NOT NULL,
    session_id      VARCHAR(64)   NOT NULL,
    step_name       VARCHAR(128)  NOT NULL,
    step_content    CLOB,
    step_output     CLOB,
    status          VARCHAR(32)   NOT NULL DEFAULT 'RUNNING',
    error_message   CLOB,
    start_time      TIMESTAMP     NOT NULL,
    end_time        TIMESTAMP,
    cost_ms         BIGINT,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_step_trace_id   ON agent_trace_step (trace_id);
CREATE INDEX idx_step_session_id ON agent_trace_step (session_id);

-- 3. agent_tool_call —— 工具调用记录
CREATE TABLE IF NOT EXISTS agent_tool_call (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    trace_id        VARCHAR(64)   NOT NULL,
    session_id      VARCHAR(64)   NOT NULL,
    tool_name       VARCHAR(128)  NOT NULL,
    tool_params     CLOB,
    tool_result     CLOB,
    status          VARCHAR(32)   NOT NULL DEFAULT 'SUCCESS',
    error_message   VARCHAR(2000),
    started_at      TIMESTAMP     NOT NULL,
    finished_at     TIMESTAMP     NOT NULL,
    cost_ms         BIGINT        NOT NULL DEFAULT 0,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tool_call_trace_id   ON agent_tool_call (trace_id);
CREATE INDEX idx_tool_call_session_id ON agent_tool_call (session_id);
CREATE INDEX idx_tool_call_tool_name  ON agent_tool_call (tool_name);

-- 4. agent_replan_record —— 重规划记录
CREATE TABLE IF NOT EXISTS agent_replan_record (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    trace_id        VARCHAR(64)   NOT NULL,
    session_id      VARCHAR(64)   NOT NULL,
    trigger_step    VARCHAR(128)  NOT NULL,
    reason          VARCHAR(64)   NOT NULL,
    summary         CLOB,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_replan_trace_id   ON agent_replan_record (trace_id);
CREATE INDEX idx_replan_session_id ON agent_replan_record (session_id);
