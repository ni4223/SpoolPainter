# U9 — Code Generation Plan (Part 1)

**Stage**: CONSTRUCTION → Per-Unit Loop → Code Generation Part 1 (U9)
**Unit**: U9 — Settings + Theming + UI Shell (Sort split + Theme TopAppBar + Currency)
**Authored**: 2026-05-29
**Approval gate**: this plan must be approved before Code Gen Part 2 executes the checkboxes below.

**Inputs**:
- `aidlc-docs/construction/u9-settings-theming-banner/functional-design/{domain-entities,business-rules,business-logic-model,frontend-components}.md` (FD Part 2, re-locked + approved 2026-05-29 after sort-split + theme-TopAppBar revision)
- `aidlc-docs/construction/plans/u9-settings-theming-banner-functional-design-plan.md` Q-U9-1..Q-U9-13 ledger
- `aidlc-docs/inception/application-design/unit-of-work.md` §3-U9
- `aidlc-docs/inception/requirements/requirements.md` FR-9.1..FR-9.4, FR-10.2/3, FR-12.1/2, FR-13.1..4
- `aidlc-docs/inception/requirements/requirements-delta-banner-passive.md` (Q-U9-10=B; closes S-10.2 Retry AC)
- `aidlc-docs/inception/user-stories/stories.md` S-9.1..S-9.4, S-10.2, S-12.1, S-13.1/2

**Branch**: `v2`. Working tree before this plan: 2 commits ahead of `origin/v2` (U7 close-out `665b362` + U8 close-out `bcb7f0d`); doc-only deltas this session: U9 FD plan, four FD artefacts (revised), `requirements-delta-banner-passive.md`, `aidlc-state.md`, `audit.md`, this plan.

**Test count target**: U8 closed at **332 / 332**. After U9: **+20 cases ≈ 352 / 352**. Per-class breakdown in §11.

**Scope reminder**:
- ✅ Settings UI: 2 sort sections (`spoolSortOrder` + `filamentSortOrder`) + currency segmented section
- ✅ Theme moves OUT of Settings; new `ThemeCycleIconButton` on MainScreen TopAppBar (`System → Light → Dark → System` cycle)
- ✅ Comparator factory file `ui/components/SortComparators.kt`
- ✅ Banner — derivation already shipped; only test coverage extends
- ✅ Currency switcher ($ / € / ¤ U+00A4)
- ✅ `dynamicColor` default flips `false → true`
- ✅ `MainActivity` Hilt-injects `SettingsRepository`; resolves `darkTheme` from `themeOverride` at `setContent`

**Out-of-scope guards** (re-stated):
- ❌ Branding restore → U9b
- ❌ Main UI parity audit vs v1 → U9b
- ❌ IME-aware snackbar host → U9b
- ❌ "Other" / "Color Wheel" affordance polish → U9b
- ❌ UI-01 Spoolman dropdown styling drift → U9b
- ❌ UI-02 / UI-05 / UI-07 → U9b or U10
- ❌ NFR-5 release-build log stripping → U10
- ❌ Vendor-key Settings → U12 / v2.1
- ❌ Custom-entry management UI → post-v2.0
- ❌ APK size review / JDK 17 portability → U10
- ❌ U9 milestone install gate (Q-T2=B → covered by U10 manual matrix)
- ❌ Legacy `sortOrder` JSON migration → U10 (forward-compat path documented in `domain-entities.md` §1.2.1)
- ❌ Long-press → menu (System/Light/Dark explicit pick) → post-v2.0

---

## §1 — Build dependencies

- [ ] 1.1 No new third-party dependencies. `material-icons-extended` already on classpath via U1 / U6a; `androidx.lifecycle.runtime.compose.collectAsStateWithLifecycle` already used in `MainScreen.kt:62`. `kotlinx-serialization-json` already wired (covers the additive `Currency` enum). No `libs.versions.toml` change.
- [ ] 1.2 No `app/build.gradle.kts` change.
- [ ] 1.3 No new permissions.

