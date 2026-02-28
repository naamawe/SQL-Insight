<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { queryPolicyApi, userAuthApi, rolePermissionApi } from '@/api/permission'
import { roleApi } from '@/api/user'
import { userApi } from '@/api/user'
import { dataSourceApi } from '@/api/datasource'
import type { Role, QueryPolicy, UserVO, DataSourceVO } from '@/types'

const activeTab = ref('auth')

// ── 角色列表（Tab 1 & Tab 3 共用） ────────────────────
const roles = ref<Role[]>([])
const roleSearch = ref('')
const rolePage = ref(1)
const roleTotal = ref(0)
const rolePageSize = 200

async function fetchRoles() {
  const res = await roleApi.page(rolePage.value, rolePageSize, roleSearch.value || undefined) as any
  roles.value = res.records
  roleTotal.value = res.total
  if (selectedRoleId.value && !roles.value.find(r => r.id === selectedRoleId.value)) {
    selectedRoleId.value = null
  }
  if (tablePermRoleId.value && !roles.value.find(r => r.id === tablePermRoleId.value)) {
    tablePermRoleId.value = null
    tablePermDsId.value = null
    allTables.value = []
    authorizedTables.value = []
  }
}

let roleTimer: ReturnType<typeof setTimeout>
function onRoleSearch() {
  clearTimeout(roleTimer)
  rolePage.value = 1
  roleTimer = setTimeout(fetchRoles, 300)
}

// ── 用户列表（Tab 2） ──────────────────────────────────
const users = ref<UserVO[]>([])
const userSearch = ref('')
const userPage = ref(1)
const userTotal = ref(0)
const userPageSize = 200

async function fetchUsers() {
  const res = await userApi.page(userPage.value, userPageSize, userSearch.value || undefined) as any
  users.value = res.records
  userTotal.value = res.total
  if (selectedUserId.value && !users.value.find(u => u.id === selectedUserId.value)) {
    selectedUserId.value = null
    authorizedIds.value = []
  }
}

let userTimer: ReturnType<typeof setTimeout>
function onUserSearch() {
  clearTimeout(userTimer)
  userPage.value = 1
  userTimer = setTimeout(fetchUsers, 300)
}

// ── 表名前端过滤（一次性加载，数量有限） ──────────────
const tableSearch = ref('')
const filteredTables = computed(() =>
  tableSearch.value ? allTables.value.filter(t => t.toLowerCase().includes(tableSearch.value.toLowerCase())) : allTables.value
)

// ── 表名分页（Tab 3） ────────────────────────────────
const tablePage = ref(1)
const tablePageSize = 24
const totalTablePages = computed(() => Math.ceil(filteredTables.value.length / tablePageSize) || 1)
const pagedTables = computed(() => {
  const start = (tablePage.value - 1) * tablePageSize
  return filteredTables.value.slice(start, start + tablePageSize)
})
watch(tableSearch, () => { tablePage.value = 1 })

// ── 数据源前端搜索过滤（Tab 3 表权限） ───────────────
const tablePermDsSearch = ref('')
const filteredTablePermDs = computed(() => {
  if (!tablePermDsSearch.value) return dataSources.value
  const q = tablePermDsSearch.value.toLowerCase()
  return dataSources.value.filter(ds =>
    ds.connName.toLowerCase().includes(q) ||
    ds.host.toLowerCase().includes(q) ||
    ds.dbType.toLowerCase().includes(q) ||
    ds.databaseName?.toLowerCase().includes(q)
  )
})

// ── 数据源前端搜索过滤（Tab 2） ──────────────────────
const dsSearch = ref('')
const filteredDataSources = computed(() => {
  if (!dsSearch.value) return dataSources.value
  const q = dsSearch.value.toLowerCase()
  return dataSources.value.filter(ds =>
    ds.connName.toLowerCase().includes(q) ||
    ds.host.toLowerCase().includes(q) ||
    ds.dbType.toLowerCase().includes(q) ||
    ds.databaseName?.toLowerCase().includes(q)
  )
})

