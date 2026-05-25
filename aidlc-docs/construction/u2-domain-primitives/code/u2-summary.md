# U2 — Domain Primitives — Code Generation Summary

**Stage**: CONSTRUCTION → Code Generation Part 2 (Generation) — complete
**Generated**: 2026-05-26
**Plan**: `aidlc-docs/construction/plans/u2-domain-primitives-code-generation-plan.md`
**Functional Design**: `aidlc-docs/construction/u2-domain-primitives/functional-design/`

---

## Files Created

| Path | Purpose | FRs / NFRs / Stories |
|---|---|---|
| `app/src/main/java/com/spoolpainter/app/domain/primitives/CardUid.kt` | `@JvmInline` value class wrapping lowercase-hex `String`; `fromBytes(ByteArray)` factory | FR-1.2, FR-1.3, S-1.2 |
| `app/src/main/java/com/spoolpainter/app/domain/models/OpenSpoolPayload.kt` | v2 OpenSpool data class — replaces v1 `OpenSpoolData`; `lotNr` field is read-side only | FR-14.1; consumed by U4..U7 |
| `app/src/main/java/com/spoolpainter/app/domain/primitives/OpenSpoolDecodeResult.kt` | Sealed result type for codec failures: `Success / Malformed(reason) / NotOpenSpool` | NFR-7.1 |
| `app/src/main/java/com/spoolpainter/app/domain/primitives/OpenSpoolPayloadCodec.kt` | `object` codec — pure JSON ↔ payload; never throws | NFR-1.1 |
| `app/src/main/java/com/spoolpainter/app/domain/primitives/TagClassification.kt` | Sealed: `Blank / OpenSpool(payload) / Vendor(reason: String)` | FR-4.7 driver; consumed by U4..U7 |
| `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/CardUidEncoding.kt` | `object` — `PREFIX = "card_uid:"`; `decode(String) → Decoded(uids, opaque)`; `encode(uids, opaque)` with dedup | FR-2.1, FR-2.2, S-2.1, S-2.2 |
| `app/src/test/java/com/spoolpainter/app/domain/primitives/CardUidTest.kt` | 12 cases — empty / single / multi-byte / equality / lowercase invariant | NFR-4.1, BR-U2-T-1 |
| `app/src/test/java/com/spoolpainter/app/data/remote/spoolman/CardUidEncodingDecodeTest.kt` | 16 cases — empty / canonical / multi / case / whitespace / malformed → opaque / interleaved | NFR-4.1, BR-U2-T-2 |
| `app/src/test/java/com/spoolpainter/app/data/remote/spoolman/CardUidEncodingEncodeTest.kt` | 8 cases — empty / single / multi / dedup / opaque-only / combined | NFR-4.1, BR-U2-T-3 |
| `app/src/test/java/com/spoolpainter/app/data/remote/spoolman/CardUidEncodingRoundTripTest.kt` | 5 round-trip / fixed-point properties | NFR-4.1, BR-U2-T-4, S-2.2 |
| `app/src/test/java/com/spoolpainter/app/domain/primitives/OpenSpoolPayloadCodecTest.kt` | 17 cases — round-trip / required-field validation / non-openspool / malformed JSON / lot_nr drop / unknown fields | NFR-4.1, BR-U2-T-5 |
| `app/src/test/java/com/spoolpainter/app/domain/primitives/OpenSpoolDecodeResultTest.kt` | 6 cases — sealed-type equality + exhaustiveness | NFR-4.1, BR-U2-T-6 |

## Files Modified

| Path | Change |
|---|---|
| `app/src/main/java/com/spoolpainter/app/domain/models/FilamentSpool.kt` | Removed `import com.spoolpainter.app.domain.models.OpenSpoolData`; removed `fun fromOpenSpool(spool: OpenSpoolData): FilamentSpool { ... }` companion method. `fromSpoolman` retained for U3. |
| `app/build.gradle.kts` | Added `testImplementation("org.json:json:20231013")` — required because `org.json.JSONObject` is provided by Android at runtime but not on the JVM unit-test classpath, and `OpenSpoolPayloadCodecTest` exercises the codec via JVM unit tests. |

## Files Deleted

| Path | Reason |
|---|---|
| `app/src/main/java/com/spoolpainter/app/domain/models/OpenSpoolData.kt` | v1 type replaced by `OpenSpoolPayload`; per Q-U2-13=A big-bang delete. |

## Files Kept Dormant (deleted in later units)

Per plan §3.4 (explicit non-action list — NOT touched in U2):

- `hardware/nfc/{NfcManager,NfcController,NfcHandler}.kt` — dormant; **U4** deletes/replaces with `NfcAdapterWrapper` + `NfcRepository`.
- `data/remote/spoolman/SpoolmanService.kt` — dormant; **U3** replaces with `SpoolmanRepository`.
- `domain/models/{AppState,NfcTag,NfcResult,Material,SpoolmanModels}.kt` — dormant. v1 `domain/models/NfcResult.kt` coexists with U1's `domain/primitives/NfcResult.kt` (different packages). **U3 / U4 / U5** dispose.
- `data/local/{MaterialDatabase,BrandDatabase}.kt` — used by retained `MaterialSelector` / `BrandSelector` Compose components; **U8** migrates into `MaterialPresetSource` / `BrandPresetSource`.