---

## §2 — Domain layer (FD `domain-entities.md` §1)

### 2.1 Modify `app/src/main/java/com/spoolpainter/app/data/local/Settings.kt`

- [ ] 2.1.1 Add `Currency` enum at end of file:
  ```kotlin
  @Serializable
  enum class Currency(val symbol: String) {
      Dollar("$"),
      Euro("€"),
      Generic("¤"),  // U+00A4
  }
  ```
- [ ] 2.1.2 Modify `Settings` data class — drop `sortOrder` field; add three new fields. Final shape:
  ```kotlin
  @Serializable
  data class Settings(
      val url: String = "",
      val spoolSortOrder: SortOrder = SortOrder.Default,
      val filamentSortOrder: SortOrder = SortOrder.Default,
      val themeOverride: ThemeOverride = ThemeOverride.System,
      val currency: Currency = Currency.Dollar,
  )
  ```
- [ ] 2.1.3 `SortOrder` and `ThemeOverride` enums unchanged.

### 2.2 Modify `app/src/main/java/com/spoolpainter/app/data/local/SettingsSerializer.kt`

- [ ] 2.2.1 Verify `Json { ignoreUnknownKeys = true }` is already configured. Currently the file uses `Json` builder; confirm `ignoreUnknownKeys = true` flag is set so the legacy `sortOrder` key is silently dropped on read of an upgraded payload. If the flag is missing, add it. **Read-only check first; only add if absent.**
- [ ] 2.2.2 `defaultValue` is unchanged in shape — still `Settings()`. New defaults flow through the constructor automatically.

### 2.3 Modify `app/src/main/java/com/spoolpainter/app/data/local/SettingsRepository.kt`

- [ ] 2.3.1 Replace `setSortOrder` with `setSpoolSortOrder` on the interface; add `setFilamentSortOrder`; add `setCurrency`. Final interface:
  ```kotlin
  interface SettingsRepository {
      val settings: StateFlow<Settings>
      suspend fun setUrl(url: String)
      suspend fun setSpoolSortOrder(order: SortOrder)
      suspend fun setFilamentSortOrder(order: SortOrder)
      suspend fun setThemeOverride(theme: ThemeOverride)
      suspend fun setCurrency(currency: Currency)
  }
  ```
- [ ] 2.3.2 Mirror the four setter implementations in `SettingsRepositoryImpl` (each `store.updateData { it.copy(...) }`).
- [ ] 2.3.3 Search-and-replace any in-tree caller of `setSortOrder` (will be replaced in §3 + §4 below — settings VM was the only call site).

---

## §3 — Sort comparator factory (NEW file)

### 3.1 Create `app/src/main/java/com/spoolpainter/app/ui/components/SortComparators.kt`

- [ ] 3.1.1 Authored verbatim per FD `domain-entities.md` §2.1. Two top-level functions: `spoolComparator(SortOrder): Comparator<SpoolmanSpool>` and `filamentComparator(SortOrder): Comparator<SpoolmanFilament>`. Two private helper functions `spoolSortKey` and `filamentSortKey` for the `Alphabetical` ordering.
- [ ] 3.1.2 No DI; pure top-level functions. Imports: `data.local.SortOrder`, `domain.models.SpoolmanSpool`, `domain.models.SpoolmanFilament`.

---

## §4 — Theme cycle icon (NEW file)

### 4.1 Create `app/src/main/java/com/spoolpainter/app/ui/components/ThemeCycleIconButton.kt`

- [ ] 4.1.1 Stateless `@Composable` per FD `frontend-components.md` §2.4. Maps `ThemeOverride` → `(ImageVector, String)` via `when`:
  - `System` → `Icons.Outlined.BrightnessAuto` + "Theme: System (tap to switch to Light)"
  - `Light` → `Icons.Outlined.LightMode` + "Theme: Light (tap to switch to Dark)"
  - `Dark` → `Icons.Outlined.DarkMode` + "Theme: Dark (tap to switch to System)"
