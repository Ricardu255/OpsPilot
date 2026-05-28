# Trace Schema

## Purpose

Trace storage captures one AI Ops execution chain as:

- one session row
- zero or more execution steps
- zero or more tool calls
- zero or more replan records

## Tables

### `agent_session`

Session summary for a single trace.

| Column | Type | Constraints | Notes |
| --- | --- | --- | --- |
| `id` | bigint | primary key, auto increment | Internal row id |
| `trace_id` | varchar(64) | not null, indexed | Stable trace id |
| `session_id` | varchar(64) | not null, indexed | External session id |
| `agent_type` | varchar(64) | not null, default `UNKNOWN`, indexed | Agent category |
| `user_input` | clob | not null | Original task or alert body |
| `final_answer` | clob | nullable | Final report |
| `status` | varchar(32) | not null, default `RUNNING`, indexed | `RUNNING`, `SUCCESS`, `FAILED` |
| `error_message` | clob | nullable | Terminal error summary |
| `start_time` | timestamp | not null, indexed | Session start |
| `end_time` | timestamp | nullable | Session end |
| `cost_ms` | bigint | nullable | Total duration |
| `created_at` | timestamp | not null | Row create time |
| `updated_at` | timestamp | not null | Row update time |

### `agent_trace_step`

Planner, executor, or supervisor steps inside a session.

| Column | Type | Constraints | Notes |
| --- | --- | --- | --- |
| `id` | bigint | primary key, auto increment | Internal row id |
| `trace_id` | varchar(64) | not null, indexed | Trace id |
| `session_id` | varchar(64) | not null, indexed | Session id |
| `step_name` | varchar(128) | not null | Step key |
| `step_content` | clob | nullable | Step input or summary |
| `step_output` | clob | nullable | Step output |
| `status` | varchar(32) | not null, default `RUNNING` | Step status |
| `error_message` | clob | nullable | Step error |
| `start_time` | timestamp | not null | Step start |
| `end_time` | timestamp | nullable | Step end |
| `cost_ms` | bigint | nullable | Step duration |
| `created_at` | timestamp | not null | Row create time |

### `agent_tool_call`

Trace rows for individual tool invocations.

| Column | Type | Constraints | Notes |
| --- | --- | --- | --- |
| `id` | bigint | primary key, auto increment | Internal row id |
| `trace_id` | varchar(64) | not null, indexed | Trace id |
| `session_id` | varchar(64) | not null, indexed | Session id |
| `tool_name` | varchar(128) | not null, indexed | Tool method name |
| `tool_params` | clob | nullable | Serialized request params |
| `tool_result` | clob | nullable | Truncated result summary |
| `status` | varchar(32) | not null, default `SUCCESS` | `SUCCESS`, `EMPTY`, `FAILED` |
| `error_message` | varchar(2000) | nullable | Short error text |
| `started_at` | timestamp | not null | Tool start |
| `finished_at` | timestamp | not null | Tool finish |
| `cost_ms` | bigint | not null, default `0` | Tool duration |
| `created_at` | timestamp | not null | Row create time |

### `agent_replan_record`

Records strategy changes triggered by repeated tool failures or empty results.

| Column | Type | Constraints | Notes |
| --- | --- | --- | --- |
| `id` | bigint | primary key, auto increment | Internal row id |
| `trace_id` | varchar(64) | not null, indexed | Trace id |
| `session_id` | varchar(64) | not null, indexed | Session id |
| `trigger_step` | varchar(128) | not null | Step or tool that triggered replan |
| `reason` | varchar(64) | not null | `TOOL_FAILED`, `EMPTY_RESULT`, `LOW_CONFIDENCE`, `UNKNOWN` |
| `summary` | clob | nullable | Human-readable reason |
| `created_at` | timestamp | not null | Row create time |

## Relationships

Logical relationships are linked by `trace_id` and `session_id`.

```text
agent_session (1)
  -> agent_trace_step (n)
  -> agent_tool_call (n)
  -> agent_replan_record (n)
```

The current schema does not enforce foreign keys so trace writes stay lightweight and resilient during partial failures.

## Migration source

DDL is maintained in:

[V1__create_trace_tables.sql](D:/code/Java/SuperBizAgent/SuperBizAgent/src/main/resources/db/migration/V1__create_trace_tables.sql)
