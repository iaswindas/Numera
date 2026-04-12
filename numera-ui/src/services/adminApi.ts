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

export function useCreateLanguage() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (payload: { code: string; name: string; ocrSupported?: boolean; enabled?: boolean }) =>
      fetchApi('/admin/languages', { method: 'POST', body: JSON.stringify(payload) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'languages'] }),
  })
}

// --- Zone Management ---
export interface ManagedZone {
  id: string
  name: string
  code: string
  color: string
  description: string | null
  sortOrder: number
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export function useAdminZones() {
  return useQuery<ManagedZone[]>({ queryKey: ['admin', 'zones'], queryFn: () => fetchApi('/admin/zones') })
}

export function useCreateZone() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (payload: Record<string, unknown>) => fetchApi('/admin/zones', { method: 'POST', body: JSON.stringify(payload) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'zones'] }),
  })
}

export function useUpdateZone() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...payload }: { id: string } & Record<string, unknown>) =>
      fetchApi(`/admin/zones/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'zones'] }),
  })
}

export function useToggleZone() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) =>
      fetchApi(`/admin/zones/${id}/active`, { method: 'PATCH', body: JSON.stringify({ active }) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'zones'] }),
  })
}

export function useDeleteZone() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => fetchApi(`/admin/zones/${id}`, { method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'zones'] }),
  })
}

// --- AI Models ---
export interface AiModel {
  id: string
  name: string
  type: string
  version: string
  status: string
  accuracy: number
  lastRetrained: string
}

export interface AiModelMetrics {
  modelId: string
  accuracy: number
  precision: number
  recall: number
  f1Score: number
  lastEvaluated: string
}

export function useAiModels() {
  return useQuery<AiModel[]>({ queryKey: ['admin', 'ai-models'], queryFn: () => fetchApi('/admin/ai-models') })
}

export function useAiModelMetrics(modelId: string) {
  return useQuery<AiModelMetrics>({
    queryKey: ['admin', 'ai-models', modelId, 'metrics'],
    queryFn: () => fetchApi(`/admin/ai-models/${modelId}/metrics`),
    enabled: !!modelId,
  })
}

export function useRetrainModel() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (modelId: string) => fetchApi(`/admin/ai-models/${modelId}/retrain`, { method: 'POST' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'ai-models'] }),
  })
}

export function usePromoteModel() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ modelId, targetVersion }: { modelId: string; targetVersion?: string }) =>
      fetchApi(`/admin/ai-models/${modelId}/promote`, { method: 'POST', body: JSON.stringify({ targetVersion }) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'ai-models'] }),
  })
}

// --- Model Templates ---
export interface ModelTemplate {
  id: string
  name: string
  version: number
  currency: string
  active: boolean
  lineItems: TemplateLineItem[]
  validations: TemplateValidation[]
}

export interface TemplateLineItem {
  id: string
  itemCode: string
  label: string
  zone: string
  category: string | null
  itemType: string
  formula: string | null
  required: boolean
  isTotal: boolean
  indentLevel: number
  signConvention: string
  aliases: string[]
  sortOrder: number
}

export interface TemplateValidation {
  id: string
  name: string
  expression: string
  severity: string
}

export function useModelTemplates() {
  return useQuery<ModelTemplate[]>({ queryKey: ['model-templates'], queryFn: () => fetchApi('/model-templates') })
}

export function useModelTemplate(id: string) {
  return useQuery<ModelTemplate>({
    queryKey: ['model-templates', id],
    queryFn: () => fetchApi(`/model-templates/${id}`),
    enabled: !!id,
  })
}

export function useCreateModelTemplate() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (payload: Record<string, unknown>) => fetchApi('/model-templates', { method: 'POST', body: JSON.stringify(payload) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['model-templates'] }),
  })
}

export function useUpdateModelTemplate() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...payload }: { id: string } & Record<string, unknown>) =>
      fetchApi(`/model-templates/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['model-templates'] }),
  })
}

// --- Workflow CRUD ---
export function useCreateWorkflow() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (payload: Record<string, unknown>) => fetchApi('/admin/workflows', { method: 'POST', body: JSON.stringify(payload) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'workflows'] }),
  })
}

export function useUpdateWorkflow() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...payload }: { id: string } & Record<string, unknown>) =>
      fetchApi(`/admin/workflows/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'workflows'] }),
  })
}

export function useDeleteWorkflow() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => fetchApi(`/admin/workflows/${id}`, { method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'workflows'] }),
  })
}

// --- User Management Extensions ---
export function useUpdateAdminUser() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...payload }: { id: string } & Record<string, unknown>) =>
      fetchApi(`/admin/users/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'users'] }),
  })
}

export function useDeleteAdminUser() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => fetchApi(`/admin/users/${id}`, { method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'users'] }),
  })
}

export function useBulkUploadUsers() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (file: File) => {
      const formData = new FormData()
      formData.append('file', file)
      return fetchApi<{ created: number; skipped: number; errors: string[] }>('/admin/users/bulk-upload', {
        method: 'POST',
        body: formData,
      })
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'users'] }),
  })
}
