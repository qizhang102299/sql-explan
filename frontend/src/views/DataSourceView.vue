<template>
  <el-card shadow="never">
    <div class="page-toolbar">
      <el-input
        v-model="keyword"
        placeholder="按名称搜索"
        clearable
        style="width: 220px"
        @keyup.enter="loadPage"
      />
      <el-button type="primary" @click="loadPage">查询</el-button>
      <el-button type="success" @click="openDialog()">新增数据源</el-button>
    </div>
    <el-table v-loading="loading" :data="records" stripe>
      <el-table-column prop="name" label="名称" min-width="120" />
      <el-table-column prop="host" label="主机" min-width="140" />
      <el-table-column prop="port" label="端口" width="80" />
      <el-table-column prop="dbName" label="数据库" min-width="100" />
      <el-table-column prop="username" label="用户名" width="120" />
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">
          {{ formatTimestamp(row.createdTime) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="openDialog(row)">编辑</el-button>
          <el-button type="primary" link @click="onTest(row)">测试</el-button>
          <el-button type="danger" link @click="onDelete(row)">删除</el-button>
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

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑数据源' : '新增数据源'"
      width="520px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="主机" prop="host">
          <el-input v-model="form.host" />
        </el-form-item>
        <el-form-item label="端口" prop="port">
          <el-input-number v-model="form.port" :min="1" :max="65535" />
        </el-form-item>
        <el-form-item label="数据库" prop="dbName">
          <el-input v-model="form.dbName" />
        </el-form-item>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="editingId ? '留空则保留原密码' : '请输入密码'"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button :loading="testing" @click="onTestForm">测试连接</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteDataSource,
  dataSourcePage,
  saveDataSource,
  testDataSource,
} from '@/api/datasource'
import type { DataSourceRes } from '@/types/models'
import { formatTimestamp } from '@/utils/format'

const loading = ref(false)
const records = ref<DataSourceRes[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const keyword = ref('')

const dialogVisible = ref(false)
const editingId = ref<number | undefined>()
const saving = ref(false)
const testing = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  name: '',
  host: '127.0.0.1',
  port: 3308,
  dbName: '',
  username: '',
  password: '',
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  host: [{ required: true, message: '请输入主机', trigger: 'blur' }],
  port: [{ required: true, message: '请输入端口', trigger: 'change' }],
  dbName: [{ required: true, message: '请输入数据库名', trigger: 'blur' }],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
}

async function loadPage(): Promise<void> {
  loading.value = true
  try {
    const res = await dataSourcePage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      nameKeyword: keyword.value || undefined,
    })
    records.value = res.records ?? []
    total.value = res.total ?? 0
  } finally {
    loading.value = false
  }
}

function resetForm(): void {
  editingId.value = undefined
  form.name = ''
  form.host = '127.0.0.1'
  form.port = 3308
  form.dbName = ''
  form.username = ''
  form.password = ''
}

function openDialog(row?: DataSourceRes): void {
  if (row) {
    editingId.value = row.id
    form.name = row.name
    form.host = row.host
    form.port = row.port
    form.dbName = row.dbName
    form.username = row.username
    form.password = ''
  } else {
    resetForm()
  }
  dialogVisible.value = true
}

async function onSave(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  if (!editingId.value && !form.password) {
    ElMessage.warning('新增时请填写密码')
    return
  }
  saving.value = true
  try {
    await saveDataSource({
      id: editingId.value,
      name: form.name,
      host: form.host,
      port: form.port,
      dbName: form.dbName,
      username: form.username,
      password: form.password || undefined,
    })
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadPage()
  } finally {
    saving.value = false
  }
}

async function onTest(row: DataSourceRes): Promise<void> {
  const res = await testDataSource({ dataSourceId: row.id })
  if (res.success) {
    ElMessage.success(res.message || `连通，耗时 ${res.elapsedMs} ms`)
  } else {
    ElMessage.error(res.message || '连接失败')
  }
}

async function onTestForm(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  testing.value = true
  try {
    const res = await testDataSource({
      dataSourceId: editingId.value,
      host: form.host,
      port: form.port,
      dbName: form.dbName,
      username: form.username,
      password: form.password || undefined,
    })
    if (res.success) {
      ElMessage.success(res.message || `连通，耗时 ${res.elapsedMs} ms`)
    } else {
      ElMessage.error(res.message || '连接失败')
    }
  } finally {
    testing.value = false
  }
}

async function onDelete(row: DataSourceRes): Promise<void> {
  await ElMessageBox.confirm(`确定删除数据源「${row.name}」？`, '提示', {
    type: 'warning',
  })
  await deleteDataSource({ id: row.id })
  ElMessage.success('已删除')
  await loadPage()
}

onMounted(() => {
  loadPage()
})
</script>

<style scoped>
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
