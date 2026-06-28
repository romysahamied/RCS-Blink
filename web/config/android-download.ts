/** Default APK served from web/public/downloads/ (upload after build). */
export const DEFAULT_HOSTED_APK_PATH = '/downloads/rcs-blink-dev.apk'

/** Reliable download endpoint (serves local APK or redirects to GitHub). */
export const APK_DOWNLOAD_API_PATH = '/api/download/android-apk'

export const HOSTED_APK_FILENAME = 'rcs-blink-dev.apk'

function getApkVersionCacheBust(): string | null {
  const version = process.env.NEXT_PUBLIC_LATEST_APP_VERSION_CODE?.trim()
  return version && version.length > 0 ? version : null
}

/** Legacy static path — prefer APK_DOWNLOAD_API_PATH for downloads. */
export function getConfiguredApkDownloadUrl(): string {
  return (
    process.env.NEXT_PUBLIC_ANDROID_APP_DOWNLOAD_URL?.trim() ||
    DEFAULT_HOSTED_APK_PATH
  )
}

/** APK URL for dashboard links — uses API route with cache-busting version param. */
export function getApkDownloadUrlWithCacheBust(): string {
  const base = APK_DOWNLOAD_API_PATH
  const version = getApkVersionCacheBust()
  if (!version) {
    return base
  }
  const separator = base.includes('?') ? '&' : '?'
  return `${base}${separator}v=${encodeURIComponent(version)}`
}
