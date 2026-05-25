# Unit of Work — SpoolPainter v2

**Stage**: INCEPTION → Units Generation (Part 2: Generation)
**Source plan**: `aidlc-docs/inception/plans/unit-of-work-plan.md` (approved 2026-05-24)
**Companion artifacts**:
- `unit-of-work-dependency.md` — dependency matrix + ordering
- `unit-of-work-story-map.md` — story → unit mapping
- `unit-of-work-dependency-diagram.{mmd,png,svg}` — rendered graph
- `application-design.md` (and siblings) — components, methods, services
- `aidlc-docs/inception/user-stories/stories.md` — story IDs
- `aidlc-docs/inception/requirements/requirements.md` — FR / NFR IDs

---

## 1. Decomposition Strategy (decided in plan)

| Question | Answer | Implication |
|---|---|---|
| Q-SG1 | A — Layer-then-flow | Foundation units (DI, primitives, Spoolman, NFC) before flow units |
| Q-SG2 | B — Split U6 → U6a / U6b | Create-and-Pair separate from Move-on-bind + Two-tag |
| Q-SG3 | A — U10 own unit | Release polish ≠ functional work |
| Q-SG4 | B (constrained by Q-FU1=C) | v2.1 lightweight stubs only |
| Q-D1 | C — Hybrid | Interfaces at primary cross-unit boundaries; plain classes elsewhere |
| Q-D2 | A — Strict order | U1 → U2 → U3 → U4 → U5 → U6a → U6b → U7 → U8 → U9 → U10 |
| Q-D3 | A | Spoolman-touching helpers live in U3 |
| Q-T1 | B | DoD = code merged + unit tests passing |
| Q-T2 | B (per Q-FU2=A) | Milestone install gates at U1 / U5 / U6 / U10 (treating U6a OR U6b → "U6") |
| Q-T3 | B | Add ViewModel tests, especially for U5 / U6a / U6b / U7 |
| Q-BD1 | A | Units mirror application-design domains |
| Q-RW1 | A (revised by Q-FU1=C) | Hard gate — v2.1 cannot start until v2.0 ships to testing track |

**Project shape**: single `:app` Gradle module, brownfield, solo developer, two release waves (v2.0 first, v2.1 deferred behind a hard gate).

**Inter-unit communication (Q-D1=C — hybrid)**:
- **Repository ↔ ViewModel ↔ UseCase** boundaries are typed via interfaces / public Kotlin types declared by the producing unit. Consumers depend on the interface, not the implementation.
- Within a unit, plain classes / file-private helpers are fine.
- No service locator. No event bus. Hilt is the only inversion mechanism.

---

## 2. Definition of Done (per unit, applies to U1..U10 unless noted)

A unit is **Done** when all of the following hold:

1. Code merged to `v2` branch.
2. Unit tests for that unit's testable surface pass (NFR-4.1 minimum bar; Q-T1=B).
3. ViewModel tests pass where applicable (Q-T3=B — especially U5, U6a, U6b, U7).
4. Public interfaces declared by the unit are stable (later units consume them without modification — see `unit-of-work-dependency.md`).
5. Stories in scope (`Stories` column below) are AC-complete; `[unit]` AC bullets are covered by automated tests; `[manual]` AC bullets are noted in the unit's release notes for milestone manual verification at U1 / U5 / U6 / U10.

**Milestone install gates (Q-T2=B)**: at the end of U1, U5, U6 (covers U6a and U6b together), and U10, the developer installs the debug build on a physical device and exercises the unit's happy path. Ad-hoc Android Studio Run-on-device during dev is **not** a workflow gate (per Q-FU2=A).

**U10's gate is special**: it doubles as the **Play Store testing-track release validation** (per Q-FU1=C — hard gate before any v2.1 work).

---

## 3. v2.0 Units

### U1 — Architecture & DI Scaffold

**Domain**: Cross-cutting (foundation).

**Scope**:
- Hilt setup at `Application` + `MainActivity` (NFR-2).
- Per-layer Hilt modules: `NetworkModule`, `RepositoryModule`, `DataStoreModule`, `NfcModule` (Q-CD3=B).
- Per-screen / per-sheet HiltViewModel skeletons: `MainViewModel`, `SettingsViewModel`, `RepairConfirmViewModel`, `VendorOptInViewModel`, `AddCustomMaterialViewModel`, `AddCustomBrandViewModel` (state classes only — flow logic added in later units).
- `StateFlow<UiState>` + `Channel<UiEffect>` pattern wired (Q-DP2=C, Q-DP3=C).
- `DataStore<Settings>` wired through `DataStoreModule`; `SettingsRepository` exposes `settings: StateFlow<Settings>` (URL / sort / theme — read-only at this point).
- Sealed `NfcResult`, `NfcIntent` skeletons declared (placeholders before U4 fills them in).
- Layered package structure created under `app/src/main/java/com/spoolpainter/app/` per `.kiro/steering/structure.md`.