- [ ] 4.1.2 Renders `IconButton(onClick = onCycle, modifier = modifier.testTag("main-theme-cycle"))` with the resolved icon + `contentDescription`.
- [ ] 4.1.3 Imports: prefer `androidx.compose.material.icons.outlined.{BrightnessAuto, LightMode, DarkMode}`. **Mid-codegen fallback** (per U8 precedent): if any outlined variant resolves to a missing-symbol error at compileDebugKotlin, substitute `androidx.compose.material.icons.filled.{BrightnessAuto, LightMode, DarkMode}` — both sets ship in `material-icons-extended`.

---

## §5 — Settings UI (FD `frontend-components.md` §2)

### 5.1 Modify `app/src/main/java/com/spoolpainter/app/ui/screens/settings/SettingsUiState.kt`

- [ ] 5.1.1 Drop `themeOverride` field. Replace `sortOrder` with `spoolSortOrder` + `filamentSortOrder`. Add `currency`. Final shape:
  ```kotlin
  data class SettingsUiState(
      val url: String = "",
      val spoolSortOrder: SortOrder = SortOrder.Default,
      val filamentSortOrder: SortOrder = SortOrder.Default,
      val currency: Currency = Currency.Dollar,
  )
  ```
- [ ] 5.1.2 Imports: drop `ThemeOverride`; add `Currency`.

### 5.2 Modify `app/src/main/java/com/spoolpainter/app/ui/screens/settings/SettingsViewModel.kt`

- [ ] 5.2.1 Update state mapping: `SettingsUiState(it.url, it.spoolSortOrder, it.filamentSortOrder, it.currency)`.
- [ ] 5.2.2 Drop `onThemeOverrideChanged` (handler moved to `MainViewModel`).
- [ ] 5.2.3 Add three new handlers (BR-U9-11 — no snackbar emission):
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

### 5.3 Create `app/src/main/java/com/spoolpainter/app/ui/screens/settings/SettingsSegmentedSection.kt`

- [ ] 5.3.1 New file per FD `frontend-components.md` §2.2. Internal generic helper composable `SettingsSegmentedSection<T>(label, options, selected, onSelect, testTag)`. Implementation per FD listing — `SingleChoiceSegmentedButtonRow` with one `SegmentedButton` per option; tap-on-selected is a no-op.
- [ ] 5.3.2 Per-option testTag derives from `value.toString().lowercase()` (e.g. `settings-spool-sort-order-default`).

### 5.4 Modify `app/src/main/java/com/spoolpainter/app/ui/screens/settings/SettingsScreen.kt`

- [ ] 5.4.1 Below the existing "Refresh spool list" `OutlinedButton`, replace the placeholder `Text("Sort order, theme, and full banner UI land in U9.")` block with **three** `SettingsSegmentedSection` calls (FD listing in §2.1):
  - "Spool list sort" → spoolSortOrder + 3 enum options
  - "Filament list sort" → filamentSortOrder + 3 enum options
  - "Currency" → currency + 3 Currency enum options (labels include the symbol — `"$ Dollar"`, `"€ Euro"`, `"¤ Generic"`)
- [ ] 5.4.2 No theme section. (Theme moved to MainScreen TopAppBar — §6 below.)
- [ ] 5.4.3 Imports: add `SortOrder`, `Currency`, the `SettingsSegmentedSection` helper. Drop `ThemeOverride` if no longer referenced.

---

## §6 — MainScreen TopAppBar + theme cycle (FD `frontend-components.md` §1b, §2.5)

### 6.1 Modify `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainViewModel.kt`

