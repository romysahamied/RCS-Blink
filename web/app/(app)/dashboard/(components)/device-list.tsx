'use client'

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Smartphone,
  Battery,
  Signal,
  Copy,
  Plus,
  ExternalLink,
  Trash2,
} from 'lucide-react'
import { useToast } from '@/hooks/use-toast'
import httpBrowserClient from '@/lib/httpBrowserClient'
import { ApiEndpoints } from '@/config/api'
import { Routes } from '@/config/routes'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { formatError } from '@/lib/utils/errorHandler'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDeviceName } from '@/lib/utils'
import GenerateApiKey, {
  type GenerateApiKeyHandle,
} from './generate-api-key'
import {
  DeviceVersionCandidate,
  getDeviceVersionCode,
  isDeviceOutdated,
  latestAppVersionCode,
} from './update-app-helpers'

export default function DeviceList() {
  const addDeviceKeyRef = useRef<GenerateApiKeyHandle>(null)
  const [addDeviceInstructionOpen, setAddDeviceInstructionOpen] =
    useState(false)
  const [devicePendingDelete, setDevicePendingDelete] = useState<{
    _id: string
    label: string
  } | null>(null)
  const queryClient = useQueryClient()
  const { toast } = useToast()
  const {
    isPending,
    error,
    data: devices,
  } = useQuery({
    queryKey: ['devices'],
    queryFn: () =>
      httpBrowserClient
        .get(ApiEndpoints.gateway.listDevices())
        .then((res) => res.data),
    // select: (res) => res.data,
  })

  const deleteDeviceMutation = useMutation({
    mutationKey: ['delete-gateway-device'],
    mutationFn: (deviceId: string) =>
      httpBrowserClient.delete(ApiEndpoints.gateway.deleteDevice(deviceId)),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['devices'] })
      toast({ title: 'Device removed' })
      setDevicePendingDelete(null)
    },
    onError: (err: unknown) => {
      const { message } = formatError(err)
      toast({
        title: 'Could not remove device',
        description: message,
        variant: 'destructive',
      })
    },
  })

  const handleCopyId = (id: string) => {
    navigator.clipboard.writeText(id)
    toast({
      title: 'Device ID copied to clipboard',
    })
  }

  return (
    <>
      <GenerateApiKey ref={addDeviceKeyRef} showTrigger={false} />
      <Card>
        <CardHeader className='flex flex-row items-center justify-between space-y-0 pb-2'>
          <CardTitle className='text-lg'>Registered Devices</CardTitle>
          <Button
            variant='outline'
            size='sm'
            onClick={() => setAddDeviceInstructionOpen(true)}
          >
            <Plus className='mr-1 h-4 w-4' />
            Add device
          </Button>
        </CardHeader>
      <CardContent>
          <div className='space-y-2'>
            {isPending && (
              <>
                {[1, 2, 3].map((i) => (
                  <Card key={i} className='border-0 shadow-none'>
                    <CardContent className='flex items-center p-3'>
                      <Skeleton className='h-6 w-6 rounded-full mr-3' />
                      <div className='flex-1'>
                        <div className='flex items-center justify-between'>
                          <Skeleton className='h-4 w-[120px]' />
                          <Skeleton className='h-4 w-[60px]' />
                        </div>
                        <div className='flex items-center space-x-2 mt-1'>
                          <Skeleton className='h-4 w-[180px]' />
                        </div>
                        <div className='flex items-center mt-1 space-x-3'>
                          <Skeleton className='h-3 w-[200px]' />
                        </div>
                      </div>
                    </CardContent>
                  </Card>
                ))}
              </>
            )}

            {error && (
              <div className='flex justify-center items-center h-full'>
                <div>Error: {error.message}</div>
              </div>
            )}

            {!isPending && !error && devices?.data?.length === 0 && (
              <div className='flex justify-center items-center h-full'>
                <div>No devices found</div>
              </div>
            )}

            {devices?.data?.map((device) => (
              <Card key={device._id} className='border-0 shadow-none'>
                <CardContent className='flex items-center p-3'>
                  <Smartphone className='h-6 w-6 mr-3' />
                  <div className='flex-1'>
                    <div className='flex items-center justify-between gap-2'>
                      <h3 className='font-semibold text-sm truncate'>
                        {formatDeviceName(device)}
                      </h3>
                      <div className='flex items-center gap-2 shrink-0'>
                        {isDeviceOutdated(device as DeviceVersionCandidate) && (
                          <Badge
                            variant='outline'
                            className='border-purple-300 bg-purple-50 text-purple-700'
                          >
                            Update available
                          </Badge>
                        )}
                        <Badge
                          variant={
                            device.status === 'online' ? 'default' : 'secondary'
                          }
                          className='text-xs'
                        >
                          {device.enabled ? 'Enabled' : 'Disabled'}
                        </Badge>
                        <Button
                          type='button'
                          variant='ghost'
                          size='icon'
                          className='h-8 w-8 text-muted-foreground hover:text-destructive'
                          title='Remove device'
                          onClick={() =>
                            setDevicePendingDelete({
                              _id: device._id,
                              label: formatDeviceName(device),
                            })
                          }
                          disabled={deleteDeviceMutation.isPending}
                        >
                          <Trash2 className='h-4 w-4' />
                        </Button>
                      </div>
                    </div>
                    <div className='flex items-center space-x-2 mt-1'>
                      <code className='relative rounded bg-muted px-[0.3rem] py-[0.2rem] font-mono text-xs'>
                        {device._id}
                      </code>
                      <Button
                        variant='ghost'
                        size='icon'
                        className='h-6 w-6'
                        onClick={() => handleCopyId(device._id)}
                      >
                        <Copy className='h-3 w-3' />
                      </Button>
                    </div>
                    <div className='flex items-center mt-1 space-x-3 text-xs text-muted-foreground'>
                      <div className='flex items-center'>
                        <Battery className='h-3 w-3 mr-1' />
                        unknown
                      </div>
                      <div className='flex items-center'>
                        <Signal className='h-3 w-3 mr-1' />-
                      </div>
                      <div>
                        App version:{' '}
                        {getDeviceVersionCode(device as DeviceVersionCandidate) ??
                          'unknown'}
                      </div>
                      <div>
                        Registered at:{' '}
                        {new Date(device.createdAt).toLocaleString('en-US', {
                          dateStyle: 'medium',
                          timeStyle: 'short',
                        })}
                      </div>
                    </div>
                    {isDeviceOutdated(device as DeviceVersionCandidate) && (
                      <div className='mt-3 flex items-center justify-between gap-2 rounded-lg border border-brand-100 bg-brand-50/60 px-3 py-2 dark:border-brand-900/50 dark:bg-brand-950/20'>
                        <p className='text-xs text-muted-foreground'>
                          This device is behind the latest supported version{' '}
                          <span className='font-medium text-foreground'>
                            {latestAppVersionCode}
                          </span>
                          .
                        </p>
                        <Button
                          variant='outline'
                          size='sm'
                          asChild
                          className='shrink-0'
                        >
                          <a
                            href={Routes.downloadAndroidApp}
                            target='_blank'
                            rel='noreferrer'
                          >
                            Update app
                          </a>
                        </Button>
                      </div>
                    )}
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
      </CardContent>
      </Card>

      <AlertDialog
        open={devicePendingDelete !== null}
        onOpenChange={(open) => {
          if (!open) setDevicePendingDelete(null)
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Remove this device?</AlertDialogTitle>
            <AlertDialogDescription>
              {devicePendingDelete
                ? `“${devicePendingDelete.label}” will be unlinked from your account. You can register it again later with a new API key.`
                : null}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleteDeviceMutation.isPending}>
              Cancel
            </AlertDialogCancel>
            <AlertDialogAction
              className='bg-destructive text-destructive-foreground hover:bg-destructive/90'
              disabled={deleteDeviceMutation.isPending}
              onClick={(e) => {
                e.preventDefault()
                if (devicePendingDelete) {
                  deleteDeviceMutation.mutate(devicePendingDelete._id)
                }
              }}
            >
              {deleteDeviceMutation.isPending ? 'Removing…' : 'Remove device'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <Dialog
        open={addDeviceInstructionOpen}
        onOpenChange={setAddDeviceInstructionOpen}
      >
        <DialogContent className='sm:max-w-md'>
          <DialogHeader>
            <DialogTitle>Add a device</DialogTitle>
            <DialogDescription className='text-left'>
              Register a new device by scanning the QR code or pasting the API key.
            </DialogDescription>
          </DialogHeader>
          <ol className='list-decimal space-y-3 pl-5 text-left text-sm text-muted-foreground'>
            <li>
              Download RCS Blink app from{' '}
              <a
                href={Routes.downloadAndroidApp}
                target='_blank'
                rel='noreferrer'
                className='font-medium text-primary underline-offset-4 hover:underline'
              >
                {Routes.downloadAndroidApp}
              </a>
              , install it, and grant SMS permissions.
            </li>
            <li>
              Tap Continue to create a new API key and get a QR
              code in the next dialog. If you already have an active API key, you can paste it in the
              app instead
            </li>
            <li>
              Open the RCS Blink app and scan the QR code or paste the key manually. Your device should appear in the list when the link succeeds.
            </li>
          </ol>
          <DialogFooter className='flex-col gap-2 sm:flex-row sm:justify-between'>
            <Button variant='outline' size='sm' asChild>
              <a href={Routes.quickstart} target='_blank' rel='noreferrer'>
                Full guide
                <ExternalLink className='ml-1 h-3 w-3' />
              </a>
            </Button>
            <div className='flex w-full gap-2 sm:w-auto'>
              <Button
                variant='outline'
                size='sm'
                className='flex-1 sm:flex-none'
                onClick={() => setAddDeviceInstructionOpen(false)}
              >
                Cancel
              </Button>
              <Button
                size='sm'
                className='flex-1 sm:flex-none'
                onClick={() => {
                  setAddDeviceInstructionOpen(false)
                  addDeviceKeyRef.current?.open()
                }}
              >
                Continue
              </Button>
            </div>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
