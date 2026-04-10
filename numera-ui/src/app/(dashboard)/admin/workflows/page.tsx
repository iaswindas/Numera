'use client'

import { useAdminWorkflows } from '@/services/adminApi'

export default function AdminWorkflowsPage() {
  const workflowsQuery = useAdminWorkflows()
  const workflows = (workflowsQuery.data as Array<{ id: string; name: string; type: string; active: boolean; steps: string[] }> | undefined) ?? []

  return (
    <>
      <div className="page-header"><h1>Workflow Designer</h1><p>Manage workflow definitions</p></div>
      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <table className="data-table">
          <thead><tr><th>Name</th><th>Type</th><th>Steps</th><th>Status</th></tr></thead>
          <tbody>
            {workflows.map((w) => (
              <tr key={w.id}>
                <td>{w.name}</td>
                <td>{w.type}</td>
                <td>{Array.isArray(w.steps) ? w.steps.join(' -> ') : '-'}</td>
                <td><span className={`badge-status ${w.active ? 'approved' : 'draft'}`}><span className="dot" />{w.active ? 'Active' : 'Inactive'}</span></td>
              </tr>
            ))}
            {workflows.length === 0 ? <tr><td colSpan={4} style={{ color: 'var(--text-muted)' }}>No workflows found.</td></tr> : null}
          </tbody>
        </table>
      </div>
    </>
  )
}
