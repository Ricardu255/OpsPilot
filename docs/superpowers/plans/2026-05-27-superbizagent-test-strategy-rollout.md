# SuperBizAgent Test Strategy Rollout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 `SuperBizAgent-API-Automation` 仓库基础上，补齐测试分层、环境探测、Allure 报告增强与代表性场景用例，使其成为适合 `SSE / AIOps / Trace / MCP / Milvus` 的正式回归体系。

**Architecture:** 继续沿用当前仓库的 `pytest + allure + YAML + helper/executor` 架构，不推翻已有目录。增强重点放在三层：一是标准化执行分层与 marker；二是补强 fixture、环境快照与 Allure 附件；三是新增 `Trace / AIOps replan / CLS MCP fallback / external / e2e` 代表性用例。

**Tech Stack:** Python 3.10+, pytest, allure-pytest, requests, sseclient-py, PyYAML, jsonpath-ng

---

## File Structure

本轮计划主要修改以下文件：

- Modify: `D:\code\QA\SuperBizAgent-API-Automation\pytest.ini`
- Modify: `D:\code\QA\SuperBizAgent-API-Automation\conf\config.ini`
- Modify: `D:\code\QA\SuperBizAgent-API-Automation\README.md`
- Modify: `D:\code\QA\SuperBizAgent-API-Automation\run.py`
- Modify: `D:\code\QA\SuperBizAgent-API-Automation\conftest.py`
- Modify: `D:\code\QA\SuperBizAgent-API-Automation\common\allure_bridge.py`
- Modify: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\conftest.py`
- Modify: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\helpers.py`
- Modify: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\README.md`
- Modify: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\trace\trace.yaml`
- Modify: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\trace\test_trace_api.py`
- Modify: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\ai_ops\ai_ops.yaml`
- Modify: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\ai_ops\test_ai_ops_api.py`
- Modify: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\e2e\test_e2e_flow.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\external\__init__.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\external\cls_mcp_real.yaml`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\external\prometheus_real.yaml`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\external\test_external_api.py`

---

### Task 1: Normalize Pytest Markers And Execution Modes

**Files:**
- Modify: `D:\code\QA\SuperBizAgent-API-Automation\pytest.ini`
- Modify: `D:\code\QA\SuperBizAgent-API-Automation\conf\config.ini`
- Modify: `D:\code\QA\SuperBizAgent-API-Automation\README.md`
- Modify: `D:\code\QA\SuperBizAgent-API-Automation\run.py`

- [ ] **Step 1: Write the failing expectation as a README note**

在 `README.md` 中先补一段目标说明，明确当前问题：

```md
## Planned Test Layers

- `smoke`: 基础健康检查和稳定接口
- `core`: 核心聊天、Trace、会话能力
- `integration`: 上传、AIOps、跨接口链路
- `external`: 真实 Prometheus / CLS MCP / Milvus 联调
- `e2e`: 代表性端到端业务流

当前仓库仍主要使用 `smoke / integration / sse / e2e`，需要补齐 `core` 与 `external` 分层。
```

- [ ] **Step 2: Expand pytest markers**

将 `pytest.ini` 的 marker 定义改成：

```ini
[pytest]
addopts = -vs --alluredir ./report/temp --clean-alluredir
testpaths = ./testcase/SuperBizAgent
python_files = test_*.py
python_classes = Test*
python_functions = test_*
markers =
    superbiz: SuperBizAgent API tests
    smoke: fast smoke checks
    core: stable core business capability checks
    integration: multi-module integration checks
    external: tests that require real external dependencies
    sse: streaming response tests
    trace: trace-oriented tests
    aiops: ai ops workflow tests
    e2e: end-to-end business flow tests
```

- [ ] **Step 3: Update run configuration defaults**

将 `conf/config.ini` 的运行参数改成更适合日常回归的默认值：

```ini
[RUN]
marker_expression = smoke or core
test_path = ./testcase/SuperBizAgent
allure_results_dir = ./report/temp
allure_html_dir = ./report/allureReport
open_report = true
```

- [ ] **Step 4: Extend run.py to support named run profiles**