**Components produced** (from `components.md`):
- Hilt modules (all four).
- ViewModel skeletons (state shapes + empty methods, no flow orchestration yet).
- `SettingsRepository` (DataStore-backed; read surface only).
- `MainActivity` foreground-dispatch hooks scaffolded (the actual `nfcRepository.attach/detach` wiring lands in U4 once `NfcRepository` exists — U1 leaves a `TODO` comment).

**Stories in scope**: NFR-1, NFR-2, NFR-3 (settings), S-15.1.

**Public interfaces produced**:
- `SettingsRepository.settings: StateFlow<Settings>` (read surface).
- `MainViewModel.state: StateFlow<MainUiState>` (state shape stable; methods stubbed).
- Hilt module identifiers for downstream `@Inject` to resolve.

**Entry criteria**: Application Design approved (artifacts under `aidlc-docs/inception/application-design/`).

**Exit criteria**: App compiles; existing v1 screen still launches under the new DI graph (or compiles into a stub `MainScreen` that renders an empty state); unit tests for `SettingsRepository` reading defaults pass; **milestone install gate** — debug build runs on device.

**Tests (Q-T3=B)**:
- `SettingsRepository` reading default values (DataStore happy path).
- Hilt graph compiles.
- (No ViewModel tests yet — flow logic comes later.)

---

### U2 — Domain Primitives

**Domain**: Cross-cutting (foundation).

**Scope**:
- `CardUid` value type — canonicalisation `fromBytes(ByteArray) → lowercase hex no separators` (FR-1.2 / S-1.2).
- `CardUidEncoding` — encode / decode rules for `card_uid:<hex>,card_uid:<hex>,opaque-tail` strings stored in Spoolman `lot_nr` (FR-2.1, FR-2.2 / S-2.1, S-2.2).
- `OpenSpoolPayload` cleanup (rename of v1 `OpenSpoolData`; field set unchanged — see `services.md`).
- `TagClassification` sealed type fleshed out: `Blank | OpenSpool(payload) | Vendor(reason)` (FR-4.7 driver / S-4.6).

**Components produced**:
- `domain/primitives/CardUid.kt`.
- `data/remote/spoolman/CardUidEncoding.kt` (sits in remote-spoolman package per `components.md` §2.6).
- `domain/primitives/TagClassification.kt`.
- Updated `domain/models/OpenSpoolPayload.kt`.

**Stories in scope**: S-1.2, S-2.1, S-2.2.

**Public interfaces produced**:
- `CardUid` value type — used by U3 (Spoolman lookup), U4 (NFC read result), U5..U7 (flows).
- `CardUidEncoding.Decoded` + `decode/encode` — used by U3 + U6b move-on-bind.
- `TagClassification` — used by U4 (classifier), U5..U7 (branching).

**Entry criteria**: U1 complete.

**Exit criteria**: Pure unit tests pass for `CardUid.fromBytes`, `CardUidEncoding.encode/decode` round-trip, idempotency, edge cases (empty input, whitespace, mixed case, opaque tail preservation).

**Tests (Q-T3=B / NFR-4.1 minimum)**:
- `CardUid.fromBytes` lowercase + no-separator output.
- `canonicalise` round-trip preserves bytes.
- `CardUidEncoding.decode` parses canonical, mixed-case, whitespace, empty, tail-only inputs.
- `CardUidEncoding.encode` round-trip with `decode` is idempotent.
- Unrecognised entries preserved verbatim on decode/encode round-trip.

---

### U3 — Spoolman Client Overhaul

**Domain**: Spoolman.