// ── 数据源分页（Tab 2） ──────────────────────────────
const dsPage = ref(1)
const dsPageSize = 12
const totalDsPages = computed(() => Math.ceil(filteredDataSources.value.length / dsPageSize) || 1)
const pagedDataSources = computed(() => {
  const start = (dsPage.value - 1) * dsPageSize
  return filteredDataSources.value.slice(start, start + dsPageSize)
})
watch(dsSearch, () => { dsPage.value = 1 })

// ══════════════════════════════════════════════════════
// Tab 1：查询策略
// ══════════════════════════════════════════════════════
const selectedRoleId = ref<number | null>(null)
const policyLoading = ref(false)
const policySaving = ref(false)
const hasPolicy = ref(false)

const policy = ref<QueryPolicy>({
  roleId: 0,
  allowJoin: 0,
  allowSubquery: 0,
  allowAggregation: 0,
  maxLimit: 1000,
})

async function loadPolicy(roleId: number) {
  policyLoading.value = true
  try {
    const res = await queryPolicyApi.getByRoleId(roleId)
    if (res) {
      policy.value = res as any
      hasPolicy.value = true
    } else {
      policy.value = { roleId, allowJoin: 0, allowSubquery: 0, allowAggregation: 0, maxLimit: 1000 }
      hasPolicy.value = false
    }
  } finally {
    policyLoading.value = false
  }
}

function onRoleChange(roleId: number) {
  selectedRoleId.value = roleId
  loadPolicy(roleId)
}

async function savePolicy() {
  if (!selectedRoleId.value) return
  policy.value.roleId = selectedRoleId.value
  policySaving.value = true
  try {
    await queryPolicyApi.save(policy.value)
    ElMessage.success({ message: '查询策略已保存', duration: 2000 })
    hasPolicy.value = true
  } finally {
    policySaving.value = false
  }
}

async function deletePolicy() {
  if (!selectedRoleId.value) return
  await queryPolicyApi.remove(selectedRoleId.value)
  ElMessage.success({ message: '策略已删除', duration: 2000 })
  hasPolicy.value = false
  policy.value = { roleId: selectedRoleId.value, allowJoin: 0, allowSubquery: 0, allowAggregation: 0, maxLimit: 1000 }
}

// ══════════════════════════════════════════════════════
// Tab 2：用户数据源授权
// ══════════════════════════════════════════════════════
const dataSources = ref<DataSourceVO[]>([])
const selectedUserId = ref<number | null>(null)
const authorizedIds = ref<number[]>([])
const authLoading = ref(false)
const authSaving = ref(false)

async function loadUserAuth(userId: number) {
  authLoading.value = true
  try {
    const ids = await userAuthApi.getAuthorizedIds(userId)
    authorizedIds.value = ids as any
  } finally {
    authLoading.value = false
  }
}

function onUserChange(userId: number) {
  selectedUserId.value = userId
  loadUserAuth(userId)
}

async function saveAuth() {
  if (!selectedUserId.value) return
  authSaving.value = true
  try {
    await userAuthApi.assign(selectedUserId.value, authorizedIds.value)
    ElMessage.success({ message: '数据源授权已保存', duration: 2000 })
  } finally {
    authSaving.value = false
  }
}

function toggleDs(id: number) {
  const idx = authorizedIds.value.indexOf(id)
  if (idx === -1) authorizedIds.value.push(id)
  else authorizedIds.value.splice(idx, 1)
}

function isAuthorized(id: number) {
  return authorizedIds.value.includes(id)
}

// ══════════════════════════════════════════════════════
// Tab 3：角色表权限
// ══════════════════════════════════════════════════════
const tablePermRoleId = ref<number | null>(null)
const tablePermDsId = ref<number | null>(null)
const allTables = ref<string[]>([])
const authorizedTables = ref<string[]>([])
const tableLoading = ref(false)
const tableSaving = ref(false)

async function onTablePermRoleChange(roleId: number) {
  tablePermRoleId.value = roleId
  tablePermDsId.value = null
  allTables.value = []
  authorizedTables.value = []
  tableSearch.value = ''
}

