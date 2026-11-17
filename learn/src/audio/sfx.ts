import { game } from '../game/store'

export type Cue =
  | 'tap' | 'tile' | 'pop' | 'whoosh'
  | 'correct' | 'wrong' | 'heart'
  | 'coin' | 'chest' | 'tick'
  | 'complete' | 'streak'
  | 'drum' | 'shaker'
  | 'page' | 'splash' | 'call'

/**
 * Loba's sounds are played live, not downloaded: a likembe (the Congolese thumb piano),
 * a ngoma drum and a shaker, all synthesized. The likembe is tuned to a pentatonic scale,
 * so any run of notes sounds like a tune.
 */
const SCALE = [523.25, 587.33, 659.25, 783.99, 880, 1046.5, 1174.66, 1318.51, 1567.98]
const note = (i: number) => SCALE[Math.max(0, Math.min(SCALE.length - 1, i))]!

let ctx: AudioContext | null = null
let out: GainNode
let noiseBuffer: AudioBuffer

function engine(): AudioContext {
  if (!ctx) {
    ctx = new AudioContext()
    const rate = ctx.sampleRate
    out = ctx.createGain()
    out.gain.value = 0.9
    const limiter = ctx.createDynamicsCompressor()
    // A short synthetic room so notes ring like they would in a courtyard, not a phone speaker.
    const room = ctx.createBuffer(2, Math.floor(rate * 0.8), rate)
    for (let ch = 0; ch < 2; ch++) {
      const data = room.getChannelData(ch)
      for (let i = 0; i < data.length; i++) data[i] = (Math.random() * 2 - 1) * (1 - i / data.length) ** 3
    }
    const reverb = ctx.createConvolver()
    reverb.buffer = room
    const wet = ctx.createGain()
    wet.gain.value = 0.16
    out.connect(limiter)
    out.connect(reverb).connect(wet).connect(limiter)
    limiter.connect(ctx.destination)
    noiseBuffer = ctx.createBuffer(1, rate, rate)
    const n = noiseBuffer.getChannelData(0)
    for (let i = 0; i < n.length; i++) n[i] = Math.random() * 2 - 1
  }
  if (ctx.state === 'suspended') void ctx.resume()
  return ctx
}

function envelope(c: AudioContext, t: number, peak: number, decay: number) {
  const g = c.createGain()
  g.gain.setValueAtTime(0.0001, t)
  g.gain.exponentialRampToValueAtTime(peak, t + 0.004)
  g.gain.exponentialRampToValueAtTime(0.0001, t + 0.004 + decay)
  g.connect(out)
  return g
}

function tone(c: AudioContext, type: OscillatorType, from: number, t: number, peak: number, decay: number, to = from, glide = 0.06) {
  const osc = c.createOscillator()
  osc.type = type
  osc.frequency.setValueAtTime(from, t)
  if (to !== from) osc.frequency.exponentialRampToValueAtTime(to, t + glide)
  osc.connect(envelope(c, t, peak, decay))
  osc.start(t)
  osc.stop(t + decay + 0.05)
}

function noise(c: AudioContext, t: number, peak: number, decay: number, type: BiquadFilterType, from: number, to = from) {
  const src = c.createBufferSource()
  src.buffer = noiseBuffer
  src.loop = true
  const filter = c.createBiquadFilter()
  filter.type = type
  filter.Q.value = type === 'bandpass' ? 1.4 : 0.7
  filter.frequency.setValueAtTime(from, t)
  if (to !== from) filter.frequency.exponentialRampToValueAtTime(to, t + decay)
  src.connect(filter).connect(envelope(c, t, peak, decay))
  src.start(t, Math.random() * 0.5)
  src.stop(t + decay + 0.05)
}

