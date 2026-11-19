import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Radio, Send } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { PaginationBar } from '@/components/common/PaginationBar'
import { ErrorState, LoadingState } from '@/components/common/States'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { api, errorMessage } from '@/lib/api'
import { formatDateTime, IMPORT_STATUS_LABELS } from '@/lib/format'
import { useLiveTopic } from '@/lib/live'
import type { ImportJob, ImportStatus, Page, Source } from '@/lib/types'

const ALL = 'ALL'
const STATUS_CLASS: Record<ImportStatus, string> = {
  QUEUED: 'bg-zinc-200 text-zinc-800',
  RUNNING: 'bg-blue-600 text-white animate-pulse',
  SUCCESS: 'bg-emerald-600 text-white',
  FAILED: 'bg-red-600 text-white',
}

/** Lista zadań importu; statusy i liczniki aktualizowane na żywo przez WebSocket (/topic/admin/import-jobs). */
export default function ImportJobsPage() {
  const queryClient = useQueryClient()
  const [page, setPage] = useState(0)
  const [sourceId, setSourceId] = useState<string>(ALL)
  const [status, setStatus] = useState<string>(ALL)
  const key = ['admin', 'import-jobs', page, sourceId, status]
  const sources = useQuery({ queryKey: ['admin', 'sources'], queryFn: () => api<Source[]>('/api/v1/admin/sources') })
  const jobs = useQuery({
    queryKey: key,
    queryFn: () => api<Page<ImportJob>>('/api/v1/admin/import-jobs', {
      query: { page, size: 20, sourceId: sourceId === ALL ? undefined : sourceId, status: status === ALL ? undefined : status },
    }),
  })

  const live = useLiveTopic('/topic/admin/import-jobs', (body) => {
    const job = body as ImportJob
    let found = false
    queryClient.setQueryData<Page<ImportJob>>(key, (old) => {
      if (!old) return old
      const content = old.content.map((j) => {
        if (j.id !== job.id) return j
        found = true
        return job
      })
      return { ...old, content }
    })
    if (!found && page === 0) queryClient.invalidateQueries({ queryKey: ['admin', 'import-jobs'] })
    if (job.status === 'SUCCESS') toast.success(`Import ${job.sourceName} zakończony: +${job.created} nowych, ${job.merged} scalonych`)
    if (job.status === 'FAILED') toast.error(`Import ${job.sourceName} nie powiódł się`, { description: job.errorMessage })
  })

  const digest = useMutation({
    mutationFn: () => api<{ sent: number }>('/api/v1/admin/alerts/daily-digest', { method: 'POST' }),
    onSuccess: (r) => toast.success(`Wysłano ${r.sent} dziennych przeglądów`),
    onError: (e) => toast.error(errorMessage(e)),
  })

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <h2 className="text-lg font-semibold">Zadania importu</h2>
        <Badge variant="outline" className="gap-1"><Radio className={live ? 'size-3 text-emerald-600' : 'size-3 text-zinc-400'} />{live ? 'na żywo' : 'offline'}</Badge>
        <div className="ml-auto flex flex-wrap gap-2">
          <Select value={sourceId} onValueChange={(v) => { setSourceId(v); setPage(0) }}>
            <SelectTrigger size="sm" className="w-44"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>Wszystkie źródła</SelectItem>
              {sources.data?.map((s) => <SelectItem key={s.id} value={String(s.id)}>{s.name}</SelectItem>)}
            </SelectContent>
          </Select>
          <Select value={status} onValueChange={(v) => { setStatus(v); setPage(0) }}>
            <SelectTrigger size="sm" className="w-36"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>Każdy status</SelectItem>
              {Object.entries(IMPORT_STATUS_LABELS).map(([k, label]) => <SelectItem key={k} value={k}>{label}</SelectItem>)}
            </SelectContent>
          </Select>
          <Button size="sm" variant="outline" disabled={digest.isPending} onClick={() => digest.mutate()}><Send /> Wyślij dzienny digest</Button>
        </div>
      </div>
      {jobs.isLoading && <LoadingState />}
      {jobs.isError && <ErrorState error={jobs.error} onRetry={() => jobs.refetch()} />}
      {jobs.data && (
        <div className="overflow-x-auto rounded-xl border bg-background">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>#</TableHead>
                <TableHead>Źródło</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Postęp</TableHead>
                <TableHead className="text-right">Pobrane</TableHead>
                <TableHead className="text-right">Nowe</TableHead>
                <TableHead className="text-right">Scalone</TableHead>
                <TableHead className="text-right">Zmienione</TableHead>
                <TableHead className="text-right">Dezakt.</TableHead>
                <TableHead className="text-right">Błędy</TableHead>
                <TableHead>Utworzono</TableHead>
                <TableHead>Wyzwalacz</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {jobs.data.content.map((j) => {
                const pct = j.expectedBatches ? Math.round((j.processedBatches / Math.max(1, j.expectedBatches)) * 100) : null
                return (
                  <TableRow key={j.id}>
                    <TableCell className="tabular-nums">{j.id}</TableCell>
                    <TableCell>{j.sourceName}</TableCell>
                    <TableCell>
                      <Badge className={STATUS_CLASS[j.status]}>{IMPORT_STATUS_LABELS[j.status]}</Badge>
                      {j.errorMessage && <div className="max-w-60 truncate text-xs text-destructive" title={j.errorMessage}>{j.errorMessage}</div>}
                    </TableCell>
                    <TableCell className="w-36">
                      {j.status === 'RUNNING' || j.status === 'SUCCESS' ? (
                        <div className="space-y-1">
                          <div className="h-1.5 rounded-full bg-muted">
                            <div className="h-1.5 rounded-full bg-teal-600 transition-all" style={{ width: `${j.status === 'SUCCESS' ? 100 : pct ?? 10}%` }} />
                          </div>
                          <div className="text-[10px] text-muted-foreground">{j.processedBatches}/{j.expectedBatches ?? '?'} batchy</div>
                        </div>
                      ) : '—'}
                    </TableCell>
                    <TableCell className="text-right tabular-nums">{j.fetched}</TableCell>
                    <TableCell className="text-right tabular-nums">{j.created}</TableCell>
                    <TableCell className="text-right tabular-nums">{j.merged}</TableCell>
                    <TableCell className="text-right tabular-nums">{j.updated}</TableCell>
                    <TableCell className="text-right tabular-nums">{j.deactivated}</TableCell>
                    <TableCell className="text-right tabular-nums">{j.errors}</TableCell>
                    <TableCell className="text-xs whitespace-nowrap">{formatDateTime(j.createdAt)}</TableCell>
                    <TableCell className="text-xs">{j.trigger}{j.requestedBy ? ` (${j.requestedBy})` : ''}</TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        </div>
      )}
      {jobs.data && <PaginationBar page={jobs.data.page} totalPages={jobs.data.totalPages} totalElements={jobs.data.totalElements} onPage={setPage} />}
    </div>
  )
}
