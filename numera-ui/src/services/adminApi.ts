import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchApi } from './api'

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
