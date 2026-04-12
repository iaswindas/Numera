'use client'

import { useRef, useState, useEffect } from 'react'
import { MessageSquare, Check, X } from 'lucide-react'

interface NoteEditorProps {
  valueId: string
  initialNotes?: string
  onSave: (notes: string) => Promise<void>
}

export function NoteEditor({ valueId, initialNotes = '', onSave }: NoteEditorProps) {
  const [isOpen, setIsOpen] = useState(false)
  const [notes, setNotes] = useState(initialNotes)
  const [isSaving, setIsSaving] = useState(false)
  const popoverRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (popoverRef.current && !popoverRef.current.contains(event.target as Node)) {
        setIsOpen(false)
      }
    }

    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside)
      return () => document.removeEventListener('mousedown', handleClickOutside)
    }
  }, [isOpen])

  const handleSave = async () => {
    try {
      setIsSaving(true)
      await onSave(notes)
      setIsOpen(false)
    } catch (error) {
      console.error('Error saving notes:', error)
    } finally {
      setIsSaving(false)
    }
  }

  const handleCancel = () => {
    setNotes(initialNotes)
    setIsOpen(false)
  }

  return (
    <div style={{ position: 'relative', display: 'inline-block' }} ref={popoverRef}>
      <button
        id={`note-trigger-${valueId}`}
        aria-controls={`note-popover-${valueId}`}
        onClick={() => setIsOpen(!isOpen)}
        style={{
          background: initialNotes ? '#3b82f6' : 'transparent',
          color: initialNotes ? 'white' : 'var(--text-muted)',
          border: 'none',
          borderRadius: 4,
          padding: '4px 6px',
          cursor: 'pointer',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
        title="Add or edit notes"
      >
        <MessageSquare size={14} />
      </button>

      {isOpen && (
        <div
          id={`note-popover-${valueId}`}
          style={{
            position: 'absolute',
            top: '100%',
            right: 0,
            marginTop: 8,
            background: 'var(--bg-elevated)',
            border: '1px solid var(--border-subtle)',
            borderRadius: 6,
            padding: 12,
            minWidth: 280,
            boxShadow: '0 10px 25px rgba(0, 0, 0, 0.3)',
            zIndex: 10,
          }}
        >
          <div style={{ marginBottom: 10 }}>
            <label style={{ display: 'block', fontSize: 12, fontWeight: 500, marginBottom: 6, color: 'var(--text-secondary)' }}>
              Notes
            </label>
            <textarea
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="Add notes for this cell..."
              style={{
                width: '100%',
                padding: 8,
                border: '1px solid var(--border-subtle)',
                borderRadius: 4,
                background: 'var(--bg-input)',
                color: 'var(--text-primary)',
                fontFamily: 'inherit',
                fontSize: 12,
                minHeight: 80,
                resize: 'vertical',
              }}
            />
          </div>

          <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end' }}>
            <button
              onClick={handleCancel}
              disabled={isSaving}
              style={{
                padding: '6px 10px',
                background: 'transparent',
                border: '1px solid var(--border-subtle)',
                borderRadius: 4,
                color: 'var(--text-secondary)',
                cursor: 'pointer',
                fontSize: 12,
                display: 'flex',
                alignItems: 'center',
                gap: 4,
              }}
            >
              <X size={12} />
              Cancel
            </button>
            <button
              onClick={handleSave}
              disabled={isSaving}
              style={{
                padding: '6px 10px',
                background: '#0a84ff',
                border: 'none',
                borderRadius: 4,
                color: 'white',
                cursor: isSaving ? 'default' : 'pointer',
                fontSize: 12,
                fontWeight: 500,
                display: 'flex',
                alignItems: 'center',
                gap: 4,
              }}
            >
              <Check size={12} />
              {isSaving ? 'Saving...' : 'Save'}
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
