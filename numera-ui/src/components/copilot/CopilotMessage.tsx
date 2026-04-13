'use client'

import React from 'react'
import type { CopilotMessage as CopilotMessageType, CopilotCitation } from '@/hooks/useCopilot'
import { sanitizeHtml } from '@/utils/sanitize'

interface Props {
  message: CopilotMessageType
  onCitationClick?: (citation: CopilotCitation) => void
}

function formatTime(ts: number): string {
  return new Date(ts).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

/**
 * Minimal markdown rendering: bold, inline code, code blocks, line breaks.
 * Intentionally lightweight — no dependency on a full markdown library.
 */
function renderMarkdown(text: string): React.ReactNode[] {
  const lines = text.split('\n')
  const nodes: React.ReactNode[] = []
  let inCode = false
  let codeBuffer: string[] = []

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]

    if (line.startsWith('```')) {
      if (inCode) {
        nodes.push(
          <pre key={`code-${i}`} className="bg-gray-800 text-gray-100 rounded p-2 my-1 text-xs overflow-x-auto">
            <code>{codeBuffer.join('\n')}</code>
          </pre>,
        )
        codeBuffer = []
      }
      inCode = !inCode
      continue
    }

    if (inCode) {
      codeBuffer.push(line)
      continue
    }

    // Process inline formatting
    const formatted = line
      .replace(/\*\*(.+?)\*\*/g, '<b>$1</b>')
      .replace(/`(.+?)`/g, '<code class="bg-gray-100 px-1 rounded text-sm">$1</code>')
      .replace(/\[Source (\d+)\]/g, '<span class="text-blue-600 font-medium cursor-pointer">[Source $1]</span>')

    nodes.push(
      <span key={`line-${i}`} dangerouslySetInnerHTML={{ __html: sanitizeHtml(formatted) }} />,
    )
    if (i < lines.length - 1) {
      nodes.push(<br key={`br-${i}`} />)
    }
  }

  return nodes
}

export default function CopilotMessage({ message, onCitationClick }: Props) {
  const isUser = message.role === 'user'
  const isSystem = message.role === 'system'

  return (
    <div className={`flex ${isUser ? 'justify-end' : 'justify-start'} mb-3`}>
      <div
        className={`max-w-[85%] rounded-lg px-4 py-2 ${
          isUser
            ? 'bg-blue-600 text-white'
            : isSystem
              ? 'bg-red-50 text-red-700 border border-red-200'
              : 'bg-gray-100 text-gray-900'
        }`}
      >
        {/* Message content */}
        <div className="text-sm leading-relaxed">
          {isUser ? message.content : renderMarkdown(message.content)}
        </div>

        {/* Citations */}
        {message.citations && message.citations.length > 0 && (
          <div className="mt-2 pt-2 border-t border-gray-200">
            <p className="text-xs font-medium text-gray-500 mb-1">Sources</p>
            <div className="flex flex-col gap-1">
              {message.citations.map((citation, idx) => (
                <button
                  key={citation.source_id}
                  onClick={() => onCitationClick?.(citation)}
                  className="text-left text-xs bg-white rounded px-2 py-1 border border-gray-200 hover:border-blue-300 hover:bg-blue-50 transition-colors"
                >
                  <span className="font-medium text-blue-600">[{idx + 1}]</span>{' '}
                  <span className="text-gray-600">
                    {citation.collection} — {citation.text.slice(0, 80)}
                    {citation.text.length > 80 ? '…' : ''}
                  </span>
                  <span className="text-gray-400 ml-1">({(citation.score * 100).toFixed(0)}%)</span>
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Metadata footer */}
        <div className="flex items-center gap-2 mt-1 text-[10px] text-gray-400">
          <span>{formatTime(message.timestamp)}</span>
          {message.latency_ms != null && <span>· {message.latency_ms}ms</span>}
          {message.model && <span>· {message.model}</span>}
        </div>
      </div>
    </div>
  )
}
