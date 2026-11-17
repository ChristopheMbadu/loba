import type { QueryClient } from '@tanstack/react-query'
import { actions, game } from '../game/store'
import { meQuery, saveProgress, type Me, type ServerProgress } from './api'

/** Set while this device holds progress the server hasn't stored yet. */
const DIRTY_KEY = 'loba-unsynced'
const store = {
  get: () => { try { return localStorage.getItem(DIRTY_KEY) === '1' } catch { return false } },
  set: (dirty: boolean) => { try { if (dirty) localStorage.setItem(DIRTY_KEY, '1'); else localStorage.removeItem(DIRTY_KEY) } catch { /* private mode */ } },
}

let timer: ReturnType<typeof setTimeout> | undefined
let applying = false

const signedIn = (qc: QueryClient) => !!qc.getQueryData<Me>(meQuery.queryKey)?.learner

async function push(qc: QueryClient, keepalive = false) {
  clearTimeout(timer)
  if (!signedIn(qc) || !store.get()) return
  try {
    await saveProgress(game.get(), keepalive)
    store.set(false)
  } catch {
    // Offline: the flag stays set and the next change or visit retries.
  }
}

/** Replaces this device's progress with the account's (signing in on a new device). */
export function adopt(progress: ServerProgress | null) {
  if (!progress) return false
  applying = true
  const ok = actions.replace(progress.state)
  applying = false
  store.set(false)
  return ok
}

/** Uploads this device's progress to the account right away. */
export async function uploadNow(qc: QueryClient) {
  store.set(true)
  await push(qc)
}

/** Forgets everything stored on this device (signing out on a shared phone). */
export function forgetDevice() {
  clearTimeout(timer)
  store.set(false)
  applying = true
  actions.reset()
  applying = false
}

/**
 * Keeps the account in step with play: every change is saved a moment later, and once more when
 * the app is hidden. On launch, unsynced local progress wins; otherwise the account's copy is used.
 */
export function startSync(qc: QueryClient) {
  let last = game.get()
  game.subscribe((s) => {
    // The minute tick re-sets an unchanged state; only real changes need saving.
    if (s === last) return
    last = s
    if (applying) return
    store.set(true)
    if (!signedIn(qc)) return
    clearTimeout(timer)
    timer = setTimeout(() => void push(qc), 2000)
  })
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') void push(qc, true)
  })
  void qc.ensureQueryData(meQuery).then((me) => {
    if (!me.learner) return
    if (store.get() && game.get().onboarded) void push(qc)
    else adopt(me.progress)
  })
}
