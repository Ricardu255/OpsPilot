# SuperBizAgent API Automation Design

**Goal:** 为 `SuperBizAgent` 规划一套独立的 Python 接口自动化仓库，整体能力尽量对齐参考项目 `Pytest+Shopping Mall API Automation`，并补齐当前项目特有的 `SSE`、`Milvus` 与文件上传测试场景。

**Scope:** 本设计只覆盖自动化仓库的架构、目录、职责分层、执行方式、用例组织和一期/二期范围，不涉及当前 Java 主项目代码改造。

---

## 1. Background

当前项目是一个 Spring Boot 接口服务，已确认的主要公开接口包括：

- `POST /api/chat`
- `POST /api/chat_stream`
- `POST /api/ai_ops`
- `POST /api/chat/clear`
- `GET /api/chat/session/{sessionId}`
- `POST /api/upload`
- `GET /milvus/health`

项目当前实际接入的数据库只有 `Milvus`。`Prometheus` 与 `CLS` 属于外部观测/日志服务，不作为数据库处理。项目没有发现 MySQL、Redis、MongoDB、ClickHouse 的实际接入代码。

用户要求：

- 在当前仓库旁边新建一个独立 Python 自动化仓库
- 整体能力尽量与参考项目一致
- 分阶段推进，一期先完成核心接口冒烟与框架落地，二期补复杂集成场景

---

## 2. Design Principles

本自动化仓库遵循以下原则：

1. **能力对齐参考项目**
   保留 `pytest + allure + YAML 用例驱动 + extract.yaml + run.py + config.ini` 这套使用方式，降低迁移和学习成本。

2. **结构适配当前项目**
   在保留参考项目使用习惯的前提下，增加对 `SSE`、文件上传和 Milvus 相关场景的支持。

3. **业务代码与自动化解耦**
   Python 自动化仓库独立维护，不混入 Java 主仓依赖、报告和测试运行环境。

4. **先通路后扩展**
   一期优先完成框架骨架和核心冒烟链路；二期再扩展复杂集成与通用能力。

---

## 3. Repository Location

建议新仓库位置：

- `D:\code\QA\SuperBizAgent-API-Automation`

该仓库独立承担以下职责：

- 管理接口自动化测试资产
- 连接本地或指定环境执行回归
- 生成 Allure 报告与执行结果

---

## 4. Recommended Architecture

建议目录结构如下：

```text
SuperBizAgent-API-Automation/
├─ base/
│  ├─ apiutil.py
│  ├─ superbiz_apiutil.py
│  ├─ superbiz_sse_apiutil.py
│  ├─ generateId.py
│  └─ removefile.py
├─ common/
│  ├─ assertions.py
│  ├─ superbiz_assertions.py
│  ├─ readyaml.py
│  ├─ debugtalk.py
│  ├─ sendrequest.py
│  ├─ recordlog.py
│  ├─ allure_bridge.py
│  ├─ service_manager.py
│  └─ connection.py
├─ conf/
│  ├─ config.ini
│  ├─ operationConfig.py
│  └─ setting.py
├─ data/
│  ├─ files/
│  │  └─ demo_upload.md
│  └─ sql/
├─ logs/
├─ report/
│  ├─ temp/
│  ├─ allureReport/
│  └─ results.xml
├─ testcase/
│  └─ SuperBizAgent/
│     ├─ README.md
│     ├─ health.yaml
│     ├─ chat.yaml
│     ├─ chat_stream.yaml
│     ├─ clear_chat.yaml
│     ├─ session.yaml
│     ├─ upload.yaml
│     ├─ ai_ops.yaml
│     ├─ milvus_health.yaml
│     ├─ conftest.py
│     └─ test_superbiz_api.py
├─ conftest.py
├─ extract.yaml
├─ pytest.ini
├─ requirements.txt
├─ run.py
└─ README.md
```

---

## 5. Module Responsibilities

### 5.1 `base`

负责将 YAML 中的接口定义转换为实际请求执行。

