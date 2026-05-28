# U7 — Business Rules

**Stage**: CONSTRUCTION → Functional Design (U7 — Side Modes)
**Source plan**: `aidlc-docs/construction/plans/u7-side-modes-functional-design-plan.md`
**Reflects**: Q-U7-1..15 outcomes (locked 2026-05-27) + D-U7-1..5

---

## 1. Mode selection rules

### BR-U7-1 — `WriteMode` derivation (D-U7-3)

`MainViewModel.writeMode: StateFlow<WriteMode>` SHALL derive
synchronously from `settings.spoolmanUrl` and `spoolman.connectivity`:

| Condition | Resulting mode |
|---|---|
| `url.isBlank()` | `WriteMode.RawNoUrl` |
| `url` set AND `connectivity == Unreachable` | `WriteMode.RawDisconnected` |
| `url` set AND `connectivity != Unreachable` | `WriteMode.Spoolman` |

The flow MUST recompute on every change to either input. No DataStore
persistence — derivation only.

### BR-U7-2 — Save-button label by mode (D-U7-5)

| WriteMode | Tag observed | Button label |
|---|---|---|
| `Spoolman` | none / Blank / OpenSpool | **"Save & Write"** (existing copy — unchanged) |
| `Spoolman` | Vendor | **"Save"** (no NDEF write happens — see [[BR-U7-7]]) |
| `RawNoUrl` | (any non-vendor) | **"Write to NFC"** (matches v1.7 button label) |
| `RawDisconnected` | (any non-vendor) | **"Write to NFC"** |
| `RawNoUrl` / `RawDisconnected` | Vendor | Button disabled — see [[BR-U7-9]] |

### BR-U7-3 — Banner copy by mode (D-U7-4)

A banner SHALL render at the top of the main screen when raw-write is
engaged.

| WriteMode | Banner copy |
|---|---|
| `Spoolman` | (banner hidden) |
| `RawNoUrl` | **"Writing tag only — Spoolman not configured"** |
| `RawDisconnected` | **"Writing tag only — not connected to Spoolman"** |

> **Note**: The banner used by U5/U6 for connectivity state remains the
> source of truth. U7 extends — does not replace — the existing banner
> derivation.

---

## 2. Save & Write dispatch rules

### BR-U7-4 — `onSaveAndWriteTapped` routing ([[Q-U7-10]] = A)

Single handler with internal branching. Pseudo-code:

```kotlin
fun onSaveAndWriteTapped() {
    val mode = writeMode.value
    val classification = state.value.observedTagKind

    when {
        // Raw-write paths (no Spoolman)
        mode is RawNoUrl || mode is RawDisconnected -> {
            if (classification == ObservedTagKind.Vendor) {
                // BR-U7-9 — refused
                emit(Snackbar("Vendor tag — connect Spoolman to pair."))
                return
            }
            launchRawWrite()
        }

        // Spoolman + vendor tag
        mode is Spoolman && classification == ObservedTagKind.Vendor -> {
            launchVendorUidOnlyPair()
        }

        // Spoolman + standard
        else -> {
            launchCreateAndPair()  // existing U6a path, unchanged
        }
    }
}
```

### BR-U7-5 — Pre-flight gating

The Save button SHALL be enabled only when `canWrite == true`. `canWrite`
remains as defined in U6a (form completeness gate). Mode + classification
do not bypass `canWrite`.

---

## 3. Raw-write rules ([[FR-4.8]])

### BR-U7-6 — Raw-write payload construction

`RawWriteUseCase` SHALL build an `OpenSpoolPayload` from `RawWriteInput`
with **`spoolId = null`** (per FR-4.8). Other fields use the same
construction logic as `CreateAndPairUseCase.makePayload` (deferred refactor
per [[Q-U7-4]] = B).

### BR-U7-7 — Vendor-tag rejection in raw-write

When `nfc.arm(NfcIntent.Write(...))` returns
`NfcResult.Error("vendor-tag protected (FR-4.7)")`,
`RawWriteUseCase` SHALL return `RawWriteResult.VendorTagRejected(uid)`.
String-match prefix `"vendor-tag"` per [[Q-U6b-11]] = B (carried over).

### BR-U7-8 — No Spoolman calls invariant

