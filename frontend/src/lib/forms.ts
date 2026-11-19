import type { FieldValues, Path, UseFormSetError } from 'react-hook-form'
import { ApiError } from './api'

/**
 * Przenosi błędy pól z odpowiedzi ProblemDetail (fieldErrors) na pola formularza React Hook Form.
 * Zwraca true, jeśli udało się przypisać przynajmniej jeden błąd do znanego pola.
 */
export function applyApiErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  knownFields: readonly string[],
  fieldMap: Record<string, string> = {},
): boolean {
  if (!(error instanceof ApiError) || error.fieldErrors.length === 0) return false
  let applied = false
  for (const fe of error.fieldErrors) {
    const field = fieldMap[fe.field] ?? fe.field
    if (knownFields.includes(field)) {
      setError(field as Path<T>, { type: 'server', message: fe.message })
      applied = true
    }
  }
  return applied
}
