<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const chatStore = useChatStore()

const collapsed = ref(false)
const showLogoutDialog = ref(false)
const showDeleteDialog = ref(false)
const sessionToDelete = ref<any>(null)
const showUserMenu = ref(false)

function toggleUserMenu() {
  showUserMenu.value = !showUserMenu.value
}

function closeUserMenu() {
  showUserMenu.value = false
}

function openProfile() {
  showUserMenu.value = false
  router.push('/profile')
}

// ── 菜单项（根据角色过滤） ──────────────────────────────
const allMenus = [
  { name: 'DataSource', path: '/datasource', label: '数据源管理', roles: ['ADMIN', 'SUPER_ADMIN'] },
  { name: 'Permission', path: '/permission', label: '权限管理',   roles: ['ADMIN', 'SUPER_ADMIN'] },
  { name: 'User',       path: '/user',       label: '用户管理',   roles: ['SUPER_ADMIN'] },
  { name: 'Role',       path: '/role',       label: '角色管理',   roles: ['SUPER_ADMIN'] },
]

const menus = computed(() =>
  allMenus.filter(m => m.roles.includes(authStore.role))
)

const activeMenu = computed(() => route.path)
const isChat = computed(() => route.path === '/chat')

// 始终加载会话列表
watch(isChat, (val) => {
  if (val) {
    console.log('进入 chat 页面，加载会话列表')
    chatStore.loadSessions()
  }
}, { immediate: true })

// 非 chat 页面也加载一次会话列表（用于侧边栏显示）
console.log('LayoutView 初始化，加载会话列表')
chatStore.loadSessions()

// 监听 sessions 变化
watch(() => chatStore.sessions, (newSessions) => {
  console.log('会话列表更新:', newSessions)
  console.log('会话数量:', newSessions.length)
}, { immediate: true, deep: true })

function handleDeleteSession(session: any, e: Event) {
  e.stopPropagation()
  sessionToDelete.value = session
  showDeleteDialog.value = true
}

async function confirmDeleteSession() {
  showDeleteDialog.value = false
  if (sessionToDelete.value) {
    await chatStore.deleteSession(sessionToDelete.value.id)
    sessionToDelete.value = null
  }
}

function handleSelectSession(session: any) {
  chatStore.selectSession(session.id)
  router.push('/chat')
}

function handleNewChat() {
  chatStore.startNew()
  router.push('/chat')
}

// ── 退出登录 ───────────────────────────────────────────
async function confirmLogout() {
  showLogoutDialog.value = false
  await authStore.logout()
  router.push('/login')
}
</script>

