# U5 — Frontend Components

**Stage**: CONSTRUCTION → Functional Design Part 2 (artefact)
**Source plan**: `aidlc-docs/construction/plans/u5-read-and-pair-flow-functional-design-plan.md` (approved 2026-05-25)
**Companion artefacts**:
- `domain-entities.md` — types
- `business-rules.md` — rules (BR-U5-*)
- `business-logic-model.md` — sequence + state machine

---

## 1. Compose component hierarchy

```
MainScreen(viewModel: MainViewModel, onNavigateToSettings: () -> Unit)
└── Scaffold
    ├── topBar = MainTopBar(onSettingsClick = vm::onSettingsTapped)
    ├── snackbarHost = SnackbarHost(snackbarHostState)
    └── content = MainContent(state, callbacks)
        ├── BannerSlot(state.banner)                        // U5 always Hidden — composable still present
        ├── ReadingHint(state.activeFlow, state.nfc)        // "Tap a tag to read" while armed
        ├── UidRow(state.form.cardUid)                      // shows hex when present
        ├── SpoolmanDropdown(
        │       spools = state.spoolman.spools,
        │       selectedId = state.spoolman.selectedSpoolId,
        │       onSelect = vm::onSpoolSelected,
        │       enabled = state.spoolman.urlConfigured)
        ├── AmbiguityBlock(state.ambiguity)                 // null-safe; null → no-op
        └── FormPreview(state.form)                         // read-only — inputs land in U6a
            ├── PreviewRow("Material", state.form.material?.name)
            ├── PreviewRow("Brand",    state.form.brand?.name)
            ├── ColorPreviewRow(state.form.colorHex)
            ├── PreviewRow("Variant",  state.form.variant)
            └── TempPreviewRows(state.form.tempRanges)
```

A bottom-aligned **Read** FAB ("Read tag") completes the surface (Q-U5-11=A — minimal-but-real):

```
└── floatingActionButton = ReadFab(
        enabled = state.activeFlow != ActiveFlow.ReadingForPair,
        onClick = vm::onReadTapped)
```

