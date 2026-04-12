'use client'

import { useState } from 'react'
import { Plus } from 'lucide-react'
import { useLanguages, useToggleLanguage } from '@/services/adminApi'
import { useToast } from '@/components/ui/Toast'

export default function AdminLanguagesPage() {
  const { showToast } = useToast()
  const [newLanguageName, setNewLanguageName] = useState('')
  const [newLanguageCode, setNewLanguageCode] = useState('')

  const languagesQuery = useLanguages()
  const toggleMutation = useToggleLanguage()

  const languages = languagesQuery.data ?? []

  const onToggle = async (code: string, enabled: boolean) => {
    try {
      await toggleMutation.mutateAsync({ code, enabled: !enabled })
      showToast(`Language ${!enabled ? 'enabled' : 'disabled'}`, 'success')
    } catch (error) {
      const message =
        typeof error === 'object' && error && 'message' in error
          ? String((error as { message: string }).message)
          : 'Failed to update language'
      showToast(message, 'error')
    }
  }

  return (
    <>
      <div className="page-header">
        <h1>Language Management</h1>
        <p>Manage supported OCR and platform languages</p>
      </div>

      <div className="card" style={{ marginBottom: 16 }}>
        <div className="card-header">
          <div>
            <div className="card-title">Add New Language</div>
            <div className="card-subtitle">Register additional locale support</div>
          </div>
        </div>

        <div className="grid-2" style={{ marginBottom: 0 }}>
          <div className="input-group">
            <label>Language Name</label>
            <input className="input" value={newLanguageName} onChange={(event) => setNewLanguageName(event.target.value)} placeholder="Arabic" />
          </div>
          <div className="input-group">
            <label>Language Code</label>
            <input className="input" value={newLanguageCode} onChange={(event) => setNewLanguageCode(event.target.value)} placeholder="ar" />
          </div>
        </div>

        <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 14 }}>
          <button
            className="btn btn-primary"
            onClick={() => showToast('Add language API is not available yet in this environment', 'info')}
            disabled={!newLanguageName.trim() || !newLanguageCode.trim()}
          >
            <Plus size={14} />
            Add New Language
          </button>
        </div>
      </div>

      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <table className="data-table">
          <thead>
            <tr>
              <th>Code</th>
              <th>Name</th>
              <th>OCR Support</th>
              <th>Enabled</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {languagesQuery.isLoading ? (
              <tr><td colSpan={5} style={{ color: 'var(--text-muted)' }}>Loading languages...</td></tr>
            ) : null}
            {languagesQuery.isError ? (
              <tr><td colSpan={5} style={{ color: 'var(--danger)' }}>Failed to load languages.</td></tr>
            ) : null}
            {!languagesQuery.isLoading && !languagesQuery.isError && languages.length === 0 ? (
              <tr><td colSpan={5} style={{ color: 'var(--text-muted)' }}>No languages configured.</td></tr>
            ) : null}
            {languages.map((language) => (
              <tr key={language.code}>
                <td style={{ fontFamily: 'monospace' }}>{language.code}</td>
                <td>{language.name}</td>
                <td>
                  <span className={`badge-status ${language.ocrSupported ? 'approved' : 'draft'}`}>
                    <span className="dot" />
                    {language.ocrSupported ? 'Supported' : 'Not Supported'}
                  </span>
                </td>
                <td>
                  <span className={`badge-status ${language.enabled ? 'active' : 'inactive'}`}>
                    <span className="dot" />
                    {language.enabled ? 'Enabled' : 'Disabled'}
                  </span>
                </td>
                <td>
                  <button className="btn btn-secondary btn-sm" onClick={() => onToggle(language.code, language.enabled)}>
                    {language.enabled ? 'Disable' : 'Enable'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  )
}
