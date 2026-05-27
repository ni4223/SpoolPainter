# U6b — Business Logic Model

**Stage**: CONSTRUCTION → Per-Unit Loop → Functional Design → Business Logic Model (U6b)
**Unit**: U6b — Move-on-Bind + Two-Tag Flow

This document encodes the executable logic of U6b's three new sequences as pseudo-code, together with the participating actors and the call-graph between them. References to BR-U6b-* / FR-* / S-* are normative and live in `business-rules.md` / `requirements.md` / `stories.md`.

Actors:
- **VM** — `MainViewModel`
- **CP** — `CreateAndPairUseCase` (modified)
- **MOB** — `MoveOnBindUseCase` (impl)
- **CFM** — `MoveOnBindConfirmer` (singleton)
- **T2** — `TwoTagUseCase` (new)
- **SR** — `SpoolmanRepository`
- **NR** — `NfcRepository`
- **UI** — Compose surface (`MainScreen` + sheet hosts)
- **RV** — `RepairConfirmViewModel`

---

## 1. Move-on-Bind sequence (BR-U6b-MOB-1..7)

```
MOB.invoke(uid, targetSpoolId) =
    matches := SR.findSpoolsByCardUid(uid)            # delta §6 / U3-Δ-2
    when matches:
        is SpoolmanOutcome.Success(list):
            when list.size:
                0                                     -> return Outcome.Proceed       # BR-U6b-MOB-2
                1 if list[0].id == targetSpoolId      -> return Outcome.Proceed
                1 if list[0].id != targetSpoolId      ->
                    other := list[0]
                    confirmed := CFM.confirm(other, targetSpoolId, uid)   # SUSPENDS
                    if !confirmed: return Outcome.Declined                # BR-U6b-MOB-3
                    rmv := SR.removeCardUidFromSpool(other.id, uid)       # BR-U6b-MOB-4 step 1
                    if rmv !is Success: return Outcome.Failed(rmv.reason, partiallyModifiedSpoolId = null)
                    apd := SR.appendCardUidToSpool(targetSpoolId, uid)    # BR-U6b-MOB-4 step 2
                    if apd !is Success: return Outcome.Failed(apd.reason, partiallyModifiedSpoolId = other.id)
                    return Outcome.Moved(fromSpoolId = other.id)
                _ /* >= 2 */                          -> return Outcome.AmbiguousOwnership(list)
        else /* Network/Http/Parse error */: return Outcome.Failed(matches.reason, null)
```

### Sequence diagram

```
VM           CP            MOB           CFM          SR             NR            UI/RV
 │ onWriteTapped()
 │──────────►│
 │           │ resolveSpool() / arm Write …
 │           │──────────────────────────────────────────►│
 │           │                                           │ (write+verify)
 │           │◄──────────────────────────────────────────│ Success(uid)
 │           │ MOB(uid, targetId)         <BR-U6b-CP-1>
 │           │───────────►│
 │           │            │ findSpoolsByCardUid(uid)
 │           │            │────────►│
 │           │            │◄────────│ Success([A])
 │           │            │ CFM.confirm(A, targetId, uid)
 │           │            │──────────►│
 │           │            │           │ pendingRequest := req
 │           │            │           │ (StateFlow emission)
 │           │            │           │   ─────────────────────────────► VM observer
 │           │            │           │                                     │ activeFlow = AwaitingRepairConfirmation
 │           │            │           │                                     │ (sheet renders)
 │           │            │           │                                     │ user taps "Move it"
 │           │            │           │                                     │ RV.onConfirm() → CFM.submitResult(true)
 │           │            │           │◄───────────────────────────────────│
 │           │            │◄──────────│ true
 │           │            │ remove(A, uid)
 │           │            │────────►│
 │           │            │◄────────│ Success
 │           │            │ append(targetId, uid)
 │           │            │────────►│
 │           │            │◄────────│ Success
 │           │            │ Outcome.Moved(A.id)
 │           │◄───────────│
 │           │ append(targetId, uid)         # BR-U6b-CP-2 — idempotent second call
 │           │────────►│
 │           │◄────────│ Success (no-op)
 │           │ result = WrittenAndPaired(...)
 │◄──────────│
 │ activeFlow = PromptingPairAnother(spoolId)    <BR-U6b-MV-2>
 │ snackbar "Paired and written"
```

Notes:
- The **second** `append` from `CreateAndPairUseCase` (after MOB returns `Moved`) is intentional. It is a defence-in-depth safety net: if the `Moved` outcome was emitted but a stale read in MOB caused the append to be skipped, this redundant call covers it. Idempotency (U3-Δ-3) makes the cost: zero PATCH on the wire.
- For `Proceed`, the second append is the **only** one — MOB never appended.

---

## 2. Two-Tag sequence (BR-U6b-T2-1..8 + BR-U6b-MV-2..5)

