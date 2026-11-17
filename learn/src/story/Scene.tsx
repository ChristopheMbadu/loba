import { useState, type CSSProperties } from 'react'
import { sfx } from '../audio/sfx'

/**
 * The world of the tale, drawn once and staged by CSS: `.tale[data-page]` decides the time of
 * day, where the camera stands and what each character is doing (see styles/story.css).
 * Left of the world is the river bank where the hen feeds; further along is the forest path
 * where the crocodile meets the lizard.
 */
const G = '#2bb673', G_D = '#1e8f58', BELLY = '#cfeeb4'
const WORLD = 1700

function Croc() {
  return (
    <g className="croc-art">
      <path d="M-150 6q42-34 84-24v42q-46 8-84-18z" fill={G_D} />
      <rect x="-72" y="24" width="24" height="30" rx="10" fill={G_D} />
      <rect x="6" y="24" width="24" height="30" rx="10" fill={G_D} />
      <ellipse cx="-20" cy="5" rx="78" ry="35" fill={G} />
      <ellipse cx="-20" cy="22" rx="60" ry="15" fill={BELLY} />
      <path d="M-92-18l10-16 10 12 10-16 10 13 10-16 10 13 10-15 10 13 10-13 8 14z" fill={G_D} />
      {/* open mouth, lower jaw, then the upper jaw that swings */}
      <path className="croc-mouth" d="M40-4h100v12H40z" fill="#e8608a" />
      <path d="M34 2h108q14 2 12 15-2 11-16 11H36z" fill={G_D} />
      <path d="M64 2l6-8 6 8zM86 2l6-8 6 8zM108 2l6-8 6 8zM130 2l6-8 6 8z" fill="#fff" />
      <g className="croc-jaw">
        <path d="M28-20q10-18 30-13l8 7h72q16 0 16 13v15H34z" fill={G} />
        <path d="M54 2l6 9 6-9zM76 2l6 9 6-9zM98 2l6 9 6-9zM120 2l6 9 6-9z" fill="#fff" />
        <circle cx="54" cy="-26" r="13" fill={G} />
        <circle cx="55" cy="-27" r="8" fill="#fff" />
        <circle className="croc-pupil" cx="57" cy="-27" r="4" fill="#322e40" />
        <circle cx="142" cy="-12" r="2.6" fill={G_D} />
        <path className="croc-smile" d="M96 -1q22 9 46 -3" stroke={G_D} strokeWidth="3" fill="none" strokeLinecap="round" />
      </g>
    </g>
  )
}

function Hen() {
  return (
    <g className="hen-art">
      <path d="M-8-18V0m-7 0h12M8-18V0m-7 0h12" stroke="#e09f00" strokeWidth="4" strokeLinecap="round" fill="none" />
      <path d="M26-58q22-22 30-6-16-2-18 10 18-6 20 8-18-4-26 6z" fill="#8f4f1c" />
      <ellipse cx="0" cy="-42" rx="34" ry="28" fill="#c7702f" />
      <ellipse cx="-4" cy="-30" rx="24" ry="14" fill="#e39a55" />
      <g className="hen-wing"><ellipse cx="8" cy="-42" rx="19" ry="14" fill="#8f4f1c" /></g>
      <g className="hen-head">
        <path d="M-12-52q-14-6-14-24h18z" fill="#c7702f" />
        <path d="M-40-92q4-10 10-2 4-10 10 0 6-6 8 6z" fill="#ef4e4e" />
        <circle cx="-28" cy="-78" r="15" fill="#c7702f" />
        <path d="M-41-83l-16 6 16 6z" fill="#ffc22e" />
        <ellipse cx="-38" cy="-65" rx="5" ry="7" fill="#ef4e4e" />
        <circle cx="-32" cy="-81" r="3.2" fill="#322e40" />
      </g>
    </g>
  )
}

function Lizard() {
  return (
    <g className="lizard-art">
      <path d="M22-2q46-2 54 20t-22 26" stroke="#ff8a1f" strokeWidth="11" fill="none" strokeLinecap="round" />
      <path d="M-36 4l-12 14M-8 6l-4 16M10 6l8 14M-50 0l-14 10" stroke="#d36a0a" strokeWidth="6" strokeLinecap="round" />
      <ellipse cx="-12" cy="-4" rx="42" ry="13" fill="#ff8a1f" />
      <ellipse cx="-12" cy="2" rx="34" ry="6" fill="#ffd9a0" />
      <circle cx="-26" cy="-9" r="3" fill="#d36a0a" /><circle cx="-8" cy="-11" r="3.4" fill="#d36a0a" /><circle cx="10" cy="-9" r="3" fill="#d36a0a" />
      <g className="lizard-head">
        <path className="lizard-tongue" d="M-82-10h-16l-6-4m6 4-6 4" stroke="#ef4e4e" strokeWidth="2.5" fill="none" strokeLinecap="round" />
        <ellipse cx="-62" cy="-10" rx="20" ry="12" fill="#ff8a1f" />
        <circle cx="-66" cy="-15" r="5" fill="#fff" /><circle cx="-67" cy="-15" r="2.4" fill="#322e40" />
      </g>
    </g>
  )
}

