import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useSelector } from '@tanstack/react-store'
import { useEffect, useRef, useState, type CSSProperties, type RefObject } from 'react'
import { sfx } from '../../audio/sfx'
import { Art, Scenery } from '../../components/Art'
import { Icon, Star, type IconName } from '../../components/Icon'
import { Loba } from '../../components/Loba'
import { Rail } from '../../components/Rail'
import { LESSONS_PER_UNIT, type Unit } from '../../content/types'
import { currentUnitIndex, unitProgress, unitUnlocked } from '../../game/rules'
import { REWARDS } from '../../game/state'
import { actions, game } from '../../game/store'
import { useUnits } from '../../hooks'
import { useT } from '../../i18n/useT'

export const Route = createFileRoute('/_tabs/')({ component: PathPage })

const UNIT_COLORS: [string, string][] = [
  ['#1c9bef', '#1479bf'],
  ['#2bb673', '#1e8f58'],
  ['#ff8a1f', '#d36a0a'],
  ['#8b5cf6', '#6d3fd8'],
  ['#e8608a', '#c0436a'],
  ['#00a6a6', '#007a7a'],
]
// Geometry of the winding path: x offset per node (mirrored on alternate units) and vertical step.
const OFFSETS = [0, 56, 84, 56, 0]
const STEP = 98
const NODE_H = 70
const LESSON_ICONS: IconName[] = ['book', 'speaker', 'practice']

function PathPage() {
  const units = useUnits()
  const { t } = useT()
  const state = useSelector(game, (s) => s)
  const current = currentUnitIndex(state, units)
  const currentRef = useRef<HTMLDivElement>(null)

  // After the router's own scroll restoration, bring the next lesson into view.
  useEffect(() => {
    const id = requestAnimationFrame(() => currentRef.current?.scrollIntoView({ block: 'center' }))
    return () => cancelAnimationFrame(id)
  }, [])

  return (
    <div className="page">
      <div>
        {units.map((unit, i) => (
          <UnitSection key={unit.id} unit={unit} index={i} isCurrent={i === current} unlocked={unitUnlocked(state, units, i)} currentRef={currentRef} />
        ))}
        <div className="path-end">
          <Art name="trophy" size={56} />
          {t('appTagline')}
        </div>
      </div>
      <Rail />
    </div>
  )
}

/** Smooth dotted trail through the node centres, relative to the column's centre line. */
function trailPath(flip: number, upTo: number) {
  let d = ''
  for (let n = 0; n <= upTo; n++) {
    const x = OFFSETS[n]! * flip, y = n * STEP + NODE_H / 2
    if (n === 0) d = `M${x} ${y}`
    else {
      const px = OFFSETS[n - 1]! * flip, py = (n - 1) * STEP + NODE_H / 2
      d += ` C${px} ${py + STEP / 2} ${x} ${y - STEP / 2} ${x} ${y}`
    }
  }
  return d
}

