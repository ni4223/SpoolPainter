# U6a — Frontend Components

**Unit**: U6a — Create-and-Pair Flow
**Approved**: Q-U6a-1..15 = A (FD Part 1, 2026-05-25)

This document specifies the Compose component shapes U6a ships, including the v1 component carcasses being deleted, the new components replacing them, and the `MainScreen` integration changes.

---

## 1. `MainScreen` integration

### 1.1 New layout (replaces U5's `FormPreview` block)

```text
+---------------------------------------------+
| [TopAppBar — "Spoolpainter" + ⚙]             |  unchanged from U5
+---------------------------------------------+
| BannerSlot (offline / configured-state)      |  unchanged from U5
+---------------------------------------------+
| ReadingHint (when activeFlow == Reading…)    |  unchanged from U5
+---------------------------------------------+
| WritingHint (when activeFlow == WritingForPair  ← NEW (U6a)
|              AND nfc.state ∈ {Writing,Verifying})
+---------------------------------------------+
| UidRow (UID display)                         |  unchanged from U5
+---------------------------------------------+
| SpoolmanDropdown                             |  unchanged from U5
+---------------------------------------------+
| AmbiguityBlock (when present)                |  unchanged from U5
+---------------------------------------------+
| FilamentForm (replaces FormPreview)          |  ← NEW (U6a)
|   - Name field                               |
|   - Vendor field                             |
|   - MaterialPicker                           |
|   - BrandPicker                              |
|   - ColorPicker                              |
|   - Diameter field                           |
|   - Weight field                             |
|   - Density field (auto-filled, editable)    |
|   - TempPanel (extruder + bed)               |
|   - VariantField (optional)                  |
|   - [Save] button (bottom of form)           |  ← Q-U6a-11=A
+---------------------------------------------+
| ReadFab (kept; FAB layer, bottom-right)      |  unchanged from U5
+---------------------------------------------+
```

### 1.2 Behavioural contract

- `FilamentForm` is rendered with `enabled = (state.activeFlow == Idle && !state.form.rawWriteMode)`. While reading or writing, the form is read-only.
- The Save button at the bottom of `FilamentForm` is enabled iff `canSave == true` (per FE-7 / VM-1 / VM-2).
- On Save tap → `viewModel.onWriteTapped()`.
- After `WrittenAndPaired`, the form fully resets (per VM-4); the user lands on a clean form ready for the next pair.

### 1.3 Files touched

| File | Action |
|---|---|
| `ui/screens/main/MainScreen.kt` | modify — replace `FormPreview` with `FilamentForm`; add `WritingHint`; remove inline read-only render |
| `ui/screens/main/components/FormPreview.kt` (if exists) | **delete** (Q-U6a-10=A) |

---

## 2. `FilamentForm` (NEW)

### 2.1 File: `ui/components/FilamentForm.kt`

### 2.2 API

```kotlin
@Composable
fun FilamentForm(
    state: FormState,
    nameField: String,
    vendorField: String,
    enabled: Boolean,
    onChange: (FormChange) -> Unit,
    onSave: () -> Unit,
    canSave: Boolean,
    modifier: Modifier = Modifier,
)

sealed interface FormChange {
    data class Name(val value: String) : FormChange
    data class Vendor(val value: String) : FormChange
    data class Material(val value: com.spoolpainter.app.domain.models.Material?) : FormChange
    data class Brand(val value: com.spoolpainter.app.domain.models.Brand?) : FormChange
    data class ColorHex(val value: String?) : FormChange
    data class Diameter(val value: Double?) : FormChange
    data class Weight(val value: Double?) : FormChange
    data class Density(val value: Double?) : FormChange
    data class TempRanges(val value: com.spoolpainter.app.domain.models.TempRanges) : FormChange
    data class Variant(val value: String?) : FormChange
}
```

**Note on `name` and `vendorField`**: these aren't in `FormState` (FE-1 follow-up); U6a treats them as composable-state that the screen-level VM holds and threads via `onChange`. If cross-component reads multiply, lift into `FormState`.

### 2.3 Internal layout (Column with verticalScroll)

