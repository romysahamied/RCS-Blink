import { ISendMailOptions, MailerService } from '@nest-modules/mailer'
import { Injectable } from '@nestjs/common'
import { getMailBranding, getMailFromAddress } from './mail-branding'

@Injectable()
export class MailService {
  constructor(private readonly mailerService: MailerService) {}

  async sendEmail({ to, subject, html, from }) {
    const sendMailOptions: ISendMailOptions = {
      to,
      subject,
      html,
    }

    sendMailOptions['from'] = from || getMailFromAddress()

    if (process.env.MAIL_REPLY_TO) {
      sendMailOptions['replyTo'] = process.env.MAIL_REPLY_TO
    }
    try {
      await this.mailerService.sendMail(sendMailOptions)
    } catch (e) {
      console.log(e)
    }
  }

  async sendEmailFromTemplate({ to, cc, subject, template, context, from }: ISendMailOptions) {
    const sendMailOptions: ISendMailOptions = {
      to,
      cc,
      subject,
      template,
      context: {
        ...getMailBranding(),
        ...(context || {}),
      },
    }

    sendMailOptions['from'] = from || getMailFromAddress()

    if (process.env.MAIL_REPLY_TO) {
      sendMailOptions['replyTo'] = process.env.MAIL_REPLY_TO
    }

    try {
      await this.mailerService.sendMail(sendMailOptions)
    } catch (e) {
      console.log(e)
    }
  }
}
