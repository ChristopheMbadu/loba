import { Link } from '@tanstack/react-router'
import { useSelector } from '@tanstack/react-store'
import { dayKey } from '../game/state'
import { dailyQuests, visibleStreak, type QuestId } from '../game/rules'
import { game } from '../game/store'
import { useT } from '../i18n/useT'
import { Art, type ArtName } from './Art'
import { Stats } from './Stats'
import { Week } from './Week'

export const QUEST_ART: Record<QuestId, ArtName> = { xp: 'bolt', lessons: 'book', combo: 'flame', perfect: 'target' }
export const QUEST_LABEL = { xp: 'questXp', lessons: 'questLessons', combo: 'questCombo', perfect: 'questPerfect' } as const

export function StreakCard() {
  const { t } = useT()
  const streak = useSelector(game, (s) => visibleStreak(s, dayKey()))
  return (
    <section className="card">
      <div className={`streak-card ${streak ? '' : 'off'}`}>
        <Art name={streak ? 'flame' : 'flameOff'} size={56} />
        <div>
          <div className="num">{streak}</div>
          <p>{t(streak === 1 ? 'streakDayLabel' : 'streakDaysLabel')}</p>
        </div>
      </div>
      <Week />
    </section>
  )
}

export function DailyGoalCard() {
  const { t } = useT()
  const { xp, goal } = useSelector(game, (s) => ({ xp: s.daily.day === dayKey() ? s.daily.xp : 0, goal: s.goal }), { compare: (a, b) => a.xp === b.xp && a.goal === b.goal })
  const pct = Math.min(100, Math.round((xp / goal) * 100))
  return (
    <section className="card">
      <div className="goal">
        <Art name={pct >= 100 ? 'trophy' : 'bolt'} />
        <div className="goal-body">
          <h3>{pct >= 100 ? t('goalDone') : t('dailyGoal')}</h3>
          <div className="meter-row">
            <div className="meter" role="progressbar" aria-valuemin={0} aria-valuemax={goal} aria-valuenow={xp} aria-label={t('dailyGoal')}>
              <span style={{ width: `${pct}%` }} />
            </div>
            <span className="meter-label">{t('xpToday', { xp, goal })}</span>
          </div>
        </div>
      </div>
    </section>
  )
}

export function QuestsPreview() {
  const { t } = useT()
  const s = useSelector(game, (s) => s)
  return (
    <section className="card">
      <div className="card-head">
        <h2>{t('questsTitle')}</h2>
        <Link to="/quests">{t('seeAll')}</Link>
      </div>
      {dailyQuests(s, dayKey()).slice(0, 3).map((q) => (
        <div className="quest" key={q.id}>
          <Art name={QUEST_ART[q.id]} />
          <div className="quest-body">
            <h3>{t(QUEST_LABEL[q.id], { n: q.goal })}</h3>
            <div className="meter-row">
              <div className="meter"><span style={{ width: `${Math.min(100, (q.value / q.goal) * 100)}%` }} /></div>
              <Art name={q.value >= q.goal ? 'chestOpen' : 'chest'} size={34} />
            </div>
          </div>
        </div>
      ))}
    </section>
  )
}

export function Rail() {
  return (
    <aside className="rail">
      <Stats />
      <StreakCard />
      <DailyGoalCard />
      <QuestsPreview />
    </aside>
  )
}
