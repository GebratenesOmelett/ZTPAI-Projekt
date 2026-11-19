import { GitCompare, Heart, LogOut, Menu, Radar, Search, Settings, User as UserIcon } from 'lucide-react'
import { useState } from 'react'
import { Link, NavLink, useNavigate } from 'react-router'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from '@/components/ui/sheet'
import { useAuth } from '@/lib/auth'
import { useCompare } from '@/lib/compare'
import { cn } from '@/lib/utils'
import { NotificationBell } from './NotificationBell'

function NavItem({ to, children, onClick }: { to: string; children: React.ReactNode; onClick?: () => void }) {
  return (
    <NavLink
      to={to}
      end={to === '/'}
      onClick={onClick}
      className={({ isActive }) =>
        cn('flex items-center gap-1.5 rounded-md px-3 py-1.5 text-sm font-medium transition-colors hover:bg-muted',
          isActive ? 'bg-muted text-foreground' : 'text-muted-foreground')
      }
    >
      {children}
    </NavLink>
  )
}

export function Header() {
  const { user, isAdmin, logout } = useAuth()
  const compare = useCompare()
  const navigate = useNavigate()
  const [mobileOpen, setMobileOpen] = useState(false)

  const handleLogout = async () => {
    await logout()
    toast.success('Wylogowano')
    navigate('/')
  }

  const links = (close?: () => void) => (
    <>
      <NavItem to="/" onClick={close}><Search className="size-4" />Szukaj</NavItem>
      <NavItem to="/porownaj" onClick={close}>
        <GitCompare className="size-4" />Porównaj
        {compare.length > 0 && <Badge variant="secondary" className="ml-1">{compare.length}</Badge>}
      </NavItem>
      {user && <NavItem to="/moje-wyszukiwania" onClick={close}><Search className="size-4" />Moje wyszukiwania</NavItem>}
      {user && <NavItem to="/ulubione" onClick={close}><Heart className="size-4" />Ulubione</NavItem>}
      {isAdmin && <NavItem to="/admin" onClick={close}><Settings className="size-4" />Panel admina</NavItem>}
    </>
  )

  return (
    <header className="sticky top-0 z-[1100] border-b bg-background/95 backdrop-blur">
      <div className="mx-auto flex h-14 max-w-[1600px] items-center gap-3 px-3 sm:px-6">
        <Sheet open={mobileOpen} onOpenChange={setMobileOpen}>
          <SheetTrigger asChild>
            <Button variant="ghost" size="icon" className="md:hidden" aria-label="Menu">
              <Menu />
            </Button>
          </SheetTrigger>
          <SheetContent side="left" className="w-72">
            <SheetHeader><SheetTitle>DomRadar</SheetTitle></SheetHeader>
            <nav className="flex flex-col gap-1 px-4">{links(() => setMobileOpen(false))}</nav>
          </SheetContent>
        </Sheet>
        <Link to="/" className="flex items-center gap-2 font-semibold tracking-tight">
          <Radar className="size-6 text-teal-600" />
          <span className="text-lg">Dom<span className="text-teal-600">Radar</span></span>
        </Link>
        <nav className="ml-4 hidden items-center gap-1 md:flex">{links()}</nav>
        <div className="ml-auto flex items-center gap-2">
          {user ? (
            <>
              <NotificationBell />
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button variant="outline" size="sm" className="gap-2">
                    <UserIcon className="size-4" />
                    <span className="hidden max-w-32 truncate sm:inline">{user.displayName}</span>
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" className="w-56">
                  <DropdownMenuLabel>
                    <div className="truncate">{user.email}</div>
                    <div className="text-xs font-normal text-muted-foreground">{user.role === 'ADMIN' ? 'Administrator' : 'Użytkownik'}</div>
                  </DropdownMenuLabel>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem onSelect={() => navigate('/moje-wyszukiwania')}>Moje wyszukiwania</DropdownMenuItem>
                  <DropdownMenuItem onSelect={() => navigate('/ulubione')}>Ulubione</DropdownMenuItem>
                  <DropdownMenuItem onSelect={() => navigate('/powiadomienia')}>Powiadomienia</DropdownMenuItem>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem onSelect={handleLogout}><LogOut className="size-4" />Wyloguj</DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </>
          ) : (
            <>
              <Button variant="ghost" size="sm" asChild><Link to="/logowanie">Zaloguj</Link></Button>
              <Button size="sm" asChild><Link to="/rejestracja">Załóż konto</Link></Button>
            </>
          )}
        </div>
      </div>
    </header>
  )
}
