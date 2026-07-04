# U18 — Pinned-action dropdown menu (UI-46)

**Unit**: U18 (Pinned-action dropdown menu)
**Routing**: v2.2.x polish; closes UI-46 (regression surfaced during U17 install gate).
**Per-unit gate**: FD / NFR-R / NFR-D / Infra-D **SKIP** (small UI-polish unit;
design folds into this Code Gen plan, matching U15/U16/U17 convention).
**Stage**: Code Generation — Part 1 (plan). Awaiting approval before Part 2.

---

## §1 Problem

The Material / Brand / Color pickers put a high-value action at one end of the
menu:
- **Material / Brand**: "Other" (reveals an inline custom-name field).
- **Color**: "Color Wheel" + "Scan color".

U17 added `rememberDropdownDirection()` (`DropdownDirection.kt`) to keep that
action **field-adjacent**: top when the menu opens down, bottom when it flips
up. This fixed short menus (Color = 8 rows + action; Material = ~10 rows) but
**regressed the long one** — Brand has 30+ vendors, so when it flips upward the
action lands at the bottom of a long scroll and the user must scroll the whole
list to reach "Other".

**Root limitation**: inside one scrolling menu the action can be *always
visible* (pin at top) OR *field-adjacent* (near end) — not both on a long list.

## §2 Approach (design decisions fold in here)

Build a **shared pinned-action menu**: a custom `Popup`-based menu whose action
row is **pinned** (does not scroll) at the field-adjacent edge, with the item
list scrolling underneath in a `LazyColumn`. Used by all three pickers so it
stays one component (no duplication — this was an explicit UI-46 requirement and
a standing reuse preference from U17).

This extends the existing `LazyDropdownMenu` precedent (`Popup` +
`AnchorBelowPositionProvider` + `LazyColumn`) already used by the Filament and
Spool pickers, rather than inventing a new positioning scheme.

**Layout inside the popup** (a `Column`):
- Menu opens **down** → `[ pinned action row ] [ divider ] [ scrolling list ]`
  (action at top, adjacent to the field above it).
- Menu opens **up** → `[ scrolling list ] [ divider ] [ pinned action row ]`
  (action at bottom, adjacent to the field below it).

Either way the action row is fixed at the field-adjacent edge and the list
scrolls between it and the far edge. On a 30-vendor Brand list flipped upward,
"Other" now sits at the bottom edge next to the field and never scrolls away.

**Open-direction source**: reuse the *positioning* decision already computed by
the popup position provider. Today `DropdownDirection` measures separately from
`LazyDropdownMenu`'s `AnchorBelowPositionProvider`, so the two can disagree.
The new menu will decide up/down **once**, in the position provider, and report
it back so the pinned row lands on the correct edge — a single source of truth.

**Q-U18 open questions** (defaults pre-selected; confirm or override in Part 2):
- **Q-U18-1** — Menu max height. **[A] 320dp** (matches `LazyDropdownMenu`
  default) / [B] 400dp / [C] fraction of window height.
  **Recommended: A** (consistency with the existing custom menu).
- **Q-U18-2** — Should the pinned action row cast a subtle elevation/scrim so
  it reads as "pinned" over the scrolling list? **[A] No — a `HorizontalDivider`
  is enough** (matches current dividers) / [B] Yes — small shadow on the pinned
  row. **Recommended: A** (least visual noise; dividers already used).
- **Q-U18-3** — When a menu is short enough that the whole list + action fits
  (Color, short Material), keep the same pinned layout, or fall back to the old
  inline reorder? **[A] Same pinned layout everywhere** (one code path, simpler)
  / [B] Branch on item count. **Recommended: A** (uniform; pinning a row in a
  short list is harmless).
- **Q-U18-4** — Retire `DropdownDirection.kt` entirely (folded into the new
  menu), or keep it? **[A] Retire it** (no other callers — verified: only the 3
  pickers) / [B] Keep as a general util. **Recommended: A** (dead after this
  unit; keeping it invites drift, per the reuse preference).

## §3 File impact

**New (1)**:
- `app/src/main/java/com/spoolpainter/app/ui/components/PinnedActionMenu.kt`
  — `PinnedActionMenu(expanded, items, anchor, opensUpward-aware, pinnedContent,
  itemContent, onItemClick, onDismiss, ...)`. Popup + Column with a pinned
  action slot on the field-adjacent edge + a `LazyColumn` list. Reuses
  `LazyDropdownAnchor` / `rememberLazyDropdownAnchor` from `LazyDropdownMenu.kt`
  and a shared anchor-relative position provider that also reports the chosen
  open-direction.

**Modified (3 prod)**:
- `MaterialPicker.kt` — swap `ExposedDropdownMenu` + `rememberDropdownDirection`
  for `PinnedActionMenu`; "Other" becomes the pinned action; material rows
  become the scrolling list. Preserve `testTag("main-form-material")`, the
  inline "Custom" field, the 120dp width shrink when "Other" is selected, and
  the `Icons.Default.Add` + primary-tint styling.
