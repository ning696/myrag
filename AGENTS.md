# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## ⚠️ Mandatory Reading for RAG Tasks

**Before performing ANY RAG-related task** (document processing, chunking, embedding, vector retrieval, prompt design, answer generation, evaluation, hallucination prevention, monitoring), you **MUST**:

1. Read [文档/RAG工程化准确率提升指南.md](文档/RAG工程化准确率提升指南.md) **first**
2. Follow the patterns, thresholds, prompt templates, and engineering checklists defined there
3. Validate your implementation against the "Codex 工作清单" section before reporting completion

This applies to changes in:
- [iflyzcragback/src/main/java/com/zc/iflyzcragback/service/rag/](iflyzcragback/src/main/java/com/zc/iflyzcragback/service/rag/) (all subpackages)
- [iflyzcragback/src/main/java/com/zc/iflyzcragback/service/document/](iflyzcragback/src/main/java/com/zc/iflyzcragback/service/document/) when changing parsing, chunking, embedding, ingest, preview, or deletion behavior
- `ChatService`, `DocumentService`, and any code calling `EmbeddingModel` / `EmbeddingStore` / `ChatLanguageModel`
- Any new API endpoint that exposes retrieval or QA functionality

Skipping the guide is treated as a low-quality implementation regardless of how the task was phrased.

## Project Overview

RAG-based conversational chatbot with user authentication, document knowledge base retrieval, LangChain4j tool calling, and a planned skill mechanism. Built with Spring Boot 3 + Vue 3 + TypeScript.

**Core Features:**
1. User authentication system — JWT-based registration and login with role-based access control
2. Document knowledge base retrieval (RAG core) — PDF/TXT/Markdown upload, chunking, vectorization, and LLM-based QA
3. Tool calling — LangChain4j `@Tool` methods for realtime capabilities; current tools are `current_time` and Tavily-backed `web_search`
4. Skill system — product direction only; no active backend `skill/` package or controller exists yet
5. Data isolation — users can only access their own documents and chat history

## Architecture

```
myrag/
├── iflyzcragback/       Spring Boot 3 backend (Java 17, Maven)
│   ├── src/main/java/com/zc/iflyzcragback/
│   │   ├── controller/  REST controllers (param validation only, no business logic)
│   │   │   ├── AuthController.java     # User authentication (register/login)
│   │   │   ├── DocumentController.java # Upload, rechunk, confirm ingest, progress, list, delete
│   │   │   ├── ChatController.java     # Chat API
│   │   │   └── ToolController.java     # Tool management (admin only)
│   │   ├── service/     Business logic layer
│   │   │   ├── document/ # Document parsing, chunk preview, ingest, keyword extraction
│   │   │   ├── rag/      # RAG orchestration, routing, query rewrite, hybrid retrieval, prompt build
│   │   │   └── storage/  # File storage abstraction, MinIO implementation
│   │   ├── mapper/      MyBatis-Plus data access
│   │   ├── entity/      Database entities (User, Document, ChatSession, etc.)
│   │   ├── dto/         Request/response DTOs
│   │   ├── common/      Result wrapper, exception handling
│   │   ├── security/    JWT authentication (JwtService, JwtAuthenticationFilter)
│   │   ├── service/rag/tool/ # LangChain4j @Tool implementations and tool management
│   │   └── config/      Spring configuration (CORS, Security with JWT, etc.)
│   └── src/main/resources/
│       └── application.yml           Main config (imports optional ./.env)
│
└── iflyzcragvue/        Vue 3 + TypeScript + Vite frontend
    ├── src/
    │   ├── views/       Page-level components (Login, Register, Documents, Chat, etc.)
    │   ├── api/         Backend API wrappers (one file per module, with TS types)
    │   ├── stores/      Pinia stores (user state, auth token management)
    │   ├── router/      Vue Router config (route guards for authentication)
    │   ├── utils/       Pure utility functions
    │   └── types/       Shared TypeScript types
    └── vite.config.ts
```

## Development Commands

### Backend (iflyzcragback/)

