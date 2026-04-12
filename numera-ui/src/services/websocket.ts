import { Client, ReconnectionTimeMode, type IMessage } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

export interface WebSocketMessage {
  type: string
  timestamp: string
  [key: string]: unknown
}

type MessageHandler = (message: WebSocketMessage) => void

class NumeraWebSocket {
  private client: Client | null = null
  private handlers = new Map<string, Set<MessageHandler>>()
  private subscriptions = new Map<string, () => void>()
  private token: string | null = null
  private tenantId: string | null = null
  private active = false
  private readonly baseUrl: string

  constructor() {
    const protocol = typeof window !== 'undefined' && window.location.protocol === 'https:' ? 'https:' : 'http:'
    const host = typeof window !== 'undefined' ? window.location.host : 'localhost:8080'
    this.baseUrl = `${protocol}//${host}/ws`
  }

  connect(token?: string, tenantId?: string): void {
    if (token) this.token = token
    if (tenantId) this.tenantId = tenantId
    if (this.active) return

    this.client = this.createClient()
    this.active = true
    this.client.activate()
  }

  disconnect(): void {
    this.active = false
    const current = this.client
    this.client = null
    this.subscriptions.clear()
    void current?.deactivate()
  }

  subscribe(destination: string, handler: MessageHandler): () => void {
    if (!this.handlers.has(destination)) {
      this.handlers.set(destination, new Set())
    }
    this.handlers.get(destination)!.add(handler)
    this.ensureDestinationSubscription(destination)

    return () => {
      const bucket = this.handlers.get(destination)
      bucket?.delete(handler)
      if (!bucket || bucket.size === 0) {
        this.handlers.delete(destination)
        this.unsubscribeDestination(destination)
      }
    }
  }

  send(destination: string, payload: Record<string, unknown>): void {
    if (!this.client?.connected) {
      console.warn('[WS] Cannot publish - STOMP client not connected')
      return
    }
    this.client.publish({ destination, body: JSON.stringify(payload) })
  }

  get isConnected(): boolean {
    return this.client?.connected === true
  }

  private createClient(): Client {
    return new Client({
      webSocketFactory: () => {
        const query = this.token ? `?token=${encodeURIComponent(this.token)}` : ''
        return new SockJS(`${this.baseUrl}${query}`)
      },
      connectHeaders: {
        ...(this.token ? { Authorization: `Bearer ${this.token}` } : {}),
        ...(this.tenantId ? { 'X-Tenant-ID': this.tenantId } : {}),
      },
      reconnectDelay: 1000,
      maxReconnectDelay: 30000,
      reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
      heartbeatIncoming: 30000,
      heartbeatOutgoing: 30000,
      onConnect: () => {
        this.subscriptions.clear()
        this.handlers.forEach((_handlers, destination) => this.ensureDestinationSubscription(destination))
      },
      onWebSocketClose: () => {
        this.subscriptions.clear()
      },
      onStompError: (frame) => {
        console.warn('[WS] STOMP error:', frame.headers['message'], frame.body)
      },
      onWebSocketError: () => {
        console.warn('[WS] Transport error')
      },
      debug: () => {
        // Keep logs quiet in production-like builds.
      },
    })
  }

  private ensureDestinationSubscription(destination: string): void {
    if (!this.client?.connected || this.subscriptions.has(destination)) return

    const subscription = this.client.subscribe(destination, (message: IMessage) => {
      const payload = this.parseMessage(message)
      const handlers = this.handlers.get(destination)
      handlers?.forEach((handler) => {
        try {
          handler(payload)
        } catch (err) {
          console.error('[WS] Handler error:', err)
        }
      })
    })

    this.subscriptions.set(destination, () => subscription.unsubscribe())
  }

  private unsubscribeDestination(destination: string): void {
    const unsub = this.subscriptions.get(destination)
    if (!unsub) return
    unsub()
    this.subscriptions.delete(destination)
  }

  private parseMessage(message: IMessage): WebSocketMessage {
    const timestampFromHeaders = message.headers.timestamp
    const normalizedTimestamp =
      typeof timestampFromHeaders === 'string' && timestampFromHeaders.length > 0
        ? new Date(Number(timestampFromHeaders) || timestampFromHeaders).toISOString()
        : new Date().toISOString()

    try {
      const parsed = JSON.parse(message.body) as unknown
      if (this.isRecord(parsed)) {
        const normalizedType = typeof parsed.type === 'string' && parsed.type.length > 0 ? parsed.type : 'EVENT'
        const normalizedMessageTimestamp =
          typeof parsed.timestamp === 'string' && parsed.timestamp.length > 0
            ? parsed.timestamp
            : normalizedTimestamp

        return {
          ...parsed,
          type: normalizedType,
          timestamp: normalizedMessageTimestamp,
          destination: message.headers.destination,
          messageId: message.headers['message-id'],
        }
      }

      return {
        type: 'EVENT',
        timestamp: normalizedTimestamp,
        destination: message.headers.destination,
        rawBody: message.body,
      }
    } catch {
      return {
        type: 'UNKNOWN',
        timestamp: normalizedTimestamp,
        destination: message.headers.destination,
        rawBody: message.body,
      }
    }
  }

  private isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null
  }
}

export const wsClient = new NumeraWebSocket()
