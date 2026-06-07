# U13 — Action Split: Save vs Write — Code Generation Plan (Part 1)

**Unit**: U13 (added 2026-06-06 from Cluster B + Cluster A tester feedback)
**Per-unit gate**: FD / NFR-R / NFR-D / Infra-D **SKIP** — Cluster B (2026-06-05, 9 design decisions) + Cluster A (2026-06-06, weight-picker reframe) lock the design surface. Captured in `aidlc-docs/operations/v2.0.2-feedback-raw.md` §"Decisions (locked)" + §"U13 scope additions".
**Release window**: v2.1 (separate testing-track release). Cluster D (already shipped on tree as commit `2add547`) + F-7/F-8 polish ride v2.0.3 *without* U13.
**Plan source**: feedback-raw.md decisions 1..11 + Cluster A radio reframe.

---

## 1. Scope locked

### 1.1 Action split

Replace the single `Save & Write` / `Save & Map` / `Write to NFC` button (today: `MainScreen.kt:277`) with **two separate top-level buttons**:

- **Save to Spoolman** — Spoolman-only; no tag involved. Creates / patches vendor + filament + spool + overrides via `SpoolmanRepository`. Yields a spool id + auto-selects the new spool in the dropdown.
- **Write** — NFC-only. Opens the listen window, accepts the next tap, appends UID to the *selected* spool's `extra.card_uids`, writes the OpenSpool NDEF payload. One transaction, single tap.