<template>
  <div class="app-layout">
    <!-- ── 侧边栏 ── -->
    <aside class="sidebar" :class="{ collapsed }">
      <!-- Logo -->
      <div class="sidebar-logo">
        <!-- logo 图标：收起时 hover 显示展开箭头，展开时 hover 显示提示 -->
        <div class="logo-icon-wrap"
          :data-tooltip="collapsed ? '打开侧栏' : ''"
          @click="collapsed ? (collapsed = false) : handleNewChat()">
          <svg class="logo-svg logo-svg--default" width="28" height="28" viewBox="0 0 36 36" fill="none">
            <rect width="36" height="36" rx="10" fill="#D97706" />
            <path d="M10 13h16M10 18h10M10 23h13" stroke="white" stroke-width="2.5" stroke-linecap="round" />
          </svg>
          <svg class="logo-svg logo-svg--expand" width="28" height="28" viewBox="0 0 36 36" fill="none">
            <rect width="36" height="36" rx="10" fill="#D97706" />
            <polyline points="13 24 19 18 13 12" stroke="white" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
        <!-- 展开时显示的文字和收起按钮 -->
        <span class="logo-text" @click="handleNewChat">SQL Insight</span>
        <button class="collapse-btn" @click="collapsed = true" title="收起">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="15 18 9 12 15 6"/>
          </svg>
        </button>
      </div>

      <!-- 新建对话按钮 -->
      <div class="new-chat-wrap">
        <button class="new-chat-btn" @click="handleNewChat" :data-tooltip="collapsed ? '新建对话' : ''">
          <span class="new-chat-inner">
            <svg class="new-chat-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
              <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
            </svg>
            <span class="new-chat-label">新建对话</span>
          </span>
        </button>
      </div>

      <!-- 导航菜单 -->
      <nav class="sidebar-nav">
        <router-link
          v-for="menu in menus"
          :key="menu.name"
          :to="menu.path"
          class="nav-item"
          :class="{ active: activeMenu === menu.path }"
          :data-tooltip="collapsed ? menu.label : ''"
        >
          <!-- Chat 图标 -->
          <svg v-if="menu.name === 'Chat'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="flex-shrink:0">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
          </svg>
          <!-- DataSource 图标 -->
          <svg v-else-if="menu.name === 'DataSource'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="flex-shrink:0">
            <ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3"/><path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"/>
          </svg>
          <!-- Permission 图标 -->
          <svg v-else-if="menu.name === 'Permission'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="flex-shrink:0">
            <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/>
          </svg>
          <!-- User 图标 -->
          <svg v-else-if="menu.name === 'User'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="flex-shrink:0">
            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>
          </svg>
          <!-- Role 图标 -->
          <svg v-else-if="menu.name === 'Role'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="flex-shrink:0">
            <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/>
          </svg>
          <span>{{ menu.label }}</span>
        </router-link>

        <!-- 会话列表 -->
        <div class="session-section">
          <div class="session-section-header">
            <span>对话记录</span>
          </div>
          <TransitionGroup tag="div" name="session-item" class="session-list">
            <div
              v-for="s in chatStore.sessions"
              :key="s.id"
              class="session-item"
              :class="{ active: chatStore.currentSessionId === s.id && isChat }"
              @click="handleSelectSession(s)"
            >
              <div class="session-content">
                <div class="session-header">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="flex-shrink:0">
                    <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
                  </svg>
                  <span class="session-title">{{ s.title }}</span>
                </div>
                <span class="session-datasource">{{ s.dataSourceName }}</span>
              </div>
              <button class="session-del" @click="handleDeleteSession(s, $event)">
                <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                  <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
                </svg>
              </button>
            </div>
            <div v-if="!chatStore.sessions.length && !chatStore.loading" key="empty" class="session-empty">暂无对话记录</div>
          </TransitionGroup>
        </div>
      </nav>

      <!-- 底部用户信息 -->
      <div class="sidebar-footer">
        <div
          class="user-info"
          :class="{ clickable: true }"
          :data-tooltip="collapsed ? authStore.userInfo?.username : ''"
          @click="toggleUserMenu"
        >
          <div class="user-avatar">
            {{ authStore.userInfo?.username?.charAt(0).toUpperCase() }}
          </div>
          <div class="user-meta">
            <span class="user-name">{{ authStore.userInfo?.username }}</span>
            <span class="user-role">{{ authStore.role }}</span>
          </div>
          <svg class="user-chevron" :class="{ open: showUserMenu }" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="18 15 12 9 6 15"/>
          </svg>
        </div>

        <!-- 用户弹出菜单 -->
        <teleport to="body">
          <div v-if="showUserMenu" class="user-menu-mask" @click="closeUserMenu" />
          <div v-if="showUserMenu" class="user-menu">
            <div class="user-menu-header">
              <div class="user-menu-avatar">{{ authStore.userInfo?.username?.charAt(0).toUpperCase() }}</div>
              <div class="user-menu-info">
                <span class="user-menu-name">{{ authStore.userInfo?.username }}</span>
                <span class="user-menu-role">{{ authStore.role }}</span>
              </div>
            </div>
            <div class="user-menu-divider" />
            <button class="user-menu-item" @click="openProfile">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>
              </svg>
              个人中心
            </button>
            <button class="user-menu-item danger" @click="() => { closeUserMenu(); showLogoutDialog = true }">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/>
              </svg>
              退出登录
            </button>
          </div>
        </teleport>
      </div>
    </aside>

    <!-- ── 主内容区 ── -->
    <main class="main-content" :style="{ marginLeft: collapsed ? '56px' : 'var(--sidebar-width)' }">
      <router-view />
    </main>

    <!-- ── 删除会话确认 dialog ── -->
    <teleport to="body">
      <div v-if="showDeleteDialog" class="confirm-mask" @click.self="showDeleteDialog = false">
        <div class="confirm-dialog">
          <div class="confirm-icon" style="background:#fee2e2;color:#dc2626">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/><path d="M9 6V4h6v2"/>
            </svg>
          </div>
          <div class="confirm-body">
            <p class="confirm-title">删除对话</p>
            <p class="confirm-desc">确定要删除「{{ sessionToDelete?.title }}」吗？此操作不可撤销。</p>
          </div>
          <div class="confirm-actions">
            <button class="confirm-cancel" @click="showDeleteDialog = false">取消</button>
            <button class="confirm-ok" @click="confirmDeleteSession">删除</button>
          </div>
        </div>
      </div>
    </teleport>

    <!-- ── 退出确认 dialog ── -->
    <teleport to="body">
      <div v-if="showLogoutDialog" class="confirm-mask" @click.self="showLogoutDialog = false">
        <div class="confirm-dialog">
          <div class="confirm-icon">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/>
            </svg>
          </div>
          <div class="confirm-body">
            <p class="confirm-title">退出登录</p>
            <p class="confirm-desc">确定要退出当前账号吗？</p>
          </div>
          <div class="confirm-actions">
            <button class="confirm-cancel" @click="showLogoutDialog = false">取消</button>
            <button class="confirm-ok" @click="confirmLogout">退出</button>
          </div>
        </div>
      </div>
    </teleport>
  </div>
