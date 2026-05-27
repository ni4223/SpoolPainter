# U6b — Domain Entities

**Stage**: CONSTRUCTION → Per-Unit Loop → Functional Design → Domain Entities (U6b)
**Unit**: U6b — Move-on-Bind + Two-Tag Flow
**Inputs**:
- `aidlc-docs/construction/plans/u6b-move-on-bind-two-tag-functional-design-plan.md` (Q-U6b-1..15 = picks above)
- U6a-shipped types: `CardUid`, `SpoolmanSpool`, `SpoolmanFilament`, `SpoolmanVendor`, `OpenSpoolPayload`, `OpenSpoolPayloadCodec`, `TagClassification`, `NfcIntent`, `NfcResult`, `SpoolmanOutcome`, `MainUiState`, `ActiveFlow`, `FormState`.

This document declares the new domain types U6b introduces and the modifications to existing types. Technology-agnostic; Kotlin shapes are illustrative.

---

## 1. New Sealed Types

### 1.1 `MoveOnBindUseCase.Outcome` (replaces U6a's single-variant placeholder)

```kotlin
sealed interface Outcome {
    /** UID is unowned, or already owned by the target spool. Caller proceeds. */
    data object Proceed : Outcome

    /** Confirmation issued + atomic move executed. UID is now on target. */
    data class Moved(val fromSpoolId: Int) : Outcome

    /** User cancelled the RepairConfirm sheet. No Spoolman mutation occurred. */
    data object Declined : Outcome

    /**
     * Move failed.
     * @param partiallyModifiedSpoolId — if non-null, the A-side remove succeeded but the
     *   B-side append failed. Caller MUST surface this id so the user can repair Spoolman
     *   state manually. Q-U6b-8 = no auto-rollback.
     */
    data class Failed(
        val reason: String,
        val partiallyModifiedSpoolId: Int?,
    ) : Outcome

    /** ≥2 spools currently claim the UID — data-integrity oddity. Refuse, surface owners. */
    data class AmbiguousOwnership(val currentOwners: List<SpoolmanSpool>) : Outcome
}
```

**Invariants**:
- `Moved.fromSpoolId` ≠ `targetSpoolId` (move-on-bind only fires when source ≠ target).
- `Failed.partiallyModifiedSpoolId == null` ⇒ no PATCH was successfully issued; `≠ null` ⇒ exactly one PATCH (the A-side remove) succeeded.
- `AmbiguousOwnership.currentOwners.size >= 2`.

### 1.2 `MoveOnBindConfirmer` (new boundary type — Q-U6b-4 single-call seam)

```kotlin
interface MoveOnBindConfirmer {
    /** Suspends until the user resolves the RepairConfirm sheet. */
    suspend fun confirm(other: SpoolmanSpool, targetSpoolId: Int, uid: CardUid): Boolean

    /** UI side observes this to know when to show the sheet. */
    val pendingRequest: StateFlow<RepairConfirmRequest?>

    /** UI side calls this from sheet result handlers. */
    fun submitResult(confirm: Boolean)
}

data class RepairConfirmRequest(
    val other: SpoolmanSpool,
    val targetSpoolId: Int,
    val uid: CardUid,
)
```

**Invariants** (Q-U6b-9 single in-flight):
- At most one `RepairConfirmRequest` is pending at any time.
- `confirm(...)` SHALL throw `IllegalStateException` if invoked while another request is pending.
- `submitResult(...)` is a no-op if no request is pending.

**Lifecycle** (Q-U6b-14): the impl `MoveOnBindConfirmerImpl` is Hilt-bound `@Singleton`.

### 1.3 `TwoTagUseCase.Result`

```kotlin
sealed interface TwoTagResult {
    sealed interface Success : TwoTagResult {
        data class SecondTagPaired(val spoolId: Int, val uid: CardUid) : Success
    }

    /** Second tag classified as Vendor (FR-4.7). Abort flow. */
    data class VendorTagRejected(val uid: CardUid) : TwoTagResult

    /** Write succeeded; verify-readback differed. */
    data class VerifyFailed(val uid: CardUid, val cause: String) : TwoTagResult

    /** Spoolman call failed (cache-miss fallback round-trip, append, or move-on-bind PATCH). */
    data class SpoolmanFailed(
        val uid: CardUid,
        val outcome: SpoolmanOutcome<*>,
    ) : TwoTagResult

    /** NFC layer error (timeout, tag lost, etc.). */
    data class NfcFailed(val uid: CardUid?, val reason: String) : TwoTagResult

    /** User cancelled (sheet dismissed) or timeout fired. */
    data class Cancelled(val reason: String) : TwoTagResult

    /** Move-on-bind partial-commit — surfaced separately so UI can show A-side spool id. */
    data class MoveOnBindPartial(
        val uid: CardUid,
        val partiallyModifiedSpoolId: Int,
        val reason: String,
    ) : TwoTagResult
}
```

### 1.4 `TwoTagUseCase` input

```kotlin
data class TwoTagInput(val spoolId: Int)  // Q-U6b-5: use-case re-derives payload internally.
```

---

## 2. New UI State Types

