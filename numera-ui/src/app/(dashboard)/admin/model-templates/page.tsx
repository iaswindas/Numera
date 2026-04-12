'use client'

import { useState } from 'react'
import { Plus, Pencil, Trash2, ChevronDown, ChevronRight, GripVertical } from 'lucide-react'
import {
  useModelTemplates,
  useCreateModelTemplate,
  useUpdateModelTemplate,
  type ModelTemplate,
  type TemplateLineItem,
} from '@/services/adminApi'
import { useToast } from '@/components/ui/Toast'

type ModalMode = 'create' | 'edit' | 'items' | null

interface LineItemDraft {
  itemCode: string
  label: string
  zone: string
  category: string
  itemType: string
  formula: string
  required: boolean
  isTotal: boolean
  indentLevel: number
  signConvention: string
  sortOrder: number
  aliases: string[]
}

const EMPTY_LINE_ITEM: LineItemDraft = {
  itemCode: '', label: '', zone: 'INCOME_STATEMENT', category: '', itemType: 'INPUT',
  formula: '', required: false, isTotal: false, indentLevel: 0, signConvention: 'NATURAL',
  sortOrder: 0, aliases: [],
}

const ZONES = ['BALANCE_SHEET', 'INCOME_STATEMENT', 'CASH_FLOW', 'NOTES', 'RATIOS']
const ITEM_TYPES = ['INPUT', 'CALCULATED', 'TOTAL', 'SECTION_HEADER', 'MEMO']

