import type { ImportStatus, MarketType, PropertyType, TransactionType } from './types'

const priceFormatter = new Intl.NumberFormat('pl-PL', { maximumFractionDigits: 0 })
const decimalFormatter = new Intl.NumberFormat('pl-PL', { maximumFractionDigits: 1 })
const dateFormatter = new Intl.DateTimeFormat('pl-PL', { day: '2-digit', month: '2-digit', year: 'numeric' })
const dateTimeFormatter = new Intl.DateTimeFormat('pl-PL', { dateStyle: 'short', timeStyle: 'short' })

export function formatPrice(value?: number | null, transaction?: TransactionType): string {
  if (value === undefined || value === null) return 'Cena do uzgodnienia'
  return `${priceFormatter.format(value)} zł${transaction === 'RENT' ? '/mies.' : ''}`
}

export function formatCompactPrice(value?: number | null): string {
  if (value === undefined || value === null) return '—'
  if (value >= 1_000_000) return `${decimalFormatter.format(value / 1_000_000)} mln`
  if (value >= 10_000) return `${priceFormatter.format(Math.round(value / 1000))} tys.`
  return priceFormatter.format(value)
}

export function formatPricePerM2(value?: number | null): string {
  return value === undefined || value === null ? '—' : `${priceFormatter.format(value)} zł/m²`
}

export function formatArea(value?: number | null): string {
  return value === undefined || value === null ? '—' : `${decimalFormatter.format(value)} m²`
}

export function formatDate(value?: string | null): string {
  return value ? dateFormatter.format(new Date(value)) : '—'
}

export function formatDateTime(value?: string | null): string {
  return value ? dateTimeFormatter.format(new Date(value)) : '—'
}

export function formatPercent(value?: number | null): string {
  return value === undefined || value === null ? '—' : `${decimalFormatter.format(value)}%`
}

export function formatFloor(floor?: number | null, total?: number | null): string {
  if (floor === undefined || floor === null) return '—'
  const label = floor === 0 ? 'parter' : floor < 0 ? 'suterena' : `${floor}. piętro`
  return total ? `${label} / ${total}` : label
}

/** Polska odmiana: 1 pokój, 2-4 pokoje, 5+ pokoi. */
export function plural(n: number, one: string, few: string, many: string): string {
  if (n === 1) return one
  const lastTwo = Math.abs(n) % 100
  const last = Math.abs(n) % 10
  return last >= 2 && last <= 4 && (lastTwo < 12 || lastTwo > 14) ? few : many
}

export const PROPERTY_TYPE_LABELS: Record<PropertyType, string> = {
  APARTMENT: 'Mieszkanie',
  HOUSE: 'Dom',
  PLOT: 'Działka',
  COMMERCIAL: 'Lokal użytkowy',
  ROOM: 'Pokój',
}

export const TRANSACTION_LABELS: Record<TransactionType, string> = {
  SALE: 'Sprzedaż',
  RENT: 'Wynajem',
}

export const MARKET_LABELS: Record<MarketType, string> = {
  PRIMARY: 'Pierwotny',
  SECONDARY: 'Wtórny',
}

export const IMPORT_STATUS_LABELS: Record<ImportStatus, string> = {
  QUEUED: 'W kolejce',
  RUNNING: 'W trakcie',
  SUCCESS: 'Sukces',
  FAILED: 'Błąd',
}

export function vsMedianLabel(pct?: number | null): string | null {
  if (pct === undefined || pct === null) return null
  if (pct === 0) return 'równo z medianą dzielnicy'
  return `${decimalFormatter.format(Math.abs(pct))}% ${pct < 0 ? 'poniżej' : 'powyżej'} mediany dzielnicy`
}
