import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, GitMerge, Scissors, Trash2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { Link, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { z } from 'zod'
import { FormField } from '@/components/common/FormField'
import { ErrorState, LoadingState } from '@/components/common/States'
import { CheckList } from '@/components/search/fields'
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription,
  AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { api, ApiError, errorMessage } from '@/lib/api'
import { formatPrice, PROPERTY_TYPE_LABELS, TRANSACTION_LABELS } from '@/lib/format'
import { applyApiErrors } from '@/lib/forms'
import { useCities, useDistricts, useFeatures } from '@/lib/queries'
import type { PropertyDetails, SplitResponse } from '@/lib/types'

const optionalNumber = z.preprocess((v) => (v === '' || v === null || v === undefined || Number.isNaN(v) ? undefined : Number(v)), z.number().optional())

const schema = z.object({
  propertyType: z.enum(['APARTMENT', 'HOUSE', 'PLOT', 'COMMERCIAL', 'ROOM']),
  transactionType: z.enum(['SALE', 'RENT']),
  marketType: z.enum(['PRIMARY', 'SECONDARY', 'NONE']),
  cityId: z.coerce.number().int().positive(),
  districtId: z.coerce.number().int().optional(),
  street: z.string().max(200).optional(),
  lat: optionalNumber,
  lon: optionalNumber,
  area: z.coerce.number().min(5, 'Min. 5 m²').max(100000),
  rooms: optionalNumber.pipe(z.number().int().min(1).max(20).optional()),
  floor: optionalNumber.pipe(z.number().int().min(-1).max(100).optional()),
  totalFloors: optionalNumber.pipe(z.number().int().min(0).max(100).optional()),
  yearBuilt: optionalNumber.pipe(z.number().int().min(1800).max(2100).optional()),
  features: z.array(z.string()),
})
type Values = z.input<typeof schema>
type Parsed = z.output<typeof schema>
const FIELDS = ['propertyType', 'transactionType', 'marketType', 'cityId', 'districtId', 'street', 'lat', 'lon', 'area', 'rooms', 'floor', 'totalFloors', 'yearBuilt', 'features']

function toForm(p: PropertyDetails): Values {
  return {
    propertyType: p.propertyType, transactionType: p.transactionType, marketType: p.marketType ?? 'NONE',
    cityId: p.cityId, districtId: p.districtId, street: p.street ?? '', lat: p.lat, lon: p.lon, area: p.area,
    rooms: p.rooms, floor: p.floor, totalFloors: p.totalFloors, yearBuilt: p.yearBuilt,
    features: p.features.map((f) => f.code),
  }
}

/**
 * Edycja nieruchomości z optimistic locking: zapis wysyła wersję z odczytu. Gdy w międzyczasie
 * nieruchomość zmienił inny admin lub import, API zwraca 409 — pokazujemy dialog z opcją przeładowania.
 */
export default function AdminPropertyEditPage() {
  const { id } = useParams()
  const propertyId = Number(id)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const cities = useCities()
  const districts = useDistricts()
  const features = useFeatures()
  const [conflict, setConflict] = useState<string | null>(null)
  const [mergeSourceId, setMergeSourceId] = useState('')
  const [splitIds, setSplitIds] = useState<number[]>([])
  const key = ['admin', 'property', propertyId]
  const query = useQuery({ queryKey: key, queryFn: () => api<PropertyDetails>(`/api/v1/admin/properties/${propertyId}`) })
  const form = useForm<Values, unknown, Parsed>({ resolver: zodResolver(schema) })
  const [version, setVersion] = useState<number | null>(null)

  useEffect(() => {
    if (query.data) {
      form.reset(toForm(query.data))
      setVersion(query.data.version)
      setSplitIds([])
    }
  }, [query.data, form])

  const reload = async () => {
    setConflict(null)
    await query.refetch()
    toast.info('Wczytano aktualną wersję danych')
  }

  const handleError = (e: unknown) => {
    if (e instanceof ApiError && e.status === 409) {
      setConflict(e.message)
    } else if (!applyApiErrors(e, form.setError, FIELDS, { 'attributes.area': 'area', 'attributes.rooms': 'rooms' })) {
      toast.error(errorMessage(e))
    }
  }

  const save = useMutation({
    mutationFn: (v: Parsed) => api<PropertyDetails>(`/api/v1/admin/properties/${propertyId}`, {
      method: 'PUT',
      body: { version, attributes: { ...v, marketType: v.marketType === 'NONE' ? undefined : v.marketType, street: v.street || undefined } },
    }),
    onSuccess: (updated) => {
      queryClient.setQueryData(key, updated)
      queryClient.invalidateQueries({ queryKey: ['property', propertyId] })
      toast.success(`Zapisano (wersja ${updated.version})`)
    },
    onError: handleError,
  })
  const merge = useMutation({
    mutationFn: async (sourcePropertyId: number) => {
      const source = await api<PropertyDetails>(`/api/v1/admin/properties/${sourcePropertyId}`)
      return api<PropertyDetails>(`/api/v1/admin/properties/${propertyId}/merge`, {
        method: 'POST', body: { sourcePropertyId, targetVersion: version, sourceVersion: source.version },
      })
    },
    onSuccess: (merged) => {
      queryClient.setQueryData(key, merged)
      setMergeSourceId('')
      toast.success(`Scalono — nieruchomość ma teraz ${merged.offers.length} ofert`)
    },
    onError: handleError,
  })
  const split = useMutation({
    mutationFn: () => api<SplitResponse>(`/api/v1/admin/properties/${propertyId}/split`, { method: 'POST', body: { offerIds: splitIds, version } }),
    onSuccess: (res) => {
      queryClient.setQueryData(key, res.original)
      toast.success(`Utworzono nową nieruchomość #${res.created.id}`, {
        action: { label: 'Otwórz', onClick: () => navigate(`/admin/nieruchomosci/${res.created.id}`) },
      })
    },
    onError: handleError,
  })
  const remove = useMutation({
    mutationFn: () => api<void>(`/api/v1/admin/properties/${propertyId}`, { method: 'DELETE' }),
    onSuccess: () => {
      toast.success('Usunięto nieruchomość')
      navigate('/admin/nieruchomosci')
    },
    onError: handleError,
  })

  if (query.isLoading) return <LoadingState />
  if (query.isError) return <ErrorState error={query.error} onRetry={() => query.refetch()} />
  const p = query.data!
  const cityId = Number(form.watch('cityId'))
  const cityDistricts = (districts.data ?? []).filter((d) => d.cityId === cityId)
  const { errors } = form.formState

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <Button variant="ghost" size="sm" asChild><Link to="/admin/nieruchomosci"><ArrowLeft /> Lista</Link></Button>
        <h2 className="text-lg font-semibold">#{p.id} {p.title}</h2>
        <Badge variant="outline">wersja {version}</Badge>
        <Button variant="outline" size="sm" className="ml-auto" asChild><Link to={`/nieruchomosci/${p.id}`}>Podgląd</Link></Button>
        <Button variant="destructive" size="sm" onClick={() => window.confirm('Usunąć nieruchomość wraz z ofertami?') && remove.mutate()}>
          <Trash2 /> Usuń
        </Button>
      </div>

      <div className="grid gap-4 xl:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
        <Card>
          <CardHeader><CardTitle>Atrybuty</CardTitle></CardHeader>
          <CardContent>
            <form className="grid gap-3 sm:grid-cols-2" onSubmit={form.handleSubmit((v) => save.mutate(v))} noValidate>
              <FormField id="pe-type" label="Typ">
                <Controller control={form.control} name="propertyType" render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger id="pe-type" className="w-full"><SelectValue /></SelectTrigger>
                    <SelectContent>{Object.entries(PROPERTY_TYPE_LABELS).map(([k, l]) => <SelectItem key={k} value={k}>{l}</SelectItem>)}</SelectContent>
                  </Select>
                )} />
              </FormField>
              <FormField id="pe-tx" label="Transakcja">
                <Controller control={form.control} name="transactionType" render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger id="pe-tx" className="w-full"><SelectValue /></SelectTrigger>
                    <SelectContent>{Object.entries(TRANSACTION_LABELS).map(([k, l]) => <SelectItem key={k} value={k}>{l}</SelectItem>)}</SelectContent>
                  </Select>
                )} />
              </FormField>
              <FormField id="pe-market" label="Rynek">
                <Controller control={form.control} name="marketType" render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger id="pe-market" className="w-full"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="NONE">Brak danych</SelectItem>
                      <SelectItem value="PRIMARY">Pierwotny</SelectItem>
                      <SelectItem value="SECONDARY">Wtórny</SelectItem>
                    </SelectContent>
                  </Select>
                )} />
              </FormField>
              <FormField id="pe-city" label="Miasto" error={errors.cityId?.message}>
                <Controller control={form.control} name="cityId" render={({ field }) => (
                  <Select value={String(field.value ?? '')} onValueChange={(v) => { field.onChange(Number(v)); form.setValue('districtId', undefined) }}>
                    <SelectTrigger id="pe-city" className="w-full"><SelectValue /></SelectTrigger>
                    <SelectContent>{cities.data?.map((c) => <SelectItem key={c.id} value={String(c.id)}>{c.name}</SelectItem>)}</SelectContent>
                  </Select>
                )} />
              </FormField>
              <FormField id="pe-district" label="Dzielnica" error={errors.districtId?.message}>
                <Controller control={form.control} name="districtId" render={({ field }) => (
                  <Select value={field.value ? String(field.value) : ''} onValueChange={(v) => field.onChange(Number(v))}>
                    <SelectTrigger id="pe-district" className="w-full"><SelectValue placeholder="Wybierz" /></SelectTrigger>
                    <SelectContent>{cityDistricts.map((d) => <SelectItem key={d.id} value={String(d.id)}>{d.name}</SelectItem>)}</SelectContent>
                  </Select>
                )} />
              </FormField>
              <FormField id="pe-street" label="Ulica" error={errors.street?.message}><Input id="pe-street" {...form.register('street')} /></FormField>
              <FormField id="pe-area" label="Metraż (m²)" error={errors.area?.message}><Input id="pe-area" type="number" step="0.01" {...form.register('area')} /></FormField>
              <FormField id="pe-rooms" label="Pokoje" error={errors.rooms?.message}><Input id="pe-rooms" type="number" {...form.register('rooms')} /></FormField>
              <FormField id="pe-floor" label="Piętro (0 = parter)" error={errors.floor?.message}><Input id="pe-floor" type="number" {...form.register('floor')} /></FormField>
              <FormField id="pe-total" label="Liczba pięter" error={errors.totalFloors?.message}><Input id="pe-total" type="number" {...form.register('totalFloors')} /></FormField>
              <FormField id="pe-year" label="Rok budowy" error={errors.yearBuilt?.message}><Input id="pe-year" type="number" {...form.register('yearBuilt')} /></FormField>
              <div className="grid grid-cols-2 gap-2">
                <FormField id="pe-lat" label="Szer. geogr." error={errors.lat?.message}><Input id="pe-lat" type="number" step="any" {...form.register('lat')} /></FormField>
                <FormField id="pe-lon" label="Dł. geogr." error={errors.lon?.message}><Input id="pe-lon" type="number" step="any" {...form.register('lon')} /></FormField>
              </div>
              <div className="sm:col-span-2">
                <p className="mb-1 text-sm font-medium">Cechy</p>
                <Controller control={form.control} name="features" render={({ field }) => (
                  <div className="grid grid-cols-2 gap-x-4 sm:grid-cols-3">
                    <CheckList options={(features.data ?? []).map((f) => ({ value: f.code, label: f.name }))}
                               selected={field.value ?? []} onChange={field.onChange} />
                  </div>
                )} />
              </div>
              <div className="sm:col-span-2">
                <Button type="submit" disabled={save.isPending}>{save.isPending ? 'Zapisywanie…' : 'Zapisz zmiany'}</Button>
              </div>
            </form>
          </CardContent>
        </Card>

        <div className="space-y-4">
          <Card>
            <CardHeader><CardTitle>Oferty ({p.offers.length}) — rozdzielanie</CardTitle></CardHeader>
            <CardContent className="space-y-2">
              {p.offers.map((o) => (
                <label key={o.id} className="flex items-start gap-2 rounded-lg border p-2 text-sm">
                  <Checkbox checked={splitIds.includes(o.id)} disabled={p.offers.length < 2}
                            onCheckedChange={(c) => setSplitIds((ids) => (c === true ? [...ids, o.id] : ids.filter((x) => x !== o.id)))} />
                  <div className="flex-1">
                    <div className="font-medium">{o.sourceName} · {formatPrice(o.price, p.transactionType)} {!o.active && <Badge variant="secondary">nieaktywna</Badge>}</div>
                    <div className="text-xs text-muted-foreground">#{o.id} · {o.externalId} · {o.title}</div>
                  </div>
                </label>
              ))}
              <Button variant="outline" disabled={splitIds.length === 0 || splitIds.length === p.offers.length || split.isPending} onClick={() => split.mutate()}>
                <Scissors /> Wydziel zaznaczone do nowej nieruchomości
              </Button>
              <p className="text-xs text-muted-foreground">Przy nieruchomości musi pozostać co najmniej jedna oferta.</p>
            </CardContent>
          </Card>
          <Card>
            <CardHeader><CardTitle>Scalanie duplikatów</CardTitle></CardHeader>
            <CardContent className="space-y-2">
              <p className="text-sm text-muted-foreground">Podaj ID nieruchomości, która jest duplikatem tej. Jej oferty i ulubione zostaną przeniesione tutaj, a ona usunięta.</p>
              <form className="flex gap-2" onSubmit={(e) => {
                e.preventDefault()
                const sid = Number(mergeSourceId)
                if (Number.isFinite(sid) && sid > 0) merge.mutate(sid)
              }}>
                <Input type="number" placeholder="ID duplikatu" value={mergeSourceId} onChange={(e) => setMergeSourceId(e.target.value)} aria-label="ID duplikatu" />
                <Button type="submit" disabled={!mergeSourceId || merge.isPending}><GitMerge /> Scal</Button>
              </form>
            </CardContent>
          </Card>
        </div>
      </div>

      <AlertDialog open={!!conflict} onOpenChange={(open) => !open && setConflict(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Konflikt wersji (409)</AlertDialogTitle>
            <AlertDialogDescription>
              {conflict} Twoje niezapisane zmiany zostaną zastąpione aktualnymi danymi z serwera.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Zostań przy moich zmianach</AlertDialogCancel>
            <AlertDialogAction onClick={reload}>Wczytaj aktualne dane</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
