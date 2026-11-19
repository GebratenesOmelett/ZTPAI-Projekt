import { AlertTriangle, Inbox, Loader2 } from 'lucide-react'
import type { ReactNode } from 'react'
import { Button } from '@/components/ui/button'
import { errorMessage } from '@/lib/api'

export function LoadingState({ label = 'Ładowanie…' }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-2 py-16 text-muted-foreground" role="status">
      <Loader2 className="size-5 animate-spin" />
      <span>{label}</span>
    </div>
  )
}

export function EmptyState({ title, description, action }: { title: string; description?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 rounded-xl border border-dashed py-14 text-center">
      <Inbox className="size-10 text-muted-foreground" />
      <div>
        <p className="font-medium">{title}</p>
        {description && <p className="mt-1 text-sm text-muted-foreground">{description}</p>}
      </div>
      {action}
    </div>
  )
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 rounded-xl border border-destructive/30 bg-destructive/5 py-12 text-center" role="alert">
      <AlertTriangle className="size-9 text-destructive" />
      <div>
        <p className="font-medium">Nie udało się pobrać danych</p>
        <p className="mt-1 text-sm text-muted-foreground">{errorMessage(error)}</p>
      </div>
      {onRetry && (
        <Button variant="outline" onClick={onRetry}>
          Spróbuj ponownie
        </Button>
      )}
    </div>
  )
}
