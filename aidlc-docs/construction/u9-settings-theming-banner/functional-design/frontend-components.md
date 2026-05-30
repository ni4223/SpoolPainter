# U9 — Frontend Components

**Stage**: CONSTRUCTION → Per-Unit Loop → Functional Design → Frontend Components
**Unit**: U9 — Settings + Theming + UI Shell
**Locked**: 2026-05-29 (revised same day per user direction — theme moves to TopAppBar; sort splits into spool + filament)

Source plan: `aidlc-docs/construction/plans/u9-settings-theming-banner-functional-design-plan.md`. All Q-U9-1..Q-U9-13 resolved via "go go go".

---

## 1. SettingsScreen layout (theme block REMOVED; sort doubled)

```
╔═════════════════════════════════════════════════════╗
║ ← Settings                                           ║
╟─────────────────────────────────────────────────────╢
║                                                      ║
║ Spoolman URL (e.g. http://nas.local:7912)            ║
║ ┌──────────────────────────────────────────────────┐ ║
║ │ http://nas.local:7912                            │ ║   (existing)
║ └──────────────────────────────────────────────────┘ ║
║ [Save]   [Test connection]                           ║   (existing)
║ ┌──────── Refresh spool list ────────┐               ║   (existing)
║ └────────────────────────────────────┘               ║
║                                                      ║
║ Spool list sort                          NEW         ║
║ [ Default | Alphabetical | Material…Color ]          ║
║                                                      ║
║ Filament list sort                       NEW         ║
║ [ Default | Alphabetical | Material…Color ]          ║
║                                                      ║
║ Currency                                 NEW         ║
║ [ $ Dollar | € Euro | ¤ Generic ]                    ║
║                                                      ║
╚═════════════════════════════════════════════════════╝
```

**Theme block is intentionally absent** — Q-U9-13=A moved theme to MainScreen TopAppBar. The placeholder line "Sort order, theme, and full banner UI land in U9." (currently at `SettingsScreen.kt:117-120`) is removed.

## 1b. MainScreen TopAppBar — cycle icon (NEW)

```
╔═════════════════════════════════════════════════════╗
║ SpoolPainter                              ☼  ⚙       ║   ← (cycle icon · Settings)
╟─────────────────────────────────────────────────────╢
║                                                      ║
║   ...main content...                                 ║
║                                                      ║
╚═════════════════════════════════════════════════════╝
```

Cycle icon glyphs (current state shown, tap advances):

| `ThemeOverride` | Icon | Symbol |
|---|---|---|
| `System` | `Icons.Outlined.BrightnessAuto` | auto / "A" |
| `Light` | `Icons.Outlined.LightMode` | sun |
| `Dark` | `Icons.Outlined.DarkMode` | moon |

Settings gear remains the right-most icon to preserve discoverability of Settings.

---

## 2. New / modified components

### 2.1 `SettingsScreen` — modified (theme section removed; sort doubled)

**Path**: `app/src/main/java/com/spoolpainter/app/ui/screens/settings/SettingsScreen.kt`

**Adds three sections** below "Refresh spool list" (in this order — top-to-bottom):

```kotlin
// Section 2 — Spool list sort
SettingsSegmentedSection(
    label = "Spool list sort",
    options = listOf(
        SortOrder.Default to "Default",
        SortOrder.Alphabetical to "Alphabetical",
        SortOrder.MaterialThenColor to "Material then Color",
    ),
    selected = state.spoolSortOrder,
    onSelect = viewModel::onSpoolSortOrderChanged,
    testTag = "settings-spool-sort-order",
)

// Section 3 — Filament list sort (NEW; Q-U9-1 revised — independent from spool sort)
SettingsSegmentedSection(
    label = "Filament list sort",
    options = listOf(
        SortOrder.Default to "Default",
        SortOrder.Alphabetical to "Alphabetical",
        SortOrder.MaterialThenColor to "Material then Color",
    ),
    selected = state.filamentSortOrder,
    onSelect = viewModel::onFilamentSortOrderChanged,
    testTag = "settings-filament-sort-order",
)

// Section 4 — Currency (NEW; Q-U9-11=A)
SettingsSegmentedSection(
    label = "Currency",
    options = listOf(
        Currency.Dollar to "$ Dollar",
        Currency.Euro to "€ Euro",
        Currency.Generic to "¤ Generic",
    ),
    selected = state.currency,
    onSelect = viewModel::onCurrencyChanged,
    testTag = "settings-currency",
)
```

**Theme section deliberately omitted** (Q-U9-13=A — moved to MainScreen TopAppBar).

### 2.2 `SettingsSegmentedSection` — new internal helper

