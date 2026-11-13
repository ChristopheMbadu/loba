import type { Word } from '../content/types'
import { game } from '../game/store'

/**
 * Pronunciation comes only from recordings made by teachers in Loba Studio.
 * A word without a recording is simply silent — there is no synthetic voice.
 */
export const hasVoice = (w: Word) => !!w.audio

let current: HTMLAudioElement | null = null

export function playWord(w: Word, slow = false) {
  if (!w.audio || !game.get().voice) return
  try {
    current?.pause()
    current = new Audio(`/api/v1/audio/${w.audio}`)
    current.preservesPitch = true
    current.playbackRate = slow ? 0.7 : 1
    void current.play().catch(() => {})
  } catch {
    /* playback is best-effort */
  }
}
