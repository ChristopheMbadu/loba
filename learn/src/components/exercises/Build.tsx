import { useLayoutEffect, useRef } from 'react'
import { sfx } from '../../audio/sfx'
import { calm } from '../fx'
import type { Exercise } from '../../game/exercises'
import { useT } from '../../i18n/useT'
import { Loba } from '../Loba'
import type { Answer } from './answer'

/** Word bank: tap tiles to build the translation; tap a placed tile to return it. */
export function Build({ ex, answer, onAnswer, locked }: { ex: Extract<Exercise, { kind: 'build' }>; answer: Answer | null; onAnswer: (a: Answer) => void; locked: boolean }) {
  const { t, meaning } = useT()
  const picked = answer?.kind === 'tiles' ? answer.picked : []
  const before = useRef(new Map<string, DOMRect>())
  const tiles = () => document.querySelectorAll<HTMLElement>('.lesson-body [data-tile]')
  const set = (next: number[]) => {
    // Remember where every tile is, so after the move each one can glide from its old place.
    before.current = new Map([...tiles()].map((el) => [el.dataset.tile!, el.getBoundingClientRect()]))
    onAnswer({ kind: 'tiles', picked: next })
  }
  useLayoutEffect(() => {
    if (calm()) return
    for (const el of tiles()) {
      const was = before.current.get(el.dataset.tile!)
      if (!was) continue
      const now = el.getBoundingClientRect()
      const dx = was.left - now.left, dy = was.top - now.top
      if (Math.abs(dx) + Math.abs(dy) > 2) el.animate([{ transform: `translate(${dx}px, ${dy}px)` }, { transform: 'none' }], { duration: 280, easing: 'cubic-bezier(0.2, 0.9, 0.3, 1.15)' })
    }
    before.current.clear()
  }, [picked.join()])

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
          <button key={i} data-tile={i} className="tile" disabled={locked} onClick={() => { sfx('tap'); set(picked.filter((_, p) => p !== pos)) }}>
            {ex.tiles[i]}
          </button>
        ))}
      </div>
      <div className="bank">
        {ex.tiles.map((tile, i) =>
          picked.includes(i) ? (
            <span key={i} className="tile ghost" aria-hidden="true">{tile}</span>
          ) : (
            <button key={i} data-tile={i} className="tile" disabled={locked} onClick={() => { sfx('tile', picked.length); set([...picked, i]) }}>
              {tile}
            </button>
          ),
        )}
      </div>
    </>
  )
}
