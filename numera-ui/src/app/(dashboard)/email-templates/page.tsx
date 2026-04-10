'use client'

import { useState } from 'react'
import { Plus, Trash2 } from 'lucide-react'
import { useCreateEmailTemplate, useDeleteEmailTemplate, useEmailTemplates } from '@/services/covenantApi'
import { useAuthStore } from '@/stores/authStore'
import { useToast } from '@/components/ui/Toast'

export default function EmailTemplatesPage() {
  const { user } = useAuthStore()
  const { showToast } = useToast()
  const templatesQuery = useEmailTemplates()
  const createTemplate = useCreateEmailTemplate()
  const deleteTemplate = useDeleteEmailTemplate()

  const [showCreate, setShowCreate] = useState(false)
  const [name, setName] = useState('')
  const [subject, setSubject] = useState('')
  const [bodyHtml, setBodyHtml] = useState('<p>Hello {{CUSTOMER_NAME}}</p>')

  const templates = (templatesQuery.data as Array<{ id: string; name: string; templateCategory: string | null; subject: string | null; isActive: boolean; createdAt: string }> | undefined) ?? []

  return (
    <>
      <div className="page-header">
        <h1>Email Template & Signature Management</h1>
        <p>Configure waiver and breach letter templates with dynamic field tags</p>
      </div>

      <div className="toolbar">
        <div className="toolbar-left" />
        <div className="toolbar-right">
          <button className="btn btn-primary" onClick={() => setShowCreate(true)}><Plus size={14} />Add Template</button>
        </div>
      </div>

      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <table className="data-table">
          <thead>
            <tr><th>Name</th><th>Category</th><th>Subject</th><th>Status</th><th>Created</th><th>Actions</th></tr>
          </thead>
          <tbody>
            {templates.map((t) => (
              <tr key={t.id}>
                <td>{t.name}</td>
                <td>{t.templateCategory ?? '-'}</td>
                <td>{t.subject ?? '-'}</td>
                <td><span className={`badge-status ${t.isActive ? 'approved' : 'draft'}`}><span className="dot" />{t.isActive ? 'Active' : 'Inactive'}</span></td>
                <td>{new Date(t.createdAt).toLocaleString()}</td>
                <td>
                  <button
                    className="btn btn-ghost btn-sm"
                    style={{ color: 'var(--danger)' }}
                    onClick={async () => {
                      await deleteTemplate.mutateAsync(t.id)
                      showToast('Template deactivated', 'success')
                    }}
                  >
                    <Trash2 size={14} />
                  </button>
                </td>
              </tr>
            ))}
            {templates.length === 0 ? <tr><td colSpan={6} style={{ color: 'var(--text-muted)' }}>No templates found.</td></tr> : null}
          </tbody>
        </table>
      </div>

      {showCreate ? (
        <div className="modal-overlay">
          <div className="modal" style={{ maxWidth: 640 }}>
            <div className="card-title">Create Template</div>
            <div className="input-group" style={{ marginTop: 12 }}><label>Name</label><input className="input" value={name} onChange={(e) => setName(e.target.value)} /></div>
            <div className="input-group" style={{ marginTop: 12 }}><label>Subject</label><input className="input" value={subject} onChange={(e) => setSubject(e.target.value)} /></div>
            <div className="input-group" style={{ marginTop: 12 }}><label>Body HTML</label><textarea className="input" rows={8} value={bodyHtml} onChange={(e) => setBodyHtml(e.target.value)} /></div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}>
              <button className="btn btn-secondary" onClick={() => setShowCreate(false)}>Cancel</button>
              <button
                className="btn btn-primary"
                onClick={async () => {
                  try {
                    await createTemplate.mutateAsync({
                      actorId: user?.id,
                      payload: {
                        name,
                        covenantType: 'FINANCIAL',
                        templateCategory: 'WAIVER',
                        subject,
                        bodyHtml,
                      },
                    })
                    setShowCreate(false)
                    setName('')
                    setSubject('')
                    setBodyHtml('<p>Hello {{CUSTOMER_NAME}}</p>')
                    showToast('Template created', 'success')
                  } catch (error) {
                    const message = typeof error === 'object' && error && 'message' in error ? String((error as { message: string }).message) : 'Failed to create template'
                    showToast(message, 'error')
                  }
                }}
              >
                Save
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </>
  )
}
