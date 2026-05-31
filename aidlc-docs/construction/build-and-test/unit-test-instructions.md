# SpoolPainter v2.0 — Unit Test Execution

## Run Unit Tests

### 1. Execute All Unit Tests
```bash
./gradlew :app:testDebugUnitTest
```
- **Time**: ~30–60s warm
- **Test framework**: JUnit 4 + MockK + Turbine (coroutine-flow assertions) + Robolectric (where Android types leak into unit code)
- **Test source root**: `app/src/test/java/com/spoolpainter/app/`

### 2. Expected Results
- **Total tests**: 361 / 361 passing as of U10 close-out (commit `66e9cdf`)
- **Failures**: 0
- **Skipped**: 0
- **Test report**: `app/build/reports/tests/testDebugUnitTest/index.html` (open in a browser)
- **Raw XML**: `app/build/test-results/testDebugUnitTest/*.xml` (CI-ready format)

### 3. Coverage Targets

No automated coverage gate is enforced. Per-unit code-generation summaries (`aidlc-docs/construction/u<N>-*/code/u<N>-summary.md`) report new test counts:

| Unit | Tests added | Cumulative |
|---|---|---|
| U1 | 4 | 4 |
| U2 | 64 | 68 |
| U3 | 64 | 132 |
| U4 | 50 | 182 |
| U5 | 52 | 232 (with U4 mid-gate adjust: −2) |
| U6a | 12 | 244 |
| U6b | 37 | 281 |
| U7 | 19 | 300 |
| U8 | 32 | 343 (later trimmed to 332 after persistence layer drop) |
| U9 | 22 | 354 (refined to 362 by U9 close-out) |
| U9b | 0 | 362 (logo-restore session, no new tests) |
| U10 | −1 net | 361 (UI-32 fixture refactor; cleanup) |

Coverage is qualitative: every domain primitive (`OpenSpoolPayloadCodec`, `ColorHexCodec`, `ExtraCardUidsCodec`, `CardUid`, `Brand`, sort comparators) and every use case (`CreateAndPairUseCase`, `MoveOnBindUseCase`, `TwoTagUseCase`, `VendorUidOnlyPairUseCase`, `ReadAndPairUseCase`) has direct unit coverage. ViewModels (`MainViewModel`, `SettingsViewModel`, `MainViewModelSortTest`, `MainViewModelThemeCycleTest`) cover the state-machine surface.

### 4. Run a Specific Test Class
```bash
./gradlew :app:testDebugUnitTest --tests "com.spoolpainter.app.domain.usecases.CreateAndPairUseCaseTest"
```

### 5. Run a Specific Test Method
```bash
./gradlew :app:testDebugUnitTest --tests "com.spoolpainter.app.ui.MainViewModelTest.existingSpool_blankTag_writesPayloadAndAppendsCardUid"
```

### 6. Continuous Test Run (during development)
```bash
./gradlew :app:testDebugUnitTest --continuous
```
Re-runs on source changes. Useful while iterating on a single feature.

---

## Fix Failing Tests

If any tests fail:

### 1. Read the failure
```bash
open app/build/reports/tests/testDebugUnitTest/index.html
```
Or read the failure stacktrace inline:
```bash
./gradlew :app:testDebugUnitTest --info
```

### 2. Common failure modes

**Coroutine test timeout** (`Timed out waiting for X`)
- Cause: `runTest` block didn't advance time or the flow under test never emitted.
- Fix: Use `Turbine`'s `awaitItem()` / `awaitComplete()`; advance `TestScope` virtual clock with `runCurrent()` / `advanceTimeBy(Xms)`.

**MockK `no answer found` on a verified call**
- Cause: A repository call was added to production code but its mock wasn't stubbed.
- Fix: Add `coEvery { repo.theMethod(any()) } returns ...` in the test's `beforeEach`.

**Snackbar assertion failed** (the helper `awaitNonAmbientSnackbar` returned an unexpected string)
- Cause: U9b added passive-tap snackbars (`"Blank tag detected."`, `"Vendor tag. Press Read to load."`) that fire before the asserted snackbar. The helper filters those.
- Fix: Make sure your test calls `awaitNonAmbientSnackbar` rather than `awaitItem` directly when running on a viewmodel that has ambient classification snackbars (covered for `MainViewModelTest` + `MainViewModelTwoTagTest`).

**JDK 24 `Type T not present`**
- Cause: Gradle ≤ 8.13 + JDK 24 reflection bug in `DefaultReportContainer`.
- Fix: Already fixed in commit `95df81b` (Gradle 8.14.3 wrapper bump). If reverted: `./gradlew wrapper --gradle-version 8.14.3 --distribution-type bin`.

### 3. Re-run after fixing
```bash
./gradlew :app:testDebugUnitTest
```

---

## Test Support Fixtures

Reusable test fakes / helpers live in `app/src/test/java/`:

| Fixture | Purpose |
|---|---|
| `FakeSpoolmanApi` | In-memory Spoolman backend; supports list/get/create/patch/delete for spool/filament/vendor + extra-fields registration. |
| `FakeSpoolmanRepository` | Repository-level fake covering create-bundle / chain-delete-orphan / append-uid / sweep paths. |
| `FakeNfcRepository` | State-machine fake for NfcResult flow (Idle/Reading/Writing/Verifying/Success/Error). |
| `FakeMaterialBrandRepository` | Materials + brands lists with custom-entry append. |
| `NfcTestSupport.makeTag()` | Synthesises an `android.nfc.Tag` with non-null UID + Ndef techList for write-path tests. |
| `awaitNonAmbientSnackbar` | Drains UI-02 ambient classification emissions before asserting on the test-relevant snackbar. |
| `sampleUid()` | Returns a canonical uppercase-hex UID matching `CardUid.fromBytes` output. |

These are referenced from many test classes; updating their semantics requires touching all consumers in the same change.

---

## Lint

```bash
./gradlew :app:lintRelease
```

Lint-vital runs as part of `assembleRelease` and currently passes. Manual lint check is mostly redundant unless you've added a new module or AndroidManifest-affecting change.
