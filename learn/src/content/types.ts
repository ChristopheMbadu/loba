export type LangCode = 'ln' | 'sw'
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

/** Lessons per unit before the unit's chest unlocks. */
export const LESSONS_PER_UNIT = 4