function UnitSection({ unit, index, isCurrent, unlocked, currentRef }: { unit: Unit; index: number; isCurrent: boolean; unlocked: boolean; currentRef: RefObject<HTMLDivElement | null> }) {
  const { t, unitTitle } = useT()
  const done = useSelector(game, (s) => (unlocked ? unitProgress(s, unit) : 0))
  const chestOpen = useSelector(game, (s) => !!s.chests[unit.id])
  const [showTip, setShowTip] = useState(false)
  const [open, setOpen] = useState<number | null>(null)
  const [c, shade] = UNIT_COLORS[index % UNIT_COLORS.length]!
  const flip = index % 2 ? -1 : 1
  const style = { '--unit': c, '--unit-shade': shade } as CSSProperties
  const lobaAt = Math.min(done, LESSONS_PER_UNIT)

  // Close the popover when clicking elsewhere.
  useEffect(() => {
    if (open === null) return
    const close = (e: MouseEvent) => !(e.target as Element).closest?.('.node-wrap') && setOpen(null)
    document.addEventListener('click', close)
    return () => document.removeEventListener('click', close)
  }, [open])

  return (
    <section className={`unit ${unlocked ? '' : 'locked'}`} style={style} aria-labelledby={`unit-${unit.id}`}>
      <header className="unit-banner">
        <div>
          <div className="unit-eyebrow">
            {t('unit', { n: index + 1 })} · {t('words', { n: unit.words.length })}
          </div>
          <h2 id={`unit-${unit.id}`}>{unitTitle(unit)}</h2>
        </div>
        {unit.tip && (
          <button className="guide-btn" aria-expanded={showTip} onClick={() => setShowTip((v) => !v)}>
            <Icon name="book" /> {t('guide')}
          </button>
        )}
      </header>
      {showTip && (
        <p className="unit-tip">
          <Loba mood="happy" />
          {unit.tip}
        </p>
      )}

      <div className="path" style={{ height: LESSONS_PER_UNIT * STEP + 110 }}>
        <svg className="trail" style={{ left: '50%', width: 1 }} aria-hidden="true">
          <path d={trailPath(flip, LESSONS_PER_UNIT)} />
          {done > 0 && <path className="walked" d={trailPath(flip, Math.min(done, LESSONS_PER_UNIT))} />}
        </svg>
        <div style={{ position: 'absolute', left: `calc(50% + ${flip * 206}px)`, top: STEP * 1.1, translate: '-50% 0' }}>
          <Scenery kind={index} />
        </div>
        {isCurrent && (
          <div className="path-loba" style={{ left: `calc(50% + ${OFFSETS[lobaAt]! * flip - flip * 128}px)`, top: lobaAt * STEP - 34, translate: '-50% 0' }}>
            <Loba mood="wave" />
          </div>
        )}
        {Array.from({ length: LESSONS_PER_UNIT + 1 }, (_, n) => {
          const isChest = n === LESSONS_PER_UNIT
          const state = !unlocked || n > done ? 'locked' : n < done ? 'done' : 'current'
          const x = OFFSETS[n]! * flip
          return (
            <div key={n} className="node-wrap" style={{ top: n * STEP, marginLeft: x }} ref={isCurrent && state === 'current' ? currentRef : undefined}>
              {isChest ? (
                <ChestNode unit={unit} available={unlocked && done >= LESSONS_PER_UNIT} opened={chestOpen} />
              ) : (
                <LessonNode unit={unit} n={n} x={x} state={state} open={open === n} onToggle={() => setOpen(open === n ? null : n)} />
              )}
            </div>
          )
        })}
      </div>
    </section>
  )
}

function LessonNode({ unit, n, x, state, open, onToggle }: { unit: Unit; n: number; x: number; state: 'locked' | 'done' | 'current'; open: boolean; onToggle: () => void }) {
  const { t, unitTitle } = useT()
  const navigate = useNavigate()
  const label = `${unitTitle(unit)} · ${t('lessonOf', { n: n + 1, total: LESSONS_PER_UNIT })}${state === 'locked' ? ` · ${t('locked')}` : ''}`

  return (
    <>
      {state === 'current' && !open && <span className="node-start">{t('start')}</span>}
      <button
        className={`node ${state}`}
        aria-label={label}
        aria-expanded={open}
        onClick={() => {
          sfx('tap')
          onToggle()
        }}
      >
        {state === 'locked' ? <Icon name="lock" /> : state === 'done' ? <Icon name="check" /> : n === 0 ? <Star /> : <Icon name={LESSON_ICONS[n - 1]!} />}
      </button>
      {open && (
        <div className={`popover ${state === 'locked' ? 'locked' : ''}`} role="dialog" aria-label={label} style={{ translate: `${-x * 0.7}px 0`, '--ax': `${x * 0.7}px` } as CSSProperties}>
          <h3>{unitTitle(unit)}</h3>
          <p>{state === 'locked' ? t('lockedHint') : t('lessonOf', { n: n + 1, total: LESSONS_PER_UNIT })}</p>
          {state !== 'locked' && (
            <button className="btn white block" autoFocus onClick={() => navigate({ to: '/lesson/$unitId', params: { unitId: unit.id }, search: { n } })}>
              {state === 'done' ? t('review') : t('startXp', { xp: 10 })}
            </button>
          )}
        </div>
      )}
    </>
  )
}

function ChestNode({ unit, available, opened }: { unit: Unit; available: boolean; opened: boolean }) {
  const { t } = useT()
  const [burst, setBurst] = useState(false)
  return (
    <>
      {burst && <span className="node-start" style={{ color: 'var(--sky)' }}>{t('chestOpened', { n: REWARDS.chest })}</span>}
      <button
        className={`node chest ${available && !opened ? 'ready' : ''}`}
        aria-label={available && !opened ? t('openChest') : t('chest')}
        disabled={!available || opened}
        onClick={() => {
          actions.openChest(unit)
          sfx('coin')
          setBurst(true)
          setTimeout(() => setBurst(false), 2200)
        }}
      >
        <Art name={opened ? 'chestOpen' : available ? 'chest' : 'chestLocked'} />
      </button>
    </>
  )
}
