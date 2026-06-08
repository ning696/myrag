# myrag

myrag 是一个基于 Spring Boot 3 + Vue 3 的 RAG 知识库对话系统。项目支持用户登录、文档上传与切块预览、知识库向量化入库、混合检索问答、SSE 流式输出、LangChain4j 工具调用，以及面向多轮任务的 Skill 管理能力。

## 功能概览

- 用户认证：JWT 登录鉴权，支持 USER / ADMIN 角色。
- 文档知识库：支持 PDF、TXT、Markdown 上传，上传后先生成 chunk 预览，再由用户确认入库。
- RAG 问答：基于向量检索 + MySQL BM25 的混合检索，RRF 融合后构造 prompt，并通过 SSE 流式返回答案。
- 数据隔离：文档、会话、消息、检索结果均按当前登录用户隔离。
- 工具调用：内置 `current_time` 和 Tavily `web_search`，管理员可在后台启停并维护非敏感参数。
- Skill 能力：提供邮件、天气等多轮任务型能力，管理员可启停 Skill。
- 生产部署：`deploy/` 提供 Docker Compose、Nginx、MySQL 初始化 SQL 和运维脚本。

## 技术栈

### 后端

- Java 17
- Spring Boot 3.4.1
- Spring Security + JWT
- MyBatis-Plus 3.5.9 + MySQL 8.0
- Redis：登录尝试、chunk 预览、入库进度等临时状态
- MinIO：上传文档对象存储
- Milvus 2.3+：向量数据库
- LangChain4j 1.0.0-beta3
- DashScope `text-embedding-v2`：1536 维中文向量模型
- DeepSeek / OpenAI 兼容接口：聊天模型
- Jieba Analysis：关键词提取

### 前端

- Vue 3.5
- TypeScript 6
- Vite 8
- Element Plus
- Pinia
- Vue Router
- Axios
- `@microsoft/fetch-event-source`：SSE 流式聊天

## 目录结构

```text
myrag/
├── iflyzcragback/       # Spring Boot 后端
│   ├── sql/             # 初始化与迁移 SQL
│   └── src/
├── iflyzcragvue/        # Vue 3 前端
│   └── src/
├── deploy/              # Docker Compose、Nginx、部署与运维脚本
├── 文档/                 # 需求、设计、RAG 工程化说明等项目文档
├── AGENTS.md            # Codex 工作约束
└── README.md
```

## 环境要求

- JDK 17+
- Maven 3.8+
- Node.js 20+，npm 10+
- MySQL 8.0+
- Redis
- MinIO
- Milvus 2.3+
- 可访问的 LLM 与 Embedding 服务 API

## 后端配置

后端主配置文件位于：

```text
iflyzcragback/src/main/resources/application.yml
```

配置会导入后端工作目录下的 `.env`：

```yaml
spring:
  config:
    import: optional:file:./.env[.properties]
```

常用环境变量如下：

```properties
MYSQL_HOST=localhost
MYSQL_PORT=3306
MYSQL_DB=myrag
MYSQL_USERNAME=root
MYSQL_PASSWORD=

REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

MILVUS_HOST=localhost
MILVUS_PORT=19530
MILVUS_USERNAME=
MILVUS_PASSWORD=

MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=your-access-key
MINIO_SECRET_KEY=your-secret-key
MINIO_BUCKET=myrag

JWT_SECRET=replace-with-a-long-random-secret

LLM_BASE_URL=https://api.deepseek.com/v1
LLM_MODEL=deepseek-chat
DEEPSEEK_API_KEY=your-deepseek-api-key
DASHSCOPE_API_KEY=your-dashscope-api-key

TAVILY_API_KEY=your-tavily-api-key
WEATHER_API_KEY=your-weather-api-key
```

敏感信息只放在环境变量或 `.env` 中，不要提交到 Git。

## 初始化数据库

首次启动前创建 MySQL 表结构和默认数据：

```powershell
cd iflyzcragback
mysql -u root -p < sql/init.sql
```

初始化脚本会创建 `myrag` 数据库、用户表、文档表、chunk 表、会话消息表、Skill 状态表、Tool 配置表等，并写入默认管理员账号。

默认管理员：

```text
用户名：admin
密码：admin123
```

生产环境首次登录后必须修改默认密码。

## 本地启动

### 1. 启动后端

```powershell
cd iflyzcragback
mvn spring-boot:run
```

后端默认端口：

```text
http://localhost:8080
```

### 2. 启动前端

```powershell
cd iflyzcragvue
npm install
npm run dev
```

前端默认端口：

```text
http://localhost:5173
```

