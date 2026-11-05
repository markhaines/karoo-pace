# ADR-0004: Build and sign releases in CI from GitHub secrets, drafted not published

- **Status:** Accepted
- **Date:** 2026-11-05
- **Supersedes:** none

## Context

Releases were built and signed on Mark's machine from a gitignored keystore, with nothing
checking what was signed or whether the desk-test switch `SIMULATE_BANDS` was left on: it was
a `const` in source that had to be remembered (issue #3). The README sends riders straight to
the latest release APK, so a wrong build reaches the bike.

## Decision

A pushed `vX.Y.Z` tag runs the Release workflow, which goes no further unless the tagged
commit is on `main` and passed CI. Its build job, holding only a read-only token, restores the
keystore from repository secrets into the runner's temp directory, builds, runs the release
unit tests and lint, then verifies the APK with `apksigner` against a pinned certificate
fingerprint (also a secret) and hands the APK to a separate release job. That job alone has
write access, runs no build code, never sees the signing secrets, and creates a draft release.
The keystore and passwords exist only as secrets; the Gradle build reads them from
`KAROO_PACE_*` environment variables, or from the gitignored `keystore.properties` for a local
build. `SIMULATE_BANDS` is a `BuildConfig` field: opt-in for debug builds with
`-PsimulateBands=true`, hard-coded false for release, and checked by `ReleaseGuardTest` in the
release unit tests.

## Consequences

Losing the secrets means losing the ability to release updates that install over the old
ones, so the keystore still needs an offline backup. Publishing stays a human step: the draft
is where the device checks in `docs/releasing.md` happen. Workflow actions are pinned to
commit SHAs and only move through Dependabot PRs.

## Options rejected

- **Publish the release automatically.** Nothing automated can check how the field looks on
  the device, so a person publishes.
- **Keep `SIMULATE_BANDS` as a source constant with a lint or grep check.** A build-type flag
  makes the release case impossible rather than detected.
