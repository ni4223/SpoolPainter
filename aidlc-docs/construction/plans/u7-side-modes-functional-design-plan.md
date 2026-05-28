# U7 — Functional Design Plan

**Stage**: CONSTRUCTION → Per-Unit Loop → Functional Design (U7)
**Unit**: U7 — Side Modes (Raw-Write + Vendor UID-only Pair)

## Source artefacts

- `aidlc-docs/inception/application-design/unit-of-work.md` §3-U7
- `aidlc-docs/inception/application-design/components.md` §2.3 (`RawWriteUseCase`, `VendorUidOnlyPairUseCase`), §2.5 (`MainViewModel`), §2.8 (`VendorUidOnlyOptInSheet`)
- `aidlc-docs/inception/application-design/component-methods.md` §6 (`MainViewModel`), §7 (`MainUiState`), §8 (Compose components)
- `aidlc-docs/inception/application-design/services.md` §6 (Side modes)
- `aidlc-docs/inception/requirements/requirements.md` — FR-4.4, FR-4.5, FR-4.6, FR-4.7, FR-4.8, FR-4.9, FR-5, FR-13.2
- `aidlc-docs/inception/requirements/requirements-delta-extra-fields.md` (approved 2026-05-25)
- `aidlc-docs/inception/requirements/requirements-delta-uid-as-display-only.md` (approved 2026-05-25)
- `aidlc-docs/inception/user-stories/stories.md` — S-4.6, S-4.7, S-4.8

### Existing code touchpoints

| File | Role in U7 |
|---|---|
| `domain/usecases/CreateAndPairUseCase.kt` | Reference shape for both new use-cases (resolve → write → move-on-bind → append). Vendor flow drops Write; raw flow drops Spoolman chain. |
| `domain/usecases/MoveOnBindUseCase.kt` | Re-used as-is for vendor UID-only pair. |
| `domain/usecases/MoveOnBindConfirmer.kt` | `@Singleton`; vendor flow piggybacks on the same confirmer. |
| `ui/components/sheets/VendorOptInViewModel.kt` | U1 placeholder VM — replaced here. |
| `ui/screens/main/MainUiState.kt` | `FormState.rawWriteMode: Boolean` already declared (U1 skeleton); U7 wires the toggle and use-case selector. |
| `hardware/nfc/NfcRepository.kt` | U4 contract already throws `vendor-tag protected (FR-4.7)` on `arm(Write(...))` against vendor tags (`NonNdefTagException` path landed in U6b polish). |

---

## 1. Unit Context

### 1.1 Scope (locked by Units Generation §3-U7)

#### Raw-Write side mode (S-4.7 / FR-4.8)

- User-toggleable mode that writes an OpenSpool payload to a blank or OpenSpool tag with **zero** Spoolman interaction.
- Skips `findSpoolsByCardUid`, `createSpoolForNewFilament`, and `appendCardUidToSpool` — the entire FR-4.3 / FR-4.6 chain is bypassed.
- Still respects FR-4.7 vendor-tag NDEF protection (write blocked on vendor tag classification).
- Still respects FR-4.5 write-then-verify (`NfcRepository.runWriteThenVerify` enforces it for any `arm(Write(...))` call — independent of orchestration).
- Payload **omits `spool_id`** — there's no Spoolman id to embed.
- Mode is **not** a global preference (per S-4.7 AC). Lives on `FormState.rawWriteMode`; resets to `false` after a successful raw write or when the user clears the form. Persistence-free.

#### Vendor UID-only Pair (S-4.8 / FR-4.9)

- Opt-in path for tags classified `TagClassification.Vendor(reason)` — branded factory tags whose NDEF payload v2 must never overwrite (FR-4.7).
- **At Read time**: vendor classification produces the *same* empty-form UI state as a blank/unparseable tag (S-3.5). UID is captured into `form.cardUid`; dropdown remains pickable; form remains editable. **No prompt yet** — Read is non-destructive.
- **At Save/Write press**: detect that the staged tag is vendor-classified (form has UID + last-seen tag was Vendor) → `VendorUidOnlyOptInSheet` bottom sheet with two actions: **Pair UID only** (primary) / **Cancel** (text).
- **On Cancel**: no NDEF write, no Spoolman call; user returns to main screen with form state intact.
- **On Pair UID only**: run the Spoolman pairing chain only (FR-4.6 PATCH for existing-spool path; FR-7 create chain for new-spool path). **No NDEF write, no verify.** Move-on-bind (FR-5) still applies and runs **after** the opt-in is confirmed (per FR-4.9 spec).

#### Write-side NDEF boundary enforcement (S-4.6 AC)

When the user attempts standard `Save & Write` (non-raw, non-vendor-opt-in) on a vendor-classified tag, the path SHALL refuse the NDEF write. U4 already surfaces a `vendor-tag protected (FR-4.7)` error string from the classifier at write-arm time; U6b polish (UI-09) wired the error → snackbar "Vendor tag — write blocked".

**U7's job**: replace that snackbar dead-end with a productive route into the vendor opt-in sheet (the user's intent was clearly to pair this tag with a spool — now we offer a legal way to do it).

### 1.2 Cross-unit consumers

| Unit | Relationship |
|---|---|
| U8 (Pickers + Custom Entries + Filament Metadata UX) | Independent. Vendor UID-only new-spool path uses the same `createSpoolForNewFilament` API, so U8's filament metadata expander applies cleanly. |
| U9 (Settings + Theming + UI Shell) | Settings may eventually surface a "Default to raw-write mode" toggle (NOT in U7 scope). Banner state irrelevant for raw-write (no Spoolman). |
| U10 (Release polish) | Manual NFC verification for U7 flows captured at the U10 install gate per `unit-of-work.md` §U7 exit criteria. **No separate U7 install gate.** |
| U11 (v2.1 — vendor decode engine) | Forward-compatible. U7 cares about classification *type* (Vendor vs. Blank/OpenSpool), not the reason or decoded contents. U11 can flip the classifier without touching U7. |

