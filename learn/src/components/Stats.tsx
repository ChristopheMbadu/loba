import { Link } from '@tanstack/react-router'
import { useSelector } from '@tanstack/react-store'
import { dayKey, MAX_HEARTS } from '../game/state'
import { visibleStreak } from '../game/rules'
import { game } from '../game/store'
import { useT } from '../i18n/useT'
import { Art } from './Art'

export function LangBadge({ lang }: { lang: 'ln' | 'sw' }) {
  return <span className={`lang-badge ${lang}`}>{lang.toUpperCase()}</span>
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
