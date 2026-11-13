import { LESSONS_PER_UNIT, type LangCode, type Unit, type Word } from '../content/types'
import { emptyDaily, HEART_REGEN_MS, MAX_FREEZES, MAX_HEARTS, PRICES, REWARDS, type GameState } from './state'

// ---------- dates ----------
const toUtc = (key: string) => {
  const [y, m, d] = key.split('-').map(Number)
  return Date.UTC(y!, m! - 1, d!)
}
export const daysBetween = (a: string, b: string) => Math.round((toUtc(b) - toUtc(a)) / 864e5)

// ---------- hearts ----------
/** Applies passive heart regeneration up to `now`. */
export function syncHearts(s: GameState, now: number): GameState {
  if (s.hearts >= MAX_HEARTS) return s.heartsRefillAt === null ? s : { ...s, heartsRefillAt: null }
  let hearts = s.hearts
  let at = s.heartsRefillAt ?? now + HEART_REGEN_MS
  while (hearts < MAX_HEARTS && now >= at) {
    hearts++
    at += HEART_REGEN_MS
  }
  const heartsRefillAt = hearts >= MAX_HEARTS ? null : at
  return hearts === s.hearts && heartsRefillAt === s.heartsRefillAt ? s : { ...s, hearts, heartsRefillAt }
}

export function loseHeart(s: GameState, now: number): GameState {
  const synced = syncHearts(s, now)
  return {
    ...synced,
    hearts: Math.max(0, synced.hearts - 1),
    heartsRefillAt: synced.heartsRefillAt ?? now + HEART_REGEN_MS,
  }
}

// ---------- streak ----------
/** The streak as it should be shown today (0 if broken and not covered by freezes). */
export function visibleStreak(s: GameState, today: string): number {
  if (!s.lastActiveDay) return 0
  const gap = daysBetween(s.lastActiveDay, today)
  if (gap <= 1) return s.streak
  return gap - 1 <= s.freezes ? s.streak : 0
}

export function recordActivity(s: GameState, today: string): GameState {
  if (s.lastActiveDay === today) return s
  let { streak, freezes } = s
  const gap = s.lastActiveDay ? daysBetween(s.lastActiveDay, today) : Infinity
  if (gap === 1) streak += 1
  else if (Number.isFinite(gap) && gap > 1 && gap - 1 <= freezes) {
    freezes -= gap - 1
    streak += 1
  } else streak = 1
  return { ...s, streak, freezes, bestStreak: Math.max(s.bestStreak, streak), lastActiveDay: today, activeDays: [...s.activeDays, today].slice(-30) }
}

export const rollDaily = (s: GameState, today: string): GameState =>
  s.daily.day === today ? s : { ...s, daily: emptyDaily(today) }

// ---------- progression ----------
export const unitProgress = (s: GameState, u: Unit) => Math.min(LESSONS_PER_UNIT, s.progress[u.id] ?? 0)
export const unitDone = (s: GameState, u: Unit) => unitProgress(s, u) >= LESSONS_PER_UNIT
export const unitUnlocked = (s: GameState, units: Unit[], i: number) => i === 0 || unitDone(s, units[i - 1]!)
export const currentUnitIndex = (s: GameState, units: Unit[]) => {
  const i = units.findIndex((u) => !unitDone(s, u))
  return i === -1 ? units.length - 1 : i
}
export const unlockedWords = (s: GameState, units: Unit[]): Word[] =>
  units.filter((_, i) => unitUnlocked(s, units, i)).flatMap((u) => u.words)

export const xpMultiplier = (s: GameState, now: number) => (now < s.doubleXpUntil ? 2 : 1)

export interface SessionResult {
  kind: 'lesson' | 'practice'
  unitId?: string
  lessonIndex?: number
  mistakes: number
  bestCombo: number
}

export interface Reward {
  xp: number
  gems: number
  perfect: boolean
  streakExtended: boolean
}

export function lessonXp(r: SessionResult) {
  return (r.kind === 'lesson' ? 10 : 8) + (r.mistakes === 0 ? 5 : 0)
}

