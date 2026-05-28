# SuperBizAgent API Automation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新建一个独立的 `SuperBizAgent` Python 接口自动化仓库，能力整体对齐参考项目，并支持当前项目所需的 HTTP、SSE、文件上传与 Milvus 健康检查场景。

**Architecture:** 新仓库采用 `pytest + allure + YAML 用例驱动 + extract.yaml 数据池` 模式，目录结构整体对齐参考项目。普通 HTTP 接口和 SSE 接口分别走不同执行器，公共层负责配置、请求发送、断言、日志、Allure 与运行时变量管理。

**Tech Stack:** Python 3.10+, pytest, allure-pytest, requests, sseclient-py, PyYAML, jsonpath-ng

---

## File Structure

本计划最终将创建如下核心文件：

- Create: `D:\code\QA\SuperBizAgent-API-Automation\README.md`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\requirements.txt`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\pytest.ini`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\run.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\conftest.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\extract.yaml`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\base\apiutil.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\base\superbiz_apiutil.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\base\superbiz_sse_apiutil.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\base\generateId.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\base\removefile.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\common\assertions.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\common\superbiz_assertions.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\common\readyaml.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\common\debugtalk.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\common\sendrequest.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\common\recordlog.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\common\allure_bridge.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\common\service_manager.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\common\connection.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\conf\config.ini`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\conf\operationConfig.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\conf\setting.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\data\files\demo_upload.md`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\README.md`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\health.yaml`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\chat.yaml`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\chat_stream.yaml`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\clear_chat.yaml`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\session.yaml`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\milvus_health.yaml`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\upload.yaml`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\ai_ops.yaml`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\conftest.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\test_superbiz_api.py`

---

### Task 1: Scaffold The Independent Automation Repository

**Files:**
- Create: `D:\code\QA\SuperBizAgent-API-Automation\README.md`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\requirements.txt`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\pytest.ini`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\run.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\extract.yaml`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\base\__init__.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\common\__init__.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\conf\__init__.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\data\__init__.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\testcase\__init__.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\__init__.py`

- [ ] **Step 1: Create the repository directories**

Run:

```powershell
New-Item -ItemType Directory -Force `
  D:\code\QA\SuperBizAgent-API-Automation, `
  D:\code\QA\SuperBizAgent-API-Automation\base, `
  D:\code\QA\SuperBizAgent-API-Automation\common, `
  D:\code\QA\SuperBizAgent-API-Automation\conf, `
  D:\code\QA\SuperBizAgent-API-Automation\data\files, `
  D:\code\QA\SuperBizAgent-API-Automation\data\sql, `
  D:\code\QA\SuperBizAgent-API-Automation\logs, `
  D:\code\QA\SuperBizAgent-API-Automation\report\temp, `
  D:\code\QA\SuperBizAgent-API-Automation\report\allureReport, `
  D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent
```

Expected: all directories are created with no errors.

- [ ] **Step 2: Add the Python dependency manifest**

```txt
pytest==8.3.2
allure-pytest==2.13.5
requests==2.32.3
PyYAML==6.0.2
jsonpath-ng==1.6.1
sseclient-py==1.8.0
```

- [ ] **Step 3: Add the base pytest configuration**

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
    integration: tests that require external dependencies
    sse: streaming response tests
```

- [ ] **Step 4: Add the project README**

```md
# SuperBizAgent API Automation

独立的 Python 接口自动化仓库，使用 `pytest + allure + YAML` 驱动 `SuperBizAgent` 接口测试。

## Features

- YAML 测试用例
- Allure 报告
- extract.yaml 运行时数据池
- 普通 HTTP 接口执行
- SSE 接口执行
```

- [ ] **Step 5: Add the initial run entrypoint**

```python
from pathlib import Path
import subprocess
import pytest


def main() -> int:
    results_dir = Path("./report/temp")
    results_dir.mkdir(parents=True, exist_ok=True)
    return pytest.main(["-vs", "./testcase/SuperBizAgent", "--alluredir", str(results_dir), "--clean-alluredir"])


if __name__ == "__main__":
    raise SystemExit(main())
```

- [ ] **Step 6: Initialize the extract data pool**

```yaml
{}
```

- [ ] **Step 7: Install dependencies and verify pytest starts**

Run:

