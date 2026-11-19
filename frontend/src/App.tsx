import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes } from 'react-router'
import { LoadingState } from './components/common/States'
import { ProtectedRoute } from './components/common/ProtectedRoute'
import { AppLayout } from './components/layout/AppLayout'
import { LiveProvider } from './lib/live'
import SearchPage from './pages/SearchPage'

const PropertyPage = lazy(() => import('./pages/PropertyPage'))
const ComparePage = lazy(() => import('./pages/ComparePage'))
const LoginPage = lazy(() => import('./pages/LoginPage'))
const RegisterPage = lazy(() => import('./pages/RegisterPage'))
const SavedSearchesPage = lazy(() => import('./pages/SavedSearchesPage'))
const FavoritesPage = lazy(() => import('./pages/FavoritesPage'))
const NotificationsPage = lazy(() => import('./pages/NotificationsPage'))
const NotFoundPage = lazy(() => import('./pages/NotFoundPage'))
const AdminLayout = lazy(() => import('./pages/admin/AdminLayout'))
const SourcesPage = lazy(() => import('./pages/admin/SourcesPage'))
const ImportJobsPage = lazy(() => import('./pages/admin/ImportJobsPage'))
const AdminPropertiesPage = lazy(() => import('./pages/admin/PropertiesPage'))
const AdminPropertyEditPage = lazy(() => import('./pages/admin/PropertyEditPage'))
const UsersPage = lazy(() => import('./pages/admin/UsersPage'))

export default function App() {
  return (
    <LiveProvider>
      <Suspense fallback={<LoadingState />}>
        <Routes>
          <Route element={<AppLayout />}>
            <Route index element={<SearchPage />} />
            <Route path="nieruchomosci/:id" element={<PropertyPage />} />
            <Route path="porownaj" element={<ComparePage />} />
            <Route path="logowanie" element={<LoginPage />} />
            <Route path="rejestracja" element={<RegisterPage />} />
            <Route element={<ProtectedRoute />}>
              <Route path="moje-wyszukiwania" element={<SavedSearchesPage />} />
              <Route path="ulubione" element={<FavoritesPage />} />
              <Route path="powiadomienia" element={<NotificationsPage />} />
            </Route>
            <Route path="admin" element={<ProtectedRoute role="ADMIN" />}>
              <Route element={<AdminLayout />}>
                <Route index element={<Navigate to="zrodla" replace />} />
                <Route path="zrodla" element={<SourcesPage />} />
                <Route path="importy" element={<ImportJobsPage />} />
                <Route path="nieruchomosci" element={<AdminPropertiesPage />} />
                <Route path="nieruchomosci/:id" element={<AdminPropertyEditPage />} />
                <Route path="uzytkownicy" element={<UsersPage />} />
              </Route>
            </Route>
            <Route path="*" element={<NotFoundPage />} />
          </Route>
        </Routes>
      </Suspense>
    </LiveProvider>
  )
}
