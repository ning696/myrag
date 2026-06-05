# RAG 对话机器人系统设计文档

**版本：** v1.0  
**编写日期：** 2026-06-02  
**项目名称：** myrag - 基于 RAG 的智能对话机器人  
**技术栈：** Spring Boot 3 + Vue 3 + TypeScript

---

## 1. 系统架构

### 1.1 总体架构

本系统采用**前后端分离架构**，前端使用 Vue 3 构建单页应用，后端使用 Spring Boot 3 提供 RESTful API，向量数据库独立部署。

```
┌─────────────────────────────────────────────────────────────┐
│                         用户浏览器                            │
│                    (Vue 3 + TypeScript)                      │
└───────────────────────────┬─────────────────────────────────┘
                            │ HTTP/HTTPS
                            │ REST API
┌───────────────────────────▼─────────────────────────────────┐
│                     Nginx 反向代理                            │
│                  (静态文件 + API 路由)                         │
└───────────────────────────┬─────────────────────────────────┘
                            │
                ┌───────────┴───────────┐
                │                       │
┌───────────────▼──────────┐   ┌────────▼─────────────┐
│   前端静态文件服务        │   │   后端 API 服务       │
│   (Vue Build 产物)       │   │  (Spring Boot 3)     │
└──────────────────────────┘   └────────┬─────────────┘
                                        │
                        ┌───────────────┼───────────────┐
                        │               │               │
              ┌─────────▼────┐  ┌──────▼──────┐  ┌────▼─────────┐
              │   MySQL      │  │   Milvus    │  │  LLM API     │
              │  (元数据)     │  │ (向量数据库) │  │ (DeepSeek)   │
              └──────────────┘  └─────────────┘  └──────────────┘
```

**组件说明：**
- **前端（Vue 3）**：提供用户界面，处理用户交互，调用后端 API
- **Nginx**：反向代理，静态文件服务，负载均衡（生产环境）
- **后端（Spring Boot 3）**：业务逻辑处理，RAG 流程编排，工具/Skill 管理，JWT 认证
- **MySQL**：存储用户信息、文档元数据、会话记录、工具配置、Skill 状态
- **Milvus**：向量数据库，存储文档向量，提供相似度检索
- **LLM API**：大语言模型服务（DeepSeek / 智谱 GLM / 通义千问 / OpenAI）

### 1.2 分层架构

后端采用**严格分层架构**，职责清晰，便于维护和测试。

```
┌─────────────────────────────────────────────────────────┐
│                    表现层 (Presentation Layer)           │
│  - Vue 3 组件（views/、components/）                     │
│  - 路由管理（Vue Router）                                 │
│  - 状态管理（Pinia）                                      │
└────────────────────────┬────────────────────────────────┘
                         │ HTTP REST API
┌────────────────────────▼────────────────────────────────┐
│                    应用层 (Application Layer)            │
│  - REST Controllers（controller/）                       │
│  - 参数校验（@Valid）                                     │
│  - 统一返回格式（Result<T>）                              │
└────────────────────────┬────────────────────────────────┘
                         │ 调用业务逻辑
┌────────────────────────▼────────────────────────────────┐
│                    业务层 (Business Layer)               │
│  - Services（service/）                                  │
│  - 业务逻辑处理                                           │
│  - 事务管理（@Transactional）                            │
└────────────────────────┬────────────────────────────────┘
                         │ 调用数据访问
┌────────────────────────▼────────────────────────────────┐
│                    数据层 (Data Layer)                   │
│  - Mappers/Repositories（mapper/）                       │
│  - 数据访问对象（DTO/Entity）                             │
│  - 数据库操作（MyBatis-Plus）                             │
└────────────────────────┬────────────────────────────────┘
                         │ 持久化
                    ┌────▼─────┐
                    │ Database │
                    └──────────┘

┌─────────────────────────────────────────────────────────┐
│              基础设施层 (Infrastructure Layer)            │
│  - RAG 核心（rag/）：文档处理、向量化、检索               │
│  - 工具调用系统（tool/）：工具接口、生命周期管理            │
│  - Skill 系统（skill/）：状态机、会话管理                 │
│  - 配置（config/）：CORS、Security、统一异常处理          │
└─────────────────────────────────────────────────────────┘
```

**分层原则：**
- **Controller 层**：仅做参数校验和路由，禁止写业务逻辑
- **Service 层**：核心业务逻辑，可调用多个 Mapper/Repository
- **Mapper 层**：仅做数据访问，不写业务逻辑
- **DTO 分离**：Entity（数据库实体）不直接暴露给前端，使用 DTO/VO 包装

### 1.3 模块划分

```
iflyzcragback/src/main/java/com/zc/iflyzcragback/
├── controller/          # REST 控制器
│   ├── AuthController.java           # 用户认证 API（注册/登录）
│   ├── UserController.java           # 用户信息管理 API
│   ├── DocumentController.java       # 文档管理 API
│   ├── ChatController.java           # 对话 API
│   ├── ToolController.java         # 工具管理 API
│   └── SkillController.java          # Skill 管理 API
│
├── service/             # 业务逻辑层
│   ├── AuthService.java              # 认证服务
│   ├── UserService.java              # 用户管理服务
│   ├── DocumentService.java          # 文档处理服务
│   ├── ChatService.java              # 对话服务（RAG 编排）
│   ├── ToolService.java            # 工具管理服务
│   └── SkillService.java             # Skill 管理服务
│
├── mapper/              # 数据访问层
│   ├── UserMapper.java
│   ├── DocumentMapper.java
│   ├── ChatSessionMapper.java
│   ├── ChatMessageMapper.java
│   ├── ToolConfigMapper.java
│   └── SkillStateMapper.java
│
├── entity/              # 数据库实体
│   ├── User.java
│   ├── Document.java
│   ├── ChatSession.java
│   ├── ChatMessage.java
│   ├── ToolConfig.java
│   └── SkillState.java
│
├── dto/                 # 数据传输对象
│   ├── RegisterDTO.java
│   ├── LoginDTO.java
│   ├── UserInfoDTO.java
│   ├── DocumentUploadDTO.java
│   ├── ChatRequestDTO.java
│   ├── ChatResponseDTO.java
│   ├── ToolConfigDTO.java
│   └── SkillTriggerDTO.java
│
├── vo/                  # 视图对象
│   ├── UserVO.java
│   ├── DocumentVO.java
│   ├── ChatMessageVO.java
│   └── ToolVO.java
│
├── security/            # 安全模块
│   ├── JwtTokenProvider.java        # JWT Token 生成和验证
│   ├── JwtAuthenticationFilter.java # JWT 认证过滤器
│   └── UserDetailsServiceImpl.java  # Spring Security 用户服务
│
├── rag/                 # RAG 核心模块
│   ├── DocumentProcessor.java        # 文档分块处理
│   ├── EmbeddingService.java         # 向量化服务
│   ├── VectorStoreService.java       # 向量库操作（Milvus）
│   ├── RetrievalService.java         # 检索服务
│   └── LLMService.java               # LLM API 调用（OkHttp）
│
├── tool/              # 工具调用系统
│   ├── ManagedTool.java            # 工具元数据与可用性接口
│   ├── CurrentTimeTool.java        # current_time
│   ├── WebSearchTool.java          # web_search
│   ├── RealtimeAssistant.java      # LangChain4j AI Service
│   ├── RealtimeToolCallingService.java # 工具调用编排
│   └── ToolService.java            # tools_config 启停管理
│
├── skill/               # Skill 系统
│   ├── Skill.java                    # Skill 接口
│   ├── SkillContext.java             # Skill 上下文
│   ├── SkillResult.java              # Skill 返回结果
│   ├── SkillStateManager.java        # 状态管理器
│   ├── EmailSkill.java               # 邮件发送 Skill
│   └── WeatherSkill.java             # 天气查询 Skill
│
└── config/              # 配置类
    ├── WebConfig.java                # CORS 配置
    ├── SecurityConfig.java           # Spring Security 配置（JWT）
    ├── GlobalExceptionHandler.java   # 统一异常处理
    └── MyBatisPlusConfig.java        # MyBatis-Plus 配置
```

---

## 2. 技术选型

### 2.1 后端技术栈

