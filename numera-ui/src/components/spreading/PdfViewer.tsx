'use client'

import { useEffect, useRef, useState } from 'react'
import * as pdfjsLib from 'pdfjs-dist'
import { ChevronLeft, ChevronRight, ZoomIn, ZoomOut, Maximize2, Eye, EyeOff } from 'lucide-react'
import { fetchApi } from '@/services/api'
import { useSpreadStore } from '@/stores/spreadStore'
import { ZoneOverlay } from './ZoneOverlay'
import { SourceHighlight } from './SourceHighlight'
import type { BoundingBox, Zone } from '@/types/spread'

pdfjsLib.GlobalWorkerOptions.workerSrc = `//cdnjs.cloudflare.com/ajax/libs/pdf.js/${pdfjsLib.version}/pdf.worker.min.js`

export interface PdfViewerProps {
  documentId?: string
  documentUrl: string
  currentPage?: number
  pageNumber: number
  scale: number
  onZoneClick?: (zone: Zone) => void
  onZoneNavigate?: (zoneType: Zone['type']) => void
  highlightedCellId?: string
  onPageChange?: (page: number) => void
}

interface ZoneApiResponse {
  documentId: string
  zones: Array<{
    id: string
    pageNumber: number | null
    zoneType: string
    zoneLabel: string | null
    boundingBox: BoundingBox | null
    confidenceScore: number | null
  }>
}

function mapZoneType(zoneType: string): Zone['type'] {
  const normalized = zoneType.toUpperCase()
  if (normalized.includes('BALANCE')) return 'bs'
  if (normalized.includes('INCOME')) return 'is'
  if (normalized.includes('CASH')) return 'cf'
  if (normalized.includes('NOTES')) return 'notes'
  return 'other'
}

