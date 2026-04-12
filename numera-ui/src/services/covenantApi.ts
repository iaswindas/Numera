import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchApi } from './api'

export interface CovenantFormula {
  id: string
  name: string
  expression: string
  description?: string
  active: boolean
  createdAt?: string
  updatedAt?: string
}

export interface CovenantStatusDistribution {
  status: string
  count: number
}

export interface BreachProbabilityCell {
  monitoringItemId: string
  customerId: string
  customerName: string
  covenantId: string
  covenantName: string
  probability: number
}

export interface UpcomingDueDateItem {
  monitoringItemId: string
  covenantName: string
  customerName: string
  dueDate: string
  status: string
}

export interface CovenantTrendPoint {
  period: string
  value: number
}

export interface CovenantSignature {
  id: string
  name: string
  title?: string
}

export interface WaiverLetterPayload {
  waiverType: 'WAIVE' | 'NOT_WAIVE'
  durationType: 'INSTANCE' | 'PERMANENT'
  comments?: string
  templateId: string
  signatureId: string
  recipientEmails: string[]
}

export interface WaiverLetterResponse {
  id: string
  subject: string
  bodyHtml: string
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

export function useFormulas() {
  return useQuery<CovenantFormula[]>({
    queryKey: ['covenant', 'formulas'],
    queryFn: () => fetchApi('/covenants/formulas'),
  })
}

export function useCreateFormula() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (payload: Omit<CovenantFormula, 'id'>) =>
      fetchApi('/covenants/formulas', {
        method: 'POST',
        body: JSON.stringify(payload),
      }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['covenant', 'formulas'] }),
  })
}

export function useUpdateFormula() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: Partial<Omit<CovenantFormula, 'id'>> }) =>
      fetchApi(`/covenants/formulas/${id}`, {
        method: 'PUT',
        body: JSON.stringify(payload),
      }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['covenant', 'formulas'] }),
  })
}

export function useDeleteFormula() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => fetchApi(`/covenants/formulas/${id}`, { method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['covenant', 'formulas'] }),
  })
}

export function useCovenantStatusDistribution() {
  return useQuery<CovenantStatusDistribution[]>({
    queryKey: ['covenant', 'analytics', 'status-distribution'],
    queryFn: () => fetchApi('/covenants/analytics/status-distribution'),
  })
}

export function useBreachProbabilities() {
  return useQuery<BreachProbabilityCell[]>({
    queryKey: ['covenant', 'analytics', 'breach-probabilities'],
    queryFn: () => fetchApi('/covenants/analytics/breach-probabilities'),
  })
}

export function useUpcomingDueDates(days = 90) {
  return useQuery<UpcomingDueDateItem[]>({
    queryKey: ['covenant', 'analytics', 'upcoming', days],
    queryFn: () => fetchApi(`/covenants/analytics/upcoming?days=${days}`),
  })
}

export function useCovenantTrend(covenantId: string) {
  return useQuery<CovenantTrendPoint[]>({
    queryKey: ['covenant', 'trend', covenantId],
    queryFn: () => fetchApi(`/covenants/${covenantId}/trend`),
    enabled: covenantId.length > 0,
  })
}

export function useGenerateWaiverLetter() {
  return useMutation({
    mutationFn: ({ itemId, payload }: { itemId: string; payload: WaiverLetterPayload }) =>
      fetchApi<WaiverLetterResponse>(`/covenants/monitoring-items/${itemId}/waiver/generate`, {
        method: 'POST',
        body: JSON.stringify(payload),
      }),
  })
}

export function useSendWaiverLetter() {
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: { recipientEmails: string[]; message?: string } }) =>
      fetchApi(`/covenants/waiver-letters/${id}/send`, {
        method: 'POST',
        body: JSON.stringify(payload),
      }),
  })
}

export function useDownloadWaiverLetter(id: string) {
  return useMutation({
    mutationFn: async () => {
      const response = await fetch(`/api/covenants/waiver-letters/${id}/download`, {
        method: 'GET',
        headers: {
          Accept: 'application/pdf',
          ...getAuthHeaders(),
        },
      })
      if (!response.ok) {
        throw new Error(`Failed to download waiver letter: ${response.statusText}`)
      }
      return response.blob()
    },
  })
}

export function useSignatures() {
  return useQuery<CovenantSignature[]>({
    queryKey: ['covenant', 'signatures'],
    queryFn: () => fetchApi('/covenants/signatures'),
  })
}