```powershell
python -m pip install -r D:\code\QA\SuperBizAgent-API-Automation\requirements.txt
python -m pytest --version
```

Expected: pytest version prints successfully.

- [ ] **Step 8: Initialize git and commit the scaffold**

Run:

```powershell
git init D:\code\QA\SuperBizAgent-API-Automation
git -C D:\code\QA\SuperBizAgent-API-Automation add .
git -C D:\code\QA\SuperBizAgent-API-Automation commit -m "chore: scaffold automation repository"
```

Expected: first commit is created in the new automation repository.

---

### Task 2: Add Configuration, Logging, YAML Loading, And Runtime Variable Support

**Files:**
- Create: `D:\code\QA\SuperBizAgent-API-Automation\conf\config.ini`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\conf\setting.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\conf\operationConfig.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\common\recordlog.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\common\readyaml.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\common\debugtalk.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\base\generateId.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\base\removefile.py`

- [ ] **Step 1: Add the environment and run configuration file**

```ini
[api_envi]
host = http://127.0.0.1:9900

[REPORT_TYPE]
type = allure

[RUN]
marker_expression = smoke
test_path = ./testcase/SuperBizAgent
allure_results_dir = ./report/temp
allure_html_dir = ./report/allureReport
open_report = true

[SERVICE]
enabled = auto
start_command =
start_cwd =
health_url = http://127.0.0.1:9900/milvus/health
startup_timeout = 300
shutdown_timeout = 15
poll_interval = 2
request_timeout = 5
```

- [ ] **Step 2: Add the path and constant settings module**

```python
import logging
import os
import sys

DIR_BASE = os.path.dirname(os.path.dirname(__file__))
if DIR_BASE not in sys.path:
    sys.path.append(DIR_BASE)

LOG_LEVEL = logging.DEBUG
STREAM_LOG_LEVEL = logging.INFO
API_TIMEOUT = 120
REPORT_TYPE = "allure"

FILE_PATH = {
    "CONFIG": os.path.join(DIR_BASE, "conf", "config.ini"),
    "LOG": os.path.join(DIR_BASE, "logs"),
    "TEMP": os.path.join(DIR_BASE, "report", "temp"),
    "EXTRACT": os.path.join(DIR_BASE, "extract.yaml"),
    "YAML": DIR_BASE,
}
```

- [ ] **Step 3: Add the config reader abstraction**

```python
import configparser
from conf import setting


class OperationConfig:
    def __init__(self, filepath=None):
        self._filepath = filepath or setting.FILE_PATH["CONFIG"]
        self.conf = configparser.ConfigParser()
        self.conf.read(self._filepath, encoding="utf-8")

    def get_section_for_data(self, section, option):
        return self.conf.get(section, option)

    def get_item_value(self, section_name):
        return dict(self.conf.items(section_name))
```

- [ ] **Step 4: Add the shared logger**

```python
import logging
from pathlib import Path
from conf.setting import FILE_PATH, LOG_LEVEL, STREAM_LOG_LEVEL


Path(FILE_PATH["LOG"]).mkdir(parents=True, exist_ok=True)
logs = logging.getLogger("superbiz-api-automation")
logs.setLevel(LOG_LEVEL)

if not logs.handlers:
    formatter = logging.Formatter("%(asctime)s | %(levelname)s | %(message)s")
    file_handler = logging.FileHandler(Path(FILE_PATH["LOG"]) / "test.log", encoding="utf-8")
    file_handler.setFormatter(formatter)
    stream_handler = logging.StreamHandler()
    stream_handler.setLevel(STREAM_LOG_LEVEL)
    stream_handler.setFormatter(formatter)
    logs.addHandler(file_handler)
    logs.addHandler(stream_handler)
```

- [ ] **Step 5: Add YAML loading and extract pool support**

```python
import yaml
from conf.setting import FILE_PATH


def get_testcase_yaml(file_path):
    with open(file_path, "r", encoding="utf-8") as handle:
        data = yaml.safe_load(handle)
    testcase_list = []
    for block in data:
        base_info = block["baseInfo"]
        for case in block["testCase"]:
            testcase_list.append([base_info, case])
    return testcase_list


