import { existsSync, readFileSync, statSync } from 'fs'
import { join } from 'path'
import { NextRequest, NextResponse } from 'next/server'
import { HOSTED_APK_FILENAME } from '@/config/android-download'

export const runtime = 'nodejs'

const GITHUB_REPO =
  process.env.NEXT_PUBLIC_ANDROID_GITHUB_REPO?.trim() || 'romysahamied/RCS-Blink'

function resolveApkPath(): string | null {
  const configuredPath = process.env.ANDROID_APK_PATH?.trim()
  if (configuredPath && existsSync(configuredPath)) {
    return configuredPath
  }

  const candidates = [
    join(process.cwd(), 'public', 'downloads', HOSTED_APK_FILENAME),
    join(process.cwd(), '..', 'web', 'public', 'downloads', HOSTED_APK_FILENAME),
  ]

  for (const candidate of candidates) {
    if (existsSync(candidate)) {
      return candidate
    }
  }

  return null
}

async function resolveGithubApkUrl(): Promise<string | null> {
  try {
    const response = await fetch(
      `https://api.github.com/repos/${GITHUB_REPO}/releases?per_page=10`,
      {
        headers: { Accept: 'application/vnd.github+json' },
        next: { revalidate: 300 },
      }
    )
    if (!response.ok) {
      return null
    }

    const releases = (await response.json()) as Array<{
      prerelease?: boolean
      assets?: Array<{ name?: string; browser_download_url?: string }>
    }>

    for (const release of releases) {
      if (release.prerelease) {
        continue
      }
      const apkAsset = release.assets?.find((asset) =>
        asset.name?.toLowerCase().endsWith('.apk')
      )
      if (apkAsset?.browser_download_url) {
        return apkAsset.browser_download_url
      }
    }
  } catch {
    return null
  }

  return null
}

function apkResponse(apkPath: string) {
  const stat = statSync(apkPath)
  const body = readFileSync(apkPath)

  return new NextResponse(body, {
    status: 200,
    headers: {
      'Content-Type': 'application/vnd.android.package-archive',
      'Content-Disposition': `attachment; filename="${HOSTED_APK_FILENAME}"`,
      'Content-Length': String(stat.size),
      'Cache-Control': 'no-cache, no-store, must-revalidate',
    },
  })
}

function apkHeadResponse(apkPath: string) {
  const stat = statSync(apkPath)
  return new NextResponse(null, {
    status: 200,
    headers: {
      'Content-Type': 'application/vnd.android.package-archive',
      'Content-Length': String(stat.size),
      'Cache-Control': 'no-cache, no-store, must-revalidate',
    },
  })
}

export async function HEAD() {
  const apkPath = resolveApkPath()
  if (!apkPath) {
    const githubUrl = await resolveGithubApkUrl()
    if (githubUrl) {
      return new NextResponse(null, { status: 200 })
    }
    return new NextResponse(null, { status: 404 })
  }

  return apkHeadResponse(apkPath)
}

export async function GET(request: NextRequest) {
  const apkPath = resolveApkPath()
  if (apkPath) {
    return apkResponse(apkPath)
  }

  const githubUrl = await resolveGithubApkUrl()
  if (githubUrl) {
    return NextResponse.redirect(githubUrl)
  }

  return NextResponse.json(
    {
      error: 'APK not found on this server.',
      hint: `Upload ${HOSTED_APK_FILENAME} to web/public/downloads/ or set ANDROID_APK_PATH.`,
    },
    { status: 404 }
  )
}
