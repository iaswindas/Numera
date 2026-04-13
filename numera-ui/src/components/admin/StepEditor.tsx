'use client'

import { useState } from 'react'
import { Pencil, Trash2 } from 'lucide-react'

export interface WorkflowStep {
  id: string
  name: string
  type: 'TASK' | 'APPROVAL' | 'CONDITION' | 'NOTIFY' | 'SYSTEM'
  assigneeRole: string
  slaHours: number
  condition: string
}

interface StepEditorProps {
  step: WorkflowStep
  onChange: (step: WorkflowStep) => void
  onDelete: () => void
  index: number
}

const STEP_TYPES: WorkflowStep['type'][] = ['TASK', 'APPROVAL', 'CONDITION', 'NOTIFY', 'SYSTEM']
const ROLES = ['ANALYST', 'MANAGER', 'ADMIN', 'SYSTEM']

const typeColors: Record<string, string> = {
  TASK: '#3b82f6',
  APPROVAL: '#22c55e',
  CONDITION: '#f59e0b',
  NOTIFY: '#8b5cf6',
  SYSTEM: '#6b7280',
}

export default function StepEditor({ step, onChange, onDelete, index }: StepEditorProps) {
  const [expanded, setExpanded] = useState(false)

  return (
    <div
      style={{
        border: `2px solid ${typeColors[step.type] || 'var(--border-subtle)'}`,
        borderRadius: 8,
        padding: expanded ? 16 : '8px 12px',
        background: 'var(--surface)',
        transition: 'all 0.15s',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <span style={{
            display: 'inline-block', width: 24, height: 24, borderRadius: '50%',
            background: typeColors[step.type], color: '#fff', fontSize: 11,
            lineHeight: '24px', textAlign: 'center', fontWeight: 700,
          }}>{index + 1}</span>
          <span style={{ fontWeight: 600, fontSize: 13 }}>{step.name || `Step ${index + 1}`}</span>
          <span style={{ fontSize: 11, color: 'var(--text-muted)', padding: '1px 6px', borderRadius: 4, background: 'var(--surface-hover)' }}>{step.type}</span>
        </div>
        <div style={{ display: 'flex', gap: 4 }}>
          <button className="btn btn-secondary btn-sm" onClick={() => setExpanded(!expanded)}><Pencil size={12} /></button>
          <button className="btn btn-secondary btn-sm" onClick={onDelete}><Trash2 size={12} /></button>
        </div>
      </div>

      {expanded && (
        <div style={{ marginTop: 12 }}>
          <div className="grid-2" style={{ gap: 8 }}>
            <div className="input-group">
              <label>Step Name</label>
              <input className="input" value={step.name} onChange={e => onChange({ ...step, name: e.target.value })} />
            </div>
            <div className="input-group">
              <label>Type</label>
              <select className="input" value={step.type} onChange={e => onChange({ ...step, type: e.target.value as WorkflowStep['type'] })}>
                {STEP_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
              </select>
            </div>
          </div>
          <div className="grid-2" style={{ gap: 8, marginTop: 8 }}>
            <div className="input-group">
              <label>Assignee Role</label>
              <select className="input" value={step.assigneeRole} onChange={e => onChange({ ...step, assigneeRole: e.target.value })}>
                {ROLES.map(r => <option key={r} value={r}>{r}</option>)}
              </select>
            </div>
            <div className="input-group">
              <label>SLA (hours)</label>
              <input className="input" type="number" min={0} value={step.slaHours} onChange={e => onChange({ ...step, slaHours: Number(e.target.value) })} />
            </div>
          </div>
          {step.type === 'CONDITION' && (
            <div className="input-group" style={{ marginTop: 8 }}>
              <label>Condition Expression</label>
              <input className="input" value={step.condition} onChange={e => onChange({ ...step, condition: e.target.value })} placeholder="e.g. amount > 100000" style={{ fontFamily: 'monospace' }} />
            </div>
          )}
        </div>
      )}
    </div>
  )
}
