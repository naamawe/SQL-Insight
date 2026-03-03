<template>
  <div class="metric-card">
    <div class="metric-value">{{ formattedValue }}</div>
    <div class="metric-label">{{ label }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  data: Record<string, unknown>[]
}

const props = defineProps<Props>()

// 提取第一行第一列的值作为指标
const metricData = computed(() => {
  if (!props.data || props.data.length === 0) {
    return { value: 0, label: '无数据' }
  }
  
  const firstRow = props.data[0]
  const keys = Object.keys(firstRow)
  
  if (keys.length === 0) {
    return { value: 0, label: '无数据' }
  }
  
  const key = keys[0]
  const value = firstRow[key]
  
  return {
    value: value,
    label: key
  }
})

// 格式化数值显示
const formattedValue = computed(() => {
  const value = metricData.value.value
  
  if (typeof value === 'number') {
    // 大数字使用千分位分隔
    if (Math.abs(value) >= 1000) {
      return value.toLocaleString('zh-CN', {
        maximumFractionDigits: 2
      })
    }
    return value.toFixed(2)
  }
  
  return String(value)
})

const label = computed(() => metricData.value.label)
</script>

<style scoped>
.metric-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 12px;
  color: white;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
  transition: transform 0.2s;
}

.metric-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(102, 126, 234, 0.4);
}

.metric-value {
  font-size: 48px;
  font-weight: 700;
  line-height: 1.2;
  margin-bottom: 8px;
}

.metric-label {
  font-size: 16px;
  opacity: 0.9;
  text-transform: uppercase;
  letter-spacing: 1px;
}
</style>
