import { Injectable } from '@nestjs/common'
import { OnEvent } from '@nestjs/event-emitter'
import { PlatformCallbackService } from './platform-callback.service'
import { SMS } from 'gateway/schemas/sms.schema'

@Injectable()
export class PlatformSmsStatusListener {
  constructor(private readonly platformCallbackService: PlatformCallbackService) {}

  @OnEvent('sms.status.updated', { async: true })
  async handleSmsStatusUpdated(sms: SMS): Promise<void> {
    await this.platformCallbackService.deliverDlrForSms(sms)
  }
}
