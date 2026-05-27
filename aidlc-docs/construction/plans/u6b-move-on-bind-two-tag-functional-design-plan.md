# U6b — Functional Design Plan

**Stage**: CONSTRUCTION → Per-Unit Loop → Functional Design (U6b)
**Unit**: U6b — Move-on-Bind + Two-Tag Flow
**Source artefacts**:
- `aidlc-docs/inception/application-design/unit-of-work.md` §3-U6b
- `aidlc-docs/inception/application-design/components.md` §2.3 (`MoveOnBindUseCase`, `TwoTagUseCase`), §2.5 (`MainViewModel`), §2.8 (`RepairConfirmSheet`, `PairAnotherTagSheet`)
- `aidlc-docs/inception/application-design/component-methods.md` §6 (`MainViewModel`), §7 (`MainUiState`), §8 (Compose components)
- `aidlc-docs/inception/application-design/services.md` §5 (Move-on-bind), §7 (Two-tag flow)
- `aidlc-docs/inception/requirements/requirements.md` FR-5.1 / FR-5.2 / FR-6.1 / FR-6.2 / FR-6.3 / FR-6.4 / FR-13.2 (sheet UI)
- `aidlc-docs/inception/requirements/requirements-delta-extra-fields.md` (approved 2026-05-25 — defines FR-2-EXT.6 for `MoveOnBindUseCase` interface shape)
- `aidlc-docs/inception/requirements/requirements-delta-uid-as-display-only.md` (approved 2026-05-25)
- `aidlc-docs/inception/user-stories/stories.md` S-5.1 / S-5.2 / S-6.1 / S-6.2 / S-6.3 / S-6.4
- Existing code:
  - `app/src/main/java/com/spoolpainter/app/domain/usecases/MoveOnBindUseCase.kt` — interface + `NoOp` default (U6a)
  - `app/src/main/java/com/spoolpainter/app/domain/usecases/CreateAndPairUseCase.kt` — calls `moveOnBind.invoke(tappedUid, spoolId)` *after* write succeeds (line 77)
  - `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanRepository.kt:104` — `appendCardUidToSpool` (delta §6 idempotent)
  - `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanRepository.kt:127` — `removeCardUidFromSpool` (delta §6 idempotent)
  - `app/src/main/java/com/spoolpainter/app/ui/components/sheets/RepairConfirmViewModel.kt` — placeholder VM (U1 skeleton)

---

## 1. Unit Context (Step 1)

### 1.1 Scope (locked by Units Generation §3-U6b)

**Move-on-Bind**:
- **`MoveOnBindUseCase` impl** — replaces U6a's `NoOp` default. Pre-checks whether the tapped UID is currently owned by a *different* spool A (S-5.1); if so, surfaces a `RepairConfirmSheet` for user confirmation (S-5.2); on confirm, runs an atomic `removeCardUidFromSpool(A, uid)` followed by `appendCardUidToSpool(B, uid)`. Partial-commit handling (FR-5.2 / S-5.2) per Q11=A: on B-side failure, the A-side PATCH is **not** rolled back; surface a clear error naming which spool was partially modified.
- **`RepairConfirmViewModel` + `RepairConfirmSheet`** — bottom-sheet confirmation surface (FR-13.2). Shows other spool's display name (or id), Confirm/Cancel actions. Result flows back to the `MainViewModel` via a sheet-result event.
- **Restructure `CreateAndPairUseCase`** — current call site `moveOnBind.invoke(tappedUid, spoolId)` runs *after* the NFC write; the spec (S-5.1: "before pairing UID with target spool B") requires the precheck to occur **before** any spool-side mutation. Reorder so move-on-bind runs after the write succeeds (we know the UID), but **before** `appendCardUidToSpool(B, uid)`. **Q-U6b-1** confirms this ordering.

**Two-Tag**:
- **`TwoTagUseCase`** — second-tag pair flow (S-6.1..6.4):
  - Re-derives the OpenSpool payload from the spool's filament metadata (S-6.4) — same bytes the first tag carries.
  - Arms a Write with the re-derived payload and waits for the second tap.
  - Vendor-tag protection (S-6.2 AC) — second tag classified `Vendor` aborts the flow with an explicit error.
  - Move-on-bind on second tag (S-6.3) — reuses `MoveOnBindUseCase` for the second UID.
  - Appends `card_uid:<uid2>` to the same spool (`appendCardUidToSpool` idempotency from delta §6 keeps re-runs safe).
