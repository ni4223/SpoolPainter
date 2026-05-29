# U8 — Frontend Components

**Stage**: CONSTRUCTION → Per-Unit Loop → Functional Design → Frontend Components
**Unit**: U8 — Pickers + Custom Entries + Filament Metadata UX
**Locked**: 2026-05-28

Compose surfaces touched. Implementation lives in U8 Code Generation; this is the contract.

---

## 1. Form layout (post-U8)

```
┌─────────────────────────────────────┐
│ [ ▼ Spool ]                         │ ← top-level, always visible (existing — unchanged)
│ [ Material ] [ Brand ]              │
│ [ Color ]                           │
│ [ Variant ]                         │
│ [ Temps ]                           │
│  ─── Filament ▾ ──────────────────  │ ← collapsed expander #1 (NEW)
│  [ ▼ Filament ]                     │ ← visible only when expanded; lists ALL filaments
│  ─── More details ▾ ──────────────  │ ← collapsed expander #2 (NEW)
│  [ Empty spool weight (g) ]         │ ← visible only when expanded
│  [ Price ]                          │
│  [ Full spool weight (g) ]          │
│  [ Diameter (mm) ]                  │
│  [ Density (g/cm³) ]                │
│ [ Save & Write ]                    │
└─────────────────────────────────────┘
```

**Default UI** (both expanders collapsed) MUST be byte-identical to U7's form (BR-U8-2 + BR-U8-13 + AC-14.1).

---

## 2. New components

### 2.1 `FilamentSectionExpander` (Δ-1)

Compose composable hosting the filament picker behind a collapsed-by-default expander.

```kotlin
@Composable
fun FilamentSectionExpander(
    expanded: Boolean,
    onToggle: () -> Unit,
    filaments: List<SpoolmanFilament>,
    selectedFilamentId: Int?,
    onFilamentSelected: (SpoolmanFilament?) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
)
```

**Header row**:
- Material `Icons.Default.ExpandMore` / `ExpandLess` icon (BR-U8-14).
- `Text("Filament")` — no Unicode arrow.
- Whole row clickable; toggles `expanded`.

**Body** (visible when expanded):
- Single `FilamentPicker` Composable hosting `ExposedDropdownMenu`.
- Footer affordance — none for this picker (no add-custom path; filaments are only created via Save & Write).

### 2.2 `FilamentPicker` (Δ-1)

Lists ALL filaments from `SpoolmanRepository.filaments`. Sort: alphabetic by `vendor.name + " " + filament.name + " " + extra.variant`.

**Row format**: `vendor.name · filament.name · variant` (no `[#id]`, no weight — those are spool-scope).

**Selected slot** — when `selectedFilamentId != null`:
- Shows the picked filament's display string.
- "X" affordance to clear → `onFilamentSelected(null)` (BR-U8-22).

**No "➕ Add custom" footer** — filaments only get created via the standard create-and-pair flow.

### 2.3 `MoreDetailsExpander` (Δ-2)

Compose composable hosting the five filament-scope metadata override fields.

```kotlin
@Composable
fun MoreDetailsExpander(
    expanded: Boolean,
    onToggle: () -> Unit,
    form: FormState,
    onEmptySpoolWeightChanged: (String) -> Unit,
    onPriceChanged: (String) -> Unit,
    onFullSpoolWeightChanged: (String) -> Unit,
    onDiameterChanged: (String) -> Unit,
    onDensityChanged: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
)
```

**Header row**: same shape as `FilamentSectionExpander` (Material icon + plain text).

**Body** (visible when expanded): five `OutlinedTextField`s. Each:
- `KeyboardOptions(keyboardType = Decimal)`.
- Suffix: `"g"`, `"$"`, `"g"`, `"mm"`, `"g/cm³"` respectively.
- Empty input → callback receives `""`; VM parses to `null`.
- Per BR-U8-15.

**Position**: between `FilamentSectionExpander` and the Save & Write button (AC-14.3).

### 2.4 `AddCustomMaterialSheet` (FR-8.5; BR-U8-12 input rules)

`ModalBottomSheet`. Title: **"Add custom material"**.

**Fields**:
- Name — `OutlinedTextField`. `onValueChange` filter:
  ```kotlin
  input.filter { it.isLetterOrDigit() || it in "-+" }.take(8).uppercase()
  ```
- Extruder min / max (°C, integer; required, validated `min ≤ max`).
- Bed min / max (°C, integer; required, validated `min ≤ max`).
- Density (g/cm³, decimal; **optional**, blank → null).

**Actions**:
- `Button("Save")` — primary; disabled while invalid OR while name (post-sanitisation) matches an existing entry case-insensitively. Hint text "Already exists" when collision detected.
- `TextButton("Cancel")`.

**Save**: `AddCustomMaterialViewModel.onSave()` → `MainViewModel.onAddCustomMaterialConfirmed(material)`.

### 2.5 `AddCustomBrandSheet` (FR-8.5; BR-U8-12 input rules)

`ModalBottomSheet`. Title: **"Add custom brand"**.

**Fields**:
- Name — `OutlinedTextField`. `onValueChange` filter:
  ```kotlin
  input.filter { it.isLetterOrDigit() || it in " .-" }
       .take(10)
       .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
  ```

**Actions**:
- `Button("Save")` — primary; disabled while name blank OR matches existing case-insensitively.
- `TextButton("Cancel")`.

**Save**: `AddCustomBrandViewModel.onSave()` → `MainViewModel.onAddCustomBrandConfirmed(name)`.

---

## 3. Modified components

### 3.1 `MaterialPicker` — preset → repository swap + footer (Q-U8-2=B, Q-U8-20=A)