### 1.3 Out of scope (deferred)

- Catalogue-backed Material/Brand pickers → **U8**
- Filament metadata expander UI in raw-write or vendor opt-in flows → **U8** (`MoreDetailsExpander` lives on `FilamentForm` regardless of mode)
- Settings UI completeness (sort, theme, full banner Retry) → **U9**
- Vendor decoding (`Vendor(decoded: ...)`) → **U11/v2.1**
- Per-vendor key Settings + encrypted storage → **U12/v2.1**
- Persistence of `rawWriteMode` across app launches — explicitly excluded by S-4.7 AC ("no global preference")
- APK size review / JDK 17 portability fix → **U10**

---

## 2. Plan Steps

### 2.1 Domain entities

#### 2.1.1 `RawWriteUseCase` shape

- [ ] Lock the input/result types.

**Input** — `RawWriteInput(form: FormState, resolvedMaterialName: String? = null, newFilamentVendor: String = "Unknown")`. Same envelope as `CreateAndPairInput` minus `newFilamentName` (raw-write needs only the OpenSpool payload).

**Result** — sealed type `RawWriteResult`:

| Variant | Meaning |
|---|---|
| `Success.Written(uid)` | NDEF write + verify ok; no Spoolman calls were made. |
| `VendorTagRejected(uid)` | `TagClassification.Vendor` blocks the write per FR-4.7. |
| `VerifyFailed(uid, cause)` | Write/verify mismatch. |
| `NfcFailed(uid?, reason)` | Adapter throw, timeout, tag lifted, etc. |
| `Cancelled(reason)` | Explicit user cancel (form-clear or mode-toggle off mid-flow). |

> **Q-U7-1** — accept `FormState` directly, or wrap in `RawWriteInput`?
> **My pick:** wrap in `RawWriteInput` — symmetric with `CreateAndPairInput` and carries the `resolvedMaterialName` / `newFilamentVendor` overrides cleanly.

#### 2.1.2 `VendorUidOnlyPairUseCase` shape

- [ ] Lock the input/result types.

**Input** — `VendorUidOnlyPairInput(form, newFilamentName, newFilamentVendor, resolvedMaterialName? = null, observedUid)`.

`observedUid` is the UID the user saw at Read time, captured into `form.cardUid`. Passed explicitly (rather than read off `form.cardUid` inside the use-case) because the form's UID field can drift if the user clears + re-types. The use-case acts on the UID it was *handed* at Save/Write press.

**Result** — sealed type `VendorUidOnlyPairResult`:

| Variant | Meaning |
|---|---|
| `Success.UidPaired(spoolId, uid, isNewSpool)` | Spoolman PATCH (existing-spool) or POST chain (new-spool) ok; no NDEF write was issued. |
| `SpoolmanFailed(uid, outcome)` | Any Spoolman call failed. |
| `Cancelled(reason)` | User cancelled the opt-in sheet, OR `MoveOnBindUseCase.Outcome.Declined`. |
| `MoveOnBindPartial(uid, partiallyModifiedSpoolId, reason)` | A-side `removeCardUidFromSpool` succeeded but B-side `appendCardUidToSpool` failed. Same shape as `TwoTagResult.MoveOnBindPartial`. |

> **Q-U7-2** — separate `Cancelled.OptInDeclined` variant, or single `Cancelled` with reason string?
> **My pick:** single `Cancelled` with reason string — same approach as `CreateAndPairResult.Cancelled` and `TwoTagResult.Cancelled`; VM can branch on reason if needed.

#### 2.1.3 `MainUiState` extensions

- [ ] Add three new `ActiveFlow` variants.

| Variant | Meaning |
|---|---|
| `WritingRaw` | Raw-write in flight; FAB disabled; form disabled. |
| `AwaitingVendorOptIn(observedUid: CardUid)` | Vendor opt-in sheet visible; FAB disabled. Cancelling returns to `Idle`. |
| `PairingVendorUidOnly` | Spoolman PATCH/POST chain in flight after opt-in confirmation; FAB disabled. |

> **Q-U7-3** — does `AwaitingVendorOptIn` carry the classification reason for a sheet diagnostic line?
> **My pick:** yes — surface as a small body line ("Tag type: {reason}"). The reason string is already populated by U4's classifier (e.g. "non-OpenSpool MIME", "non-NDEF tag") and is useful diagnostic data.

#### 2.1.4 `VendorOptInUiState`

- [ ] Replace the U1 placeholder `VendorOptInUiState(placeholder = true)`.

**New shape** — `VendorOptInUiState(observedUid, classificationReason?, hasExistingSelection)`.

`hasExistingSelection` lets the sheet body switch wording:
- existing-spool path: "...the spool you've selected"
- new-spool path: "...a new spool with the details you've typed"

Both paths converge on the same opt-in confirmation; only the body copy differs.

---

### 2.2 Use-case — `RawWriteUseCase`

#### 2.2.1 Happy-path sequence

- [ ] Lock the sequence.

1. Build `OpenSpoolPayload` from `input.form` + overrides — **`spoolId = null`** (FR-4.8).
2. `nfc.arm(NfcIntent.Write(payload, expectedUid = null))`.
3. Await terminal NFC state.
4. Branch on outcome:
   - `NfcResult.Success` → `Success.Written(uid)`.
   - `NfcResult.Error` with `vendor-tag protected` prefix → `VendorTagRejected(lastSeenTag.uid ?: CardUid(""))`.
   - `NfcResult.Error` with `verify mismatch` / `verification failed` → `VerifyFailed(lastSeenTag.uid ?: CardUid(""), reason)`.
   - Any other `NfcResult.Error` → `NfcFailed(lastSeenTag.uid, reason)`.

