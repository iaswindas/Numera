'use client'

import { useState } from 'react'
import { ChevronDown, Copy } from 'lucide-react'

export interface TemplateVariable {
  key: string
  label: string
  category: 'customer' | 'covenant' | 'period' | 'misc'
  sampleValue: string
}

const TEMPLATE_VARIABLES: TemplateVariable[] = [
  { key: '{{CUSTOMER_NAME}}', label: 'Customer Name', category: 'customer', sampleValue: 'Acme Corporation' },
  { key: '{{CUSTOMER_ID}}', label: 'Customer ID', category: 'customer', sampleValue: 'CUST-001' },
  { key: '{{CUSTOMER_ADDRESS}}', label: 'Customer Address', category: 'customer', sampleValue: '123 Main St, New York, NY 10001' },
  { key: '{{CONTACT_NAME}}', label: 'Contact Name', category: 'customer', sampleValue: 'John Smith' },
  { key: '{{CONTACT_EMAIL}}', label: 'Contact Email', category: 'customer', sampleValue: 'john.smith@acme.com' },
  { key: '{{COVENANT_NAME}}', label: 'Covenant Name', category: 'covenant', sampleValue: 'Debt-to-Equity Ratio' },
  { key: '{{COVENANT_TYPE}}', label: 'Covenant Type', category: 'covenant', sampleValue: 'Financial' },
  { key: '{{COVENANT_THRESHOLD}}', label: 'Covenant Threshold', category: 'covenant', sampleValue: '2.5x' },
  { key: '{{COVENANT_ACTUAL}}', label: 'Actual Value', category: 'covenant', sampleValue: '3.1x' },
  { key: '{{COVENANT_STATUS}}', label: 'Covenant Status', category: 'covenant', sampleValue: 'Breached' },
  { key: '{{PERIOD}}', label: 'Period', category: 'period', sampleValue: 'Q1 2026' },
  { key: '{{PERIOD_START}}', label: 'Period Start', category: 'period', sampleValue: '2026-01-01' },
  { key: '{{PERIOD_END}}', label: 'Period End', category: 'period', sampleValue: '2026-03-31' },
  { key: '{{DUE_DATE}}', label: 'Due Date', category: 'period', sampleValue: '2026-04-30' },
  { key: '{{WAIVER_TYPE}}', label: 'Waiver Type', category: 'misc', sampleValue: 'Instance' },
  { key: '{{BANK_NAME}}', label: 'Bank Name', category: 'misc', sampleValue: 'First National Bank' },
  { key: '{{SIGNATORY_NAME}}', label: 'Signatory Name', category: 'misc', sampleValue: 'Jane Doe' },
  { key: '{{SIGNATORY_TITLE}}', label: 'Signatory Title', category: 'misc', sampleValue: 'VP Credit Risk' },
  { key: '{{TODAY_DATE}}', label: 'Today Date', category: 'misc', sampleValue: new Date().toLocaleDateString() },
]

const CATEGORY_LABELS: Record<string, string> = {
  customer: 'Customer',
  covenant: 'Covenant',
  period: 'Period',
  misc: 'Miscellaneous',
}

type VariableInserterProps = {
  onInsert: (variable: string) => void
  filterCategory?: 'Financial' | 'Non-Financial' | null
}

export default function VariableInserter({ onInsert, filterCategory }: VariableInserterProps) {
  const [open, setOpen] = useState(false)
  const [search, setSearch] = useState('')

  const filtered = TEMPLATE_VARIABLES.filter((v) => {
    if (search && !v.label.toLowerCase().includes(search.toLowerCase()) && !v.key.toLowerCase().includes(search.toLowerCase())) {
      return false
    }
    if (filterCategory === 'Non-Financial') {
      return v.category !== 'covenant' || !['{{COVENANT_THRESHOLD}}', '{{COVENANT_ACTUAL}}'].includes(v.key)
    }
    return true
  })

  const grouped = Object.entries(
    filtered.reduce<Record<string, TemplateVariable[]>>((acc, v) => {
      ;(acc[v.category] ??= []).push(v)
      return acc
    }, {})
  )

  return (
    <div style={{ position: 'relative' }}>
      <button
        type="button"
        className="btn btn-secondary btn-sm"
        onClick={() => setOpen(!open)}
        style={{ display: 'flex', alignItems: 'center', gap: 4 }}
      >
        Insert Variable <ChevronDown size={14} />
      </button>

      {open && (
        <div
          className="card"
          style={{
            position: 'absolute',
            top: '100%',
            right: 0,
            zIndex: 50,
            width: 320,
            maxHeight: 400,
            overflow: 'auto',
            marginTop: 4,
            padding: 8,
            boxShadow: '0 8px 24px rgba(0,0,0,.15)',
          }}
        >
          <input
            className="input"
            placeholder="Search variables..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            style={{ marginBottom: 8, fontSize: 12 }}
            autoFocus
          />

          {grouped.map(([category, vars]) => (
            <div key={category} style={{ marginBottom: 8 }}>
              <div style={{ fontSize: 11, fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', marginBottom: 4, paddingLeft: 4 }}>
                {CATEGORY_LABELS[category] ?? category}
              </div>
              {vars.map((v) => (
                <button
                  key={v.key}
                  type="button"
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    width: '100%',
                    padding: '5px 8px',
                    border: 'none',
                    background: 'none',
                    cursor: 'pointer',
                    borderRadius: 4,
                    fontSize: 12,
                    textAlign: 'left',
                  }}
                  className="hover-bg"
                  onClick={() => {
                    onInsert(v.key)
                    setOpen(false)
                    setSearch('')
                  }}
                  title={`Sample: ${v.sampleValue}`}
                >
                  <span>
                    <span style={{ fontWeight: 500 }}>{v.label}</span>
                    <span style={{ marginLeft: 6, color: 'var(--text-muted)', fontFamily: 'monospace', fontSize: 11 }}>{v.key}</span>
                  </span>
                  <Copy size={12} style={{ color: 'var(--text-muted)' }} />
                </button>
              ))}
            </div>
          ))}

          {grouped.length === 0 && (
            <div style={{ color: 'var(--text-muted)', fontSize: 12, padding: 8 }}>No variables match your search.</div>
          )}
        </div>
      )}
    </div>
  )
}

export { TEMPLATE_VARIABLES }
