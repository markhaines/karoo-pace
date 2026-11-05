# Changelog: karoo-pace

All notable changes. Semver, tagged `vMAJOR.MINOR.PATCH`.

## Unreleased

### Added
- CI on every push to `main` and every pull request: the `PaceBands` unit tests, Android Lint
  (fails on any error) and a debug build. The tests existed; nothing ran them.
- A Releases section in the README, and this changelog. Completes Dev tier 3.
- A Release workflow: a pushed `vX.Y.Z` tag builds the APK signed from GitHub secrets,
  verifies the signature against the release certificate, and drafts a GitHub release with
  the APK and its SHA-256 (#3, ADR-0004). Steps in `docs/releasing.md`.
- Lifecycle tests: several views at once, extension teardown, binder death in one view, and
  errors, plus the shared Karoo connection's connect and disconnect (#3).
- Dependabot for workflow actions and Gradle dependencies (#3).

### Security
- CI actions pinned to commit SHAs, the Gradle wrapper jar validated, checkout without
  persisted credentials (#3).

### Changed
- `GLOSSARY.md` became `CONTEXT.md`, in the domain-modeling skill's format.
- Band edges have hysteresis: the colour holds until the 3s speed is 1 percentage point past
  an edge, and down to 4 km/h at the 5 km/h cut-off, so the card no longer flips on an edge
  (#2, ADR-0002).
- The field redraws at most once a second, always with the newest reading, matching the
  Karoo's 1Hz limit on extension views (#1, ADR-0003).
- `SIMULATE_BANDS` is now a debug-only build flag (`-PsimulateBands=true`) instead of a
  constant to edit, and is always off in release builds (#3).

### Fixed
- A missing raw speed showed `0`; it now shows `--` like native fields (#2).
- NaN, infinite and negative speeds or averages could colour the card (NaN fell through to
  the slowest band). They now count as no reading and leave it neutral (#2).
- Bursts of telemetry pushed a redraw per update, so the Karoo dropped most of them and could
  show a stale frame. Stream callbacks are now conflated, so old readings cannot queue (#1).
- When the host binder died the field kept rendering into it. Rendering now stops and every
  Karoo stream consumer is unregistered; the view's cancel also stops its setup work (#1).
- A dead host binder during view setup was logged and ignored, so with no telemetry yet the
  view waited forever with its consumers registered. It now ends the view and unregisters
  them, and a binder death while drawing cancels setup still in flight (#1).
- Extension teardown left views running; it now stops every view before disconnecting from
  the Karoo, and an unexpected error in one view is logged instead of crashing the extension
  (#3).

## 0.1.0 — 2026-08-12

### Added
- A Karoo 3 speed data field coloured by pace against the ride average, with the band
  decision as a pure, unit-tested function (`PaceBands`).
- Release signing and install through the Hammerhead Companion app; README restructured
  feature-first. Apache 2.0 licence.
