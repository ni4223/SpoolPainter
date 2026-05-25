# U5 — Functional Design Plan

**Stage**: CONSTRUCTION → Per-Unit Loop → Functional Design (U5)
**Unit**: U5 — Read-and-Pair Flow
**Source artefacts**:
- `aidlc-docs/inception/application-design/unit-of-work.md` §3-U5
- `aidlc-docs/inception/application-design/components.md` §2.2 (`ReadAndPairUseCase`), §2.5 (`MainViewModel`), §2.8 (`MainScreen`), §3 (cross-component contracts)
- `aidlc-docs/inception/application-design/component-methods.md` §5 (`ReadAndPairUseCase`), §6 (`MainViewModel`), §7 (`MainUiState` shape)
- `aidlc-docs/inception/application-design/services.md` §2 (Read-and-Pair flow), §7 (banner suppression rule Q-CD1.1=A)
- `aidlc-docs/inception/requirements/requirements.md` FR-3.1 / FR-3.2 / FR-3.3 / FR-3.4 / FR-3.6 / FR-10.2 / FR-11.1 / FR-11.2 / NFR-1.1 / NFR-1.2 / NFR-1.4 / NFR-7.1 / NFR-7.2
- `aidlc-docs/inception/user-stories/stories.md` S-3.1 / S-3.2 / S-3.3 / S-3.4 / S-3.5 / S-3.6 / S-10.2 (banner only — surfaced via `Channel<UiEffect>` in U5; full banner state lands in U9)

---

## 1. Unit Context (Step 1)

### 1.1 Scope (locked by Units Generation, `unit-of-work.md` §3-U5)

- **`ReadAndPairUseCase`** (`@Inject` constructor; lives in `domain/usecases/`):
  - `suspend operator fun invoke(): ReadAndPairResult` — orchestrates the read flow.
  - Internal sequence (services.md §2):
    1. Try **tag-first**: `nfc.consumeLastSeen(NfcIntent.Read)`. If a fresh buffered tap exists (within TTL), use it directly.
    2. Otherwise **button-first**: `nfc.arm(NfcIntent.Read)`; collect from `nfc.state` until a terminal `Success(uid, classification)` or `Error(reason, cause?)` arrives.
    3. Branch on classification + Spoolman lookup result.
  - Returns sealed `ReadAndPairResult` — see §2.1 below for final shape.
- **`MainViewModel.onReadTapped()`** — invokes `readAndPair.invoke()` on `viewModelScope`, maps the result into `MainUiState` updates and `Channel<UiEffect>` events.
- **`MainViewModel.onSpoolSelected(spool: SpoolmanSpool?)`** — implements FR-3.6 dropdown-driven prefill. `null` ⇒ clear selection + reset form.
- **`MainScreen` read composables** — wire the existing placeholder Compose surface to `state.collectAsState`. Show:
  - "Tap a tag to read" hint when `activeFlow == ReadingForPair` and `nfc == Reading`.
  - UID display once `nfc == Success(uid, classification)` (S-1.1 surfacing reused).
  - Form prefill from `FormState`.
  - Ambiguity error UI (S-3.3) when result is `Ambiguous(matches)` — list spool names/ids; no auto-pair.
- **`SpoolmanDropdown`** — concrete Compose component (skeleton in U1 was empty). Bound to `state.spoolman.spools` from `SpoolmanRepository.spools` projection. Selection callback wires `onSpoolSelected`.
- **`MainUiState` shape** — finalise the slices that U5 owns: `FormState`, `SpoolmanState` (read-only projection), `NfcState` (mirror of `NfcRepository.state`), `ActiveFlow.Idle | ReadingForPair`. `BannerState` is wired through but full derivation is deferred to U9 — U5 surfaces network errors via `UiEffect.ShowSnackbar` (per S-10.2 / FR-10.2).
- **Form prefill rule contract** — three sources, one mapping (FR-3.6 / S-3.6):
  - `SpoolmanSpool` → `FormState`: reuses the existing `FilamentSpool.fromSpoolman` mapping logic, projected onto `FormState`.
  - `OpenSpoolPayload` → `FormState`: parses string min/max temps to `Int?`; falls back to material defaults when parse fails.
  - "Blank form" = empty `FormState` with UID context preserved.

### 1.2 Cross-unit consumers (locked by `unit-of-work-dependency.md`)