**Scope**:
- `SpoolmanApi` (Retrofit) extended for v2: lot_nr-filtered `GET /api/v1/spool` (FR-3.2 / S-3.1), vendor / filament / spool POST chain (FR-7 / S-7.1 / S-7.2 / S-7.3), `PATCH lot_nr` (FR-4.6 / S-4.5).
- `SpoolmanRepository` (`@Singleton`) per `components.md` §2.4 + §3:
  - `findSpoolsByCardUid(uid: CardUid): SpoolmanOutcome<List<Spool>>` (FR-3 / S-3.1).
  - `createSpoolForNewFilament(req): SpoolmanOutcome<Spool>` orchestrating the FR-7 chain (Q-S2=A — repository owns the orchestration; use-case stays a thin caller).
  - `appendCardUidToSpool(spoolId, uid)` (FR-4.6, FR-6.2 / S-4.5, S-6.2).
  - `removeCardUidFromSpool(spoolId, uid)` (FR-5.2 / S-5.2 — used by U6b).
  - `probe(): SpoolmanOutcome<Unit>` (Settings → Test connection / S-9.1).
  - `refresh(): SpoolmanOutcome<Unit>` (cache repopulation).
- In-memory caches `filaments: StateFlow<List<Filament>>`, `spools: StateFlow<List<Spool>>` (Q-S4=A).
- `connectivity: StateFlow<ConnectivityState>` (Q-CD1=A).
- Sealed `SpoolmanOutcome<T>` return type (Q-CM2=B).
- All Spoolman-touching helpers live here per Q-D3=A — including any `findSpoolsByCardUid` derivative used by both U5 and U6a/U6b.

**Components produced**:
- `data/remote/spoolman/SpoolmanApi.kt` (extended).
- `data/remote/spoolman/SpoolmanRepository.kt`.
- `data/remote/spoolman/SpoolmanOutcome.kt`.
- `data/remote/spoolman/ConnectivityState.kt`.
- Wire models extended in `SpoolmanModels.kt`.

**Stories in scope**: S-3.1, S-7.1, S-7.2, S-7.3, S-4.5 (PATCH semantics — orchestration callers in U6a/U6b), S-10.2 (connectivity reporting; banner UI in U9), NFR-7.

**Public interfaces produced** (consumed by all flow units):
- `SpoolmanRepository` (interface + Hilt-bound impl) — primary cross-unit boundary per Q-D1=C.
- `SpoolmanOutcome<T>` — error contract for every flow unit.
- `ConnectivityState` — read by U9 (banner) and SettingsScreen.

**Entry criteria**: U2 complete (needs `CardUid`, `CardUidEncoding`).

**Exit criteria**: Repository tests pass against a fake `SpoolmanApi`; `SpoolmanOutcome` paths covered for HTTP / Network / Parse errors.

**Tests**:
- `findSpoolsByCardUid` — single match, multiple matches, no match, HTTP/network error.
- `createSpoolForNewFilament` — vendor lookup-or-create, filament lookup-or-create, spool POST with `lot_nr=card_uid:<uid>`, HTTP error on each step short-circuits chain (no partial commit beyond what already POSTed — message identifies the partial state).
- `appendCardUidToSpool` / `removeCardUidFromSpool` — PATCH body computed via `CardUidEncoding`; idempotency.
- `probe()` — happy path + unreachable path.
- `connectivity` transitions: Unknown → Reachable / Unreachable on `probe()`.

---

### U4 — NFC Repository + State

**Domain**: NFC.

**Scope**:
- `NfcAdapterWrapper` — thin wrapper around `android.nfc.NfcAdapter` (lives in `hardware/nfc`).
- `NfcRepository` (`@Singleton`) per `components.md` §2.4 + §3:
  - `state: StateFlow<NfcResult>` (NFR-1.4).
  - `lastSeenTag: StateFlow<TagBuffer?>` — TTL-cleared (Q-CM1=D).
  - `attach(activity)` / `detach()` foreground-dispatch lifecycle (Q-CD4=A).
  - `arm(NfcIntent)` / `consumeLastSeen(NfcIntent)` / `disarm()`.
- UID extraction (FR-1.1 / S-1.1) producing `CardUid` via `CardUid.fromBytes`.
- Tag classification — produces `TagClassification.Blank` / `OpenSpool(payload)` / `Vendor(reason)`.
- Write-then-verify (FR-4.5 / S-4.4) implemented inside `arm(Write(payload, expectedUid?))`: write → re-read → byte-compare → emit `Success` or `Error`. NFR-6.
- `MainActivity.onResume / onPause` wires `attach / detach` (the U1 `TODO` is closed here).

**Components produced**:
- `hardware/nfc/NfcAdapterWrapper.kt`.
- `hardware/nfc/NfcRepository.kt`.
- `domain/primitives/NfcResult.kt`, `NfcIntent.kt` finalised (skeleton from U1, fields stable now).
- `MainActivity` lifecycle hooks completed.

