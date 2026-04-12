'use client'

import type { BoundingBox } from '@/types/spread'

interface SourceHighlightProps {
  bbox: BoundingBox
  pageNumber: number
  scale: number
}

export function SourceHighlight({ bbox, pageNumber, scale }: SourceHighlightProps) {
  if (!bbox || pageNumber < 1) {
    return null
  }

  return (
    <svg
      style={{
        position: 'absolute',
        top: 0,
        left: 0,
        width: '100%',
        height: '100%',
        pointerEvents: 'none',
      }}
      width="100%"
      height="100%"
    >
      {/* Highlight rectangle */}
      <rect
        x={bbox.x * scale}
        y={bbox.y * scale}
        width={bbox.width * scale}
        height={bbox.height * scale}
        fill="rgba(59, 130, 246, 0.15)"
        stroke="#3b82f6"
        strokeWidth="2"
      />
      {/* Corner markers */}
      <circle cx={bbox.x * scale} cy={bbox.y * scale} r="3" fill="#3b82f6" />
      <circle cx={(bbox.x + bbox.width) * scale} cy={bbox.y * scale} r="3" fill="#3b82f6" />
      <circle cx={bbox.x * scale} cy={(bbox.y + bbox.height) * scale} r="3" fill="#3b82f6" />
      <circle
        cx={(bbox.x + bbox.width) * scale}
        cy={(bbox.y + bbox.height) * scale}
        r="3"
        fill="#3b82f6"
      />
    </svg>
  )
}
