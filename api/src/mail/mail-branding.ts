function trimTrailingSlash(url: string): string {
  return url.replace(/\/$/, '')
}

export function getMailBranding(
  extra: Record<string, unknown> = {},
): Record<string, unknown> {
  const frontendUrl = trimTrailingSlash(
    process.env.FRONTEND_URL || 'http://localhost:3002',
  )

  const brandName = process.env.APP_BRAND_NAME?.trim() || 'RCS Blink'
  const brandDomain =
    process.env.APP_BRAND_DOMAIN?.trim() ||
    (() => {
      try {
        return new URL(frontendUrl).host
      } catch {
        return 'localhost'
      }
    })()

  const apiBase = trimTrailingSlash(
    process.env.API_PUBLIC_URL ||
      process.env.PUBLIC_API_URL ||
      frontendUrl.replace(/:\d+$/, ':3001'),
  )

  const supportEmail =
    process.env.MAIL_REPLY_TO?.trim() ||
    process.env.ADMIN_EMAIL?.trim() ||
    process.env.MAIL_FROM?.trim() ||
    `support@${brandDomain}`

  return {
    brandName,
    brandDomain,
    frontendUrl,
    dashboardUrl: `${frontendUrl}/dashboard`,
    quickstartUrl: `${frontendUrl}/quickstart`,
    downloadUrl: `${frontendUrl}/api/download/android-apk`,
    accountSettingsUrl: `${frontendUrl}/dashboard/account`,
    apiDocsUrl: apiBase,
    apiV1Url: `${apiBase}/api/v1`,
    logoUrl:
      process.env.APP_LOGO_URL?.trim() ||
      `${frontendUrl}/images/rcs-blink-icon.png`,
    supportEmail,
    tagline:
      process.env.APP_BRAND_TAGLINE?.trim() ||
      'Your gateway to powerful SMS integration',
    currentYear: new Date().getFullYear(),
    ...extra,
  }
}

export function getMailFromAddress(): string | undefined {
  const configured = process.env.MAIL_FROM?.trim()
  if (configured) {
    return configured
  }
  const { brandName, brandDomain } = getMailBranding()
  return `${brandName} <noreply@${brandDomain}>`
}

export function mailSubject(suffix: string): string {
  const { brandName } = getMailBranding()
  return `${brandName} - ${suffix}`
}
