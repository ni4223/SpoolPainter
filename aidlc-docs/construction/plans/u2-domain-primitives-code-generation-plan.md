# U2 — Code Generation Plan

**Stage**: CONSTRUCTION → Per-Unit Loop → Code Generation Part 1 (Planning)
**Unit**: U2 — Domain Primitives
**Status**: DRAFT — awaiting user approval (Step 7)

**Source artefacts** (single source of truth — this plan executes them; **no logic decisions outside this plan**):
- `aidlc-docs/construction/u2-domain-primitives/functional-design/business-logic-model.md` — algorithmic pseudocode.
- `aidlc-docs/construction/u2-domain-primitives/functional-design/business-rules.md` — 47 rules with FR/S/Q traceability.
- `aidlc-docs/construction/u2-domain-primitives/functional-design/domain-entities.md` — final type signatures + file checklist.
- `aidlc-docs/inception/application-design/unit-of-work.md` §3-U2 — unit scope + DoD.
- `aidlc-docs/inception/application-design/components.md` §2.1, §2.6, §2.8, §3 — type sketches.

---

## Unit Generation Context (Step 3)

### Stories implemented by U2
| Story | Title | Coverage |
|---|---|---|
| **S-1.2** | Canonicalise the UID as lowercase hex | `CardUid.fromBytes` returns lowercase hex, no separators, no length pad (BR-U2-CU-2). UI display uses `CardUid.toString()` (BR-U2-CU-4). |
| **S-2.1** | Parse a `lot_nr` value into UID entries + opaque tail | `CardUidEncoding.decode` (BR-U2-DEC-1..9). |
| **S-2.2** | Serialise UID entries back into a `lot_nr` value | `CardUidEncoding.encode` (BR-U2-ENC-1..6). Round-trip idempotency (BR-U2-RT-1..4). |

### Dependencies on prior units
- **U1 (Architecture & DI Scaffold)** — DONE 2026-05-25. U2 inherits Hilt + DI scaffold + U1 placeholder skeletons (`NfcResult` Idle/Reading/Writing/Verifying; `NfcIntent` Read). U2 does **not** modify those skeletons — `Success/Error` and `Write/Verify` cases are explicitly U4 territory per `unit-of-work.md` §3-U4.

### Public interfaces produced (consumed in later units)
- `CardUid` — value class wrapping lowercase-hex `String`. Consumed by U3 (Spoolman lookup), U4 (NFC read result), U5..U7 (flows).
- `CardUidEncoding.PREFIX` (`internal const val "card_uid:"`) — consumed by U3.
- `CardUidEncoding.Decoded(uids, opaque)` + `decode/encode` — consumed by U3 (`appendCardUidToSpool` / `removeCardUidFromSpool` PATCH body) + U6b (move-on-bind).
- `TagClassification` sealed type (`Blank | OpenSpool(payload) | Vendor(reason: String)`) — consumed by U4 (classifier output) + U5..U7 (branch logic).
- `OpenSpoolPayload` — consumed by U4 (NFC payload type), U5..U7 (form / write flows).
- `OpenSpoolPayloadCodec.fromJson/toJson` + `OpenSpoolDecodeResult` (`Success | Malformed(reason) | NotOpenSpool`) — consumed by U4.

### Database entities owned by U2
- None. U2 is pure value types + parsers; no DataStore / Room / network state.

### Service boundaries
- U2 ships zero IO. Every U2 type is JVM-pure; only `org.json` (for `OpenSpoolPayloadCodec`) is used and it has no Android dependencies on the test classpath.

---

## Project Structure Note (Step 2 — brownfield)

Workspace root: `/Users/mnipun/AndroidStudioProjects/SpoolPainter` (per `aidlc-state.md`).
Project type: **Brownfield** Android single-module app.
Code locations:
- **Application code** → `app/src/main/java/com/spoolpainter/app/...` (workspace root). Package layout per `.kiro/steering/structure.md`.
- **Tests** → `app/src/test/java/com/spoolpainter/app/...`.
- **Documentation** → `aidlc-docs/construction/u2-domain-primitives/code/` (markdown summaries only; no app code).
- **Build/config** → unchanged (no new dependencies — see §1.0).

---

## §1 — Build / Dependency Setup

> **U2 ships zero new runtime dependencies.** All required libraries (`org.json`, `kotlin-stdlib`, `kotlinx-coroutines-test`, `Turbine`, `JUnit 4`) are already pulled in via U1's `gradle/libs.versions.toml` and `app/build.gradle.kts`.

