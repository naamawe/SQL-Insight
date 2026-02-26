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
  { name: 'Chat',       path: '/chat',       label: 'AI 对话',   roles: ['USER', 'ADMIN', 'SUPER_ADMIN'] },
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

// 进入 /chat 时加载会话列表
watch(isChat, (val) => {
  if (val) chatStore.loadSessions()
}, { immediate: true })

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
        <!-- 收起状态：logo 图标本身作为展开按钮，hover 时显示箭头 -->
        <button v-if="collapsed" class="logo-collapse-btn" title="展开" @click="collapsed = false">
          <svg class="logo-icon" width="28" height="28" viewBox="0 0 36 36" fill="none">
            <rect width="36" height="36" rx="10" fill="#D97706" />
            <path d="M10 13h16M10 18h10M10 23h13" stroke="white" stroke-width="2.5" stroke-linecap="round" />
          </svg>
          <svg class="expand-icon" width="28" height="28" viewBox="0 0 36 36" fill="none">
            <rect width="36" height="36" rx="10" fill="#D97706" />
            <polyline points="13 24 19 18 13 12" stroke="white" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </button>
        <!-- 展开状态：正常显示 logo + 文字 + 收起按钮 -->
        <template v-else>
          <svg width="28" height="28" viewBox="0 0 36 36" fill="none" style="flex-shrink:0">
            <rect width="36" height="36" rx="10" fill="#D97706" />
            <path d="M10 13h16M10 18h10M10 23h13" stroke="white" stroke-width="2.5" stroke-linecap="round" />
          </svg>
          <span class="logo-text">SQL Insight</span>
          <button class="collapse-btn" title="收起" @click="collapsed = true">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="15 18 9 12 15 6"/>
            </svg>
          </button>
        </template>
      </div>

      <!-- 导航菜单 -->
      <nav class="sidebar-nav">
        <router-link
          v-for="menu in menus"
          :key="menu.name"
          :to="menu.path"
          class="nav-item"
          :class="{ active: activeMenu === menu.path }"
          :title="collapsed ? menu.label : ''"
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
          <span v-show="!collapsed">{{ menu.label }}</span>
        </router-link>

        <!-- 会话列表（仅 /chat 时显示，展开状态） -->
        <template v-if="isChat && !collapsed">
          <div class="session-section">
            <div class="session-section-header">
              <span>对话记录</span>
              <button class="new-chat-icon-btn" title="新建对话" @click="handleNewChat">
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
                  <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
                </svg>
              </button>
            </div>
            <div class="session-list">
              <div
                v-for="s in chatStore.sessions"
                :key="s.id"
                class="session-item"
                :class="{ active: chatStore.currentSessionId === s.id }"
                @click="handleSelectSession(s)"
              >
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="flex-shrink:0">
                  <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
                </svg>
                <span class="session-title">{{ s.title }}</span>
                <button class="session-del" @click="handleDeleteSession(s, $event)">
                  <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                    <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
                  </svg>
                </button>
              </div>
              <div v-if="!chatStore.sessions.length && !chatStore.loading" class="session-empty">暂无对话记录</div>
            </div>
          </div>
        </template>
      </nav>

      <!-- 底部用户信息 -->
      <div class="sidebar-footer">
        <div
          class="user-info"
          :class="{ clickable: true }"
          :title="collapsed ? authStore.userInfo?.username : ''"
          @click="toggleUserMenu"
        >
          <div class="user-avatar">
            {{ authStore.userInfo?.username?.charAt(0).toUpperCase() }}
          </div>
          <div v-show="!collapsed" class="user-meta">
            <span class="user-name">{{ authStore.userInfo?.username }}</span>
            <span class="user-role">{{ authStore.role }}</span>
          </div>
          <svg v-show="!collapsed" class="user-chevron" :class="{ open: showUserMenu }" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
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
    <main class="main-content">
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
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: var(--color-bg-primary);
}

