<script setup lang="ts">
import { ref, watch, computed, onMounted, onUnmounted, nextTick, shallowRef } from 'vue'
import type { ChartConfigDTO } from '@/types'
import type { ECharts } from 'echarts'

type EChartsModule = typeof import('echarts')
let echartsModule: EChartsModule | null = null
async function getEcharts(): Promise<EChartsModule> {
  if (!echartsModule) {
    echartsModule = await import('echarts')
  }
  return echartsModule
}

const props = defineProps<{
  chartConfig: ChartConfigDTO | null
  tableData: Record<string, unknown>[]
  recordId: number
}>()

const emit = defineEmits<{
  (e: 'config-change', config: ChartConfigDTO): void
  (e: 'save-config', config: ChartConfigDTO): void
}>()

// 已应用的配置（驱动图表渲染）
const localConfig = ref<ChartConfigDTO>({
  type: 'table',
  xAxis: '',
  yAxis: [],
  title: '数据结果',
})

// 编辑草稿（面板中编辑，保存后才同步到 localConfig）
const draftConfig = ref<ChartConfigDTO>({
  type: 'table',
  xAxis: '',
  yAxis: [],
  title: '数据结果',
})

// ECharts 实例 - 使用 shallowRef 避免响应式问题
const chartInstance = shallowRef<ECharts | null>(null)
const chartRef = ref<HTMLDivElement>()
let resizeObserver: ResizeObserver | null = null

// 可用的 X/Y 轴选项（从数据列中提取）
const columnOptions = computed(() => {
  if (!props.tableData || props.tableData.length === 0) return []
  const firstRow = props.tableData[0]
  return firstRow ? Object.keys(firstRow) : []
})

// 图表类型选项
const chartTypeOptions = [
  { value: 'bar', label: '柱状图', icon: '▊' },
  { value: 'line', label: '折线图', icon: '∿' },
  { value: 'pie', label: '饼图', icon: '◕' },
  { value: 'scatter', label: '散点图', icon: '⁘' },
  { value: 'table', label: '表格', icon: '⊞' },
]

// 是否显示图表（非表格模式）
const isChartMode = computed(() => localConfig.value.type !== 'table')

// 标记是否正在从外部同步配置，避免循环触发
let syncingFromProps = false

// 监听外部配置变化，同步到 localConfig 和 draftConfig
watch(() => props.chartConfig, (newConfig) => {
  if (newConfig) {
    syncingFromProps = true
    localConfig.value = { ...newConfig }
    draftConfig.value = { ...newConfig }
    nextTick(() => { syncingFromProps = false })
  }
}, { immediate: true })

// 监听 localConfig 变化，触发重绘（不再实时 emit config-change）
watch(localConfig, () => {
  if (isChartMode.value) {
    nextTick(() => renderChart())
  }
}, { deep: true })

// 监听数据变化重绘
watch(() => props.tableData, () => {
  if (isChartMode.value) {
    nextTick(() => renderChart())
  }
}, { deep: true })

// 监听图表模式变化，初始化 ECharts
watch(isChartMode, (isChart) => {
  if (isChart) {
    nextTick(() => {
      setTimeout(async () => {
        await initChart()
        renderChart()
      }, 50)
    })
  } else {
    disposeChart()
  }
})

// 辅助函数：智能解析值为数字
function parseNumericValue(val: unknown): number {
  if (val === null || val === undefined) return 0
  if (typeof val === 'number') return val
  if (typeof val === 'string') {
    const cleaned = val.replace(/,/g, '')
    const num = parseFloat(cleaned)
    return isNaN(num) ? 0 : num
  }
  return 0
}

// 初始化 ECharts
async function initChart() {
  if (!chartRef.value) return
  if (!chartInstance.value) {
    const echarts = await getEcharts()
    chartInstance.value = echarts.init(chartRef.value)
    window.addEventListener('resize', handleResize)
    resizeObserver = new ResizeObserver((entries) => {
      const entry = entries[0]
      if (entry && entry.contentRect.width > 0 && chartInstance.value) {
        chartInstance.value.resize()
        renderChart()
      }
    })
    resizeObserver.observe(chartRef.value)
  }
}

