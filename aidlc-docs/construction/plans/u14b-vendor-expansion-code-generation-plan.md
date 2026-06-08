# U14b — Vendor Expansion — Code Generation Plan (Part 1)

**Status**: Awaiting stage-gate approval
**Authored**: 2026-06-07
**Authored after**: U14 close-out commit `dadf6f4`; v2.1.0 shipped 2026-06-07.
**Per-unit gate**: FD / NFR-R / NFR-D / Infra-D **SKIP** — OpenRFID
(`suchmememanyskill/OpenRFID`, GPL-3.0) is the de facto FD. Code Gen
executes.
**Release window**: v2.1.x testing-track patch (versionCode 104 → 105,
versionName 2.1.0 → 2.1.1).

---

## §0 — Why this is a unit, not a polish patch

U14 added vendor tag read for **Bambu Lab** + **Snapmaker U1**.
`TagFormatParser.parseVendor(tag, bambuSaltHex, snapmakerSaltHex)` and
`MifareClassicReader.tryReadRawCounted(tag, bambuKeysA, smKeysA, smKeysB)`
hard-code those two vendors into both signatures. Adding a third vendor
needs a registry-shaped seam; adding four (qidi, anycubic, elegoo,
creality) without one would re-encode the U14 mistake at scale.

Doing the registry refactor + adding four vendors + adding a Settings UI
chip row in a single coherent unit keeps the architectural decision and
the new vendors paired in one diff. v2.1.x is a polish-track release;
shipping the registry refactor without new vendors would be churn for no
user-visible benefit.

---

## §1 — Scope

### §1.1 — Vendors added

| Vendor | Chip | Keys | Source | License path |
|---|---|---|---|---|
| QIDI | MifareClassic 1k | Default `0xFF` (no derivation) | `OpenRFID/src/tag/qidi/` | OpenRFID GPL-3.0 |
| Anycubic | MifareUltralight | None (plain page read) | `OpenRFID/src/tag/anycubic/` | OpenRFID GPL-3.0 |
| Elegoo | MifareUltralight | None (plain page read) | `OpenRFID/src/tag/elegoo/` | OpenRFID GPL-3.0 |
| Creality | MifareClassic 1k | HKDF salt + AES-ECB encryption key (both 32 B hex, user-supplied) | `OpenRFID/src/tag/creality/` | OpenRFID GPL-3.0 |

**Deferred to U16+**: spoolease (NDEF — needs verification it isn't
already covered by the standard NDEF classifier), tigertag (runtime
JSON DB download — separate concern, larger scope).

### §1.2 — Architecture: registry + processor interface

Replace the dispatcher-with-vendor-params shape with a registry of
`VendorTagProcessor` instances. Bambu + Snapmaker get ported into the
new shape so all six vendors live behind one interface.

```kotlin
// app/src/main/java/com/spoolpainter/app/hardware/nfc/vendor/VendorTagProcessor.kt
interface VendorTagProcessor {
    /** Stable identifier for routing + telemetry + chip row labels. */
    val id: VendorId

    /** Display name for the chip row (e.g., "Bambu Lab"). */
    val displayName: String

    /** True when this processor's prerequisites (keys, settings) are met. */
    fun isEnabled(settings: VendorSettings): Boolean

    /** Cheap pre-check: does the tag's chip type even match? */
    fun matchesChipType(techList: List<String>): Boolean

    /**
     * Attempt to derive auth keys for this tag's UID, if applicable.
     * Null means "no auth needed" (Ultralight) or "keys not configured".
     */
    fun deriveAuthKeys(uid: ByteArray, settings: VendorSettings): VendorAuth?

    /** Parse the raw bytes after read. */
    fun parse(uid: ByteArray, raw: ByteArray, auth: VendorAuth?): OpenSpoolPayload?
}
```

The dispatcher becomes:

```kotlin
object VendorTagRegistry {
    val processors: List<VendorTagProcessor> = listOf(
        BambuProcessor, SnapmakerProcessor,
        QidiProcessor, AnycubicProcessor, ElegooProcessor, CrealityProcessor,
    )
}

object TagFormatParser {
    fun parseVendor(tag: Tag, settings: VendorSettings): OpenSpoolPayload? {
        val candidates = VendorTagRegistry.processors
            .filter { it.isEnabled(settings) && it.matchesChipType(tag.techList.toList()) }
        // Try Ultralight processors first (no auth, cheap), then Mifare (auth scored)
        // ... see §3 for the dispatch algorithm
    }
}
```

