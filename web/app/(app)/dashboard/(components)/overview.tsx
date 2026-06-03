'use client'

import type { ComponentType } from 'react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { BarChart3, Smartphone, Key, MessageSquare, TrendingUp } from 'lucide-react'
import GetStartedCard from './get-started'
import { ApiEndpoints } from '@/config/api'
import httpBrowserClient from '@/lib/httpBrowserClient'
import { useQuery } from '@tanstack/react-query'
import { Skeleton } from '@/components/ui/skeleton'
import { formatError } from '@/lib/utils/errorHandler'
import { Button } from '@/components/ui/button'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'

type StatCardProps = {
  title: string
  value: string | number | undefined
  icon: ComponentType<{ className?: string }>
  description: string
  showLoader: boolean
  showError: boolean
}

export const StatCard = ({
  title,
  value,
  icon: Icon,
  description,
  showLoader,
  showError,
}: StatCardProps) => {
  return (
    <Card className='overflow-hidden transition-all hover:shadow-md'>
      <CardHeader className='flex flex-row items-center justify-between space-y-0 pb-2'>
        <CardTitle className='text-sm font-medium'>{title}</CardTitle>
        <div className='rounded-full bg-primary/10 p-2'>
          <Icon className='h-4 w-4 text-primary' />
        </div>
      </CardHeader>
      <CardContent>
        <div className='text-2xl font-bold'>
          {showError ? (
            <span className='text-sm font-normal text-destructive'>—</span>
          ) : value !== undefined ? (
            value
          ) : showLoader ? (
            <Skeleton className='h-6 w-16' />
          ) : (
            <span className='text-muted-foreground'>—</span>
          )}
        </div>
        <p className='text-xs text-muted-foreground mt-1 flex items-center'>
          {description}
          {!showError && value !== undefined && (
            <TrendingUp className='ml-1 h-3 w-3 text-green-500' />
          )}
        </p>
      </CardContent>
    </Card>
  )
}

export default function Overview() {
  const {
    data: stats,
    isPending,
    isError,
    error,
    refetch,
    isFetching,
  } = useQuery({
    queryKey: ['stats'],
    queryFn: () =>
      httpBrowserClient
        .get(ApiEndpoints.gateway.getStats())
        .then((res) => res.data?.data),
  })

  const statsFailed = isError
  const statsLoading =
    !statsFailed && !stats && (isPending || isFetching)

  return (
    <div className='space-y-6'>
      <GetStartedCard />
      {statsFailed && (
        <Alert variant='destructive'>
          <AlertTitle>Could not load dashboard stats</AlertTitle>
          <AlertDescription className='flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between'>
            <span className='text-sm'>{formatError(error).message}</span>
            <Button type='button' size='sm' variant='outline' onClick={() => refetch()}>
              Retry
            </Button>
          </AlertDescription>
        </Alert>
      )}
      <div className='grid gap-4 md:grid-cols-2 lg:grid-cols-4'>
        <StatCard
          title='Total SMS Sent'
          value={stats?.totalSentSMSCount?.toLocaleString()}
          icon={MessageSquare}
          description='Since last year'
          showLoader={statsLoading && !statsFailed}
          showError={statsFailed}
        />
        <StatCard
          title='Active Devices'
          value={stats?.totalDeviceCount}
          icon={Smartphone}
          description='Connected now'
          showLoader={statsLoading && !statsFailed}
          showError={statsFailed}
        />
        <StatCard
          title='API Keys'
          value={stats?.totalApiKeyCount}
          icon={Key}
          description='Active keys'
          showLoader={statsLoading && !statsFailed}
          showError={statsFailed}
        />
        <StatCard
          title='SMS Received'
          value={stats?.totalReceivedSMSCount?.toLocaleString()}
          icon={BarChart3}
          description='Since last year'
          showLoader={statsLoading && !statsFailed}
          showError={statsFailed}
        />
      </div>
    </div>
  )
}