#### 2.2.2 Payload construction

- [ ] Lock the helper structure.

Same `makePayload(...)` shape as `CreateAndPairUseCase`, but with `spoolId = null`. Duplicate rather than refactor — the two use-cases will diverge once U8 lands (raw-write may not need filament metadata overrides; vendor UID-only does).

> **Q-U7-4** — should `OpenSpoolPayload` get a `companion object fromForm(form, spoolId, materialOverride, vendorOverride)` factory now (currently duplicated between `CreateAndPairUseCase.makePayload` and `TwoTagUseCase.derivePayload`)?
> **My pick:** defer to U10 — refactoring `OpenSpoolPayload` construction is not in U7 scope; U7 follows the existing pattern.

#### 2.2.3 Timeout

- [ ] Lock at 15 s (matches `CreateAndPairUseCase` / `TwoTagUseCase` `writeTimeoutMs`). On timeout → `Cancelled("timeout")`.

#### 2.2.4 Vendor-tag rejection plumbing

- [ ] Reuse U6b's string-match pattern (`reason.startsWith("vendor-tag")`) per Q-U6b-11=B. NfcRepository emits the standardised `vendor-tag protected (FR-4.7)` error string for both NDEF-rejection and non-NDEF-tag cases (UI-09 fix landed in U6b polish).

#### 2.2.5 Post-success cleanup

- [ ] Lock the `rawWriteMode` reset behaviour.

`MainViewModel.applyRawWriteResult.Success.Written` SHALL set `form.rawWriteMode = false` so the next Save/Write defaults back to the standard create-and-pair flow. The user opts in per write, not per session.

> **Q-U7-5** — reset `rawWriteMode` on every successful raw write, or persist until user toggles off?
> **My pick:** reset on success — matches S-4.7 AC ("no global preference" + "persists across the writing flow only").

---

### 2.3 Use-case — `VendorUidOnlyPairUseCase`

#### 2.3.1 Existing-spool path

- [ ] Lock the happy-path sequence (`input.form.selectedSpoolId != null`).

1. `moveOnBind.invoke(input.observedUid, selectedSpoolId)` — move-on-bind precheck (FR-5 still applies per FR-4.9 spec). Confirmer sheet fires if needed; `Declined` → `Cancelled("repair declined")`.
2. On `Proceed` / `Moved` → `spoolman.appendCardUidToSpool(selectedSpoolId, observedUid)` (idempotent).
3. On Spoolman success → `Success.UidPaired(selectedSpoolId, observedUid, isNewSpool = false)`.
4. On Spoolman failure → `SpoolmanFailed`.

#### 2.3.2 New-spool path

- [ ] Lock the happy-path sequence (`input.form.selectedSpoolId == null`).

1. Construct `NewFilamentRequest` from form + overrides, **including** `cardUid = observedUid` so the create POST sets `lot_nr = card_uid:<uid>` directly (FR-7 chain on Spoolman side; same plumbing as U6a).
2. On Spoolman success → `Success.UidPaired(newSpoolId, observedUid, isNewSpool = true)`.
3. On Spoolman failure → `SpoolmanFailed`.

> **Q-U7-6** — does `NewFilamentRequest.fromForm` already carry the `cardUid` field, or do we re-add it (the U6a delta dropped it because create-and-pair appends *after* the write)?
> **My pick:** re-add a `cardUid` carrier IF Spoolman's POST consumes it — preferable to a two-step (POST without UID then PATCH UID) for the vendor flow because there's no NDEF write between them. Verify during FD Part 2 against current code; if the codebase already PATCHes after POST, vendor flow can reuse that path verbatim.

#### 2.3.3 NDEF write boundary (S-4.6 AC enforcement)

- [ ] Lock the type-level invariant.

The use-case SHALL never call `nfc.arm(NfcIntent.Write(...))`. Enforced at the type level — `VendorUidOnlyPairUseCase`'s constructor injects only `SpoolmanRepository` + `MoveOnBindUseCase` (no `NfcRepository`). `VendorUidOnlyPairUseCaseTest` asserts the dependency contract.

> **Q-U7-7** — should the use-case still inject `NfcRepository` for ctor symmetry, or strictly omit?
> **My pick:** strictly omit — dependency contract enforces the invariant at the type level. A future careless edit cannot accidentally arm a write because the dependency isn't there.

#### 2.3.4 Move-on-bind ordering

- [ ] Lock precheck-before-mutate.

Per FR-4.9 spec: "Move-on-bind (FR-5) still applies — a vendor tag's UID already paired to another spool prompts the same re-pair confirmation as any other tag (after the opt-in is confirmed)."

The Confirmer is `@Singleton`. When fired, it emits a `pendingRequest` that `MainViewModel` already observes and routes into `ActiveFlow.AwaitingRepairConfirmation`. The vendor UID-only flow inherits this routing for free — no new ViewModel plumbing needed.

For the new-spool path, the `createSpoolForNewFilament` POST itself sets `lot_nr = card_uid:<uid>` — if the UID was already on another spool, we'd need to remove it from the source *before* the POST, or we get the same UID on two spools transiently.

**Sequencing**: (1) move-on-bind precheck → (2) Spoolman POST/PATCH.

> **Q-U7-8** — move-on-bind precheck before or after Spoolman mutation?
> **My pick:** before — symmetric with `CreateAndPairUseCase`; keeps behaviour symmetric across the three pair-the-UID flows (create-and-pair, two-tag, vendor UID-only).

