# Loba for iPhone (SwiftUI)

A native app: no web view. Open `Loba.xcodeproj` in Xcode 16 or later, pick a simulator or a phone, press Run.
iOS 17 and later. No third-party packages.

## How it fits with the rest of Loba

- **Same accounts and progress as the website.** The app talks to `https://congolingo.com` (`Core/API.swift`).
  Progress is stored in the same JSON shape the website uses, so one account works on both.
- **Same rules.** `Core/Rules.swift`, `Core/Exercises.swift` are ports of `learn/src/game/*.ts`
  (hearts, streaks, XP, spaced repetition, lesson building, grading). When a rule changes on the web, change it here too.
- **Lessons** come from `/api/v1/lessons`; `Resources/lessons.json` (a copy of `data/lessons.json`) is the offline fallback.
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

The button appears only when the server says it supports the app (`app: true` in `/api/learn/auth-options`).
That needs `server-google-for-app.patch` applied to the server and deployed: sign-in happens in the system
browser sheet, the server sends a one-time code back to `loba://auth`, and the app swaps it for its session.
No change is needed in the Google Cloud console.

## Before the App Store

- Set your team under Signing & Capabilities. Bundle id: `com.congolingo.loba`.
- Email sign-in sends `Origin: https://congolingo.com` so the server's origin check accepts the app.

## Developing

Debug builds accept launch arguments for screenshots:
`-demo` (skip sign-in and onboarding), `-screen story|lesson|build|review|practice|quests|shop|profile`,
`-page N` (story page), `-autoplay yes|wrong|hold` (plays a lesson by itself).
