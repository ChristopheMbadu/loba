import { createFileRoute, Link } from '@tanstack/react-router'
import { useSelector } from '@tanstack/react-store'
import { useCallback, useEffect, useRef, useState, type CSSProperties } from 'react'
import { ambience, sfx } from '../audio/sfx'
import { Icon } from '../components/Icon'
import { LangBadge } from '../components/Stats'
import { UiSwitch } from '../components/UiSwitch'
import { actions, game } from '../game/store'
import { useLanguages } from '../hooks'
import { useT } from '../i18n/useT'
import { useStartLink } from '../landing/useStart'
import { Scene } from '../story/Scene'
import { CROCODILE_AND_HEN as tale, tokens, type WordKey } from '../story/tale'
import '../styles/story.css'

/** Masapo: the story book. Open to everyone, no account needed. */
export const Route = createFileRoute('/story')({ component: StoryBook })

const UI = {
  fr: {
    shelf: 'Masapo · Contes du Congo',
    callHint: 'Chez nous, le conteur lance « Lisapo ! » et tout le monde répond « Onge ! ». Réponds pour ouvrir le conte.',
    answer: 'Onge !',
    words: 'Mots en',
    next: 'Page suivante',
    prev: 'Page précédente',
    close: 'Fermer le conte',
    listen: 'Écouter',
    tapHint: 'Touche les mots en couleur et les personnages.',
    end: 'Le conte est fini.',
    learned: 'Les mots du conte',
    again: 'Relire',
    learn: 'Apprendre la langue',
    sound: 'Sons',
    page: 'Page',
  },
  en: {
    shelf: 'Masapo · Tales of Congo',
    callHint: 'Back home, the storyteller calls “Lisapo!” and everyone answers “Onge!”. Answer to open the tale.',
    answer: 'Onge!',
    words: 'Words in',
    next: 'Next page',
    prev: 'Previous page',
    close: 'Close the tale',
    listen: 'Listen',
    tapHint: 'Tap the coloured words and the characters.',
    end: 'The tale is finished.',
    learned: 'Words from the tale',
    again: 'Read again',
    learn: 'Learn the language',
    sound: 'Sounds',
    page: 'Page',
  },
} as const

const PHASES = ['dawn', 'dawn', 'day', 'day', 'day', 'dusk', 'dusk', 'night', 'night'] as const

