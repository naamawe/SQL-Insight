<template>
  <div class="smart-result-renderer">
    <!-- 视图切换器 -->
    <div v-if="hasVisualization" class="view-switcher">
      <el-radio-group v-model="currentView" size="small">
        <el-radio-button :value="visualization.primaryType">
          {{ getTypeLabel(visualization.primaryType) }}
          <el-tag size="small" type="success" style="margin-left: 4px">推荐</el-tag>
        </el-radio-button>
        <el-radio-button
          v-for="altType in supportedAlternatives"
          :key="altType"
          :value="altType"
        >
          {{ getTypeLabel(altType) }}
        </el-radio-button>
      </el-radio-group>

      <!-- AI 推荐理由 - 悬停提示 -->
      <el-tooltip
        v-if="visualization.reason"
        :content="visualization.reason"
        placement="top"
        effect="light"
      >
        <el-icon class="info-icon"><InfoFilled /></el-icon>
      </el-tooltip>
    </div>

    <!-- 渲染区域 -->
    <div class="render-area">
      <!-- METRIC - 大数字指标 -->
      <MetricCard v-if="currentView === 'METRIC'" :data="data" />
      
      <!-- CARDS - 卡片列表 -->
      <DataCards v-else-if="currentView === 'CARDS'" :data="data" />
      
      <!-- 图表类型 -->
      <ChartRenderer 
        v-else-if="isChartType(currentView)"
        :type="currentView"
        :data="data"
        :chart-config="visualization?.chartConfig"
      />
      
      <!-- TABLE - 默认表格 -->
      <div v-else class="table-wrapper">
        <el-table
          :data="data"
          stripe
          :max-height="500"
          :header-cell-style="{
            background: '#f3f4f6',
            color: '#374151',
            fontWeight: '600'
          }"
        >
          <el-table-column
            v-for="column in columns"
            :key="column"
            :prop="column"
            :label="column"
            min-width="120"
            show-overflow-tooltip
          />
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { InfoFilled } from '@element-plus/icons-vue'
import type { VisualizationConfig, VisualizationType } from '@/types/visualization'
import MetricCard from './MetricCard.vue'
import DataCards from './DataCards.vue'
import ChartRenderer from './ChartRenderer.vue'

interface Props {
  data: Record<string, unknown>[]
  visualization?: VisualizationConfig
}

const props = defineProps<Props>()

const hasVisualization = computed(() => !!props.visualization)

// 当前选择的视图类型
const currentView = ref<VisualizationType>('TABLE')

// 支持的图表类型列表
const supportedChartTypes: VisualizationType[] = [
  'BAR_CHART',
  'LINE_CHART',
  'PIE_CHART',
  'AREA_CHART',
  'TABLE',
  'METRIC',
  'CARDS'
]

// 过滤出前端支持的备选类型
const supportedAlternatives = computed(() => {
  if (!props.visualization) return []
  return props.visualization.alternativeTypes.filter(type =>
    supportedChartTypes.includes(type)
  )
})

// 监听 visualization 变化，自动切换到推荐视图
watch(() => props.visualization, (newViz) => {
  if (newViz) {
    currentView.value = newViz.primaryType
  }
}, { immediate: true })

// 表格列
const columns = computed(() => {
  if (!props.data || props.data.length === 0) return []
  return Object.keys(props.data[0])
})

// 判断是否为图表类型
const isChartType = (type: VisualizationType) => {
  return ['BAR_CHART', 'LINE_CHART', 'PIE_CHART', 'AREA_CHART', 'SCATTER_CHART'].includes(type)
}

// 获取类型标签
const getTypeLabel = (type: VisualizationType): string => {
  const labels: Record<VisualizationType, string> = {
    METRIC: '指标卡',
    CARDS: '卡片',
    TABLE: '表格',
    BAR_CHART: '柱状图',
    LINE_CHART: '折线图',
    PIE_CHART: '饼图',
    AREA_CHART: '面积图',
    SCATTER_CHART: '散点图',
    HEATMAP: '热力图',
    TIMELINE: '时间线',
    COMPARISON: '对比'
  }
  return labels[type] || type
}
</script>

<style scoped>
.smart-result-renderer {
  margin-top: 16px;
}

.view-switcher {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  padding: 8px 12px;
  background: white;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}

.view-switcher :deep(.el-radio-group) {
  flex: 1;
}

.view-switcher :deep(.el-radio-button) {
  margin-right: 6px;
}

.view-switcher :deep(.el-radio-button:last-child) {
  margin-right: 0;
}

.view-switcher :deep(.el-radio-button__inner) {
  border: 1px solid #e5e7eb;
  background: white;
  color: #6b7280;
  font-weight: 500;
  font-size: 13px;
  padding: 6px 12px;
  transition: all 0.2s;
  border-radius: 4px;
}

.view-switcher :deep(.el-tag) {
  height: 18px;
  line-height: 18px;
  padding: 0 4px;
  font-size: 11px;
}

.view-switcher :deep(.el-radio-button__inner:hover) {
  color: #667eea;
  border-color: #667eea;
}

.view-switcher :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: #667eea;
  color: white;
  border-color: #667eea;
  box-shadow: 0 2px 4px rgba(102, 126, 234, 0.2);
}

.info-icon {
  font-size: 18px;
  color: #9ca3af;
  cursor: help;
  transition: all 0.2s;
}

.info-icon:hover {
  color: #667eea;
  transform: scale(1.1);
}

.render-area {
  background: white;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e5e7eb;
}

.table-wrapper {
  padding: 16px;
}

.table-wrapper :deep(.el-table) {
  border-radius: 8px;
  overflow: hidden;
}

.table-wrapper :deep(.el-table__header-wrapper) {
  border-radius: 8px 8px 0 0;
}

.table-wrapper :deep(.el-table td),
.table-wrapper :deep(.el-table th) {
  padding: 12px 0;
}

.table-wrapper :deep(.el-table--striped .el-table__body tr.el-table__row--striped td) {
  background: #f9fafb;
}

.table-wrapper :deep(.el-table__body tr:hover > td) {
  background-color: #f3f4f6 !important;
}
</style>
