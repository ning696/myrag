import http from './http'
import type { ToolConfig, ToolToggleRequest } from '@/types/api'

export const listTools = () =>
  http.get<any, { data: ToolConfig[] }>('/api/tools')

export const toggleTool = (name: string, data: ToolToggleRequest) =>
  http.put<any, { data: ToolConfig }>(`/api/tools/${name}/toggle`, data)
