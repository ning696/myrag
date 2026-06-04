const TOKEN_KEY = 'token'
const LEGACY_USER_STORE_KEY = 'user-store'

export const getStoredToken = () => localStorage.getItem(TOKEN_KEY)

export const setStoredToken = (token: string) => {
  localStorage.setItem(TOKEN_KEY, token)
}

export const clearAuthStorage = () => {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(LEGACY_USER_STORE_KEY)
}