// 销毁图表
function disposeChart() {
  if (chartInstance.value) {
    chartInstance.value.dispose()
    chartInstance.value = null
    window.removeEventListener('resize', handleResize)
    resizeObserver?.disconnect()
    resizeObserver = null
  }
}

function handleResize() {
  chartInstance.value?.resize()
}

// 渲染图表
function renderChart() {
  if (!chartInstance.value || !props.tableData.length) return
  const option = buildChartOption(localConfig.value, props.tableData)
  if (option) {
    chartInstance.value.setOption(option, true)
  }
}

// 构建 ECharts 配置
// eslint-disable-next-line @typescript-eslint/no-explicit-any
function buildChartOption(config: ChartConfigDTO, data: Record<string, unknown>[]): any | null {
  const { type, xAxis, yAxis, title } = config
  if (!xAxis || !yAxis || yAxis.length === 0) return null

  const xAxisData = data.map(row => String(row[xAxis] ?? ''))

  switch (type) {
    case 'bar':
    case 'line':
      return {
        title: { text: title, left: 'center', top: 10, textStyle: { fontSize: 14 } },
        tooltip: { trigger: 'axis' },
        legend: { bottom: 10, data: yAxis },
        grid: { left: '3%', right: '4%', bottom: '15%', containLabel: true },
        xAxis: {
          type: 'category',
          data: xAxisData,
          axisLabel: { rotate: xAxisData.length > 6 ? 30 : 0 }
        },
        yAxis: { type: 'value' },
        series: yAxis.map(name => ({
          name,
          type,
          data: data.map(row => parseNumericValue(row[name])),
          smooth: type === 'line',
        })),
      }

    case 'pie': {
      const yField = yAxis[0] || xAxis
      return {
        title: { text: title, left: 'center', top: 10, textStyle: { fontSize: 14 } },
        tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
        legend: {
          type: 'plain',
          orient: 'horizontal',
          bottom: 0,
          left: 'center',
          itemWidth: 10,
          itemHeight: 10,
          textStyle: { fontSize: 11 },
        },
        series: [{
          type: 'pie',
          radius: ['30%', '55%'],
          center: ['50%', '50%'],
          top: 30,
          bottom: 60,
          data: data.map(row => ({
            name: String(row[xAxis] ?? ''),
            value: parseNumericValue(row[yField]),
          })),
          label: { show: true, formatter: '{b}: {d}%' },
          labelLine: { show: true },
        }],
      }
    }

    case 'scatter': {
      const xField = yAxis[0] ?? xAxis
      const yField = yAxis[1] ?? yAxis[0] ?? xAxis
      return {
        title: { text: title, left: 'center', top: 10, textStyle: { fontSize: 14 } },
        tooltip: {
          trigger: 'item',
          formatter: (params: any) => `${xField}: ${params.value[0]}<br/>${yField}: ${params.value[1]}`
        },
        xAxis: { type: 'value', name: xField },
        yAxis: { type: 'value', name: yField },
        series: [{
          type: 'scatter',
          symbolSize: 10,
          data: data.map(row => [
            parseNumericValue(row[xField]),
            parseNumericValue(row[yField])
          ]),
        }],
        grid: { left: '10%', right: '10%', bottom: '15%', containLabel: true },
      }
    }

    default:
      return null
  }
}

// 配置面板显示状态
const configPanelVisible = ref(false)
const chartViewRef = ref<HTMLDivElement>()
const triggerRef = ref<HTMLButtonElement>()
const panelRef = ref<HTMLDivElement>()
const panelStyle = ref({ top: '0px', right: '0px' })