async function onTablePermDsChange(dsId: number) {
  tablePermDsId.value = dsId
  if (!tablePermRoleId.value) return
  tableLoading.value = true
  try {
    const [tables, authorized] = await Promise.all([
      dataSourceApi.getTables(dsId),
      rolePermissionApi.getTables(tablePermRoleId.value, dsId),
    ])
    allTables.value = tables as any
    authorizedTables.value = authorized as any
  } finally {
    tableLoading.value = false
  }
}

function toggleTable(name: string) {
  const idx = authorizedTables.value.indexOf(name)
  if (idx === -1) authorizedTables.value.push(name)
  else authorizedTables.value.splice(idx, 1)
}

function isTableAuthorized(name: string) {
  return authorizedTables.value.includes(name)
}

function selectAllTables() {
  authorizedTables.value = [...allTables.value]
}

function clearAllTables() {
  authorizedTables.value = []
}

async function saveTablePerm() {
  if (!tablePermRoleId.value || !tablePermDsId.value) return
  tableSaving.value = true
  try {
    await rolePermissionApi.assign(tablePermRoleId.value, tablePermDsId.value, authorizedTables.value)
    ElMessage.success({ message: '表权限已保存', duration: 2000 })
  } finally {
    tableSaving.value = false
  }
}

// ── 初始化 ────────────────────────────────────────────

// 侧边栏列表滚动状态（滚动时显示滚动条，停止后隐藏）
const listScrolling = ref(false)
let listScrollTimer: ReturnType<typeof setTimeout> | null = null
function handleListScroll() {
  listScrolling.value = true
  if (listScrollTimer) clearTimeout(listScrollTimer)
  listScrollTimer = setTimeout(() => { listScrolling.value = false }, 300)
}