| 组件类别 | 技术选型 | 版本 | 说明 |
|---------|---------|------|------|
| **核心框架** | Spring Boot | 3.4.1 | 后端主框架，需 Java 17+ |
| **编程语言** | Java | 17 | LTS 版本，支持现代 Java 特性 |
| **构建工具** | Maven | 3.9.x | 依赖管理和项目构建 |
| **Web 框架** | Spring Web | - | RESTful API 支持 |
| **数据库** | MySQL | 8.0 | 关系数据库，存储用户、文档元数据 |
| **ORM 框架** | MyBatis-Plus | 3.5.x | 简化 CRUD，代码生成器 |
| **安全框架** | Spring Security | - | JWT 认证和权限控制 |
| **向量数据库** | Milvus | 2.3+ | 企业级向量数据库，高性能检索 |
| **AI 编排框架** | LangChain4j | 0.36.x | 统一抽象 LLM、Embedding、VectorStore，简化 RAG 流水线 |
| **LangChain4j 集成** | langchain4j-spring-boot-starter | 0.36.x | Spring Boot 自动装配 |
| **LLM Provider** | langchain4j-open-ai | 0.36.x | DeepSeek（OpenAI 兼容协议）/ OpenAI |
| **LLM Provider（备选）** | langchain4j-zhipu-ai / langchain4j-dashscope | 0.36.x | 智谱 GLM / 通义千问 |
| **Embedding 模型** | langchain4j-community-dashscope-spring-boot-starter | 0.36.x | 阿里云 DashScope `text-embedding-v2`，云端调用，中文优化，1536 维 |
| **向量库集成** | langchain4j-milvus | 0.36.x | LangChain4j 官方 Milvus EmbeddingStore |
| **文档解析** | langchain4j-document-parser-apache-pdfbox | 0.36.x | 内置 PDFBox PDF 解析器 |
| **Markdown 解析** | CommonMark | 0.21.x | Markdown 解析（结合 `TextDocumentParser` 兜底纯文本） |
| **HTTP 客户端** | OkHttp | 4.x | 调用搜索 API、邮件 API、天气 API 等非 LLM 服务 |
| **代码简化** | Lombok | 1.18.x | 减少样板代码 |
| **热重载** | Spring Boot DevTools | - | 开发时热重载 |
| **密码加密** | BCrypt | - | Spring Security 内置 |

**框架选择说明：**

- **为什么用 LangChain4j**：需求要求支持多 LLM 切换（DeepSeek / 智谱 / 通义 / OpenAI），LangChain4j 对每家都有官方 module，切换只需替换 `ChatLanguageModel` Bean；同时把 PDF 解析、文本分块、Embedding、Milvus 写入封装成 `EmbeddingStoreIngestor` 流水线，省去大量胶水代码。
- **为什么不用 Spring AI / Spring AI Alibaba**：Spring AI Alibaba 对通义千问深度优化，但其余 Provider 仅靠 OpenAI 兼容协议适配，多 LLM 切换不如 LangChain4j 一等公民支持。
- **OkHttp 仍保留**：仅用于非 LLM 的外部 HTTP 调用（如 WebSearchTool、WeatherSkill），LLM 调用统一走 LangChain4j。
- **Tool 调用**：实时工具采用 LangChain4j `@Tool` + AI Service 自动执行模式；Skill 仍按状态机方向设计。

### 2.2 前端技术栈

| 组件类别 | 技术选型 | 版本 | 说明 |
|---------|---------|------|------|
| **核心框架** | Vue | 3.5.34 | 使用 Composition API 和 `<script setup>` |
| **编程语言** | TypeScript | 6.0.2 | 类型安全，提升开发体验 |
| **构建工具** | Vite | 8.0.12 | 快速构建，HMR 支持 |
| **UI 库** | Element Plus | 2.x | 组件丰富，文档完善 |
| **状态管理** | Pinia | 2.x | Vue 3 官方推荐状态管理 |
| **路由** | Vue Router | 4.x | 单页应用路由管理 |
| **HTTP 客户端** | Axios | 1.x | Promise 风格，拦截器支持 |
| **Markdown 渲染** | marked / markdown-it | - | 渲染 LLM 返回的 Markdown 内容 |
| **代码高亮** | highlight.js | - | 代码块语法高亮 |

---

## 3. 数据库设计

### 3.1 ER 图

```
┌─────────────────┐
│     users       │  用户表
│─────────────────│
│ id (PK)         │
│ username        │
│ email           │
│ password        │
│ role            │
│ avatar          │
│ created_at      │
└─────────────────┘
         │
         │ 1:N (一个用户有多个文档)
         │
         ▼
┌─────────────────┐
│   documents     │  文档元信息
│─────────────────│
│ id (PK)         │
│ user_id (FK)    │
│ filename        │
│ file_type       │
│ file_size       │
│ upload_time     │
│ chunk_count     │
│ status          │
│ vector_store_id │
└─────────────────┘
         │
         │ 1:N (一个文档有多个 chunks，逻辑关联)
         │
         ▼
  [向量库 Milvus]
    (存储 chunks 和向量)

┌──────────────────┐
│  chat_sessions   │  会话记录
│──────────────────│
│ session_id (PK)  │
│ user_id (FK)     │◄─────┐
│ created_at       │      │ 1:N (一个用户有多个会话)
│ updated_at       │      │
│ status           │      │
└──────────────────┘      │
         │                │
         │ 1:N            │
         │                │
         ▼                │
┌──────────────────┐      │
│  chat_messages   │      │
│──────────────────│      │
│ id (PK)          │      │
│ session_id (FK)  │      │
│ role             │      │
│ content          │      │
│ context          │      │
│ tool_used      │      │
│ skill_used       │      │
│ created_at       │      │
└──────────────────┘      │
         │                │
         │                │
         ▼                │
┌──────────────────┐      │
│   skill_states   │      │
│──────────────────│      │
│ id (PK)          │      │
│ session_id (FK)  │──────┘
│ skill_name       │
│ current_step     │
│ state_data       │
│ created_at       │
│ updated_at       │
└──────────────────┘

┌──────────────────┐
│  tools_config  │  工具配置
│──────────────────│
│ id (PK)          │
│ tool_name      │
│ enabled          │
│ application.yml 工具参数      │
│ description      │
│ created_at       │
│ updated_at       │
└──────────────────┘
```

### 3.2 表结构设计

#### 3.2.1 users 表（用户表）

存储用户基本信息和认证凭证。

```sql
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户 ID',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名（唯一）',
    email VARCHAR(100) NOT NULL UNIQUE COMMENT '邮箱（唯一）',
    password VARCHAR(100) NOT NULL COMMENT '密码（BCrypt 加密）',
    nickname VARCHAR(50) DEFAULT NULL COMMENT '昵称',
    avatar VARCHAR(255) DEFAULT NULL COMMENT '头像 URL',
    role VARCHAR(20) DEFAULT 'USER' COMMENT '角色：USER/ADMIN',
    status VARCHAR(20) DEFAULT 'active' COMMENT '状态：active/locked/disabled',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_username (username),
    INDEX idx_email (email),
    INDEX idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
```

**字段说明：**
- `password`：使用 BCrypt 加密存储，永不明文
- `role`：`USER`（普通用户）、`ADMIN`（管理员）
- `status`：`active`（正常）、`locked`（锁定，登录失败 5 次）、`disabled`（禁用）
- `avatar`：存储头像文件路径或 URL

#### 3.2.2 documents 表（文档元信息）

存储用户上传的文档元数据，每个文档关联到具体用户。

```sql
CREATE TABLE documents (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '文档 ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID（外键）',
    filename VARCHAR(255) NOT NULL COMMENT '文件名',
    file_type VARCHAR(20) NOT NULL COMMENT '文件类型：PDF/TXT/MD',
    file_size BIGINT NOT NULL COMMENT '文件大小（字节）',
    upload_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    chunk_count INT DEFAULT 0 COMMENT '分块数量',
    status VARCHAR(20) DEFAULT 'processing' COMMENT '处理状态：processing/completed/failed',
    vector_store_id VARCHAR(100) COMMENT '向量库中的文档 ID（Milvus Collection ID）',
    error_message TEXT COMMENT '处理失败时的错误信息',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_upload_time (upload_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档元信息表';
```

**字段说明：**
- `user_id`：关联用户，实现文档隔离（用户只能访问自己的文档）
- `vector_store_id`：关联向量库中的文档 ID，用于后续检索和删除
- `status`：处理状态流转：`processing` → `completed` 或 `failed`

#### 3.2.3 chat_sessions 表（会话记录）

存储对话会话信息，每个会话关联到具体用户。

```sql
CREATE TABLE chat_sessions (
    session_id VARCHAR(64) PRIMARY KEY COMMENT '会话 ID（UUID）',
    user_id BIGINT NOT NULL COMMENT '用户 ID（外键）',
    title VARCHAR(255) DEFAULT '新对话' COMMENT '会话标题（自动生成或用户设置）',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    status VARCHAR(20) DEFAULT 'active' COMMENT '会话状态：active/completed/archived',
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话记录表';
```

