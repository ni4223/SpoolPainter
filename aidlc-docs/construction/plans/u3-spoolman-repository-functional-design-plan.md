# U3 — Functional Design Plan

**Stage**: CONSTRUCTION → Per-Unit Loop → Functional Design (U3)
**Unit**: U3 — Spoolman Client Overhaul
**Source artefacts**:
- `aidlc-docs/inception/application-design/unit-of-work.md` §3-U3
- `aidlc-docs/inception/application-design/components.md` §2.4 (`SpoolmanRepository`), §2.6 Remote (`SpoolmanApi`, `SpoolmanModels`), §2.7 (`NetworkModule`), §3 (interface signatures)
- `aidlc-docs/inception/application-design/component-methods.md` §2 (`SpoolmanRepository`), `NewSpoolRequest`, `SpoolmanOutcome`
- `aidlc-docs/inception/application-design/services.md` §1 (consumer use-cases), §6 (Settings probe path), §7 (banner suppression rule)
- `aidlc-docs/inception/requirements/requirements.md` FR-2.1 / FR-2.2 / FR-3.1 / FR-3.2 / FR-4.6 / FR-5.2 / FR-6.2 / FR-7.1 / FR-7.2 / FR-7.3 / FR-7.4 / FR-7.5 / FR-8.3 / FR-10.1 / FR-10.2 / FR-10.3 / NFR-1.2 / NFR-7.1 / NFR-7.2 / NFR-7.3 / NFR-7.4
- `aidlc-docs/inception/user-stories/stories.md` S-3.1 / S-3.3 / S-4.5 / S-7.1 / S-7.2 / S-7.3 / S-9.1 / S-10.2

---

## 1. Unit Context (Step 1)

### 1.1 Scope (locked by Units Generation)

- **`SpoolmanApi`** (Retrofit) — extended for v2:
  - `GET /api/v1/spool?lot_nr=card_uid:<uid>` — UID-substring lookup (FR-3.2 / S-3.1).
  - `GET /api/v1/vendor` — vendor list (FR-7.1 lookup, FR-8.3 dropdown merge).
  - `GET /api/v1/filament` — filament list (FR-7.2 lookup, FR-8.3 dropdown).
  - `POST /api/v1/vendor` — create vendor (FR-7.1).
  - `POST /api/v1/filament` — create filament (FR-7.2).
  - `POST /api/v1/spool` — create spool with `lot_nr` set to `card_uid:<uid>` (FR-7.3).
  - `PATCH /api/v1/spool/{id}` — update `lot_nr` field (FR-4.6 append, FR-5.2 remove, FR-6.2 append).
  - `GET /api/v1/info` — health probe (S-9.1).
- **`SpoolmanRepository`** (`@Singleton`, Hilt) — single point of access for all Spoolman interactions:
  - `findSpoolsByCardUid(uid: CardUid): SpoolmanOutcome<List<Spool>>` (FR-3 / S-3.1).
  - `createSpoolForNewFilament(req: NewSpoolRequest): SpoolmanOutcome<Spool>` orchestrating the FR-7 chain.
  - `appendCardUidToSpool(spoolId: Int, uid: CardUid): SpoolmanOutcome<Spool>` (FR-4.6 / FR-6.2 / S-4.5).
  - `removeCardUidFromSpool(spoolId: Int, uid: CardUid): SpoolmanOutcome<Spool>` (FR-5.2; consumed by U6b).
  - `probe(): SpoolmanOutcome<Unit>` (S-9.1 Test connection).
  - `refresh(): SpoolmanOutcome<Unit>` (force re-fetch of `vendors`, `filaments`, `spools` caches).
  - `vendors / filaments / spools: StateFlow<List<…>>` cache exposure (Q-S4=A).
  - `connectivity: StateFlow<ConnectivityState>` (Q-CD1=A) — single connectivity source observed by `MainViewModel` (banner) + `SettingsViewModel`.
