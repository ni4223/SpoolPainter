# U9 — Domain Entities

**Stage**: CONSTRUCTION → Per-Unit Loop → Functional Design → Domain Entities
**Unit**: U9 — Settings + Theming + UI Shell
**Locked**: 2026-05-29 (revised same day after user direction — theme moves to TopAppBar; sort splits into spool + filament)

Source plan: `aidlc-docs/construction/plans/u9-settings-theming-banner-functional-design-plan.md` (FD Part 1, Q-U9-1..Q-U9-13 resolved via "go go go" — all `My pick` defaults locked; **Q-U9-1 / Q-U9-2 / Q-U9-13 revised 2026-05-29** per user direction "For theme, put it on top bar with just sign like moon sun and auto in between … so two kind of sorts, spool sort and filament").

---

## 1. Net-new domain types

### 1.1 `Currency` enum (Q-U9-11=A)

New `@Serializable` enum on `data/local/Settings.kt`. Symbol-only — no decimal-separator / position metadata (forward-compat path captured in §6).

```kotlin
@Serializable
enum class Currency(val symbol: String) {
    Dollar("$"),
    Euro("€"),
    Generic("¤"),  // U+00A4 — generic currency sign
}
```

**Invariants**:
- `symbol` is a stable identifier: never empty, never multi-codepoint. Renders in Roboto + Noto Sans (Android system fonts ≥ API 29).
- Enum is `@Serializable` via kotlinx-serialization name-based encoding (matches `SortOrder` / `ThemeOverride` pattern in `Settings.kt:13-16`).

### 1.2 `Settings` schema delta (sort split + currency add)

`data/local/Settings.kt` — **`sortOrder` field is removed and replaced by two independent fields** `spoolSortOrder` + `filamentSortOrder`; `currency` is added. `themeOverride` is unchanged in schema (top-bar control still writes through `setThemeOverride`). All four are additive on the JSON payload — no migration code needed (kotlinx-serialization fills missing JSON fields with default values when reading old payloads, and the old `sortOrder` key is silently dropped on decode of newer code reading older payloads since unknown fields are ignored by default; **see §1.2.1 below for the legacy-`sortOrder` migration path**).

```kotlin
@Serializable
data class Settings(
    val url: String = "",
    val spoolSortOrder: SortOrder = SortOrder.Default,    // RENAMED from sortOrder (Q-U9-2 revised)
    val filamentSortOrder: SortOrder = SortOrder.Default, // NEW (Q-U9-2 revised)
    val themeOverride: ThemeOverride = ThemeOverride.System,
    val currency: Currency = Currency.Dollar,             // NEW (Q-U9-11=A)
)
```

`SettingsSerializer.defaultValue` is unchanged in shape (still `Settings()`); the new defaults flow through automatically.

#### 1.2.1 Legacy `sortOrder` migration

Old shipped payloads contain `"sortOrder": "Default" | "Alphabetical" | "MaterialThenColor"`. New code only knows `"spoolSortOrder"` + `"filamentSortOrder"`.

**Strategy**: kotlinx-serialization is configured (via `Json { ignoreUnknownKeys = true }` in `SettingsSerializer`) to silently drop the legacy `sortOrder` key on read. Both new keys default to `SortOrder.Default` when absent. **Net behaviour for an upgrading user**: any prior sort preference is forgotten on first launch of U9 — both dropdowns reset to id-descending. Acceptable because (a) sort order is a UI preference, not data; (b) v2 testers are a small population per the Play Store testing-track gate; (c) explicit "Last sort preference will be reset on this update" snackbar is out of scope.

If preserving the legacy value matters during U10 release polish, the migration becomes a one-time `JsonElement` rewrite inside `SettingsSerializer.readFrom`: if the parsed root has a `sortOrder` key, copy its value into `spoolSortOrder` (and leave `filamentSortOrder` at default) before decoding into `Settings`. Logged as a U10 follow-up (out of U9 scope).

### 1.3 `SettingsRepository` interface delta

```kotlin
interface SettingsRepository {
    val settings: StateFlow<Settings>
    suspend fun setUrl(url: String)
    suspend fun setSpoolSortOrder(order: SortOrder)     // RENAMED from setSortOrder
    suspend fun setFilamentSortOrder(order: SortOrder)  // NEW
    suspend fun setThemeOverride(theme: ThemeOverride)
    suspend fun setCurrency(currency: Currency)         // NEW (Q-U9-11=A)
}
```

