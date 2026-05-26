# U6a — Functional Design Plan

**Stage**: CONSTRUCTION → Per-Unit Loop → Functional Design (U6a)
**Unit**: U6a — Create-and-Pair Flow
**Source artefacts**:
- `aidlc-docs/inception/application-design/unit-of-work.md` §3-U6a
- `aidlc-docs/inception/application-design/components.md` §2.3 (`CreateAndPairUseCase`), §2.5 (`MainViewModel`), §2.8 (`MainScreen`), §3 (cross-component contracts)
- `aidlc-docs/inception/application-design/component-methods.md` §6 (`MainViewModel`), §7 (`MainUiState`), §8 (Compose components)
- `aidlc-docs/inception/application-design/services.md` §3 (Create-and-Pair flow), §4 (FR-7 chain), §6 (write-then-verify)
- `aidlc-docs/inception/requirements/requirements.md` FR-3.6 / FR-4.1 / FR-4.2 / FR-4.3 / FR-4.4 / FR-4.5 / FR-4.6 / FR-4.7 / FR-7.1 / FR-7.2 / FR-7.3 / FR-8 / NFR-1.x / NFR-6
- `aidlc-docs/inception/requirements/requirements-delta-extra-fields.md` (approved 2026-05-25) — **supersedes FR-2; defines FR-2-EXT.1..8**
- `aidlc-docs/inception/user-stories/stories.md` S-4.1 / S-4.2 / S-4.3 / S-4.4 / S-4.5 / S-7.1 / S-7.2 / S-7.3
- Spec reference: `paxx12-snapmaker-u1/spool-link/docs/SPOOLMAN.md`
- Spoolman source pins (verified): `spoolman/api/v1/spool.py:432` (PATCH-replaces-extra), `extra_fields.py:60-66, 134-144` (validator), `field.py:45-72` (registration)

**Workflow note** — this plan **folds U2 / U3 / U5 amendments inside U6a's per-unit loop** per the requirements delta §11. Each amendment is treated as a sub-section here; close-out is a single U6a commit (DoD #6).

---

## 1. Unit Context (Step 1)

### 1.1 Scope (locked by Units Generation §3-U6a + delta §7)

**Core U6a (write flow)**:
- **`CreateAndPairUseCase`** — Spoolman-first sequencing per FR-4.3 / S-4.2:
  - Existing-spool path: spool id = selected spool's id → `appendCardUidToSpool(spoolId, uid)` → `nfc.arm(Write(payload(spoolId)))` → verify (S-4.4 / S-4.5).
  - New-spool path: filament + spool create chain (FR-7) with `extra.variant` on filament + `extra.card_uids` on spool → arm Write payload(newSpoolId) → verify.
  - On verify failure: surface error; new-spool record persists in Spoolman; retry routes through existing-spool path automatically because UID lookup now finds it.
- **`MoveOnBindUseCase`** — **interface only** (impl deferred to U6b). U6a wires the precheck through this interface, ships with no-op default per `unit-of-work.md` §3-U6a ordering note.
- **`MainViewModel.onWriteTapped()`** — invokes the use-case based on `canWrite` gating (FR-4.1 / S-4.1).
- **`MainScreen` write-flow composables** — Save/Write FAB, write-state hint ("Tap a tag to write"), success / verify-fail UI.
- **Compose components — full impl** (U1 skeletons existed; U6a is first real use):
  - `FilamentForm` — name, vendor, material, color, diameter, weight, density, extruder min/max, bed min/max, **`variant`** (already present in v1 UI; wired to Spoolman in U6a per FR-2-EXT.2).
  - `MaterialPicker` — string-based for U6a (catalogue swap is U8 — non-breaking VM injection).
  - `BrandPicker` — string-based for U6a (catalogue swap is U8).
  - `ColorPicker` — hex-string editor + color swatch.
  - `TempPanel` — extruder + bed min/max numeric inputs.

**Folded amendments (per delta §7)**:

- **U2-Δ** — Domain primitives:
  - U2-Δ-1 Delete `CardUidEncoding.decode/encode/Decoded` (legacy `lot_nr` packing). Delete the three test classes that target them.
  - U2-Δ-2 Add `ExtraCardUidsCodec` in `domain/primitives/`.
  - U2-Δ-3 Fix `CardUid.fromBytes`: `%02x` → `%02X` (uppercase per FR-2-EXT.8).
  - U2-Δ-4 Add `CardUid.normaliseHex(raw: String): String` (uppercase + validate).