**Stories in scope**: S-1.1, S-4.4 (write-then-verify), AC bullets in S-4.6 / S-6.2 that depend on classification (full flow logic lives in U6a/U6b/U7), NFR-1.4, NFR-6.

**Public interfaces produced** (consumed by every flow unit):
- `NfcRepository` (the public surface of the NFC domain — primary cross-unit boundary per Q-D1=C).
- `NfcResult`, `NfcIntent`, `TagClassification` (final).

**Entry criteria**: U2 complete (needs `CardUid`, `TagClassification`).

**Exit criteria**: Manual NFC verification by tap-on-device — UID extraction + classification (blank, OpenSpool, vendor) — captured as a manual checklist (run at the U5 milestone install gate). Unit tests for `NfcRepository.state` transitions against a fake adapter pass.

**Tests**:
- State machine transitions: `Idle → Reading → Success | Error`, `Idle → Writing → Verifying → Success | Error`.
- TTL behaviour on `lastSeenTag`.
- Write-then-verify mismatch yields `Error` and does not advance to `Success`.
- (Instrumented NFC tests are out of scope per Q-T3=B; classification + write-verify on real hardware is verified manually at the U5 milestone install gate.)

---

### U5 — Read-and-Pair Flow

**Domain**: Flow (user-facing).

**Scope**:
- `ReadAndPairUseCase` orchestrating `NfcRepository.arm(Read)` → `consumeLastSeen` → `SpoolmanRepository.findSpoolsByCardUid(uid)` → branch:
  - 0 matches + OpenSpool payload → form prefill from payload (FR-3.4 / S-3.4).
  - 0 matches + blank/vendor/unparseable → empty form (S-3.5).
  - 1 match → form prefill from spool's filament (S-3.2).
  - >1 matches → ambiguity error (S-3.3).
- `MainViewModel.onReadTapped()` invokes the use-case; emits `MainUiState` updates and `Channel<UiEffect>` for transient errors.
- `SpoolmanDropdown` selection prefill — when user picks a spool from the dropdown, `MainViewModel.onSpoolSelected` populates form from filament (FR-3.6 / S-3.6).
- `OfflineBanner` rendered passively (S-10.2 banner; full banner-state derivation is finalised in U9 alongside Settings, but the read path surfaces network errors via the same `Channel<UiEffect>`).

**Components produced**:
- `domain/usecases/ReadAndPairUseCase.kt`.
- `MainViewModel` — read-flow methods + state branches (state shape from U1; logic fills here).
- `MainScreen` reads — Compose composables for showing read result.
- `SpoolmanDropdown` Compose component implementation (skeleton in U1).

**Stories in scope**: S-3.1, S-3.2, S-3.3, S-3.4, S-3.5, S-3.6, S-10.2 (banner only — surfaced; settings + retry control land in U9).

**Public interfaces produced**:
- `ReadAndPairUseCase` (interface) — called only from `MainViewModel`; documented as primary cross-unit boundary even though it has only one consumer (per Q-D1=C, UseCase boundaries are interface-typed).

**Entry criteria**: U3 + U4 complete.

**Exit criteria**: ViewModel tests cover the four read-result branches; **milestone install gate** — read works on device end-to-end (UID display, OpenSpool prefill, dropdown match prefill, ambiguity error).

**Tests (Q-T3=B — ViewModel tests beyond NFR-4.1 minimum)**:
- `ReadAndPairUseCase` against fake `NfcRepository` + fake `SpoolmanRepository`: each of the four branches.
- `MainViewModel.onReadTapped` produces expected `MainUiState` for each branch (single-match, multi-match ambiguity, OpenSpool prefill, empty/blank).
- `MainViewModel.onSpoolSelected` prefills form from selected spool; reselection overwrites; clearing empties.

---

### U6a — Create-and-Pair Flow

**Domain**: Flow (user-facing).

