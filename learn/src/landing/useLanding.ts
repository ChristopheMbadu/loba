import { useT } from '../i18n/useT'
import { LANDING, type LandingKey } from './copy'

export function useLanding() {
  const { ui } = useT()
  return <K extends LandingKey>(key: K) => LANDING[ui][key]
}
