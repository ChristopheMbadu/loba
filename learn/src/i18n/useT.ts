import { useSelector } from '@tanstack/react-store'
import { game } from '../game/store'
import type { LangCode, Unit, UiLang, Word, Sentence } from '../content/types'
import { STRINGS, type StringKey } from './strings'

export const LANG_NAMES: Record<UiLang, Record<LangCode, string>> = {
  fr: { ln: 'lingala', sw: 'swahili' },
  en: { ln: 'Lingala', sw: 'Swahili' },
}

export function translate(ui: UiLang, key: StringKey, vars: Record<string, string | number> = {}) {
  let s = STRINGS[ui][key]
  for (const [k, v] of Object.entries(vars)) s = s.replaceAll(`{${k}}`, String(v))
  return s
}

/** The learner's meaning text: English when chosen and available, French otherwise. */
export const meaningOf = (ui: UiLang, w: Word | Sentence) => (ui === 'en' && w.en ? w.en : w.fr)
export const unitTitle = (ui: UiLang, u: Unit) => (ui === 'en' && u.titleEn ? u.titleEn : u.title)

export function useT() {
  const ui = useSelector(game, (s) => s.ui)
  return {
    ui,
    t: (key: StringKey, vars?: Record<string, string | number>) => translate(ui, key, vars),
    langName: (c: LangCode) => LANG_NAMES[ui][c],
    meaning: (w: Word | Sentence) => meaningOf(ui, w),
    unitTitle: (u: Unit) => unitTitle(ui, u),
  }
}
