# U2 — Business Logic Model

**Stage**: CONSTRUCTION → Per-Unit Loop → Functional Design (U2)
**Source plan**: `aidlc-docs/construction/plans/u2-domain-primitives-functional-design-plan.md` (answered 2026-05-26)
**Companion artefacts**:
- `business-rules.md` — explicit rules table traced to FR-IDs and S-IDs.
- `domain-entities.md` — final type signatures.

This document is **technology-agnostic algorithmic pseudocode**. Concrete Kotlin signatures live in `domain-entities.md` and final code lands in U2 Code Generation.

---

## 1. `CardUid.fromBytes(bytes)` — UID canonicalisation (FR-1.2 / S-1.2)

```text
function fromBytes(bytes: ByteArray) -> CardUid:
    # Per Q-U2-1=A — total decoder; empty input yields empty CardUid.
    # Per Q-U2-2=A — %02x format-string mapping per byte (Kotlin idiom).
    let hex = bytes.joinToString("") { byte ->
        format byte as two-char lowercase hex (e.g., 0x4A -> "4a", 0x05 -> "05")
    }
    return CardUid(hex)
```

**Examples**:
- `fromBytes([])` → `CardUid("")`.
- `fromBytes([0x04])` → `CardUid("04")`.
- `fromBytes([0x04, 0xa1, 0xb2, 0xc3, 0xd4, 0xe5, 0x80])` → `CardUid("04a1b2c3d4e580")`.
- `fromBytes([0xFF, 0x00, 0x10])` → `CardUid("ff0010")` — leading bytes preserved; no padding stripped.

**Equality**: `CardUid` equality is byte-equal hex-string equality (per Q-U2-3=A — value class wraps `String`). `CardUid("04a1b2") == CardUid("04a1b2")`; `CardUid("04a1b2") != CardUid("04A1B2")` — but the encoder/decoder normalises to lowercase, so callers should never produce uppercase `CardUid` values directly.

**toString**: returns the underlying hex string verbatim — no separator, no length pad, no `card_uid:` prefix. UI display (FR-1.3 / S-1.2 manual AC) uses `CardUid.toString()` directly.

---

## 2. `CardUidEncoding.decode(input)` — `lot_nr` parser (FR-2.1, FR-2.2 / S-2.1)

```text
function decode(input: String) -> Decoded:
    if input is empty or whitespace-only:
        return Decoded(uids = [], opaque = "")

    let entries = input.split(",")           # Q-U2-4a=A — comma only
    let uids: mutable list of CardUid = []
    let opaqueEntries: mutable list of String = []

    for raw in entries:
        let trimmed = raw.trim()             # Q-U2-4b=A — whitespace tolerated around entry
        if trimmed is empty:
            continue                         # ignore empty fragments from leading/trailing/double commas

        # Q-U2-4c=A — case-insensitive prefix match; opaque content not mutated.
        if trimmed.startsWithIgnoreCase("card_uid:"):
            let value = trimmed.substringAfter prefix    # length-of-prefix-aware
            # Q-U2-4d=A — strict hex validation.
            if value is non-empty AND value.length is even AND every char is in [0-9a-fA-F]:
                uids.append(CardUid(value.lowercase()))
            else:
                opaqueEntries.append(raw)    # preserve verbatim — including original whitespace/case
        else:
            opaqueEntries.append(raw)        # preserve verbatim

    # Q-U2-5=A — opaque is a single comma-joined string.
    let opaque = opaqueEntries.joinToString(",")
    return Decoded(uids, opaque)
```

**Worked examples**:

| Input | `Decoded.uids` | `Decoded.opaque` |
|---|---|---|
| `""` | `[]` | `""` |
| `"card_uid:aabb"` | `[CardUid("aabb")]` | `""` |
| `"card_uid:aabb,card_uid:ccdd"` | `[CardUid("aabb"), CardUid("ccdd")]` | `""` |
| `"card_uid:aabb,batch=42"` | `[CardUid("aabb")]` | `"batch=42"` |
| `"card_uid:AABB"` | `[CardUid("aabb")]` | `""` |
| `" CARD_UID:AABB "` | `[CardUid("aabb")]` | `""` |
| `"card_uid:zz"` (non-hex) | `[]` | `"card_uid:zz"` |
| `"card_uid:abc"` (odd length) | `[]` | `"card_uid:abc"` |
| `"card_uid:"` (empty value) | `[]` | `"card_uid:"` |
| `"card_uid:aa,batch=42,card_uid:bb,notes=foo"` | `[CardUid("aa"), CardUid("bb")]` | `"batch=42,notes=foo"` |

