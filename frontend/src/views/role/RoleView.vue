<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { roleApi } from '@/api/user'
import type { Role } from '@/types'

const loading = ref(false)
const roles = ref<Role[]>([])

async function fetchList() {
  loading.value = true
  try {
    roles.value = await roleApi.list() as any
  } finally {
    loading.value = false
  }
}

// ── 新增弹窗 ──────────────────────────────────────────
const addDialogVisible = ref(false)
const addSubmitting = ref(false)
const addFormRef = ref()
const addForm = reactive({ roleName: '', description: '' })
const addRules = {
  roleName: [
    { required: true, message: '请输入角色名称', trigger: 'blur' },
    { min: 2, max: 30, message: '角色名称 2~30 位', trigger: 'blur' },
  ],
}

function openAdd() {
  Object.assign(addForm, { roleName: '', description: '' })
  addDialogVisible.value = true
}

async function handleAdd() {
  await addFormRef.value?.validate()
  addSubmitting.value = true
  try {
    await roleApi.save(addForm)
    ElMessage.success('角色创建成功')
    addDialogVisible.value = false
    fetchList()
  } finally {
    addSubmitting.value = false
  }
}

// ── 编辑弹窗 ──────────────────────────────────────────
const editDialogVisible = ref(false)
const editSubmitting = ref(false)
const editFormRef = ref()
const editForm = reactive({ id: 0, roleName: '', description: '' })

function openEdit(row: Role) {
  Object.assign(editForm, { id: row.id, roleName: row.roleName, description: row.description })
  editDialogVisible.value = true
}

async function handleEdit() {
  await editFormRef.value?.validate()
  editSubmitting.value = true
  try {
    await roleApi.update(editForm)
    ElMessage.success('角色已更新')
    editDialogVisible.value = false
    fetchList()
  } finally {
    editSubmitting.value = false
  }
}

// ── 删除 ──────────────────────────────────────────────
const showDeleteDialog = ref(false)
const roleToDelete = ref<Role | null>(null)

function openDelete(row: Role) {
  roleToDelete.value = row
  showDeleteDialog.value = true
}

async function confirmDelete() {
  if (!roleToDelete.value) return
  showDeleteDialog.value = false
  await roleApi.remove(roleToDelete.value.id)
  ElMessage.success('角色已删除')
  roleToDelete.value = null
  fetchList()
}

