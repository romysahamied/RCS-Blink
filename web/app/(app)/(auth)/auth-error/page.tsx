'use client'

import Link from 'next/link'
import { useSearchParams } from 'next/navigation'
import { Routes } from '@/config/routes'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'

const getErrorMessage = (error?: string | null) => {
  if (!error) return 'Authentication failed. Please try signing in again.'
  if (error === 'CredentialsSignin') {
    return 'Invalid email or password, or the API/database is currently unavailable.'
  }
  return 'Authentication failed. Please try again.'
}

export default function AuthErrorPage() {
  const searchParams = useSearchParams()
  const error = searchParams.get('error')

  return (
    <div className='flex min-h-screen items-center justify-center bg-gray-100 dark:bg-muted'>
      <Card className='w-[420px] shadow-lg'>
        <CardHeader>
          <CardTitle className='text-center text-2xl font-bold'>
            Sign-in Error
          </CardTitle>
        </CardHeader>
        <CardContent className='space-y-4 text-center'>
          <p className='rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800 dark:border-red-900 dark:bg-red-950/50 dark:text-red-200'>
            {getErrorMessage(error)}
          </p>
          <Link href={Routes.login}>
            <Button className='w-full'>Back to Login</Button>
          </Link>
        </CardContent>
      </Card>
    </div>
  )
}