onMounted(async () => {
  const [, dsList] = await Promise.all([
    fetchRoles(),
    dataSourceApi.list(),
  ])
  dataSources.value = dsList as any
  fetchUsers()
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div class="page-title">
        <h2>权限管理</h2>
        <span class="page-subtitle">配置角色查询策略、用户数据源授权与角色表权限</span>
      </div>
    </div>

    <!-- Tab 切换 -->
    <div class="tab-bar">
      <button class="tab-item" :class="{ active: activeTab === 'auth' }" @click="activeTab = 'auth'">数据源授权</button>
      <button class="tab-item" :class="{ active: activeTab === 'tables' }" @click="activeTab = 'tables'">表权限</button>
      <button class="tab-item" :class="{ active: activeTab === 'policy' }" @click="activeTab = 'policy'">查询策略</button>
      <div class="tab-line" :style="{ transform: activeTab === 'tables' ? 'translateX(100%)' : activeTab === 'policy' ? 'translateX(200%)' : 'translateX(0)' }" />
    </div>

    <!-- ── Tab 1：查询策略 ── -->
    <div v-if="activeTab === 'policy'" class="tab-content">
      <div class="panel">
        <div class="panel-side">
          <p class="side-label">选择角色</p>
          <div class="side-search">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
            <input v-model="roleSearch" placeholder="搜索角色..." class="side-search-input" @input="onRoleSearch" />
          </div>
          <div class="role-list" :class="{ scrolling: listScrolling }" @scroll="handleListScroll">
            <button
              v-for="r in roles"
              :key="r.id"
              class="role-item"
              :class="{ active: selectedRoleId === r.id }"
              @click="onRoleChange(r.id)"
            >
              {{ r.roleName }}
            </button>
            <div v-if="!roles.length" class="side-empty">无匹配角色</div>
          </div>
        </div>

        <div class="panel-main" v-loading="policyLoading">
          <template v-if="selectedRoleId">
            <div class="policy-header">
              <span class="policy-title">
                {{ roles.find(r => r.id === selectedRoleId)?.roleName }} 的查询策略
              </span>
              <span v-if="hasPolicy" class="policy-badge active">已配置</span>
              <span v-else class="policy-badge none">未配置</span>
            </div>

            <div class="policy-grid">
              <div class="policy-item">
                <div class="policy-item-info">
                  <span class="policy-item-name">允许 JOIN 查询</span>
                  <span class="policy-item-desc">允许在 SQL 中使用 JOIN 关联多表</span>
                </div>
                <el-switch v-model="policy.allowJoin" :active-value="1" :inactive-value="0" />
              </div>
              <div class="policy-item">
                <div class="policy-item-info">
                  <span class="policy-item-name">允许子查询</span>
                  <span class="policy-item-desc">允许在 SQL 中使用嵌套子查询</span>
                </div>
                <el-switch v-model="policy.allowSubquery" :active-value="1" :inactive-value="0" />
              </div>
              <div class="policy-item">
                <div class="policy-item-info">
                  <span class="policy-item-name">允许聚合函数</span>
                  <span class="policy-item-desc">允许使用 COUNT、SUM、AVG 等聚合函数</span>
                </div>
                <el-switch v-model="policy.allowAggregation" :active-value="1" :inactive-value="0" />
              </div>
              <div class="policy-item">
                <div class="policy-item-info">
                  <span class="policy-item-name">最大返回行数</span>
                  <span class="policy-item-desc">单次查询最多返回的数据行数</span>
                </div>
                <el-input-number
                  v-model="policy.maxLimit"
                  :min="1"
                  :max="100000"
                  :step="100"
                  size="small"
                  style="width: 130px"
                />
              </div>
            </div>

            <div class="policy-actions">
              <el-button v-if="hasPolicy" class="btn-danger-text" @click="deletePolicy">删除策略</el-button>
              <el-button type="primary" class="btn-primary" :loading="policySaving" @click="savePolicy">
                保存策略
              </el-button>
            </div>
          </template>
          <div v-else class="empty-hint">
            <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" style="color: var(--color-text-disabled)">
              <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/>
            </svg>
            <p>请从左侧选择一个角色</p>
          </div>
        </div>
      </div>
    </div>

    <!-- ── Tab 2：数据源授权 ── -->
    <div v-if="activeTab === 'auth'" class="tab-content">
      <div class="panel">
        <div class="panel-side">
          <p class="side-label">选择用户</p>
          <div class="side-search">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
            <input v-model="userSearch" placeholder="搜索用户..." class="side-search-input" @input="onUserSearch" />
          </div>
          <div class="role-list" :class="{ scrolling: listScrolling }" @scroll="handleListScroll">
            <button
              v-for="u in users"
              :key="u.id"
              class="role-item"
              :class="{ active: selectedUserId === u.id }"
              @click="onUserChange(u.id)"
            >
              <span class="user-dot">{{ u.userName.charAt(0).toUpperCase() }}</span>
              {{ u.userName }}
            </button>
            <div v-if="!users.length" class="side-empty">无匹配用户</div>
          </div>
        </div>

        <div class="panel-main panel-main--auth" v-loading="authLoading">
          <template v-if="selectedUserId">
            <div class="auth-scroll">
              <div class="policy-header">
                <div class="policy-header-left">
                  <span class="policy-title">
                    {{ users.find(u => u.id === selectedUserId)?.userName }} 的数据源权限
                  </span>
                  <span class="policy-badge active">{{ authorizedIds.length }} 个已授权</span>
                </div>
                <div class="policy-header-right">
                  <div class="ds-header-search">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
                    <input v-model="dsSearch" placeholder="搜索数据源..." class="ds-header-search-input" />
                  </div>
                </div>
              </div>

              <div class="ds-grid">
                <div
                  v-for="ds in pagedDataSources"
                  :key="ds.id"
                  class="ds-card"
                  :class="{ authorized: isAuthorized(ds.id) }"
                  @click="toggleDs(ds.id)"
                >
                  <div class="ds-card-check">
                    <svg v-if="isAuthorized(ds.id)" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                      <polyline points="20 6 9 17 4 12"/>
                    </svg>
                  </div>
                  <div class="ds-card-info">
                    <span class="ds-type-badge" :style="{ background: ds.dbType === 'mysql' ? '#00758f' : ds.dbType === 'postgresql' ? '#336791' : '#cc2927' }">
                      {{ ds.dbType.toUpperCase() }}
                    </span>
                    <span class="ds-name">{{ ds.connName }}</span>
                    <span class="ds-host">{{ ds.host }}:{{ ds.port }}/{{ ds.databaseName }}</span>
                  </div>
                </div>
              </div>

              <div v-if="!dataSources.length" class="empty-hint">
                <p>暂无数据源，请先在「数据源管理」中添加</p>
              </div>
              <div v-else-if="filteredDataSources.length === 0" class="empty-hint">
                <p>没有匹配的数据源</p>
              </div>
            </div>

            <div class="auth-footer">
              <div class="ds-page-ctrl">
                <button class="side-page-btn" :disabled="dsPage === 1" @click="dsPage--">‹</button>
                <span>{{ dsPage }} / {{ totalDsPages }}</span>
                <button class="side-page-btn" :disabled="dsPage >= totalDsPages" @click="dsPage++">›</button>
              </div>
              <el-button type="primary" class="btn-primary" :loading="authSaving" @click="saveAuth">
                保存授权
              </el-button>
            </div>
          </template>
          <div v-else class="empty-hint">
            <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" style="color: var(--color-text-disabled)">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/>
            </svg>
            <p>请从左侧选择一个用户</p>
          </div>
        </div>
      </div>
    </div>

    <!-- ── Tab 3：角色表权限 ── -->
    <div v-if="activeTab === 'tables'" class="tab-content">
      <div class="panel">
        <div class="panel-side">
          <p class="side-label">选择角色</p>
          <div class="side-search">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
            <input v-model="roleSearch" placeholder="搜索角色..." class="side-search-input" @input="onRoleSearch" />
          </div>
          <div class="role-list" :class="{ scrolling: listScrolling }" @scroll="handleListScroll">
            <button
              v-for="r in roles"
              :key="r.id"
              class="role-item"
              :class="{ active: tablePermRoleId === r.id }"
              @click="onTablePermRoleChange(r.id)"
            >
              {{ r.roleName }}
            </button>
            <div v-if="!roles.length" class="side-empty">无匹配角色</div>
          </div>
        </div>

        <div class="panel-main panel-main--auth" v-loading="tableLoading">
          <template v-if="tablePermRoleId">
            <div class="auth-scroll">
              <div class="ds-select-box">
                <div class="ds-select-header">
                  <span class="ds-select-label">选择数据源</span>
                  <div class="ds-select-search">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
                    <input v-model="tablePermDsSearch" placeholder="搜索数据源..." class="ds-select-search-input" />
                  </div>
                </div>
                <div class="ds-pill-list">
                  <button
                    v-for="ds in filteredTablePermDs"
                    :key="ds.id"
                    class="ds-pill"
                    :class="{ active: tablePermDsId === ds.id }"
                    @click="onTablePermDsChange(ds.id)"
                  >
                    <span class="ds-type-badge" :style="{ background: ds.dbType === 'mysql' ? '#00758f' : ds.dbType === 'postgresql' ? '#336791' : '#cc2927' }">
                      {{ ds.dbType.toUpperCase() }}
                    </span>
                    {{ ds.connName }}
                  </button>
                  <div v-if="filteredTablePermDs.length === 0" class="ds-select-empty">无匹配数据源</div>
                </div>
              </div>

              <template v-if="tablePermDsId">
                <div class="policy-header">
                  <div class="policy-header-left">
                    <span class="policy-title">
                      {{ roles.find(r => r.id === tablePermRoleId)?.roleName }} 在
                      {{ dataSources.find(d => d.id === tablePermDsId)?.connName }} 的可访问表
                    </span>
                    <span class="policy-badge active">{{ authorizedTables.length }} / {{ allTables.length }} 张表</span>
                  </div>
                  <div class="policy-header-right">
                    <button class="table-toolbar-btn" @click="selectAllTables">全选</button>
                    <button class="table-toolbar-btn" @click="clearAllTables">清空</button>
                    <div class="ds-header-search">
                      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
                      <input v-model="tableSearch" placeholder="搜索表名..." class="ds-header-search-input" />
                    </div>
                  </div>
                </div>

                <div v-if="allTables.length" class="table-grid">
                  <div
                    v-for="t in pagedTables"
                    :key="t"
                    class="table-card"
                    :class="{ authorized: isTableAuthorized(t) }"
                    @click="toggleTable(t)"
                  >
                    <div class="ds-card-check">
                      <svg v-if="isTableAuthorized(t)" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                        <polyline points="20 6 9 17 4 12"/>
                      </svg>
                    </div>
                    <span class="table-name">{{ t }}</span>
                  </div>
                </div>

                <div v-else-if="!tableLoading" class="empty-hint">
                  <p>该数据源暂无表信息，请先在「数据源管理」中刷新表缓存</p>
                </div>
              </template>

              <div v-else class="empty-hint">
                <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" style="color: var(--color-text-disabled)">
                  <ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3"/><path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"/>
                </svg>
                <p>请选择一个数据源</p>
              </div>
            </div>

            <div class="auth-footer">
              <div class="ds-page-ctrl">
                <template v-if="tablePermDsId && allTables.length">
                  <button class="side-page-btn" :disabled="tablePage === 1" @click="tablePage--">‹</button>
                  <span>{{ tablePage }} / {{ totalTablePages }}</span>
                  <button class="side-page-btn" :disabled="tablePage >= totalTablePages" @click="tablePage++">›</button>
                </template>
              </div>
              <el-button v-if="tablePermDsId && allTables.length" type="primary" class="btn-primary" :loading="tableSaving" @click="saveTablePerm">
                保存权限
              </el-button>
            </div>
          </template>

          <div v-else class="empty-hint">
            <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" style="color: var(--color-text-disabled)">
              <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/>
            </svg>
            <p>请从左侧选择一个角色</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 28px 32px;
  overflow: hidden;
}

.page-header { margin-bottom: 20px; flex-shrink: 0; }
.page-title h2 { font-size: 18px; font-weight: 600; color: var(--color-text-primary); margin-bottom: 2px; }
.page-subtitle { font-size: 13px; color: var(--color-text-secondary); }

/* ── Tab 栏 ── */
.tab-bar {
  position: relative;
  display: flex;
  border-bottom: 1px solid var(--color-border);
  margin-bottom: 20px;
  flex-shrink: 0;
  width: 360px;
}

.tab-item {
  flex: 1;
  padding: 8px 0;
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-secondary);
  text-align: center;
  transition: color var(--transition-fast);
}

