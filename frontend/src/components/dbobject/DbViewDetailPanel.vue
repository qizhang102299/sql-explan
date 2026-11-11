<template>
  <div v-loading="loading" class="view-detail">
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-tabs v-else-if="detail" v-model="activeTab">
      <el-tab-pane label="定义" name="def">
        <SqlReadonlyBlock :sql="detail.definitionSql ?? ''" height="360px">
          <template #actions>
            <el-button type="primary" size="small" @click="goAnalyze">
              用此 SQL 去分析
            </el-button>
          </template>
        </SqlReadonlyBlock>
      </el-tab-pane>
      <el-tab-pane label="字段" name="columns">
        <el-table :data="detail.columns" stripe size="small" border>
          <el-table-column prop="ordinalPosition" label="#" width="52" />
          <el-table-column prop="name" label="名称" min-width="120" />
          <el-table-column prop="columnType" label="类型" min-width="120" />
          <el-table-column label="可空" width="64">
            <template #default="{ row }">{{ row.nullable ? '是' : '否' }}</template>
          </el-table-column>
          <el-table-column prop="defaultValue" label="默认值" min-width="100" />
          <el-table-column prop="comment" label="注释" min-width="140" show-overflow-tooltip />
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="属性" name="attrs">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="DEFINER">{{ detail.definer ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="SECURITY">{{ detail.securityType ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="CHECK OPTION">{{ detail.checkOption ?? '-' }}</el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>
    </el-tabs>
    <el-empty v-else-if="!loading" description="请在左侧选择一个视图" />
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { DbViewDetailRes } from '@/types/models'
import SqlReadonlyBlock from './SqlReadonlyBlock.vue'

const props = defineProps<{
  dataSourceId: number
  loading: boolean
  error: string
  detail: DbViewDetailRes | null
}>()

const router = useRouter()
const activeTab = ref('def')

watch(
  () => props.detail?.viewName,
  () => {
    activeTab.value = 'def'
  },
)

function goAnalyze(): void {
  const def = props.detail?.definitionSql?.trim() ?? ''
  let sql = def
  if (!sql || sql.toUpperCase().startsWith('CREATE')) {
    const name = props.detail?.viewName
    sql = name ? `SELECT * FROM ${name} LIMIT 10` : ''
  }
  if (!sql) {
    return
  }
  const query: Record<string, string> = { sql }
  if (props.dataSourceId) {
    query.dataSourceId = String(props.dataSourceId)
  }
  router.push({ path: '/analyze', query })
}
</script>