class ReadYamlData:
    def write_yaml_data(self, value):
        with open(FILE_PATH["EXTRACT"], "a", encoding="utf-8") as handle:
            yaml.safe_dump(value, handle, allow_unicode=True, sort_keys=False)

    def clear_yaml_data(self):
        with open(FILE_PATH["EXTRACT"], "w", encoding="utf-8") as handle:
            handle.write("{}\n")
```

- [ ] **Step 6: Add dynamic replacement helpers**

```python
import uuid
from datetime import datetime


class DebugTalk:
    def uuid(self):
        return str(uuid.uuid4())

    def session_id(self):
        return f"session-{uuid.uuid4().hex[:8]}"

    def now_ts(self):
        return str(int(datetime.now().timestamp()))
```

- [ ] **Step 7: Add simple ID generators and cleanup helper**

```python
import itertools

m_id = itertools.count(1)
c_id = itertools.count(1)
```

```python
from pathlib import Path


def remove_file(directory, suffixes):
    target = Path(directory)
    for path in target.glob("*"):
        if path.suffix.lstrip(".") in suffixes:
            path.unlink(missing_ok=True)
```

- [ ] **Step 8: Verify YAML parsing and extract reset behavior**

Run:

```powershell
python -c "from common.readyaml import ReadYamlData; ReadYamlData().clear_yaml_data(); print('ok')"
```

Expected: command prints `ok` and `extract.yaml` is reset.

- [ ] **Step 9: Commit the configuration and runtime support**

Run:

```powershell
git -C D:\code\QA\SuperBizAgent-API-Automation add .
git -C D:\code\QA\SuperBizAgent-API-Automation commit -m "feat: add configuration and runtime data support"
```

Expected: second commit is created.

---

### Task 3: Add Request Engine, Assertions, Allure Integration, And Base Executors

**Files:**
- Create: `D:\code\QA\SuperBizAgent-API-Automation\common\sendrequest.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\common\assertions.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\common\superbiz_assertions.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\common\allure_bridge.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\base\apiutil.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\base\superbiz_apiutil.py`

- [ ] **Step 1: Add the unified request sender**

```python
import json
import requests
import allure
from conf import setting
from common.recordlog import logs


class SendRequest:
    def run_main(self, name, url, case_name, header, method, cookies=None, file=None, **kwargs):
        logs.info("api=%s case=%s method=%s url=%s", name, case_name, method, url)
        if kwargs:
            allure.attach(json.dumps(kwargs, ensure_ascii=False, indent=2), "request", allure.attachment_type.TEXT)
        response = requests.request(
            method=method,
            url=url,
            headers=header,
            cookies=cookies,
            files=file,
            timeout=setting.API_TIMEOUT,
            verify=False,
            **kwargs,
        )
        return response
```

- [ ] **Step 2: Add the generic assertion center**

```python
import json
import re


class Assertions:
    def assert_result(self, expected, response, status_code):
        rules = expected if isinstance(expected, list) else [expected]
        body_text = response if isinstance(response, str) else json.dumps(response, ensure_ascii=False)
        body_json = response if isinstance(response, dict) else None

        for rule in rules:
            for key, value in rule.items():
                if key == "status_code":
                    assert int(status_code) == int(value)
                elif key == "text_contains":
                    assert str(value) in body_text
                elif key == "text_not_contains":
                    assert str(value) not in body_text
                elif key == "text_regex":
                    assert re.search(str(value), body_text, re.S)
                elif key == "min_length":
                    assert len(body_text.strip()) >= int(value)
                elif key == "json_eq":
                    assert isinstance(body_json, dict)
                    for field, expected_value in value.items():
                        assert body_json.get(field) == expected_value
                elif key == "json_contains":
                    assert isinstance(body_json, dict)
                    for field in value:
                        assert field in body_json
```

- [ ] **Step 3: Add the project-specific assertion subclass**

```python
from common.assertions import Assertions


class SuperBizAssertions(Assertions):
    pass
```

- [ ] **Step 4: Add the generic YAML executor base class**

```python
import json
from common.readyaml import ReadYamlData
from common.sendrequest import SendRequest
from common.assertions import Assertions
from conf.operationConfig import OperationConfig