---

## Story Coverage

| Story | Title | Status | Coverage |
|---|---|---|---|
| **S-1.2** | Canonicalise the UID as lowercase hex | ✅ | `CardUid.fromBytes` produces lowercase hex with no separators / pad (BR-U2-CU-2). Tested by `CardUidTest` (12 cases). |
| **S-2.1** | Parse a `lot_nr` value into UID entries + opaque tail | ✅ | `CardUidEncoding.decode` (BR-U2-DEC-1..9). Tested by `CardUidEncodingDecodeTest` (16 cases). |
| **S-2.2** | Serialise UID entries back into a `lot_nr` value | ✅ | `CardUidEncoding.encode` (BR-U2-ENC-1..6) + round-trip idempotency (BR-U2-RT-1..4). Tested by `CardUidEncodingEncodeTest` (8 cases) + `CardUidEncodingRoundTripTest` (5 cases). |

Forward-referenced types produced (consumed by later units, no story burden in U2):
- `TagClassification`, `OpenSpoolPayload`, `OpenSpoolPayloadCodec`, `OpenSpoolDecodeResult` — U4/U5/U7 consumers.

---

## Public Interfaces Produced

```kotlin
// domain/primitives/CardUid.kt
@JvmInline value class CardUid(val hex: String) {
    override fun toString(): String   // returns hex verbatim
    companion object {
        fun fromBytes(bytes: ByteArray): CardUid   // total — empty bytes → CardUid("")
    }
}

// data/remote/spoolman/CardUidEncoding.kt
object CardUidEncoding {
    internal const val PREFIX: String = "card_uid:"
    data class Decoded(val uids: List<CardUid>, val opaque: String)
    fun decode(input: String): Decoded                              // total
    fun encode(uids: List<CardUid>, opaque: String = ""): String    // total; dedups uids
}

// domain/primitives/TagClassification.kt
sealed interface TagClassification {
    data object Blank : TagClassification
    data class OpenSpool(val payload: OpenSpoolPayload) : TagClassification
    data class Vendor(val reason: String) : TagClassification
}

// domain/models/OpenSpoolPayload.kt — see file for full data class shape

// domain/primitives/OpenSpoolDecodeResult.kt
sealed interface OpenSpoolDecodeResult {
    data class Success(val payload: OpenSpoolPayload) : OpenSpoolDecodeResult
    data class Malformed(val reason: String) : OpenSpoolDecodeResult
    data object NotOpenSpool : OpenSpoolDecodeResult
}

// domain/primitives/OpenSpoolPayloadCodec.kt
object OpenSpoolPayloadCodec {
    fun fromJson(json: String): OpenSpoolDecodeResult
    fun toJson(payload: OpenSpoolPayload): String
}
```

Consumers per `unit-of-work-dependency.md`:
- **U3** (next unit) — uses `CardUid`, `CardUidEncoding.PREFIX`, `CardUidEncoding.{decode,encode}`.
- **U4** — uses `CardUid`, `OpenSpoolPayload`, `OpenSpoolPayloadCodec`, `OpenSpoolDecodeResult`, `TagClassification`.
- **U5..U7** — flow units consume `CardUid`, `TagClassification`, `OpenSpoolPayload`.

---

## Forward References Deferred

| Type / API | Lands in | Reason |
|---|---|---|
| `NfcResult.Success(uid, classification)` / `NfcResult.Error(reason, cause?)` | U4 | Now unblocked — U4 can complete the sealed type using U2's `CardUid` + `TagClassification`. |
| `NfcIntent.Write(payload, expectedUid?)` / `NfcIntent.Verify(expectedPayload)` | U4 | Now unblocked — U4 can complete using U2's `OpenSpoolPayload` + `CardUid`. |
| Tag classifier algorithm (`classify(NdefMessage?) → TagClassification`) | U4 | U2 ships only the type. Pseudocode in `business-logic-model.md` §5 is U4's contract. |
| `Vendor(decoded: DecodedVendorPayload?)` refinement | U11 (v2.1) | U2 keeps `reason: String` — Q-U2-8=C deferral is explicit. |

---

## Build & Test Verification