`SettingsRepositoryImpl` mirrors the existing setter pattern (`store.updateData { it.copy(...) }`). `setThemeOverride` is unchanged in shape; the call-site shifts from `SettingsViewModel.onThemeOverrideChanged` to a new top-bar handler in `MainViewModel.onThemeCycleTapped` (see §1.5b).

### 1.4 `SettingsUiState` delta

`ui/screens/settings/SettingsUiState.kt` — drops `themeOverride` (no longer rendered on Settings); replaces `sortOrder` with two fields; gains `currency`:

```kotlin
data class SettingsUiState(
    val url: String = "",
    val spoolSortOrder: SortOrder = SortOrder.Default,    // RENAMED + replaces sortOrder
    val filamentSortOrder: SortOrder = SortOrder.Default, // NEW
    val currency: Currency = Currency.Dollar,             // NEW
)
```

`SettingsViewModel.state` mapping extends to `SettingsUiState(it.url, it.spoolSortOrder, it.filamentSortOrder, it.currency)`. `themeOverride` deliberately not projected — Settings UI no longer renders it.

### 1.5 `MainUiState` delta — `priceSuffix` + sort projection (Q-U9-12=A, Q-U9-1 revised)

`ui/screens/main/MainUiState.kt` gains `priceSuffix: String` plus **two** sort fields (one per dropdown). `themeOverride` is **not** projected onto `MainUiState` — it's read at `MainActivity.setContent` (Q-U9-5=A) AND surfaced as a separate small flow on `MainViewModel` for the TopAppBar icon (`themeOverride: StateFlow<ThemeOverride>`; see §1.5b).

```kotlin
data class MainUiState(
    // ...existing fields...
    val spoolSortOrder: SortOrder = SortOrder.Default,    // NEW (Q-U9-1 revised)
    val filamentSortOrder: SortOrder = SortOrder.Default, // NEW (Q-U9-1 revised)
    val priceSuffix: String = "$",                         // NEW — derived from Settings.currency.symbol
)
```

Default `"$"` matches today's hard-coded suffix in `MoreDetailsExpander.kt:107`.

### 1.5b `MainViewModel.themeOverride` flow (NEW — Q-U9-13=A)

`MainViewModel` exposes `themeOverride: StateFlow<ThemeOverride>` mapped from `settings.settings.themeOverride`. The MainScreen TopAppBar's cycle-icon button reads this state and binds its handler to `MainViewModel.onThemeCycleTapped()`.

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

**Rationale for separate flow** (not on `MainUiState`): the existing `MainUiState` `combine` is already large (banner + spoolman + form + nfc + activeFlow). Adding `themeOverride` would force re-emission of the entire UI state on every theme tap. A standalone `themeOverride: StateFlow` keeps the topbar icon's recomposition independent of the form's state.

**Cycle order**: `System → Light → Dark → System`. Locked per Q-U9-13=A. Visualised at the icon as sun = Light, moon = Dark, auto-symbol (`Brightness.Auto`) = System (icon shows the **current** state, tap advances to the next).

### 1.6 `BannerState` — no change

Already declared in `MainUiState.kt`:

```kotlin
sealed interface BannerState {
    data object Hidden : BannerState
    data class Offline(val lastError: String?) : BannerState
}
```

Locked as final per §2.5.2 of FD Part 1 plan. No `Reachable` variant; no `Retry` action; banner remains read-only (Q-CD1.1=A; Q-U9-9=A).

### 1.7 `SortOrder` — no schema change (Q-U9-2 revised, partial — enum kept; usage doubled)

Existing enum kept verbatim:

```kotlin
@Serializable
enum class SortOrder { Default, Alphabetical, MaterialThenColor }
```

UI labels match the enum (`Default` / `Alphabetical` / `Material then Color`), not S-9.2's story copy ("None / Brand A-Z / Material A-Z / Last Used"). Story-copy reconciliation logged for U10 / post-v2.0.

**What changed in revision**: the enum is now consumed by **two** `Settings` fields (`spoolSortOrder` + `filamentSortOrder`) and **two** comparator factories (`spoolComparator` + `filamentComparator`). The enum itself is unchanged.

### 1.8 `ThemeOverride` — no schema change

Existing enum kept verbatim:

```kotlin
@Serializable
enum class ThemeOverride { System, Light, Dark }
```

