'use client'

import { useMemo, useRef, useCallback } from 'react'
import { AgGridReact } from 'ag-grid-react'
import type { ColDef, CellClassParams, CellClickedEvent } from 'ag-grid-community'
import type { SpreadValue } from '@/types/spread'

interface SpreadTableProps {
  values: SpreadValue[]
  isLocked: boolean
  onCellClick?: (value: SpreadValue) => void
  onValueEdit?: (valueId: string, newValue: number | undefined) => void
  selectedCellCode: string | null
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

export function SpreadTable({ values, isLocked, onCellClick, onValueEdit, selectedCellCode }: SpreadTableProps) {
  const gridRef = useRef<AgGridReact>(null)

  const columnDefs = useMemo<ColDef[]>(
    () => [
      {
        headerName: 'Code',
        field: 'itemCode',
        width: 140,
        pinned: 'left',
        cellStyle: { fontFamily: 'var(--font-mono)', fontSize: 12 },
      },
      {
        headerName: 'Label',
        field: 'label',
        flex: 1,
        minWidth: 180,
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
        cellStyle: (params: CellClassParams) => ({
          ...confidenceColor(params.data?.confidenceLevel),
          fontWeight: params.data?.isFormulaCell ? 600 : 400,
          fontStyle: params.data?.isManualOverride ? 'italic' : 'normal',
        }),
      },
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
        cellStyle: { fontSize: 11 },
      },
    ],
    [isLocked]
  )

  const onCellClicked = useCallback(
    (event: CellClickedEvent) => {
      if (!onCellClick || !event.data) return
      onCellClick(event.data as SpreadValue)
    },
    [onCellClick]
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
        rowData={values}
        columnDefs={columnDefs}
        getRowId={(params) => params.data.id}
        animateRows
        onCellClicked={onCellClicked}
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
