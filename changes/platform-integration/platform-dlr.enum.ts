/** DLR status codes for platform callback payloads. */
export enum PlatformDlrStatus {
  None = 0,
  Enroute = 1,
  Delivered = 2,
  Expired = 3,
  Deleted = 4,
  Undeliverable = 5,
  Accepted = 6,
  Unknown = 7,
  Rejected = 8,
}

export enum PlatformMessageEncoding {
  Default = 0,
  Ascii = 1,
  Octet = 2,
  Latin1 = 3,
  OctetUnspecified = 4,
  Cyrillic = 6,
  LatinHebrew = 7,
  Ucs2 = 8,
}

export enum PlatformMessageType {
  Promotional = 1,
  Transactional = 2,
  Otp = 3,
}

export enum PlatformOperationCode {
  Success = 0,
  Unauthorized = 1,
  ValidationError = 2,
  NoDevice = 3,
  SendFailed = 4,
}
