<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { rolePermissionApi } from '@/api/permission'
import { dataSourceApi } from '@/api/datasource'
import { userApi } from '@/api/user'
import type { DataSourceVO } from '@/types'

const router = useRouter()
const authStore = useAuthStore()

const roleColorMap: Record<string, string> = {
  SUPER_ADMIN: '#7c3aed',
  ADMIN:       '#d97706',
  USER:        '#16a34a',
}

const dsList = ref<DataSourceVO[]>([])
const tableMap = ref<Record<number, string[]>>({})
const loading = ref(false)

// ── 数据源列表搜索 ─────────────────────────────────────
const dsListSearch = ref('')
const filteredDsList = computed(() => {
  if (!dsListSearch.value.trim()) return dsList.value
  const q = dsListSearch.value.trim().toLowerCase()
  return dsList.value.filter(ds =>
    ds.connName.toLowerCase().includes(q) ||
    ds.host.toLowerCase().includes(q) ||
    ds.databaseName.toLowerCase().includes(q)
  )
})

// ── 数据源详情弹窗 ─────────────────────────────────────
const activeDs = ref<DataSourceVO | null>(null)
const dsSearch = ref('')

function openDsDetail(ds: DataSourceVO) {
  activeDs.value = ds
  dsSearch.value = ''
}

function closeDsDetail() {
  activeDs.value = null
}

const filteredActiveTables = computed(() => {
  if (!activeDs.value) return []
  const tables = tableMap.value[activeDs.value.id] ?? []
  if (!dsSearch.value.trim()) return tables
  return tables.filter(t => t.toLowerCase().includes(dsSearch.value.trim().toLowerCase()))
})

// ── 修改密码 ──────────────────────────────────────────
const showPwdForm = ref(false)
const pwdSubmitting = ref(false)
const pwdSuccess = ref(false)
const pwdForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })
const showOldPwd = ref(false)
const showNewPwd = ref(false)
const showConfirmPwd = ref(false)

function openPwdForm() {
  pwdForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
  showOldPwd.value = false
  showNewPwd.value = false
  showConfirmPwd.value = false
  pwdSuccess.value = false
  showPwdForm.value = true
}

async function handleChangePassword() {
  const { oldPassword, newPassword, confirmPassword } = pwdForm.value
  if (!oldPassword || !newPassword || !confirmPassword) { ElMessage.warning('请填写所有字段'); return }
  if (newPassword.length < 8) { ElMessage.warning('新密码至少 8 位'); return }
  if (!/^(?=.*[A-Za-z])(?=.*\d).+$/.test(newPassword)) { ElMessage.warning('新密码必须同时包含字母和数字'); return }
  if (newPassword !== confirmPassword) { ElMessage.warning('两次密码不一致'); return }
  pwdSubmitting.value = true
  try {
    await userApi.updatePassword({ oldPassword, newPassword, confirmPassword })
    showPwdForm.value = false
    pwdSuccess.value = true
    ElMessage.success('密码修改成功')
    setTimeout(() => { pwdSuccess.value = false }, 3000)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message ?? '修改失败，请检查旧密码')
  } finally {
    pwdSubmitting.value = false
  }
}