---

## 3. `CardUidEncoding.encode(uids, opaque)` — `lot_nr` serialiser (FR-2.1, FR-2.2 / S-2.2)

```text
function encode(uids: List<CardUid>, opaque: String = "") -> String:
    # Q-U2-6=B — dedup by canonical hex equality, preserve first-seen order.
    let dedupedUids = uids.distinct()        # Set semantics; first occurrence wins for ordering

    let uidEntries = dedupedUids.map { uid -> "card_uid:" + uid.hex }   # Q-U2-14=A — uses CARD_UID_PREFIX
    let allEntries = uidEntries
    if opaque is non-empty:
        allEntries = uidEntries + [opaque]   # opaque appended at the end as a single block (Q-U2-5=A)

    return allEntries.joinToString(",")
```

**Worked examples**:

| `uids` | `opaque` | Output |
|---|---|---|
| `[]` | `""` | `""` |
| `[CardUid("aabb")]` | `""` | `"card_uid:aabb"` |
| `[CardUid("aabb"), CardUid("ccdd")]` | `""` | `"card_uid:aabb,card_uid:ccdd"` |
| `[CardUid("aabb"), CardUid("aabb")]` | `""` | `"card_uid:aabb"` (dedup) |
| `[CardUid("aabb")]` | `"batch=42"` | `"card_uid:aabb,batch=42"` |
| `[]` | `"batch=42"` | `"batch=42"` |
| `[CardUid("aabb"), CardUid("ccdd")]` | `"batch=42,notes=foo"` | `"card_uid:aabb,card_uid:ccdd,batch=42,notes=foo"` |

---

## 4. Round-trip idempotency (S-2.2)

```text
property: forall (uids, opaque):
    let s     = encode(uids.distinct(), opaque)
    let d1    = decode(s)
    let s2    = encode(d1.uids, d1.opaque)
    assert d1.uids   == uids.distinct()      # UID list equal up to dedup
    assert d1.opaque == opaque               # opaque preserved (after Q-U2-5 collapse)
    assert s2        == s                    # encode(decode(encode(...))) is fixed point
```

`encode` is **idempotent under composition with `decode`**: a value that has been through one encode/decode cycle survives further cycles unchanged.

**Note on opaque-order preservation**: per Q-U2-5=A, the encoder always appends the opaque block at the end. So the round-trip of `"card_uid:aa,batch=42,card_uid:bb"` is `"card_uid:aa,card_uid:bb,batch=42"` — order of UIDs is preserved, but interleaved opaques get reordered to a tail block. From that point on, further round-trips are stable.

---

## 5. `TagClassification` decision tree (FR-4.7 / S-4.6 driver)

```text
function classify(ndefMessage: NdefMessage?) -> TagClassification:
    if ndefMessage is null:
        return Blank                              # Q-U2-7=B
    if ndefMessage.records.isEmpty():
        return Blank
    let record = ndefMessage.records[0]           # OpenSpool tags carry one record
    if record.payload is null OR record.payload.isEmpty():
        return Blank
    let mime = record.toMimeType()                # null if not a MIME record
    if mime != "application/json":
        return Vendor(reason = "non-JSON MIME: " + (mime ?: "<no mime>"))    # Q-U2-8=C — String for v2.0
    let utf8 = record.payload.toUtf8String()
    let decodeResult = OpenSpoolPayloadCodec.fromJson(utf8)
    return when (decodeResult) {
        Success(payload)            -> OpenSpool(payload)
        Malformed(reason)           -> Vendor(reason = "openspool but malformed: " + reason)
        NotOpenSpool                -> Vendor(reason = "non-openspool JSON")
    }
```

**Note**: this pseudocode is what U4's `NfcAdapterWrapper` will implement. U2 ships the *types* (`TagClassification`, `OpenSpoolDecodeResult`, `OpenSpoolPayload`); U4 ships the byte-handling glue. The decision tree is documented here so U4's implementation has a definitive contract.

---

## 6. `OpenSpoolPayloadCodec.fromJson(json)` — JSON → payload (S-3.4 prefill driver)

