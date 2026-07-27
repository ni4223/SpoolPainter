# U21 — Type-to-search (UI-48) — Code Generation summary

**Status**: DONE. S1–S9 complete; on-device install gate PASSED. Version
bumped **112 / 2.3.0** (the device held 112 from U20, so 111 was a downgrade;
per user, bumped rather than uninstalled). Also folded in a variant signal for
the U20 scan-time float — see "Variant follow-up" below; that change was
**amended into the U20 commit**, U21's commit carries search + the version bump.

## What shipped

Type-to-search box in the **Spool** (`SpoolmanDropdown`, `MainScreen.kt`) and
**Filament** (`FilamentPicker.kt`) picker popups. Filter-as-you-type,
case-insensitive substring over each row's display text + numeric id. Reuses
U20's `PickerRanking` and `LazyDropdownMenu`.

### Locked design (Part 2 Q&A, 2026-07-27)
- **Q-U21-1 = Drop float.** A non-blank query suppresses the U20 float + divider
  and shows a flat filtered list in normal sort. Blank query = today's float
  path, byte-for-byte.
- **Q-U21-2 = Row text + id.** Match target is a precomputed `searchText` =
  `"$primary $secondary"` per row; `secondary` already carries `#id`, so
  material / brand (vendor) / name / variant / id are all matched in one pass.
- **Q-U21-3 = Sticky row inside popup.** Search field is a header above the
  scrolling rows (rendered in a `Column` above the `LazyColumn` — not the
  experimental `stickyHeader`, which doesn't resolve in this Compose version).
  Anchor stays `readOnly`, still showing the selection.
- **Q-U21-4 = No autofocus.** Field renders unfocused; keyboard on tap only.
  Clear (X) provided when non-blank.
- **Q-U21-5 = Always show.** No row-count threshold.

### Invariant held (from U20 §0)
Rendering/filtering only. No auto-select, no read/prefill/pair/save/write, no
ViewModel flow change. Empty query renders exactly as today (proven by
`PickerRanking.filter` identity test + the unchanged `partitionRanked` path).

## Files touched
1. `domain/primitives/PickerRanking.kt` — pure `filter(rows, query, textOf)`
   (blank query = identity; trim + lowercase substring, order preserved).
2. `domain/primitives/PickerRankingTest.kt` — +6 filter tests (blank identity,
   case-insensitive match, no-match, trim, id-via-projection, empty rows).
3. `ui/components/LazyDropdownMenu.kt` — opt-in `header` slot (pinned above
   rows) + non-clickable "No matches" row when a header is present and the
   filtered list is empty. Omitting `header` = today's behavior.
4. `ui/components/PickerSearchField.kt` — NEW shared search TextField
   (leading search icon, trailing clear, ImeAction.Search, no autofocus).
5. `ui/components/FilamentPicker.kt` — query state, `searchText` on the row
   tuple, filter-vs-float branch, reset query on dismiss / select / clear.
6. `ui/screens/main/MainScreen.kt` (`SpoolmanDropdown`) — same wiring.

## Variant follow-up (U20 scorer, amended into U20)
User asked mid-gate whether the F2 heuristic float accounts for variant. Added
it to `SpoolMatchScorer`: query variant from the vendor tag `subtype`
("Basic"/blank ignored), candidate variant from `extra["variant"]`
(`FormMapping.decodeExtraVariant`, made `internal`). **Weight 1.0 (below color
2.0), lenient substring match** — variant is hand-typed free text so it only
breaks ties, never overrides material/brand/color. Belongs to U20's float, so
committed there (amend), not U21. +4 tests.

## Build
- `compileDebugKotlin` ✅
- `testDebugUnitTest` ✅ **565/565** (Δ +10 from 555: +6 PickerRanking.filter,
  +4 SpoolMatchScorer variant).
- Full `assemble*` / `bundle*` deferred to the batched release with the user.

## Not touched
`PinnedActionMenu` and the Material / Brand / Color pickers (short curated
lists, scroll-only per UI-48). `SpoolMatchScorer`, `ColorHexCodec`, ViewModel,
use cases, repo, NFC — untouched. No fuzzy match (substring is the v1 bar).
