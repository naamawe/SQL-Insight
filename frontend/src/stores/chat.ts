import { defineStore } from 'pinia'
import { ref } from 'vue'
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
      console.log('loadSessions 返回数据:', res)
      console.log('res 的类型:', typeof res)
      console.log('res 是否为数组:', Array.isArray(res))

      // 兼容两种返回格式：直接数组 或 PageResult 对象
      if (Array.isArray(res)) {
        sessions.value = res
      } else if (res && res.records) {
        sessions.value = res.records
      } else {
        console.warn('未知的返回格式:', res)
        sessions.value = []
      }

      console.log('最终 sessions.value:', sessions.value)
    } catch (e) {
      console.error('加载会话列表失败:', e)
      sessions.value = []
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