```text
function fromJson(json: String) -> OpenSpoolDecodeResult:                 # Q-U2-12=B
    # Step 1 — parse JSON. Top-level must be an object.
    let cleanJson = json.dropWhile { c -> c != '{' }                      # tolerate language prefix per v1
    let obj
    try:
        obj = parseJson(cleanJson)
    catch JsonException:
        return NotOpenSpool                                               # malformed JSON ≠ malformed openspool
    if obj is not a JSON object:
        return NotOpenSpool

    # Step 2 — protocol gate. Q-U2-10=A keeps protocol/version as fields.
    let protocol = obj.optString("protocol", "")
    if protocol != "openspool":
        return NotOpenSpool

    # Step 3 — required fields.
    let type = obj.optString("type", null)
    if type is null OR type is empty:
        return Malformed(reason = "missing type")
    let brand = obj.optString("brand", null)
    if brand is null OR brand is empty:
        return Malformed(reason = "missing brand")
    let minTemp = obj.optString("min_temp", null)
    if minTemp is null OR minTemp is empty:
        return Malformed(reason = "missing min_temp")
    let maxTemp = obj.optString("max_temp", null)
    if maxTemp is null OR maxTemp is empty:
        return Malformed(reason = "missing max_temp")

    # Step 4 — optional fields with v1-equivalent defaults.
    let version    = obj.optString("version", "1.0")
    let colorHex   = obj.optString("color_hex", "").nullIfEmpty()
    let bedMinTemp = obj.optString("bed_min_temp", "").nullIfEmpty()
    let bedMaxTemp = obj.optString("bed_max_temp", "").nullIfEmpty()
    let subtype    = obj.optString("subtype", "Basic")
    let spoolId    = obj.optString("spool_id", "").nullIfEmpty()
    let lotNr      = obj.optString("lot_nr", "").nullIfEmpty()            # Q-U2-9=B — read-only

    return Success(OpenSpoolPayload(
        protocol = "openspool",
        version = version,
        type = type,
        colorHex = colorHex,
        brand = brand,
        minTemp = minTemp,
        maxTemp = maxTemp,
        bedMinTemp = bedMinTemp,
        bedMaxTemp = bedMaxTemp,
        subtype = subtype,
        spoolId = spoolId,
        lotNr = lotNr,
    ))
```

**Unknown fields**: silently ignored — vendor-injected fields don't affect parsing.

---

## 7. `OpenSpoolPayloadCodec.toJson(payload)` — payload → JSON (FR-4.4 driver)

```text
function toJson(payload: OpenSpoolPayload) -> String:
    let obj = empty JSON object
    obj.put("protocol", payload.protocol)        # always "openspool"
    obj.put("version",  payload.version)         # always "1.0" by default
    obj.put("type",     payload.type)
    obj.put("color_hex", payload.colorHex ?: "") # v1 quirk: empty string when null
    obj.put("brand",    payload.brand)
    obj.put("min_temp", payload.minTemp)
    obj.put("max_temp", payload.maxTemp)
    if payload.bedMinTemp is non-null:
        obj.put("bed_min_temp", payload.bedMinTemp)
    if payload.bedMaxTemp is non-null:
        obj.put("bed_max_temp", payload.bedMaxTemp)
    if payload.spoolId is non-null:
        obj.put("spool_id", payload.spoolId)
    # Q-U2-9=B — never emit lot_nr field on encode, even if payload.lotNr is non-null.
    if payload.subtype is non-empty:
        obj.put("subtype", payload.subtype)
    return obj.toString()
```

**Round-trip property**:

```text
property: forall payload p where p.lotNr is null:
    fromJson(toJson(p)) == Success(p)
```

The `lotNr is null` precondition reflects Q-U2-9=B: write-side drops `lotNr`, so a payload that arrives with non-null `lotNr` will round-trip to `lotNr = null`. This is intentional — v1 tags-in-the-wild that carry `lotNr` get silently sanitised on the next write.

---

## 8. Algorithm summary

| Operation | Total (terminates on all inputs)? | Throws? | Domain → Range |
|---|---|---|---|
| `CardUid.fromBytes` | Yes | Never | `ByteArray` → `CardUid` (any length, including empty) |
| `CardUidEncoding.decode` | Yes | Never | `String` → `Decoded(uids, opaque)` |
| `CardUidEncoding.encode` | Yes | Never | `(List<CardUid>, String)` → `String` |
| `OpenSpoolPayloadCodec.fromJson` | Yes | Never | `String` → `OpenSpoolDecodeResult{Success | Malformed | NotOpenSpool}` |
| `OpenSpoolPayloadCodec.toJson` | Yes | Never | `OpenSpoolPayload` → `String` |
| `TagClassification` constructor | Yes (sealed) | Never | construction-only (no parsing logic) |

No exceptions cross the U2 boundary. Every rule's "what if it goes wrong?" answer is encoded in the *return type* (sealed result, opaque tail, empty list, etc.) — caller code branches on values, not on `try/catch`.