</template>

<style scoped>
.app-layout {
  height: 100vh;
  overflow: hidden;
  background: var(--color-bg-primary);
}

/* ── 侧边栏：fixed 定位，完全脱离文档流 ── */
.sidebar {
  position: fixed;
  top: 0;
  left: 0;
  height: 100vh;
  width: var(--sidebar-width);
  background: var(--color-bg-sidebar);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: width 0.22s ease;
  z-index: 100;
}

.sidebar.collapsed {
  width: 56px;
}
.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 65px;
  padding: 0 14px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  flex-shrink: 0;
  overflow: hidden;
}

/* logo 图标：flex-shrink:0，始终在 padding-left:14px 处，不动 */
.logo-icon-wrap {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  position: relative;
  cursor: pointer;
  border-radius: 10px;
}

/* 两个 svg 叠在一起，默认显示 logo，hover 时切换为展开箭头 */
.logo-svg {
  position: absolute;
  top: 0; left: 0;
  transition: opacity 0.15s ease;
}
.logo-svg--default { opacity: 1; }
.logo-svg--expand  { opacity: 0; }

/* 收起状态：鼠标悬停整个侧边栏时切换 logo 为展开图标 */
.sidebar.collapsed:hover .logo-svg--default { opacity: 0; }
.sidebar.collapsed:hover .logo-svg--expand  { opacity: 1; }

/* 展开状态 logo 图标 hover 微微放大 */
.sidebar:not(.collapsed) .logo-icon-wrap:hover {
  transform: scale(1.08);
  transition: transform 0.18s ease;
}

/* logo tooltip（收起时 hover 显示"打开侧栏"） */
.logo-icon-wrap[data-tooltip]:not([data-tooltip=""]) {
  position: relative;
}
.logo-icon-wrap[data-tooltip]:not([data-tooltip=""])::after {
  content: attr(data-tooltip);
  position: fixed;
  left: 62px;
  background: rgba(30, 30, 35, 0.95);
  color: #fff;
  font-size: 12px;
  font-weight: 500;
  padding: 5px 10px;
  border-radius: 6px;
  white-space: nowrap;
  pointer-events: none;
  opacity: 0;
  transform: translateX(-4px);
  transition: opacity 0.12s ease, transform 0.12s ease;
  box-shadow: 0 2px 8px rgba(0,0,0,0.3);
  z-index: 9999;
}
.logo-icon-wrap[data-tooltip]:not([data-tooltip=""]):hover::after {
  opacity: 1;
  transform: translateX(0);
}

.logo-text {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-sidebar);
  letter-spacing: -0.3px;
  white-space: nowrap;
  cursor: pointer;
  overflow: hidden;
  max-width: 120px;
  opacity: 1;
  transition: max-width 0.22s ease, opacity 0.18s ease;
}

.sidebar.collapsed .logo-text {
  max-width: 0;
  opacity: 0;
  pointer-events: none;
}