```powershell
# Build
./mvnw clean package

# Run (dev mode with hot reload)
./mvnw spring-boot:run

# Run tests
./mvnw test

# Run single test class
./mvnw test -Dtest=UserServiceTest

# Run single test method
./mvnw test -Dtest=UserServiceTest#testLogin
```

### Frontend (iflyzcragvue/)

```powershell
# Install dependencies
npm install

# Dev server (default http://localhost:5173)
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview
```

## Code Conventions

### Backend

- **Layering is strict:**
  - Controller: parameter validation (@Valid), routing, no business logic
  - Service: business logic, transaction management
  - Mapper/Repository: data access only, no logic
- **DTO separation:**
  - `dto/`: request/response objects and current `*VO` response view classes
  - `entity/`: database entities (never expose directly in API)
  - There is no separate `vo/` package at the moment; keep API response objects out of `entity/`
- **Lombok is configured:**
  - Use `@Data`, `@Builder`, `@AllArgsConstructor`, `@NoArgsConstructor`
  - Annotation processing is set up in [pom.xml:81-86](iflyzcragback/pom.xml#L81-L86)
- **Tool interface pattern:**
  ```java
  interface ManagedTool {
      String name();
      String displayName();
      String description();
      Object toolInstance();
  }
  ```
  Tools are Spring beans with LangChain4j `@Tool` methods. Admins can enable/disable tools globally from the `tools_config` table; runtime parameters stay in `application.yml` and secrets stay in environment variables.
- **Skill state management:**
  - No active `skill/` package or `SkillController` exists in the current codebase.
  - If adding skills, create a clear package/module, persist session-scoped state explicitly, and update this document plus frontend API types in the same change.

### Frontend

- **File naming:**
  - Components: `PascalCase.vue` (e.g., `UserForm.vue`)
  - Directories: `kebab-case` (e.g., `user-management/`)
  - Utilities: `camelCase.ts` (e.g., `formatDate.ts`)
- **Composition API only:**
  - Use `<script setup>` syntax
  - Use `ref`/`reactive`, `computed`, `watch` from Vue 3
- **API layer:**
  - Each backend module gets one file in `src/api/` (current files: `auth.ts`, `chat.ts`, `document.ts`, `tool.ts`)
  - Export typed functions: `export const login = (data: LoginRequest): Promise<LoginResponse> => ...`
  - When adding backend endpoints, immediately define corresponding TypeScript types in the API file
- **Avoid inline styles:**
  - Use scoped `<style>` blocks or utility classes if a UI library is added

## Tech Stack Details

### Backend
- Spring Boot 3.4.1 (requires Java 17+)
- Spring Web, Spring Security (JWT authentication), Spring Boot DevTools
- Lombok for boilerplate reduction
- MyBatis-Plus 3.5.9 for ORM, plus `mybatis-plus-jsqlparser` 3.5.9 for pagination interceptor support
- MySQL 8.0 for relational data (users, documents metadata, sessions)
- Redis via `spring-boot-starter-data-redis` for login attempts, chunk preview state, and ingest progress
- MinIO Java client 8.5.17 for uploaded document storage
- Milvus 2.3+ for vector storage and similarity search
- **LangChain4j 1.0.0-beta3** as the AI orchestration framework, managed by `langchain4j-bom` and `langchain4j-community-bom`
  - `langchain4j-spring-boot-starter` and `langchain4j` core
  - `langchain4j-open-ai-spring-boot-starter` — DeepSeek (OpenAI-compatible) and OpenAI provider
  - `langchain4j-milvus` — Milvus `EmbeddingStore` with metadata filter for `userId` isolation
  - `langchain4j-community-dashscope-spring-boot-starter` — Aliyun DashScope `text-embedding-v2` (cloud, 1536 dim, Chinese-optimized)
  - `langchain4j-document-parser-apache-pdfbox` — built-in PDF parser
- `StreamingChatLanguageModel` is used for SSE token streaming in `RagOrchestrator`
- Java 17 `HttpClient` is used by the Tavily-backed `web_search` tool; LLM calls go through LangChain4j
- BCrypt for password encryption
- JJWT 0.12.6 for JWT generation and validation
- Jieba Analysis 1.0.2 for keyword extraction

### Frontend
- Vue 3.5.x with `<script setup>` SFCs (`package-lock.json` currently resolves Vue to 3.5.35)
- TypeScript 6.0.x (`package-lock.json` currently resolves TypeScript to 6.0.3)
- Vite 8.0.x (`package-lock.json` currently resolves Vite to 8.0.16)
- Element Plus 2.14.1 and `@element-plus/icons-vue` 2.3.2 for UI components
- Pinia 3.0.4 and `pinia-plugin-persistedstate` 4.7.1 for auth/document/chat state
- Vue Router 5.1.0 for navigation and route guards
- Axios 1.17.0 for normal HTTP requests (with auth token interceptor)
- `@microsoft/fetch-event-source` 2.0.1 for streaming chat/SSE

## RAG System Design Notes

Per 文档/RAG系统说明文档.md, 文档/开发文档/design.md, and the current code:

1. **User Authentication:**
   - JWT-based authentication with BCrypt password encryption
   - Token valid for 2 hours (7 days with "remember me")
   - Role-based access control: USER (normal users) and ADMIN (can manage tools/skills)
   - Data isolation: users can only access their own documents and chat sessions

2. **Document Processing Pipeline:**
   - Upload to MinIO → parse PDF/TXT/Markdown → chunk preview in Redis → user confirms ingest → embed → store chunks in MySQL and vectors in Milvus
   - Embedding model: Aliyun DashScope `text-embedding-v2` (cloud, 1536 dim) — auto-configured by `langchain4j-community-dashscope-spring-boot-starter`
   - Supported formats: PDF, TXT, Markdown
   - Chunking defaults: size 800, overlap 80, strategy `RECURSIVE`; Markdown can use `BY_HEADING`
   - Vector database: Milvus 2.3+ (enterprise-grade, high-performance)

3. **Retrieval Flow:**
   - User query → route (`QueryRouter`) → optional query rewrite (`QueryRewriter`) → hybrid retrieval (`VectorRetriever` + MySQL BM25 via `DocumentChunkMapper`) → RRF merge → prompt build (`PromptBuilder`) → streaming LLM generation via LangChain4j `StreamingChatLanguageModel`
   - Milvus vector search must filter with LangChain4j metadata key `userId`, e.g. `metadataKey("userId").isEqualTo(userId.toString())`
   - MySQL BM25 retrieval must also filter by current `userId`
   - LLM API: DeepSeek by default through the OpenAI-compatible starter. Other providers require adding the matching dependency/bean and updating config.
   - Chat timeout 10s, streaming timeout 60s, max retries 3 (configured in `application.yml`)

4. **Tool Calling:**
   - `QueryRouter` routes realtime/public-current questions to `TOOL_CALLING`.
   - `RealtimeAssistant` exposes enabled `@Tool` beans to the model; current tools are `current_time` and `web_search`.
   - Tools are enabled/disabled via `tools_config` and managed by admin-only `ToolController`.
   - Tool parameters are configured in `application.yml`; API keys must stay in environment variables.

5. **Skill State Machine:**
   - Skill system is described as a product direction but is not implemented in the current backend package structure.
   - Do not assume `SkillController`, `Skill` interface, or `skill_states` table exists unless adding them in the same task.

## Common Patterns

### Adding a New Backend API Endpoint

1. Create DTO in `dto/` (request/response objects with validation annotations)
2. Add method in Service with business logic (inject current user ID from SecurityContext)
3. Add Controller method with `@RestController`, `@RequestMapping`, `@Valid`
4. **Authorization**: Extract current user ID from `SecurityUtils`; use `@PreAuthorize` for role checks such as admin-only tool management
5. Return unified Result wrapper: `Result.success(data)` / `Result.error(code, message)`
6. **Immediately** create corresponding function in `iflyzcragvue/src/api/` with TypeScript types

**Example with auth:**
```java
@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    
    @PostMapping("/upload")
    public Result<UploadResponse> upload(@RequestParam("file") MultipartFile file) {
        Long userId = SecurityUtils.getCurrentUserId();  // Extract from JWT token
        return Result.success(documentService.upload(file, userId));
    }
}
```

### Adding a New Tool

1. Implement `ManagedTool` in `service/rag/tool/`
2. Add one or more LangChain4j `@Tool` methods with clear `@P` parameter descriptions
3. Add `@Component` annotation for Spring auto-detection
4. Add default row in `tools_config` seed SQL and matching frontend display if needed
5. Keep non-sensitive runtime parameters in `application.yml`; never store API keys in the database

### Adding a New Skill

1. Create the missing backend package/interface/controller intentionally; no current `skill/` package exists
2. Define state machine (steps, transitions, completion conditions)
3. Store state explicitly (database or Redis), keyed by session/user as appropriate
4. Handle user input for each step, provide clear prompts, then return control to RAG/chat mode
5. Add matching frontend API wrappers and route/view updates if the skill is user-visible

## CORS Configuration

When backend and frontend run on different ports (backend :8080, frontend :5173), CORS is configured through `WebConfig` + `CorsProperties`, backed by `application.yml`:

```yaml
cors:
  allowed-origins: http://localhost:5173
  allowed-methods: GET,POST,PUT,DELETE,OPTIONS
  allow-credentials: true
  max-age: 3600
```

## Verification Requirements

- **Backend changes:** After editing, run `./mvnw clean package` to verify compilation. Run `./mvnw test` if tests exist.
- **Frontend changes:** After editing, run `npm run build` to verify TypeScript compilation. If the change affects UI, run `npm run dev` and manually test in browser.
- **API changes:** When adding/modifying REST endpoints, verify:
  1. Backend compiles and runs
  2. Frontend TypeScript types are updated in `src/api/`
  3. Frontend can successfully call the endpoint (via browser dev tools or manual testing)
  4. **Authentication**: Verify JWT token is correctly sent and validated
  5. **Authorization**: Verify role-based access control works (USER vs ADMIN)
  6. **Data isolation**: Verify users can only access their own data

## Notes

- **Authentication is implemented:** JWT-based with Spring Security, BCrypt password encryption
- **Database is configured:** MySQL 8.0 with MyBatis-Plus
- **Vector database:** Milvus 2.3+ for high-performance vector search
- **File storage:** MinIO is the primary uploaded document store
- **Redis:** Used for transient state such as preview chunks, ingest progress, and login attempt tracking
- **UI library:** Element Plus is configured
- **HTTP client:** LangChain4j handles LLM/embedding HTTP traffic; Tavily web search currently uses Java 17 `HttpClient`.
- **API Key security:** Never commit LLM API keys, Milvus credentials, or JWT secrets to git. Use environment variables or `.env` files (add to `.gitignore`).
- **Data isolation:** All queries must filter by current user ID from JWT. Milvus metadata uses `userId`; MySQL rows use `user_id` columns mapped to `userId` entity fields.

## API Documentation References

When implementing LLM model integrations or debugging model API calls, refer to these official API documentation links:

### Alibaba Cloud Qwen (通义千问)
- **API Console**: https://bailian.console.aliyun.com/cn-beijing?spm=5176.12818093_47.overview_recent.1.45902cc92buxYi&tab=api#/api/?type=model&url=2712515
- **Use for**: DashScope API configuration, Qwen model parameters, API key setup, rate limits, supported models list
- **LangChain4j integration**: Current embedding integration uses `langchain4j-community-dashscope-spring-boot-starter`; add the appropriate DashScope/Qwen chat dependency before enabling Qwen chat models.

### DeepSeek
- **Tool Calls Guide**: https://api-docs.deepseek.com/zh-cn/guides/tool_calls
- **Use for**: DeepSeek API integration, function calling patterns, tool use implementation, streaming responses
- **LangChain4j integration**: Current code uses `langchain4j-open-ai-spring-boot-starter` with DeepSeek base URL (OpenAI-compatible endpoint)
- **Key differences from OpenAI**: Check this guide for DeepSeek-specific behavior in tool calls and streaming

**When to use these references:**
- Configuring new model providers in `application.yml`
- Implementing function calling / tool use in chat services
- Debugging model API connection issues
- Comparing feature support between different providers
- Setting up API authentication and headers
