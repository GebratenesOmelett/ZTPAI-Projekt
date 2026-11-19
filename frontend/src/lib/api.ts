import type { AuthResponse, FieldError, ProblemDetail, User } from './types'

/** Błąd API zbudowany z odpowiedzi RFC 7807 (ProblemDetail). */
export class ApiError extends Error {
  readonly status: number
  readonly errorCode?: string
  readonly fieldErrors: FieldError[]
  readonly details?: Record<string, unknown>

  constructor(problem: ProblemDetail) {
    super(problem.detail || problem.title || `Błąd ${problem.status}`)
    this.name = 'ApiError'
    this.status = problem.status
    this.errorCode = problem.errorCode
    this.fieldErrors = problem.fieldErrors ?? []
    this.details = problem.details
  }
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.fieldErrors.length > 0 && error.status === 400) {
      return `${error.message}: ${error.fieldErrors.map((f) => `${f.field} – ${f.message}`).join('; ')}`
    }
    return error.message
  }
  if (error instanceof TypeError) {
    return 'Brak połączenia z serwerem'
  }
  return error instanceof Error ? error.message : 'Nieznany błąd'
}

// ---------------------------------------------------------------------------
// Sesja: access token trzymany wyłącznie w pamięci (nie w localStorage),
// refresh token w ciasteczku httpOnly (niedostępny dla JS).
// ---------------------------------------------------------------------------
type SessionListener = (user: User | null) => void

let accessToken: string | null = null
let currentUser: User | null = null
const listeners = new Set<SessionListener>()

export const session = {
  get token() {
    return accessToken
  },
  get user() {
    return currentUser
  },
  set(auth: AuthResponse | null) {
    accessToken = auth?.accessToken ?? null
    currentUser = auth?.user ?? null
    listeners.forEach((l) => l(currentUser))
  },
  subscribe(listener: SessionListener) {
    listeners.add(listener)
    return () => {
      listeners.delete(listener)
    }
  },
}

let refreshInFlight: Promise<string | null> | null = null

/** Odświeża access token na podstawie ciasteczka; równoległe wywołania współdzielą jedno żądanie. */
export function refreshAccessToken(): Promise<string | null> {
  if (!refreshInFlight) {
    refreshInFlight = fetch('/api/v1/auth/refresh', { method: 'POST', credentials: 'include' })
      .then(async (res) => {
        if (!res.ok) {
          session.set(null)
          return null
        }
        const auth = (await res.json()) as AuthResponse
        session.set(auth)
        return auth.accessToken
      })
      .catch(() => {
        session.set(null)
        return null
      })
      .finally(() => {
        refreshInFlight = null
      })
  }
  return refreshInFlight
}

type Query = Record<string, string | number | boolean | undefined | null | (string | number)[]>

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  query?: Query | URLSearchParams
  signal?: AbortSignal
}

function buildUrl(path: string, query?: Query | URLSearchParams): string {
  if (!query) return path
  const params = query instanceof URLSearchParams ? query : new URLSearchParams()
  if (!(query instanceof URLSearchParams)) {
    for (const [key, value] of Object.entries(query)) {
      if (value === undefined || value === null || value === '') continue
      if (Array.isArray(value)) value.forEach((v) => params.append(key, String(v)))
      else params.set(key, String(value))
    }
  }
  const qs = params.toString()
  return qs ? `${path}?${qs}` : path
}

async function toApiError(res: Response): Promise<ApiError> {
  try {
    const problem = (await res.json()) as ProblemDetail
    return new ApiError({ ...problem, status: problem.status ?? res.status })
  } catch {
    return new ApiError({ status: res.status, detail: res.statusText || `Błąd ${res.status}` })
  }
}

/**
 * Wywołanie REST API. Przy 401 na endpointach innych niż /auth/* próbuje jednorazowo odświeżyć token
 * (interceptor) i powtarza żądanie.
 */
export async function api<T>(path: string, options: RequestOptions = {}, retried = false): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`

  const res = await fetch(buildUrl(path, options.query), {
    method: options.method ?? 'GET',
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    credentials: 'include',
    signal: options.signal,
  })

  if (res.status === 401 && !retried && !path.startsWith('/api/v1/auth/')) {
    const token = await refreshAccessToken()
    if (token) return api<T>(path, options, true)
  }
  if (!res.ok) throw await toApiError(res)
  if (res.status === 204) return undefined as T
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}
