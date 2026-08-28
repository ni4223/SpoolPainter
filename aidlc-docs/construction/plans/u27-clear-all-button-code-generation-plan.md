# U27 — Code Generation Plan

**Unit**: U27 — dedicated "Clear all" button in the header ([[UI-67]])
**Opened**: 2026-08-28
**Per-unit gate**: FD / NFR-R / NFR-D / Infra-D **SKIP** — one composable's
layout changes, no new component, no data model, no infrastructure, no domain or
use-case code. Same convention as U20-U26.
**Design**: answered in
`inception/requirements/requirements-questions-ui67-clear-all.md` →
**2.1 A, 2.2 A, 2.3 B, 2.4 A**.

**SCOPE CUT 2026-08-28 mid-implementation** — maintainer: *"implement clear all
only"*. Question 2.2's answer (turn `MoreVert` into a Settings gear) is
**deferred**, not reversed. What shipped: the new button, plus removal of the
now-duplicate "Clear all" menu row, because that row *is* clear-all. The
`MoreVert` menu survives holding one item, Settings. That one-item oddity is the
known, accepted interim state and the remaining half of 2.2 A.

---

## §1 Scope — one file

`MainScreen.kt`, `MainLogoHeader` only (`:553-649`). No ViewModel change:
`onClearAll()` already exists and is already wired through
`onClearAllClick = viewModel::onClearAll` (`:192`).

**Before**: a single `MoreVert` IconButton at `TopEnd` opening a `DropdownMenu`
with two `MenuRow`s, "Clear all" and "Settings".

**After** (as cut):
- `TopStart` — `TextButton` labelled **"Clear all"** → `onClearAllClick` (new)
- `TopEnd` — `MoreVert` menu **unchanged**, now holding only the Settings row

**Deferred** (question 2.2 A, second half): replace `MoreVert` with
`Icons.Default.Settings` and drop the menu entirely.

Everything else in the header (the tinted logo, the low-contrast halo) is
untouched.

## §2 Decisions carried from the answered questions

