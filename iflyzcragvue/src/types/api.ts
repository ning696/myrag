export interface User {
  id: number
  username: string
  email: string
  nickname?: string
  avatar?: string
  role: string
}

export interface LoginRequest {
  username: string
  password: string
  rememberMe?: boolean
}

export interface LoginResponse {
  token: string
  user: User
}

export interface Document {
  id: number
  filename: string
  fileType: string
  fileSize: number
  uploadTime: string
  chunkCount: number
  status: string
}

export interface ChunkPreview {
  chunkIndex: number
  content: string
  title?: string
  keywords?: string[]
}

export interface ChunkParams {
  size: number
  overlap: number
  strategy: string
}

export interface UploadResponse {
  documentId: number
  params: ChunkParams
  chunks: ChunkPreview[]
}

export interface IngestProgress {
  status: string
  processed: number
  total: number
}

export interface ChatSession {
  sessionId: string
  title: string
  createdAt: string
  updatedAt: string
}

export interface ChatMessage {
  id: number
  role: string
  content: string
  citations?: Citation[]
  confidence?: number
  answerMode?: 'CHAT' | 'RAG_ANSWER' | 'NO_KB_HIT' | 'WEB_SEARCH' | 'REALTIME_UNAVAILABLE'
  routeReason?: string
  createdAt: string
}

export interface Citation {
  n: number
  documentId?: number
  documentName?: string
  chunkIndex?: number
  content: string
  score: number
  sourceType?: 'document' | 'web'
  title?: string
  url?: string
  publishedDate?: string
}

export interface ChatRequest {
  sessionId: string
  query: string
}

export interface PluginConfig {
  pluginName: string
  description: string
  enabled: boolean
  hookType: 'before' | 'after' | 'both'
  priority: number
  configJson: string
  registered: boolean
}

export interface PluginToggleRequest {
  enabled: boolean
}

export interface PluginConfigRequest {
  configJson?: string
  hookType?: 'before' | 'after' | 'both'
  priority?: number
}
