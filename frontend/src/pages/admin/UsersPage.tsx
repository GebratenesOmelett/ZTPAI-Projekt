import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Search, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { PaginationBar } from '@/components/common/PaginationBar'
import { ErrorState, LoadingState } from '@/components/common/States'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Switch } from '@/components/ui/switch'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { api, ApiError, errorMessage } from '@/lib/api'
import { useAuth } from '@/lib/auth'
import { formatDate } from '@/lib/format'
import type { Page, Role, User } from '@/lib/types'

const ALL = 'ALL'

export default function UsersPage() {
  const { user: me } = useAuth()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(0)
  const [qInput, setQInput] = useState('')
  const [q, setQ] = useState('')
  const [role, setRole] = useState(ALL)
  const query = useQuery({
    queryKey: ['admin', 'users', page, q, role],
    queryFn: () => api<Page<User>>('/api/v1/admin/users', { query: { page, size: 20, q: q || undefined, role: role === ALL ? undefined : role } }),
  })
  const onError = (e: unknown) => {
    const title = e instanceof ApiError && e.errorCode === 'LAST_ADMIN' ? 'Ostatni administrator'
      : e instanceof ApiError && e.errorCode === 'SELF_MODIFICATION' ? 'Nie możesz zmienić własnego konta' : 'Błąd'
    toast.error(title, { description: errorMessage(e) })
  }
  const update = useMutation({
    mutationFn: ({ id, body }: { id: number; body: { role?: Role; enabled?: boolean } }) =>
      api<User>(`/api/v1/admin/users/${id}`, { method: 'PATCH', body }),
    onSuccess: (u) => {
      toast.success(`Zaktualizowano ${u.email}`)
      queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
    },
    onError,
  })
  const remove = useMutation({
    mutationFn: (id: number) => api<void>(`/api/v1/admin/users/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      toast.success('Usunięto użytkownika')
      queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
    },
    onError,
  })

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <h2 className="text-lg font-semibold">Użytkownicy</h2>
        <form className="ml-auto flex gap-2" onSubmit={(e) => { e.preventDefault(); setQ(qInput.trim()); setPage(0) }}>
          <Input className="h-8 w-56" placeholder="E-mail lub nazwa" value={qInput} onChange={(e) => setQInput(e.target.value)} aria-label="Szukaj użytkownika" />
          <Button size="sm" type="submit"><Search /></Button>
        </form>
        <Select value={role} onValueChange={(v) => { setRole(v); setPage(0) }}>
          <SelectTrigger size="sm" className="w-36"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>Każda rola</SelectItem>
            <SelectItem value="USER">Użytkownik</SelectItem>
            <SelectItem value="ADMIN">Administrator</SelectItem>
          </SelectContent>
        </Select>
      </div>
      {query.isLoading && <LoadingState />}
      {query.isError && <ErrorState error={query.error} onRetry={() => query.refetch()} />}
      {query.data && (
        <div className="overflow-x-auto rounded-xl border bg-background">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Użytkownik</TableHead>
                <TableHead>Rola</TableHead>
                <TableHead>Aktywny</TableHead>
                <TableHead>Utworzono</TableHead>
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {query.data.content.map((u) => (
                <TableRow key={u.id}>
                  <TableCell>
                    <div className="font-medium">{u.displayName} {u.id === me?.id && <Badge variant="outline">Ty</Badge>}</div>
                    <div className="text-xs text-muted-foreground">{u.email}</div>
                  </TableCell>
                  <TableCell>
                    <Select value={u.role} onValueChange={(v) => update.mutate({ id: u.id, body: { role: v as Role } })}>
                      <SelectTrigger size="sm" className="w-36"><SelectValue /></SelectTrigger>
                      <SelectContent>
                        <SelectItem value="USER">Użytkownik</SelectItem>
                        <SelectItem value="ADMIN">Administrator</SelectItem>
                      </SelectContent>
                    </Select>
                  </TableCell>
                  <TableCell>
                    <Switch checked={u.enabled} aria-label={u.enabled ? 'Zablokuj' : 'Odblokuj'}
                            onCheckedChange={(enabled) => update.mutate({ id: u.id, body: { enabled } })} />
                  </TableCell>
                  <TableCell className="text-sm">{formatDate(u.createdAt)}</TableCell>
                  <TableCell className="text-right">
                    <Button size="icon-sm" variant="ghost" aria-label="Usuń"
                            onClick={() => window.confirm(`Usunąć konto ${u.email}?`) && remove.mutate(u.id)}>
                      <Trash2 className="text-destructive" />
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
      {query.data && <PaginationBar page={query.data.page} totalPages={query.data.totalPages} totalElements={query.data.totalElements} onPage={setPage} />}
    </div>
  )
}
