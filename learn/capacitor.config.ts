import type { CapacitorConfig } from '@capacitor/cli'

const config: CapacitorConfig = {
  appId: 'io.github.christophembadu.loba',
  appName: 'Loba',
  webDir: 'dist-ios',
  ios: { contentInset: 'never', backgroundColor: '#ffffff' },
  plugins: {
    // Requests go out from the phone itself, so the learner's session cookie works without a browser origin.
    CapacitorHttp: { enabled: true },
    SplashScreen: { launchAutoHide: false, backgroundColor: '#ffffff' },
    LocalNotifications: { presentationOptions: ['banner', 'sound'] },
  },
}

export default config
