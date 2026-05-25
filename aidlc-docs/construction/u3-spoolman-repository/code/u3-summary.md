# U3 — Spoolman Client Overhaul — Code Generation Summary

**Stage**: CONSTRUCTION → Code Generation Part 2 (Generation) — complete
**Generated**: 2026-05-24
**Plan**: `aidlc-docs/construction/plans/u3-spoolman-repository-code-generation-plan.md`
**Functional Design**: `aidlc-docs/construction/u3-spoolman-repository/functional-design/`

---

## Files Created

| Path | Purpose | FRs / NFRs / Stories |
|---|---|---|
| `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanOutcome.kt` | Sealed `SpoolmanOutcome<T>` + `flatMap` / `map` extensions | NFR-7.1; consumed by U5..U9 |
| `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/ConnectivityState.kt` | Sealed `ConnectivityState` (`Unknown / Reachable / Unreachable`) | FR-10.2, S-10.2; consumed by U9 |
| `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/NewSpoolRequest.kt` | DTO for FR-7 chain + `TempRanges` | FR-7, S-7.1, S-7.2, S-7.3 |
| `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanRequests.kt` | Request DTOs (`Create*Request`, `UpdateSpoolLotNrRequest`) | FR-7, FR-4.6, FR-5.2 |
| `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanApi.kt` | Retrofit interface — 11 endpoints (info / find / list / get / create vendor/filament/spool / patch lot_nr) | FR-3.2, FR-4.6, FR-5.2, FR-7, FR-8.3, FR-10.2 |
| `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanApiFactory.kt` | Retrofit + OkHttp injectable factory; rebuilds `SpoolmanApi` per base-URL change | Q-U3-3=C, Q-U3-11=A |
| `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanRepository.kt` | `@Singleton` repository — single source of truth for Spoolman | every U3 FR/Story |
| `app/src/main/java/com/spoolpainter/app/di/Qualifiers.kt` | `@AppScope`, `@IoDispatcher` Hilt qualifier annotations | Q-U3-11=A wiring |
| `app/src/test/java/com/spoolpainter/app/data/remote/spoolman/FakeSpoolmanApi.kt` | Scriptable in-memory `SpoolmanApi` impl + `Failure` sealed type | NFR-4.1 test scaffolding |
| `app/src/test/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanRepositoryTestSupport.kt` | `SpoolmanRepositoryHarness` test helper | NFR-4.1 |
| `app/src/test/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanOutcomeTest.kt` | 6 cases — flatMap / map / short-circuit / variant identity | BR-U3-T-1 |
| `app/src/test/java/com/spoolpainter/app/data/remote/spoolman/ConnectivityStateTransitionTest.kt` | 7 cases — one per BR-U3-CONN-1..7 | BR-U3-T-2 |
| `app/src/test/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanRepositoryProbeTest.kt` | 5 cases — success / 5xx / IOException / ParseError / URL-not-configured | BR-U3-T-3 |
| `app/src/test/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanRepositoryFindByCardUidTest.kt` | 8 cases — empty / single / multi / none / prefix / 5xx / IOException / URL-not-configured | BR-U3-T-4 |
| `app/src/test/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanRepositoryAppendCardUidTest.kt` | 7 cases — append / idempotent / opaque preserved / empty rejected / 5xx read / IOException read / IOException PATCH | BR-U3-T-5 |
| `app/src/test/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanRepositoryRemoveCardUidTest.kt` | 7 cases — remove / no-op / opaque preserved / clear-to-empty / empty rejected / 5xx read / IOException PATCH | BR-U3-T-6 |
| `app/src/test/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanRepositoryCreateChainTest.kt` | 12 cases — vendor hit/miss / case-insensitive / filament hit/miss / variant null≡"" / variant whitespace / spool POST lot_nr / fail @ vendor / fail @ filament / fail @ spool / empty UID rejected | BR-U3-T-7 |
| `app/src/test/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanRepositoryRefreshTest.kt` | 4 cases — full success / fail @ vendors / fail @ filaments / fail @ spools | BR-U3-T-8 |
| `app/src/test/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanRepositoryUrlChangeTest.kt` | 4 cases — blank / blank→non-blank / non-blank→different / non-blank→blank | BR-U3-T-9 |
| `app/src/test/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanRepositoryCacheInvalidationTest.kt` | 4 cases — PATCH replace / POST prepend / find-no-touch / refresh repopulate | BR-U3-T-10 |

