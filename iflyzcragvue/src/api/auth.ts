import http from './http'
import type { LoginRequest, LoginResponse, User } from '@/types/api'

export const login = (data: LoginRequest) =>
  http.post<any, { data: LoginResponse }>('/api/auth/login', data)

export const me = () =>
  http.get<any, { data: User }>('/api/auth/me')
