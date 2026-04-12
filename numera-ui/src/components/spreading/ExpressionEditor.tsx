'use client'

import { useEffect, useMemo, useState } from 'react'
import type { SpreadValue } from '@/types/spread'

interface ExpressionEditorProps {
  open: boolean
  targetValue: SpreadValue | null
  allValues: SpreadValue[]
  onClose: () => void
  onSave: (expression: string, computedValue: number) => Promise<void>
}

function validateExpression(expression: string, currentCode?: string | null): string | null {
  const trimmed = expression.trim()
  if (!trimmed) return 'Expression is required.'

  let balance = 0
  for (const ch of trimmed) {
    if (ch === '(') balance += 1
    if (ch === ')') balance -= 1
    if (balance < 0) return 'Parentheses are not balanced.'
  }
  if (balance !== 0) return 'Parentheses are not balanced.'

  if (/[*+\-/]{2,}/.test(trimmed.replace(/\s+/g, ''))) {
    return 'Expression contains consecutive operators.'
  }

  if (currentCode && new RegExp(`\\{${currentCode}\\}`, 'i').test(trimmed)) {
    return 'Expression cannot reference itself.'
  }

  return null
}

function evaluateExpression(expression: string, valuesByCode: Record<string, number>): number {
  const substituted = expression.replace(/\{([A-Za-z0-9_\-.]+)\}/g, (_, code: string) => {
    const value = valuesByCode[code] ?? 0
    return String(value)
  })

  const normalized = substituted
    .replace(/\bABS\s*\(/gi, 'Math.abs(')
    .replace(/\bNEG\s*\(/gi, '-(')
    .replace(/\bCONTRA\s*\(/gi, '-(')
    .replace(/×/g, '*')
    .replace(/÷/g, '/')

  if (!/^[0-9+\-*/().,\sA-Za-z]*$/.test(normalized)) {
    throw new Error('Expression contains unsupported characters.')
  }

  // eslint-disable-next-line no-new-func
  const result = Function(`"use strict"; return (${normalized});`)()
  if (typeof result !== 'number' || Number.isNaN(result) || !Number.isFinite(result)) {
    throw new Error('Expression did not evaluate to a numeric value.')
  }

  return result
}

export function ExpressionEditor({ open, targetValue, allValues, onClose, onSave }: ExpressionEditorProps) {
  const [expression, setExpression] = useState('')
  const [isSaving, setIsSaving] = useState(false)

  useEffect(() => {
    if (!open || !targetValue) return
    setExpression(targetValue.expressionDetail?.formula as string ?? '')
  }, [open, targetValue])

  const valuesByCode = useMemo(() => {
    const entries = allValues
      .filter((v) => v.mappedValue != null)
      .map((v) => [v.itemCode, Number(v.mappedValue)] as const)
    return Object.fromEntries(entries)
  }, [allValues])

  const validationError = useMemo(
    () => validateExpression(expression, targetValue?.itemCode),
    [expression, targetValue?.itemCode]
  )

  const previewValue = useMemo(() => {
    if (validationError) return null
    try {
      return evaluateExpression(expression, valuesByCode)
    } catch {
      return null
    }
  }, [expression, valuesByCode, validationError])

  if (!open || !targetValue) return null

  const sourceReferences = allValues
    .filter((v) => v.sourcePage != null)
    .slice(0, 8)

  const appendToken = (token: string) => {
    setExpression((prev) => `${prev}${token}`)
  }

  const handleSave = async () => {
    if (validationError || previewValue == null) return
    setIsSaving(true)
    try {
      await onSave(expression, previewValue)
      onClose()
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(0, 0, 0, 0.45)',
        zIndex: 60,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 20,
      }}
    >
      <div
        style={{
          width: 'min(760px, 96vw)',
          maxHeight: '90vh',
          overflow: 'auto',
          background: 'var(--bg-secondary)',
          border: '1px solid var(--border-subtle)',
          borderRadius: 12,
          padding: 16,
          display: 'flex',
          flexDirection: 'column',
          gap: 12,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div>
            <div style={{ fontWeight: 700, fontSize: 15 }}>Expression Editor</div>
            <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
              {targetValue.itemCode} | {targetValue.label}
            </div>
          </div>
          <button className="btn btn-ghost btn-sm" onClick={onClose}>Close</button>
        </div>

        <textarea
          className="input"
          rows={4}
          placeholder="Example: {REV_001} - ABS({EXP_010}) + 1000"
          value={expression}
          onChange={(e) => setExpression(e.target.value)}
          style={{ fontFamily: 'var(--font-mono)' }}
        />

        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
          {['+', '-', '×', '÷', '(', ')'].map((token) => (
            <button key={token} className="btn btn-ghost btn-sm" onClick={() => appendToken(token)}>{token}</button>
          ))}
          {['ABS()', 'NEG()', 'CONTRA()'].map((fn) => (
            <button key={fn} className="btn btn-ghost btn-sm" onClick={() => appendToken(fn)}>{fn}</button>
          ))}
          {['*1000', '/1000', '*1000000', '/1000000'].map((scale) => (
            <button key={scale} className="btn btn-ghost btn-sm" onClick={() => appendToken(scale)}>{scale}</button>
          ))}
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>Source References</div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
            {sourceReferences.map((value) => (
              <button
                key={value.id}
                className="btn btn-ghost btn-sm"
                onClick={() => appendToken(`{${value.itemCode}}`)}
                title={`Page ${value.sourcePage ?? '-'} | ${value.label}`}
              >
                {value.itemCode} (Pg {value.sourcePage ?? '-'})
              </button>
            ))}
            {sourceReferences.length === 0 && (
              <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>No source references available.</span>
            )}
          </div>
        </div>

        <div
          style={{
            padding: '10px 12px',
            borderRadius: 8,
            border: '1px solid var(--border-subtle)',
            background: 'var(--bg-primary)',
            fontSize: 13,
          }}
        >
          {validationError ? (
            <span style={{ color: '#ff453a' }}>{validationError}</span>
          ) : (
            <span>
              Live Preview: <strong>{previewValue == null ? '-' : previewValue.toLocaleString()}</strong>
            </span>
          )}
        </div>

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <button className="btn btn-ghost btn-sm" onClick={onClose}>Cancel</button>
          <button
            className="btn btn-primary btn-sm"
            onClick={handleSave}
            disabled={isSaving || !!validationError || previewValue == null}
          >
            {isSaving ? 'Saving...' : 'Save Formula'}
          </button>
        </div>
      </div>
    </div>
  )
}
