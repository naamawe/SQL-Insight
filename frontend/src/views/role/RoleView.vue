<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { roleApi } from '@/api/user'
import type { Role } from '@/types'

const loading = ref(false)
const roles = ref<Role[]>([])
const total = ref(0)
const searchName = ref('')
const pagination = reactive({ current: 1, size: 10 })

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
    const res = await roleApi.page(pagination.current, pagination.size, searchName.value || undefined) as any
    roles.value = res.records
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
const addForm = reactive({ roleName: '', description: '' })
let addErrorTimer: ReturnType<typeof setTimeout> | null = null

function setAddError(msg: string) {
  addError.value = msg
  if (addErrorTimer) clearTimeout(addErrorTimer)
  addErrorTimer = setTimeout(() => { addError.value = '' }, 4000)
}

function openAdd() {
  Object.assign(addForm, { roleName: '', description: '' })
  addError.value = ''
  addDialogVisible.value = true
}

async function handleAdd() {
  if (!addForm.roleName.trim()) { setAddError('请输入角色名称'); return }
  addError.value = ''
  addSubmitting.value = true
  try {
    await roleApi.save(addForm)
    addDialogVisible.value = false
    ElMessage.success({ message: '角色创建成功', duration: 2000 })
    fetchList()
  } catch (e: any) {
    setAddError(e.message || '创建失败，请重试')
  } finally {
    addSubmitting.value = false
  }
}

// ── 编辑弹窗 ──────────────────────────────────────────
const editDialogVisible = ref(false)
const editSubmitting = ref(false)
const editError = ref('')
const editForm = reactive({ id: 0, roleName: '', description: '' })
let editErrorTimer: ReturnType<typeof setTimeout> | null = null

function setEditError(msg: string) {
  editError.value = msg
  if (editErrorTimer) clearTimeout(editErrorTimer)
  editErrorTimer = setTimeout(() => { editError.value = '' }, 4000)
}

function openEdit(row: Role) {
  Object.assign(editForm, { id: row.id, roleName: row.roleName, description: row.description })
  editError.value = ''
  editDialogVisible.value = true
}