#### 2.3.5 Post-success behaviour

- [ ] Lock VM state after `Success.UidPaired`.

`MainViewModel.applyVendorUidOnlyPairResult.Success.UidPaired`:
- Form preserved (per UI-06 polish).
- Dropdown selection preserved (or set to the new spool id for the new-spool path) per UI-10 polish.
- Snackbar "UID paired" emitted.
- Transition to `PromptingPairAnother` per Q-U7-9 outcome.

> **Q-U7-9** — does `PairAnotherTagSheet` fire after a vendor UID-only success?
> **Options:**
> - **(a) Yes** — pairing another tag is independent of how the first tag got paired; user might want a second tag (which would take the standard write+verify path if blank).
> - **(b) No** — vendor UID-only is a one-shot recovery flow; offering "pair another" muddies the UX.
>
> **My pick:** (a) yes — keeps behaviour symmetric with create-and-pair.
>
> **Note:** If the second tag is also vendor, the same opt-in sheet would fire for it. That requires extending `TwoTagUseCase` to route vendor classification into the opt-in sheet — **out of U7 scope**. For v2.0, U7 keeps `TwoTagUseCase` unchanged; second-tag vendor → `VendorTagRejected` snackbar (same as today). User can re-trigger a second vendor UID-only pair from the main screen by tapping the vendor tag again at idle.

---

### 2.4 ViewModel — `MainViewModel` extensions

#### 2.4.1 New state extensions

- [ ] Add `WritingRaw`, `AwaitingVendorOptIn(observedUid)`, `PairingVendorUidOnly` to `ActiveFlow` (per 2.1.3).

#### 2.4.2 New handlers

- [ ] `onRawWriteModeToggled(enabled: Boolean)` — sets `_state.update { it.copy(form = it.form.copy(rawWriteMode = enabled)) }`. No flow side-effect.
- [ ] `onSaveAndWriteTapped()` (existing handler — extend, do not duplicate). New branch logic:
  - If `form.rawWriteMode` → `rawWrite.invoke(...)` → `applyRawWriteResult(...)`.
  - Else if last-seen tag classification was `Vendor` AND `form.cardUid != null` → transition to `ActiveFlow.AwaitingVendorOptIn(observedUid = form.cardUid)`. **Do not call any use-case yet** — wait for sheet result.
  - Else → existing `createAndPair.invoke(...)` path.
- [ ] `onVendorOptInConfirmed()` — sheet "Pair UID only" tap; transitions into `PairingVendorUidOnly`; launches `vendorUidOnlyPair.invoke(...)` with the observed UID from the active-flow state.
- [ ] `onVendorOptInCancelled()` — sheet "Cancel" tap or scrim dismiss; transitions back to `Idle`; form state intact. Snackbar suppressed per Q-U7-11.
- [ ] `applyRawWriteResult(result: RawWriteResult)` — branches per result type; transitions to `Idle`; emits snackbar.
- [ ] `applyVendorUidOnlyPairResult(result: VendorUidOnlyPairResult)` — branches per result type; transitions to `Idle`; emits snackbar.

> **Q-U7-10** — `onSaveAndWriteTapped` dispatch — single handler with internal branching, or three separate handlers (`onRawWriteTapped`, `onVendorOptInOpened`, `onCreateAndPairTapped`)?
> **My pick:** single handler with internal branching — there's only one Save/Write button in the UI, and routing is the VM's job. Keeps the UI dumb.

> **Q-U7-11** — vendor opt-in cancel — emit a snackbar, or suppress (mirroring UI-12 polish)?
> **My pick:** suppress — same logic as UI-12: user explicitly chose Cancel; they don't need a snackbar telling them what they just did. Form state preserved so they can re-tap or change selection.

#### 2.4.3 Vendor-classification observation

- [ ] Surface the observed tag classification to UI state.

`MainViewModel` already collects `nfc.lastSeenTag` (U1 / U6b). The `lastSeenTag.classification` field is what `onSaveAndWriteTapped` inspects to decide opt-in vs. standard flow.

> **Q-U7-12** — does the form retain a "this tag is vendor" hint UI between tap and Save (e.g. a small chip or banner saying "Vendor tag — UID-only available")?
> **My pick:** yes — render a Compose `AssistChip` near the form header: "Vendor tag detected — UID-only pair available." Cleared when `form.cardUid` is cleared or when a non-vendor tag is observed. Driven by `state.form.cardUid != null && state.lastSeenTag?.classification is Vendor`.
>
> May require surfacing `lastSeenTag.classification` into `MainUiState` as a new derived field — or adding `state.observedTagKind: Vendor | NonVendor | None`.

#### 2.4.4 Test-only injection

- [ ] `FakeRawWriteUseCase` (returns deterministic `RawWriteResult`).
- [ ] `FakeVendorUidOnlyPairUseCase` (returns deterministic `VendorUidOnlyPairResult`).
- [ ] `MainViewModelTest` ctor extended with the two new use-case parameters; existing tests unaffected because the two new flows are triggered only by `form.rawWriteMode` or vendor classification on the observed tag.

---

### 2.5 Compose UI

#### 2.5.1 `VendorUidOnlyOptInSheet`

- [ ] Lock the Compose surface.

- `ModalBottomSheet` host (per FR-13.2; pattern matches `RepairConfirmSheet` + `PairAnotherTagSheet`).
- **Title** — per Q-U7-13 below.
- **Body lines:**
  - "This tag is encoded and we can't read its contents — but we can map its UID to a Spoolman spool."
  - Optional 2nd line (when `classificationReason != null`): "Tag type: {reason}".
  - Optional 3rd line (existing-spool path): "We'll pair this tag's UID to **{selectedSpoolDisplay}**."
  - Optional 3rd line (new-spool path): "We'll create a new spool with the details above and pair this tag's UID to it."
