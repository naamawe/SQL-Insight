<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { userApi, roleApi } from '@/api/user'
import type { UserVO, Role } from '@/types'
import type { UserSaveDTO, UserUpdateDTO } from '@/api/user'

// ── 列表 ──────────────────────────────────────────────
const loading = ref(false)
const tableData = ref<UserVO[]>([])
const total = ref(0)
const searchName = ref('')
const pagination = reactive({ current: 1, size: 10 })
const roles = ref<Role[]>([])

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
const addFormRef = ref()
const addSubmitting = ref(false)
const addForm = reactive<UserSaveDTO>({ userName: '', password: '', roleId: 0 })

const addRules = {
  userName: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度 3~20 位', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' },
  ],
  roleId: [{ required: true, validator: (_: unknown, v: number, cb: (e?: Error) => void) => v ? cb() : cb(new Error('请选择角色')), trigger: 'change' }],
}

function openAdd() {
  Object.assign(addForm, { userName: '', password: '', roleId: 0 })
  addDialogVisible.value = true
}

async function handleAdd() {
  await addFormRef.value?.validate()
  addSubmitting.value = true
  try {
    await userApi.save(addForm)
    ElMessage.success('用户创建成功')
    addDialogVisible.value = false
    fetchList()
  } finally {
    addSubmitting.value = false
  }
}

// ── 编辑角色弹窗 ──────────────────────────────────────
const editDialogVisible = ref(false)
const editFormRef = ref()
const editSubmitting = ref(false)
const editForm = reactive<UserUpdateDTO>({ id: 0, roleId: 0, status: 1 })

function openEdit(row: UserVO) {
  Object.assign(editForm, { id: row.id, roleId: row.roleId, status: row.status })
  editDialogVisible.value = true
}

async function handleEdit() {
  await editFormRef.value?.validate()
  editSubmitting.value = true
  try {
    await userApi.update(editForm)
    ElMessage.success('用户信息已更新')
    editDialogVisible.value = false
    fetchList()
  } finally {
    editSubmitting.value = false
  }
}

// ── 删除 ──────────────────────────────────────────────
async function handleDelete(row: UserVO) {
  await ElMessageBox.confirm(`确定删除用户「${row.userName}」吗？此操作不可恢复。`, '删除确认', {
    type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消',
  })
  await userApi.remove(row.id)
  ElMessage.success('用户已删除')
  fetchList()
}

// ── 重置密码 ──────────────────────────────────────────
async function handleResetPassword(row: UserVO) {
  await ElMessageBox.confirm(`确定重置「${row.userName}」的密码吗？`, '重置密码', {
    type: 'warning', confirmButtonText: '确认重置', cancelButtonText: '取消',
  })
  await userApi.resetPassword(row.id)
  ElMessage.success('密码已重置为默认密码')
}

// ── 工具函数 ──────────────────────────────────────────
function getRoleName(roleId: number) {
  return roles.value.find(r => r.id === roleId)?.roleName ?? '-'
}

const permissionMap: Record<string, { label: string; color: string }> = {
  ROLE_SUPER_ADMIN: { label: 'SUPER ADMIN', color: '#7c3aed' },
  ROLE_ADMIN:       { label: 'ADMIN',       color: '#d97706' },
  ROLE_USER:        { label: 'USER',         color: '#16a34a' },
}

onMounted(async () => {
  const [, roleList] = await Promise.all([fetchList(), roleApi.list()])
  roles.value = roleList
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

    <!-- 表格 -->
    <div class="table-card">
      <el-table v-loading="loading" :data="tableData" row-key="id">
        <el-table-column label="用户名" prop="userName" min-width="140">
          <template #default="{ row }">
            <div class="user-cell">
              <div class="user-avatar-sm">{{ row.userName.charAt(0).toUpperCase() }}</div>
              <span class="user-name">{{ row.userName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="系统权限" min-width="130">
          <template #default="{ row }">
            <span
              class="perm-badge"
              :style="{ background: permissionMap[row.systemPermission]?.color ?? '#888' }"
            >
              {{ permissionMap[row.systemPermission]?.label ?? row.systemPermission }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="业务角色" min-width="120">
          <template #default="{ row }">
            <span class="role-text">{{ getRoleName(row.roleId) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <span class="status-dot" :class="row.status === 1 ? 'active' : 'inactive'">
              {{ row.status === 1 ? '正常' : '禁用' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="gmtCreated" min-width="160">
          <template #default="{ row }">
            <span class="time-text">{{ row.gmtCreated?.slice(0, 16) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <button class="action-btn" title="编辑" @click="openEdit(row)">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                </svg>
              </button>
              <button class="action-btn" title="重置密码" @click="handleResetPassword(row)">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                </svg>
              </button>
              <button class="action-btn danger" title="删除" @click="handleDelete(row)">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/>
                </svg>
              </button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty-state">
            <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" style="color: var(--color-text-disabled)">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>
            </svg>
            <p>暂无用户数据</p>
          </div>
        </template>
      </el-table>
      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.current"
          :page-size="pagination.size"
          :total="total"
          layout="total, prev, pager, next"
          background
          @current-change="handlePageChange"
        />
      </div>
    </div>

    <!-- 新增用户弹窗 -->
    <el-dialog v-model="addDialogVisible" title="新增用户" width="440px" :close-on-click-modal="false" class="user-dialog">
      <el-form ref="addFormRef" :model="addForm" :rules="addRules" label-width="80px" class="user-form">
        <el-form-item label="用户名" prop="userName">
          <el-input v-model="addForm.userName" placeholder="3~20 位字符" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="addForm.password" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
        <el-form-item label="角色" prop="roleId">
          <el-select v-model="addForm.roleId" placeholder="请选择角色" style="width: 100%">
            <el-option v-for="r in roles" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="addSubmitting" @click="handleAdd">创建</el-button>
      </template>
    </el-dialog>

    <!-- 编辑用户弹窗 -->
    <el-dialog v-model="editDialogVisible" title="编辑用户" width="440px" :close-on-click-modal="false" class="user-dialog">
      <el-form ref="editFormRef" :model="editForm" label-width="80px" class="user-form">
        <el-form-item label="角色">
          <el-select v-model="editForm.roleId" style="width: 100%">
            <el-option v-for="r in roles" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editForm.status" style="width: 100%">
            <el-option label="正常" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSubmitting" @click="handleEdit">保存</el-button>
      </template>
    </el-dialog>
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
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.table-card :deep(.el-table) { flex: 1; font-size: 13px; }
.table-card :deep(.el-table th) {
  background: var(--color-bg-input);
  color: var(--color-text-secondary);
  font-weight: 500;
  font-size: 12px;
}
.table-card :deep(.el-table td) { border-bottom-color: var(--color-border); }
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

.user-name {
  font-weight: 500;
  color: var(--color-text-primary);
}

.perm-badge {
  padding: 2px 8px;
  border-radius: var(--radius-full);
  font-size: 11px;
  font-weight: 600;
  color: white;
  letter-spacing: 0.3px;
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

.row-actions { display: flex; align-items: center; gap: 4px; }

.action-btn {
  width: 30px;
  height: 30px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-secondary);
  transition: background var(--transition-fast), color var(--transition-fast);
}

.action-btn:hover { background: var(--color-bg-input); color: var(--color-text-primary); }
.action-btn.danger:hover { background: #fef2f2; color: var(--color-error); }

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
  justify-content: flex-end;
  flex-shrink: 0;
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
</style>
