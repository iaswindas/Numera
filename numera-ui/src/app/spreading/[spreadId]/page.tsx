'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useParams, useRouter } from 'next/navigation'
import { ArrowLeft, CheckCheck, Clock, Columns, Copy, Lock, MessageCircle, RotateCcw, Send, Shield, Sparkles, Unlock } from 'lucide-react'
import {
  useAcceptAll,
  useAcquireLock,
  useAutofillFromBase,
  useBasePeriod,
  useLockHeartbeat,
  useProcessSpread,
  useReleaseLock,
  useRollbackSpread,
  useSpreadHistory,
  useSpreadItem,
  useSpreadLock,
  useSpreadValues,
  useSpreadVariance,
  useSubmitSpread,
  useUpdateSpreadValue,
} from '@/services/spreadApi'
import { useToast } from '@/components/ui/Toast'
import { PdfViewer } from '@/components/spreading/PdfViewer'
import { SpreadTable } from '@/components/spreading/SpreadTable'
import { ValidationPanel } from '@/components/spreading/ValidationPanel'
import { LockBanner } from '@/components/spreading/LockBanner'
import { CategoryNav } from '@/components/spreading/CategoryNav'
import { ExpressionEditor } from '@/components/spreading/ExpressionEditor'
import { PageToolbar } from '@/components/spreading/PageToolbar'
import { CommentPanel } from '@/components/spreading/CommentPanel'
import { LoadHistoricalModal } from '@/components/spreading/LoadHistoricalModal'
import { useSpreadStore } from '@/stores/spreadStore'
import { useWebSocketSubscription } from '@/hooks/useWebSocket'
import type { SpreadValue, MappingResult, BoundingBox, Zone } from '@/types/spread'

type RightPanel = 'values' | 'history' | 'validation' | 'comments'

const zoneToCategory: Record<Zone['type'], string> = {
  bs: 'Balance Sheet',
  is: 'Income Statement',
  cf: 'Cash Flow',
  notes: 'Notes',
  other: 'All',
}

function asBoundingBox(value: unknown): BoundingBox | null {
  if (!value || typeof value !== 'object') return null
  const box = value as Record<string, unknown>
  const x = Number(box.x)
  const y = Number(box.y)
  const width = Number(box.width)
  const height = Number(box.height)
  if ([x, y, width, height].some((n) => Number.isNaN(n))) return null
  return { x, y, width, height }
}