.tab-item.active { color: var(--color-text-primary); }

.tab-line {
  position: absolute;
  bottom: -1px;
  left: 0;
  width: 33.33%;
  height: 2px;
  background: var(--color-accent);
  border-radius: 2px;
  transition: transform var(--transition-base);
}

/* ── 内容区 ── */
.tab-content { flex: 1; overflow: hidden; }

.panel {
  display: flex;
  height: 100%;
  gap: 16px;
}

/* 左侧列表 */
.panel-side {
  width: 200px;
  flex-shrink: 0;
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 16px 8px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.side-list {
  flex: 1;
  overflow-y: auto;
}

.side-search {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0 8px 8px;
  padding: 6px 10px;
  border: 1.5px solid var(--color-border);
  border-radius: 20px;
  background: var(--color-bg-input);
  color: var(--color-text-disabled);
  transition: border-color 0.2s, box-shadow 0.2s;
}
.side-search:focus-within {
  border-color: var(--color-accent);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-accent) 15%, transparent);
  color: var(--color-accent);
}

.side-search-input {
  flex: 1;
  font-size: 12px;
  color: var(--color-text-primary);
  background: transparent;
  outline: none;
  border: none;
  min-width: 0;
}

.side-search-input::placeholder { color: var(--color-text-disabled); }

