'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import { useParams, useRouter } from 'next/navigation'
import { ArrowLeft, CheckCheck, Clock, Lock, RotateCcw, Send, Shield, Sparkles, Unlock } from 'lucide-react'
import {
  useAcceptAll,
  useAcquireLock,
  useLockHeartbeat,
  useProcessSpread,
  useReleaseLock,
  useRollbackSpread,
  useSpreadHistory,
  useSpreadItem,
  useSpreadLock,
  useSpreadValues,
  useSubmitSpread,
  useUpdateSpreadValue,
} from '@/services/spreadApi'
import { useToast } from '@/components/ui/Toast'
import { PdfViewer } from '@/components/spreading/PdfViewer'
import { SpreadTable } from '@/components/spreading/SpreadTable'
import { ValidationPanel } from '@/components/spreading/ValidationPanel'
import { useSpreadStore } from '@/stores/spreadStore'
import type { SpreadValue, MappingResult } from '@/types/spread'

type RightPanel = 'values' | 'history' | 'validation'

export default function SpreadingWorkspacePage() {
  const router = useRouter()
  const params = useParams<{ spreadId: string }>()
  const spreadId = params.spreadId
  const { showToast } = useToast()

  // ── Queries ──────────────────────────────────────────────────────────
  const spreadQuery = useSpreadItem(spreadId)
  const valuesQuery = useSpreadValues(spreadId)
  const historyQuery = useSpreadHistory(spreadId)
  const lockQuery = useSpreadLock(spreadId)

  // ── Mutations ────────────────────────────────────────────────────────
  const processMutation = useProcessSpread()
  const acceptAllMutation = useAcceptAll(spreadId)
  const submitMutation = useSubmitSpread()
  const rollbackMutation = useRollbackSpread()
  const updateValueMutation = useUpdateSpreadValue(spreadId)
  const acquireLockMutation = useAcquireLock()
  const releaseLockMutation = useReleaseLock()
  const heartbeatMutation = useLockHeartbeat()

  // ── Store ────────────────────────────────────────────────────────────
  const { highlightedSourcePage, selectCell, highlightSource, clearHighlight, selectedCellCode } = useSpreadStore()

  // ── Local state ──────────────────────────────────────────────────────
  const [draftComments, setDraftComments] = useState('')
  const [rightPanel, setRightPanel] = useState<RightPanel>('values')
  const [validationResult, setValidationResult] = useState<MappingResult | null>(null)
  const [pdfPage, setPdfPage] = useState<number>(1)

  const values = valuesQuery.data ?? []
  const history = historyQuery.data?.versions ?? []
  const spread = spreadQuery.data
  const lockInfo = lockQuery.data
  const isLockedByOther = lockInfo?.locked === true && lockInfo.lockedBy !== undefined
  const isLockedByMe = lockInfo?.locked === true // simplified check

  // ── Lock heartbeat ───────────────────────────────────────────────────
  useEffect(() => {
    if (!isLockedByMe) return
    const interval = setInterval(() => heartbeatMutation.mutate(spreadId), 5 * 60 * 1000)
    return () => clearInterval(interval)
  }, [isLockedByMe, spreadId]) // eslint-disable-line react-hooks/exhaustive-deps

  // ── Confidence summary ───────────────────────────────────────────────
  const confidenceSummary = useMemo(() => {
    const high = values.filter((v) => v.confidenceLevel === 'HIGH').length
    const medium = values.filter((v) => v.confidenceLevel === 'MEDIUM').length
    const low = values.filter((v) => v.confidenceLevel === 'LOW').length
    return { high, medium, low, total: values.length }
  }, [values])

  // ── Handlers ──────────────────────────────────────────────────────────
  const handleCellClick = useCallback(
    (value: SpreadValue) => {
      selectCell(value.itemCode)
      if (value.sourcePage) {
        highlightSource(value.sourcePage, { x: 0, y: 0, width: 0, height: 0 })
        setPdfPage(value.sourcePage)
      }
    },
    [selectCell, highlightSource]
  )

  const handleValueEdit = useCallback(
    async (valueId: string, newValue: number | undefined) => {
      await updateValueMutation.mutateAsync({
        valueId,
        mappedValue: newValue,
        overrideComment: 'Manual edit',
        expressionType: 'MANUAL',
      })
      showToast('Value updated', 'success')
    },
    [updateValueMutation, showToast]
  )

  const handleProcess = useCallback(async () => {
    const result = await processMutation.mutateAsync(spreadId)
    setValidationResult(result)
    setRightPanel('validation')
    showToast('Processing complete', 'success')
  }, [processMutation, spreadId, showToast])

  const handleLockToggle = useCallback(async () => {
    if (isLockedByMe) {
      await releaseLockMutation.mutateAsync(spreadId)
      showToast('Lock released', 'info')
    } else {
      await acquireLockMutation.mutateAsync(spreadId)
      showToast('Lock acquired', 'success')
    }
  }, [isLockedByMe, spreadId, acquireLockMutation, releaseLockMutation, showToast])

  if (spreadQuery.isLoading) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh', color: 'var(--text-muted)' }}>
        Loading workspace...
      </div>
    )
  }

  return (
    <div style={{ height: '100vh', display: 'flex', flexDirection: 'column' }}>
      {/* ── Top Toolbar ────────────────────────────────────────────── */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          padding: '8px 12px',
          borderBottom: '1px solid var(--border-subtle)',
          background: 'var(--bg-secondary)',
        }}
      >
        <button className="btn btn-ghost btn-sm" onClick={() => router.back()}>
          <ArrowLeft size={14} />
        </button>
        <span style={{ fontWeight: 600, fontSize: 14 }}>Spread Workspace</span>
        <span
          className="badge"
          style={{
            background: spread?.status === 'SUBMITTED' ? 'var(--color-accent)' : 'var(--bg-elevated)',
            fontSize: 11,
          }}
        >
          {spread?.status}
        </span>
        <span style={{ color: 'var(--text-muted)', fontSize: 12 }}>v{spread?.currentVersion}</span>

        {/* Lock indicator */}
        {lockInfo?.locked && (
          <span
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 4,
              fontSize: 11,
              color: '#ff9f0a',
              marginLeft: 8,
            }}
          >
            <Lock size={12} />
            {lockInfo.lockedByName ?? lockInfo.lockedBy}
          </span>
        )}

        <div style={{ flex: 1 }} />

        {/* Action buttons */}
        <button className="btn btn-ghost btn-sm" onClick={handleLockToggle}>
          {isLockedByMe ? <Unlock size={14} /> : <Lock size={14} />}
          {isLockedByMe ? 'Unlock' : 'Lock'}
        </button>
        <button className="btn btn-ghost btn-sm" onClick={handleProcess} disabled={processMutation.isPending}>
          <Sparkles size={14} />
          Process
        </button>
        <button className="btn btn-ghost btn-sm" onClick={() => acceptAllMutation.mutate('HIGH')}>
          <CheckCheck size={14} />
          Accept High
        </button>
        <button
          className="btn btn-primary btn-sm"
          onClick={() => {
            submitMutation.mutate({ spreadId, comments: draftComments, overrideValidationWarnings: true })
            showToast('Spread submitted', 'success')
          }}
        >
          <Send size={14} />
          Submit
        </button>
      </div>

      {/* ── Info Bar ───────────────────────────────────────────────── */}
      <div
        style={{
          display: 'flex',
          gap: 16,
          padding: '6px 12px',
          borderBottom: '1px solid var(--border-subtle)',
          fontSize: 12,
          color: 'var(--text-secondary)',
        }}
      >
        <span>Statement: {spread?.statementDate}</span>
        <span style={{ color: '#34c759' }}>High: {confidenceSummary.high}</span>
        <span style={{ color: '#ff9f0a' }}>Medium: {confidenceSummary.medium}</span>
        <span style={{ color: '#ff453a' }}>Low: {confidenceSummary.low}</span>
        <span>Total: {confidenceSummary.total}</span>
      </div>

      {/* ── Dual-Pane Content ──────────────────────────────────────── */}
      <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
        {/* Left: PDF Viewer (45%) */}
        <div
          style={{
            width: '45%',
            borderRight: '2px solid var(--border-subtle)',
            display: 'flex',
            flexDirection: 'column',
          }}
        >
          {spread?.documentId ? (
            <PdfViewer
              documentId={spread.documentId}
              currentPage={highlightedSourcePage ?? pdfPage}
              onPageChange={setPdfPage}
            />
          ) : (
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                height: '100%',
                color: 'var(--text-muted)',
              }}
            >
              No document attached
            </div>
          )}
        </div>

        {/* Right: Tabbed Panel (55%) */}
        <div style={{ width: '55%', display: 'flex', flexDirection: 'column' }}>
          {/* Tab bar */}
          <div
            style={{
              display: 'flex',
              gap: 0,
              borderBottom: '1px solid var(--border-subtle)',
              background: 'var(--bg-secondary)',
            }}
          >
            {[
              { key: 'values' as RightPanel, label: 'Spread Values', icon: <Sparkles size={13} /> },
              { key: 'validation' as RightPanel, label: 'Validation', icon: <Shield size={13} /> },
              { key: 'history' as RightPanel, label: 'History', icon: <Clock size={13} /> },
            ].map((tab) => (
              <button
                key={tab.key}
                onClick={() => setRightPanel(tab.key)}
                style={{
                  padding: '8px 16px',
                  fontSize: 12,
                  fontWeight: rightPanel === tab.key ? 600 : 400,
                  border: 'none',
                  background: rightPanel === tab.key ? 'var(--bg-primary)' : 'transparent',
                  color: rightPanel === tab.key ? 'var(--text-primary)' : 'var(--text-muted)',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 4,
                  borderBottom: rightPanel === tab.key ? '2px solid var(--color-accent)' : '2px solid transparent',
                }}
              >
                {tab.icon}
                {tab.label}
              </button>
            ))}
          </div>

          {/* Panel content */}
          <div style={{ flex: 1, overflow: 'auto' }}>
            {rightPanel === 'values' && (
              <SpreadTable
                values={values}
                isLocked={isLockedByOther}
                onCellClick={handleCellClick}
                onValueEdit={handleValueEdit}
                selectedCellCode={selectedCellCode}
              />
            )}

            {rightPanel === 'validation' && (
              <div style={{ padding: 12 }}>
                <ValidationPanel
                  validations={validationResult?.validations ?? []}
                  isLoading={processMutation.isPending}
                />
              </div>
            )}

            {rightPanel === 'history' && (
              <div style={{ padding: 12 }}>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  {history.map((h) => (
                    <div
                      key={h.versionNumber}
                      style={{
                        padding: 10,
                        border: '1px solid var(--border-subtle)',
                        borderRadius: 8,
                      }}
                    >
                      <div style={{ fontWeight: 600 }}>
                        v{h.versionNumber} - {h.action}
                      </div>
                      <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
                        {h.createdBy} | {new Date(h.createdAt).toLocaleString()}
                      </div>
                      <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>{h.comments ?? '-'}</div>
                      <button
                        className="btn btn-ghost btn-sm"
                        style={{ marginTop: 8 }}
                        onClick={async () => {
                          await rollbackMutation.mutateAsync({
                            spreadId,
                            version: h.versionNumber,
                            comments: 'Rollback from UI',
                          })
                          showToast(`Rolled back to v${h.versionNumber}`, 'success')
                        }}
                      >
                        <RotateCcw size={13} />
                        Rollback
                      </button>
                    </div>
                  ))}
                  {history.length === 0 && (
                    <span style={{ color: 'var(--text-muted)', fontSize: 12 }}>
                      No history snapshots yet.
                    </span>
                  )}
                </div>

                <div className="input-group" style={{ marginTop: 12 }}>
                  <label>Submission Comments</label>
                  <textarea
                    className="input"
                    rows={3}
                    value={draftComments}
                    onChange={(e) => setDraftComments(e.target.value)}
                  />
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
