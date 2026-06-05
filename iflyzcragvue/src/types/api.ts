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
  answerMode?: 'CHAT' | 'RAG_ANSWER' | 'NO_KB_HIT' | 'TOOL_CALLING' | 'SKILL' | 'REALTIME_UNAVAILABLE'
  routeReason?: string
  skillUsed?: string
  skillStep?: string
  skillCompleted?: boolean
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

export interface ToolConfig {
  toolName: string
  displayName: string
  description: string
  enabled: boolean
  available: boolean
  params: ToolParamDefinition[]
}

export interface ToolToggleRequest {
  enabled: boolean
}

export type ToolParamValue = string | number | boolean

export interface ToolParamDefinition {
  key: string
  label: string
  type: 'boolean' | 'integer' | 'number' | 'select' | 'timezone' | 'url'
  description: string
  defaultValue: ToolParamValue
  value: ToolParamValue
  overridden: boolean
  options?: string[]
  min?: number
  max?: number
}

export interface ToolParamsRequest {
  params: Record<string, ToolParamValue>
}

export interface ToolGlobalConfig {
  params: ToolParamDefinition[]
}

export interface SkillConfig {
  skillName: string
  displayName: string
  description: string
  enabled: boolean
  available: boolean
}

export interface SkillToggleRequest {
  enabled: boolean
}
