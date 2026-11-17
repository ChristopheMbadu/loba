import { LocalNotifications } from '@capacitor/local-notifications'
import { game } from '../game/store'
import { native } from '../platform'

/** The daily reminder is a choice made on this phone, so it stays on this phone. */
const KEY = 'loba-reminder'
const ID = 1
const HOUR = 19

const TEXT = {
  fr: { title: 'Loba t’attend !', body: 'Cinq minutes suffisent pour garder ta série.' },
  en: { title: 'Loba is waiting!', body: 'Five minutes is enough to keep your streak.' },
} as const

const read = (): 'on' | 'off' | null => {
  try {
    return localStorage.getItem(KEY) as 'on' | 'off' | null
  } catch {
    return null
  }
}
const write = (value: 'on' | 'off') => {
  try {
    localStorage.setItem(KEY, value)
  } catch {
    /* private mode */
  }
}

export const reminderOn = () => read() === 'on'

/** Turns the evening reminder on or off. Returns whether it is on afterwards. */
export async function setReminder(on: boolean): Promise<boolean> {
  if (!native) return false
  try {
    await LocalNotifications.cancel({ notifications: [{ id: ID }] })
    if (on) {
      const { display } = await LocalNotifications.requestPermissions()
      if (display !== 'granted') on = false
    }
    if (on) {
      await LocalNotifications.schedule({
        notifications: [{ id: ID, ...TEXT[game.get().ui], schedule: { on: { hour: HOUR, minute: 0 }, allowWhileIdle: true } }],
      })
    }
  } catch {
    on = false
  }
  write(on ? 'on' : 'off')
  return on
}

/** After a first finished lesson, offer the reminder once; later, keep its wording in the learner's language. */
export function reminderAfterLesson() {
  if (!native) return
  const choice = read()
  if (choice === null || choice === 'on') void setReminder(true)
}
