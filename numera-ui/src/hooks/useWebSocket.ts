'use client'

import { useEffect, useRef, useCallback } from 'react'
import { wsClient, type WebSocketMessage } from '@/services/websocket'
import { useAuthStore } from '@/stores/authStore'

/**
 * Hook to connect to WebSocket and auto-manage lifecycle.
 * Call once at the app layout level.
 */
export function useWebSocketConnection() {
  const token = useAuthStore((s) => s.accessToken)
  const tenantId = useAuthStore((s) => s.user?.tenantId)

  useEffect(() => {
    if (token) {
      wsClient.connect(token, tenantId)
    }
    return () => {
      wsClient.disconnect()
    }
  }, [token, tenantId])
}

/**
 * Hook to subscribe to a specific WebSocket message type.
 */
export function useWebSocketSubscription(
  destination: string | null | undefined,
  handler: (message: WebSocketMessage) => void,
) {
  const handlerRef = useRef(handler)
  handlerRef.current = handler

  useEffect(() => {
    if (!destination) return
    const unsubscribe = wsClient.subscribe(destination, (msg) => handlerRef.current(msg))
    return unsubscribe
  }, [destination])
}

/**
 * Hook to send WebSocket messages.
 */
export function useWebSocketSend() {
  return useCallback((destination: string, payload: Record<string, unknown>) => {
    wsClient.send(destination, payload)
  }, [])
}
