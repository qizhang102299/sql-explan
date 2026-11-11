<template>
  <el-card shadow="never">
    <div class="page-toolbar">
      <span>数据源</span>
      <el-select
        v-model="form.dataSourceId"
        filterable
        placeholder="请选择数据源"
        style="width: 260px"
        :loading="dsLoading"
      >
        <el-option
          v-for="ds in dataSources"
          :key="ds.id"
          :label="`${ds.name} (${ds.host}:${ds.port}/${ds.dbName})`"
          :value="ds.id"
        />
      </el-select>
      <el-checkbox v-model="form.enableBenchmark">开启基准测试</el-checkbox>
      <span v-show="form.enableBenchmark" class="inline-field">
        次数
        <el-input-number
          v-model="form.benchmarkIterations"
          :min="1"
          :max="20"
          size="small"
        />
      </span>
      <el-checkbox v-model="form.verifyIndexBenefit">
        验证索引收益（目标库建临时索引）
      </el-checkbox>
      <el-button type="primary" :loading="analyzing" @click="onAnalyze">
        开始分析
      </el-button>
    </div>
    <SqlEditor v-model="form.sql" height="300px" />
    <AnalyzeResultPanel :result="result" :original-sql="lastOriginalSql" />
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import SqlEditor from '@/components/SqlEditor.vue'
import AnalyzeResultPanel from '@/components/analyze/AnalyzeResultPanel.vue'
import { dataSourcePage } from '@/api/datasource'
import { analyzeSql } from '@/api/sql'
import type { DataSourceRes, SqlAnalyzeRes } from '@/types/models'

const route = useRoute()

const dataSources = ref<DataSourceRes[]>([])
const dsLoading = ref(false)
const analyzing = ref(false)
const result = ref<SqlAnalyzeRes | null>(null)
const lastOriginalSql = ref('')

const form = reactive({
  dataSourceId: undefined as number | undefined,
  sql: 'SELECT * FROM user LIMIT 10;',
  enableBenchmark: true,
  benchmarkIterations: 5,
  verifyIndexBenefit: false,
})

async function loadDataSources(): Promise<void> {
  dsLoading.value = true
  try {
    const page = await dataSourcePage({ pageNum: 1, pageSize: 100 })
    dataSources.value = page.records ?? []
    if (!form.dataSourceId && dataSources.value.length) {
      form.dataSourceId = dataSources.value[0].id
    }
  } finally {
    dsLoading.value = false
  }
}

async function onAnalyze(): Promise<void> {
  if (form.dataSourceId == null) {
    ElMessage.warning('请选择数据源')
    return
  }
  if (!form.sql.trim()) {
    ElMessage.warning('请输入 SQL')
    return
  }
  analyzing.value = true
  try {
    lastOriginalSql.value = form.sql
    result.value = await analyzeSql({
      dataSourceId: form.dataSourceId,
      sql: form.sql,
      enableBenchmark: form.enableBenchmark,
      benchmarkIterations: form.benchmarkIterations,
      verifyIndexBenefit: form.verifyIndexBenefit,
    })
  } finally {
    analyzing.value = false
  }
}

onMounted(async () => {
  await loadDataSources()
  const qSql = route.query.sql
  const qDs = route.query.dataSourceId
  if (typeof qSql === 'string' && qSql) {
    form.sql = qSql
  }
  if (typeof qDs === 'string' && qDs) {
    const id = Number(qDs)
    if (!Number.isNaN(id)) {
      form.dataSourceId = id
    }
  }
})
</script>

<style scoped>
.inline-field {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}
</style>
