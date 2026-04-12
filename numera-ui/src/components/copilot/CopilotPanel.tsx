'use client'

import React, { useState, useRef, useEffect, useCallback } from 'react'
import { useCopilot, type CopilotCitation } from '@/hooks/useCopilot'
import CopilotMessage from './CopilotMessage'

const QUICK_QUERIES = [
  'Show me recent covenant breaches',
  'What is the current ratio for this borrower?',
  'Summarize the latest financial statements',
  'List all spreads pending approval',
  'What documents were uploaded this week?',
]

export default function CopilotPanel() {
  const [isOpen, setIsOpen] = useState(false)
  const [input, setInput] = useState('')
  const scrollRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLTextAreaElement>(null)

  const {
    messages,
    isLoading,
    error,
    sendMessage,
    clearConversation,
    cancelRequest,
    fetchStatus,
  } = useCopilot()

  // Auto-scroll to bottom on new messages
  useEffect(() => {
    const el = scrollRef.current
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  }, [messages, isLoading])

  // Fetch status when panel opens
  useEffect(() => {
    if (isOpen) {
      fetchStatus()
    }
  }, [isOpen, fetchStatus])

  // Focus input when panel opens
  useEffect(() => {
    if (isOpen) {
      inputRef.current?.focus()
    }
  }, [isOpen])

  const handleSubmit = useCallback(
    (e?: React.FormEvent) => {
      e?.preventDefault()
      if (!input.trim() || isLoading) return
      sendMessage(input.trim())
      setInput('')
    },
    [input, isLoading, sendMessage],
  )

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault()
        handleSubmit()
      }
    },
    [handleSubmit],
  )

  const handleQuickQuery = useCallback(
    (query: string) => {
      if (isLoading) return
      sendMessage(query)
    },
    [isLoading, sendMessage],
  )

  const handleCitationClick = useCallback((citation: CopilotCitation) => {
    // Navigate to source depending on collection type
    const meta = citation.metadata
    if (meta.spread_id) {
      window.open(`/spreading/${meta.spread_id}`, '_blank')
    } else if (meta.document_id) {
      window.open(`/documents/${meta.document_id}`, '_blank')
    } else if (meta.covenant_id) {
      window.open(`/covenants/${meta.covenant_id}`, '_blank')
    }
  }, [])

  return (
    <>
      {/* Floating trigger button */}
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="fixed bottom-6 right-6 z-50 w-14 h-14 rounded-full bg-blue-600 text-white shadow-lg hover:bg-blue-700 transition-all flex items-center justify-center"
        aria-label={isOpen ? 'Close Copilot' : 'Open Copilot'}
      >
        {isOpen ? (
          <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        ) : (
          <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z"
            />
          </svg>
        )}
      </button>

      {/* Slide-out panel */}
      {isOpen && (
        <div className="fixed bottom-24 right-6 z-50 w-[420px] h-[600px] bg-white rounded-xl shadow-2xl border border-gray-200 flex flex-col overflow-hidden">
          {/* Header */}
          <div className="flex items-center justify-between px-4 py-3 bg-blue-600 text-white">
            <div className="flex items-center gap-2">
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M9.75 3.104v5.714a2.25 2.25 0 01-.659 1.591L5 14.5M9.75 3.104c-.251.023-.501.05-.75.082m.75-.082a24.301 24.301 0 014.5 0m0 0v5.714c0 .597.237 1.17.659 1.591L19 14.5M14.25 3.104c.251.023.501.05.75.082M19 14.5l-2.47 2.47a3.375 3.375 0 01-2.386.99H9.856a3.375 3.375 0 01-2.386-.99L5 14.5m14 0V17a2.25 2.25 0 01-2.25 2.25H7.25A2.25 2.25 0 015 17v-2.5"
                />
              </svg>
              <h2 className="font-semibold text-sm">Numera Copilot</h2>
            </div>
            <div className="flex items-center gap-1">
              <button
                onClick={clearConversation}
                className="p-1.5 rounded hover:bg-blue-500 transition-colors"
                title="Clear conversation"
              >
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                </svg>
              </button>
              <button
                onClick={() => setIsOpen(false)}
                className="p-1.5 rounded hover:bg-blue-500 transition-colors"
                title="Close"
              >
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
          </div>

          {/* Messages area */}
          <div ref={scrollRef} className="flex-1 overflow-y-auto px-4 py-3">
            {messages.length === 0 && (
              <div className="text-center mt-8">
                <p className="text-gray-500 text-sm mb-4">
                  Ask me about spreads, covenants, documents, or financial data.
                </p>
                <div className="flex flex-col gap-2">
                  {QUICK_QUERIES.map(q => (
                    <button
                      key={q}
                      onClick={() => handleQuickQuery(q)}
                      className="text-left text-xs bg-gray-50 rounded-lg px-3 py-2 border border-gray-200 hover:border-blue-300 hover:bg-blue-50 transition-colors text-gray-700"
                    >
                      {q}
                    </button>
                  ))}
                </div>
              </div>
            )}

            {messages.map(msg => (
              <CopilotMessage key={msg.id} message={msg} onCitationClick={handleCitationClick} />
            ))}

            {isLoading && (
              <div className="flex justify-start mb-3">
                <div className="bg-gray-100 rounded-lg px-4 py-3 flex items-center gap-2">
                  <div className="flex gap-1">
                    <span className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
                    <span className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
                    <span className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
                  </div>
                  <button
                    onClick={cancelRequest}
                    className="text-xs text-gray-500 hover:text-red-500 ml-2"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            )}
          </div>

          {/* Error banner */}
          {error && (
            <div className="px-4 py-2 bg-red-50 text-red-700 text-xs border-t border-red-200">
              {error}
            </div>
          )}

          {/* Input area */}
          <form onSubmit={handleSubmit} className="border-t border-gray-200 px-4 py-3">
            <div className="flex items-end gap-2">
              <textarea
                ref={inputRef}
                value={input}
                onChange={e => setInput(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder="Ask a question…"
                rows={1}
                className="flex-1 resize-none rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                disabled={isLoading}
              />
              <button
                type="submit"
                disabled={!input.trim() || isLoading}
                className="rounded-lg bg-blue-600 px-3 py-2 text-white text-sm font-medium hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
              >
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
                </svg>
              </button>
            </div>
          </form>
        </div>
      )}
    </>
  )
}
