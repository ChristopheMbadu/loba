import { describe, expect, it } from 'vitest'
import { bundledCurriculum } from '../content/curriculum'
import { LESSONS_PER_UNIT } from '../content/types'
import { buildLesson, buildPractice } from './exercises'
import { gradeTiles, gradeTyped, normalize, toTiles } from './grading'
import { migrateLegacy, sanitize } from './migrate'
import { seeded } from './random'
import * as rules from './rules'
import { accuracyOf, isFinished, sessionReducer, startSession } from './session'
import { freshState, HEART_REGEN_MS, MAX_HEARTS, PRICES } from './state'

const curriculum = bundledCurriculum()
const s0 = () => ({ ...freshState('fr'), onboarded: true })

describe('curriculum', () => {
  it('loads both languages in order', () => {
    expect(curriculum.ln.length).toBe(12)
    expect(curriculum.sw.length).toBe(12)
    expect(curriculum.ln.map((u) => u.position)).toEqual([...curriculum.ln.map((u) => u.position)].sort((a, b) => a - b))
  })
})

describe('hearts', () => {
  it('regenerates one heart per interval and stops at max', () => {
    let s = rules.loseHeart(rules.loseHeart(s0(), 0), 0)
    expect(s.hearts).toBe(MAX_HEARTS - 2)
    expect(rules.syncHearts(s, HEART_REGEN_MS - 1).hearts).toBe(MAX_HEARTS - 2)
    expect(rules.syncHearts(s, HEART_REGEN_MS).hearts).toBe(MAX_HEARTS - 1)
    s = rules.syncHearts(s, HEART_REGEN_MS * 10)
    expect(s.hearts).toBe(MAX_HEARTS)
    expect(s.heartsRefillAt).toBeNull()
  })
  it('never goes below zero', () => {
    let s = s0()
    for (let i = 0; i < 9; i++) s = rules.loseHeart(s, 0)
    expect(s.hearts).toBe(0)
  })
})

describe('streak', () => {
  it('starts, extends and resets', () => {
    let s = rules.recordActivity(s0(), '2026-11-10')
    expect(s.streak).toBe(1)
    s = rules.recordActivity(s, '2026-11-10')
    expect(s.streak).toBe(1)
    s = rules.recordActivity(s, '2026-11-11')
    expect(s.streak).toBe(2)
    expect(rules.visibleStreak(s, '2026-11-13')).toBe(0)
    s = rules.recordActivity(s, '2026-11-14')
    expect(s.streak).toBe(1)
    expect(s.bestStreak).toBe(2)
  })
  it('spends freezes to cover missed days', () => {
    let s = { ...rules.recordActivity(s0(), '2026-11-10'), freezes: 2 }
    expect(rules.visibleStreak(s, '2026-11-13')).toBe(1)
    s = rules.recordActivity(s, '2026-11-13')
    expect(s.streak).toBe(2)
    expect(s.freezes).toBe(0)
  })
  it('handles month and DST boundaries', () => {
    expect(rules.daysBetween('2026-10-31', '2026-11-01')).toBe(1)
    expect(rules.daysBetween('2026-03-28', '2026-03-30')).toBe(2)
  })
})

describe('sessions', () => {
  it('awards XP, perfect gems and advances the unit without regressing', () => {
    const unit = curriculum.ln[0]!
    const { state, reward } = rules.completeSession(s0(), { kind: 'lesson', unitId: unit.id, lessonIndex: 0, mistakes: 0, bestCombo: 6 }, 0, '2026-11-13')
    expect(reward).toMatchObject({ xp: 15, gems: 5, perfect: true, streakExtended: true })
    expect(state.progress[unit.id]).toBe(1)
    expect(state.daily).toMatchObject({ xp: 15, lessons: 1, bestCombo: 6, perfect: 1 })
    const replay = rules.completeSession({ ...state, progress: { [unit.id]: 3 } }, { kind: 'lesson', unitId: unit.id, lessonIndex: 0, mistakes: 2, bestCombo: 1 }, 0, '2026-11-13')
    expect(replay.state.progress[unit.id]).toBe(3)
    expect(replay.reward.streakExtended).toBe(false)
  })
  it('doubles XP while the boost is active', () => {
    const { reward } = rules.completeSession({ ...s0(), doubleXpUntil: 100 }, { kind: 'practice', mistakes: 1, bestCombo: 0 }, 50, '2026-11-13')
    expect(reward.xp).toBe(16)
  })
  it('unlocks units sequentially', () => {
    const units = curriculum.ln
    const s = { ...s0(), progress: { [units[0]!.id]: LESSONS_PER_UNIT } }
    expect(rules.unitUnlocked(s, units, 1)).toBe(true)
    expect(rules.unitUnlocked(s, units, 2)).toBe(false)
    expect(rules.currentUnitIndex(s, units)).toBe(1)
  })
})

