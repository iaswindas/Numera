'use client'

import { useState } from 'react'
import { Plus, Pencil, Trash2, Power } from 'lucide-react'
import { useAdminWorkflows, useCreateWorkflow, useUpdateWorkflow, useDeleteWorkflow } from '@/services/adminApi'
import { useToast } from '@/components/ui/Toast'
import WorkflowDesigner, { type WorkflowDefinition } from '@/components/admin/WorkflowDesigner'
import type { WorkflowStep } from '@/components/admin/StepEditor'

interface RawWorkflow {
  id: string
  name: string
  type: string
  active: boolean
  steps: string[] | WorkflowStep[]
}

function normalizeSteps(raw: string[] | WorkflowStep[]): WorkflowStep[] {
  if (!raw || raw.length === 0) return []
  if (typeof raw[0] === 'string') {
    return (raw as string[]).map((s, i) => ({
      id: `step-${i}`,
      name: s,
      type: 'TASK' as const,
      assigneeRole: 'ANALYST',
      slaHours: 24,
      condition: '',
    }))
  }
  return raw as WorkflowStep[]
}

export default function AdminWorkflowsPage() {
  const { showToast } = useToast()
  const workflowsQuery = useAdminWorkflows()
  const createWorkflow = useCreateWorkflow()
  const updateWorkflow = useUpdateWorkflow()
  const deleteWorkflowMut = useDeleteWorkflow()

  const workflows = (workflowsQuery.data as RawWorkflow[] | undefined) ?? []

  const [showDesigner, setShowDesigner] = useState(false)
  const [designerWorkflow, setDesignerWorkflow] = useState<WorkflowDefinition | null>(null)

  const openNew = () => {
    setDesignerWorkflow({
      id: '',
      name: '',
      type: 'SPREADING',
      active: true,
      steps: [],
    })
    setShowDesigner(true)
  }

  const openEdit = (w: RawWorkflow) => {
    setDesignerWorkflow({
      id: w.id,
      name: w.name,
      type: w.type,
      active: w.active,
      steps: normalizeSteps(w.steps),
    })
    setShowDesigner(true)
  }

  const saveWorkflow = async () => {
    if (!designerWorkflow) return
    const payload = {
      name: designerWorkflow.name,
      type: designerWorkflow.type,
      active: designerWorkflow.active,
      steps: designerWorkflow.steps.map(s => s.name || s.id),
    }
    try {
      if (designerWorkflow.id) {
        await updateWorkflow.mutateAsync({ id: designerWorkflow.id, ...payload })
        showToast('Workflow updated', 'success')
      } else {
        await createWorkflow.mutateAsync(payload)
        showToast('Workflow created', 'success')
      }
      setShowDesigner(false)
    } catch (error) {
      const msg = typeof error === 'object' && error && 'message' in error ? String((error as { message: string }).message) : 'Failed to save workflow'
      showToast(msg, 'error')
    }
  }

  const toggleActive = async (w: RawWorkflow) => {
    try {
      await updateWorkflow.mutateAsync({ id: w.id, active: !w.active })
      showToast(`Workflow ${!w.active ? 'activated' : 'deactivated'}`, 'success')
    } catch {
      showToast('Failed to toggle workflow', 'error')
    }
  }

  const deleteWorkflow = async (w: RawWorkflow) => {
    if (!confirm(`Delete workflow "${w.name}"?`)) return
    try {
      await deleteWorkflowMut.mutateAsync(w.id)
      showToast('Workflow deleted', 'success')
    } catch {
      showToast('Failed to delete workflow', 'error')
    }
  }

  return (
    <>
      <div className="page-header"><h1>Workflow Designer</h1><p>Design and manage approval workflows</p></div>
      <div className="toolbar">
        <div className="toolbar-left" />
        <div className="toolbar-right">
          <button className="btn btn-primary" onClick={openNew}><Plus size={16} />New Workflow</button>
        </div>
      </div>

      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <table className="data-table">
          <thead><tr><th>Name</th><th>Type</th><th>Steps</th><th>Status</th><th>Actions</th></tr></thead>
          <tbody>
            {workflows.map(w => (
              <tr key={w.id}>
                <td style={{ fontWeight: 600 }}>{w.name}</td>
                <td><span style={{ fontSize: 11, padding: '2px 8px', borderRadius: 4, background: 'var(--surface-hover)' }}>{w.type}</span></td>
                <td>{Array.isArray(w.steps) ? (typeof w.steps[0] === 'string' ? w.steps.join(' → ') : (w.steps as WorkflowStep[]).map(s => s.name).join(' → ')) : '-'}</td>
                <td>
                  <span className={`badge-status ${w.active ? 'approved' : 'draft'}`}>
                    <span className="dot" />{w.active ? 'Active' : 'Inactive'}
                  </span>
                </td>
                <td style={{ display: 'flex', gap: 4 }}>
                  <button className="btn btn-secondary btn-sm" onClick={() => openEdit(w)}><Pencil size={13} /> Design</button>
                  <button className="btn btn-secondary btn-sm" onClick={() => toggleActive(w)}><Power size={13} /></button>
                  <button className="btn btn-secondary btn-sm" onClick={() => deleteWorkflow(w)}><Trash2 size={13} /></button>
                </td>
              </tr>
            ))}
            {workflows.length === 0 && <tr><td colSpan={5} style={{ color: 'var(--text-muted)' }}>No workflows found.</td></tr>}
          </tbody>
        </table>
      </div>

      {showDesigner && designerWorkflow && (
        <div className="modal-overlay">
          <div className="modal" style={{ maxWidth: 750, maxHeight: '90vh', overflow: 'auto' }}>
            <WorkflowDesigner workflow={designerWorkflow} onChange={setDesignerWorkflow} />
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 20 }}>
              <button className="btn btn-secondary" onClick={() => setShowDesigner(false)}>Cancel</button>
              <button className="btn btn-primary" disabled={!designerWorkflow.name.trim()} onClick={saveWorkflow}>Save Workflow</button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
