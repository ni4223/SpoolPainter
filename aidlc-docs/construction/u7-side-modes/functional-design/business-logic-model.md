# U7 — Business Logic Model

**Stage**: CONSTRUCTION → Functional Design (U7 — Side Modes)
**Source plan**: `aidlc-docs/construction/plans/u7-side-modes-functional-design-plan.md`
**Reflects**: Q-U7-1..15 outcomes (locked 2026-05-27) + D-U7-1..5

---

## 1. Mode-derivation flow

```mermaid
flowchart LR
    Settings([settings.spoolmanUrl]) --> D{url.isBlank?}
    D -- yes --> RawNoUrl[WriteMode.RawNoUrl]
    D -- no --> C{connectivity}
    C -- Unreachable --> RawDisc[WriteMode.RawDisconnected]
    C -- otherwise --> Sp[WriteMode.Spoolman]
```

`MainViewModel.writeMode` is recomputed every time either input changes.
No persistence, no toggle.

---

## 2. Save & Write dispatch

```mermaid
flowchart TD
    Tap([User taps Save & Write button]) --> CW{canWrite?}
    CW -- false --> NoOp[Button is disabled — no-op]
    CW -- true --> Mode{writeMode}

    Mode -- RawNoUrl/RawDisconnected --> ClsRaw{observedTagKind}
    ClsRaw -- Vendor --> RefuseRaw[Snackbar: 'Vendor tag — connect Spoolman to pair'<br/>Form preserved]
    ClsRaw -- Blank/OpenSpool/None --> Raw[launch RawWriteUseCase]

    Mode -- Spoolman --> ClsSp{observedTagKind}
    ClsSp -- Vendor --> Vendor[launch VendorUidOnlyPairUseCase]
    ClsSp -- Blank/OpenSpool/None --> CAP[launch CreateAndPairUseCase<br/>existing U6a path]
```

---

## 3. RawWriteUseCase happy + error paths

```mermaid
sequenceDiagram
    participant VM as MainViewModel
    participant UC as RawWriteUseCase
    participant NFC as NfcRepository

    VM->>UC: invoke(RawWriteInput)
    UC->>UC: payload = makePayload(form, spoolId = null)
    UC->>NFC: arm(NfcIntent.Write(payload, expectedUid = null))
    Note over UC,NFC: Wait for terminal NfcResult.<br/>15s timeout (writeTimeoutMs).

    alt NfcResult.Success(uid, classification)
        UC-->>VM: RawWriteResult.Success.Written(uid)
    else NfcResult.Error('vendor-tag protected ...')
        UC-->>VM: RawWriteResult.VendorTagRejected(lastSeenUid)
    else NfcResult.Error('verify mismatch')
        UC-->>VM: RawWriteResult.VerifyFailed(uid, cause)
    else NfcResult.Error(other)
        UC-->>VM: RawWriteResult.NfcFailed(uid?, reason)
    else timeout
        UC-->>VM: RawWriteResult.Cancelled('timeout')
    end
```

**Invariants**:
- ✅ Zero `SpoolmanRepository` calls (verified by ctor: no Spoolman dep injected).
- ✅ `payload.spoolId == null` (FR-4.8).
- ✅ FR-4.7 vendor protection inherited from `NfcRepository.runWriteThenVerify`.
- ✅ FR-4.5 verify inherited from `NfcRepository.runWriteThenVerify`.

---

## 4. VendorUidOnlyPairUseCase — existing-spool path

```mermaid
sequenceDiagram
    participant VM as MainViewModel
    participant UC as VendorUidOnlyPairUseCase
    participant MOB as MoveOnBindUseCase
    participant SR as SpoolmanRepository

    VM->>UC: invoke(VendorUidOnlyPairInput w/ selectedSpoolId)

    UC->>MOB: invoke(observedUid, selectedSpoolId)
    Note over MOB: precheck — confirmer sheet may fire.

    alt MoveOnBind.Outcome.Proceed | Moved
        UC->>SR: appendCardUidToSpool(selectedSpoolId, observedUid)
        alt SpoolmanOutcome.Success
            UC-->>VM: VendorUidOnlyPairResult.Success.UidPaired(<br/>selectedSpoolId, uid, isNewSpool=false)
        else http/network error
            UC-->>VM: VendorUidOnlyPairResult.SpoolmanFailed(uid, outcome)
        end
    else MoveOnBind.Outcome.Declined
        UC-->>VM: VendorUidOnlyPairResult.Cancelled('repair declined')
    else MoveOnBind.Outcome.Failed(reason, partial)
        Note over UC: partiallyModifiedSpoolIds may be non-empty
        UC-->>VM: VendorUidOnlyPairResult.MoveOnBindPartial(uid, partial[0], reason)
    end
```

