import { ChevronDown } from 'lucide-react'
import { useEffect, useState, type ReactNode } from 'react'
import { Checkbox } from '@/components/ui/checkbox'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { cn } from '@/lib/utils'

/** Pole liczbowe z wartością undefined dla pustego tekstu. */
export function NumberField({ value, onChange, placeholder, min, step, id, className, ariaLabel }: {
  value?: number
  onChange: (value?: number) => void
  placeholder?: string
  min?: number
  step?: number
  id?: string
  className?: string
  ariaLabel?: string
}) {
  const [text, setText] = useState(value === undefined ? '' : String(value))
  useEffect(() => {
    setText(value === undefined ? '' : String(value))
  }, [value])
  return (
    <Input
      id={id}
      aria-label={ariaLabel}
      inputMode="decimal"
      type="number"
      min={min}
      step={step}
      className={cn('h-8', className)}
      placeholder={placeholder}
      value={text}
      onChange={(e) => {
        setText(e.target.value)
        const n = e.target.value === '' ? undefined : Number(e.target.value)
        onChange(n !== undefined && Number.isFinite(n) ? n : undefined)
      }}
    />
  )
}

export function RangeField({ label, min, max, onMin, onMax, unit, step }: {
  label: string
  min?: number
  max?: number
  onMin: (v?: number) => void
  onMax: (v?: number) => void
  unit?: string
  step?: number
}) {
  return (
    <div className="space-y-1">
      <Label className="text-xs text-muted-foreground">{label}{unit ? ` (${unit})` : ''}</Label>
      <div className="flex items-center gap-2">
        <NumberField value={min} onChange={onMin} placeholder="od" step={step} min={0} ariaLabel={`${label} od`} />
        <span className="text-muted-foreground">–</span>
        <NumberField value={max} onChange={onMax} placeholder="do" step={step} min={0} ariaLabel={`${label} do`} />
      </div>
    </div>
  )
}

export function CheckList<T extends string | number>({ options, selected, onChange, maxHeight }: {
  options: { value: T; label: string; hint?: ReactNode }[]
  selected: T[]
  onChange: (values: T[]) => void
  maxHeight?: boolean
}) {
  const toggle = (value: T, checked: boolean) =>
    onChange(checked ? [...selected, value] : selected.filter((v) => v !== value))
  return (
    <div className={cn('space-y-1.5', maxHeight && 'max-h-48 overflow-y-auto pr-1')}>
      {options.map((o) => {
        const id = `chk-${String(o.value)}-${o.label}`
        return (
          <div key={String(o.value)} className="flex items-center gap-2">
            <Checkbox id={id} checked={selected.includes(o.value)} onCheckedChange={(c) => toggle(o.value, c === true)} />
            <Label htmlFor={id} className="flex-1 cursor-pointer text-sm font-normal">{o.label}</Label>
            {o.hint}
          </div>
        )
      })}
    </div>
  )
}

export function CheckboxField({ label, checked, onChange }: { label: string; checked?: boolean; onChange: (v: boolean) => void }) {
  const id = `cb-${label}`
  return (
    <div className="flex items-center gap-2">
      <Checkbox id={id} checked={checked === true} onCheckedChange={(c) => onChange(c === true)} />
      <Label htmlFor={id} className="cursor-pointer text-sm font-normal">{label}</Label>
    </div>
  )
}

export function FilterSection({ title, children, defaultOpen = false, badge }: {
  title: string
  children: ReactNode
  defaultOpen?: boolean
  badge?: number
}) {
  const [open, setOpen] = useState(defaultOpen)
  return (
    <Collapsible open={open} onOpenChange={setOpen} className="border-b py-2 last:border-0">
      <CollapsibleTrigger className="flex w-full items-center justify-between py-1 text-sm font-semibold">
        <span className="flex items-center gap-2">
          {title}
          {badge ? <span className="rounded-full bg-teal-600 px-1.5 text-[10px] text-white">{badge}</span> : null}
        </span>
        <ChevronDown className={cn('size-4 transition-transform', open && 'rotate-180')} />
      </CollapsibleTrigger>
      <CollapsibleContent className="space-y-3 pt-2">{children}</CollapsibleContent>
    </Collapsible>
  )
}
