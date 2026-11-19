import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { BellRing, Save } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { z } from 'zod'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Switch } from '@/components/ui/switch'
import { api, ApiError, errorMessage } from '@/lib/api'
import { useAuth } from '@/lib/auth'
import { cleanCriteria, type Criteria } from '@/lib/criteria'
import { applyApiErrors } from '@/lib/forms'
import { queryKeys } from '@/lib/queries'
import type { AlertMode, SavedSearch } from '@/lib/types'

const schema = z.object({
  name: z.string().trim().min(1, 'Podaj nazwę').max(100, 'Maksymalnie 100 znaków'),
  alertEnabled: z.boolean(),
  alertMode: z.enum(['INSTANT', 'DAILY']),
})
type FormValues = z.infer<typeof schema>

export interface EditingSavedSearch {
  id: number
  name: string
  alertEnabled: boolean
  alertMode: AlertMode
}

/** Zapis bieżących kryteriów jako wyszukiwania z opcjonalnym alertem (lub nadpisanie edytowanego). */
export function SaveSearchDialog({ criteria, editing, onSaved }: {
  criteria: Criteria
  editing?: EditingSavedSearch | null
  onSaved?: (saved: SavedSearch) => void
}) {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { name: editing?.name ?? '', alertEnabled: editing?.alertEnabled ?? false, alertMode: editing?.alertMode ?? 'DAILY' },
  })
  useEffect(() => {
    if (open) form.reset({ name: editing?.name ?? '', alertEnabled: editing?.alertEnabled ?? false, alertMode: editing?.alertMode ?? 'DAILY' })
  }, [open, editing, form])

  const mutation = useMutation({
    mutationFn: (values: FormValues) => {
      const body = { ...values, criteria: cleanCriteria(criteria) }
      return editing
        ? api<SavedSearch>(`/api/v1/me/saved-searches/${editing.id}`, { method: 'PUT', body })
        : api<SavedSearch>('/api/v1/me/saved-searches', { method: 'POST', body })
    },
    onSuccess: (saved) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.savedSearches })
      toast.success(editing ? 'Zapisano zmiany wyszukiwania' : 'Wyszukiwanie zapisane', {
        description: saved.alertEnabled ? `Alert ${saved.alertMode === 'INSTANT' ? 'natychmiastowy' : 'dzienny'} włączony` : undefined,
      })
      setOpen(false)
      onSaved?.(saved)
    },
    onError: (error) => {
      if (!applyApiErrors(error, form.setError, ['name', 'alertEnabled', 'alertMode'])) {
        toast.error(error instanceof ApiError && error.status === 422 ? 'Przekroczony limit' : 'Nie udało się zapisać', {
          description: errorMessage(error),
        })
      }
    },
  })

  if (!user) {
    return (
      <Button variant="outline" size="sm" asChild>
        <Link to="/logowanie"><Save /> Zaloguj się, aby zapisać</Link>
      </Button>
    )
  }

  const alertEnabled = form.watch('alertEnabled')

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" variant={editing ? 'default' : 'outline'}>
          <Save /> {editing ? `Zapisz zmiany w „${editing.name}”` : 'Zapisz wyszukiwanie'}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <form onSubmit={form.handleSubmit((v) => mutation.mutate(v))} noValidate>
          <DialogHeader>
            <DialogTitle>{editing ? 'Aktualizuj zapisane wyszukiwanie' : 'Zapisz wyszukiwanie'}</DialogTitle>
            <DialogDescription>Zapisujemy bieżące filtry. Możesz włączyć alert o nowych ogłoszeniach i obniżkach cen.</DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-4">
            <div className="space-y-1.5">
              <Label htmlFor="ss-name">Nazwa</Label>
              <Input id="ss-name" placeholder="np. Mokotów 3 pokoje" aria-invalid={!!form.formState.errors.name} {...form.register('name')} />
              {form.formState.errors.name && <p className="text-sm text-destructive">{form.formState.errors.name.message}</p>}
            </div>
            <div className="flex items-center justify-between rounded-lg border p-3">
              <div className="flex items-center gap-2">
                <BellRing className="size-4 text-teal-600" />
                <div>
                  <Label htmlFor="ss-alert">Alert o nowych ogłoszeniach</Label>
                  <p className="text-xs text-muted-foreground">E-mail + powiadomienie w aplikacji</p>
                </div>
              </div>
              <Controller control={form.control} name="alertEnabled"
                          render={({ field }) => <Switch id="ss-alert" checked={field.value} onCheckedChange={field.onChange} />} />
            </div>
            {alertEnabled && (
              <div className="space-y-1.5">
                <Label>Częstotliwość</Label>
                <Controller control={form.control} name="alertMode" render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger className="w-full"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="INSTANT">Natychmiast po imporcie (max 3)</SelectItem>
                      <SelectItem value="DAILY">Dzienny przegląd o 7:00</SelectItem>
                    </SelectContent>
                  </Select>
                )} />
              </div>
            )}
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>Anuluj</Button>
            <Button type="submit" disabled={mutation.isPending}>{mutation.isPending ? 'Zapisywanie…' : 'Zapisz'}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
