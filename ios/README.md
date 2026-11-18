# Loba for iPhone (SwiftUI)

A native app: no web view. Open `Loba.xcodeproj` in Xcode 16 or later, pick a simulator or a phone, press Run.
iOS 17 and later. No third-party packages.

## How it fits with the rest of Loba

- **Same accounts and progress as the website.** The app talks to `https://congolingo.com` (`Core/API.swift`).
  Progress is stored in the same JSON shape the website uses, so one account works on both.
- **Same rules.** `Core/Rules.swift`, `Core/Exercises.swift` are ports of `learn/src/game/*.ts`
  (hearts, streaks, XP, spaced repetition, lesson building, grading). When a rule changes on the web, change it here too.
- **Lessons** come from `/api/v1/lessons`; `Resources/lessons.json` (a copy of `data/lessons.json`) is the offline fallback.
- **The map game** (Mboka na biso) uses the website's own content file: it downloads `/api/v1/map` at launch and uses it from the
  next launch; `Resources/drc-map.json` is a copy of `data/drc-map.json` for the first launch and offline. Edit facts in
  `data/drc-map.json` only. The satellite image needs a public Mapbox token in `Resources/Mapbox.txt` (not committed);
  without it the map is drawn plain.
- **Works without an account** ("Later, without an account"): progress stays on the phone until the learner signs up.

## Layout

| Path | What |
| --- | --- |
| `Loba/Core/` | Models, game state, rules, lesson engine, server client, store, sounds |
| `Loba/Views/` | Screens: welcome and sign-in, onboarding, river path, lesson player, practice, quests, shop, profile, story book |
| `Loba/Resources/Sounds/` | The website's sound cues, rendered from its synthesizer (`learn/src/audio/sfx.ts`) |
| `Loba/Resources/Fonts/` | Fredoka and Nunito (SIL Open Font License, licence files alongside) |
| `Loba/Assets.xcassets` | The illustrated icons, and each story page as two layers (`story-back-N` landscape, `story-cast-N` characters), exported from the web app |
| `Loba/Views/Loba.swift` | Loba, drawn and animated in code from the website's shapes (blink, wave, flap, hop) |
| `Loba/Views/Effects.swift` | Sparks, night sky and fireflies, clouds and birds, sun rays, goal ring, week strip |

## Google sign-in

The button appears when `/api/learn/auth-options` reports `google: true`, `app: true`,
and `appProtocol: 2`. Email login and guest mode still work without Google.

The deployed server lives in **webynn/loba**, while this iOS project lives in
**ChristopheMbadu/loba**. Deploy the matching backend changes there before testing Google.
`server-google-for-app.patch` targets webynn/loba at d6e50b0; it does not apply to this
repository's older server. The existing Google web OAuth client and HTTPS callback
remain unchanged; no client secret is embedded in the app.

The app opens ASWebAuthenticationSession with a fresh proof challenge and random state.
The server stores both on the OAuth attempt. On success it returns a two-minute,
single-use code to `loba://auth`. The app verifies the callback destination and state,
then redeems the code with its private verifier for its own session cookie and progress.
Cancellation is silent; duplicate taps are blocked; failure to open the browser releases
the busy state. Existing password accounts must link Google through the website first.

Validation commands (from the repository root):

```sh
swiftc -module-cache-path /tmp/loba-swift-cache ios/Loba/Core/GoogleAuthRequest.swift ios/Tests/GoogleAuthRequestTests.swift -o /tmp/loba-google-tests
/tmp/loba-google-tests
xcodebuild -project ios/Loba.xcodeproj -scheme Loba -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' -derivedDataPath /tmp/loba-build CODE_SIGNING_ALLOWED=NO build
```

Also verify real Google sign-in, cancellation, returning-account progress, sign-out,
and restarting the app on a device after the backend deployment. Automated callback
checks do not replace a live provider/device test.

## Before the App Store

- Set your team under Signing & Capabilities. Bundle id: `com.congolingo.loba`.
- Email sign-in sends `Origin: https://congolingo.com` so the server's origin check accepts the app.

## Developing

Debug builds accept launch arguments for screenshots:
`-demo` (skip sign-in and onboarding), `-screen story|lesson|build|review|practice|quests|shop|profile`,
`-page N` (story page), `-autoplay yes|wrong|hold` (plays a lesson by itself).

## App branding

The app icon uses the supplied Loba mascot, adapted to a 1024×1024 opaque square.
The splash uses the user's `Owl Laughing.lottie`, extracted without visual changes
into `owl-laughing.json`, on a plain white background with the Loba name.
Playback uses the native Lottie Swift package (pinned to 4.6.1); no web view
or runtime network request is used. Reduce Motion stops playback. Startup timing is unchanged.

This branch preserves Claude's local launch/session changes captured before the
branding work. Preserve any newer local edits when integrating it.