**字段说明：**
- `session_id`：使用 UUID 保证唯一性
- `user_id`：关联用户，实现会话隔离（用户只能访问自己的会话）
- `title`：会话标题，可根据第一条消息自动生成

#### 3.2.4 chat_messages 表（消息记录）

存储会话中的每条消息（用户消息和系统回答）。

```sql
CREATE TABLE chat_messages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '消息 ID',
    session_id VARCHAR(64) NOT NULL COMMENT '会话 ID',
    role VARCHAR(20) NOT NULL COMMENT '角色：user/assistant/system',
    content TEXT NOT NULL COMMENT '消息内容',
    context TEXT COMMENT '检索到的上下文（JSON 数组）',
    source_documents TEXT COMMENT '来源文档列表（JSON 数组）',
    tool_used VARCHAR(255) COMMENT '使用的工具名称',
    skill_used VARCHAR(255) COMMENT '使用的 Skill 名称',
    tokens_used INT DEFAULT 0 COMMENT '消耗的 token 数量',
    response_time INT DEFAULT 0 COMMENT '响应时间（毫秒）',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    FOREIGN KEY (session_id) REFERENCES chat_sessions(session_id) ON DELETE CASCADE,
    INDEX idx_session_id (session_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息记录表';
```

**字段说明：**
- `role`：`user`（用户消息）、`assistant`（系统回答）、`system`（系统提示）
- `context`：存储检索到的 chunks，用于调试和溯源
- `source_documents`：存储来源文档名称，前端渲染引用来源
- `tokens_used` 和 `response_time`：用于性能监控和成本统计

#### 3.2.5 tools_config 表（工具配置）

存储工具的启停状态。工具运行参数不进入数据库，统一由 `application.yml` 与环境变量维护。

```sql
CREATE TABLE tools_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '工具配置 ID',
    tool_name VARCHAR(100) NOT NULL UNIQUE COMMENT '工具名称（唯一）',
    display_name VARCHAR(100) NOT NULL COMMENT '展示名称',
    description VARCHAR(500) COMMENT '工具描述',
    enabled BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
    INDEX idx_tool_name (tool_name),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工具配置表';
```

默认工具为 `current_time` 和 `web_search`。前端只允许管理员启停工具，不提供 JSON 参数、Hook 或优先级编辑。

#### 3.2.6 skill_states 表（Skill 状态）

存储 Skill 的会话状态，支持多轮对话。

```sql
CREATE TABLE skill_states (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '状态 ID',
    session_id VARCHAR(64) NOT NULL COMMENT '会话 ID',
    skill_name VARCHAR(100) NOT NULL COMMENT 'Skill 名称',
    current_step VARCHAR(100) COMMENT '当前步骤',
    state_data TEXT COMMENT 'JSON 格式的状态数据',
    is_completed BOOLEAN DEFAULT FALSE COMMENT '是否已完成',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    FOREIGN KEY (session_id) REFERENCES chat_sessions(session_id) ON DELETE CASCADE,
    INDEX idx_session_id (session_id),
    INDEX idx_is_completed (is_completed)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Skill 状态表';
```

**字段说明：**
- `state_data`：存储 Skill 收集的数据，示例（EmailSkill）：
  ```json
  {
    "step": "ASK_SUBJECT",
    "recipient": "user@example.com",
    "subject": null,
    "content": null
  }
  ```
- `is_completed`：标记 Skill 是否完成，完成后可返回 RAG 模式

---

## 4. API 接口设计

### 4.1 统一返回格式

所有 API 接口统一使用 `Result<T>` 包装返回数据。

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    private Integer code;      // 200 成功，401 未认证，403 无权限，500 失败
    private String message;    // 消息
    private T data;           // 数据
    private Long timestamp;   // 时间戳

    public static <T> Result<T> success(T data) {
        return Result.<T>builder()
                .code(200)
                .message("success")
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static <T> Result<T> error(Integer code, String message) {
        return Result.<T>builder()
                .code(code)
                .message(message)
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    public static <T> Result<T> unauthorized(String message) {
        return error(401, message);
    }
    
    public static <T> Result<T> forbidden(String message) {
        return error(403, message);
    }
}
```

### 4.2 核心接口列表

#### 4.2.1 用户认证接口

| 接口 | 方法 | 路径 | 说明 | 是否需要认证 |
|------|------|------|------|-------------|
| 用户注册 | POST | `/api/auth/register` | 注册新用户 | ❌ |
| 用户登录 | POST | `/api/auth/login` | 用户登录获取 Token | ❌ |
| 获取当前用户信息 | GET | `/api/auth/me` | 获取当前登录用户信息 | ✅ |
| 退出登录 | POST | `/api/auth/logout` | 退出登录（清除 Token） | ✅ |

#### 4.2.2 用户管理接口

| 接口 | 方法 | 路径 | 说明 | 是否需要认证 |
|------|------|------|------|-------------|
| 更新用户信息 | PUT | `/api/users/profile` | 更新昵称、头像 | ✅ |
| 修改密码 | PUT | `/api/users/password` | 修改密码 | ✅ |

#### 4.2.3 文档管理接口

| 接口 | 方法 | 路径 | 说明 | 是否需要认证 |
|------|------|------|------|-------------|
| 上传文档 | POST | `/api/documents/upload` | 上传文档并自动处理 | ✅ |
| 获取文档列表 | GET | `/api/documents` | 分页获取当前用户的文档列表 | ✅ |
| 获取文档详情 | GET | `/api/documents/{id}` | 获取单个文档详情 | ✅ |
| 删除文档 | DELETE | `/api/documents/{id}` | 删除文档及向量数据 | ✅ |

#### 4.2.4 对话接口

| 接口 | 方法 | 路径 | 说明 | 是否需要认证 |
|------|------|------|------|-------------|
| 发送消息 | POST | `/api/chat` | 发送消息并获取回答（核心接口） | ✅ |
| 获取会话列表 | GET | `/api/chat/sessions` | 获取当前用户的会话列表 | ✅ |
| 获取会话历史 | GET | `/api/chat/sessions/{sessionId}` | 获取会话的消息历史 | ✅ |
| 删除会话 | DELETE | `/api/chat/sessions/{sessionId}` | 删除会话及所有消息 | ✅ |

#### 4.2.5 工具管理接口（仅管理员）

| 接口 | 方法 | 路径 | 说明 | 是否需要认证 |
|------|------|------|------|-------------|
| 获取工具列表 | GET | `/api/tools` | 获取所有工具配置 | ✅（ADMIN） |
| 启用/禁用工具 | PUT | `/api/tools/{name}/toggle` | 切换工具启用状态 | ✅（ADMIN） |
| 更新工具配置 | PUT | `/api/tools/{name}/config` | 更新工具运行参数 | ✅（ADMIN） |

#### 4.2.6 Skill 管理接口（仅管理员）

| 接口 | 方法 | 路径 | 说明 | 是否需要认证 |
|------|------|------|------|-------------|
| 获取可用 Skill 列表 | GET | `/api/skills` | 获取所有已注册的 Skill | ✅（ADMIN） |
| 手动触发 Skill | POST | `/api/skills/{name}/trigger` | 手动触发指定 Skill | ✅（ADMIN） |

### 4.3 核心接口详细设计

#### 4.3.1 POST /api/auth/register

**功能：** 用户注册。

**请求：**
```json
{
  "username": "testuser",
  "email": "test@example.com",
  "password": "Test@1234"
}
```

**响应：**
```json
{
  "code": 200,
  "message": "注册成功",
  "data": {
    "userId": 1,
    "username": "testuser",
    "email": "test@example.com",
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 7200000
  },
  "timestamp": 1717318200000
}
```

**前端 TypeScript 类型：**
```typescript
interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

interface AuthResponse {
  userId: number;
  username: string;
  email: string;
  token: string;
  expiresIn: number;
}
```

#### 4.3.2 POST /api/auth/login

**功能：** 用户登录。

**请求：**
```json
{
  "usernameOrEmail": "testuser",
  "password": "Test@1234",
  "rememberMe": false
}
```

**响应：**
```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "userId": 1,
    "username": "testuser",
    "email": "test@example.com",
    "role": "USER",
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 7200000
  },
  "timestamp": 1717318200000
}
```

**前端 TypeScript 类型：**
```typescript
interface LoginRequest {
  usernameOrEmail: string;
  password: string;
  rememberMe?: boolean;
}

