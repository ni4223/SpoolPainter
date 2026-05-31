# SpoolPainter v2.0 — Build Instructions

## Prerequisites

### Build Tool
- **Gradle**: 8.14.3 (Kotlin DSL; wrapped in `gradlew` — do not install separately)
- **Android Gradle Plugin**: 8.x (per `gradle/libs.versions.toml`)

### JDK
- **Java 17 (Temurin / Adoptium)** for `compile*Kotlin` and Gradle daemon. JDK 24 is also tolerated by Gradle 8.14.3.
- `JAVA_HOME` must point at a JDK 17+. Verify with `java -version` (`17.x.x` or `24.x.x` are known good).
- Compile target is JVM 11 per `app/build.gradle.kts` `compileOptions { sourceCompatibility = JavaVersion.VERSION_11 }`. The toolchain JDK only needs to be ≥ 17; the produced bytecode is JVM 11.

### Android SDK
- **compileSdk**: 36, **targetSdk**: 36, **minSdk**: 29
- Path configured via `local.properties` `sdk.dir=...` (Homebrew install at `/opt/homebrew/share/android-commandlinetools/` works fine).
- `platform-tools` (for `adb`) and `build-tools` (for `aapt`) are required if doing on-device install / APK inspection.

### Signing (release builds only)
- **Keystore**: `~/spoolpainter-release-key.jks` (RSA 2048, alias `spoolpainter`)
- **Password**: read from `KEYSTORE_PASSWORD` env var if set, otherwise from `~/spoolpainter-keystore.pwd` (single line, trimmed).
- See `app/build.gradle.kts` `signingConfigs.release` for the resolution order.
- Keystore is **local only** — not in repo, not in any backup the project owns. **Loss = irreversible Play Store decision** (cannot rotate signing key on Play Store-signed apps without app-signing transfer).

### System Requirements
- **OS**: macOS, Linux, or Windows (project developed on macOS Darwin 24.6.0)
- **Memory**: 4 GB RAM minimum (8 GB recommended for R8 release builds)
- **Disk**: ~3 GB for `app/build/` + Gradle caches (`~/.gradle/caches/`)

### Environment Variables
- `JAVA_HOME` — path to JDK 17+ (mandatory)
- `ANDROID_HOME` / `ANDROID_SDK_ROOT` — optional; `local.properties` `sdk.dir` takes precedence
- `KEYSTORE_PASSWORD` — optional override for the password file

---

## Build Steps

### 1. Clone & Configure
```bash
git clone <remote-url> SpoolPainter
cd SpoolPainter
# Generate local.properties if not present
echo "sdk.dir=$ANDROID_HOME" > local.properties
# OR use Android Studio "Open Project" — IDE auto-creates local.properties
```

### 2. Build Debug APK (development)
```bash
./gradlew :app:assembleDebug
```
- **Time**: ~1–2 min cold, ~10s warm
- **Output**: `app/build/outputs/apk/debug/app-debug.apk` (~64 MB unminified)
- **Application ID**: `com.spoolpainter.app.debug` — installs side-by-side with the production app (`com.spoolpainter.app`).

### 3. Build Signed Release APK (testing-track / production)
```bash
./gradlew :app:assembleRelease
```
- **Time**: ~1.5 min cold, ~45s warm
- **Output**: `app/build/outputs/apk/release/app-release.apk` (~6.9 MB after R8 minify + resource shrink)
- **Application ID**: `com.spoolpainter.app` (same as v1 production — in-place upgrade)

### 4. Build Release Bundle (Play Store upload)
```bash
./gradlew :app:bundleRelease
```
- **Output**: `app/build/outputs/bundle/release/app-release.aab` (~7.6 MB AAB)
- Upload this to Play Console testing-track, **not** the APK.

### 5. Build All Outputs
```bash
./gradlew :app:assembleDebug :app:assembleRelease :app:bundleRelease
```

---

## Verify Build Success

### Expected Output (assembleRelease)
```
> Task :app:minifyReleaseWithR8
> Task :app:packageRelease
> Task :app:assembleRelease

BUILD SUCCESSFUL in 1m 16s
55 actionable tasks: 55 executed
```

