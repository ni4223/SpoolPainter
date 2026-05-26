# U6a — Domain Entities

**Unit**: U6a — Create-and-Pair Flow (with folded U2-Δ / U3-Δ / U5-Δ amendments)
**Approved**: Q-U6a-1..15 = A (FD Part 1, 2026-05-25)
**Related**: [requirements-delta-extra-fields.md](../../../inception/requirements/requirements-delta-extra-fields.md), [u6a-create-and-pair-flow-functional-design-plan.md](../../plans/u6a-create-and-pair-flow-functional-design-plan.md)

---

## 1. Use-case input/output types

### 1.1 `CreateAndPairResult` (sealed)

```kotlin
sealed interface CreateAndPairResult {
    sealed interface Success : CreateAndPairResult {
        val spoolId: Int
        val uid: CardUid

        data class WrittenAndPaired(
            override val spoolId: Int,
            override val uid: CardUid,
            val isNewSpool: Boolean,
        ) : Success
    }

    data class VerifyFailed(
        val spoolId: Int,
        val uid: CardUid,
        val isNewSpool: Boolean,
        val cause: String,
    ) : CreateAndPairResult

    data class SpoolmanFailed(
        val uid: CardUid,
        val outcome: SpoolmanOutcome<*>,
    ) : CreateAndPairResult

    data class NfcFailed(
        val uid: CardUid?,
        val reason: String,
    ) : CreateAndPairResult

    data class Cancelled(val reason: String) : CreateAndPairResult
}
```

**Notes** (per Q-U6a-3, Q-U6a-4):
- `Success.PairedNoWrite` is **not** defined in U6a — deferred to U7 (vendor UID-only path).
- `MoveOnBindRequired` is **not** part of the result hierarchy — U6a's no-op default just proceeds without the move branch (Q-U6a-4=A).

### 1.2 `NewFilamentRequest`

The data carrier U6a's new-spool path hands to `SpoolmanRepository.createSpoolForNewFilament(...)`. Sources its values from `MainUiState.form` plus the freshly-read UID.

```kotlin
data class NewFilamentRequest(
    val name: String,
    val vendorName: String,
    val material: String,
    val colorHex: String,        // 6-char uppercase hex, no '#'
    val diameter: Double,        // mm
    val weight: Double,          // grams
    val density: Double,         // g/cm³ (looked up from material w/ PLA=1.24 default per spec §"Create Filament")
    val extruderMin: Int,
    val extruderMax: Int,
    val bedMin: Int,
    val bedMax: Int,
    val variant: String?,        // FR-2-EXT.2 — optional; null/blank → omitted from filament POST
    val cardUid: CardUid,        // FR-2-EXT.1 — seeds spool's extra.card_uids
)
```

**Companion**:
```kotlin
companion object {
    fun fromForm(form: FormState, uid: CardUid): NewFilamentRequest = ...
}
```
Resolves `density` via `MaterialDatabase.densityFor(material)` (defaults 1.24 per spec table). `variant` is the form's variant verbatim (trimmed; blank → null).

## 2. Wire DTO additions (U3-Δ-1)

### 2.1 `SpoolmanSpool` — `extra` map

```kotlin
data class SpoolmanSpool(
    // ... existing fields (id, filament, remaining_weight, archived, registered, last_used, lot_nr, ...)
    val extra: Map<String, String>? = null,
)
```

Backwards-compatible default of `null`; null-safe everywhere. `extra["card_uids"]` is the only key SpoolPainter writes, but the DTO is general.

### 2.2 `SpoolmanFilament` — `extra` map

```kotlin
data class SpoolmanFilament(
    // ... existing fields (id, name, vendor, material, color_hex, diameter, weight, ...)
    val extra: Map<String, String>? = null,
)
```

`extra["variant"]` is the only key SpoolPainter writes.

## 3. Domain primitives — U2-Δ

### 3.1 `CardUid` — casing fix (U2-Δ-3)

```kotlin
@JvmInline
value class CardUid(val hex: String) {
    override fun toString(): String = hex

    companion object {
        fun fromBytes(bytes: ByteArray): CardUid =
            CardUid(bytes.joinToString("") { "%02X".format(it) })  // was "%02x" — fixed per FR-2-EXT.8

        fun normaliseHex(raw: String): String {
            val upper = raw.uppercase()
            require(upper.matches(Regex("^[0-9A-F]+\$"))) { "Not valid hex: $raw" }
            return upper
        }
    }
}
```

### 3.2 `ExtraCardUidsCodec` (U2-Δ-2) — replaces legacy `CardUidEncoding`

```kotlin
object ExtraCardUidsCodec {
    /** Encode a list of UIDs as Spoolman's wire format for `extra.card_uids`.
     *  Output for two UIDs: the literal 18-byte JSON-string `"\"AABBCCDD,11223344\""`. */
    fun encode(uids: List<CardUid>): String {
        val joined = uids.joinToString(",") { it.hex }
        return Gson().toJson(joined)  // wraps in quotes + escapes; never throws on String input
    }

    /** Defensive decode — accepts both the JSON-wrapped wire form and a raw comma-separated string.
     *  Per Q-U6a-1=A: invalid hex entries are silently skipped (logged), not thrown. */
    fun decode(value: String): List<CardUid> {
        if (value.isEmpty()) return emptyList()
        val stripped = value
            .removePrefix("\"")
            .removeSuffix("\"")
            .takeIf { it.isNotEmpty() }
            ?: return emptyList()
        return stripped.split(",")
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { runCatching { CardUid(CardUid.normaliseHex(it)) }.getOrNull() }
            .toList()
    }
}
```

