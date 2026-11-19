import { useState } from 'react'
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { formatCompactPrice, formatDate, formatPrice } from '@/lib/format'
import type { Offer } from '@/lib/types'

const COLORS = ['#0f766e', '#2563eb', '#d97706', '#9333ea', '#dc2626', '#0891b2']

/** Historia cen per portal (linie schodkowe); aktywne oferty przedłużone do dziś. */
export function PriceHistoryChart({ offers }: { offers: Offer[] }) {
  const [now] = useState(() => Date.now())
  const series = offers.map((o, i) => ({
    key: `o${o.id}`,
    name: `${o.sourceName}${o.active ? '' : ' (nieaktywna)'}`,
    color: COLORS[i % COLORS.length],
    points: [
      ...o.priceHistory.map((h) => ({ t: new Date(h.changedAt).getTime(), price: h.price })),
      ...(o.active ? [{ t: now, price: o.price }] : []),
    ],
  }))
  const times = Array.from(new Set(series.flatMap((s) => s.points.map((p) => p.t)))).sort((a, b) => a - b)
  const data = times.map((t) => {
    const row: Record<string, number> = { t }
    for (const s of series) {
      const point = s.points.find((p) => p.t === t)
      if (point) row[s.key] = point.price
    }
    return row
  })

  if (times.length === 0) return <p className="text-sm text-muted-foreground">Brak historii cen.</p>

  return (
    <div className="h-72 w-full">
      <ResponsiveContainer>
        <LineChart data={data} margin={{ top: 10, right: 16, left: 8, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
          <XAxis dataKey="t" type="number" scale="time" domain={['dataMin', 'dataMax']}
                 tickFormatter={(t: number) => formatDate(new Date(t).toISOString())} fontSize={12} />
          <YAxis tickFormatter={(v: number) => formatCompactPrice(v)} fontSize={12} width={70} domain={['auto', 'auto']} />
          <Tooltip
            labelFormatter={(t) => formatDate(new Date(Number(t)).toISOString())}
            formatter={(value, name) => [formatPrice(Number(value)), String(name)]}
          />
          <Legend />
          {series.map((s) => (
            <Line key={s.key} dataKey={s.key} name={s.name} type="stepAfter" stroke={s.color} strokeWidth={2}
                  dot={{ r: 3 }} connectNulls isAnimationActive={false} />
          ))}
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}