interface LoginResponse {
  userId: number;
  username: string;
  email: string;
  role: 'USER' | 'ADMIN';
  token: string;
  expiresIn: number;
}
```

#### 4.3.3 POST /api/documents/upload

**功能：** 上传文档并自动触发分块和向量化流程。（需要认证）

**请求：**
```http
POST /api/documents/upload
Authorization: Bearer {token}
Content-Type: multipart/form-data

file: (binary data)
```

**响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "userId": 1,
    "filename": "example.pdf",
    "fileType": "PDF",
    "fileSize": 1024000,
    "status": "processing",
    "uploadTime": "2026-06-02T14:30:00"
  },
  "timestamp": 1717318200000
}
```

**前端 TypeScript 类型：**
```typescript
interface DocumentUploadResponse {
  id: number;
  userId: number;
  filename: string;
  fileType: string;
  fileSize: number;
  status: 'processing' | 'completed' | 'failed';
  uploadTime: string;
}
```

#### 4.3.4 POST /api/chat

**功能：** 发送消息并获取 RAG 回答（核心接口，需要认证）。

**请求：**
```json
{
  "sessionId": "uuid-xxx",  // 可选，首次对话时为空
  "message": "什么是 RAG？",
  "useTools": true        // 可选，默认 true
}
```

**请求头：**
```http
Authorization: Bearer {token}
```

**响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "sessionId": "uuid-xxx",
    "answer": "RAG（检索增强生成）是一种结合向量检索和大语言模型的技术...",
    "context": [
      "检索到的上下文片段 1",
      "检索到的上下文片段 2"
    ],
    "sourceDocuments": ["document1.pdf", "document2.txt"],
    "toolUsed": null,
    "skillTriggered": null,
    "tokensUsed": 1500,
    "responseTime": 2500
  },
  "timestamp": 1717318200000
}
```

**前端 TypeScript 类型：**
```typescript
interface ChatRequest {
  sessionId?: string;
  message: string;
  useTools?: boolean;
}

interface ChatResponse {
  sessionId: string;
  answer: string;
  context?: string[];
  sourceDocuments?: string[];
  toolUsed?: string;
  skillTriggered?: string;
  tokensUsed?: number;
  responseTime?: number;
}
```

#### 4.3.5 GET /api/documents

**功能：** 分页获取当前用户的文档列表。（需要认证）

**请求：**
```http
GET /api/documents?page=1&size=20&status=completed
Authorization: Bearer {token}
```

**响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 100,
    "pages": 5,
    "current": 1,
    "records": [
      {
        "id": 1,
        "userId": 1,
        "filename": "example.pdf",
        "fileType": "PDF",
        "fileSize": 1024000,
        "uploadTime": "2026-06-02T14:30:00",
        "chunkCount": 25,
        "status": "completed"
      }
    ]
  },
  "timestamp": 1717318200000
}
```

#### 4.3.6 PUT /api/tools/{name}/toggle

**功能：** 启用或禁用工具。（需要管理员权限）

**请求：**
```http
PUT /api/tools/CurrentTimeTool/toggle
Authorization: Bearer {token}
Content-Type: application/json

{
  "enabled": true
}
```

**响应：**
```json
{
  "code": 200,
  "message": "工具状态已更新",
  "data": {
    "toolName": "CurrentTimeTool",
    "enabled": true
  },
  "timestamp": 1717318200000
}
```

---

## 5. RAG 核心设计

> 本章基于 **LangChain4j 0.36.x** 实现。核心抽象：
> - `Document` / `TextSegment`：文档与分块
> - `DocumentParser`：PDF / TXT / MD 解析
> - `DocumentSplitter`：文本分块
> - `EmbeddingModel`：向量化（DashScope text-embedding-v2，云端调用）
> - `EmbeddingStore<TextSegment>`：向量存储（`MilvusEmbeddingStore`）
> - `EmbeddingStoreIngestor`:  解析 → 分块 → 向量化 → 入库 一体化流水线
> - `ChatLanguageModel`：LLM 调用，多 Provider 一致接口
> - `ContentRetriever` / `Filter`：检索与 metadata 过滤（支撑 userId 数据隔离）

### 5.1 文档处理流程

```
┌──────────────┐
│ 用户上传文档  │
└──────┬───────┘
       │
       ▼
┌──────────────────────────────┐
│ 身份验证                     │
│ - 检查 JWT Token，提取 userId│
└──────┬───────────────────────┘
       │
       ▼
┌──────────────────────────────┐
│ DocumentParser 加载          │
│ - PDF: ApachePdfBoxDocument  │
│   Parser                     │
│ - TXT/MD: TextDocumentParser │
│ - 注入 metadata: userId,     │
│   documentId, filename       │
└──────┬───────────────────────┘
       │
       ▼
┌──────────────────────────────┐
│ DocumentSplitter 分块        │
│ - DocumentSplitters          │
│   .recursive(800, 80)        │
│ - 段落 → 句子 → 字符递归切分 │
└──────┬───────────────────────┘
       │
       ▼
┌──────────────────────────────┐
│ EmbeddingModel 向量化        │
│ - DashScope text-embedding-v2│
│ - 维度 1536                  │
└──────┬───────────────────────┘
       │
       ▼
┌──────────────────────────────┐
│ MilvusEmbeddingStore 写入    │
│ - 自动持久化 vector + segment│
│   text + metadata            │
└──────┬───────────────────────┘
       │
       ▼
┌──────────────────────────────┐
│ 更新 documents 表状态        │
│ status = completed           │
│ chunk_count = N              │
└──────────────────────────────┘
```

**LangChain4j 实现（DocumentService.processDocument）：**

```java
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentMapper documentMapper;
    private final EmbeddingModel embeddingModel;            // DashScope text-embedding-v2 Bean
    private final EmbeddingStore<TextSegment> embeddingStore; // MilvusEmbeddingStore Bean
    private final DocumentParser pdfParser;                 // ApachePdfBoxDocumentParser
    private final DocumentParser textParser;                // TextDocumentParser

    public void processDocument(Long documentId, Long userId) {
        Document doc = documentMapper.selectById(documentId);
        if (!doc.getUserId().equals(userId)) {
            throw new ForbiddenException("无权访问该文档");
        }

        try (InputStream in = Files.newInputStream(Path.of(doc.getStoragePath()))) {
            // 1. 解析为 LangChain4j Document
            DocumentParser parser = "PDF".equals(doc.getFileType()) ? pdfParser : textParser;
            dev.langchain4j.data.document.Document document = parser.parse(in);

            // 2. 注入 metadata：用户隔离 + 来源溯源
            document.metadata()
                    .put("userId", userId.toString())
                    .put("documentId", documentId.toString())
                    .put("filename", doc.getFilename());

            // 3. 用 Ingestor 一次性完成 split + embed + store
            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                    .documentSplitter(DocumentSplitters.recursive(800, 80))
                    .embeddingModel(embeddingModel)
                    .embeddingStore(embeddingStore)
                    .build();
            IngestionResult result = ingestor.ingest(document);

            // 4. 更新文档状态
            doc.setStatus("completed");
            doc.setChunkCount(result.tokenUsage() == null ? 0 : (int) result.tokenUsage().inputTokenCount());
            documentMapper.updateById(doc);
        } catch (Exception e) {
            doc.setStatus("failed");
            doc.setErrorMessage(e.getMessage());
            documentMapper.updateById(doc);
            throw new BusinessException("文档处理失败", e);
        }
    }
}
```

**说明：**
- `EmbeddingStoreIngestor` 把"分块 → embedding → 写入向量库"封装成单次调用，替代原方案中三段手写代码。
- `metadata` 是后续检索时按 `userId` 过滤的依据，必须在解析后立即注入。
- 分块器 `DocumentSplitters.recursive(maxSegmentSize, maxOverlap)` 内部按段落 → 句子 → 字符递归切分，已覆盖原方案"段落优先 + 句子边界"诉求。

### 5.2 检索流程

```
┌──────────────┐
│ 用户提问     │
└──────┬───────┘
       │
       ▼
┌──────────────────────────────┐
│ JWT 认证：提取 userId        │
└──────┬───────────────────────┘
       │
       ▼
┌──────────────────────────────┐
│ 工具调用 @Tool       │
│ - CurrentTimeTool / Calculator    │
│   命中即直接返回，跳过 RAG   │
└──────┬───────────────────────┘
       │
       ▼
┌──────────────────────────────┐
│ ContentRetriever.retrieve    │
│ - EmbeddingStoreContent      │
│   Retriever                  │
│ - 内部完成：query embedding  │
│   + Milvus 检索              │
│ - filter: userId == ?        │
│ - maxResults = 3             │
└──────┬───────────────────────┘
       │
       ▼
┌──────────────────────────────┐
│ PromptTemplate 渲染          │
│ - 拼接 context + question    │
└──────┬───────────────────────┘
       │
       ▼
┌──────────────────────────────┐
│ ChatLanguageModel.generate   │
│ - DeepSeek (OpenAI 协议)     │
│ - 超时 10s，重试 3 次        │
│ - 多 Provider 切换仅换 Bean  │
└──────┬───────────────────────┘
       │
       ▼
┌──────────────────────────────┐
│ 工具调用 @Tool        │
│ - WebSearchTool 兜底补充   │
└──────┬───────────────────────┘
       │
       ▼
┌──────────────────────────────┐
│ 返回答案 + 引用来源          │
└──────────────────────────────┘
```

