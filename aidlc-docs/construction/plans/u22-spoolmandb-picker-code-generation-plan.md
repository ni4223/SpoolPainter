# U22 — SpoolmanDB filament picker (external filament database)

**Unit**: U22. Pick a filament from Spoolman's external filament database
("SpoolmanDB") directly in the Filament picker. Builds directly on U21's
type-to-search field — the SAME search box now drives the local filter
(unchanged) and a debounced server-side search — and extends the
`LazyDropdownMenu` slot pattern (U21 added `header`; U22 adds `footer`).

**Per-unit gate**: Functional Design / NFR-R / NFR-D / Infra-D **SKIP**
(feature unit; design folded into this Code Gen plan, mirroring U20/U21).
Code Generation runs Part 1 (this plan) → Part 2 (execution).

**Goal**: let the user find any catalogue filament (Polymaker, Bambu,
SUNLU, …) without hand-typing its specs, and — the user's explicit
requirement — make Spoolman's web UI recognise the created records as a
database import (highlight badge), exactly like an import done in the
Spoolman web UI. That recognition is the `external_id` stamped on the
filament + vendor POSTs at Save time.

---

## §0 Overriding invariants

1. **Existing picker behaviors byte-identical** while the external
   section is hidden (blank query / closed picker): the U21 local filter,
   the U20 scan-suggestion float + divider + sort, and reset-on-close are
   untouched. The external search NEVER mutates the local filament list
   state; it lives in its own `ExternalSearchUiState`.
2. **Records are only created on Save — no immediate import.** Picking a
   database entry prefills the form via `FormMapping.applyExternalFilament`;
   nothing is POSTed until the user taps Save (the app's create-on-save
   model). Pick-time and save-time `external_id` dedup guarantee a DB
   entry is never imported twice.
3. **Version gating fails closed.** Unknown / garbage / pre-release
   versions are treated as Unsupported; `externalSearchState` is a
   `combine(serverInfo, internal)` gate so Unsupported wins over every
   internal state — stale Results can never surface on a non-supporting
   server (e.g. after a URL switch).

---

## §1 In scope

- **SpoolmanDB section inside the existing Filament picker dropdown**,
  pinned below the local rows via the new `LazyDropdownMenu` `footer`
  slot. Section label from `GET /api/v1/info` → `external_db_name`
  (fallback "SpoolmanDB").
- **One search box, two searches**: the U21 picker search field keeps
  driving the local filter as-is AND feeds a debounced (300 ms) server
  search via `GET /api/v1/external/filament/search` (`query`, `limit=20`,
  `offset` paging; total match count from the `x-total-count` response
  header, falling back to the page size when absent). Queries < 2 chars
  stay Idle; a "Load more (N more)" row appends the next page.
- **Version gate**: new pure `SpoolmanInfo?.supportsExternalDbSearch()`
  — search endpoint needs Spoolman ≥ 0.26.0. Fails closed on null
  receiver / null / blank / non-numeric / pre-release versions.
  Unsupported → picker shows "Database search requires Spoolman v0.26+".
  `MainViewModel` runs a one-shot `testConnection()` probe once the
  server is Reachable but `serverInfo` is still null, so an unknown
  version resolves instead of sticking.
- **Pick behavior**: if an inventory filament already carries the same
  `external_id` → delegate to existing-filament selection (no duplicate).
  Else prefill the form (`FormMapping.applyExternalFilament`) and clear
  both selections. Origin chip "From SpoolmanDB" (`InputChip`, testTag
  `filament-origin-chip`); its dismiss-X clears ONLY the external linkage
  — the prefilled values stay for manual editing.
