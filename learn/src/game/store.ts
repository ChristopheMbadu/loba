import { createStore } from '@tanstack/react-store'
import type { LangCode, UiLang, Unit } from '../content/types'
import { migrateLegacy, sanitize } from './migrate'
import * as rules from './rules'
import { dayKey, freshState, type GameState } from './state'

const KEY = 'loba-learn-v1'
const LEGACY_KEY = 'loba-state-v4'

const detectUi = (): UiLang =>
  typeof navigator !== 'undefined' && !navigator.language.toLowerCase().startsWith('fr') ? 'en' : 'fr'

function load(): GameState {
  const ui = detectUi()
  try {
    const raw = localStorage.getItem(KEY)
    if (raw) return sanitize(JSON.parse(raw), ui)
    const legacy = localStorage.getItem(LEGACY_KEY)
    if (legacy) return migrateLegacy(JSON.parse(legacy), ui)
  } catch {
    // Private mode or corrupted data: start fresh rather than fail.
  }
  return freshState(ui)
}

export const game = createStore<GameState>(load())

game.subscribe((s) => {
  try {
    localStorage.setItem(KEY, JSON.stringify(s))
  } catch {
    // Storage full or blocked; progress continues in memory.
  }
})

if (typeof document !== 'undefined') document.documentElement.lang = game.get().ui

const update = (fn: (s: GameState) => GameState) => game.setState(fn)
const now = () => Date.now()
const today = () => dayKey()

/** All state changes go through here, so components never hand-edit game state. */
export const actions = {
  tick: () => update((s) => rules.rollDaily(rules.syncHearts(s, now()), today())),
  finishOnboarding: (lang: LangCode, goal: number) => update((s) => ({ ...s, lang, goal, onboarded: true })),
  setUi: (ui: UiLang) => {
    document.documentElement.lang = ui
    update((s) => ({ ...s, ui }))
  },
  setLang: (lang: LangCode) => update((s) => ({ ...s, lang })),
  setGoal: (goal: number) => update((s) => ({ ...s, goal })),
  setProfile: (name: string, avatar: string) =>
    update((s) => ({ ...s, name: name.replace(/[\u0000-\u001f<>]/g, '').slice(0, 20), avatar })),
  toggle: (key: 'sound' | 'voice') => update((s) => ({ ...s, [key]: !s[key] })),
  loseHeart: () => update((s) => rules.loseHeart(s, now())),
  recordWord: (lang: LangCode, term: string, correct: boolean) => update((s) => rules.recordWord(s, lang, term, correct, now())),
  completeSession: (r: rules.SessionResult) => {
    let reward: rules.Reward | undefined
    update((s) => {
      const out = rules.completeSession(s, r, now(), today())
      reward = out.reward
      return out.state
    })
    return reward!
  },
  claimQuest: (id: rules.QuestId) => update((s) => rules.claimQuest(s, id, today())),
  buy: (item: rules.ShopItem) => update((s) => rules.buy(s, item, now())),
  openChest: (u: Unit) => update((s) => rules.openChest(s, u)),
  reset: () => update((s) => ({ ...freshState(s.ui), onboarded: false })),
}
