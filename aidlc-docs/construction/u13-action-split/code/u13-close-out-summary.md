# U13 Close-Out Summary — Action Split + v2.1 Polish

**Status**: DONE 2026-06-07
**Branch**: `v2`, close-out commit pending push to `origin/v2`
**Tests**: **403 / 403** ✅
**Build matrix**: `compileDebugKotlin` ✅ / `testDebugUnitTest` ✅ /
`assembleDebug` ✅ 65 MB / `assembleRelease` ✅ **7.0 MB R8** /
`bundleRelease` ✅ 7.7 MB AAB
**Install gate**: full §A through §I PASS (most paths walked on-device
2026-06-06/07; §F snackbar regression sweep + §H Snapmaker round-trip +
§I.5/§I.6 release smoke marked PASS-by-coverage from prior units).

## What ships in U13

### Action split (the original ask)

- `Save & Write` combo button replaced by **two top-level buttons**:
  - **Save to Spoolman** (HTTP only) at the bottom of the outer Card.
  - **Write** in the inline `[Read | Write]` row beneath Save.
  Each button does exactly one job; either button can flip to a
  full-width **Cancel** during its own tag-waiting flow.
- Vendor UID mapping — originally Save's responsibility per Q-U13-1=A
  — got moved onto **Write** mid-install-gate (round 2 reframe). Save
  is now a pure HTTP form-edit action across all states; Write
  handles NDEF on writable tags AND HTTP-only UID append (`Map tag`
  label) on vendor tags.
- Bidirectional Remaining/Measured row replaced by a Spoolman-parity
  radio (`WeightMethodRadio.kt`): top segmented row picks the active
  method; only that method's input field renders below. Inactive
  method's field is hidden entirely — no silent keystroke loss.

### Layout reshape (round 1 polish)

- Inner sections moved to elevation-0 tonal `surfaceContainerHigh`
  Surfaces inside an outer Card (Q-U13-3 went B → C during install).
- Stationary bottom action bar dropped; inline `[Read | Write]` row
  lives inside the outer Card under Save.
- `NfcStatusOverlay` (radiating-waves indicator + headlineSmall label,
  fade+scale animation) replaces the old top-of-screen reading/writing
  pill. Stacked above PullToRefreshBox.
- Snackbar repositioned to 25% above bottom inset; custom Surface
  with `bodyLarge` text + `inverseSurface` colour.
- Logo gets a leading `Spacer(40dp)` so the spool *hole* sits at
  screen centre (not bounding-box centre) — the NFC-waves region
  takes ~23% of width on the right.

### Microcopy pass (round 2)

State-aware Save label:
```
canSave false              → "Save to Spoolman"          (destination, when greyed)
selectedSpoolId != null    → "Update"
selectedFilamentId != null → "Create spool"
otherwise                  → "Create filament and spool"
```

Write button hint (under disabled Write tag):
```
selectedSpoolId != null              → null (Write enabled)
observed == Vendor                   → "Create a spool to map this tag."
selectedFilamentId != null           → "Create spool first."
otherwise                            → "Create filament and spool first."
```

(The Write hint dropped a redundant "Tap" prefix late in the session
2026-06-07 — buttons already tell you they're tappable.)

Filament section hint:
```
selectedSpoolId != null     → null (suppress)
selectedFilamentId != null  → "Tap Save to create a spool for this filament."
otherwise                   → "Select a filament, or fill in the details to create one."
```

Picker placeholders (unselected dropdowns):
- Spool dropdown empty → `Spools in Spoolman` (was `Select a Spoolman spool…`)
- Filament dropdown empty → `Filaments in Spoolman` (was `Optional`)

Weight radio styling:
- Selected option wrapped in a `Surface(shape=20dp, color=primaryContainer)` with SemiBold label.
- Inactive options stay plain text + radio dot.
- Filament-weight locked supportingText
  (`Switch to Remaining or Measured above to edit.`) dropped entirely.
- Remaining-mode supportingText: `Scale will read N g` →
  `Spool on scale: N g` (pairs with Measured-mode `Filament left: N g`).

Pair-another second-tap overlay copy:
- `Tap second tag to write` → `Tap second tag` (correct for both
  NDEF and HTTP-only branches).

## v2.1 polish landed mid-close-out

After §I PASS, the user pulled in three additional polish items from
v2.1 that fit on top of the same close-out commit:

### Currency: 3 segmented options → 22-entry dropdown

`Settings.Currency` enum extended from 3 entries to 22. New
`SettingsCurrencySection.kt` uses an exposed dropdown matching the
Sort sections' visual grammar. Existing IDs (`Dollar`, `Euro`,
`Generic`) preserved as canonical names so DataStore JSON stays
back-compat.

