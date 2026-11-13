import type { Exercise } from '../../game/exercises'
import { gradeTiles, gradeTyped, type Grade } from '../../game/grading'

export type Answer =
  | { kind: 'choice'; index: number }
  | { kind: 'tiles'; picked: number[] }
  | { kind: 'text'; value: string }
  | { kind: 'match'; done: boolean }

export function isAnswerable(e: Exercise, a: Answer | null): boolean {
  if (!a) return false
  if (a.kind === 'tiles') return a.picked.length > 0
  if (a.kind === 'text') return a.value.trim().length > 0
  if (a.kind === 'match') return a.done
  return e.kind !== 'match'
}

export function grade(e: Exercise, a: Answer): Grade {
  switch (e.kind) {
    case 'meaning':
    case 'term':
    case 'listen':
      return a.kind === 'choice' && e.options[a.index] === e.word ? 'correct' : 'wrong'
    case 'type':
      return a.kind === 'text' ? gradeTyped(a.value, e.word.term) : 'wrong'
    case 'build':
      return a.kind === 'tiles' ? gradeTiles(a.picked.map((i) => e.tiles[i]!), e.sentence.answer) : 'wrong'
    case 'match':
      return a.kind === 'match' && a.done ? 'correct' : 'wrong'
  }
}
