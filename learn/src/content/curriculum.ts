import { queryOptions } from '@tanstack/react-query'
import bundled from '../../../data/lessons.json'
import type { Curriculum, LangCode, Sentence, Unit, Word } from './types'

const str = (v: unknown, max = 500) => (typeof v === 'string' ? v.trim().slice(0, max) : '')

function toWord(v: unknown): Word | null {
  if (!v || typeof v !== 'object') return null
  const o = v as Record<string, unknown>
  const term = str(o.term), fr = str(o.fr)
  const audio = typeof o.audio === 'string' && /^[0-9a-f-]{36}$/.test(o.audio) ? o.audio : undefined
  return term && fr ? { term, fr, en: str(o.en), ...(audio ? { audio } : {}) } : null
}

function toSentence(v: unknown): Sentence | null {
  if (!v || typeof v !== 'object') return null
  const o = v as Record<string, unknown>
  const answer = str(o.answer), fr = str(o.fr)
  const distractors = Array.isArray(o.distractors) ? o.distractors.map((d) => str(d, 60)).filter(Boolean) : []
  return answer && fr ? { fr, en: str(o.en), answer, distractors } : null
}

/** Validates one lesson's content. Anything malformed is dropped, never rendered. */
export function toUnit(id: string, v: unknown): Unit | null {
  if (!v || typeof v !== 'object') return null
  const o = v as Record<string, unknown>
  if (o.language !== 'ln' && o.language !== 'sw') return null
  const words = Array.isArray(o.words) ? o.words.map(toWord).filter((w): w is Word => !!w) : []
  if (words.length < 4) return null
  return {
    id,
    language: o.language,
    position: typeof o.position === 'number' ? o.position : 999,
    title: str(o.title, 120),
    titleEn: str(o.titleEn, 120),
    icon: str(o.icon, 8) || '📘',
    tip: str(o.tip, 1000),
    words,
    sentences: Array.isArray(o.sentences) ? o.sentences.map(toSentence).filter((s): s is Sentence => !!s) : [],
  }
}

export function groupByLanguage(units: Unit[]): Curriculum {
  const byLang: Curriculum = { ln: [], sw: [] }
  for (const u of units) byLang[u.language].push(u)
  for (const k of Object.keys(byLang) as LangCode[]) byLang[k].sort((a, b) => a.position - b.position)
  return byLang
}

export const bundledCurriculum = (): Curriculum =>
  groupByLanguage((bundled as unknown[]).map((l) => toUnit(String((l as { id: unknown }).id), l)).filter((u): u is Unit => !!u))

/**
 * Published lessons from Loba Studio. Drafts are never exposed by that endpoint.
 * Offline, or before Studio is deployed, the curriculum shipped with the app is used.
 */
async function fetchCurriculum(): Promise<Curriculum> {
  try {
    const res = await fetch('/api/v1/lessons', { headers: { Accept: 'application/json' } })
    if (!res.ok) throw new Error(String(res.status))
    const body = (await res.json()) as { lessons?: { id: string; content: unknown }[] }
    const units = (body.lessons ?? []).map((l) => toUnit(l.id, l.content)).filter((u): u is Unit => !!u)
    const grouped = groupByLanguage(units)
    if (grouped.ln.length && grouped.sw.length) return grouped
    throw new Error('incomplete curriculum')
  } catch {
    return bundledCurriculum()
  }
}

export const curriculumQuery = queryOptions({
  queryKey: ['curriculum'],
  queryFn: fetchCurriculum,
  staleTime: 5 * 60 * 1000,
})
