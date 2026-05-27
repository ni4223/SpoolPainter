# SpoolPainter v2 — Requirements Delta: NDEF MIME-Type Regression + Filament Matcher Strictness

**Status**: Approved 2026-05-26 by user direction ("add to all docs no approval required") during U6b Code Gen Part 1 pause. Folds into U6b construction scope as **U6b-Δ-3** + **U6b-Δ-4**. v2.0 only. Carries no new functional requirement IDs — both are corrections that re-align v2 to the v1 behaviour the user already validated on Snapmaker U1 firmware.

**Drafted**: 2026-05-26
**Affects units**: U6b (Move-on-Bind + Two-Tag) — both bug fixes ride along with the move-on-bind / two-tag implementation, gated by the U6 milestone install gate against a Snapmaker U1 tag and a real Spoolman instance.

---

## 1. Why this delta exists

Two regressions surfaced during U6a's install-gate session that the user identified during the U6b Code Gen Part 1 pause on 2026-05-26:

1. **Bug #1 — NDEF MIME-type regression vs Snapmaker U1 firmware**. v2 writes tags using a *vendor* MIME type (`application/vnd.openspool+json`) that v1 never used. The Snapmaker U1 firmware filters NDEF records by MIME type — so tags written by v2 are **invisible to the printer**. Reads still work because our classifier accepts both vendor MIME and `application/json`. JSON shape is unchanged from v1.
2. **Bug #2 — Filament matcher strictness causes silent duplicate filaments**. `SpoolmanRepository.resolveOrCreateFilament` uses strict equality on `colorHex` (case-sensitive, with-or-without leading `#`) and on `extra.variant` (treats `null` and empty string as different). Spoolman returns colors however the user typed them; the form canonicalises to uppercase no-`#`. The mismatch silently creates a second filament row under the same vendor, leaving the original orphaned. Repeated retries on the same filament accumulate twins.

Both regressions are corrections, not new behaviour. The user's repeated v1 validation against Snapmaker U1 firmware is the source of truth.

[SPEC]: https://snapmakeru1-extended-firmware.pages.dev/rfid_support
[V1-WRITE]: `main:app/src/main/java/com/spoolpainter/app/hardware/nfc/NfcManager.kt#writeTag`
[V1-READ]: `main:app/src/main/java/com/spoolpainter/app/domain/models/FilamentSpool.kt`
[V2-WRITE]: `app/src/main/java/com/spoolpainter/app/hardware/nfc/NfcRepository.kt:263-272`
[V2-MATCH]: `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanRepository.kt:300-340`

---

## 2. Bug #1 — NDEF MIME-type regression

### 2.1 Symptom

User taps an NTAG to write filament data via SpoolPainter v2. App reports success; Spoolman record updated; `extra.card_uids` PATCH'd. **Snapmaker U1 firmware does not recognise the tag** when the spool is loaded into the printer. Re-reading the same tag inside SpoolPainter shows the data — internal round-trip works, printer-side does not.

### 2.2 Root cause

[SPEC] is explicit: *"Use any NFC app that supports NDEF with JSON (MIME type: `application/json`)"*. The Snapmaker U1 firmware filters NDEF records to only those whose MIME-type byte string is `application/json`. v1 wrote that exact string ([V1-WRITE]). v2's U2 introduced a "canonical" vendor MIME `application/vnd.openspool+json` (`NfcRepository.MIME_OPENSPOOL`); U4's `encodePayloadRecords` ([V2-WRITE]) wires it to all writes.

The vendor MIME has no basis in the OpenSpool spec — the spec is JSON-over-NDEF with `application/json`. Adopting a vendor sub-type was a v2-internal design decision that broke firmware compatibility.

### 2.3 Required behaviour (normative)

- **All writes MUST emit NDEF MIME type `application/json`** — bytes `61 70 70 6C 69 63 61 74 69 6F 6E 2F 6A 73 6F 6E` (US-ASCII, 16 bytes), TNF=`0x02` (TNF_MIME_MEDIA).
- **Reads MUST continue to accept both** `application/json` and `application/vnd.openspool+json` (case-insensitive) — for forward-compat with any tag a user wrote during v2's U4..U6a window before the fix landed.
- The `MIME_OPENSPOOL` constant MAY be retained as a read-side classifier fallback. It MUST NOT be referenced by the write path.
- JSON wire shape (`OpenSpoolPayloadCodec.toJson`) is **unchanged** — the bug is the NDEF type byte, not the payload.

### 2.4 Verification

- Unit-test asserts the write MIME equals `application/json` (`NfcRepositoryWriteVerifyTest` — adjust existing assertion).
- Unit-test asserts `application/vnd.openspool+json` still classifies as OpenSpool on read.
- **U6 install gate must include a real-firmware verify**: write a fresh tag → load spool into Snapmaker U1 → printer reads correct material/colour/temps.

---

## 3. Bug #2 — Filament matcher strictness

### 3.1 Symptom

User has filament F1 in Spoolman with `color_hex = "ff0000"` and `extra.variant = "Matte"`. User taps Save & Write with the form populated identically (form canonicalises to `colorHex = "FF0000"`). `resolveOrCreateFilament` ([V2-MATCH]) does not match F1 → creates F2 (same vendor, same material, `color_hex = "FF0000"`, `extra.variant = "\"Matte\""`). New spool pairs to F2. F1 stays orphan. Each retry can spawn another twin.

### 3.2 Root cause

