# U9 — Code Generation Summary

**Stage**: CONSTRUCTION → Per-Unit Loop → Code Generation Part 2 (U9)
**Unit**: U9 — Settings + Theming + UI Shell (sort split + theme widget + currency)
**Executed**: 2026-05-29 (Code Gen Part 1 plan approved via "aidlc continue. start code generation"; Δ-1 install-time iteration folded same day)

Source plan: `aidlc-docs/construction/plans/u9-settings-theming-banner-code-generation-plan.md` §1..§16 executed end-to-end. Install-time iteration captured below as **U9-Δ-1** — no separate FD revision; small in-flight UX corrections applied directly per the same authorisation pattern as U8.

---

## Verification (final)

| Step | Result |
|---|---|
| `./gradlew compileDebugKotlin` | ✅ |
| `./gradlew testDebugUnitTest` | ✅ **362 / 362** (Δ +30 vs U8's 332; 0 failures, 0 errors) |
| `./gradlew assembleDebug` + `installDebug` | ✅ **65 MB** APK on moto g stylus 2025 / Android 16 (Δ +31 MB vs U8's 34 MB) |

**APK size** exceeds plan §13.3's 36 MB U10 trigger — root cause was the `material-icons-extended` dependency. Logged as **U10-Δ-1** (R8 / per-icon vector copy in release polish).

---

## Final shipped behaviour (post-Δ-1)

### Sort
- **Two enums, no runtime coercion**: `SpoolSortKey { Material, Brand, Id, LastUsed }` and `FilamentSortKey { Material, Brand, Id }`. Filament-side `LastUsed` doesn't exist at the type level — Spoolman has `last_used` per spool, not per filament.
- **`SpoolmanSpool.last_used: String?`** modelled (Gson maps Spoolman's ISO-8601 `last_used` field).
- **Comparator**: `LastUsed Asc` = oldest first; `Desc` = most-recent first; **null `last_used` (never-consumed spools) always sorts last** regardless of direction.
- **UI**: per dropdown — full-width sort-key dropdown row + full-width segmented `Ascending / Descending` row directly underneath. No checkmark icon (segmented selection colour already conveys state). Generic `<T : Enum<T>>` `SettingsSortSection` accepts a `keys: Array<T>` + `keyLabel: (T) -> String`, so the spool section gets 4 options and the filament section gets 3 — no UI filter logic.

### Theme
- **2 states**: `ThemeOverride { Light, Dark }`. `System` removed.
- **Widget**: Material 3 `Switch` with sun/moon thumb icon (`ThemeToggleSwitch`). Lives **only** on the Settings TopAppBar (right side of the title). Removed from MainScreen TopAppBar.
- **Resolution**: `MainActivity.setContent` reads `settings.themeOverride == Dark` directly, applies via `SpoolPainterTheme(darkTheme, dynamicColor = true)`.

### Currency
- 3 segmented buttons: `$ Dollar` / `€ Euro` / `¤ Money` (the U+00A4 generic currency sign — labelled "Money" per user direction).
- `priceSuffix` propagates `Settings.currency.symbol` → `MainUiState.priceSuffix` → `FilamentForm.priceSuffix` → `MoreDetailsExpander.priceSuffix` (replaces the hard-coded `"$"`).

### Settings screen layout (final)
- URL field
- **Save** (full-width Button) — Test connection button removed per user direction
- **Refresh spool list** (full-width OutlinedButton)
- Spool list sort: dropdown (4 keys) + Asc/Desc segmented row
- Filament list sort: dropdown (3 keys) + Asc/Desc segmented row
- Currency: 3 segmented buttons
- TopAppBar: back arrow + theme toggle Switch

### Banner
- Derivation untouched; new test coverage covers BR-U9-24-style matrix (5 cases).

---

## Net file impact (post-Δ-1)

**Created (5 production + 5 test + 1 doc)**
- `app/src/main/java/com/spoolpainter/app/data/local/Settings.kt` (rewritten — Currency/SortKey enums)
- `app/src/main/java/com/spoolpainter/app/ui/components/SortComparators.kt`
- `app/src/main/java/com/spoolpainter/app/ui/components/ThemeToggleSwitch.kt`
- `app/src/main/java/com/spoolpainter/app/ui/screens/settings/SettingsSegmentedSection.kt`
- `app/src/main/java/com/spoolpainter/app/ui/screens/settings/SettingsSortSection.kt`
- `app/src/main/java/com/spoolpainter/app/ui/screens/settings/SortKeyLabels.kt`
- `app/src/test/java/com/spoolpainter/app/ui/components/SortComparatorTest.kt` (10 cases)
- `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelBannerTest.kt` (5 cases)
- `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelCurrencyTest.kt` (3 cases)
- `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelSortTest.kt` (2 cases)
- `app/src/test/java/com/spoolpainter/app/ui/screens/settings/SettingsViewModelTest.kt` (6 cases)
- `aidlc-docs/construction/u9-settings-theming-banner/code/u9-summary.md` (this file)

**Modified — production**
- `app/src/main/java/com/spoolpainter/app/data/local/Settings.kt` — `Currency` enum + `Settings(spoolSortKey/spoolSortDirection/filamentSortKey/filamentSortDirection/themeOverride/currency)` shape
- `app/src/main/java/com/spoolpainter/app/data/local/SettingsRepository.kt` — 6 setters
- `app/src/main/java/com/spoolpainter/app/data/local/SettingsSerializer.kt` — `Json { ignoreUnknownKeys = true; coerceInputValues = true }` so legacy `themeOverride: "System"` and old `sortOrder` payloads decode silently
- `app/src/main/java/com/spoolpainter/app/domain/models/SpoolmanModels.kt` — `SpoolmanSpool.last_used: String?`
- `app/src/main/java/com/spoolpainter/app/ui/screens/settings/SettingsUiState.kt` — sort/currency
- `app/src/main/java/com/spoolpainter/app/ui/screens/settings/SettingsViewModel.kt` — 8 handlers + standalone `themeOverride` flow + `onThemeToggled`; `onTestConnectionTapped` removed
- `app/src/main/java/com/spoolpainter/app/ui/screens/settings/SettingsScreen.kt` — full-width Save, no Test connection, two `SettingsSortSection`s, theme Switch on TopAppBar
- `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainUiState.kt` — `spoolSortKey/spoolSortDirection/filamentSortKey/filamentSortDirection/priceSuffix`
- `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainViewModel.kt` — `SortProjection` private data class projecting 4 sort fields + price suffix
- `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainScreen.kt` — `SpoolmanDropdown` accepts `SpoolSortKey × SortDirection` and uses `spoolComparator`; theme toggle absent (Settings only)
- `app/src/main/java/com/spoolpainter/app/ui/components/FilamentForm.kt` — plumbs `filamentSortKey/filamentSortDirection/priceSuffix`
- `app/src/main/java/com/spoolpainter/app/ui/components/FilamentSectionExpander.kt` — accepts + forwards `sortKey + sortDirection`
- `app/src/main/java/com/spoolpainter/app/ui/components/FilamentPicker.kt` — `filamentComparator(sortKey, sortDirection)` keyed on the pair
- `app/src/main/java/com/spoolpainter/app/ui/components/MoreDetailsExpander.kt` — `priceSuffix: String` parameter
- `app/src/main/java/com/spoolpainter/app/ui/activity/MainActivity.kt` — Hilt-injects `SettingsRepository`; `darkTheme = settings.themeOverride == Dark`
- `app/src/main/java/com/spoolpainter/app/ui/theme/Theme.kt` — `dynamicColor: Boolean = true`

**Modified — tests + support**
- `app/src/test/java/com/spoolpainter/app/data/local/SettingsRepositoryTest.kt` — 8 setter cases (split sort × 2, direction × 2, currency, theme, default, URL)
- `app/src/test/java/com/spoolpainter/app/support/FakeSettingsRepository.kt` — interface alignment
- `app/src/test/java/com/spoolpainter/app/support/FakeSpoolmanRepository.kt` — `connectivity: StateFlow<ConnectivityState>` + `setConnectivity(...)` for banner tests

**Modified — build**
- `gradle/libs.versions.toml` — `androidx-compose-material-icons-extended` library entry
- `app/build.gradle.kts` — `implementation(libs.androidx.compose.material.icons.extended)`

**Deleted**
- `app/src/main/java/com/spoolpainter/app/ui/components/ThemeCycleIconButton.kt` (replaced by `ThemeToggleSwitch`)
- `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelThemeCycleTest.kt` (theme moved entirely off MainViewModel)

---

## Mid-codegen + Δ-1 pivots

1. **`material-icons-extended` not on classpath** — plan §1.1 said it was; it wasn't. Added the dep. APK +30 MB. Logged as U10-Δ-1.
2. **Type inference on `compareBy(...)` inside `when (order) ->`** — Kotlin 2.0.21 couldn't pick an overload. Refactored `SortComparators.kt` to explicit `Comparator { a, b -> CI.compare(...) }` lambdas chained with `.then(...)`.
3. **Theme widget reframed**: 3-state cycle icon (FD spec) → `Switch` (Light↔Dark). User direction "2 in theme is fine" dropped `ThemeOverride.System`.
4. **Theme location reframed twice**: cycle icon on **both** TopAppBars → user clarified "settings only" → moved to Settings TopAppBar exclusively. `MainViewModel.themeOverride` + `onThemeToggled` removed.
5. **Sort schema reframed**: enum `SortOrder { Default, Alphabetical, MaterialThenColor }` → `SortKey × SortDirection` per dropdown → split into two enums (`SpoolSortKey` / `FilamentSortKey`) so filament-side `LastUsed` doesn't exist at the type level.
6. **`SettingsViewModel.onSortDirectionToggled` bug** — read `state.value` (`WhileSubscribed` flow) and saw the stale initial cached value. Fixed by reading `settings.settings.value` (the `Eagerly`-started repo flow). Later moot once the toggle handlers were replaced with explicit `onSortDirectionChanged(direction)` segmented-button handlers (rev4 UI).
7. **Sort UX iterations** rev1 (segmented 3-key buttons) → rev2 (key dropdown + sibling asc/desc IconButton) → rev3 (6-row combined menu) → rev4 (key dropdown + segmented Asc/Desc row). Final shape per "stacked rows" direction. Checkmark icons on segmented buttons suppressed (`icon = {}`) — colour state alone conveys selection.
8. **`SpoolmanSpool.last_used`** initially absent in v2 (and v1) — v1 sorted server-side via `?sort=` query string, never modelled the field. Added per "v1 had Last Used, v2 must too — no regressions" direction. Sort stays client-side (no `?sort=` query param) — matches v2's local-cache architecture.
9. **Test connection button removed** per user direction. `SpoolmanRepository.init` already runs `ensureExtraFieldsRegistered + refresh` on every URL bind, so the explicit button was redundant.
10. **Currency third option** labelled "$ Money" instead of "$ Generic" (per user direction). Symbol stays U+00A4.

---

## Brownfield invariants

- ✅ No `*_modified` / `*_new` / `*.bak` files. All edits in-place.
- ✅ No `MaterialDatabase` / `BrandDatabase` resurrection.
- ✅ No `*.proto` files.
- ✅ Zero production references to legacy `Settings.sortOrder` / `setSortOrder` / `SortOrder` enum (post-edit `rg` returns empty).
- ✅ `ThemeOverride.System` not referenced anywhere in production after Δ-1; removed cleanly.
- ✅ No new permissions.
- ✅ Inclusive language preserved.

---

## U10 follow-ups logged

- **U10-Δ-1**: APK size review. `material-icons-extended` adds ~30 MB. Options: R8 minify in release builds, or hand-copy the 4 icons (`KeyboardArrowDown/Up`, `BrightnessAuto/LightMode/DarkMode`, `Clear`, `Settings`) as vectors and drop the dep entirely. Release build polish.
- **U10-Δ-2**: Reframed — `last_used` now modelled. The remaining U10 ask is the legacy `sortOrder` JSON migration (one-time `JsonElement` rewrite in `SettingsSerializer.readFrom`) for users upgrading from a pre-U9 v2 build that still has `sortOrder` populated. `coerceInputValues` covers the enum-value drift but not key→key translation.
- **JDK 17 portability** — still required to build. Logged for U10.

---

## Manual verification checklist for U10

(Per Q-T2=B no formal U9 install gate; recorded here for next install-time iteration.)

- [ ] Spool sort: Material / Brand / ID / Last Used all reorder the dropdown correctly; Asc/Desc segmented row flips ordering live
- [ ] Filament sort: Material / Brand / ID (no Last Used option present)
- [ ] Sort independence: flipping spool sort doesn't change filament sort, and vice versa
- [ ] Last Used Desc puts most-recently-consumed spool first; never-consumed spools sort last
- [ ] Theme Switch on Settings TopAppBar: Light ↔ Dark applies live without activity recreate; persists across cold-start
- [ ] Currency segmented: `$ Dollar` / `€ Euro` / `¤ Money` — picking one flips the price suffix in `MoreDetailsExpander` everywhere
- [ ] Banner unchanged; appears only when URL is configured AND Spoolman unreachable
- [ ] Material You dynamic color visible on Android 12+ device
- [ ] No "Test connection" button on Settings; Save button is full-width
- [ ] TalkBack reads "Theme: Light (tap to switch to Dark)" / vice versa on the Switch

---

## Approval gate

Per `core-workflow.md` CONSTRUCTION → Per-Unit Loop → Code Generation:

🔧 **Request Changes** — modify / re-execute
✅ **Continue to Next Stage** — approve U9 close-out; open U9b per-unit loop
