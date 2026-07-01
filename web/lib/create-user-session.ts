import { cookies } from 'next/headers'
import { encode } from 'next-auth/jwt'
import type { AuthenticatedUser } from '@/lib/authenticate-user'

const SESSION_MAX_AGE = 30 * 24 * 60 * 60

export async function createUserSession(user: AuthenticatedUser): Promise<void> {
  const secret = process.env.NEXTAUTH_SECRET || process.env.AUTH_SECRET
  if (!secret) {
    throw new Error('NEXTAUTH_SECRET is not configured')
  }

  const token = await encode({
    token: {
      name: user.name,
      email: user.email,
      picture: user.avatar,
      sub: user._id,
      id: user._id,
      role: user.role,
      accessToken: user.accessToken,
      avatar: user.avatar,
      phone: user.phone,
    },
    secret,
    maxAge: SESSION_MAX_AGE,
  })

  const secure = process.env.NODE_ENV === 'production'
  const cookieName = secure
    ? '__Secure-next-auth.session-token'
    : 'next-auth.session-token'

  cookies().set(cookieName, token, {
    httpOnly: true,
    sameSite: 'lax',
    path: '/',
    secure,
    maxAge: SESSION_MAX_AGE,
  })
}
