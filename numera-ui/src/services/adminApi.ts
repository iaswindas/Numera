import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchApi } from './api'

export interface AdminLineItem {
  itemCode: string
  label: string
  zone?: string
  category?: string
}

export interface TaxonomyUploadResult {
  createdCount: number
  updatedCount?: number
  errorCount: number
  errors: string[]
}

export interface LanguageConfig {
  code: string
  name: string
  ocrSupported: boolean
  enabled: boolean
}

function getAuthHeaders(): Record<string, string> {
  if (typeof window === 'undefined') return {}
  try {
    const raw = localStorage.getItem('numera-auth')
    if (!raw) return {}
    const state = JSON.parse(raw)?.state
    const accessToken = state?.accessToken as string | undefined
    const tenantId = state?.user?.tenantId as string | undefined
    return {
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      ...(tenantId ? { 'X-Tenant-ID': tenantId } : {}),
    }
  } catch {
    return {}
  }
}

export function useAdminUsers() {
  return useQuery({ queryKey: ['admin', 'users'], queryFn: () => fetchApi('/admin/users') })
}

export function useCreateAdminUser() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (payload: Record<string, unknown>) => fetchApi('/admin/users', { method: 'POST', body: JSON.stringify(payload) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'users'] }),
  })
}

export function useAdminTaxonomy() {
  return useQuery({ queryKey: ['admin', 'taxonomy'], queryFn: () => fetchApi('/admin/taxonomy') })
}

export function useAdminWorkflows() {
  return useQuery({ queryKey: ['admin', 'workflows'], queryFn: () => fetchApi('/admin/workflows') })
}

export function useAdminSystemConfig() {
  return useQuery({ queryKey: ['admin', 'system-config'], queryFn: () => fetchApi('/admin/system-config') })
}

export function useModelLineItems() {
  return useQuery<AdminLineItem[]>({
    queryKey: ['admin', 'model-line-items'],
    queryFn: () => fetchApi('/admin/model-line-items'),
  })
}

export function useExportTaxonomy() {
  return useMutation({
    mutationFn: async () => {
      const response = await fetch('/api/admin/taxonomy/export', {
        method: 'GET',
        headers: {
          Accept: 'application/octet-stream',
          ...getAuthHeaders(),
        },
      })
      if (!response.ok) {
        throw new Error(`Failed to export taxonomy: ${response.statusText}`)
      }
      return response.blob()
    },
  })
}

export function useBulkUploadTaxonomy() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (file: File) => {
      const formData = new FormData()
      formData.append('file', file)
      return fetchApi<TaxonomyUploadResult>('/admin/taxonomy/bulk-upload', {
        method: 'POST',
        body: formData,
      })
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'taxonomy'] }),
  })
}

export function useLanguages() {
  return useQuery<LanguageConfig[]>({
    queryKey: ['admin', 'languages'],
    queryFn: () => fetchApi('/admin/languages'),
  })
}

export function useToggleLanguage() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ code, enabled }: { code: string; enabled: boolean }) =>
      fetchApi(`/admin/languages/${code}/toggle`, {
        method: 'PUT',
        body: JSON.stringify({ enabled }),
      }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'languages'] }),
  })
}
