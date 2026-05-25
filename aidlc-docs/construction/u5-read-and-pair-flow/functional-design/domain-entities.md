# U5 — Domain Entities

**Stage**: CONSTRUCTION → Functional Design Part 2 (artefact)
**Source plan**: `aidlc-docs/construction/plans/u5-read-and-pair-flow-functional-design-plan.md` (approved 2026-05-25)
**Companion artefacts**:
- `business-rules.md` — branch / prefill / cancellation rules
- `business-logic-model.md` — use-case + ViewModel state machine
- `frontend-components.md` — `MainScreen` Compose surface for U5

This artefact is technology-agnostic per AIDLC rule details for Functional Design. Hilt / Compose / Retrofit specifics are implementation concerns and live in the Code Generation plan.

---

## 1. Entity inventory

U5 introduces / finalises the following domain concepts. Types pre-existing from earlier units are listed for reference but their internals are not redefined here.

| Entity | Owner unit | Status in U5 |
|---|---|---|
| `ReadAndPairResult` | U5 | **NEW** — sealed type returned by `ReadAndPairUseCase.invoke()`. |
| `MainUiState` | U1 (skeleton) → U5 | **FINALISED** for U5's slice. Later units extend `ActiveFlow`. |
| `FormState` | U5 | **NEW**. |
| `TempRanges` | U5 | **NEW** (domain model). |
| `SpoolmanState` | U5 | **NEW** (read-only projection of `SpoolmanRepository`). |
| `NfcState` | U1 (skeleton) → U5 | **FINALISED** as `typealias NfcState = NfcResult`. |
| `BannerState` | U5 (placeholder) → U9 (full) | **PLACEHOLDER** in U5 (always `Hidden`). |
| `ActiveFlow` | U5 (baseline) → U6a/U6b/U7 (extend) | **NEW** with `Idle | ReadingForPair`. |
| `AmbiguityState` | U5 | **NEW**. |
| `Brand` | U5 | **NEW** value class (interim; U8 may extend). |
| `Material` | v1 (`domain/models/Material.kt`) | **REUSED** unchanged. |
| `CardUid` | U2 | **REUSED**. |
| `OpenSpoolPayload` | U2 | **REUSED**. |
| `TagClassification` | U2 | **REUSED**. |
| `NfcResult`, `NfcIntent` | U4 | **REUSED**. |
| `SpoolmanSpool`, `SpoolmanFilament`, `SpoolmanVendor` | U3 | **REUSED**. |
| `SpoolmanOutcome<T>` | U3 | **REUSED**. |
| `UiEffect` | U1 | **REUSED**; `ShowSnackbar(message)` extended in usage (no new variants in U5). |

---

## 2. New entities — full shape

### 2.1 `ReadAndPairResult`

Sealed hierarchy with named-non-happy-path variants (Q7=B in stories). Single value type returned by `ReadAndPairUseCase.invoke()`.

```
ReadAndPairResult
├── Success
│   ├── PrefillFromSpoolman(uid, spool, classification)
│   ├── PrefillFromTag(uid, payload)
│   └── BlankForm(uid, classification)
├── Ambiguous(uid, matches, classification)
├── SpoolmanFailed(uid, classification, outcome)
├── NfcFailed(reason)
└── Cancelled(reason)
```

| Variant | Fields | Trigger |
|---|---|---|
| `Success.PrefillFromSpoolman` | `uid: CardUid`, `spool: SpoolmanSpool`, `classification: TagClassification` | Spoolman returned exactly 1 match (regardless of classification — collision rule §2.1.6 in plan). |
| `Success.PrefillFromTag` | `uid: CardUid`, `payload: OpenSpoolPayload` | 0 Spoolman matches AND classification = `OpenSpool(payload)`. |
| `Success.BlankForm` | `uid: CardUid`, `classification: TagClassification` | 0 Spoolman matches AND classification ∈ {`Blank`, `Vendor`}. |
| `Ambiguous` | `uid: CardUid`, `matches: List<SpoolmanSpool>` (size ≥ 2), `classification: TagClassification` | Spoolman returned > 1 matches. Form is **not** prefilled. |
| `SpoolmanFailed` | `uid: CardUid`, `classification: TagClassification`, `outcome: SpoolmanOutcome<*>` (`HttpError | NetworkError | ParseError`, **excluding** URL-not-configured per Q-U5-3=A) | Spoolman returned a non-success outcome. |
| `NfcFailed` | `reason: String` | NFC layer produced `NfcResult.Error` before any Spoolman call (e.g., "NFC not available", "zero-length UID — non-NFC-A tag?"). |
| `Cancelled` | `reason: String` | VM cancelled the use-case (re-tap Read while armed; `viewModelScope` cancelled). |

