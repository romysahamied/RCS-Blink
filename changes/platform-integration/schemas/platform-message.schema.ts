import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose'
import { Document, Types } from 'mongoose'
import { SMS } from 'gateway/schemas/sms.schema'
import { User } from 'users/schemas/user.schema'
import { Device } from 'gateway/schemas/device.schema'

export type PlatformMessageDocument = PlatformMessage & Document

@Schema({ timestamps: true })
export class PlatformMessage {
  _id?: Types.ObjectId

  @Prop({ type: Number, required: true, unique: true, index: true })
  messageId: number

  @Prop({ type: Types.ObjectId, ref: SMS.name, index: true })
  sms?: Types.ObjectId

  @Prop({ type: Types.ObjectId, ref: User.name, required: true, index: true })
  user: Types.ObjectId

  @Prop({ type: Types.ObjectId, ref: Device.name, required: true })
  device: Types.ObjectId

  @Prop({ type: String, required: true })
  destinationAddress: string

  @Prop({ type: String, required: true })
  sourceAddress: string

  @Prop({ type: Number, required: true })
  messageType: number

  @Prop({ type: Number, required: true })
  messageEncoding: number

  @Prop({ type: String })
  userReferenceId?: string

  @Prop({ type: String })
  callBackUrl?: string

  @Prop({ type: String, default: 'rcs' })
  channel: string

  @Prop({ type: Number, default: 0 })
  lastDlrStatus: number
}

export const PlatformMessageSchema = SchemaFactory.createForClass(PlatformMessage)

@Schema({ collection: 'platform_message_counters' })
export class PlatformMessageCounter {
  @Prop({ type: String, required: true, unique: true })
  name: string

  @Prop({ type: Number, default: 0 })
  seq: number
}

export type PlatformMessageCounterDocument = PlatformMessageCounter & Document

export const PlatformMessageCounterSchema =
  SchemaFactory.createForClass(PlatformMessageCounter)
