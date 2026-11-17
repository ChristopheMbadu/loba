import { useEffect, useState, type CSSProperties } from 'react'
import type { Word } from '../../content/types'
import { hasVoice, playWord } from '../../audio/voice'
import { sfx } from '../../audio/sfx'
import { useSelector } from '@tanstack/react-store'
import type { Exercise } from '../../game/exercises'
import { game } from '../../game/store'
import { useT } from '../../i18n/useT'
import { Icon } from '../Icon'
import { Loba } from '../Loba'
import type { Answer } from './answer'

type ChoiceExercise = Extract<Exercise, { kind: 'meaning' | 'term' | 'listen' }>

export function SpeakButton({ word, big }: { word: Word; big?: boolean }) {
  const { t } = useT()
  const [ring, setRing] = useState(0)
  if (!hasVoice(word)) return null
  return (
    <button className={`speak-btn ${big ? 'big' : ''}`} onClick={() => { playWord(word); setRing((n) => n + 1) }} aria-label={t('play')}>
      <Icon name="speaker" />
      {ring > 0 && <i className="speak-ring" key={ring} />}
    </button>
  )
}

/** Meaning, recall and listening exercises: pick one of four. Keys 1–4 select. */
export function Choice({ ex, answer, onAnswer, locked }: { ex: ChoiceExercise; answer: Answer | null; onAnswer: (a: Answer) => void; locked: boolean }) {
  const { t, meaning, langName } = useT()
  const lang = useSelector(game, (s) => s.lang)
  const selected = answer?.kind === 'choice' ? answer.index : null
  const label = (w: Word) => (ex.kind === 'meaning' ? meaning(w) : w.term)

  useEffect(() => {
    if (ex.kind !== 'term') playWord(ex.word)
  }, [ex])

  useEffect(() => {
    if (locked) return
    const onKey = (e: KeyboardEvent) => {
      if ((e.target as HTMLElement).tagName === 'INPUT') return
      const i = Number(e.key) - 1
      if (i >= 0 && i < ex.options.length) {
        sfx('tap')
        onAnswer({ kind: 'choice', index: i })
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [ex, locked, onAnswer])

  return (
    <>
      <div className="ex-head">
        {ex.retry ? <span className="ex-tag retry">↻ {t('previousMistake')}</span> : ex.kind === 'meaning' && ex.isNew && <span className="ex-tag">✦ {t('newWord')}</span>}
        <h1>{ex.kind === 'meaning' ? t('pickMeaning') : ex.kind === 'term' ? t('sayIn', { lang: langName(lang) }) : t('listenPick')}</h1>
      </div>

      {ex.kind === 'meaning' && (
        <div className="speech">
          <Loba mood="idle" />
          <div className="bubble">
            <SpeakButton word={ex.word} />
            <span className="prompt-text">{ex.word.term}</span>
          </div>
        </div>
      )}
      {ex.kind === 'term' && (
        <div className="speech">
          <Loba mood="think" />
          <p className="bubble"><span className="prompt-text">{meaning(ex.word)}</span></p>
        </div>
      )}
      {ex.kind === 'listen' && (
        <div className="listen-row">
          <SpeakButton word={ex.word} big />
          <button className="speak-btn slow" onClick={() => playWord(ex.word, true)} aria-label={t('playSlow')} style={{ fontFamily: 'var(--display)', fontWeight: 600, fontSize: 20 }}>0.7×</button>
        </div>
      )}

      <div className={`choices ${ex.kind === 'meaning' ? '' : 'grid'}`} role="group">
        {ex.options.map((w, i) => {
          const state = locked ? (w === ex.word ? 'is-correct' : i === selected ? 'is-wrong' : '') : ''
          return (
            <button
              key={w.term}
              className={`choice ${state}`}
              style={{ '--i': i } as CSSProperties}
              aria-pressed={selected === i}
              disabled={locked}
              onClick={() => {
                sfx('tap')
                if (ex.kind === 'term') playWord(w)
                onAnswer({ kind: 'choice', index: i })
              }}
            >
              <span className="key" aria-hidden="true">{i + 1}</span>
              {label(w)}
            </button>
          )
        })}
      </div>
    </>
  )
}
