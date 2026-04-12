'use client'

import { useState } from 'react'
import { BarChart3, RefreshCw, AlertTriangle, Search } from 'lucide-react'
import { usePortfolioRatios, useRatioTrends, usePortfolioAlerts, useMaterializeSnapshots, RATIO_CODES } from '@/services/portfolioApi'
import { RatioComparisonTable } from '@/components/portfolio/RatioComparisonTable'
import { RatioScatterPlot } from '@/components/portfolio/RatioScatterPlot'
import { TrendChart } from '@/components/portfolio/TrendChart'
import { DashboardExport } from '@/components/dashboard/DashboardExport'
import { LoadingSkeleton } from '@/components/ui/LoadingSkeleton'

export default function PortfolioPage() {
  const [selectedClientId, setSelectedClientId] = useState<string | null>(null)
  const [selectedRatio, setSelectedRatio] = useState<string>('CURRENT_RATIO')
  const [nlQuery, setNlQuery] = useState('')
  const [activeTab, setActiveTab] = useState<'table' | 'scatter' | 'trends'>('table')

  const ratiosQuery = usePortfolioRatios()
  const alertsQuery = usePortfolioAlerts()
  const materialize = useMaterializeSnapshots()

  const selectedClient = ratiosQuery.data?.find((r) => r.customerId === selectedClientId)
  const trendQuery = useRatioTrends(selectedClientId, selectedRatio)

  const ratioLabel = RATIO_CODES.find((r) => r.code === selectedRatio)?.label ?? selectedRatio
  const criticalAlerts = alertsQuery.data?.filter((a) => a.severity === 'CRITICAL') ?? []
  const warningAlerts = alertsQuery.data?.filter((a) => a.severity === 'WARNING') ?? []

  return (
    <div>
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: 12 }}>
        <div>
          <h1 style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <BarChart3 size={22} /> Portfolio Analytics
          </h1>
          <p>Cross-client ratio comparison, trends, and alerts</p>
        </div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <button
            className="btn btn-secondary"
            onClick={() => materialize.mutate()}
            disabled={materialize.isPending}
            style={{ display: 'flex', alignItems: 'center', gap: 6 }}
          >
            <RefreshCw size={14} className={materialize.isPending ? 'animate-spin' : ''} />
            {materialize.isPending ? 'Computing...' : 'Refresh Ratios'}
          </button>
          <DashboardExport />
        </div>
      </div>

      {/* Alert summary badges */}
      {(criticalAlerts.length > 0 || warningAlerts.length > 0) && (
        <div style={{ display: 'flex', gap: 12, marginBottom: 16, flexWrap: 'wrap' }}>
          {criticalAlerts.length > 0 && (
            <div style={{
              background: 'rgba(239,68,68,0.1)', border: '1px solid rgba(239,68,68,0.3)',
              borderRadius: 8, padding: '8px 14px', fontSize: 13, display: 'flex', alignItems: 'center', gap: 6,
            }}>
              <AlertTriangle size={14} style={{ color: '#ef4444' }} />
              <strong>{criticalAlerts.length}</strong> critical ratio change{criticalAlerts.length !== 1 ? 's' : ''}
            </div>
          )}
          {warningAlerts.length > 0 && (
            <div style={{
              background: 'rgba(245,158,11,0.1)', border: '1px solid rgba(245,158,11,0.3)',
              borderRadius: 8, padding: '8px 14px', fontSize: 13, display: 'flex', alignItems: 'center', gap: 6,
            }}>
              <AlertTriangle size={14} style={{ color: '#f59e0b' }} />
              <strong>{warningAlerts.length}</strong> warning{warningAlerts.length !== 1 ? 's' : ''}
            </div>
          )}
        </div>
      )}

      {/* NL query bar */}
      <div className="card" style={{ marginBottom: 16, padding: '12px 16px' }}>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <Search size={16} style={{ color: 'var(--text-muted)', flexShrink: 0 }} />
          <input
            className="input"
            value={nlQuery}
            onChange={(e) => setNlQuery(e.target.value)}
            placeholder="Ask a question about your portfolio, e.g. 'Which clients have D/E above 2?'"
            style={{ flex: 1, border: 'none', outline: 'none', background: 'transparent' }}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && nlQuery.trim()) {
                // NL query integration — passes to copilot when available
                window.dispatchEvent(new CustomEvent('copilot:query', { detail: { query: nlQuery } }))
              }
            }}
          />
        </div>
      </div>

      {/* Tab navigation */}
      <div style={{ display: 'flex', gap: 4, marginBottom: 16, borderBottom: '1px solid var(--border)' }}>
        {(['table', 'scatter', 'trends'] as const).map((tab) => (
          <button
            key={tab}
            onClick={() => setActiveTab(tab)}
            style={{
              padding: '8px 16px', fontSize: 13, fontWeight: 500, border: 'none', background: 'none',
              borderBottom: activeTab === tab ? '2px solid var(--accent, #3b82f6)' : '2px solid transparent',
              color: activeTab === tab ? 'var(--accent, #3b82f6)' : 'var(--text-muted)',
              cursor: 'pointer',
            }}
          >
            {tab === 'table' ? 'Ratio Table' : tab === 'scatter' ? 'Scatter Plot' : 'Trend Charts'}
          </button>
        ))}
      </div>

      {ratiosQuery.isLoading && (
        <div className="card" style={{ padding: 24 }}>
          <LoadingSkeleton height={300} />
        </div>
      )}

      {ratiosQuery.isError && (
        <div className="card" style={{ padding: 24, color: 'var(--text-muted)' }}>
          Failed to load portfolio data. Please try refreshing.
        </div>
      )}

      {ratiosQuery.data && (
        <>
          {activeTab === 'table' && (
            <div className="card" style={{ padding: 16 }}>
              <div className="card-header">
                <div>
                  <div className="card-title">Client Ratios</div>
                  <div className="card-subtitle">{ratiosQuery.data.length} client period{ratiosQuery.data.length !== 1 ? 's' : ''}</div>
                </div>
              </div>
              <RatioComparisonTable
                data={ratiosQuery.data}
                onSelectClient={setSelectedClientId}
                selectedClientId={selectedClientId}
              />
            </div>
          )}

          {activeTab === 'scatter' && (
            <div className="card" style={{ padding: 16 }}>
              <div className="card-header">
                <div className="card-title">Ratio Scatter Plot</div>
              </div>
              <RatioScatterPlot data={ratiosQuery.data} />
            </div>
          )}

          {activeTab === 'trends' && (
            <div className="card" style={{ padding: 16 }}>
              <div className="card-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
                <div className="card-title">Ratio Trends</div>
                <div style={{ display: 'flex', gap: 12 }}>
                  <select
                    className="input"
                    value={selectedClientId ?? ''}
                    onChange={(e) => setSelectedClientId(e.target.value || null)}
                    style={{ width: 'auto', minWidth: 180 }}
                  >
                    <option value="">Select client...</option>
                    {[...new Map(ratiosQuery.data.map((r) => [r.customerId, r.customerName]))].map(([id, name]) => (
                      <option key={id} value={id}>{name}</option>
                    ))}
                  </select>
                  <select
                    className="input"
                    value={selectedRatio}
                    onChange={(e) => setSelectedRatio(e.target.value)}
                    style={{ width: 'auto', minWidth: 160 }}
                  >
                    {RATIO_CODES.map((r) => (
                      <option key={r.code} value={r.code}>{r.label}</option>
                    ))}
                  </select>
                </div>
              </div>
              {selectedClientId ? (
                trendQuery.isLoading ? (
                  <LoadingSkeleton height={280} />
                ) : trendQuery.data ? (
                  <TrendChart
                    data={trendQuery.data}
                    ratioLabel={ratioLabel}
                    customerName={selectedClient?.customerName ?? ''}
                  />
                ) : (
                  <div style={{ padding: 32, textAlign: 'center', color: 'var(--text-muted)', fontSize: 13 }}>
                    No trend data available.
                  </div>
                )
              ) : (
                <div style={{ padding: 32, textAlign: 'center', color: 'var(--text-muted)', fontSize: 13 }}>
                  Select a client above or click a row in the Ratio Table.
                </div>
              )}
            </div>
          )}
        </>
      )}
    </div>
  )
}
