import { Building2, Database, History, Users } from 'lucide-react'
import { NavLink, Outlet } from 'react-router'
import { cn } from '@/lib/utils'

const LINKS = [
  { to: '/admin/zrodla', label: 'Źródła', icon: Database },
  { to: '/admin/importy', label: 'Importy', icon: History },
  { to: '/admin/nieruchomosci', label: 'Nieruchomości', icon: Building2 },
  { to: '/admin/uzytkownicy', label: 'Użytkownicy', icon: Users },
]

export default function AdminLayout() {
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2 border-b pb-2">
        <h1 className="mr-4 text-xl font-semibold">Panel administratora</h1>
        {LINKS.map(({ to, label, icon: Icon }) => (
          <NavLink key={to} to={to}
                   className={({ isActive }) => cn('flex items-center gap-1.5 rounded-md px-3 py-1.5 text-sm font-medium',
                     isActive ? 'bg-teal-600 text-white' : 'text-muted-foreground hover:bg-muted')}>
            <Icon className="size-4" />{label}
          </NavLink>
        ))}
      </div>
      <Outlet />
    </div>
  )
}