onMounted(fetchList)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div class="page-title">
        <h2>角色管理</h2>
        <span class="page-subtitle">管理系统业务角色与描述</span>
      </div>
      <div class="page-actions">
        <el-button type="primary" class="btn-primary" @click="openAdd">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
          新增角色
        </el-button>
      </div>
    </div>

    <div class="table-card">
      <el-table v-loading="loading" :data="roles" row-key="id">
        <el-table-column label="角色名称" prop="roleName" min-width="160">
          <template #default="{ row }">
            <div class="role-cell">
              <div class="role-icon">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/>
                </svg>
              </div>
              <span class="role-name">{{ row.roleName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="描述" prop="description" min-width="200">
          <template #default="{ row }">
            <span class="desc-text">{{ row.description || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="gmtCreated" min-width="160">
          <template #default="{ row }">
            <span class="time-text">{{ row.gmtCreated?.slice(0, 16) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <button class="action-btn" title="编辑" @click="openEdit(row)">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                </svg>
              </button>
              <button class="action-btn danger" title="删除" @click="openDelete(row)">
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
              <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/>
            </svg>
            <p>暂无角色数据</p>
          </div>
        </template>
      </el-table>
    </div>

    <!-- 新增角色弹窗 -->
    <el-dialog v-model="addDialogVisible" title="新增角色" width="440px" :close-on-click-modal="false" class="role-dialog">
      <el-form ref="addFormRef" :model="addForm" :rules="addRules" label-width="80px" class="role-form">
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="addForm.roleName" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="addForm.description" type="textarea" :rows="3" placeholder="角色描述（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="addSubmitting" @click="handleAdd">创建</el-button>
      </template>
    </el-dialog>

    <!-- 编辑角色弹窗 -->
    <el-dialog v-model="editDialogVisible" title="编辑角色" width="440px" :close-on-click-modal="false" class="role-dialog">
      <el-form ref="editFormRef" :model="editForm" :rules="addRules" label-width="80px" class="role-form">
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="editForm.roleName" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="3" placeholder="角色描述（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSubmitting" @click="handleEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 删除确认 -->
    <teleport to="body">
      <div v-if="showDeleteDialog" class="confirm-mask" @click.self="showDeleteDialog = false">
        <div class="confirm-dialog">
          <div class="confirm-icon" style="background:#fee2e2;color:#dc2626">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/><path d="M9 6V4h6v2"/>
            </svg>
          </div>
          <div class="confirm-body">
            <p class="confirm-title">删除角色</p>
            <p class="confirm-desc">确定要删除角色「{{ roleToDelete?.roleName }}」吗？此操作不可撤销。</p>
          </div>
          <div class="confirm-actions">
            <button class="confirm-cancel" @click="showDeleteDialog = false">取消</button>
            <button class="confirm-ok" @click="confirmDelete">删除</button>
          </div>
        </div>
      </div>
    </teleport>
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

.page-title h2 { font-size: 18px; font-weight: 600; color: var(--color-text-primary); margin-bottom: 2px; }
.page-subtitle { font-size: 13px; color: var(--color-text-secondary); }
.page-actions { display: flex; align-items: center; gap: 10px; }

.btn-primary {
  background: var(--color-accent);
  border-color: var(--color-accent);
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  gap: 6px;
}
.btn-primary:hover { background: var(--color-accent-light); border-color: var(--color-accent-light); }

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
.table-card :deep(.el-table th) { background: var(--color-bg-input); color: var(--color-text-secondary); font-weight: 500; font-size: 12px; }
.table-card :deep(.el-table td) { border-bottom-color: var(--color-border); }
.table-card :deep(.el-table tr:hover td) { background: #faf9f7; }

.role-cell { display: flex; align-items: center; gap: 8px; }

.role-icon {
  width: 28px;
  height: 28px;
  border-radius: var(--radius-sm);
  background: var(--color-accent-bg);
  color: var(--color-accent);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.role-name { font-weight: 500; color: var(--color-text-primary); }
.desc-text { font-size: 13px; color: var(--color-text-secondary); }
.time-text { font-size: 12px; color: var(--color-text-secondary); }

.row-actions { display: flex; align-items: center; gap: 4px; }

.action-btn {
  width: 30px; height: 30px;
  border-radius: var(--radius-sm);
  display: flex; align-items: center; justify-content: center;
  color: var(--color-text-secondary);
  transition: background var(--transition-fast), color var(--transition-fast);
}
.action-btn:hover { background: var(--color-bg-input); color: var(--color-text-primary); }
.action-btn.danger:hover { background: #fef2f2; color: var(--color-error); }

.empty-state {
  padding: 48px 0;
  display: flex; flex-direction: column; align-items: center; gap: 12px;
  color: var(--color-text-disabled); font-size: 13px;
}

.role-dialog :deep(.el-dialog__header) { padding: 20px 24px 16px; border-bottom: 1px solid var(--color-border); margin: 0; }
.role-dialog :deep(.el-dialog__title) { font-size: 15px; font-weight: 600; color: var(--color-text-primary); }
.role-dialog :deep(.el-dialog__body) { padding: 20px 24px; }
.role-dialog :deep(.el-dialog__footer) { padding: 14px 24px; border-top: 1px solid var(--color-border); }

.role-form :deep(.el-input__wrapper),
.role-form :deep(.el-textarea__inner) {
  box-shadow: none !important;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-input);
}
.role-form :deep(.el-input__wrapper:hover),
.role-form :deep(.el-input__wrapper.is-focus),
.role-form :deep(.el-textarea__inner:focus) {
  border-color: var(--color-accent) !important;
}

/* 删除确认 dialog */
.confirm-mask {
  position: fixed; inset: 0;
  background: rgba(0,0,0,0.3);
  display: flex; align-items: flex-start; justify-content: center;
  padding-top: 8vh; z-index: 9999;
}
.confirm-dialog {
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 24px; width: 320px;
  display: flex; flex-direction: column; gap: 12px;
  box-shadow: var(--shadow-lg);
}
.confirm-icon { width: 40px; height: 40px; border-radius: var(--radius-md); display: flex; align-items: center; justify-content: center; }
.confirm-title { font-size: 15px; font-weight: 600; color: var(--color-text-primary); margin: 0; }
.confirm-desc { font-size: 13px; color: var(--color-text-secondary); margin: 2px 0 0; }
.confirm-actions { display: flex; gap: 8px; justify-content: flex-end; margin-top: 4px; }
.confirm-cancel { padding: 7px 16px; border-radius: var(--radius-sm); font-size: 13px; color: var(--color-text-secondary); background: var(--color-bg-input); border: 1px solid var(--color-border); transition: background var(--transition-fast); }
.confirm-cancel:hover { background: var(--color-border); color: var(--color-text-primary); }
.confirm-ok { padding: 7px 16px; border-radius: var(--radius-sm); font-size: 13px; font-weight: 500; background: var(--color-error); color: white; transition: opacity var(--transition-fast); }
.confirm-ok:hover { opacity: 0.88; }
</style>