- **`SpoolmanOutcome<T>`** sealed type (Q-CM2=B) — `Success<T> | HttpError | NetworkError | ParseError`. Error contract for every flow unit (NFR-7.1 — no silent swallow).
- **`ConnectivityState`** sealed type — `Unknown | Reachable | Unreachable(reason)`. `Unknown` ≡ URL not configured.
- **`NewSpoolRequest`** data class — DTO for the FR-7 chain.
- **Wire models** in `SpoolmanModels.kt` — extended for v2 fields (`Vendor` with `id` + `name`, `Filament` with `vendor_id` + `material` + `color_hex` + extra settings, `Spool` with `id` + `filament` + `lot_nr` + `archived`, plus POST/PATCH request bodies).
- **Migration of v1 `SpoolmanService`** — removed; all callers reroute through `SpoolmanRepository`. `FilamentSpool.fromSpoolman(SpoolmanSpool)` retained (still used by U5/U6a for dropdown projection).
- **Hilt wiring in `NetworkModule`** — provides OkHttp client (with logging interceptor + LAN-friendly timeouts), Retrofit, base-URL re-creation when `SettingsRepository.url` changes (Q-U3-3 below), `SpoolmanApi`. `SpoolmanRepository` is `@Inject`-constructor — no `@Provides` needed.

### 1.2 Cross-unit consumers (locked by `unit-of-work-dependency.md`)

- **U5 (Read-and-Pair)** consumes `findSpoolsByCardUid` + `connectivity` + `filaments` + `spools` + `SpoolmanOutcome`.
- **U6a (Create-and-Pair)** consumes `createSpoolForNewFilament` + `appendCardUidToSpool` (existing-spool path) + `connectivity`.
- **U6b (Move-on-bind + Two-tag)** consumes `removeCardUidFromSpool` + `appendCardUidToSpool`.
- **U7 (Raw write)** does **not** consume `SpoolmanRepository` (FR-4.8 — Spoolman-free side mode); the unit must take care never to invoke it.
- **U8 (Material/Brand catalogue)** consumes `vendors` + `filaments` (precedence merge per FR-8.3).
- **U9 (Settings + banner)** consumes `connectivity` + `probe()`.

### 1.3 Out-of-scope for U3 (deferred)

- `MainViewModel` / `SettingsViewModel` integration — U5 / U9.
- `MoveOnBindUseCase` orchestration of `remove + append` — U6b (Q-S3=C — sequencing lives in the use-case).
- `MaterialBrandRepository` precedence merge of vendors/filaments — U8.
- Cleartext-traffic config audit (NFR-7.3 unchanged) — verified at U10 release polish.
- Banner UI / Retry control wiring (S-10.2) — U9.

---

## 2. Plan Steps (checkboxes)

### 2.1 Wire-level API contract & domain entities
- [ ] 2.1.1 Lock the `SpoolmanApi` Retrofit method shapes (HTTP verbs, paths, query params, body types, response wrappers).
- [ ] 2.1.2 Lock the wire model field set for `Vendor`, `Filament`, `Spool`, and the POST/PATCH request bodies.
- [ ] 2.1.3 Lock the `NewSpoolRequest` shape (already defined in `component-methods.md`; restate for U3).
- [ ] 2.1.4 Lock `SpoolmanOutcome<T>` (already defined; restate canonical mapping rules: HttpError vs NetworkError vs ParseError).
- [ ] 2.1.5 Lock `ConnectivityState` shape (`Unknown | Reachable | Unreachable(reason: String?)`).

