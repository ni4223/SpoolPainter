# U22 — SpoolmanDB filament picker — Code Generation summary

**Status**: DONE — **code-level validation only** (`compileDebugKotlin` /
`testDebugUnitTest` / `assembleDebug`). **On-device install gate NOT yet
run** — flagged as pending; the Spoolman-web-UI highlight (the point of
the `external_id` stamping) still needs an on-device/on-server smoke
check before release. **Version NOT bumped** — held at versionCode 113 /
versionName 2.3.1 (current level after the v2.3.1 CameraX page-size fix;
batch-release policy per U20/U21 precedent). Not committed yet.

## What shipped

A **SpoolmanDB section** in the Filament picker dropdown (pinned footer
below the local rows, label from `/api/v1/info` → `external_db_name`,
fallback "SpoolmanDB"). U21's search field now drives BOTH the local
filter (unchanged) and a **debounced (300 ms) server search** —
`GET /api/v1/external/filament/search`, query + limit 20 + offset paging,
total count from the `x-total-count` header (page-size fallback), "Load
more (N more)" row, < 2 chars stays Idle. **Picking an entry prefills the
whole form** (material, brand, colour, variant, temps, density, weights)
and Save creates the records through the normal chain, stamped with the
DB `external_id` — filament AND vendor — so Spoolman's web UI highlights
them as a database import, exactly like a web-UI import. Already-imported
entries delegate to the existing filament instead of duplicating.

