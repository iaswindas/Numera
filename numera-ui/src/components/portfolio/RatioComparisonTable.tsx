'use client'

import { useMemo, useState } from 'react'
import { ArrowDown, ArrowUp, AlertTriangle } from 'lucide-react'
import type { PortfolioRatioRow, RatioAlert } from '@/services/portfolioApi'
import { RATIO_CODES } from '@/services/portfolioApi'

interface Props {
  data: PortfolioRatioRow[]
  onSelectClient?: (customerId: string) => void
  selectedClientId?: string | null
}

export function RatioComparisonTable({ data, onSelectClient, selectedClientId }: Props) {
  const [sortCol, setSortCol] = useState<string>('customerName')
  const [sortAsc, setSortAsc] = useState(true)

  const visibleRatios = RATIO_CODES.slice(0, 8) // most important ratios

  const sorted = useMemo(() => {
    const copy = [...data]
    copy.sort((a, b) => {
      if (sortCol === 'customerName') {
        return sortAsc
          ? a.customerName.localeCompare(b.customerName)
          : b.customerName.localeCompare(a.customerName)
      }
      if (sortCol === 'statementDate') {
        return sortAsc
          ? a.statementDate.localeCompare(b.statementDate)
          : b.statementDate.localeCompare(a.statementDate)
      }
      const aVal = a.ratios[sortCol] ?? -Infinity
      const bVal = b.ratios[sortCol] ?? -Infinity
      return sortAsc ? aVal - bVal : bVal - aVal
    })
    return copy
  }, [data, sortCol, sortAsc])

  function handleSort(col: string) {
    if (sortCol === col) setSortAsc(!sortAsc)
    else {
      setSortCol(col)
      setSortAsc(true)
    }
  }

  function alertBadge(alerts: RatioAlert[], code: string) {
    const alert = alerts.find((a) => a.ratioCode === code)
    if (!alert) return null
    const color =
      alert.severity === 'CRITICAL' ? '#ef4444' : alert.severity === 'WARNING' ? '#f59e0b' : '#06b6d4'
    return (
      <span
        title={`${alert.changePercent?.toFixed(1)}% change`}
        style={{ color, marginLeft: 4, verticalAlign: 'middle' }}
      >
        <AlertTriangle size={12} style={{ display: 'inline' }} />
      </span>
    )
  }

  return (
    <div style={{ overflowX: 'auto' }}>
      <table className="data-table" style={{ width: '100%', fontSize: 13 }}>
        <thead>
          <tr>
            <th onClick={() => handleSort('customerName')} style={{ cursor: 'pointer', whiteSpace: 'nowrap' }}>
              Client {sortCol === 'customerName' ? (sortAsc ? <ArrowUp size={12} /> : <ArrowDown size={12} />) : null}
            </th>
            <th onClick={() => handleSort('statementDate')} style={{ cursor: 'pointer', whiteSpace: 'nowrap' }}>
              Period {sortCol === 'statementDate' ? (sortAsc ? <ArrowUp size={12} /> : <ArrowDown size={12} />) : null}
            </th>
            {visibleRatios.map((r) => (
              <th key={r.code} onClick={() => handleSort(r.code)} style={{ cursor: 'pointer', whiteSpace: 'nowrap', textAlign: 'right' }}>
                {r.label} {sortCol === r.code ? (sortAsc ? <ArrowUp size={12} /> : <ArrowDown size={12} />) : null}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {sorted.length === 0 && (
            <tr>
              <td colSpan={2 + visibleRatios.length} style={{ textAlign: 'center', padding: 24, color: 'var(--text-muted)' }}>
                No ratio data available. Materialize snapshots first.
              </td>
            </tr>
          )}
          {sorted.map((row) => (
            <tr
              key={`${row.customerId}-${row.statementDate}`}
              onClick={() => onSelectClient?.(row.customerId)}
              style={{
                cursor: onSelectClient ? 'pointer' : undefined,
                background: selectedClientId === row.customerId ? 'var(--bg-selected, rgba(59,130,246,0.08))' : undefined,
              }}
            >
              <td style={{ fontWeight: 500 }}>{row.customerName}</td>
              <td>{row.statementDate}</td>
              {visibleRatios.map((r) => {
                const val = row.ratios[r.code]
                return (
                  <td key={r.code} style={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}>
                    {val != null ? val.toFixed(2) : '—'}
                    {alertBadge(row.alerts, r.code)}
                  </td>
                )
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