/** A plucked metal tine: a pure note, a quick bright overtone and the click of the thumb. */
function likembe(c: AudioContext, freq: number, t: number, v = 1) {
  tone(c, 'sine', freq, t, 0.24 * v, 0.6)
  tone(c, 'sine', freq * 2, t, 0.05 * v, 0.22)
  tone(c, 'sine', freq * 5.4, t, 0.03 * v, 0.05)
  noise(c, t, 0.04 * v, 0.012, 'highpass', 3200)
}
/** A hand drum: the skin's pitch drops as it rings, with the slap of the palm on top. */
function ngoma(c: AudioContext, freq: number, t: number, v = 1) {
  tone(c, 'sine', freq * 1.9, t, 0.55 * v, 0.3, freq)
  noise(c, t, 0.14 * v, 0.05, 'lowpass', 900)
}
const shaker = (c: AudioContext, t: number, v = 1) => noise(c, t, 0.06 * v, 0.07, 'highpass', 5500)
function wood(c: AudioContext, t: number, v = 1) {
  tone(c, 'triangle', 880, t, 0.07 * v, 0.035, 620, 0.03)
  noise(c, t, 0.035 * v, 0.014, 'bandpass', 2200)
}

const STEP = 0.118 // a sixteenth note at rumba tempo
const CUES: Record<Cue, (c: AudioContext, t: number, n: number) => void> = {
  tap: (c, t) => wood(c, t),
  // Each tile placed plays the next note up, so building a sentence plays a little tune.
  tile: (c, t, n) => likembe(c, note(n % 6), t, 0.7),
  pop: (c, t) => tone(c, 'sine', 420, t, 0.12, 0.09, 860, 0.07),
  whoosh: (c, t) => noise(c, t, 0.09, 0.32, 'bandpass', 500, 3200),
  // The answer climbs the scale as the combo grows; from three in a row the drum joins in.
  correct: (c, t, n) => {
    const i = Math.max(0, Math.min(4, n - 1))
    likembe(c, note(i), t)
    likembe(c, note(i + 2), t + 0.09)
    shaker(c, t + 0.09, 0.8)
    if (n >= 3) ngoma(c, 98, t, 0.55)
  },
  wrong: (c, t) => {
    ngoma(c, 82, t, 0.7)
    ngoma(c, 69, t + 0.15, 0.6)
    tone(c, 'triangle', 196, t, 0.05, 0.22, 147, 0.2)
  },
  heart: (c, t) => tone(c, 'triangle', 520, t, 0.07, 0.25, 180, 0.22),
  coin: (c, t) => {
    likembe(c, note(7), t, 0.8)
    likembe(c, note(8), t + 0.07, 0.8)
    shaker(c, t + 0.07)
  },
  tick: (c, t) => likembe(c, note(5), t, 0.22),
  chest: (c, t) => {
    ;[0, 0.11, 0.2, 0.27, 0.32].forEach((at, i) => ngoma(c, i % 2 ? 110 : 98, t + at, 0.4 + i * 0.12))
    ;[0, 2, 3, 5, 7].forEach((i, k) => likembe(c, note(i), t + 0.42 + k * 0.07))
    shaker(c, t + 0.42)
    shaker(c, t + 0.7)
  },
  streak: (c, t) => {
    noise(c, t, 0.1, 0.5, 'bandpass', 300, 2600)
    ngoma(c, 98, t, 0.6)
    ;[2, 4, 5, 7].forEach((i, k) => likembe(c, note(i), t + 0.12 + k * 0.1))
    ngoma(c, 131, t + 0.52, 0.7)
  },
  // A bar of sebene: the guitar-like run that gets a rumba crowd dancing, over a clave on the drum.
  complete: (c, t) => {
    const run: [step: number, scale: number][] = [[0, 3], [1, 4], [2, 5], [3, 4], [4, 5], [5, 6], [6, 7], [8, 6], [9, 5], [10, 7]]
    for (const [s, i] of run) likembe(c, note(i), t + s * STEP, 0.9)
    for (const s of [0, 3, 6, 10, 12]) ngoma(c, s === 12 ? 82 : 98, t + s * STEP, s === 12 ? 0.9 : 0.6)
    for (let s = 0; s <= 12; s += 2) shaker(c, t + s * STEP, 0.7)
    for (const i of [0, 5, 7, 8]) likembe(c, note(i), t + 12 * STEP, 0.8)
  },
  drum: (c, t, n) => ngoma(c, [98, 131, 82][n % 3]!, t, 0.9),
  // Story book: a page turning, each with its own three-note phrase.
  page: (c, t, n) => {
    noise(c, t, 0.06, 0.22, 'bandpass', 900, 2600)
    const phrase = [[0, 2, 4], [2, 4, 5], [4, 2, 0], [3, 5, 7], [5, 4, 2], [1, 3, 5], [4, 5, 7], [7, 5, 4]][n % 8]!
    phrase.forEach((i, k) => likembe(c, note(i), t + 0.14 + k * 0.13, 0.75))
  },
  splash: (c, t) => {
    noise(c, t, 0.16, 0.45, 'lowpass', 2400, 300)
    ngoma(c, 69, t, 0.9)
    ngoma(c, 62, t + 0.22, 0.6)
  },
  // The storyteller's call: two beats of the drum, then the likembe answers.
  call: (c, t) => {
    ngoma(c, 98, t, 0.9)
    ngoma(c, 98, t + 0.2, 0.7)
    ngoma(c, 131, t + 0.4, 0.9)
    ;[2, 4, 5, 7, 5].forEach((i, k) => likembe(c, note(i), t + 0.6 + k * 0.12))
  },
  shaker: (c, t) => {
    shaker(c, t)
    shaker(c, t + 0.09, 0.7)
  },
}