- [x] **1.0 Verify no dependency changes needed.** Confirm via `app/build.gradle.kts` review that no edit is required. (Story: NFR-4.1 minimum-bar baseline.)

---

## §2 — Business Logic Generation (primitives)

> Section creates the seven type files per `domain-entities.md` §9 file checklist.

- [x] **2.1 Create `CardUid`.**
  - Path: `app/src/main/java/com/spoolpainter/app/domain/primitives/CardUid.kt`.
  - Body: `@JvmInline value class CardUid(val hex: String)` with `override fun toString() = hex` and a `companion object { fun fromBytes(bytes: ByteArray): CardUid = CardUid(bytes.joinToString("") { "%02x".format(it) }) }`.
  - Rules enforced: BR-U2-CU-1..6.
  - Story: **S-1.2**.

- [x] **2.2 Create `OpenSpoolPayload`** (rename of v1 `OpenSpoolData`, v2 contract).
  - Path: `app/src/main/java/com/spoolpainter/app/domain/models/OpenSpoolPayload.kt`.
  - Body: `data class OpenSpoolPayload(val protocol: String = "openspool", val version: String = "1.0", val type: String, val colorHex: String?, val brand: String, val minTemp: String, val maxTemp: String, val bedMinTemp: String? = null, val bedMaxTemp: String? = null, val subtype: String = "Basic", val spoolId: String? = null, val lotNr: String? = null)`.
  - Rules enforced: BR-U2-OP-1..5.
  - Story: forward-reference type for U4..U7.

- [x] **2.3 Create `OpenSpoolDecodeResult`.**
  - Path: `app/src/main/java/com/spoolpainter/app/domain/primitives/OpenSpoolDecodeResult.kt`.
  - Body: `sealed interface OpenSpoolDecodeResult { data class Success(val payload: OpenSpoolPayload) : OpenSpoolDecodeResult; data class Malformed(val reason: String) : OpenSpoolDecodeResult; data object NotOpenSpool : OpenSpoolDecodeResult }`.
  - Rules enforced: BR-U2-DR-1..4.

- [x] **2.4 Create `OpenSpoolPayloadCodec`.**
  - Path: `app/src/main/java/com/spoolpainter/app/domain/primitives/OpenSpoolPayloadCodec.kt`.
  - Body: `object OpenSpoolPayloadCodec` with two methods:
    - `fun fromJson(json: String): OpenSpoolDecodeResult` — implementation per `business-logic-model.md` §6 pseudocode (try `JSONObject` parse inside try/catch returning `NotOpenSpool` on `JSONException`; protocol gate; required-field checks emit `Malformed("missing <field>")`; populate from optional fields with v1 defaults).
    - `fun toJson(payload: OpenSpoolPayload): String` — implementation per §7 pseudocode (canonical key order; `lot_nr` never emitted; `color_hex` emitted as empty string when null per v1 quirk).
  - Rules enforced: BR-U2-CO-1..10.
  - **No `android.util.Log` calls** (BR-U2-CO-1 forbids Android dependencies — also matches v2 NFR-5 release-build log-strip spirit).

- [x] **2.5 Create `TagClassification`.**
  - Path: `app/src/main/java/com/spoolpainter/app/domain/primitives/TagClassification.kt`.
  - Body: `sealed interface TagClassification { data object Blank : TagClassification; data class OpenSpool(val payload: OpenSpoolPayload) : TagClassification; data class Vendor(val reason: String) : TagClassification }`.
  - Rules enforced: BR-U2-TC-1..6.
  - **Note**: classifier algorithm (deciding which case) lives in U4. U2 ships the type only.

- [x] **2.6 Create `CardUidEncoding`.**
  - Path: `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/CardUidEncoding.kt`.
  - Body: `object CardUidEncoding` with:
    - `internal const val PREFIX = "card_uid:"`.
    - `data class Decoded(val uids: List<CardUid>, val opaque: String)`.
    - `fun decode(input: String): Decoded` — implementation per `business-logic-model.md` §2 pseudocode.
    - `fun encode(uids: List<CardUid>, opaque: String = ""): String` — implementation per §3 pseudocode (uses `uids.distinct()` for dedup-on-encode).
  - Rules enforced: BR-U2-CE-1, BR-U2-DEC-1..9, BR-U2-ENC-1..6.
  - Story: **S-2.1, S-2.2**.

