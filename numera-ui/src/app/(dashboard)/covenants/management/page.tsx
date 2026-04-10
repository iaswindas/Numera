'use client'
import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { Plus, Search, Shield, Eye, Edit, Loader2 } from 'lucide-react'
import { useCovenantCustomers } from '@/services/covenantApi'

interface CovenantCustomerItem {
  id: string
  customerName: string
  rimId: string | null
  clEntityId: string | null
  financialYearEnd: string | null
  isActive: boolean
  financialCovenants: number
  nonFinancialCovenants: number
  totalCovenants: number
  updatedAt: string
}

export default function CovenantManagementPage() {
  const router = useRouter()
  const [showCreate, setShowCreate] = useState(false)
  const [searchQuery, setSearchQuery] = useState('')

  const customersQuery = useCovenantCustomers(searchQuery || undefined)
  const customers = (customersQuery.data ?? []) as CovenantCustomerItem[]

  return (
    <>
      <div className="page-header">
        <h1>Covenant Management</h1>
        <p>Manage covenant customers and covenant definitions</p>
      </div>

      <div className="toolbar">
        <div className="toolbar-left">
          <div className="search-bar">
            <Search size={16} />
            <input
              placeholder="Search by RIM ID or customer name..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </div>
        </div>
        <div className="toolbar-right">
          <button className="btn btn-primary" onClick={() => setShowCreate(true)}>
            <Plus size={16} />New Customer
          </button>
        </div>
      </div>

      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <table className="data-table">
          <thead>
            <tr>
              <th>Customer Name</th>
              <th>RIM ID</th>
              <th>Last Modified</th>
              <th>Financial</th>
              <th>Non-Financial</th>
              <th>Total Covenants</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {customersQuery.isLoading && (
              <tr>
                <td colSpan={8} style={{ textAlign: 'center', padding: 32, color: 'var(--text-muted)' }}>
                  <Loader2 size={20} className="spin" /> Loading customers...
                </td>
              </tr>
            )}
            {!customersQuery.isLoading && customers.length === 0 && (
              <tr>
                <td colSpan={8} style={{ textAlign: 'center', padding: 32, color: 'var(--text-muted)' }}>
                  No covenant customers found.{searchQuery ? ' Try a different search.' : ' Create one to get started.'}
                </td>
              </tr>
            )}
            {customers.map(c => (
              <tr key={c.id}>
                <td>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <Shield size={16} style={{ color: 'var(--accent)', flexShrink: 0 }} />
                    <span style={{ fontWeight: 600, color: 'var(--accent)', cursor: 'pointer' }}
                      onClick={() => router.push(`/covenants/${c.id}`)}>
                      {c.customerName}
                    </span>
                  </div>
                </td>
                <td style={{ fontFamily: 'monospace', fontSize: 12, color: 'var(--text-secondary)' }}>{c.rimId ?? '-'}</td>
                <td style={{ fontSize: 12, color: 'var(--text-secondary)' }}>{c.updatedAt ? new Date(c.updatedAt).toLocaleDateString() : '-'}</td>
                <td style={{ fontWeight: 600, color: 'var(--accent)' }}>{c.financialCovenants ?? 0}</td>
                <td style={{ fontWeight: 600, color: 'var(--purple)' }}>{c.nonFinancialCovenants ?? 0}</td>
                <td style={{ fontWeight: 700 }}>{c.totalCovenants ?? 0}</td>
                <td>
                  <span className={`badge-status ${c.isActive ? 'approved' : 'draft'}`}>
                    <span className="dot" />{c.isActive ? 'Active' : 'Inactive'}
                  </span>
                </td>
                <td>
                  <div style={{ display: 'flex', gap: 4 }}>
                    <button className="btn btn-ghost btn-sm"><Edit size={14} /></button>
                    <button className="btn btn-primary btn-sm"><Plus size={13} />Add Covenant</button>
                    <button className="btn btn-ghost btn-sm" onClick={() => router.push(`/covenants/${c.id}`)}>
                      <Eye size={14} />View
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {showCreate && (
        <div className="modal-overlay">
          <div className="modal" style={{ maxWidth: 640 }}>
            <div className="card-header">
              <div className="card-title">Create Covenant Customer</div>
              <button className="btn btn-ghost btn-sm" onClick={() => setShowCreate(false)}>✕</button>
            </div>
            <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: 1, marginBottom: 12 }}>
              Basic Information
            </div>
            <div className="grid-2" style={{ marginBottom: 16 }}>
              <div className="input-group"><label>Customer Name *</label><input className="input" placeholder="Enter customer name" /></div>
              <div className="input-group"><label>RIM ID *</label><input className="input" placeholder="RIM-XXXXX" /></div>
              <div className="input-group"><label>CL Entity ID</label><input className="input" placeholder="ENT-XXXXX" /></div>
              <div className="input-group"><label>Financial Year End *</label><input className="input" type="date" /></div>
            </div>
            <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: 1, marginBottom: 12, marginTop: 20 }}>
              Contacts
            </div>
            <div className="tabs">
              <div className="tab active">Internal Users</div>
              <div className="tab">External Users</div>
            </div>
            <div style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
              <input className="input" style={{ flex: 1 }} placeholder="Search by username (min 4 chars)..." />
              <button className="btn btn-secondary"><Search size={14} />Search</button>
              <button className="btn btn-primary"><Plus size={14} />Add User</button>
            </div>
            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end', marginTop: 20 }}>
              <button className="btn btn-secondary" onClick={() => setShowCreate(false)}>Cancel</button>
              <button className="btn btn-primary" onClick={() => setShowCreate(false)}>Create Customer</button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
