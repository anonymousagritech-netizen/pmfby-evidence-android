# Roadmap — explicitly deferred

Things left out of this pass on purpose, as clearly-commented stubs/TODOs rather than
faked behavior, or not attempted at all. See `LIMITATIONS.md` for what's risky about what
*was* built; this file is about what wasn't.

## Deferred entirely (out of scope for this pass)

- **Push notifications** — no FCM/notification-on-assignment wiring. A surveyor only
  learns about new/changed assignments by opening the app and refreshing.
- **OCR** — not applicable to this app (it belongs to the separate crop-insurance document
  pipeline elsewhere in the broader project, not this survey app).
- **Multi-language UI** — every string in every screen is hardcoded English in the Kotlin
  source, not pulled from `strings.xml`/resource qualifiers. Real localization would need
  both the resource-extraction work and actual translated copy, neither started here.
- **Telephony-provider integration for auto-injected voice messages** — see
  `LIMITATIONS.md` "Automated voice-message injection into a live call." Would require
  carrier-level or Telecom-API integration this app doesn't have, and arguably shouldn't
  attempt without a much more serious review of what's actually possible/permitted.
- **Admin-managed prerecorded voice-message audio pipeline** — the Voice Message screen
  lists template names but has nothing to actually download and play, because the backend
  (`message_templates` table/routes as reviewed) models text SMS templates only, with no
  column or endpoint for an uploaded audio asset per template. Building this needs backend
  work (a storage column + upload endpoint) before the Android side has anything real to
  fetch.
- **Instrumented/Espresso/Compose UI tests** — see `LIMITATIONS.md`'s last section.
- **Proper Room `Migration`s** — `AppDatabase` uses `fallbackToDestructiveMigration()`,
  acceptable only because nothing has ever shipped from this module. Replace with real
  versioned migrations before any actual release, or local data will be silently wiped on
  every future schema change.
- **Dependency injection framework (Hilt/Dagger)** — `PmfbyEvidenceApp` is a hand-wired
  singleton container. Fine at this size; revisit if the dependency graph grows
  meaningfully past what's here now.

## Partially real, needs finishing

- **Emergency-capture crash recovery** — the in-session cancel/cleanup path (user taps
  Cancel) deletes the partial file correctly in the code as written. What's NOT
  implemented: detecting and cleaning up an orphaned partial file left behind by an actual
  process death mid-recording (not a clean cancel) on the *next* app launch — e.g. a
  WorkManager one-time job on `Application.onCreate` that sweeps `cacheDir/evidence/` for
  files older than some threshold with no matching `evidence_files`/`offline_queue` row.
- **Farmer-list "Call not answered" / "SMS sent" tabs** — see `LIMITATIONS.md`'s
  "approximation, not ground truth" section. A real fix means the backend exposing each
  case's most recent call/message attempt status on `GET /farmers` or `GET /farmers/:id`
  (a join this app's reviewed routes didn't have), not something fixable client-side alone.
- **Offline queue for discrepancy-flag photos** — see `LIMITATIONS.md`. Possibly fixable by
  having the backend accept a flag creation with an idempotency key *and* a pending/
  not-yet-uploaded evidence reference, resolved asynchronously — a backend design change,
  not just a client one.
- **CALL_PHONE instant-call path** — `CallLauncher.callDirectly` exists and is wired to a
  Profile toggle, but was never exercised on a device (no SDK here). Verify the permission
  prompt flow and that it actually places a call before relying on it.

## Worth reconsidering once this actually builds

- Revisit `PullToRefreshBox`/`ExposedDropdownMenuBox` and other Material3 APIs used here
  against whatever exact `material3` version Gradle actually resolves under compose-bom
  2024.12.01 — these are exactly the kind of API that shifts signature across minor
  versions, and this was written against documentation/memory, not a successful build.
- Consider whether `WorkManager`'s periodic 15-minute retry interval is the right cadence
  for a genuinely low-connectivity field deployment — it was picked as a reasonable
  default, not derived from a real-world test.
