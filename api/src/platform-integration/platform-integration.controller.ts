import { Body, Controller, HttpCode, HttpStatus, Post, Query } from '@nestjs/common'
import { ApiOperation, ApiQuery, ApiTags } from '@nestjs/swagger'
import {
  PlatformSendMessageDto,
  PlatformSendMessageResponseDto,
} from './platform-integration.dto'
import { MessageChannel } from '../gateway/message-channel.enum'
import { PlatformIntegrationService } from './platform-integration.service'

@ApiTags('platform')
@Controller('platform')
export class PlatformIntegrationController {
  constructor(
    private readonly platformIntegrationService: PlatformIntegrationService,
  ) {}

  @ApiOperation({
    summary:
      'Submit outbound message (external platform contract). Defaults to RCS (opens messaging composer on device). Authenticate with apiToken in body.',
  })
  @ApiQuery({
    name: 'deviceId',
    required: false,
    description:
      'Optional gateway device id when the account has multiple devices',
  })
  @ApiQuery({
    name: 'channel',
    required: false,
    enum: ['rcs', 'sms'],
    description:
      'Delivery channel override. Defaults to rcs (or PLATFORM_DEFAULT_CHANNEL).',
  })
  @HttpCode(HttpStatus.OK)
  @Post('messages')
  async sendMessage(
    @Body() body: PlatformSendMessageDto,
    @Query('deviceId') deviceId?: string,
    @Query('channel') channel?: string,
  ): Promise<PlatformSendMessageResponseDto> {
    return this.platformIntegrationService.sendMessage(body, deviceId, channel)
  }

  @ApiOperation({
    summary:
      'Submit outbound RCS (same body as /messages; channel is always rcs).',
  })
  @ApiQuery({
    name: 'deviceId',
    required: false,
    description:
      'Optional gateway device id when the account has multiple devices',
  })
  @HttpCode(HttpStatus.OK)
  @Post('rcs/messages')
  async sendRcsMessage(
    @Body() body: PlatformSendMessageDto,
    @Query('deviceId') deviceId?: string,
  ): Promise<PlatformSendMessageResponseDto> {
    return this.platformIntegrationService.sendMessage(
      body,
      deviceId,
      MessageChannel.RCS,
    )
  }
}
