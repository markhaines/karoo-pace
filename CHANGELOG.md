# Changelog: karoo-pace

All notable changes. Semver, tagged `vMAJOR.MINOR.PATCH`.

## Unreleased

### Added
- CI on every push to `main` and every pull request: the `PaceBands` unit tests, Android Lint
  (fails on any error) and a debug build. The tests existed; nothing ran them.
- A Releases section in the README, and this changelog. Completes Dev tier 3.

### Changed
- `GLOSSARY.md` became `CONTEXT.md`, in the domain-modeling skill's format.
- The field redraws at most once a second, always with the newest reading, matching the
  Karoo's 1Hz limit on extension views (#1, ADR-0003).

### Fixed
- Bursts of telemetry pushed a redraw per update, so the Karoo dropped most of them and could
  show a stale frame. Stream callbacks are now conflated, so old readings cannot queue (#1).
- When the host binder died the field kept rendering into it. Rendering now stops and every
  Karoo stream consumer is unregistered; the view's cancel also stops its setup work (#1).

## 0.1.0 — 2026-08-12

### Added
- A Karoo 3 speed data field coloured by pace against the ride average, with the band
  decision as a pure, unit-tested function (`PaceBands`).
- Release signing and install through the Hammerhead Companion app; README restructured
  feature-first. Apache 2.0 licence.
