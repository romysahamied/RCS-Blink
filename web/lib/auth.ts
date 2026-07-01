import CredentialsProvider from 'next-auth/providers/credentials'
import { httpServerClient } from './httpServerClient'
import { DefaultSession } from 'next-auth'
import { ApiEndpoints } from '@/config/api'
import { Routes } from '@/config/routes'
import {
  loginWithEmailPassword,
  registerWithEmailPassword,
} from '@/lib/authenticate-user'

// add custom fields to the session and user interfaces
declare module 'next-auth' {
  interface Session {
    user: {
      id?: string
      role?: string
      phone?: string
      avatar?: string
      accessToken?: string
    } & DefaultSession['user']
  }

  interface User {
    phone?: string
    avatar?: string
    accessToken?: string
  }
}

export const authOptions = {
  providers: [
    CredentialsProvider({
      id: 'email-password-login',
      name: 'email-password-login',
      credentials: {
        email: { label: 'email', type: 'text' },
        password: { label: 'Password', type: 'password' },
        turnstileToken: { label: 'Turnstile Token', type: 'text' },
      },
      async authorize(credentials) {
        const { email, password, turnstileToken } = credentials
        if (!email || !password || !turnstileToken) {
          return null
        }
        return loginWithEmailPassword(email, password, turnstileToken)
      },
    }),
    CredentialsProvider({
      id: 'email-password-register',
      name: 'email-password-register',
      credentials: {
        email: { label: 'email', type: 'text' },
        password: { label: 'Password', type: 'password' },
        name: { label: 'Name', type: 'text' },
        phone: { label: 'Phone', type: 'text' },
        turnstileToken: { label: 'Turnstile Token', type: 'text' },
      },
      async authorize(credentials) {
        const { email, password, name, phone, turnstileToken } = credentials
        if (!email || !password || !name || !turnstileToken) {
          return null
        }
        return registerWithEmailPassword({
          email,
          password,
          name,
          phone: phone ?? undefined,
          turnstileToken,
        })
      },
    }),
    CredentialsProvider({
      id: 'google-id-token-login',
      name: 'google-id-token-login',
      credentials: {
        idToken: { label: 'idToken', type: 'text' },
      },
      async authorize(credentials) {
        const { idToken } = credentials
        try {
          const res = await httpServerClient.post(
            ApiEndpoints.auth.signInWithGoogle(),
            {
              idToken,
            }
          )

          const user = res.data.data.user
          const accessToken = res.data.data.accessToken

          return {
            ...user,
            accessToken,
          }
        } catch (e) {
          console.log(e)

          return null
        }
      },
    }),
  ],
  pages: {
    signIn: Routes.login,
    error: Routes.authError,
  },
  secret: process.env.NEXTAUTH_SECRET || process.env.AUTH_SECRET,
  trustHost: true,
  session: {
    strategy: 'jwt',
  },
  callbacks: {
    async redirect({ url }) {
      if (url.startsWith('/')) {
        return url
      }
      try {
        const { pathname, search, hash } = new URL(url)
        const path = `${pathname}${search}${hash}`
        if (path.startsWith('/')) {
          return path
        }
      } catch {
        // ignore malformed URLs
      }
      return Routes.dashboard
    },
    async jwt({ token, user, trigger, session }) {
      if (trigger === 'update') {
        if (session.name !== token.name) {
          token.name = session.name
        }
        if (session.phone !== token.phone) {
          token.phone = session.phone
        }
        return token
      }

      if (user) {
        token.id = user._id
        token.role = user.role
        token.accessToken = user.accessToken
        token.avatar = user.avatar
        token.phone = user.phone
      }
      return token
    },
    async session({ session, token }): Promise<any> {
      session.user.id = token.id
      session.user.role = token.role
      session.user.accessToken = token.accessToken
      session.user.avatar = token.avatar
      session.user.phone = token.phone
      return session
    },
  },
}
