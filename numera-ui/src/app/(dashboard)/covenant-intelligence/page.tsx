'use client'

import { useMemo, useState } from 'react'
import { useRouter } from 'next/navigation'
import { Pie, PieChart, Cell, ResponsiveContainer, Tooltip, Legend } from 'recharts'
import BreachHeatmap from '@/components/covenant/BreachHeatmap'
import CovenantCalendar from '@/components/covenant/CovenantCalendar'
import TrendChart from '@/components/covenant/TrendChart'
import {
  useBreachProbabilities,
  useCovenantStatusDistribution,
  useCovenantTrend,
  useUpcomingDueDates,
} from '@/services/covenantApi'

type TabValue = 'overview' | 'heatmap' | 'trends' | 'calendar'

const COLORS = ['#10b981', '#60a5fa', '#f59e0b', '#f87171', '#a78bfa', '#06b6d4']

export default function CovenantIntelligencePage() {
  const router = useRouter()
  const [activeTab, setActiveTab] = useState<TabValue>('overview')

  const statusDistributionQuery = useCovenantStatusDistribution()
  const breachProbabilitiesQuery = useBreachProbabilities()
  const upcomingQuery = useUpcomingDueDates(90)

  const breachData = breachProbabilitiesQuery.data ?? []
  const statusData = statusDistributionQuery.data ?? []
  const dueDates = upcomingQuery.data ?? []

  const covenants = useMemo(() => {
    const map = new Map<string, string>()
    for (const row of breachData) {
      map.set(row.covenantId, row.covenantName)
    }
    return Array.from(map.entries()).map(([id, name]) => ({ id, name }))
  }, [breachData])

  const [selectedCovenantId, setSelectedCovenantId] = useState('')

  const selectedCovenant = useMemo(
    () => covenants.find((item) => item.id === selectedCovenantId),
    [covenants, selectedCovenantId]
  )

  const trendQuery = useCovenantTrend(selectedCovenantId)

  const totalItems = useMemo(() => statusData.reduce((sum, item) => sum + item.count, 0), [statusData])
  const highRiskCount = useMemo(() => breachData.filter((item) => item.probability >= 0.8).length, [breachData])
  const avgRisk = useMemo(() => {
    if (breachData.length === 0) return 0
    const total = breachData.reduce((sum, item) => sum + item.probability, 0)
    return total / breachData.length
  }, [breachData])

  const onOpenMonitoringItem = (monitoringItemId: string) => {
    router.push(`/covenants?itemId=${monitoringItemId}`)
  }

  return (
    <>
      <div className="page-header">
        <h1>Covenant Intelligence</h1>
        <p>Portfolio-wide risk analytics with predictive breach insights</p>
      </div>

      <div className="tabs" style={{ marginBottom: 16 }}>
        <button className={`tab ${activeTab === 'overview' ? 'active' : ''}`} onClick={() => setActiveTab('overview')}>Overview</button>
        <button className={`tab ${activeTab === 'heatmap' ? 'active' : ''}`} onClick={() => setActiveTab('heatmap')}>Heatmap</button>
        <button className={`tab ${activeTab === 'trends' ? 'active' : ''}`} onClick={() => setActiveTab('trends')}>Trends</button>
        <button className={`tab ${activeTab === 'calendar' ? 'active' : ''}`} onClick={() => setActiveTab('calendar')}>Calendar</button>
      </div>

      {activeTab === 'overview' ? (
        <>
          <div className="stat-grid" style={{ gridTemplateColumns: 'repeat(4, minmax(160px, 1fr))' }}>
            <div className="stat-card accent">
              <div className="stat-label">Total Monitoring Items</div>
              <div className="stat-value">{totalItems}</div>
            </div>
            <div className="stat-card warning">
              <div className="stat-label">Average Breach Risk</div>
              <div className="stat-value">{Math.round(avgRisk * 100)}%</div>
            </div>
            <div className="stat-card danger">
              <div className="stat-label">High Risk Items</div>
              <div className="stat-value">{highRiskCount}</div>
            </div>
            <div className="stat-card purple">
              <div className="stat-label">Due in 90 Days</div>
              <div className="stat-value">{dueDates.length}</div>
            </div>
          </div>

          <div className="card">
            <div className="card-header">
              <div>
                <div className="card-title">Status Distribution</div>
                <div className="card-subtitle">Current covenant status mix across monitoring items</div>
              </div>
            </div>

            {statusDistributionQuery.isLoading ? <div style={{ color: 'var(--text-muted)' }}>Loading distribution...</div> : null}
            {statusDistributionQuery.isError ? <div style={{ color: 'var(--danger)' }}>Failed to load distribution analytics.</div> : null}
            {!statusDistributionQuery.isLoading && statusData.length === 0 ? <div style={{ color: 'var(--text-muted)' }}>No status distribution available.</div> : null}

            {statusData.length > 0 ? (
              <div style={{ width: '100%', height: 320 }}>
                <ResponsiveContainer>
                  <PieChart>
                    <Pie data={statusData} dataKey="count" nameKey="status" innerRadius={80} outerRadius={120}>
                      {statusData.map((entry, index) => (
                        <Cell key={entry.status} fill={COLORS[index % COLORS.length]} />
                      ))}
                    </Pie>
                    <Tooltip
                      contentStyle={{
                        background: 'var(--bg-secondary)',
                        border: '1px solid var(--border-subtle)',
                        borderRadius: 8,
                      }}
                    />
                    <Legend />
                  </PieChart>
                </ResponsiveContainer>
              </div>
            ) : null}
          </div>
        </>
      ) : null}

      {activeTab === 'heatmap' ? (
        <div className="card">
          <div className="card-header">
            <div>
              <div className="card-title">Early Warning Heatmap</div>
              <div className="card-subtitle">Risk probability by customer and covenant type</div>
            </div>
          </div>

          {breachProbabilitiesQuery.isLoading ? <div style={{ color: 'var(--text-muted)' }}>Loading breach probabilities...</div> : null}
          {breachProbabilitiesQuery.isError ? <div style={{ color: 'var(--danger)' }}>Failed to load breach probabilities.</div> : null}
          {!breachProbabilitiesQuery.isLoading && !breachProbabilitiesQuery.isError ? (
            <BreachHeatmap data={breachData} onSelect={onOpenMonitoringItem} />
          ) : null}
        </div>
      ) : null}

      {activeTab === 'trends' ? (
        <div className="card">
          <div className="card-header">
            <div>
              <div className="card-title">Covenant Trend Analysis</div>
              <div className="card-subtitle">Track covenant trajectory against configured limits</div>
            </div>
          </div>

          <div className="input-group" style={{ maxWidth: 420, marginBottom: 16 }}>
            <label>Select Covenant</label>
            <select
              className="input"
              value={selectedCovenantId}
              onChange={(event) => setSelectedCovenantId(event.target.value)}
            >
              <option value="">Select covenant</option>
              {covenants.map((covenant) => (
                <option key={covenant.id} value={covenant.id}>{covenant.name}</option>
              ))}
            </select>
          </div>

          {!selectedCovenantId ? <div style={{ color: 'var(--text-muted)' }}>Choose a covenant to view trend chart.</div> : null}
          {selectedCovenantId && trendQuery.isLoading ? <div style={{ color: 'var(--text-muted)' }}>Loading trend...</div> : null}
          {selectedCovenantId && trendQuery.isError ? <div style={{ color: 'var(--danger)' }}>Failed to load trend data.</div> : null}
          {selectedCovenantId && !trendQuery.isLoading && !trendQuery.isError ? (
            <>
              <div style={{ marginBottom: 10, fontSize: 13, color: 'var(--text-secondary)' }}>
                Showing trend for <span style={{ color: 'var(--text-primary)', fontWeight: 600 }}>{selectedCovenant?.name}</span>
              </div>
              <TrendChart data={trendQuery.data ?? []} upperLimit={1.0} lowerLimit={0.3} />
            </>
          ) : null}
        </div>
      ) : null}

      {activeTab === 'calendar' ? (
        <div className="card">
          <div className="card-header">
            <div>
              <div className="card-title">Upcoming Covenant Calendar</div>
              <div className="card-subtitle">Due-date timeline for the next 90 days</div>
            </div>
          </div>

          {upcomingQuery.isLoading ? <div style={{ color: 'var(--text-muted)' }}>Loading calendar...</div> : null}
          {upcomingQuery.isError ? <div style={{ color: 'var(--danger)' }}>Failed to load due dates.</div> : null}
          {!upcomingQuery.isLoading && !upcomingQuery.isError ? (
            <CovenantCalendar items={dueDates} onSelect={onOpenMonitoringItem} />
          ) : null}
        </div>
      ) : null}
    </>
  )
}
