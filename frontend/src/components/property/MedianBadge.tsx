import { TrendingDown, TrendingUp } from 'lucide-react'
import { vsMedianLabel } from '@/lib/format'
import { cn } from '@/lib/utils'

/** "X% poniżej/powyżej mediany dzielnicy" — zielony, gdy taniej niż mediana. */
export function MedianBadge({ pct, className }: { pct?: number | null; className?: string }) {
  const label = vsMedianLabel(pct)
  if (!label || pct === undefined || pct === null) return null
  const below = pct < 0
  return (
    <span className={cn('inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 text-xs font-medium',
      below ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-900/40 dark:text-emerald-300'
        : 'bg-amber-100 text-amber-800 dark:bg-amber-900/40 dark:text-amber-300', className)}>
      {below ? <TrendingDown className="size-3" /> : <TrendingUp className="size-3" />}
      {label}
    </span>
  )
}
