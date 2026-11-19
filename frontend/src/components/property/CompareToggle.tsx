import { GitCompare } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { compareStore, MAX_COMPARE, useCompare } from '@/lib/compare'

export function CompareToggle({ id, size = 'sm' }: { id: number; size?: 'sm' | 'default' }) {
  const ids = useCompare()
  const active = ids.includes(id)
  return (
    <Button variant={active ? 'secondary' : 'outline'} size={size} aria-pressed={active}
            onClick={(e) => {
              e.preventDefault()
              if (!compareStore.toggle(id)) toast.warning(`Możesz porównać maksymalnie ${MAX_COMPARE} nieruchomości`)
            }}>
      <GitCompare /> {active ? 'W porównaniu' : 'Porównaj'}
    </Button>
  )
}
