# U6b — Business Rules

**Stage**: CONSTRUCTION → Per-Unit Loop → Functional Design → Business Rules (U6b)
**Unit**: U6b — Move-on-Bind + Two-Tag Flow
**Naming**: rule IDs use the prefix `BR-U6b-` followed by an area code (`MOB` move-on-bind, `T2` two-tag, `MV` MainViewModel, `UI` UI surface).

Source FRs: FR-5.1 / FR-5.2 / FR-6.1..6.4 / FR-13.2.

---

## 1. `CreateAndPairUseCase` — reorder (Q-U6b-1)

### BR-U6b-CP-1 — move-on-bind precheck precedes Spoolman append
After a successful NFC write yields a `tappedUid`, `CreateAndPairUseCase` SHALL invoke `MoveOnBindUseCase(tappedUid, targetSpoolId)` **before** calling `appendCardUidToSpool(targetSpoolId, tappedUid)`.

**Why**: today's call site at `CreateAndPairUseCase.kt:77` runs `moveOnBind.invoke(...)` after the append-call sequence — which contradicts S-5.1 ("before pairing UID with target spool B, app calls the same `findSpoolsByCardUid` lookup"). Move-on-bind's PATCH-pair MUST land *before* the append on B, otherwise the same UID exists transiently on both A and B.

