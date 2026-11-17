import type { QueryClient } from '@tanstack/react-query'
import { redirect } from '@tanstack/react-router'
import { game } from '../game/store'
import { meQuery } from './api'

/**
 * Learning starts with an account, then a language and daily goal. Visitors without an account
 * go to the landing page (or, if they already have progress here, straight to sign-up).
 * When the account service can't be reached, learners keep going on this device.
 */
export async function requireAccount(queryClient: QueryClient) {
  const me = await queryClient.ensureQueryData(meQuery)
  if (!me.learner && !me.offline) {
    throw game.get().onboarded ? redirect({ to: '/account', search: { mode: 'signup' } }) : redirect({ to: '/home' })
  }
}

export async function requireLearner(queryClient: QueryClient) {
  await requireAccount(queryClient)
  if (!game.get().onboarded) throw redirect({ to: '/welcome' })
}
