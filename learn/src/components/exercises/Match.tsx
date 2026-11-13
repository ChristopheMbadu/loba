import { useMemo, useState } from 'react'
import { playWord } from '../../audio/voice'
import { sfx } from '../../audio/sfx'
import type { Exercise } from '../../game/exercises'
import { shuffle } from '../../game/random'
import { useT } from '../../i18n/useT'
import type { Answer } from './answer'

type Side = 'term' | 'meaning'

/** Tap a word, then its meaning. Pairs fade out; a mismatch shakes. */
export function Match({ ex, onAnswer }: { ex: Extract<Exercise, { kind: 'match' }>; onAnswer: (a: Answer) => void }) {
  const { t, meaning } = useT()
  const right = useMemo(() => shuffle(ex.pairs), [ex])
  const left = useMemo(() => shuffle(ex.pairs), [ex])
  const [pick, setPick] = useState<{ side: Side; term: string } | null>(null)
  const [matched, setMatched] = useState<Set<string>>(new Set())
  const [miss, setMiss] = useState<string | null>(null)

  const tap = (side: Side, term: string) => {
    if (matched.has(term)) return
    if (side === 'term') playWord(ex.pairs.find((w) => w.term === term)!)
    if (!pick || pick.side === side) {
      sfx('tap')
      setPick({ side, term })
      return
    }
    if (pick.term === term) {
      const next = new Set(matched).add(term)
      setMatched(next)
      setPick(null)
      sfx('correct')
      if (next.size === ex.pairs.length) onAnswer({ kind: 'match', done: true })
    } else {
      sfx('wrong')
      setMiss(`${side}:${term}`)
      setPick(null)
      setTimeout(() => setMiss(null), 350)
    }
  }

  const cell = (side: Side, term: string, text: string) => {
    const key = `${side}:${term}`
    return (
      <button
        key={key}
        className={`choice ${matched.has(term) ? 'matched' : ''} ${miss === key ? 'shake' : ''}`}
        aria-pressed={pick?.side === side && pick.term === term}
        disabled={matched.has(term)}
        onClick={() => tap(side, term)}
      >
        {text}
      </button>
    )
  }

  return (
    <>
      <div className="ex-head">
        <h1>{t('matchPairs')}</h1>
      </div>
      <div className="match">
        <div className="col">{left.map((w) => cell('term', w.term, w.term))}</div>
        <div className="col">{right.map((w) => cell('meaning', w.term, meaning(w)))}</div>
      </div>
    </>
  )
}
