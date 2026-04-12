'use client'

import { useMemo, useState } from 'react'
import * as Dialog from '@radix-ui/react-dialog'
import { Download, Upload, X } from 'lucide-react'
import {
  useAdminTaxonomy,
  useBulkUploadTaxonomy,
  useExportTaxonomy,
} from '@/services/adminApi'
import { useToast } from '@/components/ui/Toast'

export default function AdminTaxonomyPage() {
  const { showToast } = useToast()
  const [isUploadOpen, setIsUploadOpen] = useState(false)
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [uploadResult, setUploadResult] = useState<{ createdCount: number; updatedCount?: number; errorCount: number; errors: string[] } | null>(null)

  const taxonomyQuery = useAdminTaxonomy()
  const exportMutation = useExportTaxonomy()
  const uploadMutation = useBulkUploadTaxonomy()

  const entries = useMemo(
    () => ((taxonomyQuery.data as { entries?: Array<{ itemCode: string; label: string; zone: string; category: string }> })?.entries ?? []),
    [taxonomyQuery.data]
  )

  const handleExport = async () => {
    try {
      const blob = await exportMutation.mutateAsync()
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = 'taxonomy.xlsx'
      link.click()
      URL.revokeObjectURL(url)
    } catch (error) {
      const message =
        typeof error === 'object' && error && 'message' in error
          ? String((error as { message: string }).message)
          : 'Failed to export taxonomy'
      showToast(message, 'error')
    }
  }

  const handleUpload = async () => {
    if (!selectedFile) {
      showToast('Select an Excel or CSV file first', 'info')
      return
    }

    try {
      const result = await uploadMutation.mutateAsync(selectedFile)
      setUploadResult(result)
      showToast('Taxonomy upload completed', 'success')
    } catch (error) {
      const message =
        typeof error === 'object' && error && 'message' in error
          ? String((error as { message: string }).message)
          : 'Bulk upload failed'
      showToast(message, 'error')
    }
  }

  return (
    <>
      <div className="page-header">
        <h1>Taxonomy & Zones</h1>
        <p>Manage taxonomy mappings and bulk import/export zone definitions</p>
      </div>

      <div className="toolbar">
        <div className="toolbar-left" />
        <div className="toolbar-right">
          <button className="btn btn-secondary" onClick={handleExport} disabled={exportMutation.isPending}>
            <Download size={14} />
            Download Excel
          </button>

          <Dialog.Root open={isUploadOpen} onOpenChange={setIsUploadOpen}>
            <Dialog.Trigger asChild>
              <button className="btn btn-primary">
                <Upload size={14} />
                Bulk Upload
              </button>
            </Dialog.Trigger>
            <Dialog.Portal>
              <Dialog.Overlay className="modal-overlay" />
              <Dialog.Content className="modal" style={{ maxWidth: 640 }}>
                <div className="card-header" style={{ marginBottom: 8 }}>
                  <div>
                    <Dialog.Title className="card-title">Bulk Upload Taxonomy</Dialog.Title>
                    <Dialog.Description className="card-subtitle">Upload Excel or CSV files to add or update taxonomy entries.</Dialog.Description>
                  </div>
                  <Dialog.Close asChild>
                    <button className="btn btn-ghost btn-sm">
                      <X size={14} />
                    </button>
                  </Dialog.Close>
                </div>

                <div className="input-group" style={{ marginBottom: 12 }}>
                  <label>File</label>
                  <input
                    className="input"
                    type="file"
                    accept=".xlsx,.xls,.csv"
                    onChange={(event) => {
                      const file = event.target.files?.[0] ?? null
                      setSelectedFile(file)
                      setUploadResult(null)
                    }}
                  />
                </div>

                {uploadMutation.isPending ? (
                  <div className="card" style={{ padding: 12, marginBottom: 12 }}>
                    <div style={{ marginBottom: 8, fontSize: 13 }}>Uploading and validating taxonomy...</div>
                    <div className="progress-bar"><div className="fill accent" style={{ width: '70%' }} /></div>
                  </div>
                ) : null}

                {uploadResult ? (
                  <div className="card" style={{ padding: 12, marginBottom: 12 }}>
                    <div style={{ fontWeight: 600, marginBottom: 8 }}>Upload Results</div>
                    <div style={{ fontSize: 13, marginBottom: 4 }}>Created: {uploadResult.createdCount}</div>
                    <div style={{ fontSize: 13, marginBottom: 4 }}>Updated: {uploadResult.updatedCount ?? 0}</div>
                    <div style={{ fontSize: 13, marginBottom: 8 }}>Errors: {uploadResult.errorCount}</div>
                    {uploadResult.errors.length > 0 ? (
                      <ul style={{ paddingLeft: 18, color: 'var(--text-secondary)', fontSize: 12 }}>
                        {uploadResult.errors.slice(0, 5).map((error) => (
                          <li key={error}>{error}</li>
                        ))}
                      </ul>
                    ) : null}
                  </div>
                ) : null}

                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                  <Dialog.Close asChild>
                    <button className="btn btn-secondary">Close</button>
                  </Dialog.Close>
                  <button className="btn btn-primary" onClick={handleUpload} disabled={uploadMutation.isPending || !selectedFile}>
                    Upload
                  </button>
                </div>
              </Dialog.Content>
            </Dialog.Portal>
          </Dialog.Root>
        </div>
      </div>

      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <table className="data-table">
          <thead><tr><th>Item Code</th><th>Label</th><th>Zone</th><th>Category</th></tr></thead>
          <tbody>
            {taxonomyQuery.isLoading ? (
              <tr><td colSpan={4} style={{ color: 'var(--text-muted)' }}>Loading taxonomy entries...</td></tr>
            ) : null}
            {taxonomyQuery.isError ? (
              <tr><td colSpan={4} style={{ color: 'var(--danger)' }}>Failed to load taxonomy entries.</td></tr>
            ) : null}
            {!taxonomyQuery.isLoading && !taxonomyQuery.isError && entries.length === 0 ? (
              <tr><td colSpan={4} style={{ color: 'var(--text-muted)' }}>No taxonomy entries found.</td></tr>
            ) : null}
            {entries.map((entry) => (
              <tr key={entry.itemCode}>
                <td>{entry.itemCode}</td>
                <td>{entry.label}</td>
                <td>{entry.zone}</td>
                <td>{entry.category}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  )
}
