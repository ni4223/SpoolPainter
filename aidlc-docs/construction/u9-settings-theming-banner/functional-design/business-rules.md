# U9 — Business Rules

**Stage**: CONSTRUCTION → Per-Unit Loop → Functional Design → Business Rules
**Unit**: U9 — Settings + Theming + UI Shell
**Locked**: 2026-05-29 (revised same day per user direction — theme moves to TopAppBar; sort splits into spool + filament)

Source plan: `aidlc-docs/construction/plans/u9-settings-theming-banner-functional-design-plan.md`. All Q-U9-1..Q-U9-13 resolved via "go go go" (My pick defaults).

---

## §1. Settings persistence (FR-9.1, FR-9.2, FR-9.3, FR-9.4 — currency)

**BR-U9-1** (FR-9.1) — `Settings.url` is the single source of truth for Spoolman base URL. Persisted via `SettingsRepository.setUrl`. Already shipped at U5; U9 does not modify URL handling.

**BR-U9-2a** (FR-9.2 — split) — `Settings.spoolSortOrder` is the single source of truth for the **spool dropdown** ordering. Persisted via `SettingsRepository.setSpoolSortOrder`. Default `SortOrder.Default` (id-descending — newest first; matches v1 + U6/U7/U8 behavior).

**BR-U9-2b** (FR-9.2 — split, NEW) — `Settings.filamentSortOrder` is the single source of truth for the **filament picker** ordering. Persisted via `SettingsRepository.setFilamentSortOrder`. Default `SortOrder.Default` (id-descending). The two sort fields are **independent** — changing one does not change the other.

**BR-U9-3** (FR-9.3) — `Settings.themeOverride` is the single source of truth for app theme mode. Persisted via `SettingsRepository.setThemeOverride`. Default `ThemeOverride.System` (follow OS dark-mode setting). **Setter call site (revised)**: invoked by `MainViewModel.onThemeCycleTapped()` from the MainScreen TopAppBar's cycle icon — no longer surfaced on `SettingsScreen` (Q-U9-13=A).

**BR-U9-4** (FR-9.4 — NEW) — `Settings.currency` is the single source of truth for the currency symbol shown in price fields. Persisted via `SettingsRepository.setCurrency`. Default `Currency.Dollar` (symbol `$`) — preserves v2.0 pre-U9 visual behavior; no migration code needed (kotlinx-serialization fills missing JSON field with default on read).

**BR-U9-5** — All five setters (`setUrl`, `setSpoolSortOrder`, `setFilamentSortOrder`, `setThemeOverride`, `setCurrency`) write directly to `DataStore<Settings>` via `store.updateData { it.copy(...) }`. No optimistic-UI layer; observers see the update via `settings: StateFlow<Settings>` after persistence completes. Matches the existing pattern in `SettingsRepositoryImpl.kt:25-37`.

**BR-U9-6** — `SettingsSerializer` is unchanged. `Currency` enum is `@Serializable` via name-based encoding (matching `SortOrder` / `ThemeOverride`); old serialised payloads without the `currency` / `spoolSortOrder` / `filamentSortOrder` keys decode to defaults. The legacy `sortOrder` key is silently dropped by `Json { ignoreUnknownKeys = true }`. Net effect on upgrade: prior sort preference resets to `Default` for both dropdowns. Migration carve-out documented in `domain-entities.md` §1.2.1 — deferred to U10.

---

## §2. Settings UI (S-9.1, S-9.2, S-9.4)

**BR-U9-7** (Q-U9-4=A) — All settings controls rendered on `SettingsScreen` use `SingleChoiceSegmentedButtonRow`. **Theme is no longer rendered on `SettingsScreen`** (Q-U9-13=A — moved to MainScreen TopAppBar). Section ordering (top-to-bottom):

1. URL (existing — OutlinedTextField + Save/Test/Refresh)
2. Spool list sort (NEW — `spoolSortOrder`)
3. Filament list sort (NEW — `filamentSortOrder`)
4. Currency (NEW)

**BR-U9-8** (Q-U9-2 partial) — Sort-order labels match the **enum**, not S-9.2 story copy. Shown labels: `Default` / `Alphabetical` / `Material then Color` (same labels for both spool and filament sections). Story-copy reconciliation logged for U10 / post-v2.0.

