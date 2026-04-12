'use client'

import { useCallback, useMemo, useState } from 'react'
import { Upload, CheckCircle, XCircle, FileText, Eye, MessageSquare, Clock, AlertTriangle } from 'lucide-react'
import { useToast } from '@/components/ui/Toast'
import { useAuthStore } from '@/stores/authStore'
import {
  useMonitoringDocuments,
  useUploadMonitoringDocument,
  useSubmitForVerification,
  useCheckerDecision,
  useTriggerDocumentAction,
} from '@/services/covenantApi'

/* ── Types ─────────────────────────────────────── */

export type VerificationStatus = 'PENDING' | 'SUBMITTED' | 'APPROVED' | 'REJECTED'

export interface VerificationDocument {
  id: string
  monitoringItemId: string
  fileName: string
  fileUrl: string
  mimeType: string
  status: VerificationStatus
  makerComments?: string
  checkerComments?: string
  submittedBy?: string
  submittedAt?: string
  reviewedBy?: string
  reviewedAt?: string
}

type DocumentVerificationProps = {
  monitoringItemId: string
  role: 'maker' | 'checker'
}

/* ── Status badge helper ───────────────────────── */

const STATUS_CONFIG: Record<VerificationStatus, { label: string; className: string; icon: React.ReactNode }> = {
  PENDING: { label: 'Pending', className: 'draft', icon: <Clock size={12} /> },
  SUBMITTED: { label: 'Submitted', className: 'pending', icon: <FileText size={12} /> },
  APPROVED: { label: 'Approved', className: 'approved', icon: <CheckCircle size={12} /> },
  REJECTED: { label: 'Rejected', className: 'rejected', icon: <XCircle size={12} /> },
}

/* ── Component ─────────────────────────────────── */

