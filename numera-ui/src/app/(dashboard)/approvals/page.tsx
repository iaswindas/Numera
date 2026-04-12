'use client'

import { useCallback, useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { ArrowLeft, Check, X, AlertCircle } from 'lucide-react'
import { usePendingApprovals, useApproveSpread, useRejectSpread } from '@/services/spreadApi'
import { useToast } from '@/components/ui/Toast'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'

interface ApprovalState {
  type: 'approve' | 'reject' | null
  spreadId: string | null
  comment: string
}

export default function ApprovalsPage() {
  const router = useRouter()
  const { showToast } = useToast()
  const approvalsQuery = usePendingApprovals()
  const approveMutation = useApproveSpread()
  const rejectMutation = useRejectSpread()

  const [approvalState, setApprovalState] = useState<ApprovalState>({
    type: null,
    spreadId: null,
    comment: '',
  })

  const approvals = approvalsQuery.data ?? []
  const isDialogOpen = approvalState.type !== null

  const handleApproveClick = (spreadId: string) => {
    setApprovalState({ type: 'approve', spreadId, comment: '' })
  }

  const handleRejectClick = (spreadId: string) => {
    setApprovalState({ type: 'reject', spreadId, comment: '' })
  }

  const handleConfirmApproval = useCallback(async () => {
    if (!approvalState.spreadId) return

    try {
      await approveMutation.mutateAsync({
        spreadId: approvalState.spreadId,
        approverComment: approvalState.comment,
      })
      showToast('Spread approved successfully', 'success')
      setApprovalState({ type: null, spreadId: null, comment: '' })
    } catch (error) {
      showToast('Failed to approve spread', 'error')
    }
  }, [approvalState, approveMutation, showToast])

  const handleConfirmRejection = useCallback(async () => {
    if (!approvalState.spreadId) return

    if (!approvalState.comment.trim()) {
      showToast('Rejection reason is required', 'error')
      return
    }

    try {
      await rejectMutation.mutateAsync({
        spreadId: approvalState.spreadId,
        rejectionReason: approvalState.comment,
      })
      showToast('Spread rejected', 'success')
      setApprovalState({ type: null, spreadId: null, comment: '' })
    } catch (error) {
      showToast('Failed to reject spread', 'error')
    }
  }, [approvalState, rejectMutation, showToast])

  const handleCancel = () => {
    setApprovalState({ type: null, spreadId: null, comment: '' })
  }

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    })
  }

  if (approvalsQuery.isLoading) {
    return (
      <div style={{ padding: 24 }}>
        <div className="card" style={{ textAlign: 'center', padding: 40 }}>
          Loading pending approvals...
        </div>
      </div>
    )
  }

  return (
    <>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 24 }}>
        <Link href="/dashboard" className="btn btn-ghost">
          <ArrowLeft size={16} />
        </Link>
        <h1 style={{ fontSize: 22, fontWeight: 700 }}>Pending Approvals</h1>
        <span className="badge">{approvals.length}</span>
      </div>

      {approvals.length === 0 ? (
        <div className="card" style={{ textAlign: 'center', padding: 40 }}>
          <AlertCircle size={32} style={{ margin: '0 auto 12px', color: 'var(--text-muted)' }} />
          <p style={{ color: 'var(--text-muted)', marginBottom: 8 }}>No pending approvals</p>
          <p style={{ color: 'var(--text-muted)', fontSize: 13 }}>All submitted spreads have been processed</p>
        </div>
      ) : (
        <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
          <table className="data-table">
            <thead>
              <tr>
                <th>Customer</th>
                <th>Period</th>
                <th>Analyst</th>
                <th>Submitted</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {approvals.map((approval: any) => (
                <tr key={approval.id}>
                  <td style={{ fontWeight: 500 }}>{approval.customerName || '-'}</td>
                  <td>{approval.statementDate ? formatDate(approval.statementDate) : '-'}</td>
                  <td>{approval.createdBy || '-'}</td>
                  <td style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                    {approval.createdAt ? formatDate(approval.createdAt) : '-'}
                  </td>
                  <td>
                    <span
                      className="badge"
                      style={{
                        background: 'rgba(255, 159, 10, 0.15)',
                        color: '#ff9f0a',
                        fontSize: 11,
                      }}
                    >
                      {approval.status}
                    </span>
                  </td>
                  <td>
                    <div style={{ display: 'flex', gap: 8 }}>
                      <button
                        className="btn btn-sm"
                        style={{
                          background: 'rgba(52, 199, 89, 0.1)',
                          color: '#34c759',
                          border: 'none',
                          cursor: 'pointer',
                          display: 'flex',
                          alignItems: 'center',
                          gap: 4,
                        }}
                        onClick={() => handleApproveClick(approval.id)}
                        disabled={approveMutation.isPending}
                      >
                        <Check size={14} />
                        Approve
                      </button>
                      <button
                        className="btn btn-sm"
                        style={{
                          background: 'rgba(255, 69, 58, 0.1)',
                          color: '#ff453a',
                          border: 'none',
                          cursor: 'pointer',
                          display: 'flex',
                          alignItems: 'center',
                          gap: 4,
                        }}
                        onClick={() => handleRejectClick(approval.id)}
                        disabled={rejectMutation.isPending}
                      >
                        <X size={14} />
                        Reject
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Approval Dialog */}
      <ConfirmDialog
        open={isDialogOpen && approvalState.type === 'approve'}
        title="Approve Spread"
        description="Are you sure you want to approve this spread?"
        confirmText="Approve"
        cancelText="Cancel"
        onConfirm={handleConfirmApproval}
        onCancel={handleCancel}
      />

      {/* Rejection Dialog with Comment */}
      {isDialogOpen && approvalState.type === 'reject' && (
        <div className="modal-overlay">
          <div className="modal" style={{ maxWidth: 480 }}>
            <div className="card-title">Reject Spread</div>
            <p style={{ marginTop: 12, marginBottom: 16, color: 'var(--text-muted)', fontSize: 14 }}>
              Please provide a reason for rejection (required):
            </p>
            <textarea
              value={approvalState.comment}
              onChange={(e) =>
                setApprovalState({ ...approvalState, comment: e.target.value })
              }
              placeholder="Enter rejection reason..."
              style={{
                width: '100%',
                padding: 10,
                border: '1px solid var(--border-subtle)',
                borderRadius: 4,
                background: 'var(--bg-input)',
                color: 'var(--text-primary)',
                fontFamily: 'inherit',
                fontSize: 14,
                minHeight: 100,
                marginBottom: 16,
              }}
            />
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
              <button className="btn btn-secondary" onClick={handleCancel}>
                Cancel
              </button>
              <button
                className="btn btn-danger"
                onClick={handleConfirmRejection}
                disabled={rejectMutation.isPending || !approvalState.comment.trim()}
              >
                {rejectMutation.isPending ? 'Rejecting...' : 'Confirm Rejection'}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
