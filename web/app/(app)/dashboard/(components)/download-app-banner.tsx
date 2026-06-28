'use client'

import { Download, Smartphone } from 'lucide-react'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { HOSTED_APK_FILENAME } from '@/config/android-download'
import { Routes } from '@/config/routes'
import { latestAppVersionCode } from './update-app-helpers'

export default function DownloadAppBanner() {
  return (
    <Alert className='border-brand-200 bg-brand-50/90 text-brand-950 shadow-sm dark:border-brand-800 dark:bg-brand-950/80 dark:text-brand-50'>
      <Smartphone className='h-4 w-4 text-brand-600 dark:text-brand-300' />
      <AlertDescription className='flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between'>
        <div className='pr-2 text-sm'>
          <span className='font-medium'>Download RCS Blink for Android</span>
          <span className='text-muted-foreground'>
            {' '}
            — install the official gateway app on your phone (v{latestAppVersionCode}).
          </span>
        </div>
        <Button asChild size='sm' className='w-full shrink-0 sm:w-auto'>
          <a href={Routes.downloadAndroidApp} download={HOSTED_APK_FILENAME}>
            <Download className='mr-2 h-4 w-4' />
            Download App
          </a>
        </Button>
      </AlertDescription>
    </Alert>
  )
}
