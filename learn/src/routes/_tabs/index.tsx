import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useSelector } from '@tanstack/react-store'
import { useEffect, useRef, useState, type CSSProperties, type RefObject } from 'react'
import { buzz, sfx } from '../../audio/sfx'
import { playWord } from '../../audio/voice'
import { burstFrom, flyGems } from '../../components/fx'
import { Lottie } from '../../components/Lottie'
import { Art, Scenery } from '../../components/Art'
import { Icon, Star, type IconName } from '../../components/Icon'
import { Loba } from '../../components/Loba'
import { Rail } from '../../components/Rail'
import { LESSONS_PER_UNIT, type Unit, type Word } from '../../content/types'
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
/** Lessons done per unit the last time the path was on screen, so new progress can be played out. */
const lastSeen = new Map<string, number>()

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

/**
 * The path is the Congo river. It runs through the node centres (x is relative to the column's
 * centre line), from node `from` to node `to`, optionally flowing in from `top` and out to `bottom`.
 */
function river(flip: number, from: number, to: number, top?: number, bottom?: number) {
  const at = (n: number) => [OFFSETS[n]! * flip, n * STEP + NODE_H / 2] as const
  const [x0, y0] = at(from)
  let d = top === undefined ? `M${x0} ${y0}` : `M${x0} ${top}L${x0} ${y0}`
  for (let n = from + 1; n <= to; n++) {
    const [px, py] = at(n - 1), [x, y] = at(n)
    d += ` C${px} ${py + STEP / 2} ${x} ${y - STEP / 2} ${x} ${y}`
  }
  if (bottom !== undefined) d += `L${at(to)[0]} ${bottom}`
  return d
}