`RawWriteUseCase` SHALL never reference `SpoolmanRepository`. The
constructor injects only `NfcRepository`. Type system enforces the
invariant. **Test**: `RawWriteUseCaseTest` verifies the ctor signature.

---

## 4. Vendor UID-only pair rules ([[FR-4.9]])

### BR-U7-9 — Vendor + no-Spoolman precondition (D-U7-1, D-U7-2)

Vendor tag pairing requires Spoolman.

| WriteMode | Behaviour |
|---|---|
| `Spoolman` | Proceed to vendor flow ([[BR-U7-10]]). |
| `RawDisconnected` | Snackbar **"Spoolman not reachable — try again when connected."** Form preserved. UID retained on `form.cardUid`. |
| `RawNoUrl` | Snackbar **"Spoolman needed to save vendor tag — connect and try again."** Form preserved. UID retained on `form.cardUid`. |

### BR-U7-10 — Vendor flow happy paths ([[Q-U7-9]] = A)

`VendorUidOnlyPairUseCase` SHALL execute the Spoolman side of
create-and-pair, **omitting the NDEF write**. Two paths:

#### Existing-spool path (`form.selectedSpoolId != null`)

1. `moveOnBind.invoke(observedUid, selectedSpoolId)` — precheck per [[BR-U7-11]].
2. On `Outcome.Proceed | Moved` →
   `spoolman.appendCardUidToSpool(selectedSpoolId, observedUid)`
   (idempotent per `requirements-delta-extra-fields.md` §6).
3. On Spoolman success → `Success.UidPaired(selectedSpoolId, observedUid, isNewSpool = false)`.

#### New-spool path (`form.selectedSpoolId == null`)

1. `moveOnBind.invoke(observedUid, /* targetSpoolId = sentinel */ -1)`
   — precheck **before** the new spool exists. The use-case re-invokes
   `moveOnBind` with the actual `newSpoolId` after the POST so
   `extra.card_uids` lands on it. *(Implementation detail: open in §5 of
   `business-logic-model.md`.)*
2. `spoolman.createSpoolForNewFilament(...)` — same `NewFilamentRequest`
   plumbing as `CreateAndPairUseCase`.
3. After POST → `spoolman.appendCardUidToSpool(newSpoolId, observedUid)`.
4. On all-success → `Success.UidPaired(newSpoolId, observedUid, isNewSpool = true)`.

### BR-U7-11 — Move-on-bind precheck ([[Q-U7-8]] reframe)

For both paths, move-on-bind precheck runs **before** the spool is
mutated to carry the UID:

- **Existing-spool path**: before the `appendCardUidToSpool(selectedSpoolId, …)` PATCH.
- **New-spool path**: before the `appendCardUidToSpool(newSpoolId, …)` PATCH (the create POST itself does not set `extra.card_uids`; the PATCH does, per the existing post-delta plumbing).

If `MoveOnBindUseCase` returns:

| Outcome | Vendor flow result |
|---|---|
| `Proceed` | Continue to PATCH. |
| `Moved(fromSpoolIds)` | Continue to PATCH. |
| `Declined` | Return `Cancelled("repair declined")`. |
| `Failed(reason, partial)` | Return `MoveOnBindPartial(uid, partial[0], reason)` if partial, else `SpoolmanFailed(uid, ParseError(IllegalStateException(reason)))`. |

### BR-U7-12 — No NDEF write invariant ([[Q-U7-7]] = A)

`VendorUidOnlyPairUseCase`'s constructor SHALL omit `NfcRepository`. Type
system forbids the use-case from issuing any NDEF intent. Compile-time
guarantee — no runtime check needed. **Test**:
`VendorUidOnlyPairUseCaseTest` verifies the ctor signature.

### BR-U7-13 — Post-success behaviour

After `Success.UidPaired`:

- Form values preserved per UI-06 polish.
- `form.selectedSpoolId` preserved per UI-10 polish (set to the just-paired
  spool id for the new-spool path).
- `_effects.trySend(UiEffect.ShowSnackbar("Tag paired"))` — wording to be
  finalised in code-gen; avoid the term "UID" per [[Q-U7-13]] reframe.
- Transition to `ActiveFlow.PromptingPairAnother(spoolId)` — same as U6a's
  create-and-pair success path. `PairAnotherTagSheet` fires.

---

## 5. Vendor classification UX rules ([[Q-U7-12]])

