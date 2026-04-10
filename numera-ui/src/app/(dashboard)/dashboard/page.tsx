'use client'

import Link from 'next/link'
import { Area, AreaChart, Cell, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { Activity, ArrowRight, Clock, FileText, Target, TrendingUp, Zap } from 'lucide-react'
import { useDashboardStats } from '@/services/dashboardApi'
import { useMonitoringSummary } from '@/services/covenantApi'
import { LoadingSkeleton } from '@/components/ui/LoadingSkeleton'

export default function DashboardPage() {
  const statsQuery = useDashboardStats()
  const monitoringSummaryQuery = useMonitoringSummary()

  if (statsQuery.isLoading) {
    return (
      <div>
        <div className="page-header">
          <h1>Dashboard</h1>
          <p>Overview of your spreading and covenant activities</p>
        </div>
        <div className="stat-grid">
          <LoadingSkeleton height={120} />
          <LoadingSkeleton height={120} />
          <LoadingSkeleton height={120} />
          <LoadingSkeleton height={120} />
        </div>
      </div>
    )
  }

  if (statsQuery.isError) {
    return (
      <div className="card">
        <div className="card-title">Failed to load dashboard</div>
        <p style={{ marginTop: 8, color: 'var(--text-muted)' }}>Please ensure the backend is running and try again.</p>
      </div>
    )
  }

  const stats = statsQuery.data as {
    spreads: number
    aiAccuracy: number
    avgProcessingTimeMs: number
    covenantRiskCount: number
    customers: number
    recentSpreads: Array<{ id: string; customerName: string; statementDate: string; status: string; accuracy: number; processingTimeMs: number | null }>
    spreadTrend: Array<{ month: string; count: number }>
    covenantStatusDistribution: Record<string, number>
  }

  const pieDistribution = monitoringSummaryQuery.data
    ? Object.entries((monitoringSummaryQuery.data as { distribution: Record<string, number> }).distribution).map(([name, value], idx) => ({
        name,
        value,
        color: ['#10b981', '#f59e0b', '#ef4444', '#06b6d4', '#8b5cf6'][idx % 5],
      }))
    : []

  return (
    <>
      <div className="page-header">
        <h1>Dashboard</h1>
        <p>Overview of your spreading and covenant activities</p>
      </div>

      <div className="stat-grid">
        <div className="stat-card accent">
          <div className="stat-label"><FileText size={14} style={{ display: 'inline', verticalAlign: -2, marginRight: 4 }} />Total Spreads</div>
          <div className="stat-value">{stats.spreads}</div>
          <div className="stat-change up"><TrendingUp size={14} />Live portfolio count</div>
        </div>
        <div className="stat-card success">
          <div className="stat-label"><Zap size={14} style={{ display: 'inline', verticalAlign: -2, marginRight: 4 }} />AI Accuracy</div>
          <div className="stat-value">{Number(stats.aiAccuracy).toFixed(2)}%</div>
          <div className="stat-change up"><TrendingUp size={14} />Calculated from mapped values</div>
        </div>
        <div className="stat-card warning">
          <div className="stat-label"><Clock size={14} style={{ display: 'inline', verticalAlign: -2, marginRight: 4 }} />Avg Processing Time</div>
          <div className="stat-value">{Math.round(stats.avgProcessingTimeMs / 1000)}s</div>
          <div className="stat-change up"><TrendingUp size={14} />Document processing average</div>
        </div>
        <div className="stat-card danger">
          <div className="stat-label"><Target size={14} style={{ display: 'inline', verticalAlign: -2, marginRight: 4 }} />Covenants at Risk</div>
          <div className="stat-value">{stats.covenantRiskCount}</div>
          <div className="stat-change down"><TrendingUp size={14} />Breached monitoring items</div>
        </div>
        <div className="stat-card purple">
          <div className="stat-label"><Activity size={14} style={{ display: 'inline', verticalAlign: -2, marginRight: 4 }} />Active Customers</div>
          <div className="stat-value">{stats.customers}</div>
          <div className="stat-change up"><TrendingUp size={14} />Current customer records</div>
        </div>
      </div>

      <div className="grid-2">
        <div className="card">
          <div className="card-header">
            <div>
              <div className="card-title">Spreads Processed</div>
              <div className="card-subtitle">Monthly trend over last 7 months</div>
            </div>
          </div>
          <ResponsiveContainer width="100%" height={220}>
            <AreaChart data={stats.spreadTrend}>
              <XAxis dataKey="month" stroke="#64748b" fontSize={11} tickLine={false} axisLine={false} />
              <YAxis stroke="#64748b" fontSize={11} tickLine={false} axisLine={false} />
              <Tooltip />
              <Area type="monotone" dataKey="count" stroke="#3b82f6" fill="rgba(59,130,246,0.22)" strokeWidth={2} />
            </AreaChart>
          </ResponsiveContainer>
        </div>

        <div className="card">
          <div className="card-header">
            <div>
              <div className="card-title">Covenant Status Distribution</div>
              <div className="card-subtitle">From monitoring summary endpoint</div>
            </div>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 24 }}>
            <ResponsiveContainer width="50%" height={220}>
              <PieChart>
                <Pie data={pieDistribution} dataKey="value" cx="50%" cy="50%" innerRadius={52} outerRadius={84}>
                  {pieDistribution.map((entry, i) => <Cell key={i} fill={entry.color} />)}
                </Pie>
                <Tooltip />
              </PieChart>
            </ResponsiveContainer>
            <div style={{ flex: 1 }}>
              {pieDistribution.map((s, i) => (
                <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8, fontSize: 13 }}>
                  <div style={{ width: 10, height: 10, borderRadius: 3, background: s.color, flexShrink: 0 }} />
                  <span style={{ flex: 1, color: 'var(--text-secondary)' }}>{s.name}</span>
                  <span style={{ fontWeight: 600 }}>{s.value}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      <div className="card">
        <div className="card-header">
          <div className="card-title">Recent Spreads</div>
          <Link href="/customers" className="btn btn-ghost btn-sm">View All <ArrowRight size={14} /></Link>
        </div>
        <table className="data-table">
          <thead>
            <tr>
              <th>Customer</th>
              <th>Date</th>
              <th>Status</th>
              <th>AI Accuracy</th>
              <th>Processing Time</th>
            </tr>
          </thead>
          <tbody>
            {stats.recentSpreads.map((s) => (
              <tr key={s.id}>
                <td style={{ fontWeight: 500 }}>{s.customerName}</td>
                <td style={{ color: 'var(--text-secondary)', fontSize: 12 }}>{s.statementDate}</td>
                <td><span className={`badge-status ${s.status.toLowerCase()}`}><span className="dot" />{s.status}</span></td>
                <td>{Number(s.accuracy).toFixed(2)}%</td>
                <td>{s.processingTimeMs ? `${Math.round(s.processingTimeMs / 1000)}s` : '-'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  )
}
