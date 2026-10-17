# Limitations and risks

Written honestly, on purpose: this file exists so nobody mistakes "written" for "verified."

## Nothing in this module has been compiled or run

No JDK/Android SDK exists in the environment this was built in — `./gradlew tasks` fails
immediately with `JAVA_HOME is not set and no 'java' command could be found`, before
Gradle even attempts to resolve dependencies or compile a single file. Every Kotlin file
under `app/` and the new additions to `core/` were written and then reviewed by hand for
imports, syntax, and API usage against the declared dependency versions — not checked by a
compiler, not run, no test executed. `core/`'s previously-passing test results
(`core/build/test-results`, `core/build/reports/tests` — both gitignored, so they don't
appear in `git status`) are stale artifacts from some earlier environment that actually had
a JDK; they say nothing about whether the code added in this pass compiles.

Concretely, this means:
- There could be a typo, a wrong import, a signature mismatch, or an API used incorrectly
  for the pinned library version, anywhere in this module, and nothing here would have
  caught it.
- The Jetpack Compose code in particular is the least exercised category — Compose APIs
  (especially newer Material3 ones like `PullToRefreshBox`, `ExposedDropdownMenuBox`) have
  shifted signatures across versions, and the version actually resolved by Gradle could
  differ subtly from what was assumed while writing this.
- Room's generated schema (annotation processing via KSP) has never actually run against
  these entities — a `@PrimaryKey`/column type mistake would only surface at build time.

**Build and run this for real, in Android Studio, before trusting any of it.**

## Call-recording reliability — there is none

This app has no ability to record, or confirm, what happened on an actual phone call.
`ACTION_DIAL`/`ACTION_CALL` hand off entirely to the system dialer; Android gives
third-party (non-default-dialer) apps no reliable callback for call connection, duration,
or outcome. `CallStateObserver` is explicitly a best-effort, cosmetic hint that is allowed
to simply never fire on a given OEM/Android version, and no code path in this app treats
its output as a source of truth. Every call outcome recorded is `outcome_source =
'user_entered'` — the surveyor's own account of what happened, never a verified fact.

## SMS-delivery confirmation — there is none

`SmsComposer` opens the system SMS app with `ACTION_SENDTO` and the text pre-filled; this
app has no `SEND_SMS` permission and never sends a message programmatically. The furthest
this app can honestly claim is "the composer was opened" (`status = 'opened_composer'`),
matching the backend's own `message_events.status` values (`drafted` / `opened_composer`,
never `sent`/`delivered`). Whether the surveyor actually pressed Send in their SMS app, and
whether the carrier actually delivered it, is unknowable from here.

## Locked-screen / background emergency capture — not implemented, not attempted

The emergency press-to-start flow only ever runs as an explicit foreground action while
the app is open. Android requires a persistent, visible notification for any foreground
service, which is itself the honest answer to "can this be covert?" — no. If the screen
locks or the app is killed, recording stops; there is no attempt anywhere in this codebase
to capture audio/video covertly, in the background, or past a locked screen. A surveyor
facing an active threat cannot rely on this app continuing to record if they lock their
phone or the app is killed by the OS.

## Automated voice-message injection into a live call — not implemented, not attempted

The Voice Message screen is **local playback only** — "play this prerecorded message to
the farmer on speaker," never "inject this audio into an ongoing cellular call." Android
gives third-party apps no API to do the latter over a standard cellular (non-VoIP) call;
building it would require either carrier-level integration or hooking an audio
accessibility/telecom API this app does not and should not claim to have. See also
ROADMAP.md: even the "local playback" half isn't fully real yet, since no prerecorded
audio assets or admin-upload pipeline exist on the backend to download from.

## Emergency capture — what it honestly is and isn't

`EmergencyCaptureService` wraps real CameraX (video+audio) recording and `:core`'s
`EmergencyTimerController` for the 120-second hard cap (unit tested there against a fake
clock — see `core/src/test/kotlin/.../timer/EmergencyTimerControllerTest.kt`). What's
*not* verified: that CameraX actually binds and records correctly on a real device across
OEMs, that the 1-second `Handler` ticker stays accurate under real load, or that the
crash-recovery cleanup (partial-file deletion on cancel) behaves correctly when the
process is actually killed mid-recording rather than cleanly stopped — there is no
WorkManager-based "orphaned partial file" sweep implemented yet (see ROADMAP.md).

## Farmer-list status tabs are an approximation, not ground truth

"Call not answered" and "SMS sent" are derived from `lastCallOutcome`/`lastMessageStatus`
fields this device writes to its own local cache the moment *this device* successfully
submits a call outcome or SMS-composer-opened event — never from the server. A case another
surveyor's device (or the web admin) already acted on, viewed fresh on a newly-installed
app before this device makes its own first attempt, will show as "Assigned" even if it
genuinely has history elsewhere. See `core/src/main/kotlin/.../status/CaseStatusMapper.kt`'s
class doc for the full reasoning.

## Discrepancy-flag photo upload cannot be queued offline

Every other write in this app (call events, message events, emergency incidents) falls
back to the local offline queue on a network error and is retried later with the same
idempotency key. Raising a discrepancy flag cannot: the backend requires a real,
server-issued `evidenceFileId` from a successful photo upload *before* the flag itself can
even be created, so a network failure here fails visibly rather than silently queuing
something that would reference a file id that doesn't exist yet.

## Instrumented (on-device) tests do not exist

Only plain-JVM JUnit tests exist (`:core`, pure logic). No Espresso/Compose UI tests were
written for the `:app` module — writing them without ever having run a single build would
be pure guesswork about what the generated test scaffolding even looks like for this
project's exact dependency versions, so none were added rather than faking coverage.
