<script setup lang="ts">
import { ref, reactive, nextTick, onMounted, onUnmounted, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { streamChat, chatApi } from '@/api/chat'
import { dataSourceApi } from '@/api/datasource'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import type { ChatMessage, DataSourceVO } from '@/types'

const authStore = useAuthStore()
const chatStore = useChatStore()

// ── 数据源选择 ────────────────────────────────────────
const dataSources = ref<DataSourceVO[]>([])
const currentDataSourceId = ref<number | null>(null)
const dsDropdownOpen = ref(false)

const currentDsName = computed(() => {
  if (!currentDataSourceId.value) return null
  return dataSources.value.find(d => d.id === currentDataSourceId.value)?.connName ?? null
})

function selectDs(id: number) {
  currentDataSourceId.value = currentDataSourceId.value === id ? null : id
  dsDropdownOpen.value = false
}

function toggleDsDropdown() {
  dsDropdownOpen.value = !dsDropdownOpen.value
}

// 切换会话时同步数据源
watch(() => chatStore.currentSessionId, async (id) => {
  if (id === null) {
    messages.value = []
    currentDataSourceId.value = null
    question.value = ''
  } else {
    const s = chatStore.sessions.find(s => s.id === id)
    if (s) currentDataSourceId.value = s.dataSourceId
    if (!sending.value) {
      await loadHistory(id)
    }
  }
})

// currentSessionId 已是 null 时删除会话，用独立信号强制清空
watch(() => chatStore.clearChatSignal, () => {
  messages.value = []
  currentDataSourceId.value = null
  question.value = ''
})

// ── 消息列表 ──────────────────────────────────────────
const messages = ref<ChatMessage[]>([])
const msgListRef = ref<HTMLElement>()
const historyLoading = ref(false)

function scrollToBottom() {
  nextTick(() => {
    if (msgListRef.value) {
      msgListRef.value.scrollTop = msgListRef.value.scrollHeight
    }
  })
}

async function loadHistory(sessionId: number) {
  historyLoading.value = true
  messages.value = []
  try {
    const records = (await chatApi.getSessionRecords(sessionId)) as unknown as any[]
    for (const r of records) {
      messages.value.push({ id: `u-${r.id}`, role: 'user', content: r.question })
      messages.value.push({
        id: `a-${r.id}`,
        role: 'ai',
        content: r.summary ?? '',
        sql: r.sqlText,
        sqlCorrected: r.corrected,
        tableData: r.resultData ?? undefined,
        total: r.rowTotal,
        recordId: r.id,
        resultExpired: r.resultExpired,
      })
    }
    scrollToBottom()
  } finally {
    historyLoading.value = false
  }
}

async function rerunExpired(msg: ChatMessage) {
  if (!msg.recordId) return
  try {
    const result: any = await chatApi.rerunRecord(msg.recordId)
    msg.tableData = result.data
    msg.content = result.summary
    msg.total = result.total
    msg.resultExpired = false
  } catch {
    ElMessage.error({ message: '重新执行失败', duration: 2000 })
  }
}

// ── 输入 ──────────────────────────────────────────────
const question = ref('')
const sending = ref(false)
let abortCtrl: AbortController | null = null

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}

