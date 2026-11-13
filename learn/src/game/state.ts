import type { LangCode, UiLang } from '../content/types'

export interface WordStat {
  level: number // 0–5, spaced-repetition box
  misses: number
  due: number // epoch ms
}

export interface Daily {
  day: string // YYYY-MM-DD (local)
  xp: number
  lessons: number
  bestCombo: number
  perfect: number
  claimed: string[]
}

export interface GameState {
  v: 1
  onboarded: boolean
  ui: UiLang
  lang: LangCode
  goal: number
  sound: boolean
  voice: boolean
  name: string
  avatar: string
  xp: number
  gems: number
  hearts: number
  heartsRefillAt: number | null
  streak: number
  bestStreak: number
  lastActiveDay: string | null
  /** Recent days with a completed session, for the week calendar. */
  activeDays: string[]
  freezes: number
  doubleXpUntil: number
  lessonsDone: number
  /** unit id → lessons completed (0…LESSONS_PER_UNIT) */
  progress: Record<string, number>
  chests: Record<string, true>
  /** `${lang}:${term}` → stats */
  words: Record<string, WordStat>
  daily: Daily
}

export const MAX_HEARTS = 5
export const HEART_REGEN_MS = 30 * 60 * 1000
export const MAX_FREEZES = 2
export const GOALS = [10, 20, 40, 60] as const
export const AVATARS = ['🦜', '🦁', '🐘', '🦒', '🐆', '🦓', '🦩', '🐒', '🦅', '🐢', '🐊', '🦋', '🌍', '⚽', '🥁', '🎸', '🌺', '👑']

export const PRICES = { refill: 50, freeze: 100, doubleXp: 80 } as const
export const REWARDS = { chest: 25, perfect: 5 } as const

export const dayKey = (d: Date = new Date()) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

export const emptyDaily = (day: string): Daily => ({ day, xp: 0, lessons: 0, bestCombo: 0, perfect: 0, claimed: [] })

export const freshState = (ui: UiLang = 'fr'): GameState => ({
  v: 1,
  onboarded: false,
  ui,
  lang: 'ln',
  goal: 20,
  sound: true,
  voice: true,
  name: '',
  avatar: '🦜',
  xp: 0,
  gems: 50,
  hearts: MAX_HEARTS,
  heartsRefillAt: null,
  streak: 0,
  bestStreak: 0,
  lastActiveDay: null,
  activeDays: [],
  freezes: 0,
  doubleXpUntil: 0,
  lessonsDone: 0,
  progress: {},
  chests: {},
  words: {},
  daily: emptyDaily(dayKey()),
})
