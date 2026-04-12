'use client'
import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { User } from '@/types/auth'

interface AuthState {
  accessToken: string | null
  refreshToken: string | null
  user: User | null
  isAuthenticated: boolean
  setToken: (accessToken: string, refreshToken: string) => void
  setUser: (user: User) => void
  logout: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      user: null,
      isAuthenticated: false,
      setToken: (accessToken, refreshToken) =>
        set(() => {
          if (typeof document !== 'undefined') {
            document.cookie = `numera-auth=${accessToken}; path=/; max-age=86400; SameSite=Lax; Secure`
          }
          return { accessToken, refreshToken, isAuthenticated: true }
        }),
      setUser: (user) => set({ user }),
      logout: () =>
        set(() => {
          if (typeof document !== 'undefined') {
            document.cookie = 'numera-auth=; path=/; max-age=0; SameSite=Lax'
          }
          return { accessToken: null, refreshToken: null, user: null, isAuthenticated: false }
        }),
    }),
    { name: 'numera-auth', partialize: (s) => ({ accessToken: s.accessToken, refreshToken: s.refreshToken, user: s.user }) }
  )
)
