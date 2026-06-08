<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Document, Files, Plus, Promotion } from '@element-plus/icons-vue'
import { fetchEventSource } from '@microsoft/fetch-event-source'
import { useChatStore } from '@/stores/chat'

const router = useRouter()
const chatStore = useChatStore()
const query = ref('')
const sending = ref(false)
const messagesContainer = ref<HTMLDivElement>()
const defaultApiBaseUrl = import.meta.env.DEV ? 'http://localhost:8080' : ''
const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || defaultApiBaseUrl).replace(/\/$/, '')

const activeSessionTitle = computed(() => {
  const session = chatStore.sessions.find((s) => s.sessionId === chatStore.activeSessionId)
  return session?.title || '新的知识库对话'
})

const canSend = computed(() => Boolean(query.value.trim()) && !sending.value)

const handleSend = async () => {
  if (!query.value.trim()) return
  if (!chatStore.activeSessionId) {
    await chatStore.createSession()
  }

  const userMsg = { id: Date.now(), role: 'user', content: query.value, createdAt: new Date().toISOString() }
  chatStore.addMessage(userMsg)
  const userQuery = query.value
  query.value = ''
  sending.value = true

  let aiContent = ''
  const aiMsg = { id: Date.now() + 1, role: 'assistant', content: '', createdAt: new Date().toISOString() }
  chatStore.addMessage(aiMsg)

  const token = localStorage.getItem('token')
  await fetchEventSource(`${apiBaseUrl}/api/chat/messages/stream`, {
    openWhenHidden: true,
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`
    },
    body: JSON.stringify({ sessionId: chatStore.activeSessionId, query: userQuery }),
    onmessage(ev) {
      if (ev.event === 'token') {
        aiContent += ev.data
        chatStore.updateMessage(aiMsg.id, { content: aiContent })
        nextTick(() => scrollToBottom())
      } else if (ev.event === 'citations') {
        chatStore.updateMessage(aiMsg.id, { citations: JSON.parse(ev.data) })
      } else if (ev.event === 'done') {
        if (ev.data && ev.data !== '{}') {
          const data = JSON.parse(ev.data)
          chatStore.updateMessage(aiMsg.id, {
            answerMode: data.answerMode,
            confidence: data.confidence ?? undefined,
            routeReason: data.routeReason,
            skillUsed: data.skillUsed,
            skillStep: data.skillStep,
            skillCompleted: data.skillCompleted
          })
        }
        sending.value = false
      }
    },
    onerror(err) {
      ElMessage.error('对话失败')
      sending.value = false
      throw err
    }
  })
  nextTick(() => scrollToBottom())
}

const scrollToBottom = () => {
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

const handleNewSession = async () => {
  await chatStore.createSession()
  ElMessage.success('新建会话成功')
}

const confidenceText = (confidence?: number) => {
  if (confidence === undefined || confidence === null) return '未返回置信度'
  return `${Math.round(Math.min(confidence, 1) * 100)}% 可信`
}

const confidenceClass = (confidence?: number) => {
  if (confidence === undefined || confidence === null) return 'unknown'
  if (confidence >= 0.75) return 'high'
  if (confidence >= 0.65) return 'medium'
  return 'low'
}

const answerMetaText = (msg: { answerMode?: string; confidence?: number; skillUsed?: string }) => {
  if (msg.answerMode === 'CHAT') return '普通对话'
  if (msg.answerMode === 'NO_KB_HIT') return '未命中知识库'
  if (msg.answerMode === 'TOOL_CALLING') return '工具调用'
  if (msg.answerMode === 'SKILL') return msg.skillUsed ? `技能流程 · ${msg.skillUsed}` : '技能流程'
  if (msg.answerMode === 'REALTIME_UNAVAILABLE') return '需要实时数据'
  return confidenceText(msg.confidence)
}

const answerMetaClass = (msg: { answerMode?: string; confidence?: number }) => {
  if (msg.answerMode === 'CHAT') return 'chat'
  if (msg.answerMode === 'NO_KB_HIT') return 'no-hit'
  if (msg.answerMode === 'TOOL_CALLING') return 'tool-calling'
  if (msg.answerMode === 'SKILL') return 'skill'
  if (msg.answerMode === 'REALTIME_UNAVAILABLE') return 'realtime'
  return confidenceClass(msg.confidence)
}

const sourceTitle = (citation: { sourceType?: string; title?: string; documentName?: string; n: number }) => {
  if (citation.sourceType === 'web') return `网页 ${citation.n} · ${citation.title || citation.documentName || '未命名网页'}`
  return `来源 ${citation.n} · ${citation.documentName || '未命名文档'}`
}

const sourceMeta = (citation: { sourceType?: string; chunkIndex?: number; score?: number; publishedDate?: string }) => {
  const score = citation.score === undefined || citation.score === null
    ? ''
    : `相关度 ${Math.round(citation.score * 100)}%`
  if (citation.sourceType === 'web') {
    return [citation.publishedDate ? `发布时间 ${citation.publishedDate}` : '', score].filter(Boolean).join(' · ')
  }
  return [`Chunk ${citation.chunkIndex ?? '-'}`, score.replace('相关度', '相似度')].filter(Boolean).join(' · ')
}

const formatTime = (value: string) => {
  if (!value) return ''
  return new Date(value).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

onMounted(async () => {
  await chatStore.fetchSessions()
  if (chatStore.sessions.length > 0 && !chatStore.activeSessionId) {
    await chatStore.loadMessages(chatStore.sessions[0].sessionId)
    nextTick(() => scrollToBottom())
  }
})
</script>

<template>
  <section class="chat-page">
    <aside class="chat-sidebar panel">
      <div class="session-head">
        <div>
          <h2>会话</h2>
          <span>{{ chatStore.sessions.length }} 个对话</span>
        </div>
        <el-button type="primary" :icon="Plus" circle @click="handleNewSession" />
      </div>

      <div v-if="chatStore.sessions.length" class="session-list">
        <button
          v-for="s in chatStore.sessions"
          :key="s.sessionId"
          type="button"
          :class="['session-item', { active: s.sessionId === chatStore.activeSessionId }]"
          @click="chatStore.loadMessages(s.sessionId)"
        >
          <el-icon><ChatDotRound /></el-icon>
          <span>
            <strong>{{ s.title || '未命名会话' }}</strong>
            <small>{{ new Date(s.updatedAt || s.createdAt).toLocaleString('zh-CN') }}</small>
          </span>
        </button>
      </div>

      <div v-else class="session-empty">
        <el-icon><ChatDotRound /></el-icon>
        <span>还没有会话</span>
      </div>

      <el-button class="back-button" :icon="Files" @click="router.push('/documents')">返回知识库</el-button>
    </aside>

    <main class="chat-workspace panel">
      <header class="chat-header">
        <div>
          <h1>{{ activeSessionTitle }}</h1>
          <p>回答应基于已入库文档，并展示结构化来源与置信度。</p>
        </div>
      </header>

      <div ref="messagesContainer" class="messages">
        <div v-if="!chatStore.activeMessages.length" class="empty-state chat-empty">
          <div class="empty-icon">
            <el-icon><Promotion /></el-icon>
          </div>
          <h3>开始一次知识库问答</h3>
          <p>可以询问制度条款、流程说明、技术文档或上传资料中的具体内容。</p>
        </div>

        <article
          v-for="msg in chatStore.activeMessages"
          :key="msg.id"
          :class="['message', msg.role === 'user' ? 'user' : 'assistant']"
        >
          <div class="message-avatar">{{ msg.role === 'user' ? '我' : 'AI' }}</div>
          <div class="message-body">
            <div class="bubble">
              <div v-if="msg.content" class="message-content">{{ msg.content }}</div>
              <div v-else class="typing">
                <span></span>
                <span></span>
                <span></span>
              </div>
            </div>

            <div v-if="msg.role !== 'user'" class="answer-meta">
              <span :class="['confidence', answerMetaClass(msg)]">{{ answerMetaText(msg) }}</span>
              <span>{{ formatTime(msg.createdAt) }}</span>
            </div>

            <div v-if="msg.citations?.length" class="citations">
              <div
                v-for="(citation, citationIndex) in msg.citations"
                :key="`${msg.id}-${citation.n}-${citation.url || citation.documentId || citationIndex}`"
                class="citation-item"
              >
                <div>
                  <strong>
                    <a
                      v-if="citation.sourceType === 'web' && citation.url"
                      :href="citation.url"
                      target="_blank"
                      rel="noreferrer"
                    >
                      {{ sourceTitle(citation) }}
                    </a>
                    <template v-else>{{ sourceTitle(citation) }}</template>
                  </strong>
                  <span>{{ sourceMeta(citation) }}</span>
                </div>
                <p>{{ citation.content }}</p>
              </div>
            </div>
          </div>
        </article>
      </div>

      <footer class="composer">
        <div class="composer-box">
          <el-input
            v-model="query"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 5 }"
            placeholder="输入问题，基于已入库文档进行回答..."
            :disabled="sending"
            @keydown.enter.exact.prevent="handleSend"
          />
          <el-button type="primary" :icon="Promotion" :loading="sending" :disabled="!canSend" @click="handleSend">
            发送
          </el-button>
        </div>
        <p>
          <el-icon><Document /></el-icon>
          回答质量取决于检索命中与来源质量；低置信度回答请优先检查引用。
        </p>
      </footer>
    </main>
  </section>
</template>

<style scoped>
.chat-page {
  display: grid;
  width: min(100%, 1440px);
  height: calc(100vh - var(--topbar-height) - 48px);
  min-height: 620px;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 18px;
  margin: 0 auto;
}

.chat-sidebar,
.chat-workspace {
  min-height: 0;
  overflow: hidden;
}

.chat-sidebar {
  display: flex;
  flex-direction: column;
}

.session-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-bottom: 1px solid var(--border-subtle);
  padding: 16px;
}

.session-head h2 {
  color: var(--text-primary);
  font-size: 16px;
  font-weight: 700;
}

.session-head span {
  color: var(--text-tertiary);
  font-size: 12px;
}

.session-list {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 6px;
  min-height: 0;
  overflow-y: auto;
  padding: 10px;
  scrollbar-gutter: stable;
}

.session-item {
  display: flex;
  width: 100%;
  min-height: 76px;
  flex: 0 0 76px;
  align-items: flex-start;
  gap: 10px;
  border: 0;
  border-radius: var(--radius-lg);
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  padding: 11px;
  text-align: left;
}

.session-item:hover,
.session-item.active {
  background: var(--brand-soft);
  color: var(--brand);
}

.session-item span {
  display: grid;
  min-width: 0;
  gap: 4px;
}

.session-item strong,
.session-item small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-item strong {
  color: inherit;
  font-size: 14px;
  font-weight: 650;
}

.session-item small {
  color: var(--text-tertiary);
  font-size: 12px;
}

.session-empty {
  display: grid;
  flex: 1;
  place-items: center;
  align-content: center;
  gap: 8px;
  color: var(--text-tertiary);
  font-size: 13px;
}

.back-button {
  margin: 12px;
}

.chat-workspace {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
}

.chat-header {
  border-bottom: 1px solid var(--border-subtle);
  padding: 18px 20px;
}

.chat-header h1 {
  color: var(--text-primary);
  font-size: 18px;
  font-weight: 700;
}

.chat-header p {
  margin-top: 5px;
  color: var(--text-secondary);
  font-size: 13px;
}

.messages {
  min-height: 0;
  overflow-y: auto;
  padding: 22px;
  background:
    linear-gradient(180deg, rgba(248, 250, 252, 0.8), rgba(255, 255, 255, 0.75)),
    var(--surface);
}

.chat-empty {
  min-height: 100%;
}

.message {
  display: flex;
  gap: 12px;
  margin-bottom: 18px;
}

.message.user {
  justify-content: flex-end;
}

.message.user .message-avatar {
  order: 2;
  background: #172033;
}

.message.user .message-body {
  align-items: flex-end;
}

.message-avatar {
  display: grid;
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 50%;
  background: var(--brand);
  color: #fff;
  font-size: 12px;
  font-weight: 750;
}

.message-body {
  display: flex;
  max-width: min(760px, 78%);
  min-width: 0;
  flex-direction: column;
  gap: 8px;
}

.bubble {
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface);
  padding: 13px 15px;
  box-shadow: var(--shadow-sm);
}

.message.user .bubble {
  border-color: transparent;
  background: #172033;
  color: #fff;
}

.message-content {
  font-size: 14px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

.typing {
  display: flex;
  gap: 5px;
  padding: 6px 0;
}

.typing span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  animation: pulse 1s infinite ease-in-out;
  background: var(--text-tertiary);
}

.typing span:nth-child(2) {
  animation-delay: 0.15s;
}

.typing span:nth-child(3) {
  animation-delay: 0.3s;
}

.answer-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--text-tertiary);
  font-size: 12px;
}

.confidence {
  border-radius: 999px;
  padding: 2px 8px;
  background: var(--surface-subtle);
}

.confidence.high {
  background: var(--success-soft);
  color: var(--success);
}

.confidence.medium {
  background: var(--warning-soft);
  color: var(--warning);
}

.confidence.low {
  background: var(--danger-soft);
  color: var(--danger);
}

.confidence.chat {
  background: var(--surface-subtle);
  color: var(--text-secondary);
}

.confidence.no-hit,
.confidence.tool-calling,
.confidence.skill,
.confidence.realtime {
  background: var(--warning-soft);
  color: var(--warning);
}

.citations {
  display: grid;
  gap: 8px;
}

.citation-item {
  display: grid;
  gap: 8px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: rgba(248, 250, 252, 0.9);
  padding: 10px 12px;
}

.citation-item div {
  display: grid;
  gap: 2px;
}

.citation-item strong {
  color: var(--text-primary);
  font-size: 13px;
}

.citation-item a {
  color: var(--brand);
  text-decoration: none;
}

.citation-item a:hover {
  text-decoration: underline;
}

.citation-item span,
.citation-item p {
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.citation-item p {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.composer {
  border-top: 1px solid var(--border-subtle);
  background: var(--surface);
  padding: 14px;
}

.composer-box {
  display: flex;
  align-items: flex-end;
  gap: 10px;
}

.composer-box .el-input {
  flex: 1;
}

.composer p {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  color: var(--text-tertiary);
  font-size: 12px;
  line-height: 1.5;
}

@keyframes pulse {
  0%,
  80%,
  100% {
    opacity: 0.35;
    transform: translateY(0);
  }

  40% {
    opacity: 1;
    transform: translateY(-3px);
  }
}

@media (max-width: 1020px) {
  .chat-page {
    height: auto;
    min-height: 0;
    grid-template-columns: 1fr;
  }

  .chat-sidebar {
    max-height: 280px;
  }

  .chat-workspace {
    min-height: 680px;
  }
}

@media (max-width: 640px) {
  .messages {
    padding: 14px;
  }

  .message-body {
    max-width: calc(100% - 46px);
  }

  .composer-box {
    align-items: stretch;
    flex-direction: column;
  }

  .composer-box .el-button {
    width: 100%;
  }
}
</style>
