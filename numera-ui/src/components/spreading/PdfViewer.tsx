'use client'

import { useCallback, useMemo } from 'react'
import { ChevronLeft, ChevronRight, ZoomIn, ZoomOut, Maximize2 } from 'lucide-react'

interface PdfViewerProps {
  documentId: string
  currentPage: number | null
  onPageChange?: (page: number) => void
  highlightCoords?: { x: number; y: number; width: number; height: number } | null
}

export function PdfViewer({ documentId, currentPage, onPageChange }: PdfViewerProps) {
  const pdfUrl = useMemo(() => `/api/documents/${documentId}/download`, [documentId])

  const goTo = useCallback(
    (page: number) => {
      if (onPageChange && page >= 1) onPageChange(page)
    },
    [onPageChange]
  )

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      {/* PDF toolbar */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 6,
          padding: '6px 10px',
          borderBottom: '1px solid var(--border-subtle)',
          fontSize: 12,
          background: 'var(--bg-secondary)',
        }}
      >
        <button
          className="btn btn-ghost btn-sm"
          onClick={() => goTo((currentPage ?? 1) - 1)}
          disabled={!currentPage || currentPage <= 1}
        >
          <ChevronLeft size={14} />
        </button>
        <span style={{ minWidth: 60, textAlign: 'center' }}>
          Page {currentPage ?? 1}
        </span>
        <button className="btn btn-ghost btn-sm" onClick={() => goTo((currentPage ?? 1) + 1)}>
          <ChevronRight size={14} />
        </button>
        <div style={{ flex: 1 }} />
        <button className="btn btn-ghost btn-sm" title="Zoom In">
          <ZoomIn size={14} />
        </button>
        <button className="btn btn-ghost btn-sm" title="Zoom Out">
          <ZoomOut size={14} />
        </button>
        <a
          href={pdfUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="btn btn-ghost btn-sm"
          title="Open in new tab"
        >
          <Maximize2 size={14} />
        </a>
      </div>

      {/* PDF content - using iframe for native PDF rendering with page navigation */}
      <div style={{ flex: 1, overflow: 'hidden' }}>
        <iframe
          src={`${pdfUrl}#page=${currentPage ?? 1}`}
          style={{ width: '100%', height: '100%', border: 'none' }}
          title="Financial Statement PDF"
        />
      </div>
    </div>
  )
}
