import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, Navigate, useNavigate } from 'react-router'
import { toast } from 'sonner'
import { FormField } from '@/components/common/FormField'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { ApiError, errorMessage } from '@/lib/api'
import { useAuth } from '@/lib/auth'
import { applyApiErrors } from '@/lib/forms'
import { registerSchema, type RegisterValues } from '@/lib/schemas'

export function RegisterForm({ onRegistered }: { onRegistered?: () => void }) {
  const { register: registerUser } = useAuth()
  const [formError, setFormError] = useState<string | null>(null)
  const form = useForm<RegisterValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: { displayName: '', email: '', password: '', confirmPassword: '' },
  })
  const { errors, isSubmitting } = form.formState

  const onSubmit = async (values: RegisterValues) => {
    setFormError(null)
    try {
      await registerUser(values.email, values.password, values.displayName)
      toast.success('Konto utworzone — jesteś zalogowany')
      onRegistered?.()
    } catch (e) {
      if (e instanceof ApiError && e.errorCode === 'EMAIL_TAKEN') {
        form.setError('email', { type: 'server', message: e.message })
      } else if (!applyApiErrors(e, form.setError, ['email', 'password', 'displayName'])) {
        setFormError(errorMessage(e))
      }
    }
  }

  return (
    <form className="space-y-4" onSubmit={form.handleSubmit(onSubmit)} noValidate>
      {formError && <Alert variant="destructive"><AlertDescription>{formError}</AlertDescription></Alert>}
      <FormField id="displayName" label="Imię lub nazwa" error={errors.displayName?.message}>
        <Input id="displayName" autoComplete="name" aria-invalid={!!errors.displayName} {...form.register('displayName')} />
      </FormField>
      <FormField id="reg-email" label="E-mail" error={errors.email?.message}>
        <Input id="reg-email" type="email" autoComplete="email" aria-invalid={!!errors.email} {...form.register('email')} />
      </FormField>
      <FormField id="reg-password" label="Hasło" error={errors.password?.message} hint="Min. 8 znaków, litera i cyfra">
        <Input id="reg-password" type="password" autoComplete="new-password" aria-invalid={!!errors.password} {...form.register('password')} />
      </FormField>
      <FormField id="confirmPassword" label="Powtórz hasło" error={errors.confirmPassword?.message}>
        <Input id="confirmPassword" type="password" autoComplete="new-password" aria-invalid={!!errors.confirmPassword} {...form.register('confirmPassword')} />
      </FormField>
      <Button type="submit" className="w-full" disabled={isSubmitting}>{isSubmitting ? 'Tworzenie konta…' : 'Załóż konto'}</Button>
    </form>
  )
}

export default function RegisterPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  if (user) return <Navigate to="/" replace />
  return (
    <div className="mx-auto max-w-md py-8">
      <Card>
        <CardHeader>
          <CardTitle className="text-2xl">Rejestracja</CardTitle>
          <CardDescription>Załóż konto, aby zapisywać wyszukiwania i dostawać alerty o nowych ogłoszeniach.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <RegisterForm onRegistered={() => navigate('/')} />
          <p className="text-center text-sm text-muted-foreground">
            Masz już konto? <Link to="/logowanie" className="text-teal-700 hover:underline">Zaloguj się</Link>
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