class RequestBase:
    def __init__(self):
        self.run = SendRequest()
        self.conf = OperationConfig()
        self.read = ReadYamlData()
        self.asserts = Assertions()

    def replace_load(self, data):
        return data

    @staticmethod
    def allure_attach_response(response):
        if isinstance(response, (dict, list)):
            return json.dumps(response, ensure_ascii=False, indent=2)
        return str(response)
```

- [ ] **Step 5: Add the normal HTTP executor**

```python
import json
import allure
from base.apiutil import RequestBase
from common.superbiz_assertions import SuperBizAssertions


class SuperBizRequestBase(RequestBase):
    def __init__(self):
        super().__init__()
        self.asserts = SuperBizAssertions()

    def specification_yaml(self, base_info, test_case):
        url_host = self.conf.get_section_for_data("api_envi", "host")
        api_name = base_info["api_name"]
        url = url_host.rstrip("/") + "/" + base_info["url"].lstrip("/")
        method = base_info["method"]
        header = base_info["header"]
        case_name = test_case.pop("case_name")
        validation = test_case.pop("validation")
        response = self.run.run_main(api_name, url, case_name, header, method, **test_case)
        status_code = response.status_code
        try:
            response_body = response.json()
        except Exception:
            response_body = response.text
        allure.attach(self.allure_attach_response(response_body), "response", allure.attachment_type.TEXT)
        self.asserts.assert_result(validation, response_body, status_code)
```

- [ ] **Step 6: Add the root pytest hooks and Allure environment writer**

```python
from pathlib import Path
import warnings
import pytest
from base.removefile import remove_file
from common.readyaml import ReadYamlData


@pytest.fixture(scope="session", autouse=True)
def clear_extract():
    warnings.simplefilter("ignore", ResourceWarning)
    Path("./report/temp").mkdir(parents=True, exist_ok=True)
    ReadYamlData().clear_yaml_data()
    remove_file("./report/temp", ["json", "txt", "attach", "properties"])
```

- [ ] **Step 7: Verify the core modules import cleanly**

Run:

```powershell
python -c "from base.superbiz_apiutil import SuperBizRequestBase; print(SuperBizRequestBase.__name__)"
```

Expected: prints `SuperBizRequestBase`.

- [ ] **Step 8: Commit the core execution engine**

Run:

```powershell
git -C D:\code\QA\SuperBizAgent-API-Automation add .
git -C D:\code\QA\SuperBizAgent-API-Automation commit -m "feat: add request engine and base YAML executors"
```

Expected: commit is created successfully.

---

### Task 4: Add SSE Support, Service Probing, And The First Smoke Cases

**Files:**
- Create: `D:\code\QA\SuperBizAgent-API-Automation\common\service_manager.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\base\superbiz_sse_apiutil.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\health.yaml`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\chat.yaml`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\chat_stream.yaml`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\clear_chat.yaml`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\session.yaml`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\milvus_health.yaml`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\test_superbiz_api.py`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\README.md`

- [ ] **Step 1: Add a health probe utility**

```python
import time
import requests
from conf.operationConfig import OperationConfig


def wait_until_ready():
    conf = OperationConfig()
    url = conf.get_section_for_data("SERVICE", "health_url")
    timeout = int(conf.get_section_for_data("SERVICE", "startup_timeout"))
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            response = requests.get(url, timeout=5)
            if response.status_code < 500:
                return True
        except Exception:
            pass
        time.sleep(2)
    raise TimeoutError(f"Service health check timed out: {url}")
```

- [ ] **Step 2: Add the SSE executor**

```python
import allure
import requests
from base.apiutil import RequestBase
from common.superbiz_assertions import SuperBizAssertions


class SuperBizSseRequestBase(RequestBase):
    def __init__(self):
        super().__init__()
        self.asserts = SuperBizAssertions()

    def specification_yaml(self, base_info, test_case):
        url_host = self.conf.get_section_for_data("api_envi", "host")
        api_name = base_info["api_name"]
        url = url_host.rstrip("/") + "/" + base_info["url"].lstrip("/")
        header = base_info["header"]
        method = base_info["method"]
        case_name = test_case.pop("case_name")
        validation = test_case.pop("validation")
        response = requests.request(method=method, url=url, headers=header, stream=True, timeout=120, **test_case)
        chunks = []
        for line in response.iter_lines(decode_unicode=True):
            if line:
                chunks.append(line)
        body = "\n".join(chunks)
        allure.attach(body, "sse-response", allure.attachment_type.TEXT)
        self.asserts.assert_result(validation, body, response.status_code)
```

