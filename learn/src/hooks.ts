import { useSuspenseQuery } from '@tanstack/react-query'
import { useSelector } from '@tanstack/react-store'
import { useEffect, useState } from 'react'
import { catalogQuery } from './content/curriculum'
import type { Language, LangCode } from './content/types'
import { game } from './game/store'

export function useCatalog() {
  return useSuspenseQuery(catalogQuery).data
}

export function useCurriculum() {
  return useCatalog().curriculum
}

/** Languages learners can pick: visible in Studio and with at least one unit. */
export function useLanguages(): Language[] {
  return useCatalog().languages
}

export function useLanguage(code: LangCode): Language | undefined {
  return useLanguages().find((l) => l.code === code)
}

/** Units for the language currently being learned. */
export function useUnits() {
  const lang = useSelector(game, (s) => s.lang)
  return useCurriculum()[lang] ?? []
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
