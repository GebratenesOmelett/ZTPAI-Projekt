import { useSyncExternalStore } from 'react'

export const MAX_COMPARE = 4
const STORAGE_KEY = 'domradar.compare'

type Listener = () => void
const listeners = new Set<Listener>()

function read(): number[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    const parsed = raw ? (JSON.parse(raw) as unknown) : []
    return Array.isArray(parsed) ? parsed.filter((n): n is number => typeof n === 'number').slice(0, MAX_COMPARE) : []
  } catch {
    return []
  }
}

let snapshot = read()

function write(ids: number[]) {
  snapshot = ids
  localStorage.setItem(STORAGE_KEY, JSON.stringify(ids))
  listeners.forEach((l) => l())
}

/** Lista nieruchomości do porównania (max 4), trzymana w localStorage. */
export const compareStore = {
  get: () => snapshot,
  /** @returns false, jeśli osiągnięto limit */
  add(id: number): boolean {
    if (snapshot.includes(id)) return true
    if (snapshot.length >= MAX_COMPARE) return false
    write([...snapshot, id])
    return true
  },
  remove(id: number) {
    write(snapshot.filter((x) => x !== id))
  },
  toggle(id: number): boolean {
    if (snapshot.includes(id)) {
      this.remove(id)
      return true
    }
    return this.add(id)
  },
  clear() {
    write([])
  },
  subscribe(listener: Listener) {
    listeners.add(listener)
    return () => {
      listeners.delete(listener)
    }
  },
}

export function useCompare(): number[] {
  return useSyncExternalStore(compareStore.subscribe, compareStore.get, compareStore.get)
}