### BR-U7-14 — Vendor-tag chip

When `state.observedTagKind == Vendor`, the main screen SHALL render a
small `AssistChip` near the form header reading
**"Vendor tag — content unreadable"**.

The chip SHALL clear when:
- `form.cardUid` becomes null (form clear / reset), or
- `state.observedTagKind != Vendor` (user taps a non-vendor tag).

### BR-U7-15 — Helper text under chip

Below the chip (or as form-helper text near the top of `FilamentForm`),
the screen SHALL render **"Fill in the details below to link this tag."**

This text SHALL be visible only when `state.observedTagKind == Vendor`.

---

## 6. Acceptance criteria coverage matrix

| Story / FR | AC | Mapped rule |
|---|---|---|
| FR-4.7 | NDEF write blocked on vendor tag | [[BR-U7-7]] (raw-write rejection) + [[BR-U7-12]] (vendor flow type-level invariant) |
| FR-4.8 | Raw-write skips Spoolman entirely | [[BR-U7-8]] |
| FR-4.8 | Raw-write payload omits `spool_id` | [[BR-U7-6]] |
| FR-4.8 | Raw-write enforces vendor-tag protection | [[BR-U7-7]] |
| FR-4.8 | Raw-write enforces write-then-verify | NfcRepository contract (U4) — inherited |
| FR-4.9 | Vendor tag → empty form path at Read time | Existing U5 read-flow behaviour — Vendor classification routes through same UI as Blank/Unparseable. **No code changes in U7.** |
| FR-4.9 | Save & Write on vendor tag prompts opt-in | **Reframed** — chip + helper text + button copy ([[BR-U7-2]], [[BR-U7-14]], [[BR-U7-15]]) instead of modal sheet |
| FR-4.9 | Pair UID only path runs Spoolman chain only | [[BR-U7-10]] + [[BR-U7-12]] |
| FR-4.9 | Cancel preserves form state | N/A — no sheet to cancel; chip is passive |
| FR-4.9 | Move-on-bind still applies | [[BR-U7-11]] |
| FR-4.9 | UI signals tag is unreadable / no NDEF written | [[BR-U7-14]] + [[BR-U7-15]] + [[BR-U7-2]] (button label "Save", not "Save & Write") |
| S-4.6 | NDEF write boundary on vendor tag | [[BR-U7-7]] (raw) + [[BR-U7-12]] (Spoolman vendor flow) |
| S-4.7 | Mode is not a global preference | [[BR-U7-1]] — derived from settings + connectivity, not stored |
| S-4.7 | No "force-overwrite" toggle | No such control exists in U7 |
| S-4.8 | Modal opt-in sheet | **Reframed away** — see [[Q-U7-13]] outcome |
| S-4.8 | Pair UID only path executes FR-7 chain (new spool) | [[BR-U7-10]] new-spool path |
| S-4.8 | Pair UID only path executes PATCH (existing spool) | [[BR-U7-10]] existing-spool path |
| S-4.8 | Move-on-bind reuse | [[BR-U7-11]] |
| S-4.8 | Spoolman-side errors surface clearly | `SpoolmanFailed` propagated via `MainViewModel.humanReadable(outcome)` (existing plumbing) |

---

## 7. Acceptance reframes — explicit divergences from original spec

These deltas vs. the original FR-4.9 / S-4.8 spec are **intentional**,
captured in the FD plan §3 reframes, and approved as part of FD locking.

| Spec wording | Reframe |
|---|---|
| FR-4.9: "the app SHALL present a modal bottom sheet" | Replaced with main-screen `AssistChip` + helper text + Save-button copy change. Same intent (user knows they're doing UID-only pair before committing) without an extra modal. |
| FR-4.9: opt-in confirmation prompt copy | N/A — no prompt. Helper text under chip carries the explanation. |
| S-4.8 AC: "On Cancel ⇒ no NDEF write, no Spoolman call" | N/A — no sheet to cancel. Cancellation = user just doesn't tap Save. |
| FR-4.8: "side mode" | Reframed: not a "mode" the user toggles. The app *is* in raw-write mode whenever Spoolman is unavailable. No UI affordance to switch modes manually. |

These reframes will be folded back into `requirements.md` via a delta
document at code-gen time (or, if simpler, an inline note on FR-4.8 +
FR-4.9 referencing this design doc).
