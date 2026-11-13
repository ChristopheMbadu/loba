import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useState } from 'react'
import { Art } from '../components/Art'
import { Loba } from '../components/Loba'
import { LangBadge } from '../components/Stats'
import type { LangCode } from '../content/types'
import { actions } from '../game/store'
import { useCurriculum } from '../hooks'
import { useT } from '../i18n/useT'
import type { StringKey } from '../i18n/strings'

export const Route = createFileRoute('/welcome')({ component: Welcome })

const GOAL_OPTIONS: [xp: number, label: StringKey, minutes: number][] = [
  [10, 'goalCasual', 5],
  [20, 'goalRegular', 10],
  [40, 'goalSerious', 15],
  [60, 'goalIntense', 20],
]

function Welcome() {
  const { t, ui, langName } = useT()
  const navigate = useNavigate()
  const curriculum = useCurriculum()
  const [step, setStep] = useState<'hello' | 'lang' | 'goal'>('hello')
  const [lang, setLang] = useState<LangCode | null>(null)
  const [goal, setGoal] = useState(20)

  const finish = () => {
    if (!lang) return
    actions.finishOnboarding(lang, goal)
    navigate({ to: '/lesson/$unitId', params: { unitId: curriculum[lang][0]!.id }, search: { n: 0 } })
  }

  return (
    <div className="onb">
      <div className="onb-head">
        {step === 'hello' ? <span /> : (
          <button className="icon-btn" onClick={() => setStep(step === 'goal' ? 'lang' : 'hello')} aria-label="←">
            <span style={{ fontSize: 26, fontWeight: 900 }}>←</span>
          </button>
        )}
        <div className="meter onb-progress" style={{ '--fill': 'var(--green)', visibility: step === 'hello' ? 'hidden' : 'visible' } as React.CSSProperties}>
          <span style={{ width: step === 'lang' ? '40%' : '80%' }} />
        </div>
        <div className="segmented" role="group" aria-label={t('interfaceLang')}>
          {(['fr', 'en'] as const).map((code) => (
            <button key={code} aria-pressed={ui === code} onClick={() => actions.setUi(code)}>
              {code.toUpperCase()}
            </button>
          ))}
        </div>
      </div>

      <div className="onb-body">
        {step === 'hello' && (
          <div className="onb-hero">
            <div className="bubble-top">{t('welcomeTitle')}</div>
            <Loba mood="wave" />
            <p>{t('welcomeText')}</p>
          </div>
        )}

        {step === 'lang' && (
          <>
            <div className="speech"><Loba mood="happy" /><p className="bubble">{t('chooseLang')}</p></div>
            <div className="stack">
              {(['ln', 'sw'] as const).map((code) => (
                <button key={code} className="option-card" aria-pressed={lang === code} onClick={() => setLang(code)}>
                  <LangBadge lang={code} />
                  <span className="body">
                    <h3 style={{ textTransform: 'capitalize' }}>{langName(code)}</h3>
                    <span>{t(code === 'ln' ? 'lnRegion' : 'swRegion')}</span>
                  </span>
                </button>
              ))}
            </div>
          </>
        )}

        {step === 'goal' && (
          <>
            <div className="speech"><Loba mood="think" /><p className="bubble">{t('chooseGoal')}</p></div>
            <div className="stack">
              {GOAL_OPTIONS.map(([xp, label, minutes]) => (
                <button key={xp} className="option-card" aria-pressed={goal === xp} onClick={() => setGoal(xp)}>
                  <Art name={xp >= 40 ? 'flame' : xp >= 20 ? 'bolt' : 'star'} />
                  <span className="body">
                    <h3>{t(label)}</h3>
                    <span>{t('minPerDay', { n: minutes })}</span>
                  </span>
                  <span className="right">{xp} XP</span>
                </button>
              ))}
            </div>
          </>
        )}
      </div>

      <div className="lesson-foot">
        <div className="foot-inner" style={{ justifyContent: 'flex-end' }}>
          {step === 'hello' && <button className="btn" onClick={() => setStep('lang')}>{t('getStarted')}</button>}
          {step === 'lang' && <button className="btn" disabled={!lang} onClick={() => setStep('goal')}>{t('continue')}</button>}
          {step === 'goal' && (
            <button className="btn green" onClick={finish}>
              {t('firstLesson')}
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
