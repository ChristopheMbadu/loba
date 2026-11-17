import { useQuery } from '@tanstack/react-query'
import { createFileRoute, Link } from '@tanstack/react-router'
import { useSelector } from '@tanstack/react-store'
import { useEffect, useRef, useState } from 'react'
import { meQuery } from '../account/api'
import { Loba } from '../components/Loba'
import { LangBadge } from '../components/Stats'
import { actions, game } from '../game/store'
import { useT } from '../i18n/useT'
import { useCurriculum, useLanguages } from '../hooks'
import type { Taste } from '../landing/copy'
import { useGreetings, useStartLink } from '../landing/useStart'
import { HeroScene } from '../landing/HeroScene'
import { MiniLesson } from '../landing/MiniLesson'
import { ChatPhone, MicDemo, RumbaDrum, StreakDemo } from '../landing/Showcase'
import { useLanding } from '../landing/useLanding'
import { Lottie } from '../components/Lottie'
import { Scene } from '../story/Scene'
import '../styles/landing.css'
import '../styles/story.css'

export const Route = createFileRoute('/home')({ component: Home })

function UiSwitch() {
  const { ui } = useT()
  const t = useLanding()
  return (
    <div className="segmented" role="group" aria-label={t('ui')}>
      {(['fr', 'en'] as const).map((code) => (
        <button key={code} aria-pressed={ui === code} onClick={() => actions.setUi(code)}>{code.toUpperCase()}</button>
      ))}
    </div>
  )
}

