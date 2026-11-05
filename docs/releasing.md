# Releasing and verifying karoo-pace

Releases are built, signed and verified by `.github/workflows/release.yml` when a version tag
is pushed. The keystore and its passwords exist only as GitHub repository secrets: never in
the repo, never in a workflow file. The workflow creates a **draft** release; publishing it
is a manual step. See ADR-0004.

## One-time setup: signing secrets

Add these under Settings, Secrets and variables, Actions, **Repository secrets**:

| Secret | What it holds |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | The release keystore file, base64 encoded |
| `RELEASE_KEYSTORE_PASSWORD` | The keystore password |
| `RELEASE_KEY_ALIAS` | The alias of the signing key inside the keystore |
| `RELEASE_KEY_PASSWORD` | The password of that key |
| `RELEASE_CERT_SHA256` | SHA-256 fingerprint of the signing certificate. Colons and case do not matter |

**Reuse the keystore that signed 0.1.0.** Android refuses an update signed with a different
key, so a new keystore would force every rider to uninstall first.

From the machine that holds the keystore (paths are examples):

```sh
base64 -i release/karoo-pace.jks | tr -d '\n' | pbcopy     # macOS; on Linux: base64 -w0
keytool -list -v -keystore release/karoo-pace.jks -alias <alias> | grep 'SHA256:'
```

Paste the base64 text into `RELEASE_KEYSTORE_BASE64` and the `SHA256:` value into
`RELEASE_CERT_SHA256`. If any secret is missing, the workflow stops before building and names
the missing one.

`GITHUB_TOKEN` is provided by Actions and needs no setup; it reads karoo-ext from GitHub
Packages and creates the draft release.

## Cutting a release

1. On a branch: bump `versionName` **and** `versionCode` in `app/build.gradle.kts`, move the
   CHANGELOG `Unreleased` entries under the new version, and merge through a PR with CI green.
2. Wait for CI on `main` to pass on the merge commit, then tag that commit and push the tag:
   `git tag v0.2.0 && git push origin v0.2.0`. The workflow stops unless the tagged commit is
   on `main`, the latest CI workflow run (`ci.yml`, pushed to `main`) for that commit passed,
   and the tag equals `v` + `versionName`. If CI was still running, re-run the workflow once
   it is green. The gate is `.github/scripts/check-release-gate.sh`; CI runs its tests
   (`test-release-gate.sh`) on every PR.
3. The Release workflow then, in a build job with a read-only token:
   - runs `testReleaseUnitTest` (which includes `ReleaseGuardTest`, so a build with
     `SIMULATE_BANDS` on cannot ship), `lintRelease` and `assembleRelease`;
   - verifies the APK with `apksigner`: exactly one signer, and its certificate SHA-256 equals
     `RELEASE_CERT_SHA256`; the package is `com.hainesy.karoopace` and not debuggable;
   - hands the APK and its `.sha256` to a separate release job, the only one with write
     access, which creates a draft release `vX.Y.Z` with `karoo-pace-X.Y.Z.apk` and its
     `.sha256`. That job runs no build code and never sees the signing secrets.
4. Check the draft, run the device checks below on that APK, then publish it.

## Verifying a downloaded release

```sh
sha256sum -c karoo-pace-X.Y.Z.apk.sha256
apksigner verify --print-certs karoo-pace-X.Y.Z.apk | grep 'SHA-256 digest'
```

The digest must match the release certificate fingerprint. `apksigner` is in the Android SDK
`build-tools`.

## Device verification

Unit tests cover the band decision, redraw timing and lifecycle on the JVM. How the field
looks is only provable on a Karoo. Do this before publishing a release, with the Karoo
plugged in over adb.

1. Desk test the colours: `./gradlew :app:assembleDebug -PsimulateBands=true`, install with
   `adb install -r app/build/outputs/apk/debug/app-debug.apk`. The card cycles through every
   band every 2s.
2. On a profile page, add the field in each layout you ride with. At minimum check the
   measured reference slot (238x126 at 300dpi, where the geometry in `PaceView.kt` was
   taken), plus a full-width and a small slot, with left, centre and right alignment.
3. For each, take a screenshot and compare against a native Speed field on the same page:
   `adb exec-out screencap -p > field.png`. Font, label position, digits ending on the
   bottom edge, and chevron size should match README "How it matches native".
4. With GPS off (or indoors with no fix) the number shows `--`, never `0`, and the card is
   black.
5. Switch the profile to imperial and check the number changes unit.
6. Install the release APK (not debug) and confirm the colour follows real speed, not the
   2s cycle.

No automated screenshot or golden-image test exists yet: Glance RemoteViews have to be
inflated by Android, and the field geometries are the host's, so this list is the check.
