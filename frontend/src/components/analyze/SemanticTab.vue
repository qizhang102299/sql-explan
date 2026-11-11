<template>
  <el-empty v-if="!semantic" description="无语义分析数据" />
  <div v-else class="semantic-grid">
    <el-descriptions :column="1" border>
      <el-descriptions-item label="意图概述">
        {{ semantic.intentSummary || '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="涉及表">
        <el-tag
          v-for="t in semantic.involvedTables ?? []"
          :key="t"
          class="tag-gap"
          size="small"
        >
          {{ t }}
        </el-tag>
        <span v-if="!(semantic.involvedTables?.length)">-</span>
      </el-descriptions-item>
      <el-descriptions-item label="过滤条件">
        {{ semantic.filtersDescription || '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="连接关系">
        {{ semantic.joinsDescription || '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="聚合">
        {{ semantic.aggregationDescription || '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="排序">
        {{ semantic.orderingDescription || '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="分页">
        {{ semantic.paginationDescription || '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="子查询 / CTE">
        {{ semantic.subqueryCteDescription || '-' }}
      </el-descriptions-item>
    </el-descriptions>
    <template v-if="semantic.riskNotes?.length">
      <div class="section-title">潜在风险</div>
      <el-alert
        v-for="(note, idx) in semantic.riskNotes"
        :key="idx"
        :title="note"
        type="warning"
        :closable="false"
        show-icon
        class="risk-alert"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import type { SqlSemanticInsight } from '@/types/models'

defineProps<{
  semantic?: SqlSemanticInsight
}>()
</script>

<style scoped>
.tag-gap {
  margin-right: 6px;
  margin-bottom: 4px;
}

.risk-alert {
  margin-bottom: 8px;
}
</style>
