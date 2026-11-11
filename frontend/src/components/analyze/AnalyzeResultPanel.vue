<template>
  <el-card v-if="result" class="result-card" shadow="never">
    <div class="result-meta">
      <el-tag :type="result.analysisSource === 'AI' ? 'success' : 'info'">
        分析来源：{{ analysisSourceLabel(result.analysisSource) }}
      </el-tag>
      <span v-if="result.aiStatusNote" class="ai-note">{{ result.aiStatusNote }}</span>
      <span class="time-label">
        完成时间：{{ formatTimestamp(result.createdTime) }}
      </span>
      <span v-if="result.recordId">记录 ID：{{ result.recordId }}</span>
    </div>
    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane label="语义分析" name="semantic">
        <SemanticTab :semantic="result.semantic" />
      </el-tab-pane>
      <el-tab-pane label="执行计划" name="plan">
        <PlanTab :plan="result.plan" />
      </el-tab-pane>
      <el-tab-pane label="索引建议" name="index">
        <IndexTab :report="result.indexAdvices" />
      </el-tab-pane>
      <el-tab-pane label="优化 SQL" name="optimized">
        <OptimizedSqlTab
          :original-sql="originalSql"
          :optimized="result.optimizedSql"
        />
      </el-tab-pane>
      <el-tab-pane label="耗时对比" name="benchmark">
        <BenchmarkTab :benchmark="result.benchmark" />
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { SqlAnalyzeRes } from '@/types/models'
import { analysisSourceLabel, formatTimestamp } from '@/utils/format'
import SemanticTab from './SemanticTab.vue'
import PlanTab from './PlanTab.vue'
import IndexTab from './IndexTab.vue'
import OptimizedSqlTab from './OptimizedSqlTab.vue'
import BenchmarkTab from './BenchmarkTab.vue'

defineProps<{
  result: SqlAnalyzeRes | null
  originalSql: string
}>()

const activeTab = ref('semantic')
</script>

<style scoped>
.result-card {
  margin-top: 16px;
}

.result-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  font-size: 13px;
  color: #606266;
}

.ai-note {
  color: #e6a23c;
}

.time-label {
  margin-left: auto;
}
</style>