describe('quests, shop and chests', () => {
  it('pays a quest once', () => {
    let s = { ...s0(), daily: { ...s0().daily, day: '2026-11-13', perfect: 1 } }
    s = rules.claimQuest(s, 'perfect', '2026-11-13')
    expect(s.gems).toBe(70)
    expect(rules.claimQuest(s, 'perfect', '2026-11-13').gems).toBe(70)
    expect(rules.claimQuest(s, 'lessons', '2026-11-13').gems).toBe(70)
  })
  it('only sells what is useful and affordable', () => {
    const s = { ...s0(), gems: 500 }
    expect(rules.canBuy(s, 'refill', 0)).toBe(false)
    const hurt = rules.loseHeart(s, 0)
    expect(rules.buy(hurt, 'refill', 0)).toMatchObject({ hearts: MAX_HEARTS, gems: 500 - PRICES.refill })
    const frozen = rules.buy(rules.buy(rules.buy(s, 'freeze', 0), 'freeze', 0), 'freeze', 0)
    expect(frozen.freezes).toBe(2)
    expect(rules.canBuy({ ...s, gems: 10 }, 'freeze', 0)).toBe(false)
  })
  it('opens a finished unit chest once', () => {
    const u = curriculum.ln[0]!
    expect(rules.openChest(s0(), u).gems).toBe(50)
    const done = { ...s0(), progress: { [u.id]: LESSONS_PER_UNIT } }
    const opened = rules.openChest(done, u)
    expect(opened.gems).toBe(75)
    expect(rules.openChest(opened, u).gems).toBe(75)
  })
})

describe('spaced repetition', () => {
  it('schedules correct words later and brings misses back', () => {
    const w = curriculum.ln[0]!.words[0]!
    let s = rules.recordWord(s0(), 'ln', w.term, true, 0)
    expect(rules.reviewWords(s, 'ln', [w], 0)).toEqual([])
    s = rules.recordWord(s, 'ln', w.term, false, 0)
    expect(rules.reviewWords(s, 'ln', [w], 0)).toEqual([w])
    expect(rules.wordsLearned(rules.recordWord(s0(), 'ln', w.term, true, 0), 'ln')).toBe(1)
  })
})

describe('grading', () => {
  it('forgives case, accents and punctuation', () => {
    expect(normalize('  Sango NINI ? ')).toBe('sango nini')
    expect(gradeTyped('sango nini', 'Sango nini?')).toBe('correct')
    expect(gradeTyped('matondoo', 'Matondo')).toBe('typo')
    expect(gradeTyped('mbote', 'Matondo')).toBe('wrong')
    expect(gradeTyped('', 'Ee')).toBe('wrong')
    expect(gradeTyped('e', 'Ee')).toBe('wrong')
  })
  it('splits and grades tiles', () => {
    expect(toTiles('Nkombo na yo nani?')).toEqual(['Nkombo', 'na', 'yo', 'nani'])
    expect(gradeTiles(['Nkombo', 'na', 'yo', 'nani'], 'Nkombo na yo nani?')).toBe('correct')
    expect(gradeTiles(['na', 'Nkombo', 'yo', 'nani'], 'Nkombo na yo nani?')).toBe('wrong')
  })
})

