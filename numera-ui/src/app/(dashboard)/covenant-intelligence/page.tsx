'use client'

import { useMemo } from 'react'
import { AlertTriangle } from 'lucide-react'
import { useMonitoringItems } from '@/services/covenantApi'

export default function CovenantIntelligencePage() {
  const monitoringQuery = useMonitoringItems()
  const items = (monitoringQuery.data as Array<{ id: string; covenantName: string; status: string; breachProbability?: number; periodEnd: string; dueDate: string }> | undefined) ?? []

  const topRisks = useMemo(
    () => [...items].sort((a, b) => Number(b.breachProbability ?? 0) - Number(a.breachProbability ?? 0)).slice(0, 10),
    [items]
  )

  const byStatus = useMemo(() => {
    const map = new Map<string, number>()
    for (const item of items) {
      map.set(item.status, (map.get(item.status) ?? 0) + 1)
    }
    return map
  }, [items])

  return (
    <>
      <div className="page-header">
        <h1>Covenant Intelligence</h1>
        <p>Predictive breach analytics and portfolio-level covenant monitoring</p>
      </div>

      <div className="stat-grid" style={{ gridTemplateColumns: 'repeat(4,1fr)' }}>
        <div className="stat-card danger"><div className="stat-label">Breached</div><div className="stat-value">{byStatus.get('BREACHED') ?? 0}</div></div>
        <div className="stat-card warning"><div className="stat-label">Overdue</div><div className="stat-value">{byStatus.get('OVERDUE') ?? 0}</div></div>
        <div className="stat-card accent"><div className="stat-label">Submitted</div><div className="stat-value">{byStatus.get('SUBMITTED') ?? 0}</div></div>
        <div className="stat-card purple"><div className="stat-label">Closed</div><div className="stat-value">{byStatus.get('CLOSED') ?? 0}</div></div>
      </div>

      <div className="card" style={{ marginBottom: 20 }}>
        <div className="card-header">
          <div className="card-title"><AlertTriangle size={16} style={{ display: 'inline', verticalAlign: -2, marginRight: 6 }} />Highest Risk Covenants</div>
        </div>
        <table className="data-table">
          <thead>
            <tr><th>Covenant</th><th>Status</th><th>Breach Probability</th><th>Period End</th><th>Due Date</th></tr>
          </thead>
          <tbody>
            {topRisks.map((r) => (
              <tr key={r.id}>
                <td>{r.covenantName}</td>
                <td><span className={`badge-status ${r.status.toLowerCase()}`}><span className="dot" />{r.status}</span></td>
                <td>{r.breachProbability ?? '-'}</td>
                <td>{r.periodEnd}</td>
                <td>{r.dueDate}</td>
              </tr>
            ))}
            {topRisks.length === 0 ? <tr><td colSpan={5} style={{ color: 'var(--text-muted)' }}>No monitoring data found.</td></tr> : null}
          </tbody>
        </table>
      </div>
    </>
  )
}
