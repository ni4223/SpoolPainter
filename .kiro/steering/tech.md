---
inclusion: always
---

# SpoolPainter — Tech Stack

## Platform
- **Android** — `minSdk 29`, `targetSdk 36`, `compileSdk 36`
- **Kotlin** — JVM target 11
- **Build system** — Gradle (Kotlin DSL), version catalog at `gradle/libs.versions.toml`

## UI
- **Jetpack Compose** with Material 3 (`androidx.compose.material3`)
- Theme defined in `app/src/main/java/com/spoolpainter/app/ui/theme/Theme.kt`
- Single Activity (`MainActivity`), Compose-only screens

## Architecture
- **MVVM**: `MainViewModel` exposes state to Compose screens
- Layers (current):
  - `ui/` — Compose screens, components, theme
  - `domain/models/` — data classes (`OpenSpoolData`, `FilamentSpool`,
    `SpoolmanModels`, `NfcTag`, `Material`, `NfcResult`, `AppState`)
  - `hardware/nfc/` — `NfcManager`, `NfcController`, `NfcHandler`
  - `data/local/` — `MaterialDatabase`, `BrandDatabase` (in-memory presets)
  - `data/remote/spoolman/` — `SpoolmanService` (Retrofit)

## Networking
- **Retrofit 2.9.0** + Gson converter, OkHttp logging interceptor
- `usesCleartextTraffic="true"` (Spoolman is typically self-hosted on LAN over HTTP)

## NFC
- Native Android NFC API (`android.nfc.*`), no third-party lib
- NDEF format, OpenSpool data spec
- Permission: `android.permission.NFC`, `uses-feature` required

## Testing
- JUnit 4, Compose UI test, Espresso — currently minimal coverage; v2 should
  expand this.

## Build variants
- `debug` — installs as `com.spoolpainter.app.debug` alongside prod for
  side-by-side testing during the v2 rewrite
- `release` — signed with local keystore at `~/spoolpainter-release-key.jks`
  (password from env `KEYSTORE_PASSWORD` or `~/spoolpainter-keystore.pwd`)

## Conventions
- Package: `com.spoolpainter.app.*`
- Compose previews live next to components (`@Preview` annotations)
- Don't add comments that just restate the code; only document non-obvious
  invariants (NFC payload formats, OpenSpool spec quirks, Spoolman API
  contract details)
