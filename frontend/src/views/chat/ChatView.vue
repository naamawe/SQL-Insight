<script setup lang="ts">
import { ref, reactive, nextTick, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { chatApi, streamChat } from '@/api/chat'
import { dataSourceApi } from '@/api/datasource'
import { useAuthStore } from '@/stores/auth'
import type { ChatSession, ChatMessage, DataSourceVO } from '@/types'

const authStore = useAuthStore()

// ── 会话列表 ──────────────────────────────────────────
const sessions = ref<ChatSession[]>([])
const currentSessionId = ref<number | null>(null)
const sessionsLoading = ref(false)

async function loadSessions() {
  sessionsLoading.value = true
  try {
    const res = await chatApi.getSessions(1, 50)
    sessions.value = res.records
  } finally {
    sessionsLoading.value = false
  }
}

async function selectSession(session: ChatSession) {
  if (currentSessionId.value === session.id) return
  currentSessionId.value = session.id
  currentDataSourceId.value = session.dataSourceId
  messages.value = []
}

async function deleteSession(session: ChatSession, e: Event) {
  e.stopPropagation()
  await ElMessageBox.confirm(`删除会话「${session.title}」？`, '确认', {
    type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消',
  })
  await chatApi.deleteSession(session.id)
  if (currentSessionId.value === session.id) {
    currentSessionId.value = null
    messages.value = []
  }
  loadSessions()
}

function startNewChat() {
  currentSessionId.value = null
  currentDataSourceId.value = null
  messages.value = []
  question.value = ''
}

// ── 数据源选择 ────────────────────────────────────────
const dataSources = ref<DataSourceVO[]>([])
const currentDataSourceId = ref<number | null>(null)

// ── 消息列表 ──────────────────────────────────────────
const messages = ref<ChatMessage[]>([])
const msgListRef = ref<HTMLElement>()

function scrollToBottom() {
  nextTick(() => {
    if (msgListRef.value) {
      msgListRef.value.scrollTop = msgListRef.value.scrollHeight
    }
  })
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
  if (!currentSessionId.value && !currentDataSourceId.value) {
    ElMessage.warning('请先选择一个数据源')
    return
  }

  // 添加用户消息
  const userMsg: ChatMessage = { id: `u-${Date.now()}`, role: 'user', content: q }
  messages.value.push(userMsg)
  question.value = ''
  scrollToBottom()

  // 添加 AI 占位消息
  const aiMsg: ChatMessage = {
    id: `a-${Date.now()}`,
    role: 'ai',
    content: '',
    loading: true,
    stage: '正在思考...',
  }
  messages.value.push(aiMsg)
  sending.value = true
  scrollToBottom()

  const req = {
    question: q,
    sessionId: currentSessionId.value ?? undefined,
    dataSourceId: currentDataSourceId.value ?? undefined,
  }

  abortCtrl = streamChat(req, authStore.token, {
    onStage(msg) {
      aiMsg.stage = msg
      scrollToBottom()
    },
    onSql(sql, corrected) {
      aiMsg.sql = sql
      aiMsg.sqlCorrected = corrected
      aiMsg.stage = undefined
      scrollToBottom()
    },
    onData(rows, total, sessionId) {
      aiMsg.tableData = rows
      aiMsg.total = total
      // 首次对话拿到 sessionId，更新会话
      if (!currentSessionId.value) {
        currentSessionId.value = sessionId
        loadSessions()
      }
      scrollToBottom()
    },
    onSummaryToken(token) {
      aiMsg.content += token
      aiMsg.loading = false
      scrollToBottom()
    },
    onDone() {
      aiMsg.loading = false
      aiMsg.stage = undefined
      sending.value = false
      abortCtrl = null
      loadSessions()
    },
    onError(msg) {
      aiMsg.loading = false
      aiMsg.stage = undefined
      aiMsg.content = msg
      sending.value = false
      abortCtrl = null
    },
  })
}

function stopStream() {
  abortCtrl?.abort()
  abortCtrl = null
  sending.value = false
  const last = messages.value[messages.value.length - 1]
  if (last?.loading) {
    last.loading = false
    last.stage = undefined
    if (!last.content) last.content = '已停止生成'
  }
}

// ── SQL 展开/收起 ─────────────────────────────────────
const expandedSql = reactive<Set<string>>(new Set())
function toggleSql(id: string) {
  if (expandedSql.has(id)) expandedSql.delete(id)
  else expandedSql.add(id)
}

// ── 初始化 ────────────────────────────────────────────
onMounted(async () => {
  const [, dsList] = await Promise.all([loadSessions(), dataSourceApi.list()])
  dataSources.value = dsList
})

onUnmounted(() => { abortCtrl?.abort() })
</script>

<template>
  <div class="chat-layout">
    <!-- ── 左侧会话列表 ── -->
    <aside class="chat-sidebar">
      <div class="sidebar-top">
        <button class="new-chat-btn" @click="startNewChat">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
          新建对话
        </button>
      </div>

      <div class="session-list" v-loading="sessionsLoading">
        <div
          v-for="s in sessions"
          :key="s.id"
          class="session-item"
          :class="{ active: currentSessionId === s.id }"
          @click="selectSession(s)"
        >
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="flex-shrink:0">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
          </svg>
          <span class="session-title">{{ s.title }}</span>
          <button class="session-del" @click="deleteSession(s, $event)">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </div>
        <div v-if="!sessions.length && !sessionsLoading" class="session-empty">暂无对话记录</div>
      </div>
    </aside>

    <!-- ── 主对话区 ── -->
    <div class="chat-main">
      <!-- 消息列表 -->
      <div ref="msgListRef" class="msg-list">
        <!-- 欢迎屏 -->
        <div v-if="!messages.length" class="welcome">
          <div class="welcome-logo">
            <svg width="40" height="40" viewBox="0 0 36 36" fill="none">
              <rect width="36" height="36" rx="10" fill="#D97706"/>
              <path d="M10 13h16M10 18h10M10 23h13" stroke="white" stroke-width="2.5" stroke-linecap="round"/>
            </svg>
          </div>
          <h2 class="welcome-title">SQL Insight</h2>
          <p class="welcome-desc">用自然语言查询你的数据库，AI 帮你生成并执行 SQL</p>
          <div v-if="dataSources.length" class="welcome-hint">
            请在下方选择数据源，然后开始提问
          </div>
          <div v-else class="welcome-hint warn">
            暂无可用数据源，请联系管理员配置
          </div>
        </div>

        <!-- 消息气泡 -->
        <div v-for="msg in messages" :key="msg.id" class="msg-row" :class="msg.role">
          <!-- 用户消息 -->
          <div v-if="msg.role === 'user'" class="bubble user-bubble">
            {{ msg.content }}
          </div>

          <!-- AI 消息 -->
          <div v-else class="bubble ai-bubble">
            <!-- 阶段提示 -->
            <div v-if="msg.loading && msg.stage" class="stage-hint">
              <span class="stage-dot" />
              {{ msg.stage }}
            </div>

            <!-- SQL 块 -->
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

            <!-- 结果表格 -->
            <div v-if="msg.tableData && msg.tableData.length" class="result-table-wrap">
              <div class="result-meta">
                共 {{ msg.total }} 条结果
              </div>
              <el-table :data="msg.tableData" size="small" class="result-table" max-height="300">
                <el-table-column
                  v-for="col in Object.keys(msg.tableData[0])"
                  :key="col"
                  :prop="col"
                  :label="col"
                  min-width="100"
                  show-overflow-tooltip
                />
              </el-table>
            </div>

            <!-- 摘要文字 -->
            <div v-if="msg.content" class="ai-text">{{ msg.content }}</div>

            <!-- 加载动画 -->
            <div v-if="msg.loading && !msg.stage" class="typing-dots">
              <span /><span /><span />
            </div>
          </div>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="input-area">
        <!-- 数据源选择器（新会话时显示） -->
        <div v-if="!currentSessionId" class="ds-selector">
          <el-select
            v-model="currentDataSourceId"
            placeholder="选择数据源"
            size="small"
            style="width: 200px"
          >
            <el-option
              v-for="ds in dataSources"
              :key="ds.id"
              :label="ds.connName"
              :value="ds.id"
            />
          </el-select>
        </div>

        <div class="input-box">
          <textarea
            v-model="question"
            class="input-textarea"
            placeholder="输入你的问题，按 Enter 发送，Shift+Enter 换行..."
            rows="1"
            @keydown="handleKeydown"
          />
          <div class="input-actions">
            <button v-if="sending" class="stop-btn" @click="stopStream">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
                <rect x="6" y="6" width="12" height="12" rx="2"/>
              </svg>
            </button>
            <button v-else class="send-btn" :disabled="!question.trim()" @click="sendMessage">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/>
              </svg>
            </button>
          </div>
        </div>
      </div>
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

.chat-sidebar {
  width: 220px;
  flex-shrink: 0;
  border-right: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  background: var(--color-bg-surface);
}

.sidebar-top {
  padding: 12px 10px;
  border-bottom: 1px solid var(--color-border);
}

.new-chat-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 12px;
  border-radius: var(--radius-md);
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-primary);
  border: 1px solid var(--color-border);
  background: var(--color-bg-input);
  transition: background var(--transition-fast), border-color var(--transition-fast);
}

