export interface ApiError {
  status: number
  message: string
  details?: Record<string, string[]>
}

export interface PaginatedResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface ApiResponse<T> {
  data: T
  success: boolean
  message?: string
}
