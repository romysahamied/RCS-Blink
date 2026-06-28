/** Default APK served from web/public/downloads/ (upload after build). */
export const DEFAULT_HOSTED_APK_PATH = '/downloads/rcs-blink-dev.apk'

export const HOSTED_APK_FILENAME = 'rcs-blink-dev.apk'

export function getConfiguredApkDownloadUrl(): string {
  return (
    process.env.NEXT_PUBLIC_ANDROID_APP_DOWNLOAD_URL?.trim() ||
    DEFAULT_HOSTED_APK_PATH
  )
}
