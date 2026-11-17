import { createFileRoute, redirect, useNavigate } from '@tanstack/react-router'
import { useState } from 'react'
import { requireAccount } from '../account/guard'
import { Art } from '../components/Art'
import { Loba } from '../components/Loba'
import { LangBadge } from '../components/Stats'
import type { LangCode } from '../content/types'
import { actions, game } from '../game/store'
import { useCurriculum, useLanguages } from '../hooks'
import { useT } from '../i18n/useT'
import type { StringKey } from '../i18n/strings'

/** After the account is created: pick a language and a daily goal, then the first lesson. */
export const Route = createFileRoute('/welcome')({
  beforeLoad: async ({ context }) => {
    await requireAccount(context.queryClient)
    if (game.get().onboarded) throw redirect({ to: '/' })
  },
  component: Welcome,
})

const GOAL_OPTIONS: [xp: number, label: StringKey, minutes: number][] = [
  [10, 'goalCasual', 5],
  [20, 'goalRegular', 10],
  [40, 'goalSerious', 15],
  [60, 'goalIntense', 20],
]

type Step = 'lang' | 'goal'

function Welcome() {
  const { t, ui, langName, region } = useT()
  const navigate = useNavigate()
  const curriculum = useCurriculum()
  const languages = useLanguages()
  const [step, setStep] = useState<Step>('lang')
  const [lang, setLang] = useState<LangCode | null>(languages.length === 1 ? languages[0]!.code : null)
  const [goal, setGoal] = useState(20)

  const finish = () => {
    if (!lang) return
    actions.finishOnboarding(lang, goal)
    navigate({ to: '/lesson/$unitId', params: { unitId: curriculum[lang]![0]!.id }, search: { n: 0 } })
  }

  return (
    <div className="onb">
      <div className="onb-head">
        {step === 'lang' ? <span /> : (
          <button className="icon-btn" onClick={() => setStep('lang')} aria-label="←">
            <span style={{ fontSize: 26, fontWeight: 900 }}>←</span>
          </button>
        )}
        <div className="meter onb-progress" style={{ '--fill': 'var(--green)' } as React.CSSProperties}>
          <span style={{ width: step === 'lang' ? '45%' : '85%' }} />
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
        {step === 'lang' && (
          <>
            <div className="speech"><Loba mood="wave" /><p className="bubble">{t('chooseLang')}</p></div>
            <div className="stack">
              {languages.map((l) => (
                <button key={l.code} className="option-card" aria-pressed={lang === l.code} onClick={() => setLang(l.code)}>
                  <LangBadge lang={l.code} />
                  <span className="body">
                    <h3 style={{ textTransform: 'capitalize' }}>{langName(l.code)}</h3>
                    {region(l.code) && <span>{region(l.code)}</span>}
                  </span>
                  {l.greeting && <span className="right" lang={l.code}>{l.greeting}</span>}
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
          {step === 'lang' && <button className="btn" disabled={!lang} onClick={() => setStep('goal')}>{t('continue')}</button>}
          {step === 'goal' && <button className="btn green" onClick={finish}>{t('firstLesson')}</button>}
        </div>
      </div>
    </div>
  )
}