- [ ] 6.1.1 Add `themeOverride: StateFlow<ThemeOverride>` mapped from `settings.settings.map { it.themeOverride }.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeOverride.System)` per FD §1.5b.
- [ ] 6.1.2 Add `onThemeCycleTapped()` handler that computes the next state via `when (themeOverride.value) { System -> Light; Light -> Dark; Dark -> System }` and calls `settings.setThemeOverride(next)` inside `viewModelScope.launch { ... }`.
- [ ] 6.1.3 Extend the existing `MainUiState`-projecting `combine` (currently at `MainViewModel.kt:165-187`) to also project `spoolSortOrder` + `filamentSortOrder` + `priceSuffix` (derived from `Settings.currency.symbol`) into `MainUiState`. Keep the banner derivation logic untouched.
- [ ] 6.1.4 No changes to `onSettingsTapped`, `onSpoolSelected`, etc.

### 6.2 Modify `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainUiState.kt`

- [ ] 6.2.1 Add three fields per FD §1.5:
  ```kotlin
  val spoolSortOrder: SortOrder = SortOrder.Default,
  val filamentSortOrder: SortOrder = SortOrder.Default,
  val priceSuffix: String = "$",
  ```
- [ ] 6.2.2 Defaults preserve current visual behavior (id-descending sorts; `$` price suffix).

### 6.3 Modify `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainScreen.kt`

- [ ] 6.3.1 `MainScreen` collects `themeOverride` flow:
  ```kotlin
  val themeOverride by viewModel.themeOverride.collectAsStateWithLifecycle()
  ```
- [ ] 6.3.2 Update `Scaffold.topBar` callsite to pass `themeOverride` + `onThemeCycleTapped` into `MainTopBar`.
- [ ] 6.3.3 Modify private `MainTopBar` composable per FD `frontend-components.md` §2.5 — gains two new params (`themeOverride: ThemeOverride`, `onThemeCycleTapped: () -> Unit`); renders `ThemeCycleIconButton(current = themeOverride, onCycle = onThemeCycleTapped)` BEFORE the existing Settings gear `IconButton`.
- [ ] 6.3.4 Modify `SpoolmanDropdown` (line 272-302): signature gains `sortOrder: SortOrder`. Replace `.sortedByDescending { it.id ?: Int.MIN_VALUE }` (line 285) with `.sortedWith(spoolComparator(sortOrder))`. Caller passes `state.spoolSortOrder`.
- [ ] 6.3.5 Add `import com.spoolpainter.app.ui.components.ThemeCycleIconButton` + `import com.spoolpainter.app.ui.components.spoolComparator` + `import com.spoolpainter.app.data.local.ThemeOverride` (if not already present).

### 6.4 Modify `app/src/main/java/com/spoolpainter/app/ui/components/FilamentPicker.kt`

- [ ] 6.4.1 Function signature gains `sortOrder: SortOrder` parameter (after `filaments`).
- [ ] 6.4.2 Replace the `remember(filaments) { filaments.sortedBy { it.displayString().lowercase() } }` (line 45) with `remember(filaments, sortOrder) { filaments.sortedWith(filamentComparator(sortOrder)) }`.
- [ ] 6.4.3 Imports: add `com.spoolpainter.app.data.local.SortOrder` + `com.spoolpainter.app.ui.components.filamentComparator` (same package; no import needed if `filamentComparator` is in the same package).

### 6.5 Modify `app/src/main/java/com/spoolpainter/app/ui/components/MoreDetailsExpander.kt`

- [ ] 6.5.1 Function signature gains `priceSuffix: String` parameter.
- [ ] 6.5.2 Replace hard-coded `suffix = "$"` (line 107) with `suffix = priceSuffix`.

### 6.6 Modify `app/src/main/java/com/spoolpainter/app/ui/components/FilamentForm.kt`

- [ ] 6.6.1 Function signature gains `filamentSortOrder: SortOrder` + `priceSuffix: String` parameters.
- [ ] 6.6.2 Pass `filamentSortOrder` through to `FilamentPicker` callsite.
- [ ] 6.6.3 Pass `priceSuffix` through to `MoreDetailsExpander` callsite.
- [ ] 6.6.4 `MainScreen` callsite passes `state.filamentSortOrder` + `state.priceSuffix` from `MainUiState`.

---

