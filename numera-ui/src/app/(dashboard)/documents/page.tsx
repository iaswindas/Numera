'use client'

import { useRef, useState } from 'react'
import { Play, Search, Trash2, Upload } from 'lucide-react'
import { useDeleteDocument, useDocuments, useProcessDocument, useUploadDocument } from '@/services/documentApi'
import { useCustomers } from '@/services/customerApi'
import { useToast } from '@/components/ui/Toast'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'

export default function DocumentsPage() {
  const { showToast } = useToast()
  const [query, setQuery] = useState('')
  const [customerId, setCustomerId] = useState('')
  const [language, setLanguage] = useState('en')
  const [confirmDeleteId, setConfirmDeleteId] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement | null>(null)

  const docsQuery = useDocuments()
  const customersQuery = useCustomers({ query })
  const uploadMutation = useUploadDocument()
  const processMutation = useProcessDocument()
  const deleteMutation = useDeleteDocument()

  const documents = docsQuery.data ?? []
  const customers = customersQuery.data ?? []

  const filtered = documents.filter((d) => d.filename.toLowerCase().includes(query.toLowerCase()))

  const onUploadClick = () => {
    if (!customerId) {
      showToast('Select a customer before uploading', 'error')
      return
    }
    fileInputRef.current?.click()
  }

  const onFileSelected = async (file?: File) => {
    if (!file || !customerId) return
    const formData = new FormData()
    formData.append('file', file)
    formData.append('customerId', customerId)
    formData.append('language', language)
    try {
      await uploadMutation.mutateAsync(formData)
      showToast('Document uploaded and processing started', 'success')
    } catch (error) {
      const message = typeof error === 'object' && error && 'message' in error ? String((error as { message: string }).message) : 'Upload failed'
      showToast(message, 'error')
    }
  }

  return (
    <>
      <div className="page-header">
        <h1>File Store</h1>
        <p>Upload and manage financial documents for AI processing</p>
      </div>

      <div className="toolbar">
        <div className="toolbar-left">
          <div className="search-bar">
            <Search size={16} />
            <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search files..." />
          </div>
          <select className="input" style={{ width: 220 }} value={customerId} onChange={(e) => setCustomerId(e.target.value)}>
            <option value="">Select customer</option>
            {customers.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
          <select className="input" style={{ width: 120 }} value={language} onChange={(e) => setLanguage(e.target.value)}>
            <option value="en">English</option>
            <option value="ar">Arabic</option>
          </select>
        </div>
        <div className="toolbar-right">
          <button className="btn btn-primary" onClick={onUploadClick} disabled={uploadMutation.isPending}>
            <Upload size={16} />{uploadMutation.isPending ? 'Uploading...' : 'Upload Files'}
          </button>
          <input
            ref={fileInputRef}
            type="file"
            accept="application/pdf"
            style={{ display: 'none' }}
            onChange={(e) => onFileSelected(e.target.files?.[0])}
          />
        </div>
      </div>

      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <table className="data-table">
          <thead>
            <tr>
              <th>File Name</th>
              <th>Language</th>
              <th>Status</th>
              <th>Uploaded By</th>
              <th>Uploaded At</th>
              <th>Zones</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((f) => (
              <tr key={f.id}>
                <td style={{ fontWeight: 500 }}>{f.filename}</td>
                <td>{f.language}</td>
                <td><span className={`badge-status ${f.processingStatus.toLowerCase()}`}><span className="dot" />{f.processingStatus}</span></td>
                <td>{f.uploadedByName}</td>
                <td style={{ color: 'var(--text-secondary)' }}>{new Date(f.createdAt).toLocaleString()}</td>
                <td>{f.zonesDetected}</td>
                <td>
                  <div style={{ display: 'flex', gap: 6 }}>
                    <button className="btn btn-ghost btn-sm" onClick={() => processMutation.mutate(f.id)} title="Process">
                      <Play size={14} />
                    </button>
                    <button className="btn btn-ghost btn-sm" style={{ color: 'var(--danger)' }} onClick={() => setConfirmDeleteId(f.id)} title="Delete">
                      <Trash2 size={14} />
                    </button>
                  </div>
                </td>
              </tr>
            ))}
            {!docsQuery.isLoading && filtered.length === 0 ? (
              <tr><td colSpan={7} style={{ color: 'var(--text-muted)' }}>No documents found.</td></tr>
            ) : null}
          </tbody>
        </table>
      </div>

      <ConfirmDialog
        open={!!confirmDeleteId}
        title="Delete document"
        description="This action cannot be undone."
        confirmText="Delete"
        onCancel={() => setConfirmDeleteId(null)}
        onConfirm={async () => {
          if (!confirmDeleteId) return
          await deleteMutation.mutateAsync(confirmDeleteId)
          setConfirmDeleteId(null)
          showToast('Document deleted', 'success')
        }}
      />
    </>
  )
}