async function sendMessage() {
  const q = question.value.trim()
  if (!q || sending.value) return
  if (!chatStore.currentSessionId && !currentDataSourceId.value) {
    ElMessage.warning({ message: '请先选择一个数据源', duration: 2000 })
    return
  }

  const userMsg: ChatMessage = { id: `u-${Date.now()}`, role: 'user', content: q }
  messages.value.push(userMsg)
  question.value = ''
  scrollToBottom()

  const aiMsg: ChatMessage = { id: `a-${Date.now()}`, role: 'ai', content: '', loading: true, stage: '正在思考...' }
  messages.value.push(aiMsg)
  sending.value = true
  scrollToBottom()

  const req = {
    question: q,
    sessionId: chatStore.currentSessionId ?? undefined,
    dataSourceId: currentDataSourceId.value ?? undefined,
  }

  abortCtrl = streamChat(req, authStore.token, {
    onStage(msg) { aiMsg.stage = msg; scrollToBottom() },
    onSql(sql, corrected) { aiMsg.sql = sql; aiMsg.sqlCorrected = corrected; aiMsg.stage = undefined; scrollToBottom() },
    onData(rows, total, sessionId) {
      aiMsg.tableData = rows
      aiMsg.total = total
      if (!chatStore.currentSessionId) {
        chatStore.selectSession(sessionId)
        chatStore.loadSessions()
      }
      scrollToBottom()
    },
    onSummaryToken(token) { aiMsg.content += token; aiMsg.loading = false; scrollToBottom() },
    onDone() { aiMsg.loading = false; aiMsg.stage = undefined; sending.value = false; abortCtrl = null; chatStore.loadSessions() },
    onError(msg) { aiMsg.loading = false; aiMsg.stage = undefined; aiMsg.content = msg; sending.value = false; abortCtrl = null },
  })
}

function stopStream() {
  abortCtrl?.abort()
  abortCtrl = null
  sending.value = false
  const last = messages.value[messages.value.length - 1]
  if (last?.loading) { last.loading = false; last.stage = undefined; if (!last.content) last.content = '已停止生成' }
}

// ── SQL 展开/收起 ─────────────────────────────────────
const expandedSql = reactive<Set<string>>(new Set())
function toggleSql(id: string) {
  if (expandedSql.has(id)) expandedSql.delete(id)
  else expandedSql.add(id)
}

// ── 初始化 ────────────────────────────────────────────
onMounted(async () => {
  // 先恢复历史记录，避免闪欢迎界面
  const sid = chatStore.currentSessionId
  if (sid !== null && !sending.value) {
    loadHistory(sid)  // 不 await，让历史加载和数据源加载并行
  }
  const fetchDs = authStore.isAdmin ? dataSourceApi.list() : dataSourceApi.myList()
  const [, dsList] = await Promise.all([chatStore.loadSessions(), fetchDs])
  dataSources.value = dsList as any
  document.addEventListener('mousedown', handleOutsideClick)
})

onUnmounted(() => {
  abortCtrl?.abort()
  document.removeEventListener('mousedown', handleOutsideClick)
})

function handleOutsideClick(e: MouseEvent) {
  const wrap = document.querySelector('.ds-pill-wrap')
  if (wrap && !wrap.contains(e.target as Node)) dsDropdownOpen.value = false
}
</script>