```text
Column(verticalScroll, padding 16.dp, vertical spacing 12.dp)
├── OutlinedTextField  Name *           (label="Name")
├── OutlinedTextField  Vendor *         (label="Vendor name")
├── MaterialPicker     state.material   (FE-2)
├── BrandPicker        state.brand      (FE-3)
├── ColorPicker        state.colorHex   (FE-4)
├── Row {
│     OutlinedTextField  Diameter (mm)
│     OutlinedTextField  Weight (g)
│ }
├── OutlinedTextField  Density (g/cm³)  (auto-filled from material; editable)
├── TempPanel          state.tempRanges (FE-5)
├── VariantField       state.variant    (FE-6, inline per Q-U6a-14=A)
└── Button             onClick = onSave, enabled = canSave
                       Text = "Save & Write"
```

### 2.4 Validation hooks

- Hex color: 6-char `[0-9A-Fa-f]`. On invalid input: red border + helper text.
- Diameter: `> 0`; default suggestion 1.75.
- Weight: `> 0`; default suggestion 1000.
- Density: derived; user can override.
- Temp ranges: per-row `min ≤ max`; red border on violation.

### 2.5 Read-only mode (`enabled = false`)

All sub-components SHOULD render with their stock disabled state. The Save button is hidden (not greyed) when `enabled = false` to avoid confusing affordance.

---

## 3. `MaterialPicker` (NEW — replaces v1 `MaterialSelector`)

### 3.1 File: `ui/components/MaterialPicker.kt`

### 3.2 API

```kotlin
@Composable
fun MaterialPicker(
    selected: Material?,
    onSelect: (Material?) -> Unit,
    modifier: Modifier = Modifier,
)
```

### 3.3 Behaviour

- `ExposedDropdownMenuBox` over `MaterialDatabase.all()` (v1 `data/local/MaterialDatabase` preserved until U8).
- Items: each material's `name` displayed.
- Bottom item "Custom…" → opens an inline `OutlinedTextField` for free-text entry; on confirm, `onSelect(Material(name = typed, isCustom = true))`.
- Clear button (X icon in field): `onSelect(null)`.

### 3.4 v1 → v2 migration

- v1 `ui/components/MaterialSelector.kt` is **deleted**.
- v1 `MaterialSelector`'s API contract differed (used `MaterialDatabase` directly + emitted `Material`); v2's `MaterialPicker` API is friendlier to U8 swap.
- Visual look: matches v1 (Material 3 dropdown). No theme drift expected.

---

## 4. `BrandPicker` (NEW — replaces v1 `BrandSelector`)

### 4.1 File: `ui/components/BrandPicker.kt`

### 4.2 API

```kotlin
@Composable
fun BrandPicker(
    selected: Brand?,
    onSelect: (Brand?) -> Unit,
    modifier: Modifier = Modifier,
)
```

### 4.3 Behaviour

Same shape as `MaterialPicker` (FE-2): dropdown over `BrandDatabase.all()` + "Custom…" + clear.

### 4.4 v1 → v2 migration

- v1 `ui/components/BrandSelector.kt` is **deleted**.

---

## 5. `ColorPicker` (NEW — replaces v1 `ColorSelector`)

### 5.1 File: `ui/components/ColorPicker.kt`

### 5.2 API

```kotlin
@Composable
fun ColorPicker(
    hex: String?,                         // 6-char hex, no '#'
    onChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
)
```

### 5.3 Layout

```text
Row {
    OutlinedTextField (label="Color (hex)", value=hex.orEmpty(), maxLength=6,
                        keyboard=Ascii)
    Spacer(8.dp)
    Box(Modifier.size(48.dp).background(parsedColor ?: checkerPattern))
}
```

### 5.4 Hex parsing

- Filter input to `[0-9A-Fa-f]`, max 6 chars.
- On every keystroke: `onChange(text.takeIf { it.length == 6 } ?: null)`. Partial input → `null` upstream.
- `parsedColor`: if `hex.length == 6`, `Color(0xFF000000 or hex.toLong(16).toInt())`. Otherwise `null` → swatch shows checker.

### 5.5 v1 → v2 migration

- v1 `ui/components/ColorSelector.kt` is **deleted** (it has a heavy color-wheel dialog; U6a ships a simpler hex+swatch surface to keep the form compact; full color-wheel can return in U9 if user requests it).
- This is a **deliberate UX simplification**. If user objects, v1 dialog can be ported back as `ColorPickerDialog` invoked from a "Pick color" button — flagged in U6a out-of-scope; revisit in U9.

---

## 6. `TempPanel` (NEW — replaces v1 `TemperatureCard`)

### 6.1 File: `ui/components/TempPanel.kt`

### 6.2 API

