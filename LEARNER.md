# Loba learner app (`learn/`)

The Duolingo-style learner app served at `/learn`. React + TypeScript, built with Vite.

- **TanStack Router**: file-based routes in `learn/src/routes`, validated search params
  (`/lesson/$unitId?n=`, `/session?mode=`), loaders that guard locked lessons.
- **TanStack Query**: fetches the curriculum from Studio's published API
  (`/api/v1/lessons`). Offline, or before Studio is deployed, it falls back to the
  curriculum bundled from `data/lessons.json`.
- **TanStack Store**: game state (XP, hearts, streak, progress, spaced repetition),
  persisted to `localStorage` under `loba-learn-v1`. Saves from the previous app
  (`loba-state-v4`) are migrated on first load.

## Layout

| Path | What |
| --- | --- |
| `src/game/` | Pure game logic: rules (hearts, streak, freezes, quests, shop, chests, SRS), exercise generation, grading, session reducer, save migration. Unit-tested in `rules.test.ts`. |
| `src/content/` | Curriculum types, validation and fetching. |
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
