# UI Followups — running list

Captured during install-gate testing and ad-hoc UX feedback. Each item is a
self-contained polish task; route to U9 (Settings + Theming + Banner) or U10
(Release polish) when scheduling.

## Convention

- **ID**: `UI-NN` (sequential).
- **State**: open / fixed (link to commit) / declined.
- **Routing**: target unit if scheduled.
- **Found in**: when first reported.

---

## UI-01 — Spoolman dropdown styling inconsistent with form fields

**State**: open
**Found in**: U6b install-gate, 2026-05-27
**Routing**: U9 (Settings + Theming + Banner) — same surface area as banner/theme polish.

The Spoolman dropdown at the top of the main screen (`MainScreen.SpoolmanDropdown`,
`app/src/main/java/com/spoolpainter/app/ui/screens/main/MainScreen.kt:208-273`)
visually doesn't match the rest of the form (FilamentForm — material picker,
brand picker, color picker, temp panel). It looks like a different design system.

Likely culprit: it's the only field above the form Card, uses a raw
`OutlinedTextField` + `ExposedDropdownMenuBox` instead of one of the styled
pickers; spacing / border / label-typography drift.

**Fix scope**: align padding, label style, container shape, and surface
elevation with `MaterialPicker` / `BrandPicker`. Consider hosting it inside
the same Card surface as the form so the screen reads as one unit.

---

## UI-02 — No prompt on passive (ambient) tag tap

**State**: open
**Found in**: U6b install-gate, 2026-05-27
**Routing**: U9 or U10 (small UX nudge).

When a tag is held near the device while the app is open in idle state, the
app captures the UID via `NfcRepository.lastSeenTag` and surfaces it into
`form.cardUid` silently. There's no visual cue that the tag was seen, and a
new user wouldn't know to press **Read tag** to load the spool data.

**Fix scope**: small inline hint or transient snackbar on first ambient tap
in an idle session — "Tag detected. Press **Read tag** to load." Should not
fire repeatedly (debounce per UID, or once-per-session).

**Implementation hints**:
- Hook into the existing `nfc.lastSeenTag.uid` collector in `MainViewModel.init`
  (`MainViewModel.kt:93-105`) — fire `_effects.trySend(UiEffect.ShowSnackbar(...))`
  when activeFlow == Idle and the UID is new vs. the previous tap.
- Or render a small `Text` hint above/below the FAB only when
  `form.cardUid != null && activeFlow == Idle && state.spoolman.selectedSpoolId == null`.

---

## UI-04 — "Pair another" button looks disabled (gray)

**State**: fixed (this session)
**Found in**: U6b install-gate, 2026-05-27
**Routing**: U6b polish or U10.

The "Pair another" button on `PairAnotherTagSheet` uses `FilledTonalButton`,
which renders with the secondary container colour — a muted/grayish tone in
the current theme. User read this as "disabled" and retried the action,
thinking the first tap hadn't registered.

Same applies to "Move it" on `RepairConfirmSheet` (also `FilledTonalButton`).

**Fix scope**: change the primary action to `Button` (filled — uses the
primary container colour, clearly affordant) and keep the secondary action
as `TextButton`. Files:
- `app/src/main/java/com/spoolpainter/app/ui/components/sheets/PairAnotherTagSheet.kt`
- `app/src/main/java/com/spoolpainter/app/ui/components/sheets/RepairConfirmSheet.kt`

---

## UI-06 — Form cleared after both tags paired

**State**: fixed (this session)
**Found in**: U6b install-gate, 2026-05-27
**Routing**: U6b polish (this is current `MainViewModel.applyTwoTagResult.Success` behaviour).

After the second tag is successfully paired, the form is wiped (material,
brand, colour, variant, temps, spool selection — all cleared). User feedback:
"i dont like that". Reasoning: they just paired 2 tags to one spool of
*PLA Red Matte 1.75mm*; the natural next action is to grab another empty
spool of the same filament (or a fresh blank-tag pair for a different colour
of the same material) and continue. Erasing the form forces re-entry.

