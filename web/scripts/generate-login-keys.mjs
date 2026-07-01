import { generateKeyPairSync } from 'node:crypto'
import { writeFileSync } from 'node:fs'
import { resolve } from 'node:path'

const { publicKey, privateKey } = generateKeyPairSync('rsa', {
  modulusLength: 2048,
  publicKeyEncoding: { type: 'spki', format: 'der' },
  privateKeyEncoding: { type: 'pkcs8', format: 'der' },
})

const envBlock = [
  '# RSA keys for encrypting login passwords in transit (browser -> Next.js server)',
  `NEXT_PUBLIC_LOGIN_ENCRYPTION_PUBLIC_KEY=${publicKey.toString('base64')}`,
  `LOGIN_ENCRYPTION_PRIVATE_KEY_B64=${privateKey.toString('base64')}`,
  '',
].join('\n')

const outputPath = resolve(process.cwd(), '.env.login-keys')
writeFileSync(outputPath, envBlock, 'utf8')

console.log(`Wrote ${outputPath}`)
console.log('Copy these lines into web/.env.local')
