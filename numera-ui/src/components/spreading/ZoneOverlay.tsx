'use client'

import type { Zone } from '@/types/spread'

interface ZoneOverlayProps {
  zones: Zone[]
  pageNumber: number
  scale: number
  onZoneClick: (zone: Zone) => void
}

const zoneColors = {
  bs: '#3b82f6', // blue - Balance Sheet
  is: '#10b981', // green - Income Statement
  cf: '#f59e0b', // orange - Cash Flow
  notes: '#8b5cf6', // purple
  other: '#6b7280', // gray
}

export function ZoneOverlay({ zones, pageNumber, scale, onZoneClick }: ZoneOverlayProps) {
  const pageZones = zones.filter((z) => z.pageNumber === pageNumber)

  if (pageZones.length === 0) {
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
        pointerEvents: 'auto',
      }}
      width="100%"
      height="100%"
      onMouseUp={(e) => {
        const svg = e.currentTarget
        const rect = svg.getBoundingClientRect()
        const x = (e.clientX - rect.left) / scale
        const y = (e.clientY - rect.top) / scale

        for (const zone of pageZones) {
          const bbox = zone.boundingBox
          if (x >= bbox.x && x <= bbox.x + bbox.width && y >= bbox.y && y <= bbox.y + bbox.height) {
            onZoneClick(zone)
            break
          }
        }
      }}
    >
      {pageZones.map((zone) => (
        <g key={zone.id}>
          <rect
            x={zone.boundingBox.x * scale}
            y={zone.boundingBox.y * scale}
            width={zone.boundingBox.width * scale}
            height={zone.boundingBox.height * scale}
            fill={zoneColors[zone.type as keyof typeof zoneColors] || zoneColors.other}
            fillOpacity="0.08"
            stroke={zoneColors[zone.type as keyof typeof zoneColors] || zoneColors.other}
            strokeWidth="1.5"
            pointerEvents="all"
            style={{ cursor: 'pointer' }}
          />
          <title>{`${zone.type.toUpperCase()} - Confidence: ${(zone.confidence * 100).toFixed(0)}%`}</title>
        </g>
      ))}
    </svg>
  )
}
