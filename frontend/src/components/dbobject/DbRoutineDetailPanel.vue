<template>
  <div v-loading="loading" class="routine-detail">
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-tabs v-else-if="detail" v-model="activeTab">
      <el-tab-pane label="参数" name="params">
        <el-empty v-if="!detail.parameters.length" description="无参数" />
        <el-table v-else :data="detail.parameters" stripe size="small" border>
          <el-table-column label="方向" width="100">
            <template #default="{ row }">
              <el-tag :type="directionTag(row.direction)" size="small">
                {{ row.direction ?? 'IN' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="名称" min-width="120" />
          <el-table-column prop="parameterType" label="类型" min-width="140" />
        </el-table>
        <el-alert
          v-if="detail.routineType === 'FUNCTION' && detail.returnType"
          class="return-type"
          :title="`返回类型：${detail.returnType}`"
          type="info"
          :closable="false"
          show-icon
        />
      </el-tab-pane>
      <el-tab-pane label="源码" name="source">
        <SqlReadonlyBlock :sql="detail.sourceDefinition ?? ''" height="360px" />
      </el-tab-pane>
      <el-tab-pane label="属性" name="attrs">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="DEFINER">{{ detail.definer ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="确定性">
            {{ detail.deterministic ? '是' : '否' }}
          </el-descriptions-item>
          <el-descriptions-item label="数据访问">{{ detail.sqlDataAccess ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">
            {{ formatTimestamp(detail.createdTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="修改时间">
            {{ formatTimestamp(detail.modifiedTime) }}
          </el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>
    </el-tabs>
    <el-empty v-else-if="!loading" description="请在左侧选择存储过程或函数" />
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import type { DbRoutineDetailRes } from '@/types/models'
import { formatTimestamp } from '@/utils/format'
import SqlReadonlyBlock from './SqlReadonlyBlock.vue'

const props = defineProps<{
  loading: boolean
  error: string
  detail: DbRoutineDetailRes | null
}>()

const activeTab = ref('params')

watch(
  () => props.detail?.routineName,
  () => {
    activeTab.value = 'params'
  },
)

function directionTag(direction?: string): 'success' | 'warning' | 'info' {
  const d = (direction ?? 'IN').toUpperCase()
  if (d === 'OUT') {
    return 'warning'
  }
  if (d === 'INOUT') {
    return 'success'
  }
  return 'info'
}
</script>

<style scoped>
.return-type {
  margin-top: 12px;
}
</style>
