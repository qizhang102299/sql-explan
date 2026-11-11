<template>
  <div class="sql-editor-wrap">
    <div ref="hostRef" class="sql-editor-host" :style="{ height: editorHeight }" />
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as monaco from 'monaco-editor'
import { ensureMonacoEnvironment } from '@/utils/monaco'
import type { SqlValidateRes } from '@/types/models'

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
  paste: []
}>()

const hostRef = ref<HTMLDivElement | null>(null)
const editorHeight = ref(props.height)
let editor: monaco.editor.IStandaloneCodeEditor | null = null
let skipEmit = false

const MARKER_OWNER = 'sql-validate'

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
  editor.onDidPaste(() => {
    if (!props.readonly) {
      emit('paste')
    }
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

function clearValidationMarkers(): void {
  const model = editor?.getModel()
  if (!model) {
    return
  }
  monaco.editor.setModelMarkers(model, MARKER_OWNER, [])
}

function setValidationMarkers(detail: SqlValidateRes | null | undefined): void {
  const model = editor?.getModel()
  if (!model) {
    return
  }
  if (!detail || detail.valid) {
    clearValidationMarkers()
    return
  }
  const line = detail.line ?? 1
  const column = detail.column ?? 1
  const endLine = detail.endLine ?? line
  const tokenLen = detail.token?.length ?? 1
  const endColumn = detail.endColumn ?? column + tokenLen
  monaco.editor.setModelMarkers(model, MARKER_OWNER, [
    {
      startLineNumber: line,
      startColumn: column,
      endLineNumber: endLine,
      endColumn: Math.max(endColumn, column + 1),
      message: detail.message ?? 'SQL 校验失败',
      severity: monaco.MarkerSeverity.Error,
    },
  ])
}

/**
 * 将建议中的目标文本替换到编辑器（优先替换 token，否则追加到光标处）。
 */
function applySuggestion(suggestion: string, replaceTarget?: string): void {
  if (!editor) {
    return
  }
  const match = suggestion.match(/你是否想(?:写|查询|使用列)\s+(.+)/)
  const replacement = match?.[1]?.trim()
  if (!replacement) {
    return
  }
  const model = editor.getModel()
  if (!model) {
    return
  }
  const full = model.getValue()
  const searchToken = replaceTarget?.trim() || replacement
  const tokenIdx = findCaseInsensitiveWord(full, searchToken)
  if (tokenIdx >= 0) {
    const start = model.getPositionAt(tokenIdx)
    const end = model.getPositionAt(tokenIdx + searchToken.length)
    editor.executeEdits('suggestion', [
      {
        range: new monaco.Range(
          start.lineNumber,
          start.column,
          end.lineNumber,
          end.column,
        ),
        text: replacement,
        forceMoveMarkers: true,
      },
    ])
    return
  }
  const sel = editor.getSelection()
  if (sel) {
    editor.executeEdits('suggestion', [
      { range: sel, text: replacement, forceMoveMarkers: true },
    ])
  }
}

function findCaseInsensitiveWord(text: string, word: string): number {
  const upper = text.toUpperCase()
  const target = word.toUpperCase()
  let idx = 0
  while (idx < upper.length) {
    const found = upper.indexOf(target, idx)
    if (found < 0) {
      return -1
    }
    const before = found === 0 || !/[A-Z0-9_]/.test(upper.charAt(found - 1))
    const afterEnd = found + target.length
    const after =
      afterEnd >= upper.length || !/[A-Z0-9_]/.test(upper.charAt(afterEnd))
    if (before && after) {
      return found
    }
    idx = found + 1
  }
  return -1
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

defineExpose({
  setValidationMarkers,
  clearValidationMarkers,
  applySuggestion,
})
</script>

<style scoped>
.sql-editor-wrap {
  width: 100%;
}

.sql-editor-host {
  width: 100%;
  min-width: 0;
  max-width: 100%;
  box-sizing: border-box;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
  background: #fff;
}
</style>
