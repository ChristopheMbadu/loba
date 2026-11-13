# Loba Studio

Loba Studio is a private editorial dashboard for the Lingala and Swahili curriculum. It adds email/password sign-in, lesson creation and editing, vocabulary and sentence editors, previews, draft/publish controls, search, language filters, and an editorial activity log.

The server uses Node.js 22+ and PostgreSQL. The interface is plain HTML, CSS and JavaScript with no build step. Railway can build it using the root Dockerfile.

## Scope

- `/` and `/admin`: private lesson-management dashboard.
- `/api/admin/*`: authenticated editorial API.
- `/api/v1/lessons`: public, versioned JSON API exposing **only published snapshots** for a future SwiftUI app.
- `/learn`: the learner app (`learn/`, see [LEARNER.md](LEARNER.md)). It reads published lessons from `/api/v1/lessons`, so publishing in Studio updates what learners see. Progress is stored on the learner's device.
- The import contains all 24 vocabulary/sentence units (12 per language). Dialogue scenarios, stories, gamification, learner accounts and progress are not migrated into Studio yet.

There is no public admin registration. On first startup, the server creates one editor from `ADMIN_EMAIL` and `ADMIN_PASSWORD`. Passwords use salted scrypt hashes. Sessions are random, stored as hashes in PostgreSQL, and expire after 12 hours. Production cookies are secure, HTTP-only and same-site. Mutations require the exact configured origin. Login attempts are limited in the database.

## Run locally

1. Install Node.js 22+ and PostgreSQL; create an empty database named `loba`.
2. Run `npm install` from the repository root.
3. Copy `.env.example` to `.env`. Set the database connection, your editor email and a unique password (14–256 characters). Do not commit `.env`.
4. Run `npm start` and open `http://localhost:3000`.

First startup applies the schema and imports the existing curriculum as published content. A migration marker prevents future restarts from reimporting deleted lessons or overwriting edits. The bootstrap password is only used when no admin exists; changing the environment variable does not reset an existing account.

## Deploy on Railway

### Project prepared on November 12, 2026

