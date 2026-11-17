import { actions } from '../game/store'
import { useT } from '../i18n/useT'

/** Switches the whole site between French and English. The choice is saved with the learner's progress. */
export function UiSwitch({ className = '' }: { className?: string }) {
  const { ui, t } = useT()
  return (
    <div className={`segmented ui-switch ${className}`} role="group" aria-label={t('interfaceLang')}>
      {(['fr', 'en'] as const).map((code) => (
        <button key={code} aria-pressed={ui === code} lang={code} onClick={() => actions.setUi(code)}>
          {code.toUpperCase()}
        </button>
      ))}
    </div>
  )
}