function StoryBook() {
  const { ui, langName } = useT()
  const s = UI[ui]
  const start = useStartLink()
  const sound = useSelector(game, (g) => g.sound)
  const learning = useSelector(game, (g) => g.lang)
  // The tale's words exist in these languages; offer the ones learners can actually study.
  const offered = useLanguages().map((l) => l.code).filter((code) => code in tale.words.croc.in)
  const codes = offered.length ? offered : ['ln']
  const [code, setCode] = useState(() => (codes.includes(learning) ? learning : codes[0]!))
  const [page, setPage] = useState(0)
  const [word, setWord] = useState<WordKey | null>(null)
  const last = tale.pages.length + 1
  const phase = PHASES[Math.min(page, PHASES.length - 1)]!
  const opened = useRef(false)
  const swipe = useRef<number | null>(null)
  const voice = useRef<HTMLAudioElement | null>(null)

  const go = useCallback(
    (to: number) => {
      const next = Math.max(0, Math.min(last, to))
      if (next === page) return
      if (page === 0 && next > 0) {
        opened.current = true
        sfx('call')
      } else sfx('page', next)
      if (next === 2) setTimeout(() => sfx('splash'), 650)
      voice.current?.pause()
      setWord(null)
      setPage(next)
    },
    [page, last],
  )

  // The river plays under the day pages, crickets under the night ones, once the book is open.
  const night = phase === 'night'
  useEffect(() => {
    if (opened.current && page > 0) ambience(sound ? (night ? 'night' : 'river') : null)
    else ambience(null)
  }, [night, sound, page === 0])
  useEffect(() => () => ambience(null), [])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'ArrowRight' || e.key === ' ') go(page + 1)
      if (e.key === 'ArrowLeft') go(page - 1)
    }
    addEventListener('keydown', onKey)
    return () => removeEventListener('keydown', onKey)
  }, [go, page])

  const say = (key: WordKey) => tale.words[key].in[code] ?? tale.words[key].in.ln!
  const current = page >= 1 && page < last ? tale.pages[page - 1]! : null
  const listen = () => {
    if (!current?.audio) return
    voice.current?.pause()
    voice.current = new Audio(`/api/v1/audio/${current.audio}`)
    void voice.current.play().catch(() => {})
  }

  let wordIndex = 0
  return (
    <div
      className="tale"
      data-page={Math.min(page, last - 1)}
      data-phase={phase}
      data-end={page === last || undefined}
      onTouchStart={(e) => (swipe.current = e.touches[0]!.clientX)}
      onTouchEnd={(e) => {
        if (swipe.current === null) return
        const dx = e.changedTouches[0]!.clientX - swipe.current
        swipe.current = null
        if (Math.abs(dx) > 60 && page > 0) go(page + (dx < 0 ? 1 : -1))
      }}
    >
      <header className="tale-bar">
        <Link to="/home" className="tale-close" aria-label={s.close}><Icon name="close" /></Link>
        <span className="tale-shelf">{s.shelf}</span>
        <div className="tale-tools">
          {codes.length > 1 && (
            <div className="tale-langs" role="group" aria-label={s.words}>
              {codes.map((c) => (
                <button key={c} aria-pressed={code === c} onClick={() => { sfx('tap'); setCode(c) }} aria-label={`${s.words} ${langName(c)}`}>
                  <LangBadge lang={c} />
                </button>
              ))}
            </div>
          )}
          <UiSwitch />
          <button className="tale-sound" aria-pressed={sound} aria-label={s.sound} onClick={() => actions.toggle('sound')}>
            <Icon name="speaker" />
          </button>
        </div>
      </header>

      <main className="tale-book">
        <div className="tale-art stage" data-page={Math.min(page, last - 1)} data-phase={phase}>
          <Scene shout={`${say('sibling')} !`} />
          <div className="tale-cover" aria-hidden={page !== 0}>
            <span>{tale.origin[ui]}</span>
            <h1>{tale.title[ui]}</h1>
          </div>
        </div>

        <section className="tale-page" key={`${page}-${ui}-${code}`} aria-live="polite">
          {page === 0 && (
            <div className="tale-open">
              <p className="tale-call" lang="ln">Lisapo !</p>
              <p className="tale-call-hint">{s.callHint}</p>
              <button className="btn sun tale-answer" lang="ln" onClick={() => go(1)}>{s.answer}</button>
            </div>
          )}

          {current && (
            <>
              <span className="tale-folio">{s.page} {page} / {tale.pages.length}</span>
              <p className="tale-text">
                {tokens(current.text[ui]).map((part, i) =>
                  typeof part === 'string' ? (
                    part.split(/(\s+)/).map((w, k) =>
                      w.trim() ? <span key={`${i}-${k}`} className="tw" style={{ '--w': wordIndex++ } as CSSProperties}>{w}</span> : w,
                    )
                  ) : (
                    <button
                      key={i}
                      className={`tw tale-word ${word === part.key ? 'open' : ''}`}
                      style={{ '--w': wordIndex++ } as CSSProperties}
                      lang={code}
                      aria-expanded={word === part.key}
                      onClick={() => {
                        sfx('tile', i)
                        setWord(word === part.key ? null : part.key)
                      }}
                    >
                      {say(part.key)}
                      {word === part.key && <i lang={ui}>{tale.words[part.key].meaning[ui]}</i>}
                    </button>
                  ),
                )}
              </p>
              {current.audio && (
                <button className="btn small outline tale-listen" onClick={listen}><Icon name="speaker" /> {s.listen}</button>
              )}
              {page === 1 && <p className="tale-hint">{s.tapHint}</p>}
            </>
          )}

          {page === last && (
            <div className="tale-end">
              <p className="tale-call" lang="ln">Lisapo esili.</p>
              <p className="tale-call-hint">{s.end}</p>
              <p className="tale-moral">{tale.moral[ui]}</p>
              <h2>{s.learned}</h2>
              <ul className="tale-glossary">
                {(Object.keys(tale.words) as WordKey[]).map((key, i) => (
                  <li key={key} style={{ '--w': i } as CSSProperties}>
                    <b lang={code}>{say(key)}</b>
                    <span>{tale.words[key].meaning[ui]}</span>
                  </li>
                ))}
              </ul>
              <div className="tale-end-actions">
                <Link {...start} className="btn green">{s.learn}</Link>
                <button className="btn outline" onClick={() => go(0)}>{s.again}</button>
              </div>
              <small>{tale.source[ui]}</small>
            </div>
          )}
        </section>
      </main>

      <footer className="tale-nav">
        <button className="tale-arrow" disabled={page === 0} onClick={() => go(page - 1)} aria-label={s.prev}>‹</button>
        <ol className="tale-beads" aria-hidden="true">
          {Array.from({ length: last + 1 }, (_, i) => (
            <li key={i} className={i === page ? 'on' : i < page ? 'done' : ''} />
          ))}
        </ol>
        <button className="tale-arrow next" disabled={page === last} onClick={() => go(page + 1)} aria-label={s.next}>›</button>
      </footer>
    </div>
  )
}
