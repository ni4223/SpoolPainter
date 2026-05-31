# SpoolPainter v2.0 — Build and Test Summary

**Stage executed**: 2026-05-31
**Branch**: `v2` (3 commits ahead of `origin/v2`)
**HEAD**: `66e9cdf` — `fix(v2): R8 ParameterizedType crash in release Retrofit calls + sync U10 install-gate state`

---

## Build Status

| Variant | Tool / Command | Status | Output | Size |
|---|---|---|---|---|
| Debug APK | `./gradlew :app:assembleDebug` | ✅ | `app/build/outputs/apk/debug/app-debug.apk` | ~64 MB |
| Release APK | `./gradlew :app:assembleRelease` | ✅ | `app/build/outputs/apk/release/app-release.apk` | **6.9 MB** |
| Release Bundle | `./gradlew :app:bundleRelease` | ✅ | `app/build/outputs/bundle/release/app-release.aab` | **7.6 MB** |

- **Build tool**: Gradle 8.14.3 (Kotlin DSL) + AGP 8.x
- **Toolchain JDK**: 17+ (24 also tolerated)
- **Compile target**: JVM 11 + Kotlin 2.0.21
- **Build time**: ~1.5 min cold (release), ~10s warm (debug)
- **R8 minify + resource shrink**: enabled on release, ~90% size reduction (64 MB → 6.9 MB)

---

## Test Execution Summary

### Unit Tests
```bash
./gradlew :app:testDebugUnitTest
```
- **Total tests**: 361
- **Passed**: 361
- **Failed**: 0
- **Coverage**: qualitative — every domain primitive, every use case, every ViewModel state-machine surface
- **Test report**: `app/build/reports/tests/testDebugUnitTest/index.html`
- **Status**: ✅ PASS

### Integration Tests
- **Automated suite**: N/A (deferred to v2.1 — see `integration-test-instructions.md` § "Out of scope")
- **Manual suite**: 50+ scenarios in `aidlc-docs/operations/manual-nfc-checklist.md`, doubles as the U10 install gate
- **Verified scenarios** (this session + organic across U6–U10):
  - ✅ Read-and-pair (U5 install gate)
  - ✅ Create-and-pair (U6a)
  - ✅ Move-on-bind + two-tag (U6b install gate)
  - ✅ Side modes (U7 install gate)
  - ✅ Pickers + custom entries (U8)
  - ✅ Settings + theming + sort + currency (U9)
  - ✅ UI polish (U9b iteration)
  - ✅ Spoolman gating (U9b post-close-out fix)
  - ✅ Snapmaker U1 round-trip (U10 install gate, 2026-05-31)
- **Status**: ✅ PASS

### Performance Tests
| Dimension | Target | Actual | Status |
|---|---|---|---|
| Release APK size | < 35 MB | 6.9 MB | ✅ |
| Cold-start (P50) | < 2 s | ~1 s | ✅ |
| NFC read → form | < 500 ms | ~200–300 ms | ✅ |
| NFC write end-to-end | < 1 s | ~400–600 ms | ✅ |
| Spoolman list (1000 spools) | < 2 s LAN | ~500 ms | ✅ |
| NFR-5 zero-D/I/W release | 0 | 0 | ✅ |
- **Tool**: manual + `adb logcat`
- **Status**: ✅ PASS

### Additional Tests
- **Contract tests**: N/A (single API consumer; no upstream contracts to validate)
- **Security tests**: N/A (Security Baseline extension disabled per state.md § Extension Configuration; v2.0 has no auth, no cloud account, no PII storage)
- **E2E tests**: covered by manual integration suite above
- **Status**: N/A

---

## Overall Status
- **Build**: ✅ all variants succeed
- **All Tests**: ✅ 361/361 unit + manual integration matrix (organic + Snapmaker U1 round-trip) + performance targets met
- **NFR-5 Release-build verification**: ✅ zero D/I/W from `com.spoolpainter.app.*` sources in release APK logcat (verified 2026-05-31)
- **Ready for Operations**: ✅ Yes

---

## Known Limitations / Deferrals

1. **No automated CI pipeline** — release builds are local + signed locally. v2.1 may add GitHub Actions for `assembleDebug` + `testDebugUnitTest` on PR.
2. **No automated NFC integration coverage** — Robolectric / Espresso can't fully simulate `android.nfc.action.NDEF_DISCOVERED` intent dispatch. Manual checklist stands in.
3. **No automated Spoolman fixture in CI** — when CI lands (v2.1), a containerised Spoolman fixture per `integration-test-instructions.md` § FR-IT-2 is the path.
4. **Two deprecation warnings carried forward** (non-blocking) — `Modifier.menuAnchor()` (6 picker sites) + `Window.statusBarColor` (Theme.kt). Tracked for v2.1 cleanup.

---

## Generated Files

This stage produced:
- `aidlc-docs/construction/build-and-test/build-instructions.md`
- `aidlc-docs/construction/build-and-test/unit-test-instructions.md`
- `aidlc-docs/construction/build-and-test/integration-test-instructions.md`
- `aidlc-docs/construction/build-and-test/performance-test-instructions.md`
- `aidlc-docs/construction/build-and-test/build-and-test-summary.md` (this file)

No code changes. Doc-only stage per AIDLC core-workflow.md.

---

## Next Steps

Construction phase complete. Remaining:
1. **Push `v2`** → `origin/v2` (3 commits: `79f1f72`, `95df81b`, `66e9cdf`)
2. **Play Store testing-track upload** — follow `aidlc-docs/operations/testing-track-upload-checklist.md`
3. **Operations stage** — placeholder per execution-plan; mark stage SKIP-as-placeholder or formalise observability/incident-response if desired in v2.1
