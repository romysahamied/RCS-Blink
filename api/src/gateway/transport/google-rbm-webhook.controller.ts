import { Body, Controller, Headers, HttpCode, HttpStatus, Post } from '@nestjs/common'
import { ApiOperation, ApiTags } from '@nestjs/swagger'
import { GoogleRbmWebhookService } from './google-rbm-webhook.service'

@ApiTags('gateway')
@Controller('gateway/rcs/google-rbm')
export class GoogleRbmWebhookController {
  constructor(private readonly googleRbmWebhookService: GoogleRbmWebhookService) {}

  @ApiOperation({ summary: 'Google RBM delivery webhook (status callbacks)' })
  @Post('/webhook/status')
  @HttpCode(HttpStatus.OK)
  async handleStatusWebhook(
    @Body() payload: any,
    @Headers('x-google-rbm-signature') signature?: string,
  ) {
    this.googleRbmWebhookService.verifySignature(signature)
    return this.googleRbmWebhookService.processStatusEvent(payload)
  }
}
