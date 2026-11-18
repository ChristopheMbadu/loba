# Loba for Android (Kotlin + Jetpack Compose)

A native app: no web view. It is the iPhone app (`../ios`) ported screen for screen, file for file.
Open this folder in Android Studio, pick an emulator or a phone, press Run. Android 8.0 (API 26) and later.

## How it fits with the rest of Loba

- **Same accounts and progress as the website and the iPhone app.** The app talks to `https://congolingo.com`
  (`core/Api.kt`). Progress is stored in the same JSON shape, so one account works everywhere.
- **Same rules.** `core/Rules.kt` and `core/Exercises.kt` are ports of `ios/Loba/Core/*.swift`, themselves ports of
  `learn/src/game/*.ts` (hearts, streaks, XP, spaced repetition, lesson building, grading).
  When a rule changes on the web, change it in both apps.
- **Lessons** come from `/api/v1/lessons`; `assets/lessons.json` (a copy of `data/lessons.json`) is the offline fallback.
- **The map game** downloads `/api/v1/map` at launch and uses it from the next launch; `assets/drc-map.json` is the
  bundled copy. The satellite image needs a public Mapbox token in `app/src/main/assets/Mapbox.txt` (not committed);
  without it the map is drawn plain.
- **Works without an account** ("Later, without an account"): progress stays on the phone until the learner signs up.

## Layout

Everything is under `app/src/main/`. Each Kotlin file mirrors the Swift file of the same name.

| Path | What |
| --- | --- |
| `java/com/congolingo/loba/App.kt` | The application and its single activity |
| `java/com/congolingo/loba/core/` | Models, game state, rules, lesson engine, server client, store, sounds, map and puzzle data |
| `java/com/congolingo/loba/ui/` | Screens: splash, welcome and sign-in, onboarding, river path, lesson player, practice, quests, shop, profile, story book, word puzzle, map game, guess the word, phrasebook |
| `ui/Theme.kt`, `ui/Components.kt` | Palette, typefaces, chunky buttons, cards, covers and sheets |
| `res/raw/` | The sound cues (same files as iOS) |
| `res/font/` | Fredoka and Nunito (SIL Open Font License, licence files in `assets/`) |
| `res/drawable-nodpi/` | The illustrated icons and story layers, copied from `ios/Loba/Assets.xcassets` |
| `assets/owl-laughing.json` | The splash animation, played with Lottie |

Differences from iOS that are deliberate:

- `Unit` is called `LessonUnit` (Kotlin has its own `Unit`).
- `AppStore` is a Kotlin `object` whose properties are Compose state, instead of an environment object.
- The sign-in cookie is kept by the app itself (`core/Api.kt`), as iOS does for free.
- The evening reminder is a WorkManager job (`core/Store.kt`); Android 13+ asks for the notification permission.
- Google sign-in opens a browser tab (Custom Tabs) and comes back through `loba://auth`, with the same proof
  challenge, state and one-time code checks as iOS (`core/GoogleAuthRequest.kt`).

## Commands

From this folder (`JAVA_HOME` can point at Android Studio's bundled JDK:
`/Applications/Android Studio.app/Contents/jbr/Contents/Home`):

```sh
./gradlew :app:testDebugUnitTest   # rules, grading, progress JSON, lesson building, puzzle, Google callback checks
./gradlew :app:assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug        # onto the running emulator or the connected phone
```

Debug builds accept intent extras for screenshots, the counterpart of the iOS launch arguments:

```sh
adb shell am start -n com.congolingo.loba/.MainActivity --ez demo true --ez quick true --es screen story --ei page 2
```

`demo` (skip sign-in and onboarding), `quick` (skip the splash's minimum time), `still` (no decorative motion),
`screen story|lesson|build|review|puzzle|puzzles|map|practice|quests|shop|profile`, `page N` (story page),
`autoplay yes|wrong|hold`, `maplevel …`.

## Before Google Play

- Application id: `com.congolingo.loba` (the iOS bundle id). No signing key or Play listing exists yet.
- Email sign-in sends `Origin: https://congolingo.com` so the server's origin check accepts the app.
- Google sign-in needs the same backend as iOS (`/api/learn/auth-options` reporting `google`, `app` and
  `appProtocol: 2`); the button stays hidden otherwise. Verify it on a real device after deploying.
- The launcher icon is the iOS icon as a plain bitmap; make an adaptive icon before release.
