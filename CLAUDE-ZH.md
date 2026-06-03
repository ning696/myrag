# CLAUDE.md 中文版

本文档是 CLAUDE.md 的中文镜像版本，供开发者阅读理解。AI 编码时会读取英文版 CLAUDE.md。

## ⚠️ RAG 任务强制阅读

**在执行任何 RAG 相关任务**（文档处理、分块、embedding、向量检索、prompt 设计、答案生成、评估、防幻觉、监控）之前，**必须**：

1. 首先阅读 [文档/RAG工程化准确率提升指南.md](文档/RAG工程化准确率提升指南.md)
2. 遵循其中定义的方案、阈值、prompt 模板和工程化检查清单
3. 在汇报任务完成前，对照"Claude Code 工作清单"小节逐条核对实现

适用范围：
- [iflyzcragback/src/main/java/com/zc/iflyzcragback/rag/](iflyzcragback/src/main/java/com/zc/iflyzcragback/rag/)（含所有子包）
- `ChatService`、`DocumentService`，以及任何调用 `EmbeddingModel` / `EmbeddingStore` / `ChatLanguageModel` 的代码
- 任何新增的检索或问答类 API 接口

无论任务如何表述，跳过该指南都将被视为低质量实现。

## 项目概述

基于 RAG（检索增强生成）的对话机器人，包含插件和技能机制。技术栈：Spring Boot 3 + Vue 3 + TypeScript。

**核心功能：**
1. 文档知识库检索（RAG 核心）— 支持 PDF/TXT/Markdown 上传、分块、向量化、基于 LLM 的问答
2. 插件机制 — 可插拔模块（如网络搜索、时间查询、计算器），在 RAG 前后执行
3. 技能系统 — 有状态的多轮任务流程（如发送邮件、查询天气）

## 架构说明

```
myrag/
├── iflyzcragback/       Spring Boot 3 后端 (Java 17, Maven)
│   ├── src/main/java/com/zc/iflyzcragback/
│   │   ├── controller/  REST 控制器（仅做参数校验，不写业务逻辑）
│   │   ├── service/     业务逻辑层
│   │   ├── mapper/      MyBatis-Plus / JPA 数据访问层
│   │   ├── entity/      数据库实体类
│   │   ├── dto/         请求/响应 DTO（数据传输对象）
│   │   ├── vo/          视图对象（带额外计算字段的响应包装）
│   │   ├── plugin/      插件接口 + 实现（WebSearch、Calculator 等）
│   │   ├── skill/       技能接口 + 有状态任务流
│   │   ├── rag/         RAG 核心（嵌入、向量库、检索）
│   │   └── config/      Spring 配置（CORS、Security 等）
│   └── src/main/resources/
│       ├── application.properties    主配置文件
│       └── application-dev.properties 开发环境配置
│
└── iflyzcragvue/        Vue 3 + TypeScript + Vite 前端
    ├── src/
    │   ├── views/       页面级组件
    │   ├── components/  可复用 UI 组件
    │   ├── api/         后端 API 封装（每个模块一个文件，带 TS 类型）
    │   ├── stores/      Pinia 状态管理
    │   ├── router/      Vue Router 4 路由配置
    │   ├── utils/       纯函数工具库
    │   └── types/       共享 TypeScript 类型定义
    └── vite.config.ts
```

## 开发命令

### 后端 (iflyzcragback/)

```powershell
# 构建
./mvnw clean package

# 运行（开发模式，支持热重载）
./mvnw spring-boot:run

# 运行测试
./mvnw test

# 运行单个测试类
./mvnw test -Dtest=UserServiceTest

# 运行单个测试方法
./mvnw test -Dtest=UserServiceTest#testLogin
```

### 前端 (iflyzcragvue/)

```powershell
# 安装依赖
npm install

# 开发服务器（默认 http://localhost:5173）
npm run dev

# 生产构建
npm run build

# 预览生产构建
npm run preview
```

## 代码规范

### 后端约定

- **分层严格：**
  - Controller：参数校验（@Valid）、路由，**不写业务逻辑**
  - Service：业务逻辑、事务管理
  - Mapper/Repository：仅数据访问，不写逻辑
  
- **DTO 分离：**
  - `dto/`：请求/响应对象
  - `entity/`：数据库实体（**禁止直接在 API 中暴露**）
  - `vo/`：视图对象（API 响应，带额外计算字段）
  