- **U3-Δ** — Spoolman repository:
  - U3-Δ-1 Wire DTO: `SpoolmanSpool` and `SpoolmanFilament` gain `extra: Map<String, String>?`.
  - U3-Δ-2 `findSpoolsByCardUid(uid)` rewrite — single `GET /spool?limit=1000&allow_archived=true` + client-side substring filter on decoded `extra.card_uids`.
  - U3-Δ-3 `appendCardUidToSpool(spoolId, uid)` — full-`extra` read-modify-write.
  - U3-Δ-4 `removeCardUidFromSpool(spoolId, uid)` — full-`extra` read-modify-write.
  - U3-Δ-5 `createSpoolForNewFilament(req)` — emits `extra.card_uids` on spool POST + `extra.variant` on filament POST (only if non-blank).
  - U3-Δ-6 `ensureExtraFieldsRegistered()` — `GET /field/{spool,filament}` + `POST` missing definitions per delta §5.
  - U3-Δ-7 Lazy retry helper — detects HTTP 400 `"Unknown extra field"`, calls Δ-6, retries once.
  - U3-Δ-8 Connection test → `GET /api/v1/info`.
  - U3-Δ-9 Stop reading/writing `lot_nr` for UID purposes (preserve fidelity in DTO; no app code touches it).

- **U5-Δ** — Read flow tweaks:
  - U5-Δ-1 `MainViewModel.onSpoolSelected(spool)` decodes `extra.card_uids` instead of `lot_nr`. Rename `FormMapping.SpoolmanUidSource.FromLotNrOrClear` → `FromCardUidsOrClear`.
  - U5-Δ-2 Parked multi-UID `lot_nr` dropdown bug retires structurally — no work, just a release note.

### 1.2 Cross-unit consumers (locked by `unit-of-work-dependency.md`)

- **U6b (Move-on-bind + Two-tag)** consumes `MoveOnBindUseCase` interface (impls it); reuses `findSpoolsByCardUid` (now bulk-fetch+filter) for "find other owners" of a UID.
- **U7 (Raw write + Vendor UID-only)** consumes `CreateAndPairUseCase` semantics (Vendor UID-only is a sibling use-case sharing the same Spoolman-first sequencing without NDEF write).
- **U8 (Material/Brand catalogue)** swaps the string-based `MaterialPicker` / `BrandPicker` for catalogue-driven pickers; non-breaking VM injection swap.
- **U9 (Settings)** consumes the existing minimal `SettingsScreen` shipped early in U5 + the connection-test rewiring done by U3-Δ-8 + the `ensureExtraFieldsRegistered` hook from U3-Δ-6.

### 1.3 Out-of-scope for U6a (deferred)

- `MoveOnBindUseCase` **impl** — U6b.
- `TwoTagUseCase`, `PairAnotherTagSheet`, `RepairConfirmSheet` — U6b.
- `RawWriteUseCase`, `VendorUidOnlyPairUseCase`, `VendorOptInViewModel` — U7.
- Catalogue-backed `MaterialPicker` / `BrandPicker` — U8.
- Full `BannerState` derivation, sort order, theme override — U9.
- Instrumented hardware tests — U6 milestone install gate at end of U6b (not U6a).

---

## 2. Plan Steps (checkboxes)

### 2.1 Domain entities — U2-Δ + U6a additions

- [ ] 2.1.1 Lock `ExtraCardUidsCodec` API:
  - `encode(uids: List<CardUid>): String` — comma-join uppercase hex, JSON-string-wrap via `Gson().toJson(...)`. Output for two UIDs is the literal 18-byte string `"\"AABBCCDD,11223344\""`.
  - `decode(value: String): List<CardUid>` — defensive: strip surrounding `"` if present, split `,`, trim, drop empties, validate hex (must match `[0-9A-F]+` after uppercasing), wrap each as `CardUid`. Reject (return empty list? throw? — **Q-U6a-1**) on invalid hex.
  - `decode("")` and `decode("\"\"")` both return `emptyList()`.
