ALTER TABLE agent_tool_call
    ADD COLUMN IF NOT EXISTS error_category VARCHAR(64);

ALTER TABLE agent_tool_call
    ADD COLUMN IF NOT EXISTS retry_count INT NOT NULL DEFAULT 0;
