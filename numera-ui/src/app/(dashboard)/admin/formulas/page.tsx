'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchApi } from '@/services/api'

export default function AdminFormulasPage() {
  const qc = useQueryClient()
  const templatesQuery = useQuery({ queryKey: ['model-templates'], queryFn: () => fetchApi('/model-templates') })
  const createTemplate = useMutation({
    mutationFn: () => fetchApi('/model-templates', {
      method: 'POST',
      body: JSON.stringify({
        name: `Template ${new Date().toISOString().slice(0, 10)}`,
        version: 1,
        currency: 'AED',
        active: true,
        lineItems: [],
        validations: [],
      }),
    }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['model-templates'] }),
  })

  const templates = (templatesQuery.data as Array<{ id: string; name: string; version: number; currency: string; active: boolean }> | undefined) ?? []

  return (
    <>
      <div className="page-header"><h1>Formula Management</h1><p>Manage model templates and formulas</p></div>
      <div className="toolbar"><div className="toolbar-left" /><div className="toolbar-right"><button className="btn btn-primary" onClick={() => createTemplate.mutate()}>Create Template</button></div></div>
      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <table className="data-table">
          <thead><tr><th>Name</th><th>Version</th><th>Currency</th><th>Status</th></tr></thead>
          <tbody>
            {templates.map((t) => (
              <tr key={t.id}>
                <td>{t.name}</td>
                <td>{t.version}</td>
                <td>{t.currency}</td>
                <td><span className={`badge-status ${t.active ? 'approved' : 'draft'}`}><span className="dot" />{t.active ? 'Active' : 'Inactive'}</span></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  )
}
