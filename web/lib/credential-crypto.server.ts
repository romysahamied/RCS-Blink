import { webcrypto } from 'node:crypto'

function base64ToBytes(value: string): Uint8Array {
  return new Uint8Array(Buffer.from(value, 'base64'))
}

async function getPrivateKey() {
  const pem = process.env.LOGIN_ENCRYPTION_PRIVATE_KEY
  if (pem) {
    return webcrypto.subtle.importKey(
      'pkcs8',
      new TextEncoder().encode(pem.replace(/\\n/g, '\n')),
      { name: 'RSA-OAEP', hash: 'SHA-256' },
      false,
      ['decrypt'],
    )
  }

  const keyB64 = process.env.LOGIN_ENCRYPTION_PRIVATE_KEY_B64
  if (!keyB64) {
    throw new Error(
      'LOGIN_ENCRYPTION_PRIVATE_KEY or LOGIN_ENCRYPTION_PRIVATE_KEY_B64 is not configured',
    )
  }

  return webcrypto.subtle.importKey(
    'pkcs8',
    base64ToBytes(keyB64),
    { name: 'RSA-OAEP', hash: 'SHA-256' },
    false,
    ['decrypt'],
  )
}

export async function decryptLoginSecret(ciphertextB64: string): Promise<string> {
  const privateKey = await getPrivateKey()
  const decrypted = await webcrypto.subtle.decrypt(
    { name: 'RSA-OAEP' },
    privateKey,
    base64ToBytes(ciphertextB64),
  )

  return new TextDecoder().decode(decrypted)
}
