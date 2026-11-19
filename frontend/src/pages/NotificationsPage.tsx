import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { BellRing, CheckCheck, TrendingDown } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { PaginationBar } from '@/components/common/PaginationBar'
import { EmptyState, ErrorState, LoadingState } from '@/components/common/States'
import { Button } from '@/components/ui/button'
import { Switch } from '@/components/ui/switch'
import { api, errorMessage } from '@/lib/api'
import { formatDateTime } from '@/lib/format'
import { queryKeys } from '@/lib/queries'
import type { AppNotification, Page } from '@/lib/types'
import { cn } from '@/lib/utils'

export default function NotificationsPage() {
  const [page, setPage] = useState(0)
  const [unreadOnly, setUnreadOnly] = useState(false)
  const queryClient = useQueryClient()
  const query = useQuery({
    queryKey: [...queryKeys.notifications, page, unreadOnly],
    queryFn: () => api<Page<AppNotification>>('/api/v1/me/notifications', { query: { page, size: 20, unreadOnly } }),
  })
  const invalidate = () => queryClient.invalidateQueries({ queryKey: queryKeys.notifications })
  const setRead = useMutation({
    mutationFn: ({ id, read }: { id: number; read: boolean }) => api(`/api/v1/me/notifications/${id}`, { method: 'PATCH', body: { read } }),
    onSuccess: invalidate,
    onError: (e) => toast.error(errorMessage(e)),
  })
  const markAll = useMutation({
    mutationFn: () => api('/api/v1/me/notifications', { method: 'PATCH' }),
    onSuccess: () => {
      invalidate()
      toast.success('Wszystkie oznaczone jako przeczytane')
    },
  })

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h1 className="text-2xl font-semibold">Powiadomienia</h1>
        <div className="flex items-center gap-3">
          <label className="flex items-center gap-2 text-sm">
            <Switch checked={unreadOnly} onCheckedChange={(v) => { setUnreadOnly(v); setPage(0) }} /> Tylko nieprzeczytane
          </label>
          <Button variant="outline" size="sm" onClick={() => markAll.mutate()}><CheckCheck /> Oznacz wszystkie</Button>
        </div>
      </div>
      {query.isLoading && <LoadingState />}
      {query.isError && <ErrorState error={query.error} onRetry={() => query.refetch()} />}
      {query.data?.content.length === 0 && (
        <EmptyState title="Brak powiadomień" description="Włącz alert w „Moich wyszukiwaniach”, aby dostawać informacje o nowych ogłoszeniach." />
      )}
      <ul className="space-y-2">
        {query.data?.content.map((n) => {
          const ids = [...(n.payload.newPropertyIds ?? []), ...(n.payload.priceDropPropertyIds ?? [])].slice(0, 6)
          return (
            <li key={n.id} className={cn('rounded-xl border bg-background p-3', !n.read && 'border-teal-600/50 bg-teal-50/60 dark:bg-teal-950/20')}>
              <div className="flex items-start gap-3">
                {n.type === 'PRICE_DROP' ? <TrendingDown className="mt-0.5 size-5 text-red-600" /> : <BellRing className="mt-0.5 size-5 text-teal-600" />}
                <div className="flex-1">
                  <p className="font-medium">{n.title}</p>
                  <p className="text-xs text-muted-foreground">{formatDateTime(n.createdAt)}</p>
                  {ids.length > 0 && (
                    <div className="mt-2 flex flex-wrap gap-2">
                      {ids.map((id) => (
                        <Link key={id} to={`/nieruchomosci/${id}`} className="rounded-md border px-2 py-0.5 text-xs hover:border-teal-600"
                              onClick={() => !n.read && setRead.mutate({ id: n.id, read: true })}>
                          Ogłoszenie #{id}
                        </Link>
                      ))}
                    </div>
                  )}
                </div>
                <Button variant="ghost" size="sm" onClick={() => setRead.mutate({ id: n.id, read: !n.read })}>
                  {n.read ? 'Oznacz jako nowe' : 'Przeczytane'}
                </Button>
              </div>
            </li>
          )
        })}
      </ul>
      {query.data && <PaginationBar page={query.data.page} totalPages={query.data.totalPages} onPage={setPage} />}
    </div>
  )
}
