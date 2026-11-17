import { Capacitor } from '@capacitor/core'

/** True inside the iOS app; false on the website. */
export const native = Capacitor.isNativePlatform()

/**
 * The website talks to the server it was loaded from. The iOS app is loaded from the phone,
 * so its build names the server (VITE_API_ORIGIN, no trailing slash).
 */
export const API_ORIGIN: string = import.meta.env.VITE_API_ORIGIN ?? ''

export const api = (path: string) => API_ORIGIN + path

/** The privacy page lives on the website; the app opens it in the browser. */
export const privacyLink = { href: api('/privacy.html'), ...(native ? { target: '_blank', rel: 'noopener' } : null) }