- [ ] 2.1.2 Lock `CardUid.normaliseHex` semantics: `fun normaliseHex(raw: String): String` — uppercase, throws `IllegalArgumentException` on non-hex. Used by `decode` and by NFC payload UID extraction (U4 already produces `%02X` after Δ-3, but defensive).
- [ ] 2.1.3 Lock `NewFilamentRequest` shape — what U6a's new-spool path emits to U3 as the FR-7 chain input. Existing v1 already has `FilamentSpool` / `Material` / `Brand` types; U6a's `NewFilamentRequest` carries: `name`, `vendorName`, `material`, `colorHex`, `diameter`, `weight`, `density`, `extruderMin/Max`, `bedMin/Max`, **`variant: String?`** (FR-2-EXT.2), and `cardUid: CardUid` for the spool's `extra.card_uids` seed.
- [ ] 2.1.4 Lock `FormState.variant: String` — already exists in v1 UI per delta §3 FR-2-EXT.2; in U6a's `MainUiState`, `FormState` gains `variant` if not already there. **Q-U6a-2** — does the v2 `FormState` (shipped in U5) already include a `variant` field, or do we add it in U6a?

### 2.2 Use-case — `CreateAndPairUseCase`

- [ ] 2.2.1 Lock `CreateAndPairResult` sealed-type shape:
  - `Success.WrittenAndPaired(spoolId: Int, uid: CardUid, isNewSpool: Boolean)` — happy path.
  - `Success.PairedNoWrite(spoolId: Int, uid: CardUid)` — used by U7's vendor UID-only path; not emitted by U6a but defined here so U7 can reuse the result type. **Q-U6a-3** — emit this from U6a (anticipating U7), or leave for U7?
  - `VerifyFailed(spoolId: Int, uid: CardUid, isNewSpool: Boolean, cause: String)` — write-then-verify mismatch (FR-4.5 / S-4.4). New-spool record persists.
  - `SpoolmanFailed(uid: CardUid, outcome: SpoolmanOutcome<*>)` — append/create call failed.
  - `NfcFailed(uid: CardUid?, reason: String)` — NFC error during write or verify.
  - `MoveOnBindRequired(uid: CardUid, currentOwner: SpoolmanSpool, targetSpoolId: Int)` — U6a's no-op default returns success-without-move; the U6a interface still defines this variant so U6b can light it up. **Q-U6a-4** — does U6a's no-op default just *proceed* (no `MoveOnBindRequired` ever returned), or does it return `MoveOnBindRequired` and let `MainViewModel` handle? My read: just proceed, since "proceed without move" is the no-op semantics.
  - `Cancelled(reason: String)`.
- [ ] 2.2.2 Lock the **existing-spool path** sequence:
  1. Resolve `targetSpoolId = state.spoolman.selectedSpoolId` (must be non-null per `canWrite`).
  2. Compute payload bytes `payload = OpenSpoolPayloadCodec.toJson(makePayload(form, spoolId = targetSpoolId))`.
  3. (Move-on-bind precheck — `moveOnBind.invoke(uid, targetSpoolId)` — no-op in U6a).
  4. `appendCardUidToSpool(targetSpoolId, uid)` — Spoolman-first per FR-4.3.
  5. `nfc.arm(Write(payload))` — collect `state` until terminal.
  6. On success: `nfc.arm(Verify(payload))` — verify identical bytes round-trip.
  7. Return `WrittenAndPaired(targetSpoolId, uid, isNewSpool=false)`.
- [ ] 2.2.3 Lock the **new-spool path** sequence:
  1. Resolve `req = NewFilamentRequest.fromForm(state.form, uid)`.
  2. (Move-on-bind precheck — same no-op).
  3. `createSpoolForNewFilament(req)` — runs FR-7 chain inside U3:
     - `vendorId = ensureVendor(req.vendorName)`,
     - `filamentId = createFilament(req.copy(vendorId), variant=req.variant)`,
     - `spoolId = createSpool(filamentId, extra.card_uids = ExtraCardUidsCodec.encode(listOf(uid)))`.
     - U3 returns `Success(spoolId)` with the appropriate failure modes.
  4. `nfc.arm(Write(payload(spoolId)))` then `nfc.arm(Verify(...))`.
  5. Return `WrittenAndPaired(spoolId, uid, isNewSpool=true)`.
