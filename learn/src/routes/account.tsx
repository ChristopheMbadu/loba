import { createFileRoute, Link, redirect, useNavigate } from '@tanstack/react-router'
import { meQuery } from '../account/api'
import { AuthForm, type AuthMode } from '../account/AuthForm'
import { Loba } from '../components/Loba'
import { actions, game } from '../game/store'
import { useT } from '../i18n/useT'
import { HeroScene } from '../landing/HeroScene'
import { useLanding } from '../landing/useLanding'
import { useGreetings } from '../landing/useStart'
import '../styles/landing.css'

/** Sign up or sign in — the first step before any learning. Styled like the landing page. */
export const Route = createFileRoute('/account')({
  validateSearch: (search: Record<string, unknown>): { mode: AuthMode } => ({ mode: search.mode === 'login' ? 'login' : 'signup' }),
  beforeLoad: async ({ context }) => {
    const me = await context.queryClient.ensureQueryData(meQuery)
    if (me.learner) throw redirect({ to: game.get().onboarded ? '/' : '/welcome' })
  },
  component: AccountPage,
})

function AccountPage() {
  const { t, ui } = useT()
  const l = useLanding()
  const navigate = useNavigate()
  const greetings = useGreetings()
  const { mode } = Route.useSearch()
  const onboarded = game.get().onboarded
  const login = mode === 'login'

  return (
    <div className="home acct">
      <header className="lp-head scrolled">
        <div className="lp-wrap lp-head-inner">
          <Link to="/home" className="lp-brand" aria-label="Loba">
            <Loba mood="happy" />
            <span className="brand-word">loba</span>
          </Link>
          <div className="segmented" role="group" aria-label={t('interfaceLang')}>
            {(['fr', 'en'] as const).map((code) => (
              <button key={code} aria-pressed={ui === code} onClick={() => actions.setUi(code)}>{code.toUpperCase()}</button>
            ))}
          </div>
        </div>
      </header>

      <main className="lp-wrap acct-main">
        <div className="acct-art">
          <HeroScene hint={l('tapLoba')} greetings={greetings} />
        </div>
        <section className="acct-card" aria-labelledby="acct-title">
          <h1 id="acct-title">{login ? t('loginTitle') : t('accountTitle')}</h1>
          <p>{login ? t('loginWhy') : onboarded ? `${t('accountWhy')} ${t('accountKeep')}` : t('accountWhy')}</p>
          <AuthForm
            mode={mode}
            onModeChange={(m) => navigate({ to: '/account', search: { mode: m }, replace: true })}
            onDone={() => navigate({ to: game.get().onboarded ? '/' : '/welcome' })}
          />
        </section>
      </main>
      <div className="acct-band" aria-hidden="true" />
    </div>
  )
}