<template>
  <div class="chat-layout">
    <!-- ── 主对话区 ── -->
    <div class="chat-main">

      <!-- ── 空状态：居中欢迎屏 ── -->
      <Transition name="welcome">
        <div v-if="historyLoading" class="history-loading">
          <div class="loading-spinner" />
        </div>
        <div v-else-if="!messages.length && !chatStore.currentSessionId" class="welcome-screen">
          <div class="welcome-logo">
            <svg width="44" height="44" viewBox="0 0 36 36" fill="none">
              <rect width="36" height="36" rx="10" fill="#D97706"/>
              <path d="M10 13h16M10 18h10M10 23h13" stroke="white" stroke-width="2.5" stroke-linecap="round"/>
            </svg>
          </div>
          <h2 class="welcome-title">SQL Insight</h2>
          <p class="welcome-desc">用自然语言查询你的数据库，AI 帮你生成并执行 SQL</p>

          <!-- 居中输入框 -->
          <div class="welcome-input-wrap">
            <div class="input-box input-box--welcome">
              <textarea
                v-model="question"
                class="input-textarea welcome-textarea"
                placeholder="输入你的问题..."
                rows="1"
                @keydown="handleKeydown"
              />
              <div class="input-toolbar">
                <!-- 自定义数据源胶囊按钮 -->
                <div class="ds-pill-wrap">
                  <button class="ds-pill" :class="{ active: currentDataSourceId }" @click="toggleDsDropdown">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3"/><path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"/>
                    </svg>
                    <span>{{ currentDsName ?? '选择数据源' }}</span>
                    <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" :style="{ transform: dsDropdownOpen ? 'rotate(180deg)' : '', transition: 'transform 0.15s' }">
                      <polyline points="6 9 12 15 18 9"/>
                    </svg>
                  </button>
                  <div v-if="dsDropdownOpen" class="ds-dropdown">
                    <div v-if="!dataSources.length" class="ds-dropdown-empty">暂无数据源</div>
                    <button
                      v-for="ds in dataSources"
                      :key="ds.id"
                      class="ds-dropdown-item"
                      :class="{ selected: currentDataSourceId === ds.id }"
                      @click="selectDs(ds.id)"
                    >
                      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3"/><path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"/>
                      </svg>
                      {{ ds.connName }}
                      <svg v-if="currentDataSourceId === ds.id" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" style="margin-left:auto">
                        <polyline points="20 6 9 17 4 12"/>
                      </svg>
                    </button>
                  </div>
                </div>
                <div style="flex:1" />
                <button class="send-btn" :disabled="!question.trim() || !currentDataSourceId" @click="sendMessage">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/>
                  </svg>
                </button>
              </div>
            </div>
          </div>
        </div>
      </Transition>

      <!-- ── 有消息时：正常对话布局 ── -->
      <Transition name="chat">
        <div v-if="messages.length" class="chat-content">
          <!-- 消息列表 -->
          <div ref="msgListRef" class="msg-list">
            <div v-for="msg in messages" :key="msg.id" class="msg-row" :class="msg.role">
              <div v-if="msg.role === 'user'" class="bubble user-bubble">{{ msg.content }}</div>
              <div v-else class="bubble ai-bubble">
                <div v-if="msg.loading && msg.stage" class="stage-hint">
                  <span class="stage-dot" />{{ msg.stage }}
                </div>
                <div v-if="msg.sql" class="sql-block">
                  <div class="sql-header" @click="toggleSql(msg.id)">
                    <div class="sql-header-left">
                      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3"/><path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"/>
                      </svg>
                      <span>生成的 SQL</span>
                      <span v-if="msg.sqlCorrected" class="corrected-tag">已修正</span>
                    </div>
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" :style="{ transform: expandedSql.has(msg.id) ? 'rotate(180deg)' : '', transition: 'transform 0.2s' }">
                      <polyline points="6 9 12 15 18 9"/>
                    </svg>
                  </div>
                  <pre v-if="expandedSql.has(msg.id)" class="sql-code">{{ msg.sql }}</pre>
                </div>
                <div v-if="msg.tableData && msg.tableData.length" class="result-table-wrap">
                  <div class="result-meta">共 {{ msg.total }} 条结果</div>
                  <el-table :data="msg.tableData" size="small" class="result-table" max-height="300">
                    <el-table-column v-for="col in Object.keys(msg.tableData[0] || {})" :key="col" :prop="col" :label="col" min-width="100" show-overflow-tooltip />
                  </el-table>
                </div>
                <div v-else-if="msg.resultExpired && msg.sql" class="expired-hint">
                  数据已过期
                  <button class="rerun-btn" @click="rerunExpired(msg)">重新执行</button>
                </div>
                <div v-if="msg.content" class="ai-text">{{ msg.content }}</div>
                <div v-if="msg.loading && !msg.stage" class="typing-dots"><span /><span /><span /></div>
              </div>
            </div>
          </div>

          <!-- 底部输入区 -->
          <div class="input-area-outer">
          <div class="input-area">
            <div class="input-box">
              <textarea v-model="question" class="input-textarea" placeholder="输入你的问题，按 Enter 发送，Shift+Enter 换行..." rows="1" @keydown="handleKeydown" />
              <div class="input-toolbar">
                <!-- 数据源胶囊（新会话时显示选择器，已有会话时显示 badge） -->
                <div v-if="!chatStore.currentSessionId" class="ds-pill-wrap">
                  <button class="ds-pill" :class="{ active: currentDataSourceId }" @click="toggleDsDropdown">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3"/><path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"/>
                    </svg>
                    <span>{{ currentDsName ?? '选择数据源' }}</span>
                    <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" :style="{ transform: dsDropdownOpen ? 'rotate(180deg)' : '', transition: 'transform 0.15s' }">
                      <polyline points="6 9 12 15 18 9"/>
                    </svg>
                  </button>
                  <div v-if="dsDropdownOpen" class="ds-dropdown">
                    <div v-if="!dataSources.length" class="ds-dropdown-empty">暂无数据源</div>
                    <button v-for="ds in dataSources" :key="ds.id" class="ds-dropdown-item" :class="{ selected: currentDataSourceId === ds.id }" @click="selectDs(ds.id)">
                      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3"/><path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"/>
                      </svg>
                      {{ ds.connName }}
                      <svg v-if="currentDataSourceId === ds.id" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" style="margin-left:auto">
                        <polyline points="20 6 9 17 4 12"/>
                      </svg>
                    </button>
                  </div>
                </div>
                <div v-else-if="currentDsName" class="ds-badge">
                  <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3"/><path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"/>
                  </svg>
                  {{ currentDsName }}
                </div>
                <div style="flex:1" />
                <button v-if="sending" class="stop-btn" @click="stopStream">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="6" width="12" height="12" rx="2"/></svg>
                </button>
                <button v-else class="send-btn" :disabled="!question.trim() || (!chatStore.currentSessionId && !currentDataSourceId)" @click="sendMessage">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/>
                  </svg>
                </button>
              </div>
            </div>
          </div>
          </div>
        </div>
      </Transition>
    </div>
  </div>
