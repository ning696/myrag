<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useChatStore } from '@/stores/chat'
import { fetchEventSource } from '@microsoft/fetch-event-source'

const router = useRouter()
const chatStore = useChatStore()
const query = ref('')
const sending = ref(false)
const messagesContainer = ref<HTMLDivElement>()

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
  await fetchEventSource('http://localhost:8080/api/chat/messages/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    },
    body: JSON.stringify({ sessionId: chatStore.activeSessionId, query: userQuery }),
    onmessage(ev) {
      if (ev.event === 'token') {
        aiContent += ev.data
        aiMsg.content = aiContent
      } else if (ev.event === 'done') {
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

onMounted(async () => {
  await chatStore.fetchSessions()
  if (chatStore.sessions.length > 0 && !chatStore.activeSessionId) {
    await chatStore.loadMessages(chatStore.sessions[0].sessionId)
  }
})
</script>

<template>
  <el-container>
    <el-aside width="250px">
      <el-button type="primary" @click="handleNewSession" style="width: 100%; margin-bottom: 10px">新建会话</el-button>
      <el-menu :default-active="chatStore.activeSessionId">
        <el-menu-item v-for="s in chatStore.sessions" :key="s.sessionId" :index="s.sessionId"
          @click="chatStore.loadMessages(s.sessionId)">
          {{ s.title }}
        </el-menu-item>
      </el-menu>
      <el-button @click="$router.push('/documents')" style="width: 100%; margin-top: 10px">返回文档列表</el-button>
    </el-aside>
    <el-container>
      <el-main ref="messagesContainer">
        <div v-for="msg in chatStore.activeMessages" :key="msg.id" :class="['message', msg.role]">
          <div class="bubble">{{ msg.content }}</div>
        </div>
      </el-main>
      <el-footer height="80px">
        <el-input v-model="query" placeholder="输入问题..." @keyup.enter="handleSend" :disabled="sending">
          <template #append>
            <el-button type="primary" :loading="sending" @click="handleSend">发送</el-button>
          </template>
        </el-input>
      </el-footer>
    </el-container>
  </el-container>
</template>

<style scoped>
.el-aside {
  background: #f5f5f5;
  padding: 10px;
}
.el-main {
  padding: 20px;
  overflow-y: auto;
  height: calc(100vh - 140px);
}
.message {
  margin-bottom: 15px;
  display: flex;
}
.message.user {
  justify-content: flex-end;
}
.message.assistant {
  justify-content: flex-start;
}
.bubble {
  max-width: 70%;
  padding: 10px 15px;
  border-radius: 10px;
  word-wrap: break-word;
  white-space: pre-wrap;
}
.user .bubble {
  background: #409eff;
  color: white;
}
.assistant .bubble {
  background: #f0f0f0;
  color: #333;
}
.el-footer {
  padding: 20px;
  border-top: 1px solid #ddd;
}
</style>
