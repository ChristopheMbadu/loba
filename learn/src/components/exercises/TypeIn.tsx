import { useSelector } from '@tanstack/react-store'
import type { Exercise } from '../../game/exercises'
import { game } from '../../game/store'
import { useT } from '../../i18n/useT'
import type { Answer } from './answer'

export function TypeIn({ ex, answer, onAnswer, onSubmit, locked }: { ex: Extract<Exercise, { kind: 'type' }>; answer: Answer | null; onAnswer: (a: Answer) => void; onSubmit: () => void; locked: boolean }) {
  const { t, meaning, langName } = useT()
  const lang = langName(useSelector(game, (s) => s.lang))
  return (
    <>
      <div className="ex-head">
        {ex.retry && <span className="ex-tag retry">↻ {t('previousMistake')}</span>}
        <h1>{t('typeAnswer', { lang })}</h1>
      </div>
      <div className="prompt">
        <span className="prompt-text">{meaning(ex.word)}</span>
      </div>
      <textarea
        className="type-input"
        autoFocus
        readOnly={locked}
        placeholder={t('typePlaceholder')}
        aria-label={t('typePlaceholder')}
        autoCapitalize="off"
        autoCorrect="off"
        spellCheck={false}
        lang="und"
        value={answer?.kind === 'text' ? answer.value : ''}
        onChange={(e) => onAnswer({ kind: 'text', value: e.target.value })}
        onKeyDown={(e) => {
          if (e.key === 'Enter') {
            e.preventDefault()
            onSubmit()
          }
        }}
      />
    </>
  )
}
