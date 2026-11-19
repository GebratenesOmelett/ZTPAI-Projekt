import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Archive, HeartOff, NotebookPen } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { PaginationBar } from '@/components/common/PaginationBar'
import { EmptyState, ErrorState, LoadingState } from '@/components/common/States'
import { PhotoOrPlaceholder } from '@/components/search/PropertyCard'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { api, errorMessage } from '@/lib/api'
import { formatArea, formatDate, formatPrice, formatPricePerM2 } from '@/lib/format'
import { queryKeys } from '@/lib/queries'
import type { Favorite, Page } from '@/lib/types'

function NoteEditor({ favorite, onSave, saving }: { favorite: Favorite; onSave: (note: string | null) => void; saving: boolean }) {
  const [editing, setEditing] = useState(false)
  const [text, setText] = useState(favorite.note ?? '')
  if (!editing) {
    return (
      <div className="flex flex-wrap items-start gap-2">
        {favorite.note ? <p className="flex-1 rounded-md bg-amber-50 p-2 text-sm whitespace-pre-line dark:bg-amber-950/30">{favorite.note}</p>
          : <p className="flex-1 text-sm text-muted-foreground">Brak notatki</p>}
        <Button size="sm" variant="outline" onClick={() => { setText(favorite.note ?? ''); setEditing(true) }}>
          <NotebookPen /> {favorite.note ? 'Edytuj notatkę' : 'Dodaj notatkę'}
        </Button>
        {favorite.note && <Button size="sm" variant="ghost" disabled={saving} onClick={() => onSave(null)}>Usuń notatkę</Button>}
      </div>
    )
  }
  return (
    <form className="space-y-2" onSubmit={(e) => {
      e.preventDefault()
      onSave(text.trim() || null)
      setEditing(false)
    }}>
      <Textarea value={text} onChange={(e) => setText(e.target.value)} maxLength={2000} rows={3} autoFocus aria-label="Notatka" />
      <div className="flex gap-2">
        <Button type="submit" size="sm" disabled={saving}>Zapisz notatkę</Button>
        <Button type="button" size="sm" variant="ghost" onClick={() => setEditing(false)}>Anuluj</Button>
        <span className="ml-auto text-xs text-muted-foreground">{text.length}/2000</span>
      </div>
    </form>
  )
}

export default function FavoritesPage() {
  const [page, setPage] = useState(0)
  const queryClient = useQueryClient()
  const query = useQuery({
    queryKey: queryKeys.favorites(page),
    queryFn: () => api<Page<Favorite>>('/api/v1/me/favorites', { query: { page, size: 10 } }),
  })
  const updateNote = useMutation({
    mutationFn: ({ id, note }: { id: number; note: string | null }) =>
      api<Favorite>(`/api/v1/me/favorites/${id}`, { method: 'PATCH', body: { note } }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['favorites'] })
      toast.success('Notatka zapisana')
    },
    onError: (e) => toast.error(errorMessage(e)),
  })
  const remove = useMutation({
    mutationFn: (id: number) => api<void>(`/api/v1/me/favorites/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['favorites'] })
      toast.success('Usunięto z ulubionych')
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  if (query.isLoading) return <LoadingState />
  if (query.isError) return <ErrorState error={query.error} onRetry={() => query.refetch()} />
  const data = query.data!

  return (
    <div className="mx-auto max-w-4xl space-y-4">
      <h1 className="text-2xl font-semibold">Ulubione ({data.totalElements})</h1>
      {data.content.length === 0 && (
        <EmptyState title="Nie masz jeszcze ulubionych" description="Dodaj nieruchomość do ulubionych na stronie jej szczegółów." />
      )}
      {data.content.map((f) => (
        <article key={f.id} className="flex flex-col gap-3 rounded-xl border bg-background p-3 sm:flex-row">
          <Link to={`/nieruchomosci/${f.propertyId}`} className="h-36 shrink-0 overflow-hidden rounded-lg sm:w-48">
            <PhotoOrPlaceholder src={f.mainPhotoUrl} alt={f.title} className={f.archived ? 'grayscale' : ''} />
          </Link>
          <div className="flex flex-1 flex-col gap-2">
            <div className="flex items-start justify-between gap-2">
              <div>
                <Link to={`/nieruchomosci/${f.propertyId}`} className="font-medium hover:text-teal-700">{f.title}</Link>
                <p className="text-sm text-muted-foreground">{f.city}{f.district ? `, ${f.district}` : ''} · {formatArea(f.area)}{f.rooms ? ` · ${f.rooms} pok.` : ''}</p>
              </div>
              <div className="text-right">
                <div className="font-semibold">{formatPrice(f.price, f.transactionType)}</div>
                <div className="text-xs text-muted-foreground">{formatPricePerM2(f.pricePerM2)}</div>
              </div>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              {f.archived && <Badge variant="secondary"><Archive className="size-3" /> Archiwalne — brak aktywnych ofert</Badge>}
              {f.priceDropped && <Badge className="bg-red-600 text-white">Obniżka ceny</Badge>}
              <span className="text-xs text-muted-foreground">Dodano {formatDate(f.createdAt)}</span>
              <Button size="sm" variant="ghost" className="ml-auto text-destructive" disabled={remove.isPending} onClick={() => remove.mutate(f.id)}>
                <HeartOff /> Usuń z ulubionych
              </Button>
            </div>
            <NoteEditor favorite={f} saving={updateNote.isPending} onSave={(note) => updateNote.mutate({ id: f.id, note })} />
          </div>
        </article>
      ))}
      <PaginationBar page={data.page} totalPages={data.totalPages} onPage={setPage} />
    </div>
  )
}
