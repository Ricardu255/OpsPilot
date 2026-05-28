ALTER TABLE agent_session
    ADD COLUMN IF NOT EXISTS root_trace_id VARCHAR(64);

ALTER TABLE agent_session
    ADD COLUMN IF NOT EXISTS parent_trace_id VARCHAR(64);

ALTER TABLE agent_session
    ADD COLUMN IF NOT EXISTS branch_type VARCHAR(32) NOT NULL DEFAULT 'MAIN';

ALTER TABLE agent_session
    ADD COLUMN IF NOT EXISTS branch_label VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_session_root_trace_id ON agent_session (root_trace_id);
CREATE INDEX IF NOT EXISTS idx_session_parent_trace_id ON agent_session (parent_trace_id);
