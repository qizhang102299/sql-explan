<template>
  <div ref="hostRef" class="sql-editor-host" :style="{ height: editorHeight }" />
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as monaco from 'monaco-editor'
import { ensureMonacoEnvironment } from '@/utils/monaco'

const props = withDefaults(
  defineProps<{
    modelValue: string
    readonly?: boolean
    height?: string
  }>(),
  {
    readonly: false,
    height: '280px',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const hostRef = ref<HTMLDivElement | null>(null)
const editorHeight = ref(props.height)
let editor: monaco.editor.IStandaloneCodeEditor | null = null
let skipEmit = false

function initEditor(): void {
  if (!hostRef.value) {
    return
  }
  ensureMonacoEnvironment()
  editor = monaco.editor.create(hostRef.value, {
    value: props.modelValue,
    language: 'sql',
    theme: 'vs',
    readOnly: props.readonly,
    automaticLayout: true,
    minimap: { enabled: false },
    fontSize: 13,
    scrollBeyondLastLine: false,
    wordWrap: 'on',
  })
  editor.onDidChangeModelContent(() => {
    if (!editor || props.readonly) {
      return
    }
    skipEmit = true
    emit('update:modelValue', editor.getValue())
    skipEmit = false
  })
}

watch(
  () => props.modelValue,
  (val) => {
    if (!editor || skipEmit) {
      return
    }
    const current = editor.getValue()
    if (current !== val) {
      editor.setValue(val ?? '')
    }
  },
)

watch(
  () => props.readonly,
  (ro) => {
    editor?.updateOptions({ readOnly: ro })
  },
)

watch(
  () => props.height,
  (h) => {
    editorHeight.value = h
    editor?.layout()
  },
)

onMounted(() => {
  initEditor()
})

onBeforeUnmount(() => {
  editor?.dispose()
  editor = null
})
</script>

<style scoped>
.sql-editor-host {
  width: 100%;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
  background: #fff;
}
</style>