在 `run.py` 中为 `smoke/core/integration/external/e2e` 增加命令行透传支持，最小代码形态如下：

```python
from pathlib import Path
import sys
import pytest

from conf.operationConfig import OperationConfig


def main(argv=None) -> int:
    argv = argv or sys.argv[1:]
    repo_root = Path(__file__).resolve().parent
    config = OperationConfig()
    run_cfg = config.get_item_value("RUN")

    marker = " ".join(argv).strip() or run_cfg.get("marker_expression")
    test_path = repo_root / run_cfg.get("test_path", "testcase/SuperBizAgent")
    report_dir = repo_root / run_cfg.get("allure_results_dir", "report/temp")

    report_dir.mkdir(parents=True, exist_ok=True)
    args = [str(test_path), "--alluredir", str(report_dir), "--clean-alluredir"]
    if marker:
        args.extend(["-m", marker])
    return pytest.main(args)


if __name__ == "__main__":
    raise SystemExit(main())
```

- [ ] **Step 5: Document the supported run commands**

在 `README.md` 中新增一段明确命令：

```md
## Recommended Runs

- `python run.py` -> smoke or core
- `python run.py "integration and not external"` -> 集成回归
- `python run.py external` -> 真实依赖联调
- `python run.py e2e` -> 端到端链路
- `python run.py "aiops and sse"` -> AIOps 流式专项
```

---

### Task 2: Add Environment Snapshot Fixtures And Allure Context

**Files:**
- Modify: `D:\code\QA\SuperBizAgent-API-Automation\conftest.py`
- Modify: `D:\code\QA\SuperBizAgent-API-Automation\common\allure_bridge.py`
- Modify: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\conftest.py`
- Modify: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\helpers.py`

- [ ] **Step 1: Add environment snapshot helpers**

在 `common/allure_bridge.py` 中补三个方法：

```python
import json
import os

try:
    import allure
    from allure_commons.types import AttachmentType
except Exception:  # pragma: no cover
    allure = None
    AttachmentType = None


def attach_text(name, content):
    if not allure:
        return
    allure.attach(str(content), name=name, attachment_type=AttachmentType.TEXT if AttachmentType else None)


def attach_json(name, content):
    if not allure:
        return
    payload = content if isinstance(content, str) else json.dumps(content, ensure_ascii=False, indent=2)
    allure.attach(payload, name=name, attachment_type=AttachmentType.JSON if AttachmentType else None)


def build_env_snapshot(extra=None):
    snapshot = {
        "DASHSCOPE_API_KEY_present": bool(os.getenv("DASHSCOPE_API_KEY", "").strip()),
        "SUPERBIZ_RUN_AI_OPS": os.getenv("SUPERBIZ_RUN_AI_OPS", ""),
        "SUPERBIZ_RUN_EXTERNAL": os.getenv("SUPERBIZ_RUN_EXTERNAL", ""),
        "SUPERBIZ_TRACE_ASSERT_STRICT": os.getenv("SUPERBIZ_TRACE_ASSERT_STRICT", ""),
    }
    if extra:
        snapshot.update(extra)
    return snapshot
```

- [ ] **Step 2: Add a session-level environment fixture**

把根级 `conftest.py` 改成：

```python
import pytest

from base.removefile import remove_file
from common.allure_bridge import attach_json, build_env_snapshot
from common.readyaml import ReadYamlData
from conf.setting import EXTRACT_PATH, TEMP_DIR


@pytest.fixture(scope="session", autouse=True)
def prepare_runtime_artifacts():
    TEMP_DIR.mkdir(parents=True, exist_ok=True)
    ReadYamlData(EXTRACT_PATH).clear_yaml_data()
    remove_file(TEMP_DIR, [".json", ".txt", ".png", ".attach", ".tmp"])
    attach_json("runtime-environment", build_env_snapshot())
    yield
```

- [ ] **Step 3: Add service-state snapshot at test session level**

在 `testcase/SuperBizAgent/conftest.py` 中增加：

