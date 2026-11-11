<template>
  <el-card shadow="never">
    <div class="page-toolbar">
      <span>数据源</span>
      <el-select
        v-model="filterDataSourceId"
        clearable
        placeholder="全部"
        style="width: 240px"
      >
        <el-option
          v-for="ds in dataSources"
          :key="ds.id"
          :label="ds.name"
          :value="ds.id"
        />
      </el-select>
      <el-button type="primary" @click="loadPage">查询</el-button>
    </div>
    <el-table v-loading="loading" :data="records" stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column label="SQL" min-width="280" show-overflow-tooltip>
        <template #default="{ row }">
          {{ truncateSql(row.sqlText) }}
        </template>
      </el-table-column>
      <el-table-column prop="dataSourceId" label="数据源 ID" width="110" />
      <el-table-column prop="costMs" label="耗时 (ms)" width="100" />
      <el-table-column label="分析时间" width="170">
        <template #default="{ row }">
          {{ formatTimestamp(row.createdTime) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="openDetail(row)">查看详情</el-button>
          <el-button type="primary" link @click="goAnalyze(row)">再次分析</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-model:current-page="pageNum"
      v-model:page-size="pageSize"
      class="pager"
      layout="total, prev, pager, next"
      :total="total"
      @current-change="loadPage"
    />

    <el-drawer v-model="drawerVisible" title="历史分析详情" size="72%">
      <AnalyzeResultPanel
        :result="detail"
        :original-sql="detailOriginalSql"
      />
    </el-drawer>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AnalyzeResultPanel from '@/components/analyze/AnalyzeResultPanel.vue'
import { dataSourcePage } from '@/api/datasource'
import { historyDetail, historyPage } from '@/api/sql'
import type {
  DataSourceRes,
  SqlAnalyzeRes,
  SqlHistoryRecordSummaryRes,
} from '@/types/models'
import { formatTimestamp, truncateSql } from '@/utils/format'

const router = useRouter()

const loading = ref(false)
const records = ref<SqlHistoryRecordSummaryRes[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const filterDataSourceId = ref<number | undefined>()

const dataSources = ref<DataSourceRes[]>([])

const drawerVisible = ref(false)
const detail = ref<SqlAnalyzeRes | null>(null)
const detailOriginalSql = ref('')

async function loadDataSources(): Promise<void> {
  const page = await dataSourcePage({ pageNum: 1, pageSize: 100 })
  dataSources.value = page.records ?? []
}

async function loadPage(): Promise<void> {
  loading.value = true
  try {
    const res = await historyPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      dataSourceId: filterDataSourceId.value,
    })
    records.value = res.records ?? []
    total.value = res.total ?? 0
  } finally {
    loading.value = false
  }
}

async function openDetail(row: SqlHistoryRecordSummaryRes): Promise<void> {
  detailOriginalSql.value = row.sqlText
  detail.value = await historyDetail({ id: row.id })
  drawerVisible.value = true
}

function goAnalyze(row: SqlHistoryRecordSummaryRes): void {
  router.push({
    path: '/analyze',
    query: {
      sql: row.sqlText,
      dataSourceId: String(row.dataSourceId),
    },
  })
}

onMounted(async () => {
  await loadDataSources()
  await loadPage()
})
</script>

<style scoped>
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