### Build Artifacts
| Variant | Path | Approx Size |
|---|---|---|
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` | 64 MB |
| Release APK | `app/build/outputs/apk/release/app-release.apk` | 6.9 MB |
| Release Bundle | `app/build/outputs/bundle/release/app-release.aab` | 7.6 MB |
| Mapping File | `app/build/outputs/mapping/release/mapping.txt` | ~1 MB |

The mapping file deobfuscates R8 stack traces — **upload to Play Console along with the AAB** so testing-track crash reports stay readable.

### Common Warnings (acceptable, non-blocking)
- `'fun Modifier.menuAnchor(): Modifier' is deprecated. Use overload that takes MenuAnchorType and enabled parameters.` — Compose Material3 deprecation; surfaces in 6 picker sites. Tracked for v2.1 cleanup.
- `'var statusBarColor: Int' is deprecated.` — Android 15+ edge-to-edge migration; `Theme.kt` line 62. Not user-visible.
- `R8 Type T not present` if running tests under JDK before 8.14.3 — fixed by Gradle 8.14.3 wrapper bump (see `aidlc-docs/ui-followups.md` UI-32).

---

## Troubleshooting

### Build fails with `JAVA_HOME is not set` or wrong JDK
- **Cause**: Gradle daemon picked up a JDK that doesn't match `compileOptions`.
- **Fix**: `export JAVA_HOME=$(/usr/libexec/java_home -v 17)` (macOS) and rerun. Optionally set `org.gradle.java.home=...` in `~/.gradle/gradle.properties`.

### Build fails with `Could not find SDK`
- **Cause**: `local.properties` missing or `sdk.dir` wrong.
- **Fix**: Either open the project once in Android Studio (which writes `local.properties` automatically) or write it manually: `echo "sdk.dir=/path/to/Android/sdk" > local.properties`.

### `assembleRelease` succeeds but the APK crashes on first launch
- **Cause**: R8 stripped runtime-required generic Signature metadata (e.g. Retrofit `Continuation` upper-bound types, Gson `TypeToken` parameterised types).
- **Fix**: Check `app/proguard-rules.pro` for the keep rules under `# ----- Retrofit + OkHttp -----`. The conditional `-if interface * { @retrofit2.http.* <methods>; }` rule + `Continuation` keep + per-interface `<methods>` keep are mandatory for R8 full mode (AGP 8+ default). Symptom of missing rules: `ClassCastException: java.lang.Class cannot be cast to java.lang.reflect.ParameterizedType`. See `aidlc-docs/ui-followups.md` UI-34 for the U10-era fix.

### Release build is too large (> 35 MB)
- **Cause**: R8 minify or resource shrink disabled, or a heavy library landed (e.g. `material-icons-extended` adds ~30 MB unminified).
- **Fix**: Confirm `app/build.gradle.kts` release block has `isMinifyEnabled = true` and `isShrinkResources = true`. R8 typically delivers a ~90% reduction.

### Hilt KSP errors (`Cannot resolve @HiltAndroidApp`)
- **Cause**: Stale Gradle cache or KSP version mismatch.
- **Fix**: `./gradlew --stop && rm -rf ~/.gradle/caches/8.14.3 && ./gradlew :app:assembleDebug`.

### Gradle daemon hangs / runs out of memory
- **Fix**: `./gradlew --stop`. Bump `org.gradle.jvmargs=-Xmx4g` in `gradle.properties` if R8 OOMs on release builds.

---

## Continuous Integration

No CI pipeline is provisioned for v2.0 — release validation is via local `assembleRelease` + manual on-device smoke per `aidlc-docs/operations/manual-nfc-checklist.md`. Future CI work is tracked under v2.1 / Operations stage.

---

## Reproducibility

- The wrapped `gradlew` pins Gradle 8.14.3 (per `gradle/wrapper/gradle-wrapper.properties`).
- `gradle/libs.versions.toml` pins all library versions.
- `app/build.gradle.kts` pins `versionCode` and `versionName` per release.

A clean machine with JDK 17 + Android SDK installed should produce a byte-identical APK from the same commit, except for: timestamps in the AndroidManifest.xml `targetSdkVersion` line, the signing-block (signature timestamp), and R8's symbol mapping (mapping.txt content varies per build but functional bytecode is identical).
