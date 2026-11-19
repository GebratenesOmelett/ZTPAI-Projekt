import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from '@/lib/auth'
import { RegisterForm } from './RegisterPage'

function renderForm(onRegistered = vi.fn()) {
  const client = new QueryClient()
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <AuthProvider>
          <RegisterForm onRegistered={onRegistered} />
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
  return onRegistered
}

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('formularz rejestracji', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn(async (url: string) => {
      if (url.includes('/auth/refresh')) return jsonResponse(401, { status: 401, errorCode: 'INVALID_REFRESH_TOKEN' })
      return jsonResponse(409, { status: 409, errorCode: 'EMAIL_TAKEN', detail: 'Konto z tym adresem e-mail już istnieje' })
    })
    vi.stubGlobal('fetch', fetchMock)
  })
  afterEach(() => vi.unstubAllGlobals())

  it('waliduje pola po stronie klienta (Zod) bez wysyłania żądania', async () => {
    renderForm()
    await userEvent.type(screen.getByLabelText('E-mail'), 'zly-email')
    await userEvent.type(screen.getByLabelText('Hasło'), 'krotkie')
    await userEvent.type(screen.getByLabelText('Powtórz hasło'), 'inne')
    await userEvent.click(screen.getByRole('button', { name: 'Załóż konto' }))

    expect(await screen.findByText('Minimum 2 znaki')).toBeInTheDocument()
    expect(screen.getByText('Niepoprawny adres e-mail')).toBeInTheDocument()
    expect(screen.getByText('Hasło musi mieć co najmniej 8 znaków')).toBeInTheDocument()
    expect(screen.getByText('Hasła nie są identyczne')).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalledWith('/api/v1/auth/register', expect.anything())
  })

  it('pokazuje błąd 409 z API przy polu e-mail', async () => {
    const onRegistered = renderForm()
    await userEvent.type(screen.getByLabelText('Imię lub nazwa'), 'Anna')
    await userEvent.type(screen.getByLabelText('E-mail'), 'anna@example.com')
    await userEvent.type(screen.getByLabelText('Hasło'), 'Haslo12345')
    await userEvent.type(screen.getByLabelText('Powtórz hasło'), 'Haslo12345')
    await userEvent.click(screen.getByRole('button', { name: 'Załóż konto' }))

    expect(await screen.findByText('Konto z tym adresem e-mail już istnieje')).toBeInTheDocument()
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/v1/auth/register', expect.objectContaining({ method: 'POST' })))
    expect(onRegistered).not.toHaveBeenCalled()
  })
})