- Workspace: **Webynn**.
- Project: **loba-studio**, ID `a04780c7-5f54-4ef0-85bb-924e1c0ec75c`.
- Production environment: `7a93c058-59cc-4a86-9391-165312c22683`.
- PostgreSQL service: `2003fa92-b140-44f8-85a7-407403c576e9`, observed **Online** with a persistent volume.
- Application service: **loba-web**, ID `5c877df0-da82-4067-9cda-0c71e2d1221f`, created but **offline pending source upload**.
- Applied app settings: `DATABASE_URL=${{Postgres.DATABASE_URL}}`, `NODE_ENV=production`, `ADMIN_EMAIL=chris@webynn.com`, health check `/health`.
- Public address reserved: `https://loba-web-production.up.railway.app` (the application is not deployed yet). Explicit `PORT=3000` and `APP_ORIGIN` are configured to match.
- Source fork created: [webynn/loba](https://github.com/webynn/loba). Studio source, curriculum, maintenance scripts, tests and CI are committed there. Railway's source is set to this repository's `main` branch, staged for the first deployment.
- No admin password, live application deployment, or database schema migration has been completed yet.

Open [the prepared Railway project](https://railway.com/project/a04780c7-5f54-4ef0-85bb-924e1c0ec75c).

To finish from your own terminal without waiting for GitHub repository access:

1. In `loba-web` → Variables, personally enter `ADMIN_PASSWORD` (a unique password with at least 14 characters). Apply the variable change.
2. Generate a domain in `loba-web` → Settings → Networking. This provides `RAILWAY_PUBLIC_DOMAIN`. If it is not available before the first deployment, set `APP_ORIGIN` to the generated HTTPS origin (no trailing slash).
3. Run:

   ```sh
   cd "/Users/webynn/Source Control/LOBA"
   railway link --project a04780c7-5f54-4ef0-85bb-924e1c0ec75c --environment production --service loba-web
   railway up
   ```

4. Verify `/health`, sign in, and run the browser acceptance checks below.

The Railway account is now connected to GitHub as `webynn`, using its existing Railway App installation. Source changes are staged for the first deployment; enter the initial `ADMIN_PASSWORD` yourself in Railway, then apply the staged changes. Do not create another project or database. The local checkout still has `ChristopheMbadu/loba` as its origin because this session cannot change Git metadata; reconcile the browser commits before making future local pushes.

### Generic setup for another environment

The repository must include the new Studio files before connecting it to a deployed service. The current working copy may contain uncommitted additions; review and push them first.

1. In Railway, create or select a project for **Loba Studio**.
2. Add a **PostgreSQL** database service.
3. Add a GitHub service using the repository that contains these changes. Use the repository root; Railway will detect `Dockerfile`.
4. On the application service, add these variables:

   | Variable | Value |
   | --- | --- |
   | `DATABASE_URL` | A reference to the database service's `DATABASE_URL` (for a service named `Postgres`: `${{Postgres.DATABASE_URL}}`) |
   | `ADMIN_EMAIL` | Your editor email address |
   | `ADMIN_PASSWORD` | A unique password of at least 14 characters |
   | `NODE_ENV` | `production` |

5. Generate an application domain in Networking. The app uses `RAILWAY_PUBLIC_DOMAIN` to determine its allowed origin. Set `APP_ORIGIN=https://your-domain` explicitly for a custom domain, with no trailing slash. Redeploy after changing the domain or origin.
6. Set the health check path to `/health` in the application's deployment settings. The service listens on Railway's injected `PORT`.
7. Deploy, open the domain, and sign in using your configured editor account.

The database is durable; the app needs no filesystem volume. Use Railway's database backup settings for backups. No Railway project, database, domain or deployment is created by the files alone.

The first successful startup stores the editor password hash in the database. You can remove the `ADMIN_PASSWORD` bootstrap variable afterwards. If you lose your password, use the password-reset script below rather than deleting the database.

## Reset the editor password

Run `npm run admin:reset` in a trusted local shell with `.env` pointing to the intended database. The script reads the email and new password interactively (password input is hidden), updates the existing editor, and revokes all their sessions. It will not create an account. Never place a real password in a command argument or commit it.

To add another administrator, run `npm run admin:reset -- --create` in the application's Railway Console (or a trusted shell connected to the intended database). Enter the new email, then enter and confirm a password of 14–256 characters. The create mode inserts a separate administrator and refuses duplicate emails; it does not replace existing accounts. All administrator accounts currently have the same lesson-management permissions; there is no separate owner role.

## Editorial workflow

Creating and saving a lesson produces a draft. On an already published lesson, **Save draft** changes only the editable content. Learners keep receiving the last published snapshot. **Publish lesson** saves your edits and makes the new snapshot public. **Unpublish** removes it from the public API. Deletion is allowed only after unpublishing, and requires confirmation.

Concurrent edits use revision checks: an out-of-date editor receives a conflict instead of overwriting someone else's work. Close and reopen the editor after refreshing to load the latest revision; copy any unsaved work before discarding it.

Teaching notes are plain text. English meanings are editable; missing translations remain blank rather than being invented. The preview shows vocabulary and sentence answers, not the full legacy learner game.

## Word recordings

In the lesson editor, each vocabulary entry has **Record** (microphone, up to 15 seconds) and **Upload audio** (MP3, M4A, WAV, OGG or WebM, up to 1.5 MB). Recordings are stored in PostgreSQL (`studio_audio`), attached to the word as an `audio` id, and follow the same draft/publish flow as the rest of the lesson. Learners stream them from the public `GET /api/v1/audio/:id`. The file type is detected from its content, not the upload's declared type. Uploads never attached to a lesson are deleted at startup after 7 days.

Browser-made recordings are WebM (Chrome, Firefox) or MP4 (Safari); older Safari versions cannot play WebM, so MP3 or M4A uploads are the most widely playable.

## Published API for the future SwiftUI client

`GET /api/v1/lessons` requires no credentials and returns:

```json
{
  "schemaVersion": 1,
  "lessons": [{
    "id": "ln-mbote",
    "version": 1,
    "published_at": "2026-11-12T12:00:00Z",
    "content": {
      "language": "ln",
      "position": 1,
      "title": "Mbote ! Saluer",
      "titleEn": "Greetings",
      "icon": "👋",
      "tip": "A teaching note",
      "words": [{"term": "Mbote", "fr": "Bonjour", "en": "Hello"}],
      "sentences": [{"fr": "Je vais bien", "en": "I am well", "answer": "Nazali malamu", "distractors": ["yo"]}]
    }
  }]
}
```

Lesson IDs stay stable when titles or positions change. Draft edits do not alter the public version. The future learner app should persist progress by lesson ID and treat missing lessons as unpublished. Learner authentication and progress endpoints are not part of this first admin release.

## Checks

- `npm run check`: JavaScript syntax.
- `npm test`: password handling, request protections, admin routing, curriculum validation and public/draft API contracts. HTTP tests inject an in-memory test double and don't need network access; they do not validate PostgreSQL queries.
- `TEST_DATABASE_URL=postgresql://... npm test`: also runs the actual PostgreSQL integration test. Use a **dedicated empty disposable database**. It creates test tables and data and leaves them in place for inspection.
- `.github/workflows/studio.yml` runs these checks on GitHub with an isolated PostgreSQL 16 service, including the database integration test. Its password is disposable CI-only test data, not a production credential.

Browser acceptance: sign in; filter each language; create a draft; add words and sentences; save and reload; publish; inspect `/api/v1/lessons`; save a private edit and verify the public version is unchanged; unpublish and delete the test lesson; sign out and confirm admin endpoints reject access.

GitHub Actions run [43279382143](https://github.com/webynn/loba/actions/runs/43279382143) passed all 11 tests, with zero skips, including the real PostgreSQL integration test. The live dashboard still needs first deployment and browser acceptance testing. Local package installation and local server preview were blocked by this session's network and port restrictions.

## Public registration and dashboard UI

The dashboard uses the MIT-licensed Tabler UI kit, pinned to @tabler/core 1.7.5 and served locally at /admin/tabler.css. Source and license: https://github.com/tabler/tabler . Package license notices remain in the installed dependency.

Anyone may register at /signup with an email and a 14–256 character password. Public registrations are stored with approved=false and receive no session. Approved administrators use Accounts to grant workspace access; approval grants lesson management and account-approval permissions. Email ownership is not verified, so administrators must independently recognize the person before approving. Existing administrators keep access. The /learn application still uses its original authentication and is separate from Studio registration.
