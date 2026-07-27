# U20 — Picker surfacing: scan-time reorder + filament→spools reorder — Code Generation Plan

**Unit**: U20 (UI-49-reframed + UI-52). **Type-to-search (UI-48) split to its own
follow-up unit** per user direction 2026-07-27.
**Stage**: CONSTRUCTION — Code Generation (Part 1 plan)
**Per-unit gate**: FD / NFR-R / NFR-D / Infra-D **SKIP** (feature unit; design folded
into this Code Gen plan per established convention — same as U15/U17/U18/U19).
**Baseline**: v2.2.1 shipped (versionCode 111); `v2` in sync with `origin/v2`;
517/517 tests green.
**Proposed version bump**: versionCode 111 → **112**, versionName 2.2.1 → **2.3.0**.

---

## §0 Overriding invariant — enhance, don't change behaviour

**Both features are RENDERING-ONLY enhancements inside the picker popup, shown
when the user OPENS the dialog. Nothing auto-selects. No existing flow changes.**
- The suggested-id sets (F2 scan matches, F3 the selected filament's spools) are
  **passive hints**. They never touch `selectedSpoolId`/`selectedFilamentId`, the
  form, the read/prefill path, pairing, save, or write.
- The reorder is applied by the picker **at open time** — the user opens the
  Spool/Filament dropdown and sees a "Suggested" / "This filament" group floated
  to the top. Close without picking → absolutely nothing changed.
- Read, prefill, pairing, save, write behave exactly as v2.2.1 today.

Acceptance bar for every step: if any existing test's selection/flow behaviour
changes, the implementation is wrong.

---

## §1 Scope (two rendering enhancements, one shared helper)

### F2 — UI-49 (reframed): scan an unmapped tag → reorder both pickers
When a tag is Read that is **not** already paired (vendor tag, or OpenSpool tag
with no `card_uids` link and no resolvable spool id), run a heuristic over the
inventory and remember which spools/filaments score well. **When the user opens**
the Spool or Filament picker, those good matches are **floated to the top under a
"Suggested" section**, the rest of the list below in normal sort order. **No
confirm chip. No auto-select. No reorder unless the user opens the picker.** Only
matches above a confidence floor are floated; if nothing qualifies, the pickers
look exactly as today.

### F3 — UI-52 (new): select a filament → float its spools in the Spool picker
When a filament is selected, remember its **unarchived** spools (deterministic:
spool carries `filament.id`). **When the user opens** the Spool picker, those
spools float to the top under a **"This filament" section**, with an inline hint
near the picker ("This filament has N spools"). No scorer. **Never auto-select.**
Selecting the filament does not change the spool selection.

### Explicitly OUT of U20
- **UI-48 type-to-search box** — split to its own unit (see §8). Keeps `LazyDropdownMenu`'s
  generic contract untouched here; no search field, no empty-result guard.
- Material / Brand / Color pickers — unaffected.
- Any auto-select or flow change (§0).

---

## §2 Design decisions

### LOCKED answers (Part 2 Q&A, 2026-07-27)
- **Q-U20-1 = Float, no header.** Floated group sits at the top with a thin
  divider before the rest; NO "Suggested"/"This filament" label row. Applies to
  both F2 and F3. → simplifies D2 (no header-label machinery; just a divider
  between the two groups).
- **Q-U20-2 = Heuristic, use whatever signals we have — but NOT temps.** Additive
  score over material, brand, color (temps explicitly excluded per user
  2026-07-27 "i dont care about temps for the search though"); missing/unknown
  signals contribute nothing (graceful degradation). Rank descending, drop
  no-signal candidates, show top 3. → D3 below.
- **Q-U20-3 = Cap at 3.** `SUGGESTED_CAP = 3` (user 2026-07-27 "just 3").
- **Q-U20-4 = No F3 hint.** No main-screen cue text; the reorder inside the Spool
  picker is the whole feature. → drops the "This filament has N spools" hint.
- **Q-U20-5 = N/A** (no header → no count).

### Shared core
- **D1 — One pure helper.** New `PickerRanking` object in `domain/primitives/`
  (JVM-testable, parallels `ColorSampling`): `partition(rows, isSuggested)` →
  `(suggested, rest)` preserving each group's existing sort order. Powers both
  F2 and F3 reorders. (Filter/search is deferred with UI-48.)
- **D2 — Two-group rendering, divider only (Q-U20-1).** `LazyDropdownMenu` gains
  an **opt-in** way to render a **divider between two groups** (no header label).
  **Recommend**: keep `items: List<T>` and add an optional
  `dividerBefore: ((T) -> Boolean)?` — the host renders a thin divider row when
  the callback returns true for an item (i.e. the first item of the "rest" group).
  Callers that don't pass it render exactly as today (zero change to
  Material/Brand/Color on `PinnedActionMenu`, or any omitting caller).

