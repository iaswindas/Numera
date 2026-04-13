'use client'

import { useState } from 'react'
import { Plus, Trash2, Copy, Edit2, Filter, X } from 'lucide-react'
import { useCreateEmailTemplate, useDeleteEmailTemplate, useEmailTemplates, useUpdateEmailTemplate, useDuplicateEmailTemplate } from '@/services/covenantApi'
import { useAuthStore } from '@/stores/authStore'
import { useToast } from '@/components/ui/Toast'
import TemplateEditor from '@/components/covenant/TemplateEditor'

type EmailTemplate = {
  id: string
  name: string
  templateCategory: string | null
  covenantType: string | null
  subject: string | null
  bodyHtml: string | null
  isActive: boolean
  createdAt: string
}

export default function EmailTemplatesPage() {
  const { user } = useAuthStore()
  const { showToast } = useToast()
  const templatesQuery = useEmailTemplates()
  const createTemplate = useCreateEmailTemplate()
  const updateTemplate = useUpdateEmailTemplate()
  const deleteTemplate = useDeleteEmailTemplate()
  const duplicateTemplate = useDuplicateEmailTemplate()

  const [showEditor, setShowEditor] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [name, setName] = useState('')
  const [subject, setSubject] = useState('')
  const [bodyHtml, setBodyHtml] = useState('<p>Hello {{CUSTOMER_NAME}},</p><p></p><p>Regarding covenant <b>{{COVENANT_NAME}}</b> for period {{PERIOD}}.</p>')
  const [covenantType, setCovenantType] = useState<'FINANCIAL' | 'NON_FINANCIAL'>('FINANCIAL')
  const [templateCategory, setTemplateCategory] = useState<'WAIVER' | 'BREACH' | 'REMINDER' | 'GENERAL'>('WAIVER')
  const [categoryFilter, setCategoryFilter] = useState<string>('ALL')

  const allTemplates = (templatesQuery.data as EmailTemplate[] | undefined) ?? []
  const templates = categoryFilter === 'ALL'
    ? allTemplates
    : allTemplates.filter((t) => t.covenantType === categoryFilter || t.templateCategory === categoryFilter)

  const resetForm = () => {
    setEditingId(null)
    setName('')
    setSubject('')
    setBodyHtml('<p>Hello {{CUSTOMER_NAME}},</p><p></p><p>Regarding covenant <b>{{COVENANT_NAME}}</b> for period {{PERIOD}}.</p>')
    setCovenantType('FINANCIAL')
    setTemplateCategory('WAIVER')
  }

  const openCreate = () => {
    resetForm()
    setShowEditor(true)
  }

  const openEdit = (t: EmailTemplate) => {
    setEditingId(t.id)
    setName(t.name)
    setSubject(t.subject ?? '')
    setBodyHtml(t.bodyHtml ?? '')
    setCovenantType((t.covenantType as 'FINANCIAL' | 'NON_FINANCIAL') ?? 'FINANCIAL')
    setTemplateCategory((t.templateCategory as 'WAIVER' | 'BREACH' | 'REMINDER' | 'GENERAL') ?? 'WAIVER')
    setShowEditor(true)
  }

  const onDuplicate = async (id: string) => {
    try {
      await duplicateTemplate.mutateAsync(id)
      showToast('Template duplicated', 'success')
    } catch {
      showToast('Failed to duplicate template', 'error')
    }
  }

  const onSave = async () => {
    try {
      if (editingId) {
        await updateTemplate.mutateAsync({
          id: editingId,
          payload: { name, subject, bodyHtml, covenantType, templateCategory },
        })
        showToast('Template updated', 'success')
      } else {
        await createTemplate.mutateAsync({
          actorId: user?.id,
          payload: { name, covenantType, templateCategory, subject, bodyHtml },
        })
        showToast('Template created', 'success')
      }
      setShowEditor(false)
      resetForm()
    } catch (error) {
      const message = typeof error === 'object' && error && 'message' in error ? String((error as { message: string }).message) : 'Failed to save template'
      showToast(message, 'error')
    }
  }

  return (
    <>
      <div className="page-header">
        <h1>Email Template & Signature Management</h1>
        <p>Configure waiver and breach letter templates with dynamic field tags</p>
      </div>

      <div className="toolbar">
        <div className="toolbar-left" style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <Filter size={14} style={{ color: 'var(--text-muted)' }} />
          <select
            className="input"
            style={{ width: 180, fontSize: 12 }}
            value={categoryFilter}
            onChange={(e) => setCategoryFilter(e.target.value)}
          >
            <option value="ALL">All Categories</option>
            <option value="FINANCIAL">Financial</option>
            <option value="NON_FINANCIAL">Non-Financial</option>
            <option value="WAIVER">Waiver</option>
            <option value="BREACH">Breach</option>
            <option value="REMINDER">Reminder</option>
          </select>
        </div>
        <div className="toolbar-right">
          <button className="btn btn-primary" onClick={openCreate}><Plus size={14} />Add Template</button>
        </div>
      </div>

      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <table className="data-table">
          <thead>
            <tr><th>Name</th><th>Type</th><th>Category</th><th>Subject</th><th>Status</th><th>Created</th><th>Actions</th></tr>
          </thead>
          <tbody>
            {templates.map((t) => (
              <tr key={t.id}>
                <td style={{ fontWeight: 600 }}>{t.name}</td>
                <td>{t.covenantType ?? '-'}</td>
                <td>{t.templateCategory ?? '-'}</td>
                <td style={{ maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{t.subject ?? '-'}</td>
                <td><span className={`badge-status ${t.isActive ? 'approved' : 'draft'}`}><span className="dot" />{t.isActive ? 'Active' : 'Inactive'}</span></td>
                <td>{new Date(t.createdAt).toLocaleString()}</td>
                <td>
                  <div style={{ display: 'flex', gap: 4 }}>
                    <button className="btn btn-ghost btn-sm" title="Edit" onClick={() => openEdit(t)}><Edit2 size={14} /></button>
                    <button className="btn btn-ghost btn-sm" title="Duplicate" onClick={() => onDuplicate(t.id)}><Copy size={14} /></button>
                    <button
                      className="btn btn-ghost btn-sm"
                      style={{ color: 'var(--danger)' }}
                      title="Deactivate"
                      onClick={async () => {
                        await deleteTemplate.mutateAsync(t.id)
                        showToast('Template deactivated', 'success')
                      }}
                    >
                      <Trash2 size={14} />
                    </button>
                  </div>
                </td>
              </tr>
            ))}
            {templates.length === 0 ? <tr><td colSpan={7} style={{ color: 'var(--text-muted)' }}>No templates found.</td></tr> : null}
          </tbody>
        </table>
      </div>

      {/* Template editor modal */}
      {showEditor && (
        <div className="modal-overlay">
          <div className="modal" style={{ maxWidth: 960, maxHeight: '90vh', overflow: 'auto' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
              <div className="card-title">{editingId ? 'Edit Template' : 'Create Template'}</div>
              <button className="btn btn-ghost btn-sm" onClick={() => { setShowEditor(false); resetForm() }}><X size={16} /></button>
            </div>

            <div className="grid-2" style={{ marginBottom: 12 }}>
              <div className="input-group"><label>Name</label><input className="input" value={name} onChange={(e) => setName(e.target.value)} placeholder="Template name" /></div>
              <div className="input-group"><label>Subject</label><input className="input" value={subject} onChange={(e) => setSubject(e.target.value)} placeholder="Email subject line" /></div>
            </div>

            <div className="grid-2" style={{ marginBottom: 12 }}>
              <div className="input-group">
                <label>Covenant Type</label>
                <select className="input" value={covenantType} onChange={(e) => setCovenantType(e.target.value as 'FINANCIAL' | 'NON_FINANCIAL')}>
                  <option value="FINANCIAL">Financial</option>
                  <option value="NON_FINANCIAL">Non-Financial</option>
                </select>
              </div>
              <div className="input-group">
                <label>Category</label>
                <select className="input" value={templateCategory} onChange={(e) => setTemplateCategory(e.target.value as 'WAIVER' | 'BREACH' | 'REMINDER' | 'GENERAL')}>
                  <option value="WAIVER">Waiver</option>
                  <option value="BREACH">Breach</option>
                  <option value="REMINDER">Reminder</option>
                  <option value="GENERAL">General</option>
                </select>
              </div>
            </div>

            <div className="input-group" style={{ marginBottom: 16 }}>
              <label>Body</label>
              <TemplateEditor
                value={bodyHtml}
                onChange={setBodyHtml}
                category={covenantType === 'FINANCIAL' ? 'Financial' : 'Non-Financial'}
              />
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
              <button className="btn btn-secondary" onClick={() => { setShowEditor(false); resetForm() }}>Cancel</button>
              <button className="btn btn-primary" onClick={onSave} disabled={!name.trim() || !subject.trim()}>
                {editingId ? 'Save Changes' : 'Create Template'}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
}
