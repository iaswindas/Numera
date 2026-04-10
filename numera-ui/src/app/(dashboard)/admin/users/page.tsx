'use client'

import { useState } from 'react'
import { Plus } from 'lucide-react'
import { useAdminUsers, useCreateAdminUser } from '@/services/adminApi'
import { useToast } from '@/components/ui/Toast'

export default function AdminUsersPage() {
  const { showToast } = useToast()
  const usersQuery = useAdminUsers()
  const createUser = useCreateAdminUser()
  const [showCreate, setShowCreate] = useState(false)
  const [email, setEmail] = useState('')
  const [fullName, setFullName] = useState('')
  const [role, setRole] = useState('ROLE_ANALYST')

  const users = (usersQuery.data as Array<{ id: string; email: string; fullName: string; enabled: boolean; roles: string[]; lastLoginAt: string | null }> | undefined) ?? []

  return (
    <>
      <div className="page-header"><h1>User Management</h1><p>Manage user accounts and role assignments</p></div>
      <div className="toolbar"><div className="toolbar-left" /><div className="toolbar-right"><button className="btn btn-primary" onClick={() => setShowCreate(true)}><Plus size={16} />Add User</button></div></div>

      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <table className="data-table">
          <thead><tr><th>Name</th><th>Email</th><th>Roles</th><th>Status</th><th>Last Login</th></tr></thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.id}>
                <td>{u.fullName}</td>
                <td>{u.email}</td>
                <td>{u.roles.join(', ')}</td>
                <td><span className={`badge-status ${u.enabled ? 'approved' : 'draft'}`}><span className="dot" />{u.enabled ? 'Active' : 'Inactive'}</span></td>
                <td>{u.lastLoginAt ? new Date(u.lastLoginAt).toLocaleString() : '-'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {showCreate ? (
        <div className="modal-overlay">
          <div className="modal" style={{ maxWidth: 520 }}>
            <div className="card-title">Create User</div>
            <div className="input-group" style={{ marginTop: 12 }}><label>Email</label><input className="input" value={email} onChange={(e) => setEmail(e.target.value)} /></div>
            <div className="input-group" style={{ marginTop: 12 }}><label>Full Name</label><input className="input" value={fullName} onChange={(e) => setFullName(e.target.value)} /></div>
            <div className="input-group" style={{ marginTop: 12 }}><label>Role</label><select className="input" value={role} onChange={(e) => setRole(e.target.value)}><option value="ROLE_ANALYST">Analyst</option><option value="ROLE_MANAGER">Manager</option><option value="ROLE_ADMIN">Admin</option></select></div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}>
              <button className="btn btn-secondary" onClick={() => setShowCreate(false)}>Cancel</button>
              <button className="btn btn-primary" onClick={async () => {
                try {
                  await createUser.mutateAsync({ email, fullName, roles: [role], enabled: true })
                  showToast('User created', 'success')
                  setShowCreate(false)
                  setEmail('')
                  setFullName('')
                } catch (error) {
                  const message = typeof error === 'object' && error && 'message' in error ? String((error as { message: string }).message) : 'Failed to create user'
                  showToast(message, 'error')
                }
              }}>Save</button>
            </div>
          </div>
        </div>
      ) : null}
    </>
  )
}
