function base64ToBytes(value: string): Uint8Array {
  const binary = atob(value)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i)
  }
  return bytes
}

function bytesToBase64(bytes: Uint8Array): string {
  let binary = ''
  for (let i = 0; i < bytes.length; i += 1) {
    binary += String.fromCharCode(bytes[i])
  }
  return btoa(binary)
}

export async function encryptLoginSecret(value: string): Promise<string> {
  const keyB64 = process.env.NEXT_PUBLIC_LOGIN_ENCRYPTION_PUBLIC_KEY
  if (!keyB64) {
    throw new Error('Login encryption is not configured')
  }

  const key = await crypto.subtle.importKey(
    'spki',
    base64ToBytes(keyB64),
    { name: 'RSA-OAEP', hash: 'SHA-256' },
    false,
    ['encrypt'],
  )

  const encrypted = await crypto.subtle.encrypt(
    { name: 'RSA-OAEP' },
    key,
    new TextEncoder().encode(value),
  )

  return bytesToBase64(new Uint8Array(encrypted))
}
