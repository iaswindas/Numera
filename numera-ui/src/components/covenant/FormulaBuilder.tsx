'use client'

import { useMemo, useRef, useState } from 'react'
import { Search } from 'lucide-react'

export interface LineItem {
  itemCode: string
  label: string
  category?: string
  zone?: string
}

type FormulaBuilderProps = {
  value: string
  onChange: (formula: string) => void
  lineItems: LineItem[]
}

const OPERATORS = [
  { label: '+', value: '+' },
  { label: '-', value: '-' },
  { label: 'x', value: '*' },
  { label: '÷', value: '/' },
  { label: '(', value: '(' },
  { label: ')', value: ')' },
]

function validateFormula(formula: string): string | null {
  if (!formula.trim()) return 'Formula is required'

  let depth = 0
  for (const char of formula) {
    if (char === '(') depth += 1
    if (char === ')') depth -= 1
    if (depth < 0) return 'Unmatched closing parenthesis'
  }
  if (depth !== 0) return 'Parentheses are not balanced'

  if (/([+\-*/]\s*){2,}/.test(formula)) {
    return 'Consecutive operators are not allowed'
  }

  if (/^[+*/]|[+\-*/]$/.test(formula.trim())) {
    return 'Formula cannot start or end with an operator'
  }

  return null
}

function renderPreview(formula: string, lineItems: LineItem[]): string {
  return formula.replace(/\{([A-Za-z0-9_-]+)\}/g, (_, code: string) => {
    const found = lineItems.find((item) => item.itemCode === code)
    return found ? found.label : code
  })
}

