'use client'

import { Download } from 'lucide-react'
import { useAuditTrailReport, useCovenantSummaryReport, useSpreadingSummaryReport } from '@/services/reportApi'

export default function ReportsPage() {
  const spreadingQuery = useSpreadingSummaryReport()
  const covenantQuery = useCovenantSummaryReport()
  const auditQuery = useAuditTrailReport()

  const download = (format: string) => {
    window.open(`/api/reports/export?format=${encodeURIComponent(format)}`, '_blank')
  }

  return (
    <>
      <div className="page-header">
        <h1>Reports & MIS</h1>
        <p>Generate compliance, audit, and analytics reports</p>
      </div>

      <div className="toolbar">
        <div className="toolbar-left" />
        <div className="toolbar-right">
          <button className="btn btn-secondary" onClick={() => download('csv')}><Download size={14} />Export CSV</button>
          <button className="btn btn-primary" onClick={() => download('xlsx')}><Download size={14} />Export Excel</button>
        </div>
      </div>

      <div className="grid-2">
        <div className="card">
          <div className="card-header"><div className="card-title">Spreading Summary</div></div>
          <pre style={{ fontSize: 12, whiteSpace: 'pre-wrap', color: 'var(--text-secondary)' }}>
            {JSON.stringify(spreadingQuery.data ?? {}, null, 2)}
          </pre>
        </div>
        <div className="card">
          <div className="card-header"><div className="card-title">Covenant Summary</div></div>
          <pre style={{ fontSize: 12, whiteSpace: 'pre-wrap', color: 'var(--text-secondary)' }}>
            {JSON.stringify(covenantQuery.data ?? {}, null, 2)}
          </pre>
        </div>
      </div>

      <div className="card">
        <div className="card-header"><div className="card-title">Audit Trail</div></div>
        <pre style={{ fontSize: 12, whiteSpace: 'pre-wrap', color: 'var(--text-secondary)' }}>
          {JSON.stringify(auditQuery.data ?? {}, null, 2)}
        </pre>
      </div>
    </>
  )
}
