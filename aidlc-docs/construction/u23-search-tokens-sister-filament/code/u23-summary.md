# U23 — Summary

**Scope**: UI-60 (search) + UI-57 (sister-filament flow + clear-all) + UI-61
(brand casing). Version **HELD** at 114 / 2.3.2.
**Status**: S1-S11 **DONE**. Install gate PASSED on moto g stylus 2025 /
Android 16 (114 / 2.3.2-DEBUG).

## UI-60 — multi-token search

`PickerRanking.filter` now splits the query on whitespace and requires **every**
token as a case-insensitive substring, instead of one contiguous `contains`.

Root cause of the report ("`3dhojor petg` finds nothing, `3dhojor pla` works"):
the searchable text is `primaryRowText() + " " + secondaryRowText()`, with brand
in primary and material in secondary joined by `" · "`. A cross-field two-word
query could only match if one field happened to repeat the other's words — which
is exactly why the PLA row matched (its Spoolman `name` repeats the brand) and
the PETG row did not. Cross-field search was broken generally, not per-material.

Blank/whitespace-only query is still the identity, so the U20 float path is
untouched. The Spool picker gets the fix for free (same helper).

+7 tests in `PickerRankingTest` (16 → 23), including the exact reported case.

## UI-57 — sister filament + clear-all

**`onFilamentSelected(null)` no longer resets the form.** It drops the filament
link and keeps every value, so an already-configured filament works as a template:
pick it, tap the X (which also clears `identityLocked`, re-enabling
material/brand/colour), change a field, Save. With no filament id attached,
`SaveToSpoolmanUseCase.resolveSpool` takes its create branch and
`resolveOrCreateFilament` matches on vendor + material + colour + variant, so a
changed colour **creates** a new filament while density / diameter / weights
carry over.

The X clears the **spool** link too. It has to: `FormMapping.fromSpoolman` derives
`selectedFilamentId` from `spool.filament.id`, so "filament unlinked, spool still
linked" is not stable — U22's `reDeriveSelectedSpoolForm` would silently re-link
on the next cache refresh. In the sister flow this is a no-op (picking a filament
already cleared the spool).

**New `MainViewModel.onClearAll()`** inherits the reset the X gave up, verbatim:
preserves `rawWriteMode` + `moreDetailsExpanded`, clears ambiguity / observed-tag
state / scan suggestions / custom material+brand. Surfaced as an
`Icons.Outlined.RestartAlt` `IconButton` in the header, immediately left of the
`⋮`, in a `Row` with a deliberate 4dp gap. **No confirmation and no Undo**, both
by user decision — the 4dp gap is the only mis-tap guard.

+2 net tests; the pre-existing `onFilamentSelected null resets form to defaults`
test asserted the old behaviour and was **rewritten**, since this is a deliberate
behaviour change rather than a regression.

## UI-61 — brand casing: verified, no code change

The user's own hypothesis was right: nothing mangles case. `BrandPresetSource`
ships the casing verbatim, and `resolveOrCreateVendor` matches vendors
case-insensitively and never renames, so the first spelling to reach a server
wins permanently. See the plan §1 F3 for the two changes made and reverted.

## Verification

- `compileDebugKotlin` ✅
- `testDebugUnitTest` ✅ **592 / 592** (Δ +13 vs the 579 baseline), 0 failures
- `assembleDebug` ✅ **68.68 MB**
- Net `app/src` diff touches: `PickerRanking.kt`, `MainViewModel.kt`,
  `MainScreen.kt`, `MaterialBrandRepository.kt`, `BrandPresetSource.kt`
  (comment only), + 3 test files

## UI-62 — duplicate brand rows (added mid-unit)

`mergeBrands` (`MaterialBrandRepository.kt`) deduped on `lowercase()` without
trimming, so a Spoolman vendor stored as `"TECBEARS "` did not collide with the
preset `"TECBEARS"` and both rendered — indistinguishable on screen. Now trims
before both the dedupe key and the kept value, and drops blank/whitespace-only
names. Presets still come first so a preset's spelling wins.

+4 tests, folded into the existing `MaterialBrandRepositoryTest` rather than a
new file (that class already covered preset-spelling-wins and the no-duplicate
invariant). One test deliberately pins the case that a *genuinely* different
spelling ("Techbear" vs "TECBEARS") must keep both rows, so this isn't "fixed"
into over-merging later.

## Install gate — PASSED

Device: moto g stylus 2025 / Android 16, versionCode 114 / 2.3.2-DEBUG.

- **UI-60 search** — user confirmed: "search is fine".
- **UI-57 sister filament** — user confirmed working.
- **Clear all** — verified by driving the device: typed `SisterTest` into Variant,
  invoked clear, field returned to its `Optional` placeholder, and
  `topResumedActivity` stayed on `MainActivity` (i.e. it cleared the form and did
  not fall through to Settings).
- **UI-62 duplicate brands** — user confirmed **one** TECBEARS row after the fix,
  where there had been two. That is positive evidence the cause was a
  whitespace-padded Spoolman vendor name, which is exactly what the trim
  addressed; the "genuinely different spelling" branch was not the cause.
- Zero errors in logcat across install, launch, typing and clearing.

## Clear-all UX — six iterations before it stuck

Worth recording because four of the six were rejected on device, and the reason
was structural rather than cosmetic: **the top-right of this screen is contested
space.** `SpoolPainterLogo` fills the width and its 40dp leading Spacer pushes the
artwork rightward, so anything anchored `TopEnd` either crowds the NFC waves or
opens over them.

1. `Icons.Outlined.RestartAlt` icon — rejected: a circular arrow reads as
   *reload*, and this screen already has pull-to-refresh. ("i dont like reset
   logo that tells nothing, it could even be reload")
2. Bare "Clear" text — rejected: too close to the waves.
3. `OutlinedButton` "Clear" + `OutlinedIconButton` on the dots, as a matched
   pair — rejected outright; the border made the crowding *more* visible and its
   edge overlapped the waves. My prediction that a border would make the
   proximity read as deliberate was simply wrong.
4. **Overflow menu on the existing dots** — accepted. Costs zero layout space and
   makes `MoreVert` mean what it conventionally means.
5. Menu restyled to match `LazyDropdownMenu` (12dp → 20dp corners,
   `surfaceContainer`, 8dp shadow) after "does dropdown there match overall
   app?" — it did not; M3's defaults are much squarer.
6. Menu geometry: `DropdownMenuItem` replaced with plain clickable `Text` rows to
   escape its 112dp minimum content width (a `width(148.dp)` attempt made the
   gutter *worse*), rows 44dp, and `offset = DpOffset(-12.dp, -10.dp)` to anchor
   to the 24dp glyph rather than the invisible 48dp touch bounds. Padding
   finally cut 16dp → 10dp so the menu stops clipping the waves.

**Cost knowingly accepted**: Clear and Settings are both 2 taps now, on a screen
where the user says they clear often. The 1-tap alternative that avoids the logo
entirely — Clear on the Filament section header row, which has an empty
half-width — was offered twice and not taken. If the extra tap grates later,
that is the move.

## Owed

- Nothing on the gate. One assertion I wrote failed during development and **the
  code was right, the test was wrong** — a fresh `FormState()` defaults material
  to PLA and therefore carries PLA's preset density 1.24, not null. Fixed by
  comparing against `FormState()` rather than hardcoding.