.side-empty {
  padding: 8px 10px;
  font-size: 12px;
  color: var(--color-text-disabled);
  text-align: center;
}


.side-page-btn {
  width: 22px;
  height: 22px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border);
  background: var(--color-bg-input);
  font-size: 14px;
  color: var(--color-text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background var(--transition-fast);
}
.side-page-btn:hover:not(:disabled) { background: var(--color-border); color: var(--color-text-primary); }
.side-page-btn:disabled { opacity: 0.4; cursor: not-allowed; }


.side-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--color-text-disabled);
  text-transform: uppercase;
  letter-spacing: 0.8px;
  padding: 0 8px;
  margin-bottom: 8px;
}

.role-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: transparent transparent;
  transition: scrollbar-color 0.3s;
}
.role-list.scrolling {
  scrollbar-color: var(--color-border-strong) transparent;
}
.role-list::-webkit-scrollbar { width: 4px; }
.role-list::-webkit-scrollbar-thumb {
  background: transparent;
  border-radius: var(--radius-full);
  transition: background 0.3s;
}
.role-list.scrolling::-webkit-scrollbar-thumb { background: var(--color-border-strong); }

.role-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: var(--radius-md);
  font-size: 13px;
  color: var(--color-text-secondary);
  text-align: left;
  transition: background var(--transition-fast), color var(--transition-fast);
}

