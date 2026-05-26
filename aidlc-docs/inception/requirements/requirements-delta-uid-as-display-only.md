# SpoolPainter v2 — Requirements Delta: UID as Display-Only

**Status**: **Approved 2026-05-25** during U6a's Code Generation Part 2 in-flight iteration. Folds into U4 / U5 / U6a construction scope (no separate per-unit loop reopens — same shape as `requirements-delta-extra-fields.md`). v2.0 only.

---

## 1. Why this delta exists

`FormState.cardUid: CardUid?` was performing two unrelated jobs that conflicted in practice:

1. **Display** — populated by Read flow / ambient taps / dropdown selection so the UI could surface "what UID is the form currently associated with."
2. **Behavioural enforcement** — passed as `NfcIntent.Write.expectedUid` so `NfcRepository.runWriteThenVerify` would reject any tap that didn't match. The UID-mismatch reason text "wrong tag UID — expected X, got Y" came from this guard.

The two-tag flow (Read tag 1 → Save & Write tag 2 to the **same** spool, story S-6.1 / S-6.2) is a first-class v2.0 requirement. It was structurally **blocked** by enforcement (2): after Read, `cardUid = tag1`, so tapping tag 2 was rejected. The UI worked around it with a "clear cardUid on success" hack in `MainViewModel.applyWriteResult`, but that masked the mismatch rather than fixing it — and tag 2 still couldn't be written without first running `onSpoolSelected(null)` to manually reset.

Concretely, this delta is also surfaced by a **multi-UID display problem**: with `extra.card_uids` introduced in `requirements-delta-extra-fields.md` (FR-2-EXT.1), a Spoolman spool can hold N paired tags. `FormMapping.fromSpoolman` derives `form.cardUid` via `ExtraCardUidsCodec.decode(...).firstOrNull()` — so a multi-tag spool's UI surface shows only the first UID, silently misleading the user about which tags are bound. There is no "fix" for this within the single-`CardUid?` field shape; the display has to either surface the list (out of scope for v2.0) or surface nothing.

### What removed enforcement does NOT compromise

The `expectedUid` guard was originally added (U4 FD plan §2.4.1) as defence-in-depth against "user reads tag A, then accidentally taps tag B during the subsequent write." Re-examining the failure mode:

- The write itself is just bytes — no corruption.
- `appendCardUidToSpool` is called with `tappedUid` (the actually-tapped UID, captured from `NfcResult.Success`), so Spoolman state remains consistent regardless of what `expectedUid` was.
- The one real risk — same UID claimed by two spools simultaneously — is covered structurally by `MoveOnBindUseCase` (`requirements-delta-extra-fields.md` FR-2-EXT.6, U6b scope): on bind, find any other spool already holding the UID, PATCH-remove it from that other spool, append it to the target.

`expectedUid` enforcement therefore duplicates protection that move-on-bind already provides, while breaking a legitimate user flow (S-6.1 / S-6.2) that move-on-bind is *also* designed to support. Drop the enforcement.

## 2. Supersession

This delta does **not** supersede any top-level FR. Its targets are two implementation decisions:

- **U4 FD plan §2.4.1** (originally: "if `expectedUid != null` and tag UID ≠ `expectedUid` → emit Error('wrong tag UID — expected <hex>, got <hex>'). Do NOT advance.") — **superseded**. The guard is removed.
- **U5 FD § "UidRow"** (originally: render `Text("UID: <hex>")` above the dropdown when `state.form.cardUid != null`) — **superseded**. The composable is removed from `MainScreen`.

Both targets are strictly within v2.0 construction scope; v1.x is unaffected.

## 3. New / revised requirements

### FR-4-EXT.1 — UID-mismatch enforcement removed from write path

`NfcRepository.runWriteThenVerify` SHALL accept whichever tag the user taps, regardless of whether `NfcIntent.Write.expectedUid` is set. The "wrong tag UID — expected <hex>, got <hex>" error reason is **retired**.

The `NfcIntent.Write.expectedUid` field on the data class is **retained** for source-compatibility with existing call sites (U6a `CreateAndPairUseCase`, plus tests). All call sites SHALL pass `expectedUid = null`. Future cleanup (full removal from the type signature) is deferred to U10.

> Trace: targets U4 FD plan §2.4.1, §2.7.1 (error-reasons enum). Touches FR-4.5 (verify-mismatch path is unaffected — verify-fail still emits `NfcResult.Error("verify mismatch …")`). Does NOT touch FR-4.7 (vendor-tag protection): that gate runs after the deleted UID-match guard in `runWriteThenVerify` and is independent of `expectedUid`.