- **Actions:** `Button` "Pair UID only" (primary) / `TextButton` "Cancel".
- Dismiss-via-scrim treated as Cancel.

> **Q-U7-13** — sheet title?
> - **(a)** "Pair tag UID only?" (FR-4.9 spec literal — verbose)
> - **(b)** "This tag's content can't be written. Pair its UID?" (explanatory)
> - **(c)** "Pair UID only?" (concise, matches the button verb)
>
> **My pick:** (c) — concise and matches the button label; body lines carry the detail.

#### 2.5.2 Raw-write toggle UI

- [ ] Lock the affordance location.

> **Q-U7-14** — where does the toggle live?
> - **(a) Inline checkbox in `FilamentForm`** — `Switch` row above/below the temp panel, labelled "Raw-write mode (no Spoolman)". Always visible; off by default.
> - **(b) Hamburger / overflow menu in TopAppBar** — toggle hidden behind a kebab menu entry "Switch to raw-write mode". Less discoverable but cleaner main-screen UX.
> - **(c) Settings entry only** — toggle in `SettingsScreen`, persists across writes (reverts S-4.7 AC "no global preference").
>
> **My pick:** (b) overflow menu — keeps the main screen visually focused on the standard create-and-pair flow (the dominant path); raw-write is a power-user side mode (per S-4.7 P2 audience). The overflow menu entry toggles to "Switch to standard mode" when raw-write is on. A small chip or banner appears below the form header when raw-write is active.

#### 2.5.3 Sheet hosting in `MainScreen`

- [ ] Show `VendorUidOnlyOptInSheet` when `state.activeFlow is AwaitingVendorOptIn`.
- [ ] Single sheet host pattern carried over from U6b — `BottomSheetHost` selector composable serializes which sheet is visible. Add a third branch for `AwaitingVendorOptIn`.

#### 2.5.4 FAB + form gating

- [ ] `Save & Write` and `Read tag` buttons disabled while `activeFlow !in {Idle}` — extend the existing predicate to cover `WritingRaw` / `AwaitingVendorOptIn` / `PairingVendorUidOnly`.
- [ ] Form fields disabled while `activeFlow !in {Idle, AwaitingVendorOptIn}` — see Q-U7-15.

> **Q-U7-15** — form editable while opt-in sheet visible?
> **My pick:** no, disabled — sheet is modal; making the form editable underneath leads to footgun (user edits form, taps "Pair UID only", expects new values to apply — but the use-case is already running with the pre-edit snapshot). Cancel returns control; user can then edit.

---

### 2.6 ViewModel test plan (Q-T3=B)

#### 2.6.1 `RawWriteUseCaseTest`

- [ ] **happy path** — blank tag → `arm(Write)` invoked once → `Success.Written(uid)`; **no Spoolman calls** (assert via `FakeSpoolmanRepository.callLog`).
- [ ] **vendor-tag rejection** — classifier emits "vendor-tag protected" → `VendorTagRejected(uid)`; no Spoolman calls.
- [ ] **verify-fail** — NfcRepository emits `verify mismatch` → `VerifyFailed`; no Spoolman calls.
- [ ] **generic NFC fail** — any other error → `NfcFailed`; no Spoolman calls.
- [ ] **timeout** — 15 s elapsed without terminal NFC state → `Cancelled("timeout")`; no Spoolman calls.
- [ ] **payload omits `spool_id`** — assert `OpenSpoolPayload.spoolId == null` in the `arm(Write(payload, ...))` argument captured via the fake adapter.

#### 2.6.2 `VendorUidOnlyPairUseCaseTest`

- [ ] **existing-spool happy** — `selectedSpoolId != null` → `appendCardUidToSpool` invoked once → `Success.UidPaired(spoolId, uid, isNewSpool=false)`; **no `nfc.arm` calls** (asserted by ctor — `NfcRepository` not injected).
- [ ] **new-spool happy** — `selectedSpoolId == null` → `createSpoolForNewFilament` invoked once with `cardUid` carrier (per Q-U7-6) → `Success.UidPaired(newSpoolId, uid, isNewSpool=true)`.
- [ ] **move-on-bind precheck** — UID owned by spool A → confirmer fires → on confirm `Moved(fromSpoolIds=[A])` → existing-spool path proceeds; on decline `Declined` → `Cancelled("repair declined")`.
- [ ] **PATCH fail** — `appendCardUidToSpool` returns HttpError → `SpoolmanFailed`.
- [ ] **POST fail** (new-spool path) — `createSpoolForNewFilament` returns HttpError → `SpoolmanFailed`.
- [ ] **move-on-bind partial** — A-side success, B-side fail → `MoveOnBindPartial(uid, partiallyModifiedSpoolId=A, reason)`.

#### 2.6.3 `MainViewModelRawWriteTest`

- [ ] `onRawWriteModeToggled(true)` → `state.form.rawWriteMode == true`.
- [ ] `onSaveAndWriteTapped` with `rawWriteMode=true` → `rawWrite.invoke` called; `createAndPair.invoke` not called.
- [ ] `applyRawWriteResult.Success.Written` → `state.form.rawWriteMode == false` (Q-U7-5); snackbar emitted.
- [ ] `applyRawWriteResult.VendorTagRejected` → snackbar "Vendor tag — write blocked"; `rawWriteMode` preserved.

#### 2.6.4 `MainViewModelVendorOptInTest`

