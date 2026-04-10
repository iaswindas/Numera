'use client'

import { useAdminTaxonomy } from '@/services/adminApi'

export default function AdminTaxonomyPage() {
  const taxonomyQuery = useAdminTaxonomy()
  const entries = ((taxonomyQuery.data as { entries?: Array<{ itemCode: string; label: string; zone: string; category: string }> })?.entries) ?? []

  return (
    <>
      <div className="page-header"><h1>Taxonomy & Zones</h1><p>Manage taxonomy mappings and zone definitions</p></div>
      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <table className="data-table">
          <thead><tr><th>Item Code</th><th>Label</th><th>Zone</th><th>Category</th></tr></thead>
          <tbody>
            {entries.map((e) => (
              <tr key={e.itemCode}>
                <td>{e.itemCode}</td>
                <td>{e.label}</td>
                <td>{e.zone}</td>
                <td>{e.category}</td>
              </tr>
            ))}
            {entries.length === 0 ? <tr><td colSpan={4} style={{ color: 'var(--text-muted)' }}>No taxonomy entries found.</td></tr> : null}
          </tbody>
        </table>
      </div>
    </>
  )
}