function UnitSection({ unit, index, isCurrent, unlocked, currentRef }: { unit: Unit; index: number; isCurrent: boolean; unlocked: boolean; currentRef: RefObject<HTMLDivElement | null> }) {
  const { t, unitTitle, meaning } = useT()
  const done = useSelector(game, (s) => (unlocked ? unitProgress(s, unit) : 0))
  const chestOpen = useSelector(game, (s) => !!s.chests[unit.id])
  const [showTip, setShowTip] = useState(false)
  const [open, setOpen] = useState<number | null>(null)
  const [c, shade] = UNIT_COLORS[index % UNIT_COLORS.length]!
  const flip = index % 2 ? -1 : 1
  const style = { '--unit': c, '--unit-shade': shade } as CSSProperties
  const lobaAt = Math.min(done, LESSONS_PER_UNIT)
  const height = LESSONS_PER_UNIT * STEP + 110
  // Each stretch of river ends under a unit banner, which crosses it like a bridge.
  const top = -62, bottom = height + 62
  const [before] = useState(() => lastSeen.get(unit.id) ?? done)
  const advanced = done > before
  const freshNode = useRef<HTMLDivElement>(null)
  const [said, setSaid] = useState<{ word: Word; n: number } | null>(null)
  const [poke, setPoke] = useState(0)
  // Beside the path: palm, drum, hut, then a forest elephant.
  const scene = index % 4
  const [walking, setWalking] = useState(false)
  useEffect(() => {
    if (!poke || scene !== 3) return
    setWalking(true)
    const id = setTimeout(() => setWalking(false), 2400)
    return () => clearTimeout(id)
  }, [poke, scene])

  useEffect(() => {
    lastSeen.set(unit.id, done)
    if (!advanced) return
    const id = setTimeout(() => {
      sfx('pop')
      burstFrom(freshNode.current, { count: 14, power: 6, colors: [c, '#ffc22e', '#ffffff', shade] })
    }, 650)
    return () => clearTimeout(id)
  }, [unit.id, done, advanced, c, shade])

  useEffect(() => {
    if (!said) return
    const id = setTimeout(() => setSaid(null), 2600)
    return () => clearTimeout(id)
  }, [said])

  // Tap Loba and he says a word from this unit.
  const speak = () => {
    const word = unit.words[(said ? said.n + 1 : Math.floor(Math.random() * unit.words.length)) % unit.words.length]!
    setSaid({ word, n: unit.words.indexOf(word) })
    sfx('tile', said ? said.n + 1 : 2)
    playWord(word)
  }

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

      <div className="path" style={{ height }}>
        <svg className="river" style={{ left: '50%', width: 1 }} aria-hidden="true">
          <path className="river-bank" d={river(flip, 0, LESSONS_PER_UNIT, top, bottom)} />
          <path className="river-water" d={river(flip, 0, LESSONS_PER_UNIT, top, bottom)} />
          {unlocked && <path className="river-walked" d={river(flip, 0, advanced ? before : lobaAt, top, done >= LESSONS_PER_UNIT && !advanced ? bottom : undefined)} />}
          {advanced && <path className="river-walked river-new" pathLength={1} d={river(flip, before, lobaAt, undefined, done >= LESSONS_PER_UNIT ? bottom : undefined)} />}
          {unlocked && (
            <g className="river-waves">
              <path d={river(flip, 0, LESSONS_PER_UNIT, top, bottom)} transform="translate(-13 0)" />
              <path d={river(flip, 0, LESSONS_PER_UNIT, top, bottom)} transform="translate(13 0)" />
            </g>
          )}
        </svg>
        {unlocked && (
          <span className="fish" style={{ left: `calc(50% + ${((OFFSETS[index % 3 + 1]! + OFFSETS[index % 3 + 2]!) / 2) * flip}px)`, top: (index % 3 + 1) * STEP + NODE_H / 2 + STEP / 2, '--flip': flip } as CSSProperties}>
            <Lottie name="fish" autoplay={false} rest={0} every={6000 + (index % 4) * 1300} />
          </span>
        )}
        <button
          className={`scenery-btn scenery-${scene}`}
          style={{ left: `calc(50% + ${flip * 206}px)`, top: STEP * 1.1 }}
          tabIndex={-1}
          aria-hidden="true"
          onClick={() => {
            sfx(scene === 1 ? 'drum' : scene === 0 ? 'shaker' : scene === 3 ? 'pop' : 'tap', poke)
            setPoke((n) => n + 1)
          }}
        >
          {scene === 3 ? <Lottie name="elephant" className="scenery" loop autoplay={walking} rest={0.3} /> : <Scenery kind={scene} key={poke} className={poke ? 'poked' : ''} />}
        </button>
        {isCurrent && (
          <div
            className={`path-loba ${advanced ? 'travel' : ''}`}
            style={{ left: `calc(50% + ${OFFSETS[lobaAt]! * flip - flip * 128}px)`, top: lobaAt * STEP - 34, '--fx': `${(OFFSETS[before]! - OFFSETS[lobaAt]!) * flip}px`, '--fy': `${(before - lobaAt) * STEP}px` } as CSSProperties}
          >
            {said && (
              <p className="loba-says" key={said.n} aria-live="polite">
                <b lang={unit.language}>{said.word.term}</b>
                <span>{meaning(said.word)}</span>
              </p>
            )}
            <button className="loba-tap" onClick={speak} aria-label="Loba">
              <Loba mood={said ? 'cheer' : 'wave'} />
            </button>
          </div>
        )}
        {Array.from({ length: LESSONS_PER_UNIT + 1 }, (_, n) => {
          const isChest = n === LESSONS_PER_UNIT
          const state = !unlocked || n > done ? 'locked' : n < done ? 'done' : 'current'
          const x = OFFSETS[n]! * flip
          return (
            <div key={n} className={`node-wrap ${advanced && n === done - 1 ? 'fresh' : ''}`} style={{ top: n * STEP, marginLeft: x, '--n': n } as CSSProperties} ref={isCurrent && state === 'current' ? currentRef : advanced && n === done - 1 ? freshNode : undefined}>
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
        onClick={(e) => {
          actions.openChest(unit)
          sfx('chest')
          buzz([20, 40, 60])
          burstFrom(e.currentTarget, { count: 26, power: 9 })
          flyGems(e.currentTarget, 8)
          setBurst(true)
          setTimeout(() => setBurst(false), 2200)
        }}
      >
        <Art name={opened ? 'chestOpen' : available ? 'chest' : 'chestLocked'} />
        {available && !opened && <Lottie name="sparkle" className="chest-sparkle" loop />}
      </button>
    </>
  )
}
