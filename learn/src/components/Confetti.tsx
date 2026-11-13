import { useMemo } from 'react'

const COLORS = ['#1c9bef', '#ffc22e', '#ef4e4e', '#2bb673', '#8b5cf6', '#ff8a1f']

export function Confetti({ count = 70 }: { count?: number }) {
  const pieces = useMemo(
    () =>
      Array.from({ length: count }, (_, i) => ({
        left: `${Math.random() * 100}%`,
        background: COLORS[i % COLORS.length],
        animationDuration: `${1.8 + Math.random() * 1.8}s`,
        animationDelay: `${Math.random() * 0.5}s`,
        transform: `rotate(${Math.random() * 360}deg)`,
      })),
    [count],
  )
  return (
    <div className="confetti" aria-hidden="true">
      {pieces.map((style, i) => (
        <i key={i} style={style} />
      ))}
    </div>
  )
}