```kotlin
@Composable
fun TempPanel(
    ranges: TempRanges,
    materialDefaults: TempRanges?,    // null when no material selected
    onChange: (TempRanges) -> Unit,
    modifier: Modifier = Modifier,
)
```

### 6.3 Layout

```text
Column {
    Text("Extruder temp (°C)")
    Row { IntField(label="Min"); IntField(label="Max") }
    Spacer(8.dp)
    Text("Bed temp (°C)")
    Row { IntField(label="Min"); IntField(label="Max") }
    Spacer(8.dp)
    TextButton(onClick = applyDefaults, enabled = materialDefaults != null)
        Text("Use material defaults")
}
```

### 6.4 Validation

- `min ≤ max` per row; red border on violation.
- Numeric-only input filter.
- Empty field → null in `TempRanges`.

### 6.5 v1 → v2 migration

- v1 `ui/components/TemperatureCard.kt` is **deleted**.

---

## 7. `VariantField` (NEW — inline in `FilamentForm.kt` per Q-U6a-14=A)

### 7.1 Inline implementation (no separate file)

Defined as a private `@Composable` inside `FilamentForm.kt`:

```kotlin
@Composable
private fun VariantField(
    value: String?,
    enabled: Boolean,
    onChange: (String?) -> Unit,
) {
    OutlinedTextField(
        value = value.orEmpty(),
        onValueChange = { typed ->
            val capped = typed.take(64)                     // Q-U6a-15=A — 64 char cap
            onChange(capped.takeIf { it.isNotBlank() })     // blank → null
        },
        label = { Text("Variant (optional)") },
        enabled = enabled,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
```

---

## 8. `WritingHint` (NEW)

### 8.1 Inline in `MainScreen.kt`

```kotlin
@Composable
private fun WritingHint(visible: Boolean) {
    AnimatedVisibility(visible) {
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Text(
                text = "Tap a tag to write…",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
```

### 8.2 Visibility predicate

`visible = state.activeFlow == ActiveFlow.WritingForPair && state.nfc in setOf(NfcResult.Writing, NfcResult.Verifying)`.

Mirror of U5's `ReadingHint` for symmetry.

---

## 9. Save button — placement details (Q-U6a-11=A)

- **Inside `FilamentForm.kt`**, at the bottom of the Column (last child).
- Full-width `Button` (Material 3 Filled).
- Text: **"Save & Write"** (combines Spoolman save + NDEF write semantics in one verb).
- Enabled iff `canSave`.
- Loading state: when `state.activeFlow == WritingForPair`, button text becomes "Writing…" with a small inline `CircularProgressIndicator`. Button disabled to prevent re-tap.
- Read FAB (U5) remains in its bottom-right slot — unchanged. Two surfaces serve two flows; no FAB conflict because the FAB is a layout sibling, not inside the form.

---

## 10. File-action summary

| File | Action | Notes |
|---|---|---|
| `ui/components/FilamentForm.kt` | **create** | FE-1, FE-7, FE-9 |
| `ui/components/MaterialPicker.kt` | **create** | FE-2 |
| `ui/components/BrandPicker.kt` | **create** | FE-3 |
| `ui/components/ColorPicker.kt` | **create** | FE-4 (simplified vs v1) |
| `ui/components/TempPanel.kt` | **create** | FE-5 |
| `ui/components/MaterialSelector.kt` | **delete** | v1 carcass |
| `ui/components/BrandSelector.kt` | **delete** | v1 carcass |
| `ui/components/ColorSelector.kt` | **delete** | v1 carcass |
| `ui/components/TemperatureCard.kt` | **delete** | v1 carcass |
| `ui/screens/main/MainScreen.kt` | modify | replace `FormPreview` block; add `WritingHint`; remove read-only inline render |
| `ui/screens/main/components/FormPreview.kt` (if separate) | **delete** | Q-U6a-10=A |
| `ui/screens/main/MainViewModel.kt` | modify | add `onWriteTapped`, `canWrite`, write-flow state ops |

---

## 11. Out-of-scope confirmations

- **No color-wheel dialog** in U6a (v1 had one; U6a ships hex+swatch only). Revisit in U9 if requested.
- **No catalogue-driven `MaterialPicker` / `BrandPicker`** (U8).
- **No sticky footer for Save** — Save scrolls with the form. Sticky-footer is a U9 polish item if requested.
- **No sheet-based form expansion** (full-screen form variant). Out of scope.
- **No instrumented Compose UI tests** for `FilamentForm` — manual verification at U6 milestone install gate (U6b).
