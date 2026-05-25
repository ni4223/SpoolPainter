# U3 — Code Generation Plan

**Stage**: CONSTRUCTION → Per-Unit Loop → Code Generation Part 1 (Planning)
**Unit**: U3 — Spoolman Client Overhaul
**Status**: DRAFT — awaiting user approval

**Source artefacts** (single source of truth — this plan executes them; no logic decisions outside this plan):
- `aidlc-docs/construction/u3-spoolman-repository/functional-design/business-logic-model.md` — algorithmic pseudocode + process diagrams.
- `aidlc-docs/construction/u3-spoolman-repository/functional-design/business-rules.md` — ~80 rules with FR/S/Q traceability.
- `aidlc-docs/construction/u3-spoolman-repository/functional-design/domain-entities.md` — final type signatures + file inventory.
- `aidlc-docs/construction/plans/u3-spoolman-repository-functional-design-plan.md` — locked Q-U3-1 .. Q-U3-11 decisions.
- `aidlc-docs/inception/application-design/unit-of-work.md` §3-U3 — unit scope + DoD.
- `aidlc-docs/inception/application-design/components.md` §2.4 / §2.6 / §2.7 / §3.

---

## Unit Generation Context

### Stories implemented by U3
| Story | Title | Coverage |
|---|---|---|
| **S-3.1** | Look up an unknown tag by UID in Spoolman | `findSpoolsByCardUid` (BR-U3-FIND-1..5) |
| **S-4.5** | Pair UID into an existing Spoolman spool (PATCH) — repository half | `appendCardUidToSpool` (BR-U3-APPEND-*); orchestration in U6a |
| **S-7.1** | Resolve-or-create vendor by name | `resolveOrCreateVendor` (BR-U3-VEN-*) |
| **S-7.2** | Resolve-or-create filament | `resolveOrCreateFilament` (BR-U3-FIL-*) |
| **S-7.3** | POST a new spool with `lot_nr = card_uid:<uid>` | `createSpoolStep` (BR-U3-SP-*) |
| **S-9.1** | Configure the Spoolman server URL — repository half | `probe()` (BR-U3-PROBE-*) |
| **S-10.2** | Visible banner with Retry — repository half (`connectivity` source) | `connectivity: StateFlow<ConnectivityState>` (BR-U3-CONN-*) |
| **NFR-7** | Network — sealed error contract | `SpoolmanOutcome<T>` + error mapping (BR-U3-O-*) |

### Dependencies on prior units
- **U1 (Architecture & DI Scaffold)** DONE 2026-05-25 — Hilt + KSP plugin, `RepositoryModule` provides `@Singleton CoroutineScope`, `SettingsRepository` injectable, OkHttp + Retrofit + logging-interceptor on the classpath.
- **U2 (Domain Primitives)** DONE 2026-05-26 — `CardUid` value class, `CardUidEncoding.PREFIX = "card_uid:"`, `CardUidEncoding.{decode, encode}`, `CardUidEncoding.Decoded(uids, opaque)`.

### Public interfaces produced (consumed in later units)
- `SpoolmanRepository` (`@Singleton`, Hilt) — primary cross-unit boundary per Q-D1=C.
- `SpoolmanOutcome<T>` sealed type + `flatMap` / `map` extensions — error contract for every flow unit.
- `ConnectivityState` sealed type — read by U9 banner + Settings Test connection.
- `NewSpoolRequest` data class — consumed by U6a `CreateAndPairUseCase`.

### Database entities owned by U3
- None. U3 ships zero persistent storage. Three `MutableStateFlow`-backed in-memory caches (`vendors`, `filaments`, `spools`); no Room, no DataStore.

### Service boundaries
- HTTP only — Spoolman REST. No other external systems.

---

## Project Structure Note (brownfield)

Workspace root: `/Users/mnipun/AndroidStudioProjects/SpoolPainter`.
Project type: **Brownfield** Android single-module app.
Code locations:
- **Application code** → `app/src/main/java/com/spoolpainter/app/...`
- **Tests** → `app/src/test/java/com/spoolpainter/app/...`
- **Documentation** → `aidlc-docs/construction/u3-spoolman-repository/code/u3-summary.md` (markdown summary only; written at end of Part 2)

