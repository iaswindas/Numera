'use client'

import { useState } from 'react'
import { Plus, ArrowDown } from 'lucide-react'
import StepEditor, { type WorkflowStep } from './StepEditor'

export interface WorkflowDefinition {
  id: string
  name: string
  type: string
  active: boolean
  steps: WorkflowStep[]
}

interface WorkflowDesignerProps {
  workflow: WorkflowDefinition
  onChange: (workflow: WorkflowDefinition) => void
}

const WORKFLOW_TYPES = ['SPREADING', 'COVENANT', 'ONBOARDING', 'REVIEW', 'CUSTOM']

let stepCounter = 0
function nextStepId() {
  return `step-${Date.now()}-${++stepCounter}`
}

export default function WorkflowDesigner({ workflow, onChange }: WorkflowDesignerProps) {
  const updateStep = (idx: number, step: WorkflowStep) => {
    const steps = [...workflow.steps]
    steps[idx] = step
    onChange({ ...workflow, steps })
  }

  const deleteStep = (idx: number) => {
    onChange({ ...workflow, steps: workflow.steps.filter((_, i) => i !== idx) })
  }

  const addStep = () => {
    const step: WorkflowStep = {
      id: nextStepId(),
      name: '',
      type: 'TASK',
      assigneeRole: 'ANALYST',
      slaHours: 24,
      condition: '',
    }
    onChange({ ...workflow, steps: [...workflow.steps, step] })
  }

  const moveStep = (idx: number, dir: -1 | 1) => {
    const target = idx + dir
    if (target < 0 || target >= workflow.steps.length) return
    const steps = [...workflow.steps]
    const tmp = steps[idx]
    steps[idx] = steps[target]
    steps[target] = tmp
    onChange({ ...workflow, steps })
  }

  return (
    <div>
      <div className="grid-2" style={{ marginBottom: 16 }}>
        <div className="input-group">
          <label>Workflow Name</label>
          <input className="input" value={workflow.name} onChange={e => onChange({ ...workflow, name: e.target.value })} />
        </div>
        <div className="input-group">
          <label>Type</label>
          <select className="input" value={workflow.type} onChange={e => onChange({ ...workflow, type: e.target.value })}>
            {WORKFLOW_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
          </select>
        </div>
      </div>

      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
        <div style={{ fontWeight: 600, fontSize: 14 }}>Steps ({workflow.steps.length})</div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13 }}>
            <input type="checkbox" checked={workflow.active} onChange={e => onChange({ ...workflow, active: e.target.checked })} />
            Active
          </label>
        </div>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
        {workflow.steps.map((step, idx) => (
          <div key={step.id}>
            <div style={{ display: 'flex', gap: 8, alignItems: 'flex-start' }}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 2, paddingTop: 6 }}>
                <button className="btn btn-secondary btn-sm" style={{ padding: '0 4px', lineHeight: 1, fontSize: 10 }} onClick={() => moveStep(idx, -1)} disabled={idx === 0}>&uarr;</button>
                <button className="btn btn-secondary btn-sm" style={{ padding: '0 4px', lineHeight: 1, fontSize: 10 }} onClick={() => moveStep(idx, 1)} disabled={idx === workflow.steps.length - 1}>&darr;</button>
              </div>
              <div style={{ flex: 1 }}>
                <StepEditor step={step} onChange={s => updateStep(idx, s)} onDelete={() => deleteStep(idx)} index={idx} />
              </div>
            </div>
            {idx < workflow.steps.length - 1 && (
              <div style={{ display: 'flex', justifyContent: 'center', padding: '4px 0' }}>
                <ArrowDown size={16} style={{ color: 'var(--text-muted)' }} />
              </div>
            )}
          </div>
        ))}
      </div>

      <div style={{ display: 'flex', justifyContent: 'center', marginTop: 12 }}>
        <button className="btn btn-secondary" onClick={addStep}><Plus size={14} /> Add Step</button>
      </div>
    </div>
  )
}
