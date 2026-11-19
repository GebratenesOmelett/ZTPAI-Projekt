import type { Criteria } from './criteria'
import { formatCompactPrice, MARKET_LABELS, PROPERTY_TYPE_LABELS, TRANSACTION_LABELS } from './format'
import type { City, District } from './types'

function range(label: string, min?: number, max?: number, unit = ''): string | null {
  if (min === undefined && max === undefined) return null
  if (min !== undefined && max !== undefined) return `${label} ${min}–${max}${unit}`
  return min !== undefined ? `${label} od ${min}${unit}` : `${label} do ${max}${unit}`
}

/** Czytelne podsumowanie kryteriów (np. na liście zapisanych wyszukiwań). */
export function summarizeCriteria(c: Criteria, cities: City[] = [], districts: District[] = []): string[] {
  const parts: (string | null)[] = []
  if (c.q) parts.push(`„${c.q}”`)
  if (c.transactionType) parts.push(TRANSACTION_LABELS[c.transactionType])
  if (c.propertyTypes?.length) parts.push(c.propertyTypes.map((t) => PROPERTY_TYPE_LABELS[t]).join(', '))
  if (c.marketType) parts.push(`rynek ${MARKET_LABELS[c.marketType].toLowerCase()}`)
  if (c.cityIds?.length) parts.push(c.cityIds.map((id) => cities.find((x) => x.id === id)?.name ?? `#${id}`).join(', '))
  if (c.districtIds?.length) parts.push(c.districtIds.map((id) => districts.find((x) => x.id === id)?.name ?? `#${id}`).join(', '))
  if (c.priceMin !== undefined || c.priceMax !== undefined) {
    parts.push(`cena ${c.priceMin !== undefined ? `od ${formatCompactPrice(c.priceMin)} ` : ''}${c.priceMax !== undefined ? `do ${formatCompactPrice(c.priceMax)}` : ''}`.trim())
  }
  parts.push(range('cena/m²', c.pricePerM2Min, c.pricePerM2Max, ' zł'))
  parts.push(range('metraż', c.areaMin, c.areaMax, ' m²'))
  if (c.rooms?.length) parts.push(`pokoje: ${c.rooms.map((r) => (r >= 5 ? '5+' : r)).join(', ')}`)
  if (c.notGroundFloor) parts.push('bez parteru')
  if (c.notLastFloor) parts.push('nie ostatnie piętro')
  if (c.features?.length) parts.push(`cechy (${c.featureMode === 'ANY' ? 'dowolna' : 'wszystkie'}): ${c.features.length}`)
  if (c.polygon) parts.push('obszar z mapy')
  if (c.radiusKm && c.lat !== undefined) parts.push(`promień ${c.radiusKm} km`)
  if (c.multiSource) parts.push('≥ 2 portale')
  if (c.sellerType === 'PRIVATE') parts.push('tylko prywatne')
  if (c.sellerType === 'AGENCY') parts.push('tylko biura')
  if (c.addedWithinDays) parts.push(`dodane w ${c.addedWithinDays} dni`)
  if (c.minPriceDropPercent) parts.push(`obniżka ≥ ${c.minPriceDropPercent}%`)
  return parts.filter((p): p is string => !!p)
}
