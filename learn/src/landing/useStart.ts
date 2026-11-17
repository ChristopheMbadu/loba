import { useQuery } from '@tanstack/react-query'
import { useSelector } from '@tanstack/react-store'
import { useMemo } from 'react'
import { meQuery } from '../account/api'
import { game } from '../game/store'
import { useLanguages } from '../hooks'
import { GREETINGS, type Greeting } from './copy'

/**
 * Where "Get started" leads: learning always begins with an account, then the
 * language and daily goal, then the first lesson.
 */
export function useStartLink() {
  const me = useQuery(meQuery).data
  const onboarded = useSelector(game, (s) => s.onboarded)
  if (me?.learner || me?.offline) return { to: onboarded ? '/' : '/welcome' } as const
  return { to: '/account', search: { mode: 'signup' } } as const
}

/** Loba greets in every language learners can study. */
export function useGreetings(): Greeting[] {
  const languages = useLanguages()
  return useMemo(() => {
    const codes = languages.map((l) => l.code)
    return [
      ...GREETINGS.filter((g) => codes.includes(g.lang)),
      ...languages
        .filter((l) => l.greeting && !GREETINGS.some((g) => g.lang === l.code))
        .map((l) => ({ text: `${l.greeting} !`, lang: l.code, fr: 'Bonjour', en: 'Hello' })),
    ]
  }, [languages])
}