**Scope**:
- `CreateAndPairUseCase` — Spoolman-first sequencing (FR-4.3 / S-4.2):
  - Move-on-bind precheck (FR-5 / S-5.1) — calls `findSpoolsByCardUid(uid)`; if a different spool A already owns the UID, route through U6b's `MoveOnBindUseCase` for confirmation (cross-unit dependency declared as a `MoveOnBindUseCase` interface — but U6a depends on U6b via interface only; U6a ships first if U6b is just an interface stub at this point — see ordering note below).
  - Existing-spool path: spool id = selected spool's id → `NfcRepository.arm(Write(payload(spoolId)))` → verify → `appendCardUidToSpool(spoolId, uid)` (S-4.5 PATCH).
  - New-spool path: `SpoolmanRepository.createSpoolForNewFilament(req with lot_nr=card_uid:<uid>)` (FR-7 chain / S-7.x) → `arm(Write(payload(newSpoolId)))` → verify.
  - On verify failure (S-4.4): error surfaced; new-spool path's just-created Spoolman record stays — retry uses existing-spool path automatically because UID lookup now finds it.
- `MainViewModel.onWriteTapped()` invokes the use-case based on `canWrite` (FR-4.1 / S-4.1).

**Ordering note re U6b dependency**: Q-D2=A locks U6a → U6b. U6a's move-on-bind precheck depends on U6b's `MoveOnBindUseCase`. To honour the strict order, U6a ships with the precheck **wired through a `MoveOnBindUseCase` interface declared in the unit boundary file**. U6b lands the implementation. Until U6b ships, U6a uses a no-op default that simply proceeds without the move-on-bind branch — acceptable at the U6a milestone because the U6 install gate (per Q-T2=B) covers U6a + U6b together.

**Components produced**:
- `domain/usecases/CreateAndPairUseCase.kt`.
- `domain/usecases/MoveOnBindUseCase.kt` — **interface only** (impl in U6b).
- `MainViewModel.onWriteTapped` flow logic.
- `MainScreen` write-flow composables.
- `FilamentForm`, `MaterialPicker`, `BrandPicker`, `ColorPicker`, `TempPanel` Compose components — full implementation (skeletons created in U1; used here for the first time).

**Stories in scope**: S-4.1, S-4.2, S-4.3, S-4.4, S-4.5, S-7.1, S-7.2, S-7.3.

**Public interfaces produced**:
- `CreateAndPairUseCase` (interface).
- `MoveOnBindUseCase` (interface; impl in U6b).

**Entry criteria**: U5 complete.

**Exit criteria**: ViewModel tests for both existing-spool and new-spool paths pass; verify-fail behaviour tested. Manual write verification deferred to U6 milestone install gate (after U6b).

**Tests (Q-T3=B)**:
- `CreateAndPairUseCase` happy paths (existing-spool, new-spool) against fake repos.
- Verify-fail path: new-spool record remains in Spoolman; retry routes through existing-spool path (state simulation).
- `MainViewModel.onWriteTapped` `canWrite` gating.

---

### U6b — Move-on-Bind + Two-Tag Flow

**Domain**: Flow (user-facing).

**Scope**:
- `MoveOnBindUseCase` — implementation. Detect existing UID owner (S-5.1) → `RepairConfirmSheet` confirmation → atomic `removeCardUidFromSpool(A, uid)` followed by `appendCardUidToSpool(B, uid)` (S-5.2). Partial-commit handling (FR-5.2 / S-5.2) — error message identifies which spool was partially modified.
- `RepairConfirmViewModel` — sheet state + result event flowing back to `MainViewModel.onRepairResult(...)`.
- `TwoTagUseCase` — second-tag pair flow (FR-6 / S-6.x):
  - Offers prompt after first successful pair (S-6.1).
  - Re-derives payload from spool filament (S-6.4).
  - Writes byte-identical OpenSpool payload to second tag (S-6.2).
  - Runs move-on-bind for second UID (S-6.3) — reuses `MoveOnBindUseCase`.
  - Vendor-tag protection still enforced for second tag (S-6.2 AC).
- `PairAnotherTagSheet` — bottom sheet for the FR-6.1 prompt.
- `MainViewModel.onPairAnotherTag()` / `onTwoTagResult(...)`.

**Components produced**:
- `domain/usecases/MoveOnBindUseCase.kt` — impl.
- `domain/usecases/TwoTagUseCase.kt`.
- `RepairConfirmSheet` + `RepairConfirmViewModel`.
- `PairAnotherTagSheet`.
- `MainViewModel` two-tag-flow methods.

**Stories in scope**: S-5.1, S-5.2, S-6.1, S-6.2, S-6.3, S-6.4.

**Public interfaces produced**:
- `MoveOnBindUseCase` impl backing the U6a interface.
- `TwoTagUseCase` (interface).

**Entry criteria**: U6a complete.

