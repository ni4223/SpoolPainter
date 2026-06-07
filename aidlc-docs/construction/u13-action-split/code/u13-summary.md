# U13 — Action Split (Save vs Write) — Code Generation Summary

**Date**: 2026-06-06
**Per-unit gate**: FD / NFR-R / NFR-D / Infra-D **SKIP** (Cluster B + Cluster A locked the design surface).
**Release window**: v2.1 (separate testing-track release).
**Plan**: `aidlc-docs/construction/plans/u13-action-split-code-generation-plan.md`

## What shipped

The single `Save & Write` button is split into:

- **Save to Spoolman** — HTTP-only; commits vendor + filament + spool records and any expander-driven patches. Lives at the bottom of the outer Card.
- **Write** — NFC-only; arms a tap, writes the OpenSpool NDEF payload, PATCHes the captured UID into `extra.card_uids`. Lives in a stationary bottom-bar [Read | Write] action row.

The bottom-bar buttons toggle to **Cancel** while a tag-waiting flow is in flight (Read + NDEF Write standard + NDEF Write raw). HTTP-only flows (Save, vendor UID-only pair) stay disabled-while-in-flight without a separate Cancel surface — ~250 ms typical Spoolman roundtrip.

The bidirectional Remaining + Measured weight row is replaced by a Spoolman-parity radio: top segmented row picks Remaining or Measured; only the active method's input field renders below — the inactive method's field is hidden entirely (saves vertical real estate; eliminates the silent-keystroke bug by construction since only one source of truth exists at a time).

## Q&A locked (Code Gen Part 2)

- **Q-U13-1 = A**: vendor + Spoolman + chip visible → **Save** routes to vendor UID-only pair (subsumes the pair on this path). Write button stays disabled with caption "Vendor tag — tap Save to pair."
- **Q-U13-2** = drop Gross radio (locked by plan §1.4).
- **Q-U13-3 = B**: inner Cards = elevation 0 + thin `surfaceVariant` border, inside the outer Card.
- **Q-U13-4 = A**: Save button label = "Save to Spoolman" (locked).
- **Q-U13-5 = A**: orphan-Read path follows Save → Write (no auto-Save inside Write).

## File inventory

### Created (5)

- `app/src/main/java/com/spoolpainter/app/domain/usecases/SaveToSpoolmanResult.kt` — sum-type for the new use case.
- `app/src/main/java/com/spoolpainter/app/domain/usecases/SaveToSpoolmanUseCase.kt` — Spoolman-only orchestration extracted from `CreateAndPairUseCase` steps 1 / 1a / 1b.
- `app/src/main/java/com/spoolpainter/app/ui/components/WeightMethodRadio.kt` — radio + single active field.
- `app/src/test/java/com/spoolpainter/app/support/FakeSaveToSpoolmanUseCase.kt` — test fake.
- `app/src/test/java/com/spoolpainter/app/domain/usecases/SaveToSpoolmanUseCaseTest.kt` — 8 cases for the Save use case.
- `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelSaveTapTest.kt` — 6 cases for `onSaveTapped`.
- `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelWeightMethodTest.kt` — 4 cases for the radio handlers.

(Test count fudge: plan said "3 new files"; reality is 3 prod + 4 test files.)

### Modified (production — 6)

- `MainViewModel.kt` — split `onWriteTapped` into `onSaveTapped` + `onWriteTapped`; add `canSave` + `isReadInFlight` + `isWriteCancellable` + `saveInFlight` flows; replace `onMeasuredWeightChanged` back-solve with the radio model (`onWeightMethodPicked` + `onActiveWeightChanged`); add Cancel toggles for tag-waiting flows; pair-another sheet Cancel returns to `PromptingPairAnother`; F-8 snackbar copy fix; joint-state copy dropped per §1.5.
- `MainUiState.kt` — `WeightMethod { Remaining, Measured }` enum; `FormState` gains `weightMethod` + `measuredEntry`.
- `MainScreen.kt` — outer Card wrapping three inner sections (each as elevation-0 Card with `BorderStroke(1.dp, surfaceVariant)`); drop ReadFab + `floatingActionButton` slot; add `bottomBar` Slot with `MainBottomActions` Row (Read + Write, each owning their Cancel toggle); vendor caption rendered above the Write button.
- `CreateAndPairUseCase.kt` — write-only: drop `resolveSpool` + variant + spool-scope patch + `lastResolvedOrphan`/`lastResolvedSpoolId` (moved to `SaveToSpoolmanUseCase`); `CreateAndPairInput` gains required `spoolId` + `isNewSpool`.
- `MoreDetailsExpander.kt` — replace bidirectional Remaining/Measured pair with `WeightMethodRadio`; signature gains `weightMethod` + `activeWeightValueG` + `onWeightMethodPicked` + `onActiveWeightChange`; locked-filament-weight supportingText on existing-spool.
- `FilamentForm.kt` — `FormChange.RemainingWeightChanged` + `MeasuredWeightChanged` removed; `WeightMethodPicked` + `ActiveWeightChanged` added (kept here for future expander-side wiring; current expander writes through the dedicated callback). `SaveAndWriteButton` renamed → `SaveToSpoolmanButton` with locked label.

### Modified (test — 12)

