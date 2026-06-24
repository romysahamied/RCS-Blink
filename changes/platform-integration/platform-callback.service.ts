import { Injectable, Logger } from '@nestjs/common'
import { InjectModel } from '@nestjs/mongoose'
import axios from 'axios'
import { Model } from 'mongoose'
import {
  PlatformMessage,
  PlatformMessageDocument,
} from './schemas/platform-message.schema'
import {
  PlatformDlrStatus,
  PlatformOperationCode,
} from './platform-dlr.enum'
import { PlatformDlrCallbackPayloadDto } from './platform-integration.dto'
import { SMS } from 'gateway/schemas/sms.schema'

@Injectable()
export class PlatformCallbackService {
  private readonly logger = new Logger(PlatformCallbackService.name)

  constructor(
    @InjectModel(PlatformMessage.name)
    private platformMessageModel: Model<PlatformMessageDocument>,
  ) {}

  mapSmsStatusToDlr(
    status: string,
  ): { dlrStatus: PlatformDlrStatus; remarks: string } {
    const normalized = String(status || '').toLowerCase()

    switch (normalized) {
      case 'pending':
      case 'dispatched':
        return {
          dlrStatus: PlatformDlrStatus.Enroute,
          remarks: 'Dlr Status is : Enroute',
        }
      case 'sent':
        return {
          dlrStatus: PlatformDlrStatus.Accepted,
          remarks: 'Dlr Status is : Accepted',
        }
      case 'delivered':
        return {
          dlrStatus: PlatformDlrStatus.Delivered,
          remarks: 'Dlr Status is : Delivered',
        }
      case 'failed':
        return {
          dlrStatus: PlatformDlrStatus.Undeliverable,
          remarks: 'Dlr Status is : Undeliverable',
        }
      case 'received':
        return {
          dlrStatus: PlatformDlrStatus.Delivered,
          remarks: 'Dlr Status is : Delivered',
        }
      default:
        return {
          dlrStatus: PlatformDlrStatus.Unknown,
          remarks: 'Dlr Status is : Unknown',
        }
    }
  }

  async deliverDlrForSms(sms: SMS): Promise<void> {
    if (!sms?._id) return

    const platformMessage = await this.platformMessageModel.findOne({
      sms: sms._id,
    })

    if (!platformMessage?.callBackUrl) return

    const { dlrStatus, remarks } = this.mapSmsStatusToDlr(sms.status)

    if (platformMessage.lastDlrStatus === dlrStatus) return

    const payload: PlatformDlrCallbackPayloadDto = {
      messageId: platformMessage.messageId,
      operationCode: PlatformOperationCode.Success,
      status: 'Success',
      dlrStatus,
      userReferenceId: platformMessage.userReferenceId ?? null,
      destinationAddress: this.toCallbackDestination(
        platformMessage.destinationAddress,
      ),
      remarks,
      callBackUrl: platformMessage.callBackUrl ?? null,
    }

    try {
      await axios.post(platformMessage.callBackUrl, payload, {
        timeout: 15000,
        headers: { 'Content-Type': 'application/json' },
        validateStatus: () => true,
      })
      await this.platformMessageModel.updateOne(
        { _id: platformMessage._id },
        { $set: { lastDlrStatus: dlrStatus } },
      )
    } catch (error) {
      this.logger.warn(
        `Platform DLR callback failed for messageId=${platformMessage.messageId}: ${error?.message || error}`,
      )
    }
  }

  private toCallbackDestination(address: string): string | number {
    const digits = String(address).replace(/\D/g, '')
    if (/^\d+$/.test(digits) && digits.length <= 15) {
      const asNumber = Number(digits)
      if (Number.isSafeInteger(asNumber)) return asNumber
    }
    return address
  }
}
