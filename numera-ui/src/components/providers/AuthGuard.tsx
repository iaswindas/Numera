'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useAuthStore } from '@/stores/authStore'

export function AuthGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter()
  const { accessToken, isAuthenticated, logout } = useAuthStore()

  useEffect(() => {
    if (!isAuthenticated || !accessToken) {
      router.replace('/login')
      return
    }

    try {
      const parts = accessToken.split('.')
      if (parts.length === 3) {
        const payload = JSON.parse(atob(parts[1])) as { exp?: number }
        if (payload.exp && payload.exp * 1000 <= Date.now()) {
          logout()
          router.replace('/login')
        }
      }
    } catch {
      // ignore malformed tokens and let backend enforce auth
    }
  }, [accessToken, isAuthenticated, logout, router])

  if (!isAuthenticated || !accessToken) {
    return null
  }

  return <>{children}</>
}
