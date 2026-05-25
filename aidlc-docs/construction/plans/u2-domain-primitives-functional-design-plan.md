# U2 — Functional Design Plan

**Stage**: CONSTRUCTION → Per-Unit Loop → Functional Design (U2)
**Unit**: U2 — Domain Primitives
**Source artefacts**:
- `aidlc-docs/inception/application-design/unit-of-work.md` §3-U2
- `aidlc-docs/inception/application-design/components.md` §2.1, §2.6, §2.8 + §3 type sketches
- `aidlc-docs/inception/application-design/component-methods.md` §1
- `aidlc-docs/inception/application-design/services.md`
- `aidlc-docs/inception/requirements/requirements.md` FR-1.1 / FR-1.2 / FR-1.3 / FR-2.1 / FR-2.2 / FR-2.3 / FR-4.7 / FR-4.9
- `aidlc-docs/inception/user-stories/stories.md` S-1.1 (read context) / S-1.2 / S-2.1 / S-2.2

---

## 1. Unit Context (Step 1)

### 1.1 Scope (locked by Units Generation)
- `CardUid` value type — canonical lowercase-hex UID string. `fromBytes(ByteArray)` constructor; equality + `toString` defined.
- `CardUidEncoding` — encode/decode rules for the `card_uid:<hex>,card_uid:<hex>,opaque-tail` strings stored in Spoolman `lot_nr`.
- `TagClassification` sealed type — `Blank | OpenSpool(payload) | Vendor(reason)`.
- `OpenSpoolPayload` — rename of v1 `OpenSpoolData`; field set unchanged per `services.md` references.
- Stories: **S-1.2, S-2.1, S-2.2** (S-1.1 ReadResult shape touched only as a forward-reference for U4 — not finalised here).

### 1.2 Cross-unit consumers (locked by `unit-of-work-dependency.md`)
- `CardUid` consumed by U3 (Spoolman lookup), U4 (NFC read result), U5..U7 (flows).
- `CardUidEncoding.Decoded` + `decode/encode` consumed by U3 (PATCH builder for `appendCardUidToSpool` / `removeCardUidFromSpool`) + U6b (move-on-bind PATCH composition).
- `TagClassification` consumed by U4 (classifier output) + U5..U7 (branch logic).
- `OpenSpoolPayload` consumed by U4 (NFC payload parse / write), U5 (form prefill), U6a (Create-and-Pair payload), U6b (Two-tag re-derivation), U7 (Raw write).

### 1.3 Out-of-scope for U2 (deferred)
- `NfcResult.Success` / `NfcResult.Error` final field set — U4.
- `NfcIntent.Write` / `NfcIntent.Verify` final field set — U4.
- `OpenSpoolPayloadParser` (NDEF byte-level parser) — U4.
- Spoolman API types extension (`SpoolmanApi`, `SpoolmanRepository`) — U3.
- Vendor-decode `DecodedVendorPayload` / refining `Vendor(reason)` to `Vendor(decoded?)` — U11 (v2.1).

---

## 2. Plan Steps (checkboxes)

### 2.1 Domain models & business rules
- [ ] 2.1.1 Lock `CardUid` invariants and constructor surface.
- [ ] 2.1.2 Lock `CardUidEncoding.Decoded` shape + parse rules.
- [ ] 2.1.3 Lock `CardUidEncoding.encode` rules (separator + tail emission + ordering).
- [ ] 2.1.4 Lock `CardUidEncoding` round-trip idempotency contract.
- [ ] 2.1.5 Lock `TagClassification` decision rules (what makes a tag Blank vs OpenSpool vs Vendor).
- [ ] 2.1.6 Lock `OpenSpoolPayload` field set + nullability + provenance of `lotNr`.

### 2.2 Validation logic & error surfaces
- [ ] 2.2.1 Define how `CardUid.fromBytes` handles `ByteArray` of length 0 and very-long inputs.
- [ ] 2.2.2 Define how `CardUidEncoding.decode` reacts to malformed entries (truncated hex, non-hex chars, odd-length hex, dangling colon).
- [ ] 2.2.3 Define how `OpenSpoolPayload` JSON decode reacts to missing required fields and to vendor-injected unknown fields.

