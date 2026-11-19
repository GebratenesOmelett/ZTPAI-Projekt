import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Button } from '@/components/ui/button'

function pages(current: number, total: number): (number | 'gap')[] {
  const result: (number | 'gap')[] = []
  for (let i = 0; i < total; i++) {
    if (i === 0 || i === total - 1 || Math.abs(i - current) <= 1) result.push(i)
    else if (result[result.length - 1] !== 'gap') result.push('gap')
  }
  return result
}

/** Paginacja (strony liczone od 0, jak w API). */
export function PaginationBar({ page, totalPages, totalElements, onPage }: {
  page: number
  totalPages: number
  totalElements?: number
  onPage: (page: number) => void
}) {
  if (totalPages <= 1) {
    return totalElements !== undefined ? <p className="py-3 text-center text-sm text-muted-foreground">Wyników: {totalElements}</p> : null
  }
  return (
    <nav className="flex flex-wrap items-center justify-center gap-1 py-4" aria-label="Paginacja">
      <Button variant="outline" size="sm" disabled={page === 0} onClick={() => onPage(page - 1)}>
        <ChevronLeft /> Poprzednia
      </Button>
      {pages(page, totalPages).map((p, i) => p === 'gap' ? (
        <span key={`gap-${i}`} className="px-1 text-muted-foreground">…</span>
      ) : (
        <Button key={p} size="sm" variant={p === page ? 'default' : 'ghost'} aria-current={p === page ? 'page' : undefined}
                onClick={() => onPage(p)}>{p + 1}</Button>
      ))}
      <Button variant="outline" size="sm" disabled={page >= totalPages - 1} onClick={() => onPage(page + 1)}>
        Następna <ChevronRight />
      </Button>
    </nav>
  )
}
