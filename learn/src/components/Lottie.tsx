import { useEffect, useRef } from 'react'
import type { AnimationItem } from 'lottie-web'

/**
 * Animations from the LottieFiles library, recoloured to Loba's palette (see src/lottie).
 * Each file and the player load on demand, so screens that don't use them pay nothing.
 */
const FILES = {
  heart: () => import('../lottie/heart.json'),
  fish: () => import('../lottie/fish.json'),
  elephant: () => import('../lottie/elephant.json'),
  gem: () => import('../lottie/gem.json'),
  africa: () => import('../lottie/africa.json'),
  pirogue: () => import('../lottie/pirogue.json'),
  sparkle: () => import('../lottie/sparkle.json'),
}

interface Props {
  name: keyof typeof FILES
  className?: string
  loop?: boolean
  /** Play on arrival. When false the animation waits on its resting frame for `replay` to change. */
  autoplay?: boolean
  /** Play once every so many milliseconds, e.g. a fish that leaps now and then. */
  every?: number
  /** Change this value to play again from the start. */
  replay?: number
  /** Where the animation rests when it isn't playing: 0 is the first frame, 1 the last. */
  rest?: number
  /** Crops the drawing, for files with a lot of empty space around the subject. */
  viewBox?: string
}

export function Lottie({ name, className = '', loop = false, autoplay = true, every, replay = 0, rest = 1, viewBox }: Props) {
  const box = useRef<HTMLSpanElement>(null)
  const anim = useRef<AnimationItem | null>(null)
  const firstReplay = useRef(replay)

  useEffect(() => {
    const el = box.current
    if (!el) return
    let gone = false
    let timer: ReturnType<typeof setInterval> | undefined
    const calm = matchMedia('(prefers-reduced-motion: reduce)').matches
    void Promise.all([import('lottie-web/build/player/lottie_light'), FILES[name]()]).then(([player, file]) => {
      if (gone) return
      const a = player.default.loadAnimation({
        container: el,
        renderer: 'svg',
        loop: loop && !calm,
        autoplay: false,
        // The player changes the data it is given, so each instance gets its own copy.
        animationData: structuredClone(file.default),
        rendererSettings: { preserveAspectRatio: 'xMidYMid meet' },
      })
      anim.current = a
      if (viewBox) el.querySelector('svg')?.setAttribute('viewBox', viewBox)
      const resting = Math.max(0, Math.round((a.totalFrames - 1) * rest))
      if (calm || !autoplay) a.goToAndStop(resting, true)
      else a.play()
      if (every && !calm) timer = setInterval(() => a.goToAndPlay(0, true), every)
    })
    return () => {
      gone = true
      clearInterval(timer)
      anim.current?.destroy()
      anim.current = null
    }
  }, [name, loop, autoplay, every, rest, viewBox])

  useEffect(() => {
    if (replay === firstReplay.current) return
    if (!matchMedia('(prefers-reduced-motion: reduce)').matches) anim.current?.goToAndPlay(0, true)
  }, [replay])

  return <span ref={box} className={`lottie ${className}`} aria-hidden="true" />
}