async function handleEdit() {
  if (!editForm.roleName.trim()) { setEditError('请输入角色名称'); return }
  editError.value = ''
  editSubmitting.value = true
  try {
    await roleApi.update(editForm)
    editDialogVisible.value = false
    ElMessage.success({ message: '角色已更新', duration: 2000 })
    fetchList()
  } catch (e: any) {
    setEditError(e.message || '保存失败，请重试')
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
  try {
    await roleApi.remove(roleToDelete.value.id)
    ElMessage.success({ message: '角色已删除', duration: 2000 })
    roleToDelete.value = null
    fetchList()
  } catch {
    // 错误已由 http 拦截器统一展示
  }
}

onMounted(() => {
  fetchList()
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
    <div class="page-header">
      <div class="page-title">
        <h2>角色管理</h2>
        <span class="page-subtitle">管理系统业务角色与描述</span>
      </div>
      <div class="page-actions">
        <el-input
          v-model="searchName"
          placeholder="搜索角色名称…"
          clearable
          class="search-input"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix>
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
            </svg>
          </template>
        </el-input>
        <el-button type="primary" class="btn-primary" @click="openAdd">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
          新增角色
        </el-button>
      </div>
    </div>

    <div class="table-card">
      <div class="table-body" :class="{ 'is-empty': !loading && roles.length === 0 }" ref="tableBodyRef">
      <el-table v-loading="loading" :data="roles" row-key="id" :row-style="{ height: rowHeight + 'px' }">
        <el-table-column label="角色名称" prop="roleName" min-width="180" header-align="left">
          <template #default="{ row }">
            <div class="role-cell">
              <div class="role-icon">
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="2" y="7" width="20" height="14" rx="2" ry="2"/><path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"/>
                </svg>
              </div>
              <span class="role-name">{{ row.roleName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="描述" prop="description" min-width="260" header-align="left">
          <template #default="{ row }">
            <span class="desc-text">{{ row.description || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="gmtCreated" min-width="180" header-align="left">
          <template #default="{ row }">
            <span class="time-text">{{ row.gmtCreated?.slice(0, 16).replace('T', ' ') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right" align="center" header-align="center">
          <template #default="{ row }">
            <div class="row-actions">
              <button class="action-btn edit" title="编辑" @click="openEdit(row)">
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
        <template #empty><span /></template>
      </el-table>
      <div v-if="!loading && roles.length === 0" class="empty-overlay" :style="{ top: headerHeight + 'px' }">
        <div class="empty-state">
          <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" style="color: var(--color-text-disabled)">
            <rect x="2" y="7" width="20" height="14" rx="2" ry="2"/><path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"/>
          </svg>
          <p>暂无角色数据</p>
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

    <!-- 新增角色弹窗 -->
    <Teleport to="body">
      <div v-if="addDialogVisible" class="modal-overlay" @click.self="addDialogVisible = false">
        <div class="modal-dialog">
          <div class="modal-header">
            <div class="modal-header-icon add-icon">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
            </div>
            <div>
              <div class="modal-title">新增角色</div>
              <div class="modal-sub">创建一个新的业务角色</div>
            </div>
          </div>
          <div class="modal-body">
            <div class="field">
              <label class="field-label">角色名称 <span class="required">*</span></label>
              <input v-model="addForm.roleName" class="field-input" placeholder="请输入角色名称" maxlength="30" @input="addError = ''" @keydown.enter.prevent="handleAdd" />
            </div>
            <div class="field">
              <label class="field-label">描述</label>
              <textarea v-model="addForm.description" class="field-textarea" placeholder="角色描述（可选）" rows="3" />
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
              {{ addSubmitting ? '创建中…' : '创建角色' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 编辑角色弹窗 -->
    <Teleport to="body">
      <div v-if="editDialogVisible" class="modal-overlay" @click.self="editDialogVisible = false">
        <div class="modal-dialog">
          <div class="modal-header">
            <div class="modal-header-icon edit-icon">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
              </svg>
            </div>
            <div>
              <div class="modal-title">编辑角色</div>
              <div class="modal-sub">{{ editForm.roleName }}</div>
            </div>
          </div>
          <div class="modal-body">
            <div class="field">
              <label class="field-label">角色名称 <span class="required">*</span></label>
              <input v-model="editForm.roleName" class="field-input" placeholder="请输入角色名称" maxlength="30" @input="editError = ''" @keydown.enter.prevent="handleEdit" />
            </div>
            <div class="field">
              <label class="field-label">描述</label>
              <textarea v-model="editForm.description" class="field-textarea" placeholder="角色描述（可选）" rows="3" />
            </div>
            <div class="modal-error-wrap">
              <Transition name="modal-err">
                <div v-if="editError" class="modal-error">
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="flex-shrink:0"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
                  {{ editError }}
                </div>
              </Transition>
            </div>
          </div>
          <div class="modal-footer">
            <button class="modal-btn-cancel" @click="editDialogVisible = false">取消</button>
            <button class="modal-btn-ok" :disabled="editSubmitting" @click="handleEdit">
              {{ editSubmitting ? '保存中…' : '保存' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 删除确认 -->
    <Teleport to="body">
      <div v-if="showDeleteDialog" class="modal-overlay" @click.self="showDeleteDialog = false">
        <div class="modal-dialog">
          <div class="modal-header">
            <div class="modal-header-icon danger-icon">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/>
              </svg>
            </div>
            <div>
              <div class="modal-title">删除角色</div>
              <div class="modal-sub">此操作不可撤销</div>
            </div>
          </div>
          <div class="modal-body">
            <p class="delete-msg">确定要删除角色「<strong>{{ roleToDelete?.roleName }}</strong>」吗？</p>
          </div>
          <div class="modal-footer">
            <button class="modal-btn-cancel" @click="showDeleteDialog = false">取消</button>
            <button class="modal-btn-danger" @click="confirmDelete">删除</button>
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

.page-title h2 { font-size: 18px; font-weight: 600; color: var(--color-text-primary); margin-bottom: 2px; }
.page-subtitle { font-size: 13px; color: var(--color-text-secondary); }
.page-actions { display: flex; align-items: center; gap: 10px; }

.search-input { width: 200px; }
.search-input :deep(.el-input__wrapper) {
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  box-shadow: none !important;
  border-radius: var(--radius-md);
}
.search-input :deep(.el-input__wrapper:hover),
.search-input :deep(.el-input__wrapper.is-focus) { border-color: var(--color-accent); }

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
  min-height: 0;
  display: flex;
  flex-direction: column;
  width: 100%;
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.table-body { flex: 1; overflow: auto; position: relative; }

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

.role-cell { display: flex; align-items: center; gap: 10px; }

.role-icon {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: var(--color-accent-bg, #eef2ff);
  color: var(--color-accent);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.role-name { font-weight: 500; color: var(--color-text-primary); font-size: 13px; }
.desc-text { font-size: 13px; color: var(--color-text-secondary); }
.time-text { font-size: 12px; color: var(--color-text-secondary); }

.row-actions { display: flex; align-items: center; justify-content: center; gap: 6px; }

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

.action-btn.edit { color: var(--color-text-secondary); background: var(--color-bg-input); border-color: var(--color-border); }
.action-btn.edit:hover { color: var(--color-text-primary); background: var(--color-bg-surface); border-color: #bbb; }

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
  animation: dialog-in 0.15s ease;
  overflow: hidden;
}

@keyframes dialog-in {
  from { opacity: 0; transform: scale(0.95) translateY(6px); }
  to   { opacity: 1; transform: scale(1) translateY(0); }
}

.modal-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 22px 18px;
  border-bottom: 1px solid var(--color-border);
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

.add-icon  { background: var(--color-accent, #4f46e5)18; color: var(--color-accent); }
.edit-icon { background: #d9770618; color: #d97706; }
.danger-icon { background: #dc262618; color: #dc2626; }

.modal-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 2px;
}

.modal-sub {
  font-size: 12px;
  color: var(--color-text-secondary);
}

.modal-body {
  padding: 20px 22px 4px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 固定高度占位区：错误内容淡入淡出，弹窗高度始终不变 */
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
  line-height: 1.4;
  width: 100%;
}

.modal-err-enter-active { transition: opacity 0.18s ease; }
.modal-err-leave-active { transition: opacity 0.18s ease; }
.modal-err-enter-from  { opacity: 0; }
.modal-err-leave-to    { opacity: 0; }

.field { display: flex; flex-direction: column; gap: 6px; }

.field-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.4px;
}

.required { color: #dc2626; }

.field-input {
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
}

.field-input:focus {
  border-color: var(--color-accent);
  background: var(--color-bg-surface);
}

.field-input::placeholder { color: var(--color-text-disabled); }

.field-textarea {
  width: 100%;
  padding: 8px 12px;
  border: 1.5px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-bg-input);
  font-size: 13px;
  color: var(--color-text-primary);
  outline: none;
  resize: vertical;
  min-height: 80px;
  transition: border-color 0.15s;
  box-sizing: border-box;
  font-family: inherit;
  line-height: 1.5;
}

.field-textarea:focus {
  border-color: var(--color-accent);
  background: var(--color-bg-surface);
}

.field-textarea::placeholder { color: var(--color-text-disabled); }

.delete-msg {
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.6;
  margin: 0;
}

.delete-msg strong { color: var(--color-text-primary); }

.modal-footer {
  padding: 16px 22px;
  border-top: 1px solid var(--color-border);
  display: flex;
  justify-content: flex-end;
  gap: 8px;
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

.modal-btn-danger {
  padding: 7px 20px;
  border-radius: 999px;
  border: none;
  background: var(--color-error, #dc2626);
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.15s;
}
.modal-btn-danger:hover { opacity: 0.85; }
</style>