**LangChain4j 实现（RagOrchestrator TOOL_CALLING 分支）：**

```java
if (route == QueryRoute.TOOL_CALLING) {
    ToolCallingAnswer toolAnswer = realtimeToolCallingService.answer(query);
    if (!toolAnswer.isAvailable()) {
        streamRealtimeUnavailable(...);
        return;
    }

    streamDirectAndSave(
            session,
            query,
            toolAnswer.answer(),
            AnswerMode.TOOL_CALLING,
            toolAnswer.citations(),
            String.join(",", toolAnswer.usedTools())
    );
    return;
}
```

**关键点：**
- `RealtimeToolCallingService` 按 `tools_config.enabled` 动态传入可用工具对象。
- `RealtimeAssistant` 的系统提示约束模型：实时问题先取时间，公开实时信息再搜索，禁止编造实时数值。
- `web_search` 的结果会转换为 `sourceType=web` 的 `CitationVO`，随 SSE `citations` 事件返回。
- LLM 切换：把 `ChatLanguageModel` Bean 由 `OpenAiChatModel`（DeepSeek 走兼容协议）换成其他 LangChain4j Provider 即可。

### 5.3 分块策略

**实现方式：** 直接复用 LangChain4j 内置 `DocumentSplitters.recursive(maxSegmentSize, maxOverlap)`，不再手写分块算法。

**参数：**
- `maxSegmentSize = 800` 字符（覆盖需求 500–1000 区间）
- `maxOverlap = 80` 字符（覆盖需求 50–100 区间）

**递归切分顺序：**
1. **段落优先**：按 `\n\n` 切段，未超长则保留整段
2. **句子兜底**：段落仍超长时按 `。！？.!?` 切句
3. **词 / 字符兜底**：句子仍超长时按词或字符切到不超长为止
4. **重叠保留**：相邻分块按 `maxOverlap` 字符重叠，保持上下文连贯

**示例：**

```java
DocumentSplitter splitter = DocumentSplitters.recursive(800, 80);
List<TextSegment> segments = splitter.split(document);
// 每个 TextSegment 自动继承 document 的 metadata（userId、documentId、filename）
```

**对中文的适配：** `DocumentByParagraphSplitter` / `DocumentBySentenceSplitter` 默认按双换行和西文标点切分。若中文文档以单换行或仅中文标点分句，可在切分前对原文做一次预处理（替换为标准段落分隔符），或自定义 `DocumentSplitter` 实现传入 Ingestor。

### 5.4 Prompt 模板

**LangChain4j 实现：** 使用 `PromptTemplate` 渲染，避免字符串拼接。

```java
private static final PromptTemplate PROMPT_TEMPLATE = PromptTemplate.from("""
        你是一个基于知识库的智能助手。请根据以下上下文回答用户问题。

        上下文：
        {{context}}

        用户问题：{{question}}

        回答要求：
        1. 基于上下文回答，不要编造信息
        2. 如果上下文中没有相关信息，明确告知用户"根据当前知识库，我无法找到相关信息"
        3. 引用具体的文档来源（在回答末尾列出来源文档）
        4. 回答简洁明了，使用 Markdown 格式
        """);

private Prompt buildPrompt(String question, List<Content> contents) {
    String context = contents.stream()
            .map(c -> {
                TextSegment seg = c.textSegment();
                String source = seg.metadata().getString("filename");
                return "---\n" + seg.text() + "\n\n来源：" + source;
            })
            .collect(Collectors.joining("\n"));

    return PROMPT_TEMPLATE.apply(Map.of(
            "context", context,
            "question", question
    ));
}
```

### 5.5 关键 Bean 配置

集中在 `config/RagConfig.java`，将 LangChain4j 各组件装配为 Spring Bean。

```java
@Configuration
public class RagConfig {

    /**
     * 嵌入模型：DashScope text-embedding-v2（云端，1536 维）。
     * 由 langchain4j-community-dashscope-spring-boot-starter 通过
     * langchain4j.community.dashscope.embedding-model.* 自动装配，
     * 此处显式声明仅为说明 Bean 类型；实际可省略，直接 @Autowired EmbeddingModel。
     */
    @Bean
    public EmbeddingModel embeddingModel(@Value("${langchain4j.community.dashscope.embedding-model.api-key}") String apiKey,
                                         @Value("${langchain4j.community.dashscope.embedding-model.model-name:text-embedding-v2}") String modelName) {
        return QwenEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .build();
    }

    /** Milvus 向量库；collection 字段需包含 metadata 列以支持 userId 过滤 */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(MilvusProperties props) {
        return MilvusEmbeddingStore.builder()
                .host(props.getHost())
                .port(props.getPort())
                .username(props.getUsername())
                .password(props.getPassword())
                .collectionName("myrag_chunks")
                .dimension(1536)
                .indexType(IndexType.HNSW)
                .metricType(MetricType.COSINE)
                .build();
    }

    /** LLM：DeepSeek 走 OpenAI 兼容协议；切换 Provider 仅需替换本 Bean */
    @Bean
    public ChatLanguageModel chatLanguageModel(LlmProperties props) {
        return OpenAiChatModel.builder()
                .baseUrl(props.getBaseUrl())          // https://api.deepseek.com/v1
                .apiKey(props.getApiKey())            // ${LLM_API_KEY}
                .modelName(props.getModelName())      // deepseek-chat
                .timeout(Duration.ofSeconds(10))
                .maxRetries(3)
                .build();
    }

    @Bean
    public DocumentParser pdfDocumentParser() {
        return new ApachePdfBoxDocumentParser();
    }

    @Bean
    public DocumentParser textDocumentParser() {
        return new TextDocumentParser();
    }
}
```

**多 Provider 切换示例：**

| Provider | Bean 替换 | 依赖 |
|---------|----------|------|
| DeepSeek | `OpenAiChatModel`（baseUrl 指向 deepseek） | `langchain4j-open-ai` |
| OpenAI | `OpenAiChatModel`（默认 baseUrl） | `langchain4j-open-ai` |
| 智谱 GLM | `ZhipuAiChatModel` | `langchain4j-zhipu-ai` |
| 通义千问 | `QwenChatModel` | `langchain4j-dashscope` |

通过 `@ConditionalOnProperty(name = "llm.api.provider", havingValue = "...")` 实现配置驱动的 Bean 选择。

---

## 6. 工具调用系统设计

### 6.1 设计目标

业务层不再存在自研工具钩子或工具管理器。实时能力统一通过 LangChain4j `@Tool` 暴露给模型，由 `RealtimeAssistant` AI Service 自动判断和执行工具调用。

核心约束：
- 工具启停存储在 `tools_config`，仅包含启停与展示信息。
- 工具运行参数放在 `application.yml` 与环境变量中，前端不编辑 JSON 参数。
- 当前内置工具只有 `current_time` 和 `web_search`。
- 涉及“今天/当前/最新/实时”的公开信息查询时，系统提示要求先调用 `current_time`，再按需调用 `web_search`。
- 工具失败或禁用时不得编造实时数值。

### 6.2 后端结构

```text
service/rag/tool/
├── ManagedTool.java              # 工具元数据与可用性接口
├── CurrentTimeTool.java          # @Tool current_time
├── WebSearchTool.java            # @Tool web_search
├── WebSearchSource.java          # 搜索来源结构
├── RealtimeAssistant.java        # LangChain4j AI Service 接口
├── RealtimeToolCallingService.java # 构建 AI Service、汇总答案/引用/usedTools
└── ToolService.java              # 管理 tools_config 启停
```

### 6.3 管理 API

| 接口 | 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|------|
| 获取工具列表 | GET | `/api/tools` | 返回已注册工具与启停状态 | ADMIN |
| 启用/禁用工具 | PUT | `/api/tools/{name}/toggle` | 更新 `tools_config.enabled` | ADMIN |

`ToolVO` 固定字段：`toolName`、`displayName`、`description`、`enabled`、`available`。

