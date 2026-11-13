import { createFileRoute, redirect } from '@tanstack/react-router'
import { useState } from 'react'
import { LessonPlayer } from '../components/LessonPlayer'
import { curriculumQuery } from '../content/curriculum'
import { buildPractice } from '../game/exercises'
import { shuffle } from '../game/random'
import { reviewWords, unlockedWords } from '../game/rules'
import { game } from '../game/store'

type Mode = 'review' | 'listen'

export const Route = createFileRoute('/session')({
  validateSearch: (search: Record<string, unknown>): { mode: Mode } => ({ mode: search.mode === 'listen' ? 'listen' : 'review' }),
  loader: async ({ context }) => {
    const s = game.get()
    if (!s.onboarded) throw redirect({ to: '/welcome' })
    const units = (await context.queryClient.ensureQueryData(curriculumQuery))[s.lang]
    return { pool: unlockedWords(s, units) }
  },
  component: SessionRoute,
})

function SessionRoute() {
  const { pool } = Route.useLoaderData()
  const { mode } = Route.useSearch()
  const [exercises] = useState(() => {
    const s = game.get()
    const due = reviewWords(s, s.lang, pool, Date.now())
    // Top up with other unlocked words so there is always a full session.
    const candidates = [...due, ...shuffle(pool.filter((w) => !due.includes(w)))]
    const words = (mode === 'listen' ? candidates.filter((w) => w.audio) : candidates).slice(0, 8)
    return buildPractice(words, pool, mode, s.voice)
  })
  return <LessonPlayer exercises={exercises} mode="practice" />
}
