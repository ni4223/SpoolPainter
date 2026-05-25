# U2 — Business Rules

**Stage**: CONSTRUCTION → Per-Unit Loop → Functional Design (U2)
**Source plan**: `aidlc-docs/construction/plans/u2-domain-primitives-functional-design-plan.md` (answered 2026-05-26)

This document is a flat, traceable rule register: one rule per row, each tagged with the FR/S/Q identifier that authorises it.

---

## 1. `CardUid` rules

| Rule ID | Rule | Source |
|---|---|---|
| BR-U2-CU-1 | `CardUid` is a value class wrapping a single `String` field named `hex`. | Q-U2-3=A; components.md §3 sketch |
| BR-U2-CU-2 | `CardUid.fromBytes(bytes)` returns lowercase-hex of `bytes`, two characters per byte, no separators, no length pad. | FR-1.2; S-1.2 AC; Q-U2-2=A |
| BR-U2-CU-3 | `CardUid.fromBytes(emptyByteArray)` returns `CardUid("")` and never throws. Empty `CardUid` is a valid value but compares unequal to any non-empty `CardUid`. | Q-U2-1=A |
| BR-U2-CU-4 | `CardUid.toString()` returns the `hex` field verbatim — no separator, no length pad, no `card_uid:` prefix. | FR-1.3; S-1.2 AC |
| BR-U2-CU-5 | `CardUid` equality is byte-equal `String` equality on `hex`. Callers MUST always construct with lowercase input; the encoder/decoder normalises to lowercase before constructing. | FR-1.2; Q-U2-3=A |
| BR-U2-CU-6 | `CardUid.fromBytes` MUST NOT trim, pad, or otherwise alter the byte input. Each byte is rendered independently. | FR-1.2 |

---

## 2. `CardUidEncoding` rules

### 2.1 Constants

| Rule ID | Rule | Source |
|---|---|---|
| BR-U2-CE-1 | The literal `"card_uid:"` is declared as `internal const val PREFIX` on `CardUidEncoding` (or its companion). U3 imports it via `CardUidEncoding.PREFIX`; no other module redeclares the literal. | Q-U2-14=A |

### 2.2 `decode(input: String) -> Decoded`

| Rule ID | Rule | Source |
|---|---|---|
| BR-U2-DEC-1 | Empty or whitespace-only input ⇒ `Decoded(uids = [], opaque = "")`. | S-2.1 AC |
| BR-U2-DEC-2 | The only inter-entry separator on input is the comma `,`. Semicolon, tab, newline, etc. are part of an opaque entry. | FR-2.1; Q-U2-4a=A |
| BR-U2-DEC-3 | Each comma-separated entry is `trim()`-ed before classification. Leading/trailing whitespace around an entry does not affect parsing. | S-2.1 AC; Q-U2-4b=A |
| BR-U2-DEC-4 | Empty fragments (e.g., from leading/trailing/double commas) are skipped — they neither produce a UID nor an opaque entry. | S-2.1 AC |
| BR-U2-DEC-5 | An entry with the case-insensitive `card_uid:` prefix is a UID candidate; all other entries are opaque. The prefix match is whitespace-stripped from the trimmed entry only — the opaque content of opaque entries is never mutated. | S-2.1 AC; Q-U2-4c=A |
| BR-U2-DEC-6 | A UID candidate is **accepted** as a `CardUid` if and only if its value (the substring after `card_uid:`): (a) is non-empty; (b) has even length; (c) every character is in `[0-9a-fA-F]`. The accepted value is normalised by `lowercase()` before constructing the `CardUid`. | FR-1.2; Q-U2-4d=A |
| BR-U2-DEC-7 | A UID candidate that fails any of BR-U2-DEC-6's clauses is preserved verbatim as opaque content (using the **untrimmed** original entry text — original whitespace and case retained). | FR-2.2; Q-U2-4d=A |
| BR-U2-DEC-8 | Multiple opaque entries are concatenated into `Decoded.opaque` with a single `,` between them, in input order. | Q-U2-5=A |
| BR-U2-DEC-9 | `decode` is total — it never throws and never returns `null`. | NFR-7.1 |

### 2.3 `encode(uids: List<CardUid>, opaque: String = "") -> String`