**Invariants**:
- ✅ Zero `nfc.arm` calls (verified by ctor: no `NfcRepository` injected).
- ✅ Move-on-bind always before append (per [[BR-U7-11]]).

---

## 5. VendorUidOnlyPairUseCase — new-spool path

The new-spool path has a subtlety: move-on-bind runs *before* the spool
exists. The use-case orders calls so the source-spool detach happens
first, the new spool is created next, and the new spool finally claims
the UID via `appendCardUidToSpool`.

```mermaid
sequenceDiagram
    participant VM as MainViewModel
    participant UC as VendorUidOnlyPairUseCase
    participant MOB as MoveOnBindUseCase
    participant SR as SpoolmanRepository

    VM->>UC: invoke(VendorUidOnlyPairInput w/ selectedSpoolId = null)

    UC->>SR: findSpoolsByCardUid(observedUid)
    SR-->>UC: [] | [spoolA] | [spoolA, spoolB]

    alt 0 owners (UID is unowned)
        UC->>SR: createSpoolForNewFilament(...)
        SR-->>UC: SpoolmanOutcome.Success(newSpool)
        UC->>SR: appendCardUidToSpool(newSpool.id, observedUid)
        SR-->>UC: Success
        UC-->>VM: Success.UidPaired(newSpool.id, uid, isNewSpool=true)

    else 1+ owners (UID currently belongs to other spool(s))
        UC->>MOB: invoke(observedUid, /* sentinel */ -1)
        Note over MOB: Confirmer sheet fires: 'Re-pair this tag from {owner} to a new spool?'

        alt MoveOnBind.Outcome.Proceed | Moved
            Note over UC: Source spools have already had UID removed by MOB
            UC->>SR: createSpoolForNewFilament(...)
            SR-->>UC: SpoolmanOutcome.Success(newSpool)
            UC->>SR: appendCardUidToSpool(newSpool.id, observedUid)
            SR-->>UC: Success
            UC-->>VM: Success.UidPaired(newSpool.id, uid, isNewSpool=true)

        else Declined
            UC-->>VM: Cancelled('repair declined')
        else Failed(reason, partial)
            UC-->>VM: SpoolmanFailed(uid, ParseError(IllegalStateException(reason)))
        end
    end
```

> **Sentinel `targetSpoolId = -1` open question** for code-gen: the
> existing `MoveOnBindUseCase.invoke(uid, targetSpoolId)` signature
> assumes a real target id. For the new-spool path we don't have one yet.
> Two implementation options at code-gen time:
>
> 1. Pass `-1`; have `MoveOnBindUseCaseImpl` detect the sentinel and skip
>    the "already on target" idempotency check (since there is no target).
>    The user's confirmation is then "remove the UID from {owner}, period."
> 2. Add a new `MoveOnBindUseCase.detachFromAll(uid)` method that just
>    sweeps the source spools without a target.
>
> Defer pick to code-gen Part 1; both are sound. The plan default is
> option 1 because it requires no API addition.

---

## 6. Vendor-tag observed → UI signal

```mermaid
sequenceDiagram
    participant Tag as User taps tag
    participant NFC as NfcRepository
    participant VM as MainViewModel
    participant UI as MainScreen

    Tag->>NFC: tap (passive — Idle state)
    NFC->>NFC: classify → Vendor(reason)
    NFC-->>VM: nfc.lastSeenTag.value = TagBuffer(uid, Vendor(reason))
    VM->>VM: derive observedTagKind = Vendor
    VM->>VM: form.cardUid = uid
    VM-->>UI: state.observedTagKind = Vendor, form.cardUid = <uid>

    UI->>UI: render AssistChip 'Vendor tag — content unreadable'
    UI->>UI: render helper text 'Fill in the details below to link this tag.'
    UI->>UI: Save button label = 'Save'
```

No flow state transition — `activeFlow` stays `Idle`. The chip + button
copy are pure derived UI; the use-case launches only when the user taps
Save.

---

## 7. Vendor refusal — no Spoolman

```mermaid
sequenceDiagram
    participant U as User
    participant VM as MainViewModel
    participant UI as MainScreen

    Note over U,UI: writeMode = RawNoUrl or RawDisconnected<br/>observedTagKind = Vendor

    U->>UI: tap 'Save' (vendor button label)
    UI->>VM: onSaveAndWriteTapped()
    VM->>VM: BR-U7-9 short-circuit
    alt RawNoUrl
        VM-->>UI: Snackbar: 'Spoolman needed to save vendor tag — connect and try again.'
    else RawDisconnected
        VM-->>UI: Snackbar: 'Spoolman not reachable — try again when connected.'
    end
    Note over VM: form preserved.<br/>activeFlow stays Idle.<br/>No use-case invoked.
```