- `apiutil.py`
  提供通用请求执行基类，保留参考项目的核心处理逻辑：
  - 读取 `baseInfo`
  - 解析 `testCase`
  - 执行参数替换
  - 提取响应数据
  - 调用断言中心

- `superbiz_apiutil.py`
  负责普通 HTTP/JSON 接口执行，覆盖：
  - `chat`
  - `clear`
  - `session`
  - `upload`
  - `milvus health`

- `superbiz_sse_apiutil.py`
  负责流式接口执行，覆盖：
  - `chat_stream`
  - `ai_ops`

### 5.2 `common`

负责公共能力抽象。

- `sendrequest.py`
  统一封装 HTTP 请求发送，保留参考项目能力：
  - `GET/POST/PUT/DELETE`
  - `params/data/json/files`
  - `timeout`
  - `cookies`
  - Allure 请求参数附件

- `readyaml.py`
  读取 YAML 用例，写入和清空 `extract.yaml`。

- `debugtalk.py`
  处理 `${...}` 动态表达式，提供：
  - 时间戳
  - 随机字符串
  - UUID
  - 提取变量读取
  - 自定义会话 ID 生成

- `assertions.py` / `superbiz_assertions.py`
  提供断言中心，支持：
  - `status_code`
  - `json_eq`
  - `json_contains`
  - `text_contains`
  - `text_not_contains`
  - `text_regex`
  - `min_length`
  - `ne`

- `allure_bridge.py`
  生成环境信息和附件，保持和参考项目一致的 Allure 集成体验。

- `service_manager.py`
  负责服务探活及可选的自动拉起逻辑。

- `connection.py`
  保留为二期扩展能力入口，用于后续数据库断言或缓存校验，不作为一期必做项。

### 5.3 `conf`

负责环境配置和运行配置，继续沿用参考项目的 `config.ini + operationConfig.py + setting.py` 模式。

### 5.4 `testcase/SuperBizAgent`

负责用例资产管理。

一期先按单项目目录存放，后续接口增多时再按业务域拆分子目录，例如：

- `chat/`
- `aiops/`
- `file/`
- `milvus/`

### 5.5 `data`

存放测试数据与上传样本文件。

- `files/` 用于上传接口样本
- `sql/` 作为预留目录，后续如需要数据库断言可启用

---

## 6. YAML Case Format

继续沿用参考项目的 `baseInfo + testCase` 结构。

普通 HTTP 用例格式：

```yaml
- baseInfo:
    api_name: Chat
    url: /api/chat
    method: post
    header:
      Content-Type: application/json
      Accept: application/json
  testCase:
    - case_name: chat returns success
      json:
        Id: session-001
        Question: 你好
      validation:
        - status_code: 200
        - json_contains:
            code: 200
            message: success
```

SSE 用例格式建议在 `baseInfo` 中新增：

```yaml
- baseInfo:
    api_name: Chat Stream
    url: /api/chat_stream
    method: post
    response_type: sse
    header:
      Content-Type: application/json
      Accept: text/event-stream
  testCase:
    - case_name: stream returns content and done
      json:
        Id: session-stream-001
        Question: 请回复一句自动化测试用短句
      validation:
        - status_code: 200
        - text_contains: content
        - text_contains: done
        - text_not_contains: error
```

支持字段包括：

- `params`
- `data`
- `json`
- `files`
- `validation`
- `extract`
- `extract_list`
- `cookies`

---

## 7. Runtime Data Strategy

保留参考项目的 `extract.yaml` 机制作为运行时数据池。

用途：

- 保存前置接口提取的数据
- 支持会话串联场景
- 支持上传后结果复用

典型场景：

- `chat` 创建会话后，后续 `session` 查询复用会话 ID
- `upload` 结果中的文件名或路径供后续查询复用
- `chat_stream` 过程中生成的会话 ID 被 `clear` 或 `session` 用例复用

---

## 8. Assertions Strategy