### §1.3 — Settings + UI

- **Section rename**: `Advanced` → **`Vendor tag support`** in
  `SettingsVendorSection.kt`.
- **Chip row**: new composable `VendorTagChipRow` rendered at the top of
  the (now-expanded) section. Shows one chip per registered vendor.
  Built-in vendors (OpenSpool, Snapmaker, QIDI, Anycubic, Elegoo) are
  always lit; vendors needing user keys (Bambu, Creality) render dimmed
  (alpha 0.5) when their keys aren't configured. Tap on a dimmed chip
  scrolls to the relevant key field below.
- **Creality keys**: two new fields in the section, paralleling the
  existing Bambu key field — `Creality tag key` (HKDF salt) and
  `Creality encryption key` (AES-256-ECB key). Both 32 B hex (64
  hex chars). Both optional. Per OpenRFID's `creality/processor.py`,
  if only the HKDF salt is set, plaintext tags work and encrypted ones
  show a non-fatal "encrypted, no key" log warning.

### §1.4 — Out of scope

- Reading Bambu / Snapmaker key constants from anywhere other than
  `Settings`. The current constants stay where they are.
- Spoolease NDEF parser. Likely already covered by the OpenSpool NDEF
  path; verify in U16.
- Tigertag runtime DB. Separate effort.
- Adding **write** support for any of the four new vendors. Same
  constraint as U14: vendor tags are read-only (FR-4.7); pairing
  remains a Map-tag flow that PATCHes `extra.card_uids` without writing
  bytes to the chip.
- Bundling a Creality default key. The constants in
  `OpenRFID/src/tag/creality/constants.py` are SHA-256 hashes of the
  keys, not the keys. User must source their own (same posture as
  Bambu).
- Localising the chip row labels. Vendor names are brand strings; not
  translated.

---

## §2 — File impact

### §2.1 — New production files (12)

| Path | Purpose |
|---|---|
| `app/src/main/java/com/spoolpainter/app/hardware/nfc/vendor/VendorTagProcessor.kt` | Interface + `VendorId` enum + `VendorAuth` + `VendorSettings` value classes |
| `app/src/main/java/com/spoolpainter/app/hardware/nfc/vendor/VendorTagRegistry.kt` | Static list of processors + dispatch algorithm |
| `app/src/main/java/com/spoolpainter/app/hardware/nfc/vendor/MifareUltralightReader.kt` | New chip-family reader; reads pages 0..N as a single byte array |
| `app/src/main/java/com/spoolpainter/app/hardware/nfc/vendor/QidiProcessor.kt` | QIDI lookup-table parser |
| `app/src/main/java/com/spoolpainter/app/hardware/nfc/vendor/QidiTables.kt` | `MATERIALS` + `COLORS` constants ported from OpenRFID |
| `app/src/main/java/com/spoolpainter/app/hardware/nfc/vendor/AnycubicProcessor.kt` | Anycubic Ultralight parser |
| `app/src/main/java/com/spoolpainter/app/hardware/nfc/vendor/ElegooProcessor.kt` | Elegoo Ultralight parser |
| `app/src/main/java/com/spoolpainter/app/hardware/nfc/vendor/ElegooTables.kt` | Material subtype lookup ported from OpenRFID |
| `app/src/main/java/com/spoolpainter/app/hardware/nfc/vendor/CrealityProcessor.kt` | Creality MifareClassic parser w/ HKDF + AES decrypt |
| `app/src/main/java/com/spoolpainter/app/hardware/nfc/vendor/CrealityTables.kt` | `CREALITY_FILAMENT_CODE_TO_DATA` ported from OpenRFID |
| `app/src/main/java/com/spoolpainter/app/hardware/nfc/vendor/BambuProcessor.kt` | Adapter wrapping existing `BambuFormat.kt` functions |
| `app/src/main/java/com/spoolpainter/app/hardware/nfc/vendor/SnapmakerProcessor.kt` | Adapter wrapping existing `SnapmakerFormat.kt` functions |
| `app/src/main/java/com/spoolpainter/app/ui/screens/settings/VendorTagChipRow.kt` | Compose chip row composable |

### §2.2 — Modified production files (~7)