### 2.3 Frontend / UI rules (FR-1.3 / S-1.2 manual AC)
- [ ] 2.3.1 Confirm UID display format on screen exactly equals `CardUid.toString()` (no separator, no length-pad).

### 2.4 Tests (NFR-4.1 minimum bar; `unit-of-work.md` §3-U2 Tests)
- [ ] 2.4.1 List the unit-test cases U2 must ship with.

### 2.5 Brownfield migration of v1 OpenSpoolData
- [ ] 2.5.1 Choose a migration strategy for v1 callers (`OpenSpoolData.fromJson`, `OpenSpoolData.toJson`, `OpenSpoolData.toOpenSpoolData(spool)`, `OpenSpoolData.generateLotNr()`, `FilamentSpool.fromOpenSpool(spool)`).

---

## 3. Open Questions (Step 3 — `[Answer]:` tags below)

> These are the choices U2's Functional Design cannot make on its own without nailing down a behavioural contract. Each is scoped tight; defaults are recommended in plain English for fast review.

### Q-U2-1 — `CardUid.fromBytes` on empty / malformed input

`FR-1.2` says variable-length raw bytes → lowercase hex with no separators or padding. It does not say what to do when input is empty (zero-length `ByteArray`). For tag reads, an empty UID is anomalous (`Tag.getId()` returns at least 4 bytes on real hardware), but the decoder must still terminate.

**Options**:
- **A** — `fromBytes(empty)` returns `CardUid("")` (empty hex string). Empty-string `CardUid` is a valid value type but compares unequal to any decoded tag UID. Decoder is total.
- **B** — `fromBytes(empty)` throws `IllegalArgumentException("UID bytes empty")`. Caller (U4 NfcRepository) must wrap in try/catch and emit `NfcResult.Error`.
- **C** — `fromBytes(empty)` returns `null`. Forces nullable handling at every call site.

**Recommendation**: **A**. The encoder/decoder layer should be total; the policy decision "an empty UID means a misread" lives in U4 where `NfcResult.Error` exists. U2 stays pure data.

[Answer]: A (recommended) — accepted via "Accept all recommendations" 2026-05-26.

### Q-U2-2 — `CardUid.fromBytes` byte-to-hex mapping

**Options**:
- **A** — `bytes.joinToString("") { "%02x".format(it) }` (lowercase, two-hex-chars-per-byte).
- **B** — `bytes.toHexString(HexFormat.Default)` (Kotlin 1.9+ stdlib) configured to lowercase, no separator, no length pad.

Both produce the same output for the same input. **A** has no Kotlin-version dependency; **B** is more idiomatic in Kotlin 2.x.

**Recommendation**: **A**. Project uses Kotlin 2.0.x via `libs.versions.toml`, but the simpler `%02x` format is universally understood, has zero risk of HexFormat config drift, and is what S-1.2's AC implicitly assumes.

[Answer]: A (recommended) — accepted via "Accept all recommendations" 2026-05-26.

### Q-U2-3 — `CardUid` value-class vs data-class

`components.md` §3 sketches `CardUid` as `@JvmInline value class CardUid(val hex: String)`. Value classes are zero-allocation in some call paths but disallow secondary constructors (the `fromBytes` factory must live on the companion). They also lose `equals`/`hashCode` symmetry with `String` keys in maps unless callers unwrap.

**Options**:
- **A** — `@JvmInline value class CardUid(val hex: String) { companion object { fun fromBytes(...) = CardUid(...) } }` — keep the sketch. Idiomatic, zero-cost.
- **B** — `data class CardUid(val hex: String)` — slightly more allocation, easier interop with reflection / Gson if ever needed.

**Recommendation**: **A**. Spoolman models go through Gson; `CardUid` does not (it embeds inside `lot_nr` strings, not into Retrofit DTOs directly). Value-class wins on simplicity and matches the design sketch.

[Answer]: A (recommended) — accepted via "Accept all recommendations" 2026-05-26.

### Q-U2-4 — `CardUidEncoding.decode` — separator + whitespace tolerance