.collapse-btn {
  margin-left: auto;
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  border-radius: var(--radius-sm);
  color: var(--color-text-sidebar-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  max-width: 24px;
  opacity: 1;
  overflow: hidden;
  transition: background var(--transition-fast), color var(--transition-fast),
              max-width 0.22s ease, opacity 0.18s ease;
}
.collapse-btn:hover {
  background: var(--color-bg-sidebar-hover);
  color: var(--color-text-sidebar);
}
.sidebar.collapsed .collapse-btn {
  max-width: 0;
  opacity: 0;
  pointer-events: none;
}

/* ── 新建对话 ── */
.new-chat-wrap {
  padding: 8px 10px;
  display: flex;
  overflow: hidden;
  transition: padding 0.22s ease;
}
.sidebar.collapsed .new-chat-wrap {
  padding: 8px;
}
.new-chat-btn {
  display: flex;
  align-items: center;
  gap: 0;
  padding: 7px 12px;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border-sidebar, rgba(255,255,255,0.12));
  background: transparent;
  color: var(--color-text-sidebar);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  overflow: hidden;
  white-space: nowrap;
  width: 100%;
  justify-content: center;
  transition: background var(--transition-fast);
}
.new-chat-btn:hover { background: var(--color-bg-sidebar-hover); }
.new-chat-btn[data-tooltip]:not([data-tooltip=""]) { position: relative; }
.new-chat-btn[data-tooltip]:not([data-tooltip=""])::after {
  content: attr(data-tooltip);
  position: fixed;
  left: 62px;
  background: rgba(30, 30, 35, 0.95);
  color: #fff;
  font-size: 12px;
  font-weight: 500;
  padding: 5px 10px;
  border-radius: 6px;
  white-space: nowrap;
  pointer-events: none;
  opacity: 0;
  transform: translateX(-4px);
  transition: opacity 0.12s ease, transform 0.12s ease;
  box-shadow: 0 2px 8px rgba(0,0,0,0.3);
  z-index: 9999;
}
.new-chat-btn[data-tooltip]:not([data-tooltip=""]):hover::after {
  opacity: 1;
  transform: translateX(0);
}
.new-chat-icon {
  flex-shrink: 0;
}
.new-chat-inner {
  display: flex;
  align-items: center;
  gap: 8px;
  transition: gap 0.22s ease;
}
.new-chat-label {
  overflow: hidden;
  max-width: 120px;
  opacity: 1;
  transition: max-width 0.22s ease, opacity 0.15s ease;
}
.sidebar.collapsed .new-chat-label {
  max-width: 0;
  opacity: 0;
}
.sidebar.collapsed .new-chat-btn {
  padding: 7px;
}
.sidebar.collapsed .new-chat-inner {
  gap: 0;
}
.sidebar.collapsed .new-chat-icon {
  margin-left: 0;
}

/* ── 导航 ── */
.sidebar-nav {
  flex: 1;
  padding: 12px 8px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  overflow-y: auto;
  overflow-x: hidden;
  min-height: 0;
}

.session-section {
  margin-top: 8px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  padding-top: 8px;
  display: flex;
  flex-direction: column;
  max-height: 320px;
  min-height: 0;
  opacity: 1;
  visibility: visible;
  transition: opacity 0.2s ease, visibility 0.2s ease;
  overflow: hidden;
}

.sidebar.collapsed .session-section {
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
}

.session-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 8px 6px;
  font-size: 11px;
  font-weight: 600;
  color: var(--color-text-sidebar-muted);
  text-transform: uppercase;
  letter-spacing: 0.6px;
  opacity: 1;
  transition: opacity 0.2s ease;
}

/* ── CSS Tooltip（无延迟，比 title 好看）── */
.nav-item[data-tooltip]:not([data-tooltip=""]) {
  position: relative;
}
.nav-item[data-tooltip]:not([data-tooltip=""])::after {
  content: attr(data-tooltip);
  position: fixed;
  left: 62px;
  background: rgba(30, 30, 35, 0.95);
  color: #fff;
  font-size: 12px;
  font-weight: 500;
  padding: 5px 10px;
  border-radius: 6px;
  white-space: nowrap;
  pointer-events: none;
  opacity: 0;
  transform: translateX(-4px);
  transition: opacity 0.12s ease, transform 0.12s ease;
  box-shadow: 0 2px 8px rgba(0,0,0,0.3);
  z-index: 9999;
}
.nav-item[data-tooltip]:not([data-tooltip=""]):hover::after {
  opacity: 1;
  transform: translateX(0);
}