### 2.2 Repository business logic & business rules
- [ ] 2.2.1 Define `findSpoolsByCardUid` rules: build query string `card_uid:<hex>` (use `CardUidEncoding.PREFIX`); single / multiple / no-match all return `Success(list)` (multi-match disambiguation belongs to U5/U6a — repository is pure data fetch).
- [ ] 2.2.2 Define `createSpoolForNewFilament` orchestration: vendor lookup-or-create (case-insensitive name match per FR-7.1) → filament lookup-or-create (match on `(vendor_id, material, color_hex, variant)` per FR-7.2; null/empty variant treated as equivalent) → spool POST with `lot_nr=card_uid:<uid>` (FR-7.3). Define short-circuit on first non-2xx (FR-7.4 / Q11=A — no partial-commit retry; surface fault, leave any partial state intact and let caller retry).
- [ ] 2.2.3 Define `appendCardUidToSpool` PATCH composition: GET-then-PATCH read-modify-write of `lot_nr`; uses `CardUidEncoding.decode` → ensure-present → `CardUidEncoding.encode`. Idempotent by construction (BR-U2-ENC-1 dedup carries over). Preserves opaque tail (FR-2.2).
- [ ] 2.2.4 Define `removeCardUidFromSpool` PATCH composition: GET-then-PATCH; remove only the matched UID; preserve opaque tail and any other UIDs (FR-5.2 / Q6=A); idempotent if UID already absent.
- [ ] 2.2.5 Define `probe()` semantics: GET `/api/v1/info` (cheap), update `connectivity`, never throw — always returns `SpoolmanOutcome<Unit>`. Behaviour when URL not configured: short-circuit to `NetworkError(cause = UrlNotConfigured)` (per services.md §6 contract) and set `connectivity = Unknown`.
- [ ] 2.2.6 Define `refresh()` semantics: parallel GETs for vendors / filaments / spools; first failure aborts the rest and returns the failing outcome; success populates all three `StateFlow`s atomically; updates `connectivity = Reachable`.
- [ ] 2.2.7 Define cache invalidation: every successful PATCH/POST patches the relevant cached list in place (replace-by-id), so observers see the change without a fresh GET. `refresh()` is the only force-reload path (NFR-7.2 — pull-to-refresh).
- [ ] 2.2.8 Define connectivity transitions: every Retrofit call updates `connectivity` as a side effect — success → `Reachable`; HttpError(404 on `/api/v1/info`-style probe) → `Unreachable(reason)`; NetworkError → `Unreachable(reason)`; ParseError → keep prior connectivity (it's a server-data fault, not a connectivity fault); URL-not-configured → `Unknown`.
- [ ] 2.2.9 Define base-URL handling: `SpoolmanApi` is built lazily against `SettingsRepository.settings.value.url`; URL change triggers Retrofit rebuild. URL trailing-slash normalisation and `http://` validation rules.
- [ ] 2.2.10 Define error-mapping rules: any `HttpException`/`Response.code() !in 200..299` → `HttpError(code, message)`; `IOException` (incl. `SocketTimeoutException`, `UnknownHostException`) → `NetworkError(cause)`; Gson `JsonSyntaxException` / Retrofit conversion failure → `ParseError(cause)`. Cancellation (`CancellationException`) is rethrown — never swallowed (NFR-7.1).
- [ ] 2.2.11 Define threading: every suspend repository method runs `withContext(Dispatchers.IO)` for the Retrofit call; cache StateFlow updates use `MutableStateFlow.value =` on calling dispatcher (no Main-thread requirement).

### 2.3 Validation logic & error surfaces
- [ ] 2.3.1 Define what happens if `findSpoolsByCardUid` is called with an empty `CardUid("")` — repository returns `Success(emptyList())` without making a network call (avoids querying for `card_uid:` with no hex, which Spoolman's substring search would treat as a match-everything wildcard). Reason: U2 made `CardUid.fromBytes(empty)` total; the policy decision lives one layer up.
- [ ] 2.3.2 Define what happens if the URL is not configured (empty-string in `SettingsRepository`): every method short-circuits to `NetworkError(cause = UrlNotConfigured)`; `connectivity` stays `Unknown`. No Retrofit call is fired.
- [ ] 2.3.3 Define what happens if a PATCH read-modify-write loses the race against a concurrent edit (Spoolman's API has no ETag): we accept last-write-wins; document that v2 is single-user (NFR-1 implicit) so the race is a non-goal.
- [ ] 2.3.4 Define what happens during the FR-7 chain when the *spool* POST fails after the vendor + filament were already POSTed — partial state survives in Spoolman (no rollback per Q11=A); returned outcome's `HttpError.message` describes the partial commit so U6a can surface a useful retry message.
- [ ] 2.3.5 Define what happens during FR-7 chain when an existing vendor matches but its filament POST fails — same: orphan vendor (or just a found vendor) survives, filament is not committed; outcome surfaces the failing step.

### 2.4 Frontend / UI rules — N/A for U3
- U3 ships no UI (per `unit-of-work.md` §3-U3). UI consumers (banner, dropdown) land in U5/U8/U9.

### 2.5 Tests (NFR-4.1 minimum bar; `unit-of-work.md` §3-U3 Tests)
- [ ] 2.5.1 List the unit-test cases U3 must ship with (against a fake `SpoolmanApi` — no real HTTP).

### 2.6 Brownfield migration of v1 `SpoolmanService`
- [ ] 2.6.1 Choose a migration strategy for `SpoolmanService` (single existing v1 class — no UI consumers per current grep, dormant after U2).

---

## 3. Open Questions (Step 3 — `[Answer]:` tags below)

> Choices U3's Functional Design must lock down. Each option lists what becomes invariant if chosen. Defaults flagged "(recommended)" follow the principle "repository is total + pure data + sealed errors", consistent with U2's stance.

### Q-U3-1 — `findSpoolsByCardUid` for empty `CardUid("")`

`CardUid.fromBytes(emptyByteArray) → CardUid("")` is total per U2 Q-U2-1=A. The query Spoolman would receive is `lot_nr=card_uid:` (no hex), which is a substring match for *every* spool whose `lot_nr` starts with `card_uid:` — a catastrophic false-positive set.

**Options**:
- **A** — Repository short-circuits on empty `uid.hex` and returns `Success(emptyList())` without firing the GET. (Cheap, total, defensive.)
- **B** — Repository fires the GET unchanged, returns whatever Spoolman sends back. (Surfaces the bug to U5 — but U5 already treats "multi-match" as an anomaly, which would mask the empty-UID bug as a data anomaly instead of a programmer error.)
- **C** — Repository throws `IllegalArgumentException` on empty UID. (Forces every caller to wrap; breaks the "repository never throws" stance from U2.)

**Recommendation**: **A**. Total + cheap + no surprise multi-match anomaly downstream. Documented as a business rule.

[Answer]: A (recommended) — accepted via "full speed ahead" 2026-05-24.

### Q-U3-2 — Cache invalidation strategy on PATCH/POST success

Three caches: `vendors`, `filaments`, `spools`. After `appendCardUidToSpool` PATCH succeeds, the `spools` cache must reflect the new `lot_nr`.

**Options**:
- **A** — Patch-in-place: replace the matching entry in the cached list using the response body. No extra GET. Fast, but cache + server can drift if the response body is partial.
- **B** — Force a full `refresh()` after every PATCH/POST. Simple correctness; slow on every mutation.
- **C** — Hybrid: patch-in-place for PATCH (response is the full updated `Spool`); full refresh after the FR-7 chain only (because vendor + filament + spool all changed).

**Recommendation**: **A**. Spoolman returns the complete updated row from PATCH (and from `POST /spool` for new-spool path). No drift in v2 use cases. `refresh()` exists as the explicit user-driven re-sync path.

[Answer]: A (recommended) — accepted via "full speed ahead" 2026-05-24.

### Q-U3-3 — Base-URL change handling

`SettingsRepository.url` can change at runtime (user edits Settings). Retrofit instances are immutable.

**Options**:
- **A** — `SpoolmanRepository` rebuilds Retrofit + `SpoolmanApi` lazily inside each suspend call when it observes a URL change. Adds a guard but means at most one extra ms on URL change.
- **B** — `NetworkModule` provides `Provider<SpoolmanApi>` and rebuilds on every fetch (always pays the rebuild cost). Simple but wasteful.
- **C** — `SpoolmanRepository` collects `SettingsRepository.url` and rebuilds Retrofit when it changes; subsequent calls reuse the cached instance. Most efficient but most code.

**Recommendation**: **C**. The rebuild is cheap (Retrofit's builder is lazy; OkHttp is shared across rebuilds via `OkHttpClient` singleton). Worth the small extra code because URL changes are rare and we never want to pay the rebuild cost per call.

[Answer]: C (recommended) — accepted via "full speed ahead" 2026-05-24.

### Q-U3-4 — `OkHttpClient` timeouts

v1's `SpoolmanService` used `connectTimeout=3s`, `readTimeout=5s`. v2 ships on Spoolman-on-LAN, no auth, single-user.

**Options**:
- **A** — Inherit v1 values (3s connect / 5s read / no write timeout default).
- **B** — Increase to 5s connect / 10s read to accommodate slow LAN hosts (e.g., low-power Pi running Spoolman + Octoprint).
- **C** — Aggressively short: 2s connect / 3s read for snappy "is it offline?" feedback per S-10.2.

**Recommendation**: **A**. v1's values were never reported as a problem; preserve behaviour. Optimisation deferred to U10 if needed.

[Answer]: A (recommended) — accepted via "full speed ahead" 2026-05-24.

### Q-U3-5 — Logging interceptor level

v1's `SpoolmanService` used `OkHttpClient.Builder()` with no logging interceptor wired up (the `okhttp3:logging-interceptor` dep is on the classpath but unused). v2's `NetworkModule` is fresh.

**Options**:
- **A** — Off entirely. Quietest; no PII/Spoolman-data exposure in logs.
- **B** — `BASIC` (URL + status only) on debug builds, off on release.
- **C** — `BODY` on debug builds, off on release. Maximum diagnostics; verbose.

**Recommendation**: **B**. Useful for testing-track diagnostics without flooding logcat with bodies. Release builds stay silent (NFR-1 implicit; plus we don't want Spoolman tokens — even though there are none in v2 — exfiltrated to logs).

[Answer]: B (recommended) — accepted via "full speed ahead" 2026-05-24.

### Q-U3-6 — `SpoolmanService.kt` migration strategy

v1's `SpoolmanService` is the only Spoolman client. It's still on disk (kept dormant after U2). Per current grep, no production code calls it (UI was rewired during U1's `MainViewModel` skeleton). Tests do not exist for it.

**Options**:
- **A** — Big-bang delete in U3 alongside the new repository's introduction (matches U2's posture for `OpenSpoolData`).
- **B** — Keep dormant through U5 (Read-and-Pair flow lands), deferring delete until then so U5 has the option to compare paths.
- **C** — Convert the existing `SpoolmanApi` interface into the v2 surface (extend it in place); delete only the `class SpoolmanService` wrapper. Re-uses the package + interface name without introducing a parallel file.

**Recommendation**: **A**. Same rationale as U2's `OpenSpoolData` delete: the new repository covers every prior call site (none in production) and a parallel `SpoolmanService` is dead weight. The new `SpoolmanApi` interface lives in a fresh file (`SpoolmanApi.kt`) at the same package; the v1 `interface SpoolmanApi` from `SpoolmanService.kt` is removed in the same commit.

[Answer]: A (recommended) — accepted via "full speed ahead" 2026-05-24.

### Q-U3-7 — `Spool` / `Filament` / `Vendor` model split

Two reasonable shapes for the wire/domain types:
- v1's flat `SpoolmanSpool` (with embedded `SpoolmanFilament` with embedded `SpoolmanVendor`) under `domain/models`.
- A v2-clean split: wire DTOs (`SpoolDto`, `FilamentDto`, `VendorDto`) under `data/remote/spoolman` + domain models (`Spool`, `Filament`, `Vendor`) under `domain/models`.

**Options**:
- **A** — Reuse v1 wire types (`SpoolmanSpool`, `SpoolmanFilament`, `SpoolmanVendor`) verbatim and just extend with new fields (`lot_nr` for write, `vendor_id` on `Filament`, etc.). `SpoolmanRepository` returns these directly.
- **B** — Introduce v2 domain types (`Spool`, `Filament`, `Vendor`) per `components.md` §2.4 / §3, plus separate POST/PATCH request DTOs under `data/remote/spoolman`. Wire ↔ domain mapping owned by repository.
- **C** — Hybrid: keep v1 wire types as-is for GET reads (no field set change needed for GET), introduce dedicated request DTOs (`CreateVendorRequest`, `CreateFilamentRequest`, `CreateSpoolRequest`, `UpdateSpoolLotNrRequest`) for POST/PATCH only, plus a single `LotNrUpdate(lot_nr: String?)` for PATCH.

**Recommendation**: **C**. Smallest blast radius — keeps the dropdown's existing `FilamentSpool.fromSpoolman(SpoolmanSpool)` projection working untouched, while the POST/PATCH bodies are clean v2-shaped types. Repository's public surface still uses v1 names (`SpoolmanSpool`, `SpoolmanFilament`, `SpoolmanVendor`) — fewer downstream renames in U5/U6/U8.

[Answer]: C (recommended) — accepted via "full speed ahead" 2026-05-24.

### Q-U3-8 — `connectivity` `StateFlow` initial value

`SpoolmanRepository` is constructed at app startup; `connectivity` must publish a value immediately.

**Options**:
- **A** — Initial value `Unknown` (URL not yet read). Transitions on first call. Banner stays hidden until first explicit attempt.
- **B** — Repository emits `Unknown` until it has read `SettingsRepository.url`; then emits `Unknown` if blank, or auto-fires a `probe()` and emits `Reachable`/`Unreachable` based on result.
- **C** — Initial value `Unreachable("not yet probed")`. Banner could appear at startup until a probe succeeds.

**Recommendation**: **A**. No automatic startup probe (avoids surprising the user with a network call before they've opened Settings). Banner is hidden when `Unknown` per services.md §7. First lookup/refresh updates `connectivity` naturally.

[Answer]: A (recommended) — accepted via "full speed ahead" 2026-05-24.

### Q-U3-9 — Tests scope (which behaviours U3 ships with unit tests for)

`unit-of-work.md` §3-U3 lists test bullets at a high level. Choose what U3 commits to as the testable surface vs. what defers to integration in U5/U6.

**Options**:
- **A** — Tests for **every method** on `SpoolmanRepository` (probe / find / create chain / append / remove / refresh / connectivity transitions / cache invalidation), against a fake `SpoolmanApi`. Highest coverage, ~30+ test cases.
- **B** — Tests for the **mutating** methods only (create chain, append, remove) + connectivity transitions + error mapping. Find / probe / refresh covered indirectly via U5's use-case tests. ~15–20 test cases.
- **C** — Tests for the **public happy paths** + each `SpoolmanOutcome` branch (`HttpError` / `NetworkError` / `ParseError`) once per method category (read / write). ~12 test cases.

**Recommendation**: **A**. U3 is the cross-unit boundary type for U5..U8; the more invariants we lock here, the fewer ways downstream units can drift. Same posture as U2 (64 tests).

[Answer]: A (recommended) — accepted via "full speed ahead" 2026-05-24.

### Q-U3-10 — Private FR-7 chain helpers — public for testing or not

The vendor lookup-or-create + filament lookup-or-create steps are reusable building blocks (FR-7.5 reuse rule). They could be private helpers inside `createSpoolForNewFilament`, or pulled out as `internal` functions for direct test coverage.

**Options**:
- **A** — Keep all chain steps private inside `createSpoolForNewFilament`; test only via the orchestrator (more end-to-end, more brittle when intermediate behaviour changes).
- **B** — Extract `internal suspend fun resolveOrCreateVendor(name)` + `internal suspend fun resolveOrCreateFilament(...)` so each can be tested in isolation. The orchestrator becomes a thin "call A, call B, call C" sequence.
- **C** — Same as B, but each helper returns `SpoolmanOutcome<…>` directly (so the orchestrator doesn't need its own try/catch — it just `flatMap`s on outcomes via a small extension function).

**Recommendation**: **C**. Outcome-as-monad pattern keeps the orchestrator readable + lets each step be tested with the same fake `SpoolmanApi`. No new dependency; the `flatMap` extension lives in `SpoolmanOutcome.kt`.

[Answer]: C (recommended) — accepted via "full speed ahead" 2026-05-24.

### Q-U3-11 — `SettingsRepository` consumption shape

Repository needs the current URL. Two ways to wire it:

**Options**:
- **A** — Inject `SettingsRepository` and `.collect { url -> rebuildRetrofit(url) }` once in an init block (using the Hilt-managed `@Singleton CoroutineScope` from U1).
- **B** — Inject `Provider<SpoolmanApi>` and let `NetworkModule` rebuild Retrofit when URL changes.
- **C** — Inject a `SettingsRepository` reference and read `settings.value.url` lazily inside each call (no flow collection inside repository).

**Recommendation**: **A**. Matches the "repository owns its data sources" stance from `components.md`. Uses the existing `@Singleton CoroutineScope` already provisioned in U1's `RepositoryModule`. URL change triggers a single Retrofit rebuild and goes silent thereafter.

[Answer]: A (recommended) — accepted via "full speed ahead" 2026-05-24.

---

## 4. Decision Records (filled after user answers)

> Each Q-U3-* answer recorded here once approved, with timestamp and any short rationale.

| Question | Answer | Decided |
|---|---|---|
| Q-U3-1 | A — empty `CardUid` short-circuits to `Success(emptyList())` | 2026-05-24 |
| Q-U3-2 | A — patch-in-place using PATCH/POST response body; `refresh()` is the explicit re-sync path | 2026-05-24 |
| Q-U3-3 | C — repo collects `SettingsRepository.url`; rebuilds Retrofit only on URL change | 2026-05-24 |
| Q-U3-4 | A — inherit v1's 3 s connect / 5 s read OkHttp timeouts | 2026-05-24 |
| Q-U3-5 | B — `BASIC` logging on debug builds, off on release | 2026-05-24 |
| Q-U3-6 | A — big-bang delete `SpoolmanService.kt` (no production callers) | 2026-05-24 |
| Q-U3-7 | C — keep v1 `SpoolmanSpool/Filament/Vendor` for GETs; introduce request DTOs only for POST/PATCH | 2026-05-24 |
| Q-U3-8 | A — `connectivity` initial value `Unknown`; no startup probe | 2026-05-24 |
| Q-U3-9 | A — every method tested against fake `SpoolmanApi` (high coverage stance) | 2026-05-24 |
| Q-U3-10 | C — `internal` outcome-returning chain helpers + `SpoolmanOutcome.flatMap` extension | 2026-05-24 |
| Q-U3-11 | A — `SpoolmanRepository` collects URL flow once in init via the Hilt-managed `@Singleton CoroutineScope` | 2026-05-24 |

---

## 5. Approval Gate

**This plan is the Functional Design Part 1 deliverable for U3.** Once the user answers (or accepts recommendations on) the open questions above, U3 proceeds to Functional Design Part 2 — generation of:

- `aidlc-docs/construction/u3-spoolman-repository/functional-design/business-logic-model.md`
- `aidlc-docs/construction/u3-spoolman-repository/functional-design/business-rules.md`
- `aidlc-docs/construction/u3-spoolman-repository/functional-design/domain-entities.md`

After Functional Design Part 2 is approved, U3 enters **Code Generation Part 1** (separate plan file `u3-spoolman-repository-code-generation-plan.md`). NFR Requirements / NFR Design / Infrastructure Design **SKIP** per the U3 stage-gate decision.