### BR-U6b-CP-2 — outcome branching
Given `MoveOnBindUseCase.Outcome` from BR-U6b-CP-1:
- `Proceed` → continue to `appendCardUidToSpool(targetSpoolId, tappedUid)`. Result: `Success.WrittenAndPaired`.
- `Moved(fromSpoolId)` → continue to `appendCardUidToSpool(...)`. Result: `Success.WrittenAndPaired` (the move already removed UID from `fromSpoolId` and added to `targetSpoolId`; the append is the second half of the move and is idempotent — but we still call it explicitly so a `Moved` outcome plus a stale cache scenario doesn't leave the append undone).
  - **Note**: `MoveOnBindUseCase` already issued the append PATCH internally; the second call here is idempotent (delta §6 / U3-Δ-3).
- `Declined` → SKIP the append on B. Return `CreateAndPairResult.Cancelled("repair declined — UID still on spool ${fromSpoolId}")` so the UI can surface a clear snackbar (Q-U6b-2).
- `Failed(reason, partiallyModifiedSpoolId)` → return `CreateAndPairResult.SpoolmanFailed(uid, ...)` with the partial-modification id surfaced in the reason.
- `AmbiguousOwnership(owners)` → return `CreateAndPairResult.SpoolmanFailed(uid, ...)`; reason names all owner spool ids so the user can repair Spoolman manually.

### BR-U6b-CP-3 — non-blocking write
The reorder MUST NOT remove the NFC write step's place in the sequence: write happens before move-on-bind (UID is unknown until the tap). The write IS the source of truth for `tappedUid`.

---

## 2. `MoveOnBindUseCase` impl

### BR-U6b-MOB-1 — owner lookup
`MoveOnBindUseCase.invoke(uid, targetSpoolId)` SHALL call `spoolmanRepository.findSpoolsByCardUid(uid)` first. The lookup MUST use the bulk-fetch+filter shipped in U3-Δ-2 (no `lot_nr` filter; substring decode against `extra.card_uids`).

### BR-U6b-MOB-2 — branch on owner-set size
Given `findSpoolsByCardUid` `SpoolmanOutcome.Success(matches)`:
- `matches.isEmpty()` → return `Outcome.Proceed`. UID is unowned.
- `matches.size == 1 && matches.single().id == targetSpoolId` → return `Outcome.Proceed`. Idempotent re-pair into the same spool.
- `matches.size == 1 && matches.single().id != targetSpoolId` → drive the confirmation path (BR-U6b-MOB-3).
- `matches.size >= 2` → return `Outcome.AmbiguousOwnership(matches)`. Refuse (Q-U6b-3 = A); UI surfaces the conflict to the user.

### BR-U6b-MOB-3 — confirmation gate
If exactly one foreign owner is found, `MoveOnBindUseCase` SHALL call `confirmer.confirm(other = matches.single(), targetSpoolId, uid)` and SHALL await its boolean result. (Q-U6b-4 single-call seam.)
- `false` → return `Outcome.Declined`. **No** Spoolman PATCH issued.
- `true` → continue to BR-U6b-MOB-4.

### BR-U6b-MOB-4 — atomic remove-then-append
On confirmation `true`:
1. Call `spoolmanRepository.removeCardUidFromSpool(other.id, uid)`.
   - On non-`Success` outcome: return `Outcome.Failed(reason = outcome.errorMessage, partiallyModifiedSpoolId = null)`. No B-side PATCH attempted.
2. Call `spoolmanRepository.appendCardUidToSpool(targetSpoolId, uid)`.
   - On non-`Success` outcome: return `Outcome.Failed(reason = outcome.errorMessage, partiallyModifiedSpoolId = other.id)`. **No rollback** of step 1 (Q-U6b-8 = B).
3. Both succeeded → return `Outcome.Moved(fromSpoolId = other.id)`.

### BR-U6b-MOB-5 — partial-commit error message
`Outcome.Failed.reason` for `partiallyModifiedSpoolId != null` MUST include both:
- The Spoolman call name that failed (e.g., `appendCardUidToSpool`).
- The partially-modified spool id (so the user can fix it manually in the Spoolman web UI).

Example reason: `"appendCardUidToSpool failed (HTTP 500) — UID was already removed from spool #${A}; add it back manually if you cancel."` Caller maps this to a banner.

### BR-U6b-MOB-6 — multi-UID source preserved
`removeCardUidFromSpool(A, uid)` MUST remove **only** the matched `uid`; other entries in A's `extra.card_uids` (e.g., the second tag from a two-tag pair) MUST be preserved verbatim. This is satisfied by U3-Δ-4's full-extra read-modify-write — no new behaviour required. (S-5.2 AC + S-6.3 AC.)

### BR-U6b-MOB-7 — confirmer single in-flight
`MoveOnBindConfirmer.confirm(...)` SHALL throw `IllegalStateException` if invoked while another confirmation is pending (Q-U6b-9). The serialisation guarantee comes from `MainUiState.activeFlow` — only one flow can be in `WritingForPair` / `WritingSecondTag` at a time, and each can spawn at most one confirmation.

---

## 3. `MainViewModel` — flow gating

### BR-U6b-MV-1 — extended `canRead` / `canWrite` predicates
`canRead` and `canWrite` SHALL evaluate to `false` while `activeFlow` is any of:
- `ReadingForPair`, `WritingForPair` (existing U6a behaviour, unchanged)
- `PromptingPairAnother(_)` (NEW — sheet visible)
- `WritingSecondTag(_)` (NEW — second-tag write in flight)
- `AwaitingRepairConfirmation(_,_,_)` (NEW — RepairConfirm sheet visible)

### BR-U6b-MV-2 — `applyWriteResult(WrittenAndPaired)` transitions to `PromptingPairAnother`
On a successful first pair (`CreateAndPairResult.Success.WrittenAndPaired(spoolId, uid, isNewSpool)`), `MainViewModel` SHALL transition `activeFlow` from `WritingForPair` to `PromptingPairAnother(spoolId)` and SHALL emit a "Paired and written" snackbar. The `PairAnotherTagSheet` becomes visible.

### BR-U6b-MV-3 — `onPairAnotherTagAccepted()`
- Transition `activeFlow` to `WritingSecondTag(spoolId)`.
- Launch `viewModelScope.launch { twoTag.invoke(TwoTagInput(spoolId)) }` with the same 15s `withTimeoutOrNull` envelope as U6a's `writeTimeoutMs`.
- On terminal: route through `applyTwoTagResult(...)` (BR-U6b-MV-5).

### BR-U6b-MV-4 — `onPairAnotherTagDismissed()`
- Transition `activeFlow` to `Idle`.
- Clear the form (Q-U6b-13 = A): same logic as U6a's "first-pair-success form-clear" path — reset `FormState` to defaults; null `selectedSpoolId`; clear `_customMaterial` / `_customBrand`; null `cardUid`.
- Emit snackbar: "Saved with one tag".

### BR-U6b-MV-5 — `applyTwoTagResult(...)`
Mirrors `applyWriteResult` shape:
- `Success.SecondTagPaired(spoolId, uid)` → `activeFlow = Idle`; clear form; snackbar "Both tags paired".
- `VendorTagRejected(uid)` → `activeFlow = Idle`; snackbar "Vendor tag — write blocked"; form NOT cleared (lets user retry with a different tag — but per Q-U6b-13 first-pair already cleared the form, so this is moot in the typical flow).
- `VerifyFailed(uid, cause)` → `activeFlow = Idle`; snackbar "Second-tag verify failed: ${cause}".
- `SpoolmanFailed(uid, outcome)` → `activeFlow = Idle`; surface `BannerState.Error(outcome)` (existing U6a banner).
- `MoveOnBindPartial(uid, partial, reason)` → `activeFlow = Idle`; surface `BannerState.Error` with the partial-modification id explicit.
- `NfcFailed(uid, reason)` → `activeFlow = Idle`; snackbar "Tag write failed: ${reason}".
- `Cancelled(reason)` → `activeFlow = Idle`; snackbar "Second-tag pairing cancelled (${reason})".

### BR-U6b-MV-6 — observe `MoveOnBindConfirmer.pendingRequest`
`MainViewModel.init { ... }` SHALL `viewModelScope.launch` a collector on `confirmer.pendingRequest`. On a non-null emission, transition `activeFlow` to `AwaitingRepairConfirmation(uid = req.uid, currentOwner = req.other, targetSpoolId = req.targetSpoolId)`. On a `null` emission (request resolved), restore the prior `activeFlow` (the use-case in flight resumes — typically `WritingForPair` or `WritingSecondTag`).

### BR-U6b-MV-7 — `onRepairResult(confirm: Boolean)`
Single one-liner: `confirmer.submitResult(confirm)`. The ensuing flow-state transition is driven by BR-U6b-MV-6 + the use-case's continuation.

### BR-U6b-MV-8 — no persistence (FR-6.4)
Two-tag in-flight state (`PromptingPairAnother`, `WritingSecondTag`) SHALL NOT be written to DataStore. On process death, the flow drops; the user can re-tap either paired tag and FR-3 + FR-6.1 will offer the prompt again.

---

## 4. `TwoTagUseCase`

### BR-U6b-T2-1 — payload re-derivation
`TwoTagUseCase.invoke(TwoTagInput(spoolId))` SHALL:
1. Look up `spool` in `SpoolmanRepository.spools.value` (cache).
2. If absent: call `SpoolmanRepository.getSpool(spoolId)` (Q-U6b-10 = B fallback). On non-Success → return `TwoTagResult.SpoolmanFailed(uid = CardUid.EMPTY, outcome)`.
3. Look up `filament` in `SpoolmanRepository.filaments.value` matching `spool.filament.id` (the filament id is part of the spool DTO).
4. If filament absent: call `getFilament(filamentId)` round-trip (or surface the same fallback shape — out-of-scope detail; reuse existing repo helper if present, else add a thin one).
5. Look up `vendor` in `SpoolmanRepository.vendors.value` matching `filament.vendor.id`.
6. Build `OpenSpoolPayload` from `(filament, vendor, spool.extra.variant, spool.id)`. Same code path as `CreateAndPairUseCase.makePayload(...)` so byte-equality with the first tag is guaranteed.

### BR-U6b-T2-2 — second-tag write
After payload build, `TwoTagUseCase` SHALL `nfc.arm(NfcIntent.Write(payload, expectedUid = null))` and SHALL await the terminal `NfcResult` (same `awaitTerminalNfc` as U6a).

### BR-U6b-T2-3 — vendor-tag rejection (S-6.2 AC)
If the resulting `NfcResult.Error.reason` contains the substring `"vendor tag"` (case-insensitive — Q-U6b-11 string-match), `TwoTagUseCase` SHALL return `TwoTagResult.VendorTagRejected(uid = nfc.lastSeenTag.value?.uid ?: CardUid.EMPTY)`. No `appendCardUidToSpool` is invoked.

Alternatively (cleaner future): if `NfcResult.Success.classification is TagClassification.Vendor`, return `VendorTagRejected` directly. Both paths covered.

### BR-U6b-T2-4 — verify-fail / NFC-fail mapping
Mirrors U6a's `WriteResult` mapping:
- `NfcResult.Success(uid, classification)` with `uid.hex.isEmpty()` → `NfcFailed(null, "zero-length UID — non-NFC-A tag?")`.
- `NfcResult.Error.reason` containing "verify mismatch" / "verification failed" → `VerifyFailed(uid = lastSeenTag.uid, cause = reason)`.
- Other `NfcResult.Error` → `NfcFailed(uid, reason)`.

### BR-U6b-T2-5 — move-on-bind on second tag (S-6.3)
On a non-vendor `Success`, `TwoTagUseCase` SHALL call `moveOnBindUseCase.invoke(secondUid, spoolId)` and SHALL branch on the outcome identically to BR-U6b-CP-2 — except the Cancelled-equivalent is `TwoTagResult.Cancelled("repair declined — UID still on spool ${fromSpoolId}")`.

### BR-U6b-T2-6 — second-tag append
On `Proceed | Moved` from BR-U6b-T2-5, `TwoTagUseCase` SHALL `appendCardUidToSpool(spoolId, secondUid)`. Idempotent (delta §6).

### BR-U6b-T2-7 — vendor-tag protection re-check
If at any point classification flips to `Vendor` (e.g., the user presents a Bambu vendor tag on the second tap), the use-case MUST abort BEFORE issuing any Spoolman PATCH. (Already covered by BR-U6b-T2-3 since classification is part of the `Success` payload.)

### BR-U6b-T2-8 — timeout (Q-U6b-12)
Caller (`MainViewModel.onPairAnotherTagAccepted`) wraps the `twoTag.invoke(...)` call in `withTimeoutOrNull(15_000L)`. On null (timeout) → `applyTwoTagResult(TwoTagResult.Cancelled("timeout"))`.

---

## 5. UI Surface Rules

### BR-U6b-UI-1 — sheet host gating
`MainScreen` SHALL host both sheets via the same `ModalBottomSheet` slot, gated by `state.activeFlow`:
- `is PromptingPairAnother` → show `PairAnotherTagSheet`.
- `is AwaitingRepairConfirmation` → show `RepairConfirmSheet`.
- Other → no sheet visible.

Single sheet at a time per Q-U6b-15.

### BR-U6b-UI-2 — sheet dismiss-via-scrim
Both sheets treat scrim/back-button dismiss as the secondary action:
- `PairAnotherTagSheet` scrim-dismiss → `onPairAnotherTagDismissed()` (BR-U6b-MV-4).
- `RepairConfirmSheet` scrim-dismiss → `onRepairResult(false)` (BR-U6b-MV-7).

### BR-U6b-UI-3 — `RepairConfirmSheet` copy
- Title: `"Re-pair this tag to the selected spool?"`
- Body: `"Currently on: ${otherSpoolDisplay}"` — where `otherSpoolDisplay` is the resolved spool name (filament/vendor display) or `"spool #${otherSpoolId}"` fallback when the spool has no human-friendly name.
- Primary button: `"Move it"`.
- Secondary button: `"Cancel"`.

(Q-U6b-7 = B concise.)

### BR-U6b-UI-4 — `PairAnotherTagSheet` copy
- Title: `"Pair another tag with this spool?"`
- Body: `"We'll write the same data to the second tag and remember both."`
- Primary button: `"Pair another"`.
- Secondary button: `"Done"`.

### BR-U6b-UI-5 — banner / snackbar precedence
While `activeFlow != Idle`, the existing `BannerState` machinery from U6a is paused (no new banner emissions); snackbars surface progress / cancellation. On terminal transition back to `Idle`, queued banners flush per the existing U6a + U9-deferred behaviour.

---

## 6. Cross-cutting

### BR-U6b-X-1 — flow non-persistence (FR-6.4)
No `DataStore<Settings>` writes for any U6b state. The DataStore schema is unchanged.

### BR-U6b-X-2 — test-only seams
- `FakeMoveOnBindConfirmer` (test source set) returns deterministic `Boolean` (`true` / `false` / throws) — used by `MoveOnBindUseCaseTest` + `MainViewModelTwoTagTest`.
- `FakeTwoTagUseCase` extends `TwoTagUseCase` and overrides `invoke` to return canned `TwoTagResult` — used by `MainViewModelTwoTagTest`.

### BR-U6b-X-3 — Hilt graph
`RepositoryModule.bindMoveOnBindUseCase(...)` parameter changes from `MoveOnBindUseCase.NoOp` to `MoveOnBindUseCaseImpl`. A new `@Binds @Singleton` for `MoveOnBindConfirmer → MoveOnBindConfirmerImpl` is added in the same module.
