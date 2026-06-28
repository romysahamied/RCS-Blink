/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  i18n: {
    locales: ['en-US'],
    defaultLocale: 'en-US',
  },
  output: 'standalone',

  async redirects() {
    return [
      {
        source: '/',
        destination: '/dashboard',
        permanent: true,
      },
      {
        source: '/android',
        destination: '/download',
        permanent: false,
      },
    ]
  },

  async headers() {
    return [
      {
        source: '/downloads/:path*',
        headers: [
          {
            key: 'Cache-Control',
            value: 'no-cache, no-store, must-revalidate',
          },
        ],
      },
    ]
  },

  async rewrites() {
    return [
      {
        source: '/downloads/rcs-blink-dev.apk',
        destination: '/api/download/android-apk',
      },
    ]
  },
}



module.exports = nextConfig;