**Exit criteria**: Move-on-bind ViewModel tests pass (single-UID move; multi-UID source spool retains other UIDs); two-tag ViewModel tests pass (second-tag write-verify; second-tag move-on-bind). **U6 milestone install gate**: end-to-end create-and-pair, move-on-bind, two-tag flows verified on device.

**Tests (Q-T3=B)**:
- `MoveOnBindUseCase` — happy path (move from A to B); partial-commit failure between `removeCardUidFromSpool` and `appendCardUidToSpool` surfaces correct error message; cancel produces no PATCHes.
- Multi-UID source spool — only the matched UID is removed; other UIDs preserved.
- `TwoTagUseCase` — second-tag identical payload; second-tag move-on-bind; vendor-tag protection on second tag.
- `RepairConfirmViewModel` — confirm/cancel result propagation.

---

### U7 — Side Modes (Raw-Write + Vendor UID-Only Pair)

**Domain**: Flow (user-facing) — opt-in side modes.

**Scope**:
- `RawWriteUseCase` (S-4.7) — Spoolman-free OpenSpool payload write to blank/OpenSpool tag. Skips FR-4.3 sequencing and FR-4.6 PATCH/POST. Vendor-tag protection (FR-4.7) still enforced. Write-then-verify still enforced. Payload omits `spool_id`.
- `VendorUidOnlyPairUseCase` (S-4.8 / FR-4.9) — UID-only pair for vendor/foreign tags:
  - At Read time, vendor classification surfaces empty form (same UI state as S-3.5).
  - At Save/Write time, `VendorUidOnlyOptInSheet` modal asks "Pair UID only?".
  - On confirm: existing-spool → PATCH per S-4.5 semantics; new-spool details → FR-7 chain; **no NDEF write** in either case.
  - Move-on-bind (S-5.1/S-5.2) still applies, surfaced **after** the opt-in confirmation.
- `VendorOptInViewModel` — sheet state + result event flowing back to `MainViewModel`.
- NDEF-write boundary enforcement (FR-4.7 / S-4.6) — already gated in `NfcRepository`'s classifier in U4; U7 is where the write-side flow consumers respect it (rejection path on vendor classification when `arm(Write(...))` is attempted outside the UID-only flow).

**Components produced**:
- `domain/usecases/RawWriteUseCase.kt`.
- `domain/usecases/VendorUidOnlyPairUseCase.kt`.
- `VendorUidOnlyOptInSheet` + `VendorOptInViewModel`.
- `MainViewModel` raw-write + vendor-opt-in entry points.

**Stories in scope**: S-4.6 (write-side enforcement), S-4.7, S-4.8.

**Public interfaces produced**:
- `RawWriteUseCase`, `VendorUidOnlyPairUseCase` (interfaces).

**Entry criteria**: U6b complete (reuses `MoveOnBindUseCase`).

**Exit criteria**: ViewModel tests for both side modes pass; manual NFC verification of vendor UID-only pair captured for the U10 milestone gate (because U7 sits between U6 and U10 milestone gates, and Q-T2=B does not require a separate U7 install gate).

**Tests (Q-T3=B)**:
- `RawWriteUseCase` — Spoolman calls not invoked; vendor-tag protection enforced; payload `spool_id` omitted; verify enforced.
- `VendorUidOnlyPairUseCase` — existing-spool PATCH path; new-spool FR-7 chain; cancel produces no PATCH/POST; NDEF write never invoked; move-on-bind precondition still applies.
- `VendorOptInViewModel` — confirm/cancel propagation.

---

### U8 — Pickers + Custom Entries

**Domain**: Pickers / Local-data.

**Scope**:
- `MaterialPresetSource` (hardcoded; FR-8.1 / S-8.1).
- `BrandPresetSource` (hardcoded; FR-8.2 / S-8.1).
- `MaterialBrandLocalStore` — DataStore-Proto-backed user-added entries (Q-CD2=A; FR-8.5 / S-8.3, S-8.4). Schema: `CustomMaterials { repeated CustomMaterial }` + `CustomBrands { repeated CustomBrand }`.
- `MaterialBrandRepository` — merges presets + Spoolman vendors + user-added (case-insensitive dedup; Spoolman entries take precedence; FR-8.3, FR-8.5).
- `AddCustomMaterialSheet` + `AddCustomMaterialViewModel`.
- `AddCustomBrandSheet` + `AddCustomBrandViewModel`.
- `MaterialPicker` / `BrandPicker` Compose components fully wired (skeletons existed in U6a; this unit hardens behaviour with the merged source).

