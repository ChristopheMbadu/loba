import { useEffect, useRef, useState } from 'react'
import { sfx } from '../audio/sfx'
import { Art } from '../components/Art'
import { game } from '../game/store'
import { useT } from '../i18n/useT'
import { CHAT, type Taste } from './copy'
import { useLanding } from './useLanding'

/** Reveal once, when the element first scrolls into view. */
function useSeen<T extends Element>() {
  const ref = useRef<T>(null)
  const [seen, setSeen] = useState(false)
  useEffect(() => {
    const el = ref.current
    if (!el) return
    const io = new IntersectionObserver(([e]) => { if (e?.isIntersecting) { setSeen(true); io.disconnect() } }, { threshold: 0.45 })
    io.observe(el)
    return () => io.disconnect()
  }, [])
  return [ref, seen] as const
}

export function ChatPhone({ taste }: { taste: Taste }) {
  return (
    <div className="phone-wrap">
      <Phone taste={taste} />
      <PhoneHint />
    </div>
  )
}

function PhoneHint() {
  const t = useLanding()
  return <p className="phone-hint">{t('chatHint')}</p>
}

function Phone({ taste: wanted }: { taste: Taste }) {
  const { ui } = useT()
  const t = useLanding()
  const [ref, seen] = useSeen<HTMLDivElement>()
  const [open, setOpen] = useState<number | null>(null)
  // Conversations are written for Lingala and Swahili; other languages show the Lingala one.
  const taste = CHAT[wanted] ? wanted : 'ln'
  const msgs = CHAT[taste]!
  return (
    <div className={`phone ${seen ? 'seen' : ''}`} ref={ref}>
      <div className="phone-head">
        <span className="phone-avatar" aria-hidden="true">
          <svg viewBox="0 0 48 48"><circle cx="24" cy="24" r="24" fill="#ffd99a" /><path d="M10 20c0-10 6-15 14-15s14 5 14 15c-3-4-8-6-14-6s-11 2-14 6z" fill="#ef4e4e" /><path d="M12 18c4-5 20-5 24 0" stroke="#ffc22e" strokeWidth="3" fill="none" /><circle cx="24" cy="27" r="11" fill="#8f4f1c" /><circle cx="20" cy="26" r="1.6" fill="#2c2540" /><circle cx="28" cy="26" r="1.6" fill="#2c2540" /><path d="M20 31q4 3 8 0" stroke="#2c2540" strokeWidth="1.8" fill="none" strokeLinecap="round" /><path d="M8 48c2-8 8-11 16-11s14 3 16 11z" fill="#2bb673" /></svg>
        </span>
        <span><b>{t('mamaName')}</b><small>{t('online')}</small></span>
      </div>
      <ol className="phone-chat">
        {msgs.map((m, i) => (
          <li key={`${taste}-${i}`} className={m.from} style={{ '--d': `${i * 0.7 + 0.2}s` } as React.CSSProperties}>
            <button onClick={() => setOpen(open === i ? null : i)} aria-expanded={open === i}>
              <span lang={taste}>{m.text}</span>
              {open === i && <small>{ui === 'en' ? m.en : m.fr}</small>}
            </button>
          </li>
        ))}
      </ol>
      <div className="phone-input" aria-hidden="true">
        <span>{taste === 'ln' ? 'Matondo mama…' : 'Asante mama…'}</span>
        <i><svg viewBox="0 0 24 24"><path d="M4 12l15-7-5 15-2.5-6z" fill="#fff" /></svg></i>
      </div>
    </div>
  )
}

export function MicDemo({ taste, word }: { taste: Taste; word: string }) {
  const t = useLanding()
  const [state, setState] = useState<'idle' | 'listening' | 'done'>('idle')
  const tap = () => {
    if (state === 'listening') return
    sfx('tap')
    setState('listening')
    window.setTimeout(() => { setState('done'); sfx('correct') }, 1500)
  }
  return (
    <div className={`mic-demo ${state}`}>
      <div className="mic-word">
        <span className="mic-chip" aria-hidden="true"><Art name="headphones" size={26} /></span>
        <b lang={taste}>{word}</b>
        <span className="mic-chip slow" aria-hidden="true">0.7×</span>
      </div>
      <div className="mic-bars" aria-hidden="true">{Array.from({ length: 9 }, (_, i) => <i key={i} style={{ '--i': i } as React.CSSProperties} />)}</div>
      <button className="mic-btn" onClick={tap} aria-label={t('speakTap')}>
        <svg viewBox="0 0 48 48" aria-hidden="true"><rect x="16" y="4" width="16" height="26" rx="8" fill="#fff" /><path d="M10 22a14 14 0 0 0 28 0M24 36v8M16 44h16" stroke="#fff" strokeWidth="4" fill="none" strokeLinecap="round" /></svg>
      </button>
      <p className="mic-status" aria-live="polite">{state === 'done' ? <b>✓ {t('speakGood')}</b> : <span>{t('speakTap')}</span>}</p>
    </div>
  )
}