function toggleConfigPanel() {
  if (!configPanelVisible.value) {
    draftConfig.value = { ...localConfig.value }
    // 计算面板位置：固定在触发按钮下方右对齐
    if (triggerRef.value) {
      const rect = triggerRef.value.getBoundingClientRect()
      panelStyle.value = {
        top: `${rect.top}px`,
        right: `${window.innerWidth - rect.left + 6}px`,
      }
    }
  }
  configPanelVisible.value = !configPanelVisible.value
}

function handleClickOutside(e: MouseEvent) {
  const target = e.target as Node
  // 排除 el-select 弹出层（渲染在 body 下）
  if (target instanceof Element && target.closest('.el-select-dropdown, .el-popper')) return
  // 排除 fixed 定位的配置面板自身
  if (panelRef.value && panelRef.value.contains(target)) return
  if (chartViewRef.value && !chartViewRef.value.contains(target)) {
    configPanelVisible.value = false
  }
}

// 保存配置：将草稿应用到 localConfig，触发重绘和保存
function handleSaveConfig() {
  localConfig.value = { ...draftConfig.value }
  if (!syncingFromProps) {
    emit('config-change', localConfig.value)
  }
  emit('save-config', localConfig.value)
  configPanelVisible.value = false
}

// 取消：关闭面板，草稿丢弃
function handleCancelConfig() {
  configPanelVisible.value = false
}

onMounted(() => {
  if (isChartMode.value) {
    nextTick(async () => {
      await initChart()
      renderChart()
    })
  }
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  disposeChart()
  document.removeEventListener('click', handleClickOutside)
})
</script>

<template>
  <div class="chart-view" ref="chartViewRef">
    <div class="chart-render-area">
      <!-- 右上角齿轮按钮 -->
      <button ref="triggerRef" class="config-trigger" @click.stop="toggleConfigPanel" :class="{ active: configPanelVisible }" title="图表配置">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="3"/>
          <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/>
        </svg>
      </button>

      <!-- 配置面板 -->
      <Transition name="panel-fade">
        <div v-if="configPanelVisible" ref="panelRef" class="config-panel" :style="panelStyle" @click.stop>
          <!-- 面板头部 -->
          <div class="panel-header">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="3"/>
              <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/>
            </svg>
            <span>图表配置</span>
          </div>

          <!-- 图表类型 -->
          <div class="panel-section">
            <div class="section-label">图表类型</div>
            <div class="type-grid">
              <button
                v-for="opt in chartTypeOptions"
                :key="opt.value"
                class="type-btn"
                :class="{ selected: draftConfig.type === opt.value }"
                @click="draftConfig.type = opt.value"
              >
                <span class="type-icon">{{ opt.icon }}</span>
                <span class="type-label">{{ opt.label }}</span>
              </button>
            </div>
          </div>

          <!-- 轴配置 -->
          <div class="panel-section" v-if="draftConfig.type !== 'table'">
            <div class="section-label">数据映射</div>
            <div class="field-row">
              <span class="field-label">X 轴</span>
              <el-select v-model="draftConfig.xAxis" size="small" placeholder="选择列" clearable style="flex: 1" :teleported="false">
                <el-option v-for="col in columnOptions" :key="col" :label="col" :value="col" />
              </el-select>
            </div>
            <div class="field-row">
              <span class="field-label">Y 轴</span>
              <el-select v-model="draftConfig.yAxis" size="small" multiple placeholder="选择列" collapse-tags collapse-tags-tooltip style="flex: 1" :teleported="false">
                <el-option v-for="col in columnOptions" :key="col" :label="col" :value="col" />
              </el-select>
            </div>
          </div>

          <!-- 标题 -->
          <div class="panel-section">
            <div class="section-label">标题</div>
            <el-input v-model="draftConfig.title" size="small" placeholder="图表标题" />
          </div>

          <!-- 操作按钮 -->
          <div class="panel-footer">
            <button class="btn-cancel" @click="handleCancelConfig">取消</button>
            <button class="btn-save" @click="handleSaveConfig">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                <polyline points="20 6 9 17 4 12"/>
              </svg>
              应用
            </button>
          </div>
        </div>
      </Transition>

      <!-- 表格模式 -->
      <template v-if="localConfig.type === 'table'">
        <el-table :data="tableData" size="small" class="chart-table" max-height="300">
          <el-table-column v-for="col in columnOptions" :key="col" :prop="col" :label="col" min-width="100" show-overflow-tooltip />
        </el-table>
      </template>
      <!-- 图表模式 -->
      <template v-else>
        <div v-if="tableData.length > 0" ref="chartRef" class="echarts-container" />
        <div v-else class="chart-placeholder">
          <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" style="opacity: 0.4">
            <line x1="18" y1="20" x2="18" y2="10"/>
            <line x1="12" y1="20" x2="12" y2="4"/>
            <line x1="6" y1="20" x2="6" y2="14"/>
          </svg>
          <p>暂无数据</p>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.chart-view {
  overflow: visible;
}

