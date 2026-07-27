# U20 — Picker surfacing (scan-time reorder + filament→spools) — Code Gen summary

**Unit**: U20 (UI-49 reframed + UI-52). Type-to-search (UI-48) split to a later unit.
**Version**: **HELD at versionCode 111 / 2.2.1** per user direction — do not bump
per-unit; the version will be raised once for a batch of units when we release
together. (Part 2 briefly bumped to 112/2.3.0, then reverted.)
**Tests**: 517 → **555** (Δ +38). **Install gate PASSED** on-device
(moto g stylus 2025 / Android 16): F3 (B1–B4) + F2 rank order verified. See below.

## Post-Part-2 fix — suggestion ORDER (on-device)

The first on-device F2 pass surfaced matches in the **picker's default sort**
(id-desc), not match quality (user saw Bambu-white / Elegoo-black / Elegoo-red
for a red-Elegoo tag). Root cause: `PickerRanking.partition` preserved input
order within the floated group. **Fix**: state changed `Set<Int>` → ordered
`List<Int>` (scorer rank order), spool list ordered by its filament's rank, and
new `PickerRanking.partitionRanked` floats the group by rank. Both pickers use it;
F3's set shares rank 0 (keeps normal sort among a filament's spools). Regression
test `suggestions are ordered best-match first` reproduces the exact case
(red-Elegoo → red, black, white). Δ +4 tests (3 partitionRanked + 1 regression).

**Note (kept intentionally):** selecting a *spool* also sets `selectedFilamentId`
(FormMapping.fromSpoolman), so reopening the Spool picker floats that filament's
sibling spools. User reviewed and **likes this** ("i actually like all siblings
together") — left as-is, no gating added.

## What shipped (Code Gen Part 2, S1–S11)

Two rendering-only picker enhancements sharing one pure core. Overriding
invariant held throughout: **passive hints only — floating happens when the user
opens a picker; nothing auto-selects; no read/prefill/pair/save/write behaviour
changed** (§0 of the plan).

### F2 — scan an unmapped tag → float matches (UI-49)
- New pure `domain/primitives/SpoolMatchScorer.kt`: additive score over
  **material / brand / color only** (NO temps, Q-U20-2). Material + brand exact
  (case-insensitive, trimmed); color = RGB-Euclidean closeness above a 0.5 floor.
  Suggested = score > 0, sorted descending (tie-break ascending filamentId),
  **capped at 3** (`SUGGESTED_CAP`, Q-U20-3).
- New `ColorHexCodec.toRgb(hex)` for the color distance (canonicalise → decode;
  null on non-6-hex).
- `MainViewModel.computeScanSuggestions(payload)` scores `filaments.value`, maps
  the suggested filament ids to their unarchived spools' ids, and stores both on
  state. Wired into `applyResult` `BlankForm` (vendor `parsedHint`) and
  `PrefillFromTag` (OpenSpool-no-match) branches.

### F3 — select a filament → float its spools (UI-52)
- `SpoolmanDropdown` computes the floated set: filament-selected →
  that filament's unarchived spools (deterministic); else scan set (F2). F3 wins
  over F2 (precedence D9). No main-screen hint text (Q-U20-4).

### Shared rendering
- New pure `domain/primitives/PickerRanking.kt`: `partition(rows, isSuggested)` →
  suggested-first (order preserved) + `suggestedCount`.
- `LazyDropdownMenu` gained an opt-in `dividerBefore: ((T)->Boolean)?` — a thin
  `HorizontalDivider` before the first non-suggested row, **no header label**
  (Q-U20-1). Omitting callers render exactly as before (Material/Brand/Color on
  PinnedActionMenu untouched).
- `SpoolmanDropdown` + `FilamentPicker` pass `dividerBefore` keyed on the
  boundary row; `FilamentForm` → `FilamentSection` → `FilamentPicker` thread
  `scanSuggestedFilamentIds`.

### State + clearing
- `MainUiState`: `scanSuggestedSpoolIds: List<Int>` + `scanSuggestedFilamentIds:
  List<Int>` (**ordered, best-match first**). Set on unpaired reads; cleared on
  read-start, paired read (`PrefillFromSpoolman`), manual `onSpoolSelected`, and
  manual `onFilamentSelected`.

## New / modified files
**New prod**: `PickerRanking.kt` (partition + partitionRanked), `SpoolMatchScorer.kt`.
**New test**: `PickerRankingTest.kt` (10), `ColorHexCodecTest.kt` (8),
`SpoolMatchScorerTest.kt` (11), `MainViewModelSuggestionTest.kt` (9).
**Modified prod**: `ColorHexCodec.kt` (+toRgb), `LazyDropdownMenu.kt`
(+dividerBefore), `MainUiState.kt` (+2 ordered lists), `MainViewModel.kt` (scorer
wiring + rank-ordered lists + clears), `MainScreen.kt` (SpoolmanDropdown
float+divider via partitionRanked + call site), `FilamentPicker.kt`
(float+divider via partitionRanked), `FilamentSection.kt` + `FilamentForm.kt`
(thread the list). **`app/build.gradle.kts` version NOT changed** (held per user).

## Build matrix (all green)
- `compileDebugKotlin` ✅
- `testDebugUnitTest` ✅ **555/555**
- `assembleDebug` ✅ ~70 MB
- `assembleRelease` ✅ R8 (version held 111/2.2.1)
- `bundleRelease` ✅ AAB
(Full assemble/bundle last run green at the 112/2.3.0 bump before revert; version
now held at 111/2.2.1 for a batched bump. Re-run at batch-release time.)

## S12 install gate — PASSED (moto g stylus 2025 / Android 16)
- **F3 (B1–B4) ✅**: select a filament → Spool picker floats its unarchived
  spools above a divider; archived excluded; never auto-selects; clear returns to
  normal sort. Sibling-spool float on spool-pick kept intentionally (user likes it).
- **F2 rank ✅**: unpaired read floats top-3 material/brand/color matches, **best
  first** (verified with the red-Elegoo case after the ordering fix).
- Vendor-tag A2 (no-match → no float), C2 (paired → resolves, no float), and D
  (read/save/write regression) not separately re-run on-device; A2/clear paths +
  the passive invariant are covered by MainViewModelSuggestionTest.

## Memories
[[reference_adb_path]], [[feedback_no_em_dash]], [[feedback_no_offset_modifier]].
