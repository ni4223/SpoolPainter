# U6a — Business Rules

**Unit**: U6a — Create-and-Pair Flow (with folded U2-Δ / U3-Δ / U5-Δ amendments)
**Approved**: Q-U6a-1..15 = A (FD Part 1, 2026-05-25)

Each rule is traced to its source: FR (functional requirement), FR-2-EXT (delta), S (story), or Q (design Q&A). Rules are grouped by area (CP = CreateAndPair use-case, VM = MainViewModel, U2-Δ = primitives amendments, U3-Δ = repository amendments, U5-Δ = read-flow amendments, FE = frontend, T = test obligations).

---

## 1. CreateAndPair use-case (CP)

### CP-1 — `canWrite` precondition (FR-4.1 / S-4.1 / Q-U6a-5)
`CreateAndPairUseCase.invoke()` SHALL be called only when `MainUiState` satisfies:
- `state.form.cardUid != null` (tap-first per Q-U6a-5),
- `state.form.canSubmit == true` (required form fields populated),
- `state.activeFlow == ActiveFlow.Idle`.

`MainViewModel.onWriteTapped` enforces this by checking `canWrite`; the use-case itself MAY assume this contract holds (caller-side gate).

### CP-2 — Branch decision: existing-spool vs new-spool (FR-4.3 / S-4.2)
The use-case SHALL branch on `state.form.selectedSpoolId`:
- non-null → **existing-spool path** (CP-3..CP-5).
- null → **new-spool path** (CP-6..CP-8).

### CP-3 — Existing-spool: Spoolman-first sequencing (FR-4.3)
For the existing-spool path, the use-case SHALL execute in this order:
1. Move-on-bind precheck via `moveOnBindUseCase.invoke(uid, targetSpoolId)`. In U6a, the no-op default returns `Outcome.Proceed` unconditionally (Q-U6a-4=A).
2. `appendCardUidToSpool(targetSpoolId, uid)` — Spoolman PATCH first.
3. `nfc.arm(NfcIntent.Write(payload))` — NDEF write.
4. `nfc.arm(NfcIntent.Verify(payload))` — read-back compare per FR-4.5.

### CP-4 — Existing-spool: payload computation (FR-4.4)
The NDEF payload sent to `arm(Write(...))` SHALL be `OpenSpoolPayloadCodec.toJson(makePayload(form, spoolId = targetSpoolId))`. The `spool_id` field is the **resolved** target spool's id (not 0, not null).

### CP-5 — Existing-spool result mapping
- Verify success → `Success.WrittenAndPaired(targetSpoolId, uid, isNewSpool=false)`.
- Verify mismatch → `VerifyFailed(targetSpoolId, uid, isNewSpool=false, cause=...)`.
- Spoolman PATCH error before NDEF write → `SpoolmanFailed(uid, outcome)`.
- NFC error during write or verify → `NfcFailed(uid, reason)`.

### CP-6 — New-spool: FR-7 chain via `createSpoolForNewFilament`
For the new-spool path, the use-case SHALL:
1. `req = NewFilamentRequest.fromForm(state.form, uid)` (computes density via `MaterialDatabase.densityFor(material)` per spec §"Create Filament").
2. Move-on-bind precheck (no-op in U6a).
3. `createSpoolForNewFilament(req)` — runs the full FR-7 chain inside U3 (vendor → filament → spool).
4. `nfc.arm(Write(payload(spoolId)))` then `nfc.arm(Verify(...))`.

### CP-7 — New-spool: spec-aligned filament POST (FR-2-EXT.2)
`createSpoolForNewFilament` SHALL include `extra.variant = jsonEncode(req.variant)` in the filament POST body **iff** `req.variant != null && req.variant.isNotBlank()`. Empty/null `variant` SHALL be omitted from the POST entirely (per spec §"`variant` Custom Field" — "omitted from create payloads when the variant is unknown or empty").

### CP-8 — New-spool: spec-aligned spool POST (FR-2-EXT.1)
The spool POST body SHALL include `extra.card_uids = ExtraCardUidsCodec.encode(listOf(uid))`. The `lot_nr` field SHALL NOT be set by U6a (FR-2 superseded; legacy encoding gone).

