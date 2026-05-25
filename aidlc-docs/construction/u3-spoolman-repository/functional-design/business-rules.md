# U3 — Business Rules

**Stage**: CONSTRUCTION → Per-Unit Loop → Functional Design Part 2 (U3)
**Unit**: U3 — Spoolman Client Overhaul
**Source**: plan + decisions in `aidlc-docs/construction/plans/u3-spoolman-repository-functional-design-plan.md`. Each rule cites its source FR / NFR / Story / Q-U3-* answer.

Rules use the prefix `BR-U3-` followed by a category code:
- `O` — `SpoolmanOutcome` mapping
- `CONN` — `ConnectivityState` transitions
- `URL` — base-URL handling
- `CFG` — URL-not-configured short-circuit
- `RM` — `SpoolmanRepository` method-level
- `FIND` — `findSpoolsByCardUid`
- `CHAIN` — `createSpoolForNewFilament` orchestration
- `VEN` — vendor lookup-or-create
- `FIL` — filament lookup-or-create
- `SP` — spool POST
- `APPEND` — `appendCardUidToSpool`
- `REMOVE` — `removeCardUidFromSpool`
- `CACHE` — cache invalidation
- `PROBE` — `probe()`
- `REFRESH` — `refresh()`
- `LOG` — logging interceptor
- `T` — tests
- `MIG` — migration of v1 `SpoolmanService`
- `CTOR`, `FAC`, `CANCEL`, `REQ`, `API` — see `domain-entities.md` for cross-reference

---

## 1. SpoolmanOutcome mapping (NFR-7.1)

- **BR-U3-O-1** — Every Retrofit call MUST be wrapped in a `runCatching { … }` shape; the catcher is the *only* place that produces `NetworkError` / `ParseError` outcomes.
- **BR-U3-O-2** — `Response.isSuccessful` `&& body() != null` ⇒ `Success(body)`; the caller need not re-check.
- **BR-U3-O-3** — `Response.isSuccessful` `&& body() == null` ⇒ `ParseError(IllegalStateException("empty body"))`. Spoolman never legitimately returns 2xx without a body for the verbs U3 uses.
- **BR-U3-O-4** — `!Response.isSuccessful` ⇒ `HttpError(code, errorBodyOrStatus)` where `errorBodyOrStatus` is the truncated error body (≤ 200 chars; falls back to status line if body empty).
- **BR-U3-O-5** — `IOException` (any subclass: `SocketTimeoutException`, `UnknownHostException`, `ConnectException`, …) ⇒ `NetworkError(cause)`.
- **BR-U3-O-6** — `JsonSyntaxException` / `JsonParseException` / Retrofit conversion failure ⇒ `ParseError(cause)`.
- **BR-U3-O-7** — `CancellationException` MUST be rethrown without producing an outcome (Kotlin coroutines correctness; consumers cancel via parent scope).
- **BR-U3-O-8** — Any other `Throwable` (programming errors only — `IllegalStateException`, etc.) MUST be rethrown; runtime errors are not silently mapped to `NetworkError` (they are bugs, not network conditions).
- **BR-U3-O-9** — `flatMap` short-circuits on first non-`Success`; the unchecked `as SpoolmanOutcome<R>` cast on the error variants is sound because none of them carry a `T`.
- **BR-U3-O-10** — `map(f)` ≡ `flatMap { Success(f(it)) }` — a single test covers consistency.

## 2. ConnectivityState transitions (FR-10.2 / Q-CD1=A / Q-U3-8=A)

- **BR-U3-CONN-1** — Initial value of `connectivity` is `ConnectivityState.Unknown` (Q-U3-8=A).
- **BR-U3-CONN-2** — Successful `Success(_)` outcome on any HTTP call ⇒ `connectivity = Reachable`.
- **BR-U3-CONN-3** — `HttpError(code, _)` outcome ⇒ `connectivity = Reachable` (server responded; banner should NOT appear; caller decides based on `code`).
- **BR-U3-CONN-4** — `NetworkError(cause)` where `cause` is an `IOException` ⇒ `connectivity = Unreachable(reason)`. `reason` is `cause.message` if non-blank else `cause::class.simpleName ?: "Network error"`.
- **BR-U3-CONN-5** — `NetworkError(cause = UrlNotConfigured)` ⇒ `connectivity = Unknown` (NOT `Unreachable`); banner stays hidden.
- **BR-U3-CONN-6** — `ParseError(_)` ⇒ `connectivity` UNCHANGED (server-data fault, not a connectivity fault).
- **BR-U3-CONN-7** — Connectivity update is atomic — performed before the outcome is returned to the caller, so a downstream `flatMap` always sees the post-update value.
- **BR-U3-CONN-8** — On URL change to a blank string, `connectivity` resets to `Unknown` (BR-U3-URL-3 trigger).