function Home() {
  // The flag waves unless the visitor has asked their device for less motion.
  const [waving] = useState(() => !matchMedia('(prefers-reduced-motion: reduce)').matches)
  const t = useLanding()
  const { ui, langName, region } = useT()
  const onboarded = useSelector(game, (s) => s.onboarded)
  const me = useQuery(meQuery).data
  const languages = useLanguages()
  const curriculum = useCurriculum()
  const [taste, setTaste] = useState<Taste>(() => (languages.some((l) => l.code === 'ln') ? 'ln' : languages[0]!.code))
  const greetings = useGreetings()
  const start = useStartLink()
  const micWord = taste === 'ln' ? 'Matondo' : taste === 'sw' ? 'Asante' : curriculum[taste]?.[0]?.words[0]?.term ?? ''
  const [pastHero, setPastHero] = useState(false)
  const ctaRef = useRef<HTMLDivElement>(null)

  useEffect(() => { document.documentElement.lang = ui }, [ui])
  useEffect(() => {
    const el = ctaRef.current
    if (!el) return
    const io = new IntersectionObserver(([e]) => setPastHero(!!e && !e.isIntersecting && e.boundingClientRect.top < 0))
    io.observe(el)
    return () => io.disconnect()
  }, [])

  const playWith = (code: Taste) => {
    setTaste(code)
    document.getElementById('play')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  return (
    <div className="home">
      <header className={`lp-head ${pastHero ? 'scrolled' : ''}`}>
        <div className="lp-wrap lp-head-inner">
          <Link to="/home" className="lp-brand" aria-label="Loba">
            <Loba mood="happy" />
            <span className="brand-word">loba</span>
          </Link>
          <div className="lp-head-right">
            <Link {...start} className="btn green small lp-head-cta" tabIndex={pastHero ? 0 : -1} aria-hidden={!pastHero}>{t('start')}</Link>
            {!me?.learner && !me?.offline && <Link to="/account" search={{ mode: 'login' }} className="lp-signin">{t('signIn')}</Link>}
            <UiSwitch />
          </div>
        </div>
      </header>

      <main>
        <section className="lp-hero lp-wrap">
          <HeroScene hint={t('tapLoba')} greetings={greetings} />
          <div className="lp-hero-copy">
            <h1>{t('heroTitle')}</h1>
            <p>{t('heroText')}</p>
            <div className="lp-ctas" ref={ctaRef}>
              <Link {...start} className="btn green">{t('start')}</Link>
              {onboarded
                ? <Link to="/" className="btn outline">{t('continue')}</Link>
                : <a href="#play" className="btn outline">{t('tryLesson')}</a>}
            </div>
          </div>
        </section>

        <div className="lp-river" aria-hidden="true">
          <div className="lp-boat">
            <Loba mood="idle" />
            <Lottie name="pirogue" loop viewBox="40 190 920 470" />
          </div>
        </div>

        <nav className="lp-langs" aria-label={t('langsLabel')}>
          <div className="lp-wrap lp-langs-inner">
            {languages.map((l) => (
              <button key={l.code} className="lp-lang" onClick={() => playWith(l.code)} style={{ '--badge': l.color } as React.CSSProperties}>
                <LangBadge lang={l.code} />
                <span className="lp-lang-meta">
                  <b>{langName(l.code)}</b>
                  {region(l.code) && <small>{region(l.code)}</small>}
                </span>
                {l.greeting && <span className="lp-lang-word" lang={l.code}>{l.greeting} !</span>}
              </button>
            ))}
          </div>
        </nav>

        <section id="play" className="lp-play">
          <div className="lp-wrap lp-play-inner">
            <div className="lp-play-copy">
              <h2>{t('playTitle')}</h2>
              <p>{t('playText')}</p>
            </div>
            <MiniLesson taste={taste} setTaste={setTaste} />
          </div>
        </section>

        <section className="lp-row lp-wrap">
          <div className="lp-row-art"><ChatPhone taste={taste} /></div>
          <div className="lp-row-copy">
            <h2>{t('chatTitle')}</h2>
            <p>{t('chatText')}</p>
          </div>
        </section>

        <section className="lp-row lp-wrap flip">
          <div className="lp-row-art"><MicDemo taste={taste} word={micWord} /></div>
          <div className="lp-row-copy">
            <h2>{t('speakTitle')}</h2>
            <p>{t('speakText')}</p>
          </div>
        </section>

        <section className="lp-row lp-wrap">
          <div className="lp-row-art"><StreakDemo /></div>
          <div className="lp-row-copy">
            <h2>{t('streakTitle')}</h2>
            <p>{t('streakText')}</p>
          </div>
        </section>

        <section className="lp-story">
          <div className="lp-wrap lp-row">
            <div className="lp-row-art">
              <Link to="/story" className="lp-book" aria-label={t('storyCta')}>
                <span className="lp-book-art stage" data-page="0" data-phase="dawn"><Scene shout="" /></span>
                <span className="lp-book-title">{t('storyName')}</span>
                <span className="lp-book-call" lang="ln" aria-hidden="true">Lisapo !</span>
              </Link>
            </div>
            <div className="lp-row-copy">
              <span className="lp-eyebrow">{t('storyEyebrow')}</span>
              <h2>{t('storyTitle')}</h2>
              <p>{t('storyText')}</p>
              <Link to="/story" className="btn sun">{t('storyCta')}</Link>
            </div>
          </div>
        </section>

        <section className="lp-rumba">
          <div className="lp-wrap lp-row flip">
            <div className="lp-row-art"><RumbaDrum /></div>
            <div className="lp-row-copy">
              <h2>{t('rumbaTitle')}</h2>
              <p>{t('rumbaText')}</p>
            </div>
          </div>
        </section>

        <section className="lp-final">
          <div className="lp-wrap lp-final-inner">
            <div className="lp-final-copy">
              <h2 lang="ln">{t('finalTitle')}</h2>
              <p>{t('finalText')}</p>
              <Link {...start} className="btn white">{t('start')}</Link>
            </div>
            <div className="lp-final-art" aria-hidden="true">
              <svg className="lp-flag" viewBox="0 0 300 280">
                <defs>
                  {/* The cloth's outline ripples from the pole outwards, and a band of light and shade travels across it. */}
                  <clipPath id="lp-flag-cloth">
                    <path d="M46 22c56-22 128 18 214-4v138c-86 22-158-18-214 4z">
                      {waving && <animate attributeName="d" dur="2.8s" repeatCount="indefinite" calcMode="spline" keyTimes="0;0.5;1" keySplines="0.45 0 0.55 1;0.45 0 0.55 1"
                        values="M46 22c56-22 128 18 214-4v138c-86 22-158-18-214 4z;M46 22c56 18 128-20 214 6v138c-86-26-158 20-214-2z;M46 22c56-22 128 18 214-4v138c-86 22-158-18-214 4z" />}
                    </path>
                  </clipPath>
                  <linearGradient id="lp-flag-shade" x1="0" x2="0.5" spreadMethod="reflect">
                    <stop offset="0" stopColor="#000" stopOpacity="0" />
                    <stop offset=".5" stopColor="#000" stopOpacity=".13" />
                    <stop offset="1" stopColor="#fff" stopOpacity=".16" />
                    {waving && <animateTransform attributeName="gradientTransform" type="translate" from="0 0" to="1 0" dur="2.8s" repeatCount="indefinite" />}
                  </linearGradient>
                </defs>
                <rect x="34" y="12" width="10" height="262" rx="5" fill="#8f4f1c" />
                <circle cx="39" cy="12" r="9" fill="#ffc22e" />
                <g clipPath="url(#lp-flag-cloth)">
                  <g className="lp-flag-cloth">
                    <rect x="40" y="-10" width="240" height="210" fill="#1c9bef" />
                    <path d="M20 156 290-33v54L20 210z" fill="#ffc22e" />
                    <path d="M20 166 290-23v34L20 200z" fill="#ef4e4e" />
                    <path d="m84 32 6.5 13.2 14.5 2.1-10.5 10.2 2.5 14.5L84 65.2l-13 6.8 2.5-14.5L63 47.3l14.5-2.1z" fill="#ffc22e" />
                  </g>
                  <rect x="40" y="-10" width="240" height="210" fill="url(#lp-flag-shade)" />
                </g>
              </svg>
              <Loba mood="cheer" />
            </div>
          </div>
        </section>
      </main>

      <footer className="lp-foot">
        <div className="lp-wrap lp-foot-inner">
          <div className="lp-foot-brand">
            <span className="brand-word">loba</span>
            <p>{t('footTag')}</p>
          </div>
          <nav className="lp-foot-links" aria-label="Loba">
            {languages.map((l) => <button key={l.code} onClick={() => playWith(l.code)}>{langName(l.code)}</button>)}
            <a href="/privacy.html">{t('privacy')}</a>
          </nav>
          <UiSwitch />
        </div>
        <p className="lp-wrap lp-foot-base"><Lottie name="africa" className="lp-africa" /> {t('madeIn')}</p>
      </footer>
    </div>
  )
}
