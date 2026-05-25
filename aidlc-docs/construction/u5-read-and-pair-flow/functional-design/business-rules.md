# U5 — Business Rules

**Stage**: CONSTRUCTION → Functional Design Part 2 (artefact)
**Source plan**: `aidlc-docs/construction/plans/u5-read-and-pair-flow-functional-design-plan.md` (approved 2026-05-25)
**Companion artefacts**:
- `domain-entities.md` — type shapes
- `business-logic-model.md` — state machine + sequence diagrams
- `frontend-components.md` — Compose surface

Each rule is technology-agnostic. Implementation uses Kotlin / Hilt / Compose; that's the Code Generation plan's concern.

Rules are organised by surface. IDs are stable for cross-reference from tests, code review, and downstream units.

---

## BR-U5-RP — `ReadAndPairUseCase` rules

### BR-U5-RP-1 — One-shot invocation contract

`invoke()` is suspend, single-shot, returns one `ReadAndPairResult`. The use-case owns no long-lived state. The caller (`MainViewModel`) owns the `ActiveFlow.ReadingForPair` UI state for the duration of the call.

### BR-U5-RP-2 — Tag-first vs button-first ordering

Always attempt `nfc.consumeLastSeen(NfcIntent.Read)` first. If it returns non-null, use that result and skip `arm(Read)`. Otherwise call `nfc.arm(NfcIntent.Read)` and await terminal `nfc.state`.

### BR-U5-RP-3 — Terminal-state collection

After `arm(Read)`, observe `nfc.state` and suspend until `state.first { it is NfcResult.Success || it is NfcResult.Error }` returns. Skip non-terminal states (`Idle`, `Reading`, `Writing`, `Verifying`).

### BR-U5-RP-4 — Read-then-Spoolman-lookup is unconditional on classification

The use-case always calls `spoolman.findSpoolsByCardUid(uid)` after a successful NFC read, regardless of `TagClassification`. Vendor and Blank classifications still query Spoolman because:
1. Vendor tags can be paired by UID-only (FR-4.9; relevant in U7).
2. Blank tags may have prior Spoolman pairings (e.g., user re-reads after a remove).

### BR-U5-RP-5 — Branch table (final result selection)

Given `(classification, spoolmanResult)` after BR-U5-RP-4. Note: 0-match × OpenSpool may be revised by the `spool_id` fallback (BR-U5-RP-13).

| classification | spoolman matches | result |
|---|---|---|
| any | exactly 1 match | `Success.PrefillFromSpoolman(uid, spool, classification)` |
| `OpenSpool(payload)` | 0 matches | `Success.PrefillFromTag(uid, payload)` *unless* BR-U5-RP-13 fallback resolves a spool, in which case `Success.PrefillFromSpoolman(uid, spool, classification)` |
| `Blank` | 0 matches | `Success.BlankForm(uid, Blank)` |
| `Vendor(reason)` | 0 matches | `Success.BlankForm(uid, Vendor(reason))` |
| any | ≥ 2 matches | `Ambiguous(uid, matches, classification)` |
| any | `SpoolmanOutcome` non-success (excluding URL-not-configured per BR-U5-RP-7) | `SpoolmanFailed(uid, classification, outcome)` |

### BR-U5-RP-6 — Spoolman vs OpenSpool collision rule

If classification is `OpenSpool(payload)` AND Spoolman returns exactly 1 match, **the Spoolman match wins**. The `OpenSpoolPayload` is dropped from the result. Rationale: Spoolman is the source of truth for paired spools; FR-3.3's single-match branch is unconditional.

### BR-U5-RP-7 — URL-not-configured short-circuit

When `findSpoolsByCardUid` returns `NetworkError(cause)` AND `cause is UrlNotConfigured`, the use-case treats the call as **0 Spoolman matches** and proceeds via BR-U5-RP-5. No `SpoolmanFailed` result is emitted; no banner activity, no snackbar. (Preserves S-10.1 + Q-CD1.1=A banner suppression rule.)

The discriminator is the presence of `UrlNotConfigured` as the `NetworkError.cause` (see U3 plan §2.2.5). Any other `NetworkError`, `HttpError`, `ParseError` → `SpoolmanFailed`.

