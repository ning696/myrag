import http from './http'
import type { ToolConfig, ToolGlobalConfig, ToolParamsRequest, ToolToggleRequest } from '@/types/api'

export const listTools = () =>
  http.get<any, { data: ToolConfig[] }>('/api/tools')

export const toggleTool = (name: string, data: ToolToggleRequest) =>
  http.put<any, { data: ToolConfig }>(`/api/tools/${name}/toggle`, data)

export const getGlobalToolParams = () =>
  http.get<any, { data: ToolGlobalConfig }>('/api/tools/global')

export const updateGlobalToolParams = (data: ToolParamsRequest) =>
  http.put<any, { data: ToolGlobalConfig }>('/api/tools/global', data)

export const updateToolParams = (name: string, data: ToolParamsRequest) =>
  http.put<any, { data: ToolConfig }>(`/api/tools/${name}/params`, data)
