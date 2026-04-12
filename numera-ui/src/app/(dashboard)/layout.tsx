'use client'

import { Sidebar } from '@/components/layout/Sidebar'
import { Header } from '@/components/layout/Header'
import { AuthGuard } from '@/components/providers/AuthGuard'
import { useWebSocketConnection } from '@/hooks/useWebSocket'

function WebSocketProvider({ children }: { children: React.ReactNode }) {
  useWebSocketConnection()
  return <>{children}</>
}

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  return (
    <AuthGuard>
      <WebSocketProvider>
        <div className="app-layout">
          <Sidebar />
          <div className="main-content">
            <Header />
            <div className="page-content">
              {children}
            </div>
          </div>
        </div>
      </WebSocketProvider>
    </AuthGuard>
  )
}