**BR-U9-9** — **Reserved (was Theme labels — moved to BR-U9-19a per Q-U9-13=A)**. Intentionally left blank to avoid renumbering BR references in dependent docs.

**BR-U9-10** — Currency labels render the symbol + name: `$ Dollar` / `€ Euro` / `¤ Generic`. Symbol-first ordering aids recognition; readable both for sighted users and screen readers (TalkBack reads "Dollar sign Dollar").

**BR-U9-11** (Q-U9-6=A) — Settings changes (spool sort, filament sort, currency) **do not** emit confirmation snackbars. The segmented-button selection state is its own confirmation; URL save still emits "URL saved" because the text field doesn't visually "stick" until submitted (asymmetric — preserved). The MainScreen TopAppBar theme cycle also does **not** emit a snackbar — the icon's own state-flip + the colorScheme swap are the visual confirmation (BR-U9-19c).

**BR-U9-12** — Settings UI does **not** expose a "Reset to defaults" / "Wipe all settings" affordance. Single-user app; uninstall is the wipe.

---

## §3. Sort comparator wiring (FR-9.2 — extended, Q-U9-1 revised)

**BR-U9-13a** (revised — sort split) — `MainScreen.SpoolmanDropdown` (line 283) reads `state.spoolSortOrder` and applies `spoolComparator(state.spoolSortOrder)` from `ui/components/SortComparators.kt`. `state.spoolSortOrder` is sourced from `MainUiState`.

**BR-U9-13b** (NEW — sort split) — `FilamentPicker` (line 45) reads `sortOrder: SortOrder` parameter (passed by caller `FilamentForm` / `MainScreen`) and applies `filamentComparator(sortOrder)`. Caller passes `state.filamentSortOrder` — independent of the spool sort.

**BR-U9-14** — Comparator semantics (unchanged from prior revision; both sort fields use the same enum):

| `SortOrder` | Spool order | Filament order |
|---|---|---|
| `Default` | `id` descending (newest first) | `id` descending |
| `Alphabetical` | `vendor.name → filament.name → id` joined string, case-insensitive | `vendor.name → filament.name → material` joined, case-insensitive |
| `MaterialThenColor` | `material` (case-insensitive) → `color_hex` → `id` desc tiebreaker | same |

**BR-U9-15** — `SpoolmanDropdown` continues to filter `archived` spools from the visible list **before** sorting (existing behavior at `MainScreen.kt:284`). Move-on-bind callers still observe the archived spools through the unfiltered cache; this rule applies to **rendering** only.

**BR-U9-16** — Default-mode behavior change in filament picker: today `FilamentPicker.kt:45` sorts by display-string ascending; U9 flips Default to id-descending for parity with the spool dropdown. Documented in U9 close-out summary as a deliberate behavior change (FR-9.2 spec wins).

**BR-U9-17** — Sort changes propagate via `StateFlow` recomposition, not list re-fetch. No Spoolman API round-trip on sort change. Both sort fields share this rule independently.

---

## §4. Theme override + dynamic color (FR-12.1, FR-12.2, S-12.1, S-9.3)

**BR-U9-18** (Q-U9-5=A) — `MainActivity.setContent` is the **only** site that reads `settings.themeOverride` for `darkTheme` resolution. Resolution:

| `ThemeOverride` | `darkTheme` |
|---|---|
| `System` | `isSystemInDarkTheme()` |
| `Light` | `false` |
| `Dark` | `true` |

`SpoolPainterTheme(darkTheme, dynamicColor = true)` consumes the resolved boolean. Theme function stays pure — no `SettingsRepository` injection inside `Theme.kt`.

**BR-U9-19** (Q-U9-8=A) — `dynamicColor = true` always. On Android 12+ (`Build.VERSION.SDK_INT >= S`), Material You wallpaper-derived palettes apply. On pre-Android-12, the `dynamicColor` branch is unreachable — falls through to the existing `LightColors` / `DarkColors` gold/dark-goldenrod palette (no behavior change for pre-12 users).

**BR-U9-19a** (Q-U9-13=A — NEW) — Theme override is exposed to the user via a **3-state cycle icon** in the MainScreen TopAppBar (NOT in Settings). The icon visualises the **current** state and tap advances to the next:

| Current `ThemeOverride` | Icon shown | Tap advances to |
|---|---|---|
| `System` | `Icons.Outlined.BrightnessAuto` (auto/A symbol) | `Light` |
| `Light` | `Icons.Outlined.LightMode` (sun) | `Dark` |
| `Dark` | `Icons.Outlined.DarkMode` (moon) | `System` |