.session-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.session-item {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  padding: 7px 8px;
  border-radius: var(--radius-md);
  cursor: pointer;
  color: var(--color-text-sidebar-muted);
  font-size: 12px;
  transition: background var(--transition-fast), color var(--transition-fast);
}

.session-item:hover {
  background: var(--color-bg-sidebar-hover);
  color: var(--color-text-sidebar);
}

.session-item.active {
  background: var(--color-bg-sidebar-active);
  color: var(--color-text-sidebar);
}

.session-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  overflow: hidden;
  min-width: 0;
}

.session-header {
  display: flex;
  align-items: center;
  gap: 7px;
  overflow: hidden;
}

.session-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-datasource {
  font-size: 10px;
  color: var(--color-text-sidebar-muted);
  opacity: 0.7;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding-left: 19px;
}

.session-del {
  opacity: 0;
  width: 18px;
  height: 18px;
  border-radius: var(--radius-full);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: var(--color-text-sidebar-muted);
  transition: opacity var(--transition-fast), background var(--transition-fast), color var(--transition-fast);
}

.session-item:hover .session-del { opacity: 1; }
.session-del:hover { background: rgba(220, 38, 38, 0.18); color: #fca5a5; }

.session-empty {
  padding: 16px 8px;
  text-align: center;
  font-size: 11px;
  color: var(--color-text-sidebar-muted);
}

/* ── 会话列表增删动画 ── */
/* 删除：向左淡出 + 高度收缩（让下方条目顶上来） */
.session-item-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease,
              max-height 0.25s ease, padding-top 0.25s ease, padding-bottom 0.25s ease;
  overflow: hidden;
  max-height: 40px;
}
.session-item-leave-to {
  opacity: 0;
  transform: translateX(-8px);
  max-height: 0;
  padding-top: 0;
  padding-bottom: 0;
}

/* 留下的条目平滑位移顶上来 */
.session-item-move {
  transition: transform 0.25s ease;
}

/* 新增：从左淡入（新建会话时） */
.session-item-enter-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.session-item-enter-from {
  opacity: 0;
  transform: translateX(-6px);
}

.nav-item {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 10px;
  padding: 9px 12px;
  border-radius: var(--radius-md);
  font-size: 14px;
  color: var(--color-text-sidebar-muted);
  text-decoration: none;
  transition: background var(--transition-fast), color var(--transition-fast);
  white-space: nowrap;
  overflow: hidden;
}

.nav-item span {
  opacity: 1;
  max-width: 160px;
  transition: opacity 0.15s ease, max-width 0.22s ease;
  overflow: hidden;
}

.sidebar.collapsed .nav-item span {
  opacity: 0;
  max-width: 0;
}

.nav-item:hover {
  background: var(--color-bg-sidebar-hover);
  color: var(--color-text-sidebar);
}

.nav-item.active {
  background: var(--color-bg-sidebar-active);
  color: var(--color-text-sidebar);
}

/* ── 底部用户区 ── */
.sidebar-footer {
  padding: 12px 8px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 2px;
  border-radius: var(--radius-md);
  min-width: 0;
  transition: background var(--transition-fast);
  overflow: hidden;
  white-space: nowrap;
}

.sidebar:not(.collapsed) .user-info {
  flex: 1;
  padding-right: 12px;
}

.sidebar.collapsed .user-info {
  flex: none;
}

/* user-info tooltip（收起时 hover 显示用户名） */
.user-info[data-tooltip]:not([data-tooltip=""]) {
  position: relative;
}
.user-info[data-tooltip]:not([data-tooltip=""])::after {
  content: attr(data-tooltip);
  position: fixed;
  left: 62px;
  background: rgba(30, 30, 35, 0.95);
  color: #fff;
  font-size: 12px;
  font-weight: 500;
  padding: 5px 10px;
  border-radius: 6px;
  white-space: nowrap;
  pointer-events: none;
  opacity: 0;
  transform: translateX(-4px);
  transition: opacity 0.12s ease, transform 0.12s ease;
  box-shadow: 0 2px 8px rgba(0,0,0,0.3);
  z-index: 9999;
}
.user-info[data-tooltip]:not([data-tooltip=""]):hover::after {
  opacity: 1;
  transform: translateX(0);
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-full);
  background: var(--color-accent);
  color: white;
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.user-meta {
  display: flex;
  flex-direction: column;
  min-width: 0;
  opacity: 1;
  max-width: 160px;
  transition: opacity 0.15s ease, max-width 0.22s ease;
  overflow: hidden;
}

