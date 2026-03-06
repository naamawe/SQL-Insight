<script setup lang="ts">
import { ref, watch, computed, onMounted, onUnmounted, nextTick, shallowRef } from 'vue'
import * as echarts from 'echarts'
import type { ChartConfigDTO } from '@/types'
import type { ECharts } from 'echarts'

const props = defineProps<{
  chartConfig: ChartConfigDTO | null
  tableData: Record<string, unknown>[]
  recordId: number
}>()

const emit = defineEmits<{
  (e: 'config-change', config: ChartConfigDTO): void
  (e: 'save-config', config: ChartConfigDTO): void
}>()

// 本地配置的副本，允许用户编辑
const localConfig = ref<ChartConfigDTO>({
  type: 'table',
  xAxis: '',
  yAxis: [],
  title: '数据结果',
})

// ECharts 实例 - 使用 shallowRef 避免响应式问题
const chartInstance = shallowRef<ECharts | null>(null)
const chartRef = ref<HTMLDivElement>()

// 可用的 X/Y 轴选项（从数据列中提取）
const columnOptions = computed(() => {
  if (!props.tableData || props.tableData.length === 0) return []
  const firstRow = props.tableData[0]
  return firstRow ? Object.keys(firstRow) : []
})

// 图表类型选项
const chartTypeOptions = [
  { value: 'bar', label: '柱状图' },
  { value: 'line', label: '折线图' },
  { value: 'pie', label: '饼图' },
  { value: 'scatter', label: '散点图' },
  { value: 'table', label: '表格' },
]

// 是否显示图表（非表格模式）
const isChartMode = computed(() => localConfig.value.type !== 'table')

// 监听外部配置变化
watch(() => props.chartConfig, (newConfig) => {
  if (newConfig) {
    localConfig.value = { ...newConfig }
  }
}, { immediate: true })

// 监听配置变化，触发事件和重绘
watch(localConfig, (newVal) => {
  emit('config-change', newVal)
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
      initChart()
      renderChart()
    })
  } else {
    disposeChart()
  }
})

// 辅助函数：智能解析值为数字unction parseNumericValue(val: unknown): number {
  if (val === null || val === undefined) return 0
  if (typeof val === 'number') return val
  if (typeof val === 'string') {
    // 尝试解析数字，去除可能的千分位逗号
    const cleaned = val.replace(/,/g, '')
    const num = parseFloat(cleaned)
    return isNaN(num) ? 0 : num
  }
  return 0
}

// 初始化 ECharts
function initChart() {
  if (!chartRef.value) return

  if (!chartInstance.value) {
    chartInstance.value = echarts.init(chartRef.value)
    window.addEventListener('resize', handleResize)
  }
}

// 销毁图表
function disposeChart() {
  if (chartInstance.value) {
    chartInstance.value.dispose()
    chartInstance.value = null
    window.removeEventListener('resize', handleResize)
  }
}

// 处理窗口大小变化
function handleResize() {
  chartInstance.value?.resize()
}

// 渲染图表
function renderChart() {
  if (!chartInstance.value || !props.tableData.length) return

  const config = localConfig.value
  const option = buildChartOption(config, props.tableData)

  if (option) {
    chartInstance.value.setOption(option, true)
  }
}

// 构建 ECharts 配置
function buildChartOption(config: ChartConfigDTO, data: Record<string, unknown>[]): echarts.EChartsOption | null {
  const { type, xAxis, yAxis, title } = config

  if (!xAxis || !yAxis || yAxis.length === 0) {
    return null
  }

  // 提取 X 轴数据（作为分类标签）
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
        legend: { bottom: 10 },
        series: [{
          type: 'pie',
          radius: ['30%', '60%'],
          center: ['50%', '50%'],
          data: data.map(row => ({
            name: String(row[xAxis] ?? ''),
            value: parseNumericValue(row[yField]),
          })),
          label: { show: true, formatter: '{b}: {d}%' },
        }],
      }
    }

    case 'scatter': {
      // 散点图需要 X 和 Y 都是数值类型
      // 使用第一个 Y 轴作为 X 值，第二个 Y 轴（或第一个）作为 Y 值
      const xField = yAxis[0]
      const yField = yAxis[1] || yAxis[0]
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

// 保存配置
function handleSaveConfig() {
  emit('save-config', localConfig.value)
}

// 重置为 AI 推荐
function handleResetToAi() {
  if (props.chartConfig) {
    localConfig.value = { ...props.chartConfig }
  }
}

onMounted(() => {
  // 初始如果是图表模式，需要初始化
  if (isChartMode.value) {
    nextTick(() => {
      initChart()
      renderChart()
    })
  }
})

onUnmounted(() => {
  disposeChart()
})
</script>

<template>
  <div class="chart-view">
    <!-- 配置面板 -->
    <div class="chart-config-panel">
      <div class="config-row">
        <span class="config-label">图表类型</span>
        <el-select v-model="localConfig.type" size="small" style="width: 120px">
          <el-option
            v-for="opt in chartTypeOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </div>

      <div class="config-row">
        <span class="config-label">X 轴</span>
        <el-select v-model="localConfig.xAxis" size="small" style="width: 120px" clearable>
          <el-option
            v-for="col in columnOptions"
            :key="col"
            :label="col"
            :value="col"
          />
        </el-select>
      </div>

      <div class="config-row">
        <span class="config-label">Y 轴</span>
        <el-select
          v-model="localConfig.yAxis"
          size="small"
          multiple
          style="width: 160px"
          collapse-tags
          collapse-tags-tooltip
        >
          <el-option
            v-for="col in columnOptions"
            :key="col"
            :label="col"
            :value="col"
          />
        </el-select>
      </div>

      <div class="config-row">
        <span class="config-label">标题</span>
        <el-input v-model="localConfig.title" size="small" style="width: 180px" />
      </div>

      <div class="config-actions">
        <el-button size="small" @click="handleResetToAi" :disabled="!chartConfig">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="margin-right: 4px">
            <path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/>
            <path d="M3 3v5h5"/>
          </svg>
          AI 推荐
        </el-button>
        <el-button type="primary" size="small" @click="handleSaveConfig">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="margin-right: 4px">
            <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/>
            <polyline points="17 21 17 13 7 13 7 21"/>
            <polyline points="7 3 7 8 15 8"/>
          </svg>
          保存
        </el-button>
      </div>
    </div>

    <!-- 图表渲染区域 -->
    <div class="chart-render-area">
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
          <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" style="opacity: 0.5">
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
  border: 1px solid var(--color-border);
  border-radius: 8px;
  margin: 8px 0;
  overflow: hidden;
}

.chart-config-panel {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding: 12px;
  background: var(--color-fill-1);
  border-bottom: 1px solid var(--color-border);
  align-items: center;
}

.config-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.config-label {
  font-size: 12px;
  color: var(--color-text-secondary);
  white-space: nowrap;
}

.config-actions {
  margin-left: auto;
  display: flex;
  gap: 8px;
}

.chart-render-area {
  min-height: 200px;
  padding: 16px;
}

.echarts-container {
  width: 100%;
  height: 280px;
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