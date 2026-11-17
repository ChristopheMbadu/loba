import { createFileRoute, notFound, redirect } from '@tanstack/react-router'
import { useState } from 'react'
import { LessonPlayer } from '../components/LessonPlayer'
import { catalogQuery } from '../content/curriculum'
import { requireLearner } from '../account/guard'
import { LESSONS_PER_UNIT } from '../content/types'
import { buildLesson } from '../game/exercises'
import { unitProgress, unitUnlocked, unlockedWords } from '../game/rules'
import { actions, game } from '../game/store'

export const Route = createFileRoute('/lesson/$unitId')({
  validateSearch: (search: Record<string, unknown>): { n: number } => {
    const n = Math.trunc(Number(search.n))
    return { n: Number.isFinite(n) ? Math.min(LESSONS_PER_UNIT - 1, Math.max(0, n)) : 0 }
  },
  beforeLoad: ({ context }) => requireLearner(context.queryClient),
  loaderDeps: ({ search }) => ({ n: search.n }),
  loader: async ({ context, params, deps }) => {
    const s = game.get()
    const { curriculum } = await context.queryClient.ensureQueryData(catalogQuery)
    const unit = Object.values(curriculum).flat().find((u) => u.id === params.unitId)
    if (!unit) throw notFound()
    const units = curriculum[unit.language]!
    // Following a link to a locked lesson lands on the path instead.
    if (!unitUnlocked(s, units, units.indexOf(unit)) || deps.n > unitProgress(s, unit)) throw redirect({ to: '/' })
    if (s.lang !== unit.language) actions.setLang(unit.language)
    return { unit, pool: unlockedWords(s, units) }
  },
  component: LessonRoute,
})

function LessonRoute() {
  const { unit, pool } = Route.useLoaderData()
  const { n } = Route.useSearch()
  // Built once per visit so re-renders never reshuffle the lesson.
  const [exercises] = useState(() => {
    const s = game.get()
    const seen = new Set(Object.keys(s.words).filter((k) => k.startsWith(`${unit.language}:`)).map((k) => k.slice(unit.language.length + 1)))
    return buildLesson(unit, n, { pool, canListen: s.voice, seen })
  })
  return <LessonPlayer key={`${unit.id}-${n}`} exercises={exercises} mode="lesson" unitId={unit.id} lessonIndex={n} />
}