| Task | Outcome |
|---|---|
| `./gradlew :app:compileDebugKotlin` | ✅ Pass — only pre-existing Compose deprecation warnings on retained v1 components (`BrandSelector`, `ColorSelector`, `MaterialSelector`, `Theme.statusBarColor`); same set as U1, no U2 additions. |
| `./gradlew :app:testDebugUnitTest` | ✅ Pass — **68 / 68 tests pass, 0 failures, 0 skipped**: U1 `SettingsRepositoryTest` 4 + `CardUidTest` 12 + `CardUidEncodingDecodeTest` 16 + `CardUidEncodingEncodeTest` 8 + `CardUidEncodingRoundTripTest` 5 + `OpenSpoolDecodeResultTest` 6 + `OpenSpoolPayloadCodecTest` 17. |
| `./gradlew :app:assembleDebug` | ✅ Pass — produces `app/build/outputs/apk/debug/app-debug.apk` (33 MB, no growth from U1's 34 MB baseline — APK size is dominated by Compose + Hilt; U2 adds only small Kotlin source). |
| Brownfield invariant `grep -rn "OpenSpoolData" app/src` | ✅ Pass — zero matches. |

### JDK note (unchanged from U1)

Builds invoked with `JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-17.jdk/Contents/Home`. Default machine `JAVA_HOME` (JDK 24) breaks Gradle 8.13's `Type T not present` reflection. Durable fix deferred to U10 release polish.

---

## Exit-Criteria Checklist (per `unit-of-work.md` §3-U2)

- [x] Pure unit tests pass for `CardUid.fromBytes` lowercase + no-separator output.
- [x] `CardUidEncoding.decode` parses canonical, mixed-case, whitespace, empty, tail-only inputs.
- [x] `CardUidEncoding.encode` round-trip with `decode` is idempotent.
- [x] Unrecognised entries preserved verbatim on decode/encode round-trip.
- [x] `OpenSpoolPayload` rename complete; v1 `OpenSpoolData.kt` deleted.
- [x] Compile passes; tests pass; APK assembles.
- [x] Public interfaces produced are stable for U3 / U4 / U5..U7 consumption (no further changes expected without an explicit unit revisit).

**No milestone install gate** — per `unit-of-work.md` §2, U2 is verified by unit tests; install gates are at U1 / U5 / U6 / U10.

---

## Forbidden Patterns Audit

| Pattern (from `unit-of-work-dependency.md` §5) | Status |
|---|---|
| UI → data source direct calls | ✅ N/A — U2 ships no UI. |
| Activity / Context in Hilt graph | ✅ N/A — U2 ships no Hilt-bound types. |
| Use-cases holding state | ✅ N/A — U2 ships no use-cases. |
| Service locator / event bus | ✅ N/A. |
| `android.util.Log` in primitives / codec | ✅ none — `OpenSpoolPayloadCodec` is pure JVM. |
| Exceptions crossing the U2 boundary | ✅ none — `JSONException` caught inside codec; all failures map to sealed `OpenSpoolDecodeResult`. |
| `OpenSpoolData` references after U2 | ✅ zero (verified by grep). |

---

## Functional-Design Rule Coverage Audit

Spot-check map of representative business-rules → code locations (see `business-rules.md` for full list):

| Rule | Code line(s) |
|---|---|
| BR-U2-CU-2 (lowercase hex, two chars per byte, no separator) | `CardUid.kt:9` — `bytes.joinToString("") { "%02x".format(it) }` |
| BR-U2-CU-3 (empty bytes → empty CardUid; never throws) | `CardUid.kt:9` (joinToString on empty = empty string) + `CardUidTest::fromBytes empty returns empty CardUid` |
| BR-U2-CE-1 (`PREFIX` const) | `CardUidEncoding.kt:7` — `internal const val PREFIX = "card_uid:"` |
| BR-U2-DEC-2 (comma-only separator) | `CardUidEncoding.kt:18` — `input.split(",")` |
| BR-U2-DEC-3 (whitespace trim around entry) | `CardUidEncoding.kt:19` — `raw.trim()` |
| BR-U2-DEC-5 (case-insensitive prefix match) | `CardUidEncoding.kt:23-25` — `regionMatches(..., ignoreCase = true)` |
| BR-U2-DEC-6 (strict hex validation: non-empty + even-length + all hex) | `CardUidEncoding.kt:27-30` |
| BR-U2-DEC-7 (malformed entry preserved verbatim, original whitespace retained) | `CardUidEncoding.kt:30-32` — `opaqueEntries.add(raw)` (untrimmed) |
| BR-U2-ENC-1 (dedup by canonical-hex equality, first-seen order) | `CardUidEncoding.kt:43` — `uids.distinct()` |
| BR-U2-ENC-5 (empty UIDs + non-empty opaque → opaque verbatim, no leading comma) | `CardUidEncoding.kt:45-47` — conditional list construction |
| BR-U2-CO-3 (tolerate leading non-`{` prefix) | `OpenSpoolPayloadCodec.kt:11` |
| BR-U2-CO-5 (protocol gate; non-openspool → NotOpenSpool) | `OpenSpoolPayloadCodec.kt:18-20` |
| BR-U2-CO-7 (canonical key order; lot_nr never emitted) | `OpenSpoolPayloadCodec.kt:51-66` (no `obj.put("lot_nr", …)` line) |
| BR-U2-CO-8 (color_hex emitted as empty string when null — v1 quirk) | `OpenSpoolPayloadCodec.kt:57` — `payload.colorHex ?: ""` |
| BR-U2-OP-3 (lotNr read-on-decode, never write-on-encode) | Decode: `OpenSpoolPayloadCodec.kt:43`; Encode: absence of `lot_nr` put |
| BR-U2-MIG-1 + BR-U2-MIG-2 (zero `OpenSpoolData` refs) | Verified by `grep -rn` post-§3 |
