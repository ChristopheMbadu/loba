import { useState } from 'react'
import { sfx } from '../audio/sfx'
import { Loba, type Mood } from '../components/Loba'
import { useT } from '../i18n/useT'
import type { Greeting } from './copy'

/** Loba in a pirogue on the Congo river at golden hour. Tap him to hear a greeting. */
export function HeroScene({ hint, greetings }: { hint: string; greetings: Greeting[] }) {
  const { ui } = useT()
  const [i, setI] = useState(0)
  const [mood, setMood] = useState<Mood>('wave')
  const g = greetings[i % greetings.length]!

  const tap = () => {
    sfx('tap')
    setI((n) => n + 1)
    setMood('cheer')
    window.setTimeout(() => setMood('wave'), 650)
  }

  return (
    <div className="scene">
      <svg className="scene-back" viewBox="0 0 400 400" aria-hidden="true">
        <defs>
          <clipPath id="scene-disk"><circle cx="200" cy="200" r="190" /></clipPath>
        </defs>
        <g clipPath="url(#scene-disk)">
          <rect width="400" height="400" fill="#e3f3fd" />
          <circle cx="292" cy="128" r="56" fill="#fff1c2" />
          <circle cx="292" cy="128" r="38" fill="#ffc22e" />
          {/* palm on the far bank */}
          <g transform="translate(14 140) scale(1.15)">
            <path d="M56 110c2-26 0-44 6-62l8 2c-6 18-2 36-4 60z" fill="#b96a2b" /><path d="M58 96h9M59 82h8M61 68h8" stroke="#8f4f1c" strokeWidth="3" strokeLinecap="round" />
            <path d="M66 50C50 30 30 30 16 40c16-2 30 2 44 14zM66 50C60 26 44 14 26 14c16 8 26 20 34 38zM66 50c4-24 22-34 40-30-16 4-26 14-36 32zM66 50c16-12 34-10 46 2-16-4-30-2-44 4zM66 50c8 4 20 14 22 30-8-12-16-18-26-24z" fill="#2bb673" />
            <path d="M66 50C50 30 30 30 16 40c16-2 30 2 44 14zM66 50c16-12 34-10 46 2-16-4-30-2-44 4z" fill="#1e8f58" />
            <circle cx="62" cy="54" r="5" fill="#8f4f1c" /><circle cx="70" cy="56" r="5" fill="#b96a2b" />
          </g>
          {/* far bank: rainforest canopy */}
          <path d="M0 262c18-14 30-20 46-12 10-16 28-20 42-8 12-14 32-14 44 0 14-12 34-10 44 4 12-10 30-8 40 4 14-14 36-12 46 2 12-10 30-10 40 2 12-8 26-6 36 6 14-8 30-6 42 8 V300H0z" fill="#2bb673" />
          <path d="M0 276c30-8 60-6 90 0s70 6 110 0 80-6 110 0 60 6 90 0V300H0z" fill="#1e8f58" />
          {/* the river */}
          <rect y="282" width="400" height="118" fill="#1c9bef" />
          <g className="scene-waves">
            <path d="M-400 304q25-8 50 0t50 0 50 0 50 0 50 0 50 0 50 0 50 0 50 0 50 0 50 0 50 0 50 0 50 0 50 0 50 0" stroke="#6cc4fa" strokeWidth="4" fill="none" strokeLinecap="round" />
            <path d="M-380 340q25-8 50 0t50 0 50 0 50 0 50 0 50 0 50 0 50 0 50 0 50 0 50 0 50 0 50 0 50 0 50 0 50 0" stroke="#6cc4fa" strokeWidth="4" fill="none" strokeLinecap="round" opacity=".7" />
            <path d="M-410 372q25-8 50 0t50 0 50 0 50 0 50 0 50 0 50 0 50 0 50 0 50 0 50 0 50 0 50 0 50 0 50 0 50 0" stroke="#6cc4fa" strokeWidth="4" fill="none" strokeLinecap="round" opacity=".5" />
          </g>
          {/* sun glitter on the water */}
          <path d="M270 296h44M282 312h22M276 326h30" stroke="#ffe08a" strokeWidth="4" strokeLinecap="round" />
        </g>
      </svg>

      <button className="scene-loba" onClick={tap} aria-label={`${g.text} (${ui === 'en' ? g.en : g.fr})`}>
        <Loba mood={mood} />
      </button>

      <svg className="scene-front" viewBox="0 0 400 400" aria-hidden="true">
        {/* pirogue */}
        <path d="M44 266q156 34 312 0l-14 30q-142 40-284 0z" fill="#8f4f1c" />
        <path d="M44 266q156 34 312 0l-3 8q-153 32-306 0z" fill="#b96a2b" />
        <path d="M66 286q134 30 268 0" stroke="#ffc22e" strokeWidth="4" fill="none" strokeLinecap="round" />
        <path d="M120 296l8-6 8 6 8-6 8 6M248 296l8-6 8 6 8-6 8 6" stroke="#ef4e4e" strokeWidth="3" fill="none" strokeLinejoin="round" />
        {/* paddle resting across the hull */}
        <path d="M86 262l92 22" stroke="#b96a2b" strokeWidth="6" strokeLinecap="round" />
        <ellipse cx="74" cy="259" rx="16" ry="7" transform="rotate(13 74 259)" fill="#8f4f1c" />
      </svg>

      <p className="scene-bubble" key={i} aria-live="polite">
        <b lang={g.lang}>{g.text}</b>
        <span>{ui === 'en' ? g.en : g.fr}</span>
      </p>
      <span className="scene-hint" aria-hidden="true">{hint}</span>
    </div>
  )
}