.role-item:hover { background: var(--color-bg-input); color: var(--color-text-primary); }
.role-item.active { background: var(--color-accent-bg); color: var(--color-accent); font-weight: 500; }

.user-dot {
  width: 22px;
  height: 22px;
  border-radius: var(--radius-full);
  background: var(--color-accent);
  color: white;
  font-size: 11px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

/* 右侧主区 */
.panel-main {
  flex: 1;
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 24px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.policy-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--color-border);
}

.policy-header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.policy-header-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.ds-header-search {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  border: 1.5px solid var(--color-border);
  border-radius: 20px;
  background: var(--color-bg-input);
  color: var(--color-text-disabled);
  transition: border-color 0.2s, box-shadow 0.2s;
}

.ds-header-search:focus-within {
  border-color: var(--color-accent);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-accent) 15%, transparent);
  color: var(--color-accent);
}

.ds-header-search-input {
  width: 160px;
  font-size: 12px;
  color: var(--color-text-primary);
  background: transparent;
  outline: none;
  border: none;
}

.ds-header-search-input::placeholder { color: var(--color-text-disabled); }

.policy-title { font-size: 15px; font-weight: 600; color: var(--color-text-primary); }

.policy-badge {
  padding: 2px 8px;
  border-radius: var(--radius-full);
  font-size: 11px;
  font-weight: 500;
}

.policy-badge.active { background: #dcfce7; color: var(--color-success); }
.policy-badge.none   { background: var(--color-bg-input); color: var(--color-text-disabled); }

/* 策略表单 */
.policy-grid { display: flex; flex-direction: column; gap: 0; }

.policy-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 0;
  border-bottom: 1px solid var(--color-border);
}

.policy-item:last-child { border-bottom: none; }

.policy-item-info { display: flex; flex-direction: column; gap: 3px; }
.policy-item-name { font-size: 14px; font-weight: 500; color: var(--color-text-primary); }
.policy-item-desc { font-size: 12px; color: var(--color-text-secondary); }

.policy-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding-top: 4px;
}

.btn-primary {
  background: var(--color-accent);
  border-color: var(--color-accent);
  border-radius: var(--radius-md);
}
.btn-primary:hover { background: var(--color-accent-light); border-color: var(--color-accent-light); }

.btn-danger-text { color: var(--color-error); border-color: var(--color-error); border-radius: var(--radius-md); }
.btn-danger-text:hover { background: #fef2f2; }

/* 数据源卡片网格 */
.ds-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 10px;
}

