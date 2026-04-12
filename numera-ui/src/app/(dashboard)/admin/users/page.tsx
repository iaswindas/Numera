'use client'

import { useState, useRef } from 'react'
import { Plus, Pencil, Trash2, Upload, Eye, X } from 'lucide-react'
import { useAdminUsers, useCreateAdminUser, useUpdateAdminUser, useDeleteAdminUser, useBulkUploadUsers } from '@/services/adminApi'
import { useToast } from '@/components/ui/Toast'

interface AdminUser {
  id: string
  email: string
  fullName: string
  enabled: boolean
  roles: string[]
  lastLoginAt: string | null
  accountStatus?: string
}

type ModalMode = 'create' | 'edit' | 'detail' | 'bulk' | null

export default function AdminUsersPage() {
  const { showToast } = useToast()
  const usersQuery = useAdminUsers()
  const createUser = useCreateAdminUser()
  const updateUser = useUpdateAdminUser()
  const deleteUser = useDeleteAdminUser()
  const bulkUpload = useBulkUploadUsers()

  const users = (usersQuery.data as AdminUser[] | undefined) ?? []

  const [modalMode, setModalMode] = useState<ModalMode>(null)
  const [selectedUser, setSelectedUser] = useState<AdminUser | null>(null)
  const [email, setEmail] = useState('')
  const [fullName, setFullName] = useState('')
  const [role, setRole] = useState('ROLE_ANALYST')
  const [enabled, setEnabled] = useState(true)
  const [password, setPassword] = useState('')
  const fileRef = useRef<HTMLInputElement>(null)
  const [bulkResult, setBulkResult] = useState<{ created: number; skipped: number; errors: string[] } | null>(null)

  const resetForm = () => {
    setEmail('')
    setFullName('')
    setRole('ROLE_ANALYST')
    setEnabled(true)
    setPassword('')
    setSelectedUser(null)
  }

  const openCreate = () => { resetForm(); setModalMode('create') }

  const openEdit = (u: AdminUser) => {
    setSelectedUser(u)
    setEmail(u.email)
    setFullName(u.fullName)
    setRole(u.roles[0] || 'ROLE_ANALYST')
    setEnabled(u.enabled)
    setPassword('')
    setModalMode('edit')
  }

  const openDetail = (u: AdminUser) => { setSelectedUser(u); setModalMode('detail') }

  const openBulk = () => { setBulkResult(null); setModalMode('bulk') }

  const onSave = async () => {
    try {
      if (modalMode === 'edit' && selectedUser) {
        await updateUser.mutateAsync({
          id: selectedUser.id,
          fullName,
          roles: [role],
          enabled,
          ...(password ? { password } : {}),
        })
        showToast('User updated', 'success')
      } else {
        await createUser.mutateAsync({ email, fullName, roles: [role], enabled })
        showToast('User created', 'success')
      }
      setModalMode(null)
      resetForm()
    } catch (error) {
      const message = typeof error === 'object' && error && 'message' in error ? String((error as { message: string }).message) : 'Failed'
      showToast(message, 'error')
    }
  }

  const onDelete = async (u: AdminUser) => {
    if (!confirm(`Delete user "${u.fullName}"? This action cannot be undone.`)) return
    try {
      await deleteUser.mutateAsync(u.id)
      showToast('User deleted', 'success')
    } catch {
      showToast('Failed to delete user', 'error')
    }
  }

  const onDeactivate = async (u: AdminUser) => {
    try {
      await updateUser.mutateAsync({ id: u.id, fullName: u.fullName, roles: u.roles, enabled: !u.enabled })
      showToast(`User ${u.enabled ? 'deactivated' : 'activated'}`, 'success')
    } catch {
      showToast('Failed to update user', 'error')
    }
  }

  const onBulkUpload = async () => {
    const file = fileRef.current?.files?.[0]
    if (!file) return
    try {
      const result = await bulkUpload.mutateAsync(file)
      setBulkResult(result)
      showToast(`Imported ${result.created} users`, 'success')
    } catch {
      showToast('Bulk upload failed', 'error')
    }
  }

  return (
    <>
      <div className="page-header"><h1>User Management</h1><p>Manage user accounts and role assignments</p></div>
      <div className="toolbar">
        <div className="toolbar-left" />
        <div className="toolbar-right" style={{ display: 'flex', gap: 8 }}>
          <button className="btn btn-secondary" onClick={openBulk}><Upload size={16} /> Bulk Import</button>
          <button className="btn btn-primary" onClick={openCreate}><Plus size={16} /> Add User</button>
        </div>
      </div>

      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <table className="data-table">
          <thead><tr><th>Name</th><th>Email</th><th>Roles</th><th>Status</th><th>Last Login</th><th>Actions</th></tr></thead>
          <tbody>
            {users.map(u => (
              <tr key={u.id}>
                <td style={{ fontWeight: 600 }}>{u.fullName}</td>
                <td>{u.email}</td>
                <td>{u.roles.join(', ')}</td>
                <td>
                  <span className={`badge-status ${u.enabled ? 'approved' : 'draft'}`}>
                    <span className="dot" />{u.enabled ? 'Active' : 'Inactive'}
                  </span>
                </td>
                <td>{u.lastLoginAt ? new Date(u.lastLoginAt).toLocaleString() : '-'}</td>
                <td style={{ display: 'flex', gap: 4 }}>
                  <button className="btn btn-secondary btn-sm" onClick={() => openDetail(u)} title="View"><Eye size={13} /></button>
                  <button className="btn btn-secondary btn-sm" onClick={() => openEdit(u)} title="Edit"><Pencil size={13} /></button>
                  <button className="btn btn-secondary btn-sm" onClick={() => onDeactivate(u)} title={u.enabled ? 'Deactivate' : 'Activate'}>
                    {u.enabled ? 'Deactivate' : 'Activate'}
                  </button>
                  <button className="btn btn-secondary btn-sm" onClick={() => onDelete(u)} title="Delete"><Trash2 size={13} /></button>
                </td>
              </tr>
            ))}
            {users.length === 0 && <tr><td colSpan={6} style={{ color: 'var(--text-muted)' }}>{usersQuery.isLoading ? 'Loading...' : 'No users found.'}</td></tr>}
          </tbody>
        </table>
      </div>

      {/* Create / Edit Modal */}
      {(modalMode === 'create' || modalMode === 'edit') && (
        <div className="modal-overlay">
          <div className="modal" style={{ maxWidth: 520 }}>
            <div className="card-title">{modalMode === 'create' ? 'Create User' : 'Edit User'}</div>
            <div className="input-group" style={{ marginTop: 12 }}>
              <label>Email</label>
              <input className="input" value={email} onChange={e => setEmail(e.target.value)} disabled={modalMode === 'edit'} />
            </div>
            <div className="input-group" style={{ marginTop: 12 }}>
              <label>Full Name</label>
              <input className="input" value={fullName} onChange={e => setFullName(e.target.value)} />
            </div>
            <div className="input-group" style={{ marginTop: 12 }}>
              <label>Role</label>
              <select className="input" value={role} onChange={e => setRole(e.target.value)}>
                <option value="ROLE_ANALYST">Analyst</option>
                <option value="ROLE_MANAGER">Manager</option>
                <option value="ROLE_ADMIN">Admin</option>
              </select>
            </div>
            {modalMode === 'edit' && (
              <div className="input-group" style={{ marginTop: 12 }}>
                <label>New Password (leave blank to keep)</label>
                <input className="input" type="password" value={password} onChange={e => setPassword(e.target.value)} />
              </div>
            )}
            <div className="input-group" style={{ marginTop: 12 }}>
              <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <input type="checkbox" checked={enabled} onChange={e => setEnabled(e.target.checked)} /> Active
              </label>
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}>
              <button className="btn btn-secondary" onClick={() => setModalMode(null)}>Cancel</button>
              <button className="btn btn-primary" onClick={onSave}>Save</button>
            </div>
          </div>
        </div>
      )}

      {/* Detail View */}
      {modalMode === 'detail' && selectedUser && (
        <div className="modal-overlay">
          <div className="modal" style={{ maxWidth: 520 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div className="card-title">User Details</div>
              <button className="btn btn-secondary btn-sm" onClick={() => setModalMode(null)}><X size={14} /></button>
            </div>
            <div style={{ marginTop: 16 }}>
              <div style={{ display: 'grid', gridTemplateColumns: '120px 1fr', gap: '8px 16px', fontSize: 13 }}>
                <span style={{ color: 'var(--text-muted)' }}>Name</span><span style={{ fontWeight: 600 }}>{selectedUser.fullName}</span>
                <span style={{ color: 'var(--text-muted)' }}>Email</span><span>{selectedUser.email}</span>
                <span style={{ color: 'var(--text-muted)' }}>Roles</span><span>{selectedUser.roles.join(', ')}</span>
                <span style={{ color: 'var(--text-muted)' }}>Status</span>
                <span>
                  <span className={`badge-status ${selectedUser.enabled ? 'approved' : 'draft'}`}>
                    <span className="dot" />{selectedUser.enabled ? 'Active' : 'Inactive'}
                  </span>
                </span>
                <span style={{ color: 'var(--text-muted)' }}>Last Login</span>
                <span>{selectedUser.lastLoginAt ? new Date(selectedUser.lastLoginAt).toLocaleString() : 'Never'}</span>
              </div>
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 20 }}>
              <button className="btn btn-secondary" onClick={() => { setModalMode(null); openEdit(selectedUser) }}>Edit</button>
              <button className="btn btn-secondary" onClick={() => setModalMode(null)}>Close</button>
            </div>
          </div>
        </div>
      )}

      {/* Bulk Upload Modal */}
      {modalMode === 'bulk' && (
        <div className="modal-overlay">
          <div className="modal" style={{ maxWidth: 520 }}>
            <div className="card-title">Bulk Import Users</div>
            <div style={{ marginTop: 12, fontSize: 13, color: 'var(--text-muted)' }}>
              Upload a CSV file with columns: <code>email, fullname, role</code>
            </div>
            <div className="input-group" style={{ marginTop: 12 }}>
              <label>CSV File</label>
              <input ref={fileRef} type="file" accept=".csv" className="input" />
            </div>
            {bulkResult && (
              <div style={{ marginTop: 12, padding: 12, background: 'var(--surface-hover)', borderRadius: 8, fontSize: 13 }}>
                <div><strong>Created:</strong> {bulkResult.created}</div>
                <div><strong>Skipped:</strong> {bulkResult.skipped}</div>
                {bulkResult.errors.length > 0 && (
                  <div style={{ marginTop: 8 }}>
                    <strong>Errors:</strong>
                    <ul style={{ margin: '4px 0 0 16px', color: 'var(--danger)' }}>
                      {bulkResult.errors.slice(0, 10).map((e, i) => <li key={i}>{e}</li>)}
                      {bulkResult.errors.length > 10 && <li>...and {bulkResult.errors.length - 10} more</li>}
                    </ul>
                  </div>
                )}
              </div>
            )}
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}>
              <button className="btn btn-secondary" onClick={() => setModalMode(null)}>Close</button>
              <button className="btn btn-primary" onClick={onBulkUpload} disabled={bulkUpload.isPending}>
                {bulkUpload.isPending ? 'Uploading...' : 'Upload'}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
