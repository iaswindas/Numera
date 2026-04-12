'use client'

import {
  Area,
  CartesianGrid,
  ComposedChart,
  Legend,
  Line,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import type { CovenantTrendPoint } from '@/services/covenantApi'

type TrendChartProps = {
  data: CovenantTrendPoint[]
  upperLimit?: number
  lowerLimit?: number
}

export default function TrendChart({ data, upperLimit, lowerLimit }: TrendChartProps) {
  if (data.length === 0) {
    return <div style={{ color: 'var(--text-muted)', fontSize: 13 }}>No trend data available for the selected covenant.</div>
  }

  const maxValue = Math.max(...data.map((d) => d.value))
  const zoneCap = upperLimit ?? maxValue

  const chartData = data.map((point) => ({
    ...point,
    redZone: upperLimit ? Math.max(point.value - upperLimit, 0) : 0,
    greenZone: point.value,
    zoneCap,
  }))

  return (
    <div style={{ width: '100%', height: 320 }}>
      <ResponsiveContainer>
        <ComposedChart data={chartData} margin={{ top: 12, right: 16, left: 6, bottom: 6 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="rgba(148,163,184,0.2)" />
          <XAxis dataKey="period" stroke="var(--text-secondary)" fontSize={11} />
          <YAxis stroke="var(--text-secondary)" fontSize={11} />
          <Tooltip
            contentStyle={{
              background: 'var(--bg-secondary)',
              border: '1px solid var(--border-subtle)',
              borderRadius: 10,
              color: 'var(--text-primary)',
            }}
          />
          <Legend />

          <Area type="monotone" dataKey="greenZone" fill="rgba(16,185,129,0.15)" stroke="none" name="Safe zone" />
          <Area type="monotone" dataKey="redZone" fill="rgba(239,68,68,0.2)" stroke="none" name="Breach zone" stackId="risk" />

          <Line type="monotone" dataKey="value" stroke="#60a5fa" strokeWidth={2.5} dot={{ r: 3 }} name="Covenant value" />

          {upperLimit !== undefined ? (
            <ReferenceLine y={upperLimit} stroke="#ef4444" strokeDasharray="6 4" label="Upper limit" />
          ) : null}
          {lowerLimit !== undefined ? (
            <ReferenceLine y={lowerLimit} stroke="#10b981" strokeDasharray="6 4" label="Lower limit" />
          ) : null}
        </ComposedChart>
      </ResponsiveContainer>
    </div>
  )
}
