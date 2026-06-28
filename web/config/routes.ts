export const Routes = {
  landingPage: 'https://textbee.dev',
  contribute: '/contribute',
  useCases: 'https://textbee.dev/use-cases',
  quickstart: 'https://textbee.dev/quickstart',
  login: '/login',
  authError: '/auth-error',
  register: '/register',
  logout: '/logout',
  resetPassword: '/reset-password',
  verifyEmail: '/verify-email',

  dashboard: '/dashboard',

  downloadAndroidApp:
    process.env.NEXT_PUBLIC_ANDROID_APP_DOWNLOAD_URL?.trim() || '/download',
  privacyPolicy: 'https://textbee.dev/privacy-policy',
  refundPolicy: 'https://textbee.dev/refund-policy',
  termsOfService: 'https://textbee.dev/terms-of-service',
  statusPage: 'https://status.textbee.dev',
}
