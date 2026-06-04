# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## ⚠️ Mandatory Reading for RAG Tasks

**Before performing ANY RAG-related task** (document processing, chunking, embedding, vector retrieval, prompt design, answer generation, evaluation, hallucination prevention, monitoring), you **MUST**:

1. Read [文档/RAG工程化准确率提升指南.md](文档/RAG工程化准确率提升指南.md) **first**
2. Follow the patterns, thresholds, prompt templates, and engineering checklists defined there
3. Validate your implementation against the "Codex 工作清单" section before reporting completion

This applies to changes in:
- [iflyzcragback/src/main/java/com/zc/iflyzcragback/rag/](iflyzcragback/src/main/java/com/zc/iflyzcragback/rag/) (all subpackages)
- `ChatService`, `DocumentService`, and any code calling `EmbeddingModel` / `EmbeddingStore` / `ChatLanguageModel`
- Any new API endpoint that exposes retrieval or QA functionality

Skipping the guide is treated as a low-quality implementation regardless of how the task was phrased.

## Project Overview

RAG-based conversational chatbot with user authentication, plugin and skill mechanisms. Built with Spring Boot 3 + Vue 3 + TypeScript.

**Core Features:**
1. User authentication system — JWT-based registration and login with role-based access control
2. Document knowledge base retrieval (RAG core) — PDF/TXT/Markdown upload, chunking, vectorization, and LLM-based QA
3. Plugin mechanism — pluggable modules (e.g., WebSearch, Time, Calculator) that hook before/after RAG
4. Skill system — stateful multi-turn task flows (e.g., email sending, weather queries)
5. Data isolation — users can only access their own documents and chat history

## Architecture

