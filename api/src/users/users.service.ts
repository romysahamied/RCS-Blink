import { HttpException, HttpStatus, Injectable } from '@nestjs/common'
import { InjectModel } from '@nestjs/mongoose'
import { User, UserDocument } from './schemas/user.schema'
import { Model } from 'mongoose'
import { Cron, CronExpression } from '@nestjs/schedule'
import { MailService } from '../mail/mail.service'
import { getMailBranding, mailSubject } from '../mail/mail-branding'
import { BillingService } from '../billing/billing.service'
import { Device, DeviceDocument } from '../gateway/schemas/device.schema'
import { Plan } from 'src/billing/schemas/plan.schema'
import { UpdateOnboardingDTO } from '../auth/auth.dto'
import {
  ONBOARDING_OPTIONAL_STEP_IDS,
  ONBOARDING_STEP_ORDER,
} from './onboarding.constants'

@Injectable()
export class UsersService {
  constructor(
    @InjectModel(User.name) private userModel: Model<UserDocument>,
    @InjectModel(Device.name) private deviceModel: Model<DeviceDocument>,
    private mailService: MailService,
    private billingService: BillingService,
  ) {}

  async findOne(params) {
    return await this.userModel.findOne(params)
  }

  async findAll() {
    return await this.userModel.find()
  }

  async create({
    name,
    email,
    password,
    phone,
  }: {
    name: string
    email: string
    password?: string
    phone?: string
  }) {
    if (await this.findOne({ email })) {
      throw new HttpException(
        {
          error: 'user exists with the same email',
        },
        HttpStatus.BAD_REQUEST,
      )
    }

    const newUser = new this.userModel({
      name,
      email,
      password,
      phone,
    })
    return await newUser.save()
  }

  async updateProfile(
    input: { name: string; phone: string },
    user: UserDocument,
  ) {
    const userToUpdate = await this.findOne({ _id: user._id })
    if (!userToUpdate) {
      throw new HttpException({ error: 'User not found' }, HttpStatus.NOT_FOUND)
    }

    if (input.name) {
      userToUpdate.name = input.name
    }
    if (input.phone) {
      userToUpdate.phone = input.phone
    }

    return await userToUpdate.save()
  }

  async updateOnboarding(input: UpdateOnboardingDTO, user: UserDocument) {
    const u = await this.findOne({ _id: user._id })
    if (!u) {
      throw new HttpException({ error: 'User not found' }, HttpStatus.NOT_FOUND)
    }

    if (!u.onboarding) {
      u.onboarding = {
        currentStepId: 'verify_email',
        skippedStepIds: [],
      }
    }
    if (!u.onboarding.skippedStepIds) {
      u.onboarding.skippedStepIds = []
    }

    if (input.skipStepId) {
      if (
        !ONBOARDING_OPTIONAL_STEP_IDS.includes(
          input.skipStepId as (typeof ONBOARDING_OPTIONAL_STEP_IDS)[number],
        )
      ) {
        throw new HttpException(
          { error: 'Step is not optional' },
          HttpStatus.BAD_REQUEST,
        )
      }
      if (!u.onboarding.skippedStepIds.includes(input.skipStepId)) {
        u.onboarding.skippedStepIds.push(input.skipStepId)
      }
      const idx = ONBOARDING_STEP_ORDER.indexOf(
        input.skipStepId as (typeof ONBOARDING_STEP_ORDER)[number],
      )
      if (idx >= 0 && idx < ONBOARDING_STEP_ORDER.length - 1) {
        u.onboarding.currentStepId = ONBOARDING_STEP_ORDER[idx + 1]
      }
    }

    if (input.currentStepId) {
      u.onboarding.currentStepId = input.currentStepId
    }

    if (input.complete === true && !u.onboarding.completedAt) {
      u.onboarding.completedAt = new Date()
    }

    u.markModified('onboarding')
    return await u.save()
  }