---

## §1 — Build / Dependency Setup

> U3 ships **one** new `testImplementation`-scope dependency: `com.squareup.retrofit2:retrofit-mock` (or hand-rolled fake; we'll use a hand-rolled `FakeSpoolmanApi` per Q-U3-9=A — no new dep). All runtime libraries (`retrofit`, `converter-gson`, `okhttp-logging-interceptor`, Hilt, Coroutines) are already on the classpath via U1.

- [ ] **1.0** Verify no runtime-dependency changes are needed. (Existing `app/build.gradle.kts` already pulls `retrofit:2.9.0`, `converter-gson:2.9.0`, `okhttp logging-interceptor:4.12.0`.) No new entries in `gradle/libs.versions.toml`.

---

## §2 — Domain types (cross-unit boundary)

### 2.1 `SpoolmanOutcome<T>` sealed type + `flatMap` / `map`
- [ ] **2.1.1** Create `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanOutcome.kt`.
- [ ] **2.1.2** Declare `sealed interface SpoolmanOutcome<out T>` with four variants (`Success<T>`, `HttpError`, `NetworkError`, `ParseError`) per `domain-entities.md` §2.1.
- [ ] **2.1.3** Add `inline fun <T, R> SpoolmanOutcome<T>.flatMap(...)` and `inline fun <T, R> SpoolmanOutcome<T>.map(...)` per BR-U3-O-9 / BR-U3-O-10.

### 2.2 `ConnectivityState` sealed type
- [ ] **2.2.1** Create `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/ConnectivityState.kt`.
- [ ] **2.2.2** Declare `sealed interface ConnectivityState` with `Unknown` / `Reachable` / `Unreachable(reason: String)` per BR-U3-CONN-*.

### 2.3 `NewSpoolRequest` + `TempRanges`
- [ ] **2.3.1** Create `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/NewSpoolRequest.kt`.
- [ ] **2.3.2** Declare `data class NewSpoolRequest(...)` per `domain-entities.md` §2.3.
- [ ] **2.3.3** Declare `data class TempRanges(...)` in the same file (U3-local; promoted by a later unit if needed).

---

## §3 — Wire models + Retrofit interface

### 3.1 Extend v1 wire models in place
- [ ] **3.1.1** Edit `app/src/main/java/com/spoolpainter/app/domain/models/SpoolmanModels.kt`:
  - Add `id: Int? = null` field on `SpoolmanVendor` (BR-U3-API + S-7.2 join key).
  - No other field-set changes — `SpoolmanSpool`, `SpoolmanFilament` already carry the fields U3 needs.
- [ ] **3.1.2** Add `data class SpoolmanInfo(val version: String? = null)` in the same file (probe response).

### 3.2 New request DTOs
- [ ] **3.2.1** Create `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanRequests.kt`.
- [ ] **3.2.2** Declare `CreateVendorRequest`, `CreateFilamentRequest`, `CreateSpoolRequest`, `UpdateSpoolLotNrRequest` per `domain-entities.md` §2.7.

### 3.3 New `SpoolmanApi` Retrofit interface
- [ ] **3.3.1** Create `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanApi.kt`.
- [ ] **3.3.2** Declare `interface SpoolmanApi` with the eleven endpoints listed in `domain-entities.md` §2.5 (info / findSpoolsByLotNr / listSpools / listFilaments / listVendors / createVendor / createFilament / createSpool / patchSpoolLotNr / **plus** `getSpool(id)` per BR-U3-APPEND-1 amendment).

### 3.4 `SpoolmanApiFactory`
- [ ] **3.4.1** Create `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanApiFactory.kt`.
- [ ] **3.4.2** Implement `class SpoolmanApiFactory @Inject constructor(okHttpClient, gson)` with `fun create(baseUrl: String): SpoolmanApi` per `domain-entities.md` §2.8 + BR-U3-URL-1.

---

## §4 — Repository implementation

### 4.1 `SpoolmanRepository.kt`
- [ ] **4.1.1** Create `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanRepository.kt`.
- [ ] **4.1.2** Declare `@Singleton class SpoolmanRepository @Inject constructor(...)` with the four constructor deps from `domain-entities.md` §2.4 (`SettingsRepository`, `SpoolmanApiFactory`, `@AppScope CoroutineScope`, `@IoDispatcher CoroutineDispatcher`).
- [ ] **4.1.3** Declare private `MutableStateFlow` backers + public `StateFlow` accessors for `connectivity`, `vendors`, `filaments`, `spools`.
- [ ] **4.1.4** Declare private `@Volatile var cachedApi: SpoolmanApi?` (BR-U3-URL-4).
- [ ] **4.1.5** Implement `init { ... }` URL-collection block per `business-logic-model.md` §2.7 (BR-U3-URL-2 / BR-U3-URL-3 / BR-U3-CONN-8).
- [ ] **4.1.6** Implement private helper `performHttp(label, block)` per `business-logic-model.md` §5 — single chokepoint for `runCatching` + connectivity-update + outcome-mapping (BR-U3-O-* / BR-U3-CONN-* / BR-U3-CANCEL-1).
- [ ] **4.1.7** Implement private helper `urlNotConfigured(): SpoolmanOutcome<Nothing>` (BR-U3-CFG-1 / BR-U3-CONN-5).
- [ ] **4.1.8** Implement private helper `httpError(code, message)` (BR-U3-CONN-3 wrapper).
- [ ] **4.1.9** Implement private object `UrlNotConfiguredException : IOException(...)` (BR-U3-CFG-2).
- [ ] **4.1.10** Implement `suspend fun probe(): SpoolmanOutcome<Unit>` (BR-U3-PROBE-*).
- [ ] **4.1.11** Implement `suspend fun findSpoolsByCardUid(uid): SpoolmanOutcome<List<SpoolmanSpool>>` (BR-U3-FIND-*).
- [ ] **4.1.12** Implement `suspend fun appendCardUidToSpool(spoolId, uid): SpoolmanOutcome<SpoolmanSpool>` (BR-U3-APPEND-*).
- [ ] **4.1.13** Implement `suspend fun removeCardUidFromSpool(spoolId, uid): SpoolmanOutcome<SpoolmanSpool>` (BR-U3-REMOVE-*).
- [ ] **4.1.14** Implement `suspend fun refresh(): SpoolmanOutcome<Unit>` (BR-U3-REFRESH-*).
- [ ] **4.1.15** Implement `suspend fun createSpoolForNewFilament(req): SpoolmanOutcome<SpoolmanSpool>` (BR-U3-CHAIN-*) using internal helpers below.
- [ ] **4.1.16** Implement `internal suspend fun resolveOrCreateVendor(api, name): SpoolmanOutcome<SpoolmanVendor>` (BR-U3-VEN-*).
- [ ] **4.1.17** Implement `internal suspend fun resolveOrCreateFilament(api, vendor, req): SpoolmanOutcome<SpoolmanFilament>` (BR-U3-FIL-*).
- [ ] **4.1.18** Implement `internal suspend fun createSpoolStep(api, filament, uid): SpoolmanOutcome<SpoolmanSpool>` (BR-U3-SP-*).
- [ ] **4.1.19** Implement private `replaceById<T>(...)` cache-patch helper (BR-U3-CACHE-1).
- [ ] **4.1.20** Implement private `prepend<T>(...)` cache-insert helper used by VEN-6 / FIL-6 / SP-3.

### 4.2 Hilt qualifiers
- [ ] **4.2.1** Create `app/src/main/java/com/spoolpainter/app/di/Qualifiers.kt`.
- [ ] **4.2.2** Declare `@Qualifier annotation class AppScope` (for the existing `@Singleton CoroutineScope`).
- [ ] **4.2.3** Declare `@Qualifier annotation class IoDispatcher`.

### 4.3 `RepositoryModule` updates
- [ ] **4.3.1** Edit `app/src/main/java/com/spoolpainter/app/di/RepositoryModule.kt`:
  - Annotate the existing `provideAppCoroutineScope()` with `@AppScope` (binding migration; existing consumer `SettingsRepository` continues to work because it injects an unqualified `CoroutineScope` — see §4.3.2).
  - Add `@Provides @Singleton @IoDispatcher fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO`.
- [ ] **4.3.2** Migrate `SettingsRepository`'s constructor parameter to `@AppScope externalScope: CoroutineScope` (one-line edit) so Hilt resolves both `SpoolmanRepository` and `SettingsRepository` against the qualified scope. (Alternative: keep the unqualified `@Provides` and add a separate `@AppScope` `@Provides` returning the same instance — but that would create two scopes. Migration is the clean fix.)

### 4.4 `NetworkModule` updates
- [ ] **4.4.1** Edit `app/src/main/java/com/spoolpainter/app/di/NetworkModule.kt`:
  - Add `@Provides @Singleton fun provideOkHttpClient(): OkHttpClient` — 3 s connect / 5 s read (BR-U3-FAC + Q-U3-4=A); attach `HttpLoggingInterceptor(BASIC)` only when `BuildConfig.DEBUG` (BR-U3-LOG-*).
  - Add `@Provides @Singleton fun provideGson(): Gson` (default Gson — Spoolman wire types use snake_case; we accept Gson's default behaviour because v1 already does; we DON'T set a `FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES` because v1 model fields are already declared in snake_case).
  - `SpoolmanApiFactory` is `@Inject`-constructor — no `@Provides` needed.
  - `SpoolmanApi` is **not** provided here — it's owned and lifecycle-managed by `SpoolmanRepository` (per Q-U3-3=C).

---

## §5 — Brownfield migration (delete v1 `SpoolmanService`)

- [ ] **5.1** Delete `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanService.kt` (BR-U3-MIG-1).
- [ ] **5.2** Verify `grep -rn "SpoolmanService\|class SpoolmanService" app/src/main` returns zero matches (BR-U3-MIG-3).
- [ ] **5.3** Verify `FilamentSpool.fromSpoolman(SpoolmanSpool)` still compiles (consumer reference verified by `compileDebugKotlin`).

---

## §6 — Tests

All tests live under `app/src/test/java/com/spoolpainter/app/data/remote/spoolman/`. Each test file maps to one `BR-U3-T-*` group from `business-rules.md` §15.

### 6.1 `FakeSpoolmanApi`
- [ ] **6.1.1** Create `app/src/test/java/com/spoolpainter/app/data/remote/spoolman/FakeSpoolmanApi.kt`.
- [ ] **6.1.2** Implement scriptable in-memory `SpoolmanApi` impl: per-method failure injection (`var failVendorList: Throwable?`, etc.), seedable lists, call log for assertions. ~150 LOC.

### 6.2 `SpoolmanOutcomeTest` (BR-U3-T-1, BR-U3-T-12)
- [ ] **6.2.1** Create `SpoolmanOutcomeTest.kt`. 5 cases: flatMap success+success / success+error / error+anything / map success / map preserves error variant identity (and code).

### 6.3 `ConnectivityStateTransitionTest` (BR-U3-T-2)
- [ ] **6.3.1** Create `ConnectivityStateTransitionTest.kt`. 7 cases — one per BR-U3-CONN-1..7. Uses real `SpoolmanRepository` + `FakeSpoolmanApi`.

### 6.4 `SpoolmanRepositoryProbeTest` (BR-U3-T-3)
- [ ] **6.4.1** Create test file. 5 cases: success / 500 / IOException / JsonSyntaxException (mock via FakeSpoolmanApi) / URL-not-configured.

### 6.5 `SpoolmanRepositoryFindByCardUidTest` (BR-U3-T-4)
- [ ] **6.5.1** Create test file. 7 cases per BR-U3-FIND coverage matrix in business-rules.md §15.

### 6.6 `SpoolmanRepositoryAppendCardUidTest` (BR-U3-T-5)
- [ ] **6.6.1** Create test file. 7 cases (UID added when absent / UID idempotent when present / opaque tail preserved / empty UID rejected / HttpError on read / NetworkError on read / NetworkError on PATCH).

### 6.7 `SpoolmanRepositoryRemoveCardUidTest` (BR-U3-T-6)
- [ ] **6.7.1** Create test file. 7 cases per BR-U3-T-6.

### 6.8 `SpoolmanRepositoryCreateChainTest` (BR-U3-T-7)
- [ ] **6.8.1** Create test file. 11 cases per BR-U3-T-7 list in business-rules.md §15.

### 6.9 `SpoolmanRepositoryRefreshTest` (BR-U3-T-8)
- [ ] **6.9.1** Create test file. 4 cases (full success / fail at vendors / fail at filaments / fail at spools).

### 6.10 `SpoolmanRepositoryUrlChangeTest` (BR-U3-T-9)
- [ ] **6.10.1** Create test file. 4 cases (blank → non-blank / non-blank → different / non-blank → blank / connectivity returns to Unknown after URL clear). Uses an in-memory `SettingsRepository` fake (or real one with `MapDataStore`-style stub).

### 6.11 `SpoolmanRepositoryCacheInvalidationTest` (BR-U3-T-10)
- [ ] **6.11.1** Create test file. 5 cases (PATCH replaces by id / POST prepends / find does not patch / refresh repopulates / URL change clears).

### 6.12 Test count target
- **Total target**: ~55–60 new test cases.
- **Cumulative** after U3: 68 (U1+U2) + ~57 (U3) = **~125 tests**.

---

## §7 — Verification

- [ ] **7.1** `./gradlew :app:compileDebugKotlin` — must pass with no new warnings beyond the pre-existing Compose deprecation set carried since U1.
- [ ] **7.2** `./gradlew :app:testDebugUnitTest` — all U1 + U2 + U3 tests must pass (target ≥ 125).
- [ ] **7.3** `./gradlew :app:assembleDebug` — must produce `app-debug.apk`; size delta vs. U2 expected to be <1 MB (no new runtime deps).
- [ ] **7.4** Brownfield invariant: `grep -rn "OpenSpoolData" app/src` → 0 matches (carried from U2).
- [ ] **7.5** Brownfield invariant: `grep -rn "SpoolmanService" app/src/main` → 0 matches (BR-U3-MIG-3).
- [ ] **7.6** Public-interface stability check: review `SpoolmanRepository` / `SpoolmanOutcome` / `ConnectivityState` / `NewSpoolRequest` against `components.md` §3 + `component-methods.md` §2 to ensure later units (U5/U6a/U6b/U7/U8/U9) don't need to revisit U3.

---

## §8 — Code-Generation Summary artefact

- [ ] **8.1** Create `aidlc-docs/construction/u3-spoolman-repository/code/u3-summary.md` mirroring U2's summary shape: file inventory (created / modified / deleted) + story coverage table + public interfaces produced + forward-references deferred + verification table + forbidden-patterns audit + functional-design rule coverage spot-check.

---

## §9 — Out-of-scope (for explicit non-action — guards against scope creep)

- [ ] **9.1** No edits to `MainViewModel`, `SettingsViewModel`, `SpoolmanDropdown`, `OfflineBanner` (U5 / U9 territory).
- [ ] **9.2** No edits to `MaterialBrandRepository` / `MaterialDatabase` / `BrandDatabase` (U8 territory).
- [ ] **9.3** No edits to `NfcRepository` / `NfcManager` / `NfcController` / `NfcHandler` (U4 territory).
- [ ] **9.4** No new app code under `aidlc-docs/`.
- [ ] **9.5** No `--no-verify` / `--amend` git operations (per `unit-of-work.md` §2.1 + global rule).
- [ ] **9.6** No push to `origin/v2` (close-out commit lands locally only — user owns push).

---

## Approval Gate

This plan is the Code Generation Part 1 deliverable for U3.

> **🚀 WHAT'S NEXT?**
>
> 🔧 **Request Changes** — push back on individual checkboxes / scope decisions.
> ✅ **Continue to Next Stage** — approve plan; proceed to Code Generation Part 2 (executes every unchecked box above, then runs verification §7).
