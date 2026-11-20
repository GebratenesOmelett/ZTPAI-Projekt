import { Save } from 'lucide-react'
import { lazy, Suspense, useState } from 'react'
import { Link } from 'react-router'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { useAuth } from '@/lib/auth'
import type { Criteria } from '@/lib/criteria'
import type { AlertMode, SavedSearch } from '@/lib/types'

// formularz (zod + react-hook-form) jest pobierany dopiero przy pierwszym otwarciu okna
const SaveSearchForm = lazy(() => import('./SaveSearchForm'))

export interface EditingSavedSearch {
  id: number
  name: string
  alertEnabled: boolean
  alertMode: AlertMode
}

// eslint-disable-next-line react-refresh/only-export-components
export function SaveSearchHeader({ editing }: { editing?: EditingSavedSearch | null }) {
  return (
    <DialogHeader>
      <DialogTitle>{editing ? 'Aktualizuj zapisane wyszukiwanie' : 'Zapisz wyszukiwanie'}</DialogTitle>
      <DialogDescription>Zapisujemy bieżące filtry. Możesz włączyć alert o nowych ogłoszeniach i obniżkach cen.</DialogDescription>
    </DialogHeader>
  )
}

/** Zapis bieżących kryteriów jako wyszukiwania z opcjonalnym alertem (lub nadpisanie edytowanego). */
export function SaveSearchDialog({ criteria, editing, onSaved }: {
  criteria: Criteria
  editing?: EditingSavedSearch | null
  onSaved?: (saved: SavedSearch) => void
}) {
  const { user } = useAuth()
  const [open, setOpen] = useState(false)

  if (!user) {
    return (
      <Button variant="outline" size="sm" asChild>
        <Link to="/logowanie"><Save /> Zaloguj się, aby zapisać</Link>
      </Button>
    )
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" variant={editing ? 'default' : 'outline'}>
          <Save /> {editing ? `Zapisz zmiany w „${editing.name}”` : 'Zapisz wyszukiwanie'}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <Suspense fallback={<SaveSearchHeader editing={editing} />}>
          <SaveSearchForm criteria={criteria} editing={editing} onCancel={() => setOpen(false)}
                          onSaved={(saved) => { setOpen(false); onSaved?.(saved) }} />
        </Suspense>
      </DialogContent>
    </Dialog>
  )
}
