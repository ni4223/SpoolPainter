# U2 — Domain Entities

**Stage**: CONSTRUCTION → Per-Unit Loop → Functional Design (U2)
**Source plan**: `aidlc-docs/construction/plans/u2-domain-primitives-functional-design-plan.md` (answered 2026-05-26)

This document is the **final type contract** for U2's primitives. Every signature here lands in `app/src/main/java/com/spoolpainter/app/...` during U2 Code Generation. Annotations and import details are illustrative; final code may polish formatting but MUST NOT change semantics.

---

## 1. `CardUid`

**File**: `domain/primitives/CardUid.kt`

```kotlin
package com.spoolpainter.app.domain.primitives

@JvmInline
value class CardUid(val hex: String) {
    override fun toString(): String = hex

    companion object {
        /**
         * Lowercase hex, two characters per byte, no separators, no length pad.
         * Empty input yields CardUid("") — total decoder; see BR-U2-CU-3.
         */
        fun fromBytes(bytes: ByteArray): CardUid =
            CardUid(bytes.joinToString("") { "%02x".format(it) })
    }
}
```

Rules: BR-U2-CU-1..6.

Consumers: U3 (Spoolman lookup), U4 (NfcResult + classifier), U5..U7 (flows).

---

## 2. `CardUidEncoding`

**File**: `data/remote/spoolman/CardUidEncoding.kt`

```kotlin
package com.spoolpainter.app.data.remote.spoolman

import com.spoolpainter.app.domain.primitives.CardUid

object CardUidEncoding {
    internal const val PREFIX = "card_uid:"

    data class Decoded(
        val uids: List<CardUid>,
        val opaque: String,
    )

    /** Parse `lot_nr` content into UID list + opaque tail. Total — never throws. */
    fun decode(input: String): Decoded { ... }

    /**
     * Render UID list + opaque tail into `lot_nr` content.
     * Deduplicates UIDs by canonical-hex equality (preserves first-seen order).
     * Opaque tail (if non-empty) appended after all UIDs with a single ',' separator.
     * Total — never throws.
     */
    fun encode(uids: List<CardUid>, opaque: String = ""): String { ... }
}
```

Rules: BR-U2-CE-1, BR-U2-DEC-1..9, BR-U2-ENC-1..6, BR-U2-RT-1..4.

Consumers: U3 (PATCH body construction; `findSpoolsByCardUid` URL-builder uses `PREFIX`), U6b (move-on-bind PATCH).

---

## 3. `TagClassification`

**File**: `domain/primitives/TagClassification.kt`

```kotlin
package com.spoolpainter.app.domain.primitives

import com.spoolpainter.app.domain.models.OpenSpoolPayload

sealed interface TagClassification {
    data object Blank : TagClassification
    data class OpenSpool(val payload: OpenSpoolPayload) : TagClassification
    data class Vendor(val reason: String) : TagClassification
}
```

Rules: BR-U2-TC-1..6.

Consumers: U4 (classifier producer), U5..U7 (flow branching).

**Note on package**: `TagClassification` lives in `domain/primitives/` (not `domain/models/`) per components.md §2.8 — it's a primitive sum type that drives sealed branching, not a data record.

---

## 4. `OpenSpoolPayload`

**File**: `domain/models/OpenSpoolPayload.kt`

```kotlin
package com.spoolpainter.app.domain.models

data class OpenSpoolPayload(
    val protocol: String = "openspool",
    val version: String = "1.0",
    val type: String,
    val colorHex: String?,
    val brand: String,
    val minTemp: String,
    val maxTemp: String,
    val bedMinTemp: String? = null,
    val bedMaxTemp: String? = null,
    val subtype: String = "Basic",
    val spoolId: String? = null,
    val lotNr: String? = null,                 // read-only field — see BR-U2-OP-3
)
```

Rules: BR-U2-OP-1..5.

Consumers: U4 (NFC payload type for `arm(Write(payload))`), U5 (form prefill), U6a/U6b (Create-and-Pair / Two-tag), U7 (Raw-write).

**Migration note** (Q-U2-13=A): this file replaces v1 `domain/models/OpenSpoolData.kt`, which is deleted in U2.

---

## 5. `OpenSpoolPayloadCodec`

**File**: `domain/primitives/OpenSpoolPayloadCodec.kt`

```kotlin
package com.spoolpainter.app.domain.primitives

import com.spoolpainter.app.domain.models.OpenSpoolPayload

/**
 * Pure JSON ↔ OpenSpoolPayload codec. No Android dependencies.
 * Exceptions never cross this boundary — all failures map to OpenSpoolDecodeResult cases.
 */
object OpenSpoolPayloadCodec {
    fun fromJson(json: String): OpenSpoolDecodeResult { ... }
    fun toJson(payload: OpenSpoolPayload): String { ... }
}
```

Rules: BR-U2-CO-1..10.

