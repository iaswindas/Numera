/* eslint-disable react-refresh/only-export-components */
'use client'

import { createContext, useContext, useMemo, useState } from 'react'

type ToastVariant = 'success' | 'error' | 'info'

type ToastItem = {
  id: number
  title: string
  variant: ToastVariant
}

type ToastContextValue = {
  showToast: (title: string, variant?: ToastVariant) => void
}

const ToastContext = createContext<ToastContextValue | null>(null)

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([])

  const value = useMemo<ToastContextValue>(() => ({
    showToast: (title, variant = 'info') => {
      const id = Date.now() + Math.floor(Math.random() * 1000)
      setToasts((prev) => [...prev, { id, title, variant }])
      setTimeout(() => setToasts((prev) => prev.filter((t) => t.id !== id)), 3000)
    },
  }), [])

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div style={{ position: 'fixed', right: 16, bottom: 16, zIndex: 1000, display: 'flex', flexDirection: 'column', gap: 8 }}>
        {toasts.map((t) => (
          <div
            key={t.id}
            style={{
              minWidth: 240,
              padding: '10px 12px',
              borderRadius: 8,
              border: '1px solid var(--border-subtle)',
              background: t.variant === 'success' ? 'rgba(16,185,129,0.16)' : t.variant === 'error' ? 'rgba(239,68,68,0.16)' : 'rgba(59,130,246,0.16)',
              color: 'var(--text-primary)',
              fontSize: 13,
            }}
          >
            {t.title}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast() {
  const ctx = useContext(ToastContext)
  if (!ctx) {
    throw new Error('useToast must be used inside ToastProvider')
  }
  return ctx
}