- `CreateAndPairUseCaseTest.kt` — rewritten as write-only: 11 cases covering append happy path / verify-failed / NFC-failed / move-on-bind. Save-side cases moved to `SaveToSpoolmanUseCaseTest`.
- `MainViewModelTest.kt`, `MainViewModelTwoTagTest.kt`, `MainViewModelMoreDetailsExpanderTest.kt`, `MainViewModelRawWriteTest.kt`, `MainViewModelRefreshTest.kt`, `MainViewModelBannerTest.kt`, `MainViewModelSortTest.kt`, `MainViewModelFilamentPickerTest.kt`, `MainViewModelCurrencyTest.kt` — added `saveToSpoolman` ctor arg.
- `MainViewModelTest.kt` happy-path tests split: caller calls `onSaveTapped()` then `onWriteTapped()`; new "no spool selected isNoOp" test; verify/Spoolman/NFC failure paths pre-select spool; new Cancel toggle tests for Read + standard Write; `isWriteCancellable` assertion for `PairingVendorUidOnly`.
- `MainViewModelMoreDetailsExpanderTest.kt` — bidirectional cases replaced with radio cases (active=Remaining + edit, active=Measured + edit-with-empty, active=Measured + edit-without-empty + later-empty-set, switch-method-drops-entry, negative-back-solve-skips).
- `MainViewModelRawWriteTest.kt` — vendor + RawNoUrl test reframed as "Write disabled" (canWrite false); vendor + Spoolman test reframed as "Save routes to vendorUidOnlyPair" per Q-U13-1=A.
- `MainViewModelFilamentPickerTest.kt` — filament-only Write test reframed as "Write gated until Save".

### Deleted

None.

## Build matrix

| Stage                       | Result        |
|-----------------------------|---------------|
| `compileDebugKotlin`        | ✅ (1 pre-existing warning at `MainViewModel.kt:346`) |
| `testDebugUnitTest`         | ✅ **403 / 403** (Δ +13 vs Cluster D's 390) |
| `assembleDebug`             | ✅ 64 MB        |
| `assembleRelease`           | ✅ **7.0 MB** (R8) |
| `bundleRelease`             | ✅ 7.7 MB AAB   |

**Test target was ~412**; landed at **403** — short by 9 because the existing `MoreDetailsExpander` cases compressed when the bidirectional pair collapsed to a single radio (the plan's "+22 net" overestimated; actual delta from the U9b 390 baseline is +13).

## Brownfield invariants

- ✅ No `*_modified.kt` / `*_new.kt` / `*.bak` files left behind.
- ✅ No production references to `MoveOnBindUseCase.NoOp`.
- ✅ No leftover `RemainingWeightChanged` / `MeasuredWeightChanged` FormChange variants in production.
- ✅ Compose preview annotations on new + modified components still build.

## Snackbar copy delta (§1.5)

| Path                                       | Old copy                                             | New copy                                              |
|--------------------------------------------|------------------------------------------------------|------------------------------------------------------|
| Save success (new spool)                   | (combo: "Saved to Spoolman. Tag write…")             | `"Saved spool #N. Tap Write to pair a tag."`         |
| Save success (existing spool — patched)    | (combo)                                              | `"Updated spool #N."`                                |
| Save failure (Spoolman)                    | `humanReadable(outcome)`                             | `humanReadable(outcome)` (unchanged)                 |
| Save UrlNotConfigured                      | n/a (Save was new)                                   | `"Configure Spoolman in Settings."`                  |
| Tag write VerifyFailed                     | `"Saved to Spoolman. Tag write failed. Try again."` | `"Tag write failed. Try again."`                     |
| Tag write NfcFailed                        | `"Saved to Spoolman. Tag write failed. Try again."` | `"Tag write failed. Try again."`                     |
| Pair-another `VerifyFailed` / `NfcFailed`  | `"Couldn't write to second tag. Try again."`        | `"Couldn't write to second tag. Tap Write to retry."` |
| Pair-another timeout / Cancelled           | `"No second tag tapped. Tap Pair another to retry."` | `"No second tag tapped. Tap Write to retry."`        |

## Manual install gate (deferred)

Per Q-T2=B, no U13 install gate. Manual verification will happen organically during v2.1 testing-track iteration. The 12-scenario checklist captured in plan §17 (covering Save / Write / radio / Cancel / RawNoUrl / sheet Cancel / Snapmaker U1 round-trip) lives in the plan for the v2.1 release window.

## Deferrals (next units)

- **F-4 erase tag completely** — v2.1+ alongside U11/U12.
- **F-15 multi-vendor decode** — U11/U12.
- **APK size review for v2.1** — release APK at 7.0 MB; no growth vs v2.0.3.

## What changed since the plan

- Plan said "3 new files"; reality is 3 prod + 4 test = 7 created.
- Test target ~412; landed 403. Δ from U9b baseline: +13 (plan overestimated `MoreDetailsExpander` test growth).
- Save use case ownership of `lastResolvedOrphan` / `lastResolvedSpoolId` (moved cleanly per §3 plan).
- Added `_saveInFlight` private flag + `saveInFlight: StateFlow<Boolean>` so concurrent Save taps coalesce (Save runs in `viewModelScope` without a screen-blocking `activeFlow` transition; the Save button's disabled state during the coroutine is the only feedback).
- `WeightMethodRadio` uses a horizontal RadioButton pair (not `SegmentedButton`) — read cleaner at the field's 1-line height.
- Inner Cards rendered via a small `InnerSectionCard` helper composable wrapping each section.
