import { X } from 'lucide-react'
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import type { Criteria } from '@/lib/criteria'
import { NumberField } from './fields'

/**
 * Szybki przedział ceny nad wynikami ("od – do", wpisywany ręcznie). Pozostałe filtry są w panelu po lewej.
 * Komponent montowany z key = bieżący zakres, więc szkic resetuje się po zmianie filtrów z innego miejsca.
 */
export function PriceRangeBar({ criteria, onChange }: {
  criteria: Criteria
  onChange: (patch: Partial<Criteria>) => void
}) {
  const [min, setMin] = useState<number | undefined>(criteria.priceMin)
  const [max, setMax] = useState<number | undefined>(criteria.priceMax)
  const active = criteria.priceMin !== undefined || criteria.priceMax !== undefined
  const dirty = min !== criteria.priceMin || max !== criteria.priceMax

  const apply = () => {
    // zamiana miejscami, gdy "od" > "do" (zamiast błędu walidacji)
    const [from, to] = min !== undefined && max !== undefined && min > max ? [max, min] : [min, max]
    onChange({ priceMin: from, priceMax: to })
  }

  return (
    <form className="flex flex-wrap items-center gap-2 rounded-xl border bg-background px-3 py-2"
          onSubmit={(e) => { e.preventDefault(); apply() }}>
      <span className="text-sm font-medium">Cena</span>
      <div className="flex w-full max-w-sm flex-1 items-center gap-2 sm:w-auto">
        <NumberField value={min} onChange={setMin} placeholder="od" step={10000} min={0} ariaLabel="Cena od" />
        <span className="text-muted-foreground">–</span>
        <NumberField value={max} onChange={setMax} placeholder="do" step={10000} min={0} ariaLabel="Cena do" />
        <span className="text-sm text-muted-foreground">zł</span>
      </div>
      <Button type="submit" size="sm" variant={dirty ? 'default' : 'outline'} disabled={!dirty}>Zastosuj</Button>
      {active && (
        <Button type="button" size="sm" variant="ghost" onClick={() => onChange({ priceMin: undefined, priceMax: undefined })}>
          <X /> Wyczyść
        </Button>
      )}
    </form>
  )
}
