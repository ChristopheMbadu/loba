import { useEffect, useState } from 'react'
import { FlameSticker, SparkleTrioSticker, HeartSticker } from 'stick-it-up'

const stickers = { flame: FlameSticker, sparkle: SparkleTrioSticker, heart: HeartSticker }

/** Decorative rewards: the adjacent text carries their meaning for screen readers. */
export function RewardSticker({ kind, size = 64, className = '' }: {
  kind: keyof typeof stickers
  size?: number
  className?: string
}) {
  const [canAnimate, setCanAnimate] = useState(false)
  useEffect(() => {
    const query = window.matchMedia('(prefers-reduced-motion: no-preference) and (hover: hover)')
    const update = () => setCanAnimate(query.matches)
    update()
    query.addEventListener('change', update)
    return () => query.removeEventListener('change', update)
  }, [])
  const Sticker = stickers[kind]
  return <span className={`reward-sticker ${className}`} aria-hidden="true">
    <Sticker size={size} peel={canAnimate} focusable="false" />
  </span>
}