/** Plays a cue if the learner has sounds on. `n` shapes it: combo length, tile position… */
export function sfx(cue: Cue, n = 1) {
  if (!game.get().sound) return
  try {
    const c = engine()
    CUES[cue](c, c.currentTime + 0.01, n)
  } catch {
    /* audio is best-effort */
  }
}

/** A short vibration on phones that support it; follows the same setting as sounds. */
export function buzz(pattern: number | number[]) {
  if (!game.get().sound) return
  try {
    navigator.vibrate?.(pattern)
  } catch {
    /* not supported */
  }
}

let ambient: { stop: () => void } | null = null

/**
 * A quiet bed of sound under the story book: the river by day, and at night a slower river
 * with crickets. Pass null to fade it out. Must first be started from a tap, like every sound.
 */
export function ambience(kind: 'river' | 'night' | null) {
  ambient?.stop()
  ambient = null
  if (!kind || !game.get().sound) return
  try {
    const c = engine()
    const master = c.createGain()
    master.gain.setValueAtTime(0.0001, c.currentTime)
    master.gain.exponentialRampToValueAtTime(1, c.currentTime + 1.5)
    master.connect(out)
    const water = c.createBufferSource()
    water.buffer = noiseBuffer
    water.loop = true
    const filter = c.createBiquadFilter()
    filter.type = 'lowpass'
    filter.frequency.value = kind === 'night' ? 380 : 620
    const level = c.createGain()
    level.gain.value = kind === 'night' ? 0.03 : 0.045
    // The water swells and settles every few seconds.
    const swell = c.createOscillator()
    swell.frequency.value = 0.21
    const depth = c.createGain()
    depth.gain.value = 0.018
    swell.connect(depth).connect(level.gain)
    water.connect(filter).connect(level).connect(master)
    water.start()
    swell.start()
    const crickets = kind === 'night'
      ? setInterval(() => {
          const t = c.currentTime + 0.02
          for (let k = 0; k < 3; k++) {
            const osc = c.createOscillator()
            osc.frequency.value = 4300
            const g = c.createGain()
            g.gain.setValueAtTime(0.0001, t + k * 0.07)
            g.gain.exponentialRampToValueAtTime(0.012, t + k * 0.07 + 0.005)
            g.gain.exponentialRampToValueAtTime(0.0001, t + k * 0.07 + 0.045)
            osc.connect(g).connect(master)
            osc.start(t + k * 0.07)
            osc.stop(t + k * 0.07 + 0.06)
          }
        }, 1300)
      : undefined
    ambient = {
      stop: () => {
        clearInterval(crickets)
        master.gain.cancelScheduledValues(c.currentTime)
        master.gain.setTargetAtTime(0.0001, c.currentTime, 0.25)
        setTimeout(() => {
          water.stop()
          swell.stop()
          master.disconnect()
        }, 1500)
      },
    }
  } catch {
    /* audio is best-effort */
  }
}
