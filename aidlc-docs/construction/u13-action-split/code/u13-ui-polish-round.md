# U13 Install-Gate UI Polish Round — 2026-06-06

Install-gate iteration session that ran on top of U13 Code Gen Part 2.
Manual matrix sections **§A pass / §B.1 pass / §B.2 pass (B2e dropped) /
§B.3 pass**. Rest of the matrix not yet attempted.

Tests **403 / 403 ✅** held throughout (no test assertion strings touched).
APK still 64 MB debug. No close-out commit (per
[[feedback_aidlc_unit_close_out_commit]]).

## Decisions reversed or revised this round

- **Q-U13-3** revised B → **C (tonal Surface, surfaceContainerHigh)**.
  Original elevation-0 + thin border felt dated. New shape: each inner
  section is an M3 tonal-palette `Surface` in `surfaceContainerHigh`
  (no border, no elevation). Modern, neutral, plays nicely with the
  outer elevation-5 Card.
- **Stationary bottom [Read | Write] bar dropped**. Read tag and Write
  tag are now **inline OutlinedButtons** under Save inside the outer
  Card. Reframe driver: Save is primary, tag I/O is secondary. Bottom
  bar's heavy chrome was over-emphasising secondary actions.
- **Single full-width Cancel** replaces the two action buttons during
  any tag-waiting flow (Read, standard NDEF Write, pair-another second
  tag). Cancel routes to whichever flow is running. Cleaner than
  per-button Cancel toggle.
- **Status overlay** — top-of-screen reading/writing pill removed.
  Replaced by a centered floating **NfcStatusOverlay**: animated
  radiating-waves indicator (3 phase-staggered concentric rings,
  ~1800 ms cycle) + `headlineSmall` status text inside an
  `primaryContainer` Surface. Animates in/out with fade+scale. Pinned
  to screen center, doesn't push content, visible regardless of scroll
  position.
- **B2e dropped from the matrix**: re-tapping Save with no edits is
  allowed; `SpoolmanRepository.sparseDiff` already collapses
  unchanged fields to a 0-byte PATCH, so the snackbar fires on the
  user's intent without dirty-tracking logic.

## Copy revisions (no em dashes per
[[feedback_no_em_dash]] saved this session)

- **Save button label** is now **state-aware**:
  - canSave false → `Save to Spoolman` (destination, not action)
  - Spool selected → `Update`
  - Filament selected, no spool → `Create spool`
  - Vendor tag observed, no spool → `Create spool and map tag`
  - Nothing selected → `Create filament and spool`
- **Write button hint** (under disabled Write tag, state-aware via
  new `computeWriteHint`):
  - Nothing picked → `Pick a spool, or create one and tap Save.`
  - Filament picked → `Tap Save to create a spool for this filament.`
  - Vendor tag → `Vendor tag detected. Tap Save to map it.`
  - Spool selected → no hint
- **Filament section hint** (under "Filament" header, state-aware):
  - Nothing picked →
    `Select a filament, or fill in the details to create one.`
  - Filament picked, no spool →
    `Tap Save to create a spool for this filament.`
  - Spool selected → no hint
- **Picker placeholders** rewritten:
  - Spool dropdown empty → `Spools in Spoolman` (was
    `Select a Spoolman spool…`)
  - Filament dropdown empty → `Filaments in Spoolman` (was `Optional`)
- **Weight method radio**:
  - Filament-locked supportingText `Switch to Remaining or Measured
    above to edit.` **removed** (filament weight greys with no
    explanatory line on locked path).
  - Remaining-mode supportingText was `Scale will read N g`, now
    `Spool on scale: N g` (pairs with Measured-mode `Filament left:
    N g`).

## UI primitives changed

- `MainScreen` outer layout: PullToRefreshBox now nested in an outer
  `Box(fillMaxSize)` so `NfcStatusOverlay` can stack on top via
  `BoxScope.align(Center)`.
- `Scaffold.bottomBar` slot **removed**. `MainBottomActions` →
  `InlineReadWriteRow` (lives inside the outer Card under
  SaveToSpoolmanButton).
- `Snackbar` is now a custom `Surface` (rounded 20dp, `inverseSurface`
  color, `bodyLarge` text, fillMaxWidth) lifted to **25 % above the
  bottom inset** so it sits in the lower-middle, not hugging the
  gesture bar.
- `WeightMethodRadio.RadioOption` now wraps in a `Surface` with
  `primaryContainer` background when selected and SemiBold label —
  active method reads as a chip; inactive stays plain.
- `NfcStatusOverlay` + `RadiatingWavesIndicator` are new private
  composables in `MainScreen.kt`.
- `SpoolPainterLogo`: leading `Spacer(40.dp)` added to the image Row
  so the spool **hole** sits at screen center instead of the bounding
  box (NFC-waves region of the SVG accounts for ~23% of width on the
  right). Original Spacer-trick on the title preserved.
- `SaveToSpoolmanButton` gained `label: String = "Save to Spoolman"`
  parameter; disabled-state colors restored to original
  `primary @ 0.5α` after an accidental drop mid-session.

## File-level diff vs U13 Code Gen Part 2 baseline

Modified beyond the Part 2 plan list:

- `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainScreen.kt`
  (overlay, bottom-bar drop, snackbar shape, Save label routing,
  state-aware hint helper, logo Spacer fix)
- `app/src/main/java/com/spoolpainter/app/ui/components/FilamentForm.kt`
  (Save button label parameter)
- `app/src/main/java/com/spoolpainter/app/ui/components/FilamentPicker.kt`
  (placeholder copy)
- `app/src/main/java/com/spoolpainter/app/ui/components/FilamentSection.kt`
  (state-aware hint, `selectedSpoolId` param added)
- `app/src/main/java/com/spoolpainter/app/ui/components/MoreDetailsExpander.kt`
  (filament-weight supportingText drop in locked state)
- `app/src/main/java/com/spoolpainter/app/ui/components/WeightMethodRadio.kt`
  (chip-style RadioOption, hint copy revision, "Optional" placeholder
  drop)
- `app/src/main/java/com/spoolpainter/app/ui/components/SpoolPainterLogo.kt`
  (leading Spacer for spool-hole centering)

No test files modified this round (no contract changes).

## Memory captured this session

- [[feedback_no_em_dash]] —
  `~/.claude/projects/-Users-mnipun-AndroidStudioProjects-SpoolPainter/memory/feedback_no_em_dash.md`.
  Never use `—` in user-facing copy. Use periods or commas.

## Outstanding for next session

- Continue install-gate matrix at **§C (Write — NFC-only happy
  paths)**. §A / §B.1 / §B.2 / §B.3 confirmed pass.
- Rest of §C / §D / §E / §F (snackbar copy regression sweep, ~14
  strings, will need re-verifying against the new tighter copy)
  / §G / §H Snapmaker round-trip / §I release-side build.
- Eventually: close-out commit shape `feat(v2.1): U13 — action split
  (Save/Write) + radio weight picker + UI polish`.

## Working tree fingerprint at end of session

Same as the U13 session-resume note before this round started:
17 modified + 7 untracked, branch `v2` 1 ahead of `origin/v2`
(commit `2add547` from v2.0.3 Cluster D). UI polish round added no
new files; only edited the 6 files listed above.