/* ── 侧边栏 ── */
.sidebar {
  width: var(--sidebar-width);
  flex-shrink: 0;
  background: var(--color-bg-sidebar);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: width 0.22s ease;
}

.sidebar.collapsed {
  width: 56px;
}

.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 14px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  min-height: 65px;
}

.sidebar.collapsed .sidebar-logo {
  justify-content: center;
  padding: 20px 8px 16px;
}

.logo-text {
  flex: 1;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-sidebar);
  letter-spacing: -0.3px;
  white-space: nowrap;
  overflow: hidden;
}

.collapse-btn {
  width: 24px;
  height: 24px;
  border-radius: var(--radius-sm);
  color: var(--color-text-sidebar-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: background var(--transition-fast), color var(--transition-fast);
}

.collapse-btn:hover {
  background: var(--color-bg-sidebar-hover);
  color: var(--color-text-sidebar);
}

/* 收起状态的 logo 按钮 */
.logo-collapse-btn {
  width: 28px;
  height: 28px;
  position: relative;
  flex-shrink: 0;
  border-radius: 10px;
}

.logo-collapse-btn .logo-icon,
.logo-collapse-btn .expand-icon {
  position: absolute;
  top: 0;
  left: 0;
  transition: opacity 0.15s;
}

.logo-collapse-btn .expand-icon {
  opacity: 0;
}

.logo-collapse-btn:hover .logo-icon {
  opacity: 0;
}

.logo-collapse-btn:hover .expand-icon {
  opacity: 1;
}

/* ── 导航 ── */
.sidebar-nav {
  flex: 1;
  padding: 12px 8px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  overflow-y: auto;
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
}

.new-chat-icon-btn {
  width: 20px;
  height: 20px;
  border-radius: var(--radius-sm);
  color: var(--color-text-sidebar-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background var(--transition-fast), color var(--transition-fast);
}

.new-chat-icon-btn:hover {
  background: var(--color-bg-sidebar-hover);
  color: var(--color-text-sidebar);
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
  align-items: center;
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

.session-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-del {
  opacity: 0;
  width: 18px;
  height: 18px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: var(--color-text-sidebar-muted);
  transition: opacity var(--transition-fast), background var(--transition-fast);
}

.session-item:hover .session-del { opacity: 1; }
.session-del:hover { background: rgba(220, 38, 38, 0.2); color: #fca5a5; }

.session-empty {
  padding: 16px 8px;
  text-align: center;
  font-size: 11px;
  color: var(--color-text-sidebar-muted);
}

.nav-item {
  display: flex;
  align-items: center;
  justify-content: center;
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

.sidebar:not(.collapsed) .nav-item {
  justify-content: flex-start;
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
  gap: 8px;
}

.sidebar.collapsed .sidebar-footer {
  justify-content: center;
}

.user-info {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 8px;
  border-radius: var(--radius-md);
  min-width: 0;
}

.sidebar.collapsed .user-info {
  flex: none;
  padding: 6px;
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
  transition: transform var(--transition-fast);
  margin-left: auto;
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
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

/* ── 退出确认 dialog ── */
.confirm-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.3);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 8vh;
  z-index: 9999;
}

.confirm-dialog {
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 24px;
  width: 320px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  box-shadow: var(--shadow-lg);
}

.confirm-icon {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  background: #fef3c7;
  color: var(--color-accent);
  display: flex;
  align-items: center;
  justify-content: center;
}

.confirm-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0;
}

.confirm-desc {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin: 2px 0 0;
}

.confirm-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  margin-top: 4px;
}

.confirm-cancel {
  padding: 7px 16px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  color: var(--color-text-secondary);
  background: var(--color-bg-input);
  border: 1px solid var(--color-border);
  transition: background var(--transition-fast);
}

.confirm-cancel:hover {
  background: var(--color-border);
  color: var(--color-text-primary);
}

.confirm-ok {
  padding: 7px 16px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  font-weight: 500;
  background: var(--color-error);
  color: white;
  transition: opacity var(--transition-fast);
}

.confirm-ok:hover {
  opacity: 0.88;
}
</style>
