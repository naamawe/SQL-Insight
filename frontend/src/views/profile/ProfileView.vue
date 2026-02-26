<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { userAuthApi, rolePermissionApi } from '@/api/permission'
import { dataSourceApi } from '@/api/datasource'
import { userApi } from '@/api/user'
import type { DataSourceVO } from '@/types'

const router = useRouter()
const authStore = useAuthStore()

const dsList = ref<DataSourceVO[]>([])
const tableMap = ref<Record<number, string[]>>({})
const loading = ref(false)

// ── 修改密码 ──────────────────────────────────────────
const showPwdForm = ref(false)
const pwdSubmitting = ref(false)
const pwdSuccess = ref(false)
const pwdError = ref('')
const pwdForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })

function openPwdForm() {
  pwdForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
  pwdError.value = ''
  pwdSuccess.value = false
  showPwdForm.value = true
}

async function handleChangePassword() {
  const { oldPassword, newPassword, confirmPassword } = pwdForm.value
  if (!oldPassword || !newPassword || !confirmPassword) { pwdError.value = '请填写所有字段'; return }
  if (newPassword.length < 6) { pwdError.value = '新密码至少 6 位'; return }
  if (newPassword !== confirmPassword) { pwdError.value = '两次密码不一致'; return }
  pwdError.value = ''
  pwdSubmitting.value = true
  try {
    await userApi.updatePassword({ oldPassword, newPassword })
    showPwdForm.value = false
    pwdSuccess.value = true
    setTimeout(() => { pwdSuccess.value = false }, 3000)
  } catch (e: any) {
    pwdError.value = e?.response?.data?.message ?? '修改失败，请检查旧密码'
  } finally {
    pwdSubmitting.value = false
  }
}