## 3. Base-URL handling (Q-U3-3=C / Q-U3-11=A)

- **BR-U3-URL-1** — `SpoolmanApiFactory.create(baseUrl)` normalises the URL to end with `/`. (Retrofit baseUrl contract.)
- **BR-U3-URL-2** — `SpoolmanRepository` collects `settings.settings.map { it.url }.distinctUntilChanged()` exactly once in an `init` block, on the Hilt-managed `@Singleton CoroutineScope`.
- **BR-U3-URL-3** — When the URL changes:
  - **Blank → blank**: no-op.
  - **Blank → non-blank**: build new `SpoolmanApi`; clear all caches (vendors / filaments / spools); set `connectivity = Unknown` (no automatic probe — Q-U3-8=A).
  - **Non-blank → non-blank (different)**: build new `SpoolmanApi`; clear all caches; set `connectivity = Unknown`.
  - **Non-blank → blank**: drop cached `SpoolmanApi`; clear all caches; set `connectivity = Unknown`.
- **BR-U3-URL-4** — The cached `SpoolmanApi` reference is `@Volatile`; readers in suspend methods read it once at the start of the call.
- **BR-U3-URL-5** — A null/missing `SpoolmanApi` (URL blank) MUST short-circuit every method per BR-U3-CFG-1.

## 4. URL-not-configured short-circuit (FR-10.1 / Q-U3-8=A)

- **BR-U3-CFG-1** — Every public suspend method MUST check `cachedApi` at entry. If null (URL blank), short-circuit:
  - `findSpoolsByCardUid` → `NetworkError(UrlNotConfiguredException)`.
  - `createSpoolForNewFilament` → `NetworkError(UrlNotConfiguredException)`.
  - `appendCardUidToSpool` / `removeCardUidFromSpool` → `NetworkError(UrlNotConfiguredException)`.
  - `probe` → `NetworkError(UrlNotConfiguredException)`.
  - `refresh` → `NetworkError(UrlNotConfiguredException)`.
- **BR-U3-CFG-2** — `UrlNotConfiguredException` is a private `class UrlNotConfiguredException : IOException("Spoolman URL not configured")` — it extends `IOException` so generic `IOException`-catching tests work, but its identity is checkable.
- **BR-U3-CFG-3** — `connectivity` set to `Unknown` on every short-circuit (BR-U3-CONN-5).

## 5. Repository method-level (NFR-1.2 / NFR-7.1)

- **BR-U3-RM-1** — Every public suspend method runs HTTP work inside `withContext(ioDispatcher) { ... }`.
- **BR-U3-RM-2** — No public method ever throws to the caller (except `CancellationException` per BR-U3-O-7).
- **BR-U3-RM-3** — `MutableStateFlow.value` updates are direct (no atomic CAS needed — single-writer per cache).
- **BR-U3-RM-4** — Repository instance is constructed once at app startup (`@Singleton`) and lives until process death.

## 6. `findSpoolsByCardUid` (FR-3.2 / S-3.1 / Q-U3-1=A)

- **BR-U3-FIND-1** — If `uid.hex.isEmpty()`, return `Success(emptyList())` WITHOUT firing the GET (Q-U3-1=A — empty UID is a programmer error rather than a query, and we refuse to ask Spoolman for `lot_nr=card_uid:` since substring match would be everything).
- **BR-U3-FIND-2** — Otherwise, call `api.findSpoolsByLotNr("${CardUidEncoding.PREFIX}${uid.hex}")`. The full `card_uid:<hex>` is the substring filter (FR-3.2 — the `card_uid:` prefix prevents false positives from raw hex collisions inside opaque tails).
- **BR-U3-FIND-3** — Multi-match is NOT a repository-level error — repository returns `Success(list)` with however many spools matched. Disambiguation is U5/U6a's job (S-3.3).
- **BR-U3-FIND-4** — `findSpoolsByCardUid` does NOT mutate the `spools` cache — it's a query, not a list refresh.
- **BR-U3-FIND-5** — Standard error mapping per BR-U3-O-* applies; standard connectivity transition per BR-U3-CONN-* applies.

