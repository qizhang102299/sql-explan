<template>
  <el-tree
    v-if="treeData.length"
    :data="treeData"
    default-expand-all
    :props="{ label: 'label', children: 'children' }"
    class="plan-tree"
  >
    <template #default="{ data }">
      <div class="node-row">
        <span class="node-label">{{ data.label }}</span>
        <el-tag
          v-if="data.accessType"
          size="small"
          :type="accessTagType(data.accessType)"
          effect="plain"
        >
          {{ data.accessType }}
        </el-tag>
        <span v-if="data.rows != null" class="node-meta">
          扫描约 {{ data.rows }} 行
        </span>
        <span v-if="data.indexKey" class="node-meta">
          索引: {{ data.indexKey }}
        </span>
      </div>
    </template>
  </el-tree>
  <el-empty v-else description="暂无执行计划节点" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { PlanNode } from '@/types/models'
import { nodeKindLabel } from '@/utils/format'

const props = defineProps<{
  root?: PlanNode
}>()

interface TreeItem {
  label: string
  accessType?: string
  rows?: number
  indexKey?: string
  children?: TreeItem[]
}

function accessTagType(access?: string): 'danger' | 'warning' | 'success' {
  if (!access) {
    return 'warning'
  }
  const u = access.toUpperCase()
  if (u === 'ALL') {
    return 'danger'
  }
  if (u === 'INDEX' || u === 'RANGE' || u === 'REF' || u === 'EQ_REF') {
    return 'success'
  }
  return 'warning'
}

function nodeLabel(node: PlanNode): string {
  if (node.operationLabel) {
    return node.operationLabel
  }
  if (node.tableName) {
    return `表 ${node.tableName}`
  }
  return nodeKindLabel(node.nodeKind)
}

function mapNode(node: PlanNode): TreeItem {
  const children = (node.children ?? []).map(mapNode)
  return {
    label: nodeLabel(node),
    accessType: node.accessType,
    rows: node.rowsExaminedPerScan ?? undefined,
    indexKey: node.indexKey,
    children: children.length ? children : undefined,
  }
}

const treeData = computed(() => {
  if (!props.root) {
    return []
  }
  return [mapNode(props.root)]
})
</script>

<style scoped>
.plan-tree {
  background: #fff;
  padding: 8px;
  border-radius: 4px;
}

.node-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  line-height: 1.6;
}

.node-label {
  font-weight: 500;
}

.node-meta {
  font-size: 12px;
  color: #909399;
}
</style>
