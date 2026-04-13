'use client'

import { useMemo, useState } from 'react'
import { ResponsiveContainer, ScatterChart, Scatter, XAxis, YAxis, CartesianGrid, Tooltip, ZAxis } from 'recharts'
import type { PortfolioRatioRow } from '@/services/portfolioApi'
import { RATIO_CODES } from '@/services/portfolioApi'

interface Props {
  data: PortfolioRatioRow[]
}

export function RatioScatterPlot({ data }: Props) {
  const [xAxis, setXAxis] = useState<string>('CURRENT_RATIO')
  const [yAxis, setYAxis] = useState<string>('DEBT_TO_EQUITY')

  const xLabel = RATIO_CODES.find((r) => r.code === xAxis)?.label ?? xAxis
  const yLabel = RATIO_CODES.find((r) => r.code === yAxis)?.label ?? yAxis

  const points = useMemo(() => {
    return data
      .filter((row) => row.ratios[xAxis] != null && row.ratios[yAxis] != null)
      .map((row) => ({
        x: row.ratios[xAxis],
        y: row.ratios[yAxis],
        name: row.customerName,
      }))
  }, [data, xAxis, yAxis])

  return (
    <div>
      <div style={{ display: 'flex', gap: 16, marginBottom: 12, flexWrap: 'wrap' }}>
        <label style={{ fontSize: 13 }}>
          X Axis:{' '}
          <select value={xAxis} onChange={(e) => setXAxis(e.target.value)} className="input" style={{ width: 'auto', minWidth: 140 }}>
            {RATIO_CODES.map((r) => (
              <option key={r.code} value={r.code}>{r.label}</option>
            ))}
          </select>
        </label>
        <label style={{ fontSize: 13 }}>
          Y Axis:{' '}
          <select value={yAxis} onChange={(e) => setYAxis(e.target.value)} className="input" style={{ width: 'auto', minWidth: 140 }}>
            {RATIO_CODES.map((r) => (
              <option key={r.code} value={r.code}>{r.label}</option>
            ))}
          </select>
        </label>
      </div>

      {points.length === 0 ? (
        <div style={{ textAlign: 'center', padding: 32, color: 'var(--text-muted)', fontSize: 13 }}>
          No data points for the selected ratio combination.
        </div>
      ) : (
        <ResponsiveContainer width="100%" height={350}>
          <ScatterChart margin={{ top: 10, right: 20, bottom: 30, left: 20 }}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis
              type="number"
              dataKey="x"
              name={xLabel}
              label={{ value: xLabel, position: 'insideBottom', offset: -10 }}
            />
            <YAxis
              type="number"
              dataKey="y"
              name={yLabel}
              label={{ value: yLabel, angle: -90, position: 'insideLeft' }}
            />
            <ZAxis dataKey="name" name="Client" />
            <Tooltip
              cursor={{ strokeDasharray: '3 3' }}
              content={({ payload }) => {
                if (!payload?.length) return null
                const p = payload[0].payload as { name: string; x: number; y: number }
                return (
                  <div style={{ background: 'var(--bg-card, #fff)', border: '1px solid var(--border)', padding: '8px 12px', borderRadius: 6, fontSize: 12 }}>
                    <strong>{p.name}</strong>
                    <div>{xLabel}: {p.x.toFixed(2)}</div>
                    <div>{yLabel}: {p.y.toFixed(2)}</div>
                  </div>
                )
              }}
            />
            <Scatter data={points} fill="#3b82f6" />
          </ScatterChart>
        </ResponsiveContainer>
      )}
    </div>
  )
}
