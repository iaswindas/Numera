'use client'

import { useMemo, useState } from 'react'
import { useParams, useRouter } from 'next/navigation'
import { ArrowLeft, FileText, Shield, Send } from 'lucide-react'
import { useMonitoringItems } from '@/services/covenantApi'
import { useAuthStore } from '@/stores/authStore'
import DocumentVerification from '@/components/covenant/DocumentVerification'

type MonitoringItem = {
  id: string
  covenantName: string
  status: string
  periodEnd: string
  dueDate: string
  breachProbability?: number
  customerName?: string
  covenantType?: string
}

export default function CovenantItemDetailPage() {
  const params = useParams<{ itemId: string }>()
  const router = useRouter()
  const { user } = useAuthStore()
  const itemId = String(params.itemId ?? '')

  const monitoringQuery = useMonitoringItems()
  const items = (monitoringQuery.data as MonitoringItem[] | undefined) ?? []
  const item = useMemo(() => items.find((i) => i.id === itemId), [items, itemId])

  const [activeTab, setActiveTab] = useState<'details' | 'documents-maker' | 'documents-checker'>('details')

  // Simple role check: if user has checker/approver role, show checker tab
  const userRole = user?.role ?? ''
  const isChecker = ['CHECKER', 'APPROVER', 'ADMIN', 'MANAGER'].some((r) => userRole.toUpperCase().includes(r))

  return (
    <>
      <div className="page-header">
        <button className="btn btn-ghost btn-sm" onClick={() => router.push('/covenants')} style={{ marginBottom: 8 }}>
          <ArrowLeft size={14} /> Back to Monitoring
        </button>
        <h1>{item?.covenantName ?? `Item ${itemId}`}</h1>
        <p>Monitoring Item Detail & Document Verification</p>
      </div>

      {/* Tab bar */}
      <div className="tabs" style={{ marginBottom: 20 }}>
        <button className={`tab ${activeTab === 'details' ? 'active' : ''}`} onClick={() => setActiveTab('details')}>
          <Shield size={14} /> Details
        </button>
        <button className={`tab ${activeTab === 'documents-maker' ? 'active' : ''}`} onClick={() => setActiveTab('documents-maker')}>
          <FileText size={14} /> Documents (Maker)
        </button>
        {isChecker && (
          <button className={`tab ${activeTab === 'documents-checker' ? 'active' : ''}`} onClick={() => setActiveTab('documents-checker')}>
            <FileText size={14} /> Documents (Checker)
          </button>
        )}
      </div>

      {/* Details tab */}
      {activeTab === 'details' && (
        <div style={{ display: 'grid', gap: 16 }}>
          {monitoringQuery.isLoading && <div style={{ color: 'var(--text-muted)' }}>Loading...</div>}

          {item && (
            <div className="card">
              <div className="card-header">
                <div>
                  <div className="card-title">{item.covenantName}</div>
                  <div className="card-subtitle">Monitoring item details</div>
                </div>
                <span className={`badge-status ${item.status.toLowerCase()}`}>
                  <span className="dot" />{item.status}
                </span>
              </div>

              <div className="grid-2" style={{ marginTop: 12 }}>
                <div className="input-group">
                  <label>Item ID</label>
                  <div style={{ fontSize: 13, fontFamily: 'monospace' }}>{item.id}</div>
                </div>
                <div className="input-group">
                  <label>Covenant Type</label>
                  <div style={{ fontSize: 13 }}>{item.covenantType ?? '-'}</div>
                </div>
                <div className="input-group">
                  <label>Period End</label>
                  <div style={{ fontSize: 13 }}>{item.periodEnd}</div>
                </div>
                <div className="input-group">
                  <label>Due Date</label>
                  <div style={{ fontSize: 13 }}>{item.dueDate}</div>
                </div>
                <div className="input-group">
                  <label>Customer</label>
                  <div style={{ fontSize: 13 }}>{item.customerName ?? '-'}</div>
                </div>
                <div className="input-group">
                  <label>Breach Probability</label>
                  <div style={{ fontSize: 13 }}>
                    {item.breachProbability != null
                      ? `${(item.breachProbability * 100).toFixed(1)}%`
                      : '-'}
                  </div>
                </div>
              </div>

              <div style={{ display: 'flex', gap: 8, marginTop: 16 }}>
                <button className="btn btn-primary btn-sm" onClick={() => router.push(`/covenants/${itemId}/waiver`)}>
                  <Send size={14} /> Waiver Letter
                </button>
                <button className="btn btn-secondary btn-sm" onClick={() => setActiveTab('documents-maker')}>
                  <FileText size={14} /> Upload Documents
                </button>
              </div>
            </div>
          )}

          {!item && !monitoringQuery.isLoading && (
            <div className="card" style={{ color: 'var(--text-muted)' }}>Monitoring item not found.</div>
          )}
        </div>
      )}

      {/* Maker document tab */}
      {activeTab === 'documents-maker' && (
        <DocumentVerification monitoringItemId={itemId} role="maker" />
      )}

      {/* Checker document tab */}
      {activeTab === 'documents-checker' && (
        <DocumentVerification monitoringItemId={itemId} role="checker" />
      )}
    </>
  )
}
