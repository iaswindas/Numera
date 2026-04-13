export type ConfidenceLevel = 'HIGH' | 'MEDIUM' | 'LOW' | 'UNRESOLVED'

export interface BoundingBox {
  x: number
  y: number
  width: number
  height: number
}

export interface SpreadValue {
  id: string
  itemCode: string
  label: string
  mappedValue: number | null
  expressionType: string | null
  expressionDetail: Record<string, unknown> | null
  confidenceScore: number | null
  confidenceLevel: string | null
  sourcePage: number | null
  sourceText: string | null
  sourceDocumentName?: string | null
  sourceBbox?: string | null
  notes?: string | null
  isManualOverride: boolean
  isAutofilled: boolean
  isFormulaCell: boolean
  currency?: string | null
  smartFillSource?: string | null
}

export interface ModelLineItem {
  code: string
  label: string
  level: number
  isCategory: boolean
  isTotal: boolean
  section: string
}

export type SpreadStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'PUSHED'

export interface SpreadItem {
  id: string
  customerId: string
  documentId: string
  statementDate: string
  status: SpreadStatus
  currentVersion: number
  createdAt: string
}

export interface SpreadVersion {
  versionNumber: number
  action: string
  comments: string | null
  cellsChanged: number
  createdBy: string
  createdAt: string
}

export interface VersionHistoryResponse {
  spreadItemId: string
  versions: SpreadVersion[]
}

export interface MappingSummary {
  totalItems: number
  mapped: number
  highConfidence: number
  mediumConfidence: number
  lowConfidence: number
  unmapped: number
  formulaComputed: number
  autofilled: number
  coveragePct: number
}

export interface MappingResult {
  spreadItemId: string
  processingTimeMs: number
  summary: MappingSummary
  unitScale: number
  validations: Array<{ name: string; status: string; difference: number; severity?: string | null }>
  values: SpreadValue[]
}

export interface DocumentZone {
  id: string
  type: 'is' | 'bs' | 'cf' | 'notes' | 'other'
  page: number
  bounds: BoundingBox
  confidence: number
  rowCount: number
  mappedCount: number
}

export interface SpreadVarianceDto {
  lineItemId: string
  lineItemCode: string
  lineItemLabel?: string
  currentValue: number | null
  compareValue: number | null
  absoluteChange: number | null
  percentageChange: number | null
}

export interface Zone {
  id: string
  type: 'is' | 'bs' | 'cf' | 'notes' | 'other'
  pageNumber: number
  boundingBox: BoundingBox
  confidence: number
}

export interface ZoneData {
  zone: Zone
  pageNumber: number
}

export interface SpreadValueWithNotes extends SpreadValue {
  notes: string | null
}

export interface SpreadComment {
  id: string
  spreadItemId: string
  valueId: string | null
  content: string
  type: 'AUTO' | 'MANUAL'
  sourceUrl: string | null
  createdBy: string
  createdAt: string
}

export interface HistoricalSpread {
  id: string
  customerId: string
  statementDate: string
  status: SpreadStatus
  currentVersion: number
  templateName: string | null
}

export interface PageOperationRequest {
  type: 'MERGE' | 'ROTATE' | 'SPLIT' | 'CLEAN'
  pageNumbers: number[]
  rotationDegrees?: number
  splitAtPage?: number
}

export interface PageOperationResult {
  success: boolean
  message: string
  resultingPages: number
}
