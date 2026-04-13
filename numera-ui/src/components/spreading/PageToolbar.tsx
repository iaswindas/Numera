'use client'

import { useState, useCallback } from 'react'
import { RotateCcw, Scissors, Merge, Eraser } from 'lucide-react'
import { usePageOperation } from '@/services/spreadApi'
import type { PageOperationRequest } from '@/types/spread'

interface PageToolbarProps {
  documentId: string
  currentPage: number
  totalPages: number
  onOperationComplete?: () => void
}

export function PageToolbar({ documentId, currentPage, totalPages, onOperationComplete }: PageToolbarProps) {
  const [pendingOp, setPendingOp] = useState<string | null>(null)
  const pageOperation = usePageOperation(documentId)

  const execute = useCallback(
    async (op: PageOperationRequest, label: string) => {
      setPendingOp(label)
      try {
        await pageOperation.mutateAsync(op)
        onOperationComplete?.()
      } finally {
        setPendingOp(null)
      }
    },
    [pageOperation, onOperationComplete],
  )

  const handleRotate = useCallback(() => {
    void execute(
      { type: 'ROTATE', pageNumbers: [currentPage], rotationDegrees: 90 },
      'rotate',
    )
  }, [execute, currentPage])

  const handleSplit = useCallback(() => {
    if (totalPages < 2) return
    void execute(
      { type: 'SPLIT', pageNumbers: [currentPage], splitAtPage: currentPage },
      'split',
    )
  }, [execute, currentPage, totalPages])

  const handleMerge = useCallback(() => {
    if (currentPage >= totalPages) return
    void execute(
      { type: 'MERGE', pageNumbers: [currentPage, currentPage + 1] },
      'merge',
    )
  }, [execute, currentPage, totalPages])

  const handleClean = useCallback(() => {
    void execute(
      { type: 'CLEAN', pageNumbers: [currentPage] },
      'clean',
    )
  }, [execute, currentPage])

  const isDisabled = !!pendingOp || pageOperation.isPending

  return (
    <div
      style={{
        display: 'flex',
        gap: 4,
        alignItems: 'center',
        padding: '4px 0',
      }}
    >
      <button
        className="btn btn-ghost btn-sm"
        onClick={handleRotate}
        disabled={isDisabled}
        title="Rotate page 90°"
      >
        <RotateCcw size={13} />
        {pendingOp === 'rotate' ? 'Rotating...' : 'Rotate'}
      </button>
      <button
        className="btn btn-ghost btn-sm"
        onClick={handleSplit}
        disabled={isDisabled || totalPages < 2}
        title="Split document at current page"
      >
        <Scissors size={13} />
        {pendingOp === 'split' ? 'Splitting...' : 'Split'}
      </button>
      <button
        className="btn btn-ghost btn-sm"
        onClick={handleMerge}
        disabled={isDisabled || currentPage >= totalPages}
        title="Merge current page with the next"
      >
        <Merge size={13} />
        {pendingOp === 'merge' ? 'Merging...' : 'Merge'}
      </button>
      <button
        className="btn btn-ghost btn-sm"
        onClick={handleClean}
        disabled={isDisabled}
        title="Clean page (remove artifacts)"
      >
        <Eraser size={13} />
        {pendingOp === 'clean' ? 'Cleaning...' : 'Clean'}
      </button>
    </div>
  )
}