- **Save-time stamping + dedup** (the user's explicit requirement):
  `FormState.externalFilamentId/externalDiameterMm` →
  `SaveToSpoolmanInput.externalFilamentId/diameterMm` →
  `NewFilamentRequest` → repository stamps `external_id` on the filament
  POST and on the vendor POST (vendor external_id = manufacturer name
  when importing, Spoolman's convention for external-DB vendors). Before
  POSTing, exact `external_id` dedup runs against the in-memory cache
  (fast path) AND the LIVE `listFilaments` result (backfills the cache),
  both ahead of the existing fuzzy matcher.
- **Multi-colour rows** (`color_hexes`) render a two-colour swatch in the
  picker; the form still takes the first hex (see Q-U22-4).

## §1a Out of scope

- **Spool picker / Material / Brand / Color pickers** — the SpoolmanDB
  section is Filament-picker only; the others stay as-is (U21 scope).
- **Full multi-colour support** — README's planned "Multi-colour hex"
  item; U22 ships the first-hex approximation + swatch only.
- **A bundled / cached catalogue fallback for servers < 0.26** — rejected
  (Q-U22-3); no offline catalogue, no periodic sync.
- **Editing `external_id` on existing records** — import stamping happens
  at create only; no PATCH path, no unlink-once-imported flow.
- Spool record fields from the DB entry beyond weight/spool_weight/density
  (e.g. `spool_type`, `finish`, `pattern`, `translucent`, `glow`,
  `multi_color_direction` are parsed on the DTO but not surfaced).

---

## §2 Design decisions

### LOCKED answers (Part 2 Q&A)
- **Q-U22-1 = Integrated into the picker, not a separate sheet.** The
  database search lives as a section inside the existing Filament picker
  dropdown (pinned footer below the local rows), driven by the picker's
  existing search field. A dedicated "database browser" sheet was
  rejected: two search affordances for one job, and the picker is where
  the filament decision already happens.
- **Q-U22-2 = Prefill on pick, not immediate import.** Picking a DB entry
  seeds the form (identity, temps, expander fields); the user can tweak
  anything; records are created only on Save, via the normal
  create-filament+spool chain. Immediate POST-on-pick was rejected: it
  would bypass the app's create-on-save model and create orphans when the
  user backs out. Already-imported entries short-circuit to
  existing-filament selection so a duplicate is never created either way.
- **Q-U22-3 = Feature detection + hint, not a legacy full-catalog
  fallback.** `supportsExternalDbSearch()` gates the whole section; on a
  server < 0.26 (or unknown) the picker shows the muted hint
  "Database search requires Spoolman v0.26+". No bundled catalogue, no
  bulk-sync fallback — SpoolmanDB search is a server feature; shipping a
  stale local copy of ~9.7k entries would be a second data source to
  maintain.
- **Q-U22-4 = Multi-colour → first hex.** Form colour =
  `color_hex ?: color_hexes.first()`, canonicalised like every other
  colour entry. Multi-colour entries additionally render a two-colour
  swatch in the DB result rows so they read at a glance. Full
  multi-colour support stays on the planned-features list.
- **Q-U22-5 = Variant = name minus material word.** The DB `name` is the
  product name ("PolyLite PLA"); variant = name with ONE case-insensitive
  word-boundary occurrence of the material removed, whitespace collapsed
  ("PolyLite PLA"/PLA → "PolyLite"; name == material → null). Mirrors how
  `FormMapping.fromFilament` already derives variant for inventory rows.

### Shared mechanics (locked with the Q&A)
- **D1 — Debounce + paging.** 300 ms debounce
  (`EXTERNAL_SEARCH_DEBOUNCE_MS`), query trimmed, < 2 chars → Idle with
  the in-flight job cancelled. Page size 20; "Load more" requests
  `offset = items.size`, merges `distinctBy { id }`, keeps
  `max(current, fetched)` as the total; in-flight load-more is a no-op.
- **D2 — Total count.** `x-total-count` header (OkHttp lookup is
  case-insensitive), `?.trim()?.toIntOrNull() ?: items.size`. Servers
  without the header (0.26.x — offset paging + header landed in 0.27.0)
  degrade to one page with no Load-more row.
- **D3 — Label.** `external_db_name` from `/info`, non-blank, else
  "SpoolmanDB"; flows through `MainViewModel.externalDbLabel`
  (distinctUntilChanged) into the section heading, the "No matches in X"
  row, and the origin chip "From X".
- **D4 — Origin chip.** `InputChip` with dismiss-X;
  `onExternalSelectionCleared` nulls only `externalFilamentId` +
  `externalDiameterMm` on the form — the prefill stays. Selecting any
  filament or spool afterwards drops the linkage too (a selection means
  the save path no longer creates).
- **D5 — Diameter carried silently.** The DB diameter rides
  `FormState.externalDiameterMm` → `SaveToSpoolmanInput.diameterMm` →
  `NewFilamentRequest.diameterMm`; repository precedence = form override
  ?: external value ?: `DEFAULT_DIAMETER_MM` (1.75). No new UI field.
- **D6 — One-shot version probe.** `serverInfo` only refreshes inside
  `testConnection()`, so `MainViewModel.init` launches: wait for
  `ConnectivityState.Reachable`, if `serverInfo` still null → one
  `testConnection()` attempt; failures swallowed (gate stays
  Unsupported — fails closed).

---

## §3 Files touched (scope by layer)

**Data / remote**
1. `data/remote/spoolman/SpoolmanApi.kt` — `searchExternalFilaments`
   (`query`/`limit`/`offset`, raw `Response` for the header).
2. `data/remote/spoolman/SpoolmanRepository.kt` — `serverInfo`
   StateFlow (refreshed by `testConnection`), `searchExternalFilaments`
   (limit 20 default, `x-total-count` parse + fallback), new
   `performHttpResponse` (header-aware sibling of `performHttp`),
   `supportsExternalDbSearch()` top-level gate, `resolveOrCreateVendor` /
   `resolveOrCreateFilament` stamp `external_id` on their POSTs and run
   exact-`external_id` dedup (cache shortcut + live list check that
   backfills the cache) ahead of the fuzzy matcher, diameter precedence
   chain.
3. `data/remote/spoolman/SpoolmanRequests.kt` — `external_id` on
   `CreateVendorRequest` + `CreateFilamentRequest` (nullable, omitted
   when null).

**Domain**
4. `domain/models/SpoolmanModels.kt` — `ExternalFilament` DTO
   (snake_case via `SerializedName`; every field except
   id/manufacturer/name/material nullable), `ExternalFilamentPage`,
   `SpoolmanInfo.external_db_name`, `external_id` on `SpoolmanFilament`
   + `SpoolmanVendor`.
5. `domain/usecases/NewFilamentRequest.kt` — `externalFilamentId` +
   `diameterMm`, threaded through `fromForm(...)`.
6. `domain/usecases/SaveToSpoolmanUseCase.kt` — `SaveToSpoolmanInput`
   gains `externalFilamentId` + `diameterMm`, forwarded into
   `NewFilamentRequest.fromForm`.

**UI state / ViewModel**
7. `ui/screens/main/ExternalFilamentSearchState.kt` — NEW
   `ExternalSearchUiState` (Idle / Searching / Results(hasMore) / Error /
   Unsupported).
8. `ui/screens/main/MainUiState.kt` — `FormState.externalFilamentId` +
   `externalDiameterMm`.
9. `ui/screens/main/MainViewModel.kt` — `externalDbLabel`,
   `externalSearchState` (fail-closed `combine` gate, initial
   Unsupported), `onExternalFilamentQueryChanged` (debounce / min-2 /
   gate check), `onExternalFilamentLoadMore`, `onExternalFilamentPicked`
   (inventory dedup → delegate, else prefill + clear selections +
   ambiguity/scan-suggestion reset), `onExternalSelectionCleared`, save
   input threading, selection-clears, one-shot probe (D6).

**UI components / screens**
10. `ui/components/LazyDropdownMenu.kt` — opt-in `footer` slot pinned
    below the rows (omitting it = today's render).
11. `ui/components/FilamentPicker.kt` — `ExternalDbSection` (divider +
    label + state-driven rows: gate hint / keep-typing / searching /
    error / results + "Load more (N more)"), `ExternalFilamentRow`
    (manufacturer · name + material, testTagged), `ExternalSwatch`
    (24dp, second colour layered bottom-end for multi-colour), search
    field forwarding, `closeAndResetQuery` also resets the external
    state, pick closes the dropdown like a local pick.
12. `ui/components/FilamentSection.kt` — origin `InputChip`
    ("From <label>", testTag `filament-origin-chip`, dismiss-X).
13. `ui/components/FilamentForm.kt` — new params forwarded to picker;
    `FormChange.ExternalFilamentPicked` / `ExternalSelectionCleared`.
14. `ui/screens/main/FormMapping.kt` — `applyExternalFilament` (material
    preset lookup with synthesis from DB temps+density, brand =
    manufacturer, colour = `color_hex ?: color_hexes.first()`,
    variant = `deriveExternalVariant`, temps min=max when present (null
    keeps preset seeds), density/weight/spool_weight, both selections
    cleared) + `deriveExternalVariant` (ONE material-word strip).
15. `ui/screens/main/MainScreen.kt` — collects the two new flows, wires
    callbacks + the two new `FormChange` branches.

**Not touched**: Spool picker, Material/Brand/Color pickers,
`PickerRanking`, `SpoolMatchScorer`, NFC stack, Settings.

---

## §4 Test plan

| Suite | # | Locks |
|---|---|---|
| `ExternalFilamentDtoTest` (NEW) | 5 | full/sparse snake_case mapping, color_hex vs color_hexes, external_id serialisation on filament + vendor POST bodies (omitted when null) |
| `SpoolmanRepositoryExternalSearchTest` (NEW) | 8 | x-total-count parse + absent-header fallback, limit/offset defaults + explicit paging, HTTP error mapping, no-URL short-circuit, `testConnection` publishes `serverInfo`, info stays null until probed |
| `SupportsExternalDbSearchTest` (NEW) | 10 | 0.27.1 / exact 0.26.0 supported, 0.25.9 not, future major yes, null version / null receiver / garbage / short / pre-release all fail closed, `external_db_name` parses alongside version |
| `SpoolmanRepositoryExternalFilamentCreateChainTest` (NEW) | 8 | cache-match reuse without POST, stale-cache + live-list match reuse + backfill, no-match POSTs carry `external_id`, request diameter wins / 1.75 fallback, vendor POST carries `external_id` = vendor name, existing name-match vendor skips POST, non-external saves carry no `external_id` |
| `NewFilamentRequestTest` (NEW) | 3 | `fromForm` threads external fields, defaults stay null |
| `SaveToSpoolmanUseCaseExternalThreadingTest` (NEW) | 2 | `SaveToSpoolmanInput.externalFilamentId/diameterMm` ride the create request; absent fields keep null |
| `MainViewModelExternalSearchTest` (NEW) | 16 | 300 ms debounce + retype collapse, <2 chars Idle, error state, load-more append + id-dedupe + no-op-when-done, pick w/o match prefills + sets ids + clears selections, pick w/ inventory match delegates, Filament/Spool selection clears linkage, save input threading, Unsupported gate + fail-closed-unknown, label fallback + configured |
| `FormMappingTest` (+7) | 7 | applyExternalFilament: seeds identity/overrides/ids + clears selections, variant strips exactly ONE material word (unit + end-to-end), colour prefers color_hex + canonicalises, present temps min=max, null temps keep preset seeds, unknown material synthesises from DB temps + density |

**Δ target**: 588 → 647 (Δ +59). No Compose UI tests (repo has no
Compose/Robolectric infra; all tests are pure JVM — same call as U21 S7).

---

## §5 Build gates

- [x] `compileDebugKotlin` ✅
- [x] `testDebugUnitTest` ✅ **647 / 647** (80 suites, 0 failures; Δ +59)
- [x] `assembleDebug` ✅
- [ ] **On-device install gate — PENDING.** Smoke: search + Load more in
      the DB section, pick → prefill → Save → Spoolman web UI shows the
      database-import highlight on filament + vendor, re-pick of the same
      entry selects the existing filament, < 0.26 server shows the hint.
- [x] **Version NOT bumped** — held at the current level
      (versionCode 113 / versionName 2.3.1 as of the v2.3.1 CameraX
      page-size fix); batch-release policy per U20/U21 precedent.

---

## §6 Research grounding (Spoolman upstream)

- External filament DB support shipped in Spoolman **v0.18.0**
  (2024-05-20); the search endpoint
  `GET /api/v1/external/filament/search` shipped in **v0.26.0**; offset
  paging + the `x-total-count` header in **v0.27.0** — hence the ≥ 0.26.0
  gate and the header-absent fallback (D2).
- Upstream catalogue: **Donkie/SpoolmanDB** (~9.7k filament entries),
  synced to the Spoolman server hourly; the server proxies searches, so
  the app never talks to the catalogue directly.
- Spoolman's official Svelte client uses the same search + paging for its
  import dialog and dedups imports by `external_id` — the vendor
  `external_id` = manufacturer-name convention and the filament-record
  `external_id` highlighting in the web UI are upstream behavior this
  unit mirrors.

## §7 Decision log

| Decision | Source | Section |
|---|---|---|
| Integrated into picker vs separate sheet | Q-U22-1 | §2 |
| Prefill on pick vs immediate import | Q-U22-2 | §2 |
| Detection + hint vs full-catalog fallback | Q-U22-3 | §2 |
| Multi-colour → first hex | Q-U22-4 | §2 |
| Variant = name minus material word | Q-U22-5 | §2 |
| Debounce 300 ms / limit 20 / offset paging | D1–D2 | §2 |
| external_id save-stamping (web UI highlight) | user requirement | §1, §2 D5 |
| Version held, batched bump | U20/U21 precedent | §5 |