export default function ModelTemplatesPage() {
  const { showToast } = useToast()
  const templatesQuery = useModelTemplates()
  const createTemplate = useCreateModelTemplate()
  const updateTemplate = useUpdateModelTemplate()

  const templates = templatesQuery.data ?? []
  const [modalMode, setModalMode] = useState<ModalMode>(null)
  const [editId, setEditId] = useState('')
  const [name, setName] = useState('')
  const [version, setVersion] = useState(1)
  const [currency, setCurrency] = useState('USD')
  const [active, setActive] = useState(true)
  const [lineItems, setLineItems] = useState<LineItemDraft[]>([])
  const [expandedTemplate, setExpandedTemplate] = useState<string | null>(null)
  const [editingItem, setEditingItem] = useState<LineItemDraft | null>(null)
  const [editingItemIdx, setEditingItemIdx] = useState(-1)

  const openCreate = () => {
    setModalMode('create')
    setEditId('')
    setName('')
    setVersion(1)
    setCurrency('USD')
    setActive(true)
    setLineItems([])
  }

  const openEdit = (t: ModelTemplate) => {
    setModalMode('edit')
    setEditId(t.id)
    setName(t.name)
    setVersion(t.version)
    setCurrency(t.currency)
    setActive(t.active)
    setLineItems(t.lineItems.map(li => ({
      itemCode: li.itemCode, label: li.label, zone: li.zone, category: li.category ?? '',
      itemType: li.itemType, formula: li.formula ?? '', required: li.required, isTotal: li.isTotal,
      indentLevel: li.indentLevel, signConvention: li.signConvention, sortOrder: li.sortOrder,
      aliases: li.aliases ?? [],
    })))
  }

  const openLineItemEditor = (t: ModelTemplate) => {
    setModalMode('items')
    setEditId(t.id)
    setName(t.name)
    setLineItems(t.lineItems.map(li => ({
      itemCode: li.itemCode, label: li.label, zone: li.zone, category: li.category ?? '',
      itemType: li.itemType, formula: li.formula ?? '', required: li.required, isTotal: li.isTotal,
      indentLevel: li.indentLevel, signConvention: li.signConvention, sortOrder: li.sortOrder,
      aliases: li.aliases ?? [],
    })))
  }

  const saveTemplate = async () => {
    const payload = { name, version, currency, active, lineItems, validations: [] }
    try {
      if (modalMode === 'edit' || modalMode === 'items') {
        await updateTemplate.mutateAsync({ id: editId, ...payload })
        showToast('Template updated', 'success')
      } else {
        await createTemplate.mutateAsync(payload)
        showToast('Template created', 'success')
      }
      setModalMode(null)
    } catch (error) {
      const msg = typeof error === 'object' && error && 'message' in error ? String((error as { message: string }).message) : 'Failed'
      showToast(msg, 'error')
    }
  }

  const addLineItem = () => {
    setEditingItem({ ...EMPTY_LINE_ITEM, sortOrder: lineItems.length })
    setEditingItemIdx(-1)
  }

  const editLineItem = (idx: number) => {
    setEditingItem({ ...lineItems[idx] })
    setEditingItemIdx(idx)
  }

  const saveLineItem = () => {
    if (!editingItem) return
    if (editingItemIdx >= 0) {
      setLineItems(prev => prev.map((li, i) => i === editingItemIdx ? editingItem : li))
    } else {
      setLineItems(prev => [...prev, editingItem])
    }
    setEditingItem(null)
    setEditingItemIdx(-1)
  }

  const removeLineItem = (idx: number) => {
    setLineItems(prev => prev.filter((_, i) => i !== idx))
  }

  const moveItem = (idx: number, dir: -1 | 1) => {
    const target = idx + dir
    if (target < 0 || target >= lineItems.length) return
    setLineItems(prev => {
      const arr = [...prev]
      const tmp = arr[idx]
      arr[idx] = arr[target]
      arr[target] = tmp
      return arr.map((li, i) => ({ ...li, sortOrder: i }))
    })
  }

  return (
    <>
      <div className="page-header"><h1>Model Template Management</h1><p>Configure financial model templates and line items</p></div>
      <div className="toolbar">
        <div className="toolbar-left" />
        <div className="toolbar-right">
          <button className="btn btn-primary" onClick={openCreate}><Plus size={16} />Create Template</button>
        </div>
      </div>

      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <table className="data-table">
          <thead>
            <tr><th style={{ width: 30 }} /><th>Name</th><th>Version</th><th>Currency</th><th>Line Items</th><th>Status</th><th>Actions</th></tr>
          </thead>
          <tbody>
            {templates.length === 0 && (
              <tr><td colSpan={7} style={{ color: 'var(--text-muted)' }}>{templatesQuery.isLoading ? 'Loading...' : 'No templates found.'}</td></tr>
            )}
            {templates.map(t => (
              <>
                <tr key={t.id}>
                  <td style={{ cursor: 'pointer' }} onClick={() => setExpandedTemplate(expandedTemplate === t.id ? null : t.id)}>
                    {expandedTemplate === t.id ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
                  </td>
                  <td style={{ fontWeight: 600 }}>{t.name}</td>
                  <td>v{t.version}</td>
                  <td>{t.currency}</td>
                  <td>{t.lineItems?.length ?? 0}</td>
                  <td>
                    <span className={`badge-status ${t.active ? 'approved' : 'draft'}`}>
                      <span className="dot" />{t.active ? 'Active' : 'Draft'}
                    </span>
                  </td>
                  <td style={{ display: 'flex', gap: 4 }}>
                    <button className="btn btn-secondary btn-sm" onClick={() => openEdit(t)}><Pencil size={13} /> Edit</button>
                    <button className="btn btn-secondary btn-sm" onClick={() => openLineItemEditor(t)}>Line Items</button>
                  </td>
                </tr>
                {expandedTemplate === t.id && t.lineItems?.length > 0 && (
                  <tr key={`${t.id}-exp`}>
                    <td colSpan={7} style={{ padding: '0 16px 12px 40px', background: 'var(--surface-hover)' }}>
                      <table style={{ width: '100%', fontSize: 12 }}>
                        <thead><tr><th>Code</th><th>Label</th><th>Zone</th><th>Type</th><th>Formula</th></tr></thead>
                        <tbody>
                          {t.lineItems.map((li: TemplateLineItem) => (
                            <tr key={li.id}>
                              <td style={{ fontFamily: 'monospace' }}>{li.itemCode}</td>
                              <td style={{ paddingLeft: li.indentLevel * 16 }}>{li.label}</td>
                              <td>{li.zone}</td>
                              <td>{li.itemType}</td>
                              <td style={{ fontFamily: 'monospace', color: 'var(--text-muted)' }}>{li.formula || '-'}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </td>
                  </tr>
                )}
              </>
            ))}
          </tbody>
        </table>
      </div>

      {/* Template Create/Edit Modal */}
      {(modalMode === 'create' || modalMode === 'edit') && (
        <div className="modal-overlay">
          <div className="modal" style={{ maxWidth: 560 }}>
            <div className="card-title">{modalMode === 'create' ? 'Create Template' : 'Edit Template'}</div>
            <div className="input-group" style={{ marginTop: 12 }}><label>Name</label><input className="input" value={name} onChange={e => setName(e.target.value)} /></div>
            <div className="grid-2" style={{ marginTop: 12 }}>
              <div className="input-group"><label>Version</label><input className="input" type="number" value={version} onChange={e => setVersion(Number(e.target.value))} /></div>
              <div className="input-group"><label>Currency</label><input className="input" value={currency} onChange={e => setCurrency(e.target.value)} /></div>
            </div>
            <div className="input-group" style={{ marginTop: 12 }}>
              <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <input type="checkbox" checked={active} onChange={e => setActive(e.target.checked)} /> Active
              </label>
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}>
              <button className="btn btn-secondary" onClick={() => setModalMode(null)}>Cancel</button>
              <button className="btn btn-primary" disabled={!name.trim()} onClick={saveTemplate}>Save</button>
            </div>
          </div>
        </div>
      )}

      {/* Line Item Editor Modal */}
      {modalMode === 'items' && (
        <div className="modal-overlay">
          <div className="modal" style={{ maxWidth: 900, maxHeight: '90vh', overflow: 'auto' }}>
            <div className="card-title">Line Items — {name}</div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 8 }}>
              <button className="btn btn-primary btn-sm" onClick={addLineItem}><Plus size={14} /> Add Item</button>
            </div>
            <table className="data-table" style={{ marginTop: 8, fontSize: 12 }}>
              <thead><tr><th style={{ width: 30 }} /><th>Code</th><th>Label</th><th>Zone</th><th>Type</th><th>Formula</th><th>Actions</th></tr></thead>
              <tbody>
                {lineItems.map((li, idx) => (
                  <tr key={idx}>
                    <td>
                      <span style={{ cursor: 'grab', display: 'flex', flexDirection: 'column', gap: 2 }}>
                        <button className="btn btn-secondary btn-sm" style={{ padding: '0 2px', lineHeight: 1 }} onClick={() => moveItem(idx, -1)}>&uarr;</button>
                        <button className="btn btn-secondary btn-sm" style={{ padding: '0 2px', lineHeight: 1 }} onClick={() => moveItem(idx, 1)}>&darr;</button>
                      </span>
                    </td>
                    <td style={{ fontFamily: 'monospace' }}>{li.itemCode}</td>
                    <td style={{ paddingLeft: li.indentLevel * 12 }}>{li.label}</td>
                    <td>{li.zone}</td>
                    <td>{li.itemType}</td>
                    <td style={{ fontFamily: 'monospace', color: 'var(--text-muted)' }}>{li.formula || '-'}</td>
                    <td style={{ display: 'flex', gap: 4 }}>
                      <button className="btn btn-secondary btn-sm" onClick={() => editLineItem(idx)}><Pencil size={12} /></button>
                      <button className="btn btn-secondary btn-sm" onClick={() => removeLineItem(idx)}><Trash2 size={12} /></button>
                    </td>
                  </tr>
                ))}
                {lineItems.length === 0 && <tr><td colSpan={7} style={{ color: 'var(--text-muted)' }}>No line items yet.</td></tr>}
              </tbody>
            </table>

            {/* Inline line item edit form */}
            {editingItem && (
              <div className="card" style={{ marginTop: 12, padding: 16, border: '1px solid var(--primary)' }}>
                <div className="card-title" style={{ fontSize: 13 }}>{editingItemIdx >= 0 ? 'Edit Line Item' : 'New Line Item'}</div>
                <div className="grid-2" style={{ marginTop: 8 }}>
                  <div className="input-group"><label>Item Code</label><input className="input" value={editingItem.itemCode} onChange={e => setEditingItem({ ...editingItem, itemCode: e.target.value })} /></div>
                  <div className="input-group"><label>Label</label><input className="input" value={editingItem.label} onChange={e => setEditingItem({ ...editingItem, label: e.target.value })} /></div>
                </div>
                <div className="grid-2" style={{ marginTop: 8 }}>
                  <div className="input-group">
                    <label>Zone</label>
                    <select className="input" value={editingItem.zone} onChange={e => setEditingItem({ ...editingItem, zone: e.target.value })}>
                      {ZONES.map(z => <option key={z} value={z}>{z.replace(/_/g, ' ')}</option>)}
                    </select>
                  </div>
                  <div className="input-group">
                    <label>Item Type</label>
                    <select className="input" value={editingItem.itemType} onChange={e => setEditingItem({ ...editingItem, itemType: e.target.value })}>
                      {ITEM_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
                    </select>
                  </div>
                </div>
                <div className="input-group" style={{ marginTop: 8 }}>
                  <label>Formula</label>
                  <input className="input" value={editingItem.formula} onChange={e => setEditingItem({ ...editingItem, formula: e.target.value })} placeholder="e.g. TOTAL_ASSETS - TOTAL_LIABILITIES" style={{ fontFamily: 'monospace' }} />
                </div>
                <div className="grid-2" style={{ marginTop: 8 }}>
                  <div className="input-group"><label>Category</label><input className="input" value={editingItem.category} onChange={e => setEditingItem({ ...editingItem, category: e.target.value })} /></div>
                  <div className="input-group">
                    <label>Sign Convention</label>
                    <select className="input" value={editingItem.signConvention} onChange={e => setEditingItem({ ...editingItem, signConvention: e.target.value })}>
                      <option value="NATURAL">Natural</option><option value="CONTRA">Contra</option><option value="ABS">Absolute</option>
                    </select>
                  </div>
                </div>
                <div className="grid-2" style={{ marginTop: 8 }}>
                  <div className="input-group"><label>Indent Level</label><input className="input" type="number" min={0} max={5} value={editingItem.indentLevel} onChange={e => setEditingItem({ ...editingItem, indentLevel: Number(e.target.value) })} /></div>
                  <div style={{ display: 'flex', gap: 16, alignItems: 'center', paddingTop: 20 }}>
                    <label style={{ display: 'flex', gap: 4, alignItems: 'center' }}><input type="checkbox" checked={editingItem.required} onChange={e => setEditingItem({ ...editingItem, required: e.target.checked })} /> Required</label>
                    <label style={{ display: 'flex', gap: 4, alignItems: 'center' }}><input type="checkbox" checked={editingItem.isTotal} onChange={e => setEditingItem({ ...editingItem, isTotal: e.target.checked })} /> Is Total</label>
                  </div>
                </div>
                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 12 }}>
                  <button className="btn btn-secondary" onClick={() => { setEditingItem(null); setEditingItemIdx(-1) }}>Cancel</button>
                  <button className="btn btn-primary" disabled={!editingItem.itemCode.trim() || !editingItem.label.trim()} onClick={saveLineItem}>
                    {editingItemIdx >= 0 ? 'Update' : 'Add'}
                  </button>
                </div>
              </div>
            )}

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}>
              <button className="btn btn-secondary" onClick={() => setModalMode(null)}>Cancel</button>
              <button className="btn btn-primary" onClick={saveTemplate}>Save All Line Items</button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
