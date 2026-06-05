# AI 结对编程

> SpringBoot3 + Vue3 全栈实习学习指南（含 Claude Code AI 提效）

## 📅 第一周：基础铺垫与环境搭建

### Day 1-2：开发环境

JDK 17/21 安装配置
Maven/Gradle 基础
nvm
IDEA / VS Code 插件推荐（Copilot, Claude Dev, Tabnine）
Claude Code 配置：安装、API Key 设置、基本命令

### Day 3-4：Java/SpringBoot 基础速通

Lambda、Stream API、Optional
注解原理、反射基础
Maven 多模块结构

### Day 5：Vue3 基础速通

Composition API 核心
ref/reactive、computed、watch
生命周期钩子

### 周末：AI 辅助学习

用 Claude Code 生成一个"用户登录" demo，理解如何与 AI 交互
```text
# Claude Code 示例提示
"帮我创建一个 SpringBoot 3 项目，包含：
- 用户实体（id, username, password）
- JWT 认证
- H2 内存数据库
- 基础 CRUD API"
```

## 📅 第二周：核心功能开发

### Day 6-7：SpringBoot3 核心

Spring Security + JWT
统一异常处理（@RestControllerAdvice）
数据校验（@Valid）
MyBatis-Plus / JPA 基础

### Day 8-9：Vue3 进阶

Vue Router 4 动态路由
Pinia 状态管理
Axios 封装 + 请求拦截器
Element Plus / Naive UI 组件库

### Day 10-11：前后端联调

跨域配置（CORS）
API 接口规范（Result 统一返回格式）
Token 前后端交互流程

### 周末：AI 辅助实战

```text
"用 Claude Code 帮我生成完整的『用户管理模块』：
前端：Vue3 + Element Plus 表格、表单、分页
后端：REST API + JWT 鉴权 + 参数校验
要求：给出完整代码和说明"
```

## 任务1～2周

### 核心任务定义

开发一个基于知识库检索的对话机器人，支持：
1. 文档知识库检索（RAG 核心）
2. Tool 调用机制（实时能力）
3. Skill 能力（多轮任务型功能）

### 技术要求与边界

1. RAG 核心（必须完成）
- 支持上传 PDF / TXT / Markdown 文档
- 文档 → 分块（chunk） → 向量化 → 本地或轻量级向量库
- 用户问题 → 检索相关片段 → 拼接 Prompt → 调用 LLM 生成回答
推荐技术栈（易上手）：
- Java、Vue3
- 向量库：Chroma  或 FAISS
- Embedding 模型：sentence-transformers （如 BAAI/bge-small-zh ）
- LLM：OpenAI API / 国内 API（DeepSeek、智谱、通义千问）
2. Tool 调用机制（简单但清晰）
Tool = 暴露给 LangChain4j Function Calling 的后端方法，由模型按需调用。
例子：
- web_search ：搜索 API
- current_time ：识别相对时间并给出当前时间
设计要求：
- Tool 基于 LangChain4j `@Tool` / `@P`
- 支持管理员动态启用/禁用
- 非敏感参数由配置文件控制，API Key 走环境变量
3. Skill 能力（任务型多轮对话）
Skill = 有状态的任务流程，例如：
- 邮件发送 Skill：询问收件人 → 主题 → 内容 → 确认发送
- 天气查询 Skill：询问城市 → 日期 → 返回天气
设计要求：
- 维护对话状态（session 级别）
- Skill 可以主动触发（如用户说“我要发邮件”）或被 LLM 识别
- Skill 执行完成后可返回 RAG 模式

## 📚 推荐资源

### 文档

- SpringBoot 3 官方文档
- Vue3 官方文档
- Claude Code 文档

### 开源项目参考

- GitHub 搜索 "springboot-vue3" 高 star 项目
- elunez/eladmin（前后端分离基础框架）
- HalseySpicy/Geeker-Admin（Vue3 后台模板）

### AI 辅助工具组合

- Claude Code：对话式开发主力
- GitHub Copilot：代码补全
- Cursor：编辑器级 AI 集成
- Continue：开源替代方案

## 🎯 产出检查清单

### 代码产出

GitHub 仓库，有规范的 commit message
README 包含项目启动说明
后端 API 文档可访问
前端项目有环境配置说明

### 技能产出

能独立用 Claude Code 完成一个 CRUD 功能
理解 JWT 鉴权流程
会调试前后端联调问题
能用 AI 解释 90% 的报错信息

### 学习笔记

记录常用提示词（至少20条）
AI 辅助开发的心得（哪些场景最有效）
常见的坑和解决方案

## ⚡ 避坑指南

1. 不要直接复制 AI 代码：必须理解每一行
2. AI 会过时：指定版本号（SpringBoot 3.x, Vue 3.x）
3. API Key 安全：不要提交到 GitHub
4. Token 管理：Claude Code 有配额，复杂问题分批问
5. 验证 AI 答案：用官方文档交叉验证

## 🚀 进阶方向（完成本计划后）

- 部署（Docker + Nginx + GitHub Actions CI/CD）
- WebSocket（实时聊天/通知）
- 微服务（Spring Cloud Alibaba）
- TypeScript 改造前端项目
- 用 AI 生成单元测试 + E2E 测试