### Locked design (Part 2 Q&A)
- **Q-U22-1 = Integrated into the picker.** No separate sheet — one
  search box in the Filament picker does inventory + database; the DB
  section is a footer slot on `LazyDropdownMenu` (new `footer` param,
  omitted = today's render).
- **Q-U22-2 = Prefill on pick, not immediate import.** Records are only
  created on Save (no orphan risk on back-out). Pick-time dedup:
  inventory filament with the same `external_id` → delegate to
  existing-filament selection. Save-time dedup: cache shortcut + LIVE
  `listFilaments` check (backfills the cache), both ahead of the fuzzy
  matcher.
- **Q-U22-3 = Feature detection + hint.** `SpoolmanInfo?.
  supportsExternalDbSearch()` — ≥ 0.26.0, fails closed on
  null/garbage/pre-release versions; Unsupported wins over every internal
  state (stale Results can never surface after a URL switch). Picker
  shows "Database search requires Spoolman v0.26+". `MainViewModel`
  one-shot `testConnection()` probe resolves an unknown version once the
  server is Reachable. No legacy full-catalog fallback.
- **Q-U22-4 = Multi-colour → first hex.** Form colour =
  `color_hex ?: color_hexes.first()`; DB rows render a two-colour swatch
  (second hex layered bottom-end) so multi-colour filaments read at a
  glance.
- **Q-U22-5 = Variant = name minus material word.** ONE case-insensitive
  word-boundary occurrence of the material stripped from the DB name
  ("PolyLite PLA"/PLA → "PolyLite"; name == material → null), whitespace
  collapsed — mirrors `fromFilament`'s variant handling.

### Invariants held
- **Existing picker behaviors byte-identical**: the U20 scan-suggestion
  float + divider + sort and the U21 local filter are untouched; blank
  query renders exactly as before; the external search never mutates the
  local filament list state (separate `ExternalSearchUiState`).
- **Records only created on Save — no immediate import.** Picking only
  prefills; nothing is POSTed until Save.
- Material synthesis from DB temps+density only when the preset lookup
  misses AND the DB carries an extruder temp; temps min=max when the DB
  carries them, null keeps preset seeds.

## Files touched
1. `data/remote/spoolman/SpoolmanApi.kt` — `searchExternalFilaments`
   endpoint (raw `Response` for the header).
2. `data/remote/spoolman/SpoolmanRepository.kt` — `serverInfo` StateFlow
   (refreshed by `testConnection`), `searchExternalFilaments`
   (limit/offset/`x-total-count` + fallback), `performHttpResponse`
   (header-aware sibling), `supportsExternalDbSearch()`,
   `external_id` stamping on filament + vendor POSTs, cache + live dedup,
   diameter precedence (form ?: external ?: 1.75).
3. `data/remote/spoolman/SpoolmanRequests.kt` — `external_id` on
   `CreateVendorRequest` + `CreateFilamentRequest`.
4. `domain/models/SpoolmanModels.kt` — `ExternalFilament`,
   `ExternalFilamentPage`, `SpoolmanInfo.external_db_name`,
   `external_id` on `SpoolmanFilament` + `SpoolmanVendor`.
5. `domain/usecases/NewFilamentRequest.kt` — `externalFilamentId` +
   `diameterMm` (constructor + `fromForm`).
6. `domain/usecases/SaveToSpoolmanUseCase.kt` — `SaveToSpoolmanInput`
   passthrough into the create request.
7. `ui/screens/main/ExternalFilamentSearchState.kt` — NEW
   `ExternalSearchUiState` (Idle/Searching/Results/Error/Unsupported).
8. `ui/screens/main/MainUiState.kt` — `FormState.externalFilamentId` /
   `externalDiameterMm` (no UI field; ride the save path).
9. `ui/screens/main/MainViewModel.kt` — `externalDbLabel`,
   `externalSearchState` gate, `onExternalFilamentQueryChanged` /
   `onExternalFilamentLoadMore` / `onExternalFilamentPicked` /
   `onExternalSelectionCleared`, save-input threading, selection-clears,
   one-shot version probe, `EXTERNAL_SEARCH_DEBOUNCE_MS = 300`.
10. `ui/components/LazyDropdownMenu.kt` — opt-in `footer` slot.
11. `ui/components/FilamentPicker.kt` — `ExternalDbSection` +
    `ExternalFilamentRow` + `ExternalSwatch` (multi-colour), footer
    wiring, query-forwarding, pick/dismiss also resets the external
    query.
12. `ui/components/FilamentSection.kt` — origin `InputChip`
    ("From SpoolmanDB", testTag `filament-origin-chip`); X clears only
    the linkage, prefill stays.
13. `ui/components/FilamentForm.kt` — new picker params +
    `FormChange.ExternalFilamentPicked` / `ExternalSelectionCleared`.
14. `ui/screens/main/FormMapping.kt` — `applyExternalFilament` +
    `deriveExternalVariant`.
15. `ui/screens/main/MainScreen.kt` — flow collection + callback wiring
    + the two new `FormChange` branches.
16. Tests: NEW `ExternalFilamentDtoTest` (5),
    `SpoolmanRepositoryExternalSearchTest` (8),
    `SupportsExternalDbSearchTest` (10),
    `SpoolmanRepositoryExternalFilamentCreateChainTest` (8),
    `NewFilamentRequestTest` (3),
    `SaveToSpoolmanUseCaseExternalThreadingTest` (2),
    `MainViewModelExternalSearchTest` (16); EXTENDED `FormMappingTest`
    (+7), `FakeSpoolmanApi`, `FakeSpoolmanRepository`.

## Build / tests
- `compileDebugKotlin` ✅
- `testDebugUnitTest` ✅ **647 / 647** (80 suites, 0 failures) —
  **588 → 647, Δ +59** (counts above).
- `assembleDebug` ✅.
- Full `assemble*` / `bundle*` deferred to the batched release.

## Follow-ups
- **On-device install gate pending** (see Status): search + Load more,
  pick → prefill → Save → verify Spoolman web UI shows the
  database-import highlight on filament + vendor; re-pick of the same
  entry must select the existing filament; a < 0.26 server must show the
  hint row.
- **Multi-colour hex stays a first-hex approximation** (Q-U22-4) until
  the planned multi-colour support (README "What's coming next"); the
  picker swatch already hints multi-colour entries.
- Servers 0.26.x (no `x-total-count` / offset paging, shipped 0.27.0)
  degrade to a single page with no Load-more row — accepted, not fixed.
