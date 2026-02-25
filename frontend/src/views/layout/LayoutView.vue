<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const collapsed = ref(false)

// ── 菜单项（根据角色过滤） ──────────────────────────────
const allMenus = [
  { name: 'Chat',       path: '/chat',       label: 'AI 对话',   roles: ['USER', 'ADMIN', 'SUPER_ADMIN'] },
  { name: 'DataSource', path: '/datasource', label: '数据源管理', roles: ['ADMIN', 'SUPER_ADMIN'] },
  { name: 'Permission', path: '/permission', label: '权限管理',   roles: ['ADMIN', 'SUPER_ADMIN'] },
  { name: 'User',       path: '/user',       label: '用户管理',   roles: ['SUPER_ADMIN'] },
]

const menus = computed(() =>
  allMenus.filter(m => m.roles.includes(authStore.role))
)

const activeMenu = computed(() => route.path)

// ── 退出登录 ───────────────────────────────────────────
async function handleLogout() {
  await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
    confirmButtonText: '退出',
    cancelButtonText: '取消',
    type: 'warning',
  })
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
          <span v-show="!collapsed">{{ menu.label }}</span>
        </router-link>
      </nav>

      <!-- 底部用户信息 -->
      <div class="sidebar-footer">
        <div class="user-info" :title="collapsed ? authStore.userInfo?.username : ''">
          <div class="user-avatar">
            {{ authStore.userInfo?.username?.charAt(0).toUpperCase() }}
          </div>
          <div v-show="!collapsed" class="user-meta">
            <span class="user-name">{{ authStore.userInfo?.username }}</span>
            <span class="user-role">{{ authStore.role }}</span>
          </div>
        </div>
        <button v-show="!collapsed" class="logout-btn" title="退出登录" @click="handleLogout">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/>
          </svg>
        </button>
      </div>
    </aside>

    <!-- ── 主内容区 ── -->
    <main class="main-content">
      <router-view />
    </main>
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
  width: 32px;
  height: 32px;
  border-radius: var(--radius-md);
  color: var(--color-text-sidebar-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: background var(--transition-fast), color var(--transition-fast);
}

.logout-btn:hover {
  background: var(--color-bg-sidebar-hover);
  color: var(--color-error);
}

/* ── 主内容 ── */
.main-content {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
</style>
