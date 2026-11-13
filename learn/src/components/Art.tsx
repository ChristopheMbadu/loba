import type { ReactNode } from 'react'

/**
 * Loba's illustrated icon set. One style throughout: flat colour, a darker
 * under-shade and a soft highlight — so nothing in the UI falls back to emoji.
 */
const ART = {
  flame: (
    <>
      <path d="M24 3c1.5 8 12 12.500 12 24.500C36 35.500 30.800 44 24 44S12 35.500 12 27.500c0-5.500 2.800-8.800 5-11 .6 3.600 2 5.800 4 7C20 15.500 21 9 24 3z" fill="#ff8a1f" />
      <path d="M24 23c.8 4 6 6.200 6 12a6 6 0 0 1-12 0c0-3 1.700-4.600 3-6 .2 1.600.9 2.400 1.800 3-.6-3.600.1-6.800 1.200-9z" fill="#ffd23e" />
    </>
  ),
  flameOff: (
    <>
      <path d="M24 3c1.500 8 12 12.500 12 24.500C36 35.500 30.800 44 24 44S12 35.500 12 27.500c0-5.500 2.800-8.800 5-11 .6 3.600 2 5.800 4 7C20 15.500 21 9 24 3z" fill="#c9cfda" />
      <path d="M24 23c.8 4 6 6.200 6 12a6 6 0 0 1-12 0c0-3 1.700-4.600 3-6 .2 1.600.9 2.400 1.800 3-.6-3.600.1-6.800 1.200-9z" fill="#e6e9ef" />
    </>
  ),
  gem: (
    <>
      <path d="M14 6h20l10 12-20 25L4 18z" fill="#1c9bef" />
      <path d="M14 6l10 12L34 6z" fill="#6cc4fa" />
      <path d="M4 18h40L24 43z" fill="#1479bf" />
      <path d="M24 18 14 6 4 18zM24 18l10-12 10 12z" fill="#3fb0f5" />
      <path d="M24 18v25L4 18z" fill="#1c9bef" />
      <path d="M16 9.500 12 14" stroke="#fff" strokeOpacity=".7" strokeWidth="2.500" strokeLinecap="round" />
    </>
  ),
  heart: (
    <>
      <path d="M24 42S5 30.500 5 17.500A10 10 0 0 1 24 12a10 10 0 0 1 19 5.500C43 30.500 24 42 24 42z" fill="#f0524f" />
      <path d="M24 42S5 30.500 5 17.500c0-1 .1-2 .4-3C8 26 24 36 24 36s16-10 18.600-21.500c.3 1 .4 2 .4 3C43 30.500 24 42 24 42z" fill="#c93a3c" />
      <path d="M14.500 12.500a5 5 0 0 0-4 4.500" stroke="#fff" strokeOpacity=".7" strokeWidth="3.200" strokeLinecap="round" fill="none" />
    </>
  ),
  heartOff: (
    <>
      <path d="M24 42S5 30.500 5 17.500A10 10 0 0 1 24 12a10 10 0 0 1 19 5.500C43 30.500 24 42 24 42z" fill="#d5dae3" />
      <path d="M24 42S5 30.500 5 17.500c0-1 .1-2 .4-3C8 26 24 36 24 36s16-10 18.600-21.500c.3 1 .4 2 .4 3C43 30.500 24 42 24 42z" fill="#b9c0cc" />
    </>
  ),
  bolt: (
    <>
      <path d="M27 3 9 27h11l-3 18 20-26H25z" fill="#ffc22e" />
      <path d="M20 27 17 45l20-26h-5L20 34z" fill="#e09f00" />
      <path d="M24 9l-8 11" stroke="#fff" strokeOpacity=".65" strokeWidth="3" strokeLinecap="round" />
    </>
  ),
  star: (
    <>
      <path d="m24 4 6 12.300 13.500 1.900-9.800 9.500 2.400 13.400L24 34.700 11.900 41.100l2.400-13.400-9.800-9.500L18 16.300z" fill="#ffc22e" />
      <path d="M24 34.700 11.900 41.100l2.400-13.400L24 30l9.700-2.300 2.400 13.400z" fill="#e09f00" />
      <path d="m20 15 2-4" stroke="#fff" strokeOpacity=".7" strokeWidth="3" strokeLinecap="round" />
    </>
  ),
  chest: (
    <>
      <rect x="5" y="20" width="38" height="22" rx="4" fill="#b96a2b" />
      <path d="M5 22a14 14 0 0 1 14-14h10a14 14 0 0 1 14 14v2H5z" fill="#d98b3d" />
      <rect x="5" y="21" width="38" height="5" fill="#ffc22e" />
      <rect x="8" y="8" width="5" height="34" rx="1" fill="#ffc22e" opacity=".9" />
      <rect x="35" y="8" width="5" height="34" rx="1" fill="#ffc22e" opacity=".9" />
      <rect x="19" y="18" width="10" height="13" rx="3" fill="#ffe08a" stroke="#e09f00" strokeWidth="2" />
      <circle cx="24" cy="24" r="2" fill="#b96a2b" />
      <path d="M14 13q4-3 9-3" stroke="#fff" strokeOpacity=".45" strokeWidth="2.500" strokeLinecap="round" fill="none" />
    </>
  ),
  chestOpen: (
    <>
      <path d="M6 17 10 4h28l4 13z" fill="#d98b3d" />
      <path d="M6 17 10 4h28l4 13z" fill="#000" opacity=".12" />
      <rect x="5" y="20" width="38" height="22" rx="4" fill="#b96a2b" />
      <path d="M8 21h32l-3-6H11z" fill="#ffe08a" />
      <circle cx="18" cy="17" r="4" fill="#ffc22e" /><circle cx="26" cy="15" r="4.500" fill="#ffd23e" /><circle cx="32" cy="18" r="3.500" fill="#ffc22e" />
      <rect x="5" y="21" width="38" height="5" fill="#ffc22e" />
      <rect x="8" y="21" width="5" height="21" fill="#ffc22e" opacity=".9" /><rect x="35" y="21" width="5" height="21" fill="#ffc22e" opacity=".9" />
    </>
  ),
  chestLocked: (
    <>
      <rect x="5" y="20" width="38" height="22" rx="4" fill="#b4bbc8" />
      <path d="M5 22a14 14 0 0 1 14-14h10a14 14 0 0 1 14 14v2H5z" fill="#c9cfda" />
      <rect x="5" y="21" width="38" height="5" fill="#dfe3ea" />
      <rect x="19" y="18" width="10" height="13" rx="3" fill="#eef0f4" stroke="#b4bbc8" strokeWidth="2" />
    </>
  ),
  trophy: (
    <>
      <path d="M13 6h22v12a11 11 0 0 1-22 0z" fill="#ffc22e" />
      <path d="M13 9H6v4a8 8 0 0 0 8 8M35 9h7v4a8 8 0 0 1-8 8" stroke="#e09f00" strokeWidth="4" fill="none" />
      <rect x="21" y="28" width="6" height="8" fill="#e09f00" /><rect x="14" y="35" width="20" height="7" rx="2.500" fill="#b96a2b" />
      <path d="M18 10v8" stroke="#fff" strokeOpacity=".6" strokeWidth="3" strokeLinecap="round" />
    </>
  ),
  target: (
    <>
      <circle cx="22" cy="26" r="18" fill="#f0524f" /><circle cx="22" cy="26" r="12.500" fill="#fff" /><circle cx="22" cy="26" r="7" fill="#f0524f" />
      <path d="M22 26 40 8" stroke="#322e40" strokeWidth="3.500" strokeLinecap="round" /><path d="M36 4v8h8l-4 4h-8v-8z" fill="#ffc22e" />
    </>
  ),
  book: (
    <>
      <path d="M6 9a3 3 0 0 1 3-3h13v34H9a3 3 0 0 1-3-3z" fill="#1c9bef" /><path d="M42 9a3 3 0 0 0-3-3H26v34h13a3 3 0 0 0 3-3z" fill="#3fb0f5" />
      <path d="M6 34v3a3 3 0 0 0 3 3h13v-3zM42 34v3a3 3 0 0 1-3 3H26v-3z" fill="#1479bf" />
      <path d="M11 14h7M11 20h7M30 14h8M30 20h8M30 26h5" stroke="#fff" strokeOpacity=".8" strokeWidth="2.500" strokeLinecap="round" />
    </>
  ),
  headphones: (
    <>
      <path d="M8 28v-6a16 16 0 0 1 32 0v6" stroke="#6d3fd8" strokeWidth="5" fill="none" strokeLinecap="round" />
      <rect x="4" y="24" width="11" height="18" rx="5" fill="#8b5cf6" /><rect x="33" y="24" width="11" height="18" rx="5" fill="#8b5cf6" />
      <path d="M8 29v6M37 29v6" stroke="#fff" strokeOpacity=".5" strokeWidth="2.500" strokeLinecap="round" />
    </>
  ),
  dumbbell: (
    <>
      <rect x="14" y="21" width="20" height="6" fill="#77819a" />
      <rect x="8" y="11" width="8" height="26" rx="3" fill="#ff8a1f" /><rect x="32" y="11" width="8" height="26" rx="3" fill="#ff8a1f" />
      <rect x="2" y="17" width="6" height="14" rx="2.500" fill="#d36a0a" /><rect x="40" y="17" width="6" height="14" rx="2.500" fill="#d36a0a" />
      <path d="M11 15v8M35 15v8" stroke="#fff" strokeOpacity=".5" strokeWidth="2.500" strokeLinecap="round" />
    </>
  ),
  freeze: (
    <>
      <path d="M8 16 24 8l16 8v18l-16 8-16-8z" fill="#8fd8ff" /><path d="M8 16l16 8v18L8 34z" fill="#5fbdf2" /><path d="M40 16l-16 8v18l16-8z" fill="#3fa5e2" />
      <path d="M14 15l8-4" stroke="#fff" strokeWidth="3" strokeLinecap="round" /><path d="M13 23v7" stroke="#fff" strokeOpacity=".6" strokeWidth="2.500" strokeLinecap="round" />
    </>
  ),
  cards: (
    <>
      <rect x="6" y="12" width="24" height="30" rx="4" fill="#1e8f58" transform="rotate(-10 18 27)" />
      <rect x="18" y="8" width="24" height="30" rx="4" fill="#2bb673" transform="rotate(8 30 23)" />
      <path d="m26 22 2.300 4.700 5.200.700-3.800 3.700.9 5.200-4.600-2.500" fill="#fff" opacity=".9" transform="rotate(8 30 23)" />
    </>
  ),
  medal: (
    <>
      <path d="M14 4h8l4 14h-8zM34 4h-8l-4 14h8z" fill="#f0524f" /><path d="M22 4h4l-2 7z" fill="#c93a3c" />
      <circle cx="24" cy="30" r="13" fill="#ffc22e" /><circle cx="24" cy="30" r="8.500" fill="#ffd96a" stroke="#e09f00" strokeWidth="2" />
    </>
  ),
  word: (
    <>
      <path d="M6 12a6 6 0 0 1 6-6h24a6 6 0 0 1 6 6v16a6 6 0 0 1-6 6H22l-10 9v-9a6 6 0 0 1-6-6z" fill="#2bb673" />
      <path d="M14 15h20M14 22h20M14 29h8" stroke="#fff" strokeWidth="3" strokeLinecap="round" opacity=".9" />
    </>
  ),
  // navigation
  navLearn: (
    <>
      <path d="M24 4 3 22h6v18a3 3 0 0 0 3 3h24a3 3 0 0 0 3-3V22h6z" fill="#ffc22e" />
      <path d="M24 4 3 22h42z" fill="#f0524f" /><path d="M24 4 3 22h8L24 11l13 11h8z" fill="#c93a3c" />
      <rect x="19" y="28" width="10" height="15" rx="2" fill="#b96a2b" />
    </>
  ),
  navShop: (
    <>
      <path d="M6 18h36l-3 22a3 3 0 0 1-3 3H12a3 3 0 0 1-3-3z" fill="#8b5cf6" /><path d="M6 18h36l-1 7H7z" fill="#6d3fd8" />
      <path d="M16 20v-6a8 8 0 0 1 16 0v6" stroke="#322e40" strokeWidth="3.500" fill="none" strokeLinecap="round" />
      <path d="m24 27 1.800 3.700 4.100.6-3 2.900.7 4.100-3.600-1.900-3.600 1.900.7-4.100-3-2.900 4.100-.6z" fill="#ffc22e" />
    </>
  ),
  navProfile: (
    <>
      <circle cx="24" cy="24" r="20" fill="#98a2b8" /><ellipse cx="16.500" cy="22" rx="8" ry="9" fill="#f6f8fc" /><ellipse cx="31.500" cy="22" rx="8" ry="9" fill="#f6f8fc" />
      <circle cx="17.500" cy="22" r="3.500" fill="#322e40" /><circle cx="30.500" cy="22" r="3.500" fill="#322e40" />
      <path d="M24 22c-4 0-6 5-4 8.500 1.500 2.500 4 4 4 4s2.500-1.500 4-4c2-3.500 0-8.500-4-8.500z" fill="#322e40" />
    </>
  ),
} satisfies Record<string, ReactNode>