```python
import os
import pytest
import requests

from common.allure_bridge import attach_json, build_env_snapshot
from common.service_manager import ServiceManager
from conf.operationConfig import OperationConfig


@pytest.fixture(scope="session", autouse=True)
def attach_service_snapshot():
    manager = ServiceManager()
    snapshot = build_env_snapshot(
        {
            "base_url": OperationConfig().get_section_for_data("api_envi", "host"),
            "service_healthy": manager.wait_for_health(startup_timeout=1),
        }
    )
    attach_json("service-snapshot", snapshot)
```

- [ ] **Step 4: Add external dependency gate fixtures**

在 `testcase/SuperBizAgent/conftest.py` 中新增：

```python
@pytest.fixture
def external_enabled():
    if os.getenv("SUPERBIZ_RUN_EXTERNAL", "").strip().lower() not in {"1", "true", "yes", "on"}:
        pytest.skip("Set SUPERBIZ_RUN_EXTERNAL=1 to enable real external dependency checks")


@pytest.fixture
def trace_strict_enabled():
    return os.getenv("SUPERBIZ_TRACE_ASSERT_STRICT", "").strip().lower() in {"1", "true", "yes", "on"}
```

- [ ] **Step 5: Attach request and response context in helpers**

在 `testcase/SuperBizAgent/helpers.py` 中，为 `request_json()` 增加请求/响应附件：

```python
from common.allure_bridge import attach_json, attach_text


def request_json(session, method, path, **kwargs):
    url = build_url(path)
    attach_json("request", {"method": method, "url": url, "kwargs": kwargs})
    response = session.request(method=method, url=url, timeout=DEFAULT_TIMEOUT, **kwargs)
    attach_text("response-status", response.status_code)
    attach_text("response-text", response.text)
    response.raise_for_status()
    return response.json()
```

---

### Task 3: Add YAML Metadata Standards And Case Typing

**Files:**
- Modify: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\helpers.py`
- Modify: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\trace\trace.yaml`
- Modify: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\ai_ops\ai_ops.yaml`
- Modify: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\README.md`

- [ ] **Step 1: Define the YAML metadata shape**

在 `testcase/SuperBizAgent/README.md` 中增加规范：

```md
## Case Metadata

每条 YAML 用例建议补齐：

- `case_name`
- `case_type`: smoke / core / integration / external / e2e
- `priority`: P0 / P1 / P2
- `feature`: chat / trace / ai_ops / upload / health
- `requires`: mock / real-prometheus / real-cls / real-milvus
```

- [ ] **Step 2: Start annotating trace.yaml**

将 `trace/trace.yaml` 中的用例扩成如下形态：

```yaml
- case_name: trace_detail_not_found_has_message_field
  case_type: core
  priority: P0
  feature: trace
  requires: mock
  baseInfo:
    api_name: Trace明细查询-不存在的traceId返回404含message
    url: /api/trace/non-existent-trace-id-99999999
    method: GET
    headers:
      Accept: application/json
  validation:
    - check: status_code
      expected: 200
    - check: jsonpath_eq
      expected:
        path: "$.code"
        value: 404
    - check: jsonpath_exists
      expected: "$.message"
```

- [ ] **Step 3: Start annotating ai_ops.yaml**

给 `ai_ops/ai_ops.yaml` 加类似字段：

```yaml
- case_name: ai_ops_stream_finishes_with_done_event
  case_type: integration
  priority: P0
  feature: ai_ops
  requires: real-cls
  baseInfo:
    api_name: AI Ops流式分析-成功结束
    url: /api/ai_ops
    method: POST
```

- [ ] **Step 4: Teach helpers to expose metadata in Allure**

在 `helpers.py` 中增加：

```python
def attach_case_metadata(case_info):
    from common.allure_bridge import attach_json
    metadata = {
        "case_name": case_info.get("case_name"),
        "case_type": case_info.get("case_type"),
        "priority": case_info.get("priority"),
        "feature": case_info.get("feature"),
        "requires": case_info.get("requires"),
    }
    attach_json("case-metadata", metadata)
```

并在 `run_yaml_case()` / `run_upload_case()` 里调用它。

- [ ] **Step 5: Verify metadata appears in existing suites**

运行：