.sidebar.collapsed .user-meta {
  opacity: 0;
  max-width: 0;
}

.user-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-sidebar);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-role {
  font-size: 11px;
  color: var(--color-text-sidebar-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.logout-btn {
  display: none;
}

/* ── 用户信息区（可点击） ── */
.user-info.clickable {
  cursor: pointer;
  flex: 1;
  transition: background var(--transition-fast);
  border-radius: var(--radius-md);
}

.user-info.clickable:hover {
  background: var(--color-bg-sidebar-hover);
}

.user-chevron {
  flex-shrink: 0;
  color: var(--color-text-sidebar-muted);
  transition: transform var(--transition-fast), opacity 0.15s ease, max-width 0.22s ease;
  opacity: 1;
  max-width: 12px;
  overflow: hidden;
  margin-left: auto;
}

.sidebar.collapsed .user-chevron {
  opacity: 0;
  max-width: 0;
}

.user-chevron.open {
  transform: rotate(180deg);
}

/* ── 用户弹出菜单 ── */
.user-menu-mask {
  position: fixed;
  inset: 0;
  z-index: 9998;
}

.user-menu {
  position: fixed;
  bottom: 72px;
  left: 12px;
  width: 220px;
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  z-index: 9999;
  overflow: hidden;
  animation: menu-in 0.15s ease;
}

@keyframes menu-in {
  from { opacity: 0; transform: translateY(6px); }
  to   { opacity: 1; transform: translateY(0); }
}

.user-menu-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 14px 12px;
}

.user-menu-avatar {
  width: 34px;
  height: 34px;
  border-radius: var(--radius-full);
  background: var(--color-accent);
  color: white;
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.user-menu-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.user-menu-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-menu-role {
  font-size: 11px;
  color: var(--color-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.4px;
}

.user-menu-divider {
  height: 1px;
  background: var(--color-border);
  margin: 0 10px;
}

.user-menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px 14px;
  font-size: 13px;
  color: var(--color-text-secondary);
  text-align: left;
  transition: background var(--transition-fast), color var(--transition-fast);
}

.user-menu-item:hover {
  background: var(--color-bg-input);
  color: var(--color-text-primary);
}

.user-menu-item.danger:hover {
  background: #fef2f2;
  color: var(--color-error);
}

/* ── 主内容 ── */
.main-content {
  transition: margin-left 0.22s ease;
  height: 100vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

/* ── 退出确认 dialog ── */
.confirm-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  backdrop-filter: blur(2px);
}

.confirm-dialog {
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: 20px;
  padding: 28px 28px 24px;
  width: 360px;
  display: flex;
  flex-direction: column;
  gap: 0;
  box-shadow: 0 20px 60px rgba(0,0,0,0.18), 0 4px 16px rgba(0,0,0,0.1);
  animation: dialog-in 0.18s ease;
}

@keyframes dialog-in {
  from { opacity: 0; transform: scale(0.95) translateY(8px); }
  to   { opacity: 1; transform: scale(1) translateY(0); }
}

.confirm-icon {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-full);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
}

.confirm-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0 0 6px;
}

.confirm-desc {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin: 0 0 20px;
  line-height: 1.5;
}

.confirm-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.confirm-cancel {
  padding: 8px 18px;
  border-radius: var(--radius-full);
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-secondary);
  background: var(--color-bg-input);
  border: 1px solid var(--color-border);
  cursor: pointer;
  transition: background var(--transition-fast), color var(--transition-fast);
}

.confirm-cancel:hover {
  background: var(--color-border);
  color: var(--color-text-primary);
}

.confirm-ok {
  padding: 8px 18px;
  border-radius: var(--radius-full);
  font-size: 13px;
  font-weight: 500;
  background: #dc2626;
  color: white;
  border: none;
  cursor: pointer;
  transition: opacity var(--transition-fast);
}

.confirm-ok:hover {
  opacity: 0.88;
}
</style>
