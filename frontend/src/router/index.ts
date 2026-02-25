import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { requiresAuth: false, title: '登录' },
  },
  {
    path: '/',
    component: () => import('@/views/layout/LayoutView.vue'),
    meta: { requiresAuth: true },
    redirect: '/chat',
    children: [
      {
        path: 'chat',
        name: 'Chat',
        component: () => import('@/views/chat/ChatView.vue'),
        meta: { title: 'AI 对话', icon: 'chat', roles: ['USER', 'ADMIN', 'SUPER_ADMIN'] },
      },
      {
        path: 'datasource',
        name: 'DataSource',
        component: () => import('@/views/datasource/DataSourceView.vue'),
        meta: { title: '数据源管理', icon: 'datasource', roles: ['ADMIN', 'SUPER_ADMIN'] },
      },
      {
        path: 'permission',
        name: 'Permission',
        component: () => import('@/views/permission/PermissionView.vue'),
        meta: { title: '权限管理', icon: 'permission', roles: ['ADMIN', 'SUPER_ADMIN'] },
      },
      {
        path: 'user',
        name: 'User',
        component: () => import('@/views/user/UserView.vue'),
        meta: { title: '用户管理', icon: 'user', roles: ['SUPER_ADMIN'] },
      },
    ],
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/ForbiddenView.vue'),
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/',
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// ── 导航守卫 ────────────────────────────────────────────
router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('token')
  const isLoggedIn = !!token

  // 未登录访问需要认证的路由 → 跳转登录
  if (to.meta.requiresAuth !== false && !isLoggedIn) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
    return
  }

  // 已登录访问登录页 → 跳转首页
  if (to.name === 'Login' && isLoggedIn) {
    next('/')
    return
  }

  // 权限校验
  const userInfoRaw = localStorage.getItem('userInfo')
  if (userInfoRaw && userInfoRaw !== 'null' && userInfoRaw !== 'undefined' && to.meta.roles) {
    try {
      const userInfo = JSON.parse(userInfoRaw)
      const perms: string[] = userInfo.permissions ?? []
      const allowedRoles = to.meta.roles as string[]
      const role = (perms.find(p => p.startsWith('ROLE_')) ?? '').replace('ROLE_', '')
      if (!allowedRoles.includes(role)) {
        next('/403')
        return
      }
    } catch {
      localStorage.removeItem('userInfo')
    }
  }

  next()
})

export default router
