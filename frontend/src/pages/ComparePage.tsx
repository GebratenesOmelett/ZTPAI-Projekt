import { useQuery } from '@tanstack/react-query'
import { Check, Minus, X } from 'lucide-react'
import { Link } from 'react-router'
import { EmptyState, ErrorState, LoadingState } from '@/components/common/States'
import { MedianBadge } from '@/components/property/MedianBadge'
import { PhotoOrPlaceholder } from '@/components/search/PropertyCard'
import { Button } from '@/components/ui/button'
import { api } from '@/lib/api'
import { compareStore, MAX_COMPARE, useCompare } from '@/lib/compare'
import {
  formatArea, formatDate, formatFloor, formatPercent, formatPrice, formatPricePerM2, MARKET_LABELS, PROPERTY_TYPE_LABELS,
} from '@/lib/format'
import { queryKeys } from '@/lib/queries'
import type { PropertyDetails } from '@/lib/types'

/** Porównywarka do 4 nieruchomości obok siebie. */
export default function ComparePage() {
  const ids = useCompare()
  const query = useQuery({
    queryKey: queryKeys.compare(ids),
    queryFn: () => api<PropertyDetails[]>('/api/v1/properties/compare', { query: { ids: ids.join(',') } }),
    enabled: ids.length > 0,
  })

  if (ids.length === 0) {
    return <EmptyState title="Nic do porównania" description={`Dodaj do ${MAX_COMPARE} nieruchomości przyciskiem „Porównaj” na liście wyników.`}
                       action={<Button asChild><Link to="/">Przejdź do wyszukiwarki</Link></Button>} />
  }
  if (query.isLoading) return <LoadingState />
  if (query.isError) return <ErrorState error={query.error} onRetry={() => query.refetch()} />
  const items = query.data ?? []
  const allFeatures = Array.from(new Map(items.flatMap((p) => p.features).map((f) => [f.code, f.name])).entries())
  const minPpm2 = Math.min(...items.map((p) => p.pricePerM2 ?? Infinity))

  const rows: { label: string; render: (p: PropertyDetails) => React.ReactNode }[] = [
    { label: 'Cena', render: (p) => <strong>{formatPrice(p.price, p.transactionType)}</strong> },
    { label: 'Cena za m²', render: (p) => <span className={p.pricePerM2 === minPpm2 ? 'font-semibold text-emerald-700' : ''}>{formatPricePerM2(p.pricePerM2)}</span> },
    { label: 'Vs mediana dzielnicy', render: (p) => <MedianBadge pct={p.median?.vsMedianPercent} /> },
    { label: 'Typ', render: (p) => PROPERTY_TYPE_LABELS[p.propertyType] },
    { label: 'Rynek', render: (p) => (p.marketType ? MARKET_LABELS[p.marketType] : '—') },
    { label: 'Lokalizacja', render: (p) => `${p.city}${p.district ? `, ${p.district}` : ''}` },
    { label: 'Metraż', render: (p) => formatArea(p.area) },
    { label: 'Pokoje', render: (p) => p.rooms ?? '—' },
    { label: 'Piętro', render: (p) => formatFloor(p.floor, p.totalFloors) },
    { label: 'Rok budowy', render: (p) => p.yearBuilt ?? '—' },
    { label: 'Portale', render: (p) => p.sourceCount },
    { label: 'Obniżka', render: (p) => formatPercent(p.priceDropPercent) },
    { label: 'Na rynku od', render: (p) => formatDate(p.firstSeenAt) },
  ]

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Porównanie ({items.length}/{MAX_COMPARE})</h1>
        <Button variant="outline" size="sm" onClick={() => compareStore.clear()}>Wyczyść</Button>
      </div>
      <div className="overflow-x-auto rounded-xl border bg-background">
        <table className="w-full min-w-[640px] text-sm">
          <thead>
            <tr>
              <th className="w-40" />
              {items.map((p) => (
                <th key={p.id} className="p-3 text-left align-top font-normal">
                  <div className="relative mb-2 aspect-[4/3] overflow-hidden rounded-lg">
                    <PhotoOrPlaceholder src={p.photos[0]} alt={p.title} />
                    <Button size="icon-sm" variant="secondary" className="absolute top-1 right-1" aria-label="Usuń z porównania"
                            onClick={() => compareStore.remove(p.id)}><X /></Button>
                  </div>
                  <Link to={`/nieruchomosci/${p.id}`} className="line-clamp-2 font-medium hover:text-teal-700">{p.title}</Link>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.label} className="border-t">
                <td className="p-3 text-muted-foreground">{row.label}</td>
                {items.map((p) => <td key={p.id} className="p-3">{row.render(p)}</td>)}
              </tr>
            ))}
            {allFeatures.map(([code, name]) => (
              <tr key={code} className="border-t">
                <td className="p-3 text-muted-foreground">{name}</td>
                {items.map((p) => (
                  <td key={p.id} className="p-3">
                    {p.features.some((f) => f.code === code) ? <Check className="size-4 text-emerald-600" /> : <Minus className="size-4 text-muted-foreground" />}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
