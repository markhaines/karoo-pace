# karoo-pace

Dev tier: 3 (public)

<!-- Screenshot slot: adb device screenshot of the coloured field mid-ride, docs/field.png.
     Grab one next time the Karoo is plugged in (SIMULATE_BANDS makes it easy off the bike). -->

A drop-in replacement for the Speed card on your Hammerhead Karoo 3: same number, same look,
but the card colours itself by how your current speed compares to your ride average. A glance
tells you whether you're up or down without reading the number mid-effort. Built to be
indistinguishable from the native field apart from that colour — same font, same metrics, same
rules.

[![Latest release](https://img.shields.io/github/v/release/markhaines/karoo-pace)](https://github.com/markhaines/karoo-pace/releases/latest)
[![License](https://img.shields.io/github/license/markhaines/karoo-pace)](LICENSE)

[Install](#install) · [Verify without riding](#verify-without-riding) · [How the colour works](#how-the-colour-works)

## How the colour works

| Speed vs ride average | Card | Chevron |
|---|---|---|
| ≥ 115% | `#32E09A` `success_green_500` | ⌃⌃ |
| 105–115% | `#8CF2C9` `success_green_400` | ⌃ |
| 95–105% | black, white text | — |
| 85–95% | `#FEB07F` `strava_orange_400` | ⌄ |
| < 85% | `#FF5252` `red_500` | ⌄⌄ |

Bands are percentages of the average rather than fixed km/h, so they stay meaningful whether
you're grinding up a climb or flying along a flat. No colour below 5 km/h, or before the ride
average has established itself. Colour follows the **3s smoothed** speed stream while the number
shown is the **raw** one, so the field reacts instantly but the colour doesn't strobe — raw GPS
speed wanders by 1–2 km/h at steady effort, which is wider than the neutral band.

All thresholds are constants in `PaceBands.kt`, which is pure Kotlin and unit tested.

## Install

No computer needed — the Karoo installs it through the Hammerhead Companion app on your phone
(Karoo 3, firmware 1.527 or later):

1. On your phone, open the [latest release](https://github.com/markhaines/karoo-pace/releases/latest)
   and long-press the `.apk` file.
2. Share the link with the **Hammerhead Companion** app — it pushes the extension to your Karoo.
3. Reboot the Karoo once, then add the field to any ride profile page.

After that, updates install the same way, no reboot needed.

<details>
<summary>Build from source instead</summary>

**Requirements:** JDK 17, the Android SDK, and `io.hammerhead:karoo-ext` in mavenLocal (clone
`hammerheadnav/karoo-ext` at the matching tag and run `./gradlew :lib:publishToMavenLocal` — no
GitHub Packages token required).

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
./gradlew :app:testDebugUnitTest      # band logic, no device needed
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Installing is enough for the field to re-render; a reboot is only needed the first time the
extension is added, so the Karoo picks up the service. Debug builds install as a separate app
id (`.debug` suffix), so they sit alongside a release install rather than replacing it.
</details>

## Verify without riding

Set `SIMULATE_BANDS = true` in `SpeedVsAverageDataType.kt` to cycle through every colour band
every two seconds, so you can check the field on your desk before taking it out. Turn it off
before your ride.

## How it matches native

Everything below was measured off the device rather than judged by eye — screenshot over adb,
then scan pixel columns for glyph bands. Reach for that loop rather than nudging constants.

- **Font**: the Karoo remaps `sans-serif` to IBM Plex Sans and registers `ibm-sans-cond`
  (IBM Plex Sans Condensed Medium) in `/system/etc/fonts.xml`. Native data fields draw numbers in
  the condensed face; plain sans-serif gives noticeably wider digits.
- **Palette**: pulled from the device's own ride app —
  `adb pull /system/priv-app/ride/ride.apk` then `aapt2 dump resources`. Native zone-coloured
  fields are *always* black text on a light tint; the text colour never tracks the value.
- **Geometry** (238×126 slot at 300dpi): label glyphs 17px from the card top in a 20px band,
  number at exactly `ViewConfig.textSize` (46sp) with digits ending on the bottom edge, icon
  23×18px. Because a Text's line box includes descent that digits never use, the number is
  offset from the *top* and allowed to overflow the bottom, where it clips — bottom-aligning
  instead leaves a gap native doesn't have.

There is no way to have the host draw the number *and* colour the card: the one hook for it,
`UpdateGraphicConfig(formatDataTypeId = ...)`, renders the number underneath the extension's own
graphic, so any opaque background hides it. Verified on a real ride. Hence replication.

## License

Apache-2.0 — see [LICENSE](LICENSE).
