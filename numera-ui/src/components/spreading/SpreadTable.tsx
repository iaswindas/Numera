'use client'

import { useMemo, useRef, useCallback } from 'react'
import { AgGridReact } from 'ag-grid-react'
import type { ColDef, CellClassParams, CellClickedEvent, CellDoubleClickedEvent } from 'ag-grid-community'
import type { SpreadValue, SpreadVarianceDto } from '@/types/spread'
import { NoteEditor } from './NoteEditor'
import { useUpdateSpreadValueNotes } from '@/services/spreadApi'

interface SpreadTableProps {
  values: SpreadValue[]
  isLocked: boolean
  onCellClick?: (value: SpreadValue) => void
  onCellDoubleClick?: (value: SpreadValue) => void
  onValueEdit?: (valueId: string, newValue: number | undefined) => void
  selectedCellCode: string | null
  showVariance?: boolean
  varianceData?: SpreadVarianceDto[]
  showOnlyMapped?: boolean
  showSmartFill?: boolean
  showCurrency?: boolean
  spreadId: string
}

interface SpreadTableRow extends SpreadValue {
  notes?: string | null
}

const confidenceColor = (level: string | null) => {
  switch (level) {
    case 'HIGH':
      return { background: 'rgba(52, 199, 89, 0.08)' }
    case 'MEDIUM':
      return { background: 'rgba(255, 159, 10, 0.08)' }
    case 'LOW':
      return { background: 'rgba(255, 69, 58, 0.08)' }
    default:
      return {}
  }
}

