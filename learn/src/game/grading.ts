/** Lower-case, strip accents and punctuation, collapse whitespace. */
export const normalize = (s: string) =>
  s
    .toLowerCase()
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/ɛ/g, 'e')
    .replace(/ɔ/g, 'o')
    .replace(/[’'`]/g, "'")
    .replace(/[?!.,;:…«»"¿¡()-]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()

export function levenshtein(a: string, b: string): number {
  let prev = Array.from({ length: b.length + 1 }, (_, j) => j)
  for (let i = 1; i <= a.length; i++) {
    const cur = [i]
    for (let j = 1; j <= b.length; j++)
      cur[j] = Math.min(prev[j]! + 1, cur[j - 1]! + 1, prev[j - 1]! + (a[i - 1] === b[j - 1] ? 0 : 1))
    prev = cur
  }
  return prev[b.length]!
}

export type Grade = 'correct' | 'typo' | 'wrong'

/** Typed answers forgive case, accents, punctuation and one small typo. */
export function gradeTyped(input: string, expected: string): Grade {
  const a = normalize(input), b = normalize(expected)
  if (!a) return 'wrong'
  if (a === b) return 'correct'
  const allowed = b.length >= 10 ? 2 : b.length >= 5 ? 1 : 0
  return levenshtein(a, b) <= allowed ? 'typo' : 'wrong'
}

/** Word-bank answers must match exactly (ignoring case and punctuation). */
export const gradeTiles = (tiles: string[], expected: string): Grade =>
  normalize(tiles.join(' ')) === normalize(expected) ? 'correct' : 'wrong'

/** Splits an answer into tappable tiles, dropping punctuation-only fragments. */
export const toTiles = (sentence: string) =>
  sentence
    .split(/\s+/)
    .map((w) => w.replace(/^[«"(¿¡]+|[?!.,;:…»")]+$/g, ''))
    .filter(Boolean)
