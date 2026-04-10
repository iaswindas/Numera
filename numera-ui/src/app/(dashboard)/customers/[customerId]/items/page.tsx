'use client'

import Link from 'next/link'
import { useParams } from 'next/navigation'
import { ArrowLeft, Building2, Plus } from 'lucide-react'
import { useCustomer } from '@/services/customerApi'
import { useCustomerSpreads } from '@/services/spreadApi'

export default function CustomerItemsPage() {
  const params = useParams<{ customerId: string }>()
  const customerId = params.customerId
  const customerQuery = useCustomer(customerId)
  const spreadsQuery = useCustomerSpreads(customerId)

  if (customerQuery.isLoading) {
    return <div className="card">Loading customer...</div>
  }

  const customer = customerQuery.data
  const items = spreadsQuery.data ?? []

  return (
    <>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 24, flexWrap: 'wrap' }}>
        <Link href="/customers" className="btn btn-ghost"><ArrowLeft size={16} /></Link>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Building2 size={20} style={{ color: 'var(--accent)' }} />
            <h1 style={{ fontSize: 22, fontWeight: 700 }}>{customer?.name ?? 'Customer'}</h1>
            <span style={{ fontSize: 12, fontFamily: 'monospace', color: 'var(--text-muted)', background: 'var(--bg-input)', padding: '2px 8px', borderRadius: 4 }}>
              {customer?.customerCode}
            </span>
          </div>
          <p style={{ fontSize: 14, color: 'var(--text-secondary)', marginTop: 2 }}>
            Industry: {customer?.industry ?? '-'} | Country: {customer?.country ?? '-'}
          </p>
        </div>
        <div style={{ flex: 1 }} />
        <button className="btn btn-primary"><Plus size={16} />Add Item</button>
      </div>

      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <table className="data-table">
          <thead>
            <tr>
              <th>Statement Date</th>
              <th>Status</th>
              <th>Version</th>
              <th>Created At</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {items.map((it) => (
              <tr key={it.id}>
                <td>{it.statementDate}</td>
                <td><span className={`badge-status ${it.status.toLowerCase()}`}><span className="dot" />{it.status}</span></td>
                <td>{it.currentVersion}</td>
                <td style={{ color: 'var(--text-secondary)' }}>{new Date(it.createdAt).toLocaleString()}</td>
                <td>
                  <Link href={`/spreading/${it.id}`} className="btn btn-primary btn-sm">Open Workspace</Link>
                </td>
              </tr>
            ))}
            {items.length === 0 ? (
              <tr>
                <td colSpan={5} style={{ color: 'var(--text-muted)' }}>No spread items found.</td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </div>
    </>
  )
}