onMounted(async () => {
  loading.value = true
  try {
    const [myDs, myTableMap] = await Promise.all([
      dataSourceApi.myList(),
      rolePermissionApi.mySummary(),
    ]) as any[]
    dsList.value = myDs
    tableMap.value = myTableMap ?? {}
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
          <span
            class="hero-role-badge"
            :style="{
              background: (roleColorMap[authStore.role] ?? '#4f46e5') + '18',
              color: roleColorMap[authStore.role] ?? '#4f46e5',
              borderColor: (roleColorMap[authStore.role] ?? '#4f46e5') + '50',
            }"
          >{{ authStore.role }}</span>
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
              <div class="pwd-input-wrap">
                <input v-model="pwdForm.oldPassword" :type="showOldPwd ? 'text' : 'password'" placeholder="请输入当前密码" class="pwd-input" />
                <button type="button" class="pwd-eye" @click="showOldPwd = !showOldPwd">
                  <svg v-if="!showOldPwd" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                  <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/><path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
                </button>
              </div>
            </div>
            <div class="pwd-field">
              <label>新密码</label>
              <div class="pwd-input-wrap">
                <input v-model="pwdForm.newPassword" :type="showNewPwd ? 'text' : 'password'" placeholder="至少 8 位，包含字母和数字" class="pwd-input" />
                <button type="button" class="pwd-eye" @click="showNewPwd = !showNewPwd">
                  <svg v-if="!showNewPwd" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                  <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/><path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
                </button>
              </div>
            </div>
            <div class="pwd-field">
              <label>确认新密码</label>
              <div class="pwd-input-wrap">
                <input v-model="pwdForm.confirmPassword" :type="showConfirmPwd ? 'text' : 'password'" placeholder="再次输入新密码" class="pwd-input" />
                <button type="button" class="pwd-eye" @click="showConfirmPwd = !showConfirmPwd">
                  <svg v-if="!showConfirmPwd" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                  <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/><path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
                </button>
              </div>
            </div>
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
          <div class="card-title">
            授权数据源
            <span v-if="dsList.length" class="ds-count-badge">{{ dsList.length }}</span>
            <label class="ds-list-search" v-if="dsList.length">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
              </svg>
              <input v-model="dsListSearch" class="ds-list-search-input" placeholder="搜索数据源..." />
              <button v-if="dsListSearch" class="ds-list-search-clear" @click.prevent="dsListSearch = ''">
                <svg width="9" height="9" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
              </button>
            </label>
          </div>
          <div v-if="loading" class="ds-empty">加载中...</div>
          <div v-else-if="!dsList.length" class="ds-empty">暂无授权数据源</div>
          <div v-else-if="!filteredDsList.length" class="ds-empty">无匹配的数据源</div>
          <div v-else class="ds-grid">
            <div v-for="ds in filteredDsList" :key="ds.id" class="ds-card" @click="openDsDetail(ds)">
              <div class="ds-card-header">
                <span class="ds-type-badge" :style="{ background: ds.dbType === 'mysql' ? '#00758f' : ds.dbType === 'postgresql' ? '#336791' : '#cc2927' }">
                  {{ ds.dbType.toUpperCase() }}
                </span>
                <div class="ds-name-group">
                  <span class="ds-name">{{ ds.connName }}</span>
                  <span class="ds-host">{{ ds.host }}:{{ ds.port }}/{{ ds.databaseName }}</span>
                </div>
              </div>
              <div class="ds-preview">
                <template v-if="!tableMap[ds.id]?.length">
                  <span class="all-tables-badge">全部可访问</span>
                </template>
                <template v-else>
                  <span v-for="t in tableMap[ds.id].slice(0, 3)" :key="t" class="table-tag">{{ t }}</span>
                  <span v-if="tableMap[ds.id].length > 3" class="table-more">+{{ tableMap[ds.id].length - 3 }} 个表</span>
                </template>
              </div>
            </div>
          </div>
        </div>

      </div>
    </div>

    <!-- 数据源详情弹窗 -->
    <Teleport to="body">
    <div v-if="activeDs" class="ds-modal-overlay" @click.self="closeDsDetail">
      <div class="ds-modal">
        <div class="ds-modal-header">
          <div class="ds-modal-title-group">
            <span class="ds-type-badge" :style="{ background: activeDs.dbType === 'mysql' ? '#00758f' : activeDs.dbType === 'postgresql' ? '#336791' : '#cc2927' }">
              {{ activeDs.dbType.toUpperCase() }}
            </span>
            <div>
              <div class="ds-modal-name">{{ activeDs.connName }}</div>
              <div class="ds-modal-host">{{ activeDs.host }}:{{ activeDs.port }}/{{ activeDs.databaseName }}</div>
            </div>
          </div>
          <button class="ds-modal-close" @click="closeDsDetail">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </div>

        <div class="ds-modal-search">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
          </svg>
          <input v-model="dsSearch" class="ds-search-input" placeholder="搜索表名..." autofocus />
        </div>

        <div class="ds-modal-body">
          <template v-if="!tableMap[activeDs.id]?.length">
            <div class="ds-no-limit">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
              无表权限限制，可访问该数据源全部表
            </div>
          </template>
          <template v-else-if="filteredActiveTables.length">
            <div class="ds-table-grid">
              <span v-for="t in filteredActiveTables" :key="t" class="table-tag-lg">{{ t }}</span>
            </div>
          </template>
          <div v-else class="ds-no-result">无匹配的表名</div>
        </div>

        <div class="ds-modal-footer">
          <span class="ds-table-count" v-if="tableMap[activeDs.id]?.length">
            共 <strong>{{ tableMap[activeDs.id].length }}</strong> 张授权表
            <template v-if="dsSearch && filteredActiveTables.length !== tableMap[activeDs.id].length">
              ，匹配 <strong>{{ filteredActiveTables.length }}</strong> 张
            </template>
          </span>
        </div>
      </div>
    </div>
  </Teleport>
  </div>
</template>

<style scoped>
.profile-page {
  height: 100%;
  background: var(--color-bg-base);
  padding: 32px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}

.profile-container {
  flex: 1;
  max-width: 860px;
  width: 100%;
  margin: 0 auto;
  overflow: hidden;
  display: flex;
  flex-direction: column;
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
  border: 1px solid transparent;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.5px;
  width: fit-content;
}

/* Grid */
.profile-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  grid-template-rows: auto 1fr;
  gap: 16px;
  flex: 1;
  overflow: hidden;
}

.full-width {
  grid-column: 1 / -1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

/* Card */
.profile-card {
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 20px 24px;
  overflow: hidden;
}

.card-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-disabled);
  text-transform: uppercase;
  letter-spacing: 0.8px;
  margin-bottom: 14px;
  display: flex;
  align-items: center;
  gap: 7px;
}