### F2 — scan-time heuristic
- **D3 — Pure scorer (Q-U20-2: material/brand/color only, NO temps).** New
  `SpoolMatchScorer` object in `domain/primitives/` (parallels `ColorSampling`):
  given a decoded query (material, brand, colorHex — any of which may be null) +
  candidate filaments, return each candidate's additive score. Temps are NOT a
  signal. Each present signal that matches contributes; absent signals contribute
  nothing (graceful degradation, so a material+color-only tag still ranks).
  Suggested set = candidates with score > 0 (at least one real signal match),
  sorted descending, **capped at `SUGGESTED_CAP = 3`** (Q-U20-3). Suggested weights
  (starting point, tune on-device at the install gate): material match strong,
  brand match strong, color = inverse of RGB distance (closer = more). Fully
  unit-tested.
- **D4 — Color distance.** Add `ColorHexCodec.toRgb(hex): Triple<Int,Int,Int>?`
  (canonicalise then decode; none exists today). RGB Euclidean → 0..1 closeness.
- **D5 — Score at the filament grain.** A spool's identity is its filament; score
  filaments, then a suggested filament implies its (unarchived) spools are
  suggested. One pass yields both a suggested-filament-id set and a
  suggested-spool-id set.
- **D6 — Where computed.** `MainViewModel.applyResult`: `BlankForm` branch
  (`MainViewModel.kt:992-1052`, vendor-`parsedHint` sub-case 1007-1013) AND
  `PrefillFromTag` (977-991, OpenSpool-no-match). Compute id sets against
  `spoolman.spools.value`; store on state. Cleared on a paired read
  (`PrefillFromSpoolman`), on a new read, and on manual spool/filament select.

### F3 — filament→spools
- **D7 — Deterministic set, no hint (Q-U20-4).** When `form.selectedFilamentId
  != null`, the Spool picker's floated set = unarchived spools with
  `filament.id == selectedFilamentId`. Recomputed on filament select. **No
  main-screen hint text** — the reorder inside the Spool picker (floated group +
  divider) is the whole feature.

### State + precedence
- **D8 — New UI state.** Add to `MainUiState`: `scanSuggestedSpoolIds: Set<Int>`,
  `scanSuggestedFilamentIds: Set<Int>` (F2). F3's set is derived from
  `selectedFilamentId` + `spools` at render time (no stored field needed, or a
  small computed projection). Empty sets = no floated group.