### 2.1 `ActiveFlow` extensions (additions to U6a's enum)

Three new variants extend the existing `MainUiState.activeFlow`:

```kotlin
sealed interface ActiveFlow {
    data object Idle : ActiveFlow
    data object ReadingForPair : ActiveFlow
    data object WritingForPair : ActiveFlow

    // U6b additions:
    data class PromptingPairAnother(val spoolId: Int) : ActiveFlow
    data class WritingSecondTag(val spoolId: Int) : ActiveFlow
    data class AwaitingRepairConfirmation(
        val uid: CardUid,
        val currentOwner: SpoolmanSpool,
        val targetSpoolId: Int,
    ) : ActiveFlow
}
```

**Invariant** (Q-U6b-15 single sheet): at most one of `PromptingPairAnother | AwaitingRepairConfirmation` is active at any time. `WritingSecondTag` blocks both sheets until terminal.

### 2.2 `RepairConfirmUiState` (replaces the U1 placeholder)

```kotlin
data class RepairConfirmUiState(
    val otherSpoolDisplay: String, // resolved name; falls back to "spool #${id}"
    val otherSpoolId: Int,
    val targetSpoolId: Int,
    val uid: CardUid,
    val visible: Boolean,
)
```

**Copy** (Q-U6b-7 concise variant):
- Title: "Re-pair this tag to the selected spool?"
- Body: "Currently on: ${otherSpoolDisplay}"
- Primary action: "Move it"
- Secondary action: "Cancel"

### 2.3 `PairAnotherTagUiState`

```kotlin
data class PairAnotherTagUiState(
    val spoolId: Int,
    val visible: Boolean,
)
```

**Copy**:
- Title: "Pair another tag with this spool?"
- Body: "We'll write the same data to the second tag and remember both."
- Primary action: "Pair another"
- Secondary action: "Done"

---

## 3. Existing Types — Modifications

### 3.1 `CreateAndPairUseCase` reorder (Q-U6b-1)

No type-shape change. Behavioural reorder only — see `business-rules.md` §1.1.

### 3.2 `MoveOnBindUseCase` impl class

Replaces U6a's `MoveOnBindUseCase.NoOp` Hilt binding:

```kotlin
class MoveOnBindUseCaseImpl @Inject constructor(
    private val spoolman: SpoolmanRepository,
    private val confirmer: MoveOnBindConfirmer,
) : MoveOnBindUseCase {
    override suspend fun invoke(uid: CardUid, targetSpoolId: Int): Outcome { /* see business-logic-model.md §1 */ }
}
```

`MoveOnBindUseCase.NoOp` is deleted; the Hilt module's `bindMoveOnBindUseCase(...)` parameter type flips from `NoOp` to `MoveOnBindUseCaseImpl`.

### 3.3 `RepairConfirmViewModel` (replaces placeholder)

```kotlin
@HiltViewModel
class RepairConfirmViewModel @Inject constructor(
    private val confirmer: MoveOnBindConfirmer,
) : ViewModel() {
    val uiState: StateFlow<RepairConfirmUiState>  // derived from confirmer.pendingRequest
    fun onConfirm()  // confirmer.submitResult(true)
    fun onDismiss()  // confirmer.submitResult(false)
}
```

### 3.4 `MainUiState` — derived predicates

Derived `canRead` / `canWrite` are extended to disable the FABs whenever `activeFlow` is one of the three new variants. No new field — the predicate is updated in `business-rules.md` §3.

---

## 4. Type Inventory (delta vs U6a)

| Kind | Name | Status |
|---|---|---|
| Sealed type | `MoveOnBindUseCase.Outcome` | **Replaces** U6a's single-variant `Proceed`-only placeholder |
| Interface | `MoveOnBindConfirmer` | **NEW** |
| Class | `MoveOnBindConfirmerImpl` | **NEW** (`@Singleton`) |
| Class | `MoveOnBindUseCaseImpl` | **NEW** (replaces `MoveOnBindUseCase.NoOp`) |
| Class | `MoveOnBindUseCase.NoOp` | **DELETED** |
| Class | `TwoTagUseCase` | **NEW** |
| Sealed type | `TwoTagResult` | **NEW** |
| Data class | `TwoTagInput` | **NEW** |
| Data class | `RepairConfirmRequest` | **NEW** |
| Sealed variants | `ActiveFlow.{PromptingPairAnother, WritingSecondTag, AwaitingRepairConfirmation}` | **NEW** (extend existing sealed type) |
| Data class | `RepairConfirmUiState` | **REPLACES** U1 placeholder |
| Data class | `PairAnotherTagUiState` | **NEW** |
| Compose | `RepairConfirmSheet` | **NEW** |
| Compose | `PairAnotherTagSheet` | **NEW** |
| ViewModel | `RepairConfirmViewModel` | **REPLACES** U1 placeholder |
| Hilt binding | `bindMoveOnBindUseCase` | **CHANGED** — binds `MoveOnBindUseCaseImpl` instead of `NoOp` |

No changes to `SpoolmanRepository` types (U3-Δ already shipped). No changes to `NfcRepository` types (U4 contract reused).
