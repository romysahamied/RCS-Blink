import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { webcrypto } from 'node:crypto'

function loadEnvFile(path) {
  const content = readFileSync(path, 'utf8')
  for (const line of content.split('\n')) {
    const trimmed = line.trim()
    if (!trimmed || trimmed.startsWith('#')) continue
    const idx = trimmed.indexOf('=')
    if (idx === -1) continue
    const key = trimmed.slice(0, idx)
    const value = trimmed.slice(idx + 1)
    if (!process.env[key]) {
      process.env[key] = value
    }
  }
}

function base64ToBytes(value) {
  return new Uint8Array(Buffer.from(value, 'base64'))
}

loadEnvFile(resolve(process.cwd(), '.env.local'))

const publicKeyB64 = process.env.NEXT_PUBLIC_LOGIN_ENCRYPTION_PUBLIC_KEY
const privateKeyB64 = process.env.LOGIN_ENCRYPTION_PRIVATE_KEY_B64

if (!publicKeyB64 || !privateKeyB64) {
  console.error('Missing login encryption keys in .env.local')
  process.exit(1)
}

const password = 'roundtrip-test-password'
const publicKey = await webcrypto.subtle.importKey(
  'spki',
  base64ToBytes(publicKeyB64),
  { name: 'RSA-OAEP', hash: 'SHA-256' },
  false,
  ['encrypt'],
)

const encrypted = await webcrypto.subtle.encrypt(
  { name: 'RSA-OAEP' },
  publicKey,
  new TextEncoder().encode(password),
)

const ciphertextB64 = Buffer.from(encrypted).toString('base64')

const privateKeyCrypto = await webcrypto.subtle.importKey(
  'pkcs8',
  base64ToBytes(privateKeyB64),
  { name: 'RSA-OAEP', hash: 'SHA-256' },
  false,
  ['decrypt'],
)

const decrypted = new TextDecoder().decode(
  await webcrypto.subtle.decrypt(
    { name: 'RSA-OAEP' },
    privateKeyCrypto,
    base64ToBytes(ciphertextB64),
  ),
)

if (decrypted !== password) {
  console.error('Login crypto roundtrip failed')
  process.exit(1)
}

console.log('Login crypto roundtrip OK')