| Path | Reason |
|---|---|
| `app/src/main/java/com/spoolpainter/app/hardware/nfc/TagFormatParser.kt` | Switch from hard-coded Bambu+Snapmaker dispatch to registry-based dispatch |
| `app/src/main/java/com/spoolpainter/app/hardware/nfc/MifareClassicReader.kt` | Generalise `tryReadRawCounted` to accept `Map<VendorId, VendorAuth>`; preserve auth-source-ordered scoring |
| `app/src/main/java/com/spoolpainter/app/hardware/nfc/NfcRepository.kt` | Pass `VendorSettings` through to `TagFormatParser.parseVendor`; classifier (`techList` → `Vendor`) extended to flag `MifareUltralight` as well as `MifareClassic` |
| `app/src/main/java/com/spoolpainter/app/data/local/SettingsRepository.kt` | Two new fields: `crealitySalt`, `crealityEncryptionKey`; backed by DataStore |
| `app/src/main/java/com/spoolpainter/app/ui/screens/settings/SettingsViewModel.kt` | Two new setters + Settings UI state extension |
| `app/src/main/java/com/spoolpainter/app/ui/screens/settings/SettingsVendorSection.kt` | Rename "Advanced" → "Vendor tag support"; add chip row + 2 new key fields; rework expanded layout to host all three keys |
| `app/src/main/java/com/spoolpainter/app/ui/screens/settings/SettingsScreen.kt` | Wire new Settings state + setters into `SettingsVendorSection` |

### §2.3 — Test files (new + modified, ~12 files)

New test classes:

| Path | Coverage |
|---|---|
| `app/src/test/java/com/spoolpainter/app/hardware/nfc/vendor/VendorTagRegistryTest.kt` | Dispatch algorithm: chip-type filter, isEnabled gating, scoring tiebreak |
| `app/src/test/java/com/spoolpainter/app/hardware/nfc/vendor/QidiProcessorTest.kt` | Material/color lookup; rejects malformed sectors; default-key auth |
| `app/src/test/java/com/spoolpainter/app/hardware/nfc/vendor/AnycubicProcessorTest.kt` | Header magic, ASCII fields, color extraction, weight-by-length |
| `app/src/test/java/com/spoolpainter/app/hardware/nfc/vendor/ElegooProcessorTest.kt` | EE EE EE EE marker, material subtype lookup, color extraction |
| `app/src/test/java/com/spoolpainter/app/hardware/nfc/vendor/CrealityProcessorTest.kt` | HKDF derivation; encrypted vs plaintext detection; AES-ECB decrypt; weight-by-length |
| `app/src/test/java/com/spoolpainter/app/hardware/nfc/vendor/MifareUltralightReaderTest.kt` | Page concatenation, partial-read handling |
| `app/src/test/java/com/spoolpainter/app/hardware/nfc/vendor/BambuProcessorAdapterTest.kt` | Adapter parity with `BambuFormatTest` (light smoke; no algo retest) |
| `app/src/test/java/com/spoolpainter/app/hardware/nfc/vendor/SnapmakerProcessorAdapterTest.kt` | Adapter parity with `SnapmakerFormatTest` |

Modified test classes:

| Path | Reason |
|---|---|
| `app/src/test/java/com/spoolpainter/app/hardware/nfc/NfcRepositoryVendorParseTest.kt` | Updated to assert against registry dispatch; add Q/A/E/Cr cases |
| `app/src/test/java/com/spoolpainter/app/data/local/SettingsRepositoryTest.kt` (if exists) or new SettingsViewModelTest | Round-trip the two new keys through DataStore |
| `app/src/test/java/com/spoolpainter/app/ui/screens/settings/VendorTagChipRowTest.kt` | Lit/dimmed chip rendering by config state |

**Test count target**: 421 + ~30 = **~451**, with no test count regressions on the Bambu/Snapmaker paths (adapter pattern preserves their existing test coverage).

### §2.4 — Repo root

| Path | Change |
|---|---|
| `NOTICE` | Add OpenRFID attribution block (project, repo URL, GPL-3.0, scope: qidi/anycubic/elegoo/creality processors + lookup tables ported from `src/tag/*/`). Leave Snapmaker U1 attribution intact. |
| `LICENSE` | No change — stays GPL-3.0 (already covers the new bundled material). |
| `README.md` | Add Vendor tag support → list the 4 new vendors. Link to OpenRFID + cite GPL-3.0 inheritance. |

