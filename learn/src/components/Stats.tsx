import { useQuery } from '@tanstack/react-query'
import { Link } from '@tanstack/react-router'
import { catalogQuery } from '../content/curriculum'
import type { LangCode } from '../content/types'
import { useSelector } from '@tanstack/react-store'
import { useEffect, useRef, useState } from 'react'
import { dayKey, MAX_HEARTS } from '../game/state'
import { visibleStreak } from '../game/rules'
import { game } from '../game/store'
import { useT } from '../i18n/useT'
import { Art } from './Art'

/** A language's code on its own colour, as set in Loba Studio. */
export function LangBadge({ lang }: { lang: LangCode }) {
  const color = useQuery(catalogQuery).data?.languages.find((l) => l.code === lang)?.color
  return <span className="lang-badge" style={color ? ({ '--badge': color } as React.CSSProperties) : undefined}>{lang.toUpperCase()}</span>
}

/** A counter that gives a little jump whenever its value changes. */
export function Num({ value }: { value: number }) {
  const prev = useRef(value)
  const [bumps, setBumps] = useState(0)
  useEffect(() => {
    if (prev.current === value) return
    prev.current = value
    setBumps((n) => n + 1)
  }, [value])
  return <span key={bumps} className={bumps ? 'num-bump' : undefined}>{value}</span>
}

export function Stats() {
  const { t, langName } = useT()
  const s = useSelector(game, (s) => s)
  const streak = visibleStreak(s, dayKey())
  return (
    <div className="stats">
      <Link to="/profile" className="lang-chip" aria-label={`${t('learning')}: ${langName(s.lang)}`}>
        <LangBadge lang={s.lang} />
      </Link>
      <Link to="/profile" className={`stat streak ${streak ? 'on' : ''}`} aria-label={`${t('streak')}: ${streak}`}>
        <Art name={streak ? 'flame' : 'flameOff'} /> <Num value={streak} />
      </Link>
      <Link to="/shop" className="stat gems" aria-label={`${t('gems')}: ${s.gems}`}>
        <Art name="gem" /> <Num value={s.gems} />
      </Link>
      <Link to="/shop" className={`stat hearts ${s.hearts ? '' : 'empty'}`} aria-label={`${t('hearts')}: ${s.hearts}/${MAX_HEARTS}`}>
        <Art name={s.hearts ? 'heart' : 'heartOff'} /> <Num value={s.hearts} />
      </Link>
    </div>
  )
}