Combo writes (today's `Save & Write` happy path) become a two-tap sequence: user taps Save, then Write. The selection auto-pin keeps Write enabled for the freshly-saved spool.

### 1.2 Bottom action row (replaces ReadFab)

Stationary bottom row with two actions:

```
┌─────────────────────┬─────────────────────┐
│  Read / Cancel      │  Write / Cancel     │
└─────────────────────┴─────────────────────┘
```

**Cancel rule (locked 2026-06-06)**: only NFC-bound flows that wait for a tag (Read + NDEF Write) get Cancel. HTTP-only flows (Save, vendor UID-only pair, second-tag listening also gets Cancel since it waits for a tap) keep their owning button disabled-while-in-flight without a separate Cancel surface — they complete in ~250 ms typical Spoolman roundtrip and the user-visible block is too short to be worth a Cancel affordance.

| `activeFlow`                       | Read button   | Write button   | Other affordance         |
|------------------------------------|---------------|----------------|--------------------------|
| `Idle`                             | Read          | Write          | —                        |
| `ReadingForPair`                   | **Cancel**    | disabled       | — (waits for tag — long) |
| `WritingForPair` (standard NDEF)   | disabled      | **Cancel**     | — (waits for tag — long) |
| `WritingRaw` (RawNoUrl, NDEF)      | disabled      | **Cancel**     | — (waits for tag — long) |
| `PairingVendorUidOnly` (HTTP only) | disabled      | disabled       | — (no Cancel, fast HTTP) |
| `WritingSecondTag(spoolId)`        | disabled      | disabled       | sheet's Pair another → **Cancel** (§11) |
| `PromptingPairAnother(spoolId)`    | disabled      | disabled       | sheet has its own Done / Pair another |
| `AwaitingRepairConfirmation(...)`  | disabled      | disabled       | sheet has its own Confirm / Cancel    |
| Save in flight (Spoolman HTTP)     | disabled      | disabled       | — (no Cancel, fast HTTP) |

- The "owning" button shows a subtle progress glyph + the word "Cancel" while in flight. Tappable; cancels the in-flight coroutine + disarms NFC + returns to Idle without a snackbar (explicit user action; F-12 part 1).
- 10 s safety-net timeouts retained on Read + Write (F-12 part 2 deferred — locked decision 11).
- HTTP-only flows (Save, vendor UID-only pair) rely on OkHttp's connect/read timeouts + the 10 s outer `withTimeoutOrNull` safety net. No mid-flight Cancel surface — they're fast enough that adding one is just extra UI weight.
- Write disabled until form is saved or a spool is selected (decision 4). Caption: "Pick a spool or hit Save first."
- Vendor-tag Write — see §6 (`canWrite` flow). Today's locked decision 5 ("Write disabled with caption") is reframed during Q-U13-1: vendor + Spoolman + spool selected SHOULD route Write → vendor UID-only pair internally (the same pair affordance, different internal route). Reconfirm during Part 2.

The bottom row is rendered in `Scaffold.bottomBar` with `imePadding()` + `navigationBarsPadding()` so it survives keyboard up + gesture-bar inset (ref U10's themes.xml lint fix path).

### 1.3 Outer Card layout (reverses U9b's three-independent-Cards decision)

```
┌─ Outer Card ─────────────────────────┐
│  ┌─ Spoolman dropdown ─┐             │
│  │  Spool ▾  [X]       │             │
│  └─────────────────────┘             │
│  ┌─ Filament form ─────┐             │
│  │  Material/Variant/  │             │
│  │  Color/Brand        │             │
│  └─────────────────────┘             │
│  ┌─ More details ▾ ────┐             │
│  │  Temperature        │             │
│  │  Weight (radio)     │             │
│  │  Others             │             │
│  └─────────────────────┘             │
│                                      │
│   [ Save to Spoolman ]               │
└──────────────────────────────────────┘
```

- Outer Card elevation 1, inner Cards drop to elevation 0 (or become Surface dividers — pick the cheapest; decision 6).
- Save to Spoolman lives at the bottom of the outer Card (signals "everything in here saves together").
- Status surfaces (banner / sheet / hints) stay between the logo header and the outer Card, on screen background.
- The bottom action row sits below `Scaffold` content, NOT inside the outer Card.

### 1.4 Radio-style weight picker (Cluster A — Spoolman parity)

Replaces today's bidirectional Remaining + Measured row + back-solve branch in `onMeasuredWeightChanged` (`MainViewModel.kt:590-608`). Mirrors Spoolman's gross/measured/remaining selector.

```
Weight measurement
( ) Remaining   (•) Measured     ← radio segmented row at TOP
[    ] g                          ← single active field below

Empty spool         [    ] g
Filament weight     [    ] g
```

**Locked 2026-06-06**: radio sits at the top of the Weight section as a single segmented row (or a horizontal RadioButton group — pick whichever reads cleanest with material3 ≤ 24 dp tall). Only the **active** method's input field renders below; the inactive method's field is **hidden entirely** (not disabled). Switching the radio swaps which field is visible. Saves vertical space — install-gate UI is dense already.

- Active = **Remaining** → "Remaining" `[    ] g` field visible. Measured is derivable for display elsewhere if needed (e.g. supportingText below the field showing "Scale will read N g" when emptySpool is set), but no second input.
- Active = **Measured** → "Measured" `[    ] g` field visible. Remaining derived as `measured − emptySpoolWeightG` and committed when emptySpool is known.
- **Gross dropped** entirely (already locked previously; reaffirmed with the hide-inactive change).

Single source of truth: only one input field for weight at any moment, eliminates the silent-keystroke-swallow bug by construction.

Locked filament weight (existing-spool path) gets supportingText: "Switch to Remaining or Measured above to edit."

### 1.5 Snackbar copy delta

Locked label set:

| Path                                           | Copy                                                  |
|------------------------------------------------|-------------------------------------------------------|
| Save success (new spool)                       | `"Saved spool #N. Tap Write to pair a tag."`         |
| Save success (existing spool — patched only)   | `"Updated spool #N."`                                |
| Save success (no-op — nothing dirty)           | `(no snackbar)` — Save button greyed before tap; this case doesn't arise |
| Save failure (Spoolman)                        | `humanReadable(outcome)` (existing helper)            |
| Write success (first tag)                      | replaced by `PromptingPairAnother` sheet (unchanged)  |
| Write success (vendor pair)                    | replaced by `PromptingPairAnother` sheet (unchanged)  |
| Write disabled tap (no spool, no save)         | (button is disabled; tap doesn't fire)                |
| Write disabled tap (vendor tag observed)       | (button is disabled; caption renders inline)          |
| Read 10 s timeout                              | `"No tag tapped. Try again."` (unchanged)            |
| Read Cancel pressed                            | `(no snackbar)` — explicit user action               |
| Write 10 s timeout                             | `"No tag tapped. Try again."` (unchanged)            |

Today's combo-state copy in `applyWriteResult` (e.g. `"Saved to Spoolman. Tag write failed. Try again."`) is reframed: failure of the Save half can no longer be conflated with the Write half (they're distinct user actions). Drop these joint-state strings.

### 1.6 Out of scope (deferred not skipped)

- **F-4 erase tag completely** — separate feature, v2.1+ alongside U11/U12.
- **F-15 multi-vendor decode** — U11/U12.
- **F-2 alphabetize** — already shipped (Cluster D, commit `2add547`).
- **F-3 blank-tag report** — already shipped (closed in feedback-raw.md as already-works).
- **F-6 refresh staleness** — already shipped (Cluster D).
- **F-10 X behaviour asymmetry** — intentional; out of scope per Cluster B locked decision §"Items NOT resolved".

---

## 2. File impact summary

### 2.1 New files (3)

| Path                                                                                            | Purpose                                                                                  |
|-------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------|
| `app/src/main/java/com/spoolpainter/app/domain/usecases/SaveToSpoolmanUseCase.kt`               | Spoolman-only orchestration extracted from `CreateAndPairUseCase` steps 1+1a+1b          |
| `app/src/main/java/com/spoolpainter/app/domain/usecases/SaveToSpoolmanResult.kt`                | Result sum-type for the new use case                                                     |
| `app/src/main/java/com/spoolpainter/app/ui/components/WeightMethodRadio.kt`                     | Radio-method composable wrapping Remaining + Measured + (deferred) Gross fields         |

### 2.2 Modified files (production — 8)

| Path                                                                                                   | Change                                                                                                                                                                                          |
|--------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainViewModel.kt`                              | Split `onWriteTapped` into `onSaveTapped` (Spoolman-only) + `onWriteTapped` (NFC-only). Add `canSave` + `canWriteAfterSave` flows. Replace `onMeasuredWeightChanged` back-solve with radio model. Add `onWeightMethodPicked`. |
| `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainUiState.kt`                                | `FormState`: add `weightMethod: WeightMethod` (enum: `Remaining`, `Measured`); replace prefilled-weight singletons with derived dirty flags driven by the active method.                       |
| `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainScreen.kt`                                 | Outer-Card wrapper; Save button inside outer Card; bottom-bar Slot with Read/Cancel + Write; remove `ReadFab`; relocate `VendorTagHint` chip body copy.                                       |
| `app/src/main/java/com/spoolpainter/app/ui/components/MoreDetailsExpander.kt`                          | Replace Remaining/Measured pair with `WeightMethodRadio`; pass `weightMethod` + `onWeightMethodPicked`. Locked filament weight gains supportingText.                                          |
| `app/src/main/java/com/spoolpainter/app/ui/components/FilamentForm.kt`                                 | Drop the form-internal Save button (already removed by U9b §2). Verify no inner Card wrap.                                                                                                    |
| `app/src/main/java/com/spoolpainter/app/domain/usecases/CreateAndPairUseCase.kt`                       | Becomes a "write only" orchestration: assumes spool already exists (resolved by Save). Drop `resolveSpool` / `applyVariantToFilamentOfSpool` / `patchSpoolFields` calls — moved to Save use case. Step 2 (arm Write) + Step 3 (UID PATCH) + Step 4 (final result) remain. |
| `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanRepository.kt`                    | No public-surface change. Internal: confirm `applyOverridesToFilamentOfSpool` + `applyVariantToFilamentOfSpool` + `patchSpoolFields` + `createSpoolForExistingFilament` + `createSpoolForNewFilamentBundle` are all callable from the new Save use case — they are. |
| `app/src/main/java/com/spoolpainter/app/di/UseCaseModule.kt` (or wherever Hilt binds use cases)        | Bind `SaveToSpoolmanUseCase` (new) — singleton, no params beyond the existing repo deps.                                                                                                      |

### 2.3 Modified files (test — 7)

| Path                                                                                                         | Change                                                                                            |
|--------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------|
| `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelTest.kt`                                | All `onWriteTapped` happy-path cases split: caller does `onSaveTapped` then `onWriteTapped`. Add `canSave` assertions. |
| `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelTwoTagTest.kt`                          | Same split — pair-another flows assume spool already saved.                                     |
| `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelMoreDetailsExpanderTest.kt`             | Replace bidirectional Remaining/Measured cases with radio cases (active=Remaining, active=Measured). |
| `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelRawWriteTest.kt`                        | Raw-write path becomes Write-only (no implicit Save — there's no Spoolman). Verify Save button is hidden / disabled in `WriteMode.RawNoUrl`. |
| `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelRefreshTest.kt`                         | No surface change; verify Save / Write don't double-fire `refreshIfStale` calls.                |
| `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelBannerTest.kt`                          | No surface change.                                                                                |
| **NEW** `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelSaveTapTest.kt`                 | Cover the new `onSaveTapped` path: 6 cases (new-spool, existing-spool patch, no-op when nothing dirty, Spoolman failure, vendor-tag Save, RawNoUrl no-op). |
| **NEW** `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelWeightMethodTest.kt`            | Cover `onWeightMethodPicked` + active-method computed values: 4 cases.                          |
| **NEW** `app/src/test/java/com/spoolpainter/app/domain/usecases/SaveToSpoolmanUseCaseTest.kt`                | Cover the new use case: 8 cases (existing-spool no-op, existing-spool variant patch, existing-spool spool-scope patch, new-filament+spool, existing-filament+new-spool, vendor-tag Save, Spoolman failure, URL-not-configured). |

### 2.4 Deleted files

None. `CreateAndPairUseCase` shrinks but stays — Write half still needs orchestration.

---

## 3. Step-by-step plan

> Checkboxes are filled in during Part 2 (code generation). Each step has a "verify" pointer.

### §1. Domain primitives — `WeightMethod` enum

- [ ] Add `WeightMethod` enum to `MainUiState.kt` (or new `domain/primitives/WeightMethod.kt` if it grows beyond 2 values):
  ```kotlin
  enum class WeightMethod { Remaining, Measured }
  ```
- [ ] Add to `FormState`: `val weightMethod: WeightMethod = WeightMethod.Measured` (Measured is the user's documented preference — most scales report total).
- [ ] Mark `prefilledMeasuredWeightG: Float?` if needed for stale-prefill guard parity; otherwise rely on `prefilledRemainingWeightG` + `prefilledEmptySpoolWeightG`.

**Verify**: `compileDebugKotlin` ✅; FormState data class deconstruct still has unique field set.

### §2. Domain — `SaveToSpoolmanUseCase`

- [ ] Create `SaveToSpoolmanResult.kt`:
  ```kotlin
  sealed interface SaveToSpoolmanResult {
      sealed interface Success : SaveToSpoolmanResult {
          data class Saved(val spoolId: Int, val isNewSpool: Boolean) : Success
          /** Existing spool, no diff — Save was a no-op. */
          data object NoChanges : Success
      }
      data class Failed(val outcome: SpoolmanOutcome<*>) : SaveToSpoolmanResult
      data object UrlNotConfigured : SaveToSpoolmanResult
  }
  ```
- [ ] Create `SaveToSpoolmanUseCase.kt`. Inject only `SpoolmanRepository`. Public surface:
  ```kotlin
  open class SaveToSpoolmanUseCase @Inject constructor(
      protected val spoolman: SpoolmanRepository,
  ) {
      open suspend operator fun invoke(snapshot: SaveToSpoolmanInput): SaveToSpoolmanResult
  }
  ```
  Where `SaveToSpoolmanInput` matches today's `CreateAndPairInput` minus NFC concerns.
- [ ] Body extracts steps 1 / 1a / 1b from `CreateAndPairUseCase.invoke` (`CreateAndPairUseCase.kt:65-130`):
  - `resolveSpool(snapshot)` (existing private helper — copy verbatim, then delete from `CreateAndPairUseCase`).
  - On `ResolvedSpool.Existing(spoolId)`: dispatch variant patch + spool-scope patch (both already log-non-fatal). Return `Success.Saved(spoolId, isNewSpool=false)` if anything was patched, else `Success.NoChanges`.
  - On `ResolvedSpool.Created(spoolId, orphan)`: store orphan on `lastResolvedOrphan`, return `Success.Saved(spoolId, isNewSpool=true)`.
  - On `ResolvedSpool.Failed(result)`: convert `result.outcome` → `Failed(outcome)`.
- [ ] `lastResolvedOrphan` + `lastResolvedSpoolId` move to this use case (cleanup chain follows the new owner).
- [ ] Wire into Hilt — bind in the existing use-case module.

**Verify**: Unit test all 8 cases in §2.3 NEW `SaveToSpoolmanUseCaseTest`. Run `testDebugUnitTest`.

### §3. Domain — `CreateAndPairUseCase` shrink

- [ ] Drop `resolveSpool` (moved to Save use case).
- [ ] `CreateAndPairInput` gains `spoolId: Int` (required — caller has it from Save). `selectedSpoolId` no longer drives resolution since the spool always exists at write time.
- [ ] `invoke()` body now starts at today's step 2 (`armWriteAndAwait`). Steps 3 + 4 unchanged.
- [ ] Delete `lastResolvedOrphan` + `lastResolvedSpoolIdInternal` (moved).
- [ ] Result types: `CreateAndPairResult` keeps `Success.WrittenAndPaired` / `VerifyFailed` / `NfcFailed` / `Cancelled`. `SpoolmanFailed` stays — step 3's UID PATCH can still fail.

**Verify**: `MainViewModelTest` passes after wiring (§5). `compileDebugKotlin` clean.

### §4. UI — `WeightMethodRadio` composable

- [ ] Create `app/src/main/java/com/spoolpainter/app/ui/components/WeightMethodRadio.kt`:
  ```kotlin
  @Composable
  fun WeightMethodRadio(
      method: WeightMethod,
      activeValueG: Float?,         // remaining when method=Remaining; measured when method=Measured
      emptySpoolWeightG: Float?,    // optional — drives supportingText when present
      enabled: Boolean,
      onMethodPicked: (WeightMethod) -> Unit,
      onActiveValueChange: (String) -> Unit,
      modifier: Modifier = Modifier,
  )
  ```
- [ ] Layout: top row is a **horizontal RadioButton pair** (or `SegmentedButton` from material3 — pick whichever is shorter at 1-line height): `( ) Remaining   ( ) Measured`. Both labels equally weighted. Only one selected at a time.
- [ ] Below the radio: a **single** `DecimalField`. Label flips with the method (`"Remaining"` or `"Measured"`); suffix `"g"`. The inactive method has **no field rendered** at all — saves the vertical real estate.
- [ ] Optional supportingText on the field (helps anchor the conversion):
  - `method == Remaining` && emptySpool set && active value set → `"Scale will read ${active + empty} g"`.
  - `method == Measured` && emptySpool set && active value set → `"Filament left: ${active − empty} g"` (clamped ≥ 0).
  - Otherwise no supportingText.
- [ ] Switching the radio is purely a state change on `WeightMethod`; the underlying remaining/measured value bridge happens in the ViewModel (§7).

**Verify**: Compose preview renders both modes. No state hoisted into the composable beyond what's passed.

### §5. UI — `MoreDetailsExpander` integration

- [ ] Replace today's `Row { DecimalField("Remaining"); DecimalField("Measured") }` (lines 163-192) with `WeightMethodRadio` (radio at top, single active field below; inactive method's field hidden).
- [ ] Pass `weightMethod` + `onWeightMethodPicked` + the single `activeValueG` (computed in caller from `state.form.weightMethod`).
- [ ] Locked filament weight: keep `enabled = filamentSpecEnabled` but supportingText becomes `"Switch to Remaining or Measured above to edit."` when `filamentSpecLocked && enabled`.

**Verify**: Snapshot tests for the expander render the radio in both active states. Inactive method's field is **not** present in the composition (test by tag absence).

### §6. ViewModel — Save / Write split

- [ ] Inject `saveToSpoolman: SaveToSpoolmanUseCase` into `MainViewModel`.
- [ ] Split today's `onWriteTapped` (`MainViewModel.kt:362-425`) into:
  - `fun onSaveTapped()` — gates: `canSave.value == true`. Build `SaveToSpoolmanInput` from current state (drop NFC fields). Launch `saveToSpoolman.invoke(...)` in `viewModelScope`. Apply `applySaveResult(...)` (new private fn).
  - `fun onWriteTapped()` — gates: `canWriteAfterSave.value == true`. Build `CreateAndPairInput` with `spoolId = state.spoolman.selectedSpoolId!!`. Reuse today's vendor-tag dispatch logic (vendor + Spoolman → vendor UID-only pair; vendor + RawNoUrl → snackbar; else → standard). Standard path calls the now-shrunken `createAndPair.invoke(...)`.
- [ ] Add flows:
  - `val canSave: StateFlow<Boolean>` — true when:
    - `activeFlow == ActiveFlow.Idle` AND
    - form has changes vs prefilled state (existing logic from `canWrite` minus the spool-required check) OR a new-spool form is fully valid AND
    - `state.writeMode == WriteMode.Spoolman`. (RawNoUrl hides Save entirely.)
  - `val canWrite: StateFlow<Boolean>` — keep today's surface but rename intent to "Write-tappable":
    - `activeFlow == ActiveFlow.Idle` AND
    - `state.spoolman.selectedSpoolId != null` (must have a saved/selected target) AND
    - `state.observedTagKind != ObservedTagKind.Vendor || state.writeMode != WriteMode.RawNoUrl`. (Vendor + RawNoUrl is impossible — the Vendor path needs Spoolman.)
- [ ] `applySaveResult(result: SaveToSpoolmanResult)`:
  - `Success.Saved(spoolId, isNewSpool)` → auto-select spool in dropdown (`spoolman.selectedSpoolId = spoolId`); pin `form.selectedSpoolId = spoolId`; set prefilled-snapshots from now-current form fields (so a follow-up Save is a no-op until user edits again); snackbar `"Saved spool #$spoolId. Tap Write to pair a tag."` (or `"Updated spool #$spoolId."` if `!isNewSpool`).
  - `Success.NoChanges` → no-op visually (button was greyed; this only fires on race). Log only.
  - `Failed(outcome)` → snackbar `humanReadable(outcome)`. Activity flow stays Idle (no NFC was armed).
  - `UrlNotConfigured` → snackbar `"Configure Spoolman in Settings."` (matches today's RawNoUrl vendor copy).
- [ ] `onWriteTapped` no longer dispatches Save — it assumes a saved/selected spool. Vendor + Spoolman path stays (vendor UID-only). RawNoUrl path stays (rawWrite use case — no Save concept).

**Verify**: New `MainViewModelSaveTapTest` passes; existing `MainViewModelTest` cases compile after the split.

### §7. ViewModel — Weight method handlers

- [ ] Add `fun onWeightMethodPicked(method: WeightMethod)`:
  - Update `state.form.weightMethod = method`.
  - Recompute the inactive method's value at display time (in MoreDetailsExpander), not in state — keeps state minimal.
- [ ] Replace today's `onMeasuredWeightChanged` (lines 590-608):
  - Empty input → null override on the field that's currently active.
  - Numeric → set the *active* method's underlying field. If active is Remaining, write `remainingWeightG`. If active is Measured, the measured value is "what scale reads"; convert internally to remaining via `measured - emptySpoolWeightG` *but only if emptySpoolWeightG is set*. Otherwise stash the user's measured value in a new transient `_measuredEntry: MutableStateFlow<Float?>` and don't commit until empty-spool resolves.
  - **Decision**: store `measuredEntry` as a transient state alongside `weightMethod = Measured`. When user switches active to Remaining, drop the transient. When emptySpool gets set later, commit `remainingWeightG = measuredEntry - emptySpool` automatically. No silent keystroke swallow.
- [ ] `canSave` includes a "weight is committable" check: when `weightMethod == Measured` and emptySpool is null and measuredEntry is set, Save knows to derive remaining at submit time (use case already accepts `remainingWeightG` only; ViewModel resolves before building input).

**Verify**: New `MainViewModelWeightMethodTest` covers 4 cases (active=Remaining + edit, active=Measured + edit-with-empty, active=Measured + edit-without-empty + later-empty-set, switch active mid-edit).

### §8. UI — MainScreen reshape

- [ ] Wrap today's three Cards (Spoolman / FilamentForm / MoreDetailsExpander) in a single outer Card. Inner Cards drop to `elevation = 0.dp` + `border = BorderStroke(1.dp, surfaceVariant)` OR become `Surface` — pick whichever recomposes cheapest. Outer Card stays at elevation 5.dp.
- [ ] Move `SaveAndWriteButton` (today: lines 277-287) inside the outer Card, below MoreDetailsExpander, full-width. Rename component → `SaveToSpoolmanButton` (or keep `SaveAndWriteButton` and pass new label + handler — but the name will rot; rename).
- [ ] Drop `ReadFab` from `Scaffold.floatingActionButton`.
- [ ] Add `bottomBar = { MainBottomActions(...) }` to `Scaffold`. The composable hosts a Row with `[Read/Cancel] [Write]`, both `weight(1f)`. Use `Modifier.imePadding().navigationBarsPadding()` on the Row.
- [ ] `MainBottomActions` reads `state.activeFlow`, `state.observedTagKind`, `state.spoolman.selectedSpoolId`, `canWrite` to decide:
  - Read button: `"Read"` when Idle, `"Cancel"` when `ReadingForPair`. Tappable in both states.
  - Write button: `enabled = canWrite.value`. Caption (rendered above the button as supportingText, not Snackbar):
    - No selection → `"Pick a spool or hit Save first."`
    - Vendor tag observed → `"Vendor tag — can't be written."`
    - Else → no caption.
- [ ] `VendorTagHint` chip body copy moves into the Write button caption above. The chip itself stays (visual signal "vendor tag observed") but loses its action wording.
- [ ] Snackbar host stays in `Scaffold.snackbarHost`; bottom padding drops from 160.dp (today) to 16.dp because the bottom bar now occupies that real estate naturally.

**Verify**: `assembleDebug` ✅. Manual install on moto g stylus 2025 / Android 16 — 12-scenario checklist appended below.

### §9. Cancel for tag-waiting flows (Read + Write NDEF)

Only the NFC-bound flows that wait for a tag get Cancel. HTTP-only flows stay disabled-while-in-flight without a separate Cancel surface (they're fast enough not to need one).

- [ ] `onReadTapped` becomes a toggle: if `state.activeFlow == ReadingForPair`, cancel the readJob + disarm + return to Idle (no snackbar); else start a fresh read.
- [ ] `onWriteTapped` becomes a toggle for tag-waiting Write states: if `state.activeFlow` is `WritingForPair` (standard NDEF) or `WritingRaw` (RawNoUrl NDEF), cancel the writeJob + disarm NFC + return to Idle (no snackbar). Else dispatch the normal Write entry path.
- [ ] `PairingVendorUidOnly` is **NOT** Cancel-toggleable — it's HTTP-only, no NFC arm in flight. Write button stays disabled for the (short) duration. If Spoolman hangs, the existing OkHttp timeout + outer 10 s `withTimeoutOrNull` safety net cover it.
- [ ] Both `readJob?.cancel()` and `writeJob?.cancel()` are sufficient to abort — `viewModelScope.launch` propagates cancellation through `withTimeoutOrNull` + the suspended `nfc.arm` / use-case body. No new cleanup needed beyond today's path.
- [ ] Bottom bar buttons compute label from `state.activeFlow`. Label flip should be observable as `StateFlow<Boolean>` (`isReadInFlight`, `isWriteCancellable`) for deterministic recomposition. `isWriteCancellable` = `activeFlow in {WritingForPair, WritingRaw}` (NOT `PairingVendorUidOnly`).
- [ ] No cancel-confirmation snackbar; bail is silent (explicit user action). The 10 s safety-net timeouts still fire `"No tag tapped. Try again."` if they lapse on their own (existing path).
- [ ] When Cancel is tapped in `WritingForPair` after the post-write UID PATCH already landed: the spool↔UID linkage stands; cancel only abandons the NDEF write retry half. This is correct — preserves the user's pairing intent through whatever Spoolman-side state was committed before they bailed.

**Verify**: New tests in `MainViewModelTest`:
- `onReadTapped` while `ReadingForPair` cancels and returns to Idle without snackbar.
- `onWriteTapped` while `WritingForPair` cancels writeJob + disarms NFC + returns to Idle without snackbar.
- `onWriteTapped` while `WritingRaw` cancels writeJob + disarms + returns to Idle.
- `onWriteTapped` while `PairingVendorUidOnly` is a no-op (button disabled in this state; assert `isWriteCancellable == false`).

### §10. RawNoUrl mode

- [ ] When `state.writeMode == WriteMode.RawNoUrl`:
  - Save button hidden (no Spoolman target). The `canSave` flow returns false; render the button conditionally.
  - Write button label changes to `"Write to NFC"` (matches today's lines 281-282 RawNoUrl branch).
  - `onWriteTapped` routes to `launchRawWrite` exactly as today's lines 388-391 — no Save concept required.

**Verify**: `MainViewModelRawWriteTest` passes after the split.

### §11. Pair-another flow (decision 10) + sheet Cancel

- [ ] `PairAnotherTagSheet` retained verbatim. Sheet's "Pair another" button still drives `WritingSecondTag` flow.
- [ ] Sheet's "Pair another" button flips to **Cancel** during `WritingSecondTag` (universal Cancel rule, §1.2 matrix). Tapping it cancels the second-tag writeJob + disarms NFC + returns the sheet to its prompt state (NOT Idle — the user is still inside the pair-another flow and may want to retry). Resolves F-7 part 2 + F-8 cleanly: the Cancel target lives on the same button that armed the listen, so the user is never told to "tap a button that has dismissed".
- [ ] Sheet's "Done" button stays as-is — it's a positive end-of-flow, not a cancel.
- [ ] After Cancel of `WritingSecondTag`: state transitions back to `PromptingPairAnother(spoolId, isVendorPair)` so the sheet keeps showing its title + Pair another / Done options. No snackbar.

**Verify**: `MainViewModelTwoTagTest` extended with:
- Sheet "Pair another" label flips to Cancel during `WritingSecondTag`.
- Tapping Cancel on `WritingSecondTag` returns to `PromptingPairAnother` (not Idle) without snackbar.
- Tapping Cancel disarms NFC.

### §12. F-8 tooltip fix

- [ ] When pair-another fails (`TwoTagResult.NfcFailed` / `Cancelled`), today's snackbar copy refers to "Pair another" — but the sheet has already dismissed. Update to:
  - `Cancelled (timeout)` → `"No second tag tapped. Tap Write to retry."` (Write is now the bottom-bar button, always visible).
  - `NfcFailed` → `"Couldn't write to second tag. Tap Write to retry."`

**Verify**: Snackbar copy assertions in `MainViewModelTwoTagTest`.

### §13. SettingsRepository — no change

The repository surface is unchanged; the U9 settings (URL, sort, theme, currency) are unaffected by U13.

### §14. Hilt + DI

- [ ] Register `SaveToSpoolmanUseCase` in the existing use-case Hilt module (probably `app/src/main/java/com/spoolpainter/app/di/UseCaseModule.kt` — verify path during Part 2).
- [ ] No new modules; no abstract bindings.

### §15. Brownfield invariants (per per-unit DoD)

- [ ] No `*_modified.kt` / `*_new.kt` / `*.bak` files left behind.
- [ ] No deprecated/dead code references (`canSubmit` may still be used by the canSave flow — keep it; rename misleading occurrences).
- [ ] No production-side references to `MoveOnBindUseCase.NoOp` (already verified clean in U6b).
- [ ] No `FormState` field that's been replaced gets left behind dead.
- [ ] Compose preview annotations on new + modified components still build.

### §16. Build matrix + smoke

- [ ] `compileDebugKotlin` ✅ (only pre-existing warnings)
- [ ] `testDebugUnitTest` ✅ — target **~412** total (today: 390; +14 SaveToSpoolmanUseCaseTest +6 MainViewModelSaveTapTest +4 MainViewModelWeightMethodTest +3 MainViewModelTwoTagTest (second-tag flip + Cancel-back-to-prompt + disarms-NFC) +4 MainViewModelTest Cancel cases (read, write-standard, write-raw, vendor-no-cancel) = +31; minus replaced cases in `MainViewModelMoreDetailsExpanderTest` (~−8) + replaced single read-cancel placeholder (~−1) = **net +22 ≈ 412**).
- [ ] `assembleDebug` ✅ — target ~64 MB (no library additions; expander/radio is hand-rolled with material3 already on classpath).
- [ ] `assembleRelease` ✅ — target ~7.0 MB (R8 minify; same proguard rules as today).
- [ ] `bundleRelease` ✅ — target ~7.7 MB.

### §17. Manual install gate (12 scenarios)

Run on moto g stylus 2025 / Android 16 (or current dev device). Cluster B + Cluster A user is the source of these decisions; verify they survived the implementation.

1. New form → fill Material/Brand/Color/Variant → Save → spool created in Spoolman + dropdown auto-selects it + snackbar fires. Tap Write → tag accepted → PromptingPairAnother sheet.
2. Pick existing spool → edit Variant → Save → patch landed (verify in Spoolman web) + snackbar `"Updated spool #N."`.
3. Pick existing spool → no edits → Save button greyed (decision 8).
4. Pick existing spool → radio defaults to last-used method (or Measured for new forms) → only the active field renders below. Edit it (active=Remaining) → Save → spool remaining_weight patched. Switch radio to Measured → Remaining input vanishes, Measured input renders with no value → enter a number with empty-spool already known → Save → spool remaining_weight = measured − empty.
5. Pick existing spool with no empty-spool set → switch radio to Measured → Measured field renders → type a number → field shows what user typed; supportingText absent (no empty-spool to anchor "Filament left"); transient measuredEntry held internally; set empty-spool → supportingText appears showing "Filament left: N g" → remaining auto-derives → Save patches both. No silent keystroke loss; user always sees the number they typed because the input field is the single source of truth for the active method.
6. Vendor tag tap (no spool selected) → chip shows "Vendor tag" + Write button caption "Vendor tag — can't be written." Save still tappable; Save with form filled creates spool + snackbar; Save again with vendor chip still visible + spool selected → Write button caption changes to "Vendor tag — can't be written." but Write button itself stays disabled (vendor pair handled differently — see scenario 7).
7. Vendor tag flow (Spoolman configured): tap vendor tag → chip + caption visible → fill form → Save → spool created → tap Write **(disabled — vendor)** ... fix: vendor + Spoolman should drive vendor-UID-only pair without NDEF. Locked decision: vendor pair fires from Save itself when chip is showing? **Re-locked during Part 2 — current FD says "Save creates Spoolman record, Write does NDEF". Vendor case has no NDEF, so "Write disabled" is correct, BUT we need a separate "Pair UID" affordance.** Resolve in Part 2 — scenario 7 outcome may flip to "Save with vendor chip = vendor UID-only pair (no NDEF)".
8. **Cancel — Read**: Tap Read → button flips to Cancel + spinner; Write button disabled. Tap Cancel → returns to Idle, no snackbar. Tap Read → 10s timeout → snackbar "No tag tapped. Try again."
8b. **Cancel — Write (standard NDEF)**: With saved spool selected, tap Write → button flips to Cancel + spinner; Read button disabled. Tap Cancel BEFORE the tag arrives → returns to Idle, no snackbar. Tap Write → tag arrives → standard PromptingPairAnother sheet.
8c. **Cancel — RawNoUrl Write**: No URL configured → tap Write → button flips to Cancel + spinner. Tap Cancel before tag → returns to Idle, no snackbar.
8d. **Vendor pair (no Cancel surface)**: Vendor chip visible + saved spool selected + tap Write → routes to vendor UID-only pair (per Q-U13-1 resolution). Both buttons stay disabled; no Cancel button rendered. Spoolman roundtrip completes in ~250ms typical → returns to Idle. If Spoolman hangs the 10s outer timeout fires.
9. **Pair-another sheet → second-tag-listening Cancel**: Pair-another tap → sheet "Pair another" button flips to Cancel + spinner. Tap Cancel → returns to PromptingPairAnother (sheet stays visible). Tap Pair another again → second tag accepted → "Both tags paired" snackbar.
10. RawNoUrl mode (no URL set in Settings): Save button hidden. Write button label "Write to NFC". Tap Write → tag accepted → form data written to NDEF (no Spoolman). Snackbar copy unchanged from today's RawWrite.
11. Stale form prefill guard: Pick existing spool → spool has remaining_weight=850 (printer firmware patches it after we loaded). Edit nothing → Save greyed. Edit Variant → Save → only variant patched (remaining_weight NOT clobbered to our stale 850).
12. Snapmaker U1 round-trip: full Save → Write → tap on U1 → openspool_tag_processor parses + spoollink resolves UID + Fluidd shows full spool data. (Same as U10 install gate scenario.)

> Update §17 #7 once vendor-pair-via-Save question is resolved during Part 2 Q&A.

---

## 4. Open questions for Part 2 Q&A

These are NOT pre-locked by Cluster B / Cluster A; raise them in Code Gen Part 2:

- **Q-U13-1**: Vendor + Spoolman + spool selected — does **Save** trigger the vendor UID-only pair (no NDEF), or does **Write** stay tappable for that case but route to vendor UID-only pair internally? Cluster B says "Write disabled with caption" but vendor pairing IS a write-equivalent action. Either:
  - **A**: Save triggers vendor UID-only pair when vendor chip is visible (Save = "commit Spoolman state + UID linkage", which subsumes vendor pair).
  - **B**: Write stays the only pair affordance; routes to vendor UID-only pair internally when vendor chip is visible. Caption changes to "Pairs UID only — no NDEF".
- **Q-U13-2**: Drop Gross radio option entirely or keep as third row? Plan §1.4 locks Drop; reconfirm during Part 2.
- **Q-U13-3**: Inner Cards (Spoolman / FilamentForm / MoreDetailsExpander) — drop to `Surface` with no border, drop to `elevation = 0` Card, or keep as visible Cards inside outer Card? Visual variation matters; user picks during Part 2 install-gate iteration.
- **Q-U13-4**: Save button label — `"Save to Spoolman"` (locked) vs shorter `"Save"`. Cluster B locked the verbose form; reconfirm.
- **Q-U13-5**: `onWriteTapped` enable gate — selectedSpoolId required. But what about the orphan-Read flow where user just read a blank tag and the form is filled but no spool exists? Cluster B says "hit Save first"; verify the user accepts that extra tap.

---

## 5. Validation against Cluster B + Cluster A locked decisions

| Decision (locked source)                                   | Plan section                                |
|------------------------------------------------------------|---------------------------------------------|
| 1. Two separate buttons, no combo                          | §1.1, §6                                    |
| 2. Save = Spoolman-only, no tag                            | §1.1, §2, §6                                |
| 3. Write = NFC-only                                        | §1.1, §3, §6                                |
| 4. Write disabled until form saved or spool selected       | §1.2, §6 (`canWrite` gate)                  |
| 5. Vendor-tag Write disabled with caption                  | §1.2, §8 (caption above button)             |
| 6. Save lives at bottom of outer Card                      | §1.3, §8                                    |
| 7. Save label: "Save to Spoolman"                          | §6, §8 (open Q-U13-4)                       |
| 8. Save enabled when form has changes vs prefilled state   | §6 (`canSave` flow)                         |
| 9. Save success: snackbar + form stays + dropdown auto-selects + Write enabled | §6 (`applySaveResult`)        |
| 10. Pair-another keeps PairAnotherTagSheet                 | §11                                         |
| 11. Read↔Cancel toggle with 10s safety net                 | §9                                          |
| **NEW 2026-06-06**: Cancel for tag-waiting flows (Read + NDEF Write); HTTP-only flows stay disabled-while-in-flight | §1.2 matrix, §9, §11 |
| Cluster A: weight radio (Spoolman parity)                  | §1.4, §4, §5, §7                            |
| Cluster A: locked filament weight gets caption             | §5                                          |

All 11 + 2 decisions traced to one or more plan sections.

---

## 6. Resume options

After this plan is approved, Code Gen Part 2 executes §1..§17 in order. Estimated effort: 1 long session (today's session is winding down — likely a fresh "aidlc continue" tomorrow).

Cluster D + F-7/F-8 (v2.0.3 release window) is independent; it can ship any time without waiting on U13.