**Path**: `app/src/main/java/com/spoolpainter/app/ui/screens/settings/SettingsSegmentedSection.kt` (new file, kept under `screens/settings/` rather than `ui/components/` because it's a tightly-coupled internal helper).

Generic over the option enum to share layout + a11y across all three sections (per Q-U9-4=A — segmented buttons; per BR-U9-7).

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> SettingsSegmentedSection(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,[u6a-create-and-pair-flow-functional-design-plan.md](../../plans/u6a-create-and-pair-flow-functional-design-plan.md)
    onSelect: (T) -> Unit,
    testTag: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, (value, displayLabel) ->
                SegmentedButton(
                    selected = value == selected,
                    onClick = { if (value != selected) onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                    modifier = Modifier.testTag("$testTag-${value.toString().lowercase()}"),
                ) {
                    Text(displayLabel)
                }
            }
        }
    }
}
```

**Notes**:
- `selected = value == selected` — equality check works for enums via reference equality (singleton instances).
- `if (value != selected) onSelect(value)` — tap-on-selected is a no-op; spares an unnecessary DataStore round-trip.
- `testTag` per option for Compose UI tests; lowercase enum name keeps tags stable.

### 2.3 `SettingsViewModel` — modified

**Path**: `app/src/main/java/com/spoolpainter/app/ui/screens/settings/SettingsViewModel.kt`

**State mapping** extends to include `spoolSortOrder` + `filamentSortOrder` + `currency`; **drops `themeOverride`** (theme is no longer rendered on Settings):

```kotlin
val state: StateFlow<SettingsUiState> = settings.settings
    .map { SettingsUiState(it.url, it.spoolSortOrder, it.filamentSortOrder, it.currency) }
    .stateIn(...)
```

**Three new handlers** (BR-U9-11 — no snackbar emission); **`onThemeOverrideChanged` removed** (handler moved to `MainViewModel`):

```kotlin
fun onSpoolSortOrderChanged(order: SortOrder) {
    viewModelScope.launch { settings.setSpoolSortOrder(order) }
}

fun onFilamentSortOrderChanged(order: SortOrder) {
    viewModelScope.launch { settings.setFilamentSortOrder(order) }
}

fun onCurrencyChanged(currency: Currency) {
    viewModelScope.launch { settings.setCurrency(currency) }
}
```

### 2.3b `MainViewModel` — modified (theme cycle handler + flows)

**Path**: `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainViewModel.kt`

Adds (per BR-U9-19a..d):

```kotlin
val themeOverride: StateFlow<ThemeOverride> = settings.settings
    .map { it.themeOverride }
    .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeOverride.System)

fun onThemeCycleTapped() {
    viewModelScope.launch {
        val next = when (themeOverride.value) {
            ThemeOverride.System -> ThemeOverride.Light
            ThemeOverride.Light -> ThemeOverride.Dark
            ThemeOverride.Dark -> ThemeOverride.System
        }
        settings.setThemeOverride(next)
    }
}
```

The existing `MainUiState` `combine` is also extended to project `spoolSortOrder` + `filamentSortOrder` + `priceSuffix` (per `domain-entities.md` §3).

### 2.4 `ThemeCycleIconButton` — NEW component

**Path**: `app/src/main/java/com/spoolpainter/app/ui/components/ThemeCycleIconButton.kt`

Stateless composable. Caller passes the current `ThemeOverride` and an `onCycle` lambda; internal `IconButton` wraps the icon + a11y description.

```kotlin
@Composable
fun ThemeCycleIconButton(
    current: ThemeOverride,
    onCycle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (icon, label) = when (current) {
        ThemeOverride.System -> Icons.Outlined.BrightnessAuto to
            "Theme: System (tap to switch to Light)"
        ThemeOverride.Light -> Icons.Outlined.LightMode to
            "Theme: Light (tap to switch to Dark)"
        ThemeOverride.Dark -> Icons.Outlined.DarkMode to
            "Theme: Dark (tap to switch to System)"
    }
    IconButton(
        onClick = onCycle,
        modifier = modifier.testTag("main-theme-cycle"),
    ) {
        Icon(imageVector = icon, contentDescription = label)
    }
}
```

**A11y notes** (BR-U9-19d): `contentDescription` reflects the current state and the tap consequence. TalkBack reads "Theme: System (tap to switch to Light)" etc., which surfaces both the state and the action.

**Icon imports**: `androidx.compose.material.icons.outlined.BrightnessAuto` / `LightMode` / `DarkMode`. All three ship in the core `material-icons-extended` artifact already on the U8 classpath (verified during U8 icon substitution). If `outlined` variants are unavailable for any reason at codegen time, substitute `Icons.Filled.BrightnessAuto` / `LightMode` / `DarkMode` — both sets ship together.

### 2.5 `MainTopBar` — modified

**Path**: `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainScreen.kt:198-210`

**Today**:
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(onSettingsClick: () -> Unit) {
    TopAppBar(
        title = { Text("SpoolPainter") },
        actions = {
            IconButton(onClick = onSettingsClick, ...) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        },
    )
}
```