- **`PairAnotherTagSheet`** — bottom-sheet prompt (FR-6.1) shown after a successful first pairing. Two actions: "Pair another tag" / "Done". Per FR-6.1 the prompt is **optional**; user can dismiss without consequence.
- **`MainViewModel`** new handlers — `onPairAnotherTag()`, `onTwoTagResult(...)`, `onRepairResult(...)`. State extended to model the two-tag in-flight phase (so the FAB and form are gated correctly). State is **not** persisted across process death (FR-6.4 / Q2A=C).

**U6 milestone install gate (Q-T2=B)**: at U6b close-out, install debug build on moto g stylus 2025 / Android 16 and exercise:
- Create-and-Pair → Pair-another-tag → second tag carries identical bytes; both UIDs on same spool.
- Tap a UID currently on spool A while spool B is selected → RepairConfirm sheet appears → confirm → tag tap writes; UID moves A→B; A retains other UIDs.
- Cancel from RepairConfirm sheet → no Spoolman PATCH issued; no NFC write attempted.
- Re-pair same UID into same spool → no-op (idempotent; no PATCH).

### 1.2 Cross-unit consumers (locked by `unit-of-work-dependency.md`)

- **U7 (Side modes)** — reuses `MoveOnBindUseCase` for `VendorUidOnlyPairUseCase` (S-4.8 / FR-4.9 — opt-in path still routes UID add through move-on-bind precheck).
- **U10 (Release polish)** — surfaces the U6 milestone install-gate observations + APK size diff for review.
- No other unit depends on `TwoTagUseCase` directly; it is consumed only by `MainViewModel`.

### 1.3 Out-of-scope for U6b (deferred)

- Catalogue-backed `MaterialPicker` / `BrandPicker` — **U8** (string-based pickers from U6a are reused as-is).
- Settings UI completeness (sort, theme override, full banner Retry) — **U9**.
- Raw-write + Vendor UID-only flows — **U7**.
- APK size review / JDK 17 portability / doc-drift sync (`component-methods.md` §1 stale rename, §6 use-case list, §7 type list) — **U10**.
- Persistent two-tag flow across app launches — explicitly excluded by FR-6.4 / Q2A=C; no DataStore writes for two-tag state.

---

## 2. Plan Steps (checkboxes)

### 2.1 Domain entities — `MoveOnBindUseCase.Outcome` + `TwoTagUseCase` types

- [x] 2.1.1 Lock `MoveOnBindUseCase.Outcome` extension. Today U6a ships a single `Proceed` variant with a comment "U6b adds: RequireConfirmation, ConfirmedAndMoved, Declined." Replace with the final shape:
  - `Proceed` — UID is unowned, or already owned by the target spool (idempotent re-pair). Caller continues as if move-on-bind didn't fire.
  - `Moved(fromSpoolId: Int)` — confirmation issued + atomic move executed. Caller continues; the UID is now on the target spool.
  - `Declined` — user cancelled the RepairConfirm sheet. **Q-U6b-2** — does the create-and-pair flow then *abort* (no Spoolman PATCH on B, no further work) or *proceed without the move* (B stays without the UID, but the NDEF write already happened)? My pick: **abort the append step on B**; surface a snackbar "Tag write succeeded but pairing cancelled — UID still on spool A" so the user understands the on-tag state.
  - `Failed(reason: String, partiallyModifiedSpoolId: Int?)` — A-side `removeCardUidFromSpool` PATCH failed (no spool mutated; `partiallyModifiedSpoolId = null`), or A-side succeeded but B-side `appendCardUidToSpool` failed (`partiallyModifiedSpoolId = A`). Caller surfaces an error banner identifying the partially-modified spool per S-5.2.
  - `AmbiguousOwnership(currentOwners: List<SpoolmanSpool>)` — `findSpoolsByCardUid` returned ≥2 spools (data-integrity oddity, multiple spools claim the same UID). **Q-U6b-3** — should U6b handle this gracefully (refuse + show error) or treat the first spool as the owner and proceed? My pick: **refuse + surface error**; data-integrity hole should be visible, not silently papered over.