Cycle order: `System → Light → Dark → System`. Locked.

**BR-U9-19b** (Q-U9-13=A) — `MainViewModel.themeOverride: StateFlow<ThemeOverride>` is the icon's data source. `MainViewModel.onThemeCycleTapped()` is the icon's handler — computes `next` via the cycle table above and calls `settings.setThemeOverride(next)`. Standalone flow (not on `MainUiState`) so theme tap recomposes the topbar icon only, not the form.

**BR-U9-19c** (Q-U9-13=A) — Theme cycle tap does **not** emit a confirmation snackbar (BR-U9-11 extends here). The icon flip + the `colorScheme` swap visible across the entire UI are sufficient confirmation.

**BR-U9-19d** (Q-U9-13=A) — Icon button has `contentDescription` set to the **current** state for screen readers: `"Theme: System (tap to switch to Light)"` / `"Theme: Light (tap to switch to Dark)"` / `"Theme: Dark (tap to switch to System)"`. This pattern surfaces both current state and the consequence of a tap to TalkBack users.

**BR-U9-20** — Theme override changes do **not** trigger `Activity.recreate()`. Material 3's `MaterialTheme` propagates the new `colorScheme` via `CompositionLocalProvider` on next recomposition; `MainActivity.setContent`'s `collectAsStateWithLifecycle(settings)` triggers re-derivation of `darkTheme`, which feeds back into `SpoolPainterTheme`.

**BR-U9-21** — On cold start: `SettingsRepository.settings` is a `StateFlow` started `Eagerly` (declared at `SettingsRepositoryImpl.kt:23-27`); first emission lands before `setContent`'s composition runs. No flicker between initial palette and override-applied palette.

**BR-U9-22** — System-bar tinting (status bar foreground icons) follows `darkTheme` via the existing `WindowCompat.getInsetsController(...).isAppearanceLightStatusBars = !darkTheme` `SideEffect` block in `Theme.kt:67-71`. Unchanged by U9.

---

## §5. Offline banner derivation (FR-10.2, FR-10.3, S-10.2)

**BR-U9-23** (Q-U9-3=A) — Banner derivation lives in `MainViewModel` (existing `combine` block at `MainViewModel.kt:165-187`). Not extracted to a pure helper; the two-flow combine is small and tied to ViewModel-scope coroutines.

**BR-U9-24** — Derivation truth table:

| `Settings.url` | `SpoolmanRepository.connectivity` | `BannerState` |
|---|---|---|
| blank | any | `Hidden` |
| set | `Unknown` | `Hidden` |
| set | `Reachable` | `Hidden` |
| set | `Unreachable(reason)` | `Offline(reason)` |