```
myrag/
├── iflyzcragback/       Spring Boot 3 backend (Java 17, Maven)
│   ├── src/main/java/com/zc/iflyzcragback/
│   │   ├── controller/  REST controllers (param validation only, no business logic)
│   │   │   ├── AuthController.java     # User authentication (register/login)
│   │   │   ├── UserController.java     # User management
│   │   │   ├── DocumentController.java # Document management
│   │   │   ├── ChatController.java     # Chat API
│   │   │   ├── PluginController.java   # Plugin management (admin only)
│   │   │   └── SkillController.java    # Skill management (admin only)
│   │   ├── service/     Business logic layer
│   │   ├── mapper/      MyBatis-Plus / JPA data access
│   │   ├── entity/      Database entities (User, Document, ChatSession, etc.)
│   │   ├── dto/         Request/response DTOs
│   │   ├── vo/          View objects (response wrappers)
│   │   ├── security/    JWT authentication (JwtTokenProvider, JwtAuthenticationFilter)
│   │   ├── plugin/      Plugin interface + implementations (WebSearch, Calculator, etc.)
│   │   ├── skill/       Skill interface + stateful task flows
│   │   ├── rag/         RAG core (embedding, Milvus vector store, retrieval)
│   │   └── config/      Spring configuration (CORS, Security with JWT, etc.)
│   └── src/main/resources/
│       ├── application.properties    Main config
│       └── application-dev.properties Dev profile
│
└── iflyzcragvue/        Vue 3 + TypeScript + Vite frontend
    ├── src/
    │   ├── views/       Page-level components (Login, Register, Documents, Chat, etc.)
    │   ├── components/  Reusable UI components
    │   ├── api/         Backend API wrappers (one file per module, with TS types)
    │   ├── stores/      Pinia stores (user state, auth token management)
    │   ├── router/      Vue Router 4 config (route guards for authentication)
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
  - `dto/`: request/response objects
  - `entity/`: database entities (never expose directly in API)
  - `vo/`: view objects (API responses with additional computed fields)
- **Lombok is configured:**
  - Use `@Data`, `@Builder`, `@AllArgsConstructor`, `@NoArgsConstructor`
  - Annotation processing is set up in [pom.xml:81-86](iflyzcragback/pom.xml#L81-L86)
- **Plugin interface pattern:**
  ```java
  interface Plugin {
      void beforeRag(String query);
      void afterRag(String answer, String context);
  }
  ```
  Plugins registered via config, dynamically enabled/disabled.
- **Skill state management:**
  - Session-scoped state (use `@SessionScope` or explicit session store)
  - Skills can return control to RAG mode after completion

### Frontend

- **File naming:**
  - Components: `PascalCase.vue` (e.g., `UserForm.vue`)
  - Directories: `kebab-case` (e.g., `user-management/`)
  - Utilities: `camelCase.ts` (e.g., `formatDate.ts`)
- **Composition API only:**
  - Use `<script setup>` syntax
  - Use `ref`/`reactive`, `computed`, `watch` from Vue 3
- **API layer:**
  - Each backend module gets one file in `src/api/` (e.g., `api/user.ts`, `api/document.ts`)
  - Export typed functions: `export const login = (data: LoginRequest): Promise<LoginResponse> => ...`
  - When adding backend endpoints, immediately define corresponding TypeScript types in the API file
- **Avoid inline styles:**
  - Use scoped `<style>` blocks or utility classes if a UI library is added

## Tech Stack Details

### Backend
- Spring Boot 3.4.1 (requires Java 17+)
- Spring Web, Spring Security (JWT authentication), Spring Boot DevTools
- Lombok for boilerplate reduction
- MyBatis-Plus for ORM
- MySQL 8.0 for relational data (users, documents metadata, sessions)
- Milvus 2.3+ for vector storage and similarity search
- **LangChain4j 0.36.x** as the AI orchestration framework (unified abstractions for `ChatLanguageModel`, `EmbeddingModel`, `EmbeddingStore`, `DocumentParser`, `DocumentSplitter`, `EmbeddingStoreIngestor`, `ContentRetriever`)
  - `langchain4j-spring-boot-starter` for auto-configuration
  - `langchain4j-open-ai` — DeepSeek (OpenAI-compatible) and OpenAI provider
  - `langchain4j-milvus` — Milvus `EmbeddingStore` with metadata filter for `userId` isolation
  - `langchain4j-community-dashscope-spring-boot-starter` — Aliyun DashScope `text-embedding-v2` (cloud, 1536 dim, Chinese-optimized)
  - `langchain4j-document-parser-apache-pdfbox` — built-in PDF parser
  - Optional providers: `langchain4j-zhipu-ai` (智谱 GLM), `langchain4j-dashscope` (通义千问)
- OkHttp 4.x for **non-LLM** external HTTP calls (web search, weather API, mail). LLM calls go through LangChain4j.
- BCrypt for password encryption

### Frontend
- Vue 3.5.34 with `<script setup>` SFCs
- TypeScript 6.0.2
- Vite 8.0.12
- Element Plus for UI components
- Pinia for state management (user auth state, token storage)
- Vue Router 4 for navigation and route guards
- Axios for HTTP requests (with auth token interceptor)

## RAG System Design Notes

Per 文档/RAG系统说明文档.md and 文档/design.md:

1. **User Authentication:**
   - JWT-based authentication with BCrypt password encryption
   - Token valid for 2 hours (7 days with "remember me")
   - Role-based access control: USER (normal users) and ADMIN (can manage plugins/skills)
   - Data isolation: users can only access their own documents and chat sessions

2. **Document Processing Pipeline:**
   - Upload → chunk → embed → store in Milvus (with user_id metadata for filtering)
   - Embedding model: Aliyun DashScope `text-embedding-v2` (cloud, 1536 dim) — auto-configured by `langchain4j-community-dashscope-spring-boot-starter`
   - Supported formats: PDF, TXT, Markdown
   - Vector database: Milvus 2.3+ (enterprise-grade, high-performance)

3. **Retrieval Flow:**
   - User query → embed → search Milvus (with `userId` metadata filter via LangChain4j `Filter`) → retrieve top-k chunks → render prompt via `PromptTemplate` → LLM generation via LangChain4j `ChatLanguageModel`
   - LLM API: DeepSeek (default, OpenAI-compatible) / Zhipu / Qwen — switch by replacing the `ChatLanguageModel` bean
   - Timeout 10s, max retries 3 (configured on the `ChatLanguageModel` builder)

4. **Plugin Hook Points:**
   - `before_rag(query)`: pre-processing (e.g., TimePlugin recognizes time-related queries)
   - `after_rag(answer, context)`: post-processing (e.g., WebSearchPlugin triggers if RAG returns no results)
   - Plugins enabled/disabled via database config (admin-only)

5. **Skill State Machine:**
   - Session-level state tracking for multi-turn tasks
   - Skills can be explicitly triggered ("I want to send an email") or LLM-detected
   - After skill completion, return to RAG mode

## Common Patterns

### Adding a New Backend API Endpoint

1. Create DTO in `dto/` (request/response objects with validation annotations)
2. Add method in Service with business logic (inject current user ID from SecurityContext)
3. Add Controller method with `@RestController`, `@RequestMapping`, `@Valid`
4. **Authorization**: Use `@PreAuthorize` for role checks, extract user ID from SecurityContext
5. Return unified Result wrapper: `Result.success(data)` / `Result.error(code, message)`
6. **Immediately** create corresponding function in `iflyzcragvue/src/api/` with TypeScript types

**Example with auth:**
```java
@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    
    @PostMapping("/upload")
    @PreAuthorize("isAuthenticated()")
    public Result<DocumentVO> upload(@RequestParam("file") MultipartFile file) {
        Long userId = SecurityUtils.getCurrentUserId();  // Extract from JWT token
        DocumentVO doc = documentService.upload(file, userId);
        return Result.success(doc);
    }
}
```

### Adding a New Plugin

1. Implement `Plugin` interface in `plugin/`
2. Add `@Component` annotation for Spring auto-detection
3. Register in database `plugins_config` table (admin-only operation)
4. Support dynamic enable/disable via PluginService

### Adding a New Skill

1. Implement `Skill` interface in `skill/`
2. Define state machine (steps, transitions, completion conditions)
3. Store state in `skill_states` table (keyed by session_id)
4. Handle user input for each step, provide clear prompts

## CORS Configuration

When backend and frontend run on different ports (backend :8080, frontend :5173), add CORS config:

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
- **UI library:** Element Plus is configured
- **HTTP client:** LangChain4j handles all LLM HTTP traffic; OkHttp is used only for non-LLM calls (web search, weather, mail).
- **API Key security:** Never commit LLM API keys, Milvus credentials, or JWT secrets to git. Use environment variables or `.env` files (add to `.gitignore`).
- **Data isolation:** All queries must filter by user_id (from JWT token). Milvus searches use expr parameter for filtering.

## API Documentation References

When implementing LLM model integrations or debugging model API calls, refer to these official API documentation links:

### Alibaba Cloud Qwen (通义千问)
- **API Console**: https://bailian.console.aliyun.com/cn-beijing?spm=5176.12818093_47.overview_recent.1.45902cc92buxYi&tab=api#/api/?type=model&url=2712515
- **Use for**: DashScope API configuration, Qwen model parameters, API key setup, rate limits, supported models list
- **LangChain4j integration**: Use `langchain4j-dashscope` dependency

### DeepSeek
- **Tool Calls Guide**: https://api-docs.deepseek.com/zh-cn/guides/tool_calls
- **Use for**: DeepSeek API integration, function calling patterns, tool use implementation, streaming responses
- **LangChain4j integration**: Use `langchain4j-open-ai` with DeepSeek base URL (OpenAI-compatible endpoint)
- **Key differences from OpenAI**: Check this guide for DeepSeek-specific behavior in tool calls and streaming

**When to use these references:**
- Configuring new model providers in `application.properties`
- Implementing function calling / tool use in chat services
- Debugging model API connection issues
- Comparing feature support between different providers
- Setting up API authentication and headers