**Invariants**:
- Exactly one variant is returned per `invoke()` call.
- `uid` is always a non-empty `CardUid` whenever a variant carrying `uid` is returned. (Empty UID is mapped to `NfcFailed("zero-length UID — non-NFC-A tag?")` per BR-U5-NFC-2.)
- `matches.size >= 2` for `Ambiguous`.

### 2.2 `MainUiState` (U5 slice)

```
MainUiState
├── form: FormState
├── spoolman: SpoolmanState
├── nfc: NfcState                    // = NfcResult
├── banner: BannerState              // U5 always Hidden
├── activeFlow: ActiveFlow           // U5: Idle | ReadingForPair
└── ambiguity: AmbiguityState?       // null unless Ambiguous result fired
```

`MainUiState` is the single VM-owned `StateFlow` value (Q-DP1=A). All five+ slices are pure data classes; no reactive composition inside the state itself.

### 2.3 `FormState`

```
FormState
├── cardUid: CardUid?           // sticky once read; survives onSpoolSelected(null) (Q-U5-7=A)
├── material: Material?         // v1 type; resolved via MaterialDatabase in U5 (Q-U5-9=A)
├── brand: Brand?               // U5 interim type (Q-U5-6=A)
├── colorHex: String?           // canonical 6-char uppercase hex without '#'
├── variant: String?            // null when payload.subtype ∈ {"Basic", ""}
├── tempRanges: TempRanges
├── selectedSpoolId: Int?       // mirrors SpoolmanState.selectedSpoolId
└── rawWriteMode: Boolean       // U5 ships always-false; U7 wires
```

**Default-empty value**: `FormState()` — all fields null/default, `tempRanges = TempRanges()`, `rawWriteMode = false`. Used as the initial VM state and by `BlankForm` rule.

### 2.4 `TempRanges`

```
TempRanges
├── extruderMin: Int?
├── extruderMax: Int?
├── bedMin: Int?
└── bedMax: Int?
```

All fields nullable independently. `TempRanges()` (all nulls) is a valid empty state. No invariant relating min ≤ max at the type level — validation lives in U6a's write-side rules.

### 2.5 `SpoolmanState`

```
SpoolmanState
├── spools: List<SpoolmanSpool>   // mirror of SpoolmanRepository.spools
├── selectedSpoolId: Int?         // mirrors FormState.selectedSpoolId for dropdown UX
└── urlConfigured: Boolean        // derived from SettingsRepository.settings.url.isNotBlank()
```

`SpoolmanState` is a **derived** projection — VM combines the two source flows (`spoolman.spools` + `settings.settings`) and emits to `MainUiState.spoolman`.

### 2.6 `BannerState`

```
BannerState (sealed)
├── Hidden
└── Offline(lastError: String?)
```

In U5, the VM **always** emits `Hidden`. The slot exists so U6a / U7 don't need to add it later. U9 wires the actual derivation.

### 2.7 `ActiveFlow`

```
ActiveFlow (sealed)
├── Idle
└── ReadingForPair
```

U5 ships only these two members. U6a/U6b/U7 add `Writing | Verifying | Repairing | TwoTag | VendorOptIn | RawWriting`. The sealed-interface boundary means U5's exhaustive-when checks must include a TODO branch for the upcoming variants — handled by routing through a sealed `else` that is a no-op in U5.

### 2.8 `AmbiguityState`

```
AmbiguityState
├── uid: CardUid
├── matches: List<SpoolmanSpool>     // size >= 2
└── classification: TagClassification
```

Held in `MainUiState.ambiguity` as nullable. Cleared on:
- Next successful read (any non-`Ambiguous` `ReadAndPairResult`).
- `onSpoolSelected(non-null)` (manual resolution path).
- `onSpoolSelected(null)` (clear).

### 2.9 `Brand` (U5 interim)

```
Brand
└── name: String     // case-preserving; equality is name-equal
```

A `data class` (not a `value class`) so U8 can add fields without breaking call sites. Equality + hashing are auto-generated. No methods on the type itself — string equality is the contract.

---

## 3. Form prefill mappings (entity → entity)

These are **functional mappings**, not behaviours. The behavioural rules (when to apply each, how to handle errors) live in `business-rules.md`.

### 3.1 `SpoolmanSpool` → `FormState`

