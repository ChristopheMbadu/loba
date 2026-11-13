import { useNavigate } from '@tanstack/react-router'
import { useSelector } from '@tanstack/react-store'
import { useCallback, useEffect, useReducer, useRef, useState } from 'react'
import { sfx } from '../audio/sfx'
import type { LangCode } from '../content/types'
import { trainedWord, type Exercise } from '../game/exercises'
import { PRICES } from '../game/state'
import type { Reward } from '../game/rules'
import { isFinished, progressOf, sessionReducer, startSession } from '../game/session'
import { actions, game } from '../game/store'
import { useT } from '../i18n/useT'
import { grade, isAnswerable, type Answer } from './exercises/answer'
import { Build } from './exercises/Build'
import { Choice } from './exercises/Choice'
import { Match } from './exercises/Match'
import { TypeIn } from './exercises/TypeIn'
import { Art } from './Art'
import { Icon } from './Icon'
import { Loba } from './Loba'
import { LessonComplete } from './LessonComplete'
import { Modal } from './Modal'

const PRAISE: Record<LangCode, string[]> = {
  ln: ['Malamu mingi !', 'Ozali makasi !', 'Bravo !'],
  sw: ['Vizuri sana!', 'Hongera!', 'Safi sana!'],
}

interface Props {
  exercises: Exercise[]
  mode: 'lesson' | 'practice'
  unitId?: string
  lessonIndex?: number
}