### 2.1 Top-level

```
VM.onPairAnotherTagAccepted() =
    spoolId := state.activeFlow.spoolId   # cast from PromptingPairAnother
    state.activeFlow := WritingSecondTag(spoolId)
    viewModelScope.launch {
        result := withTimeoutOrNull(15_000L) { T2.invoke(TwoTagInput(spoolId)) }
              ?: TwoTagResult.Cancelled("timeout")
        applyTwoTagResult(result)
    }

VM.onPairAnotherTagDismissed() =      # scrim or "Done"
    state.activeFlow := Idle
    state.form := FormState.empty()
    snackbar("Saved with one tag")
```

### 2.2 `T2.invoke(input)`

```
T2.invoke(TwoTagInput(spoolId)) =
    payload := derivePayload(spoolId)                # BR-U6b-T2-1
    if payload is Failure: return SpoolmanFailed(...)

    NR.arm(NfcIntent.Write(payload, expectedUid = null))
    nfcOutcome := NR.state.first { it terminal }     # BR-U6b-T2-2

    when nfcOutcome:
        Success(uid, classification):
            if uid.hex.isEmpty(): return NfcFailed(null, "zero-length UID — non-NFC-A tag?")
            if classification is Vendor: return VendorTagRejected(uid)   # BR-U6b-T2-3
            mob := MOB.invoke(uid, spoolId)                              # BR-U6b-T2-5
            when mob:
                Proceed | Moved        -> // continue
                Declined               -> return Cancelled("repair declined — UID still on spool ${fromSpoolId}")
                Failed(r, partial)     -> return if (partial != null) MoveOnBindPartial(uid, partial, r) else SpoolmanFailed(uid, ParseError(r))
                AmbiguousOwnership(_)  -> return SpoolmanFailed(uid, ParseError("ambiguous ownership"))
            apd := SR.appendCardUidToSpool(spoolId, uid)                 # BR-U6b-T2-6
            if apd !is Success: return SpoolmanFailed(uid, apd)
            return Success.SecondTagPaired(spoolId, uid)
        Error(reason):
            verifyMismatch := reason.contains("verify mismatch", true) || reason.contains("verification failed", true)
            return if (verifyMismatch) VerifyFailed(NR.lastSeenTag.value?.uid ?: CardUid.EMPTY, reason)
                   else                  NfcFailed(NR.lastSeenTag.value?.uid, reason)
```

### 2.3 `derivePayload(spoolId)` (BR-U6b-T2-1)

```
derivePayload(spoolId) =
    spool := SR.spools.value.firstOrNull { it.id == spoolId }
          ?: SR.getSpool(spoolId).asSuccessOr { return Failure(it) }
    filament := SR.filaments.value.firstOrNull { it.id == spool.filament?.id }
              ?: SR.getFilament(spool.filament?.id).asSuccessOr { return Failure(it) }
    vendor := SR.vendors.value.firstOrNull { it.id == filament.vendor?.id }
            ?: filament.vendor    # vendor sub-doc embedded in filament DTO
    OpenSpoolPayload(
        type      = filament.material ?: "PLA",
        colorHex  = filament.colorHex,
        brand     = vendor?.name ?: "Unknown",
        minTemp   = filament.settingsExtruderMinTemp?.toString() ?: "190",
        maxTemp   = filament.settingsExtruderMaxTemp?.toString() ?: "220",
        bedMinTemp= filament.settingsBedMinTemp?.toString(),
        bedMaxTemp= filament.settingsBedMaxTemp?.toString(),
        subtype   = (spool.extra?.get("variant") ?: filament.extra?.get("variant"))?.takeUnless { it.isBlank() } ?: "Basic",
        spoolId   = spool.id?.toString(),
    )
```

(`SR.getFilament` is a thin existing helper or added here as a one-liner around `api.getFilament(filamentId)` — see U10 doc-drift carry.)

---

## 3. `MoveOnBindConfirmer` impl

```
class MoveOnBindConfirmerImpl @Inject @Singleton constructor() : MoveOnBindConfirmer {
    private val _pendingRequest = MutableStateFlow<RepairConfirmRequest?>(null)
    private var pendingResult: CompletableDeferred<Boolean>? = null

    override val pendingRequest: StateFlow<RepairConfirmRequest?> = _pendingRequest

    override suspend fun confirm(other, targetSpoolId, uid): Boolean {
        check(_pendingRequest.value == null && pendingResult == null) {
            "MoveOnBindConfirmer: another request is already pending"     # BR-U6b-MOB-7
        }
        val deferred = CompletableDeferred<Boolean>()
        pendingResult = deferred
        _pendingRequest.value = RepairConfirmRequest(other, targetSpoolId, uid)
        return try {
            deferred.await()
        } finally {
            pendingResult = null
            _pendingRequest.value = null     # observers get null emission → restore prior activeFlow
        }
    }

    override fun submitResult(confirm: Boolean) {
        pendingResult?.complete(confirm)     # idempotent if already completed
    }
}
```