### 6.4 调用流程

```text
用户问题
  → QueryRouter 识别 TOOL_CALLING
  → ToolService 按 tools_config.enabled 选择可用工具 Bean
  → RealtimeToolCallingService 构建 RealtimeAssistant
  → 模型自动调用 current_time / web_search
  → 后端从 toolExecutions 提取 usedTools 与网页引用
  → SSE 返回 token、citations、done
  → chat_messages.tool_used 持久化本次工具名称
```

### 6.5 内置工具

#### current_time

返回当前日期、时间、星期和时区，时区读取 `rag.tools.time.default-zone`。

#### web_search

调用 Tavily 查询公开网页信息。API Key 只读取环境变量，搜索条数、深度、分数阈值、超时等读取 `rag.tools.web-search` 与 `search.*` 配置。搜索结果会转换为 `sourceType=web` 的引用返回前端。

---

## 7. Skill 系统设计

### 7.1 Skill 接口定义

```java
public interface Skill {
    /**
     * Skill 名称（唯一标识）
     */
    String getName();
    
    /**
     * Skill 描述
     */
    String getDescription();
    
    /**
     * 是否可以处理当前输入
     * @param input 用户输入
     * @param context Skill 上下文
     * @return true 表示可以处理
     */
    boolean canHandle(String input, SkillContext context);
    
    /**
     * 执行 Skill
     * @param input 用户输入
     * @param context Skill 上下文
     * @return Skill 执行结果
     */
    SkillResult execute(String input, SkillContext context);
    
    /**
     * 是否已完成
     * @param context Skill 上下文
     * @return true 表示已完成
     */
    boolean isCompleted(SkillContext context);
}
```

**SkillContext 数据结构：**
```java
@Data
@Builder
public class SkillContext {
    private String sessionId;        // 会话 ID
    private String skillName;        // Skill 名称
    private String currentStep;      // 当前步骤
    private Map<String, Object> stateData;  // 状态数据
    private long startTime;          // 开始时间
}
```

**SkillResult 数据结构：**
```java
@Data
@Builder
public class SkillResult {
    private String response;         // 回复给用户的消息
    private String nextStep;         // 下一步骤
    private boolean completed;       // 是否完成
    private Map<String, Object> updatedState;  // 更新的状态数据
}
```

### 7.2 状态管理

**SkillStateManager 实现：**
```java
@Service
public class SkillStateManager {
    
    @Autowired
    private SkillStateMapper skillStateMapper;
    
    /**
     * 获取 Skill 上下文
     */
    public SkillContext getContext(String sessionId) {
        SkillState state = skillStateMapper.selectBySessionId(sessionId);
        if (state == null) {
            return null;
        }
        
        Map<String, Object> stateData = JSON.parseObject(
            state.getStateData(), 
            new TypeReference<Map<String, Object>>(){}
        );
        
        return SkillContext.builder()
                .sessionId(sessionId)
                .skillName(state.getSkillName())
                .currentStep(state.getCurrentStep())
                .stateData(stateData)
                .startTime(state.getCreatedAt().getTime())
                .build();
    }
    
    /**
     * 保存 Skill 上下文
     */
    public void saveContext(SkillContext context) {
        SkillState state = skillStateMapper.selectBySessionId(context.getSessionId());
        
        if (state == null) {
            state = new SkillState();
            state.setSessionId(context.getSessionId());
            state.setSkillName(context.getSkillName());
        }
        
        state.setCurrentStep(context.getCurrentStep());
        state.setStateData(JSON.toJSONString(context.getStateData()));
        state.setIsCompleted(false);
        
        if (state.getId() == null) {
            skillStateMapper.insert(state);
        } else {
            skillStateMapper.updateById(state);
        }
    }
    
    /**
     * 清除 Skill 状态
     */
    public void clearContext(String sessionId) {
        skillStateMapper.deleteBySessionId(sessionId);
    }
}
```

### 7.3 示例 Skill 设计

#### EmailSkill（邮件发送 Skill）

**状态机：**
```
INIT → ASK_RECIPIENT → ASK_SUBJECT → ASK_CONTENT → CONFIRM → SEND → DONE
```

**实现代码：**
```java
@Component
public class EmailSkill implements Skill {
    
    private static final String STEP_INIT = "INIT";
    private static final String STEP_ASK_RECIPIENT = "ASK_RECIPIENT";
    private static final String STEP_ASK_SUBJECT = "ASK_SUBJECT";
    private static final String STEP_ASK_CONTENT = "ASK_CONTENT";
    private static final String STEP_CONFIRM = "CONFIRM";
    private static final String STEP_SEND = "SEND";
    private static final String STEP_DONE = "DONE";
    
    @Override
    public String getName() {
        return "EmailSkill";
    }
    
    @Override
    public String getDescription() {
        return "通过多轮对话收集邮件信息并发送";
    }
    
    @Override
    public boolean canHandle(String input, SkillContext context) {
        // 检查是否为触发关键词
        return input.contains("发邮件") || input.contains("发送邮件");
    }
    
    @Override
    public SkillResult execute(String input, SkillContext context) {
        String currentStep = context.getCurrentStep();
        
        return switch (currentStep) {
            case STEP_INIT -> handleInit();
            case STEP_ASK_RECIPIENT -> handleRecipient(input, context);
            case STEP_ASK_SUBJECT -> handleSubject(input, context);
            case STEP_ASK_CONTENT -> handleContent(input, context);
            case STEP_CONFIRM -> handleConfirm(input, context);
            default -> SkillResult.builder()
                    .response("Skill 执行出错，请重试")
                    .completed(true)
                    .build();
        };
    }
    
    @Override
    public boolean isCompleted(SkillContext context) {
        return STEP_DONE.equals(context.getCurrentStep());
    }
    
    private SkillResult handleInit() {
        return SkillResult.builder()
                .response("好的，我来帮你发送邮件。请告诉我收件人的邮箱地址。")
                .nextStep(STEP_ASK_RECIPIENT)
                .completed(false)
                .build();
    }
    
    private SkillResult handleRecipient(String input, SkillContext context) {
        // 验证邮箱格式
        if (!input.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            return SkillResult.builder()
                    .response("邮箱格式不正确，请重新输入收件人的邮箱地址。")
                    .nextStep(STEP_ASK_RECIPIENT)
                    .completed(false)
                    .build();
        }
        
        Map<String, Object> state = context.getStateData();
        state.put("recipient", input);
        
        return SkillResult.builder()
                .response("收件人已设置为：" + input + "。请输入邮件主题。")
                .nextStep(STEP_ASK_SUBJECT)
                .updatedState(state)
                .completed(false)
                .build();
    }
    
    private SkillResult handleSubject(String input, SkillContext context) {
        Map<String, Object> state = context.getStateData();
        state.put("subject", input);
        
        return SkillResult.builder()
                .response("邮件主题已设置为：" + input + "。请输入邮件内容。")
                .nextStep(STEP_ASK_CONTENT)
                .updatedState(state)
                .completed(false)
                .build();
    }
    
    private SkillResult handleContent(String input, SkillContext context) {
        Map<String, Object> state = context.getStateData();
        state.put("content", input);
        
        String confirmMessage = String.format(
            "请确认邮件信息：\n\n" +
            "收件人：%s\n" +
            "主题：%s\n" +
            "内容：%s\n\n" +
            "回复"确认"发送邮件，回复"取消"放弃发送。",
            state.get("recipient"),
            state.get("subject"),
            state.get("content")
        );
        
        return SkillResult.builder()
                .response(confirmMessage)
                .nextStep(STEP_CONFIRM)
                .updatedState(state)
                .completed(false)
                .build();
    }
    
    private SkillResult handleConfirm(String input, SkillContext context) {
        if (input.contains("确认")) {
            // 发送邮件（实际实现需调用邮件服务）
            boolean success = sendEmail(context.getStateData());
            
            if (success) {
                return SkillResult.builder()
                        .response("邮件已发送成功！")
                        .nextStep(STEP_DONE)
                        .completed(true)
                        .build();
            } else {
                return SkillResult.builder()
                        .response("邮件发送失败，请稍后重试。")
                        .nextStep(STEP_DONE)
                        .completed(true)
                        .build();
            }
        } else if (input.contains("取消")) {
            return SkillResult.builder()
                    .response("已取消发送邮件。")
                    .nextStep(STEP_DONE)
                    .completed(true)
                    .build();
        } else {
            return SkillResult.builder()
                    .response("请回复"确认"或"取消"。")
                    .nextStep(STEP_CONFIRM)
                    .completed(false)
                    .build();
        }
    }
    
    private boolean sendEmail(Map<String, Object> emailData) {
        // 实际邮件发送逻辑（使用 JavaMailSender）
        // 这里仅作示例，返回 true
        return true;
    }
}
```

