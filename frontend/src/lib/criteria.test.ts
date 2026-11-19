import { describe, expect, it } from 'vitest'
import { activeFilterCount, cleanCriteria, DEFAULT_SORT, parseSearchParams, toSearchParams, type SearchState } from './criteria'

describe('serializacja filtrów do URL', () => {
  const state: SearchState = {
    criteria: {
      q: 'balkon -parter',
      transactionType: 'SALE',
      propertyTypes: ['APARTMENT', 'HOUSE'],
      cityIds: [1],
      districtIds: [3, 7],
      priceMax: 1_200_000,
      rooms: [2, 3, 5],
      notGroundFloor: true,
      features: ['BALCONY', 'ELEVATOR'],
      featureMode: 'ANY',
      bbox: { south: 52.1, west: 20.9, north: 52.3, east: 21.2 },
      polygon: { type: 'Polygon', coordinates: [[[21, 52.2], [21.1, 52.2], [21.1, 52.3], [21, 52.2]]] },
    },
    sort: ['pricePerM2,asc', 'area,desc'],
    page: 2,
    size: 50,
  }

  it('zachowuje stan przy przejściu URL → stan → URL (round-trip)', () => {
    const params = toSearchParams(state)
    expect(parseSearchParams(new URLSearchParams(params.toString()))).toEqual(state)
  })

  it('zapisuje listy po przecinku i sortowanie jako powtórzone parametry', () => {
    const params = toSearchParams(state)
    expect(params.get('rooms')).toBe('2,3,5')
    expect(params.get('propertyTypes')).toBe('APARTMENT,HOUSE')
    expect(params.getAll('sort')).toEqual(['pricePerM2,asc', 'area,desc'])
    expect(params.get('bbox')).toBe('52.10000,20.90000,52.30000,21.20000')
  })

  it('pomija wartości puste i domyślne', () => {
    const params = toSearchParams({ criteria: { q: '  ', rooms: [], notLastFloor: false }, sort: DEFAULT_SORT, page: 0, size: 20 })
    expect(params.toString()).toBe('')
  })

  it('jest odporny na niepoprawne wartości w URL', () => {
    const parsed = parseSearchParams(new URLSearchParams('priceMin=abc&rooms=2,x,3&page=-4&size=999&polygon={zły&bbox=1,2'))
    expect(parsed.criteria.priceMin).toBeUndefined()
    expect(parsed.criteria.rooms).toEqual([2, 3])
    expect(parsed.criteria.polygon).toBeUndefined()
    expect(parsed.criteria.bbox).toBeUndefined()
    expect(parsed.page).toBe(0)
    expect(parsed.size).toBe(100)
    expect(parsed.sort).toEqual(DEFAULT_SORT)
  })

  it('akceptuje listy jako powtórzone parametry (format GET backendu)', () => {
    const parsed = parseSearchParams(new URLSearchParams('cityIds=1&cityIds=2&features=BALCONY'))
    expect(parsed.criteria.cityIds).toEqual([1, 2])
    expect(parsed.criteria.features).toEqual(['BALCONY'])
  })
})

describe('cleanCriteria i licznik filtrów', () => {
  it('usuwa puste pola przed wysłaniem do API', () => {
    expect(cleanCriteria({ q: ' metro ', rooms: [], notGroundFloor: false, priceMax: 0, street: '' }))
      .toEqual({ q: 'metro', priceMax: 0 })
  })

  it('liczy zakres min–max jako jeden filtr i pomija tekst wyszukiwania', () => {
    expect(activeFilterCount({ q: 'x', priceMin: 1, priceMax: 2, rooms: [2], lat: 52, lon: 21, radiusKm: 2 })).toBe(3)
    expect(activeFilterCount({})).toBe(0)
  })
})
