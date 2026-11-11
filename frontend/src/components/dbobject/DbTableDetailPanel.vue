<template>
  <div v-loading="loading" class="table-detail">
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-tabs v-else-if="detail" v-model="activeTab">
      <el-tab-pane label="概览" name="overview">
        <div class="card-grid">
          <el-card shadow="never" class="info-card">
            <div class="card-title">引擎</div>
            <div>{{ detail.basic?.engine ?? '-' }}</div>
          </el-card>
          <el-card shadow="never" class="info-card">
            <div class="card-title">字符集</div>
            <div>{{ detail.basic?.charset ?? '-' }}</div>
          </el-card>
          <el-card shadow="never" class="info-card">
            <div class="card-title">排序规则</div>
            <div>{{ detail.basic?.collation ?? '-' }}</div>
          </el-card>
          <el-card shadow="never" class="info-card">
            <div class="card-title">估计行数</div>
            <div>{{ detail.basic?.estimatedRows ?? '-' }}</div>
          </el-card>
          <el-card shadow="never" class="info-card">
            <div class="card-title">大小</div>
            <div>{{ formatBytes(detail.basic?.dataSizeBytes) }}</div>
          </el-card>
          <el-card shadow="never" class="info-card">
            <div class="card-title">创建时间</div>
            <div>{{ formatTimestamp(detail.basic?.createdTime) }}</div>
          </el-card>
        </div>
        <p v-if="detail.basic?.comment" class="table-comment">注释：{{ detail.basic.comment }}</p>
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
          <el-table-column label="键" width="88">
            <template #default="{ row }">
              <el-tag v-if="row.columnKey === 'PRI'" type="danger" size="small">PRI</el-tag>
              <el-tag v-else-if="row.columnKey === 'UNI'" type="warning" size="small">UNI</el-tag>
              <el-tag v-else-if="row.columnKey === 'MUL'" type="info" size="small">MUL</el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="extra" label="Extra" min-width="100" />
          <el-table-column prop="comment" label="注释" min-width="140" show-overflow-tooltip />
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="索引" name="indexes">
        <el-empty v-if="!detail.indexes.length" description="无索引" />
        <el-collapse v-else>
          <el-collapse-item
            v-for="idx in detail.indexes"
            :key="idx.indexName"
            :title="indexTitle(idx)"
          >
            <el-table :data="idx.columns" size="small" border>
              <el-table-column prop="seqInIndex" label="顺序" width="64" />
              <el-table-column prop="columnName" label="列" />
              <el-table-column label="基数">
                <template #default="{ row }">{{ row.cardinality ?? '-' }}</template>
              </el-table-column>
            </el-table>
          </el-collapse-item>
        </el-collapse>
      </el-tab-pane>
      <el-tab-pane label="外键" name="fks">
        <el-empty v-if="!detail.foreignKeys.length" description="无外键" />
        <el-table v-else :data="detail.foreignKeys" stripe size="small" border>
          <el-table-column prop="constraintName" label="约束名" min-width="140" />
          <el-table-column label="列" min-width="120">
            <template #default="{ row }">{{ row.columnNames.join(', ') }}</template>
          </el-table-column>
          <el-table-column prop="referencedTableName" label="引用表" width="120" />
          <el-table-column label="引用列" min-width="120">
            <template #default="{ row }">{{ row.referencedColumnNames.join(', ') }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="DDL" name="ddl">
        <SqlReadonlyBlock :sql="detail.createTableDdl ?? ''" height="360px" />
      </el-tab-pane>
    </el-tabs>
    <el-empty v-else-if="!loading" description="请在左侧选择一张表" />
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import type { DbTableDetailRes, DbTableIndexRes } from '@/types/models'
import { formatTimestamp } from '@/utils/format'
import SqlReadonlyBlock from './SqlReadonlyBlock.vue'

const props = defineProps<{
  loading: boolean
  error: string
  detail: DbTableDetailRes | null
}>()

const activeTab = ref('overview')

watch(
  () => props.detail?.basic?.tableName,
  () => {
    activeTab.value = 'overview'
  },
)

function formatBytes(bytes?: number | null): string {
  if (bytes == null || bytes <= 0) {
    return '-'
  }
  if (bytes < 1024) {
    return `${bytes} B`
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`
  }
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

function indexTitle(idx: DbTableIndexRes): string {
  const unique = idx.unique ? '唯一' : '非唯一'
  return `${idx.indexName}（${unique} · ${idx.indexType ?? 'BTREE'}）`
}
</script>

<style scoped>
.table-detail {
  min-height: 200px;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 12px;
}

.info-card {
  text-align: center;
}

.card-title {
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
}

.table-comment {
  margin-top: 12px;
  font-size: 13px;
  color: #606266;
}
</style>