- [ ] **vendor tap captured** — simulate `nfc.lastSeenTag` emit with `classification=Vendor("non-NDEF tag")` → `state.observedTagKind == Vendor` (or equivalent derived field per Q-U7-12).
- [ ] **Save tap fires sheet** — `onSaveAndWriteTapped` with vendor classification + `cardUid` set → `state.activeFlow == AwaitingVendorOptIn(uid)`; neither use-case invoked yet.
- [ ] **confirm runs use-case** — `onVendorOptInConfirmed` → `vendorUidOnlyPair.invoke` called; `state.activeFlow == PairingVendorUidOnly`.
- [ ] **cancel preserves form** — `onVendorOptInCancelled` → `state.activeFlow == Idle`; form preserved; no use-case invoked; snackbar suppressed (Q-U7-11).
- [ ] **success transitions** — `applyVendorUidOnlyPairResult.Success.UidPaired` → `activeFlow == PromptingPairAnother` (Q-U7-9); snackbar "UID paired".
- [ ] **spoolman-fail snackbar** — `applyVendorUidOnlyPairResult.SpoolmanFailed` → snackbar with `humanReadable(outcome)`.

#### 2.6.5 `VendorOptInViewModelTest`

- [ ] UI state hydrated from `AwaitingVendorOptIn` carries the right uid + classificationReason + hasExistingSelection.
- [ ] `onConfirm()` → relays to `MainViewModel.onVendorOptInConfirmed`.
- [ ] `onDismiss()` → relays to `MainViewModel.onVendorOptInCancelled`.

#### 2.6.6 Regression — existing tests

- [ ] `CreateAndPairUseCaseTest`, `TwoTagUseCaseTest` still pass unchanged. U7 introduces no contract changes to U6a/U6b use-cases.

---

### 2.7 Verification commands (post-Code-Gen)

- [ ] `./gradlew compileDebugKotlin` ✅
- [ ] `./gradlew testDebugUnitTest` ✅ — running total target: **281 (U6b) + ~25 (U7) ≈ 306 / 306**.
- [ ] `./gradlew assembleDebug` ✅ — APK size monitored; flagged for U10 if >36 MB (current baseline 35.5 MB after U6b).
- [ ] **No U7 milestone install gate** (Q-T2=B). Manual NFC verification deferred to U10 install gate per `unit-of-work.md` §U7 exit criteria.

#### U10 manual-NFC checklist (will be created in U10) covers

| Scenario | Expected |
|---|---|
| Raw-write — blank tag | Write succeeds; no Spoolman side-effect (verified via Spoolman web UI). |
| Raw-write — vendor tag | "Vendor tag — write blocked" snackbar; no NDEF write attempted. |
| Vendor UID-only — existing spool | Vendor tap → form blank → pick existing spool → Save & Write → opt-in sheet → confirm → spool's `extra.card_uids` includes UID. |
| Vendor UID-only — new spool | Vendor tap → form blank → fill form (no spool selected) → Save & Write → opt-in sheet → confirm → new spool created with `lot_nr = card_uid:<uid>`. |
| Vendor UID-only + move-on-bind | Vendor tag UID previously paired to spool A; tap, opt in to pair to B → RepairConfirmSheet fires → confirm → A loses UID, B gains UID, no NDEF write. |
| Vendor opt-in cancel | Sheet opens → cancel → no Spoolman calls; form intact. |

---

### 2.8 Out-of-scope guards (explicit for U7)

- ❌ Settings UI changes (sort, theme, full banner Retry → U9)
- ❌ Catalogue picker swap (U8)
- ❌ Filament metadata expander (U8 — applies to all forms once it lands)
- ❌ DataStore writes for raw-write mode (FR-4.9 / S-4.7 AC: no persistence)
- ❌ Vendor decoding (U11/v2.1)
- ❌ Per-vendor key Settings (U12/v2.1)
- ❌ `OpenSpoolPayload.fromForm` factory refactor (U10 cleanup)
- ❌ `TwoTagUseCase` extension to route second-tag vendor classification into opt-in sheet (deferred per Q-U7-9 outcome)
- ❌ APK size review / JDK 17 portability fix (U10)
- ❌ New install gate (U10 covers manual NFC verification)

---

## 3. Decision Records (LOCKED 2026-05-27)

User reframed the design mid-Q&A. Final decisions below; original A/B/C
options retained for traceability but answers reflect the simpler design
that emerged from the conversation. **Key reframes**:

- **No raw-write toggle** — raw-write engages automatically when Spoolman
  URL is blank OR `connectivity == Unreachable`. Renders a top banner.
- **No vendor opt-in sheet** — vendor tag staging shows an `AssistChip`
  + helper text on the form. Save & Write routes to the vendor use-case
  without a modal.
- **Terminology** — public-facing copy never uses "UID" (most users won't
  know the term). Use "tag" or "this tag" everywhere.
- **`extra.card_uids` is the truth, not `lot_nr`** — per
  `requirements-delta-extra-fields.md` (approved 2026-05-25). The original
  Q-U7-6 / Q-U7-8 phrasing pre-dates the delta and was incorrect.

**Final answer ledger**:

| # | Decision |
|---|---|
| Q-U7-1 | **A** — `RawWriteInput` wrapper |
| Q-U7-2 | **A** — single `Cancelled(reason: String)` variant |
| Q-U7-3 | **dropped** — no opt-in sheet exists; chip + helper text replaces it |
| Q-U7-4 | **B** — defer `OpenSpoolPayload.fromForm()` factory refactor to U10 |
| Q-U7-5 | **N/A** — no `rawWriteMode` toggle; auto-engages on `url.isBlank()` OR `connectivity == Unreachable` |
| Q-U7-6 | **N/A** — `extra.card_uids` PATCH after POST (same plumbing as create-and-pair); no `lot_nr` carrier |
| Q-U7-7 | **A** — `VendorUidOnlyPairUseCase` ctor strictly omits `NfcRepository` |
| Q-U7-8 | **N/A as posed**; move-on-bind precheck runs before `extra.card_uids` PATCH (same as create-and-pair) |
| Q-U7-9 | **A** — `PairAnotherTagSheet` fires after vendor success, treated as basic new-spool-add minus the NDEF write |
| Q-U7-10 | **A** — single `onSaveAndWriteTapped` handler with internal branching |
| Q-U7-11 | **N/A** — no sheet to cancel |
| Q-U7-12 | **A** — `AssistChip` "Vendor tag — content unreadable" + form helper text "Fill in the details below to link this tag." |
| Q-U7-13 | **dropped** — no sheet |
| Q-U7-14 | **N/A** — no toggle UI |
| Q-U7-15 | **N/A** — no sheet |