### BR-U5-RP-8 — NFC error short-circuit

If the NFC read returns `NfcResult.Error(reason, cause?)`, the use-case **does not** call `findSpoolsByCardUid`. Result is `NfcFailed(reason)`.

### BR-U5-RP-9 — Empty-UID defensive mapping

If `NfcResult.Success(uid, classification)` arrives with `uid.hex.isEmpty()` (zero-length UID — should not occur on real NFC-A/B/F/V hardware), the use-case maps to `NfcFailed("zero-length UID — non-NFC-A tag?")` without calling Spoolman. (Note: `NfcRepository` already maps zero-length UID to `Error` per BR-U4-UID-2; this is belt-and-braces.)

### BR-U5-RP-10 — Cancellation propagation

Caller cancellation (e.g., `viewModelScope` cancelled) propagates through suspending operations. The use-case does **not** catch `CancellationException`. The VM's invocation site is responsible for converting cancellation into `Cancelled(reason)` if it surfaces it to UI.

### BR-U5-RP-11 — No retry, no backoff

`invoke()` makes at most one NFC read attempt and at most one Spoolman call. Retry is a UX-layer responsibility (the Read button). No exponential backoff, no automatic re-arm on transient errors.

### BR-U5-RP-12 — No side effects on Spoolman beyond `findSpoolsByCardUid` (and `getSpool` per BR-U5-RP-13)

`invoke()` MUST NOT issue PATCH or POST. (S-3.3 [unit] AC: "No PATCH/POST is issued in this state.") Caches read by `spoolman.spools` etc. are observed read-only. The optional `getSpool(id)` fallback in BR-U5-RP-13 is GET-only.

### BR-U5-RP-13 — `spool_id` fallback when UID lookup returns 0 matches (Q-U5-12=A)

