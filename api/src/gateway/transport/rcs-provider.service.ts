import { HttpException, HttpStatus, Injectable } from '@nestjs/common'
import { ConfigService } from '@nestjs/config'
import { MessageChannel } from '../message-channel.enum'
import { MockRcsProvider } from './providers/mock-rcs.provider'
import { RcsProvider, RcsSendRequest } from './rcs-provider.interface'
import { GoogleRbmProvider } from './providers/google-rbm.provider'

export type ChannelDispatchPlan = {
  requestedChannel: MessageChannel
  dispatchChannel: MessageChannel
  usesExternalRcsProvider: boolean
  fallbackReason?: string
}

@Injectable()
export class RcsProviderService {
  constructor(
    private readonly configService: ConfigService,
    private readonly mockRcsProvider: MockRcsProvider,
    private readonly googleRbmProvider: GoogleRbmProvider,
  ) {}

  createDispatchPlan(requestedChannel: MessageChannel): ChannelDispatchPlan {
    if (requestedChannel === MessageChannel.SMS) {
      return {
        requestedChannel,
        dispatchChannel: MessageChannel.SMS,
        usesExternalRcsProvider: false,
      }
    }

    // RCS from the dashboard is handed off on the device: the gateway app opens the
    // default messaging composer (same pipeline as SMS via FCM/pull). Rich messaging vs SMS is chosen by the user's messaging app when they send.
    return {
      requestedChannel,
      dispatchChannel: MessageChannel.RCS,
      usesExternalRcsProvider: false,
    }
  }

  private getConfiguredProvider(): RcsProvider {
    const providerName = this.configService.get<string>('RCS_PROVIDER_NAME', 'mock')

    if (providerName === 'mock') {
      return this.mockRcsProvider
    }

    if (providerName === 'google') {
      return this.googleRbmProvider
    }

    throw new HttpException(
      {
        success: false,
        error: `Unsupported server-side RCS provider '${providerName}'. Supported: mock, google.`,
      },
      HttpStatus.BAD_REQUEST,
    )
  }

  async sendViaExternalProvider(payload: RcsSendRequest) {
    const provider = this.getConfiguredProvider()
    const result = await provider.sendMessage(payload)

    return {
      success: result.success,
      message: result.message,
      provider: result.provider,
      providerMessageId: result.providerMessageId,
      acceptedCount: result.acceptedCount,
    }
  }
}