export function completeSession(s: GameState, r: SessionResult, now: number, today: string): { state: GameState; reward: Reward } {
  let next = rollDaily(s, today)
  const perfect = r.mistakes === 0
  const xp = lessonXp(r) * xpMultiplier(next, now)
  const gems = perfect && r.kind === 'lesson' ? REWARDS.perfect : 0
  const before = next.lastActiveDay
  next = recordActivity(next, today)
  const progress = { ...next.progress }
  if (r.kind === 'lesson' && r.unitId !== undefined && r.lessonIndex !== undefined) {
    // Replaying an earlier lesson never moves progress backwards.
    progress[r.unitId] = Math.min(LESSONS_PER_UNIT, Math.max(progress[r.unitId] ?? 0, r.lessonIndex + 1))
  }
  next = {
    ...next,
    xp: next.xp + xp,
    gems: next.gems + gems,
    lessonsDone: next.lessonsDone + 1,
    progress,
    daily: {
      ...next.daily,
      xp: next.daily.xp + xp,
      lessons: next.daily.lessons + 1,
      bestCombo: Math.max(next.daily.bestCombo, r.bestCombo),
      perfect: next.daily.perfect + (perfect ? 1 : 0),
    },
  }
  return { state: next, reward: { xp, gems, perfect, streakExtended: before !== today } }
}

// ---------- spaced repetition ----------
const INTERVAL_DAYS = [0, 1, 2, 4, 7, 14]
export const wordKey = (lang: LangCode, term: string) => `${lang}:${term}`

export function recordWord(s: GameState, lang: LangCode, term: string, correct: boolean, now: number): GameState {
  const key = wordKey(lang, term)
  const prev = s.words[key] ?? { level: 0, misses: 0, due: now }
  const level = correct ? Math.min(5, prev.level + 1) : Math.max(0, prev.level - 2)
  const stat = {
    level,
    misses: prev.misses + (correct ? 0 : 1),
    due: correct ? now + INTERVAL_DAYS[level]! * 864e5 : now,
  }
  return { ...s, words: { ...s.words, [key]: stat } }
}

/** Words seen before, weakest and most overdue first. */
export function reviewWords(s: GameState, lang: LangCode, pool: Word[], now: number): Word[] {
  return pool
    .map((w) => ({ w, st: s.words[wordKey(lang, w.term)] }))
    .filter((x) => x.st && x.st.due <= now)
    .sort((a, b) => a.st!.level - b.st!.level || b.st!.misses - a.st!.misses || a.st!.due - b.st!.due)
    .map((x) => x.w)
}

export const wordsLearned = (s: GameState, lang: LangCode) =>
  Object.entries(s.words).filter(([k, v]) => k.startsWith(`${lang}:`) && v.level >= 1).length

// ---------- quests ----------
export type QuestId = 'xp' | 'lessons' | 'combo' | 'perfect'
export interface Quest {
  id: QuestId
  icon: string
  goal: number
  value: number
  reward: number
}

export function dailyQuests(s: GameState, today: string): Quest[] {
  const d = s.daily.day === today ? s.daily : emptyDaily(today)
  return [
    { id: 'xp', icon: '⚡', goal: s.goal, value: d.xp, reward: 15 },
    { id: 'lessons', icon: '📘', goal: 2, value: d.lessons, reward: 15 },
    { id: 'combo', icon: '🔥', goal: 8, value: d.bestCombo, reward: 20 },
    { id: 'perfect', icon: '🎯', goal: 1, value: d.perfect, reward: 20 },
  ]
}

export function claimQuest(s: GameState, id: QuestId, today: string): GameState {
  const next = rollDaily(s, today)
  const q = dailyQuests(next, today).find((x) => x.id === id)
  if (!q || q.value < q.goal || next.daily.claimed.includes(id)) return s
  return { ...next, gems: next.gems + q.reward, daily: { ...next.daily, claimed: [...next.daily.claimed, id] } }
}

// ---------- shop ----------
export type ShopItem = 'refill' | 'freeze' | 'doubleXp'

export function canBuy(s: GameState, item: ShopItem, now: number): boolean {
  if (s.gems < PRICES[item]) return false
  if (item === 'refill') return syncHearts(s, now).hearts < MAX_HEARTS
  if (item === 'freeze') return s.freezes < MAX_FREEZES
  return now >= s.doubleXpUntil
}

export function buy(s: GameState, item: ShopItem, now: number): GameState {
  if (!canBuy(s, item, now)) return s
  const paid = { ...s, gems: s.gems - PRICES[item] }
  if (item === 'refill') return { ...paid, hearts: MAX_HEARTS, heartsRefillAt: null }
  if (item === 'freeze') return { ...paid, freezes: paid.freezes + 1 }
  return { ...paid, doubleXpUntil: now + 15 * 60 * 1000 }
}

export function openChest(s: GameState, u: Unit): GameState {
  if (!unitDone(s, u) || s.chests[u.id]) return s
  return { ...s, gems: s.gems + REWARDS.chest, chests: { ...s.chests, [u.id]: true } }
}
