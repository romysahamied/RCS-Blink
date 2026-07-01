'use client'

import { Routes } from '@/config/routes'
import { signOut, useSession } from 'next-auth/react'
import { useRouter } from 'next/navigation'
import { useEffect } from 'react'

export default function Logout() {
  const session = useSession()
  const router = useRouter()
  useEffect(() => {
    const logout = async () => {
      if (session.status === 'authenticated') {
        await signOut({ redirect: false })
        router.refresh()
        router.replace(Routes.login)
      } else if (session.status === 'unauthenticated') {
        router.replace(Routes.login)
      }
    }
    logout()
  }, [router, session.status])

  return (
    <div className='text-center min-h-screen flex items-center justify-center'>
      Logging out...
    </div>
  )
}
