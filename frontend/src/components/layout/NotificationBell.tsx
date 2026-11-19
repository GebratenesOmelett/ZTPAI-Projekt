import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Bell } from 'lucide-react'
import { Link } from 'react-router'
import { Button } from '@/components/ui/button'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { api } from '@/lib/api'
import { formatDateTime } from '@/lib/format'
import { useLive } from '@/lib/live'
import { fetchUnread, queryKeys } from '@/lib/queries'
import type { AppNotification, Page } from '@/lib/types'
import { cn } from '@/lib/utils'

/** Dzwonek z licznikiem nieprzeczytanych; licznik odświeża się na żywo po pushu z WebSocket. */
export function NotificationBell() {
  const queryClient = useQueryClient()
  const { connected } = useLive()
  const unread = useQuery({ queryKey: queryKeys.unread, queryFn: fetchUnread, refetchInterval: connected ? false : 60_000 })
  const latest = useQuery({
    queryKey: [...queryKeys.notifications, 'latest'],
    queryFn: () => api<Page<AppNotification>>('/api/v1/me/notifications', { query: { size: 5 } }),
  })
  const markAll = useMutation({
    mutationFn: () => api('/api/v1/me/notifications', { method: 'PATCH' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.notifications }),
  })
  const count = unread.data?.unread ?? 0

  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button variant="ghost" size="icon" className="relative" aria-label={`Powiadomienia (${count} nieprzeczytanych)`}>
          <Bell />
          {count > 0 && (
            <span className="absolute -top-0.5 -right-0.5 flex min-w-4 items-center justify-center rounded-full bg-red-600 px-1 text-[10px] font-bold text-white">
              {count > 99 ? '99+' : count}
            </span>
          )}
          <span className={cn('absolute bottom-0.5 right-0.5 size-1.5 rounded-full', connected ? 'bg-emerald-500' : 'bg-zinc-400')}
                title={connected ? 'Połączono na żywo' : 'Brak połączenia na żywo'} />
        </Button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-80 p-0">
        <div className="flex items-center justify-between border-b px-3 py-2">
          <span className="text-sm font-medium">Powiadomienia</span>
          <Button variant="link" size="sm" disabled={count === 0 || markAll.isPending} onClick={() => markAll.mutate()}>
            Oznacz wszystkie
          </Button>
        </div>
        <ul className="max-h-80 overflow-y-auto">
          {(latest.data?.content ?? []).map((n) => (
            <li key={n.id} className={cn('border-b px-3 py-2 text-sm last:border-0', !n.read && 'bg-teal-50 dark:bg-teal-950/30')}>
              <p className="font-medium leading-snug">{n.title}</p>
              <p className="text-xs text-muted-foreground">{formatDateTime(n.createdAt)}</p>
            </li>
          ))}
          {latest.data?.content.length === 0 && <li className="px-3 py-6 text-center text-sm text-muted-foreground">Brak powiadomień</li>}
        </ul>
        <div className="border-t p-2 text-center">
          <Link to="/powiadomienia" className="text-sm text-teal-700 hover:underline">Wszystkie powiadomienia</Link>
        </div>
      </PopoverContent>
    </Popover>
  )
}
