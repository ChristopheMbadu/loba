import { createFileRoute, Link, Outlet } from '@tanstack/react-router'
import { requireLearner } from '../account/guard'
import { catalogQuery, languageOrFirst } from '../content/curriculum'
import { useSelector } from '@tanstack/react-store'
import { Art, type ArtName } from '../components/Art'
import { Loba } from '../components/Loba'
import { Stats } from '../components/Stats'
import { UiSwitch } from '../components/UiSwitch'
import { dayKey } from '../game/state'
import { dailyQuests } from '../game/rules'
import { actions, game } from '../game/store'
import { useT } from '../i18n/useT'
import type { StringKey } from '../i18n/strings'

export const Route = createFileRoute('/_tabs')({
  beforeLoad: async ({ context }) => {
    await requireLearner(context.queryClient)
    // If the learner's language was hidden in Studio, continue with one that's available.
    const code = languageOrFirst(await context.queryClient.ensureQueryData(catalogQuery), game.get().lang)
    if (code !== game.get().lang) actions.setLang(code)
  },
  component: TabsLayout,
})

const TABS: { to: '/' | '/practice' | '/quests' | '/shop' | '/profile'; icon: ArtName; label: StringKey }[] = [
  { to: '/', icon: 'navLearn', label: 'learn' },
  { to: '/practice', icon: 'dumbbell', label: 'practice' },
  { to: '/quests', icon: 'target', label: 'quests' },
  { to: '/shop', icon: 'navShop', label: 'shop' },
  { to: '/profile', icon: 'navProfile', label: 'profile' },
]

function NavLinks() {
  const { t } = useT()
  const claimable = useSelector(game, (s) => {
    const today = dayKey()
    return dailyQuests(s, today).filter((q) => q.value >= q.goal && !(s.daily.day === today && s.daily.claimed.includes(q.id))).length
  })
  return TABS.map((tab) => (
    <Link key={tab.to} to={tab.to} className="nav-link" activeOptions={{ exact: tab.to === '/' }} aria-label={t(tab.label)}>
      <Art name={tab.icon} />
      <span>{t(tab.label)}</span>
      {tab.to === '/quests' && claimable > 0 && <b className="nav-badge">{claimable}</b>}
    </Link>
  ))
}

function TabsLayout() {
  const { t } = useT()
  return (
    <div className="shell">
      <nav className="sidebar" aria-label="Loba">
        <Link to="/" className="brand" aria-label="Loba">
          <Loba mood="happy" />
          <span className="brand-word">loba</span>
        </Link>
        <p className="brand-tag">{t('appTagline')}</p>
        <NavLinks />
        <UiSwitch className="sidebar-ui" />
      </nav>
      <header className="topbar">
        <Stats />
        <UiSwitch />
      </header>
      <main className="main">
        <Outlet />
      </main>
      <nav className="tabbar" aria-label="Loba">
        <NavLinks />
      </nav>
    </div>
  )
}
