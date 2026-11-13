import { useSelector } from '@tanstack/react-store'
import { useEffect, useState, type CSSProperties } from 'react'
import { sfx } from '../audio/sfx'
import type { Reward } from '../game/rules'
import { accuracyOf, type Session } from '../game/session'
import { game } from '../game/store'
import { useT } from '../i18n/useT'
import { Art } from './Art'
import { Confetti } from './Confetti'
import { Icon } from './Icon'
import { Loba } from './Loba'
import { Week } from './Week'

const fmtTime = (ms: number) => {
  const s = Math.round(ms / 1000)
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`
}

/** Counts up to `target` for a little reward drama. */
function useCountUp(target: number, ms = 700) {
  const [n, setN] = useState(0)
  useEffect(() => {
    if (matchMedia('(prefers-reduced-motion: reduce)').matches) return setN(target)
    const start = performance.now()
    let id = requestAnimationFrame(function tick(now) {
      const p = Math.min(1, (now - start) / ms)
      setN(Math.round(target * (1 - (1 - p) ** 3)))
      if (p < 1) id = requestAnimationFrame(tick)
    })
    return () => cancelAnimationFrame(id)
  }, [target, ms])
  return n
}

export function LessonComplete({ reward, session, mode, onContinue }: { reward: Reward; session: Session; mode: 'lesson' | 'practice'; onContinue: () => void }) {
  const { t } = useT()
  const streak = useSelector(game, (s) => s.streak)
  const [step, setStep] = useState<'summary' | 'streak'>('summary')
  const [elapsed] = useState(() => fmtTime(Date.now() - session.startedAt))
  const xp = useCountUp(reward.xp)
  const accuracy = useCountUp(accuracyOf(session))

  const next = () => {
    if (step === 'summary' && reward.streakExtended) {
      sfx('coin')
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
          <Confetti />
          <div className="rays" />
          <Loba mood="cheer" />
          <h1>{mode === 'lesson' ? t('lessonComplete') : t('practiceComplete')}</h1>
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
            <Art name="flame" />
            <div className="num">{streak}</div>
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
