import { useQuery } from '@tanstack/react-query'
import { Link } from '@tanstack/react-router'
import { catalogQuery } from '../content/curriculum'
import type { LangCode } from '../content/types'
import { useSelector } from '@tanstack/react-store'
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
        <Art name={streak ? 'flame' : 'flameOff'} /> {streak}
      </Link>
      <Link to="/shop" className="stat gems" aria-label={`${t('gems')}: ${s.gems}`}>
        <Art name="gem" /> {s.gems}
      </Link>
      <Link to="/shop" className={`stat hearts ${s.hearts ? '' : 'empty'}`} aria-label={`${t('hearts')}: ${s.hearts}/${MAX_HEARTS}`}>
        <Art name={s.hearts ? 'heart' : 'heartOff'} /> {s.hearts}
      </Link>
    </div>
  )
}
