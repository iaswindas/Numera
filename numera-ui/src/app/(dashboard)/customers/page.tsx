'use client'

import { useMemo, useState } from 'react'
import Link from 'next/link'
import { Building2, Plus, Search } from 'lucide-react'
import { useCreateCustomer, useCustomers } from '@/services/customerApi'
import { useToast } from '@/components/ui/Toast'

export default function CustomersPage() {
  const { showToast } = useToast()
  const [query, setQuery] = useState('')
  const [showCreate, setShowCreate] = useState(false)
  const [customerCode, setCustomerCode] = useState('')
  const [name, setName] = useState('')
  const [industry, setIndustry] = useState('')
  const [country, setCountry] = useState('AE')
  const [relationshipManager, setRelationshipManager] = useState('')

  const customersQuery = useCustomers({ query })
  const createCustomer = useCreateCustomer()

  const customers = useMemo(() => customersQuery.data ?? [], [customersQuery.data])

  const onCreate = async () => {
    if (!customerCode || !name) {
      showToast('Customer code and name are required', 'error')
      return
    }
    try {
      await createCustomer.mutateAsync({
        customerCode,
        name,
        industry: industry || undefined,
        country: country || undefined,
        relationshipManager: relationshipManager || undefined,
      })
      showToast('Customer created', 'success')
      setShowCreate(false)
      setCustomerCode('')
      setName('')
      setIndustry('')
      setRelationshipManager('')
    } catch (error) {
      const message = typeof error === 'object' && error && 'message' in error ? String((error as { message: string }).message) : 'Failed to create customer'
      showToast(message, 'error')
    }
  }

  return (
    <>
      <div className="page-header">
        <h1>Search Customer</h1>
        <p>Search and manage customer records for financial spreading</p>
      </div>

      <div className="toolbar">
        <div className="toolbar-left">
          <div className="search-bar">
            <Search size={16} />
            <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search by customer name or code..." />
          </div>
        </div>
        <div className="toolbar-right">
          <button className="btn btn-primary" onClick={() => setShowCreate(true)}>
            <Plus size={16} />Add Customer
          </button>
        </div>
      </div>

      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <table className="data-table">
          <thead>
            <tr>
              <th>Customer Name</th>
              <th>Customer Code</th>
              <th>Industry</th>
              <th>Country</th>
              <th>Relationship Manager</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {customers.map((c) => (
              <tr key={c.id}>
                <td>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <Building2 size={16} style={{ color: 'var(--accent)', flexShrink: 0 }} />
                    <span style={{ fontWeight: 600 }}>{c.name}</span>
                  </div>
                </td>
                <td style={{ fontFamily: 'monospace', fontSize: 12, color: 'var(--text-secondary)' }}>{c.customerCode}</td>
                <td>{c.industry ?? '-'}</td>
                <td>{c.country ?? '-'}</td>
                <td>{c.relationshipManager ?? '-'}</td>
                <td>
                  <Link href={`/customers/${c.id}/items`} className="btn btn-ghost btn-sm">View Items</Link>
                </td>
              </tr>
            ))}
            {!customersQuery.isLoading && customers.length === 0 ? (
              <tr>
                <td colSpan={6} style={{ color: 'var(--text-muted)' }}>No customers found.</td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </div>

      {showCreate ? (
        <div className="modal-overlay">
          <div className="modal" style={{ maxWidth: 520 }}>
            <div className="card-title">Create Customer</div>
            <div className="grid-2" style={{ marginTop: 12 }}>
              <div className="input-group"><label>Customer Code</label><input className="input" value={customerCode} onChange={(e) => setCustomerCode(e.target.value)} /></div>
              <div className="input-group"><label>Name</label><input className="input" value={name} onChange={(e) => setName(e.target.value)} /></div>
              <div className="input-group"><label>Industry</label><input className="input" value={industry} onChange={(e) => setIndustry(e.target.value)} /></div>
              <div className="input-group"><label>Country</label><input className="input" value={country} onChange={(e) => setCountry(e.target.value)} /></div>
              <div className="input-group" style={{ gridColumn: '1 / span 2' }}><label>Relationship Manager</label><input className="input" value={relationshipManager} onChange={(e) => setRelationshipManager(e.target.value)} /></div>
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}>
              <button className="btn btn-secondary" onClick={() => setShowCreate(false)}>Cancel</button>
              <button className="btn btn-primary" onClick={onCreate} disabled={createCustomer.isPending}>{createCustomer.isPending ? 'Saving...' : 'Save'}</button>
            </div>
          </div>
        </div>
      ) : null}
    </>
  )
}