```powershell
conda run -n auto_test pytest D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\trace\test_trace_api.py -m smoke
```

Expected: Trace 用例继续可跑，且 Allure 结果中新增 metadata 附件。

---

### Task 4: Strengthen Trace And AIOps Representative Scenarios

**Files:**
- Modify: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\trace\test_trace_api.py`
- Modify: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\trace\trace.yaml`
- Modify: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\ai_ops\test_ai_ops_api.py`
- Modify: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\ai_ops\ai_ops.yaml`
- Modify: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\e2e\test_e2e_flow.py`

- [ ] **Step 1: Add a strict trace detail assertion case**

在 `trace/test_trace_api.py` 新增：

```python
@pytest.mark.superbiz
@pytest.mark.core
@pytest.mark.trace
def test_trace_stats_shape_is_stable(service_available, http_session):
    payload = request_json(
        http_session,
        "GET",
        "/api/trace/stats",
        headers={"Accept": "application/json"},
    )
    assert payload.get("code") == 200
    data = payload.get("data") or {}
    assert isinstance(data.get("recent_failures"), list)
    assert isinstance(data.get("slowest_traces"), list)
    assert data.get("total_sessions", 0) >= 0
```

- [ ] **Step 2: Add the CLS MCP fallback scenario**

在 `trace/test_trace_api.py` 中新增一条“真实模式下结构化错误”的接口级用例：

```python
@pytest.mark.superbiz
@pytest.mark.integration
@pytest.mark.trace
def test_cls_mcp_fallback_exposes_structured_error(service_available, http_session):
    payload = request_json(
        http_session,
        "GET",
        "/api/trace?agentType=AIOPS_AGENT&page=0&size=5",
        headers={"Accept": "application/json"},
    )
    assert payload.get("code") == 200
```

说明：这一步先通过 Trace 列表和 AIOps 执行结果确认“真实模式下链路可达”；更细的 `CLS_MCP_TOOL_UNAVAILABLE` 错误码断言放到 `external` 专项中，与真实依赖开关一起验证。

- [ ] **Step 3: Add the ai_ops child-trace scenario**

在 `ai_ops/test_ai_ops_api.py` 新增：

```python
@pytest.mark.superbiz
@pytest.mark.integration
@pytest.mark.aiops
@pytest.mark.sse
def test_ai_ops_replan_creates_child_trace(ai_ops_enabled, http_session):
    response = http_session.request(
        method="POST",
        url=build_url("/api/ai_ops"),
        json={},
        headers={"Content-Type": "application/json", "Accept": "text/event-stream"},
        timeout=300,
        stream=True,
    )
    assert response.status_code == 200
    try:
        for line in response.iter_lines(decode_unicode=True):
            if line and ('\"type\":\"done\"' in line or '\"type\":\"error\"' in line):
                break
    finally:
        response.close()

    payload = request_json(
        http_session,
        "GET",
        "/api/trace?agentType=AIOPS_AGENT&page=0&size=10",
        headers={"Accept": "application/json"},
    )
    assert payload.get("code") == 200
```

- [ ] **Step 4: Promote these scenarios into e2e**

在 `e2e/test_e2e_flow.py` 中组合：

```python
@pytest.mark.superbiz
@pytest.mark.e2e
@pytest.mark.aiops
def test_ai_ops_trace_e2e(ai_ops_enabled, http_session):
    response = http_session.request(
        method="POST",
        url=build_url("/api/ai_ops"),
        json={},
        headers={"Content-Type": "application/json", "Accept": "text/event-stream"},
        timeout=300,
        stream=True,
    )
    assert response.status_code == 200

    try:
        stream_lines = []
        for line in response.iter_lines(decode_unicode=True):
            if not line:
                continue
            stream_lines.append(line)
            if '"type":"done"' in line or '"type": "done"' in line:
                break
    finally:
        response.close()

    assert any('"type":"done"' in line or '"type": "done"' in line for line in stream_lines)

    stats_payload = request_json(
        http_session,
        "GET",
        "/api/trace/stats",
        headers={"Accept": "application/json"},
    )
    assert stats_payload.get("code") == 200

    list_payload = request_json(
        http_session,
        "GET",
        "/api/trace?agentType=AIOPS_AGENT&page=0&size=10",
        headers={"Accept": "application/json"},
    )
    assert list_payload.get("code") == 200
    assert list_payload.get("total", 0) >= 1
