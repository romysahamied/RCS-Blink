import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger'
import {
  IsIn,
  IsNotEmpty,
  IsOptional,
  IsString,
  IsUrl,
  MaxLength,
} from 'class-validator'

export class PlatformSendMessageDto {
  @ApiProperty({ description: 'API token (TextBee API key)' })
  @IsString()
  @IsNotEmpty()
  apiToken: string

  @ApiProperty({ description: '1=promotional, 2=transactional, 3=OTP' })
  @IsIn(['1', '2', '3', 1, 2, 3])
  messageType: string | number

  @ApiProperty({
    description:
      '0=default, 1=ascii, 2=octet, 3=latin1, 4=octet unspecified, 6=cyrillic, 7=latin hebrew, 8=ucs2',
  })
  @IsIn(['0', '1', '2', '3', '4', '6', '7', '8', 0, 1, 2, 3, 4, 6, 7, 8])
  messageEncoding: string | number

  @ApiProperty({ description: 'Recipient MSISDN, e.g. 917889630058' })
  @IsString()
  @IsNotEmpty()
  @MaxLength(20)
  destinationAddress: string

  @ApiProperty({ description: 'Sender ID / source address, e.g. MROTP' })
  @IsString()
  @IsNotEmpty()
  @MaxLength(20)
  sourceAddress: string

  @ApiProperty({ description: 'Message body' })
  @IsString()
  @IsNotEmpty()
  @MaxLength(1600)
  messageText: string

  @ApiPropertyOptional({ description: 'DLR callback URL' })
  @IsOptional()
  @IsUrl({ require_tld: false })
  callBackUrl?: string

  @ApiPropertyOptional({ description: 'Client reference id' })
  @IsOptional()
  @IsString()
  @MaxLength(128)
  userReferenceId?: string
}

export class PlatformSendMessageResponseDto {
  MessageId: number
  OperationCode: number
  Status: string
  DlrStatus: number | null
  UserReferenceId: string | null
  DestinationAddress: string
  Remarks: string
  CallBackUrl: string | null
}

export class PlatformDlrCallbackPayloadDto {
  messageId: number
  operationCode: number
  status: string
  dlrStatus: number
  userReferenceId: string | null
  destinationAddress: string | number
  remarks: string
  callBackUrl: string | null
}
