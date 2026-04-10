'use client'

import { CheckCircle, AlertTriangle, XCircle } from 'lucide-react'

interface ValidationItem {
  name: string
  status: string
  difference: number
  severity?: string | null
}

interface ValidationPanelProps {
  validations: ValidationItem[]
  isLoading?: boolean
}

export function ValidationPanel({ validations, isLoading }: ValidationPanelProps) {
  if (isLoading) {
    return (
      <div style={{ padding: 16, color: 'var(--text-muted)', fontSize: 13 }}>
        Running validation checks...
      </div>
    )
  }

  if (!validations.length) {
    return (
      <div style={{ padding: 16, color: 'var(--text-muted)', fontSize: 13 }}>
        No validation results. Run Process to generate balance checks.
      </div>
    )
  }

  const passed = validations.filter((v) => v.status === 'PASS')
  const warnings = validations.filter((v) => v.status === 'WARNING')
  const failed = validations.filter((v) => v.status === 'FAIL')

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
      <div style={{ display: 'flex', gap: 12, padding: '8px 0', fontSize: 12 }}>
        <span style={{ color: '#34c759' }}>{passed.length} Passed</span>
        <span style={{ color: '#ff9f0a' }}>{warnings.length} Warnings</span>
        <span style={{ color: '#ff453a' }}>{failed.length} Failed</span>
      </div>

      {validations.map((v, i) => {
        const Icon = v.status === 'PASS' ? CheckCircle : v.status === 'WARNING' ? AlertTriangle : XCircle
        const color = v.status === 'PASS' ? '#34c759' : v.status === 'WARNING' ? '#ff9f0a' : '#ff453a'

        return (
          <div
            key={i}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              padding: '8px 10px',
              borderRadius: 6,
              border: '1px solid var(--border-subtle)',
              fontSize: 13,
            }}
          >
            <Icon size={16} style={{ color, flexShrink: 0 }} />
            <span style={{ flex: 1 }}>{v.name}</span>
            {v.difference !== 0 && (
              <span style={{ color: 'var(--text-muted)', fontSize: 12 }}>
                Diff: {v.difference.toLocaleString()}
              </span>
            )}
          </div>
        )
      })}
    </div>
  )
}
