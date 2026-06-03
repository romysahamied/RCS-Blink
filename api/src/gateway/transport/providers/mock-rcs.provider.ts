import { Injectable } from '@nestjs/common'
import {
  RcsProvider,
  RcsSendRequest,
  RcsSendResult,
} from '../rcs-provider.interface'

@Injectable()
export class MockRcsProvider implements RcsProvider {
  readonly name = 'mock'

  async sendMessage(payload: RcsSendRequest): Promise<RcsSendResult> {
    return {
      success: true,
      provider: this.name,
      acceptedCount: payload.recipients.length,
      message: 'Mock RCS provider accepted message (no real carrier delivery).',
      providerMessageId: `mock-${Date.now()}`,
    }
  }
}
