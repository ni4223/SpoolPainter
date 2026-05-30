# U9 — Functional Design Plan

**Stage**: CONSTRUCTION → Per-Unit Loop → Functional Design (U9)
**Unit**: U9 — Settings + Theming + UI Shell
**Authored**: 2026-05-29

## Per-unit gate assessment

| Stage | Decision | Rationale |
|---|---|---|
| Functional Design | **EXECUTE** | Net behaviour: `SettingsScreen` gains **two independent sort sections (spool + filament)** + **currency** controls (today only URL+Test+Refresh); **theme moves OUT of Settings into a 3-state cycle icon (sun/auto/moon) on the MainScreen TopAppBar** per Q-U9-13=A user direction 2026-05-29; `SpoolPainterTheme` becomes settings-driven (today: `dynamicColor=false`, no override applied); `MainViewModel` already derives the banner — needs only test coverage + retire the U5 doc-drift TODO that named full Settings as U9-scope. **Sort wiring covers both spool + filament dropdowns** independently (filament side pulled in from U9b 2026-05-29; further split into two settings 2026-05-29). **Currency** = new `Settings.currency` field + price-suffix binding in `MoreDetailsExpander`. New domain logic is small but real. |
| NFR Requirements | **SKIP** | No new performance / security / scalability concerns beyond NFR-1..NFR-7 already covered at U1. DataStore round-trip already locked. Theme switch is local activity-recreation — no perf budget. |
| NFR Design | **SKIP** | Predicated on NFR-R running. |
| Infrastructure Design | **SKIP** | Per `aidlc-docs/inception/plans/execution-plan.md` — pure Android client; no CDK / Terraform / CloudFormation. |

## Source artefacts

