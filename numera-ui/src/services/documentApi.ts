import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { fetchApi } from './api'
import type { Document, DocumentProcessingStatus, DocumentUploadResponse, ZonesResponse } from '@/types/document'

export function useDocuments(params?: { customerId?: string }) {
  const query = new URLSearchParams()
  if (params?.customerId) query.set('customerId', params.customerId)

  return useQuery<Document[]>({
    queryKey: ['documents', params],
    queryFn: () => fetchApi(`/documents${query.toString() ? `?${query.toString()}` : ''}`),
    refetchInterval: 5000,
  })
}

export function useDocument(id: string) {
  return useQuery<Document>({
    queryKey: ['document', id],
    queryFn: () => fetchApi(`/documents/${id}`),
    enabled: !!id,
  })
}

export function useDocumentStatus(id: string) {
  return useQuery<DocumentProcessingStatus>({
    queryKey: ['document', id, 'status'],
    queryFn: () => fetchApi(`/documents/${id}/status`),
    enabled: !!id,
    refetchInterval: 3000,
  })
}

export function useDocumentZones(id: string) {
  return useQuery<ZonesResponse>({
    queryKey: ['document', id, 'zones'],
    queryFn: () => fetchApi(`/documents/${id}/zones`),
    enabled: !!id,
  })
}

export function useUploadDocument() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (formData: FormData) =>
      fetchApi<DocumentUploadResponse>('/documents/upload', { method: 'POST', body: formData }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['documents'] }),
  })
}

export function useProcessDocument() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => fetchApi<DocumentProcessingStatus>(`/documents/${id}/process`, { method: 'POST' }),
    onSuccess: (_data, id) => {
      qc.invalidateQueries({ queryKey: ['documents'] })
      qc.invalidateQueries({ queryKey: ['document', id] })
      qc.invalidateQueries({ queryKey: ['document', id, 'status'] })
    },
  })
}

export function useDeleteDocument() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => fetchApi(`/documents/${id}`, { method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['documents'] }),
  })
}
