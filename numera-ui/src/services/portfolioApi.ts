import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchApi } from './api'

// ── Types ──

export interface PortfolioRatioRow {
  customerId: string
  customerName: string
  statementDate: string
  ratios: Record<string, number>
  alerts: RatioAlert[]
}

export interface RatioTrendPoint {
  statementDate: string
  value: number
}

export interface RatioAlert {
  ratioCode: string
  ratioLabel: string
  previousValue: number | null
  currentValue: number
  changePercent: number | null
  severity: 'INFO' | 'WARNING' | 'CRITICAL'
}

export interface PortfolioQueryRequest {
  ratioCode?: string
  customerIds?: string[]
  minValue?: number
  maxValue?: number
  fromDate?: string
  toDate?: string
  sortBy?: string
  sortDirection?: string
  page?: number
  size?: number
}

export interface ShareDashboardRequest {
  configJson: string
  title?: string
  expiryHours?: number
}

export interface ShareDashboardResponse {
  token: string
  expiresAt: string
  shareUrl: string
}

export interface SharedDashboard {
  id: string
  token: string
  title: string | null
  dashboardConfigJson: string
  expiresAt: string
  viewCount: number
  active: boolean
}

// ── Ratio Codes ──

export const RATIO_CODES = [
  { code: 'CURRENT_RATIO', label: 'Current Ratio' },
  { code: 'QUICK_RATIO', label: 'Quick Ratio' },
  { code: 'DEBT_TO_EQUITY', label: 'Debt / Equity' },
  { code: 'DEBT_SERVICE_COVERAGE', label: 'DSCR' },
  { code: 'RETURN_ON_EQUITY', label: 'ROE' },
  { code: 'RETURN_ON_ASSETS', label: 'ROA' },
  { code: 'NET_PROFIT_MARGIN', label: 'Net Margin' },
  { code: 'GROSS_PROFIT_MARGIN', label: 'Gross Margin' },
  { code: 'ASSET_TURNOVER', label: 'Asset Turnover' },
  { code: 'INTEREST_COVERAGE', label: 'Interest Coverage' },
  { code: 'LEVERAGE_RATIO', label: 'Leverage' },
  { code: 'WORKING_CAPITAL', label: 'Working Capital' },
] as const

// ── Hooks ──

export function usePortfolioRatios() {
  return useQuery({
    queryKey: ['portfolio', 'ratios'],
    queryFn: () => fetchApi<PortfolioRatioRow[]>('/portfolio/ratios'),
  })
}

export function useRatioTrends(customerId: string | null, ratioCode: string | null) {
  return useQuery({
    queryKey: ['portfolio', 'trends', customerId, ratioCode],
    queryFn: () =>
      fetchApi<RatioTrendPoint[]>(
        `/portfolio/ratios/trends?customerId=${customerId}&ratioCode=${ratioCode}`
      ),
    enabled: !!customerId && !!ratioCode,
  })
}

export function usePortfolioQuery(request: PortfolioQueryRequest | null) {
  return useQuery({
    queryKey: ['portfolio', 'query', request],
    queryFn: () =>
      fetchApi<{ content: PortfolioRatioRow[] }>('/portfolio/query', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(request),
      }),
    enabled: !!request,
  })
}

export function usePortfolioAlerts(threshold?: number) {
  return useQuery({
    queryKey: ['portfolio', 'alerts', threshold],
    queryFn: () =>
      fetchApi<RatioAlert[]>(`/portfolio/alerts${threshold ? `?threshold=${threshold}` : ''}`),
  })
}

export function useMaterializeSnapshots() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () =>
      fetchApi<{ status: string }>('/portfolio/materialize', { method: 'POST' }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['portfolio'] })
    },
  })
}

export function useShareDashboard() {
  return useMutation({
    mutationFn: (request: ShareDashboardRequest) =>
      fetchApi<ShareDashboardResponse>('/dashboard/share', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(request),
      }),
  })
}

export function useSharedDashboard(token: string | null) {
  return useQuery({
    queryKey: ['dashboard', 'shared', token],
    queryFn: () => fetchApi<SharedDashboard>(`/dashboard/shared/${token}`),
    enabled: !!token,
  })
}