- **D9 — Spool-picker precedence** (both can float spools):
  1. Filament selected → **F3** (float that filament's spools).
  2. Else scan suggestion active → **F2** (float scan matches).
  3. Else → normal sorted list.
  Filament picker only ever floats F2's scan matches.
- **D10 — No em dash / no header label.** Floated group has NO label row
  (Q-U20-1), just a divider before the rest. No new user-facing copy strings in
  the picker; [[feedback_no_em_dash]] still applies to anything added.

---

## §3 File impact (draft — refine in Part 2)

**New production**
1. `domain/primitives/PickerRanking.kt` — pure partition (D1).
2. `domain/primitives/SpoolMatchScorer.kt` — pure weighted scorer (D3/D5).

**Modified production**
3. `domain/primitives/ColorHexCodec.kt` — add `toRgb` (D4).
4. `ui/components/LazyDropdownMenu.kt` — opt-in `dividerBefore` two-group render (D2).
5. `ui/screens/main/MainScreen.kt` — `SpoolmanDropdown` float+divider wiring (F2/F3).
6. `ui/components/FilamentPicker.kt` — float+divider wiring (F2).
7. `ui/screens/main/MainViewModel.kt` — scorer call in scan branches, set/clear
   scan sets (D6); no selection/flow change (§0).
8. `ui/screens/main/MainUiState.kt` — `scanSuggested*Ids` fields (D8).
9. `app/build.gradle.kts` — versionCode 112 / versionName 2.3.0.

**New tests**
10. `PickerRankingTest.kt` — partition grouping, order preserved, empty suggested set.
11. `SpoolMatchScorerTest.kt` — material/brand/color weighting (no temps), score>0
    inclusion, cap at 3, descending order, tie-break, empty inventory, null fields.
12. `ColorHexCodecTest.kt` (extend/new) — `toRgb` valid / `#`-prefix / short / null.
13. `MainViewModelSuggestionTest.kt` — scan sets on unpaired vendor + OpenSpool-no-match
    reads; **cleared** on paired read / manual select / new read; **no** selection
    or form change (guards §0); F3 set derives from filament select.

---

## §4 Step plan (execute in Part 2)

- [ ] S1. `PickerRanking.kt` + `PickerRankingTest.kt` (pure).
- [ ] S2. `ColorHexCodec.toRgb` + test (D4).
- [ ] S3. `SpoolMatchScorer.kt` + `SpoolMatchScorerTest.kt` (D3/D5).
- [ ] S4. `LazyDropdownMenu` opt-in `dividerBefore` two-group render (D2, Q-U20-1).
- [ ] S5. `MainUiState` scan-suggested-set fields (D8).
- [ ] S6. `MainViewModel`: scan-branch scorer + set/clear (D6). Assert no selection change (§0).
- [ ] S7. `SpoolmanDropdown`: float+divider rendering + precedence (D9); no F3 hint (F2/F3).
- [ ] S8. `FilamentPicker`: float+divider rendering (F2).
- [ ] S9. `MainViewModelSuggestionTest` (D6/D8/D9 + §0 guards).
- [ ] S10. Version bump (112 / 2.3.0).
- [ ] S11. Build matrix: `compileDebugKotlin` / `testDebugUnitTest` / `assembleDebug` / `assembleRelease` / `bundleRelease`.
- [ ] S12. Install gate (moto g stylus 2025 / Android 16): unpaired vendor/OpenSpool
  read → open both pickers → top-3 material/brand/color matches floated above a
  divider (nothing floated when no signal matches); select a filament → open Spool
  picker → its unarchived spools floated above a divider (no main-screen hint);
  already-paired read → no floated group;
  **regression: read/prefill/save/write unchanged, nothing auto-selected.**

**Test target**: 517 → ~535 (Δ ~+18: PickerRanking ~5, SpoolMatchScorer ~8,
ColorHexCodec.toRgb ~3, VM suggestions ~5, minus none).

---

## §5 Part 2 Q&A — ALL ANSWERED 2026-07-27 (see §2 "LOCKED answers")

- **Q-U20-1** = Float, no header (divider only).
- **Q-U20-2** = Heuristic over material/brand/color only (NO temps).
- **Q-U20-3** = Cap at 3.
- **Q-U20-4** = No F3 main-screen hint (reorder only).
- **Q-U20-5** = N/A (no header → no count).

Plan fully specified. Ready for Code Gen Part 2 on approval.

---

## §6 Traceability

| Requirement | Source | Plan |
|---|---|---|
| F2 scan-time reorder both pickers | UI-49 reframed, ui-followups.md:1703 | §1, D3-D6/D8/D9, S2/S3/S6 |
| F3 filament→spools reorder | UI-52 (new), user 2026-07-27 | §1, D7/D9, S6/S7 |
| Rendering-only, no auto-select | user 2026-07-27 | §0, D6, S6/S9 |
| Search split out | user 2026-07-27 | §1 OUT, §8 |
| No em dash | [[feedback_no_em_dash]] | D10 |
| Pure-helper pattern | ColorSampling.kt | D1, D3 |

## §7 Resume

Part 1 plan authored 2026-07-27; revised same session after scope discussion:
(a) surfacing is reorder-only, no chip; (b) filament→spools added as F3/UI-52;
(c) spool-select behaviour dropped; (d) **enhancement is rendering-only at
picker-open time, no behaviour change, no auto-select** (§0); (e) **type-to-search
split out to its own unit** (§8). Awaiting stage-gate approval + Part 2 Q&A
(Q-U20-1..5) before Code Gen Part 2 execution.

## §8 Deferred to a separate unit — UI-48 type-to-search

Type-to-search box in the Spool + Filament picker popups (filter-as-you-type).
Split out of U20 per user direction 2026-07-27 to keep this unit's scope on the
two rendering-only reorder features. Will reuse `PickerRanking` (extend with a
`filter(query, rows, textOf)` mode) and require the `LazyDropdownMenu` search-slot
+ empty-result-guard work that U20 intentionally does not touch. Tracked as UI-48.
