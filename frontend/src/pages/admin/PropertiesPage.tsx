import { useQuery } from '@tanstack/react-query'
import { Pencil, Search } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'
import { PaginationBar } from '@/components/common/PaginationBar'
import { ErrorState, LoadingState } from '@/components/common/States'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { api } from '@/lib/api'
import { formatArea, formatDateTime, formatPrice, PROPERTY_TYPE_LABELS } from '@/lib/format'
import { useCities } from '@/lib/queries'
import type { AdminProperty, Page } from '@/lib/types'

const ALL = 'ALL'

export default function AdminPropertiesPage() {
  const [page, setPage] = useState(0)
  const [qInput, setQInput] = useState('')
  const [q, setQ] = useState('')
  const [status, setStatus] = useState(ALL)
  const [cityId, setCityId] = useState(ALL)
  const cities = useCities()
  const query = useQuery({
    queryKey: ['admin', 'properties', page, q, status, cityId],
    queryFn: () => api<Page<AdminProperty>>('/api/v1/admin/properties', {
      query: { page, size: 20, q: q || undefined, status: status === ALL ? undefined : status, cityId: cityId === ALL ? undefined : cityId },
    }),
  })

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <h2 className="text-lg font-semibold">Nieruchomości</h2>
        <form className="ml-auto flex gap-2" onSubmit={(e) => { e.preventDefault(); setQ(qInput.trim()); setPage(0) }}>
          <Input className="h-8 w-56" placeholder="Tytuł lub ulica" value={qInput} onChange={(e) => setQInput(e.target.value)} aria-label="Szukaj" />
          <Button size="sm" type="submit"><Search /></Button>
        </form>
        <Select value={cityId} onValueChange={(v) => { setCityId(v); setPage(0) }}>
          <SelectTrigger size="sm" className="w-36"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>Wszystkie miasta</SelectItem>
            {cities.data?.map((c) => <SelectItem key={c.id} value={String(c.id)}>{c.name}</SelectItem>)}
          </SelectContent>
        </Select>
        <Select value={status} onValueChange={(v) => { setStatus(v); setPage(0) }}>
          <SelectTrigger size="sm" className="w-36"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>Każdy status</SelectItem>
            <SelectItem value="ACTIVE">Aktywne</SelectItem>
            <SelectItem value="ARCHIVED">Archiwalne</SelectItem>
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
                <TableHead>#</TableHead>
                <TableHead>Tytuł</TableHead>
                <TableHead>Lokalizacja</TableHead>
                <TableHead>Typ</TableHead>
                <TableHead className="text-right">Metraż</TableHead>
                <TableHead className="text-right">Cena</TableHead>
                <TableHead>Portale</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Widziana</TableHead>
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {query.data.content.map((p) => (
                <TableRow key={p.id}>
                  <TableCell className="tabular-nums">{p.id}</TableCell>
                  <TableCell className="max-w-72 truncate" title={p.title}>{p.title}</TableCell>
                  <TableCell className="text-sm">{p.city}{p.district ? `, ${p.district}` : ''}</TableCell>
                  <TableCell className="text-sm">{PROPERTY_TYPE_LABELS[p.propertyType]}</TableCell>
                  <TableCell className="text-right tabular-nums">{formatArea(p.area)}</TableCell>
                  <TableCell className="text-right tabular-nums">{formatPrice(p.price, p.transactionType)}</TableCell>
                  <TableCell>{p.sourceCount}</TableCell>
                  <TableCell>{p.status === 'ACTIVE' ? <Badge variant="outline">aktywna</Badge> : <Badge variant="secondary">archiwalna</Badge>}</TableCell>
                  <TableCell className="text-xs">{formatDateTime(p.lastSeenAt)}</TableCell>
                  <TableCell><Button size="icon-sm" variant="outline" asChild aria-label="Edytuj"><Link to={`/admin/nieruchomosci/${p.id}`}><Pencil /></Link></Button></TableCell>
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
