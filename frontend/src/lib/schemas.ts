import { z } from 'zod'

export const loginSchema = z.object({
  email: z.string().trim().min(1, 'Podaj e-mail').email('Niepoprawny adres e-mail'),
  password: z.string().min(1, 'Podaj hasło'),
})
export type LoginValues = z.infer<typeof loginSchema>

/** Te same reguły co walidacja Bean Validation w RegisterRequest na backendzie. */
export const registerSchema = z.object({
  displayName: z.string().trim().min(2, 'Minimum 2 znaki').max(100, 'Maksymalnie 100 znaków'),
  email: z.string().trim().min(1, 'Podaj e-mail').email('Niepoprawny adres e-mail').max(255),
  password: z.string()
    .min(8, 'Hasło musi mieć co najmniej 8 znaków')
    .max(72, 'Hasło może mieć maksymalnie 72 znaki')
    .regex(/\p{L}/u, 'Hasło musi zawierać literę')
    .regex(/\d/, 'Hasło musi zawierać cyfrę'),
  confirmPassword: z.string(),
}).refine((v) => v.password === v.confirmPassword, { message: 'Hasła nie są identyczne', path: ['confirmPassword'] })
export type RegisterValues = z.infer<typeof registerSchema>
