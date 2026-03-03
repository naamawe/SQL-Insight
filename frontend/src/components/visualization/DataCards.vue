<template>
  <div class="data-cards">
    <div v-for="(item, index) in data" :key="index" class="data-card">
      <div v-for="(value, key) in item" :key="key" class="card-field">
        <span class="field-label">{{ key }}</span>
        <span class="field-value">{{ formatValue(value) }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  data: Record<string, unknown>[]
}

defineProps<Props>()

const formatValue = (value: unknown): string => {
  if (value === null || value === undefined) {
    return '-'
  }
  
  if (typeof value === 'number') {
    return value.toLocaleString('zh-CN')
  }
  
  return String(value)
}
</script>

<style scoped>
.data-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
  padding: 8px 0;
}

.data-card {
  background: white;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 16px;
  transition: all 0.2s;
}

.data-card:hover {
  border-color: #667eea;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.15);
  transform: translateY(-2px);
}

.card-field {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid #f3f4f6;
}

.card-field:last-child {
  border-bottom: none;
}

.field-label {
  font-size: 13px;
  color: #6b7280;
  font-weight: 500;
}

.field-value {
  font-size: 14px;
  color: #111827;
  font-weight: 600;
  text-align: right;
}
</style>
