/** ISO 639 code of a language managed in Loba Studio (ln, sw, kg, lua…). */
export type LangCode = string
export type UiLang = 'fr' | 'en'

export interface Word {
  term: string
  fr: string
  en: string
  /** Id of a teacher's recording, played from /api/v1/audio/:id. */
  audio?: string
}

export interface Sentence {
  fr: string
  en: string
  answer: string
  distractors: string[]
}

export interface Unit {
  id: string
  language: LangCode
  position: number
  title: string
  titleEn: string
  icon: string
  tip: string
  words: Word[]
  sentences: Sentence[]
}

export type Curriculum = Record<LangCode, Unit[]>

/** A learning language, as configured in Loba Studio. */
export interface Language {
  code: LangCode
  name: Record<UiLang, string>
  /** A greeting shown on the landing page, e.g. "Mbote". */
  greeting: string
  /** Said after a right answer, e.g. "Malamu mingi !". */
  cheer: string
  region: Record<UiLang, string>
  color: string
}

/** Everything learners can study: visible languages that have at least one usable unit. */
export interface Catalog {
  languages: Language[]
  curriculum: Curriculum
}

/** Lessons per unit before the unit's chest unlocks. */
export const LESSONS_PER_UNIT = 4