export default function FormulaBuilder({ value, onChange, lineItems }: FormulaBuilderProps) {
  const textareaRef = useRef<HTMLTextAreaElement | null>(null)
  const [searchQuery, setSearchQuery] = useState('')

  const filteredItems = useMemo(() => {
    const normalized = searchQuery.trim().toLowerCase()
    if (!normalized) return lineItems
    return lineItems.filter(
      (item) =>
        item.itemCode.toLowerCase().includes(normalized) ||
        item.label.toLowerCase().includes(normalized) ||
        item.category?.toLowerCase().includes(normalized)
    )
  }, [lineItems, searchQuery])

  const groupedItems = useMemo(() => {
    const groups = new Map<string, LineItem[]>()
    for (const item of filteredItems) {
      const key = item.category?.trim() || 'Uncategorized'
      const group = groups.get(key)
      if (group) {
        group.push(item)
      } else {
        groups.set(key, [item])
      }
    }
    return Array.from(groups.entries())
  }, [filteredItems])

  const syntaxError = useMemo(() => validateFormula(value), [value])
  const livePreview = useMemo(() => renderPreview(value, lineItems), [value, lineItems])

  const insertAtCursor = (token: string) => {
    const node = textareaRef.current
    if (!node) {
      onChange(`${value}${token}`)
      return
    }

    const start = node.selectionStart
    const end = node.selectionEnd
    const prefix = value.slice(0, start)
    const suffix = value.slice(end)
    const nextValue = `${prefix}${token}${suffix}`
    onChange(nextValue)

    requestAnimationFrame(() => {
      node.focus()
      const nextCursor = start + token.length
      node.setSelectionRange(nextCursor, nextCursor)
    })
  }

  return (
    <div className="card" style={{ padding: 16 }}>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'minmax(220px, 1fr) minmax(360px, 2fr) 84px',
          gap: 12,
          alignItems: 'start',
        }}
      >
        <section className="card" style={{ padding: 12, maxHeight: 420, overflow: 'auto' }}>
          <div className="input-group" style={{ marginBottom: 10 }}>
            <label>Line Items</label>
            <div className="search-bar" style={{ minWidth: 0 }}>
              <Search size={14} />
              <input
                placeholder="Search line items"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
            </div>
          </div>

          {groupedItems.length === 0 ? (
            <div style={{ color: 'var(--text-muted)', fontSize: 12 }}>No matching line items found.</div>
          ) : (
            groupedItems.map(([category, items]) => (
              <div key={category} style={{ marginBottom: 12 }}>
                <div
                  style={{
                    fontSize: 11,
                    textTransform: 'uppercase',
                    letterSpacing: 0.8,
                    color: 'var(--text-muted)',
                    marginBottom: 6,
                  }}
                >
                  {category}
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                  {items.map((item) => (
                    <button
                      key={item.itemCode}
                      type="button"
                      className="btn btn-ghost btn-sm"
                      style={{
                        justifyContent: 'space-between',
                        border: '1px solid var(--border-subtle)',
                        borderRadius: 6,
                        width: '100%',
                      }}
                      onClick={() => insertAtCursor(`{${item.itemCode}}`)}
                    >
                      <span>{item.label}</span>
                      <span style={{ fontFamily: 'monospace', fontSize: 10, color: 'var(--text-muted)' }}>{item.itemCode}</span>
                    </button>
                  ))}
                </div>
              </div>
            ))
          )}
        </section>

        <section className="card" style={{ padding: 12 }}>
          <div className="input-group" style={{ marginBottom: 10 }}>
            <label>Formula Editor</label>
            <textarea
              ref={textareaRef}
              className="input"
              rows={7}
              value={value}
              onChange={(e) => onChange(e.target.value)}
              placeholder="{TOTAL_DEBT} / {EBITDA}"
              style={{ fontFamily: 'monospace' }}
            />
          </div>

          <div style={{ marginBottom: 8, fontSize: 12, color: 'var(--text-secondary)' }}>Tokens detected</div>
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', minHeight: 30 }}>
            {Array.from(value.matchAll(/\{([A-Za-z0-9_-]+)\}/g)).map((token, idx) => (
              <span
                key={`${token[1]}-${idx}`}
                style={{
                  padding: '3px 8px',
                  borderRadius: 999,
                  border: '1px solid rgba(59,130,246,0.35)',
                  background: 'rgba(59,130,246,0.14)',
                  color: '#93c5fd',
                  fontSize: 11,
                  fontFamily: 'monospace',
                }}
              >
                {token[1]}
              </span>
            ))}
            {value.match(/\{([A-Za-z0-9_-]+)\}/g) ? null : (
              <span style={{ color: 'var(--text-muted)', fontSize: 12 }}>No line-item tokens yet.</span>
            )}
          </div>

          <div style={{ marginTop: 12, fontSize: 12, color: 'var(--text-secondary)' }}>Live Preview</div>
          <div
            style={{
              marginTop: 6,
              padding: '10px 12px',
              border: '1px solid var(--border-subtle)',
              borderRadius: 8,
              background: 'var(--bg-input)',
              minHeight: 44,
            }}
          >
            {livePreview || <span style={{ color: 'var(--text-muted)' }}>Formula preview appears here.</span>}
          </div>

          {syntaxError ? (
            <div style={{ marginTop: 10, color: 'var(--danger)', fontSize: 12 }}>{syntaxError}</div>
          ) : (
            <div style={{ marginTop: 10, color: 'var(--success)', fontSize: 12 }}>Syntax looks valid.</div>
          )}
        </section>

        <section className="card" style={{ padding: 10 }}>
          <div style={{ fontSize: 11, textTransform: 'uppercase', color: 'var(--text-muted)', marginBottom: 8 }}>Ops</div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            {OPERATORS.map((operator) => (
              <button
                key={operator.label}
                type="button"
                className="btn btn-ghost btn-sm"
                style={{
                  justifyContent: 'center',
                  fontWeight: 700,
                  border: '1px solid var(--border-subtle)',
                }}
                onClick={() => insertAtCursor(operator.value)}
              >
                {operator.label}
              </button>
            ))}
            <button
              type="button"
              className="btn btn-ghost btn-sm"
              style={{ justifyContent: 'center', color: 'var(--danger)', border: '1px solid var(--border-subtle)' }}
              onClick={() => onChange('')}
            >
              Clear
            </button>
          </div>
        </section>
      </div>
    </div>
  )
}
