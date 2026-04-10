// Authentication Types
export interface User {
  id: string
  email: string
  fullName: string
  tenantId: string
  roles: string[]
}

export interface AuthTokens {
  accessToken: string
  refreshToken: string
  expiresInSec: number
}
