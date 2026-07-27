# U21 — Type-to-search the spool / filament pickers (UI-48)

**Unit**: U21. Split out of U20 (see U20 plan §8) per user 2026-07-27
("we should do search write thing seprate"). Reuses U20's `PickerRanking`
(extended with a filter mode) and the `LazyDropdownMenu` two-group render.

**Per-unit gate**: Functional Design / NFR-R / NFR-D / Infra-D **SKIP**
(rendering-only feature unit; design folded into this Code Gen plan, mirroring
U20). Code Generation runs Part 1 (this plan) → Part 2 (execution).

---

## §0 Overriding invariant (carried from U20)

Search is a **rendering / filtering** concern only. Typing filters what the
picker *shows*; it never auto-selects, never reads/prefills/pairs/saves/writes,
and changes no ViewModel flow. A picker with an empty query renders **exactly**
as it does today (float + divider from U20 intact). Closing the picker clears
the query.

---

## §1 In scope

- A **search field** at the top of the **Spool** picker (`SpoolmanDropdown`,
  `MainScreen.kt`) and the **Filament** picker (`FilamentPicker.kt`) popups —
  the two `LazyDropdownMenu` callers that render 50+ rows.
- **Filter-as-you-type**: case-insensitive substring match over each row's
  display text (the same `primary` + `secondary` strings already built per
  row) **plus the numeric id** (spool id / filament id) so "#123" or "123"
  finds a row.
- **Empty-result guard**: when the query matches nothing, the popup shows a
  single non-clickable "No matches" row instead of an empty list.
- Reuse: `PickerRanking` gains a pure `filter(rows, query, textOf)` mode;
  `LazyDropdownMenu` gains an opt-in **sticky search-slot** header.

## §1a Out of scope

- **Material / Brand / Color pickers** (`PinnedActionMenu`) — short curated
  lists, stay scroll-only (UI-48: "Material/Brand lists are short"). No search
  affordance added there this unit.
- **Fuzzy / typo-tolerant match** — case-insensitive substring is the v1 bar
  (UI-48 "fuzzy match is a nice-to-have but not required for the first cut").
- Any auto-select / flow change (§0).
- Sort changes — filtered results keep the picker's existing sort order.

---

## §2 Design decisions

### LOCKED answers (Part 2 Q&A, 2026-07-27)
- **Q-U21-1 = Drop float, flat filtered list.** While the query is non-empty,
  the floated group + divider disappear; the picker shows a flat filtered list
  in its normal sort. Search intent overrides the passive U20 surfacing.
  → D4 takes branch A.
- **Q-U21-2 = Row text + id.** Match target = row `primary` + `secondary` text
  + numeric id, folded into one precomputed `searchText` per row. Covers
  material, brand (vendor), name, variant, and #id in one case-insensitive
  substring pass. No new field-extraction code. → D3.
- **Q-U21-3 = Sticky row inside popup.** Search TextField pinned as the first
  row inside the popup (a `stickyHeader`); the anchor `OutlinedTextField` stays
  `readOnly` and keeps showing the current selection. → D2.
- **Q-U21-4 = No autofocus.** Render the search box unfocused; the soft
  keyboard only appears when the user taps into the field. Full list stays
  visible until they choose to search. Clear (X) still provided in the field.
- **Q-U21-5 = Always show.** The search box always renders at the top of the
  Spool + Filament popups regardless of row count. No `SEARCH_MIN_ROWS`
  threshold. → S6 drops the threshold gate.

