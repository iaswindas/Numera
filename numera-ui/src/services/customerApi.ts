import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { fetchApi } from './api'
import type { Customer } from '@/types/customer'

export function useCustomers(params?: { query?: string; industry?: string; country?: string }) {
  const query = new URLSearchParams()
  if (params?.query) query.set('query', params.query)
  if (params?.industry) query.set('industry', params.industry)
  if (params?.country) query.set('country', params.country)

  return useQuery<Customer[]>({
    queryKey: ['customers', params],
    queryFn: () => fetchApi(`/customers${query.toString() ? `?${query.toString()}` : ''}`),
  })
}

export function useCustomer(id: string) {
  return useQuery<Customer>({
    queryKey: ['customer', id],
    queryFn: () => fetchApi(`/customers/${id}`),
    enabled: !!id,
  })
}

export function useCreateCustomer() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: {
      customerCode: string
      name: string
      industry?: string
      country?: string
      relationshipManager?: string
    }) => fetchApi('/customers', { method: 'POST', body: JSON.stringify(data) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['customers'] }),
  })
}

export function useUpdateCustomer() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, data }: {
      id: string
      data: {
        customerCode: string
        name: string
        industry?: string
        country?: string
        relationshipManager?: string
      }
    }) => fetchApi(`/customers/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['customers'] })
      qc.invalidateQueries({ queryKey: ['customer'] })
    },
  })
}