Consumers: U4 (classifier reads NDEF bytes → utf8 string → `fromJson`; writer takes `OpenSpoolPayload` → `toJson` → utf8 → NDEF bytes).

---

## 6. `OpenSpoolDecodeResult`

**File**: `domain/primitives/OpenSpoolDecodeResult.kt`

```kotlin
package com.spoolpainter.app.domain.primitives

import com.spoolpainter.app.domain.models.OpenSpoolPayload

sealed interface OpenSpoolDecodeResult {
    data class Success(val payload: OpenSpoolPayload) : OpenSpoolDecodeResult
    data class Malformed(val reason: String) : OpenSpoolDecodeResult
    data object NotOpenSpool : OpenSpoolDecodeResult
}
```

Rules: BR-U2-DR-1..4.

Consumers: U4 classifier (`when` branch into `TagClassification`).

---

## 7. Type-relationship summary

```
CardUid                          (value class)
  ▲
  │ owned by
  │
CardUidEncoding.Decoded          (data class, via List<CardUid> + String)
CardUidEncoding                  (object)

OpenSpoolPayload                 (data class — pure data)
  ▲
  │ wrapped by
  │
TagClassification.OpenSpool      ──┐
TagClassification.Blank            ├─ sealed
TagClassification.Vendor           ┘
                                   ▲
                                   │ produced by
                                   │
                                   U4 classifier (out of U2 scope)

OpenSpoolPayloadCodec            (object)
   │ produces
   ▼
OpenSpoolDecodeResult            (sealed)
  ├─ Success(OpenSpoolPayload)
  ├─ Malformed(reason)
  └─ NotOpenSpool
```

No cycles. Every U2 primitive depends only on Kotlin stdlib + (in the case of `OpenSpoolPayloadCodec`) `org.json`.

---

## 8. What U2 does NOT ship

- **`NfcResult.Success` / `NfcResult.Error`** — final `NfcResult` cases that reference `CardUid` + `TagClassification` are added in U4 once these types are stable (see U1's deferred fields in `NfcResult.kt`).
- **`NfcIntent.Write` / `NfcIntent.Verify`** — finalised in U4 against `OpenSpoolPayload` + `CardUid`.
- **NDEF byte-level parsing** — U4 owns `NfcAdapterWrapper.classify(ndefMessage: NdefMessage?)`.
- **DecodedVendorPayload** + `Vendor(decoded: ...)` refinement — v2.1 (U11 stub).

---

## 9. File checklist (preview for Code Generation Part 1)

| Path | Action |
|---|---|
| `app/src/main/java/com/spoolpainter/app/domain/primitives/CardUid.kt` | **CREATE** |
| `app/src/main/java/com/spoolpainter/app/domain/primitives/TagClassification.kt` | **CREATE** |
| `app/src/main/java/com/spoolpainter/app/domain/primitives/OpenSpoolPayloadCodec.kt` | **CREATE** |
| `app/src/main/java/com/spoolpainter/app/domain/primitives/OpenSpoolDecodeResult.kt` | **CREATE** |
| `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/CardUidEncoding.kt` | **CREATE** |
| `app/src/main/java/com/spoolpainter/app/domain/models/OpenSpoolPayload.kt` | **CREATE** |
| `app/src/main/java/com/spoolpainter/app/domain/models/OpenSpoolData.kt` | **DELETE** |
| `app/src/main/java/com/spoolpainter/app/domain/models/FilamentSpool.kt` | **MIGRATE or DELETE** (decision in CodeGen Part 1; depends on remaining consumer count) |
| `app/src/main/java/com/spoolpainter/app/hardware/nfc/NfcManager.kt` | **MIGRATE or DELETE** (decision in CodeGen Part 1) |
| `app/src/main/java/com/spoolpainter/app/hardware/nfc/NfcController.kt` | **MIGRATE or DELETE** |
| `app/src/main/java/com/spoolpainter/app/hardware/nfc/NfcHandler.kt` | **MIGRATE or DELETE** |
| `app/src/test/java/com/spoolpainter/app/domain/primitives/CardUidTest.kt` | **CREATE** |
| `app/src/test/java/com/spoolpainter/app/data/remote/spoolman/CardUidEncodingDecodeTest.kt` | **CREATE** |
| `app/src/test/java/com/spoolpainter/app/data/remote/spoolman/CardUidEncodingEncodeTest.kt` | **CREATE** |
| `app/src/test/java/com/spoolpainter/app/data/remote/spoolman/CardUidEncodingRoundTripTest.kt` | **CREATE** |
| `app/src/test/java/com/spoolpainter/app/domain/primitives/OpenSpoolPayloadCodecTest.kt` | **CREATE** |

(Existing U1 placeholder files: `domain/primitives/NfcResult.kt`, `NfcIntent.kt` remain unchanged in U2 — they're U4's territory.)
