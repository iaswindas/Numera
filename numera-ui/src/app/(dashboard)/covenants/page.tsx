'use client'

import { useMemo, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Send } from 'lucide-react'
import { useCovenantCustomers, useCovenantDefinitions, useMonitoringItems, useWaiverRequest } from '@/services/covenantApi'
import { useAuthStore } from '@/stores/authStore'
import { useToast } from '@/components/ui/Toast'
import { useWebSocketSubscription } from '@/hooks/useWebSocket'

export default function CovenantItemsPage() {
  const { user } = useAuthStore()
  const { showToast } = useToast()
  const queryClient = useQueryClient()
  const [selectedCustomerId, setSelectedCustomerId] = useState('')

  const customersQuery = useCovenantCustomers()
  const monitoringQuery = useMonitoringItems()
  const definitionsQuery = useCovenantDefinitions(selectedCustomerId || undefined)
  const waiverMutation = useWaiverRequest()

  const customers = (customersQuery.data as Array<{ id: string; customerName: string }> | undefined) ?? []
  const monitoring = (monitoringQuery.data as Array<{ id: string; covenantName: string; status: string; periodEnd: string; dueDate: string; breachProbability?: number }> | undefined) ?? []
  const definitions = (definitionsQuery.data as Array<{ id: string; name: string; frequency: string; covenantType: string; isActive: boolean }> | undefined) ?? []

  useWebSocketSubscription(user?.tenantId ? `/topic/tenant/${user.tenantId}/covenants` : null, () => {
    void queryClient.invalidateQueries({ queryKey: ['covenant'] })
  })

  const breached = useMemo(() => monitoring.filter((m) => m.status === 'BREACHED'), [monitoring])

  const onWaive = async (monitoringItemId: string) => {
    try {
      await waiverMutation.mutateAsync({
        actorId: user?.id ?? '',
        payload: {
          monitoringItemId,
          waiverType: 'INSTANCE',
          letterType: 'WAIVE',
          signatureId: null,
          emailTemplateId: null,
          deliveryMethod: 'DOWNLOAD',
          comments: 'Waived from UI',
        },
      })
      showToast('Waiver processed', 'success')
    } catch (error) {
      const message = typeof error === 'object' && error && 'message' in error ? String((error as { message: string }).message) : 'Failed to process waiver'
      showToast(message, 'error')
    }
  }

  return (
    <>
      <div className="page-header">
        <h1>Covenant Monitoring Items</h1>
        <p>Track and manage financial and non-financial covenant obligations</p>
      </div>

      <div className="grid-2">
        <div className="card">
          <div className="card-header">
            <div className="card-title">Covenant Customers</div>
          </div>
          <select className="input" value={selectedCustomerId} onChange={(e) => setSelectedCustomerId(e.target.value)}>
            <option value="">Select customer</option>
            {customers.map((c) => <option key={c.id} value={c.id}>{c.customerName}</option>)}
          </select>
          <div style={{ marginTop: 12, fontSize: 12, color: 'var(--text-muted)' }}>Definitions for selected customer</div>
          <div style={{ marginTop: 8, display: 'flex', flexDirection: 'column', gap: 8 }}>
            {definitions.map((d) => (
              <div key={d.id} className="card" style={{ padding: 10 }}>
                <div style={{ fontWeight: 600 }}>{d.name}</div>
                <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>{d.frequency} | {d.covenantType}</div>
              </div>
            ))}
            {selectedCustomerId && definitions.length === 0 ? <span style={{ color: 'var(--text-muted)', fontSize: 12 }}>No definitions found.</span> : null}
          </div>
        </div>

        <div className="card">
          <div className="card-header">
            <div className="card-title">Breached Items</div>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {breached.map((item) => (
              <div key={item.id} className="card" style={{ padding: 10 }}>
                <div style={{ fontWeight: 600 }}>{item.covenantName}</div>
                <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
                  Due: {item.dueDate} | Period: {item.periodEnd} | Breach Probability: {item.breachProbability ?? 0}
                </div>
                <div style={{ marginTop: 8 }}>
                  <button className="btn btn-danger btn-sm" onClick={() => onWaive(item.id)}>
                    <Send size={13} />Waive / Not Waive
                  </button>
                </div>
              </div>
            ))}
            {breached.length === 0 ? <span style={{ color: 'var(--text-muted)', fontSize: 12 }}>No breached items currently.</span> : null}
          </div>
        </div>
      </div>

      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <table className="data-table">
          <thead>
            <tr>
              <th>Covenant</th>
              <th>Status</th>
              <th>Period End</th>
              <th>Due Date</th>
              <th>Breach Probability</th>
            </tr>
          </thead>
          <tbody>
            {monitoring.map((item) => (
              <tr key={item.id}>
                <td>{item.covenantName}</td>
                <td><span className={`badge-status ${item.status.toLowerCase()}`}><span className="dot" />{item.status}</span></td>
                <td>{item.periodEnd}</td>
                <td>{item.dueDate}</td>
                <td>{item.breachProbability ?? '-'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  )
}
