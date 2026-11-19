import { Outlet } from 'react-router'
import { Toaster } from '@/components/ui/sonner'
import { TooltipProvider } from '@/components/ui/tooltip'
import { Header } from './Header'

export function AppLayout() {
  return (
    <TooltipProvider>
      <div className="flex min-h-svh flex-col bg-muted/30">
        <Header />
        <main className="mx-auto w-full max-w-[1600px] flex-1 px-3 py-4 sm:px-6">
          <Outlet />
        </main>
        <footer className="border-t bg-background py-4 text-center text-xs text-muted-foreground">
          DomRadar — agregator ogłoszeń nieruchomości. Dane demonstracyjne; linki prowadzą do przykładowych adresów portali.
        </footer>
      </div>
      <Toaster richColors position="top-right" />
    </TooltipProvider>
  )
}