- [ ] 2.2.4 Lock the **UID source** rule:
  - Primary: `state.form.cardUid` (set by either ambient-tag surfacing from U5 or by `onSpoolSelected` decoding `extra.card_uids` per Δ-1).
  - If `state.form.cardUid` is null/blank when `onWriteTapped` fires → enter the "tag-first or arm-Read-then-Write" subflow. **Q-U6a-5** — does U6a require a tap *before* `onWriteTapped` (UID must already be in form), or does `onWriteTapped` itself drive a Read → then Write? Today's flow per S-4.1 implies tap-first (`canWrite` gates on UID presence). My pick: tap-first only — `canWrite = uid != null && form.canSubmit`. Confirm.
- [ ] 2.2.5 Lock **verify-fail recovery semantics** (FR-4.5):
  - New-spool path: created Spoolman record stays. Subsequent retry via the existing-spool path (`onWriteTapped` again with the same UID) finds it via `findSpoolsByCardUid` — *but* only if the new spool's `extra.card_uids` was patched. The new-spool path patches it as part of POST → so retry works.
  - Existing-spool path: `appendCardUidToSpool` already committed before the NDEF write attempt; retry is idempotent (duplicate UID is a no-op per delta §6 / Δ-3). Verify-fail just retries the NDEF write.
  - Both paths: `MainViewModel` surfaces the error via snackbar; UI offers Retry implicit through re-tapping Save.
- [ ] 2.2.6 Lock **idempotency of `appendCardUidToSpool`** (delta §6 / U3-Δ-3): if `uid` already present in target's `extra.card_uids`, the operation is a no-op (no PATCH sent; or PATCH sent with unchanged value — **Q-U6a-6** which?). My pick: no PATCH sent (avoid wasted write), but return `Success(spoolId)` so callers don't branch. Confirm.

### 2.3 ViewModel — `MainViewModel.onWriteTapped`

- [ ] 2.3.1 Lock `canWrite` derivation: `canWrite = state.form.cardUid != null && state.form.canSubmit && state.activeFlow == Idle`.
  - `form.canSubmit` = required fields populated (name, vendor, material, color, diameter, weight, extruder min ≤ max, bed min ≤ max).
  - **Q-U6a-7** — is `variant` required? Spec says "omit when unknown or empty" → optional. My pick: optional. Confirm.
- [ ] 2.3.2 Lock `onWriteTapped` flow:
  1. Set `activeFlow = WritingForPair`.
  2. `result = createAndPair.invoke()` (with timeout — **Q-U6a-8**: same 10 s as U5, or longer for write+verify? My pick: 15 s — write + verify is two NFC ops; bump from 10 s).
  3. On `result`:
     - `WrittenAndPaired` → emit `UiEffect.ShowSnackbar("Paired and written")`; clear form? **Q-U6a-9** — yes, clear form so user is ready for the next pair? Or keep form (in case they want to pair another tag with same details)? My pick: clear form + UID, transition `activeFlow = Idle`.
     - `VerifyFailed` → snackbar "Verify failed. Tap Save to retry."; keep form + UID; `activeFlow = Idle`.
     - `SpoolmanFailed` → snackbar with the outcome's user-facing message; keep form; `activeFlow = Idle`.
     - `NfcFailed` → snackbar; keep form; `activeFlow = Idle`.
     - `Cancelled` → silent; `activeFlow = Idle`.
- [ ] 2.3.3 Lock `MainUiState.activeFlow` extension: `Idle | ReadingForPair | WritingForPair`. No `TwoTag` / `Repairing` yet (U6b adds those).

### 2.4 Compose UI — write-flow surface

- [ ] 2.4.1 Lock `MainScreen` write-flow additions on top of U5's read-flow surface:
  - Existing: TopAppBar, Read FAB, BannerSlot, ReadingHint, UidRow, SpoolmanDropdown, AmbiguityBlock, FormPreview.
  - **U6a additions**:
    - Replace `FormPreview` (read-only) with `FilamentForm` (editable). **Q-U6a-10** — is `FormPreview` deleted, or kept as a separate surface for the Read-only-tag case? My pick: delete; the same `FilamentForm` is used for both display-after-read and write-flow (read-flow leaves it in non-edited state; write-flow lets user edit before Save).
    - Add `WriteFab` (or a Save button — **Q-U6a-11** placement: secondary FAB next to Read FAB, or a Save button at the bottom of the form? My pick: Save button at bottom of form, since FAB is for read; mirrors v1 layout).
    - Add `WritingHint` ("Tap a tag to write…") shown when `activeFlow == WritingForPair` and `nfc.state == Writing | Verifying`.
