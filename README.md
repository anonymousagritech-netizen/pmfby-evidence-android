# PMFBY Field Evidence — Android (Kotlin / Jetpack Compose)

Native Android app for PMFBY crop-insurance field surveyors: see only your own assigned
farmers, call/SMS them, log every contact attempt as evidence with a GPS geotag, resolve
every non-completed attempt through a mandatory 4-outcome loop, capture emergency
audio+video when facing a hostile farmer, flag suspected fraud, report integrity concerns
confidentially, and work offline with a sync queue.

**This Android app has NOT been compiled or run in this environment.** The sandbox this
was built in has no JDK/Java and no Android SDK — `java -version` fails, and `./gradlew`
itself cannot start (see "Build status in this environment" below). Every file here was
written and reviewed by hand, not verified by a compiler. Treat anything below as a
starting point to build and test for real in Android Studio before trusting it.

## Build status in this environment

```
$ ./gradlew tasks
ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
Please set the JAVA_HOME variable in your environment to match the
location of your Java installation.
```

Gradle's own launcher script needs a JVM just to start — it never got far enough to even
attempt compiling a single Kotlin file, let alone run a test. This is strictly worse than
"no Android device/emulator," which the original project spec already anticipated: there
isn't a JDK either, so not even a JVM-level unit test (`:core:test`) could be executed here,
despite `:core` having real, previously-passing tests from an earlier commit (see
`core/build/test-results` and `core/build/reports/tests`, both gitignored — they're stale
artifacts from whatever environment actually ran them before, not from this session).

## Setup to actually build this

- Android Studio Ladybug (2024.2) or newer, with Android SDK Platform 35 and a JDK 17
  toolchain (the project targets `compileSdk = 35`, `JavaVersion.VERSION_17`).
- Open the `android-kotlin` folder as the project root (it is a separate Gradle root from
  `backend-and-web` and the deprecated React Native app — see the repo's top-level
  structure).
- `local.properties` (gitignored, machine-specific) needs `sdk.dir` pointing at your
  Android SDK; Android Studio creates this for you on first open.
- Minimum SDK 26 (Android 8.0) — chosen for `java.time`, `MediaRecorder(Context)`, and
  modern `TelephonyCallback` availability without desugaring.

## Running it

1. Start the backend (`../backend-and-web/backend`) locally — see its own README/`.env`.
2. Run the app on an emulator: the default `API_BASE_URL` is
   `http://10.0.2.2:4000/api/`, the Android-emulator alias for the host machine's own
   `localhost:4000`. This needs no configuration change for the emulator.