export function PdfViewer({
  documentId,
  documentUrl,
  currentPage,
  pageNumber,
  scale,
  onZoneClick,
  onZoneNavigate,
  onPageChange,
}: PdfViewerProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const [totalPages, setTotalPages] = useState(0)
  const [isLoading, setIsLoading] = useState(false)
  const [currentScale, setCurrentScale] = useState(scale)
  const [showZones, setShowZones] = useState(true)
  const [zones, setZones] = useState<Zone[]>([])
  const { highlightedSourcePage, highlightedSourceCoords } = useSpreadStore()
  const pdfRef = useRef<pdfjsLib.PDFDocumentProxy | null>(null)
  const effectiveUrl = documentUrl || (documentId ? `/api/documents/${documentId}/download` : '')
  const activePage = currentPage ?? pageNumber

  useEffect(() => {
    if (!documentId) {
      setZones([])
      return
    }

    let isMounted = true
    const loadZones = async () => {
      try {
        const response = await fetchApi<ZoneApiResponse>(`/documents/${documentId}/zones`)
        if (!isMounted) return
        const mapped = response.zones
          .filter((zone) => zone.pageNumber != null && zone.boundingBox != null)
          .map((zone) => ({
            id: zone.id,
            type: mapZoneType(zone.zoneType),
            pageNumber: zone.pageNumber ?? 1,
            boundingBox: zone.boundingBox as BoundingBox,
            confidence: zone.confidenceScore ?? 0,
          }))
        setZones(mapped)
      } catch (error) {
        console.error('Failed to load zones:', error)
        setZones([])
      }
    }

    void loadZones()
    return () => {
      isMounted = false
    }
  }, [documentId])

  // Load PDF on mount
  useEffect(() => {
    let isMounted = true
    const loadPdf = async () => {
      try {
        setIsLoading(true)
        const pdf = await pdfjsLib.getDocument(effectiveUrl).promise
        if (isMounted) {
          pdfRef.current = pdf
          setTotalPages(pdf.numPages)
        }
      } catch (error) {
        console.error('Error loading PDF:', error)
      } finally {
        if (isMounted) {
          setIsLoading(false)
        }
      }
    }
    loadPdf()
    return () => {
      isMounted = false
    }
  }, [effectiveUrl])

  // Render PDF page on canvas
  useEffect(() => {
    if (!canvasRef.current || !pdfRef.current || activePage < 1 || activePage > totalPages) {
      return
    }

    const renderPage = async () => {
      try {
        setIsLoading(true)
        const page = await pdfRef.current!.getPage(activePage)
        const viewport = page.getViewport({ scale: currentScale })

        canvasRef.current!.width = viewport.width
        canvasRef.current!.height = viewport.height

        const renderContext = {
          canvas: canvasRef.current!,
          canvasContext: canvasRef.current!.getContext('2d')!,
          viewport,
        }
        await page.render(renderContext).promise
      } catch (error) {
        console.error('Error rendering page:', error)
      } finally {
        setIsLoading(false)
      }
    }

    renderPage()
  }, [activePage, totalPages, currentScale])

  const handleZoom = (direction: 'in' | 'out') => {
    const newScale = direction === 'in' ? currentScale + 0.1 : Math.max(0.5, currentScale - 0.1)
    setCurrentScale(newScale)
  }

  const goToPage = (page: number) => {
    if (page >= 1 && page <= totalPages && onPageChange) {
      onPageChange(page)
    }
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      {/* Toolbar */}
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
          onClick={() => goToPage(activePage - 1)}
          disabled={activePage <= 1 || isLoading}
          title="Previous page"
        >
          <ChevronLeft size={14} />
        </button>
        <span style={{ minWidth: 70, textAlign: 'center', fontSize: 12 }}>
          {isLoading ? 'Loading...' : `${activePage} / ${totalPages}`}
        </span>
        <button
          className="btn btn-ghost btn-sm"
          onClick={() => goToPage(activePage + 1)}
          disabled={activePage >= totalPages || isLoading}
          title="Next page"
        >
          <ChevronRight size={14} />
        </button>
        <div style={{ flex: 1 }} />
        <button
          className="btn btn-ghost btn-sm"
          onClick={() => setShowZones((prev) => !prev)}
          title={showZones ? 'Hide zones' : 'Show zones'}
        >
          {showZones ? <EyeOff size={14} /> : <Eye size={14} />}
          {showZones ? 'Hide Zones' : 'Show Zones'}
        </button>
        <button
          className="btn btn-ghost btn-sm"
          onClick={() => handleZoom('in')}
          disabled={isLoading}
          title="Zoom in"
        >
          <ZoomIn size={14} />
        </button>
        <span style={{ minWidth: 45, textAlign: 'center', fontSize: 11 }}>
          {Math.round(currentScale * 100)}%
        </span>
        <button
          className="btn btn-ghost btn-sm"
          onClick={() => handleZoom('out')}
          disabled={currentScale <= 0.5 || isLoading}
          title="Zoom out"
        >
          <ZoomOut size={14} />
        </button>
        <a
          href={effectiveUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="btn btn-ghost btn-sm"
          title="Open in new tab"
        >
          <Maximize2 size={14} />
        </a>
      </div>

      {/* Canvas container */}
      <div
        style={{
          flex: 1,
          overflow: 'auto',
          display: 'flex',
          alignItems: 'flex-start',
          justifyContent: 'center',
          padding: 16,
          background: 'var(--bg-primary)',
        }}
      >
        <div style={{ position: 'relative' }}>
          <canvas
            ref={canvasRef}
            style={{
              maxWidth: '100%',
              height: 'auto',
              border: '1px solid var(--border-subtle)',
            }}
          />
          {showZones && (
            <ZoneOverlay
              zones={zones}
              pageNumber={activePage}
              scale={currentScale}
              onZoneClick={(zone) => {
                onZoneClick?.(zone)
                onZoneNavigate?.(zone.type)
              }}
            />
          )}
          {highlightedSourcePage === activePage && highlightedSourceCoords && (
            <SourceHighlight bbox={highlightedSourceCoords} pageNumber={activePage} scale={currentScale} />
          )}
        </div>
      </div>
    </div>
  )
}