### §2.5 — Build files

No change. No new dependencies — `javax.crypto` (AES-ECB), `java.security`
(MessageDigest), and `Mac` (HMAC) are all on the platform already.

---

## §3 — N-step plan

Each step ends with a verification pointer. Where a step touches existing
behaviour, it includes a regression check.

### Step 1 — `VendorTagProcessor` interface + value classes

Create `vendor/VendorTagProcessor.kt`:

- `enum class VendorId { OpenSpool, Bambu, Snapmaker, Qidi, Anycubic, Elegoo, Creality }`.
  OpenSpool included for the chip row even though it isn't a vendor processor (it's the OpenSpool NDEF format SpoolPainter writes).
- `data class VendorSettings(val bambuSalt: String, val crealitySalt: String, val crealityEncKey: String)`.
- `data class VendorAuth(val keysA: List<ByteArray>, val keysB: List<ByteArray>?)`.
- The interface itself.

**Verify**: file compiles in isolation; no callers yet.

### Step 2 — `MifareUltralightReader`

New `vendor/MifareUltralightReader.kt`:

- `tryReadPages(tag: Tag, pageCount: Int = 36): ByteArray?`
- Uses `android.nfc.tech.MifareUltralight.get(tag)` + `connect()` +
  `readPages(0)` etc. Handles partial reads (some chips report fewer
  than 36 pages).

**Verify**: `MifareUltralightReaderTest` with a fake `MifareUltralight`
double covering: full read, short read, IOException mid-read.

### Step 3 — `QidiProcessor` + `QidiTables`

Port from `OpenRFID/src/tag/qidi/`:

- `QidiTables.MATERIALS: Map<Byte, Material>`
- `QidiTables.COLORS: Map<Byte, Int>` (24-bit RGB)
- `QidiProcessor.parse(uid, raw, auth): OpenSpoolPayload?`:
  - Read sector 1 (offset 64..112).
  - Validate `material_code != 0`, `color_code != 0`,
    `manufacturer_code != 0`, and bytes 3..47 are all zero.
  - Look up material + color → emit `OpenSpoolPayload` with brand
    "QIDI", `subtype` = first modifier (or "Basic"), color hex from
    24-bit RGB, no temps (Python source has them as 0; preserve that
    behaviour — `MainViewModel`'s blank-form prefill already handles
    `null` / "0" temps gracefully).

**Verify**: `QidiProcessorTest` — at least one good case per material
family (PLA, PETG, ABS, ASA, PA, TPU); rejection cases for bad codes
and non-zero trailing bytes.

### Step 4 — `AnycubicProcessor`

Port from `OpenRFID/src/tag/anycubic/processor.py`:

- Header check: bytes `0x10..0x14 == "{ \x00 e \x00"`. Reject otherwise.
- ASCII string fields: SKU (0x14..0x24), brand (0x28..0x38), filament
  type (0x3C..0x4C). Strip null bytes.
- Color: ARGB packed bytes at 0x50..0x54 (a, b, g, r byte order).
- Temps: little-endian uint16 at 0x60/0x62 (extruder), 0x74/0x76 (bed).
- Diameter: little-endian uint16 / 100.0 at 0x78.
- Weight by length: 330m=1000g, 247m=750g, 198m=600g, 165m=500g,
  82m=250g, else 1000g default.

Constants table is small enough to inline; no separate `AnycubicTables`.

**Verify**: `AnycubicProcessorTest` — one good case (PLA), header
mismatch, "+ suffix" handling (`PLA+` → type=PLA, modifier=+).

### Step 5 — `ElegooProcessor` + `ElegooTables`

Port from `OpenRFID/src/tag/elegoo/`:

- Read `data[0x40..0x69]` ("filament_data").
- Validate marker bytes 0x1..0x5 == `EE EE EE EE`.
- Material subtype: big-endian uint16 at 0x0C of filament_data.
- Lookup table from `OpenRFID/src/tag/elegoo/constants.py` (need to
  fetch + transcribe its content during Part 2 — see §4 Q-U14b-3).
