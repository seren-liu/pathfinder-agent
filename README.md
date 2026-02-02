# Pathfinder Agent

<div align="center">

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.6-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.x-4FC08D.svg)](https://vuejs.org/)
[![LangChain4j](https://img.shields.io/badge/LangChain4j-1.10.0-blue.svg)](https://github.com/langchain4j/langchain4j)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**AI 旅行规划平台**

采用 ReAct 决策循环与 LangGraph4j 状态机编排，实现智能对话、目的地推荐与行程生成

[特性](#-核心特性) • [架构](#-系统架构) • [快速开始](#-快速启动) • [性能](#-性能指标) • [监控](#-监控系统)

</div>

---

## 📋 目录

- [核心特性](#-核心特性)
- [技术栈](#-技术栈)
- [系统架构](#-系统架构)
- [性能指标](#-性能指标)
- [快速启动](#-快速启动)
- [配置说明](#-配置说明)
- [监控系统](#-监控系统)
- [项目结构](#-项目结构)
- [贡献指南](#-贡献指南)

## ✨ 核心特性

### 🤖 智能 Agent 架构
- **ReAct 决策循环**：UnifiedReActAgent 自主推理与行动，动态选择工具执行
- **工具编排**：管理对话、推荐、行程生成三大工具，支持工具链式调用
- **上下文管理**：Redis 持久化对话历史，支持多轮对话与上下文理解

### 🔄 LangGraph 状态机工作流
- **推荐流程**：5 节点状态机（意图分析 → 偏好提取 → RAG 检索 → LLM 推荐 → 结果格式化）
- **行程流程**：6 节点状态机（需求分析 → 行程生成 → 质量反思 → 优化迭代 → 地理编码 → 持久化）
- **状态追踪**：所有状态可追踪、可恢复、可回溯，支持断点续传

### 🧠 RAG 增强检索
- **向量检索**：Chroma 存储真实景点数据，语义搜索相关信息
- **减少幻觉**：基于真实数据生成推荐，避免 AI 编造不存在的景点
- **知识库管理**：支持动态导入旅游指南，自动向量化存储

### ⚡ 高性能缓存
- **多层缓存**：Redis 缓存行程、推荐结果、地理编码数据
- **性能提升**：缓存命中后响应时间降低 **88.7%**，速度提升 **8.85 倍**
- **智能失效**：基于 TTL 和事件驱动的缓存失效策略

### 📊 企业级监控
- **全链路追踪**：Micrometer + Prometheus 收集 Agent、LLM、RAG、缓存等所有指标
- **可视化监控**：Grafana 实时仪表板，监控系统健康状态
- **智能告警**：Alertmanager 支持邮件、Slack、钉钉等多渠道告警

### 🌐 异步任务处理
- **进度追踪**：Redis 实时追踪行程生成进度（10% → 100%）
- **后台地理编码**：异步批量处理地理坐标查询，不阻塞主流程
- **优雅降级**：主 AI 服务失败自动切换备用服务

## 🛠 技术栈

### 后端技术
| 技术 | 版本 | 用途 |
|------|------|------|
| **Spring Boot** | 3.5.6 | 应用框架 |
| **Java** | 17 | 编程语言 |
| **LangChain4j** | 1.10.0 | AI 集成框架 |
| **LangGraph4j** | 1.8.0-beta3 | 状态机工作流 |
| **MyBatis-Plus** | 3.5.9 | ORM 框架 |
| **PostgreSQL** | 14+ | 关系数据库 |
| **Redis** | 6+ | 缓存 & 会话存储 |
| **Chroma** | Latest | 向量数据库 |
| **Micrometer** | Latest | 指标收集 |
| **Prometheus** | Latest | 时序数据库 |
| **Grafana** | Latest | 监控可视化 |

### 前端技术
| 技术 | 版本 | 用途 |
|------|------|------|
| **Vue** | 3.x | 前端框架 |
| **Vite** | 5.x | 构建工具 |
| **Element Plus** | Latest | UI 组件库 |
| **Pinia** | Latest | 状态管理 |
| **Vue Router** | 4.x | 路由管理 |
| **Leaflet/Mapbox** | Latest | 地图展示 |

### AI 模型
| 模型 | 用途 | 特点 |
|------|------|------|
| **Gemini 2.5 Flash Lite** | 主要 LLM | 快速响应（~7s），成本低 |
| **GPT-4o Mini** | 备用 LLM | 高质量输出，自动降级 |
| **text-embedding-3-small** | 向量嵌入 | 1536 维，语义检索 |

## 系统架构

```
┌─────────────────────────────────────────────────────────┐
│  前端层：Vue 3 (PlanIntent / Destinations / Progress)  │
└────────────────────────┬────────────────────────────────┘
                         │ REST API
┌────────────────────────┴────────────────────────────────┐
│  Agent 决策层：UnifiedReActAgent + ToolRegistry        │
│  ├─ ConversationTool (对话 + 意图分析)                 │
│  ├─ RecommendationTool (LangGraph 推荐流)              │
│  └─ ItineraryGenerationTool (LangGraph 行程流)         │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────┴────────────────────────────────┐
│  编排层：LangGraph4j 状态机工作流                      │
│  ├─ RecommendationGraph (5 节点)                       │
│  └─ TravelPlanningGraph (6 节点 + 反思循环)            │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────┴────────────────────────────────┐
│  服务层：Conversation / Intent / Recommendation /      │
│          ItineraryGeneration / KnowledgeBase           │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────┴────────────────────────────────┐
│  数据层：PostgreSQL / Redis / Chroma / Gemini API      │
└─────────────────────────────────────────────────────────┘
```

## 项目结构

```
backend/    # Spring Boot 服务 + LangGraph Agent 核心
├── src/main/java/com/travel/agent/
│   ├── ai/              # Agent、工具、状态机节点
│   ├── service/         # 业务服务层
│   ├── controller/      # REST API 控制器
│   ├── entity/          # 数据库实体
│   ├── monitoring/      # 监控指标服务
│   └── evaluation/      # 推荐评估服务
└── src/main/resources/
    ├── application.yml  # 应用配置（需自行创建）
    └── mapper/          # MyBatis XML 映射

frontend/   # Vue 3 客户端
├── src/
│   ├── components/      # Vue 组件
│   ├── api/            # API 调用封装
│   └── assets/         # 静态资源
└── .env                # 前端配置（需自行创建）

data/       # RAG 知识库（旅游指南 Markdown）
├── knowledge/
│   ├── paris_guide.md
│   ├── tokyo_guide.md
│   └── ...

infra/      # Docker Compose + 数据库初始化脚本
├── docker/
│   ├── docker-compose.yml              # 基础设施
│   ├── docker-compose-monitoring.yml   # 监控服务
│   └── prometheus/                     # Prometheus 配置
└── setup_postgres.sql                  # 数据库初始化脚本
```

## 环境要求

- Java 17+、Maven 3.6+
- Node.js 18+
- PostgreSQL 14+、Redis 6+
- Chroma 向量服务（推荐 Docker 部署）

## 快速启动

**1. 启动基础设施**
```bash
cd infra/docker
docker-compose up -d  # 启动 PostgreSQL、Redis、Chroma
```

**2. 启动后端服务**
```bash
cd backend
mvn clean install
mvn spring-boot:run   # 服务运行于 http://localhost:8080
```

**3. 启动前端应用**
```bash
cd frontend
npm install
npm run dev           # 应用运行于 http://localhost:5173
```

**4. 初始化数据**
```bash
# 初始化数据库表结构
psql -U postgres -d travel_agent -f infra/setup_postgres.sql

# 导入旅行知识库到 Chroma 向量库
curl -X POST http://localhost:8080/api/knowledge/import
```

**5. 启动监控服务（可选）**
```bash
cd infra/docker
docker compose -f docker-compose-monitoring.yml up -d

# 访问监控平台
# Prometheus: http://localhost:9090
# Grafana: http://localhost:3000 (账号: admin / admin)
# Alertmanager: http://localhost:9093
```

## 前端页面展示
<img width="1763" height="923" alt="Pathfinder" src="https://github.com/user-attachments/assets/5c7f63fb-1830-4724-93cb-6b0f64c55095" />

## 📊 性能指标

### 缓存性能（实测数据）

| 指标 | 无缓存 | 有缓存 | 提升 |
|------|--------|--------|------|
| **平均响应时间** | 8.85ms | 1.0ms | **↓ 88.7%** |
| **速度倍数** | 1x | **8.85x** | - |
| **总耗时（20次）** | 177ms | 20ms | **↓ 88.7%** |

### 系统容量

- **并发支持**：1000+ QPS（基于缓存优化）
- **响应时间**：P95 < 100ms（缓存命中）
- **缓存命中率**：90-95%（推算）
- **内存占用**：~60 MB（30 天运行）
- **磁盘占用**：~26 MB（Prometheus 30 天数据）

### LLM 性能

| 模型 | 平均响应时间 | Token 消耗 | 成本 |
|------|-------------|-----------|------|
| **Gemini 2.5 Flash Lite** | ~7s | ~2000 tokens | 低 |
| **GPT-4o Mini** | ~15s | ~2500 tokens | 中 |

## 📈 监控系统

### 监控架构

```
应用程序 (Spring Boot)
    ↓ 暴露 /actuator/prometheus
Prometheus (时序数据库)
    ↓ 数据源
Grafana (可视化)
    ↓ 告警
Alertmanager (通知)
```

### 核心监控指标

#### Agent 指标
- `agent.execution.total` - Agent 执行总次数
- `agent.execution.success` - 执行成功次数
- `agent.execution.duration` - 执行耗时（P50/P95/P99）
- `agent.execution.concurrent` - 当前并发数

#### LLM 指标
- `llm.call.total` - LLM 调用总次数
- `llm.call.duration` - 调用耗时
- `llm.tokens.prompt` - Prompt Token 消耗
- `llm.tokens.completion` - Completion Token 消耗

#### 缓存指标（新增）
- `cache.hit` - 缓存命中次数
- `cache.miss` - 缓存未命中次数
- `cache.operation.duration` - 缓存操作耗时

#### RAG 指标
- `rag.search.total` - RAG 检索次数
- `rag.search.duration` - 检索耗时
- `rag.similarity.score` - 相似度分数分布

#### 系统指标
- `jvm.memory.used` - JVM 内存使用
- `system.cpu.usage` - CPU 使用率
- `http.server.requests` - HTTP 请求统计

### 访问监控平台

- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000 (admin / admin)
- **Alertmanager**: http://localhost:9093

### 查询示例

```promql
# 缓存命中率
sum(rate(cache_hit[5m])) / (sum(rate(cache_hit[5m])) + sum(rate(cache_miss[5m])))

# Agent 执行成功率
sum(rate(agent_execution_success[5m])) / sum(rate(agent_execution_total[5m]))

# LLM 平均响应时间
rate(llm_call_duration_seconds_sum[5m]) / rate(llm_call_duration_seconds_count[5m])
```

## 配置说明

**重要：首次使用前必须配置 API Keys**

1. **复制配置模板**
   ```bash
   cd backend/src/main/resources
   cp application.yml.example application.yml
   ```

2. **编辑 `application.yml` 并填入你的 API Keys**
   ```yaml
   # AI 模型配置（必需）
   gemini.api-key: YOUR_GEMINI_API_KEY      # 从 https://aistudio.google.com/app/apikey 获取
   openai.api-key: YOUR_OPENAI_API_KEY      # 从 https://platform.openai.com/api-keys 获取
   
   # 地图服务（必需）
   mapbox.access-token: YOUR_MAPBOX_TOKEN   # 从 https://account.mapbox.com/ 获取
   geoapify.api-key: YOUR_GEOAPIFY_KEY      # 从 https://www.geoapify.com/ 获取
   
   # AWS S3（可选，用于照片存储）
   s3.access-key-id: YOUR_AWS_ACCESS_KEY_ID
   s3.secret-access-key: YOUR_AWS_SECRET_KEY
   s3.bucket: your-s3-bucket-name
   
   # 数据库与缓存（使用默认值即可）
   spring.datasource.url: jdbc:postgresql://localhost:5432/travel_agent
   spring.datasource.username: postgres
   spring.datasource.password: postgres
   spring.data.redis.host: localhost
   
   # 向量数据库
   langchain4j.chroma.base-url: http://localhost:8000
   ```

3. **前端配置**
   ```bash
   cd frontend
   cp .env.example .env
   ```
   
   编辑 `frontend/.env` 并填入你的 API Keys：
   ```env
   VITE_API_BASE_URL=http://localhost:8080
   VITE_GEOAPIFY_API_KEY=your_geoapify_api_key    # 从 https://www.geoapify.com/ 获取
   VITE_MAPBOX_TOKEN=your_mapbox_token            # 从 https://account.mapbox.com/ 获取（可选）
   ```

**注意事项**

- 🔑 所有 API Keys 都需要自行申请并填入配置文件

## 🤝 贡献指南

欢迎贡献代码、报告问题或提出建议！

### 代码规范

- **Java**: 遵循 Google Java Style Guide
- **Vue**: 遵循 Vue 3 官方风格指南
- **提交信息**: 使用语义化提交（Conventional Commits）

## 📄 许可证

MIT License - 详见 [LICENSE](LICENSE) 文件

## 🙏 致谢

- [LangChain4j](https://github.com/langchain4j/langchain4j) - Java AI 集成框架
- [LangGraph4j](https://github.com/langchain4j/langgraph4j) - 状态机工作流
- [Spring Boot](https://spring.io/projects/spring-boot) - 应用框架
- [Vue.js](https://vuejs.org/) - 前端框架

---

<div align="center">

**⭐ 如果这个项目对你有帮助，请给个 Star！**

</div>
