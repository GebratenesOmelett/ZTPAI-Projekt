import type { ReactNode } from 'react'
import { Label } from '@/components/ui/label'

export function FormField({ id, label, error, children, hint }: {
  id: string
  label: string
  error?: string
  hint?: string
  children: ReactNode
}) {
  return (
    <div className="space-y-1.5">
      <Label htmlFor={id}>{label}</Label>
      {children}
      {hint && !error && <p className="text-xs text-muted-foreground">{hint}</p>}
      {error && <p id={`${id}-error`} className="text-sm text-destructive" role="alert">{error}</p>}
    </div>
  )
}
