import type { Exercise } from './exercises'

export type Status = 'answering' | 'correct' | 'typo' | 'wrong'

export interface Session {
  queue: Exercise[]
  index: number
  total: number
  solved: number
  status: Status
  mistakes: number
  combo: number
  bestCombo: number
  startedAt: number
}

export type SessionAction =
  | { type: 'graded'; result: Exclude<Status, 'answering'> }
  | { type: 'next' }

export const startSession = (queue: Exercise[], now = Date.now()): Session => ({
  queue,
  index: 0,
  total: queue.length,
  solved: 0,
  status: 'answering',
  mistakes: 0,
  combo: 0,
  bestCombo: 0,
  startedAt: now,
})

let retryId = 0

export function sessionReducer(s: Session, a: SessionAction): Session {
  if (a.type === 'graded') {
    if (s.status !== 'answering') return s
    if (a.result === 'wrong') {
      // A missed exercise comes back at the end, flagged as a previous mistake.
      const current = s.queue[s.index]!
      const again = { ...current, id: `${current.id}-r${++retryId}`, retry: true } as Exercise
      return { ...s, status: 'wrong', mistakes: s.mistakes + 1, combo: 0, queue: [...s.queue, again] }
    }
    const combo = s.combo + 1
    return { ...s, status: a.result, solved: s.solved + 1, combo, bestCombo: Math.max(s.bestCombo, combo) }
  }
  if (s.status === 'answering') return s
  return { ...s, index: s.index + 1, status: 'answering' }
}

export const isFinished = (s: Session) => s.index >= s.queue.length
export const progressOf = (s: Session) => (s.total ? Math.min(1, s.solved / s.total) : 0)
export const accuracyOf = (s: Session) => {
  const attempts = s.solved + s.mistakes
  return attempts ? Math.round((s.solved / attempts) * 100) : 100
}