  @Cron('0 12 * * *') // Every day at 12 PM
  async sendEmailToInactiveNewUsers() {
    try {
      // Get users who signed up between 3-4 days ago (not 1-2 days)
      const fourDaysAgo = new Date(Date.now() - 4 * 24 * 60 * 60 * 1000)
      const threeDaysAgo = new Date(Date.now() - 3 * 24 * 60 * 60 * 1000)

      const newUsers = await this.userModel.find({
        createdAt: {
          $gte: fourDaysAgo,
          $lt: threeDaysAgo,
        },
      })

      for (const user of newUsers) {
        try {
          // Check if user has any devices registered or has sent/received any SMS
          const devices = await this.deviceModel.find({ user: user._id })

          if (devices.length === 0 || devices.map(device=>device.sentSMSCount + device.receivedSMSCount).reduce((a,b)=>a+b,0) == 0) {
            // User hasn't registered any device, send email
            await this.mailService.sendEmailFromTemplate({
              to: user.email,
              subject: mailSubject('How can we help you get started?'),
              template: 'inactive-new-user',
              context: {
                name: user.name,
                registerDeviceUrl: `${getMailBranding().dashboardUrl}`,
              },
            })
            console.log(`Sent inactive new user email to ${user.email}`)
          }
          // Wait 200ms before processing the next user
          await new Promise((resolve) => setTimeout(resolve, 200))
        } catch (error) {
          console.error(`Error processing email for user ${user.email}:`, error)
        }
      }
    } catch (error) {
      console.error('Error sending emails to inactive new users:', error)
    }
  }

  @Cron('0 13 * * *') // Every day at 1 PM
  async sendEmailToFreeUsers() {
    try {
      // Get users who signed up between 13-14 days ago
      const fourteenDaysAgo = new Date(Date.now() - 14 * 24 * 60 * 60 * 1000)
      const thirteenDaysAgo = new Date(Date.now() - 13 * 24 * 60 * 60 * 1000)

      const usersToEmail = await this.userModel.find({
        createdAt: {
          $gte: fourteenDaysAgo,
          $lt: thirteenDaysAgo,
        },
      })

      for (const user of usersToEmail) {
        try {
          const subscription = await this.billingService.getActiveSubscription(
            user._id.toString(),
          )

          if ((subscription?.plan as Plan)?.name === 'free') {
            const devices = await this.deviceModel.find({ user: user._id })

            if (devices.length === 0 || devices.map(device=>device.sentSMSCount + device.receivedSMSCount).reduce((a,b)=>a+b,0) == 0) {
              // Only send this if they haven't set up any devices after 10-14 days
              await this.mailService.sendEmailFromTemplate({
                to: user.email,
                subject: `${user.name?.split(' ')[0]}, we'd love to help you get started with ${getMailBranding().brandName}`,
                template: 'inactive-new-user-day-10',
                context: {
                  name: user.name,
                  registerDeviceUrl: `${getMailBranding().dashboardUrl}`,
                },
              })

              console.log(`Sent inactive new user email to ${user.email}`)
            } else {
              // Only send upgrade email to active users who have at least one device
              await this.mailService.sendEmailFromTemplate({
                to: user.email,
                subject: `${user.name?.split(' ')[0]}, unlock more with ${getMailBranding().brandName} Pro`,
                template: 'upgrade-to-pro',
                context: {
                  name: user.name,
                  upgradeUrl: `${getMailBranding().frontendUrl}/checkout/pro`,
                  deviceCount: devices.length,
                },
              })
              console.log(`Sent upgrade to pro email to ${user.email}`)
            }
          }
          
          // Wait 200ms before processing the next user
          await new Promise((resolve) => setTimeout(resolve, 200))
        } catch (error) {
          console.error(`Error processing email for user ${user.email}:`, error)
        }
      }
    } catch (error) {
      console.error('Error sending emails to free plan users:', error)
    }
  }
}
