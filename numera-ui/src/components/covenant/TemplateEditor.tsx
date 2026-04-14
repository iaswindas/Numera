'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { Bold, Italic, Underline, AlignLeft, AlignCenter, AlignRight, List, ListOrdered, Undo, Redo, Copy, Eye, EyeOff } from 'lucide-react'
import VariableInserter, { TEMPLATE_VARIABLES } from './VariableInserter'
import { sanitizeHtml } from '@/utils/sanitize'

type TemplateEditorProps = {
  value: string
  onChange: (html: string) => void
  category?: 'Financial' | 'Non-Financial' | null
}

const SAMPLE_DATA: Record<string, string> = Object.fromEntries(
  TEMPLATE_VARIABLES.map((v) => [v.key, v.sampleValue])
)

function resolveSampleData(html: string): string {
  let resolved = html
  for (const [key, val] of Object.entries(SAMPLE_DATA)) {
    resolved = resolved.replaceAll(key, `<span style="background:#e0f2fe;border-radius:2px;padding:0 2px">${val}</span>`)
  }
  return resolved
}

export default function TemplateEditor({ value, onChange, category }: TemplateEditorProps) {
  const editorRef = useRef<HTMLDivElement>(null)
  const [showPreview, setShowPreview] = useState(false)
  const isInternalUpdate = useRef(false)

  useEffect(() => {
    if (editorRef.current && !isInternalUpdate.current) {
      editorRef.current.innerHTML = sanitizeHtml(value)
    }
    isInternalUpdate.current = false
  }, [value])

  const execCommand = useCallback((command: string, val?: string) => {
    document.execCommand(command, false, val)
    editorRef.current?.focus()
    syncContent()
  }, [])

  const syncContent = useCallback(() => {
    if (editorRef.current) {
      isInternalUpdate.current = true
      onChange(editorRef.current.innerHTML)
    }
  }, [onChange])

  const handleInsertVariable = useCallback((variable: string) => {
    const editor = editorRef.current
    if (!editor) return
    editor.focus()

    const sel = window.getSelection()
    if (sel && sel.rangeCount > 0) {
      const range = sel.getRangeAt(0)
      const span = document.createElement('span')
      span.style.background = 'var(--primary-light, #dbeafe)'
      span.style.borderRadius = '3px'
      span.style.padding = '0 3px'
      span.style.fontFamily = 'monospace'
      span.style.fontSize = '12px'
      span.textContent = variable
      span.contentEditable = 'false'
      range.deleteContents()
      range.insertNode(span)
      range.setStartAfter(span)
      range.collapse(true)
      sel.removeAllRanges()
      sel.addRange(range)
    } else {
      editor.innerHTML += variable
    }
    syncContent()
  }, [syncContent])

  const toolbarButtons: Array<{ icon: React.ReactNode; command: string; title: string; value?: string }> = [
    { icon: <Bold size={14} />, command: 'bold', title: 'Bold' },
    { icon: <Italic size={14} />, command: 'italic', title: 'Italic' },
    { icon: <Underline size={14} />, command: 'underline', title: 'Underline' },
    { icon: <AlignLeft size={14} />, command: 'justifyLeft', title: 'Align Left' },
    { icon: <AlignCenter size={14} />, command: 'justifyCenter', title: 'Center' },
    { icon: <AlignRight size={14} />, command: 'justifyRight', title: 'Align Right' },
    { icon: <List size={14} />, command: 'insertUnorderedList', title: 'Bullet List' },
    { icon: <ListOrdered size={14} />, command: 'insertOrderedList', title: 'Numbered List' },
    { icon: <Undo size={14} />, command: 'undo', title: 'Undo' },
    { icon: <Redo size={14} />, command: 'redo', title: 'Redo' },
  ]

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
      {/* Toolbar */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 2,
          padding: '6px 8px',
          borderBottom: '1px solid var(--border-color)',
          background: 'var(--bg-secondary)',
          borderRadius: '6px 6px 0 0',
          flexWrap: 'wrap',
        }}
      >
        {toolbarButtons.map((btn) => (
          <button
            key={btn.command}
            type="button"
            title={btn.title}
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              width: 28,
              height: 28,
              border: 'none',
              background: 'none',
              cursor: 'pointer',
              borderRadius: 4,
              color: 'var(--text-primary)',
            }}
            onMouseDown={(e) => e.preventDefault()}
            onClick={() => execCommand(btn.command, btn.value)}
          >
            {btn.icon}
          </button>
        ))}

        <div style={{ width: 1, height: 20, background: 'var(--border-color)', margin: '0 4px' }} />

        <select
          style={{ fontSize: 12, padding: '2px 6px', border: '1px solid var(--border-color)', borderRadius: 4, background: 'var(--bg-primary)' }}
          onChange={(e) => {
            if (e.target.value) execCommand('formatBlock', e.target.value)
          }}
          defaultValue=""
        >
          <option value="" disabled>Heading</option>
          <option value="p">Normal</option>
          <option value="h1">Heading 1</option>
          <option value="h2">Heading 2</option>
          <option value="h3">Heading 3</option>
        </select>

        <select
          style={{ fontSize: 12, padding: '2px 6px', border: '1px solid var(--border-color)', borderRadius: 4, background: 'var(--bg-primary)' }}
          onChange={(e) => {
            if (e.target.value) execCommand('fontSize', e.target.value)
          }}
          defaultValue=""
        >
          <option value="" disabled>Size</option>
          <option value="1">Small</option>
          <option value="3">Normal</option>
          <option value="5">Large</option>
          <option value="7">Extra Large</option>
        </select>

        <div style={{ flex: 1 }} />

        <button
          type="button"
          className="btn btn-ghost btn-sm"
          onClick={() => setShowPreview(!showPreview)}
          title={showPreview ? 'Hide Preview' : 'Show Preview'}
          style={{ display: 'flex', alignItems: 'center', gap: 4 }}
        >
          {showPreview ? <EyeOff size={14} /> : <Eye size={14} />}
          {showPreview ? 'Editor' : 'Preview'}
        </button>

        <VariableInserter onInsert={handleInsertVariable} filterCategory={category} />
      </div>

      {/* Editor / Preview */}
      <div style={{ display: 'flex', gap: 0, minHeight: 300 }}>
        {/* Editor pane */}
        {!showPreview && (
          <div
            ref={editorRef}
            contentEditable
            suppressContentEditableWarning
            onInput={syncContent}
            style={{
              flex: 1,
              padding: 16,
              minHeight: 300,
              outline: 'none',
              border: '1px solid var(--border-color)',
              borderTop: 'none',
              borderRadius: '0 0 6px 6px',
              fontFamily: 'inherit',
              fontSize: 14,
              lineHeight: 1.6,
              overflow: 'auto',
            }}
          />
        )}

        {/* Live preview with sample data */}
        {showPreview && (
          <div
            style={{
              flex: 1,
              padding: 16,
              minHeight: 300,
              border: '1px solid var(--border-color)',
              borderTop: 'none',
              borderRadius: '0 0 6px 6px',
              background: '#fff',
              color: '#1a1a1a',
              overflow: 'auto',
            }}
          >
            <div style={{ marginBottom: 8, fontSize: 11, fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase' }}>
              Preview with Sample Data
            </div>
            <div
              dangerouslySetInnerHTML={{ __html: sanitizeHtml(resolveSampleData(value)) }}
              style={{ fontSize: 14, lineHeight: 1.6 }}
            />
          </div>
        )}
      </div>
    </div>
  )
}