export function SpreadTable({
  values,
  isLocked,
  onCellClick,
  onCellDoubleClick,
  onValueEdit,
  selectedCellCode,
  showVariance = false,
  varianceData = [],
  showOnlyMapped = false,
  showSmartFill = false,
  showCurrency = false,
  spreadId,
}: SpreadTableProps) {
  const gridRef = useRef<AgGridReact>(null)
  const updateNotesMutation = useUpdateSpreadValueNotes(spreadId)

  const varianceMap = useMemo(
    () => Object.fromEntries(varianceData.map((v) => [v.lineItemCode, v])),
    [varianceData]
  )

  const filteredValues = useMemo(() => {
    if (!showOnlyMapped) return values
    return values.filter((v) => v.mappedValue != null)
  }, [values, showOnlyMapped])

  const columnDefs = useMemo<ColDef[]>(
    () => [
      {
        headerName: 'Code',
        field: 'itemCode',
        width: 140,
        pinned: 'left',
        cellStyle: () => ({ fontFamily: 'var(--font-mono)', fontSize: 12 }),
      },
      {
        headerName: 'Label',
        field: 'label',
        flex: 1,
        minWidth: 180,
      },
      {
        headerName: 'Notes',
        field: 'id',
        width: 60,
        cellRenderer: (params: { data: SpreadTableRow }) => {
          return (
            <NoteEditor
              valueId={params.data.id}
              initialNotes={params.data.notes ?? ''}
              onSave={async (notes) => {
                await updateNotesMutation.mutateAsync({ valueId: params.data.id, notes })
              }}
            />
          )
        },
        sortable: false,
        resizable: false,
      },
      {
        headerName: 'Mapped Value',
        field: 'mappedValue',
        width: 150,
        editable: !isLocked,
        type: 'numericColumn',
        valueFormatter: (p) => {
          if (p.value == null) return '-'
          return Number(p.value).toLocaleString(undefined, { minimumFractionDigits: 0, maximumFractionDigits: 2 })
        },
        cellStyle: (params: CellClassParams) => {
          const confidenceStyle = confidenceColor(params.data?.confidenceLevel)
          const style: Record<string, string | number> = {
            fontWeight: params.data?.isFormulaCell ? 600 : 400,
            fontStyle: params.data?.isManualOverride ? 'italic' : 'normal',
          }
          if (typeof confidenceStyle.background === 'string') {
            style.background = confidenceStyle.background
          }
          if (showSmartFill && params.data?.isAutofilled) {
            style.background = 'rgba(168, 85, 247, 0.10)'
            style.borderLeft = '3px solid #a855f7'
          }
          return style
        },
      },
      ...(showCurrency
        ? [
            {
              headerName: 'Currency',
              field: 'currency' as const,
              width: 80,
              valueFormatter: (p: { value: string | null | undefined }) => p.value ?? 'USD',
              cellStyle: () => ({ fontSize: 11, color: 'var(--text-muted)' }),
            },
          ]
        : []),
      ...(showVariance
        ? [
            {
              headerName: 'Change ($)',
              width: 110,
              valueGetter: (params: { data: SpreadValue }) => varianceMap[params.data?.itemCode]?.absoluteChange ?? null,
              valueFormatter: (p: { value: number | null }) => {
                if (p.value == null) return '-'
                const formatted = Number(p.value).toLocaleString(undefined, { minimumFractionDigits: 0, maximumFractionDigits: 2 })
                return p.value >= 0 ? `+${formatted}` : formatted
              },
              cellStyle: (params: CellClassParams) => {
                const variance = varianceMap[(params.data as SpreadValue | undefined)?.itemCode ?? '']
                const style: Record<string, string | number> = {
                  background: 'transparent',
                  color: 'inherit',
                }
                if (!variance?.percentageChange) return style
                const pctChange = Math.abs(variance.percentageChange)
                if (pctChange > 25) style.background = 'rgba(255, 69, 58, 0.12)'
                if (pctChange > 10 && pctChange <= 25) style.background = 'rgba(255, 159, 10, 0.12)'
                return style
              },
            },
            {
              headerName: 'Change (%)',
              width: 100,
              valueGetter: (params: { data: SpreadValue }) => varianceMap[params.data?.itemCode]?.percentageChange ?? null,
              valueFormatter: (p: { value: number | null }) => {
                if (p.value == null) return '-'
                return `${Number(p.value).toFixed(1)}%`
              },
              cellStyle: (params: CellClassParams) => {
                const variance = varianceMap[(params.data as SpreadValue | undefined)?.itemCode ?? '']
                const style: Record<string, string | number> = {
                  background: 'transparent',
                  color: 'inherit',
                  fontWeight: 400,
                }
                if (!variance?.percentageChange) return style
                const pctChange = Math.abs(variance.percentageChange)
                if (pctChange > 25) {
                  style.background = 'rgba(255, 69, 58, 0.12)'
                  style.fontWeight = 600
                  style.color = '#ff453a'
                } else if (pctChange > 10) {
                  style.background = 'rgba(255, 159, 10, 0.12)'
                  style.fontWeight = 600
                  style.color = '#ff9f0a'
                }
                return style
              },
            },
          ]
        : []),
      {
        headerName: 'Confidence',
        field: 'confidenceLevel',
        width: 110,
        cellRenderer: (params: { value: string | null }) => {
          if (!params.value) return '-'
          const color =
            params.value === 'HIGH'
              ? '#34c759'
              : params.value === 'MEDIUM'
              ? '#ff9f0a'
              : '#ff453a'
          return `<span style="display:inline-flex;align-items:center;gap:4px"><span style="width:6px;height:6px;border-radius:50%;background:${color};display:inline-block"></span>${params.value}</span>`
        },
      },
      {
        headerName: 'Source',
        field: 'sourcePage',
        width: 80,
        valueFormatter: (p) => (p.value ? `Pg ${p.value}` : '-'),
      },
      {
        headerName: 'Type',
        width: 80,
        valueGetter: (params) => {
          if (params.data?.isFormulaCell) return 'Formula'
          if (params.data?.isManualOverride) return 'Manual'
          if (params.data?.isAutofilled) return 'Auto'
          return 'ML'
        },
        cellStyle: () => ({ fontFamily: 'inherit', fontSize: 11 }),
      },
    ],
    [isLocked, showVariance, varianceMap, showSmartFill, showCurrency, updateNotesMutation]
  )

  const onCellClicked = useCallback(
    (event: CellClickedEvent) => {
      if (!onCellClick || !event.data) return
      onCellClick(event.data as SpreadValue)
    },
    [onCellClick]
  )

  const onCellDoubleClicked = useCallback(
    (event: CellDoubleClickedEvent) => {
      if (!onCellDoubleClick || !event.data) return
      if (event.colDef.field !== 'mappedValue') return
      onCellDoubleClick(event.data as SpreadValue)
    },
    [onCellDoubleClick]
  )

  const onCellValueChanged = useCallback(
    (event: { data: SpreadValue; newValue: unknown }) => {
      if (!onValueEdit) return
      const newVal = event.newValue === '' || event.newValue == null ? undefined : Number(event.newValue)
      onValueEdit(event.data.id, newVal)
    },
    [onValueEdit]
  )

  const getRowStyle = useCallback(
    (params: { data?: SpreadValue }) => {
      if (params.data?.itemCode === selectedCellCode) {
        return { background: 'rgba(10, 132, 255, 0.12)' }
      }
      return undefined
    },
    [selectedCellCode]
  )

  return (
    <div className="ag-theme-alpine-dark" style={{ width: '100%', height: '100%' }}>
      <AgGridReact
        ref={gridRef}
        rowData={filteredValues}
        columnDefs={columnDefs}
        getRowId={(params) => params.data.id}
        animateRows
        onCellClicked={onCellClicked}
        onCellDoubleClicked={onCellDoubleClicked}
        onCellValueChanged={onCellValueChanged}
        getRowStyle={getRowStyle}
        headerHeight={32}
        rowHeight={30}
        suppressMovableColumns
        defaultColDef={{ sortable: true, resizable: true }}
      />
    </div>
  )
}
