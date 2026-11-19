import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { BellRing, ExternalLink, Pencil, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { toast } from 'sonner'
import { EmptyState, ErrorState, LoadingState } from '@/components/common/States'
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription,
  AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Switch } from '@/components/ui/switch'
import { api, errorMessage } from '@/lib/api'
import { DEFAULT_SORT, toSearchParams } from '@/lib/criteria'
import { summarizeCriteria } from '@/lib/criteriaSummary'
import { formatDateTime } from '@/lib/format'
import { queryKeys, useCities, useDistricts } from '@/lib/queries'
import type { AlertMode, SavedSearch } from '@/lib/types'

export default function SavedSearchesPage() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const cities = useCities()
  const districts = useDistricts()
  const [toDelete, setToDelete] = useState<SavedSearch | null>(null)
  const [renaming, setRenaming] = useState<{ id: number; name: string } | null>(null)
  const query = useQuery({ queryKey: queryKeys.savedSearches, queryFn: () => api<SavedSearch[]>('/api/v1/me/saved-searches') })

  const patch = useMutation({
    mutationFn: ({ id, body }: { id: number; body: { name?: string; alertEnabled?: boolean; alertMode?: AlertMode } }) =>
      api<SavedSearch>(`/api/v1/me/saved-searches/${id}`, { method: 'PATCH', body }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.savedSearches })
      toast.success('Zapisano zmiany')
      setRenaming(null)
    },
    onError: (e) => toast.error('Nie udało się zapisać', { description: errorMessage(e) }),
  })
  const remove = useMutation({
    mutationFn: (id: number) => api<void>(`/api/v1/me/saved-searches/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.savedSearches })
      toast.success('Usunięto wyszukiwanie')
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  const searchUrl = (s: SavedSearch) => `/?${toSearchParams({ criteria: s.criteria, sort: DEFAULT_SORT, page: 0, size: 20 })}`

  if (query.isLoading) return <LoadingState />
  if (query.isError) return <ErrorState error={query.error} onRetry={() => query.refetch()} />
  const items = query.data ?? []

  return (
    <div className="mx-auto max-w-4xl space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-2">
        <div>
          <h1 className="text-2xl font-semibold">Moje wyszukiwania</h1>
          <p className="text-sm text-muted-foreground">Limit: 10 wyszukiwań, w tym 3 z alertem natychmiastowym ({items.length}/10).</p>
        </div>
        <Button asChild><Link to="/">Nowe wyszukiwanie</Link></Button>
      </div>
      {items.length === 0 && (
        <EmptyState title="Brak zapisanych wyszukiwań" description="Ustaw filtry w wyszukiwarce i kliknij „Zapisz wyszukiwanie”."
                    action={<Button asChild variant="outline"><Link to="/">Przejdź do wyszukiwarki</Link></Button>} />
      )}
      {items.map((s) => (
        <Card key={s.id}>
          <CardContent className="space-y-3">
            <div className="flex flex-wrap items-start justify-between gap-2">
              {renaming?.id === s.id ? (
                <form className="flex gap-2" onSubmit={(e) => {
                  e.preventDefault()
                  patch.mutate({ id: s.id, body: { name: renaming.name } })
                }}>
                  <Input value={renaming.name} onChange={(e) => setRenaming({ id: s.id, name: e.target.value })} maxLength={100} autoFocus aria-label="Nazwa" />
                  <Button type="submit" size="sm" disabled={!renaming.name.trim()}>Zapisz</Button>
                  <Button type="button" size="sm" variant="ghost" onClick={() => setRenaming(null)}>Anuluj</Button>
                </form>
              ) : (
                <h2 className="text-lg font-medium">{s.name}</h2>
              )}
              <div className="flex gap-1">
                <Button size="sm" variant="outline" asChild><Link to={searchUrl(s)}><ExternalLink /> Wyniki</Link></Button>
                <Button size="sm" variant="outline" onClick={() => navigate(searchUrl(s), {
                  state: { editSavedSearch: { id: s.id, name: s.name, alertEnabled: s.alertEnabled, alertMode: s.alertMode } },
                })}><Pencil /> Edytuj kryteria</Button>
                <Button size="sm" variant="ghost" onClick={() => setRenaming({ id: s.id, name: s.name })}>Zmień nazwę</Button>
                <Button size="icon-sm" variant="ghost" aria-label="Usuń" onClick={() => setToDelete(s)}><Trash2 className="text-destructive" /></Button>
              </div>
            </div>
            <div className="flex flex-wrap gap-1.5">
              {summarizeCriteria(s.criteria, cities.data, districts.data).map((part) => <Badge key={part} variant="secondary">{part}</Badge>)}
              {summarizeCriteria(s.criteria).length === 0 && <span className="text-sm text-muted-foreground">Wszystkie ogłoszenia</span>}
            </div>
            <div className="flex flex-wrap items-center gap-3 rounded-lg bg-muted/60 p-2">
              <BellRing className="size-4 text-teal-600" />
              <label className="flex items-center gap-2 text-sm">
                <Switch checked={s.alertEnabled} disabled={patch.isPending}
                        onCheckedChange={(alertEnabled) => patch.mutate({ id: s.id, body: { alertEnabled } })} />
                Alert {s.alertEnabled ? 'włączony' : 'wyłączony'}
              </label>
              <Select value={s.alertMode} disabled={!s.alertEnabled || patch.isPending}
                      onValueChange={(v) => patch.mutate({ id: s.id, body: { alertMode: v as AlertMode } })}>
                <SelectTrigger size="sm" className="w-56"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="INSTANT">Natychmiast (po imporcie)</SelectItem>
                  <SelectItem value="DAILY">Dzienny przegląd (7:00)</SelectItem>
                </SelectContent>
              </Select>
              <span className="ml-auto text-xs text-muted-foreground">Ostatnie powiadomienie: {formatDateTime(s.lastNotifiedAt)}</span>
            </div>
          </CardContent>
        </Card>
      ))}
      <AlertDialog open={!!toDelete} onOpenChange={(open) => !open && setToDelete(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Usunąć wyszukiwanie „{toDelete?.name}”?</AlertDialogTitle>
            <AlertDialogDescription>Alerty dla tego wyszukiwania przestaną przychodzić.</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Anuluj</AlertDialogCancel>
            <AlertDialogAction onClick={() => toDelete && remove.mutate(toDelete.id)}>Usuń</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