**Fix scope**: in `MainViewModel.applyTwoTagResult.Success.SecondTagPaired`,
keep the form populated like `applyWriteResult.WrittenAndPaired` does on the
first pair. Clear `selectedSpoolId` (since the user is moving on to a new
spool) but preserve the filament data. Only clear if user explicitly taps a
"clear form" affordance (n/a today).

Tests to update:
- `MainViewModelTwoTagTest.applyTwoTagResult Success clears form ...` —
  flip assertion to "form preserved".
- Probably also `onPairAnotherTagDismissed clears form ...` — re-evaluate
  whether **Done** should also keep the form. Likely yes for symmetry.

Note: the old per-Q-U6b decision was "clear on success" — this is reversing
that under user-facing UX feedback.

---

## UI-07 — Better messaging across confirmation/cancel/error snackbars

**State**: open
**Found in**: U6b install-gate, 2026-05-27
**Routing**: U10 (copy review; ties in with UI-05).

Several snackbar/banner strings are mechanical or developer-facing. Examples:

| Code path | Current copy | Issue |
|---|---|---|
| `applyTwoTagResult.Cancelled` | `"Second-tag pairing cancelled (timeout)"` | Reads like an error report; "(timeout)" is jargon |
| `applyTwoTagResult.SpoolmanFailed` (AmbiguousOwnership) | `"Spoolman response could not be parsed"` | Misleading — see UI-08 |
| `applyTwoTagResult.MoveOnBindPartial` | `"Partial state in Spoolman — UID was removed from spool #X; restore manually if needed"` | Long; too technical |
| `applyTwoTagResult.VerifyFailed` | `"Second-tag verify failed: ${cause}"` | Includes raw cause |
| `applyTwoTagResult.NfcFailed` | `"Tag write failed: ${reason}"` | See UI-05 |
| `applyWriteResult.Cancelled` | `"No tag tapped — try again"` | OK, kept for reference |
| `applyTwoTagResult.VendorTagRejected` | `"Vendor tag — write blocked"` | OK |

**Fix scope**: full copy review at U10. Suggested rewrites:

- Timeout → "No second tag tapped. Try **Pair another** again to retry."
- AmbiguousOwnership → see UI-08.
- MoveOnBindPartial → "Couldn't finish moving the tag. Spool #X already
  released the tag; please re-add it in Spoolman if needed."
- VerifyFailed → "Tag didn't verify. Tap **Pair another** to retry."
- NfcFailed → see UI-05.

Pair this with a short snackbar action: where retry is possible, attach an
action button rather than asking the user to navigate back.

---

## UI-08 — "Spoolman response could not be parsed" surfaces on AmbiguousOwnership

