import { HttpException, HttpStatus, Injectable } from '@nestjs/common'
import { InjectModel } from '@nestjs/mongoose'
import * as bcrypt from 'bcryptjs'
import { Model, Types } from 'mongoose'
import { AuthService } from '../auth/auth.service'
import { Device, DeviceDocument } from '../gateway/schemas/device.schema'
import { GatewayService } from '../gateway/gateway.service'
import { MessageChannel } from '../gateway/message-channel.enum'
import { SMS } from '../gateway/schemas/sms.schema'
import { User, UserDocument } from '../users/schemas/user.schema'
import { SMSType } from '../gateway/sms-type.enum'
import { PlatformOperationCode } from './platform-dlr.enum'
import {
  PlatformSendMessageDto,
  PlatformSendMessageResponseDto,
} from './platform-integration.dto'
import {
  PlatformMessage,
  PlatformMessageCounter,
  PlatformMessageCounterDocument,
  PlatformMessageDocument,
} from './schemas/platform-message.schema'

@Injectable()
export class PlatformIntegrationService {
  constructor(
    @InjectModel(PlatformMessage.name)
    private platformMessageModel: Model<PlatformMessageDocument>,
    @InjectModel(PlatformMessageCounter.name)
    private counterModel: Model<PlatformMessageCounterDocument>,
    @InjectModel(Device.name) private deviceModel: Model<DeviceDocument>,
    @InjectModel(SMS.name) private smsModel: Model<SMS>,
    @InjectModel(User.name) private userModel: Model<UserDocument>,
    private authService: AuthService,
    private gatewayService: GatewayService,
  ) {}

  async sendMessage(
    dto: PlatformSendMessageDto,
    deviceIdOverride?: string,
    channelOverride?: string,
  ): Promise<PlatformSendMessageResponseDto> {
    const user = await this.resolveUserFromApiToken(dto.apiToken)
    if (!user) {
      return this.errorResponse(dto, PlatformOperationCode.Unauthorized, 'Unauthorized')
    }

    const device = await this.resolveDevice(user, deviceIdOverride)
    if (!device) {
      return this.errorResponse(
        dto,
        PlatformOperationCode.NoDevice,
        'No enabled gateway device found',
      )
    }

    const destinationAddress = this.normalizeDestination(dto.destinationAddress)
    const messageType = Number(dto.messageType)
    const messageEncoding = Number(dto.messageEncoding)
    const channel = this.resolveChannel(messageType, channelOverride)

    let sendResult: { smsBatchId?: Types.ObjectId; success?: boolean }
    try {
      sendResult = await this.gatewayService.sendSMS(device._id.toString(), {
        message: dto.messageText,
        recipients: [destinationAddress],
        channel,
        smsBody: dto.messageText,
        receivers: [destinationAddress],
      })
    } catch (error) {
      const remarks =
        error instanceof HttpException
          ? this.extractHttpErrorMessage(error)
          : 'Failed to submit message'
      return this.errorResponse(
        dto,
        PlatformOperationCode.SendFailed,
        remarks,
        destinationAddress,
      )
    }

    const smsBatchId = await this.resolveSmsBatchId(
      sendResult,
      device._id,
      destinationAddress,
    )

    if (!this.isSendSuccessful(sendResult, smsBatchId)) {
      return this.errorResponse(
        dto,
        PlatformOperationCode.SendFailed,
        'Failed to submit message',
        destinationAddress,
      )
    }

    const batchMessages = await this.smsModel
      .find({ smsBatch: smsBatchId })
      .sort({ createdAt: -1 })
    const sms =
      batchMessages.find((row) =>
        this.isSamePhoneNumber(row.recipient, destinationAddress),
      ) ?? batchMessages[0]

    const messageId = await this.nextMessageId()

    await this.platformMessageModel.create({
      messageId,
      sms: sms?._id,
      user: user._id,
      device: device._id,
      destinationAddress,
      sourceAddress: dto.sourceAddress.trim(),
      messageType,
      messageEncoding,
      userReferenceId: dto.userReferenceId?.trim(),
      callBackUrl: dto.callBackUrl?.trim() || undefined,
      channel,
      lastDlrStatus: 0,
    })

    if (sms?._id) {
      await this.smsModel.updateOne(
        { _id: sms._id },
        {
          $set: {
            metadata: {
              ...(sms.metadata || {}),
              platform: {
                messageId,
                messageType,
                messageEncoding,
                channel,
                sourceAddress: dto.sourceAddress.trim(),
                userReferenceId: dto.userReferenceId?.trim(),
                callBackUrl: dto.callBackUrl?.trim(),
              },
            },
          },
        },
      )
    }

    return {
      MessageId: messageId,
      OperationCode: PlatformOperationCode.Success,
      Status: 'Success',
      DlrStatus: null,
      UserReferenceId: dto.userReferenceId?.trim() ?? null,
      DestinationAddress: destinationAddress,
      Remarks:
        channel === MessageChannel.RCS
          ? 'RCS request accepted — final delivery depends on recipient RCS support (see DLR callback)'
          : 'Message Submitted',
      CallBackUrl: dto.callBackUrl?.trim() ?? null,
    }
  }