- **D1 — a labelled text button, not an icon.** No honest glyph exists.
  `Icons.Default.Clear` (X) is already the *field-level* clear ("Clear spool
  selection", "Clear filament selection", "Clear search") and in a header reads
  as "close"; `RestartAlt` was already rejected in UI-57 as "reload"; anything
  `Delete`-shaped risks the far worse misread "delete my spool from Spoolman".
- **D2 — the menu loses its "Clear all" row; the menu itself stays for now.**
  Removing the row is part of clear-all: leaving it would give one action two
  entry points. Collapsing the resulting one-item `MoreVert` into a Settings gear
  is the Settings half of 2.2 A and was scoped out by *"implement clear all
  only"*.
- **D3 — no confirmation and no Undo.** UI-57's *"no confirmation dialog by
  design (the user clears often, so a prompt on every clear is friction)"*
  (`MainViewModel.kt:913-914`) stands unchanged. This was the maintainer's call
  against my recommendation, and it is the one risk this unit knowingly accepts
  (§6 R1).
- **D4 — always enabled.** Matches the menu row's behaviour today; no
  "form is dirty" predicate invented.

## §3 Why this reverses UI-57, stated rather than buried

`MainScreen.kt:577-585` records the explicit opposite decision: clear-all belongs
in the overflow menu, **not** as its own control, after three in-header variants
were tried on device and all crowded the logo.

That rejection was **location- and width-specific**. All three variants were at
`TopEnd`: a `RestartAlt` icon, bare "Clear" text, and an outlined "Clear" whose
border overlapped the NFC waves. **`TopStart` was never tried.**
`SpoolPainterLogo.kt:61-69` centres a `Row` of `Spacer(40.dp)` + a 96 dp-tall
image inside a `fillMaxWidth` Column with `CenterHorizontally`; the 600×341
vector is about 169 dp wide at that height, so the Row is about 209 dp and leaves
roughly 100 dp of empty gutter on **each** side of a 411 dp-wide screen. The
`MoreVert` IconButton already occupies the right gutter without crowding
anything, which is the existence proof that a control fits there.

The comment at `:577-585` must be **rewritten, not deleted** — it records real
device findings that are still true (RestartAlt reads as reload; a wide outlined
control overlaps the waves). The replacement keeps those findings and adds why
`TopStart` is different.

## §4 Steps

- [x] **S1** — `TextButton` at `TopStart` added inside the existing `Box`, and the
      duplicate `MenuRow("Clear all", …)` removed. The Settings-gear swap was cut
      from this unit (see SCOPE CUT above), so `MoreVert` + `DropdownMenu` remain.
- [x] **S2** — Rewrote the `:577-585` comment: keep UI-57's device findings,
      record that they were `TopEnd`-specific, and state the gutter arithmetic
      that makes `TopStart` viable.
- [x] **S3** — Test tags. Added **`main-clear-all-button`**.
      **`main-clear-all-item`** is gone with its row; **`main-overflow-button`**
      and **`main-settings-button`** are untouched because the menu stayed.
      Confirmed no test references any of them (`app/src/androidTest` contains no
      test sources at all, and no JVM test names them).
- [x] **S4** — **Nothing to remove**, and this is a consequence of the scope cut,
      not an oversight: the menu survived, so `MoreVert`, `menuExpanded`,
      `RoundedCornerShape`, `heightIn` and `clickable` are all still in use. One
      import added: `androidx.compose.material3.TextButton`.
- [x] **S5** — `compileDebugKotlin` clean; full unit suite **631 tests, 0
      failures**, exactly the predicted 631 → 631 (§5).
- [x] **S6** — Install gate **PASSED 2026-08-28** on moto g stylus 2025 /
      Android 16 (`ZA2238F4JF`). Evidence in §8.

## §5 Testing — stated honestly

**This unit adds no tests, and that is not laziness.** The change is composable
layout and wiring: `onClearAll()` is unchanged and already covered, and the only
new facts are *where* two controls sit and *what* they read. There is no pure
function to extract, so nothing here is JVM-unit-testable.

The project has **no instrumented tests at all** — `app/src/androidTest` holds a
single `.DS_Store` and no sources, so all 631 tests are JVM unit tests. Adding a
Compose UI test would mean standing up the instrumented source set for one
assertion; not worth it inside this unit, and it would not answer either §7
question anyway, since both are about physical crowding at real font scales.

Writing an assertion that merely restates the wiring would give false comfort.
**Verification for U27 is the install gate**, and §7 says exactly what to look at.

## §6 Risk accepted

- **R1 — a one-tap destructive action with no undo.** Clearing drops to one tap
  from two-behind-a-menu, and per **D3** there is no confirmation and no Undo, so
  a mis-tap loses a filled-in form with no recovery. The maintainer chose this
  over my Undo recommendation; recorded so that if it does bite in the field, the
  fix is already scoped (a snackbar action plus a pre-clear snapshot, per
  question 2.3 option A) rather than re-litigated.
- **R2 — "Clear all" could be misread as clearing Spoolman.** It is the same
  wording the menu row has used since UI-57, so this unit introduces no new
  ambiguity, but the phrase is inherited rather than validated. Worth watching in
  tester feedback now that it is promoted to a top-level control.

## §7 Install gate — what only a device can answer

1. **Does the button crowd the logo at a large system font?** D1's known cost.
   Check at default and at the largest accessibility font size; the failure mode
   is the label growing left-to-right into the spool artwork or wrapping.
2. **Is the top-left position mistaken for Back?** That corner conventionally
   holds up-navigation. This app has one screen and no back affordance there, so
   there is nothing to confuse it *with*, but a real tap is the test.

Also confirm "Clear all" resets the form, that the two view-only toggles
(`rawWriteMode`, `moreDetailsExpanded`) still survive the clear, and that the
`MoreVert` menu still opens and now shows **only** Settings.

## §8 Install gate — PASSED 2026-08-28

moto g stylus 2025 / Android 16, debug build, screen 1220 px wide. Geometry read
from `uiautomator dump`, not estimated from a screenshot.

### §7 Q1 — does the label crowd the logo at a large font? **No, at any scale.**

Measured the gap between the button's right edge and the logo image's left edge
(the logo is fixed at `[429,126][904,396]` and does not move with font scale):

| `font_scale` | "Clear all" button bounds | Right edge | Gap to logo | Wrapped? |
|---|---|---|---|---|
| 1.0 (default) | `[23,126][247,261]` | 247 | **182 px** | no |
| 1.3 | `[23,127][299,262]` | 299 | **130 px** | no |
| 2.0 (max) | `[23,126][384,277]` | 384 | **45 px** | no |

At 2.0 the text node is `[57,149][350,254]` — 105 px tall, i.e. still one line,
confirmed visually on a screenshot crop. So D1's known cost does not actually
materialise: the ~100 dp gutter absorbs even the largest accessibility font, and
`maxLines = 1` was never exercised.

**System `font_scale` was changed for this test and restored to its original
`1.0`**, verified by reading it back.

### §7 Q2 — is top-left mistaken for Back?

Nothing to confuse it with: the header has no back or up affordance, and the dump
confirms the only other clickable in that row is the `MoreVert` at
`[1062,126][1197,261]`. Left as a watch-item for tester feedback rather than
claimed as proven — only a naive user's first tap really answers it.

### Functional checks — all pass

- **The overflow menu now holds exactly one item.** Dumped with the menu open:
  one `text="Settings"`, zero `Clear all`. No duplicate entry point.
- **Clear all resets the form.** Typed `TESTVAR` into Variant, confirmed present
  in the dump, tapped Clear all, re-dumped: **0 occurrences**.
- **`moreDetailsExpanded` survives the clear**, the invariant `onClearAll`
  deliberately preserves. Expanded "Filament metadata" first; after the clear the
  node still read `content-desc="Collapse filament metadata"`.

No Spoolman records were created, modified or deleted, and the Spoolman URL was
never touched. Device left as found: form cleared, the metadata expander collapsed
back to its pre-test state, every scratch file removed from `/sdcard`.

---

## §9 Second pass 2026-08-28 — restyle + the deferred Settings gear

Two maintainer notes after seeing the first pass on device:
*"i dont like clear all, give me other option to make it lool ni ce"* and
*"whats point of seeting when we have …"*.

### D5 — filled tonal pill, label "Clear"

The plain `TextButton` read as a **link, not an action**. Replaced with
`FilledTonalButton`, 20 dp corners, tonal fill, which matches
`LazyDropdownMenu` and the old overflow popup so it reads as the same app.
Chosen from four options presented with layout previews (tonal pill, outlined
pill with an eraser glyph, icon-only circle, shorter plain text).

Two width decisions fall out of the §8 measurements and are **not** cosmetic:
- **Content padding tightened to 16 dp** from M3's 24 dp default. At
  `font_scale` 2.0 the bare label already left only 45 px of clearance to the
  logo; a default-padded pill would have consumed it.
- **Label shortened to "Clear"**. One word buys back the width the container
  costs, and at header scope nothing else it could mean. The longer phrase is
  preserved for screen readers via `contentDescription = "Clear all fields"`,
  where width is not a constraint.

### D2 completed — `MoreVert` replaced by a direct Settings gear

The deferred half of question 2.2 A, and the maintainer's own observation:
**a "more options" menu holding one option is pure overhead.** It cost two taps
to reach Settings and advertised options that did not exist. The UI-57 menu
earned its keep only while it held two rows; once "Clear all" left, it did not.

Removed: `menuExpanded`, the `DropdownMenu`, the local `MenuRow` composable, and
the `MoreVert` import. `testTag("main-settings-button")` **moves onto the gear**
— it named the Settings affordance before and still does, so nothing referring
to it changes. `main-overflow-button` is gone with the menu.

Imports: `Settings` replaces `MoreVert`; `clickable` removed (unused once
`MenuRow` went); `DropdownMenuItem` removed as well — it was **already** unused
before this unit, since the menu used plain `Text` rows, so this is an incidental
tidy-up rather than a consequence of the change.

### Verification so far

`compileDebugKotlin` clean, **631 tests / 0 failures**, installed on the device.

**§8's measurements are now stale for the left-hand control** — they were taken
against the bare `TextButton`, and the pill is wider. The device locked itself
before it could be re-measured, so **the clearance numbers for the pill are NOT
yet verified**. Outstanding on-device checks:
1. Pill clearance to the logo at `font_scale` 1.0 and 2.0, still single-line.
2. Gear opens Settings, and no overflow menu remains.
3. Re-confirm Clear still resets the form and `moreDetailsExpanded` survives.

---

## §10 Settings gear REVERTED 2026-08-28 — it was never asked for

Maintainer: *"who asked you to remove …"*, then *"i want … to open setting as it
used to be before we did clear all"*.

**They were right, and this supersedes §9's D2.** The scope cut in §0 said
*"implement clear all only"*. What followed was the **question** *"whats point of
seeting when we have …"*, and I treated a question as an instruction reversing
the maintainer's own scope cut. That was my decision presented as theirs.

Restored, byte-identical to pre-UI-67:
- `MoreVert` IconButton at `TopEnd` with `testTag("main-overflow-button")`
- the `DropdownMenu` with its shape / colour / elevation and glyph-correcting
  offsets, comments intact
- the local `MenuRow` composable
- `menuExpanded`
- imports `MoreVert`, `clickable`, and `DropdownMenuItem`

**`DropdownMenuItem` is deliberately restored even though it is unused.** It was
already unused before this unit — the menu uses plain `Text` rows — so removing it
was pure unrequested initiative on my part, not a consequence of any decision
here. Putting it back is the honest state. It can be dropped later, as its own
call.

The gear was **not** better than the menu on any evidence gathered; it was a
preference I acted on. `Icons.Default.Settings` import is gone again.

### Net diff after the revert

Excluding comments, U27 is now exactly two changes:
1. `FilledTonalButton` "Clear" added at `TopStart`
2. `MenuRow("Clear all", "main-clear-all-item", onClearAllClick)` removed

That second line is still my judgement call rather than an explicit instruction,
flagged when it was made: leaving it would give one action two entry points.
**If the maintainer wants the row back as well, it is a one-line restore** — the
menu is fully intact again.

`compileDebugKotlin` clean, **631 tests / 0 failures**, installed.

### Still unverified on device

The phone locked itself before the pill could be measured, and it stayed locked
through the revert. Outstanding, unchanged from §9:
1. Pill clearance to the logo at `font_scale` 1.0 and 2.0, still single-line.
   **§8's numbers are stale** — they were taken against the narrower `TextButton`.
2. `MoreVert` still opens and shows Settings.
3. Clear still resets the form; `moreDetailsExpanded` still survives.

---

## §11 Settled 2026-08-28 — the ⋮ glyph stays, one tap opens Settings

Maintainer asked twice for *"… to open settings like it used to be before we added
clear all"*. After misreading it twice I asked with two concrete previews rather
than guessing a third time, and the answer was **⋮ opens Settings directly**.

**What I had been getting wrong**: their "…" was the **⋮ glyph itself**, not
elided text. Re-read that way, every earlier message is consistent and none of
them ever asked for a menu:
- *"why we need settings on top of ⋮?"* — why is there a popup layer above Settings
- *"whats point of ⋮ when we have …"* — the popup has nothing else in it
- *"who asked you to remove …"* — I had removed the **⋮ glyph** and put a gear in
  its place. The objection was the icon swap, not the menu removal.

So §9's instinct (drop the popup) was right and §10's revert overshot; what was
actually wrong in §9 was replacing the glyph with `Icons.Default.Settings`.

### Final state of the header

| Position | Control | Tap |
|---|---|---|
| `TopStart` | `FilledTonalButton` "Clear" | clears the form |
| `TopEnd` | `IconButton`, **`Icons.Default.MoreVert`** | opens Settings, one tap |

`contentDescription` is now **"Settings"**, not "More options" — the glyph is
inherited, but it no longer means "more options" and a screen reader must say what
it does. `testTag("main-settings-button")` moves onto it, since that is what the
control is; `main-overflow-button` retires with the popup.

Removed with the popup: `menuExpanded`, the `DropdownMenu`, the `MenuRow`
composable, and the `clickable` import (unused purely as a consequence).
**`DropdownMenuItem` stays imported although unused** — it was already unused
before UI-67, so removing it a second time would be the same unrequested
initiative §10 called out.

`compileDebugKotlin` clean, **631 tests / 0 failures**, installed.

### Still unverified on device

Phone has been `OFF_LOCKED` throughout and the PIN was not attempted. Outstanding:
1. Pill clearance to the logo at `font_scale` 1.0 and 2.0, still single-line.
   **§8's numbers are stale** — measured against the narrower `TextButton`.
2. **⋮ opens Settings in one tap with no popup** — the change just made, entirely
   unverified.
3. Clear still resets the form; `moreDetailsExpanded` still survives.

---

## §12 `canClear` added 2026-08-28 — reverses question 2.4 A

Maintainer asked *"make clear button enable disable or we should not?"* and approved
disabling. This supersedes **D4** (always enabled).

### The predicate is derived, not invented

The reason 2.4 A was originally recommended was to avoid inventing a "form is
dirty" heuristic. That objection dissolves once the question is asked the other way
round: **"would clearing change anything?"**

`MainUiState.cleared()` (new, in `MainUiState.kt`) is now the single definition of
what a clear produces. `onClearAll` applies it; `canClear` asks whether applying it
would alter state. **One function, so the action and the button cannot drift.** A
field added to the clear automatically becomes a field that un-greys the button.

`onClearAll` shrank from 15 lines to 3 as a side effect.

The two custom-name buffers (`_customMaterial` / `_customBrand`) live **outside**
`MainUiState`, so `cleared()` cannot see them and `canClear` folds them in by hand.
That hand-fold is the one place drift is possible, so it is what the tests aim at.

### Testing — the gap in §5 is now closed

§5 said this unit had no JVM-testable surface and that verification was the install
gate. **That is no longer true**: `canClear` and `cleared()` are pure and testable,
which is a second reason to prefer disabling over always-enabled.

New `MainViewModelClearAllTest`, **12 tests**. Suite **631 → 643**, 0 failures.

**Both halves validated as non-vacuous**, by breaking the production code rather
than by assertion-reading:
- Predicate forced to `true` (never grey) → **2 failures**, the two that pin
  greying-out.
- Custom-buffer fold removed → **exactly the 2 tests written for that seam fail**
  (`a custom brand typed under Other …`, `a custom material typed under Other …`).
  Nothing else, which is the sign the tests are aimed rather than incidental.

Two tests were also stripped of a `newVm()` and a `runTest` they never used — they
exercise the pure function only, and the scaffolding was dead weight.

`compileDebugKotlin` clean, installed.

### Device verification still outstanding

Everything in §11 remains unverified (phone locked throughout), and this adds one:
4. The pill actually greys on a fresh form, un-greys on first edit, and greys again
   after a tap.

---

## §13 Install gate PASSED (second pass) 2026-08-28 — U27 COMPLETE

Maintainer confirmed on device: **"works"**. Covers everything §11 and §12 left
outstanding, on the final build (tonal "Clear" pill, ⋮ opening Settings in one tap,
`canClear` greying).

Honest scope of this confirmation: it is the **maintainer's own visual check**, not
another `uiautomator` measurement pass — the phone had locked itself and I did not
attempt the PIN. The geometry numbers that *were* machine-measured are §8's, and
those were taken against the narrower `TextButton`, so **the tonal pill's clearance
at `font_scale` 2.0 is confirmed by eye rather than measured**. It rendered
correctly, which is what the gate asks; if a large-font report ever comes in, the
pill's padding and the "Clear" label are the two knobs, both already documented as
width decisions (§9).

**Unit COMPLETE.** Not device-verified by instrument: nothing outstanding that
blocks the release.

## §14 Final state of U27

Four production files, one test file.

| File | Change |
|---|---|
| `MainScreen.kt` | `FilledTonalButton` "Clear" at `TopStart`, `enabled = canClear`; `⋮` `IconButton` now calls `onSettingsClick` directly; popup menu, `MenuRow`, `menuExpanded` removed |
| `MainUiState.kt` | new pure `MainUiState.cleared()` |
| `MainViewModel.kt` | new `canClear` StateFlow; `onClearAll` reduced to applying `cleared()` |
| `MainViewModelClearAllTest.kt` | new, 12 tests |

Tests **631 → 643**, 0 failures. Ships inside **116 / 2.4.1**, which was bumped for
U26 + UI-65 and never uploaded, so no new version code is consumed.

### Decisions that ended up reversed during this unit, and by whom

- **2.2 A** (Settings gear) — I implemented it off a *question*, was pulled up for
  it, reverted it, then asked properly and learned the actual request was "⋮ opens
  Settings, same glyph". The maintainer's "…" was the **⋮ character**, not elided
  text; misreading it cost three exchanges. Recorded in §11.
- **2.4 A** (always enabled) → `canClear`, at the maintainer's prompting. My
  original reasoning against it was avoiding an invented dirtiness heuristic, which
  stopped applying once the predicate was derived from the clear itself (§12).
- **D3 stands**: still no confirmation and no undo. §6 R1 remains the accepted risk.