### FR-4-EXT.2 — Same-UID-on-two-spools is `MoveOnBindUseCase`'s responsibility, not the write path

The conflict scenario "user writes tag B to spool A, but tag B is already paired with spool C" SHALL be detected and resolved by `MoveOnBindUseCase` (per `requirements-delta-extra-fields.md` FR-2-EXT.6). The write path SHALL NOT pre-emptively reject the tap to avoid this conflict.

> Trace: explicitly reaffirms FR-2-EXT.6's role. No code change is required by this delta — it documents the design intent that **move-on-bind is the canonical conflict resolver**, replacing `expectedUid` enforcement as defence-in-depth.

### FR-4-EXT.3 — Post-write `cardUid` reflects the just-paired UID (display only)

After `CreateAndPairResult.Success.WrittenAndPaired`, `MainViewModel` SHALL set `form.cardUid = result.uid` (the actually-tapped, just-paired UID). Previously the field was nulled to escape the now-removed enforcement; with enforcement gone, nulling is unnecessary and `cardUid` reverts to its informational role.

> Trace: targets the U6a session-time hack (audit log fix #9 — "form clearing on Save & Write"). The hack is removed: the WrittenAndPaired branch keeps `cardUid` as the just-written UID and keeps `selectedSpoolId` as the just-paired spool, with no behavioural side-effects.

### FR-3-EXT.1 — `UidRow` composable removed from `MainScreen`

`MainScreen` SHALL NOT render a top-level UID display row. The `UidRow` composable SHALL be deleted from `MainScreen.kt`.

Two reasons (recorded for future readers):

1. With `extra.card_uids` (FR-2-EXT.1), spools may hold N paired tags. `form.cardUid` is a single-value field and surfaces only the first UID via `ExtraCardUidsCodec.decode(...).firstOrNull()`. A multi-tag spool would silently appear single-tag — actively misleading.
2. The hex string has no user-facing utility; the only audience was the developer during testing-track iteration. Surfacing it on the main screen costs vertical space without informing pairing decisions.

`form.cardUid` itself is **retained** in the data model — `CreateAndPairUseCase` still uses it as the diagnostic snapshot for `NfcFailed.uid` carry, and `FormMapping` still populates it for downstream consumers (Read flow result rendering, ambient-tap observation, dropdown selection). This delta is purely a **rendering** change.

> Trace: targets the U5 FD §2.5 ("UidRow") and the U5 code-gen plan checkbox 5.5 ("UidRow.kt"). U5 itself is unchanged in behaviour; only its main-screen rendering loses one composable.

### FR-3-EXT.2 — Future debug/diagnostic UID surface (placeholder)

If a UID display is wanted for debugging (long-press, settings-only, or `BuildConfig.DEBUG`-gated), the surface SHALL be added under U10 release-polish scope. v2.0 testing-track ships with no UID display; no requirement is created here.

> Trace: explicit non-goal for v2.0. Out-of-scope guard for U6a/U6b/U7/U8/U9.

## 4. Construction-unit deltas

### U4 — NFC Repository (DONE 2026-05-24)

**Status:** **Re-open under amendment** — same shape as the U2/U3/U5 amendments handled in `requirements-delta-extra-fields.md`. No separate per-unit loop reopens; folded into U6a's per-unit loop.

- **U4-Δ-1** — Delete the `expectedUid != null && raw.uid != intent.expectedUid` guard from `NfcRepository.runWriteThenVerify` (was at the top of the function, immediately before the vendor-tag check). Do not touch the `TagClassification.Vendor` block (FR-4.7 stays).
- **U4-Δ-2** — Update the U4 wrong-tag-UID test to assert the **opposite** behaviour: a Write with `expectedUid = CardUid("deadbeef")` against a tag with a *different* UID SHALL succeed, and the wrapper SHALL register a write call. Test name updates to "write with mismatched expected UID still writes (enforcement removed)."

> Test count impact: 0 net (one rejection-path test rewritten as a success-path test; same count). U4's other 11 write-verify tests are unaffected.

### U5 — Read-and-Pair Flow (DONE 2026-05-25)

**Status:** **Re-open under amendment** — UI-only change.

- **U5-Δ-3** (this delta; numbering follows the prior U5-Δ-1/Δ-2 from `requirements-delta-extra-fields.md`) — Delete the `UidRow` composable + its callsite in `MainScreen.MainScreenContent` Column. Update touched files: `MainScreen.kt`. No corresponding test changes (no test was asserting UidRow presence).

> Test count impact: 0.

### U6a — Create-and-Pair Flow (in flight)

**Status:** **In-flight amendment** — these are the changes that triggered this delta.

- **U6a-Δ-5** — `CreateAndPairUseCase`: drop the `val expectedUid: CardUid? = form.cardUid` derivation; the use case now passes `expectedUid = null` to `NfcIntent.Write` unconditionally. The conditional `moveOnBind.invoke(expectedUid, …) / moveOnBind.invoke(tappedUid, …)` collapses to a single `moveOnBind.invoke(tappedUid, spoolId)` call after the write succeeds (move-on-bind always operates on the actually-tapped UID).
- **U6a-Δ-6** — `MainViewModel.applyWriteResult.WrittenAndPaired`: replace `form = current.form.copy(cardUid = null, selectedSpoolId = result.spoolId)` with `form = current.form.copy(cardUid = result.uid, selectedSpoolId = result.spoolId)`. The "clear cardUid hack" is retired.
- **U6a-Δ-7** — Update `MainViewModelTest`'s `onWriteTapped existingSpool emitsSnackbarAndKeepsFormOnSuccess` and `onWriteTapped newSpool emitsSnackbarAndKeepsFormOnSuccess` to assert `form.cardUid == sampleUid` (the just-paired UID) instead of `assertNull(form.cardUid)`.

> Test count impact: 0 net (asserts adjusted, not added/removed).

### U6b — Move-on-Bind + Two-Tag Flow (pending)

- **U6b-Δ-2** (this delta; numbering follows U6b-Δ-1 from the extra-fields delta) — `MoveOnBindUseCase` impl reaffirmed as the canonical conflict-resolution layer per FR-4-EXT.2. No additional surface required; the algorithm in FR-2-EXT.6 already covers the case the deleted `expectedUid` guard used to handle.

### U7 / U8 / U9 — unchanged

The delta has no scope impact.

### U10 — Release polish (pending)

- **U10-Δ-1** — Optional debug/diagnostic UID surface (`BuildConfig.DEBUG`-gated long-press / settings-only) per FR-3-EXT.2. Open-ended; only added if testers request it.
- **U10-Δ-2** — Full removal of `NfcIntent.Write.expectedUid` field from the data class (after confirming no tests still construct `Write(... expectedUid = ...)`).

## 5. Migration policy

Pure code change. No Spoolman-side migration. v2.0 has not reached real testers; no on-device migration concerns.

## 6. Non-goals (explicit)

- **Multi-UID display in the main screen** — out of scope for v2.0. A future "Tag info" expandable / "Tags paired (N)" chip could surface the full list; that is a U10 polish question, not v2.0 scope.
- **Re-enabling `expectedUid` enforcement under any flag** — explicitly not pursued. Move-on-bind is the permanent design.

## 7. Trace summary

| Source | Replaces | New FR | U4 | U5 | U6a | U6b | U10 |
|---|---|---|---|---|---|---|---|
| U4 FD §2.4.1 | (impl decision) | FR-4-EXT.1 | Δ-1, Δ-2 | — | Δ-5 | — | Δ-2 (deferred) |
| `MoveOnBindUseCase` design intent | (clarifies FR-2-EXT.6) | FR-4-EXT.2 | — | — | Δ-5 | Δ-2 | — |
| U6a session-time hack #9 | (impl decision) | FR-4-EXT.3 | — | — | Δ-6, Δ-7 | — | — |
| U5 FD §2.5 / code-gen 5.5 | (impl decision) | FR-3-EXT.1 | — | Δ-3 | — | — | — |
| (placeholder) | — | FR-3-EXT.2 | — | — | — | — | Δ-1 (optional) |

## 8. Approval gate

Pending user approval. Once approved:

1. The U6a per-unit loop continues — no stage gate is re-posed (this is an in-flight Code Gen Part 2 amendment, not a new stage).
2. `aidlc-docs/aidlc-state.md` records this delta in its Current Status summary alongside the U6a entry.
3. `aidlc-docs/audit.md` gets a new top-level entry capturing the delta and its approval.
4. The close-out commit at the end of U6a bundles: U6a code + tests + AIDLC artefacts + the U2/U3/U5 amendment code + tests + the prior `requirements-delta-extra-fields.md` + **this delta**. (Same DoD #6 / §2.1 close-out commit shape.)