export default function SpreadingWorkspacePage() {
  const router = useRouter()
  const params = useParams<{ spreadId: string }>()
  const spreadId = params.spreadId
  const queryClient = useQueryClient()
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

  // ── Subsequent Spreading ─────────────────────────────────────────────
  const basePeriodQuery = useBasePeriod(spreadId)
  const autofillMutation = useAutofillFromBase(spreadId)
  const basePeriodId = basePeriodQuery.data?.basePeriodId ?? null

  // ── Store ────────────────────────────────────────────────────────────
  const { highlightedSourcePage, selectCell, highlightSource, selectedCellCode } = useSpreadStore()

  // ── Local state ──────────────────────────────────────────────────────
  const [draftComments, setDraftComments] = useState('')
  const [rightPanel, setRightPanel] = useState<RightPanel>('values')
  const [validationResult, setValidationResult] = useState<MappingResult | null>(null)
  const [pdfPage, setPdfPage] = useState<number>(1)
  const [showVariance, setShowVariance] = useState(false)
  const [showOnlyMapped, setShowOnlyMapped] = useState(false)
  const [activeCategory, setActiveCategory] = useState<string>('All')
  const [isExpressionEditorOpen, setIsExpressionEditorOpen] = useState(false)
  const [editingValue, setEditingValue] = useState<SpreadValue | null>(null)
  const [isSplitView, setIsSplitView] = useState(false)
  const [splitPdfPage, setSplitPdfPage] = useState<number>(1)
  const [showSmartFill, setShowSmartFill] = useState(false)
  const [showCurrency, setShowCurrency] = useState(false)
  const [isHistoricalModalOpen, setIsHistoricalModalOpen] = useState(false)

  const values = valuesQuery.data ?? []
  const history = historyQuery.data?.versions ?? []
  const spread = spreadQuery.data
  const lockInfo = lockQuery.data
  const isLockedByOther = lockInfo?.locked === true && lockInfo.lockedBy !== undefined
  const isLockedByMe = lockInfo?.locked === true // simplified check

  // ── Variance Query ─────────────────────────────────────────────────────
  const compareSpreadId = basePeriodId
  const varianceQuery = useSpreadVariance(spreadId, compareSpreadId ?? '')
  const varianceData = varianceQuery.data ?? []

  useWebSocketSubscription(spreadId ? `/topic/spread/${spreadId}/lock` : null, () => {
    void queryClient.invalidateQueries({ queryKey: ['spread', spreadId, 'lock'] })
  })

  useWebSocketSubscription(spreadId ? `/topic/spread/${spreadId}/values` : null, () => {
    void queryClient.invalidateQueries({ queryKey: ['spread', spreadId, 'values'] })
    void queryClient.invalidateQueries({ queryKey: ['spread', spreadId] })
  })

  // ── Categories ─────────────────────────────────────────────────────────
  const categories = useMemo(() => {
    const cats = new Set(['All'])
    values.forEach((v) => {
      if (v.label) {
        const category = v.label.split('|')[0]?.trim()
        if (category) cats.add(category)
      }
    })
    return Array.from(cats).sort()
  }, [values])

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
        const expressionSource = asBoundingBox(value.expressionDetail?.['sourceBoundingBox'])
        highlightSource(value.sourcePage, expressionSource ?? { x: 24, y: 24, width: 260, height: 34 })
        setPdfPage(value.sourcePage)
      }
    },
    [selectCell, highlightSource]
  )

  const handleCellDoubleClick = useCallback((value: SpreadValue) => {
    setEditingValue(value)
    setIsExpressionEditorOpen(true)
  }, [])

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

  const handleExpressionSave = useCallback(
    async (expression: string, computedValue: number) => {
      if (!editingValue) return

      await updateValueMutation.mutateAsync({
        valueId: editingValue.id,
        mappedValue: computedValue,
        overrideComment: `Formula: ${expression}`,
        expressionType: 'FORMULA',
      })

      showToast('Formula saved', 'success')
    },
    [editingValue, updateValueMutation, showToast]
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

  const handleAutofill = useCallback(async () => {
    if (!basePeriodId) return
    const result = await autofillMutation.mutateAsync(basePeriodId)
    showToast(`Autofilled ${result.filledCount} values from prior period`, 'success')
  }, [basePeriodId, autofillMutation, showToast])

  if (spreadQuery.isLoading) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh', color: 'var(--text-muted)' }}>
        Loading workspace...
      </div>
    )
  }

  return (
    <div style={{ height: '100vh', display: 'flex', flexDirection: 'column' }}>
      {/* ── Lock Banner ────────────────────────────────────────────── */}
      <LockBanner lockedBy={isLockedByOther ? lockInfo?.lockedByName ?? lockInfo?.lockedBy ?? null : null} />

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
        {basePeriodId && (
          <button className="btn btn-ghost btn-sm" onClick={handleAutofill} disabled={autofillMutation.isPending}>
            <Copy size={14} />
            {autofillMutation.isPending ? 'Autofilling...' : 'Autofill from Prior'}
          </button>
        )}
        <button className="btn btn-ghost btn-sm" onClick={() => setIsHistoricalModalOpen(true)}>
          <Clock size={14} />
          Historical
        </button>
        <button
          className="btn btn-ghost btn-sm"
          onClick={() => setIsSplitView((prev) => !prev)}
          title={isSplitView ? 'Single document view' : 'Split document view'}
        >
          <Columns size={14} />
          {isSplitView ? 'Single' : 'Split'}
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
        {basePeriodId && (
          <span style={{ color: 'var(--color-accent)' }}>Prior period available</span>
        )}
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
            <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
              {/* Page operations toolbar */}
              <div style={{ padding: '4px 10px', borderBottom: '1px solid var(--border-subtle)', background: 'var(--bg-secondary)' }}>
                <PageToolbar
                  documentId={spread.documentId}
                  currentPage={highlightedSourcePage ?? pdfPage}
                  totalPages={999}
                  onOperationComplete={() => showToast('Page operation completed', 'success')}
                />
              </div>

              {/* Primary PDF viewer */}
              <div style={{ flex: isSplitView ? undefined : 1, height: isSplitView ? '50%' : undefined }}>
                <PdfViewer
                  documentId={spread.documentId}
                  documentUrl={`/api/documents/${spread.documentId}/download`}
                  currentPage={highlightedSourcePage ?? pdfPage}
                  pageNumber={highlightedSourcePage ?? pdfPage}
                  scale={1}
                  onZoneNavigate={(zoneType) => setActiveCategory(zoneToCategory[zoneType] ?? 'All')}
                  onPageChange={setPdfPage}
                />
              </div>

              {/* Secondary PDF viewer (split view) */}
              {isSplitView && (
                <div style={{ height: '50%', borderTop: '2px solid var(--border-subtle)' }}>
                  <PdfViewer
                    documentId={spread.documentId}
                    documentUrl={`/api/documents/${spread.documentId}/download`}
                    currentPage={splitPdfPage}
                    pageNumber={splitPdfPage}
                    scale={1}
                    onPageChange={setSplitPdfPage}
                  />
                </div>
              )}
            </div>
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
              { key: 'comments' as RightPanel, label: 'Comments', icon: <MessageCircle size={13} /> },
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
              <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
                {/* Controls bar */}
                <div
                  style={{
                    padding: '10px 12px',
                    borderBottom: '1px solid var(--border-subtle)',
                    background: 'var(--bg-secondary)',
                    display: 'flex',
                    gap: 12,
                    alignItems: 'center',
                    fontSize: 12,
                    flexWrap: 'wrap',
                  }}
                >
                  <label style={{ display: 'flex', alignItems: 'center', gap: 6, cursor: 'pointer' }}>
                    <input
                      type="checkbox"
                      checked={showOnlyMapped}
                      onChange={(e) => setShowOnlyMapped(e.target.checked)}
                      style={{ cursor: 'pointer' }}
                    />
                    <span>{showOnlyMapped ? 'Showing Mapped Only' : 'Showing All Rows'}</span>
                  </label>
                  <label style={{ display: 'flex', alignItems: 'center', gap: 6, cursor: 'pointer' }}>
                    <input
                      type="checkbox"
                      checked={showVariance}
                      onChange={(e) => setShowVariance(e.target.checked)}
                      style={{ cursor: 'pointer' }}
                    />
                    <span>Show Variance</span>
                  </label>
                  <label style={{ display: 'flex', alignItems: 'center', gap: 6, cursor: 'pointer' }}>
                    <input
                      type="checkbox"
                      checked={showSmartFill}
                      onChange={(e) => setShowSmartFill(e.target.checked)}
                      style={{ cursor: 'pointer' }}
                    />
                    <span>SmartFill</span>
                  </label>
                  <label style={{ display: 'flex', alignItems: 'center', gap: 6, cursor: 'pointer' }}>
                    <input
                      type="checkbox"
                      checked={showCurrency}
                      onChange={(e) => setShowCurrency(e.target.checked)}
                      style={{ cursor: 'pointer' }}
                    />
                    <span>Currency</span>
                  </label>
                </div>

                {/* Category nav */}
                <div style={{ padding: '8px 12px', borderBottom: '1px solid var(--border-subtle)', background: 'var(--bg-secondary)' }}>
                  <CategoryNav
                    categories={categories}
                    activeCategory={activeCategory}
                    onCategoryClick={setActiveCategory}
                  />
                </div>

              <SpreadTable
                values={values}
                isLocked={isLockedByOther}
                onCellClick={handleCellClick}
                onCellDoubleClick={handleCellDoubleClick}
                onValueEdit={handleValueEdit}
                selectedCellCode={selectedCellCode}
                showVariance={showVariance}
                varianceData={varianceData}
                showOnlyMapped={showOnlyMapped}
                showSmartFill={showSmartFill}
                showCurrency={showCurrency}
                spreadId={spreadId}
              />
              </div>
            )}

            {rightPanel === 'validation' && (
              <div style={{ padding: 12 }}>
                <ValidationPanel
                  validations={validationResult?.validations ?? []}
                  isLoading={processMutation.isPending}
                />
              </div>
            )}

            {rightPanel === 'comments' && (
              <CommentPanel
                spreadId={spreadId}
                selectedValueId={editingValue?.id ?? null}
                onNavigateToSource={(url) => {
                  const pageMatch = url.match(/page:(\d+)/i)
                  if (pageMatch) {
                    setPdfPage(Number(pageMatch[1]))
                  }
                }}
              />
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

      <ExpressionEditor
        open={isExpressionEditorOpen}
        targetValue={editingValue}
        allValues={values}
        onClose={() => {
          setIsExpressionEditorOpen(false)
          setEditingValue(null)
        }}
        onSave={handleExpressionSave}
      />

      <LoadHistoricalModal
        open={isHistoricalModalOpen}
        spreadId={spreadId}
        customerId={spread?.customerId ?? ''}
        onClose={() => setIsHistoricalModalOpen(false)}
        onLoaded={(count) => showToast(`Loaded ${count} historical column(s)`, 'success')}
      />
    </div>
  )
}