### Shared pure core
- **D1 — `PickerRanking.filter`.** Add a pure
  `filter(rows: List<T>, query: String, textOf: (T) -> String): List<T>` that
  trims + lowercases the query and returns rows whose `textOf(row).lowercase()`
  contains it, preserving input order. Empty/blank query returns `rows`
  unchanged (identity — so no-query path is provably today's list). Fully
  unit-tested. Kept Android-free like the rest of `PickerRanking`.

### LazyDropdownMenu search-slot
- **D2 — Opt-in sticky header.** `LazyDropdownMenu` gains an optional
  `header: (@Composable () -> Unit)? = null` rendered as a `stickyHeader`
  above the rows (stays pinned while the list scrolls). Callers that omit it
  render exactly as today. The search TextField + its state live in the
  **caller** (Spool / Filament picker), passed in via this slot — the menu
  stays generic (no search-specific types leak into it), consistent with how
  U20 kept `dividerBefore` generic.
- **D2a — Empty-result row.** When `items` is empty *because a query filtered
  everything out*, the caller passes a one-item list sentinel OR (simpler) the
  menu renders a non-clickable "No matches" row when `items.isEmpty()` **and a
  header is present** (a header means the popup should stay open for the user
  to edit their query). Without a header, empty items keeps today's behavior
  (popup returns early / renders nothing). LOCK the mechanism in Part 2.

### Caller wiring (Spool + Filament)
- **D3 — Query state.** Each caller holds `var query by remember`. On the
  cached row list, apply `PickerRanking.filter(rows, query) { it.searchText }`
  where `searchText` is a precomputed `"$primary $secondary #$id"` folded into
  the existing `*RowDisplay` tuple (one more cached field, no per-keystroke
  string building beyond the lowercase contains).
- **D4 — Interaction with U20 float (Q-U21-1 = A).** When `query` is non-empty,
  bypass `partitionRanked` so the divider disappears and the flat filtered list
  shows in normal sort. Empty query → today's `partitionRanked` float path,
  unchanged.
- **D5 — Reset on close.** Clearing `expanded` (dismiss, select, or clear)
  resets `query = ""` so the next open starts fresh.
- **D6 — No em dash in copy.** Only new user-facing string is the empty-result
  label ("No matches") and the search placeholder ("Search spools" /
  "Search filaments"). No em dashes ([[feedback_no_em_dash]]).

---

## §3 Files touched

1. `domain/primitives/PickerRanking.kt` — add pure `filter` (D1).
2. `domain/primitives/PickerRankingTest.kt` — filter cases (D1).
3. `ui/components/LazyDropdownMenu.kt` — opt-in `header` stickyHeader +
   empty-result row (D2, D2a).
4. `ui/components/FilamentPicker.kt` — search field + query state + filter (D3–D5).
5. `ui/screens/main/MainScreen.kt` — `SpoolmanDropdown` search field + query
   state + filter (D3–D5).
6. (Tests) a UI/interaction test locking: box appears only past threshold,
   typing filters, "No matches" on no hit, close resets query, empty query ==
   today's list (invariant §0).

**Not touched**: `PinnedActionMenu`, Material/Brand/Color pickers,
`SpoolMatchScorer`, `ColorHexCodec`, ViewModel, any use case / repo / NFC.

---

## §4 Step checklist (Part 2)

- [x] S1. `PickerRanking.filter` + tests (pure). — +6 tests.
- [x] S2. `LazyDropdownMenu` opt-in `header` + empty-result row. Header rendered
      in a `Column` above the `LazyColumn` (pinned) — the experimental
      `stickyHeader` does not resolve in this Compose version. New shared
      `PickerSearchField.kt` mounts in the slot.
- [x] S3. `searchText` field on the Spool + Filament row-display tuples.
- [x] S4. `FilamentPicker` — search field, query state, filter, reset-on-close.
- [x] S5. `SpoolmanDropdown` — search field, query state, filter, reset-on-close.
- [x] S6. (Q-U21-5 = always show — no threshold gate; box renders unconditionally.)
- [x] S7. Tests — filter cases cover blank-query identity (§0 invariant),
      case-insensitive match, no-match, trim, id-via-projection. No Compose
      interaction test: repo has no Compose/Robolectric test infra (all tests
      are pure JVM); the filter logic is the whole behavior and is pure-tested.
- [x] S8. Build matrix: `compileDebugKotlin` ✅ / `testDebugUnitTest` ✅ 561/561.
- [x] S9. On-device install gate **PASSED** (moto g stylus 2025 / Android 16):
      search filters both pickers, "No matches" guard, clear + reset-on-close,
      and empty-query U20 float intact. **Version bumped 111 → 112 / 2.2.1 →
      2.3.0** — the device still held 112 from the U20 session, so 111 was a
      downgrade; per user direction bumped rather than uninstalled (this is the
      same 112/2.3.0 U20 had staged before its revert, now carried by U21).

## §6 Follow-up folded in — variant signal on the scan-time float (U20 scorer)

Mid-gate the user asked whether the F2 heuristic float (U20 `SpoolMatchScorer`)
accounts for **variant**. It did not (Q-U20-2 locked material/brand/color). We
added it:
- Vendor/branded tags decode their material modifier into `OpenSpoolPayload.
  subtype` (Bambu "Matte" etc.); "Basic"/blank ignored (same rule as
  `FormMapping.fromOpenSpool`). OpenSpool tags rarely carry a real subtype.
- Spoolman side reads `extra["variant"]` via `FormMapping.decodeExtraVariant`
  (made `internal`).
- **Weight 1.0, below color (2.0)** and matched **leniently** (case-insensitive
  substring either direction): variant is hand-typed free text in Spoolman, so
  exact match misses too often; it only breaks ties, never overrides a
  material/brand/color signal. Confirmed by `variant never outranks a color
  match` test.
- **This variant change belongs to U20's float feature**, so it was **amended
  into the U20 commit**, not U21. U21's commit carries search + version bump.
- Tests +4 (SpoolMatchScorer variant cases).

**Test target**: 555 → ~570 (Δ ~+15: PickerRanking.filter ~6, picker
interaction ~9). Tune during execution.

---

## §5 Decision log

| Decision | Source | Section |
|---|---|---|
| Split from U20 | user 2026-07-27 | U20 §8 |
| Spool + Filament only; Material/Brand/Color scroll-only | UI-48 | §1a |
| Substring (not fuzzy) v1 | UI-48 | §1a |
| Version held, batched bump | user 2026-07-27 (U20 close-out) | §4 |
