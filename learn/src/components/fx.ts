/**
 * Celebration effects drawn on one shared canvas above the page: bursts of paper shapes in the
 * flag's colours. Nothing runs (and no canvas exists) when the learner prefers reduced motion.
 */
const COLORS = ['#1c9bef', '#ffc22e', '#ef4e4e', '#2bb673', '#ff8a1f', '#6cc4fa']
type Shape = 'rect' | 'tri' | 'diamond' | 'dot'
const SHAPES: Shape[] = ['rect', 'tri', 'diamond', 'dot']

interface Piece {
  x: number; y: number; vx: number; vy: number
  rot: number; spin: number; size: number
  color: string; shape: Shape
  age: number; life: number; gravity: number; drag: number
  /** Paper flutters: its visible width swings as it falls. */
  flutter: number
}

export const calm = () => typeof matchMedia !== 'undefined' && matchMedia('(prefers-reduced-motion: reduce)').matches

let canvas: HTMLCanvasElement | null = null
let pen: CanvasRenderingContext2D | null = null
let pieces: Piece[] = []
let frame = 0
let last = 0

function fit() {
  if (!canvas || !pen) return
  const dpr = Math.min(2, devicePixelRatio || 1)
  canvas.width = innerWidth * dpr
  canvas.height = innerHeight * dpr
  pen.setTransform(dpr, 0, 0, dpr, 0, 0)
}

function stage() {
  if (!canvas) {
    canvas = document.createElement('canvas')
    canvas.className = 'fx-canvas'
    canvas.setAttribute('aria-hidden', 'true')
    document.body.append(canvas)
    pen = canvas.getContext('2d')
    addEventListener('resize', fit)
    fit()
  }
  if (!frame) {
    last = performance.now()
    frame = requestAnimationFrame(draw)
  }
}

function draw(now: number) {
  if (!pen) return
  const dt = Math.min(2.5, (now - last) / 16.67)
  last = now
  pen.clearRect(0, 0, innerWidth, innerHeight)
  pieces = pieces.filter((p) => p.age < p.life && p.y < innerHeight + 40)
  for (const p of pieces) {
    p.age += dt
    p.vy += p.gravity * dt
    p.vx *= p.drag ** dt
    p.vy *= p.drag ** dt
    p.x += p.vx * dt
    p.y += p.vy * dt
    p.rot += p.spin * dt
    const s = p.size
    pen.save()
    pen.globalAlpha = Math.min(1, (p.life - p.age) / 14)
    pen.translate(p.x, p.y)
    pen.rotate(p.rot)
    pen.scale(Math.cos(p.age * p.flutter), 1)
    pen.fillStyle = p.color
    pen.beginPath()
    if (p.shape === 'rect') pen.rect(-s / 2, -s / 3, s, s / 1.5)
    else if (p.shape === 'dot') pen.arc(0, 0, s / 2.4, 0, Math.PI * 2)
    else if (p.shape === 'tri') { pen.moveTo(0, -s / 2); pen.lineTo(s / 2, s / 2); pen.lineTo(-s / 2, s / 2) }
    else { pen.moveTo(0, -s / 1.7); pen.lineTo(s / 2.4, 0); pen.lineTo(0, s / 1.7); pen.lineTo(-s / 2.4, 0) }
    pen.fill()
    pen.restore()
  }
  frame = pieces.length ? requestAnimationFrame(draw) : 0
}

interface BurstOptions {
  count?: number
  /** Direction in radians (−π/2 is straight up) and how wide the fan is. */
  angle?: number
  spread?: number
  power?: number
  size?: number
  gravity?: number
  life?: number
  colors?: string[]
}

