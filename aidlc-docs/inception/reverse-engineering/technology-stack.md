# Technology Stack

## Programming Languages
- **Kotlin** — JVM target 11; `kotlin = "2.0.21"`

## Platform
- **Android** — `minSdk 29` (Android 10), `targetSdk 36`, `compileSdk 36`

## Frameworks
- **Jetpack Compose** — Compose BOM `2024.09.00`; `androidx.compose.ui`,
  `ui-graphics`, `ui-tooling-preview` (debug: `ui-tooling`, `ui-test-manifest`)
- **Material 3** — `androidx.compose.material3`
- **AndroidX Activity Compose** — `1.8.0`
- **Lifecycle Runtime KTX** — `2.6.1`
- **AndroidX core-splashscreen** — `1.0.1`
- **AndroidX core-ktx** — `1.13.1`
- **AndroidX appcompat** — `1.7.0`
- **Material Components (legacy `com.google.android.material`)** — `1.12.0`
  (declared; minimal direct usage — Compose Material 3 is the primary surface)

## Networking
- **Retrofit 2** — `2.9.0`
- **Gson Converter** — `2.9.0`
- **OkHttp logging-interceptor** — `4.12.0` (declared but not currently
  installed on the OkHttpClient builder)

## Hardware APIs
- **Android NFC API** — `android.nfc.*` (NDEF; foreground dispatch); no
  third-party NFC library

## JSON
- **`org.json.JSONObject`** (Android stdlib) — used for OpenSpool encode/decode
- **Gson** — used only for Spoolman API responses

## Build Tools
- **Gradle (Kotlin DSL)** with version catalog (`gradle/libs.versions.toml`)
- **Android Gradle Plugin** — `8.13.2`
- **Kotlin Compose plugin** — `org.jetbrains.kotlin.plugin.compose`

## Testing Tools
- **JUnit 4** — `4.13.2` (test deps declared, no tests present)
- **AndroidX JUnit ext** — `1.1.5`
- **Espresso Core** — `3.5.1`
- **Compose UI Test (junit4 + manifest)**

## Distribution / Signing
- Local keystore at `~/spoolpainter-release-key.jks`
- Password from env `KEYSTORE_PASSWORD` or `~/spoolpainter-keystore.pwd`
- `applicationId = "com.spoolpainter.app"`; `debug` variant suffixes `.debug`
  for side-by-side install
