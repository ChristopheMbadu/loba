import { useQuery } from '@tanstack/react-query'
import { useSelector } from '@tanstack/react-store'
import { game } from '../game/store'
import { BUNDLED_LANGUAGES, catalogQuery } from '../content/curriculum'
import type { Language, LangCode, Unit, UiLang, Word, Sentence } from '../content/types'
import { STRINGS, type StringKey } from './strings'

/** French writes language names in lower case ("le lingala"); English capitalises them. */
export const languageName = (languages: Language[], ui: UiLang, code: LangCode) => {
  const name = languages.find((l) => l.code === code)?.name[ui] ?? code.toUpperCase()
  return ui === 'fr' ? name.toLocaleLowerCase('fr') : name
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
  const languages = useQuery(catalogQuery).data?.languages ?? BUNDLED_LANGUAGES
  return {
    ui,
    t: (key: StringKey, vars?: Record<string, string | number>) => translate(ui, key, vars),
    langName: (c: LangCode) => languageName(languages, ui, c),
    region: (c: LangCode) => languages.find((l) => l.code === c)?.region[ui] ?? '',
    meaning: (w: Word | Sentence) => meaningOf(ui, w),
    unitTitle: (u: Unit) => unitTitle(ui, u),
  }
}
