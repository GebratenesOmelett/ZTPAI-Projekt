import { ExternalLink } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDate, formatPrice } from '@/lib/format'
import type { Offer, TransactionType } from '@/lib/types'

/** Wszystkie oferty tej samej nieruchomości z różnych portali. */
export function OffersTable({ offers, transaction }: { offers: Offer[]; transaction: TransactionType }) {
  const minActive = Math.min(...offers.filter((o) => o.active).map((o) => o.price))
  return (
    <div className="overflow-x-auto">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Portal</TableHead>
            <TableHead>Cena</TableHead>
            <TableHead className="hidden md:table-cell">Sprzedający</TableHead>
            <TableHead className="hidden sm:table-cell">Widziana</TableHead>
            <TableHead>Status</TableHead>
            <TableHead />
          </TableRow>
        </TableHeader>
        <TableBody>
          {offers.map((o) => (
            <TableRow key={o.id} className={o.active ? '' : 'opacity-60'}>
              <TableCell>
                <div className="font-medium">{o.sourceName}</div>
                <div className="max-w-56 truncate text-xs text-muted-foreground" title={o.title}>{o.title}</div>
              </TableCell>
              <TableCell className="font-semibold tabular-nums">
                {formatPrice(o.price, transaction)}
                {o.active && o.price === minActive && offers.length > 1 && <Badge className="ml-2 bg-emerald-600 text-white">najtaniej</Badge>}
              </TableCell>
              <TableCell className="hidden md:table-cell">{o.privateSeller ? 'Osoba prywatna' : o.agencyName ?? 'Biuro'}</TableCell>
              <TableCell className="hidden text-xs sm:table-cell">{formatDate(o.firstSeenAt)} – {formatDate(o.lastSeenAt)}</TableCell>
              <TableCell>{o.active ? <Badge variant="outline">aktywna</Badge> : <Badge variant="secondary">nieaktywna</Badge>}</TableCell>
              <TableCell className="text-right">
                {o.url && (
                  <Button variant="ghost" size="sm" asChild>
                    <a href={o.url} target="_blank" rel="noopener noreferrer nofollow">Przejdź <ExternalLink /></a>
                  </Button>
                )}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}
