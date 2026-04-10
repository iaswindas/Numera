import { useQuery } from '@tanstack/react-query'
import { fetchApi } from './api'

export function useSpreadingSummaryReport() {
  return useQuery({ queryKey: ['reports', 'spreading'], queryFn: () => fetchApi('/reports/spreading-summary') })
}

export function useCovenantSummaryReport() {
  return useQuery({ queryKey: ['reports', 'covenant'], queryFn: () => fetchApi('/reports/covenant-summary') })
}

export function useAuditTrailReport(entityType?: string) {
  return useQuery({
    queryKey: ['reports', 'audit', entityType],
    queryFn: () => fetchApi(`/reports/audit-trail${entityType ? `?entityType=${encodeURIComponent(entityType)}` : ''}`),
  })
}
