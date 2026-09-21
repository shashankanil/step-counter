# Step Counter

A local-first Android step dashboard with a single swipeable home-screen widget. Quiet charcoal surfaces, crisp totals, selective dots and sparse red accents. Original artwork; no proprietary fonts, screenshot crops or branded assets.

## Build

Use JDK 17 and Android SDK Platform 37.0 / build-tools 36.0.0. Set `sdk.dir` in untracked `local.properties`.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Minimum API 26; Health Connect needs a supported Android 9+ device. The app reads aggregate steps from Health Connect, stores daily totals in Room, and uses WorkManager for optional periodic background reads. It does not count steps itself. Denied access, missing days and unavailable providers are represented honestly; no fake activity or social accounts are seeded.

## The widget

Add **Step Counter** from the launcher or Widgets tab. There is one picker entry, with four vertically swipeable pages:

1. **Walk** — dotted walker on a progress path.
2. **Stats** — today's steps and previous seven completed days' average.
3. **Month** — Monday-first calendar; red today, white goal reached, grey activity.
4. **Comparison** — you and a selected friend or group, simple bars and fetched timestamp.

No profile/login letter chips appear in widget artwork. Tap a page to open the app. Start at 2 × 2 cells; square artwork fits resized bounds.

`StackView` supplies actual vertical gestures. API 31+ uses `RemoteViews.RemoteCollectionItems`; older devices use `StepPageService` / `RemoteViewsFactory`. Both read the same `StepRepository` snapshot and refresh after foreground/WorkManager sync. Collection IDs remain stable across updates. [Android collection widget documentation](https://developer.android.com/develop/ui/views/appwidgets/collections).

Existing installations with the old separate widget providers need to remove their old widgets and add the new Step Counter widget.

## Google identity

Set the **Web** client ID in untracked `local.properties`:

```properties
GOOGLE_WEB_CLIENT_ID=your-web-client-id.apps.googleusercontent.com
# Optional cloud service; emulator loopback example:
API_BASE_URL=http://10.0.2.2:3000
```

Credential Manager uses `GetSignInWithGoogleOption`. Register the Android OAuth client for `dev.stepcounter` and the certificate that signs the installed APK. The current debug certificate SHA-1 is:

```text
F9:77:F2:60:43:97:BB:A5:97:67:9F:FE:9D:9D:1C:0B:F1:D2:93:75
```

The generated BuildConfig was checked against the configured Web ID and confirmed distinct from the Android ID. No client IDs or secrets are committed. Release signing requires its own registered certificate.

After Google returns a credential, the app persists the token's name/email/photo/given name locally in an AES-GCM encrypted Keystore-backed account record, marked `local`. Google account selection itself still needs Google Play services and internet.

When configured, the app posts the ID token to Better Auth's `/api/auth/sign-in/social`, captures `set-auth-token`, marks the session `cloud`, and uses the bearer token for social APIs. An absent or unreachable backend never discards a successful local Google identity. You shows local/cloud status, actionable errors and **Connect account** to retry. Rebuild after URL changes. Debug HTTP is restricted to localhost/emulator loopback; hosted services need HTTPS. [Google sign-in documentation](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation).

Sign-out clears local credentials and attempts Google state clearing and server revocation. Offline revocation failure is reported. It does not delete the server account or revoke step-sharing consent.

## Friends, groups and comparisons

The **Social** tab offers Friends, Groups and Compare:

- Invite an existing cloud account by Google email; only the recipient can accept.
- Create a named group and share its selectable 12-character invite code; join by code.
- Choose an accepted friend or joined group for Compare and the widget. Selection and caches are account-scoped.
- Explicitly enable **Step sharing** to upload daily totals. Sign-in alone never uploads steps.
- Disabling sharing immediately stops local uploads. Server confirmation deletes uploaded totals; if offline, deletion remains visibly pending and retries on Social refresh or the next successful health sync.
- Failed social mutations are not silently queued. Inputs remain available to retry; the UI displays connection/empty states. Comparisons carry a fetched timestamp.

Comparisons show today and the **seven days including today**. Missing totals and incomplete seven-day windows remain unavailable. This differs intentionally from the Stats page's seven **completed** days. Local dates are used; comparisons across time zones are by calendar date, not identical UTC intervals. The widget shows local self totals and up to two other members; full groups appear in the app.

Uploaded totals are visible only to accepted friends or fellow group members. They remain on the server until sharing is disabled; local sign-out/uninstall alone does not delete them. Cached comparisons can remain visible offline with their original fetch time.

## Server

Node **22.12+**, Hono, Better Auth bearer sessions, Postgres and Drizzle:

```sh
cd server
npm ci
cp .env.example .env
# Fill environment values and start Postgres.
npm run db:migrate
npm run dev
npm run build
npm test
```

Environment: `BETTER_AUTH_URL`, `BETTER_AUTH_SECRET`, `DATABASE_URL`, `GOOGLE_CLIENT_ID` (Web), `GOOGLE_ANDROID_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`. Never commit `.env.oauth`, `.env`, `local.properties` or client secrets.

The second migration adds friendships, groups/membership, sharing consent and daily step totals. Run migrations before starting the updated server. Railway/Docker preparation remains available in `server/`; no deployment is performed by this change.

| Route | Purpose |
| --- | --- |
| `GET /health` | Public liveness; does not imply database readiness |
| `GET/POST /api/auth/*` | Better Auth |
| `GET /api/me` | Profile and cloud consent status |
| `GET/POST /api/friends` | List relationships / request by `handle` |
| `POST /api/friends/:id/accept` | Recipient-only acceptance |
| `GET/POST /api/groups` | List memberships / create by `name` |
| `POST /api/groups/join` | Join by `code` |
| `GET/PUT /api/sync` | Read/set `enabled`; opt-out deletes totals |
| `PUT /api/steps` | Consent-gated `days: [{date, steps}]` upsert |
| `GET /api/compare?friend=ID&date=YYYY-MM-DD` | Accepted friend comparison |
| `GET /api/compare?group=ID&date=YYYY-MM-DD` | Member-only group comparison |

All social routes require a valid session; Android sends `Authorization: Bearer …`. SQL is parameterised. Uploads and opt-out serialise on the consent row, preventing uploads after completed revocation.

## Data and app structure

- `ui/`: Today, History, Widgets, Social, You and onboarding.
- `widgets/`: single provider, backwards-compatible collection service and shared Canvas artwork.
- `data/auth/`: Credential Manager, encrypted identity/session storage.
- `data/social/`: authenticated API access, scoped consent, target and comparison cache.
- `data/health/`, `data/db/`, `data/StepRepository.kt`: Health Connect aggregate reads and local Room cache.
- `server/src/social.ts`: authenticated social routes; `server/src/schema.ts` / `server/drizzle/`: persistence.

Health sync re-reads 30 days, retaining at most 63 local calendar days. Missing days are not zero. Time-zone changes invalidate the displayed cache until re-aggregation. WorkManager requests a 30-minute interval, subject to Android scheduling. No exact midnight or real-time refresh is promised. Backup and transfer of app data are disabled.

## Verification and remaining device checks

Android debug assembly, eight domain unit tests and lint are release checks. Server tests include existing auth checks and an end-to-end social route test using an embedded PostgreSQL engine (PGlite), applying both real migrations and testing requests, access control, groups, consent, missing totals, idempotent uploads and deletion.

No Android device is attached in this environment. Before release, exercise:

- Launcher vertical flicks through all four pages, resize, TalkBack and process restart.
- Real Google account selection, backend-offline local identity, then online Better Auth exchange.
- Two real accounts: request/accept, group join, consent, uploaded comparisons and offline opt-out retry.
- Health Connect denial/revocation, background access, date rollover and time-zone changes.

Live Google OAuth and a deployed Postgres-backed service remain unverified. The server is a demo scaffold: group removal, friend removal/blocking, abuse controls and account deletion are not yet implemented. No GitHub release is created and no APK is copied as part of this work.