Currencies added:
US Dollar, Euro, British Pound, Japanese Yen, Chinese Yuan, Indian
Rupee, South Korean Won, Swiss Franc, Canadian Dollar, Australian
Dollar, New Zealand Dollar, Brazilian Real, Mexican Peso, Swedish
Krona, Turkish Lira, Russian Ruble, South African Rand, Israeli
Shekel, UAE Dirham, Hong Kong Dollar, Singapore Dollar, Money
(generic).

`SettingsSegmentedSection.kt` was deleted — Currency was its only
consumer.

### Existing-spool filament-record unlock (UI-13 followup)

The v2.0.2 lockdown (decision J) kept Material / Brand / Color /
Density / Diameter / Filament weight / Temperatures locked on the
existing-spool path — the rationale was preventing tag↔Spoolman
identity desync when Save & Write was the combined action. Now Save
is a pure HTTP PATCH (no tag bytes touched), so the desync class
collapses: Save corrects the form, subsequent Write rewrites the tag
to match.

Newly editable on existing-spool path:
- **Color** (`filament.color_hex`)
- **Density** (`filament.density`)
- **Filament weight** (`filament.weight`)
- **Temperatures** (`filament.settings_extruder_temp` + `settings_bed_temp`)
- **Variant** (`extra.variant`) — was already

Stays locked:
- **Material + Brand** — changing those means "wrong filament
  picked"; the user should pick a different filament instead, not
  silently re-classify the existing one.

Wire shape:
- `PatchFilamentBody` extended with `color_hex: String?`.
- `ExpanderOverrides` extended with `colorHex` + `extruderTemp` +
  `bedTemp` fields.
- `FormState.toExpanderOverrides()` existing-spool branch now flows
  the full bag (was variant-only).
- `SaveToSpoolmanUseCase` switched from
  `applyVariantToFilamentOfSpool` (narrow) to
  `applyOverridesToFilamentOfSpool` (full). The narrow call site is
  gone.
- `SpoolmanRepository.applyOverridesIfNeeded` populates
  `color_hex` + temps in the patch body.
- `sparseDiff` extended with `color_hex` (case-insensitive equality)
  + `isEmpty()` updated. Sparse diffing handles the "did the user
  actually change it" math; we send the full bag every Save and let
  Spoolman side collapse to a no-op when stable.

### Existing-spool spool.price unlock

