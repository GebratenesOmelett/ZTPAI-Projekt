import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { FormField } from '@/components/common/FormField'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { errorMessage } from '@/lib/api'
import { useAuth } from '@/lib/auth'
import { applyApiErrors } from '@/lib/forms'
import { loginSchema, type LoginValues } from '@/lib/schemas'

export default function LoginPage() {
  const { login, user } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const redirect = params.get('redirect') || '/'
  const [formError, setFormError] = useState<string | null>(null)
  const form = useForm<LoginValues>({ resolver: zodResolver(loginSchema), defaultValues: { email: '', password: '' } })

  if (user) return <Navigate to={redirect} replace />

  const onSubmit = async (values: LoginValues) => {
    setFormError(null)
    try {
      const u = await login(values.email, values.password)
      toast.success(`Witaj, ${u.displayName}!`)
      navigate(redirect, { replace: true })
    } catch (e) {
      if (!applyApiErrors(e, form.setError, ['email', 'password'])) setFormError(errorMessage(e))
    }
  }

  return (
    <div className="mx-auto max-w-md py-8">
      <Card>
        <CardHeader>
          <CardTitle className="text-2xl">Logowanie</CardTitle>
          <CardDescription>Zaloguj się, aby zapisywać wyszukiwania, ulubione i otrzymywać alerty.</CardDescription>
        </CardHeader>
        <CardContent>
          <form className="space-y-4" onSubmit={form.handleSubmit(onSubmit)} noValidate>
            {formError && <Alert variant="destructive"><AlertDescription>{formError}</AlertDescription></Alert>}
            <FormField id="email" label="E-mail" error={form.formState.errors.email?.message}>
              <Input id="email" type="email" autoComplete="email" aria-invalid={!!form.formState.errors.email} {...form.register('email')} />
            </FormField>
            <FormField id="password" label="Hasło" error={form.formState.errors.password?.message}>
              <Input id="password" type="password" autoComplete="current-password" aria-invalid={!!form.formState.errors.password} {...form.register('password')} />
            </FormField>
            <Button type="submit" className="w-full" disabled={form.formState.isSubmitting}>
              {form.formState.isSubmitting ? 'Logowanie…' : 'Zaloguj'}
            </Button>
            <p className="text-center text-sm text-muted-foreground">
              Nie masz konta? <Link to="/rejestracja" className="text-teal-700 hover:underline">Zarejestruj się</Link>
            </p>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
