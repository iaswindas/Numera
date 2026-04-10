export interface Customer {
  id: string
  tenantId: string
  customerCode: string
  name: string
  industry: string | null
  country: string | null
  relationshipManager: string | null
}
