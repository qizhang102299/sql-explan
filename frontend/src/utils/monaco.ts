import editorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker'

let configured = false

export function ensureMonacoEnvironment(): void {
  if (configured) {
    return
  }
  self.MonacoEnvironment = {
    getWorker() {
      return new editorWorker()
    },
  }
  configured = true
}