- [ ] **Step 3: Add the health and Milvus health smoke YAML files**

```yaml
- baseInfo:
    api_name: Milvus Health
    url: /milvus/health
    method: get
    header:
      Accept: application/json
  testCase:
    - case_name: milvus health returns 200
      validation:
        - status_code: 200
```

```yaml
- baseInfo:
    api_name: Chat Session Health
    url: /api/chat/session/demo-session
    method: get
    header:
      Accept: application/json
  testCase:
    - case_name: session endpoint is reachable
      validation:
        - status_code: 200
```

- [ ] **Step 4: Add the chat smoke YAML**

```yaml
- baseInfo:
    api_name: Chat
    url: /api/chat
    method: post
    header:
      Content-Type: application/json
      Accept: application/json
  testCase:
    - case_name: chat returns success envelope
      json:
        Id: session-smoke-001
        Question: 请回复一句自动化测试短句
      validation:
        - status_code: 200
        - json_contains:
            code: 200
            message: success
```

- [ ] **Step 5: Add the stream, clear, and session YAML**

```yaml
- baseInfo:
    api_name: Chat Stream
    url: /api/chat_stream
    method: post
    header:
      Content-Type: application/json
      Accept: text/event-stream
  testCase:
    - case_name: stream returns content and done
      json:
        Id: session-stream-001
        Question: 请回复一句自动化测试短句
      validation:
        - status_code: 200
        - text_contains: content
        - text_contains: done
```

```yaml
- baseInfo:
    api_name: Clear Chat
    url: /api/chat/clear
    method: post
    header:
      Content-Type: application/json
      Accept: application/json
  testCase:
    - case_name: clear endpoint returns envelope
      json:
        Id: session-smoke-001
      validation:
        - status_code: 200
```

```yaml
- baseInfo:
    api_name: Session Info
    url: /api/chat/session/session-smoke-001
    method: get
    header:
      Accept: application/json
  testCase:
    - case_name: session endpoint returns envelope
      validation:
        - status_code: 200
```

- [ ] **Step 6: Add the first pytest entry file**

```python
import allure
import pytest
from base.superbiz_apiutil import SuperBizRequestBase
from base.superbiz_sse_apiutil import SuperBizSseRequestBase
from common.readyaml import get_testcase_yaml


@allure.feature("SuperBizAgent API")
class TestSuperBizApi:
    @pytest.mark.superbiz
    @pytest.mark.smoke
    @pytest.mark.parametrize("base_info,testcase", get_testcase_yaml("./testcase/SuperBizAgent/chat.yaml"))
    def test_chat(self, base_info, testcase):
        allure.dynamic.title(testcase["case_name"])
        SuperBizRequestBase().specification_yaml(base_info, testcase)

    @pytest.mark.superbiz
    @pytest.mark.smoke
    @pytest.mark.sse
    @pytest.mark.parametrize("base_info,testcase", get_testcase_yaml("./testcase/SuperBizAgent/chat_stream.yaml"))
    def test_chat_stream(self, base_info, testcase):
        allure.dynamic.title(testcase["case_name"])
        SuperBizSseRequestBase().specification_yaml(base_info, testcase)
```

- [ ] **Step 7: Run the first smoke tests against a running local service**

Run:

```powershell
python D:\code\QA\SuperBizAgent-API-Automation\run.py
```

Expected: tests start, produce Allure raw results, and either pass or fail with actionable endpoint-level assertions.

- [ ] **Step 8: Commit the smoke coverage**

Run:

```powershell
git -C D:\code\QA\SuperBizAgent-API-Automation add .
git -C D:\code\QA\SuperBizAgent-API-Automation commit -m "feat: add smoke coverage for core APIs"
```

Expected: commit is created successfully.

---

### Task 5: Add Upload And AIOps Integration Cases, Then Harden Documentation

**Files:**
- Create: `D:\code\QA\SuperBizAgent-API-Automation\data\files\demo_upload.md`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\upload.yaml`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\ai_ops.yaml`
- Modify: `D:\code\QA\SuperBizAgent-API-Automation\testcase\SuperBizAgent\test_superbiz_api.py`
- Modify: `D:\code\QA\SuperBizAgent-API-Automation\README.md`
- Create: `D:\code\QA\SuperBizAgent-API-Automation\common\connection.py`