.ds-card {
  position: relative;
  border: 1.5px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 12px 14px;
  cursor: pointer;
  transition: border-color var(--transition-fast), background var(--transition-fast);
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.ds-card:hover { border-color: var(--color-accent); background: var(--color-accent-bg); }
.ds-card.authorized { border-color: var(--color-accent); background: var(--color-accent-bg); }

.ds-card-check {
  width: 18px;
  height: 18px;
  border-radius: 4px;
  border: 1.5px solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-top: 1px;
  transition: background var(--transition-fast), border-color var(--transition-fast);
}

.ds-card.authorized .ds-card-check {
  background: var(--color-accent);
  border-color: var(--color-accent);
  color: white;
}

.ds-card-info { display: flex; flex-direction: column; gap: 4px; min-width: 0; }

.ds-type-badge {
  display: inline-block;
  padding: 1px 6px;
  border-radius: var(--radius-sm);
  font-size: 10px;
  font-weight: 700;
  color: white;
  letter-spacing: 0.3px;
  width: fit-content;
}

.ds-name { font-size: 13px; font-weight: 500; color: var(--color-text-primary); }
.ds-host { font-size: 11px; color: var(--color-text-secondary); font-family: var(--font-mono); }

/* 空状态 */
.empty-hint {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: var(--color-text-disabled);
  font-size: 13px;
}

/* ── 表权限：数据源选择区 ── */
.ds-select-box {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
  flex-shrink: 0;
}

.ds-select-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 14px;
  background: var(--color-bg-input);
  border-bottom: 1px solid var(--color-border);
  gap: 12px;
}

.ds-select-search {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border: 1.5px solid var(--color-border);
  border-radius: 20px;
  background: var(--color-bg-surface);
  color: var(--color-text-disabled);
  transition: border-color 0.2s, box-shadow 0.2s;
}

.ds-select-search:focus-within {
  border-color: var(--color-accent);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-accent) 15%, transparent);
  color: var(--color-accent);
}

.ds-select-search-input {
  width: 160px;
  font-size: 12px;
  color: var(--color-text-primary);
  background: transparent;
  outline: none;
  border: none;
}

.ds-select-search-input::placeholder { color: var(--color-text-disabled); }

.ds-select-box .ds-pill-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px 14px;
  max-height: 120px;
  overflow-y: auto;
}

.ds-select-empty {
  font-size: 12px;
  color: var(--color-text-disabled);
}

.ds-pill {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  border-radius: var(--radius-full);
  border: 1.5px solid var(--color-border);
  font-size: 13px;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: border-color var(--transition-fast), background var(--transition-fast), color var(--transition-fast);
}

.ds-pill:hover { border-color: var(--color-accent); color: var(--color-accent); }
.ds-pill.active { border-color: var(--color-accent); background: var(--color-accent-bg); color: var(--color-accent); font-weight: 500; }


.table-toolbar-btn {
  padding: 4px 12px;
  border-radius: var(--radius-sm);
  font-size: 12px;
  color: var(--color-text-secondary);
  border: 1px solid var(--color-border);
  background: var(--color-bg-input);
  cursor: pointer;
  transition: background var(--transition-fast), color var(--transition-fast);
}

.table-toolbar-btn:hover { background: var(--color-border); color: var(--color-text-primary); }

.table-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 8px;
}

.table-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: 1.5px solid var(--color-border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: border-color var(--transition-fast), background var(--transition-fast);
}

.table-card:hover { border-color: var(--color-accent); background: var(--color-accent-bg); }
.table-card.authorized { border-color: var(--color-accent); background: var(--color-accent-bg); }

.table-card .ds-card-check {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
}

.table-card.authorized .ds-card-check {
  background: var(--color-accent);
  border-color: var(--color-accent);
  color: white;
}

.table-name {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-primary);
  font-family: var(--font-mono);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ── Tab 2 授权面板专属布局 ── */
.panel-main--auth {
  padding: 0;
  overflow: hidden;
  gap: 0;
}

.auth-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.auth-footer {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  border-top: 1px solid var(--color-border);
  background: var(--color-bg-surface);
}

.ds-page-ctrl {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--color-text-secondary);
}
</style>
