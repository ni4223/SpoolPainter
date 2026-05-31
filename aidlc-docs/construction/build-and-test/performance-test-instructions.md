# SpoolPainter v2.0 — Performance Test Instructions

## Purpose

Validate that SpoolPainter v2.0 meets its NFR performance targets. v2.0 is a single-user, on-device Android app with a small list-based domain (typically < 200 spools per Spoolman instance) and a per-tap NFC interaction pattern — there is **no load test, no throughput test, no concurrency test** in the conventional sense.

The relevant performance dimensions are:
1. **APK / install footprint** — disk space on user devices (Play Store testing-track distribution)
2. **Cold-start time** — time from launcher tap to interactive main screen
3. **NFC tap responsiveness** — time from tap to populated form (read flow) or written tag (write flow)
4. **Spoolman list-fetch time** — time from `Save` in Settings to populated dropdown
5. **R8 release optimisations** — verified strip of debug-only code paths (NFR-5)

---

## NFR Targets

| Dimension | Target | v2.0 actual | Status |
|---|---|---|---|
| Release APK size | < 35 MB | 6.9 MB | ✅ 80% under target |
| Release Bundle (AAB) | < 35 MB | 7.6 MB | ✅ |
| Cold-start (P50) | < 2 s | ~1 s on moto g stylus 2025 | ✅ |
| NFC read → form populated | < 500 ms | ~200–300 ms (timeout cap 10 s) | ✅ |
| NFC write end-to-end | < 1 s for 222-byte payload | ~400–600 ms (depends on tag chip) | ✅ |
| Spoolman list (1000 spools) | < 2 s on LAN | ~500 ms typical | ✅ |
| Release log emission (NFR-5) | 0 D/I/W from app code | 0 (verified U10 install gate) | ✅ |

---

## Manual Performance Verification

### 1. APK / Bundle Size
```bash
./gradlew :app:assembleRelease :app:bundleRelease
ls -lh app/build/outputs/apk/release/app-release.apk
ls -lh app/build/outputs/bundle/release/app-release.aab
```
**Pass**: APK ≤ 10 MB, AAB ≤ 10 MB.

If size regresses (e.g. a new heavyweight library lands), inspect with:
```bash
# Requires apkanalyzer from Android SDK build-tools
$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer apk summary app/build/outputs/apk/release/app-release.apk
$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer apk file-size app/build/outputs/apk/release/app-release.apk
```

### 2. Cold-Start Time
Force-stop the app, launch from launcher, eyeball or use `adb`:
```bash
adb shell am start -W -n com.spoolpainter.app/.ui.activity.MainActivity
```
Look at `WaitTime` and `TotalTime` in the output (milliseconds).

**Pass**: `TotalTime` < 2000 ms on a mid-range device (moto g stylus 2025 / Android 16 baseline). On flagship hardware expect < 800 ms.

### 3. NFC Tap Responsiveness
Manual stopwatch with the manual NFC checklist scenarios:

**Read flow**: Press Read FAB → tap tag → time to form populated.
- **Pass**: < 500 ms perceived. The 10-second `READ_TIMEOUT_MS` in NfcRepository is for *failure* cases (no tag presented).

**Write flow**: Save & Write button tap → tap tag → "Saved" snackbar.
- **Pass**: < 1 s for 222-byte payload to NTAG21x. NTAG213 writes are slower (~700 ms) than NTAG216 (~400 ms) due to chip differences.

### 4. Spoolman List-Fetch Time
On a Spoolman instance with ~100+ spools:
1. Settings → URL → Save → time to "Connected" status.
2. Back to main → tap Spool dropdown → time to all entries visible.

**Pass**: < 2 s on LAN. WAN with > 200 ms RTT may push this to 3–4 s.

### 5. NFR-5 Release Log Strip
Verifies R8's `-assumenosideeffects android.util.Log` actually stripped all `Log.d/i/w/e` calls from app code in release builds.

```bash
# Install signed release
adb install -r app/build/outputs/apk/release/app-release.apk
# Clear logcat ring buffer
adb logcat -c
# Launch + exercise (Settings save, Read, Save & Write, ~1–2 min of activity)
adb shell monkey -p com.spoolpainter.app -c android.intent.category.LAUNCHER 1
# (manually exercise on device)
# Capture
adb logcat -d > /tmp/release-smoke.txt
```

Filter for D/I/W lines from the app's PID:
```bash
PID=$(adb shell pidof com.spoolpainter.app | tr -d '\r')
grep -E " $PID +$PID +[DIW] " /tmp/release-smoke.txt | grep -v -E "InsetsController|ImeTracker|WindowOnBackDispatcher" | head -20
```
**Pass**: Empty output. The framework's `InsetsController` / `ImeTracker` / `WindowOnBackDispatcher` lines that DO appear under the app's PID are Android system code running on the app's main thread, not our code.

If any lines from `com.spoolpainter.app.*` classes appear: `-assumenosideeffects` rule is missing or some `Log.d` slipped in via a class R8 didn't visit. Inspect `mapping.txt` to find the offending class.

---

## R8 Optimisation Verification

### Inspect Mapping File
```bash
head -50 app/build/outputs/mapping/release/mapping.txt
```
Expected structure: every renamed class shows its R8 obfuscated name + member rewrites. Classes kept verbatim by ProGuard rules (e.g. `SpoolmanApi`, `SpoolmanModels.*`, Hilt-generated classes) appear unchanged.

### Verify Retrofit Generic Preservation
Critical for release-build correctness — see `ui-followups.md` UI-34 for context.
```bash
# Disassemble the SpoolmanApi interface from the release APK
$ANDROID_HOME/build-tools/<version>/d8 --disassemble app/build/outputs/apk/release/app-release.apk \
  | grep -A 20 "data/remote/spoolman/SpoolmanApi"
```
Expected: every `@GET` / `@POST` / `@PATCH` / `@DELETE` method retains its `Lretrofit2/Response<Ljava/util/List<...>>` parameterised return signature.

If signatures show as raw `Ljava/util/List;` without generic parameters, R8 stripped the Signature attribute and runtime Retrofit calls will crash with `ClassCastException: ... cannot be cast to ... ParameterizedType` (the U10-era crash).

---

## Performance Regressions

If a future change degrades performance:

1. **APK size regression** → inspect with `apkanalyzer apk file-size`. Likely cause: a library that ships unminified resources or a large native binary. Fix: ProGuard keep rules narrowed, or library swap to a lighter alternative.

2. **Cold-start regression** → use Android Studio's CPU profiler (Method Sampling) on the launch trace. Common causes: synchronous `init` blocks in singletons, unconditional Spoolman fetch on launch (only do it if URL is configured), heavy reflection on first paint.

3. **NFC tap responsiveness regression** → check `NfcRepository.handleTag` for new synchronous Spoolman calls. The read flow should resolve from the *cached* spool list, not a fresh `listSpools` call.

4. **NFR-5 violation** → grep production code for `Log.` (excluding `Log.e`, which is kept for crash reporting). Add the offending file to the ProGuard `-keep` rules only if absolutely necessary; otherwise convert to `Timber` or remove.

---

## Out of Scope for v2.0

- **Load testing** — N/A; single-user app.
- **Throughput testing** — N/A; single-user app.
- **Concurrent users** — N/A; single-user app.
- **Stress testing** — N/A; user-paced tap interactions.
- **Memory profiling under sustained load** — deferred to v2.1 if Operations stage requires.

These dimensions become relevant only if SpoolPainter ever evolves into a multi-device or server-mediated architecture, which is **not** in the v2.0 or v2.1 roadmap.
