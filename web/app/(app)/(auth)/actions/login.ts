'use server'

import { redirect } from 'next/navigation'
import { Routes } from '@/config/routes'
import { loginWithEmailPassword } from '@/lib/authenticate-user'
import { createUserSession } from '@/lib/create-user-session'
import { decryptLoginSecret } from '@/lib/credential-crypto.server'

export type AuthActionState = {
  error?: string
  success?: boolean
}

function readRequiredField(formData: FormData, name: string): string | null {
  const value = formData.get(name)
  if (typeof value !== 'string' || !value.trim()) {
    return null
  }
  return value
}

export async function loginAction(
  _prevState: AuthActionState,
  formData: FormData,
): Promise<AuthActionState> {
  const email = readRequiredField(formData, 'email')
  const passwordEnc = readRequiredField(formData, 'passwordEnc')
  const turnstileToken = readRequiredField(formData, 'turnstileToken')

  if (!email || !passwordEnc || !turnstileToken) {
    return { error: 'Please complete all fields and bot verification.' }
  }

  let password: string
  try {
    password = await decryptLoginSecret(passwordEnc)
  } catch {
    return { error: 'Invalid email or password' }
  }

  const user = await loginWithEmailPassword(email, password, turnstileToken)
  if (!user) {
    return { error: 'Invalid email or password' }
  }

  await createUserSession(user)
  redirect(Routes.dashboard)
}