一期断言重点不放在外部依赖深校验，而放在接口契约稳定性上。

普通 HTTP 场景断言：

- HTTP 状态码
- 响应顶层 `code/message/data`
- 关键字段包含关系
- 文本长度
- 错误关键字排除

SSE 场景断言：

- 请求状态码正常
- 返回流中存在 `content`
- 返回流最终出现 `done`
- 不出现 `error`

Milvus 相关场景断言：

- `milvus/health` 接口返回 200
- 返回体包含健康信息或 collection 信息

数据库/缓存断言策略：

- 规划为二期预留能力
- 不纳入一期必做实现

---

## 9. Execution Model

执行方式保持与参考项目一致：

- `pytest.ini`
  管默认用例目录、marker、Allure 输出路径

- `conftest.py`
  负责：
  - 初始化 `extract.yaml`
  - 写入 Allure 环境信息
  - 执行汇总输出

- `run.py`
  作为统一运行入口，支持：
  - 按 marker 执行
  - 指定用例目录
  - 生成 Allure 报告
  - 可选自动打开 HTML 报告

---

## 10. Environment Configuration

`conf/config.ini` 建议至少包含以下 section：

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

说明：

- `enabled=auto` 时，可在本地环境允许的情况下自动探测并等待服务就绪
- 若后续需要，也可以通过 `start_command` 自动拉起 Java 项目

---

## 11. Test Scope

### 11.1 Phase 1

一期目标：框架落地 + 核心接口冒烟。

优先覆盖：

- `POST /api/chat`
- `POST /api/chat_stream`
- `POST /api/chat/clear`
- `GET /api/chat/session/{sessionId}`
- `GET /milvus/health`

一期完成标准：

- 可通过 `run.py` 一键执行
- 可输出 Allure 报告
- 可运行普通 HTTP 和 SSE 两种接口类型
- 可通过 `extract.yaml` 传递会话数据

### 11.2 Phase 2

二期补充复杂集成场景：

- `POST /api/upload`
- `POST /api/ai_ops`
- 更完整的 Milvus 相关验证
- 预留的 DB/缓存/外部服务断言能力

---

## 12. Recommended Approach

在三种候选方案中，采用：

- `YAML + Python 能力扩展`

不采用纯 YAML 的原因：

- `SSE`、文件上传、复杂会话串联在纯 YAML 模式下会越来越重

不采用纯 Python 的原因：

- 与参考项目差异过大，不利于保持团队使用习惯一致

采用该方案的原因：

- 能力尽量与参考项目一致
- 同时适配 `SuperBizAgent` 当前的接口特征
- 后续扩展空间更好

---

## 13. Risks And Constraints

1. `chat`、`chat_stream`、`ai_ops` 依赖外部 AI 服务，测试结果可能受模型输出波动影响。
2. `upload` 和向量化链路依赖 Milvus 可用，环境准备成本高于普通 HTTP 接口。
3. `ai_ops` 可能依赖 `Prometheus`、`CLS` 或 mock 配置，一期不适合作为基础冒烟必测项。
4. 如果把参考项目的所有历史兼容能力一次性搬入，会导致仓库过重、维护成本偏高。

应对策略：

- 一期重点做稳定冒烟链路
- 将复杂集成场景收敛到二期
- 对外部依赖场景优先做“可运行、可观测、可定位问题”的框架支持

---

## 14. Final Recommendation

建议按以下路线落地：

1. 在 `D:\code\QA\SuperBizAgent-API-Automation` 新建独立 Python 仓库
2. 目录结构整体对齐参考项目
3. 保留 `YAML + pytest + allure + extract.yaml + run.py + config.ini` 的工作流
4. 一期新增 `SSE` 执行器以支持 `chat_stream` 和 `ai_ops`
5. 一期先覆盖核心冒烟接口，二期再补复杂集成能力

该方案既能保持与参考项目高度一致的使用体验，也能覆盖 `SuperBizAgent` 的实际测试需求。
