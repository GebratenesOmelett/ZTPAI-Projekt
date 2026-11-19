import { describe, expect, it } from 'vitest'
import { formatFloor, formatPrice, plural, vsMedianLabel } from './format'

const normalize = (s: string) => s.replace(/\s/g, ' ')

describe('formatowanie', () => {
  it('odmienia liczebniki po polsku', () => {
    expect(plural(1, 'pokój', 'pokoje', 'pokoi')).toBe('pokój')
    expect(plural(3, 'pokój', 'pokoje', 'pokoi')).toBe('pokoje')
    expect(plural(5, 'pokój', 'pokoje', 'pokoi')).toBe('pokoi')
    expect(plural(12, 'pokój', 'pokoje', 'pokoi')).toBe('pokoi')
    expect(plural(22, 'pokój', 'pokoje', 'pokoi')).toBe('pokoje')
  })

  it('formatuje ceny sprzedaży i najmu', () => {
    expect(normalize(formatPrice(649000))).toBe('649 000 zł')
    expect(normalize(formatPrice(3400, 'RENT'))).toBe('3400 zł/mies.')
    expect(formatPrice(undefined)).toBe('Cena do uzgodnienia')
  })

  it('opisuje piętro i porównanie do mediany', () => {
    expect(formatFloor(0, 4)).toBe('parter / 4')
    expect(formatFloor(3)).toBe('3. piętro')
    expect(vsMedianLabel(-8.4)).toBe('8,4% poniżej mediany dzielnicy')
    expect(vsMedianLabel(12)).toBe('12% powyżej mediany dzielnicy')
    expect(vsMedianLabel(undefined)).toBeNull()
  })
})
