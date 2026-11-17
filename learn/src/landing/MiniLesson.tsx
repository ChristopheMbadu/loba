import { Link } from '@tanstack/react-router'
import { useMemo, useState } from 'react'
import { sfx } from '../audio/sfx'
import { Art } from '../components/Art'
import { confetti } from '../components/fx'
import { Loba } from '../components/Loba'
import { LangBadge } from '../components/Stats'
import { useCurriculum, useLanguages } from '../hooks'
import { useT } from '../i18n/useT'
import { QUIZ, quizFromUnit, type Taste } from './copy'
import { useStartLink } from './useStart'
import { useLanding } from './useLanding'

type Phase = 'answer' | 'right' | 'wrong' | 'done'

/** A three-question taste of a real Loba lesson, playable without an account. */
export function MiniLesson({ taste, setTaste }: { taste: Taste; setTaste: (t: Taste) => void }) {
  const { ui, langName } = useT()
  const t = useLanding()
  const languages = useLanguages()
  const curriculum = useCurriculum()
  const start = useStartLink()
  const questions = useMemo(() => QUIZ[taste]?.[ui] ?? quizFromUnit(curriculum[taste]![0]!, ui), [taste, ui, curriculum])
  const [step, setStep] = useState(0)
  const [phase, setPhase] = useState<Phase>('answer')
  const [picked, setPicked] = useState<number | null>(null)
  const [mistakes, setMistakes] = useState(0)
  // pairs state
  const [left, setLeft] = useState<string | null>(null)
  const [matched, setMatched] = useState<string[]>([])
  const [miss, setMiss] = useState<string | null>(null)

  const q = questions[step]!
  const rightCol = useMemo(() => (q.kind === 'pairs' ? [...q.pairs].sort((a, b) => a[1].localeCompare(b[1])) : []), [q])

  const reset = (next: Taste = taste) => {
    setTaste(next)
    setStep(0); setPhase('answer'); setPicked(null); setMistakes(0)
    setLeft(null); setMatched([]); setMiss(null)
  }

  const check = () => {
    if (q.kind === 'pairs' || picked === null) return
    const ok = picked === q.answer
    sfx(ok ? 'correct' : 'wrong')
    if (!ok) setMistakes((m) => m + 1)
    setPhase(ok ? 'right' : 'wrong')
  }

  const next = () => {
    if (step === questions.length - 1) {
      sfx('complete')
      confetti()
      setPhase('done')
      return
    }
    setStep(step + 1); setPhase('answer'); setPicked(null)
  }

  const pickRight = (term: string) => {
    if (!left) return
    if (left === term) {
      sfx('correct')
      const all = [...matched, term]
      setMatched(all); setLeft(null)
      if (q.kind === 'pairs' && all.length === q.pairs.length) setPhase('right')
    } else {
      sfx('wrong')
      setMistakes((m) => m + 1)
      setMiss(term); setLeft(null)
      window.setTimeout(() => setMiss(null), 340)
    }
  }

  const progress = phase === 'done' ? 100 : ((step + (phase === 'right' || phase === 'wrong' ? 1 : 0)) / questions.length) * 100
  const praise = languages.find((l) => l.code === taste)?.cheer || t('right')

  return (
    <div className="ml">
      <div className="ml-top">
        <div className="ml-tabs" role="group" aria-label={t('playTitle')}>
          {languages.map(({ code }) => (
            <button key={code} aria-pressed={taste === code} onClick={() => taste !== code && reset(code)}>
              <LangBadge lang={code} /> <span style={{ textTransform: 'capitalize' }}>{langName(code)}</span>
            </button>
          ))}
        </div>
        <div className="meter ml-meter" style={{ '--fill': 'var(--green)' } as React.CSSProperties} role="progressbar" aria-valuenow={Math.round(progress)} aria-valuemin={0} aria-valuemax={100}>
          <span style={{ width: `${Math.max(progress, 4)}%` }} />
        </div>
      </div>

      {phase === 'done' ? (
        <div className="ml-done">
          <Loba mood="dance" />
          <h3>{t('doneTitle')}</h3>
          <p>{t('doneText')}</p>
          <div className="ml-score">
            <span><Art name="bolt" size={28} /> +{15 - mistakes * 2 > 5 ? 15 - mistakes * 2 : 5} XP</span>
            <span><Art name="target" size={28} /> {Math.max(0, 100 - mistakes * 15)}%</span>
          </div>
          <div className="ml-done-ctas">
            <Link {...start} className="btn green">{start.to === '/account' ? t('doneCta') : t('start')}</Link>
            <button className="btn outline" onClick={() => reset()}>{t('again')}</button>
          </div>
        </div>
      ) : (
        <>
          <div className="ml-body" key={`${taste}-${step}`}>
            <h3 className="ml-q">{q.kind === 'meaning' ? t('qMeaning') : q.kind === 'say' ? t('qSay') : t('qPairs')}</h3>

            {q.kind !== 'pairs' && (
              <>
                <div className="speech">
                  <Loba mood={phase === 'wrong' ? 'sad' : phase === 'right' ? 'happy' : 'think'} />
                  <p className="bubble"><span className="prompt-text" lang={q.kind === 'meaning' ? taste : ui}>{q.kind === 'say' ? `« ${q.prompt} »` : q.prompt}</span></p>
                </div>
                <div className="choices" role="group">
                  {q.options.map((opt, i) => {
                    const locked = phase !== 'answer'
                    const state = locked ? (i === q.answer ? 'is-correct' : i === picked ? 'is-wrong' : '') : ''
                    return (
                      <button key={opt} className={`choice ${state}`} aria-pressed={picked === i} disabled={locked}
                        onClick={() => { sfx('tap'); setPicked(i) }}>
                        <span className="key" aria-hidden="true">{i + 1}</span>
                        <span lang={q.kind === 'say' ? taste : ui}>{opt}</span>
                      </button>
                    )
                  })}
                </div>
              </>
            )}

            {q.kind === 'pairs' && (
              <div className="match">
                <div className="col">
                  {q.pairs.map(([term]) => (
                    <button key={term} lang={taste} className={`choice ${matched.includes(term) ? 'matched' : ''}`} aria-pressed={left === term}
                      disabled={matched.includes(term)} onClick={() => { sfx('tap'); setLeft(term) }}>{term}</button>
                  ))}
                </div>
                <div className="col">
                  {rightCol.map(([term, meaning]) => (
                    <button key={term} className={`choice ${matched.includes(term) ? 'matched' : ''} ${miss === term ? 'shake' : ''}`}
                      disabled={matched.includes(term) || !left} onClick={() => pickRight(term)}>{meaning}</button>
                  ))}
                </div>
              </div>
            )}
          </div>

          <div className={`ml-foot ${phase}`} aria-live="polite">
            {phase === 'answer' ? (
              q.kind !== 'pairs' && <button className="btn green" disabled={picked === null} onClick={check}>{t('check')}</button>
            ) : (
              <>
                <div className="ml-feedback">
                  <b>{phase === 'right' ? praise : t('wrong')}</b>
                  {phase === 'wrong' && q.kind !== 'pairs' && <span lang={q.kind === 'say' ? taste : ui}>{q.options[q.answer]}</span>}
                </div>
                <button className={`btn ${phase === 'wrong' ? 'red' : 'green'}`} onClick={next} autoFocus>{t('next')}</button>
              </>
            )}
          </div>
        </>
      )}
    </div>
  )
}
