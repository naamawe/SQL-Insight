<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
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
  ElMessage.success('批量删除成功')
  fetchList()
}

// ── 新增 / 编辑弹窗 ───────────────────────────────────
type DialogMode = 'add' | 'edit'
const dialogVisible = ref(false)
const dialogMode = ref<DialogMode>('add')
const formRef = ref()
const submitting = ref(false)

const defaultForm = (): DataSourceSaveDTO & { id?: number } => ({
  connName: '', dbType: 'mysql', host: '', port: 3306,
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
  testResult.value = null
  dialogVisible.value = true
}

function openEdit(row: DataSourceVO) {
  dialogMode.value = 'edit'
  Object.assign(form, { id: row.id, connName: row.connName, dbType: row.dbType,
    host: row.host, port: row.port, databaseName: row.databaseName,
    username: row.username, password: '' })
  testResult.value = null
  dialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value?.validate()
  submitting.value = true
  try {
    if (dialogMode.value === 'add') {
      await dataSourceApi.save(form)
      ElMessage.success('数据源添加成功')
    } else {
      await dataSourceApi.update(form as DataSourceUpdateDTO)
      ElMessage.success('数据源更新成功')
    }
    dialogVisible.value = false
    fetchList()
  } finally {
    submitting.value = false
  }
}

// ── 测试连接 ──────────────────────────────────────────
const testing = ref(false)
const testResult = ref<{ ok: boolean; msg: string } | null>(null)

async function handleTest() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  testing.value = true
  testResult.value = null
  try {
    await dataSourceApi.test(form)
    testResult.value = { ok: true, msg: '连接成功' }
  } catch (e: any) {
    testResult.value = { ok: false, msg: e?.response?.data?.message || e?.message || '连接失败' }
  } finally {
    testing.value = false
  }
}

// ── 删除 ──────────────────────────────────────────────
async function handleDelete(row: DataSourceVO) {
  const ok = await showConfirm('删除确认', `确定删除数据源「${row.connName}」吗？`)
  if (!ok) return
  await dataSourceApi.remove(row.id)
  ElMessage.success('删除成功')
  fetchList()
}

// ── 刷新 Schema ───────────────────────────────────────
const refreshingId = ref<number | null>(null)
async function handleRefresh(row: DataSourceVO) {
  refreshingId.value = row.id
  try {
    await dataSourceApi.refreshTables(row.id)
    ElMessage.success('Schema 缓存已刷新')
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

function onDbTypeChange(val: string) {
  const opt = dbTypeOptions.find(o => o.value === val)
  if (opt) form.port = opt.port
}

const dbTypeBadge: Record<string, string> = {
  mysql: '#00758f', postgresql: '#336791', sqlserver: '#cc2927',
}

onMounted(fetchList)
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
      <el-table
        v-loading="loading"
        :data="tableData"
        row-key="id"
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
        <template #empty>
          <div class="empty-state">
            <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" style="color: var(--color-text-disabled)">
              <ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3"/><path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"/>
            </svg>
            <p>暂无数据源，点击「新增数据源」开始配置</p>
          </div>
        </template>
      </el-table>

      <!-- 分页 -->
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
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="520px"
      :close-on-click-modal="false"
      append-to-body
      class="ds-dialog"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-width="100px"
        class="ds-form"
      >
        <el-form-item label="连接名称" prop="connName">
          <el-input v-model="form.connName" placeholder="如：生产环境 MySQL" />
        </el-form-item>
        <el-form-item label="数据库类型" prop="dbType">
          <el-select v-model="form.dbType" style="width: 100%" @change="onDbTypeChange">
            <el-option
              v-for="opt in dbTypeOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="主机地址" prop="host">
          <el-input v-model="form.host" placeholder="127.0.0.1" />
        </el-form-item>
        <el-form-item label="端口" prop="port">
          <el-input-number v-model="form.port" :min="1" :max="65535" style="width: 100%" />
        </el-form-item>
        <el-form-item label="数据库名" prop="databaseName">
          <el-input v-model="form.databaseName" placeholder="数据库名称" />
        </el-form-item>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="数据库用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="dialogMode === 'edit' ? '不填则不修改密码' : '数据库密码'"
          />
        </el-form-item>
        <div v-if="testResult" class="test-result" :class="testResult.ok ? 'test-ok' : 'test-fail'">
          <svg v-if="testResult.ok" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>
          <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          {{ testResult.msg }}
        </div>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button class="btn-test" :loading="testing" @click="handleTest">测试连接</el-button>
          <div class="footer-right">
            <el-button @click="dialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="submitting" @click="handleSubmit">
              {{ dialogMode === 'add' ? '添加' : '保存' }}
            </el-button>
          </div>
        </div>
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
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.table-card :deep(.el-table) {
  flex: 1;
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
  padding: 48px 0;
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
  justify-content: flex-end;
  flex-shrink: 0;
}

.pagination :deep(.el-pagination.is-background .el-pager li.is-active) {
  background: var(--color-accent);
}

/* ── 弹窗 ── */
.ds-dialog :deep(.el-dialog__header) {
  padding: 20px 24px 16px;
  border-bottom: 1px solid var(--color-border);
  margin: 0;
}

.ds-dialog :deep(.el-dialog__title) {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.ds-dialog :deep(.el-dialog__body) {
  padding: 20px 24px;
}

.ds-dialog :deep(.el-dialog__footer) {
  padding: 14px 24px;
  border-top: 1px solid var(--color-border);
}

.ds-form :deep(.el-input__wrapper),
.ds-form :deep(.el-select .el-input__wrapper) {
  box-shadow: none !important;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-input);
}

.ds-form :deep(.el-input__wrapper:hover),
.ds-form :deep(.el-input__wrapper.is-focus),
.ds-form :deep(.el-select .el-input__wrapper:hover),
.ds-form :deep(.el-select .el-input.is-focus .el-input__wrapper) {
  border-color: var(--color-accent) !important;
}

.ds-form :deep(.el-input-number .el-input__wrapper) {
  box-shadow: none !important;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-input);
}

.dialog-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.footer-right {
  display: flex;
  gap: 8px;
}

.btn-test {
  border-radius: var(--radius-md);
  color: var(--color-accent);
  border-color: var(--color-accent);
}

.btn-test:hover {
  background: var(--color-accent-bg);
}

.test-result {
  margin: 4px 0 0;
  padding: 8px 12px;
  border-radius: var(--radius-md);
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 6px;
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
</style>