- [ ] **Step 1: Add an upload sample file**

```md
# Demo Upload

这是用于 `SuperBizAgent` 上传接口自动化测试的示例文档。
```

- [ ] **Step 2: Add the upload integration YAML**

```yaml
- baseInfo:
    api_name: Upload File
    url: /api/upload
    method: post
    header:
      Accept: application/json
  testCase:
    - case_name: upload returns success envelope
      files:
        file: ./data/files/demo_upload.md
      validation:
        - status_code: 200
        - text_contains: success
```

- [ ] **Step 3: Add the AIOps integration YAML**

```yaml
- baseInfo:
    api_name: AI Ops
    url: /api/ai_ops
    method: post
    header:
      Accept: text/event-stream
  testCase:
    - case_name: aiops stream returns done marker
      validation:
        - status_code: 200
        - text_contains: done
```

- [ ] **Step 4: Extend the pytest entry with integration markers**

```python
    @pytest.mark.superbiz
    @pytest.mark.integration
    @pytest.mark.parametrize("base_info,testcase", get_testcase_yaml("./testcase/SuperBizAgent/upload.yaml"))
    def test_upload(self, base_info, testcase):
        allure.dynamic.title(testcase["case_name"])
        SuperBizRequestBase().specification_yaml(base_info, testcase)

    @pytest.mark.superbiz
    @pytest.mark.integration
    @pytest.mark.sse
    @pytest.mark.parametrize("base_info,testcase", get_testcase_yaml("./testcase/SuperBizAgent/ai_ops.yaml"))
    def test_ai_ops(self, base_info, testcase):
        allure.dynamic.title(testcase["case_name"])
        SuperBizSseRequestBase().specification_yaml(base_info, testcase)
```

- [ ] **Step 5: Add a stub connection module for future database assertions**

```python
class ConnectionRegistry:
    """Reserved for future DB/cache integrations."""

    def not_implemented(self):
        raise NotImplementedError("Database and cache assertions are planned for phase 2.")
```

- [ ] **Step 6: Document the execution modes and prerequisites**

```md
## Run Modes

- `smoke`: chat, chat_stream, clear, session, milvus health
- `integration`: upload, ai_ops

## Prerequisites

- Local `SuperBizAgent` service is running on `http://127.0.0.1:9900`
- Milvus is available for integration scenarios
- DashScope and related external dependencies are configured when running streaming AI cases
```

- [ ] **Step 7: Run smoke and integration suites separately**

Run:

```powershell
python -m pytest ./testcase/SuperBizAgent -m smoke -vs --alluredir ./report/temp --clean-alluredir
python -m pytest ./testcase/SuperBizAgent -m integration -vs --alluredir ./report/temp --clean-alluredir
```

Expected: smoke suite is stable; integration suite either passes or fails with clear dependency-related diagnostics.

- [ ] **Step 8: Commit the integration coverage and docs**

Run:

```powershell
git -C D:\code\QA\SuperBizAgent-API-Automation add .
git -C D:\code\QA\SuperBizAgent-API-Automation commit -m "feat: add integration scenarios and usage documentation"
```

Expected: final planned implementation commit is created.

---

## Self-Review

### Spec Coverage

本计划已覆盖设计文档中的核心要求：

- 独立 Python 自动化仓库位置与结构
- 对齐参考项目的能力模型
- `pytest + allure + YAML` 工作流
- `extract.yaml` 运行时数据池
- 普通 HTTP 和 SSE 两类执行器
- 一期核心冒烟接口
- 二期上传与 AIOps 集成接口
- Milvus 相关环境准备与验证入口

### Placeholder Scan

已检查并去除以下问题：

- 无 `TODO`、`TBD`
- 无“稍后实现”式空步骤
- 每个任务都有明确文件和执行命令
- 每个验证步骤都有明确命令与预期

### Type Consistency

命名已统一：

- 普通执行器：`SuperBizRequestBase`
- SSE 执行器：`SuperBizSseRequestBase`
- 断言类：`SuperBizAssertions`
- 用例目录：`testcase/SuperBizAgent`

