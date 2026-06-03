import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ChatSession, ChatMessage } from '@/types/api'
import * as chatApi from '@/api/chat'

export const useChatStore = defineStore('chat', () => {
  const sessions = ref<ChatSession[]>([])
  const activeSessionId = ref<string | null>(null)
  const activeMessages = ref<ChatMessage[]>([])

  const fetchSessions = async () => {
    const res = await chatApi.listSessions()
    sessions.value = res.data
  }

  const createSession = async (title?: string) => {
    const res = await chatApi.createSession(title)
    sessions.value.unshift(res.data)
    activeSessionId.value = res.data.sessionId
    activeMessages.value = []
    return res.data
  }

  const deleteSession = async (sessionId: string) => {
    await chatApi.deleteSession(sessionId)
    sessions.value = sessions.value.filter(s => s.sessionId !== sessionId)
    if (activeSessionId.value === sessionId) {
      activeSessionId.value = null
      activeMessages.value = []
    }
  }

  const loadMessages = async (sessionId: string) => {
    const res = await chatApi.getMessages(sessionId)
    activeSessionId.value = sessionId
    activeMessages.value = res.data
  }

  const addMessage = (message: ChatMessage) => {
    activeMessages.value.push(message)
  }

  const updateMessage = (id: number, patch: Partial<ChatMessage>) => {
    const message = activeMessages.value.find((m) => m.id === id)
    if (message) {
      Object.assign(message, patch)
    }
  }

  return {
    sessions,
    activeSessionId,
    activeMessages,
    fetchSessions,
    createSession,
    deleteSession,
    loadMessages,
    addMessage,
    updateMessage
  }
})
