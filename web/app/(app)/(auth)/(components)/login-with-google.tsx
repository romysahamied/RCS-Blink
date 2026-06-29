'use client'

import { Routes } from '@/config/routes'
import { safeRedirectParam } from '@/lib/auth-navigation'
import { toast } from '@/hooks/use-toast'
import { CredentialResponse, GoogleLogin } from '@react-oauth/google'
import { signIn } from 'next-auth/react'
import { useRouter, useSearchParams } from 'next/navigation'

export default function LoginWithGoogle() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const redirect = searchParams.get('redirect')
  const googleClientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID?.trim()

  if (!googleClientId) {
    return null
  }

  const onGoogleLoginSuccess = async (
    credentialResponse: CredentialResponse
  ) => {
    toast({
      title: 'Success',
      description: 'You are logged in with Google',
      variant: 'default',
    })
    const result = await signIn('google-id-token-login', {
      redirect: false,
      idToken: credentialResponse.credential,
    })
    if (result?.ok) {
      router.push(safeRedirectParam(redirect, Routes.dashboard))
      return
    }
    toast({
      title: 'Error',
      description: 'Google sign-in failed',
      variant: 'destructive',
    })
  }

  const onGoogleLoginError = () => {
    toast({
      title: 'Error',
      description: 'Something went wrong',
      variant: 'destructive',
    })
  }
  return (
    <GoogleLogin
      onSuccess={onGoogleLoginSuccess}
      onError={onGoogleLoginError}
      useOneTap={true}
      width={'100%'}
      size='large'
      shape='pill'
      locale='en'
      theme='outline'
      text='continue_with'
    />
  )
}
