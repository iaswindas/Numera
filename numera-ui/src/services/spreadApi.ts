import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { fetchApi } from './api'
import type { MappingResult, SpreadItem, SpreadValue, VersionHistoryResponse } from '@/types/spread'

export function useSpreadItem(spreadId: string) {
  return useQuery({
    queryKey: ['spread', spreadId],
    queryFn: () => fetchApi<SpreadItem>(`/spread-items/${spreadId}`),
    enabled: !!spreadId,
  })
}

export function useSpreadValues(spreadId: string) {
  return useQuery({
    queryKey: ['spread', spreadId, 'values'],
    queryFn: () => fetchApi<SpreadValue[]>(`/spread-items/${spreadId}/values`),
    enabled: !!spreadId,
  })
}

export function useCustomerSpreads(customerId: string) {
  return useQuery({
    queryKey: ['spreads', 'customer', customerId],
    queryFn: () => fetchApi<SpreadItem[]>(`/customers/${customerId}/spread-items`),
    enabled: !!customerId,
  })
}

export function useProcessSpread() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (spreadId: string) =>
      fetchApi<MappingResult>(`/spread-items/${spreadId}/process`, { method: 'POST' }),
    onSuccess: (_data, spreadId) => {
      queryClient.invalidateQueries({ queryKey: ['spread', spreadId] })
      queryClient.invalidateQueries({ queryKey: ['spread', spreadId, 'values'] })
    },
  })
}

export function useUpdateSpreadValue(spreadId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ valueId, mappedValue, overrideComment, expressionType }: { valueId: string; mappedValue?: number; overrideComment?: string; expressionType?: string }) =>
      fetchApi(`/spread-items/${spreadId}/values/${valueId}`, {
        method: 'PUT',
        body: JSON.stringify({ mappedValue, overrideComment, expressionType }),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['spread', spreadId] })
      queryClient.invalidateQueries({ queryKey: ['spread', spreadId, 'values'] })
    },
  })
}

export function useAcceptAll(spreadId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (threshold: 'HIGH' | 'MEDIUM') =>
      fetchApi(`/spread-items/${spreadId}/values/bulk-accept`, {
        method: 'POST',
        body: JSON.stringify({ confidenceThreshold: threshold }),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['spread', spreadId] })
      queryClient.invalidateQueries({ queryKey: ['spread', spreadId, 'values'] })
    },
  })
}

export function useSubmitSpread() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ spreadId, comments, overrideValidationWarnings }: { spreadId: string; comments?: string; overrideValidationWarnings?: boolean }) =>
      fetchApi(`/spread-items/${spreadId}/submit`, {
        method: 'POST',
        body: JSON.stringify({ comments, overrideValidationWarnings: overrideValidationWarnings ?? false }),
      }),
    onSuccess: (_data, { spreadId }) => {
      queryClient.invalidateQueries({ queryKey: ['spread', spreadId] })
      queryClient.invalidateQueries({ queryKey: ['spreads'] })
    },
  })
}

export function useSpreadHistory(spreadId: string) {
  return useQuery({
    queryKey: ['spread', spreadId, 'history'],
    queryFn: () => fetchApi<VersionHistoryResponse>(`/spread-items/${spreadId}/history`),
    enabled: !!spreadId,
  })
}

export function useSpreadDiff(spreadId: string, fromVersion: number, toVersion: number) {
  return useQuery({
    queryKey: ['spread', spreadId, 'diff', fromVersion, toVersion],
    queryFn: () => fetchApi(`/spread-items/${spreadId}/diff/${fromVersion}/${toVersion}`),
    enabled: !!spreadId && fromVersion > 0 && toVersion > 0,
  })
}

export function useRollbackSpread() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ spreadId, version, comments }: { spreadId: string; version: number; comments: string }) =>
      fetchApi(`/spread-items/${spreadId}/rollback/${version}`, {
        method: 'POST',
        body: JSON.stringify({ comments }),
      }),
    onSuccess: (_data, { spreadId }) => {
      queryClient.invalidateQueries({ queryKey: ['spread', spreadId] })
      queryClient.invalidateQueries({ queryKey: ['spread', spreadId, 'values'] })
    },
  })
}

// ── Spread Locking ────────────────────────────────────────────────────

export function useSpreadLock(spreadId: string) {
  return useQuery({
    queryKey: ['spread', spreadId, 'lock'],
    queryFn: () => fetchApi<{ locked: boolean; lockedBy?: string; lockedByName?: string; acquiredAt?: string }>(`/spread-items/${spreadId}/lock`),
    enabled: !!spreadId,
    refetchInterval: 15000,
  })
}

export function useAcquireLock() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (spreadId: string) =>
      fetchApi<{ spreadItemId: string; lockedBy: string; lockedByName: string; acquiredAt: string }>(`/spread-items/${spreadId}/lock`, { method: 'POST' }),
    onSuccess: (_data, spreadId) => {
      queryClient.invalidateQueries({ queryKey: ['spread', spreadId, 'lock'] })
    },
  })
}

export function useReleaseLock() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (spreadId: string) =>
      fetchApi(`/spread-items/${spreadId}/lock`, { method: 'DELETE' }),
    onSuccess: (_data, spreadId) => {
      queryClient.invalidateQueries({ queryKey: ['spread', spreadId, 'lock'] })
    },
  })
}

export function useLockHeartbeat() {
  return useMutation({
    mutationFn: (spreadId: string) =>
      fetchApi<{ extended: boolean }>(`/spread-items/${spreadId}/lock/heartbeat`, { method: 'POST' }),
  })
}

// ── Validation ────────────────────────────────────────────────────────

export function useSpreadValidation(spreadId: string) {
  return useQuery({
    queryKey: ['spread', spreadId, 'validation'],
    queryFn: () => fetchApi<MappingResult>(`/spread-items/${spreadId}/process`, { method: 'POST' }),
    enabled: false, // manually triggered
  })
}