[V2-MATCH] line 314:
```kotlin
if ((f.color_hex ?: "") != req.colorHex) return@firstOrNull false
```
`f.color_hex` is whatever case + format Spoolman returned. `req.colorHex` is form-canonicalised (uppercase, no `#`). They mismatch for *the same color*.

[V2-MATCH] lines 317-318:
```kotlin
val existingVariant = decodeJsonString(f.extra?.get("variant"))
existingVariant == variantNormalised
```
`existingVariant` is `null` if `extra.variant` is missing, `""` if present-but-empty. `variantNormalised` is `null` if user form variant is null/blank. `null != ""` so two semantically-equivalent records mismatch.

The form-side canonicalisation that v1 already applied (commit `8d637e9` — `removePrefix("#") → takeLast(6) → uppercase()`) is faithfully carried over in v2 (`FormMapping.canonicaliseColorHex` at `FormMapping.kt:109-113`, `FilamentSpool.fromSpoolman` at lines 58-62). What's missing is the **server-side comparison** — v2 introduced `resolveOrCreateFilament` (v1 never created filaments at all, only read existing ones), and the new code does not reuse the v1 normalisation chain.

### 3.3 Required behaviour (normative)

`resolveOrCreateFilament` MUST normalise both sides of every comparison key before the equality check. Specifically:

| Key | Normalisation (both sides) |
|---|---|
| `vendor.id` | already correct — strict integer equality |
| `material` | already correct — `equals(materialName, ignoreCase = true)` |
| `color_hex` | **NEW**: strip leading `#`, take last 6 if longer, uppercase, treat empty as `null`; compare via equality on the normalised values, where `null == null` matches |
| `extra.variant` | **NEW**: trim, treat empty as `null`; `null == null` matches; otherwise case-insensitive equality |

Normalisation MUST be a single shared helper (not duplicated). Suggested:
```kotlin
private fun canonHex(raw: String?): String? =
    raw?.removePrefix("#")
        ?.let { if (it.length > 6) it.takeLast(6) else it }
        ?.uppercase()
        ?.takeIf { it.isNotEmpty() }

private fun canonVariant(raw: String?): String? =
    raw?.trim()?.takeIf { it.isNotBlank() }
```

This SHOULD be the same `canonicaliseColorHex` used in `FormMapping` — extract to a `domain/primitives/ColorHexCodec.kt` or similar so the form and the matcher canonicalise identically.

### 3.4 Out of scope for this delta

- The **UX path** to reach an orphan filament (no UI surface today). That belongs to the new orphan-filament + extra-fields requirement and lands in U8. See `requirements-delta-orphan-filament-and-extra-fields.md`.
- Repairing existing duplicate filaments in Spoolman. Out of scope — user-side dedup if needed.

### 3.5 Verification

- Unit test: existing filament with `color_hex = "ff0000"`, form input `colorHex = "FF0000"` → match (no new filament created).
- Unit test: existing filament with `color_hex = "#FF0000"`, form input `colorHex = "FF0000"` → match.
- Unit test: existing filament with `extra.variant = null`, form input `variant = null` → match.
- Unit test: existing filament with `extra.variant = ""` (empty string), form input `variant = null` → match (treated as equivalent).
- Unit test: existing filament with `extra.variant = "matte"`, form input `variant = "Matte"` → match (case-insensitive).
- Unit test: different colour or different variant → no match (fresh filament created).
- **U6 install gate**: tap Save & Write twice in a row with the same form → `listFilaments` shows one row, not two; spool count under that filament increments by 1 (not 2 filaments × 1 spool each).

---

## 4. Construction-unit deltas

### U6b-Δ-3 — NDEF MIME-type write fix
- `NfcRepository.encodePayloadRecords` writes `MIME_JSON` (= `"application/json"`) instead of `MIME_OPENSPOOL`.
- Read classifier in `NfcRepository.classifyOpenSpoolRecord` (or equivalent) keeps dual accept.
- `OpenSpoolPayloadCodec.toJson` unchanged.
- Test changes: `NfcRepositoryWriteVerifyTest` (or current equivalent) asserts the written MIME equals `application/json`. New regression test: `application/vnd.openspool+json` payload still classifies as OpenSpool on read.
- Code Generation plan section: U6b plan §13.

### U6b-Δ-4 — Filament matcher strictness fix
- New helpers `canonHex` + `canonVariant` (or shared `ColorHexCodec`) used by `resolveOrCreateFilament`.
- `resolveOrCreateFilament` (`SpoolmanRepository.kt:300-340`) compares normalised values. Logging unchanged.
- New unit test file (or extension of existing `SpoolmanRepositoryTest`): the six bullet cases above.
- Code Generation plan section: U6b plan §14.

Both deltas extend U6b Code Gen Part 1 plan with §13 and §14. Both are covered by the existing **U6 milestone install gate** at the end of U6b — re-verified manually on a Snapmaker U1 tag and a real Spoolman instance with at least one pre-existing filament + variant set.

Test count target: U6b plan's prior target was ~268..272. With Δ-3 (~1-2 added cases) + Δ-4 (~6 added cases), revised target is ~275..280.

---

## 5. Approval gate

User authorised on 2026-05-26 ("ask whatever question you want and then add to all docs no approval required") after walking through the diagnosis with the agent. Routing answer recorded: **Both as U6b-Δ** (option 1 of bug-routing decision).

This delta is not subject to the usual standardised 2-option requirements approval — user directed direct application.
