import type { Sentence, Unit, Word } from '../content/types'
import { toTiles } from './grading'
import { sample, shuffle, type Rng } from './random'

export type Exercise =
  | { kind: 'meaning'; id: string; word: Word; options: Word[]; isNew: boolean; retry?: boolean }
  | { kind: 'term'; id: string; word: Word; options: Word[]; retry?: boolean }
  | { kind: 'listen'; id: string; word: Word; options: Word[]; retry?: boolean }
  | { kind: 'match'; id: string; pairs: Word[]; retry?: boolean }
  | { kind: 'build'; id: string; sentence: Sentence; tiles: string[]; retry?: boolean }
  | { kind: 'type'; id: string; word: Word; retry?: boolean }

export interface BuildOptions {
  /** Extra words (from unlocked units) to draw distractors from. */
  pool: Word[]
  /** Whether the learner has audio switched on. Listening needs a teacher's recording too. */
  canListen: boolean
  /** Terms the learner has already met; others are flagged "new". */
  seen: Set<string>
  rng?: Rng
}

let counter = 0
const uid = (kind: string) => `${kind}-${++counter}`

/** Picks 3 distractors whose term and meaning differ from the answer, so no two options look identical. */
function optionsFor(word: Word, pool: Word[], rng: Rng): Word[] {
  const seenTerms = new Set([word.term.toLowerCase()])
  const seenMeanings = new Set([word.fr.toLowerCase()])
  const distractors: Word[] = []
  for (const w of shuffle(pool, rng)) {
    const t = w.term.toLowerCase(), m = w.fr.toLowerCase()
    if (seenTerms.has(t) || seenMeanings.has(m)) continue
    seenTerms.add(t)
    seenMeanings.add(m)
    distractors.push(w)
    if (distractors.length === 3) break
  }
  return shuffle([word, ...distractors], rng)
}

const tilesFor = (s: Sentence, rng: Rng) => {
  const answer = toTiles(s.answer)
  const lower = new Set(answer.map((t) => t.toLowerCase()))
  const extra = s.distractors.filter((d) => !lower.has(d.toLowerCase())).slice(0, 4)
  return shuffle([...answer, ...extra], rng)
}

/**
 * Builds one lesson. Each unit has four lessons that ramp up:
 *  0 – meet the first half of the words   1 – meet the second half
 *  2 – mix everything, first sentences    3 – recall: typing and full sentences
 */
export function buildLesson(unit: Unit, lessonIndex: number, opts: BuildOptions): Exercise[] {
  const rng = opts.rng ?? Math.random
  const pool = [...unit.words, ...opts.pool.filter((w) => !unit.words.includes(w))]
  const half = Math.ceil(unit.words.length / 2)
  const focus = lessonIndex === 0 ? unit.words.slice(0, half) : lessonIndex === 1 ? unit.words.slice(half) : unit.words
  // A short half (e.g. 9 words → 5 + 4) still works; anything under 3 borrows from the whole unit.
  const words = focus.length >= 3 ? focus : unit.words

  const meaning = (w: Word): Exercise => ({ kind: 'meaning', id: uid('meaning'), word: w, options: optionsFor(w, pool, rng), isNew: !opts.seen.has(w.term) })
  const term = (w: Word): Exercise => ({ kind: 'term', id: uid('term'), word: w, options: optionsFor(w, pool, rng) })
  const listen = (w: Word): Exercise =>
    opts.canListen && w.audio ? { kind: 'listen', id: uid('listen'), word: w, options: optionsFor(w, pool, rng) } : term(w)
  const match = (ws: Word[]): Exercise => ({ kind: 'match', id: uid('match'), pairs: sample(ws, Math.min(5, ws.length), rng) })
  const build = (s: Sentence): Exercise => ({ kind: 'build', id: uid('build'), sentence: s, tiles: tilesFor(s, rng) })
  const type = (w: Word): Exercise => ({ kind: 'type', id: uid('type'), word: w })
  const sentences = (n: number) => sample(unit.sentences, n, rng)

  if (lessonIndex <= 1) {
    // Introduce each word, then reinforce. Recall always comes after its introduction.
    const order = shuffle(words, rng)
    const [a, b, ...rest] = order
    const out: Exercise[] = [meaning(a!), meaning(b!), term(sample([a!, b!], 1, rng)[0]!)]
    for (const w of rest) out.push(meaning(w))
    out.push(match(words), listen(sample(order, 1, rng)[0]!), term(sample(rest.length ? rest : order, 1, rng)[0]!))
    return out
  }

  if (lessonIndex === 2) {
    const ws = shuffle(words, rng)
    const builds = sentences(2).map(build)
    const fill = Math.max(0, 2 - builds.length)
    return shuffle(
      [
        ...ws.slice(0, 2 + fill).map(term),
        ...ws.slice(2 + fill, 4 + fill).map(listen),
        ...ws.slice(4 + fill, 6 + fill).map(meaning),
        type(ws[6 % ws.length]!),
        ...builds,
      ],
      rng,
    ).concat(match(ws))
  }

  const ws = shuffle(words, rng)
  const builds = sentences(3).map(build)
  const fill = Math.max(0, 3 - builds.length)
  return shuffle(
    [...ws.slice(0, 3 + fill).map(type), ...ws.slice(3 + fill, 5 + fill).map(listen), ...ws.slice(5 + fill, 7 + fill).map(term), ...builds, match(ws)],
    rng,
  )
}

/** A hearts-free practice session from words due for review. */
export function buildPractice(words: Word[], pool: Word[], mode: 'review' | 'listen', canListen: boolean, rng: Rng = Math.random): Exercise[] {
  const P = pool.length >= 4 ? pool : words
  const ws = words.slice(0, 8)
  const optionsPool = [...ws, ...P]
  if (mode === 'listen' && canListen)
    return shuffle(words.filter((w) => w.audio).slice(0, 8), rng).map((w) => ({ kind: 'listen', id: uid('listen'), word: w, options: optionsFor(w, optionsPool, rng) }) as Exercise)
  const out: Exercise[] = ws.map((w, i) =>
    i % 3 === 2
      ? { kind: 'type', id: uid('type'), word: w }
      : i % 3 === 1
        ? { kind: 'term', id: uid('term'), word: w, options: optionsFor(w, optionsPool, rng) }
        : { kind: 'meaning', id: uid('meaning'), word: w, options: optionsFor(w, optionsPool, rng), isNew: false },
  )
  if (ws.length >= 4) out.splice(2, 0, { kind: 'match', id: uid('match'), pairs: ws.slice(0, 5) })
  return out
}

/** The word an exercise trains, for spaced-repetition tracking. */
export const trainedWord = (e: Exercise): Word | null => ('word' in e ? e.word : null)