3. Running on a **physical device** on the same LAN instead: override the base URL at
   build time, pointing at your machine's LAN IP:
   ```
   ./gradlew assembleDebug -PapiBaseUrl=http://192.168.1.50:4000/api/
   ```
   (see `app/build.gradle.kts`'s `apiBaseUrl` project property). `usesCleartextTraffic`
   is `true` in the manifest for this plain-HTTP dev setup — remove it and switch to HTTPS
   before any real deployment.
4. Sign in with a surveyor account that already exists in the backend's `users` table.

## Permissions this app requests, and why

| Permission | Why | What happens if denied |
|---|---|---|
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Automatic GPS geotag on every call/visit/emergency event | `LocationProvider` returns `null` (never a fake `0,0`) — the event is still recorded, just without a geotag |
| `CAMERA` | Discrepancy-flag mandatory photo, emergency video | Photo capture / emergency video fails with a visible error; audio-only emergency capture is not a fallback in this pass (see ROADMAP) |
| `RECORD_AUDIO` | Emergency audio+video capture, manual voice notes | Same — capture fails visibly, never silently |
| `READ_PHONE_STATE` | Best-effort call-state observation (`CallStateObserver`) — **not** used as a source of truth for anything | No-ops silently; the app never depended on it firing |
| `CALL_PHONE` | Only requested if the surveyor opts into the instant-call (`ACTION_CALL`) path in Profile — **not requested by default** | Default `ACTION_DIAL` calling path keeps working with zero permission |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CAMERA`, `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS` | The emergency capture foreground service, which Android requires to carry a persistent, visible notification | Emergency recording cannot start (Android will reject the foreground service) |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Talking to the backend API | App falls back to cached/offline behavior where implemented |

`SEND_SMS` is **never** requested — SMS always goes through `ACTION_SENDTO` (`smsto:`),
handing off to the phone's own SMS app with the text pre-filled. This app can never claim
an SMS was actually sent or delivered, only that the composer was opened — see
`telephony/SmsComposer.kt` and LIMITATIONS.md.

## Calling: ACTION_DIAL vs ACTION_CALL

Default path is `Intent.ACTION_DIAL` (`CallLauncher.dial`): opens the phone's own dialer
pre-filled with the number, needs **no permission**, and the surveyor still has to tap
Call themselves — a deliberate last-second cancel point, and the kind of unprotected
intent Play Store review treats routinely.

The opt-in path is `Intent.ACTION_CALL` (`CallLauncher.callDirectly`), gated behind the
`CALL_PHONE` runtime permission and a Profile-screen toggle the surveyor must explicitly
turn on (`SessionManager.preferInstantCall`) — granting the permission alone does not
silently switch calling behavior. It places the call immediately with no extra tap, but
skips that cancel point and is a more sensitive permission for Play Store review.

Neither path gives the app any callback about call duration or whether the call actually
connected — every recorded outcome is `outcome_source = 'user_entered'`, never verified
(see `telephony/CallStateObserver.kt`'s doc and LIMITATIONS.md).

## Architecture

- **`:core`** — plain-JVM Kotlin module, zero Android imports, so its logic is unit
  testable without an emulator or even an Android SDK:
  `idempotency` (key generation, filename sanitization), `rbac` (client-side mirror of the
  backend's scoping, display-only — the server always re-checks), `template` (SMS
  placeholder rendering, preview-only), `timer` (the emergency 120s hard-cap state
  machine), `dto` (JSON→domain mapping), `status` (farmer-list status-tab derivation),
  `http` (upload-retry classification). See `core/src/test/kotlin` for its JUnit 5 tests.
- **`:app`** — the Android application module.
  - `data/local` — Room (`AppDatabase`, entities, DAOs): a local cache of only the signed-in
    surveyor's own assigned farmers/cases, plus the `offline_queue` table.
  - `data/remote` — Retrofit `ApiService` (JSON endpoints, `Map<String, Any?>` bodies
    parsed by `:core`'s `ApiDtoMapper`) and `FileUploadApi` (plain OkHttp multipart, for
    evidence/voice-note/emergency-media uploads).
  - `data/repository` — `AuthRepository`, `FarmerRepository`, `CaseActionsRepository`
    (idempotency-keyed writes, offline-queue fallback on `IOException`).
  - `session` — `SessionManager` (JWT + role/scope, `EncryptedSharedPreferences`).
  - `location` — `LocationProvider` (FusedLocationProviderClient, honest `null` on no fix).
  - `telephony` — `CallLauncher`, `CallStateObserver`, `SmsComposer`.
  - `media` — `VoiceNoteRecorder` (manual post-call voice note).
  - `capture` — `EmergencyCaptureService` (foreground service: CameraX video + audio,
    wraps `:core`'s `EmergencyTimerController` for the 120s hard cap).
  - `work` — `UploadWorker` (WorkManager, network-gated periodic + manual retry of the
    offline queue, including failed multipart evidence uploads).
  - `ui` — every Compose screen + its `ViewModel`, `navigation/AppNavigation.kt`'s NavHost,
    `ui/theme` (the enterprise navy/white/neutral-gray Material3 theme).
  - `PmfbyEvidenceApp` — the hand-wired dependency container (no Hilt/Dagger — see its
    class doc for why that's an acceptable tradeoff at this size; `ROADMAP.md` lists it as
    a candidate for later if the graph grows).

## Screens (one Compose destination per task-spec screen)

Login · Assigned-farmers list (search + 8 status tabs) · Farmer detail · Case detail
(action hub + mandatory outcome resolution + Survey-Completed) · Call + outcome resolution
· Voice-message templates (local playback — see LIMITATIONS.md) · SMS template picker +
review + send-via-composer · Evidence history · Emergency press-to-start capture ·
Suspected-Discrepancy flag (mandatory photo) · Integrity-Concern report (confidential) ·
Offline upload queue · Profile.

See `LIMITATIONS.md` for what is honestly unverifiable or incomplete, and `ROADMAP.md` for
what was deliberately deferred.