- `aidlc-docs/inception/application-design/unit-of-work.md` §3-U9 (scope: SettingsScreen URL/Test/sort/theme/**currency**, SettingsViewModel, OfflineBanner finalisation, Material 3 theming, UI shell verify, **spool + filament sort wiring**) + §3-U9b (UI polish unit inserted 2026-05-29 — defers branding restore, IME-aware snackbar, "Other"/"Color Wheel" affordance; **filament sort moved out to U9** 2026-05-29 per user direction; **currency added to U9** same date).
- `aidlc-docs/inception/application-design/components.md` §2.1 (`SettingsScreen`, `OfflineBanner`), §2.2 (`SettingsViewModel`), §2.4 (`SettingsRepository`), §2.5 (`MainViewModel` banner derivation).
- `aidlc-docs/inception/application-design/component-methods.md` §6 (`MainViewModel`), §8 (Compose components — `SettingsScreen` / `OfflineBanner`).
- `aidlc-docs/inception/application-design/services.md` (Spoolman `probe()` contract — `SpoolmanRepository.testConnection`).
- `aidlc-docs/inception/requirements/requirements.md` — FR-9.1, FR-9.2, FR-9.3, FR-10.2, FR-10.3, FR-12.1, FR-12.2, FR-13.1, FR-13.2, FR-13.3, FR-13.4.
- `aidlc-docs/inception/user-stories/stories.md` — S-9.1, S-9.2, S-9.3, S-10.2, S-12.1, S-13.1, S-13.2.
- `aidlc-docs/ui-followups.md` — UI-01 (Spoolman dropdown styling drift), UI-02 (passive-tap prompt), UI-05 (NDEF write-failure copy), UI-07 (snackbar copy review). All **routed to U9b or U10** — U9 itself is FD-scoped and ships only the locked design surface.

### Existing code touchpoints

| File | Role in U9 |
|---|---|
| `data/local/Settings.kt` | Already declares `url: String`, `sortOrder: SortOrder`, `themeOverride: ThemeOverride`. **Schema change in U9**: add `currency: Currency` (new enum). Default `Currency.Dollar` to preserve current `$` price suffix. |
| `data/local/SettingsRepository.kt` | Already exposes `setUrl` / `setSortOrder` / `setThemeOverride`. U9 wires UI → repository for sort + theme; `setUrl` already wired by U5. **Adds `setCurrency(Currency)`**. |
| `data/local/SettingsSerializer.kt` | Default value extended to include `currency = Currency.Dollar`; `@Serializable` enum addition is JSON-additive (existing serialised state has no `currency` field → kotlinx-serialization fills with default; no migration needed). |
| `ui/screens/settings/SettingsUiState.kt` | Already carries `url` / `sortOrder` / `themeOverride`. **Adds `currency: Currency`**. |
| `ui/screens/settings/SettingsScreen.kt` | Adds **three** new sections: **Dropdown sort** (segmented buttons; `Default` / `Alphabetical` / `MaterialThenColor`) + **Theme override** (segmented buttons; `System` / `Light` / `Dark`) + **Currency** (segmented buttons; `Dollar` / `Euro` / `Generic`). The placeholder line "Sort order, theme, and full banner UI land in U9." is removed. |
| `ui/screens/settings/SettingsViewModel.kt` | Adds `onSortOrderChanged(order: SortOrder)` + `onThemeOverrideChanged(theme: ThemeOverride)` + **`onCurrencyChanged(currency: Currency)`** handlers; all delegate to `SettingsRepository`. **No** new effects. |
| `ui/components/MoreDetailsExpander.kt` | Price field's hard-coded `suffix = "$"` (line 107) becomes a parameter `priceSuffix: String` driven from `Settings.currency.symbol`. |
| `ui/screens/main/MainScreen.kt` / `MainViewModel.kt` | `MainUiState` gains `priceSuffix: String` (or `currency: Currency`); `MainViewModel` collects `Settings.currency` into the existing settings flow combine; `FilamentForm` / `MoreDetailsExpander` pass it through. |
| `ui/theme/Theme.kt` | `SpoolPainterTheme` becomes settings-driven: collects `themeOverride` from `SettingsRepository`; resolves `darkTheme` via `(System → isSystemInDarkTheme; Light → false; Dark → true)`; flips `dynamicColor=true` on Android 12+ (currently hard-coded `false`). Pre-Android-12 keeps the existing `LightColors` / `DarkColors` Material 3 palette. |
| `ui/screens/main/MainViewModel.kt` | Banner derivation already present (lines 162-187). **U9 adds tests** (`MainViewModelBannerTest`) for the `(connectivity, settings.url)` matrix. No code change unless Q-U9-3 picks option B (extract derivation to a pure helper). |
| `ui/screens/main/MainScreen.kt` | `BannerSlot` (line 213) already renders `BannerState.Offline`. U9 freezes the copy + visual style — no functional change. UI-01 (dropdown styling drift) **deferred to U9b**, NOT U9. |
| `ui/activity/MainActivity.kt` | `installSplashScreen()` already in place. Theme-override flips force activity recreation only if Hilt-injected theme state is read at top of `setContent` (Q-U9-5). |
| `data/local/Settings.kt` enums | `SortOrder { Default, Alphabetical, MaterialThenColor }` already shipped. **Story S-9.2 names four options**: `None / Brand A-Z / Material A-Z / Last Used`. The shipped enum has only three and is named differently from the story. **Reconcile in Q-U9-2**. |

### Existing code seams confirmed (read 2026-05-29)

- `SettingsScreen.kt` Scaffold pattern is straightforward (`OutlinedTextField` for URL; two `Button` rows for save/test/refresh). Adding two more controls below "Refresh spool list" is a vertical-stack append; no Scaffold restructuring needed.
- `SettingsViewModel.state` is built from `settings.settings.map { ... }.stateIn(...)`. Adding the two `setX` handlers is a one-liner each (delegate to `SettingsRepository.setSortOrder` / `setThemeOverride`).
- `SpoolPainterTheme` body is currently un-instrumented for settings (no Hilt, no Composition Local). Two valid wirings: (a) Hoist `themeOverride` collection to `MainActivity.setContent` and pass `darkTheme` + `dynamicColor` as composable parameters; (b) introduce `LocalThemeOverride` `CompositionLocal` and read inside `SpoolPainterTheme`. Q-U9-5 decides.
- `MainViewModel.observedTagUid` collector lifecycle is unaffected by theme changes — `MainViewModel` survives configuration changes via Hilt's `@HiltViewModel` scope.
- Spool dropdown sort is currently hardcoded: `MainScreen.kt:283-285` does `.filterNot { it.archived }.sortedByDescending { it.id ?: Int.MIN_VALUE }`. Wiring `SortOrder` here is a comparator switch — but **scope decision**: does U9 wire the SpoolmanDropdown to `SortOrder` *now*, or defer to U9b alongside the filament-picker sort? See Q-U9-1.
- Banner derivation in `MainViewModel.kt:165-187` reads `settings.settings.map { it.url.isNotBlank() }` + `spoolman.connectivity`. Pure logic; no I/O. Trivially testable with fakes (already used in `MainViewModelTest`).
- Test fakes: `SettingsRepository` is an interface — fake is already used in tests. `SpoolmanRepository` is `open` — covered by the existing relaxed-mockk pattern.

---

## 1. Unit Context

### 1.1 Scope (locked by Units Generation §3-U9, narrowed by U9b carve-out)

#### In scope for U9 — **functional behavior**

- **Settings UI completeness** — sort order picker + theme override picker + **currency picker** on `SettingsScreen`, all persisting to DataStore via `SettingsRepository`.
- **Theme override application** — `SpoolPainterTheme` reads `themeOverride` and `dynamicColor` (Android 12+) from settings; activity reflects changes on next composition / configuration change.
- **OfflineBanner finalisation** — already wired in `MainViewModel`; U9 adds the test-coverage matrix and freezes the copy ("Spoolman unreachable" + optional reason suffix). Banner remains read-only — **no Retry control** per Q-CD1.1=A (banner is passive; Settings owns Test connection).
- **Sort order applied to BOTH spool and filament dropdowns** — single shared comparator factory; no longer optional (Q-U9-1 default flipped from C → A 2026-05-29 per user direction "filamnet sort from 9b should be hewre too").
- **Currency switcher** — new `Settings.currency` field; price-suffix in `MoreDetailsExpander` binds to it (currently hard-coded `$`). Options: `Dollar` ($) / `Euro` (€) / `Generic` (locked in Q-U9-11).

#### In scope for U9 — **test coverage**

- `SettingsViewModelTest` extension — `onSortOrderChanged` + `onThemeOverrideChanged` + `onCurrencyChanged` round-trip through repo.
- `SettingsRepositoryTest` — round-trips for sort + theme + **currency** (covered partially at U1; verify extant; add cases if missing).
- `MainViewModelBannerTest` — derivation matrix `(url=blank|set, connectivity=Unknown|Reachable|Unreachable)` × expected `BannerState`.
- `SpoolComparatorTest` — both `spoolComparator` + `filamentComparator` factories: one case per `SortOrder`.
- `MainViewModelCurrencyTest` — price-suffix in `MainUiState` tracks `Settings.currency` changes.
- `SpoolPainterThemeTest` *(if Q-U9-5=B)* — Compose UI test verifying dark-mode flip on `themeOverride` change. Skipped if Q-U9-5=A (theme handled at activity boundary; harder to unit-test in JVM).

#### Out of scope for U9 (deferred to U9b unless noted)

- **Branding restore** (logo on main + splash artwork) → **U9b** (per `unit-of-work.md` §U9b).
- **Main UI parity audit vs v1** → **U9b**.
- **Snackbar visibility under keyboard** (Save / Test connection feedback hidden by IME) → **U9b**.
- ~~**Filament dropdown sort**~~ → **PULLED INTO U9** 2026-05-29 (was U9b). Same comparator factory ships both sides.
- **"Other" + "Color Wheel" affordance polish** → **U9b**.
- **UI-01 (Spoolman dropdown styling drift)** → **U9b** (catalogued but not part of FD).
- **UI-02 (passive-tap prompt)** → **U9b** or **U10**.
- **UI-05 / UI-07 (snackbar copy review)** → **U10**.
- **UI-13 (filament-metadata edit-on-save PATCH)** → post-v2.0 / **U10** scoping.
- **NFR-5 release-build log stripping** → **U10**.
- **APK size review / JDK 17 portability fix** → **U10**.

### 1.2 Cross-unit consumers

| Unit | Relationship |
|---|---|
| U8 (Pickers + Filament Metadata UX) | Independent. Settings doesn't expose custom-entry management for v2.0 (deferred). |
| U9b (UI Polish) | Direct consumer. U9b extends whatever sort comparator U9 picks (see Q-U9-1) and polishes the controls U9 ships. U9b also restores logo + fixes IME-hidden snackbars; both are visual-only on top of U9's wiring. |
| U10 (Release polish) | U10's manual install-gate matrix gains a row per S-9.1 / S-9.2 / S-9.3 (URL save persists; sort persists across app launches; theme override persists + applies on cold start). |
| U11 / U12 (v2.1) | Settings will eventually surface vendor-key management (S-9.4.1). Out of v2.0 scope. |

### 1.3 Out of scope (deferred — already covered above; recap for completeness)

- Vendor-key UI / encrypted storage → U12/v2.1
- Custom-entry management UI → post-v2.0
- Settings-resident "raw-write mode" toggle → never; raw-write engages automatically per U7's D-U7-3
- "Reset all settings" / "Wipe data" buttons → never (single-user app; uninstall is the wipe)

---

## 2. Plan Steps

### 2.1 Domain entities

#### 2.1.1 `Settings` schema

- [ ] No new fields. Confirmed extant: `url`, `sortOrder`, `themeOverride`.
- [ ] Reconcile `SortOrder` enum vs S-9.2 story copy. See Q-U9-2.

#### 2.1.2 `SettingsUiState`

- [ ] No new fields. Already carries the three relevant fields.

#### 2.1.3 No new domain primitives

U9 ships zero new types in `domain/`. All new behavior lives in:
- `ui/screens/settings/SettingsScreen.kt` (two new control rows)
- `ui/screens/settings/SettingsViewModel.kt` (two new handlers)
- `ui/theme/Theme.kt` (settings-driven palette resolution)
- Test files only.

---

### 2.2 SettingsScreen layout

- [ ] Lock the new section order.

```
[ Top app bar — back button + "Settings" ]

URL section (existing)
  • Label: "Spoolman URL (e.g. http://nas.local:7912)"
  • OutlinedTextField (singleLine, KeyboardType.Uri)
  • Row: [Save] [Test connection]
  • OutlinedButton: "Refresh spool list"

Sort order section (NEW)
  • Label: "Spool list sort"
  • SingleChoiceSegmentedButtonRow with three options
  • (Q-U9-2 controls labels + enum reconciliation)

Theme section (NEW)
  • Label: "Theme"
  • SingleChoiceSegmentedButtonRow: [System] [Light] [Dark]
```

> **Q-U9-4** — Control style for the two new pickers
> - **A.** ⭐ `SingleChoiceSegmentedButtonRow` (Material 3) — visually compact; reads as "one-of-N choice" at a glance. Same idiom across both controls.
> - **B.** `RadioButton` rows — verbose; clearer for screen readers.
> - **C.** Dropdown (`ExposedDropdownMenuBox`) — saves vertical space but adds a tap to see the choices.
>
> **My pick:** A — three options each is the sweet spot for segmented buttons.

> **Q-U9-2** — `SortOrder` enum reconciliation
>
> Today's `Settings.kt`: `enum class SortOrder { Default, Alphabetical, MaterialThenColor }`.
> S-9.2 story copy: "Options: None / Brand A-Z / Material A-Z / Last Used".
>
> - **A.** Keep current enum, ship UI labels matching the **enum**: "Default / Alphabetical / Material then Color". Closes the gap toward what's actually wired. ⭐
> - **B.** Migrate enum to match the story: drop `MaterialThenColor`; add `BrandAToZ`, `MaterialAToZ`, `LastUsed`. Schema break (existing DataStore has `Default` written; serialiser would need a default-value fallback). Adds a "last used" concept the codebase doesn't track today.
> - **C.** Hybrid: ship A for U9 + log a follow-up to revisit story copy in U10.
>
> **My pick:** C — the enum is shipped + serialised; renaming mid-flight invites a JSON migration path nobody asked for. The story can be reworded at U10 / post-v2.0 to match the implementation, or the enum can be expanded later if "Last Used" turns out to be wanted.

#### 2.2.1 Sort comparator wiring

> **Q-U9-1** — Sort comparator wiring scope (UPDATED 2026-05-29 — filament sort pulled in from U9b)
>
> - **A.** ⭐ Wire **both** spool dropdown + filament picker in U9 against the same `Comparator` factory keyed off `SortOrder`. (New default after user direction "filamnet sort from 9b should be hewre too".)
> - **B.** Defer wiring entirely to U9b. U9 ships the Settings UI + persistence only; nothing reads the value yet.
> - **C.** Wire spool sort in U9; defer filament sort to U9b. (Original "My pick" — superseded.)
>
> **My pick:** A — user-confirmed scope merge 2026-05-29. Single comparator factory in `ui/components/SortComparators.kt` (per Q-U9-7=B) returns `Comparator<SpoolmanSpool>` and `Comparator<SpoolmanFilament>` from the same `SortOrder` switch.

#### 2.2.2 Comparator factory shape

If Q-U9-1 = A or C:

```kotlin
// In MainScreen.kt or a new ui/components/SortComparators.kt
internal fun spoolComparator(order: SortOrder): Comparator<SpoolmanSpool> = when (order) {
    SortOrder.Default -> compareByDescending { it.id ?: Int.MIN_VALUE }    // existing behavior
    SortOrder.Alphabetical -> compareBy(String.CASE_INSENSITIVE_ORDER) { spoolDisplayName(it) }
    SortOrder.MaterialThenColor -> compareBy<SpoolmanSpool>(String.CASE_INSENSITIVE_ORDER) { it.filament?.material ?: "" }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.filament?.color_hex ?: "" }
}
```

> **Q-U9-7** — Comparator location
> - **A.** Inline private helper in `MainScreen.kt` (matches today's `.sortedByDescending` inline call).
> - **B.** ⭐ Extract to `ui/components/SortComparators.kt` so U9b can reuse for the filament picker without touching `MainScreen.kt`.
>
> **My pick:** B — Q-U9-1=C means U9b is already coming back here; a shared helper file is the cleaner seam.

---

### 2.3 SettingsViewModel handlers

- [ ] Lock the two new handler signatures.

```kotlin
fun onSortOrderChanged(order: SortOrder) {
    viewModelScope.launch { settings.setSortOrder(order) }
}

fun onThemeOverrideChanged(theme: ThemeOverride) {
    viewModelScope.launch { settings.setThemeOverride(theme) }
}
```

> **Q-U9-6** — Should sort/theme changes emit a confirmation snackbar like URL save does ("URL saved")?
> - **A.** ⭐ No snackbar. The control's selection state is its own confirmation; emitting a snackbar for every theme tap is noisy.
> - **B.** Emit "Sort order saved" / "Theme set to <X>". Symmetric with URL save.
>
> **My pick:** A — segmented buttons confirm via the highlighted segment. URL save needs a snackbar because the URL field doesn't visually "stick" until submitted.

---

### 2.4 SpoolPainterTheme — settings-driven

#### 2.4.1 Wiring

> **Q-U9-5** — How does `SpoolPainterTheme` learn the override?
>
> - **A.** ⭐ Hoist to `MainActivity`. `setContent` collects `themeOverride` from a Hilt-injected `SettingsRepository` via `collectAsStateWithLifecycle`; computes `darkTheme: Boolean` and passes both `darkTheme` and `dynamicColor=true` into `SpoolPainterTheme(darkTheme, dynamicColor)`. Theme function becomes a **pure** `@Composable`.
> - **B.** `LocalThemeOverride` `CompositionLocal` provided at `MainActivity` level; `SpoolPainterTheme` reads it internally. Less plumbing at the call site, but a hidden dependency.
> - **C.** Inject `SettingsRepository` directly into `SpoolPainterTheme` via `LocalContext` + Hilt entry-point pattern. Most magical; least test-friendly.
>
> **My pick:** A — explicit, testable, mirrors the existing `darkTheme: Boolean` parameter (just makes it settings-driven instead of system-driven). `MainActivity` owns Hilt access already (`@AndroidEntryPoint`).

If Q-U9-5 = A, MainActivity becomes:

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var nfcRepository: NfcRepository

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
                // existing settings/main toggle...
            }
        }
        intent?.let { tryDispatchNfcIntent(it) }
    }
    // onNewIntent / onResume / onPause / tryDispatchNfcIntent unchanged
}
```

#### 2.4.2 dynamicColor flip

> **Q-U9-8** — `dynamicColor` parameter default
>
> Today: `dynamicColor: Boolean = false` (hard-coded off; comment "Disable dynamic colors to use our custom theme").
> S-12.1 / FR-12.1: Material You dynamic color on Android 12+.
>
> - **A.** ⭐ Flip default to `true`. Android 12+ gets dynamic color; pre-Android-12 falls through to the existing custom palette (Theme.kt already handles this branch). Aligns with the user story.
> - **B.** Keep custom palette always. Story FR-12.1 deprioritised. Reason to keep: the gold/dark-goldenrod palette is part of v1's brand identity; Material You washes it out.
> - **C.** Settings-controlled — add `useDynamicColor: Boolean` to the schema; default true on Android 12+, user can disable to fall back to custom palette.
>
> **My pick:** A — story is explicit; "Material You is part of feeling at home on the device". If user backlash arrives post-launch, C is a follow-up. The custom palette stays as the pre-Android-12 fallback either way.

---

### 2.5 OfflineBanner — finalisation

#### 2.5.1 Already-shipped state

`MainViewModel.kt:162-187` already derives `BannerState.Offline(reason)` when `(url configured AND connectivity == Unreachable)`, else `BannerState.Hidden`. `MainScreen.BannerSlot` (line 213) already renders it as a card with the reason suffix.

> **Q-U9-3** — Extract banner derivation to a pure helper for testability?
>
> - **A.** Test the derivation in-place via `MainViewModelBannerTest`, exercising the existing `combine { ... }`. Same pattern as `MainViewModelTest` — Settings + Spoolman fakes drive the flows. ⭐
> - **B.** Extract `fun deriveBanner(urlConfigured: Boolean, connectivity: ConnectivityState): BannerState` to a top-level pure function. Trivially unit-testable; VM becomes thinner.
>
> **My pick:** A — derivation is two lines and tied to two flows; the cost of a helper extraction outweighs the benefit. JVM-runnable VM tests with fake repos already work in this codebase.

#### 2.5.2 Banner copy freeze

Current copy: `"Spoolman unreachable" + (banner.lastError?.let { ": $it" } ?: "")`.

- [ ] Locked as final. No Retry control (banner is passive per Q-CD1.1=A; Test connection lives in Settings).

> **Q-U9-9** — Should the banner gain a "Tap to open Settings" affordance?
>
> - **A.** ⭐ No — keep banner read-only. Settings is one tap away via the top-right gear icon (already wired). Adding an extra entry point fragments the navigation contract.
> - **B.** Yes — make the banner clickable; opens Settings. Saves a tap when the banner is the user's first signal.
>
> **My pick:** A — the user pick at Q-CD1.1 was explicit; UI-04 / UI-09 polish patches reaffirmed it. Gear icon stays the canonical Settings entry point.

---

### 2.5b Currency switcher

#### 2.5b.1 Schema

- [ ] Add `Currency` enum to `data/local/Settings.kt`:

```kotlin
@Serializable
enum class Currency(val symbol: String) {
    Dollar("$"),
    Euro("€"),
    Generic("¤"),  // U+00A4 — generic currency sign; locked in Q-U9-11
}
```

- [ ] `Settings` data class gains `val currency: Currency = Currency.Dollar`.
- [ ] `SettingsSerializer.defaultValue` extends with `currency = Currency.Dollar` — kotlinx-serialization fills missing field on existing serialised state, so no migration code needed.
- [ ] `SettingsRepository.setCurrency(currency: Currency)` — same shape as `setSortOrder` / `setThemeOverride`.

> **Q-U9-11** — Currency option set + generic symbol
>
> User direction 2026-05-29: "we can jst switch $ and eurpo sign for now and maybe obne that is genric like money".
>
> - **A.** ⭐ Three options: `Dollar` ($) / `Euro` (€) / `Generic` (¤ — Unicode U+00A4 generic currency sign). Matches user's "$", "€", "generic like money" intent. ¤ is the standard cross-locale "any currency" glyph; renders in every Android system font.
> - **B.** Two options only: `Dollar` ($) / `Euro` (€). Skip generic for now; add later if asked.
> - **C.** Three options with a different generic glyph: `Dollar` ($) / `Euro` (€) / `Generic` ("¥" / "£" / blank string). Reasons to consider: ¤ may be unfamiliar; some users may read it as "broken character".
> - **D.** Open-ended: free-text field for currency symbol. Most flexible; least guard-railed (multi-byte glyphs, RTL, etc.).
>
> **My pick:** A — captures the user's exact ask with the proper Unicode generic-currency glyph. ¤ renders cleanly on Android system fonts (verified in Roboto + Noto Sans). D is over-engineered for a v2.0 ask; B drops the generic option the user explicitly mentioned.

#### 2.5b.2 Price-suffix wiring

- [ ] `MoreDetailsExpander.kt:107` — `suffix = "$"` becomes `suffix = priceSuffix` (parameter).
- [ ] `MoreDetailsExpander` signature gains `priceSuffix: String` (or `currency: Currency`; pure-parameter style for testability).
- [ ] `FilamentForm` host plumbs the param through.
- [ ] `MainViewModel` settings flow combine — adds `Settings.currency` so `MainUiState` carries either `currency: Currency` or a derived `priceSuffix: String`.

> **Q-U9-12** — Carrier shape: `Currency` enum down to leaf, or pre-derived `priceSuffix: String`?
>
> - **A.** ⭐ Pre-derived `priceSuffix: String` on `MainUiState`. Leaf composable stays decoupled from the `Currency` enum; one less import in `MoreDetailsExpander`. Maps trivially in the VM (`settings.currency.symbol`).
> - **B.** Pass `Currency` enum all the way through. More forward-compatible if future polish (decimal separator, position) keys off the enum.
>
> **My pick:** A — current scope is symbol-only; symbol-only is what the leaf wants. If B's forward-compat ever lands, the swap is mechanical.

---

### 2.6 Tests

- [ ] `SettingsViewModelTest` — extend with three cases:
  - `onSortOrderChanged invokes setSortOrder on repository`
  - `onThemeOverrideChanged invokes setThemeOverride on repository`
  - `onCurrencyChanged invokes setCurrency on repository`
- [ ] `SettingsRepositoryTest` — verify (or add) round-trip cases for `setSortOrder` + `setThemeOverride` + `setCurrency`.
- [ ] `MainViewModelBannerTest` (NEW) — derivation matrix:
  - `(url="", Unknown)` → `Hidden`
  - `(url="", Reachable)` → `Hidden`
  - `(url="", Unreachable("dns"))` → `Hidden`
  - `(url="http://nas.local", Unknown)` → `Hidden`
  - `(url="http://nas.local", Reachable)` → `Hidden`
  - `(url="http://nas.local", Unreachable("dns"))` → `Offline("dns")`
  - Plus state-transition cases (URL gets configured while unreachable → banner appears; URL cleared while unreachable → banner disappears).
- [ ] **No** `SpoolPainterThemeTest` if Q-U9-5=A — theme is hoisted out of the composable and tested implicitly via the `darkTheme` argument (which is just `when (themeOverride) { ... }`). A trivial pure-Kotlin test against that resolution function suffices.
- [ ] `SpoolComparatorTest` (NEW) — `spoolComparator(SortOrder)` and `filamentComparator(SortOrder)` factories: 3 cases each (one per enum value) = 6 cases.
- [ ] `MainViewModelCurrencyTest` (NEW) — `priceSuffix` (or `currency`) on `MainUiState` reflects `Settings.currency`; toggling the setting flips the symbol on the next emission. ~3 cases.

**Target test count delta**: U8's 332 + ~18 new cases (1 currency VM + 1 currency repo + 6 comparator + 3 currency-state + originals: 3 banner + 1 sort VM + 1 theme VM ≈ 18) = **~350 / 350**. Existing 332 must remain passing; no deletions.

---

### 2.7 Acceptance criteria recap (story → AC → test/manual marker)

| Story | AC | Verified by |
|---|---|---|
| S-9.1 | Settings has free-text URL field | manual (already shipped at U5) |
| S-9.1 | Save triggers connectivity check; failure surfaces error | unit (`SettingsViewModelTest.onTestConnectionTapped*`, already shipped) |
| S-9.1 | Valid URL persisted | unit (`SettingsRepositoryTest`, already shipped) |
| S-9.2 | Options visible | manual (per Q-U9-2; story copy wording diverges from enum — recorded as known) |
| S-9.2 | Choice persisted | unit (`SettingsRepositoryTest.setSortOrder`) |
| S-9.2 | Dropdown applies sort | unit (`SpoolComparatorTest`) + manual on device, scope per Q-U9-1 |
| S-9.3 | System / Light / Dark options | manual (segmented button) |
| S-9.3 | Choice persisted | unit (`SettingsRepositoryTest.setThemeOverride`) |
| S-9.3 | App applies override on startup + on change | manual (cold-start verify) |
| S-10.2 | Banner appears on unreachable | unit (`MainViewModelBannerTest`) |
| S-10.2 | Retry control present | **N/A — explicitly NOT shipped per Q-CD1.1=A**. AC reworded in delta or accepted as deferred. See Q-U9-10. |
| S-12.1 | Dynamic color on Android 12+ | manual on device |
| S-12.1 | Light/dark follows system unless overridden | unit (theme resolution function) + manual |
| S-12.1 | Pre-Android-12 fallback | manual (need pre-12 device or emulator) |
| S-13.1 | Two-action layout (Read NFC / Write to NFC) | manual (already shipped — re-validate post-U9) |
| S-13.2 | Multi-step prompts as bottom sheets | manual (already shipped — re-validate) |

> **Q-U9-10** — S-10.2 "Retry control present" gap
>
> S-10.2 (banner with Retry) has been narrowed by Q-CD1.1=A to "passive banner; Test connection lives in Settings". Three resolutions:
>
> - **A.** ⭐ Mark the Retry AC as **N/A — superseded by Q-CD1.1=A** in the U9 close-out summary. No requirements delta needed; Q-CD1.1's audit-log entry is the authority.
> - **B.** Author a thin requirements delta (`requirements-delta-banner-passive.md`) reframing S-10.2 as "passive banner + Settings-resident Test connection". Cleaner traceability for the requirements doc.
> - **C.** Re-add a Retry button to the banner. Reverses Q-CD1.1=A. Reason: install-gate user feedback ever lands "wish the banner had Retry".
>
> **My pick:** B — a one-page delta closes the requirements-vs-implementation gap permanently; A relies on auditors digging into Q-CD1.1's history. C is a non-starter unless the user reverses themselves.

---

### 2.8 Implementation gates

- [ ] `./gradlew compileDebugKotlin` ✅
- [ ] `./gradlew testDebugUnitTest` ✅ — running total target: **332 (U8) + ~12 (U9) ≈ 344 / 344**.
- [ ] `./gradlew assembleDebug` ✅ — APK size monitored; flagged for U10 if >36 MB (current baseline 34 MB after U8).
- [ ] **No U9 milestone install gate** (Q-T2=B). Manual NFC verification deferred to U10 install gate per `unit-of-work.md` §U9 exit criteria. Theme + sort behaviour verified organically during U9b's install-time iteration.

---

### 2.9 Out-of-scope guards (explicit for U9)

- ❌ Branding restore (logo, splash artwork) → **U9b**
- ❌ Main UI parity audit vs v1 → **U9b**
- ❌ Snackbar IME-aware host → **U9b**
- ✅ Filament dropdown sort → **PULLED INTO U9** (was U9b; per Q-U9-1=A as of 2026-05-29)
- ❌ "Other" / "Color Wheel" affordance polish → **U9b**
- ❌ UI-01 Spoolman dropdown styling → **U9b**
- ❌ UI-02 passive-tap prompt → **U9b** or **U10**
- ❌ UI-05 / UI-07 snackbar copy review → **U10**
- ❌ NFR-5 release-build log stripping → **U10**
- ❌ Vendor-key Settings → **U12 / v2.1**
- ❌ Custom-entry management Settings UI → post-v2.0
- ❌ APK size review / JDK 17 portability fix → **U10**

---

## 3. Open Questions Ledger (Q-U9-1..Q-U9-12)

| # | Question | My pick |
|---|---|---|
| Q-U9-1 | Sort comparator wiring scope (REVISED 2026-05-29 — split into two settings) | **A — wire both spool + filament in U9 with TWO INDEPENDENT `Settings` fields (`spoolSortOrder` + `filamentSortOrder`); two segmented sections on Settings; comparator factory shared via `ui/components/SortComparators.kt`** |
| Q-U9-2 | Reconcile `SortOrder` enum vs S-9.2 story copy (REVISED 2026-05-29 — enum unchanged; usage doubled) | **C — ship enum-driven labels; log story revisit for U10. The `Settings` field renames (`sortOrder` → `spoolSortOrder`; new `filamentSortOrder`) drop the legacy JSON key on first read (acceptable; preference only)** |
| Q-U9-3 | Extract banner derivation to a pure helper? | A — keep in VM; test in-place |
| Q-U9-4 | Control style for sort + currency pickers (REVISED 2026-05-29 — theme dropped from list) | **A — `SingleChoiceSegmentedButtonRow` for spool sort + filament sort + currency; theme is a TopAppBar cycle icon (Q-U9-13)** |
| Q-U9-5 | How does `SpoolPainterTheme` learn the override? | A — hoist to `MainActivity` |
| Q-U9-6 | Sort/theme/currency change emits confirmation snackbar? | A — no snackbar (BR-U9-11 + BR-U9-19c) |
| Q-U9-7 | Comparator location | B — extract to `ui/components/SortComparators.kt` |
| Q-U9-8 | `dynamicColor` default flip | A — `true` (Material You on Android 12+) |
| Q-U9-9 | Banner clickable → open Settings? | A — no, banner stays read-only |
| Q-U9-10 | S-10.2 Retry AC gap | B — author requirements-delta-banner-passive.md |
| **Q-U9-11** | **Currency option set + generic glyph** | **A — Dollar / Euro / Generic (¤ U+00A4)** |
| **Q-U9-12** | **Currency carrier shape (enum vs pre-derived suffix)** | **A — pre-derived `priceSuffix: String` on `MainUiState`** |
| **Q-U9-13** | **Theme override surface (Settings vs MainScreen TopAppBar)** | **A — TopAppBar 3-state cycle icon (System/Light/Dark with sun/moon/auto glyphs); cycle order System → Light → Dark → System; standalone `MainViewModel.themeOverride: StateFlow` + `onThemeCycleTapped()`; no snackbar (icon flip + colorScheme swap are the confirmation); per-state `contentDescription` for TalkBack** |

---

## 4. Approval gate

**Per `core-workflow.md`**: this plan is **Functional Design Part 1 (Planning)**. The user must answer the **thirteen** Q-U9-* questions (or "go go go" to accept all "My pick" defaults) before I generate the FD Part 2 artefacts under `aidlc-docs/construction/u9-settings-theming-banner/functional-design/`. **FD Part 2 was generated 2026-05-29 against Q-U9-1..Q-U9-12, then revised same day after user direction split sort into spool+filament and moved theme to TopAppBar — Q-U9-13 added retroactively; FD artefacts re-locked.**

**FD Part 2 will produce** (after Q&A approval):
- `aidlc-docs/construction/u9-settings-theming-banner/functional-design/domain-entities.md`
- `aidlc-docs/construction/u9-settings-theming-banner/functional-design/business-rules.md`
- `aidlc-docs/construction/u9-settings-theming-banner/functional-design/business-logic-model.md`
- `aidlc-docs/construction/u9-settings-theming-banner/functional-design/frontend-components.md`
- (If Q-U9-10=B) `aidlc-docs/inception/requirements/requirements-delta-banner-passive.md`

After FD Part 2 approval → Code Generation Part 1 (Planning) → Part 2 (Generation) → close-out commit → U9b opens.
