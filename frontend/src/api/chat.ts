import http from '@/utils/http'
import type { ChatSession } from '@/types'
import type { PageResult } from '@/types'

export interface SqlChatRequest {
  sessionId?: number
  dataSourceId?: number
  question: string
}

export const chatApi = {
  getSessions: (current = 1, size = 20) =>
    http.get<PageResult<ChatSession>>('/ai/sessions', { params: { current, size } }),

  deleteSession: (sessionId: number) =>
    http.delete<void>(`/ai/sessions/${sessionId}`),

  renameSession: (sessionId: number, title: string) =>
    http.put<void>(`/ai/sessions/${sessionId}/title`, null, { params: { title } }),

  batchDeleteSessions: (sessionIds: number[]) =>
    http.delete<void>('/ai/sessions/batch', { data: sessionIds }),
}

/**
 * SSE 流式对话
 * 后端使用 POST + text/event-stream，前端用 fetch 读取流
 */
export function streamChat(
  req: SqlChatRequest,
  token: string,
  handlers: {
    onStage?: (message: string) => void
    onSql?: (sql: string, corrected?: boolean) => void
    onData?: (rows: Record<string, unknown>[], total: number, sessionId: number) => void
    onSummaryToken?: (token: string) => void
    onDone?: () => void
    onError?: (message: string) => void
  },
): AbortController {
  const ctrl = new AbortController()

  fetch('/api/ai/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(req),
    signal: ctrl.signal,
  }).then(async (res) => {
    if (!res.ok || !res.body) {
      handlers.onError?.(`HTTP ${res.status}`)
      return
    }
    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let buf = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buf += decoder.decode(value, { stream: true })

      // 按 SSE 规范解析：每个事件以 \n\n 分隔
      const parts = buf.split('\n\n')
      buf = parts.pop() ?? ''

      for (const part of parts) {
        let eventType = 'message'
        let dataStr = ''
        for (const line of part.split('\n')) {
          if (line.startsWith('event:')) eventType = line.slice(6).trim()
          else if (line.startsWith('data:')) dataStr = line.slice(5).trim()
        }
        if (!dataStr) continue
        try {
          const payload = JSON.parse(dataStr)
          switch (eventType) {
            case 'stage':   handlers.onStage?.(payload.message); break
            case 'sql':     handlers.onSql?.(payload.sql, payload.corrected); break
            case 'data':    handlers.onData?.(payload.rows, payload.total, payload.sessionId); break
            case 'summary': handlers.onSummaryToken?.(payload.token); break
            case 'done':    handlers.onDone?.(); break
            case 'error':   handlers.onError?.(payload.message); break
          }
        } catch { /* ignore parse errors */ }
      }
    }
  }).catch((err) => {
    if (err.name !== 'AbortError') handlers.onError?.(err.message)
  })

  return ctrl
}