After `findSpoolsByCardUid(uid)` returns `Success(emptyList())` AND classification is `OpenSpool(payload)`:
1. Parse `payload.spoolId?.toIntOrNull()`. If null → fall through to `Success.PrefillFromTag(uid, payload)` (BR-U5-RP-5 behaviour).
2. Otherwise call `spoolman.getSpool(spoolId)`:
   - `Success(spool)` → `Success.PrefillFromSpoolman(uid, spool, classification)`. Equivalent to a UID-match outcome — `MainViewModel.applyResult` projection is identical.
   - `HttpError(404, ...)` → fall through to `Success.PrefillFromTag(uid, payload)` (the spool was deleted / renumbered; treat as if the fallback didn't fire).
   - Any other non-success outcome (`HttpError(other)` / `NetworkError` (not URL-not-configured) / `ParseError`) → `SpoolmanFailed(uid, classification, outcome)`. Same snackbar surface as BR-U5-VM-3.
3. URL-not-configured does not reach this rule (BR-U5-RP-7 short-circuits the UID lookup itself; in that path the use-case never invokes `getSpool` either, since the fallback fires only after a `Success(emptyList())` outcome).

**Reason**: Recovers paired-spool metadata when v1-era tags carry `spool_id` but the corresponding spool's `lot_nr` was never updated to include `card_uid:`. UID lookup remains the **primary** key (FR-3.1); the `spool_id` fallback is a strict secondary lookup.

**No binding side effect**: This rule changes which Spoolman record the form **prefills from**. It does NOT bind the tag's UID to the spool — pairing (`card_uid:` insertion in `lot_nr`) remains U6a's `appendCardUidToSpool` responsibility on Write. The dropdown's `selectedSpoolId` is set to the resolved `spool.id` so a subsequent U6a write sequence can take the existing-spool path naturally.

### BR-U5-RP-14 — Vendor classification does NOT trigger `spool_id` fallback

`Vendor(reason)` and `Blank` classifications never carry an `OpenSpoolPayload`. They retain the BR-U5-RP-5 behaviour unconditionally (`BlankForm` on 0 matches). This avoids attempting to read `spool_id` from a tag we already classified as non-OpenSpool.

---

## BR-U5-VM — `MainViewModel` rules

### BR-U5-VM-1 — `onReadTapped` contract (revised 2026-05-25)

When `onReadTapped()` is called:
1. If `state.value.activeFlow == ReadingForPair`: invoke BR-U5-VM-2 (re-tap).
2. Else: set `activeFlow = ReadingForPair`; launch `readAndPair.invoke()` on `viewModelScope` **wrapped in `withTimeoutOrNull(readTimeoutMs)`** (default 10 s).
   - On result returned within timeout → route through BR-U5-VM-3.
   - On `null` (timeout fired) → call `nfc.disarm()`; reset `activeFlow = Idle`; emit `UiEffect.ShowSnackbar("No tag tapped — try again")`. The "Tap a tag to read…" hint clears.

The timeout protects against the persistent-hint bug surfaced by the U5 install gate: without a fuse, `nfc.state.first { Success || Error }` could suspend indefinitely if no tap arrived (or the user navigated away mid-read). Q-U5-1 originally recommended option A "no timeout" since timeout was framed as "U9/U10 polish"; install-gate evidence promoted it to a U5 concern.

### BR-U5-VM-2 — Re-tap-while-armed

When `onReadTapped()` is called while `activeFlow == ReadingForPair`:
1. Cancel the in-flight invocation (Job from BR-U5-VM-1).
2. Call `nfc.disarm()`.
3. Re-enter BR-U5-VM-1 step 2.

(`NfcRepository.arm(Read)` from non-Idle is itself idempotent per BR-U4-SM-3; the explicit `disarm()` is for symmetry / to preserve clean transitions in `nfc.state`.)

### BR-U5-VM-3 — Result → `MainUiState` mapping

| Result | State updates | UiEffect |
|---|---|---|
| `PrefillFromSpoolman(uid, spool, _)` | `form` ← `SpoolmanSpool → FormState` mapping (§3.1 of `domain-entities.md`) with `cardUid = uid`, `selectedSpoolId = spool.id`. `spoolman.selectedSpoolId = spool.id`. `ambiguity = null`. `activeFlow = Idle`. | none |
| `PrefillFromTag(uid, payload)` | `form` ← `OpenSpoolPayload → FormState` mapping (§3.2) with `cardUid = uid`. `ambiguity = null`. `activeFlow = Idle`. | none |
| `BlankForm(uid, _)` | `form` ← `BlankForm` projection (§3.3) with `cardUid = uid`. `ambiguity = null`. `activeFlow = Idle`. | none |
| `Ambiguous(uid, matches, classification)` | `form` ← `BlankForm` projection (no auto-prefill). `ambiguity = AmbiguityState(uid, matches, classification)`. `activeFlow = Idle`. | none |
| `SpoolmanFailed(_, _, outcome)` | `activeFlow = Idle`. (`form`, `ambiguity`, `spoolman.selectedSpoolId` unchanged.) | `ShowSnackbar(humanReadable(outcome))` |
| `NfcFailed(reason)` | `activeFlow = Idle`. | `ShowSnackbar(reason)` |
| `Cancelled(_)` | `activeFlow = Idle`. (No `form` change.) | none |

### BR-U5-VM-4 — `humanReadable(SpoolmanOutcome)` mapping

| Outcome | Snackbar text |
|---|---|
| `HttpError(code, message)` | `"Spoolman returned $code: $message"` |
| `NetworkError(cause)` (cause is not `UrlNotConfigured`) | `"Could not reach Spoolman: ${cause.message ?: cause::class.simpleName}"` |
| `ParseError(cause)` | `"Spoolman response could not be parsed"` |
| `NetworkError(UrlNotConfigured)` | (never reaches snackbar — consumed by BR-U5-RP-7) |

### BR-U5-VM-5 — `onSpoolSelected(spool: SpoolmanSpool?)` contract

| Argument | Behaviour |
|---|---|
| non-null AND `spool.id != state.form.selectedSpoolId` | Apply `SpoolmanSpool → FormState` mapping (§3.1) preserving `cardUid` and `rawWriteMode`. Clear `ambiguity`. Update `spoolman.selectedSpoolId`. |
| non-null AND `spool.id == state.form.selectedSpoolId` | No-op (idempotent re-select per Q-U5-6 plan §2.3.6). |
| `null` | Apply `onSpoolSelected(null)` clear (§3.4) preserving `cardUid` and `rawWriteMode`. Clear `ambiguity`. `spoolman.selectedSpoolId = null`, `form.selectedSpoolId = null`. |

### BR-U5-VM-6 — `MainUiState` initial value

```
MainUiState(
    form = FormState(),
    spoolman = SpoolmanState(spools = emptyList(), selectedSpoolId = null, urlConfigured = false),
    nfc = NfcResult.Idle,
    banner = BannerState.Hidden,
    activeFlow = ActiveFlow.Idle,
    ambiguity = null,
)
```

### BR-U5-VM-7 — `nfc` slice mirroring

`MainUiState.nfc` is updated whenever `NfcRepository.state` emits. The VM observes `nfcRepository.state` on `viewModelScope` and forwards verbatim. (Mirror, not projection — Q-U5 plan §2.2.4.)

### BR-U5-VM-8 — `spoolman.spools` slice mirroring

`MainUiState.spoolman.spools` is updated whenever `SpoolmanRepository.spools` emits. The VM observes `spoolmanRepository.spools` on `viewModelScope` and copies into the `SpoolmanState` slice while preserving `selectedSpoolId`. (Q-U5-5=A — no auto-refresh in U5.)

### BR-U5-VM-9 — `spoolman.urlConfigured` slice mirroring

`MainUiState.spoolman.urlConfigured` is `true` when `SettingsRepository.settings.url.isNotBlank()`, else `false`. The VM observes `settingsRepository.settings`.

### BR-U5-VM-10 — `banner` slice in U5

`MainUiState.banner` is **always** `BannerState.Hidden` in U5. (Q-U5-4=A — full derivation deferred to U9.)

### BR-U5-VM-11 — Ambiguity clearance triggers

`MainUiState.ambiguity` is cleared (set to `null`) on:
- Any non-`Ambiguous` `ReadAndPairResult` arriving via `onReadTapped`.
- `onSpoolSelected(non-null)` (manual resolution).
- `onSpoolSelected(null)` (explicit clear).

### BR-U5-VM-12 — `cardUid` lifecycle (revised 2026-05-25)

`FormState.cardUid` is:
- Set whenever a `ReadAndPairResult` with a UID is processed (`PrefillFromSpoolman` / `PrefillFromTag` / `BlankForm` / `Ambiguous`). The use-case still passes the just-tapped UID explicitly (`uidSource = PreserveCurrent` semantics).
- Manual `onSpoolSelected(spool)` derives UID from `CardUidEncoding.decode(spool.lot_nr).uids.firstOrNull()`. If the spool has no `card_uid:` entry in `lot_nr`, UID becomes `null`.
- `onSpoolSelected(null)` resets `FormState` entirely; UID becomes `null`.
- Ambient `lastSeenTag` collector (BR-U5-VM-7 successor) keeps the UID row in sync with the most recently tapped tag whenever no explicit override is in flight.

Original Q-U5-7=A behaviour ("UID survives dropdown changes") replaced after install-gate feedback. The decoupled-UID model is preserved for U6a's write flow as an internal use-case input — not as a UI invariant.

### BR-U5-VM-13 — No write actions in U5

`MainViewModel` ships only `onReadTapped` and `onSpoolSelected` in U5. Methods listed in `component-methods.md` §6 (`onWriteTapped`, `onMaterialChanged`, `onBrandChanged`, `onColorChanged`, `onTempChanged`, `onPairAnotherTagTapped`, `onRawWriteToggled`, `onRepairResult`, `onVendorOptInResult`, `onSettingsTapped`) are deferred to U6a/U6b/U7/U9.

`onSettingsTapped` may ship as a stub (`emit UiEffect.Navigate("settings")`) so the top-bar Settings icon in `MainScreen` (per Q-U5-11=A) has a click target — Settings screen itself lands in U9.

---

## BR-U5-MAP — Form prefill mapping rules

### BR-U5-MAP-1 — `colorHex` canonicalisation

For both `Spool → FormState` and `OpenSpoolPayload → FormState`:
1. Strip leading `#` if present.
2. Take last 6 chars if length > 6.
3. Uppercase.
4. Drop empty result (use `null`).

### BR-U5-MAP-2 — `Material` resolution and synthesis (Q-U5-9=A)

```
fun resolveMaterial(name: String, fallbackTemps: TempFallbacks?): Material? =
    MaterialDatabase.getMaterial(name) ?: fallbackTemps?.synthesise(name)
```

- For `Spool → FormState`: `name = spool.filament.material ?: "Unknown"`, `fallbackTemps = null` (form renders the raw string when `Material` is absent).
- For `OpenSpool → FormState`: `name = payload.type`, `fallbackTemps` derived from payload's parsed temp fields. If parsing fails entirely, return null and let downstream form render whatever fields are available.

### BR-U5-MAP-3 — `variant` derivation rules

| Source | Rule |
|---|---|
| `Spool → FormState` | `variant = null` (Spoolman has no variant column). |
| `OpenSpool → FormState` | `payload.subtype.takeUnless { it == "Basic" || it.isBlank() }` (preserves v1 quirk: "Basic" is the OpenSpool default and means "no variant"). |

### BR-U5-MAP-4 — `tempRanges` derivation for `Spool → FormState`

Verbatim port of v1's `FilamentSpool.fromSpoolman` (preserves user expectations):
- Extruder:
  - If `materialData != null` AND `extruderTemp ∈ [defaultMin, defaultMax]`: use material defaults.
  - Else if `extruderTemp != null`: `(extruderTemp, extruderTemp + 20)`.
  - Else: `(null, null)`.
- Bed: same rule using `settings_bed_temp` and material's bed defaults; the `+10` fallback for bed (vs `+20` for extruder) is preserved.

### BR-U5-MAP-5 — `tempRanges` derivation for `OpenSpool → FormState`

```
extruderMin = payload.minTemp.toIntOrNull() ?: material?.defaultMinTemp
extruderMax = payload.maxTemp.toIntOrNull() ?: material?.defaultMaxTemp
bedMin      = payload.bedMinTemp?.toIntOrNull() ?: material?.defaultBedMinTemp
bedMax      = payload.bedMaxTemp?.toIntOrNull() ?: material?.defaultBedMaxTemp
```

`material` here is the synthesised/resolved `Material` from BR-U5-MAP-2 — may be `null` for unparseable + unrecognised type, in which case temps are `null`.

### BR-U5-MAP-6 — Field preservation rules across mappings

| Field | `Spool → FormState` | `OpenSpool → FormState` | `BlankForm` | `onSpoolSelected(null)` |
|---|---|---|---|---|
| `cardUid` | preserved from caller | preserved/set from read | overwritten with read uid | preserved |
| `rawWriteMode` | preserved | preserved | preserved | preserved |
| All other fields | overwritten from source | overwritten from source | reset to defaults | reset to defaults |

---

## BR-U5-TS — Test surface rules

### BR-U5-TS-1 — Test fakes

Hand-rolled test doubles in `app/src/test/java/com/spoolpainter/app/support/`:
- `FakeNfcRepository` — exposes `setNextReadResult(NfcResult.Success | Error)`, `setBufferedTap(NfcResult?)`, asserts `arm` / `consumeLastSeen` / `disarm` call counts.
- `FakeSpoolmanRepository` — exposes `setFindSpoolsByCardUidResult(SpoolmanOutcome<List<SpoolmanSpool>>)`, plus mutable `spools`/`vendors`/`filaments`/`connectivity` `MutableStateFlow`s for VM tests.

### BR-U5-TS-2 — No mocking framework

Tests use plain Kotlin classes implementing the same surfaces; no Mockito, no MockK. (Same precedent as U3/U4.)

### BR-U5-TS-3 — Use-case test cases (must cover)

| Test name | Asserts |
|---|---|
| `tag_first_buffered_OpenSpool_with_zero_spoolman_matches_and_no_payload_spool_id_returns_PrefillFromTag` | BR-U5-RP-2 + BR-U5-RP-5 + BR-U5-RP-13 (null spoolId path) |
| `tag_first_buffered_OpenSpool_with_zero_uid_matches_and_payload_spool_id_resolved_returns_PrefillFromSpoolman` | BR-U5-RP-13 (Success path) |
| `tag_first_buffered_OpenSpool_with_zero_uid_matches_and_payload_spool_id_404_falls_back_to_PrefillFromTag` | BR-U5-RP-13 (404 path) |
| `tag_first_buffered_OpenSpool_with_zero_uid_matches_and_payload_spool_id_NetworkError_returns_SpoolmanFailed` | BR-U5-RP-13 (other-error path) |
| `tag_first_buffered_OpenSpool_with_one_spoolman_match_returns_PrefillFromSpoolman` | BR-U5-RP-6 |
| `tag_first_miss_falls_back_to_arm_Read` | BR-U5-RP-2 |
| `arm_Read_then_Blank_with_zero_matches_returns_BlankForm_Blank` | BR-U5-RP-5 |
| `arm_Read_then_Vendor_with_zero_matches_returns_BlankForm_Vendor` | BR-U5-RP-5 + BR-U5-RP-4 (Spoolman called for Vendor) |
| `arm_Read_then_OpenSpool_with_two_matches_returns_Ambiguous` | BR-U5-RP-5 |
| `Spoolman_HttpError_returns_SpoolmanFailed` | BR-U5-RP-5 + BR-U5-RP-11 |
| `Spoolman_NetworkError_with_UrlNotConfigured_cause_returns_BlankForm` | BR-U5-RP-7 |
| `Spoolman_NetworkError_other_cause_returns_SpoolmanFailed` | BR-U5-RP-7 |
| `Nfc_Error_short_circuits_no_Spoolman_call` | BR-U5-RP-8 |
| `zero_length_uid_returns_NfcFailed` | BR-U5-RP-9 |

### BR-U5-TS-4 — ViewModel test cases (Q-T3=B — must cover)

| Test name | Asserts |
|---|---|
| `onReadTapped_emits_ReadingForPair_then_Idle_with_PrefillFromSpoolman` | BR-U5-VM-1 + BR-U5-VM-3 |
| `onReadTapped_PrefillFromTag_updates_form_from_payload` | BR-U5-VM-3 + BR-U5-MAP-1..5 |
| `onReadTapped_BlankForm_clears_form_preserves_rawWriteMode` | BR-U5-VM-3 + BR-U5-MAP-6 |
| `onReadTapped_Ambiguous_populates_AmbiguityState_form_stays_blank` | BR-U5-VM-3 + BR-U5-VM-11 |
| `onReadTapped_SpoolmanFailed_emits_ShowSnackbar` | BR-U5-VM-3 + BR-U5-VM-4 |
| `onReadTapped_NfcFailed_emits_ShowSnackbar` | BR-U5-VM-3 |
| `onReadTapped_while_already_armed_disarms_and_rearms` | BR-U5-VM-2 |
| `onSpoolSelected_non_null_prefills_form_from_spool` | BR-U5-VM-5 + BR-U5-MAP-4 |
| `onSpoolSelected_null_clears_form_preserves_cardUid` | BR-U5-VM-5 + BR-U5-VM-12 |
| `onSpoolSelected_same_id_is_idempotent` | BR-U5-VM-5 |
| `onSpoolSelected_clears_AmbiguityState` | BR-U5-VM-11 |
| `nfc_state_mirrors_NfcRepository_state` | BR-U5-VM-7 |
| `spools_slice_mirrors_SpoolmanRepository_spools` | BR-U5-VM-8 |
| `urlConfigured_mirrors_settings_url_blank_status` | BR-U5-VM-9 |
| `banner_always_Hidden_in_U5` | BR-U5-VM-10 |

### BR-U5-TS-5 — Out-of-scope tests

- Real-device NFC tap — verified manually at the **U5 milestone install gate**.
- Compose UI semantics tests — not required by Q-T3=B (V1's Compose tests are minimal; U5 maintains parity).
- `MaterialDatabase` lookup correctness — U2-era test surface, not retested here.