export type ArtName = keyof typeof ART

export function Art({ name, size, className = '' }: { name: ArtName; size?: number; className?: string }) {
  return (
    <svg className={`art ${className}`} viewBox="0 0 48 48" width={size} height={size} aria-hidden="true">
      {ART[name]}
    </svg>
  )
}

/* Scenery beside the path — a nod to the Congo river basin. */
export function Scenery({ kind, className = '' }: { kind: number; className?: string }) {
  const scenes = [
    // palm
    <>
      <ellipse cx="60" cy="112" rx="34" ry="6" fill="#000" opacity=".07" />
      <path d="M56 110c2-26 0-44 6-62l8 2c-6 18-2 36-4 60z" fill="#b96a2b" /><path d="M58 96h9M59 82h8M61 68h8" stroke="#8f4f1c" strokeWidth="3" strokeLinecap="round" />
      <path d="M66 50C50 30 30 30 16 40c16-2 30 2 44 14zM66 50C60 26 44 14 26 14c16 8 26 20 34 38zM66 50c4-24 22-34 40-30-16 4-26 14-36 32zM66 50c16-12 34-10 46 2-16-4-30-2-44 4zM66 50c8 4 20 14 22 30-8-12-16-18-26-24z" fill="#2bb673" />
      <path d="M66 50C50 30 30 30 16 40c16-2 30 2 44 14zM66 50c16-12 34-10 46 2-16-4-30-2-44 4z" fill="#1e8f58" />
      <circle cx="62" cy="54" r="5" fill="#8f4f1c" /><circle cx="70" cy="56" r="5" fill="#b96a2b" />
    </>,
    // drum
    <>
      <ellipse cx="60" cy="112" rx="32" ry="6" fill="#000" opacity=".07" />
      <path d="M30 38h60l-10 70a4 4 0 0 1-4 3H44a4 4 0 0 1-4-3z" fill="#d98b3d" />
      <path d="M33 58h54l-2 12H35zM38 88h44l-1 8H39z" fill="#f0524f" /><path d="M36 64l6-6 6 6 6-6 6 6 6-6 6 6 6-6 6 6" stroke="#ffc22e" strokeWidth="3" fill="none" strokeLinejoin="round" />
      <ellipse cx="60" cy="38" rx="30" ry="9" fill="#ffe9c2" stroke="#b96a2b" strokeWidth="4" />
      <path d="M92 16 70 34M100 30 76 38" stroke="#8f4f1c" strokeWidth="5" strokeLinecap="round" /><circle cx="92" cy="16" r="6" fill="#b96a2b" /><circle cx="100" cy="30" r="6" fill="#b96a2b" />
    </>,
    // hut
    <>
      <ellipse cx="60" cy="112" rx="42" ry="6" fill="#000" opacity=".07" />
      <rect x="26" y="62" width="68" height="48" rx="4" fill="#ffd99a" /><path d="M26 76h68M26 92h68" stroke="#e8b86a" strokeWidth="3" />
      <path d="M60 14 10 66h100z" fill="#b96a2b" /><path d="M60 14 10 66h14L60 28l36 38h14z" fill="#8f4f1c" />
      <path d="M50 110V86a10 10 0 0 1 20 0v24z" fill="#8f4f1c" /><circle cx="84" cy="80" r="6" fill="#6cc4fa" />
    </>,
  ]
  return (
    <svg className={`scenery ${className}`} viewBox="0 0 120 120" aria-hidden="true">
      {scenes[kind % scenes.length]}
    </svg>
  )
}