### Net new design decisions (D-U7-1..D-U7-5)

Captured during the reframe; not in the original Q-U7 list.

- **D-U7-1** — Vendor tag + Spoolman URL unreachable → snackbar **"Spoolman not reachable — try again when connected."**, form preserved (user can retry once banner clears).
- **D-U7-2** — Vendor tag + no Spoolman URL configured → snackbar **"Spoolman needed to save vendor tag — connect and try again."**, form preserved.
- **D-U7-3** — Raw-write trigger condition: `settings.url.isBlank()` OR `connectivity == Unreachable`. Either condition flips into raw-write mode dynamically (no DataStore persistence).
- **D-U7-4** — Raw-write banner copy:
  - URL blank → **"Writing tag only — Spoolman not configured"**
  - URL set, unreachable → **"Writing tag only — not connected to Spoolman"**
- **D-U7-5** — Save button copy by mode:
  - Standard create-and-pair → **"Save & Write"** (unchanged)
  - Vendor tag staged → **"Save"** (no NDEF write happens)
  - Raw-write mode (no Spoolman) → **"Write to NFC"** (matches v1.7 button label)

---

## 3.0 Original questions (archived)

Original A/B/C options retained for traceability of how the conversation
resolved into the locked decisions above.

---

### Q-U7-1 — RawWriteUseCase input shape

How does `RawWriteUseCase` receive form data?

- **A.** ⭐ Wrap form + overrides in a `RawWriteInput` data class. Symmetric
  with `CreateAndPairInput`; carries `resolvedMaterialName` +
  `newFilamentVendor` overrides cleanly.
- **B.** Use-case takes `FormState` directly. One fewer type; ViewModel
  doesn't construct a wrapper.

**Answer:** ____ A

---

### Q-U7-2 — VendorUidOnlyPairResult.Cancelled shape

How is the cancellation case represented?

- **A.** ⭐ Single `Cancelled(reason: String)` variant. Same shape as
  `CreateAndPairResult.Cancelled` and `TwoTagResult.Cancelled`. VM branches
  on reason if needed.
- **B.** Separate `OptInDeclined` variant distinct from other cancellations.

**Answer:** ____A

---

### Q-U7-3 — Surface vendor classification reason in opt-in sheet?

Should the opt-in sheet show a diagnostic line like "Tag type: {reason}"?

- **A.** ⭐ Yes — small body line under the title. Reason already populated
  by U4 classifier (e.g. "non-OpenSpool MIME", "non-NDEF tag"); useful
  context.
- **B.** No — sheet shows generic copy only. Cleaner.

**Answer:** ____ B, also better messaging about you will have to fill the details of the spool

---

### Q-U7-4 — OpenSpoolPayload.fromForm() factory refactor

Refactor payload construction now (currently duplicated between
`CreateAndPairUseCase.makePayload` and `TwoTagUseCase.derivePayload`)?

- **A.** Refactor now — add `OpenSpoolPayload.fromForm(...)` companion;
  CreateAndPair / TwoTag / RawWrite all switch to it. More churn, less
  duplication.
- **B.** ⭐ Defer to U10 cleanup. U7 duplicates the helper (consistent with
  U6a/U6b). Refactor once final shape is clear.

**Answer:** ____ B

---

### Q-U7-5 — rawWriteMode lifecycle

When does `rawWriteMode` reset to `false`?

- **A.** ⭐ Reset on every successful raw write. Matches S-4.7 AC: "no
  global preference" + "persists across the writing flow only".
- **B.** Persist until user toggles off. Power user can stay in raw mode
  across multiple writes.

**Answer:** ____ There is no raw wirte mode, its just when there is no spoolman configured

---

### Q-U7-6 — NewFilamentRequest cardUid carrier (vendor new-spool path)

For the vendor UID-only **new-spool** path, how do we set `lot_nr`?

- **A.** ⭐ Re-add a `cardUid` carrier to `NewFilamentRequest` so the create
  POST sets `lot_nr = card_uid:<uid>` immediately. (Verify during FD Part 2
  that Spoolman POST actually consumes it.) One round trip; no transient
  unattached spool.
- **B.** Reuse existing two-step: POST without UID → PATCH UID. Matches
  the create-and-pair path. No new field; potential transient state where
  spool exists without UID.

**Answer:** ____ wtf is lot_nr, did you even read the design? its new deidcate feild now. what do you mean how do we set, this will just be new spool adding flow where there is no spool selected from dropdown, messaging is just to let user know we cant read, dont over complicate

---

### Q-U7-7 — VendorUidOnlyPairUseCase NfcRepository dependency

Does the use-case inject `NfcRepository`?

- **A.** ⭐ Strictly omit. Constructor takes only `SpoolmanRepository` +
  `MoveOnBindUseCase`. Type system prevents accidentally arming a write —
  a future careless edit cannot reach `nfc.arm`.
- **B.** Inject for ctor symmetry with `CreateAndPairUseCase`. Runtime-only
  invariant ("don't call arm").

**Answer:** ____A

