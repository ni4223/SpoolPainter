# U13 Vendor Reframe + State-Aware Copy Pass — 2026-06-06 (round 2)

Continuation of `u13-ui-polish-round.md` from earlier the same day.
Same install-gate iteration session. Tests **403 / 403 ✅** held
throughout. APK 64 MB debug. No close-out commit (per
[[feedback_aidlc_unit_close_out_commit]]).

## Install-gate matrix progress at end of session

- §A Layout / cosmetics — **PASS**
- §B.1 New spool from blank form — **PASS**
- §B.2 Existing-spool variant edit — **PASS** (B2e dropped: re-saving
  clean form is allowed; `sparseDiff` collapses to 0-byte PATCH)
- §B.3 Existing-spool remaining_weight — **PASS** (with new chip-style
  radio + new "Spool on scale: N g" copy)
- §C.1 Standard NDEF write — **PASS** (with vendor pair-another label
  fix)
- §C.2 Pair-another tag — **NOT YET ATTEMPTED**
- §C onward — pending

## Big architectural decision this round: vendor UID mapping moved off Save → onto Write

User flagged Save was overloaded (Q-U13-1=A behaviour: Save handled
vendor UID-only pair on top of HTTP form edits). Each button now has
exactly one job.

| State | Save | Write |
|---|---|---|
| Nothing picked | `Create filament and spool` | disabled |
| Filament picked, no spool | `Create spool` | disabled |
| Spool picked | `Update` | `Write tag` (NDEF + UID append) |
| Vendor tag, no spool | `Create filament and spool` | disabled |
| Vendor tag + spool picked | `Update` | `Map tag` (HTTP-only UID append, no NDEF) |
| Disabled (form incomplete) | `Save to Spoolman` (destination) | varies |

**Code shape**:
- `MainViewModel.onSaveTapped` — vendor branch removed; Save is always
  pure HTTP form edits via `SaveToSpoolmanUseCase`.
- `MainViewModel.onWriteTapped` — vendor branch added: when
  `state.observedTagKind == Vendor && writeMode == Spoolman`, route
  to `VendorUidOnlyPairUseCase` (HTTP-only UID append, no NDEF).
- `canWrite` simplified — vendor + Spoolman is no longer `false`;
  enabled when `selectedSpoolId != null`.
- Inline `writeLabel` flips to "Map tag" when vendor + Spoolman.
- `computeSaveLabel` dropped the `observed` parameter (no longer
  branches on tag kind).

**Bug fixed in same round**: passive ambient taps were flipping
`observedTagKind` via the `lastSeenTag` collector, causing vendor
mode to "stick" from a random pass-by tap. Removed the state mutation
from the `lastSeenTag` collector — vendor mode now reached **only**
through explicit Read (`nfc.state.Success`). The collector still
fires the ambient-tap snackbar.

**Bug fixed**: Read of an unpaired vendor tag (BlankForm path) now
clears `selectedSpoolId` (form + spoolman). Reasoning: user signalled
"I want to map this tag," so the previously-picked spool is stale.
Non-vendor (truly blank) Read keeps v1 parity (form + spool
preserved).

