const mailHost = process.env.MAIL_HOST?.trim()

export const mailTransportConfig = mailHost
  ? {
      host: mailHost,
      port: process.env.MAIL_PORT ? parseInt(process.env.MAIL_PORT, 10) : 465,
      secure: false,
      auth: {
        user: process.env.MAIL_USER,
        pass: process.env.MAIL_PASS,
      },
    }
  : {
      // Local development fallback when SMTP is not configured.
      // Emails are generated but not delivered over network.
      jsonTransport: true,
    }
