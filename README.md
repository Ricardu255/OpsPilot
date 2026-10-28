# OpsPilot

> 基于 Spring AI / Spring AI Alibaba 的 AIOps 智能运维助手：以 LLM Agent 为核心，融合 RAG 知识库、日志与指标工具调用、故障重规划与全链路追踪，面向运维场景提供对话式故障分析能力。

## 功能特性

- **对话式运维助手**：基于通义千问（qwen3-max）的多轮对话，支持 SSE 流式输出与可配置的会话历史窗口。
- **RAG 知识库**：上传 txt / md 文档，自动分块（默认 800 字符、100 重叠），经 `text-embedding-v4` 向量化后写入 Milvus，检索结果增强生成。
- **AIOps 告警分析**：Agent 可自主调用日志查询（腾讯 CLS）、指标查询（Prometheus）、内部文档检索、时间工具，对告警进行流式根因分析。
- **MCP 工具接入**：通过 Spring AI MCP Client（SSE / 异步）接入外部 MCP Server，工具能力可扩展。
- **可靠性保障**：工具调用自动重试、AI 错误分类、连续失败触发 Replan 重规划，避免单工具故障导致整体失败。
- **全链路追踪**：Trace 上下文贯穿请求全链路，工具调用、执行步骤、重规划决策持久化入库，提供查询 API 与可视化页面。
- **可观测性**：Actuator + Micrometer + Prometheus 指标暴露，自带轻量 Web 界面。

## 技术栈

| 分类 | 技术 |
| --- | --- |
| 语言 / 框架 | Java 17、Spring Boot 3.2 |
| AI 框架 | Spring AI 1.1、Spring AI Alibaba 1.1（DashScope）、Agent Framework |
| 大模型 / Embedding | qwen3-max、text-embedding-v4 |
| 向量数据库 | Milvus 2.6（IVFFLAT 索引，Docker Compose 部署，含 etcd / MinIO / Attu） |
| 工具协议 | MCP Client（WebFlux SSE） |
| 持久化 | Spring Data JPA、H2（默认）/ MySQL、Flyway 迁移 |
| 监控 | Micrometer、Prometheus、Actuator |
| 前端 | 原生 HTML / CSS / JavaScript（聊天页 + Trace 可视化页） |

## 项目结构

```
OpsPilot
├── src/main/java/org/example
│   ├── agent/tool        # Agent 工具：日志、指标、内部文档、时间
│   ├── aiops             # 重规划决策与评估
│   ├── client            # Milvus 客户端工厂与集合管理
│   ├── config            # DashScope、Milvus、文档分块、Web 等配置
│   ├── controller        # 聊天、文件上传、Trace、Milvus 检查接口
│   ├── reliability       # 错误分类、重试执行器
│   ├── service           # 聊天、RAG、文档分块、向量嵌入/索引/检索
│   ├── tool/pipeline     # 工具执行管道
│   └── trace             # Trace 上下文、拦截器、持久化与查询服务
├── src/main/resources
│   ├── db/migration      # Flyway 建表脚本
│   └── static            # 前端页面（index / trace）
├── vector-database.yml   # Milvus + etcd + MinIO + Attu 的 Docker Compose
├── Makefile              # 一键初始化 / 启动 / 上传文档
└── pom.xml
```

## 快速开始

### 环境要求

- JDK 17+、Maven 3.6+
- Docker / Docker Compose（用于启动 Milvus）
- 阿里云 DashScope API Key

### 1. 启动向量数据库

```bash
docker compose -f vector-database.yml up -d
```

启动后：Milvus 监听 `localhost:19530`，Attu 可视化界面 `http://localhost:8000`，MinIO 控制台 `http://localhost:9001`。

### 2. 配置 API Key

```powershell
# PowerShell
$env:DASHSCOPE_API_KEY = "your-dashscope-api-key"
```

```bash
# Linux / macOS
export DASHSCOPE_API_KEY=your-dashscope-api-key
```

### 3. 启动服务

```bash
mvn spring-boot:run
```

服务端口为 **9900**：

- 聊天界面：http://localhost:9900
- Trace 可视化：http://localhost:9900/trace.html
- Prometheus 指标：http://localhost:9900/actuator/prometheus

### 4. 构建知识库

在聊天界面上传文档，或调用接口：

```bash
curl -X POST http://localhost:9900/api/upload -F "file=@your-doc.md"
```

> Linux / macOS 下也可使用 `make init` 一键完成：启动 Milvus → 启动服务 → 批量上传文档。

## API 一览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/chat` | 同步对话 |
| POST | `/api/chat_stream` | SSE 流式对话 |
| POST | `/api/ai_ops` | SSE 流式 AIOps 告警分析 |
| POST | `/api/chat/clear` | 清空会话历史 |
| GET | `/api/chat/session/{sessionId}` | 查询会话历史 |
| POST | `/api/upload` | 上传文档（txt / md）并向量化 |
| GET | `/api/trace` | Trace 列表（支持分页筛选） |
| GET | `/api/trace/{traceId}` | Trace 详情（步骤 / 工具调用 / 重规划） |
| GET | `/api/trace/session/{sessionId}` | 按会话聚合查询 |
| GET | `/api/trace/stats` | Trace 统计信息 |
| GET | `/milvus/health` | Milvus 连接健康检查 |

## 关键配置

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `server.port` | 9900 | 服务端口 |
| `agent.replan.failure-threshold` | 2 | 同一工具连续失败几次后触发重规划 |
| `agent.tool.retry.max-attempts` | 1 | 单次工具调用重试次数（不含首次） |
| `agent.chat.max-window-size` | 6 | 会话历史保留轮数 |
| `document.chunk.max-size` / `overlap` | 800 / 100 | 文档分块大小与重叠 |
| `rag.top-k` | 3 | RAG 检索条数 |
| `milvus.search.nprobe` / `index.nlist` | 10 / 128 | IVFFLAT 检索与索引参数 |

数据库默认使用 H2 文件库（`./data/`），切换 MySQL 只需设置 `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD` 并将驱动改为 `com.mysql.cj.jdbc.Driver`，表结构由 Flyway 自动迁移。
