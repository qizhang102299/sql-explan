<template>
  <div class="object-tree">
    <el-input
      v-model="filter"
      placeholder="搜索对象"
      clearable
      :prefix-icon="Search"
      size="small"
    />
    <el-tree
      ref="treeRef"
      class="tree-body"
      :data="treeData"
      node-key="id"
      :props="treeProps"
      :filter-node-method="filterNode"
      highlight-current
      @node-click="onNodeClick"
    >
      <template #default="{ data }">
        <span class="tree-node">
          <span class="node-label">{{ data.label }}</span>
          <el-badge
            v-if="data.badge != null"
            :value="data.badge"
            type="info"
            class="node-badge"
          />
        </span>
      </template>
    </el-tree>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Search } from '@element-plus/icons-vue'
import type { ElTree } from 'element-plus'
import type {
  DbObjectListRes,
  DbRoutineSummaryRes,
  DbRoutineTypeEnum,
} from '@/types/models'

export type DbObjectNodeKind = 'table' | 'view' | 'procedure' | 'function'

export interface DbObjectTreeSelection {
  kind: DbObjectNodeKind
  name: string
  routineType?: DbRoutineTypeEnum
}

interface TreeNode {
  id: string
  label: string
  badge?: number
  kind?: DbObjectNodeKind
  name?: string
  routineType?: DbRoutineTypeEnum
  children?: TreeNode[]
}

const props = defineProps<{
  list: DbObjectListRes | null
}>()

const emit = defineEmits<{
  select: [payload: DbObjectTreeSelection]
}>()

const filter = ref('')
const treeRef = ref<InstanceType<typeof ElTree>>()

const treeProps = {
  children: 'children',
  label: 'label',
}

const procedures = computed(() =>
  (props.list?.routines ?? []).filter((r) => r.routineType === 'PROCEDURE'),
)

const functions = computed(() =>
  (props.list?.routines ?? []).filter((r) => r.routineType === 'FUNCTION'),
)

const treeData = computed<TreeNode[]>(() => {
  const list = props.list
  if (!list) {
    return []
  }
  return [
    {
      id: 'group-tables',
      label: '表',
      badge: list.tables.length,
      children: list.tables.map((t) => leaf('table', t.name)),
    },
    {
      id: 'group-views',
      label: '视图',
      badge: list.views.length,
      children: list.views.map((v) => leaf('view', v.name)),
    },
    {
      id: 'group-procedures',
      label: '存储过程',
      badge: procedures.value.length,
      children: procedures.value.map((r) => leafRoutine('procedure', r)),
    },
    {
      id: 'group-functions',
      label: '函数',
      badge: functions.value.length,
      children: functions.value.map((r) => leafRoutine('function', r)),
    },
  ]
})

function leaf(kind: DbObjectNodeKind, name: string): TreeNode {
  return { id: `${kind}:${name}`, label: name, kind, name }
}

function leafRoutine(kind: DbObjectNodeKind, routine: DbRoutineSummaryRes): TreeNode {
  return {
    id: `${kind}:${routine.name}`,
    label: routine.name,
    kind,
    name: routine.name,
    routineType: routine.routineType,
  }
}

function filterNode(value: string, data: TreeNode): boolean {
  if (!value) {
    return true
  }
  if (!data.name) {
    return data.label.toLowerCase().includes(value.toLowerCase())
  }
  return data.name.toLowerCase().includes(value.toLowerCase())
}

watch(filter, (val) => {
  treeRef.value?.filter(val)
})

function onNodeClick(data: TreeNode): void {
  if (!data.kind || !data.name) {
    return
  }
  emit('select', {
    kind: data.kind,
    name: data.name,
    routineType: data.routineType,
  })
}
</script>

<style scoped>
.object-tree {
  display: flex;
  flex-direction: column;
  gap: 10px;
  height: 100%;
}

.tree-body {
  flex: 1;
  overflow: auto;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 8px;
}

.tree-node {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.node-badge {
  transform: scale(0.85);
}
</style>
