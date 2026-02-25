<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { authApi } from '@/api/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

// ── Tab 切换 ──────────────────────────────────────────
type Mode = 'login' | 'register'
const mode = ref<Mode>('login')

// ── 表单数据 ──────────────────────────────────────────
const loginForm = reactive({ userName: '', password: '' })
const registerForm = reactive({ userName: '', password: '', confirmPassword: '' })

// ── 加载状态 ──────────────────────────────────────────
const loading = ref(false)

// ── 表单校验 ──────────────────────────────────────────
const loginRules = {
  userName: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}
const registerRules = {
  userName: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度 3~20 位', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    {
      validator: (_: unknown, value: string, callback: (e?: Error) => void) => {
        if (value !== registerForm.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

const loginFormRef = ref()
const registerFormRef = ref()

// ── 登录 ──────────────────────────────────────────────
async function handleLogin() {
  await loginFormRef.value?.validate()
  loading.value = true
  try {
    await authStore.login(loginForm.userName, loginForm.password)
    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
  } finally {
    loading.value = false
  }
}

// ── 注册 ──────────────────────────────────────────────
async function handleRegister() {
  await registerFormRef.value?.validate()
  loading.value = true
  try {
    await authApi.register({
      username: registerForm.userName,
      password: registerForm.password,
    })
    ElMessage.success('注册成功，请登录')
    // 注册完切回登录 Tab，并回填用户名
    mode.value = 'login'
    loginForm.userName = registerForm.userName
    loginForm.password = ''
    registerForm.userName = ''
    registerForm.password = ''
    registerForm.confirmPassword = ''
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <!-- 背景装饰 -->
    <div class="bg-decoration" />

    <!-- 登录卡片 -->
    <div class="login-card">
      <!-- Logo & 标题 -->
      <div class="card-header">
        <div class="logo">
          <svg width="36" height="36" viewBox="0 0 36 36" fill="none">
            <rect width="36" height="36" rx="10" fill="#D97706" />
            <path d="M10 13h16M10 18h10M10 23h13" stroke="white" stroke-width="2.5"
                  stroke-linecap="round" />
          </svg>
        </div>
        <h1 class="app-name">SQL Insight</h1>
        <p class="app-desc">用自然语言查询你的数据库</p>
      </div>

      <!-- Tab 切换 -->
      <div class="tab-switch">
        <button
          class="tab-btn"
          :class="{ active: mode === 'login' }"
          @click="mode = 'login'"
        >
          登录
        </button>
        <button
          class="tab-btn"
          :class="{ active: mode === 'register' }"
          @click="mode = 'register'"
        >
          注册
        </button>
        <div class="tab-indicator" :class="{ right: mode === 'register' }" />
      </div>

      <!-- 登录表单 -->
      <el-form
        v-if="mode === 'login'"
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        class="auth-form"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="userName">
          <el-input
            v-model="loginForm.userName"
            placeholder="用户名"
            size="large"
            :prefix-icon="'User'"
            autocomplete="username"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="密码"
            size="large"
            :prefix-icon="'Lock'"
            show-password
            autocomplete="current-password"
          />
        </el-form-item>
        <el-button
          type="primary"
          size="large"
          class="submit-btn"
          :loading="loading"
          @click="handleLogin"
        >
          登录
        </el-button>
      </el-form>

      <!-- 注册表单 -->
      <el-form
        v-else
        ref="registerFormRef"
        :model="registerForm"
        :rules="registerRules"
        class="auth-form"
        @keyup.enter="handleRegister"
      >
        <el-form-item prop="userName">
          <el-input
            v-model="registerForm.userName"
            placeholder="用户名（3~20位）"
            size="large"
            :prefix-icon="'User'"
            autocomplete="username"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="registerForm.password"
            type="password"
            placeholder="密码（至少6位）"
            size="large"
            :prefix-icon="'Lock'"
            show-password
            autocomplete="new-password"
          />
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <el-input
            v-model="registerForm.confirmPassword"
            type="password"
            placeholder="确认密码"
            size="large"
            :prefix-icon="'Lock'"
            show-password
            autocomplete="new-password"
          />
        </el-form-item>
        <el-button
          type="primary"
          size="large"
          class="submit-btn"
          :loading="loading"
          @click="handleRegister"
        >
          注册
        </el-button>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
/* ── 整体页面 ── */
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--color-bg-primary);
  position: relative;
  overflow: hidden;
}

/* 背景光晕装饰 */
.bg-decoration {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse 600px 400px at 20% 30%, rgba(217, 119, 6, 0.08) 0%, transparent 70%),
    radial-gradient(ellipse 500px 300px at 80% 70%, rgba(217, 119, 6, 0.05) 0%, transparent 70%);
  pointer-events: none;
}

/* ── 登录卡片 ── */
.login-card {
  position: relative;
  width: 400px;
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 40px;
  box-shadow: var(--shadow-lg);
}

/* ── 头部 ── */
.card-header {
  text-align: center;
  margin-bottom: 32px;
}

.logo {
  display: inline-flex;
  margin-bottom: 16px;
}

.app-name {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-text-primary);
  letter-spacing: -0.5px;
  margin-bottom: 6px;
}

.app-desc {
  font-size: 14px;
  color: var(--color-text-secondary);
}

/* ── Tab 切换 ── */
.tab-switch {
  position: relative;
  display: flex;
  background: var(--color-bg-input);
  border-radius: var(--radius-md);
  padding: 4px;
  margin-bottom: 28px;
}

.tab-btn {
  flex: 1;
  height: 36px;
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-secondary);
  border-radius: calc(var(--radius-md) - 2px);
  position: relative;
  z-index: 1;
  transition: color var(--transition-fast);
}

.tab-btn.active {
  color: var(--color-text-primary);
}

.tab-indicator {
  position: absolute;
  top: 4px;
  left: 4px;
  width: calc(50% - 4px);
  height: 36px;
  background: var(--color-bg-surface);
  border-radius: calc(var(--radius-md) - 2px);
  box-shadow: var(--shadow-sm);
  transition: transform var(--transition-base);
}

.tab-indicator.right {
  transform: translateX(calc(100% + 0px));
}

/* ── 表单 ── */
.auth-form {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

/* 覆盖 Element Plus 输入框样式 */
.auth-form :deep(.el-input__wrapper) {
  background: var(--color-bg-input);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  box-shadow: none !important;
  transition: border-color var(--transition-fast);
}

.auth-form :deep(.el-input__wrapper:hover),
.auth-form :deep(.el-input__wrapper.is-focus) {
  border-color: var(--color-accent);
}

.submit-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
  font-weight: 600;
  border-radius: var(--radius-md);
  margin-top: 8px;
  background: var(--color-accent);
  border-color: var(--color-accent);
  letter-spacing: 0.5px;
  transition: background var(--transition-fast), transform var(--transition-fast);
}

.submit-btn:hover {
  background: var(--color-accent-light);
  border-color: var(--color-accent-light);
  transform: translateY(-1px);
}

.submit-btn:active {
  transform: translateY(0);
}
</style>
