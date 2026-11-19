import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from './api'
import { compareStore, MAX_COMPARE } from './compare'
import { applyApiErrors } from './forms'

describe('porównywarka', () => {
  beforeEach(() => compareStore.clear())

  it('pozwala dodać maksymalnie 4 nieruchomości', () => {
    for (let id = 1; id <= MAX_COMPARE; id++) expect(compareStore.add(id)).toBe(true)
    expect(compareStore.add(99)).toBe(false)
    expect(compareStore.get()).toEqual([1, 2, 3, 4])
  })

  it('przełącza i zapisuje w localStorage', () => {
    compareStore.toggle(7)
    expect(JSON.parse(localStorage.getItem('domradar.compare')!)).toEqual([7])
    compareStore.toggle(7)
    expect(compareStore.get()).toEqual([])
  })
})

describe('mapowanie błędów ProblemDetail na formularz', () => {
  it('przypisuje fieldErrors do znanych pól', () => {
    const setError = vi.fn()
    const error = new ApiError({
      status: 400,
      errorCode: 'VALIDATION_FAILED',
      fieldErrors: [{ field: 'email', message: 'zły e-mail' }, { field: 'unknown', message: 'x' }],
    })
    expect(applyApiErrors(error, setError, ['email', 'password'])).toBe(true)
    expect(setError).toHaveBeenCalledTimes(1)
    expect(setError).toHaveBeenCalledWith('email', { type: 'server', message: 'zły e-mail' })
  })

  it('zwraca false dla błędów bez pól (np. 409)', () => {
    const setError = vi.fn()
    expect(applyApiErrors(new ApiError({ status: 409, errorCode: 'EMAIL_TAKEN' }), setError, ['email'])).toBe(false)
    expect(applyApiErrors(new Error('x'), setError, ['email'])).toBe(false)
    expect(setError).not.toHaveBeenCalled()
  })
})
