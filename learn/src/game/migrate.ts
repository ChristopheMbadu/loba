import type { LangCode, UiLang } from '../content/types'
import { AVATARS, dayKey, freshState, GOALS, MAX_FREEZES, MAX_HEARTS, type GameState, type WordStat } from './state'

const num = (v: unknown, fallback: number, min = 0, max = 1e9) => {
  const n = Number(v)
  return Number.isFinite(n) ? Math.min(max, Math.max(min, n)) : fallback
}
const obj = (v: unknown): Record<string, unknown> => (v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : {})

/** Validates a stored state. Anything unexpected falls back to defaults rather than crashing the app. */
export function sanitize(raw: unknown, ui: UiLang): GameState {
  const base = freshState(ui)
  const o = obj(raw)
  if (o.v !== 1) return base
  const words: Record<string, WordStat> = {}
  for (const [k, v] of Object.entries(obj(o.words)).slice(0, 5000)) {
    const w = obj(v)
    if (/^(ln|sw):/.test(k)) words[k] = { level: num(w.level, 0, 0, 5), misses: num(w.misses, 0), due: num(w.due, 0, 0, 1e15) }
  }
  const progress: Record<string, number> = {}
  for (const [k, v] of Object.entries(obj(o.progress)).slice(0, 500)) progress[k.slice(0, 80)] = num(v, 0, 0, 4)
  const chests: Record<string, true> = {}
  for (const [k, v] of Object.entries(obj(o.chests)).slice(0, 500)) if (v) chests[k.slice(0, 80)] = true
  const d = obj(o.daily)
  const str = (v: unknown, max: number) => (typeof v === 'string' ? v.slice(0, max) : '')
  return {
    ...base,
    onboarded: o.onboarded === true,
    ui: o.ui === 'en' ? 'en' : o.ui === 'fr' ? 'fr' : ui,
    lang: o.lang === 'sw' ? 'sw' : 'ln',
    goal: (GOALS as readonly number[]).includes(Number(o.goal)) ? Number(o.goal) : base.goal,
    sound: o.sound !== false,
    voice: o.voice !== false,
    name: str(o.name, 20).replace(/[\u0000-\u001f<>]/g, ''),
    avatar: AVATARS.includes(String(o.avatar)) ? String(o.avatar) : base.avatar,
    xp: num(o.xp, 0),
    gems: num(o.gems, base.gems),
    hearts: num(o.hearts, MAX_HEARTS, 0, MAX_HEARTS),
    heartsRefillAt: o.heartsRefillAt == null ? null : num(o.heartsRefillAt, 0, 0, 1e15),
    streak: num(o.streak, 0),
    bestStreak: num(o.bestStreak, 0),
    lastActiveDay: /^\d{4}-\d{2}-\d{2}$/.test(String(o.lastActiveDay)) ? String(o.lastActiveDay) : null,
    activeDays: Array.isArray(o.activeDays) ? o.activeDays.filter((d): d is string => typeof d === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(d)).slice(-30) : [],
    freezes: num(o.freezes, 0, 0, MAX_FREEZES),
    doubleXpUntil: num(o.doubleXpUntil, 0, 0, 1e15),
    lessonsDone: num(o.lessonsDone, 0),
    progress,
    chests,
    words,
    daily: {
      day: str(d.day, 10) || dayKey(),
      xp: num(d.xp, 0),
      lessons: num(d.lessons, 0),
      bestCombo: num(d.bestCombo, 0),
      perfect: num(d.perfect, 0),
      claimed: Array.isArray(d.claimed) ? d.claimed.filter((x): x is string => typeof x === 'string').slice(0, 10) : [],
    },
  }
}

/** Converts the previous app's save (`loba-state-v4`) so existing learners keep their progress. */
export function migrateLegacy(raw: unknown, ui: UiLang): GameState {
  const o = obj(raw)
  const s = freshState(o.ui === 'en' ? 'en' : o.ui === 'fr' ? 'fr' : ui)
  const per = obj(o.per)
  const progress: Record<string, number> = {}
  const chests: Record<string, true> = {}
  const words: Record<string, WordStat> = {}
  for (const lang of ['ln', 'sw'] as LangCode[]) {
    const p = obj(per[lang])
    for (const [unit, n] of Object.entries(obj(p.prog))) progress[`${lang}-${unit}`] = num(n, 0, 0, 4)
    for (const [unit, v] of Object.entries(obj(p.chests))) if (v) chests[`${lang}-${unit}`] = true
    for (const [term, v] of Object.entries(obj(p.words))) {
      const w = obj(v)
      words[`${lang}:${term}`] = { level: num(w.lvl, 0, 0, 5), misses: num(w.ko, 0), due: num(w.due, 0, 0, 1e15) }
    }
  }
  const last = typeof o.lastDay === 'string' ? new Date(o.lastDay) : null
  const prof = obj(o.prof)
  const legacyGoal = num(o.goal, 20)
  return {
    ...s,
    onboarded: o.onboarded === true,
    lang: o.lang === 'sw' ? 'sw' : 'ln',
    goal: (GOALS as readonly number[]).includes(legacyGoal) ? legacyGoal : 20,
    sound: o.sound !== false,
    voice: o.voice !== false,
    name: typeof prof.name === 'string' ? prof.name.slice(0, 20) : '',
    avatar: AVATARS.includes(String(prof.av)) ? String(prof.av) : '🦜',
    xp: num(o.xp, 0),
    gems: num(o.gems, 50),
    hearts: num(o.hearts, MAX_HEARTS, 0, MAX_HEARTS),
    heartsRefillAt: null,
    streak: num(o.streak, 0),
    bestStreak: num(o.bestStreak, 0),
    lastActiveDay: last && !Number.isNaN(last.getTime()) ? dayKey(last) : null,
    freezes: num(o.freeze, 0, 0, MAX_FREEZES),
    doubleXpUntil: num(o.boostUntil, 0, 0, 1e15),
    lessonsDone: num(o.total, 0),
    progress,
    chests,
    words,
  }
}