| Rule ID | Rule | Source |
|---|---|---|
| BR-U2-ENC-1 | `uids` is deduplicated by canonical-hex equality, preserving first-seen order. The deduplicated list is what gets emitted. | Q-U2-6=B |
| BR-U2-ENC-2 | Each emitted UID becomes the entry `"card_uid:" + uid.hex` (using `BR-U2-CE-1`'s constant). Hex case is whatever the `CardUid` carries — normalised lowercase by construction (see BR-U2-CU-5). | FR-2.1; Q-U2-14=A |
| BR-U2-ENC-3 | UID entries are joined with `,`; the opaque tail (if non-empty) is appended at the end with a single `,` separator. | S-2.2 AC; Q-U2-5=A |
| BR-U2-ENC-4 | If `uids` is empty and `opaque` is empty ⇒ output is `""`. | S-2.2 AC |
| BR-U2-ENC-5 | If `uids` is empty and `opaque` is non-empty ⇒ output is exactly `opaque` (no leading comma). | S-2.2 AC |
| BR-U2-ENC-6 | `encode` is total — it never throws. | NFR-7.1 |

### 2.4 Round-trip

| Rule ID | Rule | Source |
|---|---|---|
| BR-U2-RT-1 | For all `(uids, opaque)`: `decode(encode(uids.distinct(), opaque)).uids == uids.distinct()` (equality of `CardUid` lists, in order). | S-2.2 AC ("Round-trip is idempotent") |
| BR-U2-RT-2 | For all `(uids, opaque)`: `decode(encode(uids.distinct(), opaque)).opaque == opaque`. | S-2.2 AC |
| BR-U2-RT-3 | For all input strings `s`: `encode(decode(s).uids, decode(s).opaque)` is a fixed point under further round-trip cycles (subsequent encode-decode-encode produces the same output). | S-2.2 AC |
| BR-U2-RT-4 | A round-trip that crosses an opaque-interleaved input (e.g., `"card_uid:aa,batch=42,card_uid:bb"`) MAY rewrite the wire string with the opaque block reordered to the end. UID order and opaque content are preserved; positional interleaving is not. | FR-2.2; Q-U2-5=A |

---

## 3. `TagClassification` rules

| Rule ID | Rule | Source |
|---|---|---|
| BR-U2-TC-1 | `TagClassification` is a sealed type with exactly three constructors: `Blank` (object), `OpenSpool(payload: OpenSpoolPayload)`, `Vendor(reason: String)`. | components.md §3 sketch; Q-U2-8=C |
| BR-U2-TC-2 | A tag is `Blank` when: (a) NDEF message is absent, OR (b) NDEF message has zero records, OR (c) the first NDEF record's payload is null/empty. | Q-U2-7=B |
| BR-U2-TC-3 | A tag is `OpenSpool(payload)` when: NDEF first record's MIME type is exactly `"application/json"` AND `OpenSpoolPayloadCodec.fromJson(payloadUtf8)` returns `Success(payload)`. | FR-1.1; Q-U2-12=B |
| BR-U2-TC-4 | A tag is `Vendor(reason)` when none of BR-U2-TC-2 / BR-U2-TC-3 apply. The `reason` string is informational only — UI MUST NOT branch on its content. | FR-4.7; Q-U2-8=C |
| BR-U2-TC-5 | `TagClassification` is the only type that may classify a tag. The classifier function (`classify(NdefMessage?)`) lives in U4; U2 only ships the type definitions. | components.md §2.8; unit-of-work.md §3-U4 |
| BR-U2-TC-6 | `Vendor.reason` is a free-form `String` for v2.0; v2.1 (U11) refines this to `Vendor(decoded: DecodedVendorPayload?)` — that refactor is a U11-owned interface change, not a U2 obligation. | Q-U2-8=C; unit-of-work.md §4-U11 |

---

## 4. `OpenSpoolPayload` rules

| Rule ID | Rule | Source |
|---|---|---|
| BR-U2-OP-1 | `OpenSpoolPayload` is a Kotlin `data class` with the field set defined in `domain-entities.md` §4. | components.md §3.5; Q-U2-10=A |
| BR-U2-OP-2 | The `protocol` field is non-null; default is `"openspool"`. The `version` field is non-null; default is `"1.0"`. Both are kept as fields (not hard-coded into the codec) for symmetric round-trip. | Q-U2-10=A |
| BR-U2-OP-3 | The `lotNr` field is **read-on-decode, never-write-on-encode** (defensive read for v1 tags in the wild; FR-14.1 prohibits emitting it on tag). | FR-14.1; Q-U2-9=B |
| BR-U2-OP-4 | All other field nullability matches v1 `OpenSpoolData`: `colorHex`, `bedMinTemp`, `bedMaxTemp`, `spoolId`, `lotNr` are nullable; `type`, `brand`, `minTemp`, `maxTemp`, `subtype`, `protocol`, `version` are non-null. | v1 OpenSpoolData.kt; FR-14 |
| BR-U2-OP-5 | `OpenSpoolPayload` is a pure data class — it has no `toJson` / `fromJson` methods. JSON serialisation lives entirely in `OpenSpoolPayloadCodec`. | NFR-1.1; Q-U2-11=B |

---

## 5. `OpenSpoolPayloadCodec` rules

| Rule ID | Rule | Source |
|---|---|---|
| BR-U2-CO-1 | `OpenSpoolPayloadCodec` is a Kotlin `object` (singleton) in `domain/primitives/`. No Android dependencies; uses `org.json.JSONObject` (already in the v1 build, available on JVM via the Android stub but pure-Java by source). | NFR-1.1; Q-U2-11=B |
| BR-U2-CO-2 | `fromJson(json: String): OpenSpoolDecodeResult` returns one of three sealed cases per BR-U2-DR-1..3 below. | Q-U2-12=B |
| BR-U2-CO-3 | `fromJson` MUST tolerate a leading non-`{` prefix by stripping characters until the first `{` (matching v1 `OpenSpoolData.fromJson` behaviour for tags with a language code prefix). | v1 behaviour; preserves backward compatibility with legacy tags |
| BR-U2-CO-4 | `fromJson` requires non-empty values for the four required fields: `type`, `brand`, `min_temp`, `max_temp`. A missing or empty required field ⇒ `Malformed(reason = "missing <field_name>")`. | FR-1.1; FR-14.1; Q-U2-12=B |
| BR-U2-CO-5 | `fromJson` checks `protocol == "openspool"` first. If absent or not equal to `"openspool"`, returns `NotOpenSpool` — `Malformed` is reserved for openspool-protocol payloads that are otherwise broken. | FR-1.1; Q-U2-12=B |
| BR-U2-CO-6 | `fromJson` ignores unknown fields silently. Vendor-injected fields don't affect parsing. | FR-2.2 spirit (non-destructive parsing) |
| BR-U2-CO-7 | `toJson(payload)` emits keys in canonical order: `protocol`, `version`, `type`, `color_hex`, `brand`, `min_temp`, `max_temp`, then optionally `bed_min_temp`, `bed_max_temp`, `spool_id`, then `subtype` if non-empty. `lot_nr` is **never** emitted (BR-U2-OP-3). | v1 emission order (preserves on-tag byte stability across writes); Q-U2-9=B |
| BR-U2-CO-8 | `toJson` emits `color_hex` as an empty string when `payload.colorHex` is null (v1 quirk preserved for tag compatibility). | v1 OpenSpoolData.toJson |
| BR-U2-CO-9 | Round-trip property: `fromJson(toJson(p)) == Success(p')` where `p' == p` except `p'.lotNr == null` regardless of `p.lotNr`. | BR-U2-OP-3; Q-U2-9=B |
| BR-U2-CO-10 | `fromJson` and `toJson` MUST NOT throw. JSON-parse failures map to `NotOpenSpool` (per BR-U2-CO-5 — invalid JSON has no `protocol` field, so it falls into the same bucket as non-openspool valid JSON). | NFR-7.1 |

---

## 6. `OpenSpoolDecodeResult` rules

| Rule ID | Rule | Source |
|---|---|---|
| BR-U2-DR-1 | `Success(payload: OpenSpoolPayload)` — JSON parsed cleanly and all required fields are present. | Q-U2-12=B |
| BR-U2-DR-2 | `Malformed(reason: String)` — `protocol == "openspool"` BUT a required field is missing or empty. `reason` carries the missing-field name (e.g., `"missing min_temp"`). | Q-U2-12=B |
| BR-U2-DR-3 | `NotOpenSpool` — `protocol` field absent, not equal to `"openspool"`, or JSON itself failed to parse. | Q-U2-12=B |
| BR-U2-DR-4 | `OpenSpoolDecodeResult` is sealed; every consumer must exhaustively branch via `when`. | Kotlin sealed-type idiom; NFR-7.1 |

---

## 7. Brownfield migration rules

| Rule ID | Rule | Source |
|---|---|---|
| BR-U2-MIG-1 | `app/src/main/java/com/spoolpainter/app/domain/models/OpenSpoolData.kt` is **deleted** in U2. No transition window. | Q-U2-13=A |
| BR-U2-MIG-2 | All references to `OpenSpoolData` in v1 source are either (a) updated to `OpenSpoolPayload` with type-correct adapters, or (b) deleted along with the file that referenced them, in U2. End-of-U2 invariant: zero references to `OpenSpoolData` in `app/src/main/`. | Q-U2-13=A |
| BR-U2-MIG-3 | `FilamentSpool.fromOpenSpool(spool: OpenSpoolData)` is renamed `FilamentSpool.fromOpenSpoolPayload(payload: OpenSpoolPayload)` if `FilamentSpool` itself is retained for U3+. If `FilamentSpool` has no remaining v2 callers, the file is deleted. | Q-U2-13=A; unit-of-work.md §3-U2 deferred-cleanup note |
| BR-U2-MIG-4 | The dormant v1 NFC code (`hardware/nfc/NfcManager.kt`, `NfcController.kt`, `NfcHandler.kt`) MAY continue to compile against `OpenSpoolPayload` IF the rename is mechanical. Otherwise those files MAY be deleted in U2 (they are scheduled for full replacement in U4). The decision is per-file and recorded in U2's code-summary doc. | Q-U2-13=A; unit-of-work.md §3-U4 scope |
| BR-U2-MIG-5 | `OpenSpoolData.generateLotNr()` is dropped — v2's `lot_nr` is derived from `card_uid:<uid>` (FR-2), not a random UUID. | FR-2.1; FR-14.1 |

---

## 8. Test obligations (NFR-4.1 minimum bar)

| Rule ID | Rule | Source |
|---|---|---|
| BR-U2-T-1 | `CardUidTest` covers: empty bytes, single byte, 4-byte UID, 7-byte UID, 10-byte UID, byte values 0x00 / 0x0F / 0xFF, equality / inequality. | unit-of-work.md §3-U2 Tests; BR-U2-CU-* |
| BR-U2-T-2 | `CardUidEncodingDecodeTest` covers: empty input, whitespace-only input, single UID, multi UID, mixed-case prefix, surrounding whitespace, multi-comma fragments, malformed hex (non-hex / odd length / empty value) → opaque, mixed UIDs and opaque entries, opaque-only input. | unit-of-work.md §3-U2 Tests; BR-U2-DEC-* |
| BR-U2-T-3 | `CardUidEncodingEncodeTest` covers: empty list + empty opaque, single UID, multi UID, dedup, opaque-only, UID + opaque. | unit-of-work.md §3-U2 Tests; BR-U2-ENC-* |
| BR-U2-T-4 | `CardUidEncodingRoundTripTest` covers: idempotency on canonical input, idempotency on mixed-case + whitespace input (collapses to canonical), opaque-interleaved input (UID order preserved, opaque collapsed to tail). | S-2.2 AC; BR-U2-RT-* |
| BR-U2-T-5 | `OpenSpoolPayloadCodecTest` covers: full payload round-trip; minimal-required-only round-trip; missing each required field → `Malformed(reason)` carrying field name; non-openspool protocol → `NotOpenSpool`; invalid JSON → `NotOpenSpool`; v1 tag with `lot_nr` field decodes successfully; payload with `lot_nr` set encodes without emitting `lot_nr`. | BR-U2-CO-*; BR-U2-DR-* |
| BR-U2-T-6 | `OpenSpoolDecodeResultTest` covers: sealed-type structural equality (data-class equality on `Malformed.reason`, singleton equality on `NotOpenSpool`). | BR-U2-DR-* |
| BR-U2-T-7 | `TagClassification` types are tested implicitly via the codec/encoding tests — no dedicated test file. The classifier algorithm itself is tested in U4 against a fake NDEF source. | unit-of-work.md §3-U2 Tests; §3-U4 |
