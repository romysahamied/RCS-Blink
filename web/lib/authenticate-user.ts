import axios from 'axios'
import { ApiEndpoints } from '@/config/api'
import { getServerSideBaseUrl, httpServerClient } from '@/lib/httpServerClient'

export type AuthenticatedUser = {
  id: string
  _id: string
  name?: string | null
  email?: string | null
  role?: string
  phone?: string
  avatar?: string
  accessToken: string
}

export async function loginWithEmailPassword(
  email: string,
  password: string,
  turnstileToken: string,
): Promise<AuthenticatedUser | null> {
  try {
    const res = await httpServerClient.post(ApiEndpoints.auth.login(), {
      email,
      password,
      turnstileToken,
    })

    const user = res.data.data.user
    const accessToken = res.data.data.accessToken

    return {
      ...user,
      id: user._id,
      accessToken,
    }
  } catch (e) {
    if (axios.isAxiosError(e)) {
      const msg =
        e.code === 'ECONNREFUSED'
          ? `[auth] Login: cannot reach API at ${getServerSideBaseUrl()}. Is the Nest server running on port 3001?`
          : `[auth] Login failed: ${e.response?.status ?? e.code} ${e.response?.data ? JSON.stringify(e.response.data) : e.message}`
      console.error(msg)
    } else {
      console.error(e)
    }
    return null
  }
}

export async function registerWithEmailPassword(input: {
  email: string
  password: string
  name: string
  phone?: string
  turnstileToken: string
  marketingOptIn?: boolean
}): Promise<AuthenticatedUser | null> {
  try {
    const res = await httpServerClient.post(ApiEndpoints.auth.register(), input)
    const user = res.data.data.user
    const accessToken = res.data.data.accessToken

    return {
      ...user,
      id: user._id,
      accessToken,
    }
  } catch (e) {
    console.log(e)
    return null
  }
}
