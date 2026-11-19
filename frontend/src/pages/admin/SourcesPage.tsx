import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Pencil, Play, Plus, Trash2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { z } from 'zod'
import { FormField } from '@/components/common/FormField'
import { ErrorState, LoadingState } from '@/components/common/States'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Switch } from '@/components/ui/switch'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { api, ApiError, errorMessage } from '@/lib/api'
import { formatDateTime } from '@/lib/format'
import { applyApiErrors } from '@/lib/forms'
import type { Adapter, ImportJob, Source } from '@/lib/types'

const schema = z.object({
  code: z.string().trim().min(1, 'Wymagane').max(50).regex(/^[a-z0-9-]+$/, 'Małe litery, cyfry i myślniki'),
  name: z.string().trim().min(1, 'Wymagane').max(100),
  baseUrl: z.string().trim().url('Niepoprawny URL'),
  adapter: z.string().min(1, 'Wybierz adapter'),
  enabled: z.boolean(),
  importCron: z.string().trim().max(100).optional(),
})
type Values = z.infer<typeof schema>
const FIELDS = ['code', 'name', 'baseUrl', 'adapter', 'enabled', 'importCron'] as const

function SourceDialog({ source, open, onOpenChange, adapters }: {
  source: Source | null
  open: boolean
  onOpenChange: (open: boolean) => void
  adapters: Adapter[]
}) {
  const queryClient = useQueryClient()
  const form = useForm<Values>({ resolver: zodResolver(schema) })
  useEffect(() => {
    if (open) {
      form.reset(source
        ? { code: source.code, name: source.name, baseUrl: source.baseUrl, adapter: source.adapter, enabled: source.enabled, importCron: source.importCron ?? '' }
        : { code: '', name: '', baseUrl: 'https://', adapter: 'demo', enabled: true, importCron: '0 0 */6 * * *' })
    }
  }, [open, source, form])

  const save = useMutation({
    mutationFn: (v: Values) => source
      ? api<Source>(`/api/v1/admin/sources/${source.id}`, { method: 'PUT', body: v })
      : api<Source>('/api/v1/admin/sources', { method: 'POST', body: v }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'sources'] })
      toast.success(source ? 'Zapisano źródło' : 'Dodano źródło')
      onOpenChange(false)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.errorCode === 'SOURCE_CODE_TAKEN') form.setError('code', { message: e.message })
      else if (e instanceof ApiError && e.errorCode === 'UNKNOWN_ADAPTER') form.setError('adapter', { message: e.message })
      else if (!applyApiErrors(e, form.setError, FIELDS)) toast.error(errorMessage(e))
    },
  })
  const { errors } = form.formState

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <form onSubmit={form.handleSubmit((v) => save.mutate(v))} noValidate className="space-y-4">
          <DialogHeader><DialogTitle>{source ? `Edycja: ${source.name}` : 'Nowe źródło'}</DialogTitle></DialogHeader>
          <div className="grid grid-cols-2 gap-3">
            <FormField id="src-code" label="Kod" error={errors.code?.message}><Input id="src-code" {...form.register('code')} /></FormField>
            <FormField id="src-name" label="Nazwa" error={errors.name?.message}><Input id="src-name" {...form.register('name')} /></FormField>
          </div>
          <FormField id="src-url" label="Adres portalu" error={errors.baseUrl?.message}><Input id="src-url" {...form.register('baseUrl')} /></FormField>
          <FormField id="src-adapter" label="Adapter" error={errors.adapter?.message}>
            <Controller control={form.control} name="adapter" render={({ field }) => (
              <Select value={field.value} onValueChange={field.onChange}>
                <SelectTrigger id="src-adapter" className="w-full"><SelectValue placeholder="Wybierz" /></SelectTrigger>
                <SelectContent>{adapters.map((a) => <SelectItem key={a.code} value={a.code}>{a.code} — {a.description}</SelectItem>)}</SelectContent>
              </Select>
            )} />
          </FormField>
          <FormField id="src-cron" label="Harmonogram (cron, 6 pól)" error={errors.importCron?.message} hint="np. 0 0 */6 * * * — co 6 godzin; puste = tylko ręcznie">
            <Input id="src-cron" {...form.register('importCron')} />
          </FormField>
          <label className="flex items-center gap-2 text-sm">
            <Controller control={form.control} name="enabled" render={({ field }) => <Switch checked={field.value} onCheckedChange={field.onChange} />} />
            Źródło włączone
          </label>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>Anuluj</Button>
            <Button type="submit" disabled={save.isPending}>Zapisz</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

