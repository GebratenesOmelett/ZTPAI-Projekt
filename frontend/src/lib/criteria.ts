import type { MarketType, PropertyType, TransactionType } from './types'

export interface BoundingBox {
  south: number
  west: number
  north: number
  east: number
}

export interface GeoPolygon {
  type: 'Polygon'
  coordinates: number[][][]
}

/** Kryteria wyszukiwania — te same nazwy pól co w API (SearchCriteria). */
export interface Criteria {
  q?: string
  street?: string
  transactionType?: TransactionType
  propertyTypes?: PropertyType[]
  marketType?: MarketType
  cityIds?: number[]
  districtIds?: number[]
  priceMin?: number
  priceMax?: number
  pricePerM2Min?: number
  pricePerM2Max?: number
  areaMin?: number
  areaMax?: number
  rooms?: number[]
  floorMin?: number
  floorMax?: number
  notGroundFloor?: boolean
  notLastFloor?: boolean
  totalFloorsMin?: number
  totalFloorsMax?: number
  yearBuiltMin?: number
  yearBuiltMax?: number
  features?: string[]
  featureMode?: 'ALL' | 'ANY'
  includeSources?: string[]
  excludeSources?: string[]
  sellerType?: 'ANY' | 'PRIVATE' | 'AGENCY'
  bbox?: BoundingBox
  lat?: number
  lon?: number
  radiusKm?: number
  polygon?: GeoPolygon
  addedWithinDays?: number
  priceDropWithinDays?: number
  minPriceDropPercent?: number
  daysOnMarketMin?: number
  daysOnMarketMax?: number
  multiSource?: boolean
  includeArchived?: boolean
}

export interface SearchState {
  criteria: Criteria
  sort: string[]
  page: number
  size: number
}

export const DEFAULT_SORT = ['addedAt,desc']
export const DEFAULT_SIZE = 20

const NUMBER_KEYS = [
  'priceMin', 'priceMax', 'pricePerM2Min', 'pricePerM2Max', 'areaMin', 'areaMax', 'floorMin', 'floorMax',
  'totalFloorsMin', 'totalFloorsMax', 'yearBuiltMin', 'yearBuiltMax', 'lat', 'lon', 'radiusKm',
  'addedWithinDays', 'priceDropWithinDays', 'minPriceDropPercent', 'daysOnMarketMin', 'daysOnMarketMax',
] as const satisfies readonly (keyof Criteria)[]
const NUMBER_LIST_KEYS = ['cityIds', 'districtIds', 'rooms'] as const satisfies readonly (keyof Criteria)[]
const STRING_LIST_KEYS = ['propertyTypes', 'features', 'includeSources', 'excludeSources'] as const satisfies readonly (keyof Criteria)[]
const STRING_KEYS = ['q', 'street', 'transactionType', 'marketType', 'featureMode', 'sellerType'] as const satisfies readonly (keyof Criteria)[]
const BOOLEAN_KEYS = ['notGroundFloor', 'notLastFloor', 'multiSource', 'includeArchived'] as const satisfies readonly (keyof Criteria)[]

function toNumber(value: string | null): number | undefined {
  if (value === null || value.trim() === '') return undefined
  const n = Number(value)
  return Number.isFinite(n) ? n : undefined
}

function splitList(params: URLSearchParams, key: string): string[] {
  return params.getAll(key).flatMap((v) => v.split(',')).map((v) => v.trim()).filter(Boolean)
}