## §7 — MainActivity Hilt injection + theme resolution (FD `domain-entities.md` §5)

### 7.1 Modify `app/src/main/java/com/spoolpainter/app/ui/activity/MainActivity.kt`

- [ ] 7.1.1 Add `@Inject lateinit var settingsRepository: SettingsRepository`.
- [ ] 7.1.2 Inside `setContent { }`, before the existing `var showSettings by rememberSaveable…` block:
  ```kotlin
  val settings by settingsRepository.settings.collectAsStateWithLifecycle()
  val darkTheme = when (settings.themeOverride) {
      ThemeOverride.System -> isSystemInDarkTheme()
      ThemeOverride.Light -> false
      ThemeOverride.Dark -> true
  }
  ```
- [ ] 7.1.3 Replace `SpoolPainterTheme {` with `SpoolPainterTheme(darkTheme = darkTheme, dynamicColor = true) {`.
- [ ] 7.1.4 Imports: `SettingsRepository`, `ThemeOverride`, `isSystemInDarkTheme`, `collectAsStateWithLifecycle`, `getValue`.
- [ ] 7.1.5 No changes to `onCreate` flow shape, `onNewIntent`, `onResume`, `onPause`, or `tryDispatchNfcIntent`.

### 7.2 Modify `app/src/main/java/com/spoolpainter/app/ui/theme/Theme.kt`

- [ ] 7.2.1 Flip `dynamicColor: Boolean = false` → `dynamicColor: Boolean = true` (Q-U9-8=A).
- [ ] 7.2.2 Remove the comment line "// Disable dynamic colors to use our custom theme" if present.
- [ ] 7.2.3 Body otherwise unchanged — pre-Android-12 fallback (`LightColors`/`DarkColors`) remains gated by the existing `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` branch.

---

## §8 — Banner — no UI change (FD `business-rules.md` §5)

- [ ] 8.1 `MainScreen.BannerSlot` (line 213) is unchanged. No code edits to the banner card itself.
- [ ] 8.2 Banner derivation in `MainViewModel.kt:165-187` is unchanged in behavior (already shipped). U9 only adds test coverage in §11.

---

## §9 — Requirements delta (already authored)

- [ ] 9.1 `aidlc-docs/inception/requirements/requirements-delta-banner-passive.md` was authored as part of FD Part 2. No code-gen-time work — the delta is a doc-only artefact; this checkbox just acknowledges the file exists.

---

## §10 — Existing tests touched

### 10.1 Modify `app/src/test/java/com/spoolpainter/app/data/local/SettingsRepositoryTest.kt`

- [ ] 10.1.1 Existing assertions on `first.sortOrder` (line 51, 70, 72) reference the renamed field — update to `first.spoolSortOrder` and split the case into two:
  - `spoolSortOrder defaults to Default + persists when set`
  - `filamentSortOrder defaults to Default + persists when set` (NEW case)
- [ ] 10.1.2 Existing `themeOverride` round-trip case (line 80, 82) is unchanged — `setThemeOverride` is still on the interface.
- [ ] 10.1.3 Add NEW `setCurrency` round-trip case: defaults to `Currency.Dollar`; updates to `Currency.Euro`; updates to `Currency.Generic`.

### 10.2 Modify `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelTest.kt`

- [ ] 10.2.1 Constructor / fakes already accept `SettingsRepository`; the existing fake-settings setup needs: drop the legacy `setSortOrder` reference if any; add `setSpoolSortOrder` / `setFilamentSortOrder` / `setCurrency` to the fake's stub list (relaxed-mockk should handle this without changes — verify).
- [ ] 10.2.2 No new test cases here; the existing banner / read / write cases continue passing as-is.

### 10.3 Audit other callers of `SettingsRepository.setSortOrder`