- **U6a (Create-and-Pair)** consumes `MainUiState.form`, `MainUiState.spoolman.selectedSpoolId`, and the `onSpoolSelected` flow contract (so U6a's Write button can branch on `selectedSpoolId != null`).
- **U6b (Move-on-bind + Two-tag)** consumes `MainUiState.activeFlow` shape extension points (U5 ships `Idle | ReadingForPair`; U6b adds `Repairing | TwoTag`).
- **U7 (Raw write + Vendor UID-only)** consumes `MainUiState.form.rawWriteMode` (default `false` from U5) and the `Idle | ReadingForPair` baseline.
- **U8 (Material/Brand catalogue)** wires `MaterialBrandRepository` into `MainViewModel` and replaces U5's string-based `Material`/`Brand` resolution with the catalogue. U5 ships a **temporary string-based resolver** that reads from the existing `MaterialDatabase` and `BrandDatabase` so the form can render before U8 — see Q-U5-9.
- **U9 (Settings + banner)** consumes `MainUiState.banner` (placeholder slot in U5) and finalises `BannerState` derivation from `SpoolmanRepository.connectivity` + URL-configured rule.

### 1.3 Out-of-scope for U5 (deferred)

- `MainViewModel.onWriteTapped` flow logic (U6a).
- `MoveOnBindUseCase` / `TwoTagUseCase` integration (U6b).
- `RawWriteUseCase` / `VendorUidOnlyPairUseCase` integration (U7).
- `MaterialBrandRepository` (U8) — U5 uses the existing v1 `MaterialDatabase` / `BrandDatabase` for form rendering. The U8 swap is a non-breaking VM injection change.
- Full `BannerState` derivation (U9) — U5 surfaces network errors via `UiEffect.ShowSnackbar`.
- Settings screen + URL editor (U9).
- Instrumented Android tests against real hardware — manual verification at the **U5 milestone install gate**.

---

## 2. Plan Steps (checkboxes)

### 2.1 Use-case result type & state machine

- [x] 2.1.1 Lock `ReadAndPairResult` sealed-type shape — single hierarchy, named non-happy-path variants per Q7=B in stories:
  - `Success.PrefillFromSpoolman(uid: CardUid, spool: SpoolmanSpool, classification: TagClassification)` — 1 Spoolman match (winning over OpenSpool payload per services.md §2 ordering).
  - `Success.PrefillFromTag(uid: CardUid, payload: OpenSpoolPayload)` — 0 Spoolman matches + classification == `OpenSpool`.
  - `Success.BlankForm(uid: CardUid, classification: TagClassification)` — 0 Spoolman matches + classification ∈ {`Blank`, `Vendor`}.
  - `Ambiguous(uid: CardUid, matches: List<SpoolmanSpool>, classification: TagClassification)` — >1 Spoolman matches.
  - `SpoolmanFailed(uid: CardUid, classification: TagClassification, outcome: SpoolmanOutcome<*>)` — Spoolman call returned `HttpError` / `NetworkError` / `ParseError` (other than the URL-not-configured short-circuit, which is Q-U5-3).
  - `NfcFailed(reason: String)` — `NfcRepository` produced `NfcResult.Error` before any Spoolman call.
  - `Cancelled(reason: String)` — explicit `disarm()` / VM cancellation.
- [x] 2.1.2 Lock the use-case state-machine entry contract: from caller's perspective, `invoke()` is one-shot; it does not own a long-lived state field. `MainViewModel` owns the `ActiveFlow.ReadingForPair` UI state for the duration of the call.
- [x] 2.1.3 Lock the **tag-first vs button-first** decision rule (services.md §2): always try `consumeLastSeen(Read)` first; if it returns non-null, skip the `arm(Read)` step. If it returns null, `arm(Read)` and collect `nfc.state` until terminal outcome.
- [x] 2.1.4 Lock the `arm(Read)` collection contract: subscribe to `nfc.state` after `arm`; await first state ∈ {`Success(...)`, `Error(...)`} after the `Reading` transition; map to result. (Idle / Reading / non-terminal states are skipped.)
- [x] 2.1.5 Lock the **read-then-Spoolman-lookup** contract: even when classification is `Vendor` or `Blank`, the use-case still calls `findSpoolsByCardUid(uid)` because vendor tags can be paired by UID-only (FR-4.9 — relevant for U7) and blank tags may have prior pairings if the user re-reads after a remove. Single source of truth = the UID lookup result.
- [x] 2.1.6 Lock the **OpenSpool-classification × 1-Spoolman-match collision rule**: if classification == `OpenSpool(payload)` AND Spoolman returns exactly 1 match, the **Spoolman match wins** (`PrefillFromSpoolman`). Rationale: Spoolman is the source of truth for paired spools (FR-3.3 single-match branch is unconditional). The OpenSpool payload is informational only in this case.
- [x] 2.1.7 Lock cancellation: re-tapping Read while `ActiveFlow == ReadingForPair` must call `disarm()` then re-`arm(Read)` — see Q-U5-2.

### 2.2 ViewModel state shape

- [x] 2.2.1 Lock final `MainUiState` shape for U5 (slices owned now; later units extend):
  ```kotlin
  data class MainUiState(
      val form: FormState,
      val spoolman: SpoolmanState,
      val nfc: NfcState,
      val banner: BannerState,            // U5 ships BannerState.Hidden as default; U9 derives
      val activeFlow: ActiveFlow,
      val ambiguity: AmbiguityState,      // U5 owns; non-null when Ambiguous result fired
  )
  ```
- [x] 2.2.2 Lock `FormState` shape (matches component-methods.md §7 within U5's scope):
  ```kotlin
  data class FormState(
      val cardUid: CardUid? = null,                 // present once a tag has been read; survives form clear
      val material: Material? = null,
      val brand: Brand? = null,                     // U5 ships String wrapper; U8 swaps in catalogue type
      val colorHex: String? = null,
      val variant: String? = null,
      val tempRanges: TempRanges = TempRanges(),
      val selectedSpoolId: Int? = null,
      val rawWriteMode: Boolean = false,            // FR-4.8 — U5 ships always-false; U7 wires editing
  )
  data class TempRanges(
      val extruderMin: Int? = null,
      val extruderMax: Int? = null,
      val bedMin: Int? = null,
      val bedMax: Int? = null,
  )
  ```
- [x] 2.2.3 Lock `SpoolmanState` shape (read-only projection of `SpoolmanRepository`):
  ```kotlin
  data class SpoolmanState(
      val spools: List<SpoolmanSpool> = emptyList(),
      val selectedSpoolId: Int? = null,             // mirrors FormState.selectedSpoolId for dropdown UX
      val urlConfigured: Boolean = false,
  )
  ```
- [x] 2.2.4 Lock `NfcState` shape — exact mirror of `NfcRepository.state` (no projection layer):
  ```kotlin
  typealias NfcState = NfcResult     // U1 placeholder removed; U5 uses NfcResult directly
  ```
- [x] 2.2.5 Lock `BannerState` (U5 placeholder; full derivation U9):
  ```kotlin
  sealed interface BannerState {
      data object Hidden : BannerState
      data class Offline(val lastError: String?) : BannerState
  }
  ```
  U5 emits `Hidden` always; transient errors go through `UiEffect.ShowSnackbar`.
- [x] 2.2.6 Lock `ActiveFlow` shape (U5 owns the baseline + ReadingForPair; later units extend):
  ```kotlin
  sealed interface ActiveFlow {
      data object Idle : ActiveFlow
      data object ReadingForPair : ActiveFlow
      // U6a/U6b/U7 add: Writing, Verifying, Repairing, TwoTag, VendorOptIn, RawWriting
  }
  ```
- [x] 2.2.7 Lock `AmbiguityState`:
  ```kotlin
  data class AmbiguityState(
      val uid: CardUid,
      val matches: List<SpoolmanSpool>,
  )
  ```
  Emitted alongside the use-case's `Ambiguous` result; cleared on next successful read or dropdown selection.
- [x] 2.2.8 Lock the **`Brand` placeholder for U5** — see Q-U5-9. U5 ships either a `Brand(name: String)` value class living in `domain/models/` or reuses the v1 `BrandDatabase.Brand` shape. U8 will replace it.

### 2.3 Form prefill business rules

- [x] 2.3.1 Define `SpoolmanSpool → FormState` mapping (FR-3.2 / S-3.2 / S-3.6):
  - `material` ← `MaterialDatabase.getMaterial(spool.filament.material ?: "Unknown")` (preserves v1 fallback semantics; U8 will swap to `MaterialBrandRepository`).
  - `brand` ← `Brand(spool.filament.vendor?.name ?: "Unknown")`.
  - `colorHex` ← `spool.filament.color_hex` normalised via `FilamentSpool.fromSpoolman` rules (`#`-strip, take last 6 chars, uppercase).
  - `variant` ← null (Spoolman has no variant column; preserved from v1).
  - `tempRanges` ← derived from `(material, settings_extruder_temp, settings_bed_temp)` reusing `FilamentSpool.fromSpoolman` rules verbatim.
  - `selectedSpoolId` ← `spool.id`.
  - `cardUid` preserved from current `FormState.cardUid` (or set if read just produced one).
  - `rawWriteMode` preserved.
- [x] 2.3.2 Define `OpenSpoolPayload → FormState` mapping (FR-3.4 / FR-11.1 / S-3.4):
  - `material` ← `MaterialDatabase.getMaterial(payload.type)`; null fallback ⇒ create transient `Material(name = payload.type, defaultMinTemp = parsed-or-fallback, ...)` so the form renders.
  - `brand` ← `Brand(payload.brand)`.
  - `colorHex` ← `payload.colorHex?.removePrefix("#")?.takeLast(6)?.uppercase()?.takeIf { it.isNotEmpty() }`.
  - `variant` ← `payload.subtype.takeUnless { it == "Basic" || it.isBlank() }`.
  - `tempRanges`:
    - `extruderMin/Max` ← `payload.minTemp.toIntOrNull()` / `payload.maxTemp.toIntOrNull()`.
    - `bedMin/Max` ← `payload.bedMinTemp?.toIntOrNull()` / `payload.bedMaxTemp?.toIntOrNull()`.
    - On parse failure, fall back to material defaults via `MaterialDatabase` (preserves v1 quirk).
  - `selectedSpoolId` ← null (no Spoolman binding).
  - `cardUid`, `rawWriteMode` preserved.
- [x] 2.3.3 Define **blank form** (FR-3.4 / S-3.5 blank/Vendor branch):
  - `FormState.copy(cardUid = uid, material = null, brand = null, colorHex = null, variant = null, tempRanges = TempRanges(), selectedSpoolId = null)`.
  - `rawWriteMode` preserved.
  - **Vendor classification** (S-3.5): same blank-form behaviour. No error toast (it's a normal state, not a failure).
- [x] 2.3.4 Define **clear-form** semantics for `onSpoolSelected(null)` (S-3.6 clear AC):
  - Same as blank form but `cardUid` is preserved (UID is "context", not "form data") — see Q-U5-7.
  - Clears `AmbiguityState` if present.
- [x] 2.3.5 Define **re-selection** semantics (S-3.6 re-select AC): full overwrite from the newly-selected spool; no merge with prior FormState fields.
- [x] 2.3.6 Define **idempotency** of `onSpoolSelected(spool)` for the same spool already selected: no state change (`distinctUntilChanged` on `selectedSpoolId`).

### 2.4 Spoolman lookup branch business rules

- [x] 2.4.1 Define **URL-not-configured branch** (Q-CD1.1=A, services.md §7) — see Q-U5-3. Recommendation: when `SpoolmanRepository.findSpoolsByCardUid` returns `NetworkError(UrlNotConfigured)` (sentinel), the use-case treats it as **0 Spoolman matches** (not as `SpoolmanFailed`) and falls through to `PrefillFromTag` (if OpenSpool) or `BlankForm` (if Blank/Vendor). This realises the "no banner activity, no error" requirement.
- [x] 2.4.2 Define **HTTP/network/parse error branch** (FR-10.2 / S-10.2): use-case returns `SpoolmanFailed(uid, classification, outcome)`. `MainViewModel` emits `UiEffect.ShowSnackbar(...)` with a human-readable message. Form is **not** prefilled.
- [x] 2.4.3 Define **0-match × OpenSpool**: `PrefillFromTag(uid, payload)`.
- [x] 2.4.4 Define **0-match × Blank|Vendor**: `BlankForm(uid, classification)`.
- [x] 2.4.5 Define **1-match × any classification**: `PrefillFromSpoolman(uid, spool, classification)` (rule §2.1.6).
- [x] 2.4.6 Define **>1-match × any classification**: `Ambiguous(uid, matches, classification)`. `MainViewModel` populates `AmbiguityState`; form is **not** prefilled (S-3.3 "block auto-pairing"); dropdown remains usable so user can manually select one of the matches via `onSpoolSelected` — that selection then fires the standard FR-3.6 prefill path.
- [x] 2.4.7 Define empty-`CardUid` short-circuit: U3 short-circuits empty UID to `Success(emptyList())`. U5 follows the 0-match branch. (Should not occur on real hardware — defensive.)

### 2.5 NFC failure branch

- [x] 2.5.1 Define **NFC-not-available branch** (Q-U4-9=A): use-case returns `NfcFailed("NFC not available")` without calling Spoolman. `MainViewModel` emits `UiEffect.ShowSnackbar`; `ActiveFlow` returns to `Idle`.
- [x] 2.5.2 Define **NFC error during read** (e.g., zero-length UID): use-case returns `NfcFailed(error.reason)` without calling Spoolman.
- [x] 2.5.3 Define **vendor-tag during read** (FR-3.5 / FR-11.2): NOT an error. Falls through `BlankForm` branch (with `classification = Vendor(reason)` carried so U7 can pick it up later).

### 2.6 Banner / connectivity surfacing

- [x] 2.6.1 Define U5's banner contract: `MainUiState.banner` always emits `BannerState.Hidden` from U5. U9 wires the full derivation. **Reason**: U5 must not pre-empt U9's Q-U5-4 design space, but the slot must exist now so U6a / U7 can compose.
- [x] 2.6.2 Define U5's **error surface**: `UiEffect.ShowSnackbar(message)` for `SpoolmanFailed` and `NfcFailed`. Snackbar text is human-readable; falls back to `outcome.toString()` / `error.reason` as last resort.

### 2.7 Test plan

- [x] 2.7.1 Define test surface: `ReadAndPairUseCase` against `FakeNfcRepository` + `FakeSpoolmanRepository`. `MainViewModel` against the same fakes plus an in-memory `MaterialBrandRepository` stub (or direct `MaterialDatabase` lookup if Q-U5-9=A).
- [x] 2.7.2 Use-case test cases:
  - Tag-first hit: `consumeLastSeen` returns `Success(uid, OpenSpool(payload))` + Spoolman 0 matches → `PrefillFromTag`.
  - Tag-first hit + 1 match → `PrefillFromSpoolman` (collision rule §2.1.6).
  - Tag-first miss → falls through to `arm(Read)`.
  - `arm(Read)` + tap → branches by classification × match count (4 combinations + `Ambiguous` + `SpoolmanFailed`).
  - `NfcFailed` short-circuits before Spoolman call.
  - URL-not-configured short-circuit returns `PrefillFromTag` / `BlankForm` (no `SpoolmanFailed`).
  - Re-tap during `ReadingForPair` triggers `disarm` + re-`arm` (Q-U5-2).
- [x] 2.7.3 ViewModel test cases (Q-T3=B — beyond NFR-4.1 minimum bar):
  - `onReadTapped` produces correct `MainUiState` for each result variant.
  - `onSpoolSelected(spool)` prefills `FormState` from `SpoolmanSpool`.
  - `onSpoolSelected(null)` clears form, preserves `cardUid`.
  - Re-selection overwrites.
  - Same-id re-selection is idempotent.
  - `Ambiguous` populates `AmbiguityState` and skips form prefill.
  - `SpoolmanFailed` emits `UiEffect.ShowSnackbar` and skips form prefill.
- [x] 2.7.4 Out-of-scope tests: real-device NFC tap (manual verification at U5 milestone install gate).

### 2.8 Brownfield migration

- [x] 2.8.1 Disposition of v1 `FilamentSpool.fromSpoolman` (companion-object factory): **retain** — the projection logic is reused inside U5's `Spool → FormState` mapping (§2.3.1). U8 may swap callers later but the function itself stays.
- [x] 2.8.2 Disposition of v1 `MainScreenContent` Compose function (legacy file under `ui/screens/`): U5 deletes / supersedes its read-side surfaces. Write/UI scaffolding from v1 may be partially superseded; everything not used by U5 stays untouched until U6a/U6b/U7 land.
- [x] 2.8.3 Disposition of v1 `MainViewModel` (legacy if exists outside `ui/screens/main/`): only the v2 `ui/screens/main/MainViewModel` exists post-U1 (verified — `find` shows single file). No deletion required.
- [x] 2.8.4 v1 `OpenSpoolData` → `FilamentSpool.fromOpenSpool` mapping: already removed in U2. U5's `OpenSpoolPayload → FormState` mapping (§2.3.2) is its successor.

### 2.9 Hilt wiring

- [x] 2.9.1 `ReadAndPairUseCase` is `@Inject constructor(nfc: NfcRepository, spoolman: SpoolmanRepository)`. No `@Module` provider needed (constructor injection sufficient).
- [x] 2.9.2 Update `MainViewModel`'s `@Inject` constructor to take: `nfc: NfcRepository`, `spoolman: SpoolmanRepository`, `readAndPair: ReadAndPairUseCase`. (Other use-cases listed in component-methods.md §6 land in U6a/U6b/U7 — U5 must not import them yet.)
- [x] 2.9.3 No new Hilt module required.

### 2.10 Doc-drift fix-ups

- [x] 2.10.1 `component-methods.md` §6 `MainViewModel` constructor lists six use-cases. U5 ships only `readAndPair`. The other five are added by U6a/U6b/U7. This is **not** drift — it's correct phased construction. No fix-up needed.
- [x] 2.10.2 `component-methods.md` §7 `MainUiState` references `Material` / `Brand` / `Spool` types. U5 ships with `Material` (v1 type), a temporary `Brand(name: String)` value class, and `SpoolmanSpool` directly (no `Spool` alias). Record this as an interim shape; U8 will introduce the catalogue's `Brand` and may rename. **No commit-time fix-up in U5**.

---

## 3. Open Questions (to be answered)

### Q-U5-1 — `ReadAndPairUseCase.invoke()` collection rhythm

How does the use-case observe `nfc.state` after `arm(Read)`?

[Answer]:

A. **Recommended.** Subscribe via `nfc.state.first { it is NfcResult.Success || it is NfcResult.Error }` — single-shot await, simplest correctness model. Coroutine cancellation propagates cleanly when caller's scope cancels.
B. Subscribe via a `take(1)` filter on terminal states inside a `withTimeout` guard — adds a 30 s wall-clock timeout in case the device is paused with `arm` still active.
C. Subscribe via `collect` and break out on first terminal state — equivalent to A in practice; more verbose.
D. Other.

**Recommendation**: A. Cleanest, idiomatic for `StateFlow` + `suspend operator fun invoke()`. Timeout is an NFR concern (U9 / U10 polish) — adding it pre-emptively risks false-positive failures during slow taps.

---

### Q-U5-2 — Re-tap of "Read" while `ActiveFlow == ReadingForPair`

What does a second `onReadTapped()` call do while a read is already armed?

[Answer]:

A. **Recommended.** Disarm the in-flight read, immediately re-arm. Mirrors NFC-controller idiom: the latest user intent wins. (`NfcRepository.arm(Read)` from non-`Idle` already implicit-disarms per BR-U4-SM-3 — the VM just re-invokes the use-case.)
B. No-op — second tap is dropped while the first is in flight. Safer (no buffer flapping) but feels unresponsive.
C. Surface a `UiEffect.ShowSnackbar("Already reading — tap your tag")` and ignore the second tap.
D. Other.

**Recommendation**: A. Aligns with U4's idempotent re-arm semantics; gives the user a way out if they realise they tapped Read by mistake before tapping a tag.

---

### Q-U5-3 — URL-not-configured handling in `findSpoolsByCardUid`

`SpoolmanRepository.findSpoolsByCardUid` returns `NetworkError(UrlNotConfigured)` when the URL is empty. How does the use-case treat this case?

[Answer]:

A. **Recommended.** Treat as **0 matches** — fall through to `PrefillFromTag` (if OpenSpool) / `BlankForm` (otherwise). No banner, no snackbar (services.md §7 banner-suppression rule Q-CD1.1=A). Lets the app stay fully usable without a Spoolman URL (S-10.1 / FR-10.1).
B. Treat as `SpoolmanFailed` with a one-time onboarding nudge ("Configure Spoolman URL in Settings"). More guidance but contradicts S-10.1 ("App is fully usable without a Spoolman URL").
C. Treat as `SpoolmanFailed` always (uniform error model). Simplest but worst UX.
D. Other.

**Recommendation**: A. Implements Q-CD1.1=A faithfully and preserves S-10.1 across all read scenarios.

**Implementation note**: `SpoolmanRepository.findSpoolsByCardUid` currently maps URL-not-configured to `NetworkError` with cause `UrlNotConfigured` (per U3 plan §2.2.5). The use-case must pattern-match on the cause type — recommend introducing `SpoolmanOutcome.NetworkError.cause is UrlNotConfigured` as the discriminator. If cleaner, introduce a typed sentinel `SpoolmanOutcome.NotConfigured` in U3 — but that's an out-of-scope refactor. Stick with cause-matching for U5.

---

### Q-U5-4 — `BannerState` derivation in U5

`unit-of-work.md` §3-U5 says full banner-state derivation lands in U9. What does U5 ship now?

[Answer]:

A. **Recommended.** Always emit `BannerState.Hidden`. Surface transient network errors via `UiEffect.ShowSnackbar` only. U9 introduces the full `connectivity` × URL-configured derivation.
B. Wire a partial derivation now (`BannerState.Offline` when `connectivity is Unreachable`), and let U9 just refine the copy / Retry button.
C. Skip the `BannerState` slot entirely; reintroduce in U9.
D. Other.

**Recommendation**: A. Cheapest U5; leaves the full banner UX (copy, retry, suppression rules) for U9 where Settings + connectivity controls land together.

---

### Q-U5-5 — `MainUiState.spoolman.spools` source

`SpoolmanState.spools` projection — what populates it?

[Answer]:

A. **Recommended.** `MainViewModel` collects `spoolmanRepository.spools` directly into `MainUiState.spoolman.spools`. Dropdown reflects the current cache. No explicit refresh in U5 (URL-change auto-clears via U3 init logic; `refresh()` is U9's responsibility).
B. `MainViewModel` calls `spoolman.refresh()` once on first composition. Eager but adds a network call on app start that may fail; mixes U5/U9 concerns.
C. Lazy fetch — `MainViewModel` calls `refresh()` the first time the dropdown is expanded. Saves a network call but couples UX to dropdown internals.
D. Other.

**Recommendation**: A. Cleanest separation; U9 owns the eager-refresh / pull-to-refresh affordances. The dropdown will be empty on first launch until U9 ships — acceptable for U5 milestone gate provided manual refresh path exists from Settings.

**Note**: If A is chosen, the U5 install gate must be exercised after the user has at least once visited Settings → Test connection (which runs `probe()` and is the only U9-pre-existing refresh trigger). `MainViewModel` exposes nothing that fetches spools in U5.

---

### Q-U5-6 — `Brand` type in U5

`MainUiState.form.brand` and `SpoolmanFilament.vendor.name` need to project to a single domain type for the form. U8 will ship `MaterialBrandRepository` with a real `Brand` type. What does U5 use now?

[Answer]:

A. **Recommended.** Ship a tiny `domain/models/Brand.kt` value class `data class Brand(val name: String)` in U5; U8 may extend it (add `id`, `customSource` etc.) without breaking. Form mapping uses `Brand(spool.filament.vendor?.name ?: "Unknown")`.
B. Use raw `String` for `brand` in `FormState`; U8 widens to a full type. Smallest U5 footprint but creates a refactor surface in U8.
C. Lift v1's `BrandDatabase.Brand` into `domain/models/`. Ties U5 to v1's specific shape; couples U5 to U8's swap.
D. Other.

**Recommendation**: A. Smallest forward-compatible surface; data-class-ness lets U8 add fields without breakage.

---

### Q-U5-7 — `cardUid` preservation across `onSpoolSelected(null)` / dropdown clear

When the user clears the dropdown (S-3.6 clear-empties AC), does `FormState.cardUid` stay or get cleared?

[Answer]:

A. **Recommended.** **Stay.** UID is "context" of the current tap, not "form data". Clearing the dropdown should empty material/brand/temps/etc. but keep the UID visible so the user knows which tag they're working with. The next read tap overwrites it.
B. **Clear.** Reset everything to defaults — fully restart the form.
C. Configurable per use-case caller. Overkill for U5.
D. Other.

**Recommendation**: A. Matches the v1 mental model (UID is sticky once read) and supports the FR-4 write flow that follows in U6a (the user often clears the dropdown to type a new spool while still holding the same tag).

---

### Q-U5-8 — Ambiguity (>1 Spoolman match) UI behaviour

S-3.3 says "lists the matching spool names/ids and blocks auto-pairing." How does U5 surface this?

[Answer]:

A. **Recommended.** Populate `MainUiState.ambiguity = AmbiguityState(uid, matches, classification)`. `MainScreen` renders a small inline error block listing each match (id + filament name + vendor). Form stays empty (no auto-pair). Dropdown remains active — picking one of the matches via `onSpoolSelected` clears `AmbiguityState` and prefills the form via S-3.6. No PATCH/POST is issued (S-3.3 [unit] AC).
B. Show a modal dialog blocking the screen until user picks one of the matches.
C. Show only a snackbar; matches surfaced via dropdown alone.
D. Other.

**Recommendation**: A. Implements both ACs (manual: list visible; unit: no PATCH/POST + form stays empty), and reuses the FR-3.6 dropdown path for resolution.

---

### Q-U5-9 — `Material` resolution in U5 (pre-U8)

`FormState.material` needs a `Material` instance. U8 ships `MaterialBrandRepository`. What does U5 use?

[Answer]:

A. **Recommended.** Use the existing v1 `MaterialDatabase` directly in the use-case + VM (resolution helpers stay until U8). Materials missing from the DB synthesise a transient `Material(name = type, defaultMinTemp = parsed-or-fallback, ...)` so the form renders. U8 swaps callers to `MaterialBrandRepository`.
B. Create a tiny `MaterialResolver` interface in U5 with the DB-backed default impl, and let U8 swap the impl. Strictly cleaner but adds an interface that exists only to be replaced.
C. Hardcode common materials in the use-case as a temporary measure. Worst — adds throwaway code.
D. Other.

**Recommendation**: A. Honours U5/U8 sequencing; existing `MaterialDatabase` is already a domain-side singleton-ish object. Once U8 lands, the DB reference is replaced by the catalogue repository.

---

### Q-U5-10 — Test fakes — fresh hand-rolled vs reused from U3/U4

U3 shipped `FakeSpoolmanApi`; U4 shipped `FakeNfcAdapterWrapper`. Neither is a full repository fake. What does U5 use for `ReadAndPairUseCase` and `MainViewModel` tests?

[Answer]:

A. **Recommended.** Hand-roll **`FakeNfcRepository`** + **`FakeSpoolmanRepository`** in `app/src/test/java/com/spoolpainter/app/support/` — the use-case talks to repository APIs, not adapter / API fakes. Both fakes expose simple `set` / `simulate` methods to drive results. No mockito; no MockK.
B. Reuse `FakeSpoolmanApi` + `FakeNfcAdapterWrapper` and instantiate real repositories in tests. Slower (real `MutableStateFlow` plumbing), but maximally close to production paths.
C. Mix — real `SpoolmanRepository` against `FakeSpoolmanApi`; hand-rolled `FakeNfcRepository`. Inconsistent.
D. Other.

**Recommendation**: A. The use-case is a thin orchestrator over **repository surfaces** — testing it against repository fakes is the right granularity. Repository internals (cache invalidation, OkHttp wiring) are already covered by U3/U4's own tests.

---

### Q-U5-12 — `spool_id` fallback lookup when UID lookup returns 0 matches

When the read tag carries an `OpenSpool` payload with a non-null `spool_id` AND the UID lookup returns 0 matches, should the use-case try to resolve the spool by `spool_id` before falling through to `PrefillFromTag`?

[Answer]:

A. **Yes — `spool_id` fallback (chosen 2026-05-25 per user request "if we find either we use").** After UID lookup returns `Success([])`, if classification is `OpenSpool(payload)` AND `payload.spoolId.toIntOrNull() != null`, call `spoolman.getSpool(spoolId)`:
  - `Success(spool)` → result is `Success.PrefillFromSpoolman(uid, spool, classification)` (same shape as UID-match path; the `MainViewModel` projection is identical).
  - `HttpError(404, ...)` → fall through to current `Success.PrefillFromTag(uid, payload)` branch (the spool was deleted / renumbered).
  - Any other `HttpError | NetworkError | ParseError` → `SpoolmanFailed(uid, classification, outcome)` with snackbar (same as the UID-lookup error path).
B. No — UID-only lookup (original U5 plan).
C. Other.

**Recommendation**: A. Recovers paired-spool metadata when the v1 tag carries `spool_id` but the spool's `lot_nr` was never updated to include `card_uid:` (legacy v1 → v2 transition tags). UID-lookup remains the **primary** key; `spool_id` is a strict fallback that only fires on 0 UID matches. No security risk: if someone has copied a tag's `spool_id`, they could already write the same payload via U6a — this rule only changes which Spoolman record we *prefill from*, not what we *bind to* (binding is the U6a write path's concern via `card_uid:` in `lot_nr`).

**Implementation note**: requires exposing `SpoolmanRepository.getSpool(id: Int): SpoolmanOutcome<SpoolmanSpool>` as a public method (currently a private helper used by `appendCardUidToSpool` / `removeCardUidFromSpool`).

---

### Q-U5-11 — `MainScreen` Compose surface scope in U5

What does `MainScreen` look like at end of U5?

[Answer]:

A. **Recommended.** Minimal but real:
  - Top bar with a single Settings icon (nav to U9 placeholder).
  - "Read" button + UID display row.
  - `SpoolmanDropdown` (real implementation).
  - Read-only form preview rows (material / brand / colour swatch / variant / temps) — no inputs yet (inputs land in U6a).
  - Snackbar host.
  - Ambiguity error block when `AmbiguityState != null`.
  - "Tap a tag…" hint when reading.
  Write button is **not** present in U5 (U6a adds it).
B. Full editable form (inputs for every field). Larger surface; pulls U6a UI work forward.
C. Read-only display only — no `SpoolmanDropdown`. Smallest surface but FR-3.6 (dropdown selection prefill) becomes untestable on-device at the install gate.
D. Other.

**Recommendation**: A. Smallest surface that exercises every U5 AC (S-3.1..S-3.6) on-device at the milestone install gate. Inputs deferred to U6a.

---

## 4. Decision Records

User approval token: **"i trust you"** — locks in the **Recommended** option (A) for every question.

| Question | Selected option | Rationale |
|---|---|---|
| Q-U5-1 | **A → revised 2026-05-25** | Use-case still uses `nfc.state.first { Success || Error }` (recommendation unchanged at the use-case layer). VM now wraps the use-case in `withTimeoutOrNull(10_000ms)` to prevent the persistent-hint bug observed at the install gate. Timeout originally deferred to U9/U10 polish — promoted to U5 after on-device feedback. |
| Q-U5-2 | **A** | Re-tap → `disarm()` + re-`arm(Read)`. Latest user intent wins; aligns with U4's idempotent re-arm semantics (BR-U4-SM-3). |
| Q-U5-3 | **A** | URL-not-configured (`NetworkError(cause = UrlNotConfigured)`) treated as **0 matches**; falls through `PrefillFromTag` / `BlankForm`. No banner, no snackbar — preserves S-10.1 + Q-CD1.1=A banner suppression rule (services.md §7). |
| Q-U5-4 | **A** | `MainUiState.banner` always emits `BannerState.Hidden` in U5. Transient errors flow through `UiEffect.ShowSnackbar`. Full derivation lands in U9 with Settings + connectivity controls. |
| Q-U5-5 | **A** | `MainViewModel` collects `spoolmanRepository.spools` directly into `SpoolmanState.spools`. No auto-refresh in U5; dropdown reflects current cache. Refresh / pull-to-refresh is U9. Install-gate plan must call out the "visit Settings → Test connection once" pre-step. |
| Q-U5-6 | **A** | New `domain/models/Brand.kt` value class `data class Brand(val name: String)` shipped in U5. U8 may extend (add `id`, `customSource`) without breaking. |
| Q-U5-7 | **A → revised 2026-05-25** | UID row now reflects "the UID we'd act on right now": tag tap sets it; dropdown selection takes UID from spool's `lot_nr` (or clears if absent); dropdown Clear clears it. Original A ("preserve cardUid through dropdown changes") replaced after install-gate feedback that the decoupled UID was confusing. New rule: `onSpoolSelected(spool)` decodes `lot_nr` via `CardUidEncoding.decode(spool.lot_nr).uids.firstOrNull()`; `onSpoolSelected(null)` resets `FormState` entirely. Read-flow auto-prefill from a UID match still keeps the just-tapped UID (use-case sets `cardUid = uid` explicitly via `PreserveCurrent` source). |
| Q-U5-8 | **A** | `MainUiState.ambiguity = AmbiguityState(uid, matches, classification)` populated; inline error block lists matches; form stays empty (no auto-pair); dropdown remains active so user can resolve via S-3.6 path. No PATCH/POST issued. |
| Q-U5-9 | **A** | `MaterialDatabase` used directly in U5; missing materials synthesise transient `Material(name, defaults from payload-or-fallback)` so the form renders. U8 swaps callers to `MaterialBrandRepository`. |
| Q-U5-10 | **A** | Hand-rolled `FakeNfcRepository` + `FakeSpoolmanRepository` in `app/src/test/java/com/spoolpainter/app/support/`. Use-case is a thin orchestrator over repository surfaces — testing at that granularity is correct. No mockito / no MockK. |
| Q-U5-11 | **A** | `MainScreen` ships minimal-but-real surface: top bar (Settings nav stub), Read button, UID row, `SpoolmanDropdown` (real impl), read-only form preview, snackbar host, ambiguity block. Write button + form inputs land in U6a. |
| Q-U5-12 | **A** | `spool_id` fallback when UID-lookup returns 0 matches AND classification is `OpenSpool(payload)` with a non-null parseable `payload.spoolId`. Spool found via fallback → `PrefillFromSpoolman` (same shape as UID-match path). 404 → `PrefillFromTag` (current 0-match branch). Other errors → `SpoolmanFailed`. Decided 2026-05-25 in response to user request after seeing v1-era tags carrying `spool_id` without `card_uid:` in `lot_nr`. |

---

## 5. Approval Gate (Step 7-8)

After answers are recorded:
1. Generate Functional Design Part 2 artefacts at `aidlc-docs/construction/u5-read-and-pair-flow/functional-design/{business-logic-model,business-rules,domain-entities,frontend-components}.md`.
2. Present the "Functional Design Complete" workflow message.
3. Wait for explicit user approval before advancing to Code Generation.
