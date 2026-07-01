import { Routes } from '@/config/routes'
import { getSession } from 'next-auth/react'
import type { Session } from 'next-auth'
import { clearSessionCache } from '@/lib/httpBrowserClient'

/** Strip a NextAuth absolute URL down to an in-app path (avoids localhost NEXTAUTH_URL on deploy). */
export function toAppPath(
  url: string | null | undefined,
  fallback = Routes.dashboard
): string {
  if (!url) {
    return fallback
  }
  if (url.startsWith('/')) {
    return url
  }
  try {
    const { pathname, search, hash } = new URL(url)
    const path = `${pathname}${search}${hash}`
    return path.startsWith('/') ? path : fallback
  } catch {
    return fallback
  }
}

export function safeRedirectParam(
  value: string | null | undefined,
  fallback = Routes.dashboard
): string {
  if (!value) {
    return fallback
  }
  return toAppPath(decodeURIComponent(value), fallback)
}

export function isNextRedirect(error: unknown): boolean {
  return (
    typeof error === 'object' &&
    error !== null &&
    'digest' in error &&
    typeof (error as { digest?: string }).digest === 'string' &&
    (error as { digest: string }).digest.startsWith('NEXT_REDIRECT')
  )
}

/** Refresh SessionProvider after sign-in (update() is a no-op when no session exists yet). */
export async function syncSessionAfterSignIn(
  update?: () => Promise<Session | null>,
): Promise<Session | null> {
  clearSessionCache()

  const session = await getSession()
  if (session?.user) {
    return session
  }

  if (update) {
    const updated = await update()
    if (updated?.user) {
      return updated
    }
  }

  return getSession()
}
