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
      <el-button :loading="validating" @click="onValidateClick">语法校验</el-button>
      <el-button type="primary" :loading="analyzing" @click="onAnalyze">
        开始分析
      </el-button>
    </div>
    <SqlEditor
      ref="editorRef"
      v-model="form.sql"
      height="300px"
      @paste="onEditorPaste"
    />
  <div
      class="validate-status"
      :class="{
        'validate-status--ok': validation?.valid,
        'validate-status--err': validation && !validation.valid,
      }"
    >
      <template v-if="validation?.valid">
        <span class="validate-status__label ok">语法正确</span>
        <span v-if="validation.statementType" class="validate-meta">
          语句：{{ validation.statementType }}
        </span>
        <span v-if="validation.involvedTables?.length" class="validate-meta">
          表：{{ validation.involvedTables.join(', ') }}
        </span>
      </template>
      <template v-else-if="validation && !validation.valid">
        <el-tag type="danger" size="small">{{ errorTypeLabel(validation.errorType) }}</el-tag>
        <span class="validate-msg">{{ validation.message }}</span>
        <span
          v-if="validation.line != null && validation.column != null"
          class="validate-loc"
        >
          {{ validation.line }}:{{ validation.column }}
        </span>
        <button
          v-for="(s, i) in validation.suggestions ?? []"
          :key="i"
          type="button"
          class="validate-suggestion"
          @click="onApplySuggestion(s)"
        >
          {{ s }}
        </button>
      </template>
      <span v-else-if="validating" class="validate-meta">校验中…</span>
    </div>
    <AnalyzeResultPanel :result="result" :original-sql="lastOriginalSql" />
  </el-card>
</template>

<script setup lang="ts">
import { nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import SqlEditor from '@/components/SqlEditor.vue'
import AnalyzeResultPanel from '@/components/analyze/AnalyzeResultPanel.vue'
import { dataSourcePage } from '@/api/datasource'
import { analyzeSql, validateSql } from '@/api/sql'
import { ApiBizError } from '@/api/errors'
import type {
  DataSourceRes,
  SqlAnalyzeRes,
  SqlValidateErrorTypeEnum,
  SqlValidateRes,
} from '@/types/models'

const route = useRoute()

const dataSources = ref<DataSourceRes[]>([])
const dsLoading = ref(false)
const analyzing = ref(false)
const validating = ref(false)
const result = ref<SqlAnalyzeRes | null>(null)
const lastOriginalSql = ref('')
const validation = ref<SqlValidateRes | null>(null)
const editorRef = ref<InstanceType<typeof SqlEditor> | null>(null)

const VALIDATE_DEBOUNCE_MS = 300

let validateSeq = 0
let debounceTimer: ReturnType<typeof setTimeout> | null = null

const form = reactive({
  dataSourceId: undefined as number | undefined,
  sql: 'SELECT * FROM user LIMIT 10;',
  enableBenchmark: true,
  benchmarkIterations: 5,
  verifyIndexBenefit: false,
})

function errorTypeLabel(t?: SqlValidateErrorTypeEnum): string {
  const map: Record<SqlValidateErrorTypeEnum, string> = {
    SYNTAX: '语法',
    SAFETY: '安全',
    SEMANTIC: '语义',
    PARSER_UNSUPPORTED: '解析器不支持',
  }
  return t ? map[t] : '错误'
}

function applyValidationToEditor(res: SqlValidateRes | null): void {
  validation.value = res
  editorRef.value?.setValidationMarkers(res)
}

function emptyValidation(): SqlValidateRes {
  return {
    valid: false,
    errorType: 'SYNTAX',
    message: 'SQL 不能为空',
    line: 1,
    column: 1,
  }
}

async function runValidate(): Promise<void> {
  const seq = ++validateSeq
  const sql = form.sql
  if (!sql.trim()) {
    if (seq === validateSeq) {
      validating.value = false
      applyValidationToEditor(null)
    }
    return
  }
  validating.value = true
  try {
    const res = await validateSql({
      sql,
      dataSourceId: form.dataSourceId,
    })
    if (seq !== validateSeq) {
      return
    }
    applyValidationToEditor(res)
  } finally {
    if (seq === validateSeq) {
      validating.value = false
    }
  }
}

function scheduleDebouncedValidate(): void {
  if (debounceTimer) {
    clearTimeout(debounceTimer)
  }
  debounceTimer = setTimeout(() => {
    void runValidate()
  }, VALIDATE_DEBOUNCE_MS)
}

function onEditorPaste(): void {
  if (debounceTimer) {
    clearTimeout(debounceTimer)
    debounceTimer = null
  }
  // 粘贴发生在内容变更之后，等 v-model 同步到 form.sql 再校验
  void nextTick(() => runValidate())
}

function onValidateClick(): void {
  if (debounceTimer) {
    clearTimeout(debounceTimer)
    debounceTimer = null
  }
  void runValidate()
}

function onApplySuggestion(suggestion: string): void {
  editorRef.value?.applySuggestion(suggestion, validation.value?.token)
  scheduleDebouncedValidate()
}

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
    applyValidationToEditor(emptyValidation())
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
  } catch (err) {
    if (err instanceof ApiBizError && err.validation) {
      applyValidationToEditor(err.validation)
    }
  } finally {
    analyzing.value = false
  }
}

watch(
  () => form.sql,
  () => {
    scheduleDebouncedValidate()
  },
)

watch(
  () => form.dataSourceId,
  () => {
    scheduleDebouncedValidate()
  },
)

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
  scheduleDebouncedValidate()
})
</script>

<style scoped>
.inline-field {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.validate-status {
  margin-top: 8px;
  padding: 8px 12px;
  border-radius: 4px;
  font-size: 13px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  border: 1px solid #ebeef5;
  background: #fafafa;
}

.validate-status--ok {
  border-color: #b3e19d;
  background: #f0f9eb;
}

.validate-status--err {
  border-color: #fbc4c4;
  background: #fef0f0;
}

.validate-status__label.ok {
  color: #67c23a;
  font-weight: 600;
}

.validate-msg {
  color: #f56c6c;
}

.validate-loc {
  color: #909399;
  font-family: ui-monospace, monospace;
}

.validate-meta {
  color: #606266;
}

.validate-suggestion {
  border: none;
  background: transparent;
  color: #409eff;
  cursor: pointer;
  padding: 0;
  font-size: 13px;
  text-decoration: underline;
}

.validate-suggestion:hover {
  color: #66b1ff;
}
</style>
