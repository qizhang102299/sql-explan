<template>
  <div class="opt-tab">
    <div class="equiv-bar">
      <el-tag :type="optimized?.equivalencePassed ? 'success' : 'danger'" effect="dark">
        {{ optimized?.equivalencePassed ? '已通过等价校验' : '未通过等价校验' }}
      </el-tag>
      <span v-if="optimized?.explanation" class="explain">
        {{ optimized.explanation }}
      </span>
    </div>
    <el-alert
      v-if="optimized?.equivalenceReport?.message"
      :title="optimized.equivalenceReport.message"
      :type="optimized.equivalencePassed ? 'success' : 'warning'"
      :closable="false"
      show-icon
    />
    <SqlDiffPanels
      :original-sql="originalSql"
      :optimized-sql="optimized?.sql ?? ''"
    />
  </div>
</template>

<script setup lang="ts">
import type { OptimizedSqlRes } from '@/types/models'
import SqlDiffPanels from './SqlDiffPanels.vue'

defineProps<{
  originalSql: string
  optimized?: OptimizedSqlRes
}>()
</script>

<style scoped>
.opt-tab {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.equiv-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.explain {
  color: #606266;
  font-size: 14px;
}
</style>
