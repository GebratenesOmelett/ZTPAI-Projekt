import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Heart } from 'lucide-react'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { api, errorMessage } from '@/lib/api'
import { useAuth } from '@/lib/auth'
import { queryKeys } from '@/lib/queries'
import type { Favorite } from '@/lib/types'

export function FavoriteButton({ propertyId, favoriteId }: { propertyId: number; favoriteId?: number }) {
  const { user } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: queryKeys.property(propertyId) })
    queryClient.invalidateQueries({ queryKey: ['favorites'] })
  }
  const add = useMutation({
    mutationFn: () => api<Favorite>('/api/v1/me/favorites', { method: 'POST', body: { propertyId } }),
    onSuccess: () => {
      toast.success('Dodano do ulubionych')
      refresh()
    },
    onError: (e) => toast.error(errorMessage(e)),
  })
  const remove = useMutation({
    mutationFn: () => api<void>(`/api/v1/me/favorites/${favoriteId}`, { method: 'DELETE' }),
    onSuccess: () => {
      toast.success('Usunięto z ulubionych')
      refresh()
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  if (!user) {
    return <Button variant="outline" onClick={() => navigate(`/logowanie?redirect=/nieruchomosci/${propertyId}`)}><Heart /> Do ulubionych</Button>
  }
  return favoriteId ? (
    <Button variant="secondary" disabled={remove.isPending} onClick={() => remove.mutate()}>
      <Heart className="fill-red-500 text-red-500" /> W ulubionych
    </Button>
  ) : (
    <Button variant="outline" disabled={add.isPending} onClick={() => add.mutate()}>
      <Heart /> Do ulubionych
    </Button>
  )
}
