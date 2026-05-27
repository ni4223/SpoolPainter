# U6b — Frontend Components

**Stage**: CONSTRUCTION → Per-Unit Loop → Functional Design → Frontend Components (U6b)
**Unit**: U6b — Move-on-Bind + Two-Tag Flow

This document specifies U6b's Compose surface — two new bottom sheets (`RepairConfirmSheet`, `PairAnotherTagSheet`) hosted by the existing `MainScreen`, plus the wiring back to `MainViewModel` and `RepairConfirmViewModel`. No new full-screen surface is introduced.

---

## 1. Component Hierarchy

```
MainScreen (existing)
├── ... (existing content from U5/U6a)
├── BottomSheetHost (NEW — single slot, gated by state.activeFlow)
│   ├── RepairConfirmSheet         (when state.activeFlow is AwaitingRepairConfirmation)
│   │     └── RepairConfirmContent
│   └── PairAnotherTagSheet        (when state.activeFlow is PromptingPairAnother)
│         └── PairAnotherTagContent
└── ... (existing FABs, snackbar host, banner host)
```

`BottomSheetHost` is a thin Compose helper (file: `ui/components/sheets/BottomSheetHost.kt`) that selects the sheet to render based on the `activeFlow` and forwards the dismiss callback. Single sheet at a time per Q-U6b-15.

---

## 2. `RepairConfirmSheet`

**File**: `app/src/main/java/com/spoolpainter/app/ui/components/sheets/RepairConfirmSheet.kt`

### Props

```kotlin
@Composable
fun RepairConfirmSheet(
    state: RepairConfirmUiState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
)
```

`state` is the UI projection of `MoveOnBindConfirmer.pendingRequest` derived in `RepairConfirmViewModel`.

### State definition (replaces U1 placeholder)

```kotlin
data class RepairConfirmUiState(
    val otherSpoolDisplay: String,
    val otherSpoolId: Int,
    val targetSpoolId: Int,
    val uid: CardUid,
    val visible: Boolean,
)
```

`otherSpoolDisplay` resolution rule:
- If `other.filament?.vendor?.name` is non-blank: `"${vendor.name} ${filament.material} ${filament.colorHex} #${other.id}"`.
- Else if `other.filament?.material` is non-blank: `"${filament.material} #${other.id}"`.
- Else: `"spool #${other.id}"`.