### CP-9 — Verify-fail recovery (FR-4.5)
- New-spool path: when verify fails, the just-created Spoolman record (filament + spool) **persists**. A subsequent retry via `onWriteTapped` SHALL find the new spool via `findSpoolsByCardUid(uid)` (because step 3 of CP-6 already populated `extra.card_uids`) and route through the existing-spool path automatically. No new-spool retry; no rollback POST.
- Existing-spool path: `appendCardUidToSpool` was already committed; retry just retries the NDEF write. Idempotent.

### CP-10 — Idempotency of `appendCardUidToSpool` (Q-U6a-6=A)
If the target's `extra.card_uids` already contains `uid` (decoded set membership), `appendCardUidToSpool` SHALL skip the PATCH and return `Success(spool)` directly. No wasted write.

### CP-11 — Cancellation
Concurrent re-tap of Save while `activeFlow == WritingForPair` SHALL be a no-op at the VM layer (the gate in CP-1 disables Save). The use-case itself does not handle re-entry; it relies on caller-side serialization.

### CP-12 — Timeout (Q-U6a-8=A)
The use-case is wrapped in `withTimeoutOrNull(15_000L)` at the call site (`MainViewModel.onWriteTapped`). On timeout: `nfc.disarm()`, snackbar "No tag tapped — try again", `activeFlow = Idle`. The use-case itself does not own the timeout.

---

## 2. MainViewModel (VM)

### VM-1 — `canWrite` derivation (CP-1)
`canWrite` is a derived `StateFlow<Boolean>` computed from `state.form.cardUid != null && state.form.canSubmit && state.activeFlow == Idle`. Consumed by Save button enablement.