S-2.1 ACs are explicit about the *output* shape:
- comma-separated entries on the wire,
- whitespace and case-insensitive `card_uid:` prefix tolerated on read,
- emission always lowercase.

But there are still micro-ambiguities the parser must resolve.

#### Q-U2-4a — separators between entries on input
**Options**:
- **A** — accept only `,` (comma) as inter-entry separator on input. Anything else is treated as part of an opaque entry.
- **B** — accept comma + semicolon + newline + tab as inter-entry separators on input. Output always emits comma.

**Recommendation**: **A**. FR-2.1 specifies comma. S-2.1 AC examples use only comma. Tolerating extra separators is policy creep that can mis-parse a user's deliberate `lot_nr=batch=42;notes=foo`.

[Answer]: A (recommended) — accepted via "Accept all recommendations" 2026-05-26.

#### Q-U2-4b — whitespace around `card_uid:` prefix and hex value
**Options**:
- **A** — accept leading/trailing whitespace around each comma-separated entry. Hex value itself is `trim()`-ed before parse. Malformed (whitespace inside the hex, e.g. `aa bb`) is treated as a non-card_uid entry preserved opaquely.
- **B** — strict: any whitespace around or inside an entry's hex => entry treated as opaque, preserved verbatim.

**Recommendation**: **A**. S-2.1 AC says "Whitespace and case in `card_uid:` prefix tolerated on read". Extending that tolerance to surrounding whitespace is a natural reading. Whitespace *inside* hex remains malformed.

[Answer]: A (recommended) — accepted via "Accept all recommendations" 2026-05-26.

#### Q-U2-4c — case insensitivity scope
**Options**:
- **A** — `card_uid:` prefix is case-insensitive (per S-2.1 AC), but hex value is normalised by `lowercase()` on decode. Output always emits `card_uid:` lowercase prefix + lowercase hex.
- **B** — the entire entry is `lowercase()`-ed before parse — equivalent for valid entries, but means an opaque entry like `Notes:Backup` would get downcased to `notes:backup` if it sneaks into the decoder before the prefix-classification step.

**Recommendation**: **A**. Don't mutate opaque content. Per FR-2.2, opaque entries must be preserved verbatim; downcasing them is mutation.

[Answer]: A (recommended) — accepted via "Accept all recommendations" 2026-05-26.

#### Q-U2-4d — hex validation strictness
**Options**:
- **A** — `card_uid:<value>` where `<value>` is non-empty AND `<value>.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }` AND `<value>.length % 2 == 0` ⇒ accepted as a UID. Else the whole entry is preserved as opaque text in `Decoded.opaque` (FR-2.2).
- **B** — accept any non-empty value following `card_uid:` (lenient — even non-hex). Output round-trip preserves it byte-for-byte. Simpler, but allows malformed entries to silently propagate.

**Recommendation**: **A**. The whole point of `CardUid` is canonical lowercase hex (FR-1.2). A non-hex `card_uid:` entry is malformed wire data, not a valid UID. Treat it as opaque (FR-2.2 says unrecognised entries are preserved verbatim — a malformed `card_uid:` qualifies as unrecognised).

[Answer]: A (recommended) — accepted via "Accept all recommendations" 2026-05-26.

### Q-U2-5 — `CardUidEncoding.Decoded.opaque` — single string vs list

`components.md` sketch shows `data class Decoded(val uids: List<CardUid>, val opaque: String)`. But a real-world `lot_nr` like `card_uid:aa,batch=42,card_uid:bb,notes=foo` has **two** opaque entries interleaved with two UIDs. Whether opaque is a single string or a list affects the round-trip + emission order.

**Options**:
- **A** — `opaque: String` — concatenated with comma when multiple opaque entries exist. On encode, always emitted at the end after all UIDs. Round-trip of `card_uid:aa,batch=42,card_uid:bb,notes=foo` yields `Decoded(uids=[aa,bb], opaque="batch=42,notes=foo")`, then encode yields `card_uid:aa,card_uid:bb,batch=42,notes=foo`. **Loses positional information**, but round-trip remains a valid `lot_nr` and FR-2.2 still holds (unrecognised entries preserved, none deleted).
- **B** — `opaque: List<String>` — each opaque entry kept separately; encode preserves interleaved order with UIDs.
- **C** — `opaque: List<String>` — but encode regroups: UIDs first, then opaques in original order. Same trade-off as **A** but with structured access to opaques.

