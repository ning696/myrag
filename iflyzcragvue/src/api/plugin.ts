import http from './http'
import type { PluginConfig, PluginConfigRequest, PluginToggleRequest } from '@/types/api'

export const listPlugins = () =>
  http.get<any, { data: PluginConfig[] }>('/api/plugins')

export const togglePlugin = (name: string, data: PluginToggleRequest) =>
  http.put<any, { data: PluginConfig }>(`/api/plugins/${name}/toggle`, data)

export const updatePluginConfig = (name: string, data: PluginConfigRequest) =>
  http.put<any, { data: PluginConfig }>(`/api/plugins/${name}/config`, data)
