import { RotateCcw, SlidersHorizontal } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import type { Criteria } from '@/lib/criteria'
import { MARKET_LABELS, PROPERTY_TYPE_LABELS, TRANSACTION_LABELS } from '@/lib/format'
import { useCities, useDistricts, useFeatures, useSourceOptions } from '@/lib/queries'
import type { MarketType, PropertyType, TransactionType } from '@/lib/types'
import { CheckboxField, CheckList, FilterSection, NumberField, RangeField } from './fields'

const ANY = 'ANY'
const ROOM_OPTIONS = ['1', '2', '3', '4', '5']

/**
 * Panel wszystkich filtrów wyszukiwarki (zwijane sekcje). Zmiany trafiają do lokalnego szkicu
 * i są stosowane przyciskiem "Pokaż wyniki" — mniej zapytań i przewidywalne działanie.
 */
export function FiltersPanel({ criteria, onApply, onClose }: {
  criteria: Criteria
  onApply: (criteria: Criteria) => void
  onClose?: () => void
}) {
  // Komponent jest montowany z key = kryteria, więc szkic resetuje się po zmianie URL.
  const [draft, setDraft] = useState<Criteria>(criteria)

  const cities = useCities()
  const districts = useDistricts()
  const features = useFeatures()
  const sources = useSourceOptions()

  const set = <K extends keyof Criteria>(key: K, value: Criteria[K]) => setDraft((d) => ({ ...d, [key]: value }))

  // Wybrane miasta; stary link/zapisane wyszukiwanie z samymi dzielnicami pokazuje też ich miasta,
  // żeby wybrane dzielnice były widoczne (wynik wyszukiwania jest ten sam).
  const selectedCityIds = useMemo(() => {
    const ids = new Set(draft.cityIds ?? [])
    for (const id of draft.districtIds ?? []) {
      const district = districts.data?.find((d) => d.id === id)
      if (district) ids.add(district.cityId)
    }
    return [...ids]
  }, [draft.cityIds, draft.districtIds, districts.data])
  // dzielnice są dostępne dopiero po wyborze miasta — tylko dzielnice wybranych miast
  const visibleDistricts = useMemo(
    () => (districts.data ?? [])
      .filter((d) => selectedCityIds.includes(d.cityId))
      .sort((a, b) => a.name.localeCompare(b.name, 'pl')),
    [districts.data, selectedCityIds],
  )
  const sortedCities = useMemo(
    () => [...(cities.data ?? [])].sort((a, b) => a.name.localeCompare(b.name, 'pl')),
    [cities.data],
  )
  const cityName = (id: number) => cities.data?.find((c) => c.id === id)?.name ?? ''

  const apply = () => {
    onApply(draft)
    onClose?.()
  }
  const reset = () => {
    const cleared: Criteria = { q: criteria.q }
    setDraft(cleared)
    onApply(cleared)
    onClose?.()
  }

  const countOf = (...keys: (keyof Criteria)[]) =>
    keys.filter((k) => {
      const v = draft[k]
      return v !== undefined && v !== false && !(Array.isArray(v) && v.length === 0)
    }).length

  return (
    <div className="flex h-full flex-col">
      <div className="flex items-center gap-2 pb-2">
        <SlidersHorizontal className="size-4" />
        <h2 className="font-semibold">Filtry</h2>
      </div>
      <div className="flex-1 overflow-y-auto pr-1">
        <FilterSection title="Rodzaj oferty" defaultOpen badge={countOf('transactionType', 'propertyTypes', 'marketType')}>
          <div className="space-y-1">
            <Label className="text-xs text-muted-foreground">Transakcja</Label>
            <Select value={draft.transactionType ?? ANY} onValueChange={(v) => set('transactionType', v === ANY ? undefined : (v as TransactionType))}>
              <SelectTrigger className="h-8 w-full"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value={ANY}>Dowolna</SelectItem>
                {Object.entries(TRANSACTION_LABELS).map(([k, label]) => <SelectItem key={k} value={k}>{label}</SelectItem>)}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-1">
            <Label className="text-xs text-muted-foreground">Typ nieruchomości</Label>
            <CheckList
              options={Object.entries(PROPERTY_TYPE_LABELS).map(([value, label]) => ({ value: value as PropertyType, label }))}
              selected={draft.propertyTypes ?? []}
              onChange={(v) => set('propertyTypes', v)}
            />
          </div>
          <div className="space-y-1">
            <Label className="text-xs text-muted-foreground">Rynek</Label>
            <Select value={draft.marketType ?? ANY} onValueChange={(v) => set('marketType', v === ANY ? undefined : (v as MarketType))}>
              <SelectTrigger className="h-8 w-full"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value={ANY}>Dowolny</SelectItem>
                {Object.entries(MARKET_LABELS).map(([k, label]) => <SelectItem key={k} value={k}>{label}</SelectItem>)}
              </SelectContent>
            </Select>
          </div>
        </FilterSection>

        <FilterSection title="Lokalizacja" defaultOpen badge={countOf('cityIds', 'districtIds', 'street')}>
          <div className="space-y-1">
            <Label className="text-xs text-muted-foreground">Miasta</Label>
            <CheckList
              maxHeight
              options={sortedCities.map((c) => ({ value: c.id, label: c.name }))}
              selected={selectedCityIds}
              onChange={(v) => setDraft((d) => {
                // dzielnice odznaczonych miast (a przy braku miasta wszystkie) są usuwane z filtra
                const districtIds = (d.districtIds ?? []).filter((id) => {
                  const district = districts.data?.find((x) => x.id === id)
                  return district !== undefined && v.includes(district.cityId)
                })
                return { ...d, cityIds: v.length ? v : undefined, districtIds: districtIds.length ? districtIds : undefined }
              })}
            />
          </div>
          <div className="space-y-1">
            <Label className="text-xs text-muted-foreground">Dzielnice</Label>
            {selectedCityIds.length ? (
              <CheckList
                maxHeight
                options={visibleDistricts.map((d) => ({
                  value: d.id,
                  label: (selectedCityIds.length === 1 ? d.name : `${d.name} (${cityName(d.cityId)})`),
                }))}
                selected={draft.districtIds ?? []}
                onChange={(v) => set('districtIds', v.length ? v : undefined)}
              />
            ) : (
              <p className="text-xs text-muted-foreground">Najpierw wybierz miasto.</p>
            )}
          </div>
          <div className="space-y-1">
            <Label htmlFor="street" className="text-xs text-muted-foreground">Ulica</Label>
            <Input id="street" className="h-8" placeholder="np. Puławska" value={draft.street ?? ''}
                   onChange={(e) => set('street', e.target.value || undefined)} />
          </div>
        </FilterSection>

        <FilterSection title="Cena i metraż" defaultOpen badge={countOf('priceMin', 'priceMax', 'pricePerM2Min', 'pricePerM2Max', 'areaMin', 'areaMax')}>
          <RangeField label="Cena" unit="zł" min={draft.priceMin} max={draft.priceMax} step={10000}
                      onMin={(v) => set('priceMin', v)} onMax={(v) => set('priceMax', v)} />
          <RangeField label="Cena za m²" unit="zł" min={draft.pricePerM2Min} max={draft.pricePerM2Max} step={500}
                      onMin={(v) => set('pricePerM2Min', v)} onMax={(v) => set('pricePerM2Max', v)} />
          <RangeField label="Metraż" unit="m²" min={draft.areaMin} max={draft.areaMax}
                      onMin={(v) => set('areaMin', v)} onMax={(v) => set('areaMax', v)} />
        </FilterSection>

        <FilterSection title="Pokoje, piętro, budynek" badge={countOf('rooms', 'floorMin', 'floorMax', 'notGroundFloor', 'notLastFloor', 'totalFloorsMin', 'totalFloorsMax', 'yearBuiltMin', 'yearBuiltMax')}>
          <div className="space-y-1">
            <Label className="text-xs text-muted-foreground">Liczba pokoi</Label>
            <ToggleGroup type="multiple" variant="outline" size="sm" className="w-full"
                         value={(draft.rooms ?? []).map(String)}
                         onValueChange={(v) => set('rooms', v.map(Number))}>
              {ROOM_OPTIONS.map((r) => <ToggleGroupItem key={r} value={r} className="flex-1">{r === '5' ? '5+' : r}</ToggleGroupItem>)}
            </ToggleGroup>
          </div>
          <RangeField label="Piętro" min={draft.floorMin} max={draft.floorMax} onMin={(v) => set('floorMin', v)} onMax={(v) => set('floorMax', v)} />
          <CheckboxField label="Nie parter" checked={draft.notGroundFloor} onChange={(v) => set('notGroundFloor', v || undefined)} />
          <CheckboxField label="Nie ostatnie piętro" checked={draft.notLastFloor} onChange={(v) => set('notLastFloor', v || undefined)} />
          <RangeField label="Liczba pięter budynku" min={draft.totalFloorsMin} max={draft.totalFloorsMax}
                      onMin={(v) => set('totalFloorsMin', v)} onMax={(v) => set('totalFloorsMax', v)} />
          <RangeField label="Rok budowy" min={draft.yearBuiltMin} max={draft.yearBuiltMax}
                      onMin={(v) => set('yearBuiltMin', v)} onMax={(v) => set('yearBuiltMax', v)} />
        </FilterSection>

        <FilterSection title="Cechy" badge={countOf('features')}>
          <ToggleGroup type="single" variant="outline" size="sm" className="w-full"
                       value={draft.featureMode ?? 'ALL'} onValueChange={(v) => v && set('featureMode', v as 'ALL' | 'ANY')}>
            <ToggleGroupItem value="ALL" className="flex-1">Wszystkie</ToggleGroupItem>
            <ToggleGroupItem value="ANY" className="flex-1">Dowolna</ToggleGroupItem>
          </ToggleGroup>
          <CheckList
            options={(features.data ?? []).map((f) => ({ value: f.code, label: f.name }))}
            selected={draft.features ?? []}
            onChange={(v) => set('features', v)}
          />
        </FilterSection>

        <FilterSection title="Portale i sprzedający" badge={countOf('includeSources', 'excludeSources', 'sellerType')}>
          <div className="space-y-1">
            <Label className="text-xs text-muted-foreground">Tylko z portali</Label>
            <CheckList options={(sources.data ?? []).map((s) => ({ value: s.code, label: s.name }))}
                       selected={draft.includeSources ?? []} onChange={(v) => set('includeSources', v)} />
          </div>
          <div className="space-y-1">
            <Label className="text-xs text-muted-foreground">Pomiń portale</Label>
            <CheckList options={(sources.data ?? []).map((s) => ({ value: s.code, label: s.name }))}
                       selected={draft.excludeSources ?? []} onChange={(v) => set('excludeSources', v)} />
          </div>
          <div className="space-y-1">
            <Label className="text-xs text-muted-foreground">Sprzedający</Label>
            <Select value={draft.sellerType ?? ANY} onValueChange={(v) => set('sellerType', v === ANY ? undefined : (v as 'PRIVATE' | 'AGENCY'))}>
              <SelectTrigger className="h-8 w-full"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value={ANY}>Wszyscy</SelectItem>
                <SelectItem value="PRIVATE">Tylko prywatne</SelectItem>
                <SelectItem value="AGENCY">Tylko biura</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <CheckboxField label="Tylko obecne na ≥ 2 portalach" checked={draft.multiSource} onChange={(v) => set('multiSource', v || undefined)} />
        </FilterSection>

        <FilterSection title="Nowość i obniżki" badge={countOf('addedWithinDays', 'priceDropWithinDays', 'minPriceDropPercent', 'daysOnMarketMin', 'daysOnMarketMax')}>
          <div className="grid grid-cols-2 gap-2">
            <div className="space-y-1">
              <Label className="text-xs text-muted-foreground">Dodane (dni)</Label>
              <NumberField value={draft.addedWithinDays} min={1} onChange={(v) => set('addedWithinDays', v)} placeholder="np. 7" />
            </div>
            <div className="space-y-1">
              <Label className="text-xs text-muted-foreground">Obniżka (dni)</Label>
              <NumberField value={draft.priceDropWithinDays} min={1} onChange={(v) => set('priceDropWithinDays', v)} placeholder="np. 30" />
            </div>
          </div>
          <div className="space-y-1">
            <Label className="text-xs text-muted-foreground">Minimalna obniżka (%)</Label>
            <NumberField value={draft.minPriceDropPercent} min={0} onChange={(v) => set('minPriceDropPercent', v)} placeholder="np. 5" />
          </div>
          <RangeField label="Dni na rynku" min={draft.daysOnMarketMin} max={draft.daysOnMarketMax}
                      onMin={(v) => set('daysOnMarketMin', v)} onMax={(v) => set('daysOnMarketMax', v)} />
        </FilterSection>

        <FilterSection title="Obszar i inne" badge={countOf('radiusKm', 'polygon', 'bbox', 'includeArchived')}>
          {draft.lat !== undefined && draft.lon !== undefined ? (
            <div className="space-y-1">
              <Label className="text-xs text-muted-foreground">Promień od punktu (km, max 50)</Label>
              <NumberField value={draft.radiusKm} min={0.1} step={0.5} onChange={(v) => set('radiusKm', v)} />
              <Button variant="link" size="sm" className="px-0" onClick={() => setDraft((d) => ({ ...d, lat: undefined, lon: undefined, radiusKm: undefined }))}>
                Usuń punkt
              </Button>
            </div>
          ) : (
            <p className="text-xs text-muted-foreground">Punkt z promieniem, poligon i obszar ustawisz na mapie.</p>
          )}
          {(draft.polygon || draft.bbox) && (
            <Button variant="outline" size="sm" onClick={() => setDraft((d) => ({ ...d, polygon: undefined, bbox: undefined }))}>
              Usuń obszar z mapy
            </Button>
          )}
          <CheckboxField label="Pokaż również archiwalne" checked={draft.includeArchived} onChange={(v) => set('includeArchived', v || undefined)} />
        </FilterSection>
      </div>
      <div className="flex gap-2 border-t pt-3">
        <Button className="flex-1" onClick={apply}>Pokaż wyniki</Button>
        <Button variant="outline" onClick={reset} aria-label="Wyczyść filtry"><RotateCcw /></Button>
      </div>
    </div>
  )
}