```

- [ ] **Step 5: Run focused regression**

运行：

```powershell
conda run -n auto_test pytest D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\trace\test_trace_api.py -m "core or integration"
conda run -n auto_test pytest D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\ai_ops\test_ai_ops_api.py -m "integration and aiops"
```

Expected: Trace 与 AIOps 代表性场景可单独执行，报告有独立 story/feature。

---

### Task 5: Add External Dependency Suite And Final Documentation

**Files:**
- Create: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\external\__init__.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\external\cls_mcp_real.yaml`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\external\prometheus_real.yaml`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\external\test_external_api.py`
- Modify: `D:\code\QA\SuperBizAgent-API-Automation\README.md`
- Modify: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\README.md`

- [ ] **Step 1: Create the external test entry**

新增 `external/test_external_api.py`：

```python
import allure
import pytest

from ..helpers import request_json, set_title_and_description


@allure.parent_suite("SuperBizAgent 接口自动化")
@allure.suite("External 真实依赖联调")
@allure.feature("External Dependencies")
class TestExternalApi:

    @pytest.mark.superbiz
    @pytest.mark.external
    def test_prometheus_real_health(external_enabled, service_available, http_session):
        set_title_and_description(
            title="Prometheus真实链路-服务侧可访问",
            description="验证在 external 模式下，服务接口可正常返回且未在启动前失败。",
        )
        payload = request_json(http_session, "GET", "/api/trace/stats", headers={"Accept": "application/json"})
        assert payload.get("code") == 200
```

- [ ] **Step 2: Add CLS MCP real-mode YAML**

新增 `external/cls_mcp_real.yaml`：

```yaml
- case_name: cls_mcp_real_mode_is_enabled
  case_type: external
  priority: P0
  feature: trace
  requires: real-cls
  baseInfo:
    api_name: CLS MCP真实模式-环境联调
    url: /api/trace/stats
    method: GET
    headers:
      Accept: application/json
```

- [ ] **Step 3: Add Prometheus real-mode YAML**

新增 `external/prometheus_real.yaml`：

```yaml
- case_name: prometheus_real_mode_is_enabled
  case_type: external
  priority: P0
  feature: ai_ops
  requires: real-prometheus
  baseInfo:
    api_name: Prometheus真实模式-环境联调
    url: /milvus/health
    method: GET
    headers:
      Accept: application/json
```

- [ ] **Step 4: Document external prerequisites**

在 `README.md` 中新增：

```md
## External Runs

以下场景要求真实依赖准备完毕：

- `SUPERBIZ_RUN_EXTERNAL=1`
- Prometheus 可访问
- CLS MCP SSE 服务可访问
- SuperBizAgent 已按真实模式启动

推荐执行：

`conda run -n auto_test pytest -m external`
```

- [ ] **Step 5: Run a final layered command matrix**

执行：

```powershell
conda run -n auto_test pytest -m smoke
conda run -n auto_test pytest -m "smoke or core"
conda run -n auto_test pytest -m "integration and not external"
conda run -n auto_test pytest -m external
```

Expected:

- `smoke` 可快速完成；
- `core` 能覆盖聊天、Trace、会话主链路；
- `integration` 能覆盖上传和 AIOps；
- `external` 只在显式打开真实依赖时执行；
- Allure 报告中可看到分层后的 suites、stories、attachments。

---

## Final Notes

本计划不是从零搭建自动化仓库，而是对现有 `SuperBizAgent-API-Automation` 的增强落地。执行时应遵循以下顺序：

1. 先规范 marker 与运行入口；
2. 再补 fixture 和 Allure 上下文；
3. 然后为 YAML 增加元数据；
4. 最后补 `Trace / AIOps / external / e2e` 代表性场景。

这样可以保证每一步都能单独验证，不会一上来把测试体系整体推翻。
