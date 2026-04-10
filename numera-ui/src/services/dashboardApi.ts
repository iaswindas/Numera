import { useQuery } from '@tanstack/react-query'
import { fetchApi } from './api'

export function useDashboardStats() {
  return useQuery({
    queryKey: ['dashboard', 'stats'],
    queryFn: () => fetchApi('/dashboard/stats'),
    refetchInterval: 10000,
  })
}