  private async resolveUserFromApiToken(
    apiToken: string,
  ): Promise<UserDocument | null> {
    const token = String(apiToken || '').trim()
    if (!token) return null

    const regex = new RegExp(`^${token.substr(0, 17)}`, 'g')
    const apiKey = await this.authService.findApiKey({
      apiKey: { $regex: regex },
      $or: [{ revokedAt: null }, { revokedAt: { $exists: false } }],
    })

    if (!apiKey || !bcrypt.compareSync(token, apiKey.hashedApiKey)) {
      return null
    }

    return this.userModel.findById(apiKey.user)
  }

  private async resolveDevice(
    user: UserDocument,
    deviceIdOverride?: string,
  ): Promise<DeviceDocument | null> {
    const envDeviceId = process.env.PLATFORM_DEFAULT_DEVICE_ID?.trim()
    const preferredId = deviceIdOverride?.trim() || envDeviceId

    if (preferredId && Types.ObjectId.isValid(preferredId)) {
      const device = await this.deviceModel.findOne({
        _id: preferredId,
        user: user._id,
        enabled: true,
      })
      if (device) return device
    }

    return this.deviceModel
      .findOne({ user: user._id, enabled: true })
      .sort({ lastHeartbeat: -1, updatedAt: -1 })
      .exec()
  }

  private resolveChannel(
    messageType: number,
    channelOverride?: string,
  ): MessageChannel {
    const override = channelOverride?.trim().toLowerCase()
    if (override === MessageChannel.SMS) return MessageChannel.SMS
    if (override === MessageChannel.RCS) return MessageChannel.RCS

    const defaultChannel = (
      process.env.PLATFORM_DEFAULT_CHANNEL || MessageChannel.RCS
    )
      .trim()
      .toLowerCase()

    const smsTypes = (process.env.PLATFORM_SMS_MESSAGE_TYPES || '')
      .split(',')
      .map((v) => Number(v.trim()))
      .filter((v) => !Number.isNaN(v))

    if (smsTypes.includes(messageType)) {
      return MessageChannel.SMS
    }

    if (defaultChannel === MessageChannel.SMS) {
      return MessageChannel.SMS
    }

    return MessageChannel.RCS
  }

  private isSendSuccessful(
    sendResult: { success?: boolean; successCount?: number } | null | undefined,
    smsBatchId?: Types.ObjectId,
  ): boolean {
    if (!sendResult) return false
    if (sendResult.success === true && smsBatchId) return true
    if (
      typeof sendResult.successCount === 'number' &&
      sendResult.successCount > 0 &&
      smsBatchId
    ) {
      return true
    }
    return false
  }

  private async resolveSmsBatchId(
    sendResult: { smsBatchId?: Types.ObjectId; successCount?: number } | null | undefined,
    deviceId: Types.ObjectId,
    destinationAddress: string,
  ): Promise<Types.ObjectId | undefined> {
    if (sendResult?.smsBatchId) {
      return sendResult.smsBatchId
    }

    const recentMessages = await this.smsModel
      .find({
        device: deviceId,
        type: SMSType.SENT,
        createdAt: { $gte: new Date(Date.now() - 120_000) },
      })
      .sort({ createdAt: -1 })
      .limit(10)

    const matched = recentMessages.find((row) =>
      this.isSamePhoneNumber(row.recipient, destinationAddress),
    )

    return matched?.smsBatch as Types.ObjectId | undefined
  }

  private normalizeDestination(destination: string): string {
    const raw = String(destination).replace(/\s+/g, '')
    const digits = raw.replace(/\D/g, '')
    if (!digits) {
      throw new HttpException('Invalid destinationAddress', HttpStatus.BAD_REQUEST)
    }
    if (raw.startsWith('+')) {
      return `+${digits}`
    }
    if (digits.startsWith('00')) {
      return `+${digits.slice(2)}`
    }
    if (digits.length === 10) {
      return `+91${digits}`
    }
    return `+${digits}`
  }

  private isSamePhoneNumber(a?: string, b?: string): boolean {
    if (!a || !b) return false
    return a.replace(/\D/g, '') === b.replace(/\D/g, '')
  }

  private async nextMessageId(): Promise<number> {
    const counter = await this.counterModel.findOneAndUpdate(
      { name: 'platform_message' },
      { $inc: { seq: 1 } },
      { new: true, upsert: true },
    )
    return counter.seq
  }

  private errorResponse(
    dto: PlatformSendMessageDto,
    operationCode: PlatformOperationCode,
    remarks: string,
    destinationAddress?: string,
  ): PlatformSendMessageResponseDto {
    let dest = destinationAddress
    try {
      if (!dest && dto.destinationAddress) {
        dest = this.normalizeDestination(dto.destinationAddress)
      }
    } catch {
      dest = dto.destinationAddress
    }

    return {
      MessageId: 0,
      OperationCode: operationCode,
      Status: 'Failed',
      DlrStatus: null,
      UserReferenceId: dto.userReferenceId?.trim() ?? null,
      DestinationAddress: dest || dto.destinationAddress || '',
      Remarks: remarks,
      CallBackUrl: dto.callBackUrl?.trim() ?? null,
    }
  }

  private extractHttpErrorMessage(error: HttpException): string {
    const response = error.getResponse()
    if (typeof response === 'string') return response
    if (response && typeof response === 'object') {
      const body = response as Record<string, unknown>
      if (typeof body.error === 'string') return body.error
      if (typeof body.message === 'string') return body.message
    }
    return error.message || 'Request failed'
  }
}