**After U9** (theme cycle icon added before the Settings gear):
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(
    themeOverride: ThemeOverride,
    onThemeCycleTapped: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    TopAppBar(
        title = { Text("SpoolPainter") },
        actions = {
            ThemeCycleIconButton(
                current = themeOverride,
                onCycle = onThemeCycleTapped,
            )
            IconButton(onClick = onSettingsClick, ...) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        },
    )
}
```

`MainScreen` collects `viewModel.themeOverride` and passes it through:

```kotlin
val themeOverride by viewModel.themeOverride.collectAsStateWithLifecycle()
// ...
Scaffold(
    topBar = {
        MainTopBar(
            themeOverride = themeOverride,
            onThemeCycleTapped = viewModel::onThemeCycleTapped,
            onSettingsClick = viewModel::onSettingsTapped,
        )
    },
    // ...
)
```

### 2.6 `MainScreen.SpoolmanDropdown` — modified

**Path**: `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainScreen.kt:272-302`

**Today** (line 283-285):
```kotlin
val visibleSpools = spools
    .filterNot { it.archived }
    .sortedByDescending { it.id ?: Int.MIN_VALUE }
```

**After U9** (BR-U9-13a, BR-U9-15):
```kotlin
val visibleSpools = spools
    .filterNot { it.archived }
    .sortedWith(spoolComparator(sortOrder))
