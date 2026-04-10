import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchApi } from './api'

export function useCovenantCustomers(query?: string) {
  return useQuery({
    queryKey: ['covenant', 'customers', query],
    queryFn: () => fetchApi(`/covenants/customers${query ? `?query=${encodeURIComponent(query)}` : ''}`),
  })
}

export function useCovenantDefinitions(covenantCustomerId?: string) {
  return useQuery({
    queryKey: ['covenant', 'definitions', covenantCustomerId],
    queryFn: () => fetchApi(`/covenants/definitions?covenantCustomerId=${covenantCustomerId}`),
    enabled: !!covenantCustomerId,
  })
}

export function useCreateCovenant() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (payload: Record<string, unknown>) => fetchApi('/covenants/definitions', { method: 'POST', body: JSON.stringify(payload) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['covenant'] }),
  })
}

export function useMonitoringItems() {
  return useQuery({
    queryKey: ['covenant', 'monitoring'],
    queryFn: () => fetchApi('/covenants/monitoring'),
    refetchInterval: 5000,
  })
}

export function useMonitoringSummary() {
  return useQuery({
    queryKey: ['covenant', 'monitoring', 'summary'],
    queryFn: () => fetchApi('/covenants/monitoring/summary'),
    refetchInterval: 5000,
  })
}

export function useWaiverRequest() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ actorId, payload }: { actorId: string; payload: Record<string, unknown> }) =>
      fetchApi(`/covenants/waivers?actorId=${actorId}`, { method: 'POST', body: JSON.stringify(payload) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['covenant'] }),
  })
}

export function useEmailTemplates() {
  return useQuery({
    queryKey: ['covenant', 'templates'],
    queryFn: () => fetchApi('/covenants/templates'),
  })
}

export function useCreateEmailTemplate() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ actorId, payload }: { actorId?: string; payload: Record<string, unknown> }) =>
      fetchApi(`/covenants/templates${actorId ? `?actorId=${actorId}` : ''}`, { method: 'POST', body: JSON.stringify(payload) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['covenant', 'templates'] }),
  })
}

export function useUpdateEmailTemplate() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: Record<string, unknown> }) =>
      fetchApi(`/covenants/templates/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['covenant', 'templates'] }),
  })
}

export function useDeleteEmailTemplate() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => fetchApi(`/covenants/templates/${id}/active?active=false`, { method: 'PATCH' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['covenant', 'templates'] }),
  })
}
