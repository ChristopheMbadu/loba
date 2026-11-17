import { queryOptions } from '@tanstack/react-query'
import bundled from '../../../data/lessons.json'
import type { Catalog, Curriculum, Language, LangCode, Sentence, Unit, Word } from './types'

const str = (v: unknown, max = 500) => (typeof v === 'string' ? v.trim().slice(0, max) : '')
export const LANG_CODE = /^[a-z]{2,3}$/

/** Shipped with the app so it works offline and before Studio is reachable. */
export const BUNDLED_LANGUAGES: Language[] = [
  { code: 'ln', name: { fr: 'lingala', en: 'Lingala' }, greeting: 'Mbote', cheer: 'Malamu mingi !', region: { fr: 'Kinshasa, Brazzaville, l’Équateur', en: 'Kinshasa, Brazzaville, Équateur' }, color: '#1c9bef' },
  { code: 'sw', name: { fr: 'swahili', en: 'Swahili' }, greeting: 'Jambo', cheer: 'Vizuri sana !', region: { fr: 'Lubumbashi, Goma, Bukavu, Kisangani', en: 'Lubumbashi, Goma, Bukavu, Kisangani' }, color: '#2bb673' },
]

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
  if (typeof o.language !== 'string' || !LANG_CODE.test(o.language)) return null
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

/** Studio's language rows (snake_case) → the app's Language. */
export function toLanguage(v: unknown): Language | null {
  if (!v || typeof v !== 'object') return null
  const o = v as Record<string, unknown>
  if (typeof o.code !== 'string' || !LANG_CODE.test(o.code)) return null
  const en = str(o.name_en, 60), fr = str(o.name_fr, 60) || en
  if (!en) return null
  return {
    code: o.code,
    name: { fr, en },
    greeting: str(o.greeting, 40),
    cheer: str(o.cheer, 60),
    region: { fr: str(o.region_fr, 120), en: str(o.region_en, 120) || str(o.region_fr, 120) },
    color: typeof o.color === 'string' && /^#[0-9a-f]{6}$/i.test(o.color) ? o.color : '#1c9bef',
  }
}

export function groupByLanguage(units: Unit[]): Curriculum {
  const byLang: Curriculum = {}
  for (const u of units) (byLang[u.language] ??= []).push(u)
  for (const list of Object.values(byLang)) list.sort((a, b) => a.position - b.position)
  return byLang
}

/** Only languages that have at least one usable unit are offered to learners, in Studio's order. */
export function toCatalog(languages: Language[], units: Unit[]): Catalog {
  const curriculum = groupByLanguage(units)
  const known = languages.filter((l) => curriculum[l.code]?.length)
  return { languages: known, curriculum: Object.fromEntries(known.map((l) => [l.code, curriculum[l.code]!])) }
}

export const bundledCatalog = (): Catalog =>
  toCatalog(BUNDLED_LANGUAGES, (bundled as unknown[]).map((l) => toUnit(String((l as { id: unknown }).id), l)).filter((u): u is Unit => !!u))

/**
 * Published lessons and visible languages from Loba Studio. Drafts are never exposed by that endpoint.
 * Offline, or before Studio is deployed, the curriculum shipped with the app is used.
 */
async function fetchCatalog(): Promise<Catalog> {
  try {
    const res = await fetch('/api/v1/lessons', { headers: { Accept: 'application/json' } })
    if (!res.ok) throw new Error(String(res.status))
    const body = (await res.json()) as { languages?: unknown[]; lessons?: { id: string; content: unknown }[] }
    const units = (body.lessons ?? []).map((l) => toUnit(l.id, l.content)).filter((u): u is Unit => !!u)
    // Older servers didn't send languages: fall back to the two built-in ones.
    const languages = Array.isArray(body.languages) ? body.languages.map(toLanguage).filter((l): l is Language => !!l) : BUNDLED_LANGUAGES
    const catalog = toCatalog(languages, units)
    if (catalog.languages.length) return catalog
    throw new Error('empty catalog')
  } catch {
    return bundledCatalog()
  }
}

export const catalogQuery = queryOptions({
  queryKey: ['catalog'],
  queryFn: fetchCatalog,
  staleTime: 5 * 60 * 1000,
})

/** The language a learner studies, or the first one available if theirs was removed. */
export const languageOrFirst = (catalog: Catalog, code: LangCode): LangCode =>
  catalog.curriculum[code] ? code : (catalog.languages[0]?.code ?? code)