export default function DocumentVerification({ monitoringItemId, role }: DocumentVerificationProps) {
  const { user } = useAuthStore()
  const { showToast } = useToast()

  const docsQuery = useMonitoringDocuments(monitoringItemId)
  const uploadMutation = useUploadMonitoringDocument()
  const submitMutation = useSubmitForVerification()
  const decisionMutation = useCheckerDecision()
  const actionMutation = useTriggerDocumentAction()

  const [makerComment, setMakerComment] = useState('')
  const [checkerComment, setCheckerComment] = useState('')
  const [selectedDocId, setSelectedDocId] = useState<string | null>(null)
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)

  const documents = useMemo<VerificationDocument[]>(
    () => (docsQuery.data as VerificationDocument[] | undefined) ?? [],
    [docsQuery.data]
  )

  const pendingDocs = useMemo(() => documents.filter((d) => d.status === 'PENDING'), [documents])
  const submittedDocs = useMemo(() => documents.filter((d) => d.status === 'SUBMITTED'), [documents])
  const reviewedDocs = useMemo(() => documents.filter((d) => d.status === 'APPROVED' || d.status === 'REJECTED'), [documents])
  const rejectedDocs = useMemo(() => documents.filter((d) => d.status === 'REJECTED'), [documents])

  /* ── Maker: Upload ───────────────────────────── */
  const handleUpload = useCallback(async (files: FileList | null) => {
    if (!files || files.length === 0) return
    try {
      const formData = new FormData()
      Array.from(files).forEach((f) => formData.append('files', f))
      formData.append('monitoringItemId', monitoringItemId)
      if (makerComment) formData.append('comments', makerComment)

      await uploadMutation.mutateAsync({ monitoringItemId, formData })
      setMakerComment('')
      showToast(`${files.length} document(s) uploaded`, 'success')
    } catch (error) {
      const msg = error instanceof Error ? error.message : 'Upload failed'
      showToast(msg, 'error')
    }
  }, [monitoringItemId, makerComment, uploadMutation, showToast])

  /* ── Maker: Submit for Verification ──────────── */
  const handleSubmit = useCallback(async (docId: string) => {
    try {
      await submitMutation.mutateAsync({ monitoringItemId, documentId: docId })
      showToast('Document submitted for verification', 'success')
    } catch (error) {
      const msg = error instanceof Error ? error.message : 'Submit failed'
      showToast(msg, 'error')
    }
  }, [monitoringItemId, submitMutation, showToast])

  /* ── Checker: Approve / Reject ───────────────── */
  const handleDecision = useCallback(async (docId: string, decision: 'APPROVE' | 'REJECT') => {
    try {
      await decisionMutation.mutateAsync({
        monitoringItemId,
        documentId: docId,
        payload: { decision, comments: checkerComment, reviewerId: user?.id ?? '' },
      })
      setCheckerComment('')
      setSelectedDocId(null)
      showToast(`Document ${decision === 'APPROVE' ? 'approved' : 'rejected'}`, 'success')
    } catch (error) {
      const msg = error instanceof Error ? error.message : 'Decision failed'
      showToast(msg, 'error')
    }
  }, [monitoringItemId, checkerComment, user?.id, decisionMutation, showToast])

  /* ── Maker: Re-upload rejected ───────────────── */
  const handleTriggerAction = useCallback(async (docId: string) => {
    try {
      await actionMutation.mutateAsync({ monitoringItemId, documentId: docId, action: 'RESUBMIT' })
      showToast('Document returned to pending for re-upload', 'success')
    } catch (error) {
      const msg = error instanceof Error ? error.message : 'Action failed'
      showToast(msg, 'error')
    }
  }, [monitoringItemId, actionMutation, showToast])

  /* ── Render: Status Badge ────────────────────── */
  const renderBadge = (status: VerificationStatus) => {
    const cfg = STATUS_CONFIG[status]
    return (
      <span className={`badge-status ${cfg.className}`} style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
        {cfg.icon} {cfg.label}
      </span>
    )
  }

  /* ── Render: Document Row ────────────────────── */
  const renderDocRow = (doc: VerificationDocument, actions: React.ReactNode) => (
    <div key={doc.id} className="card" style={{ padding: 12, display: 'flex', alignItems: 'center', gap: 12 }}>
      <FileText size={20} style={{ color: 'var(--primary)', flexShrink: 0 }} />
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontWeight: 600, fontSize: 13, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{doc.fileName}</div>
        <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 2 }}>
          {doc.submittedAt ? `Submitted ${new Date(doc.submittedAt).toLocaleString()}` : 'Not yet submitted'}
          {doc.makerComments ? ` · "${doc.makerComments}"` : ''}
        </div>
        {doc.checkerComments && (
          <div style={{ fontSize: 11, color: doc.status === 'REJECTED' ? 'var(--danger)' : 'var(--success)', marginTop: 2 }}>
            Checker: {doc.checkerComments}
          </div>
        )}
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        {renderBadge(doc.status)}
        <button
          className="btn btn-ghost btn-sm"
          title="Preview"
          onClick={() => setPreviewUrl(doc.fileUrl)}
        >
          <Eye size={14} />
        </button>
        {actions}
      </div>
    </div>
  )

  /* ── Loading / Error ─────────────────────────── */
  if (docsQuery.isLoading) {
    return <div style={{ color: 'var(--text-muted)', padding: 20 }}>Loading documents...</div>
  }
  if (docsQuery.isError) {
    return <div style={{ color: 'var(--danger)', padding: 20 }}>Failed to load verification documents.</div>
  }

  /* ═══════════ MAKER VIEW ═══════════ */
  if (role === 'maker') {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        {/* Upload section */}
        <div className="card">
          <div className="card-header">
            <div>
              <div className="card-title">Upload Documents</div>
              <div className="card-subtitle">Upload supporting documents for this monitoring item</div>
            </div>
          </div>

          <div className="input-group" style={{ marginBottom: 12 }}>
            <label>Comments (optional)</label>
            <textarea
              className="input"
              rows={2}
              placeholder="Add notes about the uploaded documents"
              value={makerComment}
              onChange={(e) => setMakerComment(e.target.value)}
            />
          </div>

          <label
            style={{
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              padding: 32,
              border: '2px dashed var(--border-color)',
              borderRadius: 8,
              cursor: 'pointer',
              transition: 'border-color .15s',
            }}
          >
            <Upload size={28} style={{ color: 'var(--text-muted)', marginBottom: 8 }} />
            <span style={{ fontWeight: 600, fontSize: 13 }}>Click to upload or drag files here</span>
            <span style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 4 }}>PDF, XLSX, DOCX up to 25MB</span>
            <input
              type="file"
              multiple
              accept=".pdf,.xlsx,.xls,.docx,.doc,.csv"
              style={{ display: 'none' }}
              onChange={(e) => handleUpload(e.target.files)}
            />
          </label>
        </div>

        {/* Pending documents */}
        {pendingDocs.length > 0 && (
          <div className="card">
            <div className="card-header">
              <div className="card-title">Pending Documents ({pendingDocs.length})</div>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {pendingDocs.map((doc) =>
                renderDocRow(
                  doc,
                  <button
                    className="btn btn-primary btn-sm"
                    onClick={() => handleSubmit(doc.id)}
                    disabled={submitMutation.isPending}
                  >
                    Submit
                  </button>
                )
              )}
            </div>
          </div>
        )}

        {/* Rejected - needs resubmit */}
        {rejectedDocs.length > 0 && (
          <div className="card" style={{ borderLeft: '3px solid var(--danger)' }}>
            <div className="card-header">
              <div className="card-title" style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                <AlertTriangle size={16} style={{ color: 'var(--danger)' }} /> Rejected Documents ({rejectedDocs.length})
              </div>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {rejectedDocs.map((doc) =>
                renderDocRow(
                  doc,
                  <button
                    className="btn btn-secondary btn-sm"
                    onClick={() => handleTriggerAction(doc.id)}
                    disabled={actionMutation.isPending}
                  >
                    Re-upload
                  </button>
                )
              )}
            </div>
          </div>
        )}

        {/* All reviewed */}
        {reviewedDocs.length > 0 && (
          <div className="card">
            <div className="card-header"><div className="card-title">Review History</div></div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {reviewedDocs.map((doc) => renderDocRow(doc, null))}
            </div>
          </div>
        )}

        {/* Preview modal */}
        {previewUrl && (
          <div className="modal-overlay" onClick={() => setPreviewUrl(null)}>
            <div className="modal" style={{ maxWidth: 900, height: '80vh' }} onClick={(e) => e.stopPropagation()}>
              <div className="card-header">
                <div className="card-title">Document Preview</div>
                <button className="btn btn-ghost btn-sm" onClick={() => setPreviewUrl(null)}>Close</button>
              </div>
              <iframe src={previewUrl} style={{ width: '100%', height: 'calc(100% - 50px)', border: 'none' }} title="Document Preview" />
            </div>
          </div>
        )}
      </div>
    )
  }

  /* ═══════════ CHECKER VIEW ═══════════ */
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* Submitted for review */}
      <div className="card">
        <div className="card-header">
          <div>
            <div className="card-title">Documents Pending Review ({submittedDocs.length})</div>
            <div className="card-subtitle">Review and approve or reject submitted documents</div>
          </div>
        </div>

        {submittedDocs.length === 0 ? (
          <div style={{ color: 'var(--text-muted)', fontSize: 13, padding: 16 }}>No documents pending review.</div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {submittedDocs.map((doc) =>
              renderDocRow(
                doc,
                <div style={{ display: 'flex', gap: 4 }}>
                  <button
                    className="btn btn-ghost btn-sm"
                    title="Review"
                    onClick={() => setSelectedDocId(selectedDocId === doc.id ? null : doc.id)}
                  >
                    <MessageSquare size={14} />
                  </button>
                </div>
              )
            )}
          </div>
        )}
      </div>

      {/* Checker decision panel */}
      {selectedDocId && (
        <div className="card" style={{ borderLeft: '3px solid var(--primary)' }}>
          <div className="card-header">
            <div className="card-title">Review Decision</div>
          </div>
          <div className="input-group" style={{ marginBottom: 12 }}>
            <label>Comments</label>
            <textarea
              className="input"
              rows={3}
              placeholder="Provide review comments (required for rejection)"
              value={checkerComment}
              onChange={(e) => setCheckerComment(e.target.value)}
            />
          </div>
          <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
            <button className="btn btn-secondary" onClick={() => { setSelectedDocId(null); setCheckerComment('') }}>Cancel</button>
            <button
              className="btn btn-sm"
              style={{ background: 'var(--danger)', color: '#fff' }}
              disabled={decisionMutation.isPending || !checkerComment.trim()}
              onClick={() => handleDecision(selectedDocId, 'REJECT')}
            >
              <XCircle size={14} /> Reject
            </button>
            <button
              className="btn btn-primary"
              disabled={decisionMutation.isPending}
              onClick={() => handleDecision(selectedDocId, 'APPROVE')}
            >
              <CheckCircle size={14} /> Approve
            </button>
          </div>
        </div>
      )}

      {/* History */}
      {reviewedDocs.length > 0 && (
        <div className="card">
          <div className="card-header"><div className="card-title">Review History ({reviewedDocs.length})</div></div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {reviewedDocs.map((doc) => renderDocRow(doc, null))}
          </div>
        </div>
      )}

      {/* Preview modal */}
      {previewUrl && (
        <div className="modal-overlay" onClick={() => setPreviewUrl(null)}>
          <div className="modal" style={{ maxWidth: 900, height: '80vh' }} onClick={(e) => e.stopPropagation()}>
            <div className="card-header">
              <div className="card-title">Document Preview</div>
              <button className="btn btn-ghost btn-sm" onClick={() => setPreviewUrl(null)}>Close</button>
            </div>
            <iframe src={previewUrl} style={{ width: '100%', height: 'calc(100% - 50px)', border: 'none' }} title="Document Preview" />
          </div>
        </div>
      )}
    </div>
  )
}
