'use client'

import { useState } from 'react'
import { Plus, Pencil, Trash2 } from 'lucide-react'
import { useAdminZones, useCreateZone, useUpdateZone, useToggleZone, useDeleteZone, type ManagedZone } from '@/services/adminApi'
import { useToast } from '@/components/ui/Toast'

const PRESET_COLORS = ['#3b82f6', '#22c55e', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899', '#14b8a6', '#f97316']

export default function ZoneManagementPage() {
  const { showToast } = useToast()
  const zonesQuery = useAdminZones()
  const createZone = useCreateZone()
  const updateZone = useUpdateZone()
  const toggleZone = useToggleZone()
  const deleteZone = useDeleteZone()

  const zones = zonesQuery.data ?? []

  const [showModal, setShowModal] = useState(false)
  const [editId, setEditId] = useState<string | null>(null)
  const [name, setName] = useState('')
  const [code, setCode] = useState('')
  const [color, setColor] = useState('#6366f1')
  const [description, setDescription] = useState('')
  const [sortOrder, setSortOrder] = useState(0)

  const openCreate = () => {
    setEditId(null)
    setName('')
    setCode('')
    setColor('#6366f1')
    setDescription('')
    setSortOrder(zones.length)
    setShowModal(true)
  }

  const openEdit = (z: ManagedZone) => {
    setEditId(z.id)
    setName(z.name)
    setCode(z.code)
    setColor(z.color)
    setDescription(z.description ?? '')
    setSortOrder(z.sortOrder)
    setShowModal(true)
  }

  const save = async () => {
    const payload = { name, code, color, description: description || null, sortOrder }
    try {
      if (editId) {
        await updateZone.mutateAsync({ id: editId, ...payload })
        showToast('Zone updated', 'success')
      } else {
        await createZone.mutateAsync(payload)
        showToast('Zone created', 'success')
      }
      setShowModal(false)
    } catch (error) {
      const msg = typeof error === 'object' && error && 'message' in error ? String((error as { message: string }).message) : 'Failed to save zone'
      showToast(msg, 'error')
    }
  }

  const onToggle = async (z: ManagedZone) => {
    try {
      await toggleZone.mutateAsync({ id: z.id, active: !z.isActive })
      showToast(`Zone ${!z.isActive ? 'activated' : 'deactivated'}`, 'success')
    } catch {
      showToast('Failed to toggle zone', 'error')
    }
  }

  const onDelete = async (z: ManagedZone) => {
    if (!confirm(`Delete zone "${z.name}"?`)) return
    try {
      await deleteZone.mutateAsync(z.id)
      showToast('Zone deleted', 'success')
    } catch {
      showToast('Failed to delete zone', 'error')
    }
  }

  return (
    <>
      <div className="page-header"><h1>Zone Management</h1><p>Configure document zones for financial spreading</p></div>
      <div className="toolbar">
        <div className="toolbar-left" />
        <div className="toolbar-right">
          <button className="btn btn-primary" onClick={openCreate}><Plus size={16} />Add Zone</button>
        </div>
      </div>

      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <table className="data-table">
          <thead>
            <tr><th>Color</th><th>Name</th><th>Code</th><th>Description</th><th>Order</th><th>Status</th><th>Actions</th></tr>
          </thead>
          <tbody>
            {zonesQuery.isLoading && <tr><td colSpan={7} style={{ color: 'var(--text-muted)' }}>Loading zones...</td></tr>}
            {!zonesQuery.isLoading && zones.length === 0 && <tr><td colSpan={7} style={{ color: 'var(--text-muted)' }}>No zones configured.</td></tr>}
            {zones.map(z => (
              <tr key={z.id}>
                <td><div style={{ width: 20, height: 20, borderRadius: 4, background: z.color }} /></td>
                <td style={{ fontWeight: 600 }}>{z.name}</td>
                <td style={{ fontFamily: 'monospace' }}>{z.code}</td>
                <td style={{ color: 'var(--text-muted)' }}>{z.description || '-'}</td>
                <td>{z.sortOrder}</td>
                <td>
                  <span className={`badge-status ${z.isActive ? 'approved' : 'draft'}`}>
                    <span className="dot" />{z.isActive ? 'Active' : 'Inactive'}
                  </span>
                </td>
                <td style={{ display: 'flex', gap: 4 }}>
                  <button className="btn btn-secondary btn-sm" onClick={() => openEdit(z)}><Pencil size={13} /></button>
                  <button className="btn btn-secondary btn-sm" onClick={() => onToggle(z)}>{z.isActive ? 'Disable' : 'Enable'}</button>
                  <button className="btn btn-secondary btn-sm" onClick={() => onDelete(z)}><Trash2 size={13} /></button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {showModal && (
        <div className="modal-overlay">
          <div className="modal" style={{ maxWidth: 520 }}>
            <div className="card-title">{editId ? 'Edit Zone' : 'Create Zone'}</div>
            <div className="input-group" style={{ marginTop: 12 }}><label>Name</label><input className="input" value={name} onChange={e => setName(e.target.value)} placeholder="Balance Sheet" /></div>
            <div className="input-group" style={{ marginTop: 12 }}><label>Code</label><input className="input" value={code} onChange={e => setCode(e.target.value)} placeholder="BALANCE_SHEET" style={{ fontFamily: 'monospace' }} /></div>
            <div className="input-group" style={{ marginTop: 12 }}>
              <label>Color</label>
              <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                {PRESET_COLORS.map(c => (
                  <div
                    key={c}
                    onClick={() => setColor(c)}
                    style={{
                      width: 28, height: 28, borderRadius: 6, background: c, cursor: 'pointer',
                      border: color === c ? '2px solid var(--text-primary)' : '2px solid transparent',
                    }}
                  />
                ))}
                <input type="color" value={color} onChange={e => setColor(e.target.value)} style={{ width: 32, height: 28, border: 'none', padding: 0, cursor: 'pointer' }} />
              </div>
            </div>
            <div className="input-group" style={{ marginTop: 12 }}><label>Description</label><textarea className="input" value={description} onChange={e => setDescription(e.target.value)} rows={2} /></div>
            <div className="input-group" style={{ marginTop: 12 }}><label>Sort Order</label><input className="input" type="number" value={sortOrder} onChange={e => setSortOrder(Number(e.target.value))} /></div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}>
              <button className="btn btn-secondary" onClick={() => setShowModal(false)}>Cancel</button>
              <button className="btn btn-primary" disabled={!name.trim() || !code.trim()} onClick={save}>Save</button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