- [ ] 2.4.2 Lock `FilamentForm` component shape:
  - `data class FilamentFormState` (already in U5's `MainUiState.form`?) — needs `variant: String` per FR-2-EXT.2.
  - Children: `NameField`, `VendorField` (inline pickup), `MaterialPicker`, `ColorPicker`, `DiameterField`, `WeightField`, `DensityField`, `ExtruderTempPanel`, `BedTempPanel`, `VariantField` (optional, free-text).
  - Read-only mode (used during read-flow display): renders same components with `enabled=false`.
- [ ] 2.4.3 Lock `MaterialPicker` U6a shape: dropdown (string list from `MaterialDatabase`) + free-text "Custom" entry (lets user type any string). U8 will replace with catalogue-backed picker.
- [ ] 2.4.4 Lock `BrandPicker` U6a shape: dropdown (string list from `BrandDatabase`) + free-text. U8 swap mirrors `MaterialPicker`.
- [ ] 2.4.5 Lock `ColorPicker` U6a shape: hex string editor with live swatch preview. v1 `ColorSelector` is the reference (will be rewritten as `ColorPicker.kt`; v1 file deleted).
- [ ] 2.4.6 Lock `TempPanel` U6a shape: two paired `IntRangeInput` fields (min, max) with material-default fill button. v1 `TemperatureCard` is the reference.

### 2.5 U2-Δ — domain primitives amendments

- [ ] 2.5.1 Delete `CardUidEncoding.decode(lotNr)`, `encode(uids)`, and the `Decoded` data class. Delete the three test classes (`CardUidEncodingDecodeTest`, `CardUidEncodingEncodeTest`, `CardUidEncodingRoundTripTest`).
- [ ] 2.5.2 Implement `ExtraCardUidsCodec.kt` per §2.1.1 (encode + decode).
- [ ] 2.5.3 Fix `CardUid.fromBytes`: `%02x` → `%02X`. Update affected tests.
- [ ] 2.5.4 Add `CardUid.normaliseHex(raw)` per §2.1.2.
- [ ] 2.5.5 Test plan for U2-Δ:
  - `ExtraCardUidsCodecTest` — encode/decode round-trip; defensive parsing (raw vs JSON-wrapped); empty cases (`""`, `"\"\""`); single UID; multiple UIDs; lowercase input normalised; invalid hex rejected.
  - `CardUidTest` casing tests rewritten for `%02X`.
  - Net test delta: estimated -3 files / -38 cases removed (legacy encoding tests), +1 file / +12 cases added (`ExtraCardUidsCodecTest`).

### 2.6 U3-Δ — Spoolman repository amendments

- [ ] 2.6.1 Wire DTO changes — `SpoolmanSpool.extra: Map<String, String>?`; `SpoolmanFilament.extra: Map<String, String>?`. Default null; null-safe everywhere.
- [ ] 2.6.2 `findSpoolsByCardUid(uid)` — single bulk fetch with `limit=1000&allow_archived=true`. **Q-U6a-12** — what if the user has >1000 spools? My pick: ignore for v2.0 (Q-U3-1 era's exhaustive plan can be revisited); document as a known limitation. Confirm.
- [ ] 2.6.3 `appendCardUidToSpool(spoolId, uid)`:
  1. GET spool by id.
  2. Decode `extra.card_uids` → if `uid` present, return `Success(spool)` no-op.
  3. Otherwise add `uid` → re-encode → PATCH **full extra** (preserve any other extra keys; today only `card_uids` lives on Spool, but future-proof).
  4. On 400 "Unknown extra field" → call `ensureExtraFieldsRegistered()` → retry once.
  5. Update local cache patch-in-place per Q-U3-2.
- [ ] 2.6.4 `removeCardUidFromSpool(spoolId, uid)` — symmetric to Δ-3.
- [ ] 2.6.5 `createSpoolForNewFilament(req)`:
  1. Vendor: `vendorId = vendors.find { name == req.vendorName } ?: POST /vendor`.
  2. Filament: `POST /filament` with body per spec §"Create Filament" + `extra.variant` if `req.variant.isNotBlank()`.
  3. Spool: `POST /spool` with `filament_id = filamentId` + `extra.card_uids = encode(listOf(req.cardUid))`.
  4. On 400 "Unknown extra field" anywhere in chain → bootstrap → retry the failed step once.
- [ ] 2.6.6 `ensureExtraFieldsRegistered()`:
  - `GET /field/spool` → check if `card_uids` present → if not, `POST /field/spool/card_uids` with body per delta §5.
  - Same for `filament/variant`.
  - Idempotent; safe to call many times.
  - **Q-U6a-13** — should this check also probe `field_type` matches `text` and reject mismatches? My pick: no, just check key existence; field_type mismatches will surface as 400 on the actual data write. Confirm.
- [ ] 2.6.7 Lazy retry helper — extract a `executeWithExtraFieldsBootstrap` that wraps any call returning 400 "Unknown extra field". Used by Δ-3 / Δ-4 / Δ-5.
- [ ] 2.6.8 `testConnection()` — `GET /api/v1/info`; success returns `Success(version)`; failure returns `HttpError | NetworkError | ParseError`. Settings → Test Connection consumes this directly.
- [ ] 2.6.9 Remove `lot_nr` writes from `createSpoolForNewFilament`; `lot_nr` field on the wire DTO stays for fidelity. Verify no other code reads `spool.lot_nr` for app logic (U5-Δ-1 closes the only such reader).
- [ ] 2.6.10 Test plan for U3-Δ:
  - `SpoolmanRepositoryFindByCardUidTest` rewritten — bulk fetch + filter on `extra.card_uids`; archived included; substring on encoded value; lowercase-input normalised before compare.
  - `SpoolmanRepositoryAppendCardUidTest` rewritten — full-`extra` PATCH; idempotent on duplicate; preserves other extras; lazy bootstrap retry on 400.
  - `SpoolmanRepositoryRemoveCardUidTest` rewritten — symmetric.
  - `SpoolmanRepositoryCreateChainTest` rewritten — emits `extra.card_uids` + `extra.variant`; lazy bootstrap retry on each step's 400.
  - **NEW** `SpoolmanRepositoryEnsureFieldsTest` — idempotency; missing-field POST; both-fields-already-registered short-circuit; field_type mismatch path (returns Success since we don't probe type per Q-U6a-13).
  - **NEW** `SpoolmanRepositoryConnectionTestTest` — `/info` happy path; surfaces version; HTTP error; network error.
  - Net test delta: estimated +25 cases.

### 2.7 U5-Δ — read-flow tweaks

- [ ] 2.7.1 `MainViewModel.onSpoolSelected(spool)`:
  - `cardUid = ExtraCardUidsCodec.decode(spool.extra?.get("card_uids") ?: "").firstOrNull()`.
  - `null` selection clears form + UID per existing rule.
- [ ] 2.7.2 Rename `FormMapping.SpoolmanUidSource.FromLotNrOrClear` → `FromCardUidsOrClear`. Update read-flow's `PreserveCurrent` callers (no semantic change there).
- [ ] 2.7.3 Test plan for U5-Δ:
  - 2 existing VM cases updated (`onSpoolSelected_non_null_with_lot_nr_decodes_UID_into_form` → `..._with_card_uids_decodes_UID_into_form`).
  - +1 case for multi-UID spool: selecting a spool with 3 UIDs in `extra.card_uids` populates form with `firstOrNull()` (consistent with existing single-UID behaviour). This is what retires the parked U5 multi-UID bug.
  - Net test delta: 0 cases (renamed, +1, -1 retired).

### 2.8 Frontend components — Compose surface

- [ ] 2.8.1 Component file plan:
  - **Create**: `FilamentForm.kt`, `MaterialPicker.kt`, `BrandPicker.kt`, `ColorPicker.kt`, `TempPanel.kt`, `VariantField.kt` (or fold into `FilamentForm.kt` — **Q-U6a-14**).
  - **Delete**: `BrandSelector.kt`, `MaterialSelector.kt`, `ColorSelector.kt`, `TemperatureCard.kt` (v1 component carcasses).
  - **Modify**: `MainScreen.kt` (replace `FormPreview` integration with `FilamentForm`; add `WritingHint`; add Save button).
- [ ] 2.8.2 `FilamentForm` props:
  - `state: FormState`, `enabled: Boolean`, `onChange: (FormState) -> Unit`, `onSave: () -> Unit`, `canSave: Boolean`.
- [ ] 2.8.3 `VariantField`:
  - `OutlinedTextField` with label "Variant (optional)".
  - Free text; max length 64 (Spoolman key constraint is on key, not value, but pick a sane cap — **Q-U6a-15** my pick: 64).
  - Empty string surfaces as `null` to `NewFilamentRequest`.
- [ ] 2.8.4 `MaterialPicker` — `ExposedDropdownMenuBox` over `MaterialDatabase.all()` + "Custom…" item that opens an inline text input.
- [ ] 2.8.5 `BrandPicker` — same shape as `MaterialPicker` over `BrandDatabase.all()`.
- [ ] 2.8.6 `ColorPicker` — `OutlinedTextField` for hex (`AABBCC`), live `Box` swatch with `Color(0xFFAABBCC)` parse.
- [ ] 2.8.7 `TempPanel` — `IntRangeInput` × 2 (extruder, bed); "Use material defaults" button populates from `MaterialDatabase`.

### 2.9 ViewModel test plan (Q-T3=B)

- [ ] 2.9.1 `MainViewModelTest` additions:
  - `onWriteTapped_existingSpool_writesAndPairs` — selectedSpool set → calls `appendCardUidToSpool` then `nfc.arm(Write)` then `nfc.arm(Verify)`.
  - `onWriteTapped_newSpool_runsCreateChainThenWrites` — no selectedSpool → `createSpoolForNewFilament` then write+verify.
  - `onWriteTapped_verifyFailed_keepsForm` — verify mismatch → form preserved; snackbar emitted.
  - `onWriteTapped_canWriteFalse_isNoOp` — no UID → button disabled; invocation rejected.
  - `onWriteTapped_writeTimeout_disarmsAndSnackbar` — 15 s timeout → disarm + "No tag tapped" snackbar.
  - `onWriteTapped_spoolmanAppendFails_keepsForm`.
  - `onWriteTapped_appendIdempotent_secondTapNoOp` — UID already in `extra.card_uids` → no PATCH but Success returned.
- [ ] 2.9.2 `CreateAndPairUseCaseTest` (against fakes):
  - Existing-spool happy path.
  - New-spool happy path (full FR-7 chain).
  - Verify-fail recovery: existing-spool retry idempotent; new-spool record persists for retry.
  - `appendCardUidToSpool` 400 "Unknown extra field" → bootstrap → retry path tested.
  - Move-on-bind no-op: U6a stub proceeds without invoking U6b.
  - Cancellation propagation.

### 2.10 Verification commands (post-Code-Gen)

- [ ] 2.10.1 `compileDebugKotlin` — clean build.
- [ ] 2.10.2 `testDebugUnitTest` — full suite passes; expected count: U5's 232 + Δ adjustments. Estimate: -3 (legacy encoding tests deleted) -38 (legacy encoding cases) +12 (`ExtraCardUidsCodecTest`) +25 (U3-Δ) +0 (U5-Δ net) +~40 (U6a use-case + VM + UI) ≈ **271** total. Actual count locked at code-gen time.
- [ ] 2.10.3 `assembleDebug` — APK builds. Size growth from U6a UI components is expected.
- [ ] 2.10.4 Brownfield invariant greps:
  - `grep -rn "CardUidEncoding.decode\|CardUidEncoding.encode\|fromOpenSpool\|class SpoolmanService\|class NfcManager\|class NfcController\|class NfcHandler" app/src` → zero matches.
  - `grep -rn "lot_nr" app/src/main/java` — all matches must be DTO fidelity only (no app logic).
  - `grep -rn "%02x" app/src/main/java/com/spoolpainter/app/domain/primitives/CardUid.kt` → zero matches.
  - `grep -rn "card_uid:" app/src/main/java` → zero matches (legacy prefix gone).
- [ ] 2.10.5 No milestone install gate at U6a end (per Q-T2=B / `unit-of-work.md` §2). Install gate is end-of-U6b.

### 2.11 Out-of-scope guards (explicit for U6a)

- [ ] 2.11.1 No `MoveOnBindUseCase` impl beyond no-op default (U6b).
- [ ] 2.11.2 No `TwoTagUseCase` (U6b).
- [ ] 2.11.3 No `RawWriteUseCase` / `VendorUidOnlyPairUseCase` (U7).
- [ ] 2.11.4 No catalogue-backed `MaterialPicker` / `BrandPicker` (U8).
- [ ] 2.11.5 No `BannerState` derivation beyond U5's existing surface (U9).
- [ ] 2.11.6 No instrumented Android tests (manual verification at U6 milestone install gate after U6b).

---

## 3. Decision Records (open questions Q-U6a-1 .. Q-U6a-15)

| ID | Question | Recommended (A) | Decision |
|---|---|---|---|
| Q-U6a-1 | `ExtraCardUidsCodec.decode` on invalid hex: throw, or skip-and-continue? | **A** — skip-and-continue (filter invalid; log warning); robust against forward-compat field reuse | **A** (accepted 2026-05-25 via "accepted") |
| Q-U6a-2 | Does v2 `FormState` already include `variant`? | A — verify and add if not (assume not; U5 didn't ship it) | **VERIFIED — already exists** as `FormState.variant: String? = null` in `MainUiState.kt:27`; sourced from OpenSpool `subtype` via `FormMapping.kt:70`; currently rendered read-only at `MainScreen.kt:269`. U6a's task: make it editable + persist to Spoolman. No field-add needed. |
| Q-U6a-3 | `CreateAndPairResult.Success.PairedNoWrite` defined in U6a or U7? | **A** — U7; keep U6a's result type minimal | **A** |
| Q-U6a-4 | Move-on-bind no-op: just proceed, or return `MoveOnBindRequired`? | **A** — just proceed (caller never sees it in U6a) | **A** |
| Q-U6a-5 | UID source: tap-first (UID must be in form before Save) or Read-then-Write subflow on Save? | **A** — tap-first; matches v1 behaviour and `canWrite` gating | **A** |
| Q-U6a-6 | `appendCardUidToSpool` idempotent: send PATCH with unchanged value, or skip PATCH entirely? | **A** — skip PATCH (no wasted write); return `Success(spool)` | **A** |
| Q-U6a-7 | Is `variant` required for `canSubmit`? | **A** — optional (spec omits when unknown/empty) | **A** |
| Q-U6a-8 | Write+verify timeout duration | **A** — 15 s (longer than U5's 10 s; covers two NFC ops) | **A** |
| Q-U6a-9 | After `WrittenAndPaired`: clear form, or keep? | **A** — clear form + UID; activeFlow = Idle | **A** |
| Q-U6a-10 | Delete `FormPreview`, or keep separate from `FilamentForm`? | **A** — delete; `FilamentForm` with `enabled=false` covers display | **A** |
| Q-U6a-11 | Save button placement: secondary FAB, or button at bottom of form? | **A** — button at bottom of form (FAB reserved for Read) | **A** |
| Q-U6a-12 | >1000 spools: what happens? | **A** — ignore for v2.0; document as known limitation | **A** |
| Q-U6a-13 | `ensureExtraFieldsRegistered` probes `field_type` match? | **A** — no, just key existence; type-mismatch surfaces on data write 400 | **A** |
| Q-U6a-14 | `VariantField` in its own file or inside `FilamentForm.kt`? | **A** — inline in `FilamentForm.kt` (small enough); split only if it grows | **A** |
| Q-U6a-15 | `variant` max length cap | **A** — 64 chars (matches Spoolman key length cap; sane for free text) | **A** |

---

## 4. Approval gate

This Functional Design Part 1 plan is pending user approval. On approval, FD Part 2 generates:

- `aidlc-docs/construction/u6a-create-and-pair-flow/functional-design/{domain-entities,business-rules,business-logic-model,frontend-components}.md` for the U6a body.
- Three additional sections in `business-rules.md` covering U2-Δ, U3-Δ, U5-Δ rule changes.

After FD Part 2 approval, Code Generation Part 1 writes:

- `aidlc-docs/construction/plans/u6a-create-and-pair-flow-code-generation-plan.md` covering all of: U6a code, U2-Δ code, U3-Δ code, U5-Δ code in one ordered checkbox list.

After Code Generation Part 2 approval, the close-out commit bundles everything per delta §11.
