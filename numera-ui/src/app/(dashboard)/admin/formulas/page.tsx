'use client'

import { useMemo, useState } from 'react'
import { Calculator, Edit, Plus, Search, Trash2, X } from 'lucide-react'
import FormulaBuilder from '@/components/covenant/FormulaBuilder'
import { useModelLineItems } from '@/services/adminApi'
import {
  useCreateFormula,
  useDeleteFormula,
  useFormulas,
  useUpdateFormula,
  type CovenantFormula,
} from '@/services/covenantApi'
import { useToast } from '@/components/ui/Toast'

const EMPTY_FORM = {
  name: '',
  expression: '',
}

export default function AdminFormulasPage() {
  const { showToast } = useToast()
  const [search, setSearch] = useState('')
  const [isEditorOpen, setIsEditorOpen] = useState(false)
  const [editingFormulaId, setEditingFormulaId] = useState<string | null>(null)
  const [name, setName] = useState(EMPTY_FORM.name)
  const [expression, setExpression] = useState(EMPTY_FORM.expression)

  const formulasQuery = useFormulas()
  const lineItemsQuery = useModelLineItems()

  const createFormula = useCreateFormula()
  const updateFormula = useUpdateFormula()
  const deleteFormula = useDeleteFormula()

  const formulas = formulasQuery.data ?? []
  const lineItems = lineItemsQuery.data ?? []

  const filteredFormulas = useMemo(() => {
    const term = search.trim().toLowerCase()
    if (!term) return formulas
    return formulas.filter(
      (formula) =>
        formula.name.toLowerCase().includes(term) ||
        formula.expression.toLowerCase().includes(term)
    )
  }, [formulas, search])

  const resetEditor = () => {
    setName(EMPTY_FORM.name)
    setExpression(EMPTY_FORM.expression)
    setEditingFormulaId(null)
    setIsEditorOpen(false)
  }

  const startEdit = (formula: CovenantFormula) => {
    setEditingFormulaId(formula.id)
    setName(formula.name)
    setExpression(formula.expression)
    setIsEditorOpen(true)
  }

  const handleSave = async () => {
    if (!name.trim()) {
      showToast('Formula name is required', 'info')
      return
    }
    if (!expression.trim()) {
      showToast('Formula expression is required', 'info')
      return
    }

    try {
      if (editingFormulaId) {
        await updateFormula.mutateAsync({
          id: editingFormulaId,
          payload: {
            name,
            expression,
          },
        })
        showToast('Formula updated successfully', 'success')
      } else {
        await createFormula.mutateAsync({
          name,
          expression,
          active: true,
          description: undefined,
        })
        showToast('Formula created successfully', 'success')
      }
      resetEditor()
    } catch (error) {
      const message =
        typeof error === 'object' && error && 'message' in error
          ? String((error as { message: string }).message)
          : 'Failed to save formula'
      showToast(message, 'error')
    }
  }

  const handleDelete = async (id: string) => {
    try {
      await deleteFormula.mutateAsync(id)
      showToast('Formula deleted', 'success')
    } catch (error) {
      const message =
        typeof error === 'object' && error && 'message' in error
          ? String((error as { message: string }).message)
          : 'Failed to delete formula'
      showToast(message, 'error')
    }
  }

  return (
    <>
      <div className="page-header">
        <h1>Formula Management</h1>
        <p>Create and maintain covenant formulas with reusable line-item tokens</p>
      </div>

      <div className="toolbar">
        <div className="toolbar-left">
          <div className="search-bar">
            <Search size={16} />
            <input
              placeholder="Search formulas"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
          </div>
        </div>
        <div className="toolbar-right">
          <button className="btn btn-primary" onClick={() => setIsEditorOpen(true)}>
            <Plus size={16} />
            Add Formula
          </button>
        </div>
      </div>

      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <table className="data-table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Expression</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {formulasQuery.isLoading ? (
              <tr>
                <td colSpan={4} style={{ color: 'var(--text-muted)' }}>Loading formulas...</td>
              </tr>
            ) : null}

            {formulasQuery.isError ? (
              <tr>
                <td colSpan={4} style={{ color: 'var(--danger)' }}>
                  Failed to load formulas.
                </td>
              </tr>
            ) : null}

            {!formulasQuery.isLoading && !formulasQuery.isError && filteredFormulas.length === 0 ? (
              <tr>
                <td colSpan={4} style={{ color: 'var(--text-muted)' }}>
                  No formulas found.
                </td>
              </tr>
            ) : null}

            {filteredFormulas.map((formula) => (
              <tr key={formula.id}>
                <td style={{ fontWeight: 600 }}>
                  <Calculator size={14} style={{ display: 'inline', verticalAlign: -2, marginRight: 6, color: 'var(--accent)' }} />
                  {formula.name}
                </td>
                <td style={{ fontFamily: 'monospace', fontSize: 12 }}>{formula.expression}</td>
                <td>
                  <span className={`badge-status ${formula.active ? 'active' : 'inactive'}`}>
                    <span className="dot" />
                    {formula.active ? 'Active' : 'Inactive'}
                  </span>
                </td>
                <td>
                  <div style={{ display: 'flex', gap: 4 }}>
                    <button className="btn btn-ghost btn-sm" onClick={() => startEdit(formula)}>
                      <Edit size={14} />
                    </button>
                    <button className="btn btn-ghost btn-sm" style={{ color: 'var(--danger)' }} onClick={() => handleDelete(formula.id)}>
                      <Trash2 size={14} />
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {isEditorOpen ? (
        <div className="modal-overlay" onClick={(event) => {
          if (event.target === event.currentTarget) {
            resetEditor()
          }
        }}>
          <div className="modal" style={{ maxWidth: 1100 }}>
            <div className="card-header">
              <div>
                <div className="card-title">{editingFormulaId ? 'Edit Formula' : 'Create Formula'}</div>
                <div className="card-subtitle">Build formulas using model line items and operators.</div>
              </div>
              <button className="btn btn-ghost btn-sm" onClick={resetEditor}>
                <X size={14} />
              </button>
            </div>

            <div className="input-group" style={{ marginBottom: 12 }}>
              <label>Formula Name</label>
              <input className="input" value={name} onChange={(event) => setName(event.target.value)} placeholder="Debt Service Coverage Ratio" />
            </div>

            {lineItemsQuery.isLoading ? <div style={{ color: 'var(--text-muted)', marginBottom: 12 }}>Loading model line items...</div> : null}
            {lineItemsQuery.isError ? <div style={{ color: 'var(--danger)', marginBottom: 12 }}>Failed to load line items.</div> : null}

            <FormulaBuilder value={expression} onChange={setExpression} lineItems={lineItems} />

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 14 }}>
              <button className="btn btn-secondary" onClick={resetEditor}>Cancel</button>
              <button
                className="btn btn-primary"
                onClick={handleSave}
                disabled={createFormula.isPending || updateFormula.isPending}
              >
                {editingFormulaId ? 'Update Formula' : 'Save Formula'}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </>
  )
}