---

## 8. Pair another tag — vendor success branch

```mermaid
sequenceDiagram
    participant VM as MainViewModel
    participant UC as VendorUidOnlyPairUseCase
    participant UI as MainScreen

    UC-->>VM: Success.UidPaired(spoolId, uid, isNewSpool)
    VM->>VM: emit Snackbar 'Tag paired'
    VM->>VM: form preserved (UI-06)
    VM->>VM: form.selectedSpoolId = spoolId (UI-10)
    VM->>VM: activeFlow = PromptingPairAnother(spoolId)
    VM-->>UI: PairAnotherTagSheet shown

    Note over UI: User can:<br/>- tap 'Pair another' → TwoTagUseCase (existing U6b path)<br/>- tap 'Done' → activeFlow = Idle
```

> **Second-tag-vendor footgun, deferred**: if the user taps another vendor
> tag during the pair-another flow, `TwoTagUseCase` still emits
> `VendorTagRejected` (existing U6b behaviour). Routing that into the
> vendor flow is **out of U7 scope** per [[Q-U7-9]] note. User can fall
> back to manual: dismiss the sheet, tap the second vendor tag at idle,
> then Save.

---

## 9. State-machine summary

`ActiveFlow` transitions involving U7 use-cases:

```mermaid
stateDiagram-v2
    [*] --> Idle

    Idle --> WritingForPair: onSaveAndWriteTapped<br/>(Spoolman + non-vendor)
    Idle --> WritingRaw: onSaveAndWriteTapped<br/>(Raw + non-vendor)
    Idle --> PairingVendorUidOnly: onSaveAndWriteTapped<br/>(Spoolman + Vendor)

    WritingRaw --> Idle: applyRawWriteResult(*)
    PairingVendorUidOnly --> AwaitingRepairConfirmation: confirmer.pendingRequest emit
    AwaitingRepairConfirmation --> PairingVendorUidOnly: onRepairResult(true)
    AwaitingRepairConfirmation --> Idle: onRepairResult(false)<br/>+ Cancelled('repair declined')
    PairingVendorUidOnly --> PromptingPairAnother: applyVendorUidOnlyPairResult.Success
    PairingVendorUidOnly --> Idle: applyVendorUidOnlyPairResult.{Spoolman,MoveOnBindPartial,Cancelled}

    PromptingPairAnother --> WritingSecondTag: onPairAnotherTagAccepted
    PromptingPairAnother --> Idle: onPairAnotherTagDismissed

    WritingForPair --> PromptingPairAnother: applyWriteResult.Success
    WritingForPair --> AwaitingRepairConfirmation: confirmer.pendingRequest emit
    WritingForPair --> Idle: applyWriteResult.{Verify,Spoolman,Nfc,Cancelled}

    WritingSecondTag --> Idle: applyTwoTagResult(*)
```

**Pre-existing transitions** (from U6b) shown for completeness; U7 adds
`WritingRaw` and `PairingVendorUidOnly` and reuses
`AwaitingRepairConfirmation` + `PromptingPairAnother`.

---

## 10. Error precedence (snackbar copy)

When multiple error conditions stack, U7 dispatches in this priority:

| Priority | Condition | Snackbar |
|---|---|---|
| 1 | Vendor tag + RawNoUrl | "Spoolman needed to save vendor tag — connect and try again." |
| 2 | Vendor tag + RawDisconnected | "Spoolman not reachable — try again when connected." |
| 3 | RawWriteResult.VendorTagRejected | "Vendor tag — content unreadable" (informational; usually never reached because precondition rules above intercept) |
| 4 | RawWriteResult.VerifyFailed | (carry over from existing copy review at U10) |
| 5 | RawWriteResult.NfcFailed | (carry over) |
| 6 | RawWriteResult.Cancelled('timeout') | (carry over) |
| 7 | VendorUidOnlyPairResult.SpoolmanFailed | `humanReadable(outcome)` (existing plumbing) |
| 8 | VendorUidOnlyPairResult.MoveOnBindPartial | (carry over from U6b polish — UI-07 covers final wording) |
| 9 | VendorUidOnlyPairResult.Cancelled('repair declined') | suppressed per UI-12 (already shipped) |
