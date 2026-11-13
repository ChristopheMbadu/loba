import { createFileRoute, Link, Outlet, redirect } from '@tanstack/react-router'
import { useSelector } from '@tanstack/react-store'
import { Art, type ArtName } from '../components/Art'
import { Loba } from '../components/Loba'
import { Stats } from '../components/Stats'
import { dayKey } from '../game/state'
import { dailyQuests } from '../game/rules'
import { game } from '../game/store'
import { useT } from '../i18n/useT'
import type { StringKey } from '../i18n/strings'

export const Route = createFileRoute('/_tabs')({
  beforeLoad: () => {
    if (!game.get().onboarded) throw redirect({ to: '/welcome' })
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
      </nav>
      <header className="topbar">
        <Stats />
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
