<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { dataSourceApi } from '@/api/datasource'
import type { DataSourceVO, DataSourceSaveDTO } from '@/types'
import type { DataSourceUpdateDTO } from '@/api/datasource'

// ── 列表状态 ──────────────────────────────────────────
const loading = ref(false)
const tableData = ref<DataSourceVO[]>([])
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
    const res = await dataSourceApi.page(pagination.current, pagination.size, searchName.value || undefined)
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

// ── 多选 ──────────────────────────────────────────────
const selectedIds = ref<number[]>([])
function handleSelectionChange(rows: DataSourceVO[]) {
  selectedIds.value = rows.map(r => r.id)
}

// ── 自定义确认弹窗 ─────────────────────────────────────
const confirmVisible = ref(false)
const confirmTitle = ref('')
const confirmMsg = ref('')
let confirmResolve: ((v: boolean) => void) | null = null

function showConfirm(title: string, msg: string): Promise<boolean> {
  confirmTitle.value = title
  confirmMsg.value = msg
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

async function handleBatchDelete() {
  if (!selectedIds.value.length) return
  const ok = await showConfirm('批量删除', `确定删除选中的 ${selectedIds.value.length} 个数据源吗？`)
  if (!ok) return
  await dataSourceApi.batchRemove(selectedIds.value)
  ElMessage.success({ message: '批量删除成功', duration: 2000 })
  fetchList()
}

// ── 新增 / 编辑弹窗 ───────────────────────────────────
type DialogMode = 'add' | 'edit'
const dialogVisible = ref(false)
const dialogMode = ref<DialogMode>('add')
const formRef = ref()
const submitting = ref(false)

const defaultForm = (): DataSourceSaveDTO & { id?: number } => ({
  connName: '', dbType: '', host: '', port: undefined as any,
  databaseName: '', username: '', password: '',
})

const form = reactive(defaultForm())
const dialogTitle = computed(() => dialogMode.value === 'add' ? '新增数据源' : '编辑数据源')

const formRules = computed(() => ({
  connName:     [{ required: true, message: '请输入连接名称', trigger: 'blur' }],
  dbType:       [{ required: true, message: '请选择数据库类型', trigger: 'change' }],
  host:         [{ required: true, message: '请输入主机地址', trigger: 'blur' }],
  port:         [{ required: true, message: '请输入端口号', trigger: 'blur' }],
  databaseName: [{ required: true, message: '请输入数据库名', trigger: 'blur' }],
  username:     [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password:     [{ required: dialogMode.value === 'add', message: '请输入密码', trigger: 'blur' }],
}))

function openAdd() {
  dialogMode.value = 'add'
  Object.assign(form, defaultForm())
  dialogVisible.value = true
}

function openEdit(row: DataSourceVO) {
  dialogMode.value = 'edit'
  Object.assign(form, { id: row.id, connName: row.connName, dbType: row.dbType,
    host: row.host, port: row.port, databaseName: row.databaseName,
    username: row.username, password: '' })
  dialogVisible.value = true
}

async function handleSubmit() {
  // 手动验证必填字段
  if (!form.connName || !form.dbType || !form.host || !form.port || !form.databaseName || !form.username) {
    ElMessage.warning({ message: '请填写完整的数据库连接信息', duration: 2000 })
    return
  }

  // 新增模式下密码必填
  if (dialogMode.value === 'add' && !form.password) {
    ElMessage.warning({ message: '请输入密码', duration: 2000 })
    return
  }

  submitting.value = true
  try {
    if (dialogMode.value === 'add') {
      await dataSourceApi.save(form)
      ElMessage.success({ message: '数据源添加成功', duration: 2000 })
    } else {
      await dataSourceApi.update(form as DataSourceUpdateDTO)
      ElMessage.success({ message: '数据源更新成功', duration: 2000 })
    }
    dialogVisible.value = false
    fetchList()
  } finally {
    submitting.value = false
  }
}

// ── 测试连接 ──────────────────────────────────────────
const testing = ref(false)

async function handleTest() {
  // 手动验证必填字段
  if (!form.connName || !form.dbType || !form.host || !form.port || !form.databaseName || !form.username || !form.password) {
    ElMessage.warning({ message: '请填写完整的数据库连接信息', duration: 2000 })
    return
  }

  testing.value = true
  try {
    await dataSourceApi.test(form)
    ElMessage.success({ message: '连接成功', duration: 2000 })
  } catch {
    // HTTP 拦截器已经显示了错误提示，这里不再重复显示
  } finally {
    testing.value = false
  }
}

// ── 删除 ──────────────────────────────────────────────
async function handleDelete(row: DataSourceVO) {
  const ok = await showConfirm('删除确认', `确定删除数据源「${row.connName}」吗？`)
  if (!ok) return
  await dataSourceApi.remove(row.id)
  ElMessage.success({ message: '删除成功', duration: 2000 })
  fetchList()
}

// ── 刷新 Schema ───────────────────────────────────────
const refreshingId = ref<number | null>(null)
async function handleRefresh(row: DataSourceVO) {
  refreshingId.value = row.id
  try {
    await dataSourceApi.refreshTables(row.id)
    ElMessage.success({ message: 'Schema 缓存已刷新', duration: 2000 })
  } finally {
    refreshingId.value = null
  }
}

// ── DB 类型默认端口 ───────────────────────────────────
const dbTypeOptions = [
  { label: 'MySQL',      value: 'mysql',      port: 3306 },
  { label: 'PostgreSQL', value: 'postgresql', port: 5432 },
  { label: 'SQL Server', value: 'sqlserver',  port: 1433 },
]

const dbTypeDropdownOpen = ref(false)

function toggleDbTypeDropdown() {
  dbTypeDropdownOpen.value = !dbTypeDropdownOpen.value
}

function selectDbType(value: string) {
  if (form.dbType === value) {
    // 点击已选中的项，取消选中
    form.dbType = ''
    form.port = undefined as any
  } else {
    // 选中新项
    form.dbType = value
    onDbTypeChange(value)
  }
  dbTypeDropdownOpen.value = false
}

function onDbTypeChange(val: string) {
  const opt = dbTypeOptions.find(o => o.value === val)
  if (opt) form.port = opt.port
}

const dbTypeBadge: Record<string, string> = {
  mysql: '#00758f', postgresql: '#336791', sqlserver: '#cc2927',
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
  // 点击外部关闭下拉框
  const closeDropdown = (e: MouseEvent) => {
    const target = e.target as HTMLElement
    if (!target.closest('.custom-select')) {
      dbTypeDropdownOpen.value = false
    }
  }
  document.addEventListener('click', closeDropdown)

  // 保存引用以便清理
  ;(window as any).__dbTypeCloseHandler = closeDropdown
})

onUnmounted(() => {
  resizeObserver?.disconnect()
  if ((window as any).__dbTypeCloseHandler) {
    document.removeEventListener('click', (window as any).__dbTypeCloseHandler)
    delete (window as any).__dbTypeCloseHandler
  }
})
</script>

<template>
  <div class="page">
    <!-- 页头 -->
    <div class="page-header">
      <div class="page-title">
        <h2>数据源管理</h2>
        <span class="page-subtitle">管理数据库连接配置</span>
      </div>
      <div class="page-actions">
        <el-input
          v-model="searchName"
          placeholder="搜索连接名称..."
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
        <el-button
          v-if="selectedIds.length"
          class="btn-danger"
          @click="handleBatchDelete"
        >
          删除 {{ selectedIds.length }} 项
        </el-button>
        <el-button type="primary" class="btn-primary" @click="openAdd">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
          新增数据源
        </el-button>
      </div>
    </div>

    <!-- 表格 -->
    <div class="table-card">
      <div class="table-body" :class="{ 'is-empty': !loading && tableData.length === 0 }" ref="tableBodyRef">
      <el-table
        v-loading="loading"
        :data="tableData"
        row-key="id"
        :row-style="{ height: rowHeight + 'px' }"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="44" />
        <el-table-column label="连接名称" prop="connName" min-width="160">
          <template #default="{ row }">
            <div class="conn-name">
              <span class="db-badge" :style="{ background: dbTypeBadge[row.dbType] ?? '#888' }">
                {{ row.dbType.toUpperCase() }}
              </span>
              <span class="name-text">{{ row.connName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="主机" min-width="180">
          <template #default="{ row }">
            <span class="mono">{{ row.host }}:{{ row.port }}</span>
          </template>
        </el-table-column>
        <el-table-column label="数据库" prop="databaseName" min-width="120" />
        <el-table-column label="用户名" prop="username" min-width="120" />
        <el-table-column label="创建时间" prop="gmtCreated" min-width="160">
          <template #default="{ row }">
            <span class="time-text">{{ row.gmtCreated?.slice(0, 16) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <button class="action-btn" title="刷新 Schema" @click="handleRefresh(row)">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" :class="{ spinning: refreshingId === row.id }">
                  <polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
                </svg>
              </button>
              <button class="action-btn" title="编辑" @click="openEdit(row)">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                </svg>
              </button>
              <button class="action-btn danger" title="删除" @click="handleDelete(row)">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/><path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/>
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
            <ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3"/><path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"/>
          </svg>
          <p>暂无数据源，点击「新增数据源」开始配置</p>
        </div>
      </div>
      </div>

      <!-- 分页 -->
      <div class="pagination">
        <span class="pagination-total">共 <strong>{{ total }}</strong> 条记录</span>
        <el-pagination
          :current-page="pagination.current"
          :page-size="pagination.size"
          :total="total"
          layout="prev, pager, next"
          background
          @current-change="handlePageChange"
        />
      </div>
    </div>

    <!-- 自定义确认弹窗 -->
    <Teleport to="body">
      <div v-if="confirmVisible" class="confirm-overlay" @click.self="onConfirmCancel">
        <div class="confirm-dialog">
          <div class="confirm-title">{{ confirmTitle }}</div>
          <div class="confirm-msg">{{ confirmMsg }}</div>
          <div class="confirm-actions">
            <button class="confirm-btn-cancel" @click="onConfirmCancel">取消</button>
            <button class="confirm-btn-ok" @click="onConfirmOk">删除</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 新增/编辑弹窗 -->
    <Teleport to="body">
      <div v-if="dialogVisible" class="custom-dialog-mask">
        <div class="custom-dialog">
          <!-- 头部 -->
          <div class="custom-dialog-header">
            <span class="custom-dialog-title">{{ dialogTitle }}</span>
            <button class="custom-dialog-close" @click="dialogVisible = false">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
            </button>
          </div>

          <!-- 表单 -->
          <el-form ref="formRef" :model="form" class="custom-form">
            <!-- 连接名称 -->
            <div class="form-field" :class="{ 'has-value': form.connName, 'has-error': false }">
              <el-input v-model="form.connName" class="field-input" />
              <label class="field-label">连接名称</label>
              <span class="field-hint">如：生产环境 MySQL</span>
            </div>

            <!-- 数据库类型 -->
            <div class="form-field" :class="{ 'has-value': form.dbType }">
              <div class="custom-select" :class="{ 'is-open': dbTypeDropdownOpen }" @click.stop="toggleDbTypeDropdown">
                <div class="custom-select-trigger">
                  <span class="custom-select-value">{{ form.dbType ? dbTypeOptions.find(o => o.value === form.dbType)?.label : '' }}</span>
                  <svg class="custom-select-arrow" width="12" height="12" viewBox="0 0 12 12" fill="none">
                    <path d="M3 4.5L6 7.5L9 4.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
                  </svg>
                </div>
                <div v-if="dbTypeDropdownOpen" class="custom-select-dropdown" @click.stop>
                  <div
                    v-for="opt in dbTypeOptions"
                    :key="opt.value"
                    class="custom-select-option"
                    :class="{ 'is-selected': form.dbType === opt.value }"
                    @click="selectDbType(opt.value)"
                  >
                    <span>{{ opt.label }}</span>
                    <svg v-if="form.dbType === opt.value" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                      <polyline points="20 6 9 17 4 12"/>
                    </svg>
                  </div>
                </div>
              </div>
              <label class="field-label">数据库类型</label>
            </div>

            <!-- 主机 + 端口 -->
            <div class="form-row">
              <div class="form-field" :class="{ 'has-value': form.host }">
                <el-input v-model="form.host" class="field-input" />
                <label class="field-label">主机地址</label>
                <span class="field-hint">如：127.0.0.1</span>
              </div>
              <div class="form-field form-field--port" :class="{ 'has-value': form.port }">
                <el-input v-model.number="form.port" class="field-input" />
                <label class="field-label">端口</label>
              </div>
            </div>

            <!-- 数据库名 -->
            <div class="form-field" :class="{ 'has-value': form.databaseName }">
              <el-input v-model="form.databaseName" class="field-input" />
              <label class="field-label">数据库名</label>
            </div>

            <!-- 用户名 + 密码 -->
            <div class="form-row">
              <div class="form-field" :class="{ 'has-value': form.username }">
                <el-input v-model="form.username" class="field-input" />
                <label class="field-label">用户名</label>
              </div>
              <div class="form-field" :class="{ 'has-value': form.password }">
                <el-input v-model="form.password" type="password" show-password class="field-input" />
                <label class="field-label">{{ dialogMode === 'edit' ? '密码（不填则不修改）' : '密码' }}</label>
              </div>
            </div>
          </el-form>

          <!-- 底部 -->
          <div class="custom-dialog-footer">
            <button class="footer-btn btn-test-conn" :disabled="testing" @click="handleTest">
              <svg v-if="testing" class="spinning" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><path d="M21 12a9 9 0 1 1-6.22-8.56"/></svg>
              <span v-if="!testing">测试连接</span>
            </button>
            <div class="footer-right">
              <button class="footer-btn btn-cancel" @click="dialogVisible = false">取消</button>
              <button class="footer-btn btn-submit" :disabled="submitting" @click="handleSubmit">
                <svg v-if="submitting" class="spinning" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><path d="M21 12a9 9 0 1 1-6.22-8.56"/></svg>
                {{ dialogMode === 'add' ? '添加' : '保存' }}
              </button>
            </div>
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

/* ── 页头 ── */
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
  width: 220px;
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

.btn-danger {
  border-radius: var(--radius-md);
  color: var(--color-error);
  border-color: var(--color-error);
}

.btn-danger:hover {
  background: #fef2f2;
}

/* ── 表格卡片 ── */
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

.table-card :deep(.el-table) {
  font-size: 13px;
}

.table-card :deep(.el-table th) {
  background: var(--color-bg-input);
  color: var(--color-text-secondary);
  font-weight: 500;
  font-size: 12px;
}

.table-card :deep(.el-table td) {
  border-bottom-color: var(--color-border);
}

.table-card :deep(.el-table tr:hover td) {
  background: #faf9f7;
}

/* 连接名称列 */
.conn-name {
  display: flex;
  align-items: center;
  gap: 8px;
}

.db-badge {
  padding: 2px 6px;
  border-radius: var(--radius-sm);
  font-size: 10px;
  font-weight: 700;
  color: white;
  letter-spacing: 0.5px;
  flex-shrink: 0;
}

.name-text {
  font-weight: 500;
  color: var(--color-text-primary);
}

.mono {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--color-text-secondary);
}

.time-text {
  font-size: 12px;
  color: var(--color-text-secondary);
}

/* 操作按钮 */
.row-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

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

.action-btn:hover {
  background: var(--color-bg-input);
  color: var(--color-text-primary);
}

.action-btn.danger:hover {
  background: #fef2f2;
  color: var(--color-error);
}

.spinning {
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to   { transform: rotate(360deg); }
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  color: var(--color-text-disabled);
  font-size: 13px;
}

/* 分页 */
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

/* ── 自定义新增/编辑弹窗 ── */
.custom-dialog-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(3px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9998;
  animation: fade-in 0.15s ease;
}

@keyframes fade-in {
  from { opacity: 0; }
  to   { opacity: 1; }
}

.custom-dialog {
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: 20px;
  width: 540px;
  box-shadow: 0 24px 64px rgba(0,0,0,0.16), 0 4px 16px rgba(0,0,0,0.08);
  animation: dialog-in 0.18s ease;
  overflow: hidden;
}

.custom-dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px 18px;
  border-bottom: 1px solid var(--color-border);
}

.custom-dialog-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.custom-dialog-close {
  width: 28px;
  height: 28px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-secondary);
  transition: background 0.15s, color 0.15s;
}
.custom-dialog-close:hover {
  background: var(--color-bg-input);
  color: var(--color-text-primary);
}

/* ── 浮动 label 表单 ── */
.custom-form {
  padding: 20px 24px 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

/* 隐藏 Element Plus 表单验证错误提示 */
.custom-form :deep(.el-form-item__error) {
  display: none !important;
}

.custom-dialog :deep(.el-form-item__error) {
  display: none !important;
  visibility: hidden !important;
  opacity: 0 !important;
  height: 0 !important;
  overflow: hidden !important;
}

.form-row {
  display: flex;
  gap: 12px;
}

.form-field {
  position: relative;
  flex: 1;
  margin-bottom: 10px;
}

.form-field--port {
  flex: 0 0 110px;
}

.field-input :deep(.el-input__wrapper),
.field-input :deep(.el-input-number .el-input__wrapper) {
  box-shadow: none !important;
  border: 1.5px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-bg-input);
  padding: 22px 12px 8px;
  height: 52px;
  transition: border-color 0.15s;
}

.field-input :deep(.el-input__inner),
.field-input :deep(.el-input-number .el-input__inner) {
  font-size: 14px;
  color: var(--color-text-primary);
  height: auto;
  line-height: 1.4;
}

.field-input :deep(.el-input__wrapper:hover),
.field-input :deep(.el-input__wrapper.is-focus) {
  border-color: var(--color-accent) !important;
}

/* 原生 select 样式 */
.custom-select {
  position: relative;
  width: 100%;
  cursor: pointer;
}

.custom-select-trigger {
  width: 100%;
  height: 52px;
  padding: 22px 12px 8px;
  border: 1.5px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-bg-input);
  font-size: 14px;
  color: var(--color-text-primary);
  transition: border-color 0.15s;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.custom-select-trigger:hover,
.custom-select.is-open .custom-select-trigger {
  border-color: var(--color-accent);
}

.custom-select-value {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.custom-select-arrow {
  flex-shrink: 0;
  color: var(--color-text-secondary);
  transition: transform 0.2s;
}

.custom-select.is-open .custom-select-arrow {
  transform: rotate(180deg);
}

.custom-select-dropdown {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  right: 0;
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  z-index: 1000;
  overflow: hidden;
  animation: dropdown-in 0.15s ease;
}

@keyframes dropdown-in {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.custom-select-option {
  padding: 10px 12px;
  font-size: 14px;
  color: var(--color-text-primary);
  cursor: pointer;
  transition: background 0.15s;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.custom-select-option:hover {
  background: var(--color-bg-input);
}

.custom-select-option.is-selected {
  color: var(--color-accent);
  background: var(--color-accent-bg, rgba(217, 119, 6, 0.08));
}

.custom-select-option svg {
  flex-shrink: 0;
  color: var(--color-accent);
}


.field-label {
  position: absolute;
  left: 13px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 14px;
  color: var(--color-text-secondary);
  pointer-events: none;
  transition: top 0.15s ease, font-size 0.15s ease, color 0.15s ease;
  transform-origin: left center;
}

/* 有值或聚焦时 label 上浮 */
.form-field.has-value .field-label,
.form-field:focus-within .field-label {
  top: 10px;
  transform: translateY(0);
  font-size: 11px;
  color: var(--color-accent);
}

.field-hint {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 11px;
  color: var(--color-text-disabled);
  pointer-events: none;
  transition: opacity 0.15s;
}

.form-field.has-value .field-hint,
.form-field:focus-within .field-hint {
  opacity: 0;
}

/* ── 弹窗底部 ── */
.custom-dialog-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 24px;
  border-top: 1px solid var(--color-border);
}

.footer-right {
  display: flex;
  gap: 8px;
}

.footer-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 18px;
  border-radius: var(--radius-full);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s, opacity 0.15s;
}

.footer-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-test-conn {
  border: 1.5px solid var(--color-accent);
  color: var(--color-accent);
  background: transparent;
}
.btn-test-conn:hover:not(:disabled) {
  background: var(--color-accent-bg, rgba(217,119,6,0.08));
}

.btn-cancel {
  border: 1px solid var(--color-border);
  color: var(--color-text-secondary);
  background: transparent;
}
.btn-cancel:hover {
  background: var(--color-bg-input);
  color: var(--color-text-primary);
}

.btn-submit {
  border: none;
  background: var(--color-accent);
  color: white;
}
.btn-submit:hover:not(:disabled) {
  opacity: 0.88;
}

.test-result {
  margin: 4px 0 0;
  padding: 8px 12px;
  border-radius: var(--radius-md);
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 6px;
  animation: slide-in 0.2s ease;
}

@keyframes slide-in {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.test-ok {
  background: #f0fdf4;
  color: #16a34a;
  border: 1px solid #bbf7d0;
}

.test-fail {
  background: #fef2f2;
  color: #dc2626;
  border: 1px solid #fecaca;
}

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

@keyframes dialog-in {
  from { opacity: 0; transform: scale(0.95) translateY(6px); }
  to   { opacity: 1; transform: scale(1) translateY(0); }
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
  background: var(--color-error, #dc2626);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
  transition: opacity 0.15s;
}
.confirm-btn-ok:hover { opacity: 0.85; }

/* 全局隐藏 Element Plus 表单错误提示 */
:global(.el-form-item__error) {
  display: none !important;
}
</style>