- [ ] 10.3.1 Run `rg "setSortOrder|\.sortOrder\b" app/src` and update every reference to use the appropriate split field. Expected sites: `SettingsViewModel` (handler), `SettingsRepositoryTest`, `SettingsRepositoryImpl` (production), and any test fakes. **No production callers outside Settings exist today** — the legacy `state.sortOrder` is not read anywhere else (verified via the FD plan §2.1.1 read-through).

---

## §11 — New tests (FD `frontend-components.md` §7)

### 11.1 Modify `app/src/test/java/com/spoolpainter/app/ui/screens/settings/SettingsViewModelTest.kt`

If file doesn't exist, create it. Cases (3 new):

- [ ] 11.1.1 `onSpoolSortOrderChanged invokes setSpoolSortOrder on repository`
- [ ] 11.1.2 `onFilamentSortOrderChanged invokes setFilamentSortOrder on repository`
- [ ] 11.1.3 `onCurrencyChanged invokes setCurrency on repository`

(Drops `onThemeOverrideChanged` case if present — handler moved.)

### 11.2 Create `app/src/test/java/com/spoolpainter/app/ui/components/SortComparatorTest.kt`

- [ ] 11.2.1 Cases (6 total — 3 per comparator):
  - `spoolComparator Default sorts by id desc`
  - `spoolComparator Alphabetical sorts case-insensitive by vendor·name·id`
  - `spoolComparator MaterialThenColor sorts by material then color_hex with id-desc tiebreak`
  - `filamentComparator Default sorts by id desc`
  - `filamentComparator Alphabetical sorts case-insensitive by vendor·name·material`
  - `filamentComparator MaterialThenColor sorts by material then color_hex with id-desc tiebreak`
- [ ] 11.2.2 Each test builds a hand-crafted `List<SpoolmanSpool>` / `List<SpoolmanFilament>` and asserts ordering after `.sortedWith(comparator)`.

### 11.3 Create `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelBannerTest.kt`

- [ ] 11.3.1 Cases (3 derivation + 2 transition = 5 total):
  - `(url=blank, Unknown) → Hidden`
  - `(url=set, Reachable) → Hidden`
  - `(url=set, Unreachable("dns")) → Offline("dns")`
  - URL configured while connectivity is Unreachable → banner appears mid-flow
  - URL cleared while connectivity is Unreachable → banner disappears mid-flow
- [ ] 11.3.2 Uses the existing fakes pattern from `MainViewModelTest`.

### 11.4 Create `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelCurrencyTest.kt`

- [ ] 11.4.1 Cases (3 total):
  - `priceSuffix defaults to "$" when Settings.currency = Dollar`
  - `priceSuffix flips to "€" when Settings.currency changes to Euro`
  - `priceSuffix flips to "¤" when Settings.currency changes to Generic`
- [ ] 11.4.2 Drives the fake `SettingsRepository.setCurrency`; observes `MainUiState.priceSuffix`.

### 11.5 Create `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelThemeCycleTest.kt`

- [ ] 11.5.1 Cases (2 total — Q-U9-13=A cycle):
  - `themeOverride flow tracks Settings.themeOverride`
  - `onThemeCycleTapped advances System → Light → Dark → System` (single test asserts the full 4-step cycle by tapping three times and reading the `themeOverride.value` at each step)

### 11.6 Create `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelSortTest.kt`

- [ ] 11.6.1 Cases (1 — sort independence per BR-U9-2a/2b):
  - `flipping spoolSortOrder does NOT change filamentSortOrder, and vice versa` (sets `spoolSortOrder = Alphabetical`, asserts `MainUiState.filamentSortOrder` still `Default`; then sets `filamentSortOrder = MaterialThenColor`, asserts `MainUiState.spoolSortOrder` still `Alphabetical`).

### 11.7 Test count summary

