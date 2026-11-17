import { useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { actions, game } from '../game/store'
import { useT } from '../i18n/useT'
import { AccountError, login, meQuery, signup, type Me } from './api'
import { adopt, uploadNow } from './sync'

export type AuthMode = 'signup' | 'login'

/**
 * Name, email and password — nothing else. Signing up saves this device's progress to the
 * new account; signing in brings the account's progress to this device.
 */
export function AuthForm({ mode, onModeChange, onDone }: {
  mode: AuthMode
  onModeChange: (mode: AuthMode) => void
  onDone: (result: { restored: boolean }) => void
}) {
  const { t } = useT()
  const qc = useQueryClient()
  const [name, setName] = useState(() => game.get().name)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [reveal, setReveal] = useState(false)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (busy) return
    setBusy(true)
    setError('')
    try {
      if (mode === 'signup') {
        const s = game.get()
        const session = await signup({ name: name.trim(), email: email.trim(), password, state: s.onboarded ? s : undefined })
        actions.setProfile(session.learner.name, s.avatar)
        qc.setQueryData<Me>(meQuery.queryKey, { learner: session.learner, progress: session.progress, offline: false })
        onDone({ restored: false })
      } else {
        const session = await login({ email: email.trim(), password })
        qc.setQueryData<Me>(meQuery.queryKey, { learner: session.learner, progress: session.progress, offline: false })
        // A returning learner's saved progress wins; a brand-new account keeps what this device has.
        const restored = adopt(session.progress)
        if (!restored && game.get().onboarded) await uploadNow(qc)
        onDone({ restored })
      }
    } catch (err) {
      setError(err instanceof AccountError && err.status ? err.message : t('accountOffline'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="auth" onSubmit={submit} noValidate={false}>
      {mode === 'signup' && (
        <label className="auth-field">
          <span>{t('fieldName')}</span>
          <input className="text-input" value={name} onChange={(e) => setName(e.target.value)} autoComplete="given-name" required maxLength={40} />
        </label>
      )}
      <label className="auth-field">
        <span>{t('fieldEmail')}</span>
        <input className="text-input" type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" inputMode="email" required maxLength={254} />
      </label>
      <label className="auth-field">
        <span>{t('fieldPassword')}</span>
        <span className="auth-password">
          <input className="text-input" type={reveal ? 'text' : 'password'} value={password} onChange={(e) => setPassword(e.target.value)}
            autoComplete={mode === 'signup' ? 'new-password' : 'current-password'} required minLength={mode === 'signup' ? 8 : undefined} maxLength={256} />
          <button type="button" className="auth-reveal" onClick={() => setReveal(!reveal)} aria-label={reveal ? t('hidePassword') : t('showPassword')} aria-pressed={reveal}>
            {reveal ? (
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 3l18 18M10.6 6.1A9.9 9.9 0 0 1 12 6c5.5 0 9 6 9 6a16 16 0 0 1-3.2 3.8M6.6 7.6C4.3 9.2 3 12 3 12s3.5 6 9 6c1.6 0 3-.4 4.2-1.1M9.9 9.9a3 3 0 0 0 4.2 4.2" /></svg>
            ) : (
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 12s3.5-6 9-6 9 6 9 6-3.5 6-9 6-9-6-9-6z" /><circle cx="12" cy="12" r="3" /></svg>
            )}
          </button>
        </span>
        {mode === 'signup' && <small>{t('passwordHint')}</small>}
      </label>

      {error && <p className="auth-error" role="alert">{error}</p>}

      <button className="btn green block" type="submit" disabled={busy}>
        {mode === 'signup' ? t('createAccount') : t('signIn')}
      </button>
      <button type="button" className="btn ghost block" onClick={() => { setError(''); onModeChange(mode === 'signup' ? 'login' : 'signup') }}>
        {mode === 'signup' ? t('haveAccount') : t('newHere')}
      </button>
      {mode === 'signup' && (
        <p className="auth-terms">{t('accountTerms')} <a href="/privacy.html">{t('privacyPolicy')}</a>.</p>
      )}
    </form>
  )
}