---

## §3 — Brownfield Migration (v1 cleanup per Q-U2-13=A)

> Decision per Functional Design Q-U2-13=A: big-bang delete of `OpenSpoolData.kt`. Cascade strictly limited to direct callers; dormant v1 chains untouched (those are U3/U4 territory).

- [x] **3.1 Delete v1 `OpenSpoolData.kt`.**
  - Path: `app/src/main/java/com/spoolpainter/app/domain/models/OpenSpoolData.kt`.
  - Rule enforced: BR-U2-MIG-1, BR-U2-MIG-5.

- [x] **3.2 Migrate `FilamentSpool.kt` — drop `fromOpenSpool` method and its import.**
  - Path: `app/src/main/java/com/spoolpainter/app/domain/models/FilamentSpool.kt`.
  - Edit: remove `import com.spoolpainter.app.domain.models.OpenSpoolData` and the entire `fun fromOpenSpool(spool: OpenSpoolData): FilamentSpool { ... }` block (lines 77-91 of current file). Keep the rest of `FilamentSpool` (data-class shell + `fromSpoolman`) unchanged — it's still imported by the dormant `SpoolmanService.kt` and slated for full replacement in U3.
  - Rule enforced: BR-U2-MIG-2, BR-U2-MIG-3.
  - **Decision**: keep the file rather than delete it — `FilamentSpool.fromSpoolman` is non-trivial and U3 may want to consult or reuse parts of it; full replacement happens at U3's discretion.

- [x] **3.3 Brownfield invariant check.**
  - After §3.1 + §3.2: run `grep -rn "OpenSpoolData" app/src` and confirm **zero matches**.
  - Rule enforced: BR-U2-MIG-2 end-of-U2 invariant.

- [x] **3.4 Out-of-scope dormant files** (explicit non-action — recorded for traceability):
  - `hardware/nfc/{NfcManager,NfcController,NfcHandler}.kt` — dormant; **no edit in U2**. U4 deletes/replaces.
  - `data/remote/spoolman/SpoolmanService.kt` — dormant; **no edit in U2**. U3 replaces.
  - `domain/models/{AppState,NfcTag,NfcResult,Material,SpoolmanModels}.kt` — dormant (v1 NfcResult coexists with U1's primitives/NfcResult.kt because they're in different packages). **No edit in U2.** U3/U4 dispose.
  - `data/local/{MaterialDatabase,BrandDatabase}.kt` + `domain/models/Material.kt` — used by U1-retained `MaterialSelector` / `BrandSelector` Compose components; **no edit in U2.** U8 migrates into preset sources.

---

## §4 — Business Logic Unit Testing

> Tests live under `app/src/test/...`. JUnit 4 + plain assertions (no MockK/Turbine needed for U2 — U2 is pure data).

- [x] **4.1 Create `CardUidTest`.**
  - Path: `app/src/test/java/com/spoolpainter/app/domain/primitives/CardUidTest.kt`.
  - Cases per BR-U2-T-1: empty bytes → `CardUid("")`; single byte (0x00, 0x0F, 0xFF, 0x4A); 4-byte UID (e.g., `04a1b2c3`); 7-byte UID (`04a1b2c3d4e580`); 10-byte UID; `toString()` returns hex verbatim; equality (lowercase / lowercase) yes; equality (lowercase / uppercase via constructor) no — documented as expected fragility per BR-U2-CU-5; `fromBytes` then constructor-from-string yields equal `CardUid`.

- [x] **4.2 Create `CardUidEncodingDecodeTest`.**
  - Path: `app/src/test/java/com/spoolpainter/app/data/remote/spoolman/CardUidEncodingDecodeTest.kt`.
  - Cases per BR-U2-T-2: empty input; whitespace-only input; single canonical UID; multi-UID; mixed-case prefix (`CARD_UID:AABB` → uid `aabb`); leading + trailing whitespace around entry (` card_uid:aabb `); non-hex value (`card_uid:zz`) → opaque; odd-length value (`card_uid:abc`) → opaque; empty value (`card_uid:`) → opaque; opaque-only input (`batch=42`); UID + opaque interleaved (`card_uid:aa,batch=42,card_uid:bb,notes=foo`) — UIDs collected, opaque is `"batch=42,notes=foo"`; double-comma fragments handled (`card_uid:aa,,card_uid:bb`); leading/trailing comma handled (`,card_uid:aa,`).

