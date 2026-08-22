# U23 — Code Generation Plan (Part 1)

**Unit**: U23 — multi-token search + sister-filament flow + brand casing
**Scope source**: `ui-followups.md` [[UI-60]], [[UI-57]], [[UI-61]], + [[UI-62]] (found mid-unit on device)
**Opened**: 2026-08-22
**Per-unit gate**: FD / NFR-R / NFR-D / Infra-D **SKIP** — bugfix + small-UI unit,
design folds into this plan (same convention as U20 / U21 / U22).
**Version**: **HELD** at versionCode 114 / versionName 2.3.2. No per-unit bump;
batched at a joint release with the user.

---

## §0 Overriding invariants

1. **No behaviour change to read / prefill / pair / save / write flows** beyond
   what each item explicitly specifies.
2. **Blank search query stays the identity** — `PickerRanking.filter` with a
   blank query must return the input list unchanged, so the no-query path is
   provably today's list and the U20 float still layers on top.
3. **The filament X must stop being a form reset, and a replacement reset must
   land in the same change.** Shipping half 1 without half 2 removes the app's
   only reset-everything path. Non-negotiable, see [[UI-57]].
4. **No brand-casing churn.** Preset casing is each vendor's real styling; the
   write path carries it verbatim. Changing any preset's case strands the vendor
   rows the app already created on users' Spoolman servers, so it is never a
   free cosmetic edit (see §1 F3).

---

## §1 Scope

### F1 — UI-60: search matches brand + material together (BUG)

`PickerRanking.filter` (`PickerRanking.kt:70-73`) runs a single contiguous
`contains` over `primaryRowText() + " " + secondaryRowText()`. Brand lives in
primary, material in secondary, `" · "` between — so a two-word cross-field
query can only match by luck. `3dhojor pla` matched only because that filament's
Spoolman `name` repeats the brand; `3dhojor petg` had nothing contiguous to hit.

**Fix**: tokenise the query on whitespace, require **every** token present as a
case-insensitive substring (AND). Order-independent, separator-independent.
Applies to the Spool picker for free (same helper, `MainScreen.kt:778`).

### F2 — UI-57: sister-filament flow (filament X + clear-all)

1. `onFilamentSelected(null)` (`MainViewModel.kt:812-834`) stops rebuilding
   `FormState()` and clears **only** the filament link, mirroring
   `onSpoolSelected(null)` (`:673-695`).
2. New **clear-all** action carrying today's full-reset semantics, surfaced as an
   icon in the header immediately left of the `⋮`.

### F3 — UI-61: brand casing

**OUTCOME: verified, no code change needed.** Net diff is comment-only.

Two changes were made during the unit and both were backed out:
1. `GEEETECH` → `Geeetech`, on the strength of geeetech.com's `og:site_name`
   ("Geeetech Official Store"). **Reverted** — the maintainer confirmed the brand
   is all-caps. Lesson: site metadata is weak evidence for brand styling; ask.
2. `Elegoo` → `ELEGOO` across all 5 sites (preset, `ElegooProcessor.displayName`
   + `brand`, `VendorTagChipRow`, 2 test files, plus an `expectedBrand` override
   in `VendorFixtureParseTest` because the 3 Elegoo fixtures carry
   `manufacturer: Elegoo` from upstream). **Reverted** per Q-U23-2.

What survives is a comment in `BrandPresetSource` recording that the odd casings
(`eSUN`, `3DHoJor`, `TECBEARS`, `GEEETECH`) are deliberate, that the first
spelling to reach a Spoolman server wins permanently, and why `Elegoo` is
deliberately *not* `ELEGOO` — so nobody re-creates the legacy tail this unit
decided to avoid.

---

## §2 Open questions — ALL ANSWERED 2026-08-22