**Tests updated**:
- `MainViewModelSaveTapTest::vendor-tag observed routes Save to vendor UID-only pair (Q-U13-1=A)`
  → renamed + re-asserts the **inverse** behaviour ("does NOT divert
  Save to vendor pair").
- `MainViewModelRawWriteTest::vendor tag plus Spoolman - Save routes to vendorUidOnlyPair (Q-U13-1=A)`
  → renamed + re-asserts vendor + Spoolman + selected spool routes
  Write to vendor pair.
- Both tests swapped `nfc.pushLastSeenTag(...)` → `nfc.pushState(NfcResult.Success(...))`
  to match the new "vendor state only via explicit Read" rule.

## State-aware copy pass

User collaborated with ChatGPT to draft microcopy for: Filament
section hint, Write disabled hint, Save button label, picker
placeholders, weight radio supportingText. Final shipped strings
below.

### Save button (state-aware)

```
canSave false              → "Save to Spoolman"          (destination, when greyed)
selectedSpoolId != null    → "Update"
selectedFilamentId != null → "Create spool"
otherwise                  → "Create filament and spool"
```

(No vendor branch — vendor mapping is now Write's job.)

### Write button hint (under disabled Write tag, paired with Save label)

```
selectedSpoolId != null              → null (Write enabled)
observed == Vendor                   → "Create a spool to map this tag."
selectedFilamentId != null           → "Create spool first."
otherwise                            → "Create filament and spool first."
```

> **2026-06-07 copy tweak**: dropped redundant "Tap" prefix on the
> Write hint — the button itself tells you it's tappable.
> FilamentSection's filament-selected hint left as-is
> (`"Tap Save to create a spool for this filament."`) per user
> direction — that one reads as a verb prompt, not a button label
> repeat.

### Write button label

```
writeMode == Spoolman && observed == Vendor → "Map tag"
writeMode == RawNoUrl                       → "Write to NFC"
otherwise                                    → "Write tag"
```

### Filament section hint (under "Filament" header)

```
selectedSpoolId != null     → null
selectedFilamentId != null  → "Tap Save to create a spool for this filament."
otherwise                   → "Select a filament, or fill in the details to create one."
```

(Spool-selected case suppresses the hint entirely so it doesn't
compete with the in-progress edit caption.)

### Picker placeholders (unselected dropdown)

```
SpoolmanDropdown      → "Spools in Spoolman"      (was "Select a Spoolman spool…")
FilamentPicker        → "Filaments in Spoolman"   (was "Optional")
```

### Weight method radio styling + copy

- **Selected radio option** wrapped in a `Surface(shape = 20dp,
  color = primaryContainer)` with SemiBold label. Active method now
  reads as a chip. Inactive stays plain text + radio dot.
- Filament weight locked supportingText (`Switch to Remaining or
  Measured above to edit.`) **dropped** entirely.
- Remaining-mode supportingText: `Scale will read N g` →
  `Spool on scale: N g`. Pairs with Measured-mode `Filament left: N g`.
- Active value field placeholder `Optional` dropped.

### Pair-another second-tap status overlay

- `WritingSecondTag` overlay copy was `"Tap second tag to write"` —
  misleading on the vendor pair-another path (no NDEF write, just UID
  capture). Reworded to `"Tap second tag"` so it's correct for both
  branches (NDEF on blank, HTTP-only on vendor).

## New memory captured this session

- [[feedback_no_em_dash]] —
  `~/.claude/projects/-Users-mnipun-AndroidStudioProjects-SpoolPainter/memory/feedback_no_em_dash.md`.
  Never use `—` in user-facing copy. Use periods or commas. Set up
  earlier in the day; reaffirmed this round when the user flagged
  copy with em dashes.

## File-level diff vs U13 polish round 1

Modified additionally this round:

- `app/src/main/java/com/spoolpainter/app/ui/components/FilamentForm.kt`
  — `SaveToSpoolmanButton` `label: String` parameter; disabled colors
  preserved.
- `app/src/main/java/com/spoolpainter/app/ui/components/FilamentPicker.kt`
  — placeholder copy.
- `app/src/main/java/com/spoolpainter/app/ui/components/FilamentSection.kt`
  — state-aware hint, `selectedSpoolId` parameter added; `FilamentForm`
  call site threads it through.
- `app/src/main/java/com/spoolpainter/app/ui/components/MoreDetailsExpander.kt`
  — locked filament-weight supportingText dropped.
- `app/src/main/java/com/spoolpainter/app/ui/components/WeightMethodRadio.kt`
  — chip-style RadioOption (Surface wrap, primaryContainer fill on
  selected, SemiBold), Remaining hint copy, "Optional" placeholder
  drop.
- `app/src/main/java/com/spoolpainter/app/ui/components/SpoolPainterLogo.kt`
  — leading Spacer(40dp) so the spool **hole** sits at screen center
  instead of bounding-box center (preserves comment about NFC-waves
  region of the SVG taking ~23% of width on the right).
- `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainScreen.kt`
  — Save label routing, `computeSaveLabel`, `computeWriteHint`,
  inline writeLabel vendor branch, NfcStatusOverlay second-tag copy,
  picker placeholder, snackbar bottom-lift to 25%, custom Surface
  snackbar, BoxScope overlay primitive, RadiatingWavesIndicator,
  drop ReadingHint/WritingHint composables.
- `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainViewModel.kt`
  — `onSaveTapped` vendor branch removed; `onWriteTapped` vendor
  branch added; `canWrite` simplified; `lastSeenTag` collector no
  longer flips `observedTagKind`; BlankForm Vendor branch clears
  `selectedSpoolId`; comment updates throughout.
- `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelSaveTapTest.kt`
  — vendor-tag test renamed + flipped to assert NEW behaviour;
  `pushLastSeenTag` → `pushState`.
- `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelRawWriteTest.kt`
  — vendor-tag test renamed + asserts vendor pair routes through
  Write; new imports for SpoolmanFilament / SpoolmanSpool /
  SpoolmanVendor; `pushLastSeenTag` → `pushState`.

No new files this round. No test files added (the use cases didn't
change shape; only the routing changed).

## Outstanding for next session

- Continue install-gate matrix at **§C.2 (Pair-another tag)**.
- Then §C.3 (Sheet Cancel — second tag listening), §C.4 (Read flow →
  Save → Write), §C.5 (Read → blank tag → Save → Write — orphan flow
  per Q-U13-5=A).
- §D (Cancel toggles), §E (Vendor + RawNoUrl dead end), §F (snackbar
  copy regression sweep — the ~14 strings will need re-verification
  against the new copy this round drafted), §G (edge cases), §H
  (Snapmaker U1 round-trip), §I (release-side build).
- Eventually: close-out commit shape `feat(v2.1): U13 — action split
  (Save/Write) + radio weight picker + UI polish + vendor reframe`.

## Working tree fingerprint at session end

Same files as the U13 session-resume note PLUS the polish-round-1
edits PLUS this round's edits (no new files added this round). All
17 modified + 7 untracked from the original session resume still
present, several with deeper edits now.

```
git status --short:
 M aidlc-docs/aidlc-state.md
 M aidlc-docs/audit.md
 M app/src/main/java/com/spoolpainter/app/domain/usecases/CreateAndPairUseCase.kt
 M app/src/main/java/com/spoolpainter/app/ui/components/FilamentForm.kt
 M app/src/main/java/com/spoolpainter/app/ui/components/FilamentPicker.kt
 M app/src/main/java/com/spoolpainter/app/ui/components/FilamentSection.kt
 M app/src/main/java/com/spoolpainter/app/ui/components/MoreDetailsExpander.kt
 M app/src/main/java/com/spoolpainter/app/ui/components/SpoolPainterLogo.kt
 M app/src/main/java/com/spoolpainter/app/ui/screens/main/MainScreen.kt
 M app/src/main/java/com/spoolpainter/app/ui/screens/main/MainUiState.kt
 M app/src/main/java/com/spoolpainter/app/ui/screens/main/MainViewModel.kt
 M app/src/test/java/com/spoolpainter/app/domain/usecases/CreateAndPairUseCaseTest.kt
 M app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelBannerTest.kt
 M app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelCurrencyTest.kt
 M app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelFilamentPickerTest.kt
 M app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelMoreDetailsExpanderTest.kt
 M app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelRawWriteTest.kt
 M app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelRefreshTest.kt
 M app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelSortTest.kt
 M app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelTest.kt
 M app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelTwoTagTest.kt
?? aidlc-docs/construction/plans/u13-action-split-code-generation-plan.md
?? aidlc-docs/construction/u13-action-split/
?? app/src/main/java/com/spoolpainter/app/domain/usecases/SaveToSpoolmanResult.kt
?? app/src/main/java/com/spoolpainter/app/domain/usecases/SaveToSpoolmanUseCase.kt
?? app/src/main/java/com/spoolpainter/app/ui/components/WeightMethodRadio.kt
?? app/src/test/java/com/spoolpainter/app/domain/usecases/SaveToSpoolmanUseCaseTest.kt
?? app/src/test/java/com/spoolpainter/app/support/FakeSaveToSpoolmanUseCase.kt
?? app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelSaveTapTest.kt
?? app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelWeightMethodTest.kt
```

Branch `v2`, 1 ahead of `origin/v2` (commit `2add547` from v2.0.3
Cluster D). Three U13 polish artefacts on disk now:
- `u13-summary.md` (Code Gen Part 2 baseline)
- `u13-ui-polish-round.md` (round 1 — earlier this day)
- `u13-vendor-reframe-and-copy-pass.md` (round 2 — this file)
