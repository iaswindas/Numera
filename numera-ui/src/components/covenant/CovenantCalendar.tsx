'use client'

import type { UpcomingDueDateItem } from '@/services/covenantApi'

type CovenantCalendarProps = {
  items: UpcomingDueDateItem[]
  onSelect: (monitoringItemId: string) => void
}

function getWeekKey(dateValue: string): string {
  const date = new Date(dateValue)
  const day = date.getDay()
  const diff = date.getDate() - day + (day === 0 ? -6 : 1)
  const monday = new Date(date)
  monday.setDate(diff)
  return monday.toISOString().slice(0, 10)
}

export default function CovenantCalendar({ items, onSelect }: CovenantCalendarProps) {
  const grouped = items.reduce<Record<string, UpcomingDueDateItem[]>>((acc, item) => {
    const key = getWeekKey(item.dueDate)
    if (!acc[key]) {
      acc[key] = []
    }
    acc[key].push(item)
    return acc
  }, {})

  const orderedWeeks = Object.keys(grouped).sort()

  if (items.length === 0) {
    return <div style={{ color: 'var(--text-muted)', fontSize: 13 }}>No covenant due dates in the selected horizon.</div>
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {orderedWeeks.map((week) => (
        <section key={week} className="card" style={{ padding: 14 }}>
          <div style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 10, textTransform: 'uppercase', letterSpacing: 0.8 }}>
            Week of {new Date(week).toLocaleDateString()}
          </div>
          <div style={{ display: 'grid', gap: 8 }}>
            {grouped[week].map((item) => (
              <button
                key={item.monitoringItemId}
                type="button"
                onClick={() => onSelect(item.monitoringItemId)}
                className="btn btn-ghost"
                style={{
                  justifyContent: 'space-between',
                  border: '1px solid var(--border-subtle)',
                  borderRadius: 8,
                  padding: '10px 12px',
                }}
              >
                <div style={{ textAlign: 'left' }}>
                  <div style={{ fontWeight: 600 }}>{item.covenantName}</div>
                  <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>{item.customerName}</div>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <span style={{ fontSize: 12, color: 'var(--text-secondary)' }}>{new Date(item.dueDate).toLocaleDateString()}</span>
                  <span className={`badge-status ${item.status.toLowerCase()}`}>
                    <span className="dot" />
                    {item.status}
                  </span>
                </div>
              </button>
            ))}
          </div>
        </section>
      ))}
    </div>
  )
}
