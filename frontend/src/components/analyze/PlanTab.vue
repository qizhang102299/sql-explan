<template>
  <div v-if="!plan" class="empty-wrap">
    <el-empty description="无执行计划数据" />
  </div>
  <div v-else class="plan-tab">
    <el-alert
      v-if="plan.narrative"
      :title="plan.narrative"
      type="info"
      :closable="false"
      show-icon
      class="narrative"
    />
  <div class="section-title">计划节点树</div>
    <PlanNodeTree :root="plan.explainPlan?.root" />
    <el-alert
      v-for="(w, i) in plan.explainPlan?.parseWarnings ?? []"
      :key="`w-${i}`"
      :title="w"
      type="warning"
      :closable="false"
      class="warn-block"
    />
    <div class="section-title">问题点（规则识别）</div>
    <p v-if="plan.interpretation?.summary" class="summary-text">
      {{ plan.interpretation.summary }}
    </p>
    <el-table
      v-if="plan.interpretation?.issues?.length"
      :data="plan.interpretation.issues"
      stripe
      size="small"
      :row-class-name="issueRowClass"
    >
      <el-table-column label="级别" width="88">
        <template #default="{ row }">
          <el-tag :type="severityTagType(row.severity)" size="small">
            {{ severityLabel(row.severity) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="140" />
      <el-table-column
        prop="description"
        label="说明"
        min-width="220"
        show-overflow-tooltip
      />
      <el-table-column prop="relatedTable" label="相关表" width="120" />
      <el-table-column prop="evidence" label="依据" min-width="160" show-overflow-tooltip />
    </el-table>
    <el-empty
      v-else
      description="未识别到明显问题点"
      :image-size="64"
    />
  </div>
</template>

<script setup lang="ts">
import type { PlanIssue, SqlPlanRes } from '@/types/models'
import PlanNodeTree from './PlanNodeTree.vue'
import { severityLabel, severityTagType } from '@/utils/format'

defineProps<{
  plan?: SqlPlanRes
}>()

function issueRowClass({ row }: { row: PlanIssue }): string {
  if (row.severity === 'CRITICAL') {
    return 'issue-row-critical'
  }
  if (row.severity === 'WARNING') {
    return 'issue-row-warning'
  }
  return ''
}
</script>

<style scoped>
.plan-tab {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.narrative {
  margin-bottom: 4px;
}

.summary-text {
  margin: 0 0 8px;
  color: #606266;
  font-size: 14px;
}

.warn-block {
  margin-top: 8px;
}

:deep(.issue-row-critical) {
  --el-table-tr-bg-color: #fef0f0;
}

:deep(.issue-row-warning) {
  --el-table-tr-bg-color: #fdf6ec;
}
</style>
