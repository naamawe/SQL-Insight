<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { userApi, roleApi } from '@/api/user'
import type { UserVO, Role } from '@/types'
import type { UserSaveDTO, UserUpdateDTO } from '@/api/user'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const currentUserId = computed(() => authStore.userInfo?.userId)

// ── 列表 ──────────────────────────────────────────────
const loading = ref(false)
const tableData = ref<UserVO[]>([])
const total = ref(0)
const searchName = ref('')
const pagination = reactive({ current: 1, size: 10 })
const roles = ref<Role[]>([])

// ── 动态行高：让 pageSize 行恰好铺满表格区域 ─────────────
const tableBodyRef = ref<HTMLElement | null>(null)
const rowHeight = ref(52)
const headerHeight = ref(45)
let resizeObserver: ResizeObserver | null = null

function updateRowHeight() {
  if (!tableBodyRef.value) return
  const headerEl = tableBodyRef.value.querySelector('.el-table__header-wrapper') as HTMLElement
  const headerH = headerEl ? headerEl.offsetHeight : 45
  headerHeight.value = headerH
  const available = tableBodyRef.value.clientHeight - headerH
  rowHeight.value = Math.max(44, Math.floor(available / pagination.size))
}

async function fetchList() {
  loading.value = true
  try {
    const res = await userApi.page(pagination.current, pagination.size, searchName.value || undefined)
    tableData.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.current = 1
  fetchList()
}

function handlePageChange(page: number) {
  pagination.current = page
  fetchList()
}

// ── 新增弹窗 ──────────────────────────────────────────
const addDialogVisible = ref(false)
const addSubmitting = ref(false)
const addError = ref('')
const addForm = reactive<UserSaveDTO>({ userName: '', password: '', roleId: 0, systemPermission: 'USER' })
let addErrorTimer: ReturnType<typeof setTimeout> | null = null

const roleDropdownOpen = ref(false)
const addRoleSearch = ref('')
const filteredAddRoles = computed(() =>
  addRoleSearch.value.trim()
    ? roles.value.filter(r => r.roleName.toLowerCase().includes(addRoleSearch.value.trim().toLowerCase()))
    : roles.value
)

function selectAddRole(id: number) {
  addForm.roleId = id
  roleDropdownOpen.value = false
  addRoleSearch.value = ''
  addError.value = ''
}

function setAddError(msg: string) {
  addError.value = msg
  if (addErrorTimer) clearTimeout(addErrorTimer)
  addErrorTimer = setTimeout(() => { addError.value = '' }, 4000)
}

function openAdd() {
  Object.assign(addForm, { userName: '', password: '', roleId: 0, systemPermission: 'USER' })
  addError.value = ''
  roleDropdownOpen.value = false
  addRoleSearch.value = ''
  addDialogVisible.value = true
}

async function handleAdd() {
  if (!addForm.userName.trim()) { setAddError('请输入用户名'); return }
  if (addForm.userName.trim().length < 3) { setAddError('用户名至少 3 位'); return }
  if (!addForm.password) { setAddError('请输入密码'); return }
  if (addForm.password.length < 6) { setAddError('密码至少 6 位'); return }
  if (!addForm.roleId) { setAddError('请选择角色'); return }
  if (!addForm.systemPermission) { setAddError('请选择系统权限'); return }
  addError.value = ''
  addSubmitting.value = true
  try {
    await userApi.save(addForm)
    addDialogVisible.value = false
    ElMessage.success({ message: '用户创建成功', duration: 2000 })
    fetchList()
  } catch {
    // 错误已由 http 拦截器统一展示
  } finally {
    addSubmitting.value = false
  }
}

// ── 编辑角色弹窗 ──────────────────────────────────────
const editDialogVisible = ref(false)
const editFormRef = ref()
const editSubmitting = ref(false)
const editUserName = ref('')
const editRoleSearch = ref('')
const editForm = reactive<UserUpdateDTO>({ id: 0, roleId: 0, status: 1 })

const filteredRoles = computed(() =>
  editRoleSearch.value.trim()
    ? roles.value.filter(r => r.roleName.toLowerCase().includes(editRoleSearch.value.trim().toLowerCase()))
    : roles.value
)

function openEdit(row: UserVO) {
  Object.assign(editForm, { id: row.id, roleId: row.roleId, status: row.status })
  editUserName.value = row.userName
  editRoleSearch.value = ''
  editDialogVisible.value = true
}

async function handleEdit() {
  await editFormRef.value?.validate()
  editSubmitting.value = true
  try {
    await userApi.update(editForm)
    editDialogVisible.value = false
    ElMessage.success({ message: '用户信息已更新', duration: 2000 })
    fetchList()
  } catch {
    // 错误已由 http 拦截器统一展示
  } finally {
    editSubmitting.value = false
  }
}

// ── 自定义确认弹窗 ─────────────────────────────────────
const confirmVisible = ref(false)
const confirmTitle = ref('')
const confirmMsg = ref('')
const confirmOkText = ref('确认')
const confirmDanger = ref(true)
let confirmResolve: ((v: boolean) => void) | null = null

function showConfirm(title: string, msg: string, okText = '确认', danger = true): Promise<boolean> {
  confirmTitle.value = title
  confirmMsg.value = msg
  confirmOkText.value = okText
  confirmDanger.value = danger
  confirmVisible.value = true
  return new Promise(resolve => { confirmResolve = resolve })
}

function onConfirmOk() {
  confirmVisible.value = false
  confirmResolve?.(true)
}

function onConfirmCancel() {
  confirmVisible.value = false
  confirmResolve?.(false)
}

// ── 删除 ──────────────────────────────────────────────
async function handleDelete(row: UserVO) {
  const ok = await showConfirm('删除用户', `确定删除用户「${row.userName}」吗？此操作不可恢复。`, '删除')
  if (!ok) return
  try {
    await userApi.remove(row.id)
    ElMessage.success({ message: '用户已删除', duration: 2000 })
    fetchList()
  } catch {
    // 错误已由 http 拦截器统一展示
  }
}

// ── 重置密码 ──────────────────────────────────────────
async function handleResetPassword(row: UserVO) {
  const ok = await showConfirm('重置密码', `确定重置「${row.userName}」的密码吗？`, '确认重置')
  if (!ok) return
  try {
    await userApi.resetPassword(row.id)
    ElMessage.success({ message: '密码已重置为默认密码', duration: 2000 })
  } catch {
    // 错误已由 http 拦截器统一展示
  }
}

// ── 系统权限编辑 ──────────────────────────────────────
const permDialogVisible = ref(false)
const permSubmitting = ref(false)
const permUserId = ref(0)
const permUserName = ref('')
const selectedPerm = ref('')

const permOptions = [
  {
    value: 'ROLE_USER',
    label: 'USER',
    desc: '普通用户，仅可访问已授权的数据源与查询功能',
    color: '#16a34a',
    icon: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>`,
  },
  {
    value: 'ROLE_ADMIN',
    label: 'ADMIN',
    desc: '管理员，可管理用户、数据源及权限配置',
    color: '#d97706',
    icon: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>`,
  },
]

function openPermEdit(row: UserVO) {
  permUserId.value = row.id
  permUserName.value = row.userName
  selectedPerm.value = row.systemPermission
  permDialogVisible.value = true
}

async function handlePermUpdate() {
  permSubmitting.value = true
  try {
    await userApi.updateSystemPermission(permUserId.value, selectedPerm.value)
    permDialogVisible.value = false
    ElMessage.success({ message: '系统权限已更新', duration: 2000 })
    fetchList()
  } catch {
    // 错误已由 http 拦截器统一展示
  } finally {
    permSubmitting.value = false
  }
}

// ── 工具函数 ──────────────────────────────────────────
function getRoleName(roleId: number) {
  return roles.value.find(r => r.id === roleId)?.roleName ?? '-'
}

const permissionMap: Record<string, { label: string; color: string }> = {
  SUPER_ADMIN: { label: 'SUPER ADMIN', color: '#7c3aed' },
  ADMIN:       { label: 'ADMIN',       color: '#d97706' },
  USER:        { label: 'USER',        color: '#16a34a' },
}

onMounted(async () => {
  const [, roleList] = await Promise.all([fetchList(), roleApi.list()])
  roles.value = roleList
  nextTick(() => {
    if (tableBodyRef.value) {
      resizeObserver = new ResizeObserver(updateRowHeight)
      resizeObserver.observe(tableBodyRef.value)
      updateRowHeight()
    }
  })
})

onUnmounted(() => {
  resizeObserver?.disconnect()
})
</script>

<template>
  <div class="page">
    <!-- 页头 -->
    <div class="page-header">
      <div class="page-title">
        <h2>用户管理</h2>
        <span class="page-subtitle">管理系统用户与角色分配</span>
      </div>
      <div class="page-actions">
        <el-input
          v-model="searchName"
          placeholder="搜索用户名..."
          clearable
          class="search-input"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
            </svg>
          </template>
        </el-input>
        <el-button type="primary" class="btn-primary" @click="openAdd">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
          新增用户
        </el-button>
      </div>
    </div>

    <div class="table-card">
      <div class="table-body" :class="{ 'is-empty': !loading && tableData.length === 0 }" ref="tableBodyRef">
        <el-table v-loading="loading" :data="tableData" row-key="id" :row-style="{ height: rowHeight + 'px' }">
        <el-table-column label="用户名" prop="userName" min-width="160" header-align="left">
          <template #default="{ row }">
            <div class="user-cell">
              <div class="user-avatar-sm" :class="{ 'is-self': row.id === currentUserId }">{{ row.userName.charAt(0).toUpperCase() }}</div>
              <span class="user-name">{{ row.userName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="系统权限" min-width="150" header-align="left">
          <template #default="{ row }">
            <span
              class="perm-badge"
              :style="{ background: (permissionMap[row.systemPermission]?.color ?? '#888') + '20', color: permissionMap[row.systemPermission]?.color ?? '#888', borderColor: (permissionMap[row.systemPermission]?.color ?? '#888') + '50' }"
            >
              {{ permissionMap[row.systemPermission]?.label ?? row.systemPermission }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="业务角色" min-width="150" header-align="left">
          <template #default="{ row }">
            <span class="role-text">{{ getRoleName(row.roleId) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" header-align="left">
          <template #default="{ row }">
            <span class="status-dot" :class="row.status === 1 ? 'active' : 'inactive'">
              {{ row.status === 1 ? '正常' : '禁用' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="gmtCreated" min-width="180" header-align="left">
          <template #default="{ row }">
            <span class="time-text">{{ row.gmtCreated?.slice(0, 16).replace('T', ' ') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right" align="center" header-align="center">
          <template #default="{ row }">
            <div class="row-actions">
              <button class="action-btn perm" title="编辑系统权限" :disabled="row.id === currentUserId" @click="openPermEdit(row)">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
                </svg>
              </button>
              <button class="action-btn edit" title="编辑角色/状态" :disabled="row.id === currentUserId" @click="openEdit(row)">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                </svg>
              </button>
              <button class="action-btn reset" title="重置密码" :disabled="row.id === currentUserId" @click="handleResetPassword(row)">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                </svg>
              </button>
              <button class="action-btn danger" title="删除用户" :disabled="row.id === currentUserId" @click="handleDelete(row)">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/>
                </svg>
              </button>
            </div>
          </template>
        </el-table-column>
        <template #empty><span /></template>
      </el-table>
      <div v-if="!loading && tableData.length === 0" class="empty-overlay" :style="{ top: headerHeight + 'px' }">
        <div class="empty-state">
          <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" style="color: var(--color-text-disabled)">
            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>
          </svg>
          <p>暂无用户数据</p>
        </div>
      </div>
      </div>
      <div class="pagination">
        <span class="pagination-total">共 <strong>{{ total }}</strong> 条记录</span>
        <el-pagination
          v-model:current-page="pagination.current"
          :page-size="pagination.size"
          :total="total"
          layout="prev, pager, next"
          background
          @current-change="handlePageChange"
        />
      </div>
    </div>

    <!-- 新增用户弹窗 -->
    <Teleport to="body">
      <div v-if="addDialogVisible" class="modal-overlay" @click.self="addDialogVisible = false">
        <div class="modal-dialog">
          <div class="modal-header">
            <div class="modal-header-icon add-icon">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
            </div>
            <div>
              <div class="modal-title">新增用户</div>
              <div class="modal-sub">创建一个新的系统用户</div>
            </div>
          </div>
          <div class="modal-body">
            <div class="field">
              <label class="field-label">用户名 <span class="required">*</span></label>
              <input v-model="addForm.userName" class="field-input" placeholder="3~20 位字符" maxlength="20" @input="addError = ''" />
            </div>
            <div class="field">
              <label class="field-label">密码 <span class="required">*</span></label>
              <input v-model="addForm.password" type="password" class="field-input" placeholder="至少 6 位" @input="addError = ''" />
            </div>
            <div class="field">
              <label class="field-label">业务角色 <span class="required">*</span></label>
              <div class="role-dropdown" :class="{ open: roleDropdownOpen }">
                <button type="button" class="role-dropdown-trigger" @click="roleDropdownOpen = !roleDropdownOpen">
                  <span :class="{ 'placeholder-text': !addForm.roleId }">
                    {{ addForm.roleId ? roles.find(r => r.id === addForm.roleId)?.roleName : '请选择角色' }}
                  </span>
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" class="chevron"><polyline points="6 9 12 15 18 9"/></svg>
                </button>
                <div v-if="roleDropdownOpen" class="role-dropdown-panel">
                  <div class="role-search-wrap">
                    <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
                    <input v-model="addRoleSearch" class="role-search-input" placeholder="搜索角色…" @click.stop />
                  </div>
                  <div class="role-list">
                    <button v-for="r in filteredAddRoles" :key="r.id" type="button" class="role-list-item" :class="{ selected: addForm.roleId === r.id }" @click="selectAddRole(r.id)">
                      {{ r.roleName }}
                      <svg v-if="addForm.roleId === r.id" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>
                    </button>
                    <div v-if="filteredAddRoles.length === 0" class="role-list-empty">无匹配角色</div>
                  </div>
                </div>
                <div v-if="roleDropdownOpen" class="role-dropdown-backdrop" @click="roleDropdownOpen = false" />
              </div>
            </div>
            <div class="field">
              <label class="field-label">系统权限 <span class="required">*</span></label>
              <div class="permission-options">
                <button
                  type="button"
                  class="permission-btn"
                  :class="{ active: addForm.systemPermission === 'USER' }"
                  @click="addForm.systemPermission = 'USER'; addError = ''"
                >
                  普通用户
                </button>
                <button
                  type="button"
                  class="permission-btn"
                  :class="{ active: addForm.systemPermission === 'ADMIN' }"
                  @click="addForm.systemPermission = 'ADMIN'; addError = ''"
                >
                  管理员
                </button>
              </div>
            </div>
            <div class="modal-error-wrap">
              <Transition name="modal-err">
                <div v-if="addError" class="modal-error">
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="flex-shrink:0"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
                  {{ addError }}
                </div>
              </Transition>
            </div>
          </div>
          <div class="modal-footer">
            <button class="modal-btn-cancel" @click="addDialogVisible = false">取消</button>
            <button class="modal-btn-ok" :disabled="addSubmitting" @click="handleAdd">
              {{ addSubmitting ? '创建中…' : '创建用户' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 编辑用户弹窗（自定义） -->
    <Teleport to="body">
      <div v-if="editDialogVisible" class="edit-overlay" @click.self="editDialogVisible = false">
        <div class="edit-dialog">
          <!-- 头部：用户信息 -->
          <div class="edit-dialog-header">
            <div class="edit-user-avatar">{{ editUserName.charAt(0).toUpperCase() }}</div>
            <div>
              <div class="edit-dialog-title">编辑用户</div>
              <div class="edit-dialog-sub">{{ editUserName }}</div>
            </div>
          </div>

          <!-- 角色选择 -->
          <div class="edit-section">
            <div class="edit-section-row">
              <div class="edit-section-label">业务角色</div>
              <div class="edit-role-search">
                <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
                <input v-model="editRoleSearch" class="edit-role-search-input" placeholder="搜索…" />
              </div>
            </div>
            <div class="edit-role-cards">
              <button
                v-for="r in filteredRoles"
                :key="r.id"
                class="edit-role-card"
                :class="{ selected: editForm.roleId === r.id }"
                @click="editForm.roleId = r.id"
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="7" width="20" height="14" rx="2" ry="2"/><path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"/></svg>
                {{ r.roleName }}
              </button>
              <div v-if="filteredRoles.length === 0" class="edit-role-empty">无匹配角色</div>
            </div>
          </div>

          <!-- 状态选择 -->
          <div class="edit-section">
            <div class="edit-section-label">账号状态</div>
            <div class="edit-status-toggle">
              <button
                class="edit-status-btn"
                :class="{ active: editForm.status === 1 }"
                @click="editForm.status = 1"
              >
                <span class="status-dot-sm active-dot" />
                正常
              </button>
              <button
                class="edit-status-btn"
                :class="{ inactive: editForm.status === 0 }"
                @click="editForm.status = 0"
              >
                <span class="status-dot-sm inactive-dot" />
                禁用
              </button>
            </div>
          </div>

          <div class="edit-dialog-footer">
            <button class="edit-btn-cancel" @click="editDialogVisible = false">取消</button>
            <button class="edit-btn-ok" :disabled="editSubmitting" @click="handleEdit">
              {{ editSubmitting ? '保存中…' : '保存' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 系统权限编辑弹窗（自定义） -->
    <Teleport to="body">
      <div v-if="permDialogVisible" class="perm-overlay" @click.self="permDialogVisible = false">
        <div class="perm-dialog">
          <div class="perm-dialog-header">
            <div class="perm-dialog-title">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
              编辑系统权限
            </div>
            <div class="perm-dialog-sub">{{ permUserName }}</div>
          </div>

          <div class="perm-cards">
            <button
              v-for="opt in permOptions"
              :key="opt.value"
              class="perm-card"
              :class="{ selected: selectedPerm === opt.value }"
              :style="selectedPerm === opt.value ? { borderColor: opt.color, background: opt.color + '10' } : {}"
              @click="selectedPerm = opt.value"
            >
              <div class="perm-card-icon" :style="{ color: opt.color, background: opt.color + '18' }" v-html="opt.icon" />
              <div class="perm-card-body">
                <div class="perm-card-label" :style="selectedPerm === opt.value ? { color: opt.color } : {}">{{ opt.label }}</div>
                <div class="perm-card-desc">{{ opt.desc }}</div>
              </div>
              <div class="perm-card-check" :style="{ background: opt.color }" v-show="selectedPerm === opt.value">
                <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>
              </div>
            </button>
          </div>

          <div class="perm-notice" v-if="selectedPerm === 'ROLE_SUPER_ADMIN'">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
            当前用户为超级管理员，无法在此变更其权限等级
          </div>

          <div class="perm-dialog-footer">
            <button class="perm-btn-cancel" @click="permDialogVisible = false">取消</button>
            <button
              class="perm-btn-ok"
              :disabled="permSubmitting || selectedPerm === 'ROLE_SUPER_ADMIN'"
              @click="handlePermUpdate"
            >
              {{ permSubmitting ? '保存中…' : '保存' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 自定义确认弹窗 -->
    <Teleport to="body">
      <div v-if="confirmVisible" class="confirm-overlay" @click.self="onConfirmCancel">
        <div class="confirm-dialog">
          <div class="confirm-title">{{ confirmTitle }}</div>
          <div class="confirm-msg">{{ confirmMsg }}</div>
          <div class="confirm-actions">
            <button class="confirm-btn-cancel" @click="onConfirmCancel">取消</button>
            <button class="confirm-btn-ok" :class="{ danger: confirmDanger }" @click="onConfirmOk">{{ confirmOkText }}</button>
          </div>
        </div>
      </div>
    </Teleport>
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

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
  flex-shrink: 0;
}

.page-title h2 {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 2px;
}

.page-subtitle {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.page-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.search-input {
  width: 200px;
}

.search-input :deep(.el-input__wrapper) {
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  box-shadow: none !important;
  border-radius: var(--radius-md);
}

.search-input :deep(.el-input__wrapper:hover),
.search-input :deep(.el-input__wrapper.is-focus) {
  border-color: var(--color-accent);
}

.btn-primary {
  background: var(--color-accent);
  border-color: var(--color-accent);
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  gap: 6px;
}

.btn-primary:hover {
  background: var(--color-accent-light);
  border-color: var(--color-accent-light);
}

.table-card {
  flex: 1;
  min-height: 0;
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.table-body {
  flex: 1;
  overflow: auto;
  position: relative;
}

.table-body.is-empty :deep(.el-table__body-wrapper),
.table-body.is-empty :deep(.el-scrollbar__bar) {
  display: none;
}

.empty-overlay {
  position: absolute;
  inset: 0;
  z-index: 1;
  background: var(--color-bg-surface);
  display: flex;
  align-items: center;
  justify-content: center;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  color: var(--color-text-disabled);
  font-size: 13px;
}
.table-card :deep(.el-table) { font-size: 13px; }
.table-card :deep(.el-table th) {
  background: var(--color-bg-input);
  color: var(--color-text-secondary);
  font-weight: 500;
  font-size: 12px;
  padding: 13px 0;
}
.table-card :deep(.el-table td) {
  border-bottom-color: var(--color-border);
  padding: 14px 0;
}
.table-card :deep(.el-table tr:hover td) { background: #faf9f7; }

.user-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-avatar-sm {
  width: 28px;
  height: 28px;
  border-radius: var(--radius-full);
  background: var(--color-accent);
  color: white;
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.user-avatar-sm.is-self {
  box-shadow: 0 0 0 2px #fff, 0 0 0 3.5px #7c3aed;
}

.user-name {
  font-weight: 500;
  color: var(--color-text-primary);
}

.action-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
  pointer-events: none;
}

.perm-badge {
  display: inline-block;
  padding: 2px 9px;
  border-radius: var(--radius-full);
  font-size: 11px;
  font-weight: 600;
  border: 1px solid transparent;
  letter-spacing: 0.3px;
  white-space: nowrap;
}

.role-text {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.status-dot {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
}

.status-dot::before {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.status-dot.active { color: var(--color-success); }
.status-dot.active::before { background: var(--color-success); }
.status-dot.inactive { color: var(--color-text-disabled); }
.status-dot.inactive::before { background: var(--color-text-disabled); }

.time-text { font-size: 12px; color: var(--color-text-secondary); }

/* ── Action buttons ── */
.row-actions { display: flex; align-items: center; justify-content: center; gap: 4px; }

.action-btn {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid transparent;
  cursor: pointer;
  transition: background 0.15s, color 0.15s, border-color 0.15s;
  flex-shrink: 0;
}

.action-btn.perm { color: #7c3aed; background: #7c3aed12; border-color: #7c3aed28; }
.action-btn.perm:hover { background: #7c3aed22; border-color: #7c3aed55; }

.action-btn.edit { color: var(--color-text-secondary); background: var(--color-bg-input); border-color: var(--color-border); }
.action-btn.edit:hover { color: var(--color-text-primary); background: var(--color-bg-surface); }

.action-btn.reset { color: #d97706; background: #d9770612; border-color: #d9770628; }
.action-btn.reset:hover { background: #d9770622; border-color: #d9770655; }

.action-btn.danger { color: var(--color-error, #dc2626); background: #dc262612; border-color: #dc262628; }
.action-btn.danger:hover { background: #dc262622; border-color: #dc262655; }

.empty-state {
  padding: 48px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  color: var(--color-text-disabled);
  font-size: 13px;
}

.pagination {
  padding: 14px 16px;
  border-top: 1px solid var(--color-border);
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-shrink: 0;
}

.pagination-total {
  font-size: 12px;
  color: var(--color-text-secondary);
  background: var(--color-bg-input);
  border: 1px solid var(--color-border);
  border-radius: 999px;
  padding: 3px 12px;
}

.pagination-total strong {
  color: var(--color-accent);
  font-weight: 600;
}

.pagination :deep(.el-pagination.is-background .el-pager li.is-active) {
  background: var(--color-accent);
}

.user-dialog :deep(.el-dialog__header) {
  padding: 20px 24px 16px;
  border-bottom: 1px solid var(--color-border);
  margin: 0;
}

.user-dialog :deep(.el-dialog__title) {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.user-dialog :deep(.el-dialog__body) { padding: 20px 24px; }
.user-dialog :deep(.el-dialog__footer) {
  padding: 14px 24px;
  border-top: 1px solid var(--color-border);
}

.user-form :deep(.el-input__wrapper),
.user-form :deep(.el-select .el-input__wrapper) {
  box-shadow: none !important;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-input);
}

.user-form :deep(.el-input__wrapper:hover),
.user-form :deep(.el-input__wrapper.is-focus),
.user-form :deep(.el-select .el-input.is-focus .el-input__wrapper) {
  border-color: var(--color-accent) !important;
}

/* ── 通用弹窗 ── */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.3);
  backdrop-filter: blur(2px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}

.modal-dialog {
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: 20px;
  width: 400px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.14);
  animation: modal-in 0.15s ease;
}

@keyframes modal-in {
  from { opacity: 0; transform: scale(0.95) translateY(6px); }
  to   { opacity: 1; transform: scale(1) translateY(0); }
}

.modal-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 22px 18px;
  border-bottom: 1px solid var(--color-border);
  border-radius: 20px 20px 0 0;
}

.modal-header-icon {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.add-icon { background: #4f46e518; color: var(--color-accent); }

.modal-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 2px;
}

.modal-sub { font-size: 12px; color: var(--color-text-secondary); }

.modal-body {
  padding: 20px 22px 4px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.field { display: flex; flex-direction: column; gap: 6px; }

.field-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.4px;
}

.required { color: #dc2626; }

.field-input,
.field-select {
  width: 100%;
  padding: 8px 12px;
  border: 1.5px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-bg-input);
  font-size: 13px;
  color: var(--color-text-primary);
  outline: none;
  transition: border-color 0.15s;
  box-sizing: border-box;
  font-family: inherit;
}

.field-input:focus,
.field-select:focus { border-color: var(--color-accent); background: var(--color-bg-surface); }

.field-input::placeholder { color: var(--color-text-disabled); }

.field-select { appearance: none; cursor: pointer; }
.field-select option:disabled { color: var(--color-text-disabled); }

/* ── 角色自定义下拉 ── */
.role-dropdown { position: relative; }

.role-dropdown-trigger {
  width: 100%;
  padding: 8px 12px;
  border: 1.5px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-bg-input);
  font-size: 13px;
  color: var(--color-text-primary);
  outline: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  transition: border-color 0.15s, background 0.15s;
  font-family: inherit;
  text-align: left;
}

.role-dropdown-trigger:hover,
.role-dropdown.open .role-dropdown-trigger {
  border-color: var(--color-accent);
  background: var(--color-bg-surface);
}

.placeholder-text { color: var(--color-text-disabled); }

.chevron {
  flex-shrink: 0;
  color: var(--color-text-secondary);
  transition: transform 0.15s;
}

.role-dropdown.open .chevron { transform: rotate(180deg); }

.role-dropdown-panel {
  position: absolute;
  top: calc(100% + 5px);
  left: 0;
  right: 0;
  background: var(--color-bg-surface);
  border: 1.5px solid var(--color-border);
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  z-index: 10;
  overflow: hidden;
}

.role-search-wrap {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--color-border);
  color: var(--color-text-secondary);
}

.role-search-input {
  flex: 1;
  border: none;
  background: transparent;
  outline: none;
  font-size: 12px;
  color: var(--color-text-primary);
  font-family: inherit;
}

.role-search-input::placeholder { color: var(--color-text-disabled); }

.role-list {
  max-height: 180px;
  overflow-y: auto;
  padding: 4px;
}

.role-list::-webkit-scrollbar { width: 4px; }
.role-list::-webkit-scrollbar-track { background: transparent; }
.role-list::-webkit-scrollbar-thumb { background: var(--color-border); border-radius: 4px; }

.role-list-item {
  width: 100%;
  padding: 7px 10px;
  border-radius: 8px;
  border: none;
  background: transparent;
  font-size: 13px;
  color: var(--color-text-primary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  transition: background 0.12s;
  font-family: inherit;
  text-align: left;
}

.role-list-item:hover { background: var(--color-bg-input); }

.role-list-item.selected {
  color: var(--color-accent);
  font-weight: 500;
  background: var(--color-accent, #4f46e5)10;
}

.role-list-empty {
  padding: 12px 10px;
  font-size: 12px;
  color: var(--color-text-disabled);
  text-align: center;
}

.role-dropdown-backdrop {
  position: fixed;
  inset: 0;
  z-index: 9;
}

.modal-error-wrap {
  height: 34px;
  display: flex;
  align-items: center;
}

.modal-error {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 6px 10px;
  background: #dc262610;
  border: 1px solid #dc262630;
  border-radius: 8px;
  font-size: 12px;
  color: #dc2626;
  width: 100%;
}

.modal-err-enter-active, .modal-err-leave-active { transition: opacity 0.18s ease; }
.modal-err-enter-from, .modal-err-leave-to { opacity: 0; }

.modal-footer {
  padding: 16px 22px;
  border-top: 1px solid var(--color-border);
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  border-radius: 0 0 20px 20px;
}

.modal-btn-cancel {
  padding: 7px 18px;
  border-radius: 999px;
  border: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s;
}
.modal-btn-cancel:hover { background: var(--color-bg-input); }

.modal-btn-ok {
  padding: 7px 20px;
  border-radius: 999px;
  border: none;
  background: var(--color-accent);
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.15s;
}
.modal-btn-ok:hover:not(:disabled) { opacity: 0.85; }
.modal-btn-ok:disabled { opacity: 0.45; cursor: not-allowed; }

/* ── 系统权限弹窗 ── */
.perm-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.3);
  backdrop-filter: blur(2px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}

.perm-dialog {
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: 20px;
  width: 380px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.14);
  animation: dialog-in 0.15s ease;
  overflow: hidden;
}

@keyframes dialog-in {
  from { opacity: 0; transform: scale(0.95) translateY(6px); }
  to   { opacity: 1; transform: scale(1) translateY(0); }
}

.perm-dialog-header {
  padding: 20px 22px 16px;
  border-bottom: 1px solid var(--color-border);
}

.perm-dialog-title {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 3px;
}

.perm-dialog-sub {
  font-size: 12px;
  color: var(--color-text-secondary);
  padding-left: 23px;
}

.perm-cards {
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.perm-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  border: 1.5px solid var(--color-border);
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.15s, background 0.15s;
  position: relative;
}

.perm-card:hover:not(.selected) {
  background: var(--color-bg-input);
}

.perm-card-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.perm-card-body { flex: 1; min-width: 0; }

.perm-card-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 2px;
  transition: color 0.15s;
}

.perm-card-desc {
  font-size: 11px;
  color: var(--color-text-secondary);
  line-height: 1.4;
}

.perm-card-check {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.perm-notice {
  margin: 0 16px 12px;
  padding: 9px 12px;
  background: #fef3c710;
  border: 1px solid #fde68a;
  border-radius: 10px;
  font-size: 12px;
  color: #92400e;
  display: flex;
  align-items: center;
  gap: 6px;
}

.perm-dialog-footer {
  padding: 14px 16px;
  border-top: 1px solid var(--color-border);
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.perm-btn-cancel {
  padding: 7px 18px;
  border-radius: 999px;
  border: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s;
}
.perm-btn-cancel:hover { background: var(--color-bg-input); }

.perm-btn-ok {
  padding: 7px 20px;
  border-radius: 999px;
  border: none;
  background: var(--color-accent);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
  font-weight: 500;
  transition: opacity 0.15s;
}
.perm-btn-ok:hover:not(:disabled) { opacity: 0.85; }
.perm-btn-ok:disabled { opacity: 0.45; cursor: not-allowed; }

/* ── 自定义确认弹窗 ── */
.confirm-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.3);
  backdrop-filter: blur(2px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}

.confirm-dialog {
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: 20px;
  padding: 24px 28px;
  width: 320px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
  animation: dialog-in 0.15s ease;
}

.confirm-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 8px;
}

.confirm-msg {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-bottom: 20px;
  line-height: 1.5;
}

.confirm-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.confirm-btn-cancel {
  padding: 6px 18px;
  border-radius: 999px;
  border: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s;
}
.confirm-btn-cancel:hover { background: var(--color-bg-input); }

.confirm-btn-ok {
  padding: 6px 18px;
  border-radius: 999px;
  border: none;
  background: var(--color-accent);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
  transition: opacity 0.15s;
}
.confirm-btn-ok.danger { background: var(--color-error, #dc2626); }
.confirm-btn-ok:hover { opacity: 0.85; }

/* ── 编辑用户弹窗 ── */
.edit-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.3);
  backdrop-filter: blur(2px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}

.edit-dialog {
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: 20px;
  width: 360px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.14);
  animation: dialog-in 0.15s ease;
  overflow: hidden;
}

.edit-dialog-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 22px 16px;
  border-bottom: 1px solid var(--color-border);
}

.edit-user-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--color-accent);
  color: white;
  font-size: 16px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.edit-dialog-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 2px;
}

.edit-dialog-sub {
  font-size: 12px;
  color: var(--color-text-secondary);
}

.edit-section {
  padding: 16px 22px 0;
}

.edit-section-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--color-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.6px;
  margin-bottom: 8px;
}

.edit-section-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.edit-section-row .edit-section-label {
  margin-bottom: 0;
}

.edit-role-search {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 3px 9px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-bg-input);
  color: var(--color-text-secondary);
  transition: border-color 0.15s;
}

.edit-role-search:focus-within {
  border-color: var(--color-accent);
}

.edit-role-search-input {
  width: 80px;
  border: none;
  background: transparent;
  outline: none;
  font-size: 11px;
  color: var(--color-text-primary);
}

.edit-role-search-input::placeholder {
  color: var(--color-text-disabled);
}

.edit-role-empty {
  font-size: 12px;
  color: var(--color-text-disabled);
  padding: 6px 4px;
}

.edit-role-cards {
  display: flex;
  flex-wrap: wrap;
  align-content: flex-start;
  gap: 6px;
  height: 116px;
  overflow-y: auto;
  padding-right: 2px;
}

.edit-role-cards::-webkit-scrollbar {
  width: 4px;
}
.edit-role-cards::-webkit-scrollbar-track { background: transparent; }
.edit-role-cards::-webkit-scrollbar-thumb {
  background: var(--color-border);
  border-radius: 4px;
}

.edit-role-card {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: 999px;
  border: 1.5px solid var(--color-border);
  background: transparent;
  font-size: 13px;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: all 0.15s;
}

.edit-role-card:hover:not(.selected) {
  background: var(--color-bg-input);
  color: var(--color-text-primary);
}

.edit-role-card.selected {
  border-color: var(--color-accent);
  background: var(--color-accent, #4f46e5)18;
  color: var(--color-accent);
  font-weight: 600;
}

.edit-status-toggle {
  display: flex;
  gap: 6px;
}

.edit-status-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 16px;
  border-radius: 999px;
  border: 1.5px solid var(--color-border);
  background: transparent;
  font-size: 13px;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: all 0.15s;
}

.status-dot-sm {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}

.active-dot { background: var(--color-success, #16a34a); }
.inactive-dot { background: var(--color-text-disabled, #aaa); }

.edit-status-btn.active {
  border-color: var(--color-success, #16a34a);
  background: #16a34a15;
  color: var(--color-success, #16a34a);
  font-weight: 600;
}

.edit-status-btn.inactive {
  border-color: var(--color-text-disabled, #aaa);
  background: #88888815;
  color: var(--color-text-disabled, #aaa);
  font-weight: 600;
}

.edit-dialog-footer {
  padding: 16px 22px;
  margin-top: 16px;
  border-top: 1px solid var(--color-border);
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.edit-btn-cancel {
  padding: 7px 18px;
  border-radius: 999px;
  border: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s;
}
.edit-btn-cancel:hover { background: var(--color-bg-input); }

.edit-btn-ok {
  padding: 7px 20px;
  border-radius: 999px;
  border: none;
  background: var(--color-accent);
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.15s;
}
.edit-btn-ok:hover:not(:disabled) { opacity: 0.85; }
.edit-btn-ok:disabled { opacity: 0.45; cursor: not-allowed; }

/* 系统权限选择按钮 */
.permission-options {
  display: flex;
  gap: 8px;
}

.permission-btn {
  flex: 1;
  padding: 8px 16px;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  color: var(--color-text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}

.permission-btn:hover {
  border-color: var(--color-accent);
  background: rgba(217, 119, 6, 0.05);
}

.permission-btn.active {
  border-color: var(--color-accent);
  background: rgba(217, 119, 6, 0.1);
  color: var(--color-accent);
  font-weight: 600;
}
</style>
