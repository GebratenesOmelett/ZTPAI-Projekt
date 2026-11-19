import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs'
import { useQueryClient } from '@tanstack/react-query'
import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { refreshAccessToken, session } from './api'
import { useAuth } from './auth'
import { queryKeys } from './queries'
import type { AppNotification } from './types'

type Handler = (body: unknown) => void

interface LiveContextValue {
  connected: boolean
  subscribe: (destination: string, handler: Handler) => () => void
}

const LiveContext = createContext<LiveContextValue>({ connected: false, subscribe: () => () => {} })

function brokerUrl(): string {
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
  return `${protocol}://${window.location.host}/ws`
}

/**
 * Połączenie WebSocket/STOMP dla zalogowanego użytkownika: powiadomienia o alertach
 * (toast + odświeżenie dzwonka) oraz subskrypcje na żądanie (np. status importów w panelu admina).
 */
export function LiveProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const clientRef = useRef<Client | null>(null)
  const handlersRef = useRef(new Map<string, Set<Handler>>())
  const subscriptionsRef = useRef(new Map<string, StompSubscription>())
  const [connected, setConnected] = useState(false)

  const attach = useCallback((client: Client, destination: string) => {
    if (subscriptionsRef.current.has(destination)) return
    const sub = client.subscribe(destination, (message: IMessage) => {
      let body: unknown = message.body
      try {
        body = JSON.parse(message.body)
      } catch {
        /* treść tekstowa */
      }
      handlersRef.current.get(destination)?.forEach((h) => h(body))
    })
    subscriptionsRef.current.set(destination, sub)
  }, [])

  useEffect(() => {
    if (!user) return
    const client = new Client({
      brokerURL: brokerUrl(),
      reconnectDelay: 5000,
      heartbeatIncoming: 0,
      heartbeatOutgoing: 20000,
      beforeConnect: async () => {
        const token = session.token ?? (await refreshAccessToken())
        client.connectHeaders = { Authorization: `Bearer ${token ?? ''}` }
      },
      onConnect: () => {
        setConnected(true)
        subscriptionsRef.current.clear()
        handlersRef.current.forEach((_, destination) => attach(client, destination))
      },
      onWebSocketClose: () => setConnected(false),
      onStompError: () => setConnected(false),
    })
    const notificationHandler: Handler = (body) => {
      const n = body as AppNotification
      queryClient.invalidateQueries({ queryKey: queryKeys.notifications })
      toast.info(n.title, {
        description: 'Nowe powiadomienie z alertu',
        action: { label: 'Pokaż', onClick: () => navigate('/powiadomienia') },
      })
    }
    const destination = '/user/queue/notifications'
    const set = handlersRef.current.get(destination) ?? new Set<Handler>()
    set.add(notificationHandler)
    handlersRef.current.set(destination, set)
    clientRef.current = client
    client.activate()
    const subscriptions = subscriptionsRef.current
    return () => {
      set.delete(notificationHandler)
      subscriptions.clear()
      clientRef.current = null
      setConnected(false)
      void client.deactivate()
    }
  }, [user, attach, queryClient, navigate])

  const subscribe = useCallback((destination: string, handler: Handler) => {
    const set = handlersRef.current.get(destination) ?? new Set<Handler>()
    set.add(handler)
    handlersRef.current.set(destination, set)
    const client = clientRef.current
    if (client?.connected) attach(client, destination)
    return () => {
      set.delete(handler)
      if (set.size === 0) {
        subscriptionsRef.current.get(destination)?.unsubscribe()
        subscriptionsRef.current.delete(destination)
        handlersRef.current.delete(destination)
      }
    }
  }, [attach])

  return <LiveContext.Provider value={{ connected, subscribe }}>{children}</LiveContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useLive(): LiveContextValue {
  return useContext(LiveContext)
}

/** Subskrypcja kanału STOMP na czas życia komponentu. */
// eslint-disable-next-line react-refresh/only-export-components
export function useLiveTopic(destination: string, handler: Handler) {
  const { subscribe, connected } = useLive()
  const handlerRef = useRef(handler)
  useEffect(() => {
    handlerRef.current = handler
  }, [handler])
  useEffect(() => subscribe(destination, (body) => handlerRef.current(body)), [destination, subscribe, connected])
  return connected
}