| Class | Δ |
|---|---|
| `SettingsRepositoryTest` | +2 (new `filamentSortOrder` round-trip + `setCurrency` round-trip; existing `sortOrder` case becomes `spoolSortOrder`) |
| `SettingsViewModelTest` | +3 (sort×2 + currency); −1 if pre-existing `onThemeOverrideChanged` test removed (audit shows none) → net +3 |
| `SortComparatorTest` (NEW) | +6 |
| `MainViewModelBannerTest` (NEW) | +5 |
| `MainViewModelCurrencyTest` (NEW) | +3 |
| `MainViewModelThemeCycleTest` (NEW) | +2 |
| `MainViewModelSortTest` (NEW) | +1 |
| **Net** | **+22 cases** |

Final running total target: **332 + 22 ≈ 354 / 354**. (Slightly above the FD §7 estimate of 352 — the `SettingsRepositoryTest` rename produced a +2 instead of +1 once the existing `sortOrder` case was split. Net upward — non-blocking; recorded in close-out summary.)

---

## §12 — Documentation

### 12.1 Create `aidlc-docs/construction/u9-settings-theming-banner/code/u9-summary.md`

- [ ] 12.1.1 New file. Captures: net file changes (created / modified / deleted with line counts), test-count delta, mid-codegen pivots (e.g. icon import substitution), FD adherence, deferrals to U9b/U10, verification log (compileDebugKotlin / testDebugUnitTest / assembleDebug results + APK size + comparison vs U8 baseline).
- [ ] 12.1.2 Manual verification checklist for U10 (per Q-T2=B — no U9 install gate; manual matrix lives in U10):
  - Spool sort persists across app restart; flipping sort reorders the dropdown
  - Filament sort persists independently; flipping spool sort does NOT change filament order, and vice versa
  - TopAppBar cycle icon flips System → Light → Dark → System; theme applies live without activity recreate
  - Cold-start respects persisted `themeOverride` (no flicker)
  - Material You dynamic color visible on Android 12+ device
  - Pre-Android-12 fallback (custom palette) on emulator API ≤ 30
  - Currency segmented section persists; price suffix flips in `MoreDetailsExpander` everywhere
  - Banner copy + appearance unchanged from U8 baseline
  - TalkBack reads "Theme: <Current> (tap to switch to <Next>)" on the cycle icon

---

## §13 — Verification

- [ ] 13.1 `./gradlew compileDebugKotlin` ✅
- [ ] 13.2 `./gradlew testDebugUnitTest` ✅ — running total target: **354 / 354** (existing 332 must pass unchanged; +22 new).
- [ ] 13.3 `./gradlew assembleDebug` ✅ — APK size monitored; flagged for U10 if >36 MB (current baseline 34 MB after U8).
- [ ] 13.4 No U9 milestone install gate (Q-T2=B). Manual NFC verification deferred to U10. Theme + sort behaviour can be sanity-checked on device during the next install-time iteration but is not a gate.

---

## §14 — Brownfield invariants

- [ ] 14.1 No `*_modified` / `*_new` / `*.bak` files. All edits are in-place.
- [ ] 14.2 No `MaterialDatabase` / `BrandDatabase` resurrection. Those were deleted at U8.
- [ ] 14.3 No new `*.proto` files; no protobuf-gradle-plugin. (DataStore stays JSON-via-kotlinx-serialization.)
- [ ] 14.4 No production references to legacy `Settings.sortOrder` after this unit. Verify with `rg "settings\.sortOrder|setSortOrder|Settings\.sortOrder|state\.sortOrder" app/src/main` (zero hits expected; audit log captures any false positives in test scaffolding).
- [ ] 14.5 No new permissions; no new third-party deps.
- [ ] 14.6 Inclusive language: no `master` / `slave` / `whitelist` / `blacklist` introduced. (Existing codebase already conforms; check holds.)

---

## §15 — Net file impact

