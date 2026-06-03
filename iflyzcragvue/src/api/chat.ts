import http from './http'
import type { ChatSession, ChatMessage } from '@/types/api'

export const createSession = (title?: string) =>
  http.post<any, { data: ChatSession }>('/api/chat/sessions', { title })

export const listSessions = () =>
  http.get<any, { data: ChatSession[] }>('/api/chat/sessions')

export const deleteSession = (sessionId: string) =>
  http.delete<any, { data: void }>(`/api/chat/sessions/${sessionId}`)

export const renameSession = (sessionId: string, title: string) =>
  http.patch<any, { data: void }>(`/api/chat/sessions/${sessionId}`, { title })

export const getMessages = (sessionId: string) =>
  http.get<any, { data: ChatMessage[] }>(`/api/chat/sessions/${sessionId}/messages`)