- [x] **4.3 Create `CardUidEncodingEncodeTest`.**
  - Path: `app/src/test/java/com/spoolpainter/app/data/remote/spoolman/CardUidEncodingEncodeTest.kt`.
  - Cases per BR-U2-T-3: empty list + empty opaque → `""`; single UID + empty opaque → `"card_uid:aabb"`; multi-UID → `"card_uid:aabb,card_uid:ccdd"`; duplicate UIDs (`[aabb, aabb]`) → dedup to `"card_uid:aabb"`; UIDs with same hex but provided multiple times preserve first-seen order (`[bb, aa, bb]` → `"card_uid:bb,card_uid:aa"`); empty UIDs + non-empty opaque → opaque verbatim; UIDs + opaque → trailing-comma-then-opaque format.

- [x] **4.4 Create `CardUidEncodingRoundTripTest`.**
  - Path: `app/src/test/java/com/spoolpainter/app/data/remote/spoolman/CardUidEncodingRoundTripTest.kt`.
  - Cases per BR-U2-T-4 (round-trip property tests written as parameterised inputs through JUnit 4 `@Test` methods, not `@RunWith(Parameterized)` — keep simple):
    - `decode(encode(uids.distinct(), opaque)).uids == uids.distinct()` for representative inputs.
    - `decode(encode(uids.distinct(), opaque)).opaque == opaque`.
    - For mixed-case input `"card_uid:AABB"`, after one round-trip, output is `"card_uid:aabb"` and second round-trip is a fixed point.
    - For interleaved input `"card_uid:aa,batch=42,card_uid:bb"`, after one round-trip: output is `"card_uid:aa,card_uid:bb,batch=42"`, and second round-trip is fixed.

- [x] **4.5 Create `OpenSpoolPayloadCodecTest`.**
  - Path: `app/src/test/java/com/spoolpainter/app/domain/primitives/OpenSpoolPayloadCodecTest.kt`.
  - Cases per BR-U2-T-5:
    - **Round-trip with full payload** — `payload(lotNr = null, all-other-fields-set)` → `toJson` → `fromJson` → `Success(payload)` (equal).
    - **Round-trip with minimal payload** — only required fields (`type`, `brand`, `min_temp`, `max_temp`); decoded back, optional fields are at defaults / null.
    - **Missing required field — type** → `Malformed(reason="missing type")`.
    - **Missing required field — brand** → `Malformed(reason="missing brand")`.
    - **Missing required field — min_temp** → `Malformed(reason="missing min_temp")`.
    - **Missing required field — max_temp** → `Malformed(reason="missing max_temp")`.
    - **Non-openspool protocol** (`{"protocol":"foo",...}`) → `NotOpenSpool`.
    - **Missing protocol field** → `NotOpenSpool`.
    - **Invalid JSON** (`"not a json"`, `"{"`, empty string) → `NotOpenSpool`.
    - **JSON with leading non-`{` prefix** (e.g., `en{"protocol":"openspool",...}`) → tolerated, `Success(...)` returned (preserves v1 behaviour).
    - **`lot_nr` field present in JSON** → `Success(payload)` with `payload.lotNr` populated (read-side round-trip).
    - **Encode payload with `lotNr != null`** → output JSON has no `lot_nr` key (write-side drop per BR-U2-OP-3 / BR-U2-CO-7).
    - **Encode-then-decode of payload with `lotNr != null`** → decoded payload has `lotNr == null` (BR-U2-CO-9 sanitisation property).
    - **Unknown JSON fields silently ignored** → `Success(payload)` with extra vendor fields not affecting equality.

- [x] **4.6 Create `OpenSpoolDecodeResultTest`.**
  - Path: `app/src/test/java/com/spoolpainter/app/domain/primitives/OpenSpoolDecodeResultTest.kt`.
  - Cases per BR-U2-T-6: `Success(payload) == Success(samePayload)`; `Malformed("a") != Malformed("b")`; `NotOpenSpool === NotOpenSpool` (singleton object); `Success` and `Malformed` are different cases.

- [x] **4.7 No dedicated `TagClassification` test file** (BR-U2-T-7) — tested implicitly by codec/encoding tests; classifier algorithm is U4's responsibility.

---

## §5 — Documentation Generation