</template>

<style scoped>
.chat-layout {
  display: flex;
  height: 100%;
  overflow: hidden;
  background: var(--color-bg-primary);
}

.chat-main {
  flex: 1;
  position: relative;
  overflow: hidden;
  background: var(--color-bg-primary);
}

/* ── 历史加载中 ── */
.history-loading {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
.loading-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid var(--color-border);
  border-top-color: var(--color-accent);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* ── 欢迎屏：绝对定位铺满，内容自己居中 ── */
.welcome-screen {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  padding: 15vh 24px 40px;
  overflow-y: auto;
  gap: 14px;
}

/* ── 对话内容：绝对定位铺满，flex column ── */
.chat-content {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ── 欢迎屏过渡 ── */
.welcome-enter-active {
  transition: opacity 0.4s ease, transform 0.4s ease;
}
.welcome-enter-from {
  opacity: 0;
  transform: translateY(14px);
}
.welcome-leave-active {
  transition: opacity 0.22s ease, transform 0.22s ease;
  pointer-events: none;
}
.welcome-leave-to {
  opacity: 0;
  transform: translateY(-18px) scale(0.97);
}

/* ── 对话区过渡 ── */
.chat-enter-active {
  transition: opacity 0.3s ease 0.15s, transform 0.3s ease 0.15s;
}
.chat-enter-from {
  opacity: 0;
  transform: translateY(16px);
}

.welcome-logo { margin-bottom: 4px; }
.welcome-title { font-size: 24px; font-weight: 700; color: var(--color-text-primary); letter-spacing: -0.5px; margin: 0; }
.welcome-desc { font-size: 14px; color: var(--color-text-secondary); text-align: center; margin: 0; }
.welcome-hint { font-size: 13px; color: var(--color-text-secondary); }
.welcome-hint.warn { color: var(--color-warning); }

.welcome-ds {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
}

.welcome-ds :deep(.el-select .el-input__wrapper) {
  box-shadow: none !important;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-input);
}

.welcome-input-wrap {
  width: 100%;
  max-width: 640px;
  margin-top: 8px;
}

.input-box--welcome {
  flex-direction: column;
  align-items: stretch;
  gap: 0;
  padding: 0;
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border-strong);
  box-shadow: var(--shadow-md);
}

