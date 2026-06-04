import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { User } from '@/types/api'
import * as authApi from '@/api/auth'
import { clearAuthStorage, getStoredToken, setStoredToken } from '@/utils/authStorage'

export const useUserStore = defineStore('user', () => {
  const token = ref<string | null>(getStoredToken())
  const user = ref<User | null>(null)

  const login = async (username: string, password: string, rememberMe = false) => {
    const res = await authApi.login({ username, password, rememberMe })
    token.value = res.data.token
    user.value = res.data.user
    setStoredToken(res.data.token)
  }

  const logout = () => {
    token.value = null
    user.value = null
    clearAuthStorage()
  }

  const fetchMe = async () => {
    const res = await authApi.me()
    user.value = res.data
  }

  return { token, user, login, logout, fetchMe }
})
