import http from './http'
import type { SkillConfig, SkillToggleRequest } from '@/types/api'

export const listSkills = () =>
  http.get<any, { data: SkillConfig[] }>('/api/skills')

export const toggleSkill = (name: string, data: SkillToggleRequest) =>
  http.put<any, { data: SkillConfig }>(`/api/skills/${name}/toggle`, data)
