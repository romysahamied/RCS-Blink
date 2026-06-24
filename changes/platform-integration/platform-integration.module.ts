import { Module } from '@nestjs/common'
import { MongooseModule } from '@nestjs/mongoose'
import { AuthModule } from 'auth/auth.module'
import { GatewayModule } from 'gateway/gateway.module'
import { Device, DeviceSchema } from 'gateway/schemas/device.schema'
import { SMS, SMSSchema } from 'gateway/schemas/sms.schema'
import { User, UserSchema } from 'users/schemas/user.schema'
import { PlatformCallbackService } from './platform-callback.service'
import { PlatformIntegrationController } from './platform-integration.controller'
import { PlatformIntegrationService } from './platform-integration.service'
import { PlatformSmsStatusListener } from './platform-sms-status.listener'
import {
  PlatformMessage,
  PlatformMessageCounter,
  PlatformMessageCounterSchema,
  PlatformMessageSchema,
} from './schemas/platform-message.schema'

@Module({
  imports: [
    MongooseModule.forFeature([
      { name: PlatformMessage.name, schema: PlatformMessageSchema },
      { name: PlatformMessageCounter.name, schema: PlatformMessageCounterSchema },
      { name: Device.name, schema: DeviceSchema },
      { name: SMS.name, schema: SMSSchema },
      { name: User.name, schema: UserSchema },
    ]),
    AuthModule,
    GatewayModule,
  ],
  controllers: [PlatformIntegrationController],
  providers: [
    PlatformIntegrationService,
    PlatformCallbackService,
    PlatformSmsStatusListener,
  ],
  exports: [PlatformCallbackService],
})
export class PlatformIntegrationModule {}
