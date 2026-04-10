import type { ApiError } from '@/types/api'

const BASE_URL = '/api'

type FetchApiOptions = RequestInit & {
  skipAuth?: boolean
}

function getAuthSnapshot(): { accessToken: string | null; refreshToken: string | null; tenantId: string | null } {
  if (typeof window === 'undefined') return { accessToken: null, refreshToken: null, tenantId: null }
  try {
    const raw = localStorage.getItem('numera-auth')
    if (!raw) return { accessToken: null, refreshToken: null, tenantId: null }
    const state = JSON.parse(raw)?.state
    return {
      accessToken: state?.accessToken ?? null,
      refreshToken: state?.refreshToken ?? null,
      tenantId: state?.user?.tenantId ?? null,
    }
  } catch {
    return { accessToken: null, refreshToken: null, tenantId: null }
  }
}

async function tryRefresh(refreshToken: string): Promise<boolean> {
  try {
    const res = await fetch(`${BASE_URL}/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    })
    if (!res.ok) return false
    const body = await res.json()
    const raw = localStorage.getItem('numera-auth')
    if (!raw) return false
    const parsed = JSON.parse(raw)
    parsed.state = {
      ...(parsed.state ?? {}),
      accessToken: body.accessToken,
      refreshToken: body.refreshToken,
      user: body.user ?? parsed.state?.user,
      isAuthenticated: true,
    }
    localStorage.setItem('numera-auth', JSON.stringify(parsed))
    if (typeof document !== 'undefined') {
      document.cookie = `numera-auth=${body.accessToken}; path=/; max-age=86400; SameSite=Lax`
    }
    return true
  } catch {
    return false
  }
}

export async function fetchApi<T>(
  endpoint: string,
  options: FetchApiOptions = {}
): Promise<T> {
  const auth = getAuthSnapshot() ?? { accessToken: null, refreshToken: null, tenantId: null }
  const skipAuth = options.skipAuth === true
  const isFormData = typeof FormData !== 'undefined' && options.body instanceof FormData
  const headers: Record<string, string> = {
    ...(options.headers as Record<string, string> | undefined),
  }
  if (!isFormData && !headers['Content-Type']) {
    headers['Content-Type'] = 'application/json'
  }
  if (!skipAuth && auth.accessToken) headers['Authorization'] = `Bearer ${auth.accessToken}`
  if (!skipAuth && auth.tenantId) headers['X-Tenant-ID'] = auth.tenantId

  const normalized = endpoint.startsWith('/') ? endpoint : `/${endpoint}`
  const url = `${BASE_URL}${normalized}`
  if (process.env.NODE_ENV !== 'production') {
    console.debug('[api:req]', options.method ?? 'GET', url)
  }

  let res = await fetch(url, { ...options, headers })

  if (res.status === 401 && !skipAuth && auth.refreshToken) {
    const refreshed = await tryRefresh(auth.refreshToken)
    if (refreshed) {
      const retryAuth = getAuthSnapshot() ?? { accessToken: null, refreshToken: null, tenantId: null }
      if (retryAuth.accessToken) {
        headers['Authorization'] = `Bearer ${retryAuth.accessToken}`
      }
      res = await fetch(url, { ...options, headers })
    }
  }

  if (!res.ok) {
    const err: ApiError = {
      status: res.status,
      message: `Request failed: ${res.statusText}`,
    }
    try {
      const body = await res.json()
      err.message = body.message ?? err.message
      err.details = body.details
    } catch {
      // ignore parse error
    }
    throw err
  }

  if (process.env.NODE_ENV !== 'production') {
    console.debug('[api:res]', options.method ?? 'GET', url, res.status)
  }

  if (res.status === 204) return undefined as T
  return res.json()
}
