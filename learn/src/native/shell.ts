import { SplashScreen } from '@capacitor/splash-screen'
import { StatusBar, Style } from '@capacitor/status-bar'
import { native } from '../platform'

/** What the iOS app needs once the first screen is drawn. */
export function startShell() {
  if (!native) return
  document.documentElement.dataset.native = 'ios'
  // An app doesn't pinch-zoom.
  document.querySelector('meta[name=viewport]')?.setAttribute('content', 'width=device-width, initial-scale=1, viewport-fit=cover, maximum-scale=1, user-scalable=no')
  void StatusBar.setStyle({ style: Style.Light }).catch(() => {})
  requestAnimationFrame(() => void SplashScreen.hide().catch(() => {}))
}
