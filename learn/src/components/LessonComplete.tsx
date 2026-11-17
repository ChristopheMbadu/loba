import { useSelector } from '@tanstack/react-store'
import { useEffect, useRef, useState, type CSSProperties } from 'react'
import { sfx } from '../audio/sfx'
import type { Reward } from '../game/rules'
import { accuracyOf, type Session } from '../game/session'
import { game } from '../game/store'
import { useT } from '../i18n/useT'
import { Art, Scenery } from './Art'
import { confetti } from './fx'
import { Icon } from './Icon'
import { Loba } from './Loba'
import { Week } from './Week'
import { RewardSticker } from './RewardSticker'

const fmtTime = (ms: number) => {
  const s = Math.round(ms / 1000)
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`
}

/** Counts up to `target` for a little reward drama. */
function useCountUp(target: number, { ms = 900, delay = 0, from = 0, ticks = false } = {}) {
  const [n, setN] = useState(from)
  useEffect(() => {
    if (matchMedia('(prefers-reduced-motion: reduce)').matches) return setN(target)
    const start = performance.now() + delay
    let lastTick = 0
    let id = requestAnimationFrame(function tick(now) {
      const p = Math.max(0, Math.min(1, (now - start) / ms))
      setN(Math.round(from + (target - from) * (1 - (1 - p) ** 3)))
      if (ticks && p > 0 && p < 1 && now - lastTick > 70) {
        lastTick = now
        sfx('tick')
      }
      if (p < 1) id = requestAnimationFrame(tick)
    })
    return () => cancelAnimationFrame(id)
  }, [target, ms, delay, from, ticks])
  return n
}

/** The streak number rolls over from yesterday's count once the flame has landed. */
function StreakCount({ streak }: { streak: number }) {
  const n = useCountUp(streak, { from: Math.max(0, streak - 1), delay: 550, ms: 300 })
  return <div className="num" key={n}>{n}</div>
}

export function LessonComplete({ reward, session, mode, onContinue }: { reward: Reward; session: Session; mode: 'lesson' | 'practice'; onContinue: () => void }) {
  const { t } = useT()
  const streak = useSelector(game, (s) => s.streak)
  const [step, setStep] = useState<'summary' | 'streak'>('summary')
  const [elapsed] = useState(() => fmtTime(Date.now() - session.startedAt))
  const xp = useCountUp(reward.xp, { delay: 500, ticks: true })
  const accuracy = useCountUp(accuracyOf(session), { delay: 650 })
  const title = mode === 'lesson' ? t('lessonComplete') : t('practiceComplete')
  const fired = useRef(false)

  useEffect(() => {
    if (fired.current) return
    fired.current = true
    confetti()
  }, [])

  const next = () => {
    if (step === 'summary' && reward.streakExtended) {
      sfx('streak')
      setStep('streak')
    } else onContinue()
  }

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Enter' && next()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  })

  return (
    <div className="complete">
      {step === 'summary' ? (
        <div className="complete-body" key="summary">
          <div className="rays" />
          <div className="celebration-art">
            <Scenery kind={1} className="celebration-drum" />
            <Loba mood="dance" />
          </div>
          <h1 className="wave-title" aria-label={title}>
            {[...title].map((ch, i) => (
              <span key={i} aria-hidden="true" style={{ '--i': i } as CSSProperties}>{ch === ' ' ? '\u00a0' : ch}</span>
            ))}
          </h1>
          <div className="tiles">
            <div className="result-tile" style={{ '--c': 'var(--sun)' } as CSSProperties}>
              <div className="label">{t('totalXp')}</div>
              <div className="value"><Art name="bolt" /> {xp}</div>
            </div>
            <div className="result-tile" style={{ '--c': 'var(--green)' } as CSSProperties}>
              <div className="label">{t('accuracy')}</div>
              <div className="value"><Art name="target" /> {accuracy}%</div>
            </div>
            <div className="result-tile" style={{ '--c': 'var(--sky)' } as CSSProperties}>
              <div className="label">{t('time')}</div>
              <div className="value"><Icon name="clock" /> {elapsed}</div>
            </div>
          </div>
          {reward.perfect && reward.gems > 0 && (
            <span className="pill">{t('perfectLesson', { n: reward.gems })} <Art name="gem" size={22} /></span>
          )}
        </div>
      ) : (
        <div className="complete-body streak-step" key="streak">
          <div className="streak-hero">
            <RewardSticker kind="flame" size={170} />
            <StreakCount streak={streak} />
          </div>
          <h1>{t(streak === 1 ? 'streakDay' : 'streakDays', { n: streak })}</h1>
          <Week />
          <p className="muted" style={{ fontWeight: 800 }}>{t('streakKeep')}</p>
        </div>
      )}
      <div className="lesson-foot">
        <div className="foot-inner" style={{ justifyContent: 'flex-end' }}>
          <button className={`btn ${step === 'streak' ? 'sun' : ''}`} onClick={next} autoFocus key={step}>{t('continue')}</button>
        </div>
      </div>
    </div>
  )
}
