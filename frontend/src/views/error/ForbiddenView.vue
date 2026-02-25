<script setup lang="ts">
import { useRouter } from 'vue-router'
const router = useRouter()
</script>

<template>
  <div class="forbidden">
    <h1>403</h1>
    <p>您没有权限访问此页面</p>
    <el-button @click="router.push('/')">返回首页</el-button>
  </div>
</template>

<style scoped>
.forbidden {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100vh;
  gap: 16px;
  color: var(--color-text-secondary);
}
.forbidden h1 {
  font-size: 72px;
  font-weight: 700;
  color: var(--color-text-primary);
}
</style>
