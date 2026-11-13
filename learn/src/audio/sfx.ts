import { game } from '../game/store'

type Cue = 'tap' | 'correct' | 'wrong' | 'complete' | 'coin'

const NOTES: Record<Cue, [freq: number, start: number, dur: number][]> = {
  tap: [[660, 0, 0.05]],
  correct: [[660, 0, 0.09], [990, 0.08, 0.16]],
  wrong: [[220, 0, 0.12], [185, 0.1, 0.18]],
  complete: [[523, 0, 0.12], [659, 0.1, 0.12], [784, 0.2, 0.12], [1047, 0.3, 0.3]],
  coin: [[988, 0, 0.07], [1319, 0.06, 0.18]],
}

let ctx: AudioContext | null = null

/** Soft synthesized cues — no audio files to download. Only ever triggered by a user action. */
export function sfx(cue: Cue) {
  if (!game.get().sound) return
  try {
    ctx ??= new AudioContext()
    const t0 = ctx.currentTime
    for (const [freq, start, dur] of NOTES[cue]) {
      const osc = ctx.createOscillator()
      const gain = ctx.createGain()
      osc.type = 'sine'
      osc.frequency.value = freq
      gain.gain.setValueAtTime(0, t0 + start)
      gain.gain.linearRampToValueAtTime(0.12, t0 + start + 0.01)
      gain.gain.exponentialRampToValueAtTime(0.0001, t0 + start + dur)
      osc.connect(gain).connect(ctx.destination)
      osc.start(t0 + start)
      osc.stop(t0 + start + dur + 0.02)
    }
  } catch {
    /* audio is best-effort */
  }
}