**Deletes** (U2-Δ-1): `CardUidEncoding.decode/encode/Decoded`. Legacy `lot_nr:card_uid:` packing is gone.

## 4. UI state slice — `FormState` (already shipped in U5; no shape change)

```kotlin
data class FormState(
    val cardUid: CardUid? = null,
    val material: Material? = null,
    val brand: Brand? = null,
    val colorHex: String? = null,
    val variant: String? = null,           // FR-2-EXT.2 — already present (MainUiState.kt:27)
    val tempRanges: TempRanges = TempRanges(),
    val selectedSpoolId: Int? = null,
    val rawWriteMode: Boolean = false,
)
```

**No structural change.** U6a transitions `variant` from "read-only display, sourced from OpenSpool subtype" (U5's behaviour) to "user-editable + persisted to Spoolman" (U6a's job).

## 5. UI state slice — `ActiveFlow` (extension)

```kotlin
sealed interface ActiveFlow {
    data object Idle : ActiveFlow
    data object ReadingForPair : ActiveFlow      // U5
    data object WritingForPair : ActiveFlow      // U6a (NEW)
}
```

U6b will add `Repairing` / `TwoTag`; out of scope for U6a.

## 6. Cross-unit interface — `MoveOnBindUseCase` (U6a defines, U6b implements)

```kotlin
interface MoveOnBindUseCase {
    /** No-op default returns Proceed; U6b's impl detects existing UID owners and may return RequireConfirmation. */
    suspend operator fun invoke(uid: CardUid, targetSpoolId: Int): Outcome

    sealed interface Outcome {
        data object Proceed : Outcome
        // U6b adds: RequireConfirmation, ConfirmedAndMoved, Declined
    }

    /** U6a no-op binding; replaced by U6b's full impl via Hilt. */
    class NoOp @Inject constructor() : MoveOnBindUseCase {
        override suspend fun invoke(uid: CardUid, targetSpoolId: Int): Outcome = Outcome.Proceed
    }
}
```

Hilt binding in `domain/usecases/UseCaseModule.kt` provides `MoveOnBindUseCase.NoOp` as the default; U6b replaces it via a different `@Provides`.

## 7. Type relationship diagram

```text
MainUiState
├── FormState
│   ├── cardUid: CardUid?
│   ├── variant: String?           ← editable in U6a; persisted via NewFilamentRequest
│   └── selectedSpoolId: Int?      ← drives existing-spool vs new-spool branching
├── SpoolmanState
│   └── spools: List<SpoolmanSpool>
│       └── extra: Map<String, String>?      ← U3-Δ-1
│           └── ["card_uids"] → ExtraCardUidsCodec.encode(...)
└── ActiveFlow ∈ { Idle, ReadingForPair, WritingForPair }

CreateAndPairUseCase
├── input: implicit (reads MainUiState via state)
└── output: CreateAndPairResult
              ├── Success.WrittenAndPaired { spoolId, uid, isNewSpool }
              ├── VerifyFailed { spoolId, uid, isNewSpool, cause }
              ├── SpoolmanFailed { uid, outcome }
              ├── NfcFailed { uid?, reason }
              └── Cancelled { reason }

NewFilamentRequest
├── filament fields → POST /filament  (extra.variant if non-blank)
└── cardUid → seeds POST /spool's extra.card_uids = encode(listOf(cardUid))

MoveOnBindUseCase (interface only in U6a)
└── invoke(uid, targetSpoolId): Outcome.Proceed   ← no-op default
```

## 8. File checklist — domain entities (Code-Gen Part 1 preview)

| File | Action | Owner |
|---|---|---|
| `domain/primitives/CardUid.kt` | modify (casing fix + `normaliseHex`) | U2-Δ |
| `domain/primitives/ExtraCardUidsCodec.kt` | **create** | U2-Δ |
| `domain/primitives/CardUidEncoding.kt` | modify (delete legacy methods) or delete entirely | U2-Δ |
| `domain/usecases/CreateAndPairResult.kt` | **create** | U6a |
| `domain/usecases/CreateAndPairUseCase.kt` | **create** | U6a |
| `domain/usecases/MoveOnBindUseCase.kt` | **create** (interface + NoOp) | U6a |
| `domain/usecases/NewFilamentRequest.kt` | modify (add `variant` + `cardUid`; existing v1 may have similar shape — to verify in code-gen) | U6a |
| `data/remote/spoolman/SpoolmanModels.kt` | modify (`extra` on `SpoolmanSpool` + `SpoolmanFilament`) | U3-Δ |
| `ui/screens/main/MainUiState.kt` | modify (`ActiveFlow.WritingForPair`) | U6a |

Tests: see `business-rules.md` §§T-* for the per-rule test obligations.