.welcome-textarea {
  padding: 14px 16px 8px;
  width: 100%;
  box-sizing: border-box;
  flex: none;
  min-height: 52px;
  background: var(--color-bg-surface);
}

.input-toolbar {
  display: flex;
  align-items: center;
  padding: 6px 10px 8px;
  gap: 6px;
  width: 100%;
  box-sizing: border-box;
  border-top: 1px solid var(--color-border);
  background: transparent;
  border-radius: 0 0 var(--radius-lg) var(--radius-lg);
}

.ds-pill-wrap {
  position: relative;
}

.ds-pill {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px 4px 8px;
  border-radius: var(--radius-full);
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  font-size: 12px;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: border-color var(--transition-fast), background var(--transition-fast), color var(--transition-fast);
  white-space: nowrap;
  max-width: 200px;
}

.ds-pill span {
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 140px;
}

.ds-pill:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
  background: var(--color-accent-bg);
}

.ds-pill.active {
  border-color: var(--color-accent);
  color: var(--color-accent);
  background: var(--color-accent-bg);
}

.ds-dropdown {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  min-width: 180px;
  max-height: 220px;
  overflow-y: auto;
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-lg);
  padding: 4px;
  z-index: 100;
}

.ds-dropdown-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 7px 10px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  color: var(--color-text-primary);
  cursor: pointer;
  transition: background var(--transition-fast);
  text-align: left;
}

.ds-dropdown-item:hover { background: var(--color-bg-input); }
.ds-dropdown-item.selected { color: var(--color-accent); font-weight: 500; }
.ds-dropdown-item.selected svg { color: var(--color-accent); }

.ds-dropdown-empty {
  padding: 10px 12px;
  font-size: 12px;
  color: var(--color-text-disabled);
  text-align: center;
}

.input-ds-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px 8px;
}

.input-divider {
  height: 1px;
  background: var(--color-border);
  margin: 0;
}

.input-row {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 8px 12px 10px;
}

.ds-inline-select {
  flex: 1;
}

.ds-inline-select :deep(.el-input__wrapper) {
  box-shadow: none !important;
  border: none !important;
  background: transparent !important;
  padding: 0 4px !important;
}

.ds-inline-select :deep(.el-input__inner) {
  font-size: 13px;
  color: var(--color-text-secondary);
  height: 24px;
}

.ds-inline-select :deep(.el-input__suffix) {
  color: var(--color-text-disabled);
}

.msg-list {
  flex: 1;
  overflow-y: auto;
  padding: 24px 0;
  display: flex;
  flex-direction: column;
  gap: 20px;
  width: 100%;
}

.msg-row { display: flex; padding: 0 24px; max-width: 800px; width: 100%; margin: 0 auto; box-sizing: border-box; }
.msg-row.user { justify-content: flex-end; }
.msg-row.ai   { justify-content: flex-start; }
.bubble { max-width: 72%; }

.user-bubble {
  background: var(--color-bg-user-msg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg) var(--radius-lg) var(--radius-sm) var(--radius-lg);
  padding: 10px 14px;
  font-size: 14px;
  color: var(--color-text-primary);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.ai-bubble { display: flex; flex-direction: column; gap: 10px; min-width: 200px; }

.stage-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--color-text-secondary);
  padding: 6px 0;
}

.stage-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-accent);
  animation: pulse 1.2s ease-in-out infinite;
  flex-shrink: 0;
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50%       { opacity: 0.4; transform: scale(0.8); }
}

.sql-block {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--color-bg-surface);
}

.sql-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  cursor: pointer;
  background: var(--color-bg-input);
  transition: background var(--transition-fast);
}

.sql-header:hover { background: var(--color-border); }

.sql-header-left {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-secondary);
}

