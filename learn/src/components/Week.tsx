import { useSelector } from '@tanstack/react-store'
import { dayKey } from '../game/state'
import { game } from '../game/store'
import { useT } from '../i18n/useT'
import { Icon } from './Icon'

const LETTERS = { fr: ['L', 'M', 'M', 'J', 'V', 'S', 'D'], en: ['M', 'T', 'W', 'T', 'F', 'S', 'S'] }

/** Monday-to-Sunday strip showing which days of this week had a completed session. */
export function Week() {
  const { ui } = useT()
  const active = useSelector(game, (s) => s.activeDays)
  const now = new Date()
  const monday = new Date(now.getFullYear(), now.getMonth(), now.getDate() - ((now.getDay() + 6) % 7))
  const today = dayKey(now)
  return (
    <div className="week">
      {LETTERS[ui].map((letter, i) => {
        const key = dayKey(new Date(monday.getFullYear(), monday.getMonth(), monday.getDate() + i))
        const on = active.includes(key)
        return (
          <div key={i}>
            <span className={key === today ? 'today' : ''}>{letter}</span>
            <i className={`${on ? 'on' : ''} ${key === today && !on ? 'now' : ''}`}>{on && <Icon name="check" />}</i>
          </div>
        )
      })}
    </div>
  )
}
