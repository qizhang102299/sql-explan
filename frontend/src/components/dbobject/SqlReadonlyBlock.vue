<template>
  <div class="sql-readonly">
    <div class="toolbar">
      <el-button size="small" @click="copySql">复制</el-button>
      <slot name="actions" />
    </div>
    <SqlEditor :model-value="sql" readonly :height="height" />
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import SqlEditor from '@/components/SqlEditor.vue'

const props = withDefaults(
  defineProps<{
    sql: string
    height?: string
  }>(),
  {
    height: '320px',
  },
)

async function copySql(): Promise<void> {
  try {
    await navigator.clipboard.writeText(props.sql)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败')
  }
}
</script>

<style scoped>
.sql-readonly {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