.ds-list-search {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 5px;
  height: 26px;
  padding: 0 8px 0 7px;
  border-radius: 13px;
  border: 1px solid var(--color-border);
  background: var(--color-bg-base);
  color: var(--color-text-disabled);
  cursor: text;
  transition: border-color 0.2s, box-shadow 0.2s, background 0.2s;
  letter-spacing: 0;
  text-transform: none;
  font-weight: 400;
}
.ds-list-search:focus-within {
  border-color: var(--color-accent);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--color-accent) 12%, transparent);
  background: var(--color-bg-surface);
  color: var(--color-accent);
}

.ds-list-search-input {
  border: none;
  background: transparent;
  outline: none;
  font-size: 12px;
  color: var(--color-text-primary);
  font-family: inherit;
  width: 120px;
  letter-spacing: 0;
  text-transform: none;
  font-weight: 400;
  transition: width 0.2s;
}
.ds-list-search:focus-within .ds-list-search-input { width: 160px; }
.ds-list-search-input::placeholder { color: var(--color-text-disabled); }

.ds-list-search-clear {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  border: none;
  border-radius: 50%;
  background: var(--color-text-disabled);
  color: var(--color-bg-surface);
  cursor: pointer;
  padding: 0;
  flex-shrink: 0;
  opacity: 0.7;
  transition: opacity 0.15s;
}
.ds-list-search-clear:hover { opacity: 1; }

.ds-count-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--color-bg-input);
  border: 1px solid var(--color-border);
  font-size: 11px;
  font-weight: 600;
  color: var(--color-text-secondary);
  letter-spacing: 0;
  text-transform: none;
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
  flex: 1;
  padding: 7px 10px;
  border: none;
  background: transparent;
  color: var(--color-text-primary);
  font-size: 13px;
  outline: none;
  min-width: 0;
}

.pwd-input-wrap {
  display: flex;
  align-items: center;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-bg-input);
  transition: border-color 0.2s;
}
.pwd-input-wrap:focus-within { border-color: var(--color-accent); }

.pwd-eye {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: none;
  background: transparent;
  color: var(--color-text-disabled);
  cursor: pointer;
  flex-shrink: 0;
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
  transition: color 0.15s;
}
.pwd-eye:hover { color: var(--color-text-secondary); }

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
  flex: 1;
  overflow-y: auto;
  padding-right: 4px;
  align-content: start;
}