**Recommendation**: **A**. S-2.1 AC says "tail" (singular) — implies a string concept, not a list. S-2.2 AC says "Tail preserved at the end with a single comma separator when present" — this strongly implies the encoder emits all opaques concatenated at the end. **A** matches the AC literally; **B** would over-promise positional preservation that no AC requires. The behavioural cost (opaques get reordered to the end) is acceptable per FR-2.2: "preserved verbatim and never deleted" — order is not in scope.

[Answer]: A (recommended) — accepted via "Accept all recommendations" 2026-05-26.

### Q-U2-6 — `CardUidEncoding.encode` — UID de-duplication policy

When U6a/U6b call `appendCardUidToSpool(spoolId, uid)` and the same UID is already present, the encoder must decide whether to emit duplicate `card_uid:<hex>` entries or dedupe.

**Options**:
- **A** — `encode(uids, opaque)` emits `uids` verbatim including duplicates. Dedup policy lives in the *caller* (U3's `appendCardUidToSpool` checks "already present" before appending).
- **B** — `encode(uids, opaque)` deduplicates by canonical hex equality before emission. Cleaner output; loses the ability to model "this UID appears twice intentionally" (which has no real-world meaning).
- **C** — `encode(uids, opaque)` deduplicates AND `decode(...)` deduplicates so the round-trip is a set, not a list. Most defensive; but discards order.

**Recommendation**: **B**. U3's `appendCardUidToSpool` is described as idempotent (component-methods.md §2 line 74: "PATCH `lot_nr` adding `card_uid:<uid>` if not present"). Centralising the dedup in `CardUidEncoding.encode` honours that contract regardless of caller bugs and matches FR-2 spirit (UIDs are identities, not multisets). Don't pick **C** — `decode` should be total + lossless w.r.t. UIDs (input order preserved up to dedup-on-encode).

[Answer]: B (recommended) — accepted via "Accept all recommendations" 2026-05-26.

### Q-U2-7 — `TagClassification.Blank` — what counts as blank?

A "blank" NFC tag in v1's terms is one with no NDEF message. But there are edge cases.

**Options**:
- **A** — Blank ≡ no NDEF message present at all (NfcA / Mifare default state). NDEF message present but empty (zero records) ⇒ also Blank.
- **B** — Blank ≡ no NDEF message OR NDEF message with zero records OR NDEF message with one record whose payload is empty bytes.
- **C** — Blank ≡ strictly no NDEF message; an empty-record NDEF is `Vendor(reason="empty NDEF")`.

**Recommendation**: **B**. A user-facing tag with an empty NDEF behaves identically to one with no NDEF for write purposes (FR-4.4 — "blank or OpenSpool" path) — both can be safely overwritten with OpenSpool JSON. Treating an empty-payload record as Blank is the safest, most user-charitable rule.

[Answer]: B (recommended) — accepted via "Accept all recommendations" 2026-05-26.

### Q-U2-8 — `TagClassification.Vendor.reason` — string vs enum

`components.md` §3 sketches `data class Vendor(val reason: String)`. v2.1 plans to refine this to `Vendor(decoded: DecodedVendorPayload?)` (U11 lightweight stub). For v2.0, the question is whether `reason` should be free-form string or a small enum to keep U4's classifier disciplined.

**Options**:
- **A** — `reason: String` (matches sketch). U4's classifier emits whatever short label it likes (e.g., `"non-OpenSpool MIME"`, `"unparseable JSON"`, `"missing protocol field"`). U7's vendor-opt-in flow does not branch on reason — it just shows that the tag is vendor.
- **B** — `reason: VendorReason` enum with cases `NonOpenSpoolMime`, `UnparseableJson`, `MissingProtocolField`, `Other(String)`. Stricter; future-proofs telemetry.
- **C** — `reason: String` for v2.0; refactor to an enum in U11 alongside the `decoded` refinement.

**Recommendation**: **C** (which equals **A** for v2.0 with an explicit deferral note). Don't over-engineer pre-v2.1; the v2.1 refactor is already planned to touch `Vendor`. U2 keeps `reason: String` and U11's stub plan inherits it.

[Answer]: C (recommended) — accepted via "Accept all recommendations" 2026-05-26.

### Q-U2-9 — `OpenSpoolPayload` field set & nullability — which v1 fields stay, which go?

v1's `OpenSpoolData` (`app/src/main/java/com/spoolpainter/app/domain/models/OpenSpoolData.kt`) has these fields:

| Field | v1 type | v1 nullability | v2 question |
|---|---|---|---|
| `protocol` | String, default `"openspool"` | non-null | keep as constant; never user-edited |
| `version` | String, default `"1.0"` | non-null | keep as constant; never user-edited |
| `type` | String | non-null | keep — this is the material name |
| `colorHex` | String | nullable | keep nullable |
| `brand` | String | non-null | keep non-null |
| `minTemp` | String | non-null | keep — note: stored as String to match JSON exactly |
| `maxTemp` | String | non-null | keep |
| `bedMinTemp` | String? | nullable | keep |
| `bedMaxTemp` | String? | nullable | keep |
| `subtype` | String, default `"Basic"` | non-null | keep |
| `spoolId` | String? | nullable | keep — set by U6a/U6b/U7 from Spoolman spool id |
| `lotNr` | String? | nullable | **drop?** (see FR-14.1: "the on-payload `lot_nr` field remains reserved/unused by v2") |

**Options for `lotNr` field on the payload**:
- **A** — Drop `lotNr` from `OpenSpoolPayload` entirely. v2 doesn't read it on parse, doesn't write it on encode. JSON-on-tag never carries it. Aligns with FR-14.1.
- **B** — Keep `lotNr: String?` for forward/backward compat with v1 tags written by older builds. Read it if present (decode), but **never write** it (encode omits). Defensive.
- **C** — Keep `lotNr: String?` for both read and write. Wastes bytes on tag, but trivially compatible.

**Recommendation**: **B**. v1 tags in the wild may carry `lotNr` (some users sideloaded `OpenSpoolData.generateLotNr()`). Reading it lets v2 round-trip gracefully without forcing a re-write; not writing it honours FR-14.1.

[Answer]: B (recommended) — accepted via "Accept all recommendations" 2026-05-26.

### Q-U2-10 — `OpenSpoolPayload` — keep `protocol`/`version` JSON-side or hard-code them?

v1 carries `protocol` and `version` as data-class fields with defaults. v2 could keep them as fields or hard-code them in the JSON encoder/decoder.

**Options**:
- **A** — Keep `protocol` and `version` as data-class fields with `"openspool"` / `"1.0"` defaults, exactly like v1.
- **B** — Drop them as fields; the JSON encoder always writes `"protocol":"openspool","version":"1.0"`, the JSON decoder validates `protocol == "openspool"` and accepts any `version`.
- **C** — Keep `protocol` field, drop `version` (no v2.x payload-version bump is planned in scope).

**Recommendation**: **A**. Symmetric round-trip. Hard-coding them invites accidental drift in U4's parser. U2 preserves the v1 shape; U4 enforces "protocol == openspool" gate when classifying.

[Answer]: A (recommended) — accepted via "Accept all recommendations" 2026-05-26.

### Q-U2-11 — `OpenSpoolPayload` JSON encode/decode location

v1's `OpenSpoolData` has `toJson()` / `fromJson()` methods on the data class itself (using `org.json.JSONObject`). For v2, that mixes domain shape with serialisation concern. U4 will own the NDEF byte handling — but the JSON-shape mapping has to live somewhere.

**Options**:
- **A** — Keep `toJson() / fromJson()` static-companion methods on `OpenSpoolPayload` exactly like v1; U4's parser calls them. Pragmatic; one less file.
- **B** — Move the JSON ↔ payload mapping into a separate `OpenSpoolPayloadCodec` object in `domain/primitives/` (or `data/local/`); `OpenSpoolPayload` is a pure data class. Cleaner separation; matches the value-types-don't-do-IO principle.
- **C** — Defer entirely to U4 — `OpenSpoolPayload` ships as pure data in U2; codec implementation lands in U4 alongside NDEF byte handling.

**Recommendation**: **B**. v2's whole point is layered architecture (NFR-1.1). Codec object lives in `domain/primitives/OpenSpoolPayloadCodec.kt` (pure JSON ↔ data class — no Android dependencies); U4 wraps it with NDEF byte concerns. This also makes the codec trivially unit-testable in U2 (round-trip + missing-fields tests) without dragging NDEF into U2's test surface.

[Answer]: B (recommended) — accepted via "Accept all recommendations" 2026-05-26.

### Q-U2-12 — `OpenSpoolPayloadCodec.fromJson` — what to do on missing required fields?

v1's `fromJson` returns `OpenSpoolData?` (null on any failure). For v2 the question is whether to keep that contract or signal *why* the parse failed (so U4 can map "OpenSpool but malformed" to `TagClassification.Vendor(reason="missing min_temp")` rather than `Blank`).

**Options**:
- **A** — Keep `fromJson(json: String): OpenSpoolPayload?` (nullable). U4 maps `null` to `TagClassification.Vendor(reason="unparseable JSON")` if the protocol field signalled OpenSpool, else to `Blank`. Simple; loses precise error.
- **B** — `fromJson(json: String): Result<OpenSpoolPayload>` (sealed `OpenSpoolDecodeResult { Success(payload) | Malformed(reason) | NotOpenSpool }`). U4 branches on the sealed result. Most informative.
- **C** — Two-phase: `decodeProtocol(json) -> "openspool" | other?` first, then `fromJson(json)` returns `OpenSpoolPayload?`. Lets U4 distinguish "not OpenSpool" from "OpenSpool but unparseable". Two methods.

**Recommendation**: **B**. The sealed result type is exactly what U4's classifier needs to map cleanly into `TagClassification`. **A** muddies the Vendor reason ("unparseable JSON" vs "not OpenSpool" vs "missing min_temp" all collapse). The cost is one tiny sealed type added in U2 — pays for itself in U4.

[Answer]: B (recommended) — accepted via "Accept all recommendations" 2026-05-26.

### Q-U2-13 — Brownfield migration of v1 `OpenSpoolData` — when does v1 code go?

The v1 `OpenSpoolData.kt` still ships in the U1 build. It's referenced by `FilamentSpool.fromOpenSpool(spool)` and by the (kept-dormant) v1 NFC path. v2 introduces `OpenSpoolPayload`. The question is **when** v1 dies.

**Options**:
- **A** — Replace v1 `OpenSpoolData` with `OpenSpoolPayload` in U2. Delete `OpenSpoolData.kt`. Update `FilamentSpool.fromOpenSpool` to take `OpenSpoolPayload`. Anywhere that v1 NFC code touches `OpenSpoolData` either gets updated or deleted in this unit. **Big bang.**
- **B** — Add `OpenSpoolPayload` alongside `OpenSpoolData` in U2. Mark v1 as `@Deprecated`. U3/U4 migrate callers as they touch them. v1 finally deleted in U4 (when NFC layer is rewritten) or U10 (release polish). **Coexist.**
- **C** — Add `OpenSpoolPayload` as a typealias of v1 `OpenSpoolData` plus a field-renamed wrapper. Drops in via mass-rename. **Lightweight rename.**

**Recommendation**: **A**. U1 already deleted v1 ViewModels and most v1 UI; v1 NFC code is dormant (no longer wired). The only live consumer of v1 `OpenSpoolData` after U1 is `FilamentSpool.fromOpenSpool` (which is itself only called from now-deleted UI). Killing v1 cleanly in U2 keeps the brownfield boundary tight: by end of U2, there is no `OpenSpoolData.kt` left in the tree.

**Subtlety**: `FilamentSpool` itself is part of v1 domain models and *will* be rewritten in U3 (Spoolman wire models) and U5/U6 (form state). For U2, the rule is "if a v1 file references `OpenSpoolData`, replace the import with `OpenSpoolPayload` and adapt; if it references nothing useful for v2, delete it." Concretely: `FilamentSpool.fromOpenSpool` becomes `FilamentSpool.fromOpenSpoolPayload` with the same body modulo the new type, OR `FilamentSpool` itself is deleted if it has no remaining consumers.

[Answer]: A (recommended) — accepted via "Accept all recommendations" 2026-05-26.

### Q-U2-14 — `card_uid:` prefix constant — where does it live?

The literal string `"card_uid:"` shows up in:
- U2 `CardUidEncoding` (encode + decode).
- U3 `SpoolmanRepository.findSpoolsByCardUid(uid)` → `GET /api/v1/spool?lot_nr=card_uid:<uid>`.

It must be the same value in both places to avoid silent mis-lookup.

**Options**:
- **A** — `internal const val CARD_UID_PREFIX = "card_uid:"` lives inside `CardUidEncoding` companion. U3 imports it via `CardUidEncoding.PREFIX`.
- **B** — String-literal duplication in both files. A grep -- and pre-merge code review -- catches drift.

**Recommendation**: **A**. Tiny investment; U3 will reference it on the very next unit. Scoped `internal` keeps it inside `:app` (no public-API surface).

[Answer]: A (recommended) — accepted via "Accept all recommendations" 2026-05-26.

---

## 4. Functional Design Artefacts (Step 6 — preview)

Pending the answers above, U2 will produce three artefacts under `aidlc-docs/construction/u2-domain-primitives/functional-design/`:

1. `business-logic-model.md` — algorithm pseudocode for `CardUid.fromBytes`, `CardUidEncoding.decode/encode`, `OpenSpoolPayloadCodec.fromJson/toJson`, classification decision tree.
2. `business-rules.md` — the table of explicit rules per Q-U2-1..Q-U2-14 answers, traced back to FR-IDs and S-IDs.
3. `domain-entities.md` — final type signatures for `CardUid`, `CardUidEncoding.Decoded`, `TagClassification`, `OpenSpoolPayload`, `OpenSpoolDecodeResult` (if Q-U2-12=B).

No frontend-components.md — U2 ships no Compose UI.

---

## 5. Test Surface (preview — Step 2.4 detail)

U2's unit-test list (informed by `unit-of-work.md` §3-U2 Tests):

- `CardUid.fromBytes` — lowercase, no-separator output; multiple lengths (4 / 7 / 10 bytes); empty input behaviour per Q-U2-1; round-trip if a `toBytes()` exists (TBD by Q-U2-3).
- `CardUidEncoding.decode` — canonical input; mixed-case prefix; whitespace-around-entry; empty input; tail-only input; multi-UID + tail; malformed `card_uid:` falls through to opaque per Q-U2-4d.
- `CardUidEncoding.encode` — single UID; multi-UID; UIDs + tail; empty UIDs; dedup per Q-U2-6.
- `CardUidEncoding` round-trip idempotency: `decode(encode(uids, tail)) == (uids', tail')` where `uids'` is `uids` after dedup-on-encode and tail order-may-collapse per Q-U2-5.
- `TagClassification` — instances are equal when fields are equal; sealed exhaustiveness covered by use-site tests (caller responsibility).
- `OpenSpoolPayloadCodec.toJson` / `fromJson` — round-trip for fully-populated payload; round-trip for minimal payload (only required fields); unknown fields ignored on decode; missing required fields → `OpenSpoolDecodeResult.Malformed` (or `null` per Q-U2-12).
- `OpenSpoolDecodeResult` — `NotOpenSpool` for non-OpenSpool JSON, valid+other-protocol JSON, garbage; `Malformed(reason)` carries the missing-field name; `Success` carries the parsed payload.

---

## 6. Approval Gate

Once the `[Answer]:` tags are populated and any follow-up clarifications resolved, this plan is closed and Step 6 (artefact generation) executes, followed by Step 7 (completion message) and Step 8 (await approval to advance to Code Generation Part 1).