export function StreakDemo() {
  const t = useLanding()
  const [days, setDays] = useState(4)
  const [bump, setBump] = useState(0)
  const week = t('week')
  const tap = () => {
    sfx('coin')
    setDays((d) => (d >= 7 ? 1 : d + 1))
    setBump((b) => b + 1)
  }
  return (
    <div className="streak-demo">
      <button className="streak-flame" onClick={tap} aria-label={`${t('streakTap')} — ${days} ${t('days')}`}>
        <span key={bump} className="streak-pop"><Art name="flame" /></span>
        <span className="streak-num"><b>{days}</b> {t('days')}</span>
      </button>
      <ol className="streak-week" aria-hidden="true">
        {week.map((d, i) => (
          <li key={i} className={i < days ? 'on' : ''}><span>{i < days ? '✓' : ''}</span>{d}</li>
        ))}
      </ol>
      <p className="streak-hint">{t('streakTap')}</p>
    </div>
  )
}

let drumCtx: AudioContext | null = null
/** A soft synthesized drum hit — a falling sine "boom" plus a short noise slap. */
function drumHit(accent: boolean) {
  if (!game.get().sound) return
  try {
    drumCtx ??= new AudioContext()
    const c = drumCtx, t0 = c.currentTime
    const osc = c.createOscillator(), gain = c.createGain()
    osc.frequency.setValueAtTime(accent ? 150 : 220, t0)
    osc.frequency.exponentialRampToValueAtTime(accent ? 55 : 90, t0 + 0.25)
    gain.gain.setValueAtTime(0.35, t0)
    gain.gain.exponentialRampToValueAtTime(0.0001, t0 + 0.35)
    osc.connect(gain).connect(c.destination)
    osc.start(t0); osc.stop(t0 + 0.4)
  } catch { /* best-effort */ }
}

export function RumbaDrum() {
  const t = useLanding()
  const [hits, setHits] = useState(0)
  const tap = () => {
    drumHit(hits % 4 === 0)
    setHits((h) => h + 1)
  }
  return (
    <div className="drum-wrap">
      <button className="drum-btn" onClick={tap} aria-label={t('rumbaTap')}>
        <svg className="ngoma" key={hits} viewBox="0 0 240 280" aria-hidden="true">
          <ellipse cx="120" cy="262" rx="70" ry="10" fill="#000" opacity=".08" />
          <g className="ngoma-body">
            {/* carved body */}
            <path d="M46 66c0 70 26 120 30 186h88c4-66 30-116 30-186z" fill="#d98b3d" />
            <path d="M46 66c0 70 26 120 30 186h22c-6-66-22-120-22-186z" fill="#e8a45c" opacity=".7" />
            <path d="M164 252c4-66 30-116 30-186h-20c0 70-20 120-24 186z" fill="#b96a2b" />
            {/* painted band: red with sun-gold zigzag */}
            <path d="M58 150h124c-3 10-6 20-8 32H66c-2-12-5-22-8-32z" fill="#ef4e4e" />
            <path d="M64 166l10-9 10 9 10-9 10 9 10-9 10 9 10-9 10 9 10-9 10 9 10-9 10 9" stroke="#ffc22e" strokeWidth="4" fill="none" strokeLinejoin="round" />
            {/* cocoa foot with triangles */}
            <path d="M74 226h92l-2 26H76z" fill="#5a3a22" />
            <path d="M80 252l8-14 8 14 8-14 8 14 8-14 8 14 8-14 8 14 8-14 8 14" fill="#c58a3e" />
            {/* rope tension */}
            <path d="M52 76l14 56 14-58 14 60 14-60 12 60 14-60 14 58 14-56" stroke="#fff3d6" strokeWidth="4" fill="none" strokeLinejoin="round" />
            <path d="M54 134h132" stroke="#8f4f1c" strokeWidth="6" strokeLinecap="round" />
          </g>
          {/* skin */}
          <ellipse cx="120" cy="66" rx="76" ry="20" fill="#8f4f1c" />
          <ellipse cx="120" cy="62" rx="70" ry="16" fill="#ffe9c2" />
          <ellipse cx="104" cy="58" rx="26" ry="6" fill="#fff" opacity=".5" />
          {hits > 0 && <ellipse className="ngoma-ripple" cx="120" cy="62" rx="30" ry="7" fill="none" stroke="#c58a3e" strokeWidth="3" />}
        </svg>
        {hits > 0 && <span key={`n${hits}`} className="drum-note" style={{ '--x': `${(hits * 37) % 80 - 40}px` } as React.CSSProperties} aria-hidden="true">{hits % 2 ? '♪' : '♫'}</span>}
      </button>
      <p className="drum-hint">{t('rumbaTap')}</p>
    </div>
  )
}
