import { queryOptions } from '@tanstack/react-query'

export interface Learner {
  name: string
  email: string
}

export interface ServerProgress {
  state: unknown
  updated_at: string
}

export interface Me {
  learner: Learner | null
  progress: ServerProgress | null
  /** The account service can't be reached (offline, or a server without accounts). */
  offline: boolean
}

export class AccountError extends Error {
  constructor(message: string, readonly status: number) {
    super(message)
  }
}

async function call<T>(path: string, init: RequestInit = {}): Promise<T> {
  const res = await fetch(path, {
    credentials: 'same-origin',
    ...init,
    headers: { 'Content-Type': 'application/json', Accept: 'application/json', ...init.headers },
  })
  const body = (await res.json().catch(() => null)) as (T & { error?: string }) | null
  if (!res.ok || !body) throw new AccountError(body?.error ?? 'offline', body ? res.status : 0)
  return body
}

async function fetchMe(): Promise<Me> {
  try {
    const body = await call<{ learner: Learner | null; progress?: ServerProgress | null }>('/api/learn/me')
    return { learner: body.learner, progress: body.progress ?? null, offline: false }
  } catch {
    return { learner: null, progress: null, offline: true }
  }
}

export const meQuery = queryOptions({ queryKey: ['me'], queryFn: fetchMe, staleTime: Infinity })

type Session = { learner: Learner; progress: ServerProgress | null }

export const signup = (input: { name: string; email: string; password: string; state?: unknown }) =>
  call<Session>('/api/learn/signup', { method: 'POST', body: JSON.stringify(input) })

export const login = (input: { email: string; password: string }) =>
  call<Session>('/api/learn/login', { method: 'POST', body: JSON.stringify(input) })

export const logout = () => call<{ ok: true }>('/api/learn/logout', { method: 'POST', body: '{}' })

export const rename = (name: string) => call<{ learner: Learner }>('/api/learn/profile', { method: 'PUT', body: JSON.stringify({ name }) })

export const deleteAccount = (password: string) =>
  call<{ ok: true }>('/api/learn/account', { method: 'DELETE', body: JSON.stringify({ password }) })

/** `keepalive` lets the last save finish while the page is closing. */
export const saveProgress = (state: unknown, keepalive = false) =>
  call<{ updated_at: string }>('/api/learn/progress', { method: 'PUT', body: JSON.stringify({ state }), keepalive })
