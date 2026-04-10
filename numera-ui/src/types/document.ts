export type DocumentStatus = 'UPLOADED' | 'PROCESSING' | 'OCR_COMPLETE' | 'TABLES_DETECTED' | 'ZONES_CLASSIFIED' | 'READY' | 'ERROR'

export interface Document {
  id: string
  filename: string
  originalFilename: string
  fileType: string
  fileSize: number
  language: string
  processingStatus: DocumentStatus
  uploadedByName: string
  zonesDetected: number
  createdAt: string
  processingTimeMs?: number | null
  errorMessage?: string | null
}

export interface DocumentUploadResponse {
  documentId: string
  filename: string
  status: DocumentStatus
  message: string
}

export interface DocumentProcessingStatus {
  documentId: string
  status: DocumentStatus
  message?: string | null
}

export interface DocumentZone {
  id: string
  pageNumber: number | null
  zoneType: string
  zoneLabel: string
  confidenceScore: number
  classificationMethod: string | null
  detectedPeriods: string[]
  detectedCurrency: string | null
  detectedUnit: string | null
  status: string
  rowCount: number | null
}

export interface ZonesResponse {
  documentId: string
  zones: DocumentZone[]
}

export interface ProcessingStatus {
  documentId: string
  stage: 'OCR' | 'ZONE_DETECTION' | 'VALUE_MAPPING' | 'COMPLETE' | 'ERROR'
  progress: number
  message: string
  uploadedBy: string
  pagesProcessed?: number
  zonesDetected?: number
  valuesExtracted?: number
}