| Target field | Source expression |
|---|---|
| `cardUid` | (preserved from caller's existing `FormState.cardUid`) |
| `material` | `MaterialDatabase.getMaterial(spool.filament.material ?: "Unknown")`. Missing → `null`; downstream form renders the raw string. |
| `brand` | `Brand(spool.filament.vendor?.name ?: "Unknown")` |
| `colorHex` | `spool.filament.color_hex?.removePrefix("#")?.let { if (it.length > 6) it.takeLast(6) else it }?.uppercase()?.takeIf { it.isNotEmpty() }` |
| `variant` | `null` (Spoolman has no variant column). |
| `tempRanges.extruderMin` / `extruderMax` | If `materialData != null` AND `spool.filament.settings_extruder_temp` ∈ `[material.defaultMinTemp, material.defaultMaxTemp]`: use material defaults. Else: `(extruderTemp, extruderTemp + 20)`. (Verbatim port of `FilamentSpool.fromSpoolman`.) |
| `tempRanges.bedMin` / `bedMax` | Same rule with `settings_bed_temp` and material's bed defaults. |
| `selectedSpoolId` | `spool.id` |
| `rawWriteMode` | (preserved) |

### 3.2 `OpenSpoolPayload` → `FormState`

| Target field | Source expression |
|---|---|
| `cardUid` | (preserved or set by caller from the just-read tag) |
| `material` | `MaterialDatabase.getMaterial(payload.type)` ?: synthesise `Material(name = payload.type, defaultMinTemp = parsed-or-fallback, defaultMaxTemp = …, defaultBedMinTemp = …, defaultBedMaxTemp = …)` from payload string fields so the form renders. |
| `brand` | `Brand(payload.brand)` |
| `colorHex` | `payload.colorHex?.removePrefix("#")?.takeLast(6)?.uppercase()?.takeIf { it.isNotEmpty() }` |
| `variant` | `payload.subtype.takeUnless { it == "Basic" || it.isBlank() }` |
| `tempRanges.extruderMin` | `payload.minTemp.toIntOrNull()` ?: material default |
| `tempRanges.extruderMax` | `payload.maxTemp.toIntOrNull()` ?: material default |
| `tempRanges.bedMin` | `payload.bedMinTemp?.toIntOrNull()` ?: material default |
| `tempRanges.bedMax` | `payload.bedMaxTemp?.toIntOrNull()` ?: material default |
| `selectedSpoolId` | `null` |
| `rawWriteMode` | (preserved) |

### 3.3 `BlankForm` projection

```
FormState.copy(
    cardUid = uid,                      // overwritten with the just-read UID
    material = null,
    brand = null,
    colorHex = null,
    variant = null,
    tempRanges = TempRanges(),
    selectedSpoolId = null,
    rawWriteMode = (preserved),
)
```

### 3.4 `onSpoolSelected(null)` clear

```
FormState.copy(
    cardUid = (preserved),               // Q-U5-7=A — UID survives clear
    material = null,
    brand = null,
    colorHex = null,
    variant = null,
    tempRanges = TempRanges(),
    selectedSpoolId = null,
    rawWriteMode = (preserved),
)
```

---

## 4. Cross-unit type contracts

Public types declared by U5 that downstream units depend on:

| Type | Consumer | Stability promise |
|---|---|---|
| `ReadAndPairResult` (sealed) | only `MainViewModel` (single consumer per Q-D1=C, but interface-typed boundary still applies). | Variants may be **added** by later units (e.g., `Cancelled` flavours); no removal/rename without follow-up unit's approval. |
| `MainUiState`, `FormState`, `TempRanges`, `SpoolmanState`, `NfcState`, `BannerState`, `ActiveFlow`, `AmbiguityState` | U6a / U6b / U7 / U8 / U9 (read-only projections + extension points). | `data class` fields may be added with default values; no removal in v2.0 without rename. `ActiveFlow` extends by adding new variants (sealed-interface). |
| `Brand` | U8 extends (may add `id`, `customSource`). | Existing `name: String` field is permanent. |

Types reused from earlier units (`CardUid`, `TagClassification`, `OpenSpoolPayload`, `SpoolmanSpool`, etc.) are unchanged.

---

## 5. Out of scope — types intentionally NOT introduced

- `Spool` alias for `SpoolmanSpool` — `component-methods.md` §6/§7 sometimes uses the short form; U5 uses `SpoolmanSpool` directly. Recorded as documentation drift, fixed in U10 release polish.
- Form input/event types (e.g., `MaterialEditEvent`, `TempPanelEdit`) — U5's form is read-only; input events land in U6a.
- `WriteRequest`, `CreateAndPairRequest` types — U6a.
- `MoveOnBindResult`, `TwoTagResult` — U6b.
- `RawWriteResult`, `VendorUidOnlyResult` — U7.