## 7. `createSpoolForNewFilament` orchestration (FR-7 / S-7.1 / S-7.2 / S-7.3 / Q-U3-10=C)

- **BR-U3-CHAIN-1** — Validate request inputs (BR-U3-REQ-* in domain-entities §2.3). Empty `vendorName`/`materialName`/`cardUid.hex` short-circuit before any network call.
- **BR-U3-CHAIN-2** — Step A: `resolveOrCreateVendor(req.vendorName.trim())` → `SpoolmanOutcome<SpoolmanVendor>`.
- **BR-U3-CHAIN-3** — Step B: `resolveOrCreateFilament(vendor, req)` → `SpoolmanOutcome<SpoolmanFilament>`.
- **BR-U3-CHAIN-4** — Step C: `createSpoolStep(filament, req.cardUid)` → `SpoolmanOutcome<SpoolmanSpool>`.
- **BR-U3-CHAIN-5** — Steps are composed via `SpoolmanOutcome.flatMap` — first non-`Success` short-circuits and propagates (FR-7.4 / Q11=A).
- **BR-U3-CHAIN-6** — On `Success(spool)`, patch the `spools` cache (prepend), the `filaments` cache (insert if it was newly created), and the `vendors` cache (insert if it was newly created). One write per `MutableStateFlow`.
- **BR-U3-CHAIN-7** — Partial-commit policy: NO compensating action is performed. If Step B fails, the vendor created in Step A remains in Spoolman. Documented behaviour — caller's retry through `existing-spool path` will find the vendor by name.
- **BR-U3-CHAIN-EMPTY** — `req.cardUid.hex.isEmpty()` ⇒ `NetworkError(IllegalArgumentException("cardUid is empty"))` (consistency with BR-U3-FIND-1 stance — empty UID is never a real input).

### 7.1 Vendor lookup-or-create (`resolveOrCreateVendor`) — S-7.1 / FR-7.1 / FR-7.5

