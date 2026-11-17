import { useQuery, useQueryClient } from '@tanstack/react-query'
import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { AccountError, deleteAccount, logout, meQuery, rename, type Me } from '../../account/api'
import { forgetDevice } from '../../account/sync'
import { useLanguages } from '../../hooks'
import { useSelector } from '@tanstack/react-store'
import { useState } from 'react'
import { Art, type ArtName } from '../../components/Art'
import { Modal } from '../../components/Modal'
import { Loba } from '../../components/Loba'
import { Rail } from '../../components/Rail'
import { LangBadge } from '../../components/Stats'
import { AVATARS, dayKey, GOALS } from '../../game/state'
import { visibleStreak, wordsLearned } from '../../game/rules'
import { actions, game } from '../../game/store'
import { useT } from '../../i18n/useT'

export const Route = createFileRoute('/_tabs/profile')({ component: ProfilePage })

function Toggle({ on, label, onToggle }: { on: boolean; label: string; onToggle: () => void }) {
  return (
    <div className="setting">
      <span id={`lbl-${label}`}>{label}</span>
      <button className="switch" role="switch" aria-checked={on} aria-labelledby={`lbl-${label}`} onClick={onToggle} />
    </div>
  )
}

function ProfilePage() {
  const { t, ui, langName } = useT()
  const navigate = useNavigate()
  const s = useSelector(game, (s) => s)
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState(s.name)
  const [avatar, setAvatar] = useState(s.avatar)
  const [confirmReset, setConfirmReset] = useState(false)
  const qc = useQueryClient()
  const me = useQuery(meQuery).data
  const languages = useLanguages()
  const [confirmSignOut, setConfirmSignOut] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [password, setPassword] = useState('')
  const [accountError, setAccountError] = useState('')

  const leave = () => {
    forgetDevice()
    qc.setQueryData<Me>(meQuery.queryKey, { learner: null, progress: null, offline: false })
    navigate({ to: '/home' })
  }
  const signOut = async () => {
    try { await logout() } catch { /* the cookie expires on its own */ }
    leave()
  }
  const removeAccount = async () => {
    setAccountError('')
    try {
      await deleteAccount(password)
      leave()
    } catch (err) {
      setAccountError(err instanceof AccountError && err.status ? err.message : t('accountOffline'))
    }
  }

  const stats = [
    ['flame', visibleStreak(s, dayKey()), t('streak')],
    ['medal', s.bestStreak, t('bestStreak')],
    ['bolt', s.xp, t('totalXpLabel')],
    ['book', s.lessonsDone, t('lessonsDone')],
    ['word', wordsLearned(s, s.lang), t('wordsLearned')],
    ['freeze', s.freezes, t('streakFreeze')],
  ] satisfies [ArtName, number, string][]

  return (
    <div className="page">
      <div>
        <section className="profile-hero">
          <button className="avatar" onClick={() => setEditing(true)} aria-label={t('yourName')}>{s.avatar}</button>
          <div style={{ display: 'grid', gap: 4 }}>
            <h1>{s.name || t('learner')}</h1>
            <p className="muted" style={{ fontWeight: 700 }}>{t('joined', { lang: langName(s.lang) })}</p>
            <button className="btn small outline" style={{ justifySelf: 'start', marginTop: 6 }} onClick={() => setEditing(true)}>✎ {t('profile')}</button>
          </div>
        </section>

        <h2 style={{ marginBottom: 14 }}>{t('stats')}</h2>
        <div className="stat-grid" style={{ marginBottom: 32 }}>
          {stats.map(([art, value, label]) => (
            <div className="stat-card" key={label}>
              <Art name={art} />
              <div><b>{value}</b><span>{label}</span></div>
            </div>
          ))}
        </div>

        <h2 style={{ marginBottom: 6 }}>{t('settings')}</h2>
        <div className="settings">
          <div className="setting">
            <span>{t('learning')}</span>
            <div className="lang-picker" role="group" aria-label={t('learning')}>
              {languages.map(({ code: c }) => (
                <button key={c} aria-pressed={s.lang === c} onClick={() => actions.setLang(c)}>
                  <LangBadge lang={c} /> <span style={{ textTransform: 'capitalize' }}>{langName(c)}</span>
                </button>
              ))}
            </div>
          </div>
          <div className="setting">
            <span>{t('interfaceLang')}</span>
            <div className="segmented" role="group" aria-label={t('interfaceLang')}>
              {(['fr', 'en'] as const).map((c) => (
                <button key={c} aria-pressed={ui === c} onClick={() => actions.setUi(c)}>{c === 'fr' ? 'Français' : 'English'}</button>
              ))}
            </div>
          </div>
          <div className="setting">
            <span>{t('dailyGoalSetting')}</span>
            <div className="segmented" role="group" aria-label={t('dailyGoalSetting')}>
              {GOALS.map((g) => (
                <button key={g} aria-pressed={s.goal === g} onClick={() => actions.setGoal(g)}>{g}</button>
              ))}
            </div>
          </div>
          <Toggle on={s.sound} label={t('sound')} onToggle={() => actions.toggle('sound')} />
          <Toggle on={s.voice} label={t('voice')} onToggle={() => actions.toggle('voice')} />
        </div>

        {me?.learner && (
          <>
            <h2 style={{ margin: '32px 0 6px' }}>{t('account')}</h2>
            <div className="settings">
              <div className="setting account-row">
                <span><b>{me.learner.name}</b><small>{me.learner.email}</small></span>
                <button className="btn small outline" onClick={() => setConfirmSignOut(true)}>{t('signOut')}</button>
              </div>
            </div>
          </>
        )}

        <p className="muted" style={{ fontWeight: 650, margin: '24px 0 4px' }}>
          {me?.learner ? t('savedToAccount') : t('savedOnDevice')} · <a href="/privacy.html">{t('privacy')}</a>
        </p>
        <button className="danger-link" onClick={() => setConfirmReset(true)}>{t('resetProgress')}</button>
        {me?.learner && <button className="danger-link" style={{ display: 'block' }} onClick={() => { setPassword(''); setAccountError(''); setConfirmDelete(true) }}>{t('deleteAccount')}</button>}
      </div>
      <Rail />

      <Modal open={editing} onClose={() => setEditing(false)} labelledBy="edit-title">
        <h2 id="edit-title" style={{ marginBottom: 16 }}>{t('profile')}</h2>
        <div style={{ display: 'grid', gap: 16, textAlign: 'left' }}>
          <label style={{ display: 'grid', gap: 6, fontWeight: 800 }}>
            {t('yourName')}
            <input className="text-input" value={name} maxLength={20} onChange={(e) => setName(e.target.value)} />
          </label>
          <div className="avatars" role="group" aria-label="Avatar">
            {AVATARS.map((a) => (
              <button key={a} aria-pressed={avatar === a} onClick={() => setAvatar(a)}>{a}</button>
            ))}
          </div>
          <button className="btn block" onClick={() => {
            actions.setProfile(name, avatar)
            setEditing(false)
            if (me?.learner && name.trim() && name.trim() !== me.learner.name) {
              rename(name.trim()).then(({ learner }) => qc.setQueryData<Me>(meQuery.queryKey, (m) => m && { ...m, learner })).catch(() => {})
            }
          }}>{t('continue')}</button>
        </div>
      </Modal>

      <Modal open={confirmSignOut} onClose={() => setConfirmSignOut(false)} labelledBy="signout-title">
        <div className="modal-art"><Loba mood="wave" /></div>
        <h2 id="signout-title">{t('signOut')}</h2>
        <p>{t('signOutText')}</p>
        <div className="modal-actions">
          <button className="btn" onClick={() => setConfirmSignOut(false)}>{t('noThanks')}</button>
          <button className="btn outline" onClick={signOut}>{t('signOut')}</button>
        </div>
      </Modal>

      <Modal open={confirmDelete} onClose={() => setConfirmDelete(false)} labelledBy="delete-title">
        <div className="modal-art"><Loba mood="sad" /></div>
        <h2 id="delete-title">{t('deleteAccount')}</h2>
        <p>{t('deleteAccountText')}</p>
        <form className="auth" onSubmit={(e) => { e.preventDefault(); void removeAccount() }}>
          <input className="text-input" type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder={t('fieldPassword')} aria-label={t('fieldPassword')} autoComplete="current-password" required />
          {accountError && <p className="auth-error" role="alert">{accountError}</p>}
          <div className="modal-actions">
            <button type="button" className="btn" onClick={() => setConfirmDelete(false)}>{t('noThanks')}</button>
            <button type="submit" className="btn red" disabled={!password}>{t('deleteAccount')}</button>
          </div>
        </form>
      </Modal>

      <Modal open={confirmReset} onClose={() => setConfirmReset(false)} labelledBy="reset-title">
        <div className="modal-art"><Loba mood="sad" /></div>
        <h2 id="reset-title">{t('resetProgress')}</h2>
        <p>{t('resetConfirm')}</p>
        <div className="modal-actions">
          <button className="btn" onClick={() => setConfirmReset(false)}>{t('noThanks')}</button>
          <button className="btn red" onClick={() => { actions.reset(); navigate({ to: '/welcome' }) }}>{t('resetProgress')}</button>
        </div>
      </Modal>
    </div>
  )
}
