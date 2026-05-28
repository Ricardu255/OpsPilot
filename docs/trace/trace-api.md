# Trace API

## Overview

Trace APIs expose AI Ops session chains, execution steps, tool calls, replan records, and aggregate stats.

Base URL:

```text
http://localhost:9900
```

Common response envelope:

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

## Endpoints

### `GET /api/trace/{traceId}`

Returns a full trace chain by `traceId`.

Path params:

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `traceId` | string | yes | Trace identifier |

Example:

```bash
curl "http://localhost:9900/api/trace/trace-1"
```

Response shape:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "trace_id": "trace-1",
    "session": {
      "id": 1,
      "trace_id": "trace-1",
      "session_id": "session-1",
      "agent_type": "AIOPS_AGENT",
      "user_input": "alert body",
      "final_answer": "resolved",
      "status": "SUCCESS",
      "error_message": null,
      "start_time": "2026-05-26T15:00:00",
      "end_time": "2026-05-26T15:01:00",
      "cost_ms": 60000,
      "created_at": "2026-05-26T15:00:00"
    },
    "steps": [],
    "tool_calls": [],
    "replans": []
  }
}
```

### `GET /api/trace/session/{sessionId}`

Returns a full trace chain by `sessionId`.

Path params:

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `sessionId` | string | yes | Session identifier |

Example:

```bash
curl "http://localhost:9900/api/trace/session/session-1"
```

Notes:

- This endpoint returns the same detail shape as `GET /api/trace/{traceId}`.
- `tool_calls[*].tool_params` are masked for sensitive keys such as `password`, `apiKey`, and `token`.

### `GET /api/trace`

Returns a paged session list.

Query params:

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `agentType` | string | no | Exact agent type filter |
| `status` | string | no | `RUNNING`, `SUCCESS`, `FAILED` |
| `startFrom` | ISO datetime | no | Start time lower bound |
| `startTo` | ISO datetime | no | Start time upper bound |
| `page` | integer | no | Zero-based page index, default `0` |
| `size` | integer | no | Page size, default `20` |

Example:

```bash
curl "http://localhost:9900/api/trace?status=FAILED&page=0&size=10"
```

Response shape:

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 2,
      "trace_id": "trace-2",
      "session_id": "session-2",
      "agent_type": "AIOPS_AGENT",
      "user_input": "second alert",
      "final_answer": null,
      "status": "FAILED",
      "error_message": "final report missing",
      "start_time": "2026-05-26T15:10:00",
      "end_time": "2026-05-26T15:10:45",
      "cost_ms": 45000,
      "created_at": "2026-05-26T15:10:00"
    }
  ],
  "total": 1,
  "page": 0,
  "size": 10,
  "totalPages": 1
}
```

### `GET /api/trace/stats`

Returns aggregate counts and top samples.

Example:

```bash
curl "http://localhost:9900/api/trace/stats"
```

Response shape:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total_sessions": 2,
    "success_count": 1,
    "failed_count": 1,
    "running_count": 0,
    "average_cost_ms": 52500.0,
    "total_tool_calls": 1,
    "failed_tool_calls": 0,
    "recent_failures": [],
    "slowest_traces": []
  }
}
```

## Error handling

Not found cases still return HTTP `200` with a non-success payload:

```json
{
  "code": 404,
  "message": "No trace session found for sessionId=session-x"
}
```

Unexpected server errors return:

```json
{
  "code": 500,
  "message": "error message"
}
```

## Truncation and masking

- `session.user_input`, `session.error_message`, `step.step_content`, `step.error_message`, and `tool_calls[*].tool_params` are truncated for read safety.
- `tool_calls[*].tool_result` and `session.final_answer` are truncated for payload size control.
- Sensitive tool param keys are masked to `***`.

## Prometheus metrics

Prometheus metrics are exposed through:

```text
GET /actuator/prometheus
```

Trace-related metric names:

- `agent_session_total`
- `agent_session_duration_ms`
- `agent_tool_call_total`
- `agent_tool_call_failed_total`
