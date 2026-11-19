import { Badge } from '@/components/ui/badge'
import type { Criteria } from '@/lib/criteria'
import { formatCompactPrice, PROPERTY_TYPE_LABELS } from '@/lib/format'
import type { PropertyType, SearchFacets } from '@/lib/types'
import { cn } from '@/lib/utils'

function toggle<T>(list: T[] | undefined, value: T): T[] {
  const current = list ?? []
  return current.includes(value) ? current.filter((v) => v !== value) : [...current, value]
}

function FacetGroup({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-wrap items-center gap-1.5">
      <span className="mr-1 text-xs font-medium text-muted-foreground">{title}:</span>
      {children}
    </div>
  )
}

function Chip({ active, onClick, label, count }: { active: boolean; onClick: () => void; label: string; count: number }) {
  return (
    <button type="button" onClick={onClick}
            className={cn('inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-xs transition-colors',
              active ? 'border-teal-600 bg-teal-600 text-white' : 'bg-background hover:border-teal-600')}>
      {label}
      <span className={cn('tabular-nums', active ? 'text-teal-100' : 'text-muted-foreground')}>{count}</span>
    </button>
  )
}

/**
 * Fasety z liczebnościami zwracane razem z wynikami. Kliknięcie dodaje/usuwa filtr.
 * Liczby dla wymiaru nie uwzględniają filtra tego samego wymiaru (faceting rozłączny).
 */
export function FacetsBar({ facets, criteria, onChange }: {
  facets?: SearchFacets
  criteria: Criteria
  onChange: (patch: Partial<Criteria>) => void
}) {
  if (!facets) return null
  const maxBucket = Math.max(1, ...facets.priceHistogram.map((b) => b.count))
  return (
    <div className="space-y-2 rounded-xl border bg-background p-3">
      {facets.districts.length > 0 && (
        <FacetGroup title="Dzielnice">
          {facets.districts.slice(0, 12).map((f) => (
            <Chip key={f.key} label={f.label} count={f.count}
                  active={(criteria.districtIds ?? []).includes(Number(f.key))}
                  onClick={() => onChange({ districtIds: toggle(criteria.districtIds, Number(f.key)) })} />
          ))}
        </FacetGroup>
      )}
      <div className="flex flex-wrap gap-x-6 gap-y-2">
        {facets.rooms.length > 0 && (
          <FacetGroup title="Pokoje">
            {facets.rooms.map((f) => (
              <Chip key={f.key} label={f.label} count={f.count}
                    active={(criteria.rooms ?? []).includes(Number(f.key))}
                    onClick={() => onChange({ rooms: toggle(criteria.rooms, Number(f.key)) })} />
            ))}
          </FacetGroup>
        )}
        {facets.propertyTypes.length > 0 && (
          <FacetGroup title="Typ">
            {facets.propertyTypes.map((f) => (
              <Chip key={f.key} label={PROPERTY_TYPE_LABELS[f.key as PropertyType] ?? f.label} count={f.count}
                    active={(criteria.propertyTypes ?? []).includes(f.key as PropertyType)}
                    onClick={() => onChange({ propertyTypes: toggle(criteria.propertyTypes, f.key as PropertyType) })} />
            ))}
          </FacetGroup>
        )}
        {facets.sources.length > 0 && (
          <FacetGroup title="Portale">
            {facets.sources.map((f) => (
              <Chip key={f.key} label={f.label} count={f.count}
                    active={(criteria.includeSources ?? []).includes(f.key)}
                    onClick={() => onChange({ includeSources: toggle(criteria.includeSources, f.key) })} />
            ))}
          </FacetGroup>
        )}
      </div>
      {facets.priceHistogram.length > 1 && (
        <div>
          <div className="mb-1 flex items-center justify-between text-xs text-muted-foreground">
            <span>Rozkład cen (kliknij słupek, aby ustawić zakres)</span>
            {(criteria.priceMin !== undefined || criteria.priceMax !== undefined) && (
              <Badge variant="outline" className="cursor-pointer" onClick={() => onChange({ priceMin: undefined, priceMax: undefined })}>
                wyczyść zakres ceny
              </Badge>
            )}
          </div>
          <div className="flex h-14 items-end gap-0.5">
            {facets.priceHistogram.map((b) => {
              const active = criteria.priceMin === b.from && criteria.priceMax === b.to
              return (
                <button key={b.from} type="button" title={`${formatCompactPrice(b.from)} – ${formatCompactPrice(b.to)} zł: ${b.count}`}
                        onClick={() => onChange({ priceMin: b.from, priceMax: b.to })}
                        className={cn('flex-1 rounded-t transition-colors', active ? 'bg-teal-700' : 'bg-teal-500/60 hover:bg-teal-600')}
                        style={{ height: `${Math.max(4, (b.count / maxBucket) * 100)}%` }}
                        aria-label={`Cena od ${b.from} do ${b.to}: ${b.count} ofert`} />
              )
            })}
          </div>
          <div className="mt-1 flex justify-between text-[10px] text-muted-foreground">
            <span>{formatCompactPrice(facets.priceHistogram[0].from)} zł</span>
            <span>{formatCompactPrice(facets.priceHistogram[facets.priceHistogram.length - 1].to)} zł</span>
          </div>
        </div>
      )}
    </div>
  )
}
