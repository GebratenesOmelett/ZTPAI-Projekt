import { Navigate, Outlet, useLocation } from 'react-router'
import { useAuth } from '@/lib/auth'
import type { Role } from '@/lib/types'
import { EmptyState, LoadingState } from './States'

/** Trasa wymagająca zalogowania (i opcjonalnie roli). */
export function ProtectedRoute({ role }: { role?: Role }) {
  const { user, initializing } = useAuth()
  const location = useLocation()

  if (initializing) return <LoadingState label="Sprawdzanie sesji…" />
  if (!user) {
    return <Navigate to={`/logowanie?redirect=${encodeURIComponent(location.pathname + location.search)}`} replace />
  }
  if (role && user.role !== role) {
    return <EmptyState title="Brak uprawnień" description="Ta sekcja jest dostępna tylko dla administratorów." />
  }
  return <Outlet />
}
