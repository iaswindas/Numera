'use client'

import { AlertCircle } from 'lucide-react'

interface LockBannerProps {
  lockedBy: string | null
}

export function LockBanner({ lockedBy }: LockBannerProps) {
  if (!lockedBy) {
    return null
  }

  return (
    <div
      style={{
        padding: '12px 16px',
        background: 'rgba(255, 159, 10, 0.12)',
        borderBottom: '1px solid rgba(255, 159, 10, 0.3)',
        color: '#ff9f0a',
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        fontSize: 14,
        fontWeight: 500,
      }}
    >
      <AlertCircle size={18} style={{ flexShrink: 0 }} />
      <span>
        Currently being edited by <strong>{lockedBy}</strong>. You are in view-only mode.
      </span>
    </div>
  )
}
