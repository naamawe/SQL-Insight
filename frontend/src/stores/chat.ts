import { defineStore } from 'pinia'
import { chatApi } from '@/api/chat'
import type { ChatSession } from '@/types'

export const useChatStore = defineStore('chat', () => {
  const sessions = ref<ChatSession[]>([])
  const currentSessionId = ref<number | null>(null)
  const loading = ref(false)
  // 每次需要强制清空聊天界面时自增（用于 null→null 不触发 watch 的场景）
  const clearChatSignal = ref(0)

  async function loadSessions() {
    loading.value = true
    try {
      const res = await chatApi.getSessions(1, 50)
      sessions.value = res.records
    } finally {
      loading.value = false
    }
  }

  async function deleteSession(id: number) {
    await chatApi.deleteSession(id)
    await loadSessions()
    const stillExists = sessions.value.some(s => s.id === currentSessionId.value)
    if (!stillExists) {
      if (currentSessionId.value !== null) {
        currentSessionId.value = null  // 正常触发 watch
      } else {
        clearChatSignal.value++  // currentSessionId 已经是 null，用信号强制触发
      }
    }
  }

  function selectSession(id: number) {
    currentSessionId.value = id
  }

  function startNew() {
    currentSessionId.value = null
  }

  return { sessions, currentSessionId, loading, loadSessions, deleteSession, selectSession, startNew, clearChatSignal }
})
