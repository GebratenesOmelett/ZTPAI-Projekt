import { Building2, CalendarDays, Layers, MapPin, TrendingDown } from 'lucide-react'
import { Link } from 'react-router'
import { Badge } from '@/components/ui/badge'
import { CompareToggle } from '@/components/property/CompareToggle'
import { MedianBadge } from '@/components/property/MedianBadge'
import { formatArea, formatFloor, formatPrice, formatPricePerM2, plural, PROPERTY_TYPE_LABELS } from '@/lib/format'
import type { PropertySummary } from '@/lib/types'
import { cn } from '@/lib/utils'

export function PhotoOrPlaceholder({ src, alt, className }: { src?: string; alt: string; className?: string }) {
  return src ? (
    <img src={src} alt={alt} loading="lazy" className={cn('h-full w-full object-cover', className)}
         onError={(e) => { (e.currentTarget as HTMLImageElement).style.visibility = 'hidden' }} />
  ) : (
    <div className={cn('flex h-full w-full items-center justify-center bg-muted text-muted-foreground', className)}>
      <Building2 className="size-10" />
    </div>
  )
}

export function PropertyCard({ property, highlighted, onHover }: {
  property: PropertySummary
  highlighted?: boolean
  onHover?: (id: number | null) => void
}) {
  const p = property
  return (
    <article
      onMouseEnter={() => onHover?.(p.id)}
      onMouseLeave={() => onHover?.(null)}
      className={cn('group flex flex-col overflow-hidden rounded-xl border bg-background shadow-xs transition-shadow hover:shadow-md sm:flex-row',
        highlighted && 'ring-2 ring-teal-600', p.status === 'ARCHIVED' && 'opacity-75')}>
      <Link to={`/nieruchomosci/${p.id}`} className="relative h-44 shrink-0 sm:h-auto sm:w-60">
        <PhotoOrPlaceholder src={p.mainPhotoUrl} alt={p.title} />
        <div className="absolute top-2 left-2 flex flex-wrap gap-1">
          {p.priceDropped && (
            <Badge className="bg-red-600 text-white"><TrendingDown className="size-3" /> Obniżka{p.priceDropPercent ? ` ${Math.round(p.priceDropPercent)}%` : ''}</Badge>
          )}
          {p.status === 'ARCHIVED' && <Badge variant="secondary">Archiwalne</Badge>}
        </div>
      </Link>
      <div className="flex flex-1 flex-col gap-2 p-3">
        <div className="flex items-start justify-between gap-3">
          <Link to={`/nieruchomosci/${p.id}`} className="line-clamp-2 font-medium leading-snug hover:text-teal-700">{p.title}</Link>
          <div className="shrink-0 text-right">
            <div className="text-lg font-semibold tabular-nums">{formatPrice(p.price, p.transactionType)}</div>
            <div className="text-xs text-muted-foreground tabular-nums">{formatPricePerM2(p.pricePerM2)}</div>
          </div>
        </div>
        <div className="flex items-center gap-1 text-sm text-muted-foreground">
          <MapPin className="size-3.5" />
          {p.city}{p.district ? `, ${p.district}` : ''}{p.street ? `, ul. ${p.street}` : ''}
          {p.distanceMeters !== undefined && p.distanceMeters !== null && <span>· {(p.distanceMeters / 1000).toFixed(1)} km</span>}
        </div>
        <div className="flex flex-wrap gap-x-4 gap-y-1 text-sm">
          <span>{PROPERTY_TYPE_LABELS[p.propertyType]}</span>
          <span className="font-medium">{formatArea(p.area)}</span>
          {p.rooms !== undefined && p.rooms !== null && <span>{p.rooms} {plural(p.rooms, 'pokój', 'pokoje', 'pokoi')}</span>}
          {p.floor !== undefined && p.floor !== null && <span className="flex items-center gap-1"><Layers className="size-3.5" />{formatFloor(p.floor, p.totalFloors)}</span>}
          {p.yearBuilt && <span>{p.yearBuilt} r.</span>}
        </div>
        <div className="mt-auto flex flex-wrap items-center gap-2">
          <MedianBadge pct={p.vsMedianPercent} />
          <Badge variant="outline">{p.sourceCount} {plural(p.sourceCount, 'portal', 'portale', 'portali')}</Badge>
          <span className="flex items-center gap-1 text-xs text-muted-foreground">
            <CalendarDays className="size-3.5" />{p.daysOnMarket} {plural(p.daysOnMarket, 'dzień', 'dni', 'dni')} na rynku
          </span>
          <div className="ml-auto"><CompareToggle id={p.id} /></div>
        </div>
      </div>
    </article>
  )
}