export default function SourcesPage() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [editing, setEditing] = useState<Source | null>(null)
  const [dialogOpen, setDialogOpen] = useState(false)
  const sources = useQuery({ queryKey: ['admin', 'sources'], queryFn: () => api<Source[]>('/api/v1/admin/sources') })
  const adapters = useQuery({ queryKey: ['admin', 'adapters'], queryFn: () => api<Adapter[]>('/api/v1/admin/adapters') })

  const startImport = useMutation({
    mutationFn: (id: number) => api<ImportJob>(`/api/v1/admin/sources/${id}/imports`, { method: 'POST' }),
    onSuccess: (job) => {
      toast.success(`Import ${job.sourceName} przyjęty do kolejki (zadanie #${job.id})`, {
        action: { label: 'Podgląd', onClick: () => navigate('/admin/importy') },
      })
      queryClient.invalidateQueries({ queryKey: ['admin', 'import-jobs'] })
    },
    onError: (e) => {
      const title = e instanceof ApiError && e.status === 409 ? 'Import już trwa'
        : e instanceof ApiError && e.status === 422 ? 'Nie można uruchomić importu' : 'Błąd'
      toast.error(title, { description: errorMessage(e) })
    },
  })
  const toggle = useMutation({
    mutationFn: (s: Source) => api<Source>(`/api/v1/admin/sources/${s.id}`, {
      method: 'PUT', body: { code: s.code, name: s.name, baseUrl: s.baseUrl, adapter: s.adapter, enabled: !s.enabled, importCron: s.importCron },
    }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'sources'] }),
    onError: (e) => toast.error(errorMessage(e)),
  })
  const remove = useMutation({
    mutationFn: (id: number) => api<void>(`/api/v1/admin/sources/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      toast.success('Usunięto źródło')
      queryClient.invalidateQueries({ queryKey: ['admin', 'sources'] })
    },
    onError: (e) => toast.error('Nie można usunąć', { description: errorMessage(e) }),
  })

  if (sources.isLoading) return <LoadingState />
  if (sources.isError) return <ErrorState error={sources.error} onRetry={() => sources.refetch()} />

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold">Źródła danych</h2>
        <Button onClick={() => { setEditing(null); setDialogOpen(true) }}><Plus /> Dodaj źródło</Button>
      </div>
      <div className="overflow-x-auto rounded-xl border bg-background">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Portal</TableHead>
              <TableHead>Adapter</TableHead>
              <TableHead>Harmonogram</TableHead>
              <TableHead>Ostatni import</TableHead>
              <TableHead>Włączone</TableHead>
              <TableHead className="text-right">Akcje</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {sources.data?.map((s) => (
              <TableRow key={s.id}>
                <TableCell><div className="font-medium">{s.name}</div><div className="text-xs text-muted-foreground">{s.code}</div></TableCell>
                <TableCell><Badge variant="outline">{s.adapter}</Badge></TableCell>
                <TableCell className="font-mono text-xs">{s.importCron ?? '—'}</TableCell>
                <TableCell className="text-sm">{formatDateTime(s.lastImportAt)}</TableCell>
                <TableCell><Switch checked={s.enabled} onCheckedChange={() => toggle.mutate(s)} aria-label={`Włącz ${s.name}`} /></TableCell>
                <TableCell className="space-x-1 text-right whitespace-nowrap">
                  <Button size="sm" disabled={startImport.isPending} onClick={() => startImport.mutate(s.id)}><Play /> Importuj</Button>
                  <Button size="icon-sm" variant="outline" aria-label="Edytuj" onClick={() => { setEditing(s); setDialogOpen(true) }}><Pencil /></Button>
                  <Button size="icon-sm" variant="ghost" aria-label="Usuń" onClick={() => {
                    if (window.confirm(`Usunąć źródło ${s.name}? Źródła z ofertami nie można usunąć — trzeba je wyłączyć.`)) remove.mutate(s.id)
                  }}><Trash2 className="text-destructive" /></Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
      <SourceDialog source={editing} open={dialogOpen} onOpenChange={setDialogOpen} adapters={adapters.data ?? []} />
    </div>
  )
}
