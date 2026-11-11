<template>
  <el-card shadow="never" class="browser-root">
    <div class="browser-toolbar">
      <el-button link type="primary" @click="router.push('/datasource')">← 返回数据源列表</el-button>
      <span v-if="dataSource" class="ds-title">
        {{ dataSource.name }}（{{ dataSource.host }}:{{ dataSource.port }}/{{ dataSource.dbName }}）
      </span>
      <el-button :loading="listLoading" size="small" @click="loadList">刷新</el-button>
    </div>
    <el-alert v-if="listError" :title="listError" type="error" show-icon :closable="false" />
    <div v-else class="browser-body">
      <aside class="browser-aside">
        <DbObjectTree :list="objectList" @select="onSelect" />
      </aside>
      <main class="browser-main">
        <template v-if="selection">
          <h3 class="object-title">{{ selectionTitle }}</h3>
          <DbTableDetailPanel
            v-if="selection.kind === 'table'"
            :loading="detailLoading"
            :error="detailError"
            :detail="tableDetail"
          />
          <DbViewDetailPanel
            v-else-if="selection.kind === 'view'"
            :data-source-id="dataSourceId"
            :loading="detailLoading"
            :error="detailError"
            :detail="viewDetail"
          />
          <DbRoutineDetailPanel
            v-else
            :loading="detailLoading"
            :error="detailError"
            :detail="routineDetail"
          />
        </template>
        <el-empty v-else description="在左侧选择表、视图或例程" />
      </main>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  dbObjectList,
  dbObjectRoutineDetail,
  dbObjectTableDetail,
  dbObjectViewDetail,
} from '@/api/dbobject'
import { dataSourceDetail } from '@/api/datasource'
import DbObjectTree, {
  type DbObjectTreeSelection,
} from '@/components/dbobject/DbObjectTree.vue'
import DbRoutineDetailPanel from '@/components/dbobject/DbRoutineDetailPanel.vue'
import DbTableDetailPanel from '@/components/dbobject/DbTableDetailPanel.vue'
import DbViewDetailPanel from '@/components/dbobject/DbViewDetailPanel.vue'
import type {
  DataSourceRes,
  DbObjectListRes,
  DbRoutineDetailRes,
  DbRoutineTypeEnum,
  DbTableDetailRes,
  DbViewDetailRes,
} from '@/types/models'

const route = useRoute()
const router = useRouter()

const dataSourceId = computed(() => Number(route.params.id))

const dataSource = ref<DataSourceRes | null>(null)
const objectList = ref<DbObjectListRes | null>(null)
const listLoading = ref(false)
const listError = ref('')

const selection = ref<DbObjectTreeSelection | null>(null)
const detailLoading = ref(false)
const detailError = ref('')
const tableDetail = ref<DbTableDetailRes | null>(null)
const viewDetail = ref<DbViewDetailRes | null>(null)
const routineDetail = ref<DbRoutineDetailRes | null>(null)

const selectionTitle = computed(() => {
  if (!selection.value) {
    return ''
  }
  const kindMap: Record<string, string> = {
    table: '表',
    view: '视图',
    procedure: '存储过程',
    function: '函数',
  }
  return `${kindMap[selection.value.kind] ?? ''} · ${selection.value.name}`
})

async function loadDataSource(): Promise<void> {
  if (Number.isNaN(dataSourceId.value)) {
    ElMessage.error('无效的数据源 ID')
    router.replace('/datasource')
    return
  }
  dataSource.value = await dataSourceDetail({ id: dataSourceId.value })
}

async function loadList(): Promise<void> {
  listLoading.value = true
  listError.value = ''
  try {
    objectList.value = await dbObjectList({ dataSourceId: dataSourceId.value })
  } catch (err) {
    listError.value = err instanceof Error ? err.message : '加载对象列表失败'
    objectList.value = null
  } finally {
    listLoading.value = false
  }
}

function resetDetail(): void {
  tableDetail.value = null
  viewDetail.value = null
  routineDetail.value = null
  detailError.value = ''
}

async function onSelect(payload: DbObjectTreeSelection): Promise<void> {
  selection.value = payload
  resetDetail()
  detailLoading.value = true
  try {
    if (payload.kind === 'table') {
      tableDetail.value = await dbObjectTableDetail({
        dataSourceId: dataSourceId.value,
        tableName: payload.name,
      })
    } else if (payload.kind === 'view') {
      viewDetail.value = await dbObjectViewDetail({
        dataSourceId: dataSourceId.value,
        viewName: payload.name,
      })
    } else {
      const routineType: DbRoutineTypeEnum =
        payload.kind === 'function' ? 'FUNCTION' : 'PROCEDURE'
      routineDetail.value = await dbObjectRoutineDetail({
        dataSourceId: dataSourceId.value,
        routineName: payload.name,
        routineType: payload.routineType ?? routineType,
      })
    }
  } catch (err) {
    detailError.value = err instanceof Error ? err.message : '加载详情失败'
  } finally {
    detailLoading.value = false
  }
}

onMounted(async () => {
  listLoading.value = true
  try {
    await loadDataSource()
    await loadList()
  } catch {
    router.replace('/datasource')
  } finally {
    listLoading.value = false
  }
})
</script>

<style scoped>
.browser-root {
  min-height: calc(100vh - 120px);
}

.browser-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.ds-title {
  font-weight: 600;
  flex: 1;
}

.browser-body {
  display: flex;
  gap: 16px;
  min-height: 520px;
}

.browser-aside {
  width: 280px;
  flex-shrink: 0;
  min-height: 480px;
}

.browser-main {
  flex: 1;
  min-width: 0;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 16px;
}

.object-title {
  margin: 0 0 12px;
  font-size: 16px;
}
</style>
