# Step Counter · Phase 2

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
- **You:** editable local avatar initial, daily goal, Health Connect access review, optional background permission, privacy and about. Avatar and goal changes refresh all four widgets.
- **First run:** connect → choose a goal → pin a widget. Setup can be deferred without granting access; existing Phase 1 installations with step access skip it. Setup state survives restarts.

The shell is edge-to-edge black with charcoal rounded cards, a custom pill navigation bar, tracked labels, quiet secondary text and sparse red markers. Matrix glyphs are shared with `WidgetArtwork`; the Today orbit is native Compose Canvas so progress can animate without allocating a bitmap each frame. Compose Navigation supplies subtle destination fades and retained tab state. Health reads run in an activity-scoped ViewModel, avoiding duplicate work on rotation. Interactive calendar days expose spoken dates/totals, artwork exposes metric descriptions, and the sync status is a polite live region. Font scaling, contrast and screen-reader traversal still need device testing.

No artificial/sample step totals are displayed. `--` means unavailable, including averages with missing days. You can explore the app and pin widgets before connecting. Background refresh remains Android-scheduled and optional, not continuous tracking.

## Connect & permissions

1. Open Step Counter. Install/update Health Connect when prompted (built into supported newer Android versions).
2. Allow **read steps**. This app does not write health records. Connect a step-producing source to Health Connect if your device/provider does not supply steps itself.
3. Optionally allow **background health reads**, offered only when the provider supports that feature. Without it, reads happen while the app is open; widgets retain the local cache.
4. Save a daily goal (default 10,000; range 100–100,000). Refresh to see today's totals and last successful sync time.

Both the legacy permission-rationale activity and Android 14+ permission-usage alias are registered. The privacy screen explains local storage and revocation. There is no network permission, backend, Google Fit REST, account, analytics or iOS implementation. Android backup is disabled; explicit Android 12+ extraction rules also exclude local files, totals and preferences from cloud backup and device transfer. Clear app storage/uninstall to remove cached data; detected read-permission revocation clears totals on the next sync. Production store distribution will additionally require the appropriate Health Connect/Play declarations and a hosted privacy policy.

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

Tap any face to open the app. Page indicators are visual references to the separate faces; there is no widget swipe carousel. The avatar chip uses your editable local initial (A–Z), not a social profile. Tiny month dots represent zero, missing or future days; leading/trailing cells are blank. Six-week months are supported without obscuring dates or weekday labels. Glance hosts original Canvas bitmap artwork with a spoken content description; no proprietary font is needed. The Widgets tab previews all four live faces and offers launcher pinning.

## Structure

- `ui/`: Compose Navigation shell, lifecycle-aware ViewModel state, Today/History/Widgets/You, onboarding, shared Canvas art and styled privacy rationale.
- `widgets/`: four Glance widgets/receivers and shared original Canvas artwork.
- `data/health/`: provider availability, permissions, daily aggregate requests.
- `data/db/`: Room entity, DAO and database; KSP exports schemas on build.
- `data/`: repository, cache/goal policy and WorkManager sync → widget refresh.
- `domain/`: summary math and Monday-first calendar mapping.

## Verification

Local validation: `assembleDebug`, `testDebugUnitTest` (8 tests) and `lintDebug` pass with Temurin 17/API 37.0. XML parsing and `git diff --check` pass. Lint has no errors; remaining warnings concern API-specific XML attributes, dependency update suggestions and optional KTX style. KSP was updated to 2.3.5 to fix the inherited AGP 9 built-in Kotlin source-set incompatibility. The generated Room v1 schema is checked in; the database layout is unchanged.

Unit tests cover unknown-vs-zero, seven completed days, over-goal progress, six-row calendar, leap year, DST boundaries and recorded streaks (today incomplete, missing history, current-goal changes). The CI template builds/tests/lints once enabled. Before release, check on physical Android 9–13 and 14+ devices: fresh install; unsupported/update-required provider; denial/revocation; background permission denied/granted; no-data provider; overlapping sources; process death/reboot; goal changes; day/month rollover; timezone changes; large step counts; launcher resize and TalkBack. Confirm all four faces against references on a 2 × 2 launcher grid. No device or screenshot comparison has been performed in this environment.

## Later phases (not implemented)

- **Phase 3 — Social foundation:** separately designed Railway-hosted Go API + Postgres, explicit opt-in accounts, minimal shared aggregates, authentication, privacy/deletion controls. Never upload raw Health Connect records by default.
- **Phase 4 — Social experience:** friends, invitations, challenges, optional shared progress widgets, offline/error handling and production hardening. Define consent, abuse prevention and retention before implementation.
