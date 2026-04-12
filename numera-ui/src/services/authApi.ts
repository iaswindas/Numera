import { useMutation } from '@tanstack/react-query'
import { fetchApi } from './api'
import { useAuthStore } from '@/stores/authStore'
import type { User } from '@/types/auth'

interface LoginRequest {
  email: string
  password: string
}

interface LoginResponse {
  accessToken: string
  refreshToken: string
  expiresInSec: number
  user: User
}

export function useLogin() {
  const { setToken, setUser } = useAuthStore()

  return useMutation({
    mutationFn: (creds: LoginRequest) =>
      fetchApi<LoginResponse>('/auth/login', {
        method: 'POST',
        body: JSON.stringify(creds),
      }),
    onSuccess: (data) => {
      setToken(data.accessToken, data.refreshToken)
      setUser(data.user)
    },
  })
}

export function useLogout() {
  const { logout } = useAuthStore()

  return useMutation({
    mutationFn: async () => {
      // Revoke refresh tokens server-side (V-12)
      try {
        await fetchApi('/auth/logout', { method: 'POST' })
      } catch {
        // Proceed with client-side logout even if server call fails
      }
      return true
    },
    onSettled: () => logout(),
  })
}

export function useRefreshToken() {
  const { setToken } = useAuthStore()

  return useMutation({
    mutationFn: (refreshToken: string) =>
      fetchApi<LoginResponse>('/auth/refresh', {
        method: 'POST',
        body: JSON.stringify({ refreshToken }),
      }),
    onSuccess: (data) => {
      setToken(data.accessToken, data.refreshToken)
    },
  })
}