- **Q-U23-1 — Undo snackbar on clear-all? → NO** (user). Original recommendation was yes. With no
  confirmation dialog (user's call) and the icon adjacent to Settings, this is
  the only recovery path from a mis-tap. Cost: snapshot `FormState` before the
  reset, restore from the snackbar action. Note `UiEffect.ShowSnackbar` +
  `showSnackbar(effect.message)` (`MainScreen.kt:116`) currently carry a message
  only, so this needs an `actionLabel` and a `SnackbarResult` branch — a small
  extension, not a rewrite.
- **Q-U23-2 — `Elegoo` → `ELEGOO`? → NO, SKIPPED** (user: "lets skip elegoo then"), after the legacy tail was weighed: the app itself created those vendors as "Elegoo", so every existing spool would have shown the old spelling. Applied across all 5 sites then fully reverted; net UI-61 change is comment-only. Original analysis: Their own `og:site_name` is "ELEGOO
  Official" and the OpenRFID fixtures are named `ELEGOO … .yml`, so all-caps is
  likely correct. But it is **not** preset-only: `ElegooProcessor.kt:21`
  (`displayName`) and `:74` (`brand` on a decoded tag), `VendorTagChipRow.kt:50`
  (Settings label), plus 5 test files. Changing a subset recreates the exact
  inconsistency UI-61 reports. Either change all sites or none.
- **Q-U23-3 → only the two links** (filament + spool), no field values. My `prefilled*` recommendation was WRONG and withdrawn: that dirty-check is wrapped in `if (!isNewSpool)` in `SaveToSpoolmanUseCase`, so a create-path save never reads it. Original (withdrawn) text: RECOMMEND clearing `selectedFilamentId` **plus the `prefilled*`
  snapshots**, keeping every user-visible value. Rationale: the `prefilled*`
  fields are dirty-flag baselines tied to the now-unlinked filament, the same
  reasoning `onSpoolSelected(null)` uses for spool-scope fields. Everything else
  (material / brand / colour / variant / temps / density / diameter / weights) is
  precisely what the sister-filament flow needs to keep.

---

## §3 Design decisions

- **D1** — F1 stays in the pure `PickerRanking` helper. No caller changes, so
  both pickers benefit and the existing `PickerRankingTest` covers it.
- **D2** — Tokens are matched independently, so they may match across the `" · "`
  separators and across the primary/secondary boundary. That is the point.
- **D3** — Clear-all lives in `MainViewModel` as its own function rather than
  being spelled inline in the composable, so it is unit-testable (this module has
  no Compose UI test source set — same constraint that forced `sanitiseVariant`
  out of a composable in U22).
- **D4** — Header becomes a `Row` aligned `TopEnd` inside the existing `Box`
  (`MainScreen.kt:560-578`) holding the clear-all `IconButton` then the existing
  settings `IconButton`. Deliberate spacing between them; verify on-device that
  neither collides with the logo art (the logo's 40dp leading Spacer,
  `SpoolPainterLogo.kt:61-69`, shifts the image rightward, so the right gap is
  the narrower one).
- **D5** — Icon is `Icons.Outlined.RestartAlt`. NOT `Delete` (reads as "delete
  from Spoolman" in a server-connected app) and NOT `Clear`/X (already used 3x
  for per-field clears; overloading it muddles the field-vs-form distinction
  [[UI-18]] established). Extended icons already a dependency
  (`app/build.gradle.kts:79`).
- **D6** — Clear-all inherits today's proven filament-X reset semantics exactly:
  preserve `rawWriteMode` + `moreDetailsExpanded`, clear `ambiguity` /
  `observedTagKind` / `observedTagUid`, reset `_customMaterial` / `_customBrand`,
  and clear the scan-suggestion lists.
- **D7** — `testTag("main-clear-all-button")` on the new icon, matching the
  existing `main-settings-button` convention.

---

## §4 Steps

- [x] **S1** — F1: `PickerRanking.filter` tokenises on whitespace, requires all
      tokens. Blank query still returns `rows` unchanged.
- [x] **S2** — F1 tests in `PickerRankingTest`: the `3dhojor petg` cross-field
      case, order independence (`petg 3dhojor`), repeated whitespace, blank-query
      identity, single-token parity with today's behaviour.
- [x] **S3** — F2a: `onFilamentSelected(null)` clears only the filament link
      (+ Q-U23-3's answer). Update the stale comment that says the prefilled
      values are "orphaned once the link is removed".
- [x] **S4** — F2b: `MainViewModel.onClearAll()` with D6 semantics.
- [x] **S5** — F2c: header `Row` + `RestartAlt` `IconButton` wired to
      `onClearAll`, `testTag` per D7.
- [~] **S6** DROPPED (Q-U23-1 = no) — — F2d: Undo snackbar (**only if Q-U23-1 = yes**) — `FormState`
      snapshot, `UiEffect` gains an action label, `SnackbarResult` restores.
- [x] **S7** — F2 tests: X keeps every form field; X drops the link; `onClearAll`
      resets and preserves the two toggles; undo restores the snapshot (if S6).
- [x] **S8** — F3: apply Q-U23-2's answer across **all** Elegoo sites or none.
- [x] **S9** — Docs: `ui-followups.md` UI-57 / UI-60 / UI-61 → fixed; check off
      this plan; write `u23-summary.md`.
- [x] **S10** — Build matrix: `compileDebugKotlin`, `testDebugUnitTest`,
      `assembleDebug`. Baseline is **579** tests.
- [x] **S11** — On-device install gate **PASSED** 2026-08-22 (moto g stylus 2025 / Android 16, 114 / 2.3.2-DEBUG). Search, sister filament, clear-all and the UI-62 dedupe all confirmed. See the summary for the six-iteration clear-all UX trail.

---

## §5 Install gate script

Device required (moto g stylus 2025 / Android 16 has been the reference).

1. **Search (F1)**: type `3dhojor petg` in the filament picker → the filament
   appears. Then `petg 3dhojor` → same result. Then brand-only and material-only
   → both still work. Clear the box → the full list and the U20 float return.
2. **Same in the Spool picker** — a two-word brand + material query.
3. **Filament X (F2a)**: pick a filament, confirm the form fills, tap the X →
   **fields stay**, the picker shows no selection. Change the colour, Save →
   Spoolman gets a **new** filament, the sister is untouched.
4. **Clear-all (F2b/c)**: fill the form, tap the new header icon → everything
   resets. Confirm it does **not** collide with the logo, and that hitting it
   doesn't open Settings.
5. **Undo (F2d, if built)**: clear-all → tap Undo → the form comes back.
6. **Brand casing (F3)**: pick Geeetech, write a tag, read it back → `Geeetech`,
   not `GEEETECH`.

---

## §6 Risks

- **R1** — The filament-X change is a behaviour change to a control users already
  know. Mitigated by it now matching the spool X, which is the more-used control.
- **R2** — Adjacent 48dp targets (clear-all next to Settings) are a mis-tap pair.
  Accepted by the user; Q-U23-1's snackbar is the mitigation.
- **R3** — Q-U23-2 touching `ElegooProcessor` risks the 16 vendor fixtures
  (`VendorFixtureParseTest`) if their expected brand strings disagree. Check
  before editing, not after.
- **R4** — Tokenised search is strictly more permissive, so short tokens match
  more rows. Acceptable for a filter, but watch that a 1-char token doesn't make
  the list look unfiltered.
