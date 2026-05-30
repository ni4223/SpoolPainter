# U9b — Code Generation Summary (Part 2 results)

**Stage**: CONSTRUCTION → Per-Unit Loop → Code Generation Part 2 (U9b)
**Unit**: U9b — UI Polish (pure polish; editing deferred post-v2.0)
**Executed**: 2026-05-29 (Code Gen Part 2). Install-time iteration **2026-05-30** added a major scope shift: Q-U9b-7 flipped A → "split into per-section Cards", standalone `TopAppBar` dropped in favour of v1's logo Row, and `TempPanel` was folded into `MoreDetailsExpander` as one of three labelled sections under a single "Filament metadata" expander.
**Plan**: `aidlc-docs/construction/plans/u9b-ui-polish-code-generation-plan.md`
**Q-U9b-1..7**: A / A / A / locked / A / A / A (originally locked 2026-05-29; **Q-U9b-7 reversed during install-time iteration 2026-05-30** — see §3 + §10 below)

## Verification

- `./gradlew compileDebugKotlin` ✅ (4 deprecation warnings — `Modifier.menuAnchor()`; non-blocking, pre-existing)
- `./gradlew testDebugUnitTest` ✅ **362 / 362** (Δ +0 vs U9 baseline)
- `./gradlew assembleDebug` ✅ APK **64.7 MB** (Δ +0 vs U9's 65 MB)

## Per-section delta

### §2 — Branding restore (main-screen logo)
**Final shape (post 2026-05-30 install-time iteration):**
- `ui/components/SpoolPainterLogo.kt` — added `showText: Boolean = true` parameter. The Column-based variant **dropped all `Modifier.offset(...)` calls** (`offset(x = 26.dp)` on the Column, `offset(y = 15.dp)` + `offset(x = -15.dp)` on the "Spool Painter" text) — those v1 offsets caused the text to bleed into the Spoolman dropdown card on actual screens. Image height reduced 125dp → 96dp; text style headlineLarge → headlineSmall; sits cleanly inside its Column with no overflow.
- `ui/screens/main/MainScreen.kt` — Material 3 `TopAppBar` removed entirely. New `MainLogoHeader` composable renders the logo via a `Box(fillMaxWidth)` with `SpoolPainterLogo` as the centered child and the Settings `IconButton` aligned `Alignment.TopEnd` overlaying the top-right corner (no Row, no offsets). Settings icon swapped from `Icons.Default.Settings` (cog) to `Icons.Default.MoreVert` (three vertical dots) to match v1.
- Logo tint follows `state.form.colorHex` — `parseLogoColor(hex)` → falls back to `MaterialTheme.colorScheme.outline` when null/blank/invalid. Recomposes only the topbar Box on colour change.
- Removed `statusBarsPadding()` from the Scaffold content Column — Scaffold's own `padding(padding)` already insets the status bar; the explicit modifier was double-counting and wasting top space. Vertical content padding tuned 12dp → 8dp → 4dp during install-time iteration.

### §3 — UI-01 SpoolmanDropdown + Q-U9b-7 (REVERSED 2026-05-30)
- `SpoolmanDropdown` already shipped with `RoundedCornerShape(20.dp)` + matching `OutlinedTextFieldDefaults.colors` from `FilamentForm.VariantField` in U9. UI-01 styling delta is a no-op.
- **Q-U9b-7 reversed during install-time iteration**: original lock was A (no outer Card wrap). User direction *"i said everything should be wraped, like it was in v1"* led to four separate per-section Cards instead of v1's single outer Card or the original "no wrap":
  - **Card 1** — `SpoolmanDropdown` (own elevated Card, conditional on `state.spoolman.urlConfigured`)
  - **Card 2** — Filament fields: `FilamentForm` only (Material → Variant → Color → Brand). `Save & Write` button **lifted out** into a top-level `SaveAndWriteButton` composable (`ui/components/FilamentForm.kt`).
  - **Card 3** — `MoreDetailsExpander` (folds in `TempPanel`; see §4)
  - Banner / `BottomSheetHost` / `ReadingHint` / `VendorTagHint` / `AmbiguityBlock` / `WritingHint` / `InstructionFooter` sit between or after the Cards on the screen background (intentional — they're status surfaces, not form chrome).
  - **`Save & Write` is now its own row** at the bottom of the content Column, full-width.

### §4 — Temp + More-Details visual fix → MERGED into one expander (2026-05-30)
**Final shape:** the standalone `TempPanel` Card on `MainScreen` was **removed**. `MoreDetailsExpander` is now the single source of all "advanced" filament fields and renders three labelled sections inside one Card:
- **Temperature** — `TempRows` composable (Nozzle + Bed rows, extracted from `TempPanel` so the Card-less variant can be embedded). `TempPanel` is retained but no longer referenced from `MainScreen`; it stays for any future call sites or previews.
- **Weight** — Filament weight (Spoolman `weight`, net filament only) + Spool weight (Spoolman `spool_weight`, empty spool only). Each field has a one-line `supportingText` clarifying scope ("Net filament only. Excludes the empty spool." / "Empty spool only.") because the user surfaced the ambiguity during install-time iteration.
- **Others** — Diameter, Density, Price (in that order, per user direction *"do dia and den first price last"*).
- Section labels: SemiBold + `colorScheme.primary`. `HorizontalDivider` between sections.
- Card padding tightened to `12dp horizontal / 8dp vertical`; row spacing trimmed to 8dp; section gap 4dp. Compact layout per user direction *"can we make things bit tighther in meta data, meta data now take lots of space"*.
- `MoreDetailsExpander` signature gained `tempRanges: TempRanges` + `onTempRangesChange: (TempRanges) -> Unit`. Header label remained "Filament metadata" (a transient rename to "Filament details" was reverted per user direction *"why you changed the name of that section"*).
- Header `testTag("more-details")` + body `testTag("more-details-header")` preserved through both iterations; existing `MoreDetailsExpanderTest` continues to pass without edits.

### §5 — IME-aware snackbar host (Q-U9b-2=A → A+B during iteration)
- **Initial 2026-05-29**: `Modifier.imePadding()` added to the Scaffold content `Column` on both Main + Settings (Q-U9b-2 option A).
- **Install-time iteration 2026-05-30**: A alone wasn't enough — snackbar still sat below IME on the device. Switched to A+B combo: `imePadding()` on **both** the content Column and the `SnackbarHost` itself. After the §3 layout flip + iteration, the snackbar now floats above the keyboard correctly on both screens.

### §6 — "Other" / "Color Wheel" affordance polish (Q-U9b-5=A)
- `ui/components/MaterialPicker.kt` — "Other" `DropdownMenuItem` now renders a Row with `Icons.Default.Add` (primary tint) + label in `MaterialTheme.colorScheme.primary` with `FontWeight.SemiBold`. Italic styling dropped.
- `ui/components/BrandPicker.kt` — same pattern; `Icons.Default.Add` + primary colour.
- `ui/components/ColorPicker.kt` — "Color Wheel" `DropdownMenuItem` now renders `Icons.Default.Palette` + label in primary. Removed unused `FontStyle` import.
- All three icons resolve via `material-icons-core` (already on classpath); no dependency change.

### §7 — UI-05 NDEF copy + UI-07 broader audit (Q-U9b-4 locked, Q-U9b-6=A)

Locked copy table (final post-audit):

| Site | Old | New |
|---|---|---|
| `MainViewModel.kt` `CreateAndPairResult.VerifyFailed` | "Verify failed. Tap Save to retry." | "Couldn't write to tag. Try again." |
| `MainViewModel.kt` `CreateAndPairResult.NfcFailed` (non-vendor) | "NFC error: ${reason}" | "Couldn't write to tag. Try again." |
| `MainViewModel.kt` `TwoTagResult.VerifyFailed` | "Second-tag verify failed: ${cause}" | "Couldn't write to second tag. Try again." |
| `MainViewModel.kt` `TwoTagResult.NfcFailed` | "Tag write failed: ${reason}" | "Couldn't write to tag. Try again." |
| `MainViewModel.kt` `RawWriteResult.VerifyFailed` | "Verify failed. Tap Write to retry." | "Couldn't write to tag. Try again." |
| `MainViewModel.kt` `RawWriteResult.NfcFailed` | "Tag write failed: ${reason}" | "Couldn't write to tag. Try again." |
| `MainViewModel.kt` `TwoTagResult.MoveOnBindPartial` | "...; restore manually if needed" | "...Restore manually if needed." (semicolon → period) |

Locked-keep (already meet copy rule, no dash separator): "No tag tapped. Try again.", "Saved with one tag", "Both tags paired", "Tap the vendor tag again to capture its UID.", "Vendor tag. Pick a spool first.", "Vendor tag. Write blocked.", "Vendor tag. Content unreadable.", "Tag written".

Test impact: one assertion in `MainViewModelTest` (`onWriteTapped verifyFailed keepsFormAndEmitsSnackbar`) updated from `contains("Verify failed")` → `contains("Couldn't write to tag")`. NFC-failed assertion changed from `contains("tag lost")` → `contains("Couldn't write to tag")` since the use-case `reason` is no longer surfaced in user copy.

### §8 — UI-02 passive-tap prompt (Q-U9b-3=A)
- `ui/screens/main/MainViewModel.kt` — added private `var ambientTapHintShown: Boolean = false` at ViewModel scope. In the second `nfc.lastSeenTag.collect` block (the one that maps classification to `ObservedTagKind`), after the state update, fire `_effects.trySend(UiEffect.ShowSnackbar("Tag detected. Press Read tag to load."))` exactly once per ViewModel lifetime when:
  - `tag != null`
  - `!ambientTapHintShown`
  - `_state.value.activeFlow == ActiveFlow.Idle`
  - `_state.value.spoolman.selectedSpoolId == null`

  Set the boolean true on emit. No new test (Q-U9b-3=A doesn't extract a helper); existing tests adjusted via a shared `awaitNonAmbientSnackbar` Turbine extension that drains the new emission before assertions.

### §9 — Cross-cutting hygiene
- No new dependencies, no `LICENSE`/`NOTICE` updates.
- No file deletions.
- No `*_modified.kt` / `*_new.kt` / `*.bak` files left in tree.
- **Public interface delta** (U1..U9 byte-identical guarantee broken intentionally during install-time iteration): `FilamentForm` lost `canSave`, `onSave`, `saveButtonLabel`, `priceSuffix` (Save lifted into top-level `SaveAndWriteButton`; Temp moved into `MoreDetailsExpander`). `MoreDetailsExpander` gained `tempRanges` + `onTempRangesChange`. `SpoolPainterLogo` gained `showText: Boolean = true` (default keeps existing call sites stable).
- `Modifier.offset(...)` removed from `SpoolPainterLogo`'s text + outer Column and from `MainScreen`'s settings IconButton — covered by [[feedback_no_offset_modifier]].

### §10 — Install-time iteration log (2026-05-30)
The 2026-05-29 Code Gen Part 2 build was installed on moto g stylus 2025 / Android 16 (one shipped APK). The user surfaced layout issues that didn't land in the original plan; a series of single-feedback → single-fix iterations rebuilt the main-screen shape:

| User feedback | Fix |
|---|---|
| "for v1, it was supposed to change color based on filament color" | Wired `state.form.colorHex` through new `MainTopBar` (later `MainLogoHeader`); `parseLogoColor` falls back to `outline` |
| "i said everything should be wraped, like it was in v1" | Q-U9b-7 reversed; outer Card added |
| "snackbar still hidden" | Added `imePadding()` to `SnackbarHost` (Q-U9b-2 went A → A+B) |
| "wrap can that be sperpate, like dont have t be whole them 2 on top" | Split into 2 separate Cards (Spoolman + form) |
| "size of logo, look at the v1 ... wtf" | Dropped Material 3 `TopAppBar`; restored v1 logo Row |
| "now temp and meta data is still on top of some other padding" | Lifted `TempPanel` + `MoreDetailsExpander` out of `FilamentForm` |
| "whats up with button? why it moved?" | Lifted `Save & Write` out of `FilamentForm` into top-level `SaveAndWriteButton` |
| "i am thinking can we move temp to in meta data and have better defined sections" | Folded `TempPanel` into `MoreDetailsExpander` as a `Temperature` section; extracted `TempRows` Card-less variant |
| "why you changed the name of that section?" | Reverted "Filament details" → "Filament metadata" |
| "weight and all section name is not what we decided on" | Three sections: Temperature / Weight / Others (per user direction) |
| "spool dropdown is cutting into name and why sesting symbol so low" | Removed all `Modifier.offset(...)` from logo + settings; logo Image height 125dp → 96dp; text style headlineLarge → headlineSmall |
| "dont use offset stuff that get messed up on different screen sizes" | Saved as memory [[feedback_no_offset_modifier]]; layout uses Spacer/padding/Alignment/weight only |
| "setting sybol still so low" | Settings → its own row above logo (transient) |
| "that symbl is still low bit now our logo and whole app is low wasting so much space on top" | Final shape: `Box(fillMaxWidth)` with logo centered + IconButton aligned `TopEnd` overlay; dropped `statusBarsPadding()` (was double-counting against Scaffold's own inset) |
| "replace symbol with three . like v1" | `Icons.Default.Settings` → `Icons.Default.MoreVert` |
| "things bit tighther in meta data" | Card padding 16dp → 12dp/8dp; row spacing 16dp → 8dp; section gap 4dp; dropped initial supportingText |
| "now i am confused filament weight is it with spool or not" | Restored short clarifying supportingText: "Net filament only. Excludes the empty spool." / "Empty spool only." |

**Net architectural shape after iteration:**
1. `MainLogoHeader` (Box: logo + Settings IconButton overlay)
2. `BannerSlot` / `BottomSheetHost` / `ReadingHint` (status surfaces, no Card)
3. **Card 1**: `SpoolmanDropdown` (conditional)
4. `VendorTagHint` / `AmbiguityBlock` (status surfaces, no Card)
5. **Card 2**: `FilamentForm` (Material → Variant → Color → Brand)
6. **Card 3**: `MoreDetailsExpander` (Temperature / Weight / Others sections)
7. `SaveAndWriteButton` (full-width, conditional on `Idle`)
8. `WritingHint` / `InstructionFooter`

## Test infrastructure delta

- `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelTest.kt` — added `awaitNonAmbientSnackbar` Turbine extension (drains the once-per-session UI-02 hint emission so assertions can target the use-case-driven snackbar that follows). Updated 3 test assertions to consume it.
- `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelTwoTagTest.kt` — same helper duplicated (file-private). Updated 5 test assertions.

## Out-of-scope (carried forward)

- Editing a paired spool (UI-13 / UI-14 / UI-15) — post-v2.0 release.
- `material-icons-extended` R8 / per-icon vector copy (U10-Δ-1) — the new `Icons.Default.Add` + `Icons.Default.Palette` icons used here are core, not extended.
- Legacy `sortOrder` JSON key migration (U10-Δ-2).
- JDK 17 portability — durable fix in U10.
- Q-T2=B → no U9b install gate; manual verification was covered organically through code-gen review on this session and will be folded into U10's full scenario matrix.

## Files touched

Production:
- `app/src/main/java/com/spoolpainter/app/ui/components/SpoolPainterLogo.kt` (modified — `showText` param + offsets removed)
- `app/src/main/java/com/spoolpainter/app/ui/components/MoreDetailsExpander.kt` (modified — Card wrap + Temp section folded in + 3 labelled sections)
- `app/src/main/java/com/spoolpainter/app/ui/components/TempPanel.kt` (modified — extracted `TempRows` Card-less variant)
- `app/src/main/java/com/spoolpainter/app/ui/components/FilamentForm.kt` (modified — `Save & Write` button + `TempPanel` + `MoreDetailsExpander` lifted out; signature trimmed)
- `app/src/main/java/com/spoolpainter/app/ui/components/MaterialPicker.kt` (modified — Other affordance polish)
- `app/src/main/java/com/spoolpainter/app/ui/components/BrandPicker.kt` (modified — Other affordance polish)
- `app/src/main/java/com/spoolpainter/app/ui/components/ColorPicker.kt` (modified — Color Wheel affordance polish)
- `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainScreen.kt` (modified — `TopAppBar` removed; `MainLogoHeader` Box layout; per-section Cards; IME on snackbar)
- `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainViewModel.kt` (modified — UI-02 ambient hint + snackbar copy)
- `app/src/main/java/com/spoolpainter/app/ui/screens/settings/SettingsScreen.kt` (modified — IME padding on host + content)

Tests:
- `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelTest.kt` (modified — `awaitNonAmbientSnackbar` helper + 3 assertion updates)
- `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelTwoTagTest.kt` (modified — same helper + 5 assertion updates)

Total: 10 production / 2 test / 0 deleted. No build / config / dependency changes.