## Files Modified

| Path | Change |
|---|---|
| `app/src/main/java/com/spoolpainter/app/domain/models/SpoolmanModels.kt` | Added `id: Int? = null` field on `SpoolmanVendor` (FR-7.2 join key); added `data class SpoolmanInfo(val version: String? = null)` for probe response. |
| `app/src/main/java/com/spoolpainter/app/di/NetworkModule.kt` | Added `provideOkHttpClient()` (3 s connect / 5 s read; `BASIC` logging on `BuildConfig.DEBUG` only) + `provideGson()`. (Per Q-U3-4=A / Q-U3-5=B.) |
| `app/src/main/java/com/spoolpainter/app/di/RepositoryModule.kt` | Annotated `provideAppCoroutineScope()` with `@AppScope`; added `@IoDispatcher` provider for `Dispatchers.IO`. |
| `app/src/main/java/com/spoolpainter/app/data/local/SettingsRepository.kt` | Constructor parameter `externalScope` now annotated `@AppScope` for binding consistency with `SpoolmanRepository`. |
| `app/build.gradle.kts` | Enabled `buildFeatures.buildConfig = true` to expose `BuildConfig.DEBUG` for `NetworkModule`'s conditional logging interceptor. |

## Files Deleted

| Path | Reason |
|---|---|
| `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanService.kt` | v1 Spoolman client replaced by `SpoolmanRepository` + new `SpoolmanApi`. Per Q-U3-6=A big-bang delete (no production callers; matches U2's `OpenSpoolData` posture). |

## Files Kept Dormant (deleted in later units)

Per `unit-of-work.md` §3-U4 / §3-U8:
- `hardware/nfc/{NfcManager,NfcController,NfcHandler}.kt` — dormant; **U4** disposes.
- `data/local/{MaterialDatabase,BrandDatabase}.kt` — used by retained Compose pickers; **U8** migrates into `MaterialPresetSource` / `BrandPresetSource`.
- `domain/models/{AppState,NfcTag,NfcResult,Material}.kt` — dormant. v1 `domain/models/NfcResult.kt` coexists with U1's `domain/primitives/NfcResult.kt` (different packages); **U4** / **U5** dispose.

---

## Story Coverage

| Story | Title | Status | Coverage |
|---|---|---|---|
| **S-3.1** | Look up an unknown tag by UID in Spoolman | ✅ | `SpoolmanRepository.findSpoolsByCardUid` (BR-U3-FIND-1..5). Tested by `SpoolmanRepositoryFindByCardUidTest` (8 cases). |
| **S-4.5** | Pair UID into an existing Spoolman spool (PATCH) — repo half | ✅ | `SpoolmanRepository.appendCardUidToSpool` (BR-U3-APPEND-*). Tested by `SpoolmanRepositoryAppendCardUidTest` (7 cases). Use-case orchestration lives in U6a. |
| **S-7.1** | Resolve-or-create vendor by name | ✅ | `resolveOrCreateVendor` (BR-U3-VEN-*). Tested in `SpoolmanRepositoryCreateChainTest`. |
| **S-7.2** | Resolve-or-create filament | ✅ | `resolveOrCreateFilament` (BR-U3-FIL-*). Variant null≡"" + whitespace tested. |
| **S-7.3** | POST a new spool with `lot_nr = card_uid:<uid>` | ✅ | `createSpoolStep` (BR-U3-SP-*). |
| **S-9.1** | Configure the Spoolman server URL — repo half | ✅ | `SpoolmanRepository.probe()` (BR-U3-PROBE-*). Tested by `SpoolmanRepositoryProbeTest` (5 cases). UI in U9. |
| **S-10.2** | Visible banner with Retry — repo half (`connectivity` source) | ✅ | `connectivity: StateFlow<ConnectivityState>` (BR-U3-CONN-*). Tested by `ConnectivityStateTransitionTest` (7 cases). Banner UI in U9. |
| **NFR-7** | Network — sealed error contract | ✅ | `SpoolmanOutcome<T>` (BR-U3-O-*). Tested by `SpoolmanOutcomeTest` (6 cases) + propagation tests in every repo test. |

Forward-referenced types produced (consumed by later units, no story burden in U3):
- `NewSpoolRequest`, `TempRanges` — consumed by U6a `CreateAndPairUseCase`.
- `removeCardUidFromSpool` — consumed by U6b `MoveOnBindUseCase`.
- `vendors` / `filaments` `StateFlow` — consumed by U8 `MaterialBrandRepository`.

---

## Public Interfaces Produced

```kotlin
// data/remote/spoolman/SpoolmanOutcome.kt
sealed interface SpoolmanOutcome<out T> {
    data class Success<out T>(val data: T) : SpoolmanOutcome<T>
    data class HttpError(val code: Int, val message: String) : SpoolmanOutcome<Nothing>
    data class NetworkError(val cause: Throwable) : SpoolmanOutcome<Nothing>
    data class ParseError(val cause: Throwable) : SpoolmanOutcome<Nothing>
}
inline fun <T, R> SpoolmanOutcome<T>.flatMap(block: (T) -> SpoolmanOutcome<R>): SpoolmanOutcome<R>
inline fun <T, R> SpoolmanOutcome<T>.map(block: (T) -> R): SpoolmanOutcome<R>

// data/remote/spoolman/ConnectivityState.kt
sealed interface ConnectivityState {
    data object Unknown : ConnectivityState
    data object Reachable : ConnectivityState
    data class Unreachable(val reason: String) : ConnectivityState
}

// data/remote/spoolman/NewSpoolRequest.kt
data class NewSpoolRequest(
    val vendorName: String,
    val materialName: String,
    val colorHex: String,
    val variant: String?,
    val tempRanges: TempRanges,
    val cardUid: CardUid,
)
data class TempRanges(val extruderMin: Int?, val extruderMax: Int?, val bedMin: Int?, val bedMax: Int?)

// data/remote/spoolman/SpoolmanRepository.kt
@Singleton class SpoolmanRepository @Inject constructor(...) {
    val connectivity: StateFlow<ConnectivityState>
    val vendors: StateFlow<List<SpoolmanVendor>>
    val filaments: StateFlow<List<SpoolmanFilament>>
    val spools: StateFlow<List<SpoolmanSpool>>

    suspend fun probe(): SpoolmanOutcome<Unit>
    suspend fun findSpoolsByCardUid(uid: CardUid): SpoolmanOutcome<List<SpoolmanSpool>>
    suspend fun appendCardUidToSpool(spoolId: Int, uid: CardUid): SpoolmanOutcome<SpoolmanSpool>
    suspend fun removeCardUidFromSpool(spoolId: Int, uid: CardUid): SpoolmanOutcome<SpoolmanSpool>
    suspend fun refresh(): SpoolmanOutcome<Unit>
    suspend fun createSpoolForNewFilament(req: NewSpoolRequest): SpoolmanOutcome<SpoolmanSpool>
}
```

Consumers per `unit-of-work-dependency.md`:
- **U4** — independent (NFC); will not consume `SpoolmanRepository` directly.
- **U5** — `findSpoolsByCardUid`, `connectivity`, `filaments`, `spools`, `SpoolmanOutcome`.
- **U6a** — `createSpoolForNewFilament`, `appendCardUidToSpool`.
- **U6b** — `removeCardUidFromSpool`, `appendCardUidToSpool`.
- **U7** — none (raw write is Spoolman-free; U7 must not inject `SpoolmanRepository`).
- **U8** — `vendors`, `filaments` (precedence merge for material/brand pickers).
- **U9** — `connectivity`, `probe()` (Settings Test connection + offline banner).

---

## Forward References Deferred

| Type / API | Lands in | Reason |
|---|---|---|
| `MoveOnBindUseCase` orchestration of remove+append for two different spools | U6b | Q-S3=C — sequencing lives in the use-case, not the repository. |
| `OfflineBanner` Compose component reading `connectivity` | U9 | UI work blocked on `MainViewModel` integration. |
| Pull-to-refresh trigger in `MainScreen` | U10 (release polish) | Manual `refresh()` invocation point — U3 ships the suspending API. |
| ETag / optimistic-concurrency on PATCH | out of scope | Single-user assumption (NFR-1 implicit); accept last-write-wins. |

---

## Build & Test Verification

| Task | Outcome |
|---|---|
| `./gradlew :app:compileDebugKotlin` | ✅ Pass — pre-existing Compose deprecation warnings on retained v1 components only; no new warnings from U3. |
| `./gradlew :app:testDebugUnitTest` | ✅ Pass — **132 / 132 tests pass, 0 failures, 0 skipped**: U1 4 (`SettingsRepositoryTest`) + U2 64 + U3 64 (`SpoolmanOutcomeTest` 6 + `ConnectivityStateTransitionTest` 7 + `SpoolmanRepositoryProbeTest` 5 + `SpoolmanRepositoryFindByCardUidTest` 8 + `SpoolmanRepositoryAppendCardUidTest` 7 + `SpoolmanRepositoryRemoveCardUidTest` 7 + `SpoolmanRepositoryCreateChainTest` 12 + `SpoolmanRepositoryRefreshTest` 4 + `SpoolmanRepositoryUrlChangeTest` 4 + `SpoolmanRepositoryCacheInvalidationTest` 4). |
| `./gradlew :app:assembleDebug` | ✅ Pass — produces `app/build/outputs/apk/debug/app-debug.apk` (33 MB; identical to U2 baseline — no new runtime deps). |
| Brownfield invariant `grep -rn "OpenSpoolData" app/src` | ✅ Pass — zero matches (carried from U2). |
| Brownfield invariant `grep -rn "class SpoolmanService" app/src/main` | ✅ Pass — zero matches (BR-U3-MIG-3). |
| Brownfield invariant `grep -rn "SpoolmanService" app/src/main` | ✅ Pass — zero matches in main (only test-side `FakeSpoolmanApi` mentions exist). |

### JDK note (unchanged from U1/U2)

Builds invoked with `JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-17.jdk/Contents/Home`. Default machine `JAVA_HOME` (JDK 24) breaks Gradle 8.13's `Type T not present` reflection. Durable fix deferred to U10 release polish.

---

## Exit-Criteria Checklist (per `unit-of-work.md` §3-U3)

- [x] Repository tests pass against a fake `SpoolmanApi` (`FakeSpoolmanApi` — 64 cases).
- [x] `SpoolmanOutcome` paths covered for HTTP / Network / Parse errors (BR-U3-T-1 + propagation tests across the suite).
- [x] `findSpoolsByCardUid` — single match / multiple matches / no match / HTTP / network error.
- [x] `createSpoolForNewFilament` — vendor lookup-or-create / filament lookup-or-create / spool POST with `lot_nr=card_uid:<uid>` / HTTP error short-circuits chain (no compensating action).
- [x] `appendCardUidToSpool` / `removeCardUidFromSpool` — PATCH body computed via `CardUidEncoding`; idempotency.
- [x] Compile passes; tests pass; APK assembles.
- [x] Public interfaces produced are stable for U5..U9 consumption (no further changes expected without an explicit unit revisit).

**No milestone install gate** — per `unit-of-work.md` §2, U3 is verified by unit tests; install gates are at U1 / U5 / U6 / U10.

---

## Forbidden Patterns Audit

| Pattern (from `unit-of-work-dependency.md` §5) | Status |
|---|---|
| UI → data source direct calls | ✅ N/A — U3 ships no UI. |
| Activity / Context in Hilt graph | ✅ none — `SpoolmanRepository` constructor takes only `SettingsRepository` + `SpoolmanApiFactory` + `CoroutineScope` + `CoroutineDispatcher`. |
| Use-cases holding state | ✅ N/A — U3 ships no use-cases. |
| Service locator / event bus | ✅ N/A. |
| `android.util.Log` in primitives / repository | ✅ none — `SpoolmanRepository` uses no logging; OkHttp's `HttpLoggingInterceptor` is the only log-adjacent code, debug-only per BR-U3-LOG-2. |
| Exceptions crossing the U3 boundary | ✅ none — every `IOException` / `JsonSyntaxException` / `JsonParseException` / `IllegalStateException` (empty body) is mapped to a sealed `SpoolmanOutcome`. `CancellationException` IS rethrown (BR-U3-CANCEL-1). |
| `SpoolmanService` references after U3 | ✅ zero in `app/src/main` (verified by grep). |
| `OpenSpoolData` references after U2 | ✅ zero (carried). |

---

## Functional-Design Rule Coverage Audit

Spot-check map of representative business-rules → code locations:

| Rule | Code / test location |
|---|---|
| BR-U3-O-1 (single chokepoint for `runCatching`) | `SpoolmanRepository.performHttp` |
| BR-U3-O-3 (empty body → ParseError) | `SpoolmanRepository.performHttp` `body() == null` branch |
| BR-U3-O-7 / BR-U3-CANCEL-1 (rethrow CancellationException) | `SpoolmanRepository.performHttp` `catch (CancellationException) { throw e }` |
| BR-U3-CONN-3 (HttpError still ⇒ Reachable) | `SpoolmanRepository.httpError` sets `_connectivity.value = Reachable`. Test: `ConnectivityStateTransitionTest.BR-U3-CONN-3 ...`. |
| BR-U3-CONN-5 (URL-not-configured ⇒ Unknown) | `SpoolmanRepository.urlNotConfigured`. Test: `SpoolmanRepositoryProbeTest.probe with blank URL ...`. |
| BR-U3-CONN-6 (ParseError leaves connectivity unchanged) | `performHttp` JSON catch arm omits state mutation. Test: `ConnectivityStateTransitionTest.BR-U3-CONN-6 ...`. |
| BR-U3-FIND-1 (empty UID short-circuit) | `findSpoolsByCardUid` first line. Test: `SpoolmanRepositoryFindByCardUidTest.empty CardUid ...`. |
| BR-U3-FIND-2 (`card_uid:` prefix in query) | `findSpoolsByCardUid` argument construction. Test: `query string includes card_uid prefix`. |
| BR-U3-VEN-2 (case-insensitive vendor match) | `resolveOrCreateVendor` `firstOrNull { it.name.equals(name, ignoreCase = true) }`. Test: `case-insensitive vendor match`. |
| BR-U3-FIL-3 (variant null ≡ "") | `resolveOrCreateFilament` normalisation `req.variant?.trim()?.takeIf { it.isNotEmpty() }`. Test: `variant null and empty are equivalent`. |
| BR-U3-CHAIN-5 (flatMap short-circuits) | `createSpoolForNewFilament` flatMap chain. Test: `fail at filament short-circuits chain`. |
| BR-U3-CHAIN-7 (no rollback) | tested by `fail at filament short-circuits chain` and `fail at spool short-circuits` (vendor/filament remain). |
| BR-U3-APPEND-3 (UID dedup via existing `CardUidEncoding`) | `appendCardUidToSpool` `(decoded.uids + uid).distinct()`. Test: `UID idempotent when already present`. |
| BR-U3-APPEND-4 (opaque tail preserved) | `CardUidEncoding.encode(newUids, decoded.opaque)`. Test: `opaque tail preserved on append`. |
| BR-U3-REMOVE-5 (last-UID-removed clears to empty) | `removeCardUidFromSpool` + `CardUidEncoding.encode(emptyList(), "")`. Test: `removing last UID with no opaque clears lot_nr to empty string`. |
| BR-U3-URL-3 (URL change clears caches + connectivity) | `init { settings.settings.map { it.url }.distinctUntilChanged().onEach { ... } }`. Test: `URL change clears caches and resets connectivity to Unknown`. |
| BR-U3-LOG-1 / BR-U3-LOG-2 (BASIC on debug, none on release) | `NetworkModule.provideOkHttpClient` `if (BuildConfig.DEBUG)` branch. (Audited by code inspection.) |
| BR-U3-MIG-1 (delete v1 SpoolmanService) | `SpoolmanService.kt` removed. Verified by grep. |
| BR-U3-CACHE-1 (replace by id on PATCH) | `replaceSpoolInCache`. Test: `successful PATCH replaces spool by id ...`. |
| BR-U3-CACHE-6 (URL change clears caches) | `init` block `_vendors.value = emptyList()` etc. Test: `URL change clears caches and resets ...`. |
