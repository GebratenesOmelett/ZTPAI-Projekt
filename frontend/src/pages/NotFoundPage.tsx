import { Link } from 'react-router'
import { EmptyState } from '@/components/common/States'
import { Button } from '@/components/ui/button'

export default function NotFoundPage() {
  return (
    <EmptyState title="Nie znaleziono strony (404)" description="Sprawdź adres lub wróć do wyszukiwarki."
                action={<Button asChild><Link to="/">Strona główna</Link></Button>} />
  )
}
