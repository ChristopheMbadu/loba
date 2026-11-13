export type Mood = 'idle' | 'happy' | 'cheer' | 'sad' | 'think' | 'wave'

const GREY = '#98a2b8', GREY_D = '#77819a', BELLY = '#c9d0de', MASK = '#f6f8fc', BEAK = '#322e40', RED = '#f0524f', RED_D = '#c93a3c'

/**
 * Loba, the mascot: an African grey parrot (native to the Congo basin) with a flag-blue scarf.
 * Drawn in code so every pose stays consistent and themeable; animated with CSS.
 */
export function Loba({ mood = 'idle', className = '' }: { mood?: Mood; className?: string }) {
  const wingsUp = mood === 'cheer'
  const smile = mood === 'happy' || mood === 'cheer'
  return (
    <svg className={`loba loba-${mood} ${className}`} viewBox="0 0 200 200" aria-hidden="true">
      <ellipse cx="100" cy="190" rx="46" ry="6" fill="#000" opacity=".08" />
      <g className="loba-body">
        {/* tail */}
        <ellipse cx="148" cy="170" rx="10" ry="24" transform="rotate(-58 148 170)" fill={RED_D} />
        <ellipse cx="144" cy="176" rx="10" ry="24" transform="rotate(-74 144 176)" fill={RED} />
        {/* feet */}
        <ellipse cx="82" cy="184" rx="13" ry="7" fill="#4b465b" />
        <ellipse cx="118" cy="184" rx="13" ry="7" fill="#4b465b" />
        {/* wings (behind body when raised) */}
        {wingsUp && (
          <>
            <g className="loba-wing l" style={{ transformOrigin: '60px 118px' }}><ellipse cx="40" cy="96" rx="15" ry="34" transform="rotate(-38 40 96)" fill={GREY_D} /></g>
            <g className="loba-wing r" style={{ transformOrigin: '140px 118px' }}><ellipse cx="160" cy="96" rx="15" ry="34" transform="rotate(38 160 96)" fill={GREY_D} /></g>
          </>
        )}
        {mood === 'wave' && <g className="loba-wing r" style={{ transformOrigin: '140px 118px' }}><ellipse cx="160" cy="96" rx="15" ry="34" transform="rotate(38 160 96)" fill={GREY_D} /></g>}
        {/* body */}
        <ellipse cx="100" cy="130" rx="52" ry="56" fill={GREY} />
        <ellipse cx="100" cy="140" rx="35" ry="40" fill={BELLY} />
        <path d="M86 128q7 6 14 0m0 0q7 6 14 0M79 144q7 6 14 0m0 0q7 6 14 0m0 0q7 6 14 0M86 160q7 6 14 0m0 0q7 6 14 0" stroke={GREY} strokeWidth="3" fill="none" strokeLinecap="round" opacity=".7" />
        {!wingsUp && <ellipse cx="52" cy="134" rx="15" ry="34" transform="rotate(10 52 134)" fill={GREY_D} />}
        {!wingsUp && mood !== 'wave' && <ellipse cx="148" cy="134" rx="15" ry="34" transform="rotate(-10 148 134)" fill={GREY_D} />}
        {/* head */}
        <g className="loba-head">
          <path d="M84 34q4-12 10-4 5-12 10 0 7-8 10 6z" fill={GREY_D} />
          <circle cx="100" cy="74" r="46" fill={GREY} />
          <ellipse cx="80" cy="72" rx="23" ry="25" fill={MASK} />
          <ellipse cx="120" cy="72" rx="23" ry="25" fill={MASK} />
          <circle cx="66" cy="88" r="7" fill="#ff9aa8" opacity=".55" />
          <circle cx="134" cy="88" r="7" fill="#ff9aa8" opacity=".55" />
          {smile ? (
            <path d="M72 74q10-14 20 0M108 74q10-14 20 0" stroke={BEAK} strokeWidth="5.5" fill="none" strokeLinecap="round" />
          ) : (
            <g className="loba-eyes">
              <circle cx={mood === 'think' ? 85 : 82} cy={mood === 'think' ? 67 : 71} r="9.5" fill={BEAK} />
              <circle cx={mood === 'think' ? 121 : 118} cy={mood === 'think' ? 67 : 71} r="9.5" fill={BEAK} />
              <circle cx={mood === 'think' ? 88 : 85} cy={mood === 'think' ? 63 : 67} r="3.2" fill="#fff" />
              <circle cx={mood === 'think' ? 124 : 121} cy={mood === 'think' ? 63 : 67} r="3.2" fill="#fff" />
            </g>
          )}
          {mood === 'sad' && <path d="M68 58l22-8M132 58l-22-8" stroke={BEAK} strokeWidth="4.5" strokeLinecap="round" />}
          {mood === 'sad' && <path d="M133 86q5 8 0 12-5-4 0-12z" fill="#6cc7ff" />}
          {/* beak */}
          {(mood === 'cheer' || mood === 'wave') && <ellipse cx="100" cy="104" rx="8" ry="7" fill="#e8608a" />}
          <path d="M100 72c-12 0-17 14-11 24 4 7 11 11 11 11s7-4 11-11c6-10 1-24-11-24z" fill={BEAK} transform={mood === 'cheer' || mood === 'wave' ? 'translate(0 -5)' : undefined} />
          <path d="M94 80q3-4 8-3" stroke="#fff" strokeOpacity=".35" strokeWidth="3" fill="none" strokeLinecap="round" transform={mood === 'cheer' || mood === 'wave' ? 'translate(0 -5)' : undefined} />
        </g>
        {/* scarf */}
        <path d="M60 106q40 26 80 0l3 12q-43 28-86 0z" fill="#1c9bef" />
        <path d="M126 118l14 24-16-4-6 10-4-26z" fill="#1479bf" />
        <path d="m100 120 1.8 3.8 4.2.5-3.1 2.9.8 4.1-3.7-2-3.7 2 .8-4.1-3.1-2.9 4.2-.5z" fill="#ffc22e" />
      </g>
    </svg>
  )
}