Cycle order in §1.5b uses the enum values directly. No new variants.

---

## 2. Comparator factory (Q-U9-1 revised, Q-U9-7=B)

### 2.1 `ui/components/SortComparators.kt` (NEW file)

Single source of truth for both spool and filament dropdown ordering. Hidden inside `ui/components/` so reuse is cheap and the file does not pollute `domain/`.

```kotlin
package com.spoolpainter.app.ui.components

import com.spoolpainter.app.data.local.SortOrder
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanSpool

fun spoolComparator(order: SortOrder): Comparator<SpoolmanSpool> = when (order) {
    SortOrder.Default ->
        compareByDescending { it.id ?: Int.MIN_VALUE }
    SortOrder.Alphabetical ->
        compareBy(String.CASE_INSENSITIVE_ORDER) { spoolSortKey(it) }
    SortOrder.MaterialThenColor ->
        compareBy<SpoolmanSpool>(String.CASE_INSENSITIVE_ORDER) { it.filament.material ?: "" }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.filament.color_hex ?: "" }
            .thenByDescending { it.id ?: Int.MIN_VALUE }
}

fun filamentComparator(order: SortOrder): Comparator<SpoolmanFilament> = when (order) {
    SortOrder.Default ->
        compareByDescending { it.id }
    SortOrder.Alphabetical ->
        compareBy(String.CASE_INSENSITIVE_ORDER) { filamentSortKey(it) }
    SortOrder.MaterialThenColor ->
        compareBy<SpoolmanFilament>(String.CASE_INSENSITIVE_ORDER) { it.material ?: "" }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.color_hex ?: "" }
            .thenByDescending { it.id }
}

private fun spoolSortKey(spool: SpoolmanSpool): String =
    listOfNotNull(
        spool.filament.vendor?.name?.takeIf { it.isNotBlank() },
        spool.filament.name?.takeIf { it.isNotBlank() },
        spool.id?.toString(),
    ).joinToString(" · ").ifEmpty { "—" }

private fun filamentSortKey(filament: SpoolmanFilament): String =
    listOfNotNull(
        filament.vendor?.name?.takeIf { it.isNotBlank() },
        filament.name?.takeIf { it.isNotBlank() },
        filament.material?.takeIf { it.isNotBlank() },
    ).joinToString(" · ").ifEmpty { filament.id.toString() }
```

