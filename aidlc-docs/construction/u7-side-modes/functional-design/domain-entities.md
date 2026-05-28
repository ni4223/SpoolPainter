# U7 — Domain Entities

**Stage**: CONSTRUCTION → Functional Design (U7 — Side Modes)
**Source plan**: `aidlc-docs/construction/plans/u7-side-modes-functional-design-plan.md`
**Reflects**: Q-U7-1..15 outcomes (locked 2026-05-27) + D-U7-1..5

---

## 1. Use cases

### 1.1 `RawWriteUseCase`

NDEF write to a blank or OpenSpool tag with **zero** Spoolman interaction.
Engages automatically when Spoolman is unavailable (URL blank OR
unreachable — see [[D-U7-3]]).

#### Input

```kotlin
data class RawWriteInput(
    val form: FormState,
    val resolvedMaterialName: String? = null,
    val newFilamentVendor: String = "Unknown",
)
```

| Field | Notes |
|---|---|
| `form` | Same form that drives all flows. UID, material, brand, color, temps, variant. |
| `resolvedMaterialName` | "Other → custom" material, pre-resolved by ViewModel. Falls back to `form.material?.name` when null. |
| `newFilamentVendor` | Brand string for payload (since there's no Spoolman vendor record). Falls back to form.brand?.name when blank. |

#### Result

```kotlin
sealed interface RawWriteResult {
    sealed interface Success : RawWriteResult {
        data class Written(val uid: CardUid) : Success
    }
    data class VendorTagRejected(val uid: CardUid) : RawWriteResult
    data class VerifyFailed(val uid: CardUid, val cause: String) : RawWriteResult
    data class NfcFailed(val uid: CardUid?, val reason: String) : RawWriteResult
    data class Cancelled(val reason: String) : RawWriteResult
}
```

| Variant | Meaning |
|---|---|
| `Success.Written(uid)` | NDEF write + verify OK; **no Spoolman calls were made**. |
| `VendorTagRejected(uid)` | Tag classified `Vendor` — write blocked per FR-4.7. |
| `VerifyFailed(uid, cause)` | Write succeeded but readback didn't match. |
| `NfcFailed(uid?, reason)` | Adapter throw, tag lifted, etc. |
| `Cancelled(reason)` | Timeout (15 s) or explicit cancel. |

#### Constructor dependencies

```kotlin
@Inject constructor(
    private val nfc: NfcRepository,
)
```

**No `SpoolmanRepository` dependency** — type-level invariant that this
use-case never touches Spoolman.

---

### 1.2 `VendorUidOnlyPairUseCase`

Spoolman pair flow for tags whose NDEF content can't be decoded
(`TagClassification.Vendor(reason)`). Treated as **"basic new-spool add minus the NDEF write"** per [[Q-U7-9]] — same plumbing as create-and-pair,
just skipping `nfc.arm(NfcIntent.Write(...))`.

#### Input

```kotlin
data class VendorUidOnlyPairInput(
    val form: FormState,
    val newFilamentName: String,
    val newFilamentVendor: String,
    val resolvedMaterialName: String? = null,
    val observedUid: CardUid,
)
```

| Field | Notes |
|---|---|
| `observedUid` | UID captured at Read time. Passed explicitly so the use-case is decoupled from `form.cardUid` drift. |

#### Result

```kotlin
sealed interface VendorUidOnlyPairResult {
    sealed interface Success : VendorUidOnlyPairResult {
        data class UidPaired(
            val spoolId: Int,
            val uid: CardUid,
            val isNewSpool: Boolean,
        ) : Success
    }
    data class SpoolmanFailed(val uid: CardUid, val outcome: SpoolmanOutcome<*>) : VendorUidOnlyPairResult
    data class Cancelled(val reason: String) : VendorUidOnlyPairResult
    data class MoveOnBindPartial(
        val uid: CardUid,
        val partiallyModifiedSpoolId: Int,
        val reason: String,
    ) : VendorUidOnlyPairResult
}
```

| Variant | Meaning |
|---|---|
| `Success.UidPaired(spoolId, uid, isNewSpool)` | Spoolman PATCH (existing-spool) or POST + `extra.card_uids` PATCH (new-spool) succeeded. **No NDEF write was issued.** |
| `SpoolmanFailed` | Any Spoolman call failed. |
| `Cancelled(reason)` | Move-on-bind RepairConfirmSheet was declined. |
| `MoveOnBindPartial` | A-side `removeCardUidFromSpool` succeeded but B-side `appendCardUidToSpool` (or POST) failed. |

#### Constructor dependencies

```kotlin
@Inject constructor(
    private val spoolman: SpoolmanRepository,
    private val moveOnBind: MoveOnBindUseCase,
)
```

**No `NfcRepository`** ([[Q-U7-7]] = A) — type system forbids the use-case
from arming a write. A future careless edit cannot reach `nfc.arm`.

---

## 2. State extensions

### 2.1 `MainUiState.ActiveFlow`

Two new variants for raw-write + one for vendor pairing in flight.

```kotlin
sealed interface ActiveFlow {
    // existing: Idle, ReadingForPair, WritingForPair,
    // PromptingPairAnother, WritingSecondTag, AwaitingRepairConfirmation

    /** Raw-write in flight (no Spoolman). */
    data object WritingRaw : ActiveFlow

    /** Vendor UID-only pair in flight (Spoolman PATCH/POST). */
    data object PairingVendorUidOnly : ActiveFlow
}
```

> **No `AwaitingVendorOptIn` variant** — original plan called for one but
> the reframe dropped the modal sheet. Vendor classification is signalled
> via the chip on the main screen, not via flow state.

### 2.2 New derived field — `MainUiState.writeMode`

Replaces the dropped `FormState.rawWriteMode` field. Derived from settings
+ connectivity each tick — never stored.

```kotlin
sealed interface WriteMode {
    /** Spoolman URL configured AND connectivity != Unreachable.
     *  Standard create-and-pair / vendor-uid-only flows. */
    data object Spoolman : WriteMode

    /** Spoolman URL not configured. Raw-write only. */
    data object RawNoUrl : WriteMode

    /** Spoolman URL set, but currently unreachable. Raw-write only;
     *  banner explains "not connected". */
    data object RawDisconnected : WriteMode
}
```

Derivation (in `MainViewModel`):

```kotlin
val writeMode: StateFlow<WriteMode> = combine(
    settings.settings.map { it.spoolmanUrl },
    spoolman.connectivity,
) { url, conn ->
    when {
        url.isBlank() -> WriteMode.RawNoUrl
        conn == ConnectivityState.Unreachable -> WriteMode.RawDisconnected
        else -> WriteMode.Spoolman
    }
}.stateIn(viewModelScope, SharingStarted.Eagerly, WriteMode.Spoolman)
```

### 2.3 New derived field — `MainUiState.observedTagKind`

Tracks the classification of the most recently seen tag so the chip /
helper text can render. Derived from `nfc.lastSeenTag`.

```kotlin
sealed interface ObservedTagKind {
    data object None : ObservedTagKind
    data object Blank : ObservedTagKind
    data object OpenSpool : ObservedTagKind
    data object Vendor : ObservedTagKind
}
```

Driven by `nfc.lastSeenTag.value?.classification` mapped through a
straight switch.

---

## 3. Removed entities (vs. original plan)

| Entity | Reason for removal |
|---|---|
| `VendorOptInUiState` | No opt-in sheet — design reframed to chip + helper text. |
| `VendorOptInViewModel` | No sheet → no VM. The U1 placeholder stays as a dead file (delete in U10 cleanup) or is removed in this unit's code-gen. |
| `VendorUidOnlyOptInSheet` Composable | No sheet. |
| `FormState.rawWriteMode` | No toggle. Raw-write derives from settings + connectivity (see `WriteMode`). |
| `ActiveFlow.AwaitingVendorOptIn` | No sheet. |
| `MainViewModel.onVendorOptInConfirmed/Cancelled` | No sheet. |
| `BottomSheetHost` AwaitingVendorOptIn branch | No sheet. |

---

## 4. Cross-references to existing entities (unchanged)

These are reused by U7 verbatim — no schema changes required.

| Entity | Source unit | U7 usage |
|---|---|---|
| `OpenSpoolPayload` | U2 | `RawWriteUseCase` builds with `spoolId = null`. |
| `CardUid`, `CardUidEncoding` | U2 | `observedUid` carrier in vendor flow; result types. |
| `TagClassification.Vendor` | U2 / U4 | Triggers chip; routes Save & Write to vendor use-case. |
| `NfcIntent.Write` | U4 | `RawWriteUseCase` calls `nfc.arm(...)`. Vendor use-case **never** does. |
| `NfcResult.{Success,Error}` | U4 | Awaited by raw-write only. |
| `SpoolmanRepository.findSpoolsByCardUid` / `appendCardUidToSpool` / `removeCardUidFromSpool` / `createSpoolForNewFilament` | U3 | Vendor use-case calls these (same plumbing as create-and-pair). |
| `MoveOnBindUseCase` + `MoveOnBindConfirmer` | U6b | Vendor use-case dispatches move-on-bind precheck through the same singleton confirmer; sheet routing already wired in `MainViewModel`. |
| `PairAnotherTagSheet` | U6b | Fires after vendor success per [[Q-U7-9]]. |
