import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, CalendarDays, Check, MapPin, TrendingDown } from 'lucide-react'
import { Link, useNavigate, useParams } from 'react-router'
import { ErrorState, LoadingState } from '@/components/common/States'
import { CompareToggle } from '@/components/property/CompareToggle'
import { FavoriteButton } from '@/components/property/FavoriteButton'
import { MedianBadge } from '@/components/property/MedianBadge'
import { MiniMap } from '@/components/property/MiniMap'
import { OffersTable } from '@/components/property/OffersTable'
import { PhotoGallery } from '@/components/property/PhotoGallery'
import { PriceHistoryChart } from '@/components/property/PriceHistoryChart'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { ApiError } from '@/lib/api'
import { useAuth } from '@/lib/auth'
import {
  formatArea, formatDate, formatFloor, formatPercent, formatPrice, formatPricePerM2,
  MARKET_LABELS, plural, PROPERTY_TYPE_LABELS, TRANSACTION_LABELS,
} from '@/lib/format'
import { fetchProperty, queryKeys } from '@/lib/queries'

function Fact({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="rounded-lg bg-muted/60 p-3">
      <div className="text-xs text-muted-foreground">{label}</div>
      <div className="mt-0.5 font-medium">{value}</div>
    </div>
  )
}

export default function PropertyPage() {
  const { id } = useParams()
  const propertyId = Number(id)
  const navigate = useNavigate()
  const { isAdmin } = useAuth()
  const query = useQuery({ queryKey: queryKeys.property(propertyId), queryFn: () => fetchProperty(propertyId), enabled: Number.isFinite(propertyId) })

  if (query.isLoading) return <LoadingState />
  if (query.isError) {
    const notFound = query.error instanceof ApiError && query.error.status === 404
    return notFound ? (
      <Alert><AlertTitle>Nie znaleziono nieruchomości</AlertTitle><AlertDescription>Mogła zostać scalona lub usunięta. <Link className="underline" to="/">Wróć do wyszukiwarki</Link></AlertDescription></Alert>
    ) : <ErrorState error={query.error} onRetry={() => query.refetch()} />
  }
  const p = query.data!

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <Button variant="ghost" size="sm" onClick={() => navigate(-1)}><ArrowLeft /> Wróć</Button>
        {isAdmin && <Button variant="outline" size="sm" asChild className="ml-auto"><Link to={`/admin/nieruchomosci/${p.id}`}>Edytuj (admin)</Link></Button>}
      </div>

      {p.status === 'ARCHIVED' && (
        <Alert>
          <AlertTitle>Ogłoszenie archiwalne</AlertTitle>
          <AlertDescription>Nieruchomość nie ma już aktywnych ofert na portalach. Pokazujemy ostatnie znane dane.</AlertDescription>
        </Alert>
      )}

      <div className="grid gap-6 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
        <div className="space-y-4">
          <PhotoGallery photos={p.photos} title={p.title} />
          <Card>
            <CardHeader><CardTitle>Opis</CardTitle></CardHeader>
            <CardContent className="space-y-4">
              <p className="whitespace-pre-line text-sm leading-relaxed">{p.description ?? 'Brak opisu.'}</p>
              {p.features.length > 0 && (
                <div className="flex flex-wrap gap-2">
                  {p.features.map((f) => <Badge key={f.code} variant="secondary"><Check className="size-3" />{f.name}</Badge>)}
                </div>
              )}
            </CardContent>
          </Card>
          <Card>
            <CardHeader><CardTitle>Oferty z portali ({p.offers.length})</CardTitle></CardHeader>
            <CardContent><OffersTable offers={p.offers} transaction={p.transactionType} /></CardContent>
          </Card>
          <Card>
            <CardHeader><CardTitle>Historia cen</CardTitle></CardHeader>
            <CardContent><PriceHistoryChart offers={p.offers} /></CardContent>
          </Card>
        </div>

        <div className="space-y-4">
          <Card>
            <CardContent className="space-y-4">
              <div className="flex flex-wrap gap-2">
                <Badge variant="outline">{TRANSACTION_LABELS[p.transactionType]}</Badge>
                <Badge variant="outline">{PROPERTY_TYPE_LABELS[p.propertyType]}</Badge>
                {p.marketType && <Badge variant="outline">Rynek {MARKET_LABELS[p.marketType].toLowerCase()}</Badge>}
                {p.priceDropped && <Badge className="bg-red-600 text-white"><TrendingDown className="size-3" />Obniżka ceny</Badge>}
              </div>
              <h1 className="text-xl font-semibold leading-tight">{p.title}</h1>
              <p className="flex items-center gap-1 text-sm text-muted-foreground">
                <MapPin className="size-4" />{p.city}{p.district ? `, ${p.district}` : ''}{p.street ? `, ul. ${p.street}` : ''}
              </p>
              <div>
                <div className="text-3xl font-bold tabular-nums">{formatPrice(p.price, p.transactionType)}</div>
                <div className="text-sm text-muted-foreground tabular-nums">{formatPricePerM2(p.pricePerM2)}</div>
              </div>
              {p.median && (
                <div className="rounded-lg border p-3 text-sm">
                  <MedianBadge pct={p.median.vsMedianPercent} />
                  <p className="mt-2 text-muted-foreground">
                    Mediana ceny za m² w dzielnicy {p.district}: <strong className="text-foreground">{formatPricePerM2(p.median.districtMedianPricePerM2)}</strong>
                    {' '}(na podstawie {p.median.sampleSize} {plural(p.median.sampleSize, 'oferty', 'ofert', 'ofert')}).
                  </p>
                </div>
              )}
              <div className="flex flex-wrap gap-2">
                <FavoriteButton propertyId={p.id} favoriteId={p.favoriteId} />
                <CompareToggle id={p.id} size="default" />
              </div>
            </CardContent>
          </Card>
          <div className="grid grid-cols-2 gap-2">
            <Fact label="Powierzchnia" value={formatArea(p.area)} />
            <Fact label="Pokoje" value={p.rooms ?? '—'} />
            <Fact label="Piętro" value={formatFloor(p.floor, p.totalFloors)} />
            <Fact label="Rok budowy" value={p.yearBuilt ?? '—'} />
            <Fact label="Na portalach" value={`${p.sourceCount} ${plural(p.sourceCount, 'portal', 'portale', 'portali')}`} />
            <Fact label="Obniżka od najwyższej ceny" value={formatPercent(p.priceDropPercent)} />
            <Fact label="Pierwsze wystąpienie" value={formatDate(p.firstSeenAt)} />
            <Fact label="Dni na rynku" value={<span className="flex items-center gap-1"><CalendarDays className="size-4" />{p.daysOnMarket}</span>} />
          </div>
          <MiniMap lat={p.lat} lon={p.lon} className="h-64" />
        </div>
      </div>
    </div>
  )
}