(If a FAB doesn't fit the v2 theme, a primary-styled button row at the bottom of `MainContent` is acceptable. Final placement is a Code Generation concern; the **call-site contract** is what matters.)

---

## 2. Component contracts (props + state)

### 2.1 `MainScreen`

| Param | Type | Purpose |
|---|---|---|
| `viewModel` | `MainViewModel` (Hilt) | source of `state` + `effects` + intent callbacks |
| `onNavigateToSettings` | `() -> Unit` | hoisted nav callback. U5 ships a no-op default (or a Toast); U9 wires the real route |

State sources:
- `state by viewModel.state.collectAsStateWithLifecycle()` — single `MainUiState`.
- `effects` collected via `LaunchedEffect(Unit)` to deliver `ShowSnackbar` / `Navigate`.

### 2.2 `MainTopBar`

| Param | Type |
|---|---|
| `onSettingsClick` | `() -> Unit` |

Compose-only; no state. Renders: app title (`"SpoolPainter"` for now — branding deferred to U9 polish), settings icon button.

### 2.3 `BannerSlot`

| Param | Type |
|---|---|
| `banner` | `BannerState` |

In U5: when `banner is BannerState.Hidden`, renders nothing (zero-height). Slot is present so U6a / U7 / U9 can extend.

### 2.4 `ReadingHint`

| Param | Type |
|---|---|
| `activeFlow` | `ActiveFlow` |
| `nfc` | `NfcResult` |

Renders "Tap a tag to read…" caption iff `activeFlow == ReadingForPair && (nfc is Idle || nfc is Reading)`. Otherwise renders nothing.

### 2.5 `UidRow`

| Param | Type |
|---|---|
| `cardUid` | `CardUid?` |

Renders `"UID: ${uid.hex.uppercase()}"` when non-null. Otherwise renders nothing.

### 2.6 `SpoolmanDropdown` (real impl — U1 had only a stub)

| Param | Type | Purpose |
|---|---|---|
| `spools` | `List<SpoolmanSpool>` | options |
| `selectedId` | `Int?` | currently selected (mirrors `FormState.selectedSpoolId`) |
| `onSelect` | `(SpoolmanSpool?) -> Unit` | non-null on pick; null on clear |
| `enabled` | `Boolean` | false when `urlConfigured == false` |

Behaviour:
- Renders an `ExposedDropdownMenuBox` (Material 3) with the selected spool's display name (filament + vendor) when expanded; placeholder text when none selected.
- A "Clear" item at the top of the menu invokes `onSelect(null)`.
- When `enabled = false`, displays "Configure Spoolman URL in Settings" as helper text and is non-interactive.

Internal state:
- `var expanded: Boolean by remember { mutableStateOf(false) }` — UI-only.

Display name function:
```
fun spoolDisplayName(spool: SpoolmanSpool): String =
    listOfNotNull(
        spool.filament.name,
        spool.filament.vendor?.name?.takeIf { it.isNotBlank() },
        "#${spool.id ?: "?"}",
    ).joinToString(" · ")
```

### 2.7 `AmbiguityBlock`

| Param | Type |
|---|---|
| `state` | `AmbiguityState?` |

When non-null:
- Card with error-tinted surface.
- Header: `"Multiple spools claim UID ${state.uid.hex.uppercase()}"`.
- Bullet list of matches: `"#{spool.id} · {filament.name} · {vendor.name}"`.
- Helper text: `"Pick one from the dropdown to resolve, or fix the data in Spoolman."`.

When null: renders nothing (zero-height).

### 2.8 `FormPreview` (U5 read-only)

| Param | Type |
|---|---|
| `form` | `FormState` |

Renders a `Column` of label/value rows:
- "Material" → `form.material?.name ?: "—"`.
- "Brand"    → `form.brand?.name ?: "—"`.
- Colour     → `ColorPreviewRow(form.colorHex)` — small swatch + hex text or "—".
- "Variant"  → `form.variant ?: "—"`.
- Temps      → `TempPreviewRows(form.tempRanges)`:
  - `Extruder` → `"${tempRanges.extruderMin ?: "—"}–${tempRanges.extruderMax ?: "—"} °C"`.
  - `Bed`      → same with `bedMin` / `bedMax`.

All rows are non-editable. Inputs land in U6a.

### 2.9 `ReadFab` (or button)

| Param | Type |
|---|---|
| `enabled` | `Boolean` |
| `onClick` | `() -> Unit` |

Disabled while `activeFlow == ReadingForPair` (BR-U5-VM-2 still allows re-tap to disarm + re-arm — but the disable provides immediate feedback that something is in flight; the actual cancellation path is reserved for explicit user action via re-tap, which is enabled by allowing the FAB to remain tappable. Pick one path — the recommendation is **keep enabled** so re-tap works).

**Resolution**: `enabled = true` always (so re-tap is possible per BR-U5-VM-2). Visual indication of in-flight state comes from `ReadingHint` + a small progress indicator inside the FAB when `activeFlow == ReadingForPair`.

---

## 3. User interaction flows

### 3.1 Tag-first flow (S-3.1, S-3.2, S-3.4, S-3.5, S-3.6)

```
1. User taps tag (no Read armed)
   → MainActivity.onNewIntent → NfcRepository.onTagDiscovered
   → state -> Reading -> Success(uid, classification); lastSeenTag populated
2. User taps Read FAB
   → onReadTapped()
   → activeFlow = ReadingForPair
   → use-case: consumeLastSeen(Read) returns Success
   → use-case: findSpoolsByCardUid(uid)
   → maps to one of: PrefillFromSpoolman | PrefillFromTag | BlankForm | Ambiguous | SpoolmanFailed
3. VM applies result; activeFlow = Idle; form populated/cleared; snackbar if failure.
```

### 3.2 Button-first flow

```
1. User taps Read FAB (no buffered tap)
   → onReadTapped()
   → activeFlow = ReadingForPair
   → use-case: consumeLastSeen returns null
   → use-case: arm(Read); awaits nfc.state terminal
2. ReadingHint shows "Tap a tag to read…"
3. User taps tag
   → MainActivity.onNewIntent → NfcRepository.onTagDiscovered
   → state -> Success(uid, classification)
   → use-case continues with findSpoolsByCardUid
4. Same result-mapping path as 3.1.
```

### 3.3 Re-tap during reading (BR-U5-VM-2)

```
activeFlow == ReadingForPair, then user taps Read FAB again:
1. VM: cancel in-flight job
2. VM: nfc.disarm() (transitions Reading -> Idle)
3. VM: re-enter onReadTapped path (consumeLastSeen + arm or use buffered)
   activeFlow stays ReadingForPair throughout (only briefly Idle internally)
```

### 3.4 Dropdown selection (S-3.6)

```
1. User taps SpoolmanDropdown anchor
2. Menu expands listing spools (preceded by a "Clear" item)
3. User picks a spool
   → onSelect(spool) → vm.onSpoolSelected(spool)
   → form prefilled from spool (BR-U5-VM-5)
   → ambiguity cleared
4. Or user picks "Clear"
   → onSelect(null) → vm.onSpoolSelected(null)
   → form reset (cardUid + rawWriteMode preserved per BR-U5-MAP-6)
```

### 3.5 Ambiguity resolution (S-3.3)

```
1. Read produces Ambiguous result
2. AmbiguityBlock renders matches
3. User picks one match from SpoolmanDropdown
   → onSpoolSelected(spool) clears AmbiguityState and prefills form
   (No PATCH/POST issued — repository.findSpoolsByCardUid was the only call; selection is purely client-side state.)
```

---

## 4. Form validation rules in U5

U5 ships a **read-only** form preview. Input validation lives in U6a (write path). U5 validates only:

- `colorHex` canonicalisation (BR-U5-MAP-1) — applied during prefill, not during input.
- `Int?` parsing of OpenSpool temp strings (BR-U5-MAP-5) — failed parse silently falls back to material defaults; never surfaces as a user-visible error in U5.

No "save" / "submit" button is present in U5. No required-field highlighting. No async validation.

---

## 5. API integration points

| Component | Backend method | Purpose |
|---|---|---|
| `MainViewModel` (init) | `SpoolmanRepository.spools.collect` | populate `SpoolmanState.spools` |
| `MainViewModel` (init) | `SettingsRepository.settings.collect` | derive `urlConfigured` |
| `MainViewModel` (init) | `NfcRepository.state.collect` | mirror to `MainUiState.nfc` |
| `MainViewModel.onReadTapped` | `ReadAndPairUseCase.invoke()` | runs the FR-3 flow |
| `MainViewModel.onSpoolSelected` | (no backend call) | client-side state only |
| `ReadAndPairUseCase` | `NfcRepository.consumeLastSeen / arm / disarm`, `NfcRepository.state` | NFC read |
| `ReadAndPairUseCase` | `SpoolmanRepository.findSpoolsByCardUid` | UID lookup |

No direct repository calls from Compose. No Retrofit/OkHttp visible to UI. (Conforms to NFR-1.2.)

---

## 6. Compose-side state hoisting and recomposition

- `MainScreen` is the single stateful entry point. It collects state + effects from `MainViewModel`.
- All sub-components receive primitive props or sealed types — no `MutableState`/`StateFlow` references inside.
- `SpoolmanDropdown` owns its `expanded` `MutableState` locally (UI-only, transient — does not need VM round-trip).
- Recomposition triggers on `MainUiState` slice changes only (data classes are equal when fields are equal — Kotlin generated `equals` skips unchanged work).

---

## 7. Accessibility (ship in U5)

Per NFR-8 (deferred to U10 polish for full pass), U5 ships:

- All buttons / icons have `contentDescription` or surrounding text labels.
- `UidRow` text is selectable so testers can copy hex.
- `AmbiguityBlock` uses semantic role "Alert".
- Snackbar text passes through Material's accessibility defaults — no custom override in U5.

Full accessibility audit is U10's concern.

---

## 8. Out of scope for U5 frontend

- Form input components (`MaterialPicker`, `BrandPicker`, `ColorPicker`, `TempPanel`) — U6a.
- Sheets (`RepairConfirmSheet`, `VendorOptInSheet`, `PairAnotherTagSheet`, `AddCustom*Sheet`) — U6b/U7/U8.
- Settings screen — U9.
- Theming customisation — U9.
- Final v2 colour palette / iconography — U10.
- v1's `MainScreenContent` and any remaining v1 Compose surface that is **read-flow specific** is removed in U5; write-flow / sheet code from v1 stays untouched until U6a/U6b lands.
