/** Align localhost links from the API (FRONTEND_URL) with the port the web app is actually on. */
export function normalizeLocalDevLink(link: string | undefined | null): string {
  if (!link || typeof window === 'undefined') {
    return link ?? ''
  }
  try {
    const url = new URL(link)
    if (url.hostname !== 'localhost' && url.hostname !== '127.0.0.1') {
      return link
    }
    const currentPort = new URL(window.location.origin).port
    if (url.port !== currentPort) {
      url.port = currentPort
      return url.toString()
    }
  } catch {
    // ignore malformed URLs
  }
  return link
}
