import { ArrowDown, ArrowUp, Plus, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

const SORT_FIELDS: Record<string, string> = {
  addedAt: 'Data dodania',
  price: 'Cena',
  pricePerM2: 'Cena za m²',
  area: 'Metraż',
  relevance: 'Trafność',
  priceChangedAt: 'Ostatnia zmiana ceny',
  priceDrop: '% obniżki',
  daysOnMarket: 'Dni na rynku',
  vsMedian: '% vs mediana dzielnicy',
  distance: 'Odległość od punktu',
}

const MAX_KEYS = 3

interface SortKey {
  field: string
  dir: 'asc' | 'desc'
}

function parse(sort: string[]): SortKey[] {
  return sort.map((s) => {
    const [field, dir] = s.split(',')
    return { field, dir: dir === 'asc' ? 'asc' : 'desc' }
  })
}

/** Edytor sortowania wielokluczowego (np. cena za m² rosnąco, potem metraż malejąco). */
export function SortBar({ sort, onChange, hasPoint, hasQuery }: {
  sort: string[]
  onChange: (sort: string[]) => void
  hasPoint: boolean
  hasQuery: boolean
}) {
  const keys = parse(sort)
  const emit = (next: SortKey[]) => onChange(next.map((k) => `${k.field},${k.dir}`))
  const available = Object.keys(SORT_FIELDS).filter((f) =>
    (f !== 'distance' || hasPoint) && (f !== 'relevance' || hasQuery))

  return (
    <div className="flex flex-wrap items-center gap-2">
      <span className="text-sm text-muted-foreground">Sortuj:</span>
      {keys.map((key, i) => (
        <div key={`${key.field}-${i}`} className="flex items-center rounded-lg border bg-background">
          <Select value={key.field} onValueChange={(field) => emit(keys.map((k, j) => (j === i ? { ...k, field } : k)))}>
            <SelectTrigger size="sm" className="h-8 border-0 shadow-none"><SelectValue /></SelectTrigger>
            <SelectContent>
              {available.filter((f) => f === key.field || !keys.some((k) => k.field === f)).map((f) => (
                <SelectItem key={f} value={f}>{SORT_FIELDS[f]}</SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button variant="ghost" size="icon-sm" aria-label={key.dir === 'asc' ? 'Rosnąco' : 'Malejąco'}
                  title={key.dir === 'asc' ? 'Rosnąco' : 'Malejąco'}
                  onClick={() => emit(keys.map((k, j) => (j === i ? { ...k, dir: k.dir === 'asc' ? 'desc' : 'asc' } : k)))}>
            {key.dir === 'asc' ? <ArrowUp /> : <ArrowDown />}
          </Button>
          {keys.length > 1 && (
            <Button variant="ghost" size="icon-sm" aria-label="Usuń klucz sortowania" onClick={() => emit(keys.filter((_, j) => j !== i))}>
              <X />
            </Button>
          )}
        </div>
      ))}
      {keys.length < MAX_KEYS && (
        <Button variant="outline" size="sm" onClick={() => {
          const next = available.find((f) => !keys.some((k) => k.field === f))
          if (next) emit([...keys, { field: next, dir: 'asc' }])
        }}>
          <Plus /> Dodaj klucz
        </Button>
      )}
    </div>
  )
}
