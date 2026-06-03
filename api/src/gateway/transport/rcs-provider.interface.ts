export type RcsSendRequest = {
  deviceId: string
  recipients: string[]
  message: string
  simSubscriptionId?: number
}

export type RcsSendResult = {
  success: boolean
  provider: string
  acceptedCount: number
  message: string
  providerMessageId?: string
}

export interface RcsProvider {
  readonly name: string
  sendMessage(payload: RcsSendRequest): Promise<RcsSendResult>
}