- **BR-U3-VEN-1** — GET `/api/v1/vendor` (full list).
- **BR-U3-VEN-2** — Match: `existing.name.equals(name, ignoreCase = true)` — first match wins (Spoolman's vendor names are unique by convention; ignoreCase per FR-7.1).
- **BR-U3-VEN-3** — Match found ⇒ `Success(existing)`; no POST issued.
- **BR-U3-VEN-4** — No match ⇒ `POST /api/v1/vendor` with `CreateVendorRequest(name = name)`; return the response body wrapped in `Success`.
- **BR-U3-VEN-5** — `name` is the trimmed input (BR-U3-CHAIN-1) — leading/trailing whitespace never reaches Spoolman.
- **BR-U3-VEN-6** — Cache patch on POST success: prepend new vendor into `vendors` `StateFlow` (BR-U3-CACHE-2).

### 7.2 Filament lookup-or-create (`resolveOrCreateFilament`) — S-7.2 / FR-7.2 / FR-7.5

- **BR-U3-FIL-1** — GET `/api/v1/filament` (full list).
- **BR-U3-FIL-2** — Match tuple: `(filament.vendor?.id == vendor.id) && filament.material.equalsIgnoreCase(req.materialName.trim()) && filament.color_hex == req.colorHex && variantsEquivalent(filament.name?, req.variant)`.
- **BR-U3-FIL-3** — `variantsEquivalent` treats `null ≡ ""` ≡ `"  "` (whitespace-only collapses to empty after trim) per S-7.2 / Q-U3 §domain-entities BR-U3-REQ-FIL-VARIANT.
  - Note: Spoolman's filament has no dedicated `variant` field — v2 stores variant in `name` for the v2.0 timeline. v2.1 may move it; that's not a U3 concern.
- **BR-U3-FIL-4** — Match found ⇒ `Success(existing)`; no POST issued.
- **BR-U3-FIL-5** — No match ⇒ `POST /api/v1/filament` with `CreateFilamentRequest(name = req.variant?.takeIf { it.isNotBlank() }?.trim(), vendor_id = vendor.id, material = req.materialName.trim(), color_hex = req.colorHex, settings_extruder_temp = req.tempRanges.extruderMin, settings_bed_temp = req.tempRanges.bedMin)`.
  - Rationale: Spoolman accepts a single extruder/bed temp; v2 ships the `min` of the user's range as the canonical setting. (Existing v1 behaviour reused.)
- **BR-U3-FIL-6** — Cache patch on POST success: prepend into `filaments`.
- **BR-U3-FIL-VARIANT** — Per BR-U3-REQ-* normalisation: `null` and blank are stored as null; trimmed otherwise.
- **BR-U3-FIL-TEMP-NULL** — Null temp values are omitted from the JSON body (Gson default — `@Expose` is not used; the data class declares nullables with default `null`, and `GsonBuilder` is left at defaults so nulls serialise as `null`). Acceptable per Spoolman's API.

### 7.3 Spool POST (`createSpoolStep`) — S-7.3 / FR-7.3

- **BR-U3-SP-1** — POST `/api/v1/spool` with `CreateSpoolRequest(filament_id = filament.id, lot_nr = "${CardUidEncoding.PREFIX}${uid.hex}")`.
- **BR-U3-SP-2** — `lot_nr` is exactly `card_uid:<hex>` — no trailing comma, no opaque tail (this is a NEW spool, no existing UIDs to preserve).
- **BR-U3-SP-3** — Cache patch on success: prepend into `spools`.

## 8. `appendCardUidToSpool` (FR-4.6 / FR-6.2 / S-4.5)

- **BR-U3-APPEND-1** — Read step: GET `/api/v1/spool` (or use cached value if present + non-stale; for U3 simplicity, GET `/api/v1/spool` listing and find by `id` — Spoolman doesn't have `GET /api/v1/spool/{id}` returning a singular). Alternative: extend `SpoolmanApi` with `getSpool(id)` — already present (BR-U3-API-1 explicitly lists this in the interface? — no, the v2 interface listed in domain-entities does NOT include it. Decision: use the cached `spools` `StateFlow` if populated, else perform a `refresh()` first, then read-modify-write).
  - **Refinement**: Add `@GET("api/v1/spool/{id}")` to `SpoolmanApi` (single fetch — already present in v1). This avoids an unnecessary list refresh. Updated in domain-entities §2.5 ⇒ effective extension via amendment below.
- **BR-U3-APPEND-2** — Decode existing `lot_nr` via `CardUidEncoding.decode(spool.lot_nr ?: "")`.
- **BR-U3-APPEND-3** — Compute new `uids`: `decoded.uids` ∪ `{uid}` — order preserved, duplicates dropped (BR-U2-ENC-1 idempotency).
- **BR-U3-APPEND-4** — Encode via `CardUidEncoding.encode(newUids, decoded.opaque)`.
- **BR-U3-APPEND-5** — PATCH `/api/v1/spool/{id}` with `UpdateSpoolLotNrRequest(lot_nr = encoded)`.
- **BR-U3-APPEND-6** — On success, replace cached spool by `id` in `spools` `StateFlow` (BR-U3-CACHE-1).
- **BR-U3-APPEND-7** — If the encoded `lot_nr` equals the existing `lot_nr` (UID already present and opaque tail unchanged), the PATCH is still issued (idempotent server-side; saves a conditional branch and keeps connectivity transition consistent).
- **BR-U3-APPEND-8** — `uid.hex.isEmpty()` ⇒ `NetworkError(IllegalArgumentException("uid is empty"))` short-circuit.

## 9. `removeCardUidFromSpool` (FR-5.2 / S-5.2 → consumed by U6b)

- **BR-U3-REMOVE-1** — Read step: GET `/api/v1/spool/{id}` (per amendment in BR-U3-APPEND-1).
- **BR-U3-REMOVE-2** — Decode existing `lot_nr`; remove the matched UID by canonical-hex equality.
- **BR-U3-REMOVE-3** — Encode the trimmed list (preserving opaque tail) — empty `uids` + non-empty `opaque` follows BR-U2-ENC-5 (opaque-only, no leading comma).
- **BR-U3-REMOVE-4** — PATCH `/api/v1/spool/{id}` with the new `lot_nr`.
- **BR-U3-REMOVE-5** — If the encoded `lot_nr` is `""` (no UIDs and no opaque), still send the PATCH with `lot_nr = ""` (Spoolman accepts empty string for cleared `lot_nr`).
- **BR-U3-REMOVE-6** — On success, replace cached spool by `id` in `spools`.
- **BR-U3-REMOVE-7** — `uid.hex.isEmpty()` ⇒ `NetworkError(IllegalArgumentException("uid is empty"))`.
- **BR-U3-REMOVE-8** — Idempotency: if the UID is already absent from `decoded.uids`, the PATCH is still issued (no-op semantics; server's lot_nr unchanged) — keeps connectivity transitions consistent.

## 10. Cache invalidation (Q-U3-2=A / NFR-7.2)

- **BR-U3-CACHE-1** — On any successful PATCH/POST that returns a full row (`SpoolmanSpool` / `SpoolmanFilament` / `SpoolmanVendor`), the relevant `MutableStateFlow.value` is updated in place: replace by `id` if present, prepend if not.
- **BR-U3-CACHE-2** — `vendors` cache update applies on `createVendor` success (FR-7.1).
- **BR-U3-CACHE-3** — `filaments` cache update applies on `createFilament` success (FR-7.2).
- **BR-U3-CACHE-4** — `spools` cache update applies on `createSpool`, `appendCardUidToSpool`, and `removeCardUidFromSpool` successes.
- **BR-U3-CACHE-5** — `findSpoolsByCardUid` does NOT update the cache (BR-U3-FIND-4).
- **BR-U3-CACHE-6** — On URL change (BR-U3-URL-3), all three caches are reset to `emptyList()`.
- **BR-U3-CACHE-7** — `refresh()` is the only force-reload path for full re-population (FR-8.3, NFR-7.2 pull-to-refresh equivalent).

## 11. `probe()` (S-9.1 / FR-10.2)

- **BR-U3-PROBE-1** — Calls `api.getInfo()`; treats any successful response as `Reachable`.
- **BR-U3-PROBE-2** — `Success(_)` ⇒ `Success(Unit)` — body is discarded (only the connectivity side effect matters to consumers).
- **BR-U3-PROBE-3** — Standard error mapping per BR-U3-O-*; standard connectivity transition per BR-U3-CONN-*.
- **BR-U3-PROBE-4** — `probe()` does NOT touch the caches (FR-10.2 banner intent — probe is a connectivity check, not a data refresh).

## 12. `refresh()` (NFR-7.2 / FR-8.3)

- **BR-U3-REFRESH-1** — Sequentially calls `listVendors`, `listFilaments`, `listSpools`. (Not parallel — Q11=A short-circuit on first failure, simpler reasoning, no `awaitAll`.)
- **BR-U3-REFRESH-2** — First non-`Success` aborts and returns the failing outcome; later steps are not attempted.
- **BR-U3-REFRESH-3** — On full success, all three `StateFlow`s are updated in the order vendors → filaments → spools, then `Success(Unit)` returned.
- **BR-U3-REFRESH-4** — On success, `connectivity = Reachable`; on `NetworkError`, `connectivity = Unreachable`; on `HttpError`, `connectivity = Reachable` (server responded — even if 5xx, that's reachable).

## 13. Logging interceptor (Q-U3-5=B)

- **BR-U3-LOG-1** — Debug builds attach `HttpLoggingInterceptor(level = BASIC)` to the shared `OkHttpClient`.
- **BR-U3-LOG-2** — Release builds attach NO logging interceptor.
- **BR-U3-LOG-3** — Build-variant detection uses `BuildConfig.DEBUG` — no separate Hilt qualifier; the interceptor is conditionally added when constructing the `OkHttpClient` provider in `NetworkModule`.
- **BR-U3-LOG-4** — No request/response BODY logging is ever enabled by default (defence-in-depth against accidentally logging a Spoolman dump).

## 14. Migration of v1 `SpoolmanService` (Q-U3-6=A)

- **BR-U3-MIG-1** — `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanService.kt` is **DELETED** in this unit (big-bang).
- **BR-U3-MIG-2** — The file's classes (`SpoolmanService`, v1 `interface SpoolmanApi`) MUST have ZERO references in `app/src/main` after U3.
  - `FilamentSpool.fromSpoolman(SpoolmanSpool)` is RETAINED — it consumes the v2-extended `SpoolmanSpool` type unchanged.
  - `MaterialDatabase` is unchanged in U3 (U8's concern).
- **BR-U3-MIG-3** — A grep at end of U3 verifies `grep -rn "SpoolmanService\|class SpoolmanService" app/src/main` returns zero matches.

## 15. Tests (Q-U3-9=A / NFR-4.1)

The unit test surface for U3 ships against a `FakeSpoolmanApi` (test class) — never real HTTP.

- **BR-U3-T-1** — `SpoolmanOutcomeTest` covers `flatMap` + `map` short-circuit, including the unchecked-cast soundness (4 cases: success+success / success+error / error+success / error+error).
- **BR-U3-T-2** — `ConnectivityStateTransitionTest` covers each transition rule (BR-U3-CONN-1 .. CONN-7).
- **BR-U3-T-3** — `SpoolmanRepositoryProbeTest` covers BR-U3-PROBE-* (success / HttpError / NetworkError / ParseError / URL-not-configured).
- **BR-U3-T-4** — `SpoolmanRepositoryFindByCardUidTest` covers BR-U3-FIND-* (empty UID short-circuit / single match / multi-match / no match / HttpError / NetworkError / URL-not-configured).
- **BR-U3-T-5** — `SpoolmanRepositoryAppendCardUidTest` covers BR-U3-APPEND-* (UID added when absent / UID idempotent when present / opaque tail preserved / empty UID rejected / HttpError / NetworkError on read step / NetworkError on PATCH step).
- **BR-U3-T-6** — `SpoolmanRepositoryRemoveCardUidTest` covers BR-U3-REMOVE-* (UID removed / UID absent is no-op / opaque tail preserved / last-UID-removed clears to `""` / empty UID rejected / HttpError / NetworkError).
- **BR-U3-T-7** — `SpoolmanRepositoryCreateChainTest` covers BR-U3-CHAIN-* / VEN-* / FIL-* / SP-* (vendor lookup hit / vendor lookup miss → POST / filament lookup hit / filament lookup miss → POST / spool POST / fail at vendor / fail at filament / fail at spool / empty UID rejected / case-insensitive vendor match / variant null≡"" equivalence / variant whitespace-only normalised).
- **BR-U3-T-8** — `SpoolmanRepositoryRefreshTest` covers BR-U3-REFRESH-* (full success / fail at vendors / fail at filaments / fail at spools).
- **BR-U3-T-9** — `SpoolmanRepositoryUrlChangeTest` covers BR-U3-URL-* (blank → non-blank initialises / non-blank → different rebuilds + clears caches / non-blank → blank tears down / connectivity returns to Unknown after URL clear).
- **BR-U3-T-10** — `SpoolmanRepositoryCacheInvalidationTest` covers BR-U3-CACHE-* (PATCH/POST patch in place / find does not patch / refresh repopulates).
- **BR-U3-T-11** — Each test asserts `connectivity` value after the call to lock down BR-U3-CONN-* coverage.
- **BR-U3-T-12** — Tests use `kotlinx-coroutines-test` `runTest` + `UnconfinedTestDispatcher` (already on the classpath via U1).
- **BR-U3-T-13** — `Turbine` is used to assert `StateFlow` emissions where the cache is observed (already on the classpath via U1).

## 16. CancellationException correctness

- **BR-U3-CANCEL-1** — Every `runCatching`-shaped HTTP wrapper MUST rethrow `CancellationException` (the typical Kotlin pattern: `catch (e: CancellationException) { throw e }`). Tests cover this for at least one method (`findSpoolsByCardUid`).

---

## Cross-rule consistency checks

- BR-U3-O-7 + BR-U3-CANCEL-1 are the same constraint stated from two angles; a single test covers both.
- BR-U3-CONN-3 (HttpError → Reachable) plus BR-U3-PROBE-3 explicitly contradict the v1 behaviour where any error meant "offline" — call this out in `business-logic-model.md` §3.
- BR-U3-FIND-1 (empty-UID short-circuit) + BR-U3-CHAIN-EMPTY + BR-U3-APPEND-8 + BR-U3-REMOVE-7 form a consistent "empty UID is never a valid network input" stance; tests assert all four.