### VM-2 — `form.canSubmit` definition (Q-U6a-7=A)
`FormState.canSubmit` is true iff:
- `material != null`,
- `colorHex != null && colorHex.matches(Regex("^[0-9A-Fa-f]{6}\$"))`,
- `tempRanges` has all four temps populated, with `extruderMin ≤ extruderMax` and `bedMin ≤ bedMax`,
- `name.isNotBlank()` (synthesized field — see FE-1 below; FormState gains a `name` field in U6a or it lives only in `FilamentForm`'s local state).
- `vendorName.isNotBlank()`,
- `diameter > 0`, `weight > 0`.

`variant` is **NOT** required (Q-U6a-7=A).

### VM-3 — `onWriteTapped` flow
On user tap of Save:
1. `if (!canWrite.value) return`.
2. `state.update { activeFlow = WritingForPair }`.
3. `result = withTimeoutOrNull(15_000L) { createAndPair.invoke() } ?: Cancelled("timeout")`.
4. Map `result` per VM-4..VM-9.

### VM-4 — `WrittenAndPaired` result (Q-U6a-9=A)
- Emit `UiEffect.ShowSnackbar("Paired and written")`.
- Reset form to `FormState()` (clears UID, material, brand, colorHex, variant, tempRanges, selectedSpoolId).
- `activeFlow = Idle`.

### VM-5 — `VerifyFailed` result
- Emit `UiEffect.ShowSnackbar("Verify failed. Tap Save to retry.")`.
- **Keep** form (UID stays in form so retry routes through existing-spool path automatically).
- `activeFlow = Idle`.

### VM-6 — `SpoolmanFailed` result
- Emit `UiEffect.ShowSnackbar(outcome.userMessage)` — the outcome's user-facing message (e.g., "Server error: 502").
- Keep form.
- `activeFlow = Idle`.

### VM-7 — `NfcFailed` result
- Emit `UiEffect.ShowSnackbar("NFC error: $reason")`.
- Keep form.
- `activeFlow = Idle`.

### VM-8 — `Cancelled` (timeout) result
- Emit `UiEffect.ShowSnackbar("No tag tapped — try again")`.
- `nfc.disarm()` (defensive — also done by the use-case path; double-call is safe).
- Keep form.
- `activeFlow = Idle`.

### VM-9 — Concurrent flow gating
`onWriteTapped` and `onReadTapped` SHALL NOT execute concurrently. Both gate on `activeFlow == Idle`. Re-entry while non-Idle is silently dropped.

---

## 3. U2 amendments (U2-Δ)

### U2-Δ-RULE-1 — `CardUid.fromBytes` casing (FR-2-EXT.8)
`CardUid.fromBytes(bytes)` SHALL emit uppercase hex via `"%02X".format(it)`. The previous lowercase `%02x` is an error to be fixed.

### U2-Δ-RULE-2 — `CardUid.normaliseHex` contract
`CardUid.normaliseHex(raw)` SHALL uppercase the input and validate it matches `^[0-9A-F]+$`. On mismatch, throws `IllegalArgumentException`. Used by `ExtraCardUidsCodec.decode` and any other UID-from-string parser.

### U2-Δ-RULE-3 — `ExtraCardUidsCodec.encode` (FR-2-EXT.1, wire format)
- Input: `List<CardUid>` (each already uppercase by virtue of `CardUid.fromBytes` / `normaliseHex`).
- Output: `Gson().toJson(uids.joinToString(",") { it.hex })` — a JSON-string-wrapped comma-joined value.
- Empty list input → `"\"\""` (the JSON encoding of empty string).

### U2-Δ-RULE-4 — `ExtraCardUidsCodec.decode` defensive parsing (Q-U6a-1=A, FR-2-EXT.1)
- Accepts both encoded form (`"\"AABBCCDD\""`) and raw form (`AABBCCDD`).
- Strip surrounding `"` if present.
- Empty string after strip → empty list.
- Split on `,`, trim each, drop empties.
- Each entry: uppercase + validate via `CardUid.normaliseHex`. Invalid hex entries are **skipped** (Q-U6a-1=A — skip-and-continue, not throw).
- Output: `List<CardUid>` preserving input order of valid entries.

### U2-Δ-RULE-5 — Legacy `CardUidEncoding.decode/encode` deletion
Methods are deleted; their tests (`CardUidEncodingDecodeTest`, `CardUidEncodingEncodeTest`, `CardUidEncodingRoundTripTest`) are deleted. No production caller remains after U5-Δ-1 lands.

---

## 4. U3 amendments (U3-Δ)

### U3-Δ-RULE-1 — Wire DTO `extra` field
`SpoolmanSpool` and `SpoolmanFilament` SHALL include `extra: Map<String, String>? = null`. Gson deserialiser MUST handle the field as a string-keyed string-valued map (matching Spoolman's `text`-typed extras, which arrive as JSON-encoded strings).

### U3-Δ-RULE-2 — `findSpoolsByCardUid(uid)` (FR-2-EXT.4)
- SHALL fetch `GET /api/v1/spool?limit=1000&allow_archived=true` (single bulk call).
- SHALL filter the results client-side: a spool matches iff `ExtraCardUidsCodec.decode(spool.extra?.get("card_uids") ?: "").contains(uid)`.
- UID comparison SHALL be case-insensitive at decode time (decoder normalises to uppercase per U2-Δ-RULE-4); caller passes a `CardUid` whose `.hex` is already uppercase.
- Empty / unset `card_uids` → spool does not match.
- Returns `Success(matchingSpools)` even when empty (no special "not found" outcome).

### U3-Δ-RULE-3 — `appendCardUidToSpool(spoolId, uid)` (FR-2-EXT.5, CP-10)
1. `GET /spool/{spoolId}` to get full current state.
2. Decode `extra.card_uids`. If `uid` already present, return `Success(spool)` immediately (CP-10 idempotency).
3. Otherwise: `newUids = currentUids + uid`; `newExtra = spool.extra ?: {} + ("card_uids" → encode(newUids))`.
4. `PATCH /spool/{spoolId}` with body `{ "extra": newExtra }` — full extra map (FR-2-EXT.5).
5. Update local cache patch-in-place.
6. On HTTP 400 with body matching `"Unknown extra field"` → call `ensureExtraFieldsRegistered()` → retry once. Second 400 is terminal.

### U3-Δ-RULE-4 — `removeCardUidFromSpool(spoolId, uid)`
Symmetric to U3-Δ-RULE-3. Removes the UID from `card_uids`; if result is empty, sets `extra.card_uids = "\"\""` (preserves field; doesn't drop the key — keeps Spoolman's UI showing the field on spools that once had UIDs).

### U3-Δ-RULE-5 — `createSpoolForNewFilament(req)` (CP-6, CP-7, CP-8)
Three-step chain, each step with lazy-bootstrap retry per U3-Δ-RULE-7:
1. `vendorId = ensureVendor(req.vendorName)`:
   - `GET /vendor` → find by name (case-insensitive per spec §"Create Vendor").
   - If found, reuse id.
   - Else `POST /vendor { "name": req.vendorName }` → returns `{id}`.
2. `filamentId = createFilament(req, vendorId)`:
   - `POST /filament` body per spec §"Create Filament" + `extra.variant = Gson().toJson(req.variant)` iff `req.variant.isNotNullOrBlank` (CP-7).
3. `spoolId = createSpool(filamentId, req.cardUid)`:
   - `POST /spool` body `{ "filament_id": filamentId, "extra": { "card_uids": encode(listOf(req.cardUid)) } }` (CP-8).
4. Return `Success(spoolId)`.

If any step returns 400 "Unknown extra field" → `ensureExtraFieldsRegistered()` → retry that step once.

### U3-Δ-RULE-6 — `ensureExtraFieldsRegistered()` (FR-2-EXT.3 eager half, Q-U6a-13=A)
1. `GET /api/v1/field/spool` → list of registered fields.
2. If `card_uids` not present, `POST /api/v1/field/spool/card_uids` with body:
   ```json
   {"name":"Card UIDs","field_type":"text","order":1,"default_value":"\"\""}
   ```
3. `GET /api/v1/field/filament` → list.
4. If `variant` not present, `POST /api/v1/field/filament/variant` with body:
   ```json
   {"name":"Variant","field_type":"text","order":1,"default_value":"\"\""}
   ```
5. **Does NOT** probe `field_type` of existing entries (Q-U6a-13=A) — type mismatches will surface as 400 on data writes, which is the lazy-bootstrap path.
6. Idempotent — safe to call repeatedly. POST is upsert (Spoolman source: `add_or_update_extra_field`).

### U3-Δ-RULE-7 — Lazy-bootstrap retry helper (FR-2-EXT.3 lazy half)
Any data write (`appendCardUidToSpool`, `removeCardUidFromSpool`, `createSpoolForNewFilament` and its sub-calls that touch `extra`) SHALL be wrapped in a helper that:
1. Executes the call.
2. If the response is HTTP 400 with body matching the regex `Unknown extra field`, calls `ensureExtraFieldsRegistered()` and retries the original call **once**.
3. A second 400 (after retry) is propagated as the original `SpoolmanOutcome.HttpError(...)` outcome. No further retry.

### U3-Δ-RULE-8 — `testConnection()` (FR-2-EXT.7)
- `GET /api/v1/info`.
- Success: returns `SpoolmanOutcome.Success(version: String)` extracted from response body's `version` field.
- Failure: standard `HttpError | NetworkError | ParseError` mapping per Q-U3-10=C.
- Replaces any prior probe endpoint; the existing `SpoolmanRepositoryProbeTest` is rewritten to target `/info`.

### U3-Δ-RULE-9 — `lot_nr` no-app-logic invariant
- `SpoolmanSpool.lot_nr` field stays in DTO for fidelity.
- No app code reads it for app logic.
- `createSpoolForNewFilament` SHALL NOT set `lot_nr` in the POST body.
- Brownfield grep invariant (verified at code-gen): `grep -rn "spool\\.lot_nr\\|lot_nr =" app/src/main/java` returns zero matches outside DTO definitions.

---

## 5. U5 amendments (U5-Δ)

### U5-Δ-RULE-1 — `MainViewModel.onSpoolSelected(spool)` decode source
When `spool != null`:
- `cardUid = ExtraCardUidsCodec.decode(spool.extra?.get("card_uids") ?: "").firstOrNull()`.
- Form is populated from spool's filament; `cardUid` derives from the spool's `extra.card_uids` (was: `lot_nr` decode).
- If `extra.card_uids` is missing/empty, `cardUid` is null in the form.

When `spool == null` (clear): existing rule unchanged — full reset of `FormState`.

### U5-Δ-RULE-2 — `FormMapping` enum rename
`FormMapping.SpoolmanUidSource.FromLotNrOrClear` → `FromCardUidsOrClear`. The `PreserveCurrent` value (used by read-flow auto-prefill) is unchanged.

### U5-Δ-RULE-3 — Parked multi-UID `lot_nr` bug retires
The U5 carry-over bug ("multi-UID `lot_nr` decode breaks dropdown auto-select beyond 2 entries") **is closed** without a code fix. Root cause was the legacy `card_uid:` prefix combined with comma-separation; with `extra.card_uids` (single comma-separated list, no nested prefix), `decode` is unambiguous regardless of UID count.

---

## 6. Frontend — Compose components (FE)

### FE-1 — `FilamentForm` props
```kotlin
@Composable
fun FilamentForm(
    state: FormState,
    nameField: String,
    vendorField: String,
    enabled: Boolean,
    onChange: (FormState, name: String, vendor: String) -> Unit,
    onSave: () -> Unit,
    canSave: Boolean,
)
```
**Note**: `name` and `vendorField` are local to the form (not in `FormState` for now); U6a treats them as composable-local + threaded back via `onChange`. **Open follow-up**: lift into `FormState` if cross-component reads multiply.

### FE-2 — `MaterialPicker` shape (Q-U6a-3 deferred to U8)
- `ExposedDropdownMenuBox` listing strings from `MaterialDatabase.all()` (v1 db preserved until U8).
- Bottom item "Custom…" surfaces an inline `OutlinedTextField` for free-text entry.
- Selected value flows to `FormState.material`. Custom entries instantiate a `Material(name)` placeholder (U8 will replace with catalogue entry).

### FE-3 — `BrandPicker` shape — same pattern as FE-2 over `BrandDatabase.all()`.

### FE-4 — `ColorPicker`
- `OutlinedTextField` accepting 6-char hex (validated via input filter).
- Live `Box(modifier = ...background(Color(parsedHex)))` swatch beside the field.
- Invalid hex → swatch shows checker pattern.

### FE-5 — `TempPanel`
- Two `IntRangeInput` rows (extruder, bed) — each has `min` / `max` fields.
- "Use material defaults" button below: when pressed, fills both ranges from `MaterialDatabase.tempRangesFor(state.material)`. Disabled if `state.material == null`.
- Validation: `min ≤ max` per row enforced at field level (red border on violation).

### FE-6 — `VariantField` (Q-U6a-14=A inline in FilamentForm.kt, Q-U6a-15=A 64 char cap)
- `OutlinedTextField` with label "Variant (optional)".
- `maxLength = 64`.
- Empty/blank value → null on `NewFilamentRequest`.

### FE-7 — Save button placement (Q-U6a-11=A)
- `Button` at bottom of `FilamentForm` (not a FAB).
- Enabled iff `canSave`.
- Layout: form fields scroll; Save button stays at the form's bottom (not a sticky footer in U6a — keep simple; revisit in U9).

### FE-8 — `MainScreen` integration
- Replace `FormPreview` (U5 read-only block) with `FilamentForm` (Q-U6a-10=A).
- Read-flow display: `FilamentForm` rendered with `enabled = false`; user can't edit until they tap Read or clear.
- Write-flow: `FilamentForm` editable; Save button below.
- Add `WritingHint` slot above form: shown when `activeFlow == WritingForPair` AND `nfc.state ∈ {Writing, Verifying}`. Text: "Tap a tag to write…".

### FE-9 — File deletes (v1 carcasses)
- Delete `ui/components/BrandSelector.kt` (replaced by `BrandPicker.kt`).
- Delete `ui/components/MaterialSelector.kt` (replaced by `MaterialPicker.kt`).
- Delete `ui/components/ColorSelector.kt` (replaced by `ColorPicker.kt`).
- Delete `ui/components/TemperatureCard.kt` (replaced by `TempPanel.kt`).

---

## 7. Test obligations (T)

### T-1 — `ExtraCardUidsCodecTest`
- T-1.1 encode 0/1/multiple UIDs → expected wire bytes (`"\"\""`, `"\"AABBCCDD\""`, `"\"AABBCCDD,11223344\""`).
- T-1.2 decode encoded form round-trip.
- T-1.3 decode raw form (no quotes) — defensive parsing.
- T-1.4 decode with whitespace and trailing commas — robust.
- T-1.5 decode lowercase input → uppercase output.
- T-1.6 decode invalid hex entry → skipped (per U2-Δ-RULE-4 / Q-U6a-1=A); valid neighbours preserved.
- T-1.7 decode empty / `"\"\""` / `null`-string-equivalent → empty list.

### T-2 — `CardUidTest`
- T-2.1 `fromBytes([0xAA, 0xBB])` → `CardUid("AABB")` (uppercase).
- T-2.2 `normaliseHex("aabbccdd")` → `"AABBCCDD"`.
- T-2.3 `normaliseHex("AABBCC??")` throws.

### T-3 — Repository tests (all U3-Δ rules above)
- T-3.1 `findSpoolsByCardUid` matches by decoded `extra.card_uids` (single match).
- T-3.2 `findSpoolsByCardUid` includes archived spools.
- T-3.3 `findSpoolsByCardUid` returns empty when no match.
- T-3.4 `appendCardUidToSpool` happy path: PATCH body has full `extra` map with `card_uids` updated.
- T-3.5 `appendCardUidToSpool` idempotent (CP-10): UID already present → no PATCH sent; returns Success.
- T-3.6 `appendCardUidToSpool` lazy-bootstrap retry: 400 "Unknown extra field" → POST `/field/spool/card_uids` → retry → success.
- T-3.7 `removeCardUidFromSpool` happy path: empty result keeps `card_uids = "\"\""`.
- T-3.8 `createSpoolForNewFilament` happy path: vendor reuse + filament POST with `extra.variant` + spool POST with `extra.card_uids`.
- T-3.9 `createSpoolForNewFilament` omits `extra.variant` when `req.variant.isNullOrBlank()`.
- T-3.10 `createSpoolForNewFilament` lazy-bootstrap retry on each step's 400.
- T-3.11 `ensureExtraFieldsRegistered` idempotency: both fields present → no POSTs; one missing → one POST; both missing → two POSTs.
- T-3.12 `testConnection` `/info` happy path returns version.
- T-3.13 `testConnection` HTTP error / network error / parse error mapping.

### T-4 — `MainViewModelTest` (U6a + U5-Δ)
- T-4.1 `onSpoolSelected(spool)` with non-empty `extra.card_uids` decodes UID into form (U5-Δ-RULE-1).
- T-4.2 `onSpoolSelected(spool)` with multi-UID `extra.card_uids` populates first UID (closes parked U5 bug — U5-Δ-RULE-3).
- T-4.3 `onSpoolSelected(spool)` with empty `extra.card_uids` → form has null UID.
- T-4.4 `onSpoolSelected(null)` → full FormState reset.
- T-4.5 `onWriteTapped` happy path (existing-spool): VM → use-case → repository.
- T-4.6 `onWriteTapped` happy path (new-spool): runs full FR-7 chain via use-case.
- T-4.7 `onWriteTapped` `canWrite=false` (no UID) → no-op.
- T-4.8 `onWriteTapped` verify-fail keeps form, snackbar fires.
- T-4.9 `onWriteTapped` Spoolman-fail keeps form, snackbar fires.
- T-4.10 `onWriteTapped` NFC-fail keeps form, snackbar fires.
- T-4.11 `onWriteTapped` 15 s timeout → `Cancelled`, snackbar "No tag tapped — try again".
- T-4.12 `onWriteTapped` after `WrittenAndPaired` → form fully cleared (Q-U6a-9=A).
- T-4.13 Concurrent `onReadTapped` while `WritingForPair` → silently dropped (VM-9).

### T-5 — `CreateAndPairUseCaseTest`
- T-5.1 Existing-spool happy path against fakes.
- T-5.2 New-spool happy path against fakes.
- T-5.3 New-spool verify-fail: record persists in fake repo; retry routes through existing-spool (state-machine simulation).
- T-5.4 `appendCardUidToSpool` idempotent: second invocation no PATCH (fake assertion).
- T-5.5 Move-on-bind no-op: `MoveOnBindUseCase.NoOp` always returns `Outcome.Proceed`.
- T-5.6 Spoolman PATCH error before NDEF write → `SpoolmanFailed`.
- T-5.7 NFC error during write → `NfcFailed`.
- T-5.8 Verify mismatch → `VerifyFailed` with cause string.

### T-6 — Brownfield invariant greps (post-code-gen)
- T-6.1 `grep -rn "CardUidEncoding.decode\|CardUidEncoding.encode" app/src` → zero matches.
- T-6.2 `grep -rn "card_uid:" app/src/main/java` → zero matches (legacy prefix gone).
- T-6.3 `grep -rn "%02x" app/src/main/java/com/spoolpainter/app/domain/primitives/CardUid.kt` → zero matches.
- T-6.4 `grep -rn "spool\\.lot_nr\\|lot_nr =" app/src/main/java` → zero matches outside DTO type definitions.

---

## 8. Rule-trace matrix

| Rule | FR / FR-2-EXT / S | Q-U6a | Code/test owner |
|---|---|---|---|
| CP-1 | FR-4.1 / S-4.1 | Q-U6a-5 | use-case + VM |
| CP-2 | FR-4.3 | — | use-case |
| CP-3..CP-5 | FR-4.3 / FR-4.4 / FR-4.5 / S-4.2 / S-4.4 / S-4.5 | — | use-case |
| CP-6..CP-8 | FR-4.3 / FR-7 / FR-2-EXT.1 / FR-2-EXT.2 | — | use-case |
| CP-9 | FR-4.5 | — | use-case + repo |
| CP-10 | FR-2-EXT.5 | Q-U6a-6 | repo |
| CP-12 | FR-4.1 (latency) | Q-U6a-8 | VM |
| VM-1..VM-9 | FR-4.1 / FR-4.5 / S-4.1..S-4.5 | Q-U6a-7, Q-U6a-9 | VM |
| U2-Δ-RULE-1 | FR-2-EXT.8 | — | primitives |
| U2-Δ-RULE-2 | FR-2-EXT.8 | — | primitives |
| U2-Δ-RULE-3 | FR-2-EXT.1 | — | primitives |
| U2-Δ-RULE-4 | FR-2-EXT.1 | Q-U6a-1 | primitives |
| U2-Δ-RULE-5 | FR-2 supersession | — | primitives |
| U3-Δ-RULE-1 | FR-2-EXT.1, FR-2-EXT.2 | — | DTO |
| U3-Δ-RULE-2 | FR-2-EXT.4 | Q-U6a-12 | repo |
| U3-Δ-RULE-3 | FR-2-EXT.5 | Q-U6a-6 | repo |
| U3-Δ-RULE-4 | FR-2-EXT.5 | — | repo |
| U3-Δ-RULE-5 | FR-2-EXT.1, FR-2-EXT.2 / FR-7 | — | repo |
| U3-Δ-RULE-6 | FR-2-EXT.3 eager | Q-U6a-13 | repo |
| U3-Δ-RULE-7 | FR-2-EXT.3 lazy | — | repo |
| U3-Δ-RULE-8 | FR-2-EXT.7 | — | repo |
| U3-Δ-RULE-9 | FR-2-EXT.1 supersession | — | repo |
| U5-Δ-RULE-1 | FR-2-EXT.1 / FR-3.6 | — | VM |
| U5-Δ-RULE-2 | (rename) | — | VM |
| U5-Δ-RULE-3 | (carry-over closure) | — | retired |
| FE-1..FE-9 | FR-4.1 / FR-8 | Q-U6a-3, 7, 10, 11, 14, 15 | Compose |