**Invariants**:
- Both comparators are `Stable` enough for Compose recomposition: pure functions of `SortOrder`; result is a value-equal `Comparator` (Java's `Comparator.then*` chain).
- `MaterialThenColor` uses `id`-descending as a final tiebreaker so equal `(material, color)` pairs preserve recency ordering.
- Default ordering matches today's behavior (`MainScreen.kt:285` for spools; `FilamentPicker.kt:45` currently sorts by display string — Default flips it to id-desc per FR-9 spec and parity with the spool side).
- The two factories are **independent**: each consumer reads its own `SortOrder` from `MainUiState` (`spoolSortOrder` for `SpoolmanDropdown`; `filamentSortOrder` for `FilamentPicker`).

> **FD note — `FilamentPicker` Default-vs-Alphabetical sort**
> The current `FilamentPicker.kt:45` hardcodes `sortedBy { displayString.lowercase() }`. Wiring `filamentComparator(SortOrder.Default)` flips that to id-descending. This **changes** observable behavior for users who haven't picked a sort order. Acceptable: the default behavior matches the Spool dropdown (newest-first), which is internally consistent. Documented for the U9 close-out summary.

### 2.2 Consumer wiring

| Consumer | Today | After U9 |
|---|---|---|
| `MainScreen.SpoolmanDropdown` (line 283) | `.filterNot { it.archived }.sortedByDescending { it.id ?: Int.MIN_VALUE }` | `.filterNot { it.archived }.sortedWith(spoolComparator(state.spoolSortOrder))` |
| `FilamentPicker` (line 45) | `filaments.sortedBy { it.displayString().lowercase() }` | `filaments.sortedWith(filamentComparator(sortOrder))` — caller passes `state.filamentSortOrder` |

Both consumers receive their respective `SortOrder` via `MainUiState`. The two values are **not** synchronised — changing the spool sort does not change filament sort, and vice versa.

---

## 3. `MainUiState` settings-projection delta

Today `MainViewModel` collects `settings.settings.map { it.url.isNotBlank() }` only. U9 extends the `MainViewModel` settings-flow combine to project **three** more fields onto `MainUiState`:

| `MainUiState` field | Source | Default |
|---|---|---|
| `spoolSortOrder: SortOrder` | `Settings.spoolSortOrder` | `SortOrder.Default` |
| `filamentSortOrder: SortOrder` | `Settings.filamentSortOrder` | `SortOrder.Default` |
| `priceSuffix: String` | `Settings.currency.symbol` | `"$"` (from `Currency.Dollar`) |

`MainUiState` carries `SortOrder` rather than rebuilt comparators because the comparator factory consumes `SortOrder` directly. `priceSuffix` is pre-derived per Q-U9-12=A — `MainViewModel.kt` does the `.symbol` map once, leaf composables stay decoupled from `Currency`.

`themeOverride` is **not** projected onto `MainUiState`; it lives on a standalone `MainViewModel.themeOverride: StateFlow<ThemeOverride>` (§1.5b) and is read at `MainActivity.setContent` (Q-U9-5=A) for `darkTheme`/`dynamicColor` resolution.

---

## 4. `SpoolPainterTheme` signature delta (Q-U9-5=A, Q-U9-8=A)

`ui/theme/Theme.kt` — body unchanged in shape. Default for `dynamicColor` flips:

```kotlin
@Composable
fun SpoolPainterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,  // FLIPPED: was false (Q-U9-8=A)
    content: @Composable () -> Unit,
) { /* ... unchanged ... */ }
```

Pre-Android-12 fallback (the `LightColors`/`DarkColors` gold/dark-goldenrod palette) preserved verbatim — already gated by `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` inside the `colorScheme` `when`.

`MainActivity.setContent` becomes the override resolution site (Q-U9-5=A — see §5 below for the pattern).

---

## 5. `MainActivity` Hilt-injected theme resolution (Q-U9-5=A)

`ui/activity/MainActivity.kt` — adds `@Inject lateinit var settingsRepository: SettingsRepository` and resolves `darkTheme` from `Settings.themeOverride` at `setContent`:

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var nfcRepository: NfcRepository
    @Inject lateinit var settingsRepository: SettingsRepository  // NEW

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle()
            val darkTheme = when (settings.themeOverride) {
                ThemeOverride.System -> isSystemInDarkTheme()
                ThemeOverride.Light -> false
                ThemeOverride.Dark -> true
            }
            SpoolPainterTheme(darkTheme = darkTheme, dynamicColor = true) {
                var showSettings by rememberSaveable { mutableStateOf(false) }
                if (showSettings) {
                    BackHandler { showSettings = false }
                    SettingsScreen(onBack = { showSettings = false })
                } else {
                    MainScreen(onNavigateToSettings = { showSettings = true })
                }
            }
        }
        intent?.let { tryDispatchNfcIntent(it) }
    }
    // onNewIntent / onResume / onPause / tryDispatchNfcIntent unchanged
}
```

**Invariants**:
- `settingsRepository.settings` is a `StateFlow` started `Eagerly` — `collectAsStateWithLifecycle` returns a non-null first value immediately; no flicker on cold start.
- Theme resolution is a pure `when` over `ThemeOverride`; no side effects, no `LaunchedEffect`. Re-derives on settings emission (triggered by either Settings-screen URL save or **MainScreen TopAppBar theme cycle**), feeding `SpoolPainterTheme` recomposition — Material 3 swaps the `colorScheme` without an activity recreate.
- `dynamicColor = true` is hardcoded at the call site for v2.0; if Q-U9-8 ever flips back (post-launch user feedback), the parameter source becomes `Settings`.

---

## 6. Forward-compat carve-outs (post-v2.0)

| Carve-out | Trigger | Effort |
|---|---|---|
| Decimal-separator + position per locale | Currency carrier shape Q-U9-12 flips A → B (enum down to leaf); `Currency` gains `decimalSeparator: Char` + `precedesAmount: Boolean` | Mechanical — one composable signature swap |
| `useDynamicColor` Settings toggle | Q-U9-8 flips A → C (user backlash on Material You); `Settings.useDynamicColor: Boolean = true`; `MainActivity` reads it instead of hardcoding | Additive — mirrors `themeOverride` wiring |
| `LastUsed` sort option (S-9.2 story copy) | New requirement to track per-spool "last touched at"; `SortOrder.LastUsed`; `Spool.lastUsedAt: Instant` projected from extra fields | New field + new comparator branch — non-trivial; defer |
| Banner "Tap to open Settings" | Q-U9-9 flips A → B | UI-only — `Modifier.clickable { onNavigateToSettings() }` on the banner card |
| Theme as long-press → menu (System/Light/Dark explicit pick) | User feedback that 3-state cycle is unintuitive | Additive — wrap topbar icon in `combinedClickable` + `DropdownMenu` |
| Per-section sort enum reconciliation (S-9.2 story copy) | Story-copy revisit | Out of v2 scope |
| Legacy-`sortOrder` JSON migration to `spoolSortOrder` | U10 release polish ask | One-time JsonElement rewrite in `SettingsSerializer.readFrom` |

None of these are in U9 scope. Documented so U9b/U10 reviewers don't re-derive them.

---

## 7. File impact summary

| Path | Action | Reason |
|---|---|---|
| `app/src/main/java/com/spoolpainter/app/data/local/Settings.kt` | Modify | Add `Currency` enum + `currency` field; **rename `sortOrder` → `spoolSortOrder`**; **add `filamentSortOrder`** |
| `app/src/main/java/com/spoolpainter/app/data/local/SettingsRepository.kt` | Modify | Rename `setSortOrder` → `setSpoolSortOrder`; add `setFilamentSortOrder`; add `setCurrency` |
| `app/src/main/java/com/spoolpainter/app/data/local/SettingsSerializer.kt` | No-op | Default flows through constructor; kotlinx-serialization fills missing fields. (Legacy `sortOrder` migration deferred to U10 — see §1.2.1) |
| `app/src/main/java/com/spoolpainter/app/ui/screens/settings/SettingsUiState.kt` | Modify | Drop `themeOverride` (moved to MainScreen); split `sortOrder` → `spoolSortOrder` + `filamentSortOrder`; add `currency` |
| `app/src/main/java/com/spoolpainter/app/ui/screens/settings/SettingsViewModel.kt` | Modify | Add `onSpoolSortOrderChanged` + `onFilamentSortOrderChanged` + `onCurrencyChanged`; **drop** `onThemeOverrideChanged` (moved to `MainViewModel`); extend state mapping |
| `app/src/main/java/com/spoolpainter/app/ui/screens/settings/SettingsScreen.kt` | Modify | Add **two** spool/filament sort sections + currency section; **drop** the theme section; remove placeholder line |
| `app/src/main/java/com/spoolpainter/app/ui/components/SortComparators.kt` | **NEW** | Comparator factory |
| `app/src/main/java/com/spoolpainter/app/ui/components/ThemeCycleIconButton.kt` | **NEW** | Top-bar 3-state cycle icon (sun/auto/moon) |
| `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainUiState.kt` | Modify | Add `spoolSortOrder` + `filamentSortOrder` + `priceSuffix` |
| `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainViewModel.kt` | Modify | Project `spoolSortOrder` + `filamentSortOrder` + `priceSuffix` from settings; expose `themeOverride: StateFlow<ThemeOverride>`; add `onThemeCycleTapped()` |
| `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainScreen.kt` | Modify | TopAppBar gains `ThemeCycleIconButton`; wire `spoolComparator(state.spoolSortOrder)`; pass `priceSuffix` + `state.filamentSortOrder` to form chain |
| `app/src/main/java/com/spoolpainter/app/ui/components/FilamentPicker.kt` | Modify | Wire `filamentComparator`; accept `sortOrder` parameter |
| `app/src/main/java/com/spoolpainter/app/ui/components/MoreDetailsExpander.kt` | Modify | Replace hard-coded `suffix = "$"` with `priceSuffix` parameter |
| `app/src/main/java/com/spoolpainter/app/ui/components/FilamentForm.kt` | Modify | Plumb `priceSuffix` + `filamentSortOrder` through |
| `app/src/main/java/com/spoolpainter/app/ui/activity/MainActivity.kt` | Modify | Inject `SettingsRepository`; resolve `darkTheme` from `themeOverride`; flip `dynamicColor=true` |
| `app/src/main/java/com/spoolpainter/app/ui/theme/Theme.kt` | Modify | `dynamicColor` default `false` → `true` |

**Net**: 2 created, 13 modified, 0 deleted.