**Components produced**:
- `data/local/presets/MaterialPresetSource.kt`, `BrandPresetSource.kt`.
- `data/local/userdata/MaterialBrandLocalStore.kt` (Proto schema + DataStore wiring; Hilt module from U1 already declares `DataStore<CustomMaterials>` + `DataStore<CustomBrands>` — provider implementations land here).
- `data/local/MaterialBrandRepository.kt`.
- Two sheets + their VMs.

**Stories in scope**: S-8.1, S-8.2, S-8.3, S-8.4.

**Public interfaces produced**:
- `MaterialBrandRepository` — consumed by `MainViewModel` (already wired in U6a; U8 lands the real impl behind the same interface).

**Entry criteria**: U6a complete (pickers integrated into form there).

**Exit criteria**: Repository tests pass for merge + dedup; sheet ViewModel tests pass for add custom flow; round-trip test (custom entry persists across DataStore restart).

**Tests**:
- `MaterialBrandRepository.materials` — merge, case-insensitive dedup, Spoolman precedence.
- Custom material persists across DataStore restart (in-memory test DataStore).
- Custom material flows into `createSpoolForNewFilament` request when user creates a spool with that material (integration with U6a, mocked).

---

### U9 — Settings + Theming + UI Shell

**Domain**: Settings + UI shell.

**Scope**:
- `SettingsScreen` Compose — URL field, Test connection button, sort order, theme override (S-9.1, S-9.2, S-9.3).
- `SettingsViewModel` — Test-connection action delegates to `SpoolmanRepository.probe()`. Settings owns the **only** Test-connection action (Q-CD1.1=A — no banner-resident retry; banner is passive).
- `OfflineBanner` finalised — `state.banner` derivation (URL configured AND `connectivity == Unreachable` → show; URL not configured → hide entirely; URL configured AND reachable → hide). Read-only banner; no action.
- Material 3 theming — dynamic color on Android 12+ (S-12.1); system-follow + Settings override (FR-12.2).
- UI shell (S-13.1, S-13.2) — confirm `MainScreen` two-action layout; confirm bottom-sheet hosting pattern (sheets already implemented in U6b/U7; this unit verifies the shell shape).

**Components produced**:
- `ui/screens/settings/SettingsScreen.kt`.
- `SettingsViewModel` — full impl (skeleton from U1).
- `OfflineBanner.kt` — full impl.
- `ui/theme/Theme.kt` — Material 3 + dynamic color + override.

**Stories in scope**: S-9.1, S-9.2, S-9.3, S-12.1, S-13.1, S-13.2.

**Public interfaces produced**:
- (None new — `SettingsRepository` interface already exists from U1.)

**Entry criteria**: U8 complete.

**Exit criteria**: Settings ViewModel tests pass for URL save → connectivity check → state transition; banner derivation tests pass; theming verified manually at U10 milestone install gate.

**Tests (Q-T3=B)**:
- `SettingsViewModel` — URL save invokes `probe()`; valid URL persists; failure surfaces error.
- `OfflineBanner` derivation against `(connectivity, settings.url)` matrix.
- `SettingsRepository` round-trips for sort order + theme override.

---

### U10 — v2.0 Release Polish + Play Store Testing-Track Release Prep

**Domain**: Release.

**Scope**:
- NFR-5 — release-build log stripping (verify ProGuard / R8 strips Timber/Logcat tags from release variant; or remove logging at release-build time).
- Manual-NFC verification checklist — full table of NFC happy paths and edge cases captured in `aidlc-docs/operations/manual-nfc-checklist.md` (created at this stage; Operations-phase placeholder).
- **Play Store testing-track release prep** (per Q-FU1=C / project-playstore-testing memory):
  - `versionCode 100`, `versionName 2.0` (memory: v2.0 starts at 100 to leave room for v1.x patches).
  - Signed APK + AAB built from release variant.
  - Tester release notes drafted.
  - Upload to Play Store testing track (closed/internal/open — TBD by user; this unit produces the artifacts and the upload checklist; the actual upload is gated on user confirmation).
  - **Explicitly NO production-track promotion** in this unit — that's outside AIDLC.
- U10 milestone install gate — debug build verified on device, manual-NFC checklist run end-to-end, signed release build sideloaded and smoke-tested before testing-track upload.

**Components produced**:
- `aidlc-docs/operations/manual-nfc-checklist.md`.
- `aidlc-docs/operations/v2.0-tester-release-notes.md`.
- Build-config updates (versionCode, versionName).
- Signed APK + AAB (build artefacts; not committed).

