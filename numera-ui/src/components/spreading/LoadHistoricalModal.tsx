'use client'

import { useCallback, useMemo, useState } from 'react'
import { Calendar, Download, X } from 'lucide-react'
import { useHistoricalSpreads, useLoadHistoricalValues } from '@/services/spreadApi'
import type { HistoricalSpread } from '@/types/spread'

interface LoadHistoricalModalProps {
  open: boolean
  spreadId: string
  customerId: string
  onClose: () => void
  onLoaded?: (count: number) => void
}

function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}

export function LoadHistoricalModal({ open, spreadId, customerId, onClose, onLoaded }: LoadHistoricalModalProps) {
  const [dateFrom, setDateFrom] = useState('')
  const [dateTo, setDateTo] = useState('')
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())

  const historicalQuery = useHistoricalSpreads(customerId, dateFrom || undefined, dateTo || undefined)
  const loadMutation = useLoadHistoricalValues(spreadId)

  const spreads = useMemo(() => {
    const data = historicalQuery.data ?? []
    return data.filter((s) => s.id !== spreadId)
  }, [historicalQuery.data, spreadId])

  const toggleSelection = useCallback((id: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) {
        next.delete(id)
      } else {
        next.add(id)
      }
      return next
    })
  }, [])

  const handleLoad = useCallback(async () => {
    if (selectedIds.size === 0) return
    const result = await loadMutation.mutateAsync(Array.from(selectedIds))
    onLoaded?.(result.loadedColumns)
    onClose()
  }, [selectedIds, loadMutation, onLoaded, onClose])

  if (!open) return null

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
          width: 'min(680px, 96vw)',
          maxHeight: '85vh',
          overflow: 'hidden',
          background: 'var(--bg-secondary)',
          border: '1px solid var(--border-subtle)',
          borderRadius: 12,
          display: 'flex',
          flexDirection: 'column',
        }}
      >
        {/* Header */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '14px 16px',
            borderBottom: '1px solid var(--border-subtle)',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Calendar size={16} />
            <span style={{ fontWeight: 700, fontSize: 15 }}>Load Historical Spreads</span>
          </div>
          <button className="btn btn-ghost btn-sm" onClick={onClose}>
            <X size={14} />
          </button>
        </div>

        {/* Date range selector */}
        <div
          style={{
            display: 'flex',
            gap: 12,
            padding: '12px 16px',
            borderBottom: '1px solid var(--border-subtle)',
            alignItems: 'center',
            fontSize: 13,
          }}
        >
          <label style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            From:
            <input
              type="date"
              className="input"
              value={dateFrom}
              onChange={(e) => setDateFrom(e.target.value)}
              style={{ width: 160, fontSize: 13 }}
            />
          </label>
          <label style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            To:
            <input
              type="date"
              className="input"
              value={dateTo}
              onChange={(e) => setDateTo(e.target.value)}
              style={{ width: 160, fontSize: 13 }}
            />
          </label>
          <span style={{ color: 'var(--text-muted)', fontSize: 12 }}>
            {spreads.length} spread{spreads.length !== 1 ? 's' : ''} found
          </span>
        </div>

        {/* Spreads list */}
        <div style={{ flex: 1, overflow: 'auto', padding: '8px 16px' }}>
          {historicalQuery.isLoading ? (
            <div style={{ padding: 16, color: 'var(--text-muted)', fontSize: 13 }}>Loading spreads...</div>
          ) : spreads.length === 0 ? (
            <div style={{ padding: 16, color: 'var(--text-muted)', fontSize: 13 }}>
              No historical spreads found for this customer.
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              {spreads.map((s: HistoricalSpread) => (
                <label
                  key={s.id}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 10,
                    padding: '8px 10px',
                    borderRadius: 6,
                    border: '1px solid',
                    borderColor: selectedIds.has(s.id) ? '#3b82f6' : 'var(--border-subtle)',
                    background: selectedIds.has(s.id) ? 'rgba(59, 130, 246, 0.08)' : 'transparent',
                    cursor: 'pointer',
                    fontSize: 13,
                    transition: 'border-color 150ms',
                  }}
                >
                  <input
                    type="checkbox"
                    checked={selectedIds.has(s.id)}
                    onChange={() => toggleSelection(s.id)}
                    style={{ cursor: 'pointer' }}
                  />
                  <span style={{ fontWeight: 600, minWidth: 100 }}>{formatDate(s.statementDate)}</span>
                  <span
                    className="badge"
                    style={{
                      fontSize: 10,
                      background: s.status === 'APPROVED' ? 'rgba(52, 199, 89, 0.15)' : 'var(--bg-elevated)',
                      color: s.status === 'APPROVED' ? '#34c759' : 'var(--text-secondary)',
                    }}
                  >
                    {s.status}
                  </span>
                  <span style={{ color: 'var(--text-muted)', fontSize: 12 }}>v{s.currentVersion}</span>
                  {s.templateName && (
                    <span style={{ color: 'var(--text-muted)', fontSize: 12, marginLeft: 'auto' }}>
                      {s.templateName}
                    </span>
                  )}
                </label>
              ))}
            </div>
          )}
        </div>

        {/* Footer */}
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            padding: '12px 16px',
            borderTop: '1px solid var(--border-subtle)',
          }}
        >
          <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
            {selectedIds.size} selected — values will be added as additional columns
          </span>
          <div style={{ display: 'flex', gap: 8 }}>
            <button className="btn btn-ghost btn-sm" onClick={onClose}>Cancel</button>
            <button
              className="btn btn-primary btn-sm"
              onClick={() => void handleLoad()}
              disabled={selectedIds.size === 0 || loadMutation.isPending}
            >
              <Download size={13} />
              {loadMutation.isPending ? 'Loading...' : `Load ${selectedIds.size} Spread${selectedIds.size !== 1 ? 's' : ''}`}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