const EGGS = [
  { fill: '#dff1ff', spot: '#b9dcf5' },
  { fill: '#f2ecd6', spot: '#d9cfa6' },
  { fill: '#ffe2b8', spot: '#ffbf75' },
  { fill: '#fff6e4', spot: '#f0d9ae' },
  { fill: '#e2f4d0', spot: '#bfe0a0' },
]

export function Scene({ shout }: { shout: string }) {
  const [taps, setTaps] = useState({ hen: 0, croc: 0, sun: 0, lizard: 0 })
  const [egg, setEgg] = useState<[index: number, n: number]>([-1, 0])
  const tap = (who: keyof typeof taps, cue: Parameters<typeof sfx>[0], n = 1) => {
    sfx(cue, n)
    setTaps((t) => ({ ...t, [who]: t[who] + 1 }))
  }

  return (
    <svg className="scene" viewBox="0 0 800 600" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
      {/* sky: four times of day, cross-faded */}
      <rect className="sky dawn" width="800" height="600" fill="#ffe3b8" />
      <rect className="sky day" width="800" height="600" fill="#cfeafc" />
      <rect className="sky dusk" width="800" height="600" fill="#ffb37a" />
      <rect className="sky night" width="800" height="600" fill="#1d2256" />
      <g className="stars">
        {Array.from({ length: 34 }, (_, i) => (
          <circle key={i} cx={(i * 197) % 800} cy={(i * 83) % 260} r={i % 5 === 0 ? 2.4 : 1.4} fill="#fff" style={{ animationDelay: `${(i % 7) * 0.4}s` }} />
        ))}
      </g>
      <g className="sun" key={`sun${taps.sun}`} onClick={() => tap('sun', 'tile', taps.sun)}>
        <g className="rays">
          {Array.from({ length: 12 }, (_, i) => (
            <path key={i} d="M0-58v-22" stroke="#ffd96a" strokeWidth="7" strokeLinecap="round" transform={`rotate(${i * 30})`} />
          ))}
        </g>
        <circle r="50" fill="#fff1c2" opacity=".8" />
        <circle r="38" fill="#ffc22e" />
      </g>
      <g className="moon">
        <circle r="34" fill="#fff6d6" />
        <circle cx="12" cy="-8" r="30" fill="#1d2256" />
      </g>
      <g className="clouds">
        <path d="M120 120q10-26 38-18 16-22 42-6 26-4 28 24z" fill="#fff" opacity=".85" />
        <path d="M520 80q8-20 30-14 14-18 36-4 20-2 22 18z" fill="#fff" opacity=".7" />
      </g>

      <g className="birds">
        <path d="M0 0q8-10 16 0 8-10 16 0" /><path d="M46 22q6-8 12 0 6-8 12 0" /><path d="M-30 30q6-8 12 0 6-8 12 0" />
      </g>

      {/* the far bank: hills, then rainforest, drifting slowly as the camera moves */}
      <g className="cam far">
        <path className="hills" d="M-40 320q110-120 260-50 90-80 230-20 130-100 290-20 110-70 240-10 100-60 200 0V360H-40z" fill="#8fd6b4" />
        <path d={`M-40 300c30-34 60-46 96-30 20-36 62-44 92-18 26-30 70-30 96 0 30-26 74-22 96 8 26-22 66-18 88 8 30-30 78-26 100 4 26-22 66-22 88 4 26-18 58-14 80 12 30-18 66-14 92 18 30-34 72-40 104-14 26-30 70-30 96 0 30-26 74-22 96 8 26-22 66-18 88 8 30-30 78-26 100 4V360H-40z`} fill={G} />
        <path d="M-40 316c60-18 130-14 200 0s160 14 250 0 180-14 250 0 140 14 210 0 150-12 230 0V360H-40z" fill={G_D} />
        <g transform="translate(90 196)">
          <path d="M56 110c2-26 0-44 6-62l8 2c-6 18-2 36-4 60z" fill="#b96a2b" />
          <path d="M66 50C50 30 30 30 16 40c16-2 30 2 44 14zM66 50C60 26 44 14 26 14c16 8 26 20 34 38zM66 50c4-24 22-34 40-30-16 4-26 14-36 32zM66 50c16-12 34-10 46 2-16-4-30-2-44 4z" fill={G_D} />
        </g>
      </g>

      {/* the river, the crocodile in it, then the near bank and everyone on it */}
      <g className="cam near">
        <rect x="-40" y="340" width={WORLD + 80} height="140" fill="#1c9bef" />
        <g className="waves">
          <path d={`M-200 372${'q25-8 50 0t50 0'.repeat(20)}`} stroke="#6cc4fa" strokeWidth="4" fill="none" strokeLinecap="round" />
          <path d={`M-170 404${'q25-8 50 0t50 0'.repeat(20)}`} stroke="#6cc4fa" strokeWidth="4" fill="none" strokeLinecap="round" opacity=".7" />
        </g>
        <path className="glitter" d="M560 366h44M574 382h22M150 360h30" stroke="#ffe08a" strokeWidth="4" strokeLinecap="round" />
        <g className="far-pirogue">
          <path d="M0 0q40 9 80 0l-5 9q-35 9-70 0z" fill="#8f4f1c" />
          <path d="M34-2v-16M30-18a5 5 0 1 0 8 0" stroke="#5a3a22" strokeWidth="3" fill="#5a3a22" strokeLinecap="round" />
          <path d="M36-10l18 16" stroke="#b96a2b" strokeWidth="2.5" strokeLinecap="round" />
        </g>
        {[[120, 424], [470, 396], [1040, 392], [1330, 420]].map(([x, y], i) => (
          <g key={i} className="lily" transform={`translate(${x} ${y})`} style={{ animationDelay: `${i * 0.6}s` }}>
            <ellipse rx="22" ry="8" fill="#1e8f58" /><path d="M0 0l14-6" stroke="#1c9bef" strokeWidth="3" />
            <path d="M-6-2q-6-10 2-12 6 4 4 12zM2-3q2-12 10-10 2 8-6 12z" fill="#ff9aa8" /><circle cx="2" cy="-4" r="3" fill="#ffc22e" />
          </g>
        ))}

        <g className="croc swim" key={`croc${taps.croc}`} onClick={() => tap('croc', 'drum', taps.croc)}>
          <Croc />
          <text className="croc-mark" x="70" y="-62">?</text>
        </g>
        <g className="ripples"><ellipse rx="130" ry="12" /><ellipse rx="170" ry="16" /></g>
        <path d={`M-40 436${'q25-9 50 0t50 0'.repeat(18)}V490H-40z`} fill="#1c9bef" />
        <path className="waves front" d={`M-200 440${'q25-8 50 0t50 0'.repeat(20)}`} stroke="#6cc4fa" strokeWidth="4" fill="none" strokeLinecap="round" />

        <path d={`M-40 474q120-22 240-6t260-8 240 6 250-8 240 8 240-6 240 4V620H-40z`} fill="#f3d9a4" />
        <path d={`M-40 474q120-22 240-6t260-8 240 6 250-8 240 8 240-6 240 4v14q-120-10-240-4t-240-8-250 8-240-6-260 8-240 6z`} fill="#e6bf7a" />
        <path d="M60 520q8-20 4-34 12 12 10 34zM86 520q10-16 22-22-6 14-8 22zM980 536q8-20 4-34 12 12 10 34zM1006 536q10-16 22-22-6 14-8 22zM1500 520q8-20 4-34 12 12 10 34z" fill={G} />

        {[[36, 478], [70, 470], [640, 470], [900, 474], [1150, 470], [1560, 470]].map(([x, y], i) => (
          <g key={i} className="reed" transform={`translate(${x} ${y})`} style={{ animationDelay: `${i * 0.5}s` }}>
            <path d="M0 0q-4-40 2-78M10 0q6-30 0-60M-10 0q-6-24-2-48" stroke={G_D} strokeWidth="4" fill="none" strokeLinecap="round" />
            <ellipse cx="2" cy="-84" rx="5" ry="13" fill="#8f4f1c" /><ellipse cx="10" cy="-66" rx="4.5" ry="11" fill="#8f4f1c" /><ellipse cx="-12" cy="-54" rx="4" ry="10" fill="#8f4f1c" />
          </g>
        ))}
        {[[250, 548], [430, 574], [760, 560], [1120, 580], [1420, 552]].map(([x, y], i) => (
          <g key={i} transform={`translate(${x} ${y})`}>
            <path d="M0 0v-14M-9 2v-10M9 2v-12" stroke={G} strokeWidth="3" strokeLinecap="round" />
            <circle cx="0" cy="-17" r="5" fill="#ef4e4e" /><circle cx="-9" cy="-10" r="4" fill="#ffc22e" /><circle cx="9" cy="-13" r="4.5" fill="#ff8a1f" />
          </g>
        ))}

        {/* the hen's side of the bank */}
        <g transform="translate(676 372) scale(1.25)">
          <path d="M56 110c2-26 0-44 6-62l8 2c-6 18-2 36-4 60z" fill="#b96a2b" /><path d="M58 96h9M59 82h8M61 68h8" stroke="#8f4f1c" strokeWidth="3" strokeLinecap="round" />
          <g className="palm-sway">
            <path d="M66 50C50 30 30 30 16 40c16-2 30 2 44 14zM66 50C60 26 44 14 26 14c16 8 26 20 34 38zM66 50c4-24 22-34 40-30-16 4-26 14-36 32zM66 50c16-12 34-10 46 2-16-4-30-2-44 4zM66 50c8 4 20 14 22 30-8-12-16-18-26-24z" fill={G} />
            <path d="M66 50C50 30 30 30 16 40c16-2 30 2 44 14zM66 50c16-12 34-10 46 2-16-4-30-2-44 4z" fill={G_D} />
          </g>
        </g>
        <g className="hen" key={`hen${taps.hen}`} onClick={() => tap('hen', 'pop')}>
          <g transform="scale(1.45)">
            <ellipse cx="0" cy="2" rx="36" ry="6" fill="#000" opacity=".1" />
            <Hen />
            <g className="hen-zzz"><text x="-64" y="-104">z</text><text x="-80" y="-124">z</text></g>
          </g>
        </g>
        <text className="shout" x="400" y="318">{shout}</text>

        {/* further along: the forest path, the rock and the lizard */}
        <g transform="translate(1340 250)">
          <path d="M0 240c6-80-4-150 14-226h44c-10 80 4 150 2 226z" fill="#8f4f1c" />
          <path d="M-110 30c-10-60 40-96 96-80 30-40 100-40 126 0 56-10 100 30 86 84-90 30-220 30-308-4z" fill={G_D} />
          <path d="M-80 10c0-40 40-64 84-52 30-30 84-28 104 4 44-6 74 22 66 60-76 22-180 20-254-12z" fill={G} />
        </g>
        <path d="M1180 520q-6-50 44-58 60-10 84 22 22 4 20 36z" fill="#b9b2a8" />
        <path d="M1180 520q-6-50 44-58 20-4 40 0-52 8-56 58z" fill="#d2ccc2" />
        <g className="lizard" key={`liz${taps.lizard}`} onClick={() => tap('lizard', 'tile', taps.lizard + 3)}>
          <Lizard />
        </g>
        <g className="croc walk" onClick={() => tap('croc', 'drum', taps.croc)}>
          <ellipse cx="-10" cy="54" rx="120" ry="8" fill="#000" opacity=".1" />
          <Croc />
        </g>
        <g className="eggs">
          {EGGS.map((e, i) => (
            <g
              key={i}
              className={`egg ${egg[0] === i ? 'tapped' : ''}`}
              style={{ '--i': i } as CSSProperties}
              transform={`translate(${880 + i * 62} 556)`}
              onClick={() => {
                sfx('tile', i)
                setEgg([i, egg[1] + 1])
              }}
            >
              <g key={egg[0] === i ? egg[1] : 0}>
                <ellipse cx="0" cy="-17" rx="15" ry="19" fill={e.fill} />
                <circle cx="-5" cy="-22" r="2.6" fill={e.spot} /><circle cx="5" cy="-12" r="3" fill={e.spot} /><circle cx="3" cy="-26" r="2" fill={e.spot} />
              </g>
            </g>
          ))}
        </g>
      </g>

      <g className="cam front">
        <path d="M-60 620q20-70 10-110 30 40 34 110zM10 620q30-50 60-70-20 40-24 70zM760 620q20-70 10-110 30 40 34 110zM950 620q20-70 10-110 30 40 34 110zM1020 620q30-50 60-70-20 40-24 70zM1760 620q20-70 10-110 30 40 34 110z" fill={G_D} />
      </g>

      {/* evening and night wash over everything; the fireflies stay bright */}
      <rect className="wash dusk" width="800" height="600" fill="#ff7a2e" />
      <rect className="wash night" width="800" height="600" fill="#141a52" />
      <g className="fireflies">
        {Array.from({ length: 14 }, (_, i) => (
          <circle key={i} cx={60 + ((i * 131) % 700)} cy={300 + ((i * 67) % 240)} r="3.2" fill="#ffe66b" style={{ animationDelay: `${(i % 5) * 0.7}s`, animationDuration: `${3.4 + (i % 4) * 0.8}s` }} />
        ))}
      </g>
    </svg>
  )
}