.corrected-tag {
  padding: 1px 6px;
  border-radius: var(--radius-full);
  background: #fef3c7;
  color: var(--color-accent);
  font-size: 10px;
  font-weight: 600;
}

.sql-code {
  padding: 12px 14px;
  font-family: var(--font-mono);
  font-size: 12px;
  color: #e8e4de;
  background: var(--color-bg-code);
  overflow-x: auto;
  margin: 0;
  white-space: pre;
  line-height: 1.6;
}

.result-table-wrap {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.result-meta {
  padding: 6px 12px;
  font-size: 11px;
  color: var(--color-text-secondary);
  background: var(--color-bg-input);
  border-bottom: 1px solid var(--color-border);
}

.result-table :deep(.el-table__header th) {
  background: var(--color-bg-input);
  font-size: 12px;
  color: var(--color-text-secondary);
}

.result-table :deep(.el-table__body td) {
  font-size: 12px;
  font-family: var(--font-mono);
}

.ai-text {
  font-size: 14px;
  color: var(--color-text-primary);
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.typing-dots { display: flex; gap: 4px; padding: 6px 0; }

.typing-dots span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-text-disabled);
  animation: bounce 1.2s ease-in-out infinite;
}

.typing-dots span:nth-child(2) { animation-delay: 0.2s; }
.typing-dots span:nth-child(3) { animation-delay: 0.4s; }

@keyframes bounce {
  0%, 80%, 100% { transform: translateY(0); }
  40%           { transform: translateY(-6px); }
}

.input-area-outer {
  background: var(--color-bg-primary);
  flex-shrink: 0;
  padding: 12px 24px 20px;
}

.input-area {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: 800px;
  width: 100%;
  margin: 0 auto;
  box-sizing: border-box;
}

.ds-selector { display: flex; align-items: center; gap: 8px; }

.ds-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 10px 3px 8px;
  border-radius: var(--radius-full);
  border: 1px solid var(--color-border);
  background: var(--color-accent-bg);
  color: var(--color-accent);
  font-size: 12px;
}

.ds-selector :deep(.el-select .el-input__wrapper) {
  box-shadow: none !important;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-input);
}

.input-box {
  display: flex;
  flex-direction: column;
  gap: 0;
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-lg);
  padding: 0;
  transition: border-color var(--transition-fast);
  box-shadow: var(--shadow-md);
}

.input-box:focus-within { border-color: var(--color-accent); }

.input-textarea {
  flex: 1;
  resize: none;
  border: none;
  outline: none;
  background: transparent;
  font-size: 14px;
  color: var(--color-text-primary);
  font-family: var(--font-sans);
  line-height: 1.6;
  max-height: 160px;
  padding: 12px 14px 8px;
  width: 100%;
  box-sizing: border-box;
  overflow-y: auto;
}

.input-textarea::placeholder { color: var(--color-text-disabled); }
.input-actions { display: flex; align-items: center; flex-shrink: 0; }

.send-btn {
  width: 34px;
  height: 34px;
  border-radius: var(--radius-md);
  background: var(--color-accent);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background var(--transition-fast), transform var(--transition-fast);
}

.send-btn:hover:not(:disabled) { background: var(--color-accent-light); transform: translateY(-1px); }
.send-btn:disabled { background: var(--color-text-disabled); cursor: not-allowed; }

.stop-btn {
  width: 34px;
  height: 34px;
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  color: var(--color-text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background var(--transition-fast);
}

.stop-btn:hover { background: #fef2f2; color: var(--color-error); border-color: var(--color-error); }

.expired-hint {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: var(--color-text-secondary);
  padding: 8px 0;
}

.rerun-btn {
  font-size: 12px;
  padding: 3px 10px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-accent);
  color: var(--color-accent);
  background: transparent;
  cursor: pointer;
  transition: background var(--transition-fast);
}
.rerun-btn:hover { background: var(--color-accent-bg); }
</style>
