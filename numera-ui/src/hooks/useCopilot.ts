'use client'

import { useState, useCallback, useRef } from 'react'
import { fetchApi } from '@/services/api'

// ── Types ──────────────────────────────────────────────────────────

export interface CopilotCitation {
  source_id: string
  text: string
  collection: string
  score: number
  metadata: Record<string, unknown>
}

export interface CopilotAnswer {
  answer: string
  citations: CopilotCitation[]
  model: string
  provider: string
  latency_ms: number
  context_tokens: number
}

export interface CopilotMessage {
  id: string
  role: 'user' | 'assistant' | 'system'
  content: string
  citations?: CopilotCitation[]
  model?: string
  provider?: string
  latency_ms?: number
  timestamp: number
}

export interface CopilotStatus {
  status: string
  provider: string
  collections: Record<string, number>
}

// ── Hook ───────────────────────────────────────────────────────────

export function useCopilot() {
  const [messages, setMessages] = useState<CopilotMessage[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [status, setStatus] = useState<CopilotStatus | null>(null)
  const abortRef = useRef<AbortController | null>(null)

  const sendMessage = useCallback(async (question: string, customerId?: string) => {
    if (!question.trim()) return
    setError(null)

    const userMsg: CopilotMessage = {
      id: `user-${Date.now()}`,
      role: 'user',
      content: question.trim(),
      timestamp: Date.now(),
    }
    setMessages(prev => [...prev, userMsg])
    setIsLoading(true)

    abortRef.current = new AbortController()

    try {
      const resp = await fetchApi<CopilotAnswer>('/copilot/query', {
        method: 'POST',
        body: JSON.stringify({
          question: question.trim(),
          top_k: 5,
          ...(customerId ? { customer_id: customerId } : {}),
        }),
        signal: abortRef.current.signal,
      })

      const assistantMsg: CopilotMessage = {
        id: `assistant-${Date.now()}`,
        role: 'assistant',
        content: resp.answer,
        citations: resp.citations,
        model: resp.model,
        provider: resp.provider,
        latency_ms: resp.latency_ms,
        timestamp: Date.now(),
      }
      setMessages(prev => [...prev, assistantMsg])
    } catch (err: unknown) {
      if (err instanceof DOMException && err.name === 'AbortError') return
      const message = err instanceof Error ? err.message : 'Failed to get response'
      setError(message)
      const errorMsg: CopilotMessage = {
        id: `error-${Date.now()}`,
        role: 'system',
        content: `Error: ${message}`,
        timestamp: Date.now(),
      }
      setMessages(prev => [...prev, errorMsg])
    } finally {
      setIsLoading(false)
      abortRef.current = null
    }
  }, [])

  const clearConversation = useCallback(() => {
    abortRef.current?.abort()
    setMessages([])
    setError(null)
  }, [])

  const cancelRequest = useCallback(() => {
    abortRef.current?.abort()
    setIsLoading(false)
  }, [])

  const fetchStatus = useCallback(async () => {
    try {
      const resp = await fetchApi<CopilotStatus>('/copilot/status')
      setStatus(resp)
    } catch {
      setStatus(null)
    }
  }, [])

  return {
    messages,
    isLoading,
    error,
    status,
    sendMessage,
    clearConversation,
    cancelRequest,
    fetchStatus,
  }
}