.ds-grid::-webkit-scrollbar { width: 4px; }
.ds-grid::-webkit-scrollbar-track { background: transparent; }
.ds-grid::-webkit-scrollbar-thumb { background: var(--color-border); border-radius: 4px; }

.ds-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s, background 0.15s;
}

.ds-card:hover {
  border-color: var(--color-accent);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  background: var(--color-bg-input);
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
.ds-name { font-size: 13px; font-weight: 500; color: var(--color-text-primary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.ds-host { font-size: 11px; color: var(--color-text-secondary); font-family: var(--font-mono); }

.ds-preview { display: flex; flex-wrap: wrap; gap: 4px; min-height: 22px; }

.all-tables-badge {
  font-size: 11px;
  color: #16a34a;
  background: #16a34a12;
  border: 1px solid #16a34a30;
  border-radius: var(--radius-sm);
  padding: 1px 8px;
}

.table-tag {
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  background: var(--color-bg-input);
  border: 1px solid var(--color-border);
  font-size: 11px;
  color: var(--color-text-secondary);
}

.table-more {
  font-size: 11px;
  color: var(--color-text-secondary);
  padding: 2px 6px;
  border-radius: var(--radius-sm);
  background: var(--color-bg-input);
  border: 1px dashed var(--color-border);
}

.no-tables { font-size: 11px; color: var(--color-text-disabled); font-style: italic; }
.ds-empty { font-size: 13px; color: var(--color-text-disabled); text-align: center; padding: 20px 0; }

/* ── 数据源详情弹窗 ── */
.ds-modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.3);
  backdrop-filter: blur(2px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}

.ds-modal {
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: 20px;
  width: 520px;
  max-height: 70vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.14);
  animation: modal-in 0.15s ease;
  overflow: hidden;
}

@keyframes modal-in {
  from { opacity: 0; transform: scale(0.95) translateY(6px); }
  to   { opacity: 1; transform: scale(1) translateY(0); }
}

.ds-modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px;
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
}

.ds-modal-title-group {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ds-modal-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 2px;
}

.ds-modal-host {
  font-size: 11px;
  color: var(--color-text-secondary);
  font-family: var(--font-mono);
}

.ds-modal-close {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  border: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
  flex-shrink: 0;
}
.ds-modal-close:hover { background: var(--color-bg-input); color: var(--color-text-primary); }

.ds-modal-search {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border-bottom: 1px solid var(--color-border);
  color: var(--color-text-secondary);
  flex-shrink: 0;
}

.ds-search-input {
  flex: 1;
  border: none;
  background: transparent;
  outline: none;
  font-size: 13px;
  color: var(--color-text-primary);
  font-family: inherit;
}
.ds-search-input::placeholder { color: var(--color-text-disabled); }

.ds-modal-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
}

.ds-modal-body::-webkit-scrollbar { width: 4px; }
.ds-modal-body::-webkit-scrollbar-track { background: transparent; }
.ds-modal-body::-webkit-scrollbar-thumb { background: var(--color-border); border-radius: 4px; }

.ds-table-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.table-tag-lg {
  padding: 3px 10px;
  border-radius: var(--radius-sm);
  background: var(--color-bg-input);
  border: 1px solid var(--color-border);
  font-size: 12px;
  color: var(--color-text-secondary);
  font-family: var(--font-mono);
  transition: border-color 0.12s, color 0.12s;
}
.table-tag-lg:hover { border-color: var(--color-accent); color: var(--color-text-primary); }

.ds-no-limit {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #16a34a;
  padding: 12px 0;
}

.ds-no-result {
  font-size: 13px;
  color: var(--color-text-disabled);
  text-align: center;
  padding: 24px 0;
}

.ds-modal-footer {
  padding: 12px 20px;
  border-top: 1px solid var(--color-border);
  flex-shrink: 0;
}

.ds-table-count {
  font-size: 12px;
  color: var(--color-text-secondary);
}

.ds-table-count strong { color: var(--color-accent); font-weight: 600; }
</style>