- [x] 2.1.2 Lock `MoveOnBindUseCase.invoke(...)` signature change.
  - Current: `suspend operator fun invoke(uid: CardUid, targetSpoolId: Int): Outcome` — no way for the impl to surface a confirmation prompt to the UI.
  - New shape: same signature, but the impl gains a constructor dependency on a `MoveOnBindConfirmer` interface (`suspend fun confirm(other: SpoolmanSpool, target: Int): Boolean`) that the impl `await`s before issuing the move PATCHes. The Hilt-bound impl delegates to a `MoveOnBindConfirmerImpl` that drives `MainViewModel.requestRepairConfirmation(...)`. **Q-U6b-4** — agree with this seam, or prefer a simpler "use-case returns `RequireConfirmation`, ViewModel re-invokes use-case after user confirms" two-call shape? My pick: **single-call seam with a `Confirmer` interface**, since the use-case is a coroutine and a suspending boundary keeps the call site (`CreateAndPairUseCase`) one method instead of two.
- [x] 2.1.3 Lock `TwoTagUseCase` input + result types:
  - Input: `TwoTagInput(spoolId: Int, payloadBytes: ByteArray)` — caller pre-derives the payload bytes (re-derived from spool filament per S-6.4). **Q-U6b-5** — should `TwoTagUseCase` instead take `spoolId: Int` and re-derive internally (so `MainViewModel` doesn't have to)? My pick: **use-case re-derives**; keeps `MainViewModel` thin and centralises the "from spool to OpenSpool payload" mapping in one place.
  - Result sealed type `TwoTagResult` with the same shape family as `CreateAndPairResult` for consistency:
    - `Success.SecondTagPaired(spoolId: Int, uid: CardUid)`
    - `VendorTagRejected(uid: CardUid)` — second tag is `TagClassification.Vendor`; abort.
    - `MoveOnBindRequired(uid: CardUid, currentOwner: SpoolmanSpool)` — only emitted if `MoveOnBindUseCase` returns `RequireConfirmation` (which it doesn't under the Q-U6b-4=A single-call seam — but variant declared so the result is forward-compatible if we ever flip to two-call).
    - `VerifyFailed(uid: CardUid, cause: String)`
    - `SpoolmanFailed(uid: CardUid, outcome: SpoolmanOutcome<*>)`
    - `NfcFailed(uid: CardUid?, reason: String)`
    - `Cancelled(reason: String)` — including timeout.
- [x] 2.1.4 Lock `MainUiState` extension for two-tag in-flight + repair-confirmation pending:
  - `ActiveFlow.PromptingPairAnother(spoolId: Int)` — first pair just succeeded; FAB disabled; sheet visible.
  - `ActiveFlow.WritingSecondTag(spoolId: Int)` — second-tag write in flight.
  - `ActiveFlow.AwaitingRepairConfirmation(uid: CardUid, currentOwner: SpoolmanSpool, targetSpoolId: Int)` — RepairConfirm sheet visible; FAB disabled; cancelling the sheet returns `MoveOnBindUseCase.Outcome.Declined`. **Q-U6b-6** — does the AwaitingRepairConfirmation state need to carry the in-flight `Continuation` for the use-case, or is the `MoveOnBindConfirmer` injected as a singleton that holds the continuation internally? My pick: **`Confirmer` holds the continuation**; UI state just signals that the sheet should be visible.
- [x] 2.1.5 Lock `RepairConfirmUiState` (replacing the current placeholder):
  - `RepairConfirmUiState(otherSpoolDisplay: String, otherSpoolId: Int, targetSpoolId: Int, uid: CardUid)` — sheet copy reads e.g. "This tag is currently paired to **{otherSpoolDisplay}** (id #{otherSpoolId}). Move it to the selected spool (id #{targetSpoolId})?". **Q-U6b-7** — confirm exact copy: my draft above, vs. simpler "Re-pair this tag to the selected spool? Currently on: {otherSpoolDisplay}." My pick: **the simpler second draft**, less wall-of-text.

### 2.2 Use-case — `MoveOnBindUseCase` impl

- [x] 2.2.1 Lock the **happy-path sequence** (single-call via `Confirmer`):
  1. `findSpoolsByCardUid(uid)` — bulk-fetch + filter (delta §6 / U3-Δ-2; already shipped in U6a).
  2. Branch on result size:
     - `0` matches → `Outcome.Proceed` (UID is unowned).
     - `1` match where `match.id == targetSpoolId` → `Outcome.Proceed` (idempotent — already on target).
     - `1` match where `match.id != targetSpoolId` → call `confirmer.confirm(match, targetSpoolId)`; on `false` return `Declined`; on `true` continue to step 3.
     - `≥2` matches → `Outcome.AmbiguousOwnership(matches)`.
  3. `removeCardUidFromSpool(matchId, uid)` — full-extra read-modify-write (delta §6 / U3-Δ-4).
     - On failure: `Outcome.Failed(reason, partiallyModifiedSpoolId = null)`.
  4. `appendCardUidToSpool(targetSpoolId, uid)` — idempotent (delta §6 / U3-Δ-3).
     - On failure: `Outcome.Failed(reason, partiallyModifiedSpoolId = matchId)` — A was already mutated; surface that.
  5. Return `Outcome.Moved(fromSpoolId = matchId)`.
- [x] 2.2.2 Lock partial-commit semantics (Q11=A): **no auto-rollback** on B-side failure. The Failed outcome's `partiallyModifiedSpoolId` field gives the user enough info to repair manually in Spoolman web UI. **Q-U6b-8** — agree (no rollback), or attempt a best-effort rollback (re-add `card_uid` to A)? My pick: **no rollback**; rollbacks can compound failures (rollback PATCH itself fails) and the spec explicitly says "no partial commit beyond the already-applied PATCH" + "error message identifies which spool was partially modified".
- [x] 2.2.3 Lock `MoveOnBindConfirmer` Hilt binding:
  - Interface: `interface MoveOnBindConfirmer { suspend fun confirm(other: SpoolmanSpool, targetSpoolId: Int): Boolean }`.
  - Impl: `MoveOnBindConfirmerImpl @Inject constructor()` — singleton; holds `_pendingRequest: Channel<RepairConfirmRequest>` + `_pendingResult: CompletableDeferred<Boolean>?`. Exposes `pendingRequest: Flow<RepairConfirmRequest?>` for `MainViewModel` to observe + relay to UI sheet, and `submitResult(confirm: Boolean)` for the sheet result handler. **Q-U6b-9** — confirm "single in-flight request" assumption (only one create-or-two-tag flow active at a time); my pick: yes — the existing `_state.activeFlow` already enforces single-flow-at-a-time, so a single CompletableDeferred is sufficient.

### 2.3 Use-case — `TwoTagUseCase`

- [x] 2.3.1 Lock the **payload re-derivation** (S-6.4):
  - Input: `spoolId: Int`.
  - Fetch spool + filament + vendor from `SpoolmanRepository`'s caches (`spools.value` / `filaments.value` already populated).
  - Build `OpenSpoolPayload` from `spool.extra.variant` + filament's material/colorHex/temperatures + vendor's name.
  - **Q-U6b-10** — what if the cache is stale or the spool is missing? My pick: **fall back to `getSpool(spoolId)` round-trip**; if that fails, return `TwoTagResult.SpoolmanFailed(...)`.
- [x] 2.3.2 Lock the **second-tag write sequence**:
  1. Re-derive payload (2.3.1).
  2. Arm `NfcIntent.Write(payload, expectedUid = null)` — same shape as U6a's `CreateAndPairUseCase` (no UID enforcement; move-on-bind handles conflicts).
  3. On `NfcResult.Success(uid, classification)`:
     - If `classification is TagClassification.Vendor` → return `VendorTagRejected(uid)` per S-6.2 AC.
     - Otherwise call `moveOnBind.invoke(uid, spoolId)`:
       - `Proceed` / `Moved` → continue.
       - `Declined` → return `Cancelled("repair declined")`; UID is on tag but not appended; same UX as U6a-Q-U6b-2 path.
       - `Failed(reason, partial)` → return `SpoolmanFailed(uid, ...)` (wrap as appropriate).
       - `AmbiguousOwnership(...)` → surface as `SpoolmanFailed` with explanatory reason.
     - `appendCardUidToSpool(spoolId, uid)` — idempotent.
     - Return `Success.SecondTagPaired(spoolId, uid)`.
  4. Verify-fail / NFC-fail paths mirror `CreateAndPairUseCase`'s `WriteResult.Verify` / `Failed`.
- [x] 2.3.3 Lock the **vendor-tag rejection** (S-6.2 AC):
  - `NfcRepository`'s classifier already gates `arm(Write(...))` to non-vendor tags (per U4 contract). Confirm `TwoTagUseCase` receives `NfcResult.Error("vendor tag — write blocked")` via the existing path; map it to `VendorTagRejected(uid)`. **Q-U6b-11** — does U4's classifier emit a distinct error type for vendor-tag rejection, or just a string-matched `Error.reason`? My pick: **string-match for now** (existing U4 contract), file as cleanup for U10.
- [x] 2.3.4 Lock the **timeout**:
  - Same 15 s window as U6a's `writeTimeoutMs` (since this is also a write+verify operation). **Q-U6b-12** — same 15 s, or extend (user might walk to the second tag)? My pick: **15 s** (same envelope; user is already prompted and ready); on timeout return `Cancelled("timeout")`.

### 2.4 ViewModel — `MainViewModel` extensions

- [x] 2.4.1 Lock new state extensions (per 2.1.4) — `ActiveFlow.PromptingPairAnother`, `WritingSecondTag`, `AwaitingRepairConfirmation`.
- [x] 2.4.2 Lock new handlers:
  - `onPairAnotherTagOffered()` — invoked by `applyWriteResult(WrittenAndPaired)` to transition into `PromptingPairAnother`.
  - `onPairAnotherTagAccepted()` — sheet "Pair another" tap; transitions into `WritingSecondTag`; launches `twoTag.invoke(spoolId)`.
  - `onPairAnotherTagDismissed()` — sheet "Done" tap or scrim dismiss; transitions back to `Idle`; surfaces "Saved with one tag" snackbar. **Q-U6b-13** — does dismissing the prompt clear the form (so the user is ready for the next *fresh* pair) or keep it (in case they want to manually start a new pair with similar details)? My pick: **clear form** — matches U6a's "form clears on first-pair success" behaviour; the sheet is a continuation of that one pair, dismissing it should not be different from "no second tag wanted".
  - `onRepairResult(confirm: Boolean)` — relays sheet result back into `MoveOnBindConfirmerImpl.submitResult(confirm)`.
  - `onTwoTagResult(result: TwoTagResult)` — applies result; transitions back to `Idle`; emits snackbar.
- [x] 2.4.3 Lock `MoveOnBindConfirmer` observation:
  - Inject `MoveOnBindConfirmer` (singleton) into `MainViewModel`; observe `pendingRequest` flow; on emission, transition `activeFlow` into `AwaitingRepairConfirmation(...)` carrying the request data.
  - **Q-U6b-14** — does `MainViewModel` own the `MoveOnBindConfirmerImpl`'s lifecycle (so per-VM instance), or is it process-singleton (Hilt `@Singleton`)? My pick: **process-singleton**; one in-flight request at a time, simplifies wiring.
- [x] 2.4.4 Lock test-only injection — write tests use `FakeMoveOnBindConfirmer` (returns deterministic confirm/decline); same pattern as U6a's `FakeCreateAndPairUseCase`.

### 2.5 Compose UI — sheet hosts + state mapping

- [x] 2.5.1 Lock `RepairConfirmSheet` Compose surface:
  - `ModalBottomSheet` host.
  - Title (per Q-U6b-7 outcome): "Re-pair this tag to the selected spool?"
  - Body: "Currently on: {otherSpoolDisplay}".
  - Two buttons: "Move it" (primary) / "Cancel" (text).
  - Dismiss-via-scrim treated as Cancel (FR-13.2 / `RepairConfirmViewModel.onDismiss` → `onRepairResult(false)`).
- [x] 2.5.2 Lock `PairAnotherTagSheet` Compose surface:
  - `ModalBottomSheet` host.
  - Title: "Pair another tag with this spool?"
  - Body (1-2 lines): "We'll write the same data to the second tag and remember both."
  - Two buttons: "Pair another" (primary) / "Done" (text).
- [x] 2.5.3 Lock sheet hosting in `MainScreen`:
  - Show `RepairConfirmSheet` when `state.activeFlow is AwaitingRepairConfirmation`.
  - Show `PairAnotherTagSheet` when `state.activeFlow is PromptingPairAnother`.
  - Both gated by single sheet host; no overlapping sheets. **Q-U6b-15** — confirm single sheet at a time (no nesting); my pick: yes — the activeFlow state already serializes them.
- [x] 2.5.4 Lock FAB + form gating:
  - `Read` and `Save` buttons disabled while `activeFlow !in {Idle}` — already covered by U6a's `canRead` / `canWrite` derived states; extend the disable predicate to cover the three new `ActiveFlow` variants.

### 2.6 ViewModel test plan (Q-T3=B)

- [x] 2.6.1 `MoveOnBindUseCaseTest`:
  - happy path: A-owned UID → confirmer returns true → `Moved(fromSpoolId=A)`; both PATCHes invoked once; UID order on A preserved (other UIDs intact).
  - idempotent: UID already on target → `Proceed`; no PATCH.
  - unowned: UID has 0 owners → `Proceed`; no PATCH.
  - ambiguous: 2 owners → `AmbiguousOwnership`; no PATCH.
  - declined: confirmer returns false → `Declined`; no PATCH.
  - partial-commit: A-side success, B-side fails → `Failed(reason, partiallyModifiedSpoolId=A)`; only one PATCH happened.
  - A-side fails: `Failed(reason, partiallyModifiedSpoolId=null)`; no B-side PATCH attempted.
- [x] 2.6.2 `TwoTagUseCaseTest`:
  - identical-payload: bytes written to second tag equal bytes derived from spool record. (Test asserts `OpenSpoolPayloadCodec.toJson(...)` deterministic.)
  - vendor-tag rejection: classifier emits `Vendor` → `VendorTagRejected`; no PATCH; no append.
  - move-on-bind on second tag: second UID owned by A → `MoveOnBindUseCase.invoke` called; confirmer drives `Moved`; append to spool succeeds; result `Success.SecondTagPaired`.
  - cache-miss fallback: `spools.value` empty → `getSpool(spoolId)` invoked; success branches as normal.
  - cache + getSpool both fail: `SpoolmanFailed`.
- [x] 2.6.3 `RepairConfirmViewModelTest`:
  - UI state hydrated from `AwaitingRepairConfirmation` carries the right copy + ids.
  - `onConfirm()` → `MoveOnBindConfirmer.submitResult(true)`.
  - `onDismiss()` → `submitResult(false)`.
- [x] 2.6.4 `MainViewModelTwoTagTest`:
  - successful first pair → `activeFlow == PromptingPairAnother`.
  - `onPairAnotherTagAccepted` → `WritingSecondTag`; `onTwoTagResult(Success)` → `Idle`; snackbar emitted.
  - `onPairAnotherTagDismissed` → `Idle`; form cleared; snackbar emitted.
  - move-on-bind precheck path: `MoveOnBindConfirmer.pendingRequest` emits → `activeFlow == AwaitingRepairConfirmation`; `onRepairResult(true)` → submitResult forwarded.
- [x] 2.6.5 `CreateAndPairUseCaseTest` extension (regression):
  - move-on-bind reorder: `appendCardUidToSpool` is called *after* `moveOnBind.invoke` (test the call ordering with relaxed mocks).
  - existing tests (write success, verify-fail, spoolman-fail) still pass.

### 2.7 Verification commands (post-Code-Gen)

- [x] 2.7.1 `./gradlew compileDebugKotlin` ✅
- [x] 2.7.2 `./gradlew testDebugUnitTest` ✅ — running total target: **244 (U6a) + ~28 (U6b) ≈ 272 / 272**.
- [x] 2.7.3 `./gradlew assembleDebug` ✅ — APK size monitored; flagged for U10 if >35 MB.
- [x] 2.7.4 **U6 milestone install gate** — `./gradlew installDebug` to moto g stylus 2025 / Android 16; manual ACs:
  - First pair → `PairAnotherTagSheet` shown.
  - Tap "Pair another" → second tap writes; both UIDs visible in Spoolman web UI under same spool.
  - Tap a UID owned by spool A while spool B is selected → `RepairConfirmSheet` shown; confirm → tag write proceeds; A no longer owns UID; B owns UID.
  - Cancel `RepairConfirmSheet` → no Spoolman PATCH; snackbar explains UID still on A.
  - Re-pair same UID into same spool → no error, no duplicate PATCH (idempotent).
  - Vendor tag presented during second-tag flow → "Vendor tag — write blocked"; no append.

### 2.8 Out-of-scope guards (explicit for U6b)

- No Settings UI changes (sort, theme, full banner Retry → U9).
- No catalogue picker swap (U8).
- No DataStore writes for two-tag in-flight state (FR-6.4).
- No raw-write / vendor UID-only flows (U7).
- No APK size review or JDK 17 portability fix (U10).

---

## 3. Decision Records (open questions Q-U6b-1 .. Q-U6b-15)

| ID | Question | My pick | [Answer]: |
|---|---|---|---|
| Q-U6b-1 | Reorder `CreateAndPairUseCase` so move-on-bind precheck runs *before* `appendCardUidToSpool` (rather than current "called but ignored" placement at line 77)? | A — yes, reorder | **A** (accepted 2026-05-26 via "ok") |
| Q-U6b-2 | On `MoveOnBindUseCase.Outcome.Declined` after a successful tag write, does `CreateAndPairUseCase` abort the append step on B (UID stays on A; tag has B's payload but isn't bound to B in Spoolman)? | A — abort append; surface snackbar explaining the on-tag-vs-Spoolman state | **A** (accepted 2026-05-26) |
| Q-U6b-3 | `findSpoolsByCardUid` returns ≥2 spools (data-integrity oddity). What does move-on-bind do? | A — refuse with `AmbiguousOwnership` error; surface clearly | **A** (accepted 2026-05-26) |
| Q-U6b-4 | Move-on-bind UI seam: single-call use-case with injected `Confirmer` interface, or two-call ("returns RequireConfirmation, VM re-invokes")? | A — single-call `Confirmer` | **A** (accepted 2026-05-26) |
| Q-U6b-5 | `TwoTagUseCase` payload: caller pre-derives bytes, or use-case re-derives from `spoolId`? | B — use-case re-derives (centralised mapping) | **B** (accepted 2026-05-26) |
| Q-U6b-6 | Where does the `MoveOnBindConfirmer`'s pending continuation live — inside the Confirmer impl, or in `MainUiState`? | A — inside Confirmer impl; UI state is just a "sheet visible" signal | **A** (accepted 2026-05-26) |
| Q-U6b-7 | `RepairConfirmSheet` copy — verbose ("This tag is currently paired to {X} (id #{Y}). Move it to the selected spool (id #{Z})?") or concise ("Re-pair this tag to the selected spool? Currently on: {X}.")? | B — concise | **B** (accepted 2026-05-26) |
| Q-U6b-8 | Partial-commit handling: best-effort rollback (re-add UID to A) on B-side failure, or no rollback (surface `partiallyModifiedSpoolId` to user)? | B — no rollback | **B** (accepted 2026-05-26) |
| Q-U6b-9 | `MoveOnBindConfirmer` — single in-flight request only (one CompletableDeferred), or queued? | A — single in-flight (matches `activeFlow` serialisation) | **A** (accepted 2026-05-26) |
| Q-U6b-10 | `TwoTagUseCase` cache-miss fallback: trust caches only, or fall back to `getSpool(spoolId)` round-trip? | B — fall back to `getSpool(spoolId)` | **B** (accepted 2026-05-26) |
| Q-U6b-11 | Vendor-tag rejection plumbing: distinct error type from U4 classifier, or string-match on `Error.reason`? | B — string-match for now (U10 cleanup) | **B** (accepted 2026-05-26) |
| Q-U6b-12 | Two-tag write timeout — same 15 s as U6a's `writeTimeoutMs`, or extend? | A — same 15 s | **A** (accepted 2026-05-26) |
| Q-U6b-13 | `onPairAnotherTagDismissed` — clear form, or keep form? | A — clear form (continuation of first-pair-success behaviour) | **A** (accepted 2026-05-26) |
| Q-U6b-14 | `MoveOnBindConfirmer` lifecycle — process-singleton (`@Singleton`) or per-VM? | A — `@Singleton` | **A** (accepted 2026-05-26) |
| Q-U6b-15 | Single sheet at a time (RepairConfirm vs PairAnother never overlap) enforced by `activeFlow` serialisation? | A — yes, one sheet at a time | **A** (accepted 2026-05-26) |

---

## 4. Stage-Gate Action

After all `[Answer]:` tags above are filled, generate FD artefacts under
`aidlc-docs/construction/u6b-move-on-bind-two-tag/functional-design/`:
- `domain-entities.md`
- `business-rules.md`
- `business-logic-model.md`
- `frontend-components.md`

Then present the standardized 2-option completion message (Request Changes /
Continue to Next Stage) per `construction/functional-design.md`.
