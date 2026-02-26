import { defineStore } from 'pinia'
import { chatApi } from '@/api/chat'
import type { ChatSession } from '@/types'

export const useChatStore = defineStore('chat', () => {
  const sessions = ref<ChatSession[]>([])
  const currentSessionId = ref<number | null>(null)
  const loading = ref(false)

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
    if (currentSessionId.value === id) {
      currentSessionId.value = null
    }
    await loadSessions()
  }

  function selectSession(id: number) {
    currentSessionId.value = id
  }

  function startNew() {
    currentSessionId.value = null
  }

  return { sessions, currentSessionId, loading, loadSessions, deleteSession, selectSession, startNew }
})