(The display fallback chain mirrors v1/v6a's existing dropdown label format, so the user recognises the spool without needing to switch screens.)

### Visual / interaction (Q-U6b-7 = B concise)

```
┌──────────────────────────────────────────────┐
│  Re-pair this tag to the selected spool?     │  ← Material 3 title-medium
│                                              │
│  Currently on: Polymaker PLA Matte #42       │  ← body-medium
│                                              │
│       ┌──────────┐    ┌────────────┐         │
│       │ Cancel   │    │  Move it   │         │  ← TextButton + FilledTonalButton
│       └──────────┘    └────────────┘         │
└──────────────────────────────────────────────┘
```

Layout: `Column` with `Modifier.padding(24.dp)`; title + body stacked; button row right-aligned with 8.dp spacing. Uses `MaterialTheme.colorScheme.surfaceContainerHigh` from `ui/theme/Theme.kt`.

### Behaviour

| Trigger | Outcome |
|---|---|
| Primary button "Move it" tap | `onConfirm()` → `RepairConfirmViewModel.onConfirm()` → `MoveOnBindConfirmer.submitResult(true)` |
| Secondary button "Cancel" tap | `onDismiss()` → `RepairConfirmViewModel.onDismiss()` → `MoveOnBindConfirmer.submitResult(false)` |
| Scrim tap / back-button / drag-down | Same as Cancel (BR-U6b-UI-2) |
| `state.visible == false` | Sheet hidden / not composed |

### Test surface

- `RepairConfirmViewModelTest`:
  - `uiState` projection from `confirmer.pendingRequest` non-null emission carries the correct `otherSpoolDisplay` / `otherSpoolId` / `targetSpoolId` / `uid`.
  - `onConfirm()` calls `confirmer.submitResult(true)` exactly once.
  - `onDismiss()` calls `confirmer.submitResult(false)` exactly once.
  - Re-emission of a new request after a result was submitted produces a fresh `RepairConfirmUiState` with the new payload.

(No Compose UI test required — `[manual]` AC bullets per S-5.2 are covered at U6 milestone install gate.)

---

## 3. `PairAnotherTagSheet`

**File**: `app/src/main/java/com/spoolpainter/app/ui/components/sheets/PairAnotherTagSheet.kt`

### Props

```kotlin
@Composable
fun PairAnotherTagSheet(
    state: PairAnotherTagUiState,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
)
```

### State definition

```kotlin
data class PairAnotherTagUiState(
    val spoolId: Int,
    val visible: Boolean,
)
```

(`spoolId` is purely informational for tests — the sheet doesn't render it. The action handler calls a `MainViewModel` method that pulls the id from `state.activeFlow`.)

### Visual / interaction

```
┌────────────────────────────────────────────────────┐
│  Pair another tag with this spool?                 │  ← title-medium
│                                                    │
│  We'll write the same data to the second tag and   │  ← body-medium
│  remember both.                                    │
│                                                    │
│        ┌──────┐       ┌──────────────┐             │
│        │ Done │       │ Pair another │             │  ← TextButton + FilledTonalButton
│        └──────┘       └──────────────┘             │
└────────────────────────────────────────────────────┘
```

### Behaviour

| Trigger | Outcome |
|---|---|
| Primary button "Pair another" tap | `onAccept()` → `MainViewModel.onPairAnotherTagAccepted()` |
| Secondary button "Done" tap | `onDismiss()` → `MainViewModel.onPairAnotherTagDismissed()` |
| Scrim tap / back-button / drag-down | Same as Done |

### Test surface

- `MainViewModelTwoTagTest`:
  - First-pair `WrittenAndPaired` transitions `activeFlow` to `PromptingPairAnother(spoolId)`.
  - `onPairAnotherTagAccepted()` transitions to `WritingSecondTag(spoolId)` and launches the `TwoTagUseCase` invocation.
  - `onPairAnotherTagDismissed()` transitions to `Idle`, clears form, emits "Saved with one tag" snackbar.

---

## 4. `BottomSheetHost` integration

**File**: `app/src/main/java/com/spoolpainter/app/ui/components/sheets/BottomSheetHost.kt` (new)

### Signature

```kotlin
@Composable
fun BottomSheetHost(
    activeFlow: ActiveFlow,
    repairConfirmState: RepairConfirmUiState?,
    pairAnotherState: PairAnotherTagUiState?,
    onRepairConfirm: () -> Unit,
    onRepairDismiss: () -> Unit,
    onPairAnotherAccept: () -> Unit,
    onPairAnotherDismiss: () -> Unit,
)
```

Internally selects via:

```kotlin
when (activeFlow) {
    is ActiveFlow.AwaitingRepairConfirmation ->
        repairConfirmState?.let { RepairConfirmSheet(it, onRepairConfirm, onRepairDismiss) }
    is ActiveFlow.PromptingPairAnother ->
        pairAnotherState?.let { PairAnotherTagSheet(it, onPairAnotherAccept, onPairAnotherDismiss) }
    else -> Unit
}
```

`MainScreen` invokes `BottomSheetHost` once near the top of its `Box` content and passes through the four callbacks (which delegate to `mainViewModel.onRepairResult(...)` / `mainViewModel.onPairAnotherTag*()`).

---

## 5. `MainScreen` ↔ ViewModel wiring (additions)

### State observation

`MainScreen` already collects `mainViewModel.state` as `MainUiState`. U6b adds two derived values:

```kotlin
val repairConfirmState by repairConfirmViewModel.uiState.collectAsState()
val pairAnotherState by remember(state.activeFlow) {
    derivedStateOf {
        (state.activeFlow as? ActiveFlow.PromptingPairAnother)?.let {
            PairAnotherTagUiState(spoolId = it.spoolId, visible = true)
        }
    }
}
```

### Callback wiring

Five new callbacks routed from `MainScreen` to ViewModels:

| `MainScreen` callback | Routes to |
|---|---|
| `onRepairConfirm` | `repairConfirmViewModel.onConfirm()` |
| `onRepairDismiss` | `repairConfirmViewModel.onDismiss()` |
| `onPairAnotherAccept` | `mainViewModel.onPairAnotherTagAccepted()` |
| `onPairAnotherDismiss` | `mainViewModel.onPairAnotherTagDismissed()` |
| `onRepairResult` (internal) | `mainViewModel.onRepairResult(boolean)` — **only used by tests**; the production wiring goes through `MoveOnBindConfirmer` directly |

### FAB / form gating

The existing `canRead` / `canWrite` derivations (BR-U6b-MV-1) suppress the Read FAB and Save button while any of the three new `ActiveFlow` variants are active. No new disable-state UI required.

---

## 6. Form behaviour deltas

### Form clear on `Idle` transition (BR-U6b-MV-4 / -5)

Existing U6a behaviour: form is cleared on `WrittenAndPaired` success. With U6b, the cleared-form moment moves to:
- `applyWriteResult(WrittenAndPaired)` no longer clears immediately; it transitions to `PromptingPairAnother` and **defers** the clear until `Idle` is reached again.
- `Idle` reach-points that should clear:
  - `onPairAnotherTagDismissed()` — user said "Done".
  - `applyTwoTagResult(Success.SecondTagPaired)` — second pair succeeded.
- Failure paths in `applyTwoTagResult` (`VendorTagRejected`, `VerifyFailed`, `SpoolmanFailed`, `MoveOnBindPartial`, `NfcFailed`, `Cancelled`) transition to `Idle` **without** clearing the form, so the user can retry without re-typing. (Edge case: form was already cleared by a prior success — this is fine; defaults render.)

### Save button visibility

Save button continues to live at the bottom of `FilamentForm`. While `activeFlow !in {Idle}`, it disables (BR-U6b-MV-1).

---

## 7. Banner / snackbar surface

No new banner / snackbar layouts. Reuses existing `CustomSnackbar` host from U5 and `BannerState` host from U6a.

| Event | Snackbar copy |
|---|---|
| `WrittenAndPaired` (first pair) | `"Paired and written"` (existing U6a copy) |
| `Success.SecondTagPaired` | `"Both tags paired"` (NEW) |
| `onPairAnotherTagDismissed` | `"Saved with one tag"` (NEW) |
| `VendorTagRejected` (second tap) | `"Vendor tag — write blocked"` (NEW) |
| `Cancelled("timeout")` (second tag) | `"Second-tag pairing cancelled (timeout)"` (NEW) |
| `Cancelled("repair declined…")` (CP) | `"Tag write succeeded but pairing cancelled — UID still on spool ${A}"` (NEW) |
| `MoveOnBindPartial(uid, A, reason)` | banner: `"Partial state in Spoolman — UID was removed from spool #${A}; restore it manually if needed."` (NEW) |

---

## 8. Form validation rules

No new form validation rules. U6a's `FilamentForm.canSubmit` is unchanged.

---

## 9. API integration points

| Component | Calls |
|---|---|
| `RepairConfirmSheet` | none direct — drives `RepairConfirmViewModel` |
| `PairAnotherTagSheet` | none direct — drives `MainViewModel` |
| `RepairConfirmViewModel` | `MoveOnBindConfirmer.submitResult(...)` |
| `MainViewModel.onPairAnotherTagAccepted` | `TwoTagUseCase.invoke` → `SpoolmanRepository.{getSpool, getFilament, appendCardUidToSpool}` + `NfcRepository.{arm, state, lastSeenTag}` + `MoveOnBindUseCase.invoke` |
| `MainViewModel.onPairAnotherTagDismissed` | none external |
| `MoveOnBindUseCaseImpl` | `SpoolmanRepository.{findSpoolsByCardUid, removeCardUidFromSpool, appendCardUidToSpool}` + `MoveOnBindConfirmer.confirm` |

---

## 10. Accessibility / a11y

Sheets use Material 3 defaults: scrim contrast, focus management, semantic role for buttons. Both sheets are dismissible via the system back gesture (Material 3 `ModalBottomSheet` handles this). `contentDescription` on action buttons matches the visible label.

---

## 11. Out-of-scope surface

- No edits to `FilamentForm`, `MaterialPicker`, `BrandPicker`, `ColorPicker`, `TempPanel`, `SettingsScreen`, `SpoolPainterLogo`, `CustomSnackbar` — they remain U5/U6a-shipped.
- No light/dark theme tweaks — reuses U5/U6a `Theme.kt`.
- No string resource extraction — copy is inline (matches U6a's pattern; extraction deferred to U10 if it becomes a localisation requirement).