```

`SpoolmanDropdown` signature gains `sortOrder: SortOrder` parameter; `MainScreen` passes `state.spoolSortOrder` from `MainUiState`.

### 2.7 `FilamentPicker` — modified

**Path**: `app/src/main/java/com/spoolpainter/app/ui/components/FilamentPicker.kt`

**Today** (line 45):
```kotlin
val sorted = remember(filaments) {
    filaments.sortedBy { it.displayString().lowercase() }
}
```

**After U9** (BR-U9-13b, BR-U9-16):
```kotlin
val sorted = remember(filaments, sortOrder) {
    filaments.sortedWith(filamentComparator(sortOrder))
}
```

`FilamentPicker` signature gains `sortOrder: SortOrder` parameter; caller (`FilamentForm` / `FilamentSectionExpander` in `MainScreen`) passes `state.filamentSortOrder` — independent from spool sort.

**Behavior change** (BR-U9-16): default mode flips from display-string ascending to id descending. Documented as deliberate.

### 2.8 `MoreDetailsExpander` — modified

**Path**: `app/src/main/java/com/spoolpainter/app/ui/components/MoreDetailsExpander.kt:104-112`

**Today**:
```kotlin
DecimalField(
    label = "Price",
    supportingText = null,
    suffix = "$",
    value = priceMajor,
    enabled = enabled,
    testTag = "more-details-price",
    onChange = onPriceChange,
)
```

**After U9** (BR-U9-30):
```kotlin
DecimalField(
    label = "Price",
    supportingText = null,
    suffix = priceSuffix,
    value = priceMajor,
    enabled = enabled,
    testTag = "more-details-price",
    onChange = onPriceChange,
)
```

`MoreDetailsExpander` signature gains `priceSuffix: String` parameter (default unset — caller must pass).

### 2.9 `FilamentForm` — modified

**Path**: `app/src/main/java/com/spoolpainter/app/ui/components/FilamentForm.kt`

Plumbs `priceSuffix: String` and `filamentSortOrder: SortOrder` through to children. Two new signature params; pass-through only — no other changes.

### 2.10 `MainActivity` — modified

**Path**: `app/src/main/java/com/spoolpainter/app/ui/activity/MainActivity.kt`

Per `domain-entities.md` §5: inject `SettingsRepository`, resolve `darkTheme` from `Settings.themeOverride`, flip `dynamicColor=true`. Full diff in `domain-entities.md` §5. **Theme cycle handler lives on `MainViewModel`** — `MainActivity` does not surface it; it only consumes the persisted `themeOverride` for `darkTheme` resolution at composition.

### 2.11 `SpoolPainterTheme` — modified

**Path**: `app/src/main/java/com/spoolpainter/app/ui/theme/Theme.kt:48`

```kotlin
@Composable
fun SpoolPainterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,  // FLIPPED from false (Q-U9-8=A)
    content: @Composable () -> Unit,
) { /* body unchanged */ }
```

Comment "// Disable dynamic colors to use our custom theme" removed.

---

## 3. Visual state matrix

| Section | Resting state | Tapped state | Disabled? |
|---|---|---|---|
| URL field | shows persisted URL | text field accepts input | never |
| Save | enabled | brief flash, then snackbar "URL saved" | when URL unchanged from persisted (optional polish — out of scope) |
| Test connection | enabled | shows snackbar "Connected to Spoolman v…" or error variant | never |
| Refresh | enabled | shows snackbar "Refreshed spool list" or error | never |
| Spool sort segmented | one option selected (BR-U9-11 no snackbar) | tap flips selection, persists; spool dropdown reorders | never |
| Filament sort segmented | one option selected (BR-U9-11 no snackbar) | tap flips selection, persists; filament picker reorders independently | never |
| Currency segmented | one option selected | tap flips price suffix everywhere | never |
| TopAppBar theme cycle icon | shows icon for current state (sun/moon/auto) | tap advances cycle; icon flips; theme swaps | never |

---

## 4. Accessibility

- Each Settings segmented section has a single `testTag` on the wrapping Column + per-option tags (`settings-spool-sort-order-default`, `settings-filament-sort-order-alphabetical`, `settings-currency-euro`, …) for Compose UI tests.
- Material 3 segmented buttons surface "selected" state via Talkback automatically.
- Currency labels "$ Dollar" / "€ Euro" / "¤ Generic" — Talkback reads "Dollar sign Dollar", "Euro sign Euro", "Currency sign Generic". Acceptable; localised TTS will substitute names where applicable.
- Theme cycle icon `contentDescription` (BR-U9-19d) reads "Theme: <Current> (tap to switch to <Next>)". Surfaces both current state and tap consequence to TalkBack users — addresses the "blind to icon meaning" failure mode of icon-only buttons.

---

## 5. Banner — no UI change

`MainScreen.BannerSlot` (line 213) is unchanged. U9 ships test coverage only (`MainViewModelBannerTest`) + a thin requirements delta (`requirements-delta-banner-passive.md` per Q-U9-10=B) closing the S-10.2 Retry AC gap.

---

## 6. Composable signature deltas (summary)

| Composable | Before | After |
|---|---|---|
| `SpoolPainterTheme` | `(darkTheme=isSystemInDarkTheme, dynamicColor=false, content)` | `(darkTheme=isSystemInDarkTheme, dynamicColor=true, content)` |
| `MainTopBar` | `(onSettingsClick)` | `(themeOverride, onThemeCycleTapped, onSettingsClick)` |
| `SpoolmanDropdown` | `(spools, selectedId, enabled, onSelect)` | `(spools, sortOrder, selectedId, enabled, onSelect)` |
| `FilamentPicker` | `(filaments, selectedId, enabled, onSelect)` | `(filaments, sortOrder, selectedId, enabled, onSelect)` |
| `MoreDetailsExpander` | `(...existing fields..., priceMajor, ..., onPriceChange)` | `(...existing fields..., priceMajor, priceSuffix, ..., onPriceChange)` |
| `FilamentForm` | `(...form fields...)` | `(...form fields..., filamentSortOrder, priceSuffix)` |
| `ThemeCycleIconButton` | (new) | `(current: ThemeOverride, onCycle: () -> Unit, modifier)` |

`SettingsScreen` signature is unchanged — `(viewModel, onBack)`. New controls live inside.

---

## 7. Tests touching frontend-shape

| Test | What it asserts |
|---|---|
| `SettingsViewModelTest` | `onSpoolSortOrderChanged` / `onFilamentSortOrderChanged` / `onCurrencyChanged` round-trip through repository (`onThemeOverrideChanged` removed — moved to MainViewModel) |
| `SettingsRepositoryTest` | Round-trip for both sort setters, theme, and currency |
| `SpoolComparatorTest` | All three `SortOrder` values for both spool + filament comparators (6 cases) |
| `MainViewModelBannerTest` | Derivation matrix from BR-U9-24 |
| `MainViewModelCurrencyTest` | `priceSuffix` reflects `Settings.currency.symbol` and updates on change |
| `MainViewModelThemeCycleTest` (NEW — Q-U9-13=A) | `themeOverride` flow tracks `Settings.themeOverride`; `onThemeCycleTapped()` advances `System → Light → Dark → System` |
| `MainViewModelSortTest` (NEW — Q-U9-1 revised) | `MainUiState.spoolSortOrder` and `filamentSortOrder` track their respective `Settings` fields independently — flipping spool sort does not touch filament sort, and vice versa |

No Compose UI tests (`SpoolPainterThemeTest` skipped per Q-U9-5=A — pure-Kotlin resolution test against the `when` is the substitute). Sub-target test count: **~352 / 352** (delta +20 vs U8's 332 — +1 spool sort VM, +1 filament sort VM, +1 currency VM, +1 currency repo, +1 theme repo (already shipped, retained), +6 comparator, +3 currency-state, +3 banner, +2 transition, +2 theme cycle).
