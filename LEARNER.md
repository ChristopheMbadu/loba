# Loba learner app (`learn/`)

The Duolingo-style learner app served at `/learn`. React + TypeScript, built with Vite.

- **TanStack Router**: file-based routes in `learn/src/routes`, validated search params
  (`/lesson/$unitId?n=`, `/session?mode=`), loaders that guard locked lessons.
- **TanStack Query**: fetches the languages and curriculum from Studio's published API
  (`/api/v1/lessons`). Offline, or before Studio is deployed, it falls back to Lingala and
  Swahili bundled from `data/lessons.json`.
- **TanStack Store**: game state (XP, hearts, streak, progress, spaced repetition),
  persisted to `localStorage` under `loba-learn-v1`. Saves from the previous app
  (`loba-state-v4`) are migrated on first load.

## Languages

Languages are not hard-coded. Each one comes from Studio with its code, French and English
names, greeting, encouragement, regions and colour. Learners are offered every visible
language that has at least one usable unit (4+ words); onboarding, the profile picker, the
landing page and the mini-lesson all follow that list. If a learner's language is hidden
later, the app moves them to the first available one.

## Accounts

Every learner has an account: first name, email and a password of 8+ characters.

- **New learners** create it first: every "Get started" button leads to `/account` (styled
  like the landing page), then to the language and daily goal (`/welcome`), then to the first
  lesson. No learning screen opens without an account. "I already have an account" signs in.
- **Existing learners** who played before accounts existed are sent to `/account` once;
  their local progress is uploaded to the new account.
- **Sync**: while signed in, every change is saved to `PUT /api/learn/progress` a couple of
  seconds later, and again when the app is hidden. On launch, unsaved local progress wins;
  otherwise the account's copy is loaded. Signing in on a new device restores progress.
- **Signing out** removes progress from the device (it stays on the account). **Delete my
  account** in the profile erases the account and its progress after the password is
  re-entered.
- **Offline**: if the account service can't be reached, learners keep playing on the device
  and are asked to create an account when it is back.

There is no password reset yet: it needs an email provider. Learner sessions use their own
cookie (`loba_learner`, 60 days) and never grant access to Studio.

## Layout

| Path | What |
| --- | --- |
| `src/game/` | Pure game logic: rules (hearts, streak, freezes, quests, shop, chests, SRS), exercise generation, grading, session reducer, save migration. Unit-tested in `rules.test.ts`. |
| `src/content/` | Language and curriculum types, validation and fetching. |
| `src/account/` | Account API calls, sign-up/sign-in form, route guard and progress sync. |
| `src/landing/` | The public landing page at `/learn/home`: hero, mini-lesson and showcases. |
| `src/components/` | UI, including `LessonPlayer` and one component per exercise type. |
| `src/routes/` | Screens: path, practice, quests, shop, profile, onboarding, lesson, practice session. |
| `src/i18n/` | French and English UI strings. |
| `src/styles/app.css` | Design tokens and all styles, light and dark. |

Each unit has 4 lessons that ramp up (meet half the words → the other half → mix with
sentences → recall by typing) followed by a chest. Exercise types: meaning, recall,
listening, match pairs, word-bank sentences, typed answers.

There is no synthetic voice. Pronunciation plays only the recordings teachers attach to
words in Studio; a word without one has no speaker button, and listening exercises are
only generated for recorded words.

## Commands

```sh
npm run build:learn          # from the repo root: install + build into learn/dist
npm run dev:learn            # Vite dev server on :5173/learn/ (proxies /api to :3000)
npm run test:learn           # game logic tests
```

The Node server serves `learn/dist` at `/learn` (SPA fallback, hashed assets cached
immutably, strict CSP). The Docker image builds it in a separate stage.

## Not yet ported from the legacy app

The previous single-file app (root `index.html`, still the GitHub Pages demo) also had
WhatsApp-style situations, stories, the sentence machine, speaking exercises, leagues,
friends, Supabase accounts/cloud sync, push reminders and donations. Those need content
in Studio or learner accounts on the server first.

## Art and credits

The mascot (Loba, an African grey parrot), the icon set and the path scenery are original
SVG drawn in `src/components/Loba.tsx` and `src/components/Art.tsx`, animated with CSS.
No emoji or third-party illustrations are used in the UI.

- **Fredoka** and **Nunito**, SIL Open Font License, self-hosted via @fontsource-variable.
