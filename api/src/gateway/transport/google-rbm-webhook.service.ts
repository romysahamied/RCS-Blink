import { HttpException, HttpStatus, Injectable } from '@nestjs/common'
import { EventEmitter2 } from '@nestjs/event-emitter'
import { InjectModel } from '@nestjs/mongoose'
import { Model, Types } from 'mongoose'
import { ConfigService } from '@nestjs/config'
import { SMS } from '../schemas/sms.schema'
import { SMSBatch } from '../schemas/sms-batch.schema'

@Injectable()
export class GoogleRbmWebhookService {
  constructor(
    @InjectModel(SMS.name) private smsModel: Model<SMS>,
    @InjectModel(SMSBatch.name) private smsBatchModel: Model<SMSBatch>,
    private readonly configService: ConfigService,
    private readonly eventEmitter: EventEmitter2,
  ) {}

  verifySignature(signature: string | undefined): void {
    const expected = this.configService.get<string>('GOOGLE_RBM_WEBHOOK_SECRET', '')
    if (!expected) {
      return
    }
    if (!signature || signature !== expected) {
      throw new HttpException(
        { success: false, error: 'Invalid Google RBM webhook signature' },
        HttpStatus.UNAUTHORIZED,
      )
    }
  }

  async processStatusEvent(payload: any): Promise<{ success: boolean; matched: number }> {
    const providerMessageId =
      payload?.providerMessageId ||
      payload?.messageId ||
      payload?.event?.providerMessageId ||
      payload?.event?.messageId

    if (!providerMessageId) {
      throw new HttpException(
        { success: false, error: 'Missing providerMessageId/messageId in webhook payload' },
        HttpStatus.BAD_REQUEST,
      )
    }

    const rawStatus = String(
      payload?.status || payload?.event?.status || payload?.deliveryStatus || '',
    ).toLowerCase()

    const now = new Date()
    const statusPatch: Record<string, any> = {
      $set: {
        'metadata.rbmLastWebhookAt': now,
        'metadata.rbmLastWebhookPayload': payload,
      },
    }

    // Placeholder mapping. Replace with exact RBM event mapping once wired to real events.
    if (rawStatus === 'sent' || rawStatus === 'submitted' || rawStatus === 'accepted') {
      statusPatch.$set.status = 'sent'
      statusPatch.$set.sentAt = now
    } else if (rawStatus === 'delivered') {
      statusPatch.$set.status = 'delivered'
      statusPatch.$set.deliveredAt = now
    } else if (rawStatus === 'failed' || rawStatus === 'undelivered') {
      statusPatch.$set.status = 'failed'
      statusPatch.$set.failedAt = now
      statusPatch.$set.errorCode = payload?.errorCode || 'RCS_PROVIDER_FAILED'
      statusPatch.$set.errorMessage = payload?.errorMessage || 'RCS provider delivery failed'
    } else {
      statusPatch.$set['metadata.rbmUnmappedStatus'] = rawStatus || 'unknown'
    }

    const result = await this.smsModel.updateMany(
      { 'metadata.providerMessageId': providerMessageId },
      statusPatch,
    )

    if (result.modifiedCount > 0) {
      const matchedSms = await this.smsModel.find({
        'metadata.providerMessageId': providerMessageId,
      })

      for (const sms of matchedSms) {
        try {
          this.eventEmitter.emit('sms.status.updated', sms)
        } catch (error) {
          console.error('Failed to emit sms.status.updated from RBM webhook:', error)
        }
      }

      const batchIds = Array.from(
        new Set(
          matchedSms
            .map((sms) => sms.smsBatch)
            .filter(Boolean)
            .map((id) => new Types.ObjectId(id as any).toString()),
        ),
      )

      for (const batchId of batchIds) {
        await this.smsBatchModel.findByIdAndUpdate(batchId, {
          $set: {
            status:
              rawStatus === 'failed' || rawStatus === 'undelivered'
                ? 'partial_success'
                : 'processing',
            updatedAt: now,
          },
        })
      }
    }

    return { success: true, matched: result.modifiedCount }
  }
}