export function burst(x: number, y: number, o: BurstOptions = {}) {
  if (calm()) return
  const { count = 18, angle = -Math.PI / 2, spread = Math.PI * 2, power = 7, size = 9, gravity = 0.32, life = 70, colors = COLORS } = o
  for (let i = 0; i < count; i++) {
    const a = angle + (Math.random() - 0.5) * spread
    const v = power * (0.45 + Math.random() * 0.75)
    pieces.push({
      x, y, vx: Math.cos(a) * v, vy: Math.sin(a) * v,
      rot: Math.random() * 6.3, spin: (Math.random() - 0.5) * 0.4, size: size * (0.7 + Math.random() * 0.7),
      color: colors[i % colors.length]!, shape: SHAPES[i % SHAPES.length]!,
      age: 0, life: life * (0.7 + Math.random() * 0.6), gravity, drag: 0.985, flutter: 0.12 + Math.random() * 0.2,
    })
  }
  stage()
}

/** A burst from the middle of an element, e.g. the answer the learner just got right. */
export function burstFrom(el: Element | null | undefined, o: BurstOptions = {}) {
  if (!el) return
  const r = el.getBoundingClientRect()
  burst(r.left + r.width / 2, r.top + r.height / 2, o)
}

/** Two cannons from the bottom corners, then paper drifting down from the top. */
export function confetti() {
  if (calm()) return
  const w = innerWidth, h = innerHeight
  const big = { count: 70, spread: 0.9, power: Math.min(26, 13 + h / 60), size: 12, gravity: 0.26, life: 190 }
  burst(-10, h * 0.92, { ...big, angle: -Math.PI / 3.2 })
  burst(w + 10, h * 0.92, { ...big, angle: -Math.PI + Math.PI / 3.2 })
  for (let i = 0; i < 6; i++) {
    setTimeout(() => burst(Math.random() * w, -10, { count: 9, angle: Math.PI / 2, spread: 1.6, power: 3, size: 11, gravity: 0.06, life: 260 }), 350 + i * 260)
  }
}

const GEM = '<svg viewBox="0 0 48 48"><path d="M14 6h20l10 12-20 25L4 18z" fill="#1c9bef"/><path d="M14 6l10 12L34 6z" fill="#6cc4fa"/><path d="M4 18h40L24 43z" fill="#1479bf"/><path d="M24 18 14 6 4 18zM24 18l10-12 10 12z" fill="#3fb0f5"/></svg>'

/** Gems fly from where they were earned to the gem counter, which is where the eye should end up. */
export function flyGems(from: Element | null | undefined, count = 6) {
  if (!from || calm()) return
  const target = [...document.querySelectorAll<HTMLElement>('.stat.gems')].find((el) => el.offsetParent)
  if (!target) return burstFrom(from)
  const a = from.getBoundingClientRect(), b = target.getBoundingClientRect()
  const x0 = a.left + a.width / 2, y0 = a.top + a.height / 2
  const dx = b.left + 14 - x0, dy = b.top + b.height / 2 - y0
  for (let i = 0; i < count; i++) {
    const gem = document.createElement('i')
    gem.className = 'fly-gem'
    gem.innerHTML = GEM
    gem.style.left = `${x0}px`
    gem.style.top = `${y0}px`
    document.body.append(gem)
    const sx = (Math.random() - 0.5) * 120, sy = -40 - Math.random() * 70
    gem
      .animate(
        [
          { transform: 'translate(-50%, -50%) scale(0.3)', opacity: 0 },
          { transform: `translate(calc(-50% + ${sx}px), calc(-50% + ${sy}px)) scale(1.15)`, opacity: 1, offset: 0.3 },
          { transform: `translate(calc(-50% + ${dx}px), calc(-50% + ${dy}px)) scale(0.55)`, opacity: 1 },
        ],
        { duration: 760 + i * 70, delay: i * 55, easing: 'cubic-bezier(0.5, 0, 0.3, 1)', fill: 'backwards' },
      )
      .finished.then(() => {
        gem.remove()
        target.animate([{ transform: 'scale(1)' }, { transform: 'scale(1.18)' }, { transform: 'scale(1)' }], { duration: 180 })
      })
      .catch(() => gem.remove())
  }
}
