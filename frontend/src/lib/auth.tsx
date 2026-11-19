import { useQueryClient } from '@tanstack/react-query'
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, refreshAccessToken, session } from './api'
import type { AuthResponse, User } from './types'

interface AuthContextValue {
  user: User | null
  initializing: boolean
  isAdmin: boolean
  login: (email: string, password: string) => Promise<User>
  register: (email: string, password: string, displayName: string) => Promise<User>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(session.user)
  const [initializing, setInitializing] = useState(true)
  const queryClient = useQueryClient()

  useEffect(() => session.subscribe(setUser), [])

  // Przy starcie próbujemy odtworzyć sesję z ciasteczka refresh tokenu.
  useEffect(() => {
    refreshAccessToken().finally(() => setInitializing(false))
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    const auth = await api<AuthResponse>('/api/v1/auth/login', { method: 'POST', body: { email, password } })
    session.set(auth)
    queryClient.invalidateQueries()
    return auth.user
  }, [queryClient])

  const register = useCallback(async (email: string, password: string, displayName: string) => {
    const auth = await api<AuthResponse>('/api/v1/auth/register', { method: 'POST', body: { email, password, displayName } })
    session.set(auth)
    return auth.user
  }, [])

  const logout = useCallback(async () => {
    try {
      await api<void>('/api/v1/auth/logout', { method: 'POST' })
    } finally {
      session.set(null)
      queryClient.clear()
    }
  }, [queryClient])

  const value = useMemo<AuthContextValue>(() => ({
    user,
    initializing,
    isAdmin: user?.role === 'ADMIN',
    login,
    register,
    logout,
  }), [user, initializing, login, register, logout])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
