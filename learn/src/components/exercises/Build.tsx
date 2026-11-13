import { sfx } from '../../audio/sfx'
import type { Exercise } from '../../game/exercises'
import { useT } from '../../i18n/useT'
import { Loba } from '../Loba'
import type { Answer } from './answer'

/** Word bank: tap tiles to build the translation; tap a placed tile to return it. */
export function Build({ ex, answer, onAnswer, locked }: { ex: Extract<Exercise, { kind: 'build' }>; answer: Answer | null; onAnswer: (a: Answer) => void; locked: boolean }) {
  const { t, meaning } = useT()
  const picked = answer?.kind === 'tiles' ? answer.picked : []
  const set = (next: number[]) => onAnswer({ kind: 'tiles', picked: next })

  return (
    <>
      <div className="ex-head">
        {ex.retry && <span className="ex-tag retry">↻ {t('previousMistake')}</span>}
        <h1>{t('buildSentence')}</h1>
      </div>
      <div className="speech">
        <Loba mood="think" />
        <p className="bubble">{meaning(ex.sentence)}</p>
      </div>
      <div className="answer-line" aria-live="polite">
        {picked.map((i, pos) => (
          <button key={i} className="tile" disabled={locked} onClick={() => { sfx('tap'); set(picked.filter((_, p) => p !== pos)) }}>
            {ex.tiles[i]}
          </button>
        ))}
      </div>
      <div className="bank">
        {ex.tiles.map((tile, i) =>
          picked.includes(i) ? (
            <span key={i} className="tile ghost" aria-hidden="true">{tile}</span>
          ) : (
            <button key={i} className="tile" disabled={locked} onClick={() => { sfx('tap'); set([...picked, i]) }}>
              {tile}
            </button>
          ),
        )}
      </div>
    </>
  )
}
