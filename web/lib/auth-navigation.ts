import { Routes } from '@/config/routes'

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