后端 CORS 默认允许 `http://localhost:5173`。

## 常用命令

### 后端

```powershell
cd iflyzcragback

# 编译与打包
mvn clean package

# 运行测试
mvn test

# 运行单个测试类
mvn -Dtest=DocumentServiceTest test
```

### 前端

```powershell
cd iflyzcragvue

# 安装依赖
npm install

# 开发启动
npm run dev

# 生产构建
npm run build

# 预览构建产物
npm run preview
```

## 核心接口

所有受保护接口需要携带 JWT：

```text
Authorization: Bearer <token>
```

### 认证

- `POST /api/auth/login`：登录
- `GET /api/auth/me`：获取当前用户信息

### 文档

- `POST /api/documents/upload`：上传文档并生成切块预览
- `POST /api/documents/{id}/rechunk`：按新参数重新切块
- `POST /api/documents/{id}/confirm-ingest`：确认入库并异步向量化
- `GET /api/documents/{id}/ingest-progress`：查询入库进度
- `GET /api/documents`：分页查询当前用户文档
- `GET /api/documents/{id}/chunks`：查看文档 chunk
- `GET /api/documents/{id}/download`：下载原文档
- `DELETE /api/documents/{id}`：删除文档

### 聊天

- `POST /api/chat/sessions`：创建会话
- `GET /api/chat/sessions`：查询会话列表
- `PATCH /api/chat/sessions/{sessionId}`：重命名会话
- `DELETE /api/chat/sessions/{sessionId}`：删除会话
- `GET /api/chat/sessions/{sessionId}/messages`：查询会话消息
- `POST /api/chat/messages/stream`：SSE 流式问答

### 管理员

- `GET /api/tools`：工具列表
- `PUT /api/tools/{name}/toggle`：启停工具
- `PUT /api/tools/{name}/params`：更新工具非敏感参数
- `GET /api/skills`：Skill 列表
- `PUT /api/skills/{name}/toggle`：启停 Skill

## RAG 处理流程

```text
文档上传
  -> MinIO 存储原文件
  -> PDF/TXT/Markdown 解析
  -> 生成 chunk 预览并缓存到 Redis
  -> 用户确认入库
  -> DashScope embedding
  -> MySQL 保存 chunk 与元数据
  -> Milvus 保存向量

用户提问
  -> QueryRouter 判断回答模式
  -> QueryRewriter 可选改写查询
  -> VectorRetriever 向量检索
  -> MySQL BM25 关键词检索
  -> HybridRetriever 做 RRF 融合
  -> PromptBuilder 构造带来源的提示词
  -> StreamingChatLanguageModel 流式生成答案
```

RAG 相关实现需要遵守 `文档/RAG工程化准确率提升指南.md` 中的工程规范，尤其是：

- 检索必须按 `userId` 隔离。
- 向量检索需要设置相似度阈值。
- 回答应带来源引用。
- RAG 关键路径需要记录 query、topScore、hits、latency 等日志。
- 变更检索或生成策略时，应补充评估或测试。

## 部署

部署资料位于 `deploy/`：

```text
deploy/
├── docker-compose.yml
├── docker-compose.app-only.yml
├── .env.example
├── .env.app-only.example
├── mysql/init/01-init.sql
├── nginx/conf.d/myifyrag.conf
└── scripts/
```

完整部署说明见：

```text
deploy/README.md
deploy/ENV.md
deploy/OPS.md
```

典型流程：

```powershell
# 后端打包
cd iflyzcragback
mvn clean package

# 前端构建
cd ../iflyzcragvue
npm run build
```

然后将后端 jar 放到部署目录的 `backend/app.jar`，将前端 `dist/` 放到 `frontend/dist/`，按 `deploy/README.md` 执行部署脚本。

## 开发约定

- Controller 只做路由、参数校验和认证上下文提取，业务逻辑放在 Service。
- API 返回使用统一 `Result` 包装。
- DTO / VO 放在 `dto/`，不要把实体类直接暴露给前端。
- 新增后端接口时，同步更新 `iflyzcragvue/src/api/` 中的 TypeScript 类型和请求方法。
- API Key、JWT Secret、数据库密码等敏感信息不得写入代码、SQL 或前端。
- RAG 检索、文档入库、聊天记录必须保持用户级数据隔离。

## 参考文档

- `AGENTS.md`：仓库级开发约束
- `文档/RAG工程化准确率提升指南.md`：RAG 准确率、召回率与工程化检查清单
- `文档/RAG系统说明文档.md`：RAG 系统设计说明
- `文档/开发文档/design.md`：开发设计文档
- `deploy/README.md`：生产部署说明