export function LessonPlayer({ exercises, mode, unitId, lessonIndex }: Props) {
  const { t, meaning } = useT()
  const navigate = useNavigate()
  const lang = useSelector(game, (s) => s.lang)
  const hearts = useSelector(game, (s) => s.hearts)
  const gems = useSelector(game, (s) => s.gems)
  const [session, dispatch] = useReducer(sessionReducer, exercises, (q) => startSession(q))
  const [answer, setAnswer] = useState<Answer | null>(null)
  const [praise, setPraise] = useState('')
  const [quitOpen, setQuitOpen] = useState(false)
  const [heartsOpen, setHeartsOpen] = useState(mode === 'lesson' && hearts === 0)
  const [reward, setReward] = useState<Reward | null>(null)
  const finishing = useRef(false)

  const ex = session.queue[session.index]
  const status = session.status
  const usesHearts = mode === 'lesson'
  const done = isFinished(session)

  useEffect(() => {
    if (!done || finishing.current) return
    finishing.current = true
    sfx('complete')
    setReward(actions.completeSession({ kind: mode, unitId, lessonIndex, mistakes: session.mistakes, bestCombo: session.bestCombo }))
  }, [done, mode, unitId, lessonIndex, session.mistakes, session.bestCombo])

  const check = useCallback(() => {
    if (!ex || status !== 'answering' || !answer || !isAnswerable(ex, answer)) return
    const g = grade(ex, answer)
    dispatch({ type: 'graded', result: g })
    const word = trainedWord(ex)
    if (word) actions.recordWord(lang, word.term, g !== 'wrong')
    if (ex.kind === 'match') for (const w of ex.pairs) actions.recordWord(lang, w.term, true)
    if (g === 'wrong') {
      sfx('wrong')
      if (usesHearts) actions.loseHeart()
    } else {
      sfx('correct')
      const pool = PRAISE[lang]
      setPraise(Math.random() < 0.5 ? pool[Math.floor(Math.random() * pool.length)]! : t('correct'))
    }
  }, [ex, status, answer, lang, usesHearts, t])

  const next = useCallback(() => {
    if (status === 'answering') return
    if (usesHearts && game.get().hearts === 0) {
      setHeartsOpen(true)
      return
    }
    setAnswer(null)
    dispatch({ type: 'next' })
  }, [status, usesHearts])

  const skip = () => {
    if (!ex || status !== 'answering') return
    dispatch({ type: 'graded', result: 'wrong' })
    sfx('wrong')
    if (usesHearts) actions.loseHeart()
  }

  // Match exercises check themselves once every pair is found.
  useEffect(() => {
    if (ex?.kind === 'match' && answer?.kind === 'match' && answer.done) check()
  }, [ex, answer, check])

  // Enter checks, then continues.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== 'Enter' || quitOpen || heartsOpen || (e.target as HTMLElement).tagName === 'TEXTAREA') return
      if ((e.target as HTMLElement).tagName === 'BUTTON' && status === 'answering') return
      e.preventDefault()
      if (status === 'answering') check()
      else next()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [status, check, next, quitOpen, heartsOpen])

  const exit = () => navigate({ to: mode === 'lesson' ? '/' : '/practice' })

  if (done) {
    if (!reward) return null
    return <LessonComplete reward={reward} session={session} mode={mode} onContinue={exit} />
  }
  if (!ex) return null

  const correctText = ex.kind === 'meaning' ? meaning(ex.word) : ex.kind === 'build' ? ex.sentence.answer : ex.kind === 'match' ? '' : ex.word.term
  const locked = status !== 'answering'

  return (
    <div className="lesson">
      <header className="lesson-head">
        <button className="icon-btn" onClick={() => (session.solved > 0 ? setQuitOpen(true) : exit())} aria-label={t('endSession')}>
          <Icon name="close" />
        </button>
        <div className={`meter ${session.combo >= 3 ? 'hot' : ''}`} role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(progressOf(session) * 100)}>
          <span style={{ width: `${Math.max(progressOf(session) * 100, 2)}%` }} />
        </div>
        {usesHearts && (
          <span key={hearts} className={`lesson-hearts ${status === 'wrong' ? 'hit' : ''}`} aria-label={`${t('hearts')}: ${hearts}`}>
            <Art name={hearts > 0 ? 'heart' : 'heartOff'} /> {hearts}
          </span>
        )}
      </header>
      {session.combo >= 3 && status === 'correct' && <span className="combo-flag">{t('combo', { n: session.combo })}</span>}

      <main className="lesson-body" key={ex.id}>
        {(ex.kind === 'meaning' || ex.kind === 'term' || ex.kind === 'listen') && <Choice ex={ex} answer={answer} onAnswer={setAnswer} locked={locked} />}
        {ex.kind === 'match' && <Match ex={ex} onAnswer={setAnswer} />}
        {ex.kind === 'build' && <Build ex={ex} answer={answer} onAnswer={setAnswer} locked={locked} />}
        {ex.kind === 'type' && <TypeIn ex={ex} answer={answer} onAnswer={setAnswer} onSubmit={() => (status === 'answering' ? check() : next())} locked={locked} />}
      </main>

      <footer className={`lesson-foot ${status}`}>
        <div className="foot-inner" aria-live="polite">
          {status === 'answering' ? (
            <>
              {ex.kind !== 'match' ? <button className="btn outline skip-btn" onClick={skip}>{t('skip')}</button> : <span />}
              <button className="btn green" disabled={!isAnswerable(ex, answer)} onClick={check}>
                {t('check')}
              </button>
            </>
          ) : (
            <>
              <div className="feedback">
                <span className="feedback-icon">
                  <Icon name={status === 'wrong' ? 'x' : 'check'} />
                </span>
                <div>
                  <h2>{status === 'correct' ? praise : status === 'typo' ? t('correctTypo') : t('wrong')}</h2>
                  {status !== 'correct' && <p>{correctText}</p>}
                  {status === 'wrong' && ex.kind === 'build' && <p style={{ opacity: 0.8 }}>{meaning(ex.sentence)}</p>}
                </div>
              </div>
              <button className={`btn ${status === 'wrong' ? 'red' : 'green'}`} onClick={next} autoFocus>
                {t('continue')}
              </button>
            </>
          )}
        </div>
      </footer>

      <Modal open={quitOpen} onClose={() => setQuitOpen(false)} labelledBy="quit-title">
        <div className="modal-art"><Loba mood="sad" /></div>
        <h2 id="quit-title">{t('quitTitle')}</h2>
        <p>{t('quitText')}</p>
        <div className="modal-actions">
          <button className="btn" onClick={() => setQuitOpen(false)}>{t('keepLearning')}</button>
          <button className="btn ghost" style={{ color: "var(--red)" }} onClick={exit}>{t('endSession')}</button>
        </div>
      </Modal>

      <Modal open={heartsOpen} onClose={exit} labelledBy="hearts-title">
        <div className="modal-art"><Art name="heartOff" /></div>
        <h2 id="hearts-title">{t('outOfHeartsTitle')}</h2>
        <p>{t('outOfHeartsText')}</p>
        <div className="modal-actions">
          <button
            className="btn"
            disabled={gems < PRICES.refill}
            onClick={() => {
              actions.buy('refill')
              sfx('coin')
              setHeartsOpen(false)
            }}
          >
            {t('refillFor', { n: PRICES.refill })} <Art name="gem" size={22} />
          </button>
          <button className="btn outline" onClick={() => navigate({ to: '/session', search: { mode: 'review' } })}>{t('practiceInstead')}</button>
          <button className="btn ghost" onClick={exit}>{t('noThanks')}</button>
        </div>
      </Modal>
    </div>
  )
}
