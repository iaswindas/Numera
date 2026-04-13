'use client'

import { useState } from 'react'
import { RefreshCw, ArrowUpCircle, BarChart3, Activity } from 'lucide-react'
import { useAiModels, useAiModelMetrics, useRetrainModel, usePromoteModel, type AiModel } from '@/services/adminApi'
import { useToast } from '@/components/ui/Toast'

const STATUS_STYLES: Record<string, string> = {
  ACTIVE: 'approved',
  SHADOW: 'draft',
  RETIRED: 'inactive',
  TRAINING: 'pending',
}

function AccuracyBar({ value }: { value: number }) {
  const pct = Math.round(value * 100)
  const barColor = pct >= 90 ? 'var(--success)' : pct >= 80 ? 'var(--warning, #f59e0b)' : 'var(--danger)'
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
      <div style={{ flex: 1, height: 6, borderRadius: 3, background: 'var(--surface-hover)', maxWidth: 100 }}>
        <div style={{ width: `${pct}%`, height: '100%', borderRadius: 3, background: barColor }} />
      </div>
      <span style={{ fontSize: 12, fontWeight: 600 }}>{pct}%</span>
    </div>
  )
}

function MetricsPanel({ modelId }: { modelId: string }) {
  const metricsQuery = useAiModelMetrics(modelId)
  const m = metricsQuery.data

  if (metricsQuery.isLoading) return <div style={{ padding: 12, color: 'var(--text-muted)' }}>Loading metrics...</div>
  if (!m) return <div style={{ padding: 12, color: 'var(--text-muted)' }}>No metrics available.</div>

  const items = [
    { label: 'Accuracy', value: m.accuracy },
    { label: 'Precision', value: m.precision },
    { label: 'Recall', value: m.recall },
    { label: 'F1 Score', value: m.f1Score },
  ]

  return (
    <div style={{ padding: '12px 16px', background: 'var(--surface-hover)' }}>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 16 }}>
        {items.map(item => (
          <div key={item.label}>
            <div style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 4 }}>{item.label}</div>
            <AccuracyBar value={item.value} />
          </div>
        ))}
      </div>
      <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 8 }}>
        Last evaluated: {m.lastEvaluated ? new Date(m.lastEvaluated).toLocaleString() : '-'}
      </div>
    </div>
  )
}

export default function AiModelsPage() {
  const { showToast } = useToast()
  const modelsQuery = useAiModels()
  const retrain = useRetrainModel()
  const promote = usePromoteModel()

  const models = modelsQuery.data ?? []
  const [expandedId, setExpandedId] = useState<string | null>(null)
  const [compareA, setCompareA] = useState<string>('')
  const [compareB, setCompareB] = useState<string>('')
  const [showCompare, setShowCompare] = useState(false)

  const onRetrain = async (modelId: string) => {
    try {
      await retrain.mutateAsync(modelId)
      showToast('Retrain job queued', 'success')
    } catch {
      showToast('Failed to queue retrain', 'error')
    }
  }

  const onPromote = async (modelId: string) => {
    if (!confirm('Promote this model to production?')) return
    try {
      await promote.mutateAsync({ modelId })
      showToast('Model promoted', 'success')
    } catch {
      showToast('Failed to promote model', 'error')
    }
  }

  return (
    <>
      <div className="page-header"><h1>AI Model Management</h1><p>Monitor and manage machine learning models</p></div>
      <div className="toolbar">
        <div className="toolbar-left" />
        <div className="toolbar-right">
          <button className="btn btn-secondary" onClick={() => setShowCompare(true)}><BarChart3 size={16} /> A/B Compare</button>
        </div>
      </div>

      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <table className="data-table">
          <thead>
            <tr><th>Model</th><th>Type</th><th>Version</th><th>Accuracy</th><th>Last Retrained</th><th>Status</th><th>Actions</th></tr>
          </thead>
          <tbody>
            {modelsQuery.isLoading && <tr><td colSpan={7} style={{ color: 'var(--text-muted)' }}>Loading models...</td></tr>}
            {!modelsQuery.isLoading && models.length === 0 && <tr><td colSpan={7} style={{ color: 'var(--text-muted)' }}>No AI models found.</td></tr>}
            {models.map(m => (
              <>
                <tr key={m.id}>
                  <td style={{ fontWeight: 600 }}>{m.name}</td>
                  <td><span style={{ fontSize: 11, padding: '2px 8px', borderRadius: 4, background: 'var(--surface-hover)' }}>{m.type}</span></td>
                  <td>{m.version}</td>
                  <td><AccuracyBar value={m.accuracy} /></td>
                  <td>{m.lastRetrained ? new Date(m.lastRetrained).toLocaleDateString() : '-'}</td>
                  <td>
                    <span className={`badge-status ${STATUS_STYLES[m.status] || 'draft'}`}>
                      <span className="dot" />{m.status}
                    </span>
                  </td>
                  <td style={{ display: 'flex', gap: 4 }}>
                    <button className="btn btn-secondary btn-sm" onClick={() => setExpandedId(expandedId === m.id ? null : m.id)} title="Metrics"><Activity size={13} /></button>
                    <button className="btn btn-secondary btn-sm" onClick={() => onRetrain(m.id)} title="Retrain"><RefreshCw size={13} /></button>
                    {m.status === 'SHADOW' && (
                      <button className="btn btn-primary btn-sm" onClick={() => onPromote(m.id)} title="Promote"><ArrowUpCircle size={13} /> Promote</button>
                    )}
                  </td>
                </tr>
                {expandedId === m.id && (
                  <tr key={`${m.id}-metrics`}>
                    <td colSpan={7} style={{ padding: 0 }}>
                      <MetricsPanel modelId={m.id} />
                    </td>
                  </tr>
                )}
              </>
            ))}
          </tbody>
        </table>
      </div>

      {showCompare && (
        <div className="modal-overlay">
          <div className="modal" style={{ maxWidth: 700 }}>
            <div className="card-title">A/B Model Comparison</div>
            <div className="grid-2" style={{ marginTop: 12 }}>
              <div className="input-group">
                <label>Model A</label>
                <select className="input" value={compareA} onChange={e => setCompareA(e.target.value)}>
                  <option value="">Select model...</option>
                  {models.map(m => <option key={m.id} value={m.id}>{m.name} ({m.version})</option>)}
                </select>
              </div>
              <div className="input-group">
                <label>Model B</label>
                <select className="input" value={compareB} onChange={e => setCompareB(e.target.value)}>
                  <option value="">Select model...</option>
                  {models.map(m => <option key={m.id} value={m.id}>{m.name} ({m.version})</option>)}
                </select>
              </div>
            </div>
            {compareA && compareB && (
              <div style={{ marginTop: 16 }}>
                <div className="grid-2" style={{ gap: 16 }}>
                  <div>
                    <div style={{ fontWeight: 600, marginBottom: 8 }}>{models.find(m => m.id === compareA)?.name}</div>
                    <MetricsPanel modelId={compareA} />
                  </div>
                  <div>
                    <div style={{ fontWeight: 600, marginBottom: 8 }}>{models.find(m => m.id === compareB)?.name}</div>
                    <MetricsPanel modelId={compareB} />
                  </div>
                </div>
              </div>
            )}
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}>
              <button className="btn btn-secondary" onClick={() => setShowCompare(false)}>Close</button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