---

### Q-U7-8 — Move-on-bind ordering in vendor UID-only flow

When does the move-on-bind precheck run?

- **A.** ⭐ Before Spoolman PATCH/POST. Symmetric with
  `CreateAndPairUseCase`. For new-spool path: prevents the same UID
  appearing on two spools transiently (because the new POST sets `lot_nr`
  immediately).
- **B.** After Spoolman PATCH/POST. Simpler ordering; risk of two-spool
  transient state.

**Answer:** ____wtf is lot_nr?

---

### Q-U7-9 — PairAnotherTagSheet after vendor UID-only success?

After a vendor UID-only pair succeeds, do we offer "Pair another tag"?

- **A.** ⭐ Show the sheet. Symmetric with create-and-pair. User might
  want a second tag (which would take the standard write path if blank).
  *Note*: second-tag vendor classification still falls through to the
  current `VendorTagRejected` for v2.0 (extending `TwoTagUseCase` to route
  vendor → opt-in is out of U7 scope).
- **B.** Skip the sheet — vendor UID-only is a one-shot recovery flow.
  User can re-tap manually if they want a second pair.

**Answer:** ____ A, there is nothing special in this, treat this is as just basic new spool adding, dont over complicate, except we dont write back to tag

---

### Q-U7-10 — onSaveAndWriteTapped dispatch shape

How does the Save & Write button route between three flows?

- **A.** ⭐ Single `onSaveAndWriteTapped` handler with internal branching
  (rawWriteMode → vendor → standard). One Save button in UI; routing is
  the VM's job. Keeps UI dumb.
- **B.** Three separate handlers (`onRawWriteTapped`, `onVendorOptInOpened`,
  `onCreateAndPairTapped`). Each button-state pre-decides the route.

**Answer:** ____ A

---

### Q-U7-11 — Vendor opt-in cancel snackbar

When user taps Cancel on the vendor opt-in sheet, do we show a snackbar?

- **A.** ⭐ Suppress. Mirrors UI-12 polish ("Cancel" on RepairConfirm
  suppresses snackbar — user explicitly chose Cancel; no need to confirm).
  Form state preserved.
- **B.** Emit a brief "Cancelled" snackbar so the user has confirmation.

**Answer:** ____ again keeo everything same

---

### Q-U7-12 — Vendor-tag-detected hint chip on main screen?

Between tap (vendor classification) and Save & Write, do we render a hint?

- **A.** ⭐ Yes — small `AssistChip` near the form header: "Vendor tag
  detected — UID-only pair available." Cleared when `form.cardUid` is
  cleared or non-vendor tag observed. Nudges user toward the right mental
  model before they hit Save.
- **B.** No hint. User finds out at Save & Write press when the opt-in
  sheet appears.

**Answer:** ____ A, maybe beeter messaging on button and other palces

---

### Q-U7-13 — VendorUidOnlyOptInSheet title

What does the sheet title say?

- **A.** "Pair tag UID only?" — FR-4.9 spec literal; verbose.
- **B.** "This tag's content can't be written. Pair its UID?" —
  explanatory; says *why* the sheet appeared.
- **C.** ⭐ "Pair UID only?" — concise, matches the primary button verb;
  body lines carry the detail.

**Answer:** ____ its not about writing, tag is already written since its vendor tag, duh! messaging is about we cant read it

---

### Q-U7-14 — Raw-write toggle UI location

Where does the user enable raw-write mode?

- **A.** Inline `Switch` row in `FilamentForm`, labelled "Raw-write mode
  (no Spoolman)". Always visible; off by default. Highest discoverability.
- **B.** ⭐ Overflow menu in TopAppBar — kebab entry "Switch to raw-write
  mode" / "Switch to standard mode". Active-mode chip appears below form
  header when on. Keeps main screen focused on the dominant flow;
  raw-write is a power-user side mode (S-4.7 P2 audience).
- **C.** Settings entry only — toggle in `SettingsScreen`, persists across
  writes. ⚠ Reverts S-4.7 AC ("no global preference") — would also flip
  Q-U7-5.

**Answer:** ____ Again this mode is nothing more than us supportoing user who do not use spoolman

---

### Q-U7-15 — Form editable while opt-in sheet visible?

Can the user edit the form fields underneath the modal opt-in sheet?

- **A.** Yes, editable. Lets user back out and adjust before confirming
  without closing the sheet.
- **B.** ⭐ No, disabled. Sheet is modal. Editable underneath leads to
  footgun (user edits form, taps "Pair UID only", expects new values to
  apply — but the use-case already has the pre-edit snapshot). Cancel
  returns control; user can then edit.

**Answer:** ____ expalin in chat

---

## 4. Stage-Gate Action

After all `[Answer]:` tags above are filled, generate FD artefacts under `aidlc-docs/construction/u7-side-modes/functional-design/`:

| Artefact | Contents |
|---|---|
| `domain-entities.md` | `RawWriteUseCase` + `VendorUidOnlyPairUseCase` types, result hierarchies, `MainUiState` extensions. |
| `business-rules.md` | FR-4.7 NDEF boundary, FR-4.8 raw-write rules, FR-4.9 opt-in flow, FR-5 move-on-bind reuse, S-4.6/S-4.7/S-4.8 AC compliance matrix. |
| `business-logic-model.md` | Sequence diagrams (mermaid) for raw-write happy/error paths, vendor UID-only existing-spool / new-spool / move-on-bind paths. |
| `frontend-components.md` | `VendorUidOnlyOptInSheet`, raw-write toggle affordance, vendor-detected chip, `MainScreen` sheet hosting + state driving. |

Then present the standardized 2-option completion message (Request Changes / Continue to Next Stage) per `construction/functional-design.md`.
