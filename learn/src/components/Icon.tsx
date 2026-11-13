const PATHS = {
  home: <path d="M3.5 10.6 12 3.5l8.5 7.1V20a1 1 0 0 1-1 1h-5v-6h-5v6h-5a1 1 0 0 1-1-1z" />,
  practice: <path d="M6.5 6.5h-2a1 1 0 0 0-1 1v9a1 1 0 0 0 1 1h2m11-11h2a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1h-2M6.5 4.5v15m11-15v15M6.5 12h11" />,
  quests: <><circle cx="12" cy="12" r="8.5" /><circle cx="12" cy="12" r="4.5" /><circle cx="12" cy="12" r="1" fill="currentColor" /></>,
  shop: <path d="M4 9.5 5.5 4h13L20 9.5M4 9.5h16M4 9.5a2.7 2.7 0 0 0 5.3 0 2.7 2.7 0 0 0 5.4 0 2.7 2.7 0 0 0 5.3 0M5 12v8h14v-8M10 20v-4.5h4V20" />,
  profile: <><circle cx="12" cy="8" r="4" /><path d="M4 20.5a8 8 0 0 1 16 0" /></>,
  close: <path d="M6 6l12 12M18 6 6 18" />,
  check: <path d="m5 12.5 4.5 4.5L19 7.5" />,
  x: <path d="M7 7l10 10M17 7 7 17" />,
  lock: <><rect x="5" y="10.5" width="14" height="10" rx="2.5" /><path d="M8 10.5V8a4 4 0 0 1 8 0v2.5" /></>,
  speaker: <path d="M4 9.5v5h3.5L13 19V5L7.5 9.5zM16.5 8.5a5 5 0 0 1 0 7M19 6a8.5 8.5 0 0 1 0 12" />,
  book: <path d="M4 5.5A1.5 1.5 0 0 1 5.5 4H11v16H5.5A1.5 1.5 0 0 1 4 18.5zM20 5.5A1.5 1.5 0 0 0 18.5 4H13v16h5.5a1.5 1.5 0 0 0 1.5-1.5z" />,
  clock: <><circle cx="12" cy="12" r="8.5" /><path d="M12 7.5V12l3 2" /></>,
  bolt: <path d="M13 3 5 13.5h6L10 21l8-10.5h-6z" fill="currentColor" />,
} as const

export type IconName = keyof typeof PATHS

export function Icon({ name, label }: { name: IconName; label?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2.4} strokeLinecap="round" strokeLinejoin="round" role={label ? 'img' : undefined} aria-label={label} aria-hidden={label ? undefined : true}>
      {PATHS[name]}
    </svg>
  )
}

/* Filled, colored glyphs for game currencies. */
export const Flame = ({ on = true }: { on?: boolean }) => (
  <svg viewBox="0 0 24 24" aria-hidden="true">
    <path d="M12 2.5c.6 3.2 5.5 5.6 5.5 11.2A5.6 5.6 0 0 1 12 21.5a5.6 5.6 0 0 1-5.5-5.8c0-2.6 1.3-4 2.3-5 .3 1.6.9 2.6 1.9 3.2C10 10 10.5 5.5 12 2.5z" fill={on ? '#ff8a1f' : 'currentColor'} />
    <path d="M12 12.5c.3 1.6 2.6 2.7 2.6 5.2a2.6 2.6 0 0 1-5.2 0c0-1.4.8-2 1.3-2.6.1.7.4 1 .8 1.3-.3-1.6 0-3 .5-3.9z" fill={on ? '#ffc22e' : 'var(--surface)'} />
  </svg>
)

export const Gem = () => (
  <svg viewBox="0 0 24 24" aria-hidden="true">
    <path d="M7 3.5h10l4.5 5.5L12 21 2.5 9z" fill="#1c9bef" />
    <path d="M7 3.5 12 9l5-5.5M2.5 9h19M12 9v12" stroke="#7cc8f7" strokeWidth="1.4" fill="none" strokeLinejoin="round" />
  </svg>
)

export const Heart = ({ on = true }: { on?: boolean }) => (
  <svg viewBox="0 0 24 24" aria-hidden="true">
    <path d="M12 20.5S3 15 3 8.8A4.8 4.8 0 0 1 12 6.4a4.8 4.8 0 0 1 9 2.4C21 15 12 20.5 12 20.5z" fill={on ? '#ef4e4e' : 'currentColor'} />
    <path d="M7.2 7.5a2.2 2.2 0 0 0-1.7 2" stroke="#fff" strokeOpacity=".55" strokeWidth="1.8" strokeLinecap="round" fill="none" />
  </svg>
)

export const Star = () => (
  <svg viewBox="0 0 24 24" aria-hidden="true">
    <path d="m12 3 2.7 5.6 6.1.8-4.4 4.3 1 6.1L12 17l-5.4 2.8 1-6.1-4.4-4.3 6.1-.8z" fill="currentColor" />
  </svg>
)