**State**: fixed (this session) — actually went further than the original
fix: instead of just rewording the error, the AmbiguousOwnership outcome was
removed entirely. `MoveOnBindUseCase` now sweeps all conflicting source
spools in one confirmation. `RepairConfirmSheet` lists the owners ("Currently
on: • spool A • spool B") and the **Move it** button removes the UID from
each source, then appends to the target. Read flow's `AmbiguityBlock` red
card is unchanged — picking a spool from the dropdown is still the right
resolution there since there's no target spool yet.
**Found in**: U6b install-gate, 2026-05-27 — when a UID is on 2 spools, mapping it to any spool surfaces the wrong message.
**Routing**: small, can land in U6b polish or U10.

When `MoveOnBindUseCase` returns `AmbiguousOwnership(currentOwners)`, both
`CreateAndPairUseCase` and `TwoTagUseCase` wrap it as
`SpoolmanFailed(SpoolmanOutcome.ParseError(IllegalStateException("ambiguous ownership: spool ids X, Y")))`.
That goes through `MainViewModel.humanReadable(ParseError)` which is hardcoded
to return **"Spoolman response could not be parsed"** — completely misleading.

**Fix scope** (small): in `MainViewModel.humanReadable`, special-case
`ParseError` whose cause is `IllegalStateException` and surface
`cause.message` directly. Or: stop wrapping `AmbiguousOwnership` as a
ParseError — introduce a `CreateAndPairResult.AmbiguousOwnership` /
`TwoTagResult.AmbiguousOwnership` variant carried through to the VM and
surface a dedicated copy:

> "This tag is already paired with multiple spools (#X, #Y). Fix in Spoolman first."

Files:
- `app/src/main/java/com/spoolpainter/app/domain/usecases/CreateAndPairUseCase.kt:285-297`
- `app/src/main/java/com/spoolpainter/app/domain/usecases/TwoTagUseCase.kt` (Ambiguous branch)
- `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainViewModel.kt:`humanReadable`

Note the existing `AmbiguityState` red Card on the Read flow already has
proper UX for the same data shape — we should mirror its wording.

---

## UI-11 — Spool dropdown stays enabled during write (other fields disable)

**State**: fixed (this session)
**Found in**: U6b polish run, 2026-05-27.

`SpoolmanDropdown` was enabled purely on `urlConfigured`, ignoring
`activeFlow`. Other form fields disable when `activeFlow != Idle` (correctly
preventing edits mid-write). Dropdown now uses
`urlConfigured && activeFlow == Idle`.

---

## UI-12 — Cancel on RepairConfirmSheet emits misleading "No tag tapped — try again"

**State**: fixed (this session)
**Found in**: U6b polish run, 2026-05-27.

Tapping **Cancel** on the move-on-bind sheet returned
`CreateAndPairResult.Cancelled("repair declined …")` (and the parallel
`TwoTagResult.Cancelled` on the second-tag flow). Both branches in
`MainViewModel` emitted "No tag tapped — try again" / "Second-tag pairing
cancelled (...)" — misleading because the user explicitly chose Cancel.
Now: snackbar suppressed when reason starts with "repair declined".
Genuine timeouts / unknown reasons still surface their snackbar.

---

## UI-10 — Spool dropdown selection cleared on Save (1 tag or both)

**State**: fixed (this session)
**Found in**: U6b polish run, 2026-05-27.
**Routing**: same polish patch.

After Save & Write completes (either via "Done" with one tag or "Both tags
paired" via the second-tag flow), the Spoolman dropdown loses its selection.
User wants the dropdown to keep showing the just-paired spool so they have
a visible reference of what they just landed on.

**Root cause**: `MainViewModel.applyTwoTagResult.Success.SecondTagPaired`
and `onPairAnotherTagDismissed` both set
`form.selectedSpoolId = null` and `spoolman.selectedSpoolId = null`. UI-06
made the form preserve fields, but selection was still cleared. User now
wants the spool selection preserved too.

**Fix scope**: drop the `selectedSpoolId = null` in those two paths. After
the write, the dropdown stays on the just-paired spool. User can manually
clear it via the "Clear selection" entry in the dropdown if they want a
fresh entry.

---

## UI-09 — Non-NDEF vendor tags surface "tag does not support NDEF" instead of "Vendor tag — write blocked"

**State**: fixed (this session)
**Found in**: U6b install-gate, 2026-05-27 — vendor tag tapped during write.
**Routing**: U6b polish (this is a real misclassification bug, not just wording).

User tapped a vendor tag (e.g. factory Bambu) expecting the documented
"Vendor tag — write blocked" snackbar (FR-4.7). They saw
`"Tag write failed: tag does not support NDEF"` instead.

**Root cause**: when `Ndef.get(tag)` returns null at read time
(`NfcAdapterWrapper.readRecordsBlocking:90`), `RawTagRead.records` becomes
null. `NfcRepository.classify:241` then falls through to
`TagClassification.Blank`:
```kotlin
val records = raw.records ?: return TagClassification.Blank
```
A truly blank-but-formattable tag and a non-NDEF vendor tag are
indistinguishable at this layer. The write path then attempts
`Ndef.get(tag)` again (`NfcAdapterWrapper.writeRecords:53-54`) and throws
`IllegalStateException("tag does not support NDEF")`, which surfaces via
`runWriteThenVerify`'s catch-all → `Error("write failed: tag does not
support NDEF")` → snackbar.

**Fix options**:
1. **Write-time classification (preferred)**: in
   `NfcAdapterWrapper.writeRecords`, when `Ndef.get(tag)` returns null,
   try `android.nfc.tech.NdefFormatable.get(tag)`. If THAT returns null,
   it's truly non-NDEF → throw a sentinel like
   `IllegalStateException("vendor-tag protected: non-NDEF tag")`.
   `NfcRepository.runWriteThenVerify` then maps that prefix to the standard
   vendor-tag error string. As a side benefit, formattable tags can be
   promoted via `NdefFormatable.format(message)` if we ever want to write
   to truly-fresh blanks.
2. **Read-time downgrade**: have `readRecordsBlocking` distinguish
   "Ndef.get returned null" from "ndefMessage was null" by returning a
   tagged result (e.g. `null` vs an empty list). Then `classify` can return
   Vendor for the former, Blank for the latter. Less work; doesn't open
   the door to NdefFormatable writes.

**Test scope**: extend `NfcRepositoryClassifierTest` and
`NfcRepositoryWriteVerifyTest` with a case where `simulateRead` returns
`null` records AND the wrapper is told the tag is non-NDEF. Currently the
tests assert `null` records → `Blank`, which is consistent with current
behaviour but bakes the bug in.

---

## UI-05 — NDEF write-failure snackbar is too technical

**State**: open
**Found in**: U6b install-gate, 2026-05-27
**Routing**: U10 (release polish).

When `Ndef.writeNdefMessage` throws (typically: tag lifted before write
completes), the user sees a snackbar like:

> Tag write failed: Ndef.writeNdefMessage IOException (payload=221B cap=492B writable=true): no message

The diagnostics (payload / cap / writable / message) are useful in logs but
not for users — they just want "Hold the tag steady, try again."

**Fix scope**: rewrite the user-facing wording in
`MainViewModel.applyTwoTagResult.NfcFailed` and the equivalent
`applyWriteResult.NfcFailed` paths. Detect the
`Ndef.writeNdefMessage IOException` substring in the reason and surface a
friendly variant; keep the verbose form in logs only.

Suggested copy: "Tag connection lost. Hold the tag steady against the back
of the phone and try again."

---

## UI-03 — "Paired and written" snackbar covered by PairAnotherTagSheet

**State**: fixed (this session)
**Found in**: U6b install-gate, 2026-05-27
**Routing**: U6b polish (or U10 if we don't want to reopen U6b).

After the first-tag write succeeds, the snackbar "Paired and written" emits
*and* the bottom sheet "Pair another tag with this spool?" opens. The sheet
slides up from the bottom of the screen and visually covers the snackbar,
so the user never reads the success confirmation.

**Possible fixes**:
1. **Move the snackbar into the sheet** — render the confirmation as a small
   Text inside the sheet's Column, drop the snackbar emission. Cleanest UX.
2. **Sequence the emissions** — delay the sheet by ~1.5 s after the snackbar
   so the user reads "Paired and written" first. Worse — feels laggy.
3. **Drop the snackbar entirely** — the sheet's title implies success.

Option 1 recommended: change `MainViewModel.applyWriteResult.WrittenAndPaired`
to omit `_effects.trySend(UiEffect.ShowSnackbar("Paired and written"))`, and
add a confirmation Text inside `PairAnotherTagSheet` body (e.g. above the
existing copy: "Saved. Pair another tag with this spool?").

The existing test `MainViewModelTest > onWriteTapped existingSpool emits
SnackbarAndKeepsFormOnSuccess` would need to drop the snackbar assertion
under option 1.

---
