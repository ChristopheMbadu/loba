import { createFileRoute } from '@tanstack/react-router'
import { useSelector } from '@tanstack/react-store'
import { sfx } from '../../audio/sfx'
import { Art } from '../../components/Art'
import { DailyGoalCard, QUEST_ART, QUEST_LABEL as LABEL, StreakCard } from '../../components/Rail'
import { Stats } from '../../components/Stats'
import { RewardSticker } from '../../components/RewardSticker'
import { dayKey } from '../../game/state'
import { dailyQuests } from '../../game/rules'
import { actions, game } from '../../game/store'
import { useT } from '../../i18n/useT'

export const Route = createFileRoute('/_tabs/quests')({ component: QuestsPage })


function QuestsPage() {
  const { t } = useT()
  const s = useSelector(game, (s) => s)
  const today = dayKey()
  const claimed = s.daily.day === today ? s.daily.claimed : []

  return (
    <div className="page">
      <div>
        <header className="page-head quest-hero">
          <div>
            <h1>{t('questsTitle')}</h1>
            <p>{t('questsReset')}</p>
          </div>
          <RewardSticker kind="sparkle" size={88} />
        </header>
        <section className="card">
          {dailyQuests(s, today).map((q) => {
            const complete = q.value >= q.goal
            const isClaimed = claimed.includes(q.id)
            return (
              <div className={`quest ${isClaimed ? 'quest-claimed' : complete ? 'quest-ready' : ''}`} key={q.id}>
                <Art name={QUEST_ART[q.id]} />
                <div className="quest-body">
                  <h3>{t(LABEL[q.id], { n: q.goal })}</h3>
                  <div className="meter-row">
                    <div className="meter" role="progressbar" aria-label={t(LABEL[q.id], { n: q.goal })} aria-valuemin={0} aria-valuemax={q.goal} aria-valuenow={Math.min(q.value, q.goal)} style={{ '--fill': complete ? 'var(--green)' : 'var(--sun)' } as React.CSSProperties}>
                      <span style={{ width: `${Math.min(100, (q.value / q.goal) * 100)}%` }} />
                    </div>
                    <span className="meter-label">{Math.min(q.value, q.goal)}/{q.goal}</span>
                  </div>
                </div>
                <button
                  className={`btn small ${complete && !isClaimed ? 'sun' : 'outline'}`}
                  disabled={!complete || isClaimed}
                  onClick={() => {
                    actions.claimQuest(q.id)
                    sfx('coin')
                  }}
                >
                  {isClaimed ? t('claimed') : <><span className="price" style={{ color: 'inherit' }}><Art name="gem" /> {q.reward}</span></>}
                </button>
              </div>
            )
          })}
        </section>
      </div>
      <aside className="rail">
        <Stats />
        <StreakCard />
        <DailyGoalCard />
      </aside>
    </div>
  )
}
