<template>
  <el-empty v-if="!report?.advices?.length" description="暂无索引建议" />
  <div v-else>
    <p v-if="report.summary" class="summary">{{ report.summary }}</p>
    <el-table :data="report.advices" stripe>
      <el-table-column label="类型" width="130">
        <template #default="{ row }">
          {{ adviceTypeLabel(row.adviceType) }}
        </template>
      </el-table-column>
      <el-table-column label="级别" width="88">
        <template #default="{ row }">
          <el-tag :type="severityTagType(row.severity)" size="small">
            {{ severityLabel(row.severity) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="tableName" label="表" width="120" />
      <el-table-column prop="reason" label="理由" min-width="200" show-overflow-tooltip />
      <el-table-column prop="expectedBenefit" label="预期收益" min-width="160" show-overflow-tooltip />
      <el-table-column prop="writeCostNote" label="写入代价" min-width="140" show-overflow-tooltip />
      <el-table-column label="DDL" min-width="240">
        <template #default="{ row }">
          <div v-if="row.createIndexDdl" class="ddl-cell">
            <span class="mono-block ddl-text">{{ row.createIndexDdl }}</span>
            <el-button
              type="primary"
              link
              size="small"
              @click="copyDdl(row.createIndexDdl)"
            >
              复制
            </el-button>
          </div>
          <span v-else>-</span>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import type { IndexAdviceReport } from '@/types/models'
import { adviceTypeLabel, severityLabel, severityTagType } from '@/utils/format'

defineProps<{
  report?: IndexAdviceReport
}>()

async function copyDdl(ddl: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(ddl)
    ElMessage.success('已复制 DDL')
  } catch {
    ElMessage.error('复制失败，请手动选择文本')
  }
}
</script>

<style scoped>
.summary {
  margin: 0 0 12px;
  color: #606266;
}

.ddl-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

.ddl-text {
  font-size: 12px;
}
</style>