#### WeatherSkill（天气查询 Skill）

**状态机：**
```
INIT → ASK_CITY → ASK_DATE → QUERY_API → DONE
```

**实现代码：**
```java
@Component
public class WeatherSkill implements Skill {
    
    private static final String STEP_INIT = "INIT";
    private static final String STEP_ASK_CITY = "ASK_CITY";
    private static final String STEP_ASK_DATE = "ASK_DATE";
    private static final String STEP_DONE = "DONE";
    
    @Override
    public String getName() {
        return "WeatherSkill";
    }
    
    @Override
    public String getDescription() {
        return "通过多轮对话查询城市天气";
    }
    
    @Override
    public boolean canHandle(String input, SkillContext context) {
        return input.contains("查询天气") || input.contains("天气怎么样");
    }
    
    @Override
    public SkillResult execute(String input, SkillContext context) {
        String currentStep = context.getCurrentStep();
        
        return switch (currentStep) {
            case STEP_INIT -> handleInit();
            case STEP_ASK_CITY -> handleCity(input, context);
            case STEP_ASK_DATE -> handleDate(input, context);
            default -> SkillResult.builder()
                    .response("Skill 执行出错，请重试")
                    .completed(true)
                    .build();
        };
    }
    
    @Override
    public boolean isCompleted(SkillContext context) {
        return STEP_DONE.equals(context.getCurrentStep());
    }
    
    private SkillResult handleInit() {
        return SkillResult.builder()
                .response("请告诉我你想查询哪个城市的天气？")
                .nextStep(STEP_ASK_CITY)
                .completed(false)
                .build();
    }
    
    private SkillResult handleCity(String input, SkillContext context) {
        Map<String, Object> state = context.getStateData();
        state.put("city", input);
        
        return SkillResult.builder()
                .response("请告诉我你想查询哪一天的天气？（今天/明天/后天）")
                .nextStep(STEP_ASK_DATE)
                .updatedState(state)
                .completed(false)
                .build();
    }
    
    private SkillResult handleDate(String input, SkillContext context) {
        Map<String, Object> state = context.getStateData();
        String city = (String) state.get("city");
        
        // 解析日期
        String date = parseDateKeyword(input);
        
        // 调用天气 API
        String weatherInfo = queryWeather(city, date);
        
        return SkillResult.builder()
                .response(weatherInfo)
                .nextStep(STEP_DONE)
                .completed(true)
                .build();
    }
    
    private String parseDateKeyword(String keyword) {
        if (keyword.contains("今天")) {
            return LocalDate.now().toString();
        } else if (keyword.contains("明天")) {
            return LocalDate.now().plusDays(1).toString();
        } else if (keyword.contains("后天")) {
            return LocalDate.now().plusDays(2).toString();
        }
        return LocalDate.now().toString();
    }
    
    private String queryWeather(String city, String date) {
        // 调用天气 API（示例返回）
        // 实际使用时需调用如和风天气、心知天气等 API
        return String.format("%s %s 的天气：晴，气温 15-25℃，风力 3 级。", city, date);
    }
}
```

---

## 8. 安全设计

### 8.1 JWT 认证

**Token 结构：**
```json
{
  "sub": "user_id",
  "username": "testuser",
  "roles": ["USER"],
  "exp": 1717318200,
  "iat": 1717311000
}
```

**认证流程：**
```
用户登录 → 验证用户名密码（BCrypt） → 生成 JWT Token → 返回 Token
  ↓
用户请求 API → 携带 Token（Header: Authorization: Bearer {token}） 
  ↓
JwtAuthenticationFilter 拦截 → 验证 Token → 提取用户信息 → 放入 SecurityContext 
  ↓
Controller 执行业务逻辑 → 从 SecurityContext 获取当前用户
```

**配置参数：**
- Access Token 有效期：默认 2 小时
- 勾选"记住我"：7 天
- 密钥存储：环境变量 `JWT_SECRET`（至少 256 位）
- 算法：HS256

**Spring Security 配置示例：**
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .authorizeHttpRequests()
                .requestMatchers("/api/auth/**").permitAll()  // 登录注册接口无需认证
                .requestMatchers("/api/admin/**").hasRole("ADMIN")  // 管理员接口
                .anyRequest().authenticated()  // 其他接口需要认证
            .and()
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

### 8.2 API Key 管理

**LLM API Key 配置：**
```properties
# application.properties
llm.api.provider=deepseek
llm.api.key=${LLM_API_KEY}
llm.api.base-url=https://api.deepseek.com
```

**LLM API Key 配置：**
```properties
# application.properties
llm.api.provider=deepseek
llm.api.key=${LLM_API_KEY}
llm.api.base-url=https://api.deepseek.com
```

**Milvus 凭证：**
```properties
# Milvus 配置
milvus.host=${MILVUS_HOST:localhost}
milvus.port=${MILVUS_PORT:19530}
milvus.username=${MILVUS_USERNAME:}
milvus.password=${MILVUS_PASSWORD:}
```

**安全要求：**
1. **不提交到 Git**：在 `.gitignore` 添加 `.env` 文件
2. **加密存储**：敏感配置使用 Jasypt 加密
3. **环境变量**：生产环境使用环境变量注入

### 8.3 权限控制

**角色定义：**
- **ADMIN**：管理员，可配置工具、Skill，管理所有用户的文档
- **USER**：普通用户，可上传文档、进行对话，仅能访问自己的数据

**权限矩阵：**
| 功能 | USER | ADMIN |
|------|------|-------|
| 注册/登录 | ✅ | ✅ |
| 上传文档 | ✅ | ✅ |
| 删除文档 | ✅（仅自己的） | ✅（所有） |
| 对话 | ✅ | ✅ |
| 配置工具 | ❌ | ✅ |
| 配置 Skill | ❌ | ✅ |
| 查看所有用户 | ❌ | ✅ |

**数据隔离实现：**
```java
// Service 层示例
public List<Document> getUserDocuments(Long userId, int page, int size) {
    // 仅查询当前用户的文档
    return documentMapper.selectByUserId(userId, page, size);
}

// Milvus 检索时过滤
public List<Chunk> search(float[] queryVector, Long userId, int topK) {
    // 添加过滤条件：userId = {userId}
    String expr = "user_id == " + userId;
    return milvusClient.search(collectionName, queryVector, expr, topK);
}
```

### 8.4 输入验证

**防止 SQL 注入：**
- 使用 MyBatis-Plus 的参数化查询
- 禁止拼接 SQL 字符串

**防止 XSS 攻击：**
- 前端使用 DOMPurify 过滤用户输入
- 后端对 HTML 标签进行转义

**文件上传限制：**
- 单个文件最大 50MB
- 仅允许 PDF、TXT、MD 格式
- 禁止上传可执行文件（.exe、.sh、.bat）

---

## 9. 部署架构

### 9.1 开发环境

```
前端开发服务器（localhost:5173）
  ↓ HTTP
后端开发服务器（localhost:8080）
  ↓
  ├── MySQL（localhost:3306）
  ├── Milvus（localhost:19530）
  └── LLM API（外部服务）
```

**启动命令：**
```bash
# 启动 MySQL（Docker）
docker run -d --name mysql -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=myrag \
  mysql:8.0

# 启动 Milvus（Docker Compose，推荐）
wget https://github.com/milvus-io/milvus/releases/download/v2.3.0/milvus-standalone-docker-compose.yml -O docker-compose.yml
docker-compose up -d

# 启动后端
cd iflyzcragback && ./mvnw spring-boot:run

# 启动前端
cd iflyzcragvue && npm run dev
```

### 9.2 生产环境（推荐）

```
                        Internet
                           │
                           ▼
                    ┌──────────────┐
                    │    Nginx     │
                    │  (80/443)    │
                    └──────┬───────┘
                           │
            ┌──────────────┼──────────────┐
            │              │              │
    ┌───────▼────────┐ ┌──▼──────────┐  │
    │ Vue 静态文件   │ │  /api/*     │  │
    │ (Nginx Serve)  │ │  Reverse    │  │
    └────────────────┘ │  Proxy      │  │
                       └──┬──────────┘  │
                          │             │
                 ┌────────▼─────────┐   │
                 │  Spring Boot     │   │
                 │  (Docker)        │   │
                 │  Port 8080       │   │
                 └────────┬─────────┘   │
                          │             │
          ┌───────────────┼─────────────┼───────────┐
          │               │             │           │
    ┌─────▼────┐  ┌───────▼───────┐  ┌─▼──────┐  ┌─▼────────┐
    │  MySQL   │  │    Milvus     │  │ Redis  │  │ LLM API  │
    │(Docker)  │  │   (Docker)    │  │(可选)  │  │(外部)    │
    └──────────┘  └───────────────┘  └────────┘  └──────────┘
```