- **Lombok 已配置：**
  - 使用 `@Data`、`@Builder`、`@AllArgsConstructor`、`@NoArgsConstructor`
  - 注解处理器已在 [pom.xml:81-86](iflyzcragback/pom.xml#L81-L86) 配置好
  
- **插件接口模式：**
  ```java
  interface Plugin {
      void beforeRag(String query);  // RAG 前钩子
      void afterRag(String answer, String context);  // RAG 后钩子
  }
  ```
  插件通过配置注册，可动态启用/禁用。
  
- **技能状态管理：**
  - Session 级别状态（使用 `@SessionScope` 或显式 session 存储）
  - 技能完成后可返回 RAG 模式

### 前端约定

- **文件命名：**
  - 组件：`PascalCase.vue`（如 `UserForm.vue`）
  - 目录：`kebab-case`（如 `user-management/`）
  - 工具函数：`camelCase.ts`（如 `formatDate.ts`）
  
- **仅使用 Composition API：**
  - 使用 `<script setup>` 语法
  - 使用 Vue 3 的 `ref`/`reactive`、`computed`、`watch`
  
- **API 层规范：**
  - 每个后端模块对应 `src/api/` 下的一个文件（如 `api/user.ts`、`api/document.ts`）
  - 导出带类型的函数：`export const login = (data: LoginRequest): Promise<LoginResponse> => ...`
  - **新增后端接口时，必须立即在 API 文件中定义对应的 TypeScript 类型**
  
- **避免内联样式：**
  - 使用 scoped `<style>` 块，或在引入 UI 库后使用工具类

## 技术栈详情

### 后端
- Spring Boot 3.4.1（需要 Java 17+）
- Spring Web、Spring Security（JWT 认证）、Spring Boot DevTools
- Lombok 减少样板代码
- MyBatis-Plus 作为 ORM
- MySQL 8.0 存储关系型数据（用户、文档元数据、会话）
- Milvus 2.3+ 作为向量库，提供相似度检索
- **LangChain4j 0.36.x** 作为 AI 编排框架（统一抽象 `ChatLanguageModel`、`EmbeddingModel`、`EmbeddingStore`、`DocumentParser`、`DocumentSplitter`、`EmbeddingStoreIngestor`、`ContentRetriever`）
  - `langchain4j-spring-boot-starter`：Spring Boot 自动装配
  - `langchain4j-open-ai`：DeepSeek（OpenAI 兼容协议）/ OpenAI Provider
  - `langchain4j-milvus`：Milvus `EmbeddingStore`，通过 metadata filter 实现 `userId` 数据隔离
  - `langchain4j-community-dashscope-spring-boot-starter`：阿里云 DashScope `text-embedding-v2`（云端，1536 维，中文优化）
  - `langchain4j-document-parser-apache-pdfbox`：内置 PDF 解析器
  - 备选 Provider：`langchain4j-zhipu-ai`（智谱 GLM）、`langchain4j-dashscope`（通义千问）
- OkHttp 4.x 仅用于**非 LLM** 的外部 HTTP 调用（网络搜索、天气 API、邮件）。LLM 调用统一走 LangChain4j。
- BCrypt 密码加密

### 前端
- Vue 3.5.34，使用 `<script setup>` 单文件组件
- TypeScript 6.0.2
- Vite 8.0.12
- Element Plus UI 组件库
- Pinia 状态管理（用户认证状态、Token 存储）
- Vue Router 4 路由（含路由守卫）
- Axios HTTP 客户端（带 Token 拦截器）

## RAG 系统设计要点

根据 文档/RAG系统说明文档.md 与 文档/开发文档/design.md：

1. **用户认证：**
   - JWT 认证 + BCrypt 密码加密
   - Token 默认 2 小时（勾选"记住我" 7 天）
   - 角色：USER（普通用户）/ ADMIN（管理员，可管理插件、Skill）
   - 数据隔离：用户只能访问自己的文档与会话

2. **文档处理流程：**
   - 上传 → `DocumentParser` 解析（PDFBox / Text）→ `DocumentSplitters.recursive(800, 80)` 分块 → `EmbeddingModel`（DashScope text-embedding-v2，云端）向量化 → `MilvusEmbeddingStore` 写入（携带 `userId`、`documentId`、`filename` metadata）
   - 整套流水线由 `EmbeddingStoreIngestor` 一次性完成
   - 向量库：Milvus 2.3+

3. **检索流程：**
   - 用户提问 → `EmbeddingStoreContentRetriever`（filter: `metadataKey("userId").isEqualTo(...)`，maxResults=3）→ `PromptTemplate` 渲染 → `ChatLanguageModel` 生成
   - LLM API：DeepSeek（默认，OpenAI 兼容协议）/ 智谱 / 通义 —— 切换仅需替换 `ChatLanguageModel` Bean
   - 超时 10s，最多重试 3 次（在 `ChatLanguageModel` builder 上配置）

4. **插件钩子点：**
   - `before_rag(query)`：预处理（如 TimePlugin 识别时间相关问题）
   - `after_rag(answer, context)`：后处理（如 WebSearchPlugin 在 RAG 无结果时触发）
   - 插件通过数据库 `plugins_config` 表启用/禁用（仅管理员）

5. **技能状态机：**
   - Session 级别状态跟踪，存储在 `skill_states` 表
   - 技能可显式触发（"我要发邮件"）或被 LLM 识别
   - 技能完成后返回 RAG 模式

## 常见开发模式

### 新增后端 API 接口

1. 在 `dto/` 创建 DTO（带校验注解的请求/响应对象）
2. 在 Service 添加业务逻辑方法
3. 在 Controller 添加方法，使用 `@RestController`、`@RequestMapping`、`@Valid`
4. 返回统一 Result 包装：`Result.success(data)` / `Result.error(message)`
5. **立即**在 `iflyzcragvue/src/api/` 创建对应函数，带 TypeScript 类型

### 新增插件

1. 在 `plugin/` 实现 `Plugin` 接口
2. 添加 `@Component` 注解，让 Spring 自动检测
3. 在插件配置文件注册（如 `application.properties` 或专门的 `plugins.yml`）
4. 支持通过配置重载动态启用/禁用

### 新增技能

1. 在 `skill/` 实现 `Skill` 接口
2. 定义状态机（步骤、转换、完成条件）
3. 在 session 或专门的技能状态管理器中存储状态
4. 处理每一步的用户输入，提供清晰的提示

## CORS 跨域配置

当后端和前端运行在不同端口（后端 :8080，前端 :5173）时，需添加 CORS 配置：

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("*");
    }
}
```

## 验证要求

- **后端改动：** 编辑后运行 `./mvnw clean package` 验证编译。如有测试，运行 `./mvnw test`。
- **前端改动：** 编辑后运行 `npm run build` 验证 TypeScript 编译。如果改动影响 UI，运行 `npm run dev` 在浏览器手动测试。
- **API 改动：** 新增/修改 REST 接口时，验证：
  1. 后端编译并运行成功
  2. 前端 `src/api/` 中的 TypeScript 类型已更新
  3. 前端能成功调用接口（通过浏览器开发者工具或手动测试）

## 注意事项

- **认证已落地：** 基于 Spring Security 的 JWT 认证 + BCrypt 密码加密
- **数据库已配置：** MySQL 8.0 + MyBatis-Plus
- **向量库：** Milvus 2.3+，支持高性能向量检索
- **UI 库：** 已采用 Element Plus
- **HTTP 客户端：** LLM 调用统一走 LangChain4j；OkHttp 仅用于非 LLM 的外部 HTTP（搜索、天气、邮件）
- **API Key 安全：** 绝不将 LLM API Key、Milvus 凭证、JWT 密钥提交到 git。使用环境变量或 `.env` 文件（加入 `.gitignore`）
- **数据隔离：** 所有查询必须携带 `userId` 过滤；Milvus 检索使用 LangChain4j `Filter.metadataKey("userId").isEqualTo(...)`

## API 文档参考

在实现 LLM 模型集成或调试模型 API 调用时，参考以下官方 API 文档链接：

### 阿里云通义千问（Qwen）
- **API 控制台**: https://bailian.console.aliyun.com/cn-beijing?spm=5176.12818093_47.overview_recent.1.45902cc92buxYi&tab=api#/api/?type=model&url=2712515
- **用途**: DashScope API 配置、Qwen 模型参数、API Key 设置、速率限制、支持的模型列表
- **LangChain4j 集成**: 使用 `langchain4j-dashscope` 依赖

### DeepSeek
- **工具调用指南**: https://api-docs.deepseek.com/zh-cn/guides/tool_calls
- **用途**: DeepSeek API 集成、函数调用模式、工具使用实现、流式响应
- **LangChain4j 集成**: 使用 `langchain4j-open-ai` 配合 DeepSeek 基础 URL（OpenAI 兼容端点）
- **与 OpenAI 的差异**: 查阅此指南了解 DeepSeek 在工具调用和流式响应方面的特定行为

**何时使用这些参考文档：**
- 在 `application.properties` 中配置新的模型提供商
- 在聊天服务中实现函数调用 / 工具使用
- 调试模型 API 连接问题
- 比较不同提供商之间的功能支持
- 设置 API 认证和请求头

---

**说明：** AI 编码时会读取英文版 [CLAUDE.md](CLAUDE.md)，本文档 [CLAUDE-ZH.md](CLAUDE-ZH.md) 仅供开发者阅读参考。