/** URL → stan wyszukiwarki (link do udostępnienia). */
export function parseSearchParams(params: URLSearchParams): SearchState {
  const c: Record<string, unknown> = {}
  for (const key of NUMBER_KEYS) {
    const n = toNumber(params.get(key))
    if (n !== undefined) c[key] = n
  }
  for (const key of NUMBER_LIST_KEYS) {
    const list = splitList(params, key).map(Number).filter(Number.isFinite)
    if (list.length) c[key] = list
  }
  for (const key of STRING_LIST_KEYS) {
    const list = splitList(params, key)
    if (list.length) c[key] = list
  }
  for (const key of STRING_KEYS) {
    const v = params.get(key)
    if (v) c[key] = v
  }
  for (const key of BOOLEAN_KEYS) {
    if (params.get(key) === 'true') c[key] = true
  }
  const bbox = params.get('bbox')?.split(',').map(Number)
  if (bbox && bbox.length === 4 && bbox.every(Number.isFinite)) {
    c.bbox = { south: bbox[0], west: bbox[1], north: bbox[2], east: bbox[3] }
  }
  const polygon = params.get('polygon')
  if (polygon) {
    try {
      const parsed = JSON.parse(polygon) as GeoPolygon
      if (parsed?.type === 'Polygon' && Array.isArray(parsed.coordinates)) c.polygon = parsed
    } catch {
      /* ignorujemy uszkodzony poligon */
    }
  }
  const sort = params.getAll('sort').filter(Boolean)
  const page = Math.max(0, toNumber(params.get('page')) ?? 0)
  const size = Math.min(100, Math.max(1, toNumber(params.get('size')) ?? DEFAULT_SIZE))
  return { criteria: c as Criteria, sort: sort.length ? sort : DEFAULT_SORT, page, size }
}

/** Stan wyszukiwarki → URL. Pomija puste wartości i wartości domyślne. */
export function toSearchParams(state: SearchState): URLSearchParams {
  const params = new URLSearchParams()
  const c = state.criteria as Record<string, unknown>
  for (const key of STRING_KEYS) {
    const v = c[key]
    if (typeof v === 'string' && v.trim()) params.set(key, v.trim())
  }
  for (const key of NUMBER_KEYS) {
    const v = c[key]
    if (typeof v === 'number' && Number.isFinite(v)) params.set(key, String(v))
  }
  for (const key of [...NUMBER_LIST_KEYS, ...STRING_LIST_KEYS]) {
    const v = c[key]
    if (Array.isArray(v) && v.length) params.set(key, v.join(','))
  }
  for (const key of BOOLEAN_KEYS) {
    if (c[key] === true) params.set(key, 'true')
  }
  if (state.criteria.bbox) {
    const b = state.criteria.bbox
    params.set('bbox', [b.south, b.west, b.north, b.east].map((n) => n.toFixed(5)).join(','))
  }
  if (state.criteria.polygon) {
    params.set('polygon', JSON.stringify(state.criteria.polygon))
  }
  const sort = state.sort.length ? state.sort : DEFAULT_SORT
  if (sort.join('|') !== DEFAULT_SORT.join('|')) sort.forEach((s) => params.append('sort', s))
  if (state.page > 0) params.set('page', String(state.page))
  if (state.size !== DEFAULT_SIZE) params.set('size', String(state.size))
  return params
}

/** Usuwa puste pola (do wysłania w body i do porównań). */
export function cleanCriteria(criteria: Criteria): Criteria {
  const out: Record<string, unknown> = {}
  for (const [key, value] of Object.entries(criteria)) {
    if (value === undefined || value === null || value === '' || value === false) continue
    if (Array.isArray(value) && value.length === 0) continue
    out[key] = typeof value === 'string' ? value.trim() : value
  }
  return out as Criteria
}

/** Liczba aktywnych filtrów (do etykiety przycisku "Filtry"). */
export function activeFilterCount(criteria: Criteria): number {
  const c = cleanCriteria(criteria) as Record<string, unknown>
  const pairs: [string, string][] = [
    ['priceMin', 'priceMax'], ['pricePerM2Min', 'pricePerM2Max'], ['areaMin', 'areaMax'], ['floorMin', 'floorMax'],
    ['totalFloorsMin', 'totalFloorsMax'], ['yearBuiltMin', 'yearBuiltMax'], ['daysOnMarketMin', 'daysOnMarketMax'], ['lat', 'lon'],
  ]
  let count = Object.keys(c).filter((k) => !['q', 'featureMode', 'radiusKm'].includes(k)).length
  for (const [a, b] of pairs) if (a in c && b in c) count--
  return count
}
