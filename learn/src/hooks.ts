import { useSuspenseQuery } from '@tanstack/react-query'
import { useSelector } from '@tanstack/react-store'
import { useEffect, useState } from 'react'
import { curriculumQuery } from './content/curriculum'
import { game } from './game/store'

export function useCurriculum() {
  return useSuspenseQuery(curriculumQuery).data
}

/** Units for the language currently being learned. */
export function useUnits() {
  const lang = useSelector(game, (s) => s.lang)
  return useCurriculum()[lang]
}

/** Re-renders every `ms` — for heart timers and boosts. */
export function useNow(ms = 30_000) {
  const [now, setNow] = useState(Date.now)
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), ms)
    return () => clearInterval(id)
  }, [ms])
  return now
}