- `BrandPicker.kt` — same swap; "Other" pinned; brand rows scroll. Preserve
  `testTag("main-form-brand")`, custom field (32-char cap), styling. **This is
  the row that actually regressed**, so it's the primary install-gate target.
- `ColorPicker.kt` — same swap; the "Color Wheel" + "Scan color" row becomes the
  pinned action (its existing nested clickable for the camera trailing icon is
  preserved verbatim); the 8 named colors + swatches become the scrolling list.
  Preserve `testTag("main-form-color")`, `testTag("main-form-color-camera")`,
  the leading swatch/NoColor icon, and both dialog hooks (`ColorWheelDialog`,
  `CameraColorSampler`).

**Deleted (1, pending Q-U18-4=A)**:
- `DropdownDirection.kt` — retired; no remaining callers after the three swaps.

**Test impact**: no existing unit test references the picker composables
directly (verified — `MainViewModelFilamentPickerTest` is VM-level;
`ColorSamplingTest` is pure math). The picker swap is presentation-only. New
tests are **not** proposed at the unit level (Compose menu layout is exercised
by the install gate, consistent with how U17's picker changes were verified).
Test count expected to hold at **514 / 514**.

## §4 Step-by-step

- [x] 1. Author `PinnedActionMenu.kt`: Popup + position provider that reports
  chosen open-direction; `Column` with pinned action slot at the
  field-adjacent edge + `LazyColumn` list underneath; matched anchor width;
  shadow + rounded clip matching `LazyDropdownMenu`. Also extracted the shared
  `PinnedOtherAction(label, onClick)` composable (post-review dedup).
- [x] 2. Rewire `MaterialPicker.kt` to `PinnedActionMenu` ("Other" pinned via
  `PinnedOtherAction`). Dropped `rememberDropdownDirection`.
- [x] 3. Rewire `BrandPicker.kt` to `PinnedActionMenu` ("Other" pinned via
  `PinnedOtherAction`). Dropped `rememberDropdownDirection`.
- [x] 4. Rewire `ColorPicker.kt` to `PinnedActionMenu` (Color Wheel + Scan
  color row pinned inline — two tap targets, not shared). Dropped
  `rememberDropdownDirection`.
- [x] 5. Delete `DropdownDirection.kt` (Q-U18-4=A).
- [x] 6. `compileDebugKotlin` — ✅ clean (only pre-existing `menuAnchor` warns).
- [x] 7. `testDebugUnitTest` — ✅ 514 / 514.
- [x] 8. `assembleDebug` — ✅ 71 MB (no meaningful change; pure UI swap).
- [x] 9. Update `ui-followups.md` UI-46 → fixed; update `aidlc-state.md` U18
  entry; append audit.

**Post-plan course-correction**: first cut copy-pasted the "Other +" action row
into both MaterialPicker and BrandPicker (24 near-identical lines). User flagged
it ("did you just copy same code in all the places?"). Extracted to the shared
`PinnedOtherAction` composable — this was the explicit no-duplication goal of
UI-46. Q-U18-5 (version bump) resolved: **no bump** — v2.2.0 was never released,
so U18 rides versionCode 110 / 2.2.0 (same pattern as U14c+U15 riding 2.1.2).

**Release matrix**: `assembleRelease` ✅ 7.90 MB R8 / `bundleRelease` ✅ 8.72 MB
AAB. Install gate PASSED (user sign-off, moto g stylus 2025 / Android 16).

## §5 Install gate (on-device, moto g stylus 2025 / Android 16)

1. **Brand near screen bottom** (the regression): scroll the form so the Brand
   field is low, tap it → menu flips **up** → "Other" is pinned at the **bottom
   edge next to the field**, visible without scrolling; the 30-vendor list
   scrolls above it.
2. **Brand near screen top**: menu opens **down** → "Other" pinned at the top
   edge next to the field; list scrolls below.
3. **Material** (short list): both directions, "Other" pinned + reachable;
   selecting "Other" still reveals the inline Custom field.
4. **Color** (short list): both directions; "Color Wheel" + "Scan color" pinned
   and reachable; tapping "Scan color" opens the camera sampler (not the wheel);
   tapping the row body opens the wheel; named colors scroll; swatches intact.
5. Regression: selecting any list row still fires `onSelect` and closes the menu;
   dismiss-on-outside-tap still works; disabled state still suppresses the menu.

## §6 Resume pointers

- Design + decisions live here (FD skipped).
- Existing precedent: `LazyDropdownMenu.kt` (Popup/LazyColumn/position provider).
- Regressed behaviour documented in `ui-followups.md` UI-46.
- No version bump decided yet — rides the same v2.2.x line as U17 (versionCode
  110 / 2.2.0); bump vs hold is a close-out question (Q-U18-5, defer to Part 2).
