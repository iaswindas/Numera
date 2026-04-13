'use client'

import { useCallback, useState } from 'react'
import { MessageCircle, Send, ExternalLink } from 'lucide-react'
import { useSpreadComments, useAddSpreadComment } from '@/services/spreadApi'
import type { SpreadComment } from '@/types/spread'

interface CommentPanelProps {
  spreadId: string
  selectedValueId: string | null
  onNavigateToSource?: (url: string) => void
}

function CommentBubble({ comment, onLinkClick }: { comment: SpreadComment; onLinkClick?: (url: string) => void }) {
  const isAuto = comment.type === 'AUTO'
  const urlPattern = /https?:\/\/[^\s]+|page:(\d+)/gi

  const renderContent = () => {
    const parts: React.ReactNode[] = []
    let lastIndex = 0
    let match: RegExpExecArray | null

    const regex = new RegExp(urlPattern)
    while ((match = regex.exec(comment.content)) !== null) {
      if (match.index > lastIndex) {
        parts.push(comment.content.slice(lastIndex, match.index))
      }
      const url = match[0]
      parts.push(
        <button
          key={match.index}
          onClick={() => onLinkClick?.(url)}
          style={{
            color: '#3b82f6',
            background: 'none',
            border: 'none',
            padding: 0,
            cursor: 'pointer',
            textDecoration: 'underline',
            display: 'inline-flex',
            alignItems: 'center',
            gap: 2,
            fontSize: 'inherit',
          }}
        >
          {url}
          <ExternalLink size={10} />
        </button>,
      )
      lastIndex = match.index + match[0].length
    }
    if (lastIndex < comment.content.length) {
      parts.push(comment.content.slice(lastIndex))
    }
    return parts
  }

  return (
    <div
      style={{
        padding: '8px 10px',
        borderRadius: 8,
        border: '1px solid var(--border-subtle)',
        background: isAuto ? 'rgba(59, 130, 246, 0.06)' : 'var(--bg-primary)',
        fontSize: 13,
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
        <span
          style={{
            fontSize: 10,
            fontWeight: 600,
            textTransform: 'uppercase',
            color: isAuto ? '#3b82f6' : '#8b5cf6',
            background: isAuto ? 'rgba(59, 130, 246, 0.12)' : 'rgba(139, 92, 246, 0.12)',
            padding: '1px 6px',
            borderRadius: 4,
          }}
        >
          {isAuto ? 'Auto' : 'Manual'}
        </span>
        <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>{comment.createdBy}</span>
        <span style={{ fontSize: 11, color: 'var(--text-muted)', marginLeft: 'auto' }}>
          {new Date(comment.createdAt).toLocaleString()}
        </span>
      </div>
      <div style={{ lineHeight: 1.5 }}>{renderContent()}</div>
    </div>
  )
}

export function CommentPanel({ spreadId, selectedValueId, onNavigateToSource }: CommentPanelProps) {
  const commentsQuery = useSpreadComments(spreadId, selectedValueId)
  const addCommentMutation = useAddSpreadComment(spreadId)
  const [draftComment, setDraftComment] = useState('')

  const comments = commentsQuery.data ?? []
  const autoComments = comments.filter((c) => c.type === 'AUTO')
  const manualComments = comments.filter((c) => c.type === 'MANUAL')

  const handleSubmit = useCallback(async () => {
    const trimmed = draftComment.trim()
    if (!trimmed) return
    await addCommentMutation.mutateAsync({
      valueId: selectedValueId ?? undefined,
      content: trimmed,
    })
    setDraftComment('')
  }, [draftComment, selectedValueId, addCommentMutation])

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) {
        void handleSubmit()
      }
    },
    [handleSubmit],
  )

  const handleLinkClick = useCallback(
    (url: string) => {
      const pageMatch = url.match(/^page:(\d+)$/i)
      if (pageMatch) {
        onNavigateToSource?.(url)
        return
      }
      onNavigateToSource?.(url)
    },
    [onNavigateToSource],
  )

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <div style={{ padding: '10px 12px', borderBottom: '1px solid var(--border-subtle)', background: 'var(--bg-secondary)' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <MessageCircle size={14} />
          <span style={{ fontWeight: 600, fontSize: 13 }}>Comments</span>
          {selectedValueId && (
            <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>
              — Filtered by selected cell
            </span>
          )}
          <span style={{ fontSize: 11, color: 'var(--text-muted)', marginLeft: 'auto' }}>
            {comments.length} total
          </span>
        </div>
      </div>

      <div style={{ flex: 1, overflow: 'auto', padding: 12 }}>
        {commentsQuery.isLoading ? (
          <div style={{ color: 'var(--text-muted)', fontSize: 13 }}>Loading comments...</div>
        ) : comments.length === 0 ? (
          <div style={{ color: 'var(--text-muted)', fontSize: 13 }}>
            No comments yet. {selectedValueId ? 'Select a cell to view auto-generated comments.' : ''}
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {autoComments.length > 0 && (
              <>
                <div style={{ fontSize: 11, fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase' }}>
                  Auto-Generated ({autoComments.length})
                </div>
                {autoComments.map((c) => (
                  <CommentBubble key={c.id} comment={c} onLinkClick={handleLinkClick} />
                ))}
              </>
            )}
            {manualComments.length > 0 && (
              <>
                <div style={{ fontSize: 11, fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', marginTop: autoComments.length > 0 ? 8 : 0 }}>
                  Manual ({manualComments.length})
                </div>
                {manualComments.map((c) => (
                  <CommentBubble key={c.id} comment={c} onLinkClick={handleLinkClick} />
                ))}
              </>
            )}
          </div>
        )}
      </div>

      <div
        style={{
          padding: '8px 12px',
          borderTop: '1px solid var(--border-subtle)',
          background: 'var(--bg-secondary)',
          display: 'flex',
          gap: 8,
        }}
      >
        <textarea
          className="input"
          rows={2}
          placeholder="Add a comment… (Ctrl+Enter to send)"
          value={draftComment}
          onChange={(e) => setDraftComment(e.target.value)}
          onKeyDown={handleKeyDown}
          style={{ flex: 1, resize: 'none', fontSize: 13 }}
        />
        <button
          className="btn btn-primary btn-sm"
          onClick={() => void handleSubmit()}
          disabled={!draftComment.trim() || addCommentMutation.isPending}
          style={{ alignSelf: 'flex-end' }}
        >
          <Send size={13} />
        </button>
      </div>
    </div>
  )
}
