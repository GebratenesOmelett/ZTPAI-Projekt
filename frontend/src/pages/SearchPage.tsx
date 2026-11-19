import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Columns2, List, Map as MapIcon, Search, SlidersHorizontal, X } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useLocation, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { EmptyState, ErrorState, LoadingState } from '@/components/common/States'
import { PaginationBar } from '@/components/common/PaginationBar'
import { FacetsBar } from '@/components/search/FacetsBar'
import { FiltersPanel } from '@/components/search/FiltersPanel'
import { PropertyCard } from '@/components/search/PropertyCard'
import { SaveSearchDialog, type EditingSavedSearch } from '@/components/search/SaveSearchDialog'
import { SearchMap } from '@/components/search/SearchMap'
import { SortBar } from '@/components/search/SortBar'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from '@/components/ui/sheet'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import { activeFilterCount, parseSearchParams, toSearchParams, type Criteria, type SearchState } from '@/lib/criteria'
import { plural } from '@/lib/format'
import { searchProperties } from '@/lib/queries'
import { cn } from '@/lib/utils'

type View = 'list' | 'split' | 'map'

const VIEW_KEY = 'domradar.view'

export default function SearchPage() {
  const [params, setParams] = useSearchParams()
  const location = useLocation()
  const state = useMemo(() => parseSearchParams(params), [params])
  const [view, setView] = useState<View>(() => (localStorage.getItem(VIEW_KEY) as View) || 'split')
  const [qDraft, setQDraft] = useState<{ source?: string; value: string }>({ source: state.criteria.q, value: state.criteria.q ?? '' })
  // gdy q w URL zmieni się z zewnątrz (np. link), pole tekstowe przyjmuje nową wartość
  const q = qDraft.source === state.criteria.q ? qDraft.value : state.criteria.q ?? ''
  const setQ = (value: string) => setQDraft({ source: state.criteria.q, value })
  const [filtersOpen, setFiltersOpen] = useState(false)
  const [highlighted, setHighlighted] = useState<number | null>(null)
  const [editing, setEditing] = useState<EditingSavedSearch | null>(
    () => (location.state as { editSavedSearch?: EditingSavedSearch } | null)?.editSavedSearch ?? null)

  useEffect(() => localStorage.setItem(VIEW_KEY, view), [view])

  const update = (patch: Partial<SearchState>) => {
    const next: SearchState = { ...state, ...patch, page: patch.page ?? 0 }
    setParams(toSearchParams(next))
    if (patch.page !== undefined) window.scrollTo({ top: 0, behavior: 'smooth' })
  }
  const updateCriteria = (patch: Partial<Criteria>) => update({ criteria: { ...state.criteria, ...patch } })

  const result = useQuery({
    queryKey: ['search', params.toString()],
    queryFn: ({ signal }) => searchProperties(state.criteria, state.page, state.size, state.sort, signal),
    placeholderData: keepPreviousData,
  })

  useEffect(() => {
    if (result.error) toast.error('Błąd wyszukiwania', { description: (result.error as Error).message })
  }, [result.error])

  const filterCount = activeFilterCount(state.criteria)
  const hasPoint = state.criteria.lat !== undefined && state.criteria.lon !== undefined
  const data = result.data

  const filters = (close?: () => void) => (
    <FiltersPanel key={JSON.stringify(state.criteria)} criteria={state.criteria} onApply={(criteria) => update({ criteria })} onClose={close} />
  )

  return (
    <div className="grid gap-4 lg:grid-cols-[300px_minmax(0,1fr)]">
      <aside className="sticky top-18 hidden h-[calc(100svh-6rem)] rounded-xl border bg-background p-4 lg:block">
        {filters()}
      </aside>

      <section className="min-w-0 space-y-3">
        <form className="flex gap-2" onSubmit={(e) => {
          e.preventDefault()
          updateCriteria({ q: q.trim() || undefined })
        }}>
          <div className="relative flex-1">
            <Search className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input value={q} onChange={(e) => setQ(e.target.value)} className="h-10 bg-background pl-9"
                   placeholder='Szukaj, np. "balkon metro -parter -suterena" lub ulica' aria-label="Wyszukiwanie pełnotekstowe" />
          </div>
          <Button type="submit" className="h-10">Szukaj</Button>
          <Sheet open={filtersOpen} onOpenChange={setFiltersOpen}>
            <SheetTrigger asChild>
              <Button type="button" variant="outline" className="h-10 lg:hidden">
                <SlidersHorizontal /> Filtry{filterCount ? ` (${filterCount})` : ''}
              </Button>
            </SheetTrigger>
            <SheetContent side="left" className="w-[90vw] max-w-sm p-4">
              <SheetHeader className="p-0"><SheetTitle className="sr-only">Filtry</SheetTitle></SheetHeader>
              {filters(() => setFiltersOpen(false))}
            </SheetContent>
          </Sheet>
        </form>

        {editing && (
          <div className="flex flex-wrap items-center gap-2 rounded-lg border border-teal-600/40 bg-teal-50 px-3 py-2 text-sm dark:bg-teal-950/30">
            <span>Edytujesz kryteria wyszukiwania <strong>„{editing.name}”</strong>. Zmień filtry i zapisz.</span>
            <div className="ml-auto flex gap-2">
              <SaveSearchDialog criteria={state.criteria} editing={editing} onSaved={() => setEditing(null)} />
              <Button size="sm" variant="ghost" onClick={() => setEditing(null)}><X /> Zakończ edycję</Button>
            </div>
          </div>
        )}

        <div className="flex flex-wrap items-center justify-between gap-2">
          <SortBar sort={state.sort} onChange={(sort) => update({ sort })} hasPoint={hasPoint} hasQuery={!!state.criteria.q} />
          <div className="flex items-center gap-2">
            {!editing && <SaveSearchDialog criteria={state.criteria} />}
            <ToggleGroup type="single" variant="outline" size="sm" value={view} onValueChange={(v) => v && setView(v as View)}>
              <ToggleGroupItem value="list" aria-label="Lista"><List /></ToggleGroupItem>
              <ToggleGroupItem value="split" aria-label="Lista i mapa" className="hidden xl:flex"><Columns2 /></ToggleGroupItem>
              <ToggleGroupItem value="map" aria-label="Mapa"><MapIcon /></ToggleGroupItem>
            </ToggleGroup>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2 text-sm">
          {data && (
            <span className="font-medium">
              {data.totalElements.toLocaleString('pl-PL')} {plural(data.totalElements, 'nieruchomość', 'nieruchomości', 'nieruchomości')}
            </span>
          )}
          {filterCount > 0 && (
            <Button variant="link" size="sm" className="h-auto px-1" onClick={() => update({ criteria: { q: state.criteria.q } })}>
              Wyczyść filtry ({filterCount})
            </Button>
          )}
          {result.isFetching && !result.isLoading && <span className="text-muted-foreground">Odświeżanie…</span>}
        </div>

        <FacetsBar facets={data?.facets} criteria={state.criteria} onChange={updateCriteria} />

        <div className={cn('grid gap-4', view === 'split' && 'xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]')}>
          {view !== 'map' && (
            <div className="min-w-0 space-y-3">
              {result.isLoading && <LoadingState label="Szukamy nieruchomości…" />}
              {result.isError && !data && <ErrorState error={result.error} onRetry={() => result.refetch()} />}
              {data && data.content.length === 0 && (
                <EmptyState title="Brak wyników" description="Zmień lub wyczyść filtry, aby zobaczyć więcej ofert." />
              )}
              {data?.content.map((p) => (
                <PropertyCard key={p.id} property={p} highlighted={highlighted === p.id} onHover={setHighlighted} />
              ))}
              {data && (
                <PaginationBar page={data.page} totalPages={data.totalPages} totalElements={data.totalElements}
                               onPage={(page) => update({ page })} />
              )}
            </div>
          )}
          {view !== 'list' && (
            <SearchMap criteria={state.criteria} onChange={updateCriteria} highlightedId={highlighted}
                       className={cn(view === 'map' ? 'h-[calc(100svh-14rem)]' : 'h-[70vh] xl:sticky xl:top-18 xl:h-[calc(100svh-6rem)]')} />
          )}
        </div>
      </section>
    </div>
  )
}