| Path | Action | Reason |
|---|---|---|
| `app/src/main/java/com/spoolpainter/app/data/local/Settings.kt` | Modify | Add `Currency` enum; rename `sortOrder` → `spoolSortOrder`; add `filamentSortOrder` + `currency` |
| `app/src/main/java/com/spoolpainter/app/data/local/SettingsRepository.kt` | Modify | Rename setter; add `setFilamentSortOrder` + `setCurrency` |
| `app/src/main/java/com/spoolpainter/app/data/local/SettingsSerializer.kt` | Verify (no-op or one-line flag add) | Confirm `ignoreUnknownKeys = true` |
| `app/src/main/java/com/spoolpainter/app/ui/screens/settings/SettingsUiState.kt` | Modify | Drop `themeOverride`; split `sortOrder`; add `currency` |
| `app/src/main/java/com/spoolpainter/app/ui/screens/settings/SettingsViewModel.kt` | Modify | Drop `onThemeOverrideChanged`; add 3 handlers; update state mapping |
| `app/src/main/java/com/spoolpainter/app/ui/screens/settings/SettingsScreen.kt` | Modify | Add 3 segmented sections; remove placeholder |
| `app/src/main/java/com/spoolpainter/app/ui/screens/settings/SettingsSegmentedSection.kt` | **NEW** | Generic helper composable |
| `app/src/main/java/com/spoolpainter/app/ui/components/SortComparators.kt` | **NEW** | Comparator factory |
| `app/src/main/java/com/spoolpainter/app/ui/components/ThemeCycleIconButton.kt` | **NEW** | TopAppBar cycle icon |
| `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainUiState.kt` | Modify | Add `spoolSortOrder` + `filamentSortOrder` + `priceSuffix` |
| `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainViewModel.kt` | Modify | `themeOverride` standalone flow + `onThemeCycleTapped`; settings combine extension |
| `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainScreen.kt` | Modify | TopBar gains cycle icon; `SpoolmanDropdown` wires `spoolComparator`; pass `priceSuffix` + `filamentSortOrder` to form |
| `app/src/main/java/com/spoolpainter/app/ui/components/FilamentPicker.kt` | Modify | Accept `sortOrder`; wire `filamentComparator` |
| `app/src/main/java/com/spoolpainter/app/ui/components/MoreDetailsExpander.kt` | Modify | `priceSuffix` parameter |
| `app/src/main/java/com/spoolpainter/app/ui/components/FilamentForm.kt` | Modify | Plumb `filamentSortOrder` + `priceSuffix` through |
| `app/src/main/java/com/spoolpainter/app/ui/activity/MainActivity.kt` | Modify | Inject `SettingsRepository`; resolve `darkTheme`; flip `dynamicColor=true` |
| `app/src/main/java/com/spoolpainter/app/ui/theme/Theme.kt` | Modify | `dynamicColor` default `false` → `true` |
| `app/src/test/java/com/spoolpainter/app/data/local/SettingsRepositoryTest.kt` | Modify | Rename + split sort case; add currency case |
| `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelTest.kt` | Modify | Update fakes |
| `app/src/test/java/com/spoolpainter/app/ui/screens/settings/SettingsViewModelTest.kt` | Modify (or NEW if absent) | 3 new handler tests |
| `app/src/test/java/com/spoolpainter/app/ui/components/SortComparatorTest.kt` | **NEW** | 6 cases |
| `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelBannerTest.kt` | **NEW** | 5 cases |
| `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelCurrencyTest.kt` | **NEW** | 3 cases |
| `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelThemeCycleTest.kt` | **NEW** | 2 cases |
| `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelSortTest.kt` | **NEW** | 1 case |
| `aidlc-docs/construction/u9-settings-theming-banner/code/u9-summary.md` | **NEW** | Per-unit summary |

**Net**: 9 created (3 production + 5 test + 1 doc) / 13 modified production + 2 modified test / 0 deleted.

---

## §16 — Approval gate

This plan is **Code Generation Part 1 (Planning)**. Per `core-workflow.md` CONSTRUCTION → Per-Unit Loop → Code Generation, the user must approve this plan before Part 2 executes the checkboxes above.

**Standardised 2-option gate** (no emergent navigation):
- 🔧 **Request Changes** — modify the plan
- ✅ **Continue to Next Stage** — approve plan; open Code Gen Part 2 (Generation)