onMounted(async () => {
  loading.value = true
  try {
    const [myIds, allDs, me] = await Promise.all([
      userAuthApi.getMyAuthorizedIds(),
      dataSourceApi.list(),
      userApi.me(),
    ]) as any[]
    dsList.value = (allDs as DataSourceVO[]).filter((ds: DataSourceVO) => (myIds as number[]).includes(ds.id))
    if (me?.roleId) {
      tableMap.value = (await rolePermissionApi.getSummary(me.roleId) as any) ?? {}
    }
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="profile-page">
    <button class="back-btn" @click="router.back()">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
        <polyline points="15 18 9 12 15 6"/>
      </svg>
      返回
    </button>
    <div class="profile-container" v-loading="loading">

      <!-- 顶部 header -->
      <div class="profile-hero">
        <div class="hero-avatar">{{ authStore.userInfo?.username?.charAt(0).toUpperCase() }}</div>
        <div class="hero-info">
          <h1 class="hero-name">{{ authStore.userInfo?.username }}</h1>
          <span class="hero-role-badge">{{ authStore.role }}</span>
        </div>
      </div>

      <div class="profile-grid">

        <!-- 账号信息卡片 -->
        <div class="profile-card">
          <div class="card-title">账号信息</div>
          <div class="info-row">
            <span class="info-label">用户名</span>
            <span class="info-value">{{ authStore.userInfo?.username }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">角色</span>
            <span class="info-value">{{ authStore.role }}</span>
          </div>
        </div>

        <!-- 安全设置卡片 -->
        <div class="profile-card">
          <div class="card-title">安全设置</div>
          <div v-if="pwdSuccess" class="pwd-success">密码修改成功 ✓</div>
          <div v-else-if="!showPwdForm" class="info-row" style="border-bottom:none">
            <span class="info-label">登录密码</span>
            <button class="pwd-change-btn" @click="openPwdForm">修改密码</button>
          </div>
          <div v-else class="pwd-form">
            <div class="pwd-field">
              <label>当前密码</label>
              <input v-model="pwdForm.oldPassword" type="password" placeholder="请输入当前密码" class="pwd-input" />
            </div>
            <div class="pwd-field">
              <label>新密码</label>
              <input v-model="pwdForm.newPassword" type="password" placeholder="至少 6 位" class="pwd-input" />
            </div>
            <div class="pwd-field">
              <label>确认新密码</label>
              <input v-model="pwdForm.confirmPassword" type="password" placeholder="再次输入新密码" class="pwd-input" />
            </div>
            <div v-if="pwdError" class="pwd-error">{{ pwdError }}</div>
            <div class="pwd-actions">
              <button class="pwd-cancel" @click="showPwdForm = false">取消</button>
              <button class="pwd-submit" :disabled="pwdSubmitting" @click="handleChangePassword">
                {{ pwdSubmitting ? '提交中...' : '确认修改' }}
              </button>
            </div>
          </div>
        </div>

        <!-- 授权数据源卡片 -->
        <div class="profile-card full-width">
          <div class="card-title">授权数据源</div>
          <div v-if="loading" class="ds-empty">加载中...</div>
          <div v-else-if="!dsList.length" class="ds-empty">暂无授权数据源</div>
          <div v-else class="ds-grid">
            <div v-for="ds in dsList" :key="ds.id" class="ds-card">
              <div class="ds-card-header">
                <span class="ds-type-badge" :style="{ background: ds.dbType === 'mysql' ? '#00758f' : ds.dbType === 'postgresql' ? '#336791' : '#cc2927' }">
                  {{ ds.dbType.toUpperCase() }}
                </span>
                <div class="ds-name-group">
                  <span class="ds-name">{{ ds.connName }}</span>
                  <span class="ds-host">{{ ds.host }}:{{ ds.port }}/{{ ds.databaseName }}</span>
                </div>
              </div>
              <div class="ds-tables">
                <template v-if="tableMap[ds.id]?.length">
                  <span v-for="t in tableMap[ds.id]" :key="t" class="table-tag">{{ t }}</span>
                </template>
                <span v-else class="no-tables">无表权限限制（可访问全部表）</span>
              </div>
            </div>
          </div>
        </div>

      </div>
    </div>
  </div>
</template>

<style scoped>
.profile-page {
  min-height: 100%;
  background: var(--color-bg-base);
  padding: 32px;
  overflow-y: auto;
}

.profile-container {
  max-width: 860px;
  margin: 0 auto;
}

/* Hero */
.profile-hero {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 28px;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--color-text-secondary);
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  cursor: pointer;
  padding: 6px 14px;
  border-radius: var(--radius-full);
  margin-bottom: 20px;
  width: fit-content;
  transition: background var(--transition-fast), color var(--transition-fast), border-color var(--transition-fast);
}
.back-btn:hover {
  background: var(--color-bg-input);
  color: var(--color-text-primary);
  border-color: var(--color-text-secondary);
}

.hero-avatar {
  width: 72px;
  height: 72px;
  border-radius: var(--radius-full);
  background: var(--color-accent);
  color: #fff;
  font-size: 28px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.hero-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.hero-name {
  font-size: 22px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0;
}

.hero-role-badge {
  display: inline-block;
  padding: 3px 10px;
  border-radius: var(--radius-full);
  background: color-mix(in srgb, var(--color-accent) 12%, transparent);
  color: var(--color-accent);
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  width: fit-content;
}

/* Grid */
.profile-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.full-width { grid-column: 1 / -1; }

/* Card */
.profile-card {
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 20px 24px;
}

.card-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-disabled);
  text-transform: uppercase;
  letter-spacing: 0.8px;
  margin-bottom: 14px;
}

.info-row {
  display: flex;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid var(--color-border);
  gap: 16px;
}
.info-row:last-child { border-bottom: none; }

.info-label {
  font-size: 13px;
  color: var(--color-text-secondary);
  width: 80px;
  flex-shrink: 0;
}

.info-value {
  font-size: 13px;
  color: var(--color-text-primary);
  font-weight: 500;
}

/* Password */
.pwd-change-btn {
  font-size: 12px;
  padding: 4px 12px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-accent);
  color: var(--color-accent);
  background: transparent;
  cursor: pointer;
  transition: background var(--transition-fast);
}
.pwd-change-btn:hover { background: color-mix(in srgb, var(--color-accent) 10%, transparent); }

.pwd-form { display: flex; flex-direction: column; gap: 12px; }

.pwd-field { display: flex; flex-direction: column; gap: 4px; }
.pwd-field label { font-size: 12px; color: var(--color-text-secondary); }

.pwd-input {
  padding: 7px 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-bg-input);
  color: var(--color-text-primary);
  font-size: 13px;
  outline: none;
  transition: border-color 0.2s;
}
.pwd-input:focus { border-color: var(--color-accent); }

.pwd-error { font-size: 12px; color: var(--color-error); }

.pwd-actions { display: flex; gap: 8px; justify-content: flex-end; margin-top: 4px; }

.pwd-cancel {
  padding: 6px 14px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 13px;
  cursor: pointer;
}
.pwd-cancel:hover { background: var(--color-bg-input); }

.pwd-submit {
  padding: 6px 14px;
  border-radius: var(--radius-sm);
  border: none;
  background: var(--color-accent);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
  transition: opacity 0.2s;
}
.pwd-submit:disabled { opacity: 0.5; cursor: not-allowed; }

.pwd-success {
  font-size: 13px;
  color: var(--color-success, #16a34a);
  padding: 8px 0;
}

/* DS Grid */
.ds-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
}

.ds-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ds-card-header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ds-type-badge {
  padding: 2px 7px;
  border-radius: var(--radius-sm);
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.5px;
  flex-shrink: 0;
}

.ds-name-group { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.ds-name { font-size: 13px; font-weight: 500; color: var(--color-text-primary); }
.ds-host { font-size: 11px; color: var(--color-text-secondary); font-family: var(--font-mono); }

.ds-tables { display: flex; flex-wrap: wrap; gap: 4px; }

.table-tag {
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  background: var(--color-bg-input);
  border: 1px solid var(--color-border);
  font-size: 11px;
  color: var(--color-text-secondary);
}

.no-tables { font-size: 11px; color: var(--color-text-disabled); font-style: italic; }
.ds-empty { font-size: 13px; color: var(--color-text-disabled); text-align: center; padding: 20px 0; }
</style>
