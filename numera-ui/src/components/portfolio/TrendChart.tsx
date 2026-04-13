'use client'

import { ResponsiveContainer, LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip } from 'recharts'
import type { RatioTrendPoint } from '@/services/portfolioApi'

interface Props {
  data: RatioTrendPoint[]
  ratioLabel: string
  customerName: string
}

export function TrendChart({ data, ratioLabel, customerName }: Props) {
  if (data.length === 0) {
    return (
      <div style={{ textAlign: 'center', padding: 32, color: 'var(--text-muted)', fontSize: 13 }}>
        No trend data available for this selection.
      </div>
    )
  }

  return (
    <div>
      <div style={{ fontSize: 13, fontWeight: 500, marginBottom: 8 }}>
        {ratioLabel} — {customerName}
      </div>
      <ResponsiveContainer width="100%" height={280}>
        <LineChart data={data} margin={{ top: 10, right: 20, bottom: 20, left: 20 }}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis
            dataKey="statementDate"
            label={{ value: 'Period', position: 'insideBottom', offset: -10 }}
            tick={{ fontSize: 11 }}
          />
          <YAxis
            label={{ value: ratioLabel, angle: -90, position: 'insideLeft' }}
            tick={{ fontSize: 11 }}
          />
          <Tooltip
            formatter={(value: number) => [value.toFixed(4), ratioLabel]}
            labelFormatter={(label: string) => `Period: ${label}`}
          />
          <Line
            type="monotone"
            dataKey="value"
            stroke="#3b82f6"
            strokeWidth={2}
            dot={{ r: 4 }}
            activeDot={{ r: 6 }}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}