| Before (U6a) | After (U8) |
|---|---|
| Reads `MaterialDatabase.materials` (object, hardcoded 10) | Reads `materialBrandRepo.materials: StateFlow<List<Material>>` via `collectAsStateWithLifecycle` |
| Includes "Other" preset → triggers inline `customMaterial` field | "Other" removed from presets; **footer row "➕ Add custom material…"** opens `AddCustomMaterialSheet` |
| Inline "Other → typed" custom field stays as a one-shot path (no DataStore) | unchanged — BR-U8-7 |

### 3.2 `BrandPicker` — same shape as `MaterialPicker`

Reads `materialBrandRepo.brands: StateFlow<List<String>>`. "Other" removed; footer row "➕ Add custom brand…" opens `AddCustomBrandSheet`.

### 3.3 `FilamentForm` — host the two new expanders

Insert `FilamentSectionExpander` after `TempPanel`, then `MoreDetailsExpander` after that, then the existing Save & Write button.

State: both expanders draw their `expanded` flag from `FormState.filamentSectionExpanded` / `moreDetailsExpanded` respectively. Toggle handlers route through `MainViewModel.onFilamentSectionToggled()` / `onMoreDetailsToggled()`.

### 3.4 `SpoolmanDropdown` — UNCHANGED

Per BR-U8-5, the spool dropdown keeps its U6a/U6b shape. No section headers, no filament rows. Single-section list of spools (archived hidden per U6a polish).

### 3.5 `BottomSheetHost` — two new branches

```kotlin
when (val flow = state.activeFlow) {
    // ... existing branches (RepairConfirm, PairAnotherTag, etc.) ...
    is ActiveFlow.AddingCustomMaterial -> AddCustomMaterialSheet(...)
    is ActiveFlow.AddingCustomBrand    -> AddCustomBrandSheet(...)
    else -> Unit
}
```

### 3.6 `AddCustomMaterialViewModel` / `AddCustomBrandViewModel` — replace U1 placeholders

```kotlin
data class AddCustomMaterialUiState(
    val name: String = "",
    val extruderMin: String = "",
    val extruderMax: String = "",
    val bedMin: String = "",
    val bedMax: String = "",
    val density: String = "",
    val saveEnabled: Boolean = false,
    val collisionHint: String? = null,   // "Already exists" when applicable
)

@HiltViewModel
class AddCustomMaterialViewModel @Inject constructor(
    private val repo: MaterialBrandRepository,
) : ViewModel() {
    val state: StateFlow<AddCustomMaterialUiState>
    fun onNameChanged(value: String)
    fun onExtruderMinChanged(value: String)
    // ... mirror handlers
    fun onSave()       // emits a one-shot result; relayed to MainViewModel
    fun onDismiss()
}
```

`AddCustomBrandViewModel` is the brand-only counterpart (one name field).

---

## 4. Form gating

| State | Form fields | Filament expander toggle | More-details expander toggle | Spool dropdown | Save & Write |
|---|---|---|---|---|---|
| `Idle` | enabled | enabled | enabled | enabled | enabled (when canSubmit) |
| `WritingStandard` (existing) | disabled | disabled | enabled (read-only inputs OK) | disabled | disabled |
| `AddingCustomMaterial` (NEW) | disabled (sheet modal over screen) | disabled | disabled | disabled | disabled |
| `AddingCustomBrand` (NEW) | disabled (sheet modal) | disabled | disabled | disabled | disabled |

**Note** — the More-details expander's TOGGLE is enabled during write flows in principle, but practically the sheet modal blocks scrolling; treating it as disabled is harmless.

---

## 5. Visual state matrix — picker selected states

| `selectedSpoolId` | `selectedFilamentId` | Spool dropdown | Filament expander | Save & Write behaviour |
|---|---|---|---|---|
| null | null | "Pick a spool" placeholder | "Pick a filament" inside expander (when open) | create-new-filament path (existing) |
| 42 | null | "Polymaker PLA · Galaxy Blue [#42] (982g)" + X | "Pick a filament" placeholder (collapsed by default) | append-to-spool path (existing) |
| null | 17 | "Pick a spool" placeholder | "Polymaker PLA Pro · Pearl White" + X | existing-filament path (Δ-1; new-spool created under F17) |

Mutex enforced at VM setters (BR-U8-22): never row 4 ("both set"). "X" clear from either picker → row 1.

---

## 6. Snackbar palette (additions)

Existing palette unchanged. U8 additions:

| Trigger | Copy |
|---|---|
| `addCustomMaterial` succeeds | "Custom material added" |
| `addCustomBrand` succeeds | "Custom brand added" |
| `patchFilament` HTTP error during existing-filament Save | "Couldn't update filament details — saved spool only." (PATCH-after-POST partial state per BR-U8-21) |
| `getFilament` returns 404 on existing-filament Save | "Filament not found in Spoolman — refresh and try again." |

All add-custom flows already auto-select the new entry (BR-U8-11), so the snackbar is informational, not a navigation cue.

---

## 7. Accessibility notes

- Both expanders' header rows MUST set `Modifier.semantics { role = Role.Button; stateDescription = if (expanded) "Expanded" else "Collapsed" }`.
- The "X" clear affordance on each picker MUST set `Modifier.semantics { contentDescription = "Clear selection" }`.
- Add-custom sheets MUST set `Modifier.semantics { isTraversalGroup = true; traversalIndex = 0f }` on the title for screen-reader landing.

---

## 8. Out of scope (UI surface)

- Edit-existing-custom-material / edit-custom-brand UI — deferred post-v2.0.
- Bulk import of custom entries — deferred.
- Brand colour swatch / logo previews — deferred.
- Filament-edit screen (without create-spool) — explicitly rejected by delta §3.
- Reordering / drag-and-drop on custom entries — deferred.
