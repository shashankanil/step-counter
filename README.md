# Step Counter · Phase 3 foundation

A local, widget-first Android step dashboard: charcoal squircles, original dot-matrix numerals and walking figures, white/grey activity dots and sparse red accents. Inspired by the supplied visual references; no Nothing trademarks, NDOT fonts, proprietary assets or screenshot crops are shipped.

## Run

Open in Android Studio with JDK 17 and Android SDK Platform 37.0 installed (`sdkmanager "platforms;android-37.0" "build-tools;36.0.0"`). Set `ANDROID_HOME` or `sdk.dir` in untracked `local.properties`.

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The project uses AGP 9.1.1 (built-in Kotlin 2.2.10), Gradle 9.3.1, Compose Material3, Glance 1.1.1, Health Connect 1.1.0, Room 2.8.4/KSP 2.3.5 and WorkManager 2.10.3. Minimum Android 8/API 26; compile/target Android 17/API 37. Health Connect requires a supported Android 9+ device; API 26–27 can run the shell but cannot sync. See [SDK setup](https://developer.android.com/about/versions/17/setup-sdk) and [AGP compatibility](https://developer.android.com/build/releases/agp-9-1-0-release-notes).

The Phase 2 authoring environment uses a temporary Temurin 17 JDK and Android SDK for verification. Device/emulator verification remains a release requirement. The CI template at `docs/android-ci.yml` runs assembly, unit tests and lint, and publishes a debug APK when successful. To enable it, copy it to `.github/workflows/android.yml` using credentials with workflow scope; the supplied token could not create workflows. Do not interpret static review as device validation.

## Phase 2 experience

- **Today:** animated 72-dot goal orbit and original walker, large dot-matrix totals, seven-completed-day average, remaining steps, quick goal presets, refresh pulse and last-sync status.
- **History:** Monday-first interactive month calendar, selected-day totals, seven-day dot bars and a recorded goal streak. History is bounded by the local Room cache; the streak stops at unknown or below-goal days and preserves yesterday's run while today is unfinished. Changing the goal recalculates the streak and calendar highlights.
- **Widgets:** live launcher-style previews of all four faces, native pin requests and fallback instructions.
- **You:** optional Google account, sign-out, editable fallback avatar initial, daily goal, Health Connect access review, optional background permission, privacy and about. Avatar and goal changes refresh all four widgets.
- **First run:** connect → choose a goal → pin a widget. Setup can be deferred without granting access; existing Phase 1 installations with step access skip it. Setup state survives restarts.

The shell is edge-to-edge black with charcoal rounded cards, a custom pill navigation bar, tracked labels, quiet secondary text and sparse red markers. Matrix glyphs are shared with `WidgetArtwork`; the Today orbit is native Compose Canvas so progress can animate without allocating a bitmap each frame. Compose Navigation supplies subtle destination fades and retained tab state. Health reads run in an activity-scoped ViewModel, avoiding duplicate work on rotation. Interactive calendar days expose spoken dates/totals, artwork exposes metric descriptions, and the sync status is a polite live region. Font scaling, contrast and screen-reader traversal still need device testing.

No artificial/sample step totals are displayed. `--` means unavailable, including averages with missing days. You can explore the app and pin widgets before connecting. Background refresh remains Android-scheduled and optional, not continuous tracking.

## Connect & permissions

1. Open Step Counter. Install/update Health Connect when prompted (built into supported newer Android versions).
2. Allow **read steps**. This app does not write health records. Connect a step-producing source to Health Connect if your device/provider does not supply steps itself.
3. Optionally allow **background health reads**, offered only when the provider supports that feature. Without it, reads happen while the app is open; widgets retain the local cache.
4. Save a daily goal (default 10,000; range 100–100,000). Refresh to see today's totals and last successful sync time.

Both the legacy permission-rationale activity and Android 14+ permission-usage alias are registered. The privacy screen explains local storage and revocation. Google accounts are optional for future social features. INTERNET is used only for identity/session requests; steps remain local until explicit sync consent (sync is not implemented). There is no Google Fit REST, analytics or iOS implementation. Android backup is disabled; explicit Android 12+ extraction rules also exclude local files, totals and preferences from cloud backup and device transfer. Clear app storage/uninstall to remove cached data; detected read-permission revocation clears totals on the next sync. Production store distribution will additionally require the appropriate Health Connect/Play declarations and a hosted privacy policy.

Steps use Health Connect's **aggregate** API, without data-origin filtering, so provider deduplication is retained. Each day uses local midnight boundaries, including DST. Sync re-reads a rolling 30-day window (within the default history allowance) and atomically upserts Room rows. At most 63 calendar days are retained; older retained rows are cached snapshots, not continuously revalidated. Time-zone changes invalidate the displayed cache until re-aggregation. No permission for extended history is requested. [Health Connect aggregation documentation](https://developer.android.com/health-and-fitness/health-connect/aggregate-data).

A successful empty aggregate is zero; an unread/missing day is unknown (`--`). The average is the previous **seven completed local days**, excluding today, and is unknown until all seven exist. Percentage can exceed 100%; artwork progress clamps at a full path/ring. WorkManager requests a 30-minute periodic sync; Android may delay it. Widgets also re-render cached state on launcher updates (30-minute requested interval). Neither mechanism promises real-time steps or an exact midnight update.

## Widgets

Long-press the home screen → **Widgets → Step Counter**, or use the in-app pin buttons (launcher confirmation required). Each widget starts at 2 × 2 cells and supports resizing. Square artwork fits the available bounds without stretching, with transparent margins at non-square sizes. Launcher cell sizes and rounding vary.

| Widget | Face |
| --- | --- |
| `WalkProgressWidget` | Original dot walker positioned along a dotted path, goal percentage, white S avatar chip, first page dot |
| `StatsStackWidget` | Dot-matrix today total, percent of goal, seven-day average, second page dot, S chip |
| `MonthGridWidget` | Monday-first current month, all seven MTWTFSS labels, red today, white goal-achieved days, grey recorded activity |
| `CircularMetricWidget` | 60-dot goal ring, center steps, small dotted runner and red accent |

Tap any face to open the app. Page indicators are visual references to the separate faces; there is no widget swipe carousel. The avatar chip prefers the Google given-name initial when signed in, with your editable local initial (A–Z) as fallback. Signing out restores that fallback. Tiny month dots represent zero, missing or future days; leading/trailing cells are blank. Six-week months are supported without obscuring dates or weekday labels. Glance hosts original Canvas bitmap artwork with a spoken content description; no proprietary font is needed. The Widgets tab previews all four live faces and offers launcher pinning.

## Structure

- `ui/`: Compose Navigation shell, lifecycle-aware ViewModel state, Today/History/Widgets/You, onboarding, shared Canvas art and styled privacy rationale.
- `widgets/`: four Glance widgets/receivers and shared original Canvas artwork.
- `data/health/`: provider availability, permissions, daily aggregate requests.
- `data/db/`: Room entity, DAO and database; KSP exports schemas on build.
- `data/`: repository, cache/goal policy and WorkManager sync → widget refresh.
- `domain/`: summary math and Monday-first calendar mapping.

## Verification

Phase 3 checks: Android debug assembly, 8 unit tests and lint pass; server TypeScript build, 5 auth/route tests and Drizzle migration generation pass on Node 22. The locked server dependencies audit with zero known vulnerabilities. A scoped esbuild override patches Drizzle Kit’s legacy development loader; migration generation was rechecked with that override. Live Google OAuth, Postgres migration execution and Docker deployment have not been exercised.

Local validation: `assembleDebug`, `testDebugUnitTest` (8 tests) and `lintDebug` pass with Temurin 17/API 37.0. XML parsing and `git diff --check` pass. Lint has no errors; remaining warnings concern API-specific XML attributes, dependency update suggestions and optional KTX style. KSP was updated to 2.3.5 to fix the inherited AGP 9 built-in Kotlin source-set incompatibility. The generated Room v1 schema is checked in; the database layout is unchanged.

Unit tests cover unknown-vs-zero, seven completed days, over-goal progress, six-row calendar, leap year, DST boundaries and recorded streaks (today incomplete, missing history, current-goal changes). The CI template builds/tests/lints once enabled. Before release, check on physical Android 9–13 and 14+ devices: fresh install; unsupported/update-required provider; denial/revocation; background permission denied/granted; no-data provider; overlapping sources; process death/reboot; goal changes; day/month rollover; timezone changes; large step counts; launcher resize and TalkBack. Confirm all four faces against references on a 2 × 2 launcher grid. No device or screenshot comparison has been performed in this environment.

## Google sign-in on Android

In untracked `local.properties`, set the **web** OAuth client ID from Google Cloud project `step-counter-509310`:

```properties
GOOGLE_WEB_CLIENT_ID=your-web-client-id.apps.googleusercontent.com
# Omit until a local or hosted account service is ready:
# API_BASE_URL=http://10.0.2.2:3000
```

The IDs were not supplied in the implementation request, so no real client IDs are hardcoded. Register the Android client for `dev.stepcounter` with the SHA-1 of the keystore actually signing the installed APK. Release signing needs its own SHA-1 registration. Never put the client secret in Android or `local.properties`.

`BuildConfig.API_BASE_URL` defaults to `http://10.0.2.2:3000`, but network exchange is stubbed unless `API_BASE_URL` is explicitly nonblank in local properties (`API_CONFIGURED`). This lets Credential Manager save a Google profile without requiring a running server. The UI calls this a device profile, not a server session. To connect an existing device profile after configuring the URL, tap **Connect account** in You; this obtains a fresh Google token. Google sign-in still requires Google Play services and internet. Missing client configuration, cancellation and failed exchange leave local steps usable.

When configured, Android posts `{ "provider": "google", "idToken": { "token": "<Google ID token>" } }` to `/api/auth/sign-in/social`. It captures the `set-auth-token` response header and supplies `Authorization: Bearer …` through `AuthRepository.authorizationHeader()` for later APIs. A failed exchange never creates a connected account. Profile, photo URL, ID token and session token are AES-GCM encrypted using an Android Keystore key; backup remains disabled. Sign-out removes them, clears Credential Manager state, and attempts server session revocation. If offline, local sign-out still succeeds and the UI reports that remote revocation could not be confirmed. Sign-out does not revoke Google consent or delete a server account.

HTTP is allowed only for emulator loopback (`10.0.2.2`) / localhost in debug builds. Release builds require HTTPS. Rebuild after changing local properties. Accounts appear in onboarding and You; neither path uploads health data or enables step sync.

References: [Android Credential Manager](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation), [Better Auth Google ID tokens](https://better-auth.com/docs/authentication/google), [bearer sessions](https://better-auth.com/docs/plugins/bearer).

## Phase 3 — Better Auth server scaffold

`server/` is a separate Node 22.12+ TypeScript package: Hono + Better Auth + bearer plugin + Postgres through Drizzle. The web client ID is first in Google's `clientId` array; the Android client ID is also accepted as an ID-token audience. There is no custom Go/JWT authentication service. The checked-in migration creates Better Auth's user, account, session and verification tables, with no step records.

```sh
cd server
npm ci
cp .env.example .env
# Fill local environment values, including a random BETTER_AUTH_SECRET:
# openssl rand -hex 32
npm run db:migrate
npm run dev
# In another terminal:
curl http://localhost:3000/health
npm run build
npm test
```

Start a local Postgres database first and match `DATABASE_URL`. `.env.example` contains placeholders only. Set `BETTER_AUTH_URL`, `BETTER_AUTH_SECRET`, `DATABASE_URL`, `GOOGLE_CLIENT_ID` (web), `GOOGLE_CLIENT_SECRET`, and `GOOGLE_ANDROID_CLIENT_ID`. The existing root `.env.oauth` is ignored and must never be committed or copied into the image. Runtime `npm start` expects environment variables supplied by the host; local `npm run dev` and `npm run db:migrate` read `server/.env`.

| Route | Behavior |
| --- | --- |
| `GET /health` | Public process liveness; does not imply database readiness |
| `GET/POST /api/auth/*` | Better Auth handler; Google sign-in, session lookup and sign-out |
| `GET /api/me` | Requires a valid session; returns profile and `syncEnabled: false`, never tokens |
| `GET /api/friends`, `GET /api/leaderboard` | Require a valid session; return 501 until Phase 4 |

For schema changes, edit `server/src/schema.ts`, run `npm run db:generate`, review and commit the migration, then run `npm run db:migrate` against the intended database. No database or cloud infrastructure is created automatically.

### Railway preparation (code only; not deployed)

The multi-stage `server/Dockerfile` uses Node 22 and runs as the node user. For a future deployment, select `/server` as the Railway service root and `/server/railway.toml` as its config file. Supply the environment above, point `DATABASE_URL` at Postgres, set `BETTER_AUTH_URL` to the public HTTPS origin, and use the web client's Google callback URL `<origin>/api/auth/callback/google` for browser OAuth. The native ID-token flow does not use that callback. `railway.toml` runs the compiled migration as a pre-deploy command and checks `/health`; `PORT` is supplied by Railway. Configure Android's URL to the same origin and rebuild. Do not deploy this scaffold until requested.

Server tests cover route access, bearer header forwarding, profile response redaction, environment validation and real Better Auth rejection of malformed Google tokens. Successful Google sign-in and session revocation still need a device with configured client IDs and a running Postgres-backed server. No live OAuth or deployment validation is claimed.

## Phase 4 (not implemented)

- **Phase 4 — Social experience:** friends, invitations, challenges, optional shared progress widgets, offline/error handling and production hardening. Define consent, abuse prevention and retention before implementation.