.new-chat-btn:hover {
  background: var(--color-accent-bg);
  border-color: var(--color-accent);
  color: var(--color-accent);
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 6px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.session-item {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 8px 10px;
  border-radius: var(--radius-md);
  cursor: pointer;
  color: var(--color-text-secondary);
  font-size: 13px;
  transition: background var(--transition-fast), color var(--transition-fast);
}

.session-item:hover { background: var(--color-bg-input); color: var(--color-text-primary); }
.session-item.active { background: var(--color-accent-bg); color: var(--color-accent); }

.session-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-del {
  opacity: 0;
  width: 20px;
  height: 20px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: var(--color-text-secondary);
  transition: opacity var(--transition-fast), background var(--transition-fast);
}

.session-item:hover .session-del { opacity: 1; }
.session-del:hover { background: #fef2f2; color: var(--color-error); }

.session-empty {
  padding: 24px 10px;
  text-align: center;
  font-size: 12px;
  color: var(--color-text-disabled);
}

.chat-main { flex: 1; display: flex; flex-direction: column; overflow: hidden; }

.msg-list {
  flex: 1;
  overflow-y: auto;
  padding: 24px 0;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.welcome {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 24px;
  gap: 12px;
}

.welcome-logo { margin-bottom: 4px; }
.welcome-title { font-size: 22px; font-weight: 700; color: var(--color-text-primary); letter-spacing: -0.5px; }
.welcome-desc { font-size: 14px; color: var(--color-text-secondary); text-align: center; }
.welcome-hint { font-size: 13px; color: var(--color-text-secondary); margin-top: 4px; }
.welcome-hint.warn { color: var(--color-warning); }

.msg-row { display: flex; padding: 0 24px; }
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

.input-area {
  padding: 12px 24px 20px;
  border-top: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: var(--color-bg-surface);
  flex-shrink: 0;
}

.ds-selector { display: flex; align-items: center; gap: 8px; }

.ds-selector :deep(.el-select .el-input__wrapper) {
  box-shadow: none !important;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-input);
}

.input-box {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  background: var(--color-bg-input);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 10px 12px;
  transition: border-color var(--transition-fast);
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
</style>