**组件说明：**
- **Nginx**：反向代理，静态文件服务，SSL 终止，负载均衡
- **Spring Boot（Docker）**：后端服务，容器化部署
- **MySQL（Docker）**：关系数据库，持久化存储
- **Milvus（Docker）**：向量数据库，向量检索
- **Redis（可选）**：缓存和 Session 存储
- **LLM API**：外部服务（DeepSeek / 智谱 / OpenAI）

### 9.3 Docker Compose 配置

```yaml
version: '3.8'

services:
  # 后端服务
  backend:
    build:
      context: ./iflyzcragback
      dockerfile: Dockerfile
    container_name: myrag-backend
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - DB_HOST=mysql
      - DB_PORT=3306
      - DB_NAME=myrag
      - DB_USER=myrag_user
      - DB_PASSWORD=${DB_PASSWORD}
      - MILVUS_HOST=milvus-standalone
      - MILVUS_PORT=19530
      - LLM_API_KEY=${LLM_API_KEY}
      - JWT_SECRET=${JWT_SECRET}
    depends_on:
      - mysql
      - milvus-standalone
    restart: unless-stopped
    networks:
      - myrag-network

  # MySQL 数据库
  mysql:
    image: mysql:8.0
    container_name: myrag-mysql
    environment:
      - MYSQL_ROOT_PASSWORD=${DB_ROOT_PASSWORD}
      - MYSQL_DATABASE=myrag
      - MYSQL_USER=myrag_user
      - MYSQL_PASSWORD=${DB_PASSWORD}
    volumes:
      - mysql-data:/var/lib/mysql
    ports:
      - "3306:3306"
    restart: unless-stopped
    networks:
      - myrag-network

  # Milvus Etcd
  etcd:
    container_name: milvus-etcd
    image: quay.io/coreos/etcd:v3.5.5
    environment:
      - ETCD_AUTO_COMPACTION_MODE=revision
      - ETCD_AUTO_COMPACTION_RETENTION=1000
      - ETCD_QUOTA_BACKEND_BYTES=4294967296
      - ETCD_SNAPSHOT_COUNT=50000
    volumes:
      - etcd-data:/etcd
    command: etcd -advertise-client-urls=http://127.0.0.1:2379 -listen-client-urls http://0.0.0.0:2379 --data-dir /etcd
    networks:
      - myrag-network

  # Milvus MinIO
  minio:
    container_name: milvus-minio
    image: minio/minio:RELEASE.2023-03-20T20-16-18Z
    environment:
      MINIO_ACCESS_KEY: minioadmin
      MINIO_SECRET_KEY: minioadmin
    volumes:
      - minio-data:/minio_data
    command: minio server /minio_data
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
      interval: 30s
      timeout: 20s
      retries: 3
    networks:
      - myrag-network

  # Milvus Standalone
  milvus-standalone:
    container_name: milvus-standalone
    image: milvusdb/milvus:v2.3.0
    command: ["milvus", "run", "standalone"]
    environment:
      ETCD_ENDPOINTS: etcd:2379
      MINIO_ADDRESS: minio:9000
    volumes:
      - milvus-data:/var/lib/milvus
    ports:
      - "19530:19530"
      - "9091:9091"
    depends_on:
      - "etcd"
      - "minio"
    restart: unless-stopped
    networks:
      - myrag-network

  # Nginx 反向代理
  nginx:
    image: nginx:alpine
    container_name: myrag-nginx
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf
      - ./nginx/ssl:/etc/nginx/ssl
      - ./iflyzcragvue/dist:/usr/share/nginx/html
    depends_on:
      - backend
    restart: unless-stopped
    networks:
      - myrag-network

volumes:
  mysql-data:
  milvus-data:
  etcd-data:
  minio-data:

networks:
  myrag-network:
    driver: bridge
```

**Nginx 配置示例（nginx.conf）：**
```nginx
upstream backend {
    server backend:8080;
}

server {
    listen 80;
    server_name example.com;

    # 前端静态文件
    location / {
        root /usr/share/nginx/html;
        try_files $uri $uri/ /index.html;
    }

    # 后端 API 代理
    location /api/ {
        proxy_pass http://backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # 超时设置
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }

    # 文件上传大小限制
    client_max_body_size 50M;
}
```

---

## 10. 技术风险与对策

### 10.1 向量检索准确率不足

**风险：** Top-K 检索可能漏掉关键信息，导致回答不准确。

**对策：**
1. **调整 Chunk 大小和重叠策略**：实验不同的 chunk 大小（500/800/1000 字符）和重叠比例（10%/15%/20%）
2. **使用混合检索**：结合向量检索和关键词检索（BM25），提高召回率
3. **引入 Rerank 模型**：在 Top-K 基础上使用 Rerank 模型重新排序，提高精准度
4. **优化 Embedding 模型**：尝试不同的 Embedding 模型（bge-large-zh、text-embedding-ada-002）

### 10.2 LLM API 调用超时

**风险：** 网络不稳定或 API 限流导致调用超时，影响用户体验。

**对策：**
1. **设置合理的超时时间**：10 秒超时，超过后返回错误提示
2. **实现重试机制**：最多重试 3 次，使用指数退避策略（1s、2s、4s）
3. **提供降级方案**：超时后仅返回检索到的上下文，不生成答案
4. **使用流式响应**：支持 SSE（Server-Sent Events），实时返回生成的文本

### 10.3 工具执行阻塞主流程

**风险：** 工具执行时间过长（如网络搜索），阻塞用户响应。

**对策：**
1. **工具异步执行**：使用 CompletableFuture 异步执行工具，不阻塞主线程
2. **设置超时限制**：单个工具最多 5 秒，超过后终止执行
3. **记录工具性能指标**：监控每个工具的平均执行时间，优化慢工具
4. **工具优先级**：高优先级工具先执行，低优先级工具可选择性跳过

### 10.4 文档处理失败

**风险：** PDF 解析失败、文本提取错误、向量化失败。

**对策：**
1. **文件格式验证**：上传前验证文件格式和完整性
2. **错误重试机制**：处理失败后自动重试 3 次
3. **详细错误日志**：记录失败原因（文件损坏、编码错误、向量库连接失败等）
4. **降级处理**：部分处理失败时，保存已处理的 chunks，标记文档状态为"部分完成"

### 10.5 用户数据隔离失效

**风险：** 代码漏洞导致用户访问到其他用户的文档或对话历史。

**对策：**
1. **强制数据库过滤**：所有查询必须包含 `user_id` 过滤条件
2. **Milvus 表达式过滤**：向量检索时使用 `expr` 参数过滤 `user_id`
3. **代码审查**：所有涉及数据访问的代码必须通过安全审查
4. **单元测试**：编写测试用例验证数据隔离逻辑

---

## 11. 后续扩展方向

### 11.1 多模态支持

- **图片识别**：使用 OCR 技术提取图片中的文字（Tesseract、PaddleOCR）
- **表格识别**：使用表格识别模型提取 PDF 中的表格数据（TableTransformer）
- **图表理解**：使用多模态 LLM（如 GPT-4V）理解图表内容

### 11.2 多轮对话上下文管理

- **对话历史**：保留最近 5 轮对话，作为上下文输入 LLM
- **对话摘要**：超过 10 轮后，使用 LLM 生成对话摘要，压缩上下文
- **引用溯源**：用户可点击回答中的引用，查看原始上下文

### 11.3 知识图谱集成

- **实体抽取**：从文档中提取实体（人名、地名、组织等）
- **关系抽取**：构建实体之间的关系（如"张三在阿里巴巴工作"）
- **图谱检索**：结合向量检索和图谱检索，提高准确性

### 11.4 用户反馈学习机制

- **回答评分**：用户可对回答点赞/点踩
- **反馈收集**：用户可标注"回答不准确"并提供正确答案
- **模型微调**：基于反馈数据微调 Embedding 模型或 Rerank 模型

### 11.5 第三方登录集成

- **OAuth 2.0 集成**：支持微信、GitHub、Google 登录
- **账号绑定**：支持多种登录方式绑定到同一账号

### 11.6 移动端适配

- **响应式设计**：前端适配移动端屏幕
- **PWA 支持**：支持离线访问和添加到主屏幕
- **原生应用**：开发 React Native / Flutter 移动应用

---

**文档结束**







