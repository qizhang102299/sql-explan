<template>
  <div class="diff-row">
    <div class="diff-col">
      <div class="diff-title">原始 SQL</div>
      <SqlEditor :model-value="originalSql" readonly height="320px" />
    </div>
    <div class="diff-col">
      <div class="diff-title">优化 SQL</div>
      <SqlEditor
        :model-value="optimizedSql || '-- 暂无优化结果'"
        readonly
        height="320px"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import SqlEditor from '@/components/SqlEditor.vue'

defineProps<{
  originalSql: string
  optimizedSql: string
}>()
</script>

<style scoped>
.diff-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

@media (max-width: 960px) {
  .diff-row {
    grid-template-columns: 1fr;
  }
}

.diff-title {
  font-weight: 600;
  margin-bottom: 8px;
  font-size: 14px;
}
</style>