---

## 4. `MainViewModel` confirmer observer (BR-U6b-MV-6)

```
init {
    viewModelScope.launch {
        confirmer.pendingRequest.collect { req ->
            if (req != null) {
                priorActiveFlow = state.activeFlow                   # snapshot (likely WritingForPair or WritingSecondTag)
                state.activeFlow = AwaitingRepairConfirmation(req.uid, req.other, req.targetSpoolId)
            } else {
                state.activeFlow = priorActiveFlow ?: Idle           # use-case continuation will overwrite shortly
            }
        }
    }
}
```

---

## 5. End-to-end happy-path sequence

```
User taps Save (existing-spool, UID owned by another spool A):
  VM.onWriteTapped()
    → CP.invoke(snapshot)
        → resolveSpool() = Existing(B)
        → arm Write, await Success(tappedUid)
        → MOB.invoke(tappedUid, B)                                # BR-U6b-CP-1
            → findSpoolsByCardUid(tappedUid) = [A]
            → CFM.confirm(A, B, tappedUid)
                → pendingRequest emits → VM activeFlow = AwaitingRepairConfirmation
                → user taps "Move it" on RepairConfirmSheet
                → RV.onConfirm() → CFM.submitResult(true)
                → confirm() returns true
            → SR.removeCardUidFromSpool(A.id, tappedUid) = Success
            → SR.appendCardUidToSpool(B, tappedUid) = Success
            → return Outcome.Moved(A.id)
        → SR.appendCardUidToSpool(B, tappedUid) = Success (no-op idempotent)   # BR-U6b-CP-2
        → return WrittenAndPaired(B, tappedUid, isNewSpool=false)
    → applyWriteResult(WrittenAndPaired) → activeFlow = PromptingPairAnother(B)   # BR-U6b-MV-2
       (PairAnotherTagSheet renders)

User taps "Pair another":
  VM.onPairAnotherTagAccepted()
    → activeFlow = WritingSecondTag(B)
    → T2.invoke(TwoTagInput(B))
        → derivePayload(B) = identical bytes
        → arm Write, await Success(secondUid)
        → MOB.invoke(secondUid, B) = Proceed (assume unowned)
        → SR.appendCardUidToSpool(B, secondUid) = Success
        → return Success.SecondTagPaired(B, secondUid)
    → applyTwoTagResult(Success) → activeFlow = Idle; clear form; snackbar "Both tags paired"
```

---

## 6. Concurrency model

- **Single-thread ViewModelScope**: all state mutations and use-case invocations dispatch on `viewModelScope` (Main dispatcher). Coroutines suspend on NFC and Spoolman calls but state writes are serial.
- **Confirmer continuation**: `CompletableDeferred<Boolean>` straddles the suspension boundary between MOB (caller) and the UI sheet result handler. Both MOB and the sheet run on the Main dispatcher; no race on `pendingResult.complete(...)`.
- **`activeFlow` serialisation**: BR-U6b-MOB-7 + Q-U6b-15 enforce single sheet at a time. The `activeFlow` enum is the gate.
- **No background work**: no `Dispatchers.IO` orchestration introduced; Retrofit + OkHttp interceptors handle their own thread switching as in U3.

---

## 7. Failure injection matrix

| Failure | Where injected | Expected `MoveOnBindUseCase.Outcome` | Expected caller mapping |
|---|---|---|---|
| `findSpoolsByCardUid` HTTP 500 | SR | `Failed(reason, partial=null)` | CP → `SpoolmanFailed`; T2 → `SpoolmanFailed` |
| 2 spools own UID | SR returns 2 entries | `AmbiguousOwnership([A,B])` | CP → `SpoolmanFailed("ambiguous: …")`; T2 → same |
| Confirmer returns false | UI cancel | `Declined` | CP → `Cancelled("repair declined…")`; T2 → `Cancelled` |
| `removeCardUidFromSpool` HTTP 422 | SR | `Failed(reason, partial=null)` | CP → `SpoolmanFailed` |
| `appendCardUidToSpool` HTTP 500 (after remove succeeded) | SR | `Failed(reason, partial=A.id)` | CP → `SpoolmanFailed` w/ A.id; T2 → `MoveOnBindPartial(uid, A.id, reason)` |
| NFC verify mismatch on second tag | NR | n/a | T2 → `VerifyFailed(uid, cause)` |
| Vendor tag on second tap | NR classification | n/a | T2 → `VendorTagRejected(uid)` |
| Timeout on `T2.invoke` | VM `withTimeoutOrNull` | n/a | VM → `applyTwoTagResult(Cancelled("timeout"))` |