Decision M v2.0.2 ("price is set at acquisition, not a moving stock
quote") locked Price on existing-spool. v2.1 reverses this — price
edits now PATCH `spool.price` only (per-spool override). Filament-
record price stays untouched, so sibling spools keep their
acquisition prices.

Wire shape:
- `MoreDetailsExpander.kt` price field gate: `enabled =
  spoolmanFieldsEnabled && !showSpoolScopeFields` →
  `enabled = spoolmanFieldsEnabled`.
- `SaveToSpoolmanUseCase` step 1b — the existing
  `patchSpoolFields(remaining_weight, spool_weight)` call extended
  with `price`, gated by the same `prefilledPriceMajor` stale-prefill
  snapshot logic that protects `remainingWeightG` and
  `emptySpoolWeightG`.

### Caption cleanup

The `"Editing this updates Spoolman"` caption above Variant and the
`"Editing these updates Spoolman"` caption above the Weight radio
both dropped. With the Save button label flipping to "Update" when a
spool is selected and Material + Brand visibly disabled, the
affordance is self-explanatory.

## §C.3 mid-gate fix (UI-35)

Filed as a session blocker the moment §C.3 failed: the
PairAnotherTagSheet vanished entirely after the user tapped "Pair
another", so there was no Cancel surface during second-tag listening
and no way back to the prompt without tapping a tag (or waiting out
the 15s timeout).

First-cut fix attempt was rejected (spinner-on-Cancel-button + two
Cancel surfaces broke convention). Shipped second-cut: extend
`isWriteCancellable` to include `WritingSecondTag` so the existing
inline `[Read|Write] → Cancel` row takes over while the sheet auto-
dismisses; `onWriteTapped` routes the cancel through the existing
`onPairAnotherTagAccepted` toggle. One Cancel surface, one
convention. Files changed: 2 (MainViewModel only — sheet/host/
projection reverted to pre-bug state).

## File inventory

**Production created (5)**:
- `app/src/main/java/com/spoolpainter/app/domain/usecases/SaveToSpoolmanResult.kt`
- `app/src/main/java/com/spoolpainter/app/domain/usecases/SaveToSpoolmanUseCase.kt`
- `app/src/main/java/com/spoolpainter/app/ui/components/WeightMethodRadio.kt`
- `app/src/main/java/com/spoolpainter/app/ui/screens/settings/SettingsCurrencySection.kt`
- (none others)

**Production deleted (1)**:
- `app/src/main/java/com/spoolpainter/app/ui/screens/settings/SettingsSegmentedSection.kt`
  (Currency was the only consumer; replaced by the new dropdown.)

**Production modified (12)**:
- `app/src/main/java/com/spoolpainter/app/data/local/Settings.kt` — Currency expanded 3 → 22
- `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanRepository.kt` — sparseDiff + applyOverridesIfNeeded
- `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanRequests.kt` — PatchFilamentBody.color_hex + ExpanderOverrides.{colorHex,extruderTemp,bedTemp}
- `app/src/main/java/com/spoolpainter/app/domain/usecases/CreateAndPairUseCase.kt`
- `app/src/main/java/com/spoolpainter/app/ui/components/FilamentForm.kt` — narrowed identityLocked semantics + drop "Editing" caption + Color unlocked
- `app/src/main/java/com/spoolpainter/app/ui/components/FilamentPicker.kt`
- `app/src/main/java/com/spoolpainter/app/ui/components/FilamentSection.kt`
- `app/src/main/java/com/spoolpainter/app/ui/components/MoreDetailsExpander.kt` — price unlock + drop Weight caption
- `app/src/main/java/com/spoolpainter/app/ui/components/SpoolPainterLogo.kt`
- `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainScreen.kt` — Save/Write split + state-aware copy + filamentSpecLocked narrowed
- `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainUiState.kt` — toExpanderOverrides existing-spool branch + filamentSpecLocked rationale
- `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainViewModel.kt` — onSaveTapped/onWriteTapped reframe + isWriteCancellable + lastSeenTag collector fix + BlankForm Vendor branch fix
- `app/src/main/java/com/spoolpainter/app/ui/screens/settings/SettingsScreen.kt` — segmented Currency → dropdown
- (and `data/remote/spoolman/SpoolmanRepository.kt` mentioned above)

**Test created (4)**:
- `app/src/test/java/com/spoolpainter/app/domain/usecases/SaveToSpoolmanUseCaseTest.kt`
- `app/src/test/java/com/spoolpainter/app/support/FakeSaveToSpoolmanUseCase.kt`
- `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelSaveTapTest.kt`
- `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelWeightMethodTest.kt`

**Test modified (10)**:
- `app/src/test/java/com/spoolpainter/app/domain/usecases/CreateAndPairUseCaseTest.kt`
- 9 × `MainViewModel*Test.kt` (banner, currency, filament-picker, more-details-expander, raw-write, refresh, sort, base, two-tag)

## Locked decisions

- **Q-U13-1=A** (round 2 reversed): Save was originally going to handle vendor pair; reversed mid-install-gate so Write handles vendor pair via HTTP-only UID append.
- **Q-U13-2** (locked from plan §1.4): drop Gross weight method.
- **Q-U13-3** (round 1 reversed B → C): inner sections use tonal `surfaceContainerHigh` Surface, not elevation-0 + thin border.
- **Q-U13-4=A**: Save button destination label is "Save to Spoolman" (when greyed).
- **Q-U13-5=A**: orphan-Read flow goes Save → Write (no auto-Save inside Write).

## Carry-overs to v2.1.x

- **UI-36 — Archive a spool / filament from the app** (logged 2026-06-07).
  Surface design + wire format sketched in `aidlc-docs/ui-followups.md`.
  Carve as a v2.1.x patch after the v2.0.3 / v2.1 testing-track push lands.

## Release window

Per user direction "v2.1 — separate release", U13 ships as v2.1.0
(versionCode bump 103 → 104, versionName 2.0.3 → 2.1.0). The currency
dropdown + filament-record unlock + spool.price unlock all ride the
same release. Play Console Open testing track upload is the next user
action after the close-out commit + push.
