/** Default APK served from web/public/downloads/ (upload after build). */
export const DEFAULT_HOSTED_APK_PATH = '/downloads/rcs-blink-dev.apk'

export const HOSTED_APK_FILENAME = 'rcs-blink-dev.apk'

function getApkVersionCacheBust(): string | null {
  const version = process.env.NEXT_PUBLIC_LATEST_APP_VERSION_CODE?.trim()
  return version && version.length > 0 ? version : null
}

/** Base APK path/URL without cache-busting query params. */
export function getConfiguredApkDownloadUrl(): string {
  return (
    process.env.NEXT_PUBLIC_ANDROID_APP_DOWNLOAD_URL?.trim() ||
    DEFAULT_HOSTED_APK_PATH
  )
}

/** APK URL for dashboard links — appends ?v=version so browsers fetch fresh builds. */
export function getApkDownloadUrlWithCacheBust(): string {
  const base = getConfiguredApkDownloadUrl()
  const version = getApkVersionCacheBust()
  if (!version) {
    return base
  }
  const separator = base.includes('?') ? '&' : '?'
  return `${base}${separator}v=${encodeURIComponent(version)}`
}
