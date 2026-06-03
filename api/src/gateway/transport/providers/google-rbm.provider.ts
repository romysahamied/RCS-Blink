import { HttpException, HttpStatus, Injectable } from '@nestjs/common'
import { ConfigService } from '@nestjs/config'
import axios from 'axios'
import {
  RcsProvider,
  RcsSendRequest,
  RcsSendResult,
} from '../rcs-provider.interface'

@Injectable()
export class GoogleRbmProvider implements RcsProvider {
  readonly name = 'google'

  constructor(private readonly configService: ConfigService) {}

  async sendMessage(payload: RcsSendRequest): Promise<RcsSendResult> {
    const baseUrl = this.configService.get<string>('GOOGLE_RBM_BASE_URL', '').trim()
    const apiKey = this.configService.get<string>('GOOGLE_RBM_API_KEY', '').trim()
    const agentId = this.configService.get<string>('GOOGLE_RBM_AGENT_ID', '').trim()
    const timeoutMs = this.configService.get<number>('GOOGLE_RBM_TIMEOUT_MS', 10000)

    if (!baseUrl || !apiKey || !agentId) {
      throw new HttpException(
        {
          success: false,
          error:
            'Google RBM provider is selected but GOOGLE_RBM_BASE_URL/GOOGLE_RBM_API_KEY/GOOGLE_RBM_AGENT_ID are missing.',
        },
        HttpStatus.BAD_REQUEST,
      )
    }

    // Placeholder request shape. Replace with the exact Google RBM endpoint + payload.
    const endpoint = `${baseUrl.replace(/\/+$/, '')}/agents/${agentId}/messages:send`
    const requestBody = {
      to: payload.recipients,
      text: payload.message,
      metadata: {
        deviceId: payload.deviceId,
        simSubscriptionId: payload.simSubscriptionId,
      },
    }

    try {
      const response = await axios.post(endpoint, requestBody, {
        timeout: timeoutMs,
        headers: {
          Authorization: `Bearer ${apiKey}`,
          'Content-Type': 'application/json',
        },
      })

      const providerMessageId =
        response.data?.messageId || response.data?.name || response.data?.id

      return {
        success: true,
        provider: this.name,
        acceptedCount: payload.recipients.length,
        message: 'Google RBM provider accepted message request.',
        providerMessageId,
      }
    } catch (error: any) {
      throw new HttpException(
        {
          success: false,
          error: 'Google RBM provider send request failed.',
          additionalInfo: {
            status: error?.response?.status,
            data: error?.response?.data,
            message: error?.message,
          },
        },
        HttpStatus.BAD_GATEWAY,
      )
    }
  }
}
