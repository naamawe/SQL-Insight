import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api/auth'
import type { UserInfo } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  // ── State ───────────────────────────────────────────
  const token = ref<string>(localStorage.getItem('token') ?? '')
  const userInfo = ref<UserInfo | null>(
    JSON.parse(localStorage.getItem('userInfo') ?? 'null'),
  )

  // ── Getters ─────────────────────────────────────────
  const isLoggedIn = computed(() => !!token.value)

  /** 当前用户角色（去掉 ROLE_ 前缀） */
  const role = computed(() => {
    const perms = userInfo.value?.permissions ?? []
    const perm = perms.find(p => p.startsWith('ROLE_')) ?? ''
    return perm.replace('ROLE_', '')
  })

  const isAdmin = computed(() =>
    ['ADMIN', 'SUPER_ADMIN'].includes(role.value),
  )

  const isSuperAdmin = computed(() => role.value === 'SUPER_ADMIN')

  // ── Actions ─────────────────────────────────────────
  async function login(userName: string, password: string) {
    const tokenStr = await authApi.login({ username: userName, password }) as unknown as string
    token.value = tokenStr
    localStorage.setItem('token', tokenStr)
    const user = await authApi.me()
    userInfo.value = user
    localStorage.setItem('userInfo', JSON.stringify(user))
  }

  async function logout() {
    try {
      await authApi.logout()
    } finally {
      clear()
    }
  }

  function clear() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    role,
    isAdmin,
    isSuperAdmin,
    login,
    logout,
    clear,
  }
})