describe('exercise generation', () => {
  for (const lang of ['ln', 'sw'] as const)
    it(`builds valid lessons for every ${lang} unit`, () => {
      for (const unit of curriculum[lang])
        for (let i = 0; i < LESSONS_PER_UNIT; i++) {
          const ex = buildLesson(unit, i, { pool: curriculum[lang].flatMap((u) => u.words), canListen: true, seen: new Set(), rng: seeded(i + unit.position) })
          expect(ex.length).toBeGreaterThanOrEqual(7)
          for (const e of ex) {
            if ('options' in e) {
              expect(e.options).toHaveLength(4)
              expect(e.options).toContain(e.word)
              expect(new Set(e.options.map((o) => o.fr.toLowerCase())).size).toBe(4)
              expect(new Set(e.options.map((o) => o.term.toLowerCase())).size).toBe(4)
            }
            if (e.kind === 'match') expect(e.pairs.length).toBeGreaterThanOrEqual(3)
            if (e.kind === 'build') for (const t of toTiles(e.sentence.answer)) expect(e.tiles).toContain(t)
          }
        }
    })
  it('introduces a word before asking to recall it in the first lesson', () => {
    const unit = curriculum.ln[0]!
    const ex = buildLesson(unit, 0, { pool: [], canListen: false, seen: new Set(), rng: seeded(3) })
    const introduced = new Set<string>()
    for (const e of ex) {
      if (e.kind === 'meaning') introduced.add(e.word.term)
      if (e.kind === 'term') expect(introduced.has(e.word.term)).toBe(true)
      expect(e.kind).not.toBe('listen')
    }
  })
  it('only asks listening questions for words a teacher has recorded', () => {
    const unit = curriculum.ln[0]!
    const silent = buildLesson(unit, 2, { pool: [], canListen: true, seen: new Set(), rng: seeded(5) })
    expect(silent.some((e) => e.kind === 'listen')).toBe(false)
    const recorded = { ...unit, words: unit.words.map((w) => ({ ...w, audio: '00000000-0000-4000-8000-000000000000' })) }
    expect(buildLesson(recorded, 2, { pool: [], canListen: true, seen: new Set(), rng: seeded(5) }).some((e) => e.kind === 'listen')).toBe(true)
    expect(buildLesson(recorded, 2, { pool: [], canListen: false, seen: new Set(), rng: seeded(5) }).some((e) => e.kind === 'listen')).toBe(false)
    expect(buildPractice(unit.words, unit.words, 'listen', true)).toEqual([])
  })
  it('builds practice sessions', () => {
    const words = curriculum.sw[0]!.words.map((w) => ({ ...w, audio: '00000000-0000-4000-8000-000000000000' }))
    expect(buildPractice(words, words, 'listen', true).every((e) => e.kind === 'listen')).toBe(true)
    expect(buildPractice(words, words, 'review', true).some((e) => e.kind === 'match')).toBe(true)
  })
})

describe('session reducer', () => {
  it('re-queues mistakes and finishes when everything is solved', () => {
    const unit = curriculum.ln[0]!
    let s = startSession(buildLesson(unit, 0, { pool: [], canListen: false, seen: new Set(), rng: seeded(1) }))
    const total = s.total
    s = sessionReducer(s, { type: 'graded', result: 'wrong' })
    expect(s.queue.length).toBe(total + 1)
    expect(s.queue.at(-1)!.retry).toBe(true)
    expect(sessionReducer(s, { type: 'graded', result: 'correct' })).toBe(s)
    s = sessionReducer(s, { type: 'next' })
    while (!isFinished(s)) s = sessionReducer(sessionReducer(s, { type: 'graded', result: 'correct' }), { type: 'next' })
    expect(s.solved).toBe(total)
    expect(s.bestCombo).toBe(total)
    expect(accuracyOf(s)).toBe(Math.round((total / (total + 1)) * 100))
  })
})

describe('saved state', () => {
  it('migrates the previous app’s progress', () => {
    const legacy = {
      ui: 'en', lang: 'sw', goal: 40, xp: 340, gems: 120, streak: 6, bestStreak: 9, lastDay: 'Thu Nov 12 2026', freeze: 1, total: 17,
      prof: { name: 'Ami', av: '🦁' }, onboarded: true,
      per: { ln: { prog: { mbote: 4, survie: 2 }, chests: { mbote: true }, words: { Mbote: { lvl: 3, ko: 1, due: 5 } } }, sw: { prog: { salamu: 1 } } },
    }
    const s = migrateLegacy(legacy, 'fr')
    expect(s).toMatchObject({ ui: 'en', lang: 'sw', goal: 40, xp: 340, gems: 120, streak: 6, freezes: 1, lessonsDone: 17, name: 'Ami', avatar: '🦁', onboarded: true, lastActiveDay: '2026-11-12' })
    expect(s.progress).toEqual({ 'ln-mbote': 4, 'ln-survie': 2, 'sw-salamu': 1 })
    expect(s.chests).toEqual({ 'ln-mbote': true })
    expect(s.words['ln:Mbote']).toEqual({ level: 3, misses: 1, due: 5 })
    for (const id of Object.keys(s.progress)) expect([...curriculum.ln, ...curriculum.sw].some((u) => u.id === id)).toBe(true)
  })
  it('rejects malformed saves without crashing', () => {
    expect(sanitize(null, 'fr').onboarded).toBe(false)
    const s = sanitize({ v: 1, hearts: 99, gems: -4, avatar: '<script>', name: '<b>x</b>', progress: { a: 'NaN' }, words: { 'evil': {} } }, 'en')
    expect(s).toMatchObject({ hearts: MAX_HEARTS, gems: 0, avatar: '🦜', name: 'bx/b' })
    expect(s.words).toEqual({})
  })
})
