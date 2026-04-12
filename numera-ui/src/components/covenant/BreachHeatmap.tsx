'use client'

import * as Tooltip from '@radix-ui/react-tooltip'
import type { BreachProbabilityCell } from '@/services/covenantApi'

type BreachHeatmapProps = {
  data: BreachProbabilityCell[]
  onSelect: (monitoringItemId: string) => void
}

function getRiskStyle(probability: number): { background: string; color: string } {
  if (probability < 0.3) return { background: 'rgba(16,185,129,0.22)', color: '#34d399' }
  if (probability < 0.6) return { background: 'rgba(245,158,11,0.24)', color: '#fbbf24' }
  if (probability < 0.8) return { background: 'rgba(249,115,22,0.24)', color: '#fb923c' }
  return { background: 'rgba(239,68,68,0.28)', color: '#f87171' }
}

export default function BreachHeatmap({ data, onSelect }: BreachHeatmapProps) {
  const customers = Array.from(new Set(data.map((d) => d.customerName)))
  const covenants = Array.from(new Set(data.map((d) => d.covenantName)))

  if (data.length === 0) {
    return <div style={{ color: 'var(--text-muted)', fontSize: 13 }}>No breach probability analytics available.</div>
  }

  return (
    <Tooltip.Provider delayDuration={120}>
      <div style={{ overflowX: 'auto' }}>
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: `220px repeat(${covenants.length}, minmax(120px, 1fr))`,
            gap: 6,
            minWidth: 620,
          }}
        >
          <div className="heatmap-header" style={{ textAlign: 'left', paddingLeft: 8 }}>Customer</div>
          {covenants.map((covenant) => (
            <div key={covenant} className="heatmap-header">{covenant}</div>
          ))}

          {customers.map((customer) => (
            <div key={customer} style={{ display: 'contents' }}>
              <div key={`${customer}-label`} className="heatmap-label" style={{ fontWeight: 600 }}>{customer}</div>
              {covenants.map((covenant) => {
                const item = data.find((d) => d.customerName === customer && d.covenantName === covenant)
                if (!item) {
                  return (
                    <div
                      key={`${customer}-${covenant}`}
                      style={{
                        borderRadius: 6,
                        border: '1px dashed var(--border-subtle)',
                        minHeight: 44,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        color: 'var(--text-muted)',
                        fontSize: 11,
                      }}
                    >
                      -
                    </div>
                  )
                }

                const style = getRiskStyle(item.probability)

                return (
                  <Tooltip.Root key={`${customer}-${covenant}`}>
                    <Tooltip.Trigger asChild>
                      <button
                        type="button"
                        onClick={() => onSelect(item.monitoringItemId)}
                        style={{
                          borderRadius: 6,
                          border: '1px solid var(--border-subtle)',
                          minHeight: 44,
                          background: style.background,
                          color: style.color,
                          fontWeight: 700,
                          cursor: 'pointer',
                        }}
                      >
                        {Math.round(item.probability * 100)}%
                      </button>
                    </Tooltip.Trigger>
                    <Tooltip.Portal>
                      <Tooltip.Content
                        sideOffset={6}
                        style={{
                          background: 'var(--bg-secondary)',
                          border: '1px solid var(--border-subtle)',
                          color: 'var(--text-primary)',
                          padding: '8px 10px',
                          borderRadius: 8,
                          fontSize: 12,
                          zIndex: 120,
                        }}
                      >
                        <div style={{ fontWeight: 600 }}>{item.covenantName}</div>
                        <div style={{ color: 'var(--text-secondary)' }}>{item.customerName}</div>
                        <div style={{ marginTop: 4 }}>{Math.round(item.probability * 100)}% breach probability</div>
                      </Tooltip.Content>
                    </Tooltip.Portal>
                  </Tooltip.Root>
                )
              })}
            </div>
          ))}
        </div>
      </div>
    </Tooltip.Provider>
  )
}