**Stories in scope**: NFR-5; NFR-9 (testing-track distribution).

**Public interfaces produced**: none (release unit).

**Entry criteria**: U9 complete.

**Exit criteria**:
- `versionCode 100`, `versionName 2.0` set.
- Release variant builds clean (lint passes; ProGuard rules verified).
- Signed APK + AAB produced from local keystore (`~/spoolpainter-release-key.jks`).
- Manual-NFC checklist run on device.
- Upload package + tester release notes ready for testing-track upload.
- **U10 milestone install gate passes — and doubles as testing-track release validation per Q-FU1=C**.

**Tests**: no new automated tests (release unit). All v2.0 unit tests + ViewModel tests must still pass.

---

## 4. v2.1 Units (lightweight stubs — Q-SG4=B + Q-FU1=C)

> **Hard gate**: U11 and U12 cannot start construction until v2.0 ships to the Play Store testing track. Below is scope + source stories only — no full decomposition. A separate Units Generation top-up will run after v2.0 ships.

### U11 — Vendor Decode Engine + GPL-3.0 Transition (v2.1, lightweight stub)

**Domain**: NFC vendor decode + Licensing.

**Scope (lightweight)**:
- Port OpenRFID parsers (Bambu / Creality / Anycubic / Elegoo / Qidi / Snapmaker / OpenSpool / TigerTag) to Kotlin.
- Introduce `VendorTagDecoder` interface (the v2.0 plugin point in `components.md` §2.8 is refined from `Vendor(reason)` into `Vendor(decoded: DecodedVendorPayload?)`).
- Bake vendor data tables as static assets (NFR-12).
- Re-license project to GPL-3.0 (NFR-11) — atomic with first U11 release.

**Stories in scope**: S-1.4, S-3.5 (vendor decode branch — refines U5's existing fall-through), NFR-11, NFR-12, S-NFR11, S-NFR12.

**Decomposition**: deferred to post-v2.0-ship Units Generation top-up.

### U12 — Vendor Key Settings + Encrypted Storage (v2.1, lightweight stub)

**Domain**: Settings (v2.1 extension).

**Scope (lightweight)**:
- Per-vendor key list in Settings — add / edit / delete.
- Keystore-backed encryption (`EncryptedSharedPreferences` or Tink-wrapped DataStore — NFR-3.4).
- UID-only fall-through when keys missing (extends U7's vendor UID-only pair flow naturally — no app-level prompts to "go get keys").

**Stories in scope**: S-9.4.1, S-9.4.2, NFR-3.4.

**Decomposition**: deferred to post-v2.0-ship Units Generation top-up.

---

## 5. Validation

**Story coverage** (full mapping in `unit-of-work-story-map.md`):
- **v2.0 stories**: 32 / 32 assigned to exactly one unit.
- **v2.1 stories**: 5 / 5 parked under U11 / U12 stubs (no decomposition).

**Component coverage** (cross-checked against `components.md` §2):
- UI / Compose: U1 (skeletons), U5 (read), U6a (write form + pickers wiring), U6b (sheets), U7 (vendor-opt-in sheet), U9 (Settings + banner + theme).
- ViewModels: U1 (skeletons), U5 (Main read), U6a (Main write), U6b (Main two-tag + Repair sheet VM), U7 (Vendor opt-in sheet VM), U8 (Add-custom sheet VMs), U9 (Settings VM).
- Use-cases: U5 (Read-and-Pair), U6a (Create-and-Pair, MoveOnBind interface), U6b (MoveOnBind impl, Two-tag), U7 (Raw-write, Vendor UID-only pair).
- Repositories: U1 (Settings — read), U3 (Spoolman), U4 (Nfc), U8 (MaterialBrand).
- Domain primitives: U2 (CardUid, CardUidEncoding, TagClassification, OpenSpoolPayload cleanup), U4 (NfcResult / NfcIntent finalised).
- Data sources: U3 (SpoolmanApi extensions), U4 (NfcAdapterWrapper), U8 (presets + local userdata).
- Hilt modules: U1 (all four).

**No orphan dependencies** — every unit's `Public interfaces produced` row is consumed by at least one downstream unit (see `unit-of-work-dependency.md`).

**Forbidden patterns reminder** (from `component-dependency.md`):
- UI never calls data sources directly (NFR-1.2).
- ViewModels never inject Hilt-scoped Activity types.
- Use-cases hold no state.
- No service locator; no event bus.
