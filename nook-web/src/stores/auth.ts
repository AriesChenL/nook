import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

const TOKEN_KEY = 'nook.token'
const USER_KEY = 'nook.user'

export interface AuthUser {
  userId: string // user public_id 字符串
  username: string
  nickname?: string
  avatarUrl?: string
}

function readUser(): AuthUser | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as AuthUser
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) ?? '')
  const user = ref<AuthUser | null>(readUser())

  const displayName = computed(() => user.value?.nickname || user.value?.username || '')
  const isLoggedIn = computed(() => !!token.value)

  function setAuth(t: string, u: AuthUser) {
    token.value = t
    user.value = u
    localStorage.setItem(TOKEN_KEY, t)
    localStorage.setItem(USER_KEY, JSON.stringify(u))
  }

  // 仅更新头像（设置头像后即时反映到侧栏/资料页，并落本地缓存）
  function setAvatar(url: string) {
    if (!user.value) return
    setAuth(token.value, { ...user.value, avatarUrl: url })
  }

  function clear() {
    token.value = ''
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  return { token, user, displayName, isLoggedIn, setAuth, setAvatar, clear }
})