- [x] **5.1 Create U2 code-summary doc.**
  - Path: `aidlc-docs/construction/u2-domain-primitives/code/u2-summary.md`.
  - Sections (mirroring U1's summary structure for traceability):
    - Files Created (table — path / purpose / FRs / NFRs / Stories).
    - Files Modified (`FilamentSpool.kt`).
    - Files Deleted (`OpenSpoolData.kt`).
    - Files Kept Dormant (re-confirms §3.4 list).
    - Story Coverage (S-1.2, S-2.1, S-2.2 with status ✅).
    - Public Interfaces Produced (Kotlin signatures).
    - Forward References Deferred (none — U2 is leaf).
    - Build & Test Verification (filled in by §6).
    - Exit-Criteria Checklist (per `unit-of-work.md` §3-U2).
    - Forbidden Patterns Audit.

---

## §6 — Build & Test Verification

> Use `JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-17.jdk/Contents/Home` per U1 summary's JDK note. Durable fix deferred to U10.

- [x] **6.1 `./gradlew :app:compileDebugKotlin`** — must pass.
  - Acceptance: zero new Kotlin compile errors. Pre-existing Compose deprecation warnings on retained v1 components (`MaterialSelector`, `BrandSelector`, `ColorSelector`, `TemperatureCard`, `CustomSnackbar`, `SpoolPainterLogo`) are tolerated as in U1.

- [x] **6.2 `./gradlew :app:testDebugUnitTest`** — must pass.
  - Acceptance: all U1 tests (`SettingsRepositoryTest`, 4 cases) **plus** all U2 tests pass. Total assertion count ≥ 40 (estimated from §4 case lists). Zero failures, zero ignored.

- [x] **6.3 `./gradlew :app:assembleDebug`** — must pass.
  - Acceptance: produces `app/build/outputs/apk/debug/app-debug.apk`. APK size growth from U1 baseline (~34 MB) should be negligible (<100 KB) — U2 only adds small Kotlin source files.

- [x] **6.4 Brownfield invariant** — `grep -rn "OpenSpoolData" app/src` returns no matches.

- [x] **6.5 Functional Design rule audit** — for each rule in `business-rules.md` §1-§6, mentally trace to a code line that enforces it; record mapping in `u2-summary.md` §Forbidden Patterns / Rule audit.

- [x] **6.6 Milestone install gate?** — **NO**. Per `unit-of-work.md` §2, milestone install gates are at U1 / U5 / U6 / U10 only. U2 is verified via unit tests; no on-device run is part of U2's DoD.

---

## §7 — Story Traceability

| Story | Code surface | Test surface | DoD per `unit-of-work.md` §3-U2 |
|---|---|---|---|
| S-1.2 — Canonicalise UID as lowercase hex | `CardUid.fromBytes` (§2.1) | `CardUidTest` (§4.1) | ✅ AC.unit covered |
| S-2.1 — Parse `lot_nr` | `CardUidEncoding.decode` (§2.6) | `CardUidEncodingDecodeTest` (§4.2) | ✅ AC.unit covered |
| S-2.2 — Serialise `lot_nr` | `CardUidEncoding.encode` (§2.6) + round-trip property | `CardUidEncodingEncodeTest` (§4.3) + `CardUidEncodingRoundTripTest` (§4.4) | ✅ AC.unit covered |

Forward-referenced types (no story burden in U2; consumed in later units):
- `TagClassification`, `OpenSpoolPayload`, `OpenSpoolPayloadCodec`, `OpenSpoolDecodeResult` — produced as stable interfaces; consumers in U4..U7.

---

## §8 — Out-of-Scope (parking lot)

- NFC byte-level parsing / classifier algorithm → **U4**.
- `NfcResult.Success(uid, classification)` / `NfcResult.Error(reason, cause)` → **U4** (U1 skeleton remains).
- `NfcIntent.Write(payload, expectedUid?)` / `NfcIntent.Verify` → **U4**.
- Vendor decode (`Vendor(decoded)` refinement) → **U11** (v2.1).
- Deletion of dormant v1 NFC / SpoolmanService / SpoolmanModels → **U3 / U4**.
- `MaterialDatabase` / `BrandDatabase` migration → **U8**.

---

## §9 — Approval Gate (Code Generation Part 1 — Step 7)

After this plan is approved, **Code Generation Part 2 (Generation)** executes §1 → §6 in order, marking each `[ ]` as `[x]`. No code is written before approval.

The completion message at end of Part 2 follows the standardised 2-option format from `code-generation.md` Step 14.