**BR-U9-25** — Banner copy frozen: `"Spoolman unreachable" + (lastError?.let { ": $it" } ?: "")`. Material 3 `Card` styling (already shipped at U6b's BannerSlot). No behavior change in U9 — only test coverage extends.

**BR-U9-26** (Q-U9-9=A) — Banner is **not clickable**. Settings is reachable via the gear icon in the top app bar (existing `onNavigateToSettings`). No second entry point. Reaffirms Q-CD1.1=A + UI-04/UI-09 polish patches.

**BR-U9-27** (Q-U9-10=B) — S-10.2's "Retry control present" AC is closed via a thin requirements delta `aidlc-docs/inception/requirements/requirements-delta-banner-passive.md` reframing S-10.2 as "passive banner; Test connection lives in Settings". Delta authored as part of U9 FD Part 2.

**BR-U9-28** — Banner state transitions (URL configured ↔ blank, connectivity Unknown ↔ Reachable ↔ Unreachable) are tested in `MainViewModelBannerTest`. Test count target +6 derivation cases + 2 transition cases = 8.

---

## §6. Currency switcher (FR-9.4 — NEW, S-9.4 — NEW)

**BR-U9-29** (Q-U9-12=A) — `MainViewModel` derives `priceSuffix: String` from `Settings.currency.symbol` in the same flow combine that already handles `url` projection. `MainUiState.priceSuffix` is the single carrier; no `Currency` enum reference in leaf composables.

**BR-U9-30** — `MoreDetailsExpander.kt:107` price field replaces `suffix = "$"` with `suffix = priceSuffix` (parameter). `FilamentForm` plumbs the parameter through. `MainScreen` reads `state.priceSuffix` from `MainUiState`.

**BR-U9-31** — Currency change is **purely visual**. No re-formatting of stored values; no migration of existing prices. The `Float? price` payload sent to / read from Spoolman is unchanged — Spoolman has no concept of currency, so the symbol is a client-side overlay only.

**BR-U9-32** — Defaults: `Currency.Dollar` (symbol `$`). Preserves the visual behavior shipped through U8 (`MoreDetailsExpander` hard-coded `$`).

**BR-U9-33** — `¤` (U+00A4) is the locked Generic symbol. Renders in Roboto + Noto Sans (Android system fonts ≥ API 29). If a future user reports rendering issues, the option-set Q-U9-11 carve-out C (alternative glyph) is the escalation path.

---

## §7. Acceptance criteria matrix

| Story | AC | Verified by |
|---|---|---|
| S-9.1 | Free-text URL field | manual (already shipped at U5) |
| S-9.1 | Save triggers connectivity check | `SettingsViewModelTest.onTestConnectionTapped*` (already shipped) |
| S-9.1 | Valid URL persisted | `SettingsRepositoryTest` (already shipped) |
| S-9.2 | Spool sort options visible | manual (segmented button) |
| S-9.2 | Spool sort choice persisted | `SettingsRepositoryTest.setSpoolSortOrder` |
| S-9.2 | Spool dropdown applies sort | `SpoolComparatorTest` + manual |
| **S-9.2** | **Filament sort options visible (independent)** | **manual** |
| **S-9.2** | **Filament sort choice persisted** | **`SettingsRepositoryTest.setFilamentSortOrder`** |
| **S-9.2** | **Filament picker applies sort independently** | **`SpoolComparatorTest` + `MainViewModelSortTest` + manual** |
| S-9.3 | System / Light / Dark options accessible | manual (TopAppBar cycle icon — Q-U9-13=A) |
| S-9.3 | Theme choice persisted | `SettingsRepositoryTest.setThemeOverride` (still shipped — call-site moved) |
| S-9.3 | App applies override on startup + change | manual on device (cycle icon round-trip) |
| **S-9.4** | **Currency options visible** | **manual** |
| **S-9.4** | **Currency choice persisted** | **`SettingsRepositoryTest.setCurrency`** |
| **S-9.4** | **Price suffix tracks Currency** | **`MainViewModelCurrencyTest`** |
| S-10.2 | Banner appears on unreachable | `MainViewModelBannerTest` |
| S-10.2 | Retry control present | **N/A — superseded by Q-CD1.1=A; closed by `requirements-delta-banner-passive.md` per Q-U9-10=B** |
| S-12.1 | Dynamic color on Android 12+ | manual on device |
| S-12.1 | Light/dark follows system unless overridden | unit (resolution `when`) + manual |
| S-12.1 | Pre-Android-12 fallback | manual (emulator API ≤ 30) |
| S-13.1 | Two-action layout | manual (already shipped — re-validate post-U9) |
| S-13.2 | Multi-step prompts as bottom sheets | manual (already shipped — re-validate) |

---

## §8. Out-of-scope guards (recap from FD plan §2.9)

- ❌ Branding restore → U9b
- ❌ Main UI parity audit → U9b
- ❌ IME-aware snackbar host → U9b
- ❌ "Other" / "Color Wheel" affordance polish → U9b
- ❌ UI-01 dropdown styling → U9b
- ❌ UI-02 / UI-05 / UI-07 → U9b or U10
- ❌ NFR-5 release-build log stripping → U10
- ❌ Vendor-key Settings → U12 / v2.1
- ❌ Custom-entry management UI → post-v2.0
- ❌ APK size review / JDK 17 portability → U10
- ❌ Sort-order story-copy reconciliation (S-9.2 names "None / Brand A-Z / Material A-Z / Last Used") → U10 / post-v2.0
- ❌ Decimal-separator + position per locale → post-v2.0 (forward-compat path documented in `domain-entities.md` §6)
- ❌ Theme as long-press → menu (System/Light/Dark explicit pick) → post-v2.0 (forward-compat path documented in `domain-entities.md` §6)
- ❌ Legacy-`sortOrder` JSON migration to `spoolSortOrder` → U10 (forward-compat path documented in `domain-entities.md` §1.2.1)