.chart-render-area {
  position: relative;
  min-height: 200px;
}

/* 齿轮按钮 */
.config-trigger {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 10;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: 1px solid transparent;
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-secondary);
  opacity: 0.25;
  transition: opacity 0.2s, background 0.2s, border-color 0.2s, color 0.2s;
}

.chart-render-area:hover .config-trigger,
.config-trigger.active {
  opacity: 1;
  background: var(--color-fill-2, #f0f2f5);
  border-color: var(--color-border);
  color: var(--el-color-primary, #409eff);
}

/* 配置面板 */
.config-panel {
  position: fixed;
  z-index: 9999;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 10px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1), 0 2px 8px rgba(0, 0, 0, 0.06);
  width: 220px;
  overflow: visible;
}

/* 面板动画 */
.panel-fade-enter-active,
.panel-fade-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}
.panel-fade-enter-from,
.panel-fade-leave-to {
  opacity: 0;
  transform: translateY(-6px) scale(0.97);
}

/* 面板头部 */
.panel-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px 8px;
  font-size: 12px;
  font-weight: 600;
  color: #303133;
  border-bottom: 1px solid #f0f2f5;
  background: #fafafa;
  border-radius: 10px 10px 0 0;
}

/* 分区 */
.panel-section {
  padding: 10px 14px;
  border-bottom: 1px solid #f0f2f5;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.section-label {
  font-size: 11px;
  font-weight: 600;
  color: #909399;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

/* 图表类型网格 */
.type-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 4px;
}

.type-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  padding: 6px 2px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  transition: all 0.15s;
  color: #606266;
}

.type-btn:hover {
  border-color: var(--el-color-primary, #409eff);
  color: var(--el-color-primary, #409eff);
  background: #ecf5ff;
}

.type-btn.selected {
  border-color: var(--el-color-primary, #409eff);
  background: var(--el-color-primary, #409eff);
  color: #fff;
}

.type-icon {
  font-size: 13px;
  line-height: 1;
}

.type-label {
  font-size: 10px;
  white-space: nowrap;
}

/* 字段行 */
.field-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.field-label {
  font-size: 12px;
  color: #606266;
  width: 28px;
  flex-shrink: 0;
}

/* 底部按钮 */
.panel-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  padding: 10px 14px;
  background: #fafafa;
  border-radius: 0 0 10px 10px;
}

.btn-cancel {
  padding: 5px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 5px;
  background: #fff;
  color: #606266;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
}

.btn-cancel:hover {
  border-color: #c0c4cc;
  color: #303133;
}

.btn-save {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 5px 12px;
  border: none;
  border-radius: 5px;
  background: var(--el-color-primary, #409eff);
  color: #fff;
  font-size: 12px;
  cursor: pointer;
  transition: background 0.15s;
}

.btn-save:hover {
  background: var(--el-color-primary-dark-2, #337ecc);
}

/* 图表容器 */
.echarts-container {
  width: 100%;
  height: 340px;
}

.chart-table {
  width: 100%;
}

.chart-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 160px;
  color: var(--color-text-secondary);
}

.chart-placeholder p {
  margin: 8px 0 4px;
  font-size: 14px;
}
</style>