- Color (RGBA, big-endian): bytes 0x10..0x13 of filament_data.
- Temps: big-endian uint16 at 0x14 (min) and 0x16 (max).
- Diameter / weight: big-endian uint16 at 0x1C / 0x1E.
- Bed temp: 0 from upstream (TODO marker upstream — preserve as 0;
  don't fabricate values).

**Verify**: `ElegooProcessorTest` — one good case, EE marker mismatch,
unknown subtype rejection.

### Step 6 — `CrealityProcessor` + `CrealityTables`

Port from `OpenRFID/src/tag/creality/`:

- HKDF: `key = AES-ECB-encrypt(saltKey, uid ⨉ 4)`, take first 6 bytes
  → `keys_a[1] = derived_key`, all other keys default `0xFF`.
  - **Important**: this isn't HKDF in the cryptographic sense
    (RFC 5869). It's a fixed-input AES-ECB-derive, but the OpenRFID
    code calls it `__hkdf_create_key`. Match upstream naming
    (`derivedKey`) but document the non-RFC nature in a Kotlin comment.
- Read sector 1 (offset 64..112). Check encrypted vs plaintext via
  `data[3] == 0x32` and `data[17] in {0x30, 0x23}`. If not
  plaintext: AES-256-ECB decrypt with `crealityEncKey` (or skip with
  log warning if no enc key configured).
- Parse ASCII fields: batch (0..3), date (3..8), supplier (8..12),
  material (12..17), color (17..24), length (24..28), serial (28..34).
- Material lookup → temps.
- Weight by length: 330m=1000g, 165m=500g, 80m=250g, else 1000g.

**Verify**: `CrealityProcessorTest` — plaintext-tag good case,
encrypted-tag good case (with key configured), encrypted-no-key
graceful skip, unknown material code rejection.

### Step 7 — `BambuProcessor` + `SnapmakerProcessor` adapters

Both wrap existing top-level functions in
`hardware/nfc/{Bambu,Snapmaker}Format.kt`. No algorithm changes.

```kotlin
object BambuProcessor : VendorTagProcessor {
    override val id = VendorId.Bambu
    override val displayName = "Bambu Lab"
    override fun isEnabled(s: VendorSettings) = s.bambuSalt.isNotBlank()
    override fun matchesChipType(t: List<String>) =
        t.contains(MifareClassic::class.java.name)
    override fun deriveAuthKeys(uid: ByteArray, s: VendorSettings) =
        runCatching { VendorAuth(bambuDeriveKeys(uid, s.bambuSalt), null) }.getOrNull()
    override fun parse(uid: ByteArray, raw: ByteArray, auth: VendorAuth?) =
        parseBambuTag(raw)
}
```

Snapmaker similar but threads its `Pair<List<ByteArray>, List<ByteArray>>`
into `VendorAuth(keysA, keysB)`.

**Verify**: `BambuProcessorAdapterTest` + `SnapmakerProcessorAdapterTest`
each cover a known-good payload from existing `BambuFormatTest` /
`SnapmakerFormatTest` and assert the adapter returns the same
`OpenSpoolPayload`. This is parity coverage, not algo retest — the
existing tests stay green untouched.

### Step 8 — `VendorTagRegistry` + dispatch algorithm

`vendor/VendorTagRegistry.kt`:

```kotlin
object VendorTagRegistry {
    val processors: List<VendorTagProcessor> = listOf(
        BambuProcessor, SnapmakerProcessor,
        QidiProcessor, AnycubicProcessor, ElegooProcessor, CrealityProcessor,
    )
}
```

The dispatcher in `TagFormatParser.parseVendor(tag, settings)`:

1. Filter by `isEnabled(settings) && matchesChipType(tag.techList)`.
2. **Ultralight branch**: if any candidate matches MifareUltralight,
   call `MifareUltralightReader.tryReadPages(tag)` once. Try
   processors in registry order; first non-null parse wins.
3. **MifareClassic branch**: collect `(processor, auth)` pairs for
   processors that returned non-null `deriveAuthKeys`. Run
   `MifareClassicReader.tryReadRawCountedMulti(tag, vendorAuths)`
   which counts auth-success-by-vendor. Pick the vendor with the
   highest auth count (Bambu/Snapmaker tiebreak preserved); fall
   through to the runner-up if its parse fails (matches U14
   behaviour).
4. Return null if no processor parses successfully.

**Verify**: `VendorTagRegistryTest` covers: Ultralight + Anycubic
matches; MifareClassic + Bambu wins over Snapmaker by auth count;
disabled vendor (no key) skipped; chip-type mismatch skipped.

### Step 9 — `MifareClassicReader` generalised

Replace the current 3-vendor signature with a vendor-keyed map:

```kotlin
fun tryReadRawCountedMulti(
    tag: Tag,
    vendorAuths: Map<VendorId, VendorAuth>,
): Pair<ByteArray?, Map<VendorId, Int>>
```

Auth loop tries each vendor's keys in registry order per sector;
records which vendor scored each successful auth. Returns the raw
1024-byte read (if any sector authed) plus the per-vendor auth count
for the dispatcher to score on.

Old `tryReadRawCounted(tag, bambuKeysA, smKeysA, smKeysB)` removed.
Callers updated (only `TagFormatParser`).

**Verify**: existing Bambu + Snapmaker round-trip behaviour preserved
(BambuProcessorAdapterTest / SnapmakerProcessorAdapterTest still pass).

### Step 10 — `NfcRepository` classifier extension

`NfcRepository.classify(raw)` currently classifies a tag as
`TagClassification.Vendor` only when `MifareClassic` is in the techList.
Extend to also flag `MifareUltralight`-only tags as vendor candidates
(they are read-only from our writer's perspective; vendor-data attempts
should run before falling through to "Blank").

```kotlin
fun isVendorChip(techList: List<String>): Boolean =
    techList.contains(MifareClassic::class.java.name) ||
    techList.contains(MifareUltralight::class.java.name)
```

The `Vendor.parsedHint` field stays the same — populated by
`TagFormatParser.parseVendor` when a registered processor parses the
chip.

**Regression check**: blank `Ndef`-only tags must still classify as
`Blank` (existing `NfcRepositoryClassifierTest` cases).

### Step 11 — `SettingsRepository` extensions

Two new DataStore-backed fields:

- `val crealitySalt: Flow<String>` + `setCrealitySalt(value: String)`
- `val crealityEncKey: Flow<String>` + `setCrealityEncKey(value: String)`

DataStore `Preferences.Key`s: `creality_salt`, `creality_enc_key`. Same
pattern as the existing `bambu_salt` key.

`VendorSettings` is composed from these flows + the existing
`bambuSalt` flow before being passed into `parseVendor`.

**Verify**: round-trip both keys via DataStore; clearing key sets
empty string (matches Bambu pattern).

### Step 12 — `SettingsViewModel` + `SettingsUiState`

Add `crealitySalt: String` and `crealityEncKey: String` to UI state.
Two new setters: `onCrealitySaltSaved(value: String)` /
`onCrealityEncKeySaved(value: String)`.

### Step 13 — `VendorTagChipRow` composable

```kotlin
@Composable
internal fun VendorTagChipRow(
    enabledVendors: Set<VendorId>,  // computed from VendorSettings
    modifier: Modifier = Modifier,
)
```

Layout: `FlowRow` of small surfaces (rounded corners, 1 dp outline).
Each chip's text is the vendor's `displayName`. Lit chips use
`MaterialTheme.colorScheme.primary` outline; dimmed chips use
`outline.copy(alpha = 0.5f)` and `bodyMedium` text at alpha 0.5.

Chips are visual only — no tap handling in this revision (the user
already sees the key fields below). A future iteration could add
"tap → scroll to field"; not in U14b scope.

**Verify**: `VendorTagChipRowTest` — chip count = 7 (OpenSpool +
6 vendors); Bambu chip dimmed when `bambuSalt = ""`; Creality chip
dimmed when either key is empty.

### Step 14 — `SettingsVendorSection` rework

Header text: `"Advanced"` → `"Vendor tag support"`.

Expanded body order:
1. `VendorTagChipRow` (always visible inside the expander).
2. Brief help text: `"Read tags from these vendors. Some need keys from the manufacturer or community."` (one line, bodySmall).
3. `OutlinedTextField` for Bambu Lab tag key (existing).
4. `OutlinedTextField` for Creality tag key (HKDF salt).
5. `OutlinedTextField` for Creality encryption key.
6. Per-field Save button (existing pattern from Bambu).

All three fields share the same field/save pattern. No copy ever cites
the form URL or the OpenRFID repo (legal posture: don't direct users to
non-Amazon-controlled instructions).

**Test tags**: `settings-vendor-creality-salt-field`,
`settings-vendor-creality-salt-save`, `settings-vendor-creality-enc-field`,
`settings-vendor-creality-enc-save`, `settings-vendor-chip-row`.

### Step 15 — `NOTICE` + `README.md` updates

`NOTICE`: prepend an OpenRFID attribution block (project name, repo
URL, GPL-3.0). Scope statement: "QIDI, Anycubic, Elegoo, and Creality
tag processors and lookup tables ported from
`OpenRFID/src/tag/{qidi,anycubic,elegoo,creality}/`". Snapmaker U1
attribution stays as-is.

`README.md`: extend the v2.1 "What's new" or add a new "Vendor tag
support" subsection listing all 6 supported vendors. Mention OpenRFID
upstream + GPL-3.0.

### Step 16 — Build matrix + on-device install gate

Run `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
:app:assembleDebug :app:assembleRelease :app:bundleRelease`.

Targets:
- `compileDebugKotlin` ✅
- `testDebugUnitTest` **~451 / 451** (Δ +30 vs U14's 421)
- `assembleDebug` ✅ (size delta: ~+0.2 MB for the 4 small lookup
  tables; not a U10 trigger)
- `assembleRelease` ✅ (~7.0 MB, R8 gates)
- `bundleRelease` ✅ AAB

Install gate scenarios are listed in §5; covers each new vendor
end-to-end on moto g stylus 2025 / Android 16.

### Step 17 — Close-out commit

Branch `v2`. Commit shape:

```
feat(v2.1.x): U14b — vendor expansion (qidi/anycubic/elegoo/creality + registry refactor)

Adds vendor tag read for QIDI (MifareClassic), Anycubic + Elegoo
(MifareUltralight), and Creality (MifareClassic w/ HKDF + AES). Ported
from suchmememanyskill/OpenRFID (GPL-3.0). Existing Bambu Lab +
Snapmaker U1 paths refactored behind a VendorTagProcessor registry
without behaviour change.

Settings "Advanced" section renamed to "Vendor tag support"; chip row
shows which vendors are enabled. Creality requires two user-supplied
keys (HKDF salt + AES-ECB encryption key) following the same pattern
as Bambu.

Co-authored-by: OpenRFID contributors (via NOTICE)
```

versionCode 104 → 105, versionName 2.1.0 → 2.1.1. Push to `origin/v2`
after install gate passes per `[[feedback_aidlc_unit_close_out_commit]]`.

---

## §4 — Open Q-U14b-* questions for Part 2 Q&A

Each question needs a `[Answer]:` tag before Part 2 starts.

### Q-U14b-1 — Chip ordering in the row → **A** (locked 2026-06-07)

Alphabetical by display name → Anycubic, Bambu Lab, Creality, Elegoo,
OpenSpool, QIDI, Snapmaker.

### Q-U14b-2 — OpenSpool chip in the row → **A** (locked 2026-06-07)

Yes, include OpenSpool. Always lit (no setup needed).

### Q-U14b-3 — Source pinning for Elegoo subtype lookup → **B** (locked 2026-06-07)

Pin to a commit SHA. Reproducible; future OpenRFID changes won't drift
our copy. NOTICE block cites the SHA. Part 2 captures the SHA at port
time and inlines it in NOTICE.

### Q-U14b-4 — Creality encrypted-tag-no-key behaviour → **A** (locked 2026-06-07)

Silent. Log a warning and return `parsedHint = null` (Vendor chip with
no prefill). Matches Bambu's no-key behaviour.

### Q-U14b-5 — Test coverage for adapter parity → **A** (locked 2026-06-07)

One happy-path test per adapter. Heavy testing stays in
`BambuFormatTest` / `SnapmakerFormatTest`.

### Q-U14b-6 — `MifareUltralight` page count → **A** (locked 2026-06-07)

Try 36 pages, fall back to whatever the chip reports. If the chip only
has 16 pages, the partial-read returns those 16 + we parse with what
came back.

### Q-U14b-7 — Bambu/Creality field copy strategy → **B** (locked 2026-06-07)

Short labels + a single section preface line above all three fields:
"Some vendors need keys you can paste here. Sources vary; SpoolPainter
doesn't ship them." No per-field supportingText. Three labels:
"Bambu Lab tag key", "Creality tag key", "Creality encryption key".

### Q-U14b-8 — Vendor display strings → **B** (locked 2026-06-07)

Drop "U1" from Snapmaker → just "Snapmaker". Matches OpenRFID upstream
naming. Update v2.1's existing "Snapmaker U1" chip copy in
`VendorTagHint` for consistency (same edit; chip row + hint share the
display string via `VendorTagProcessor.displayName`).

---

## §5 — Traceability matrix

| Decision / source | Plan section |
|---|---|
| OpenRFID GPL-3.0 license posture | §1.1, §2.4 (NOTICE), §3 Step 15 |
| Registry refactor over bolt-on | §1.2, §3 Steps 1, 8, 9 |
| Q-U14b-1..8 questions | §4 (each Q tags its target step) |
| qidi MifareClassic + default keys | §3 Step 3 |
| anycubic Ultralight + plain read | §3 Step 4 |
| elegoo Ultralight + lookup table | §3 Step 5 |
| creality MifareClassic + HKDF + AES | §3 Step 6 |
| Bambu / Snapmaker port (no algo change) | §3 Step 7 |
| Settings rename "Advanced" → "Vendor tag support" | §3 Step 14 |
| Chip row inside expanded section | §1.3, §3 Steps 13, 14 |
| Mix posture: bundle qidi/anycubic/elegoo, user-supplied creality + bambu | §1.3, §3 Step 11 |
| User-supplied keys: Bambu + Creality only | §3 Step 11 |
| No bundled Bambu / Creality keys | §1.4 |
| FD / NFR / Infra all SKIP | header |
| v2.1.x release window | header, §3 Step 17 |
| Test target ~451 (Δ +30) | §2.3, §3 Step 16 |

### §5.1 — Install gate scenarios

| ID | Scenario | Expected |
|---|---|---|
| §5.1.1 | OpenSpool tag (regression) | Prefill (full payload) |
| §5.1.2 | Bambu Lab tag with key set (regression) | Prefill (PLA, color, temps) |
| §5.1.3 | Bambu Lab tag with key empty | Vendor chip, no prefill |
| §5.1.4 | Snapmaker U1 tag (regression) | Prefill (full payload) |
| §5.1.5 | QIDI tag (PLA) | Prefill: type=PLA, brand=QIDI, color from table |
| §5.1.6 | QIDI tag (PETG) | Prefill: type=PETG, modifier=Basic |
| §5.1.7 | Anycubic tag (PLA) | Prefill: type=PLA, brand from tag, color from ARGB |
| §5.1.8 | Elegoo tag (any) | Prefill: type from subtype lookup |
| §5.1.9 | Creality plaintext tag with HKDF key set | Prefill (no enc key needed) |
| §5.1.10 | Creality encrypted tag with both keys set | Prefill |
| §5.1.11 | Creality encrypted tag with only HKDF key | Vendor chip, no prefill, log warning |
| §5.1.12 | Blank tag (regression) | Blank classification |
| §5.1.13 | Vendor tag without any keys configured (e.g., random MifareClassic) | Vendor chip, no prefill |
| §5.1.14 | Settings "Vendor tag support" section | Renamed header; chip row visible; 3 key fields render correctly |
| §5.1.15 | Chip row when no keys set | Lit: OpenSpool, Snapmaker, QIDI, Anycubic, Elegoo. Dimmed: Bambu, Creality |
| §5.1.16 | Chip row after entering Bambu key | Bambu chip lights up |

---

## §6 — Resume options

If Part 2 is interrupted between Part 1 approval and Step 17:

### §6.1 — Resume mid-implementation

Re-read this plan + the current `git status`. Pick up at the next
unchecked step. Steps 1–8 are independent of each other (registry
shape locked in Step 1) — can be parallelised. Steps 9–14 depend on
8. Steps 15–17 sequential.

### §6.2 — Resume after install-gate failure

If a vendor fails on real hardware (e.g., QIDI tag rejected because
the test fixture didn't match real-world layout), the fix lives in
the relevant `*Processor.kt` + `*ProcessorTest.kt` only. Other
vendors' code paths and the registry stay green.

### §6.3 — Resume on Q-U14b-* answer revision

If a Q-U14b-* answer changes mid-Part 2 (e.g., user wants chip
ordering reversed), the revision lives in a single composable
(`VendorTagChipRow.kt`) + maybe registry order — small blast radius.

---

## §7 — Revision history

| Date | Author | Note |
|---|---|---|
| 2026-06-07 | Claude (this session) | Initial Part 1 plan authored. |
