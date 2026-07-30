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

**State**: fixed (U10 install-gate session 2026-05-30 — 15s wall-clock cooldown via `kotlinx.datetime.Clock`; classification-aware copy: Vendor → "Vendor tag. Press Read to load.", Blank → "Blank tag detected.", OpenSpool/null → "Tag detected. Press Read to load.". `selectedSpoolId == null` gate dropped per user direction)
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

**State**: fixed (U10 §3 — MoveOnBindPartial + Cancelled rewrites + earlier U9b §7 audit covers the rest; AmbiguousOwnership friendly copy via `humanReadable.ParseError` already shipped in U6b)
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

**State**: fixed (U9b §7 — `"Couldn't write to tag. Try again."` shipped on all NfcFailed/VerifyFailed paths in `MainViewModel`)
**Found in**: U6b install-gate, 2026-05-27
**Routing**: U10 (release polish) — verification only, copy already shipped.

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

## UI-13 — Update existing filament metadata when user edits prefilled fields

**State**: partial — variant editable; remaining_weight + per-spool
price added in v2.0.2 (2026-05-31). Density / filament weight / spool
weight / diameter walked back to read-only on existing-spool path; color
hex + temp ranges still parked for v2.1.
**Found in**: U8 install-time UX feedback, 2026-05-28.
**Routing**: v2.0.1 shipped variant + 5 spec fields editable on existing
spool. v2.0.2 (2026-05-31) reframed: identity + spec locked to prevent
tag↔Spoolman desync; remaining_weight + per-spool price added as the
genuine spool-scope edit surface. Color + temps stay parked.

**Shipped 2026-05-31 (v2.0.2)**:
- `SpoolPatchBody.remaining_weight` + `SpoolPatchBody.price` added.
- `SpoolmanSpool.price` + `CreateSpoolRequest.price` added (Spoolman
  supports per-spool price; `COALESCE(spool.price, filament.price)`
  resolution per `spool.py:191`).
- `SpoolmanRepository.applyVariantToFilamentOfSpool` (narrow sibling)
  + `SpoolmanRepository.patchSpoolFields` (single-call seam for
  remaining + price).
- New form fields: `remainingWeightG` + `prefilledRemainingWeightG` +
  `prefilledPriceMajor`. Stale-prefill guard via snapshot equality.
- `MoreDetailsExpander`: new Remaining + Measured row (visible only
  when `selectedSpoolId != null`); bidirectional `measured = remaining
  + spool_weight`; "Saved to Spoolman" caption under Weight section.
- `DecimalField` rewritten with local string state (intermediate
  `"1."` accepted) + per-field length caps + single-`.` filter.
- Identity fields (Material / Brand / Color) disabled when existing
  spool OR existing filament is selected. Filament-spec (density /
  filament weight / spool weight) disabled on the same trigger.
- Diameter removed from UI everywhere; defaults to 1.75mm at create
  time. Existing non-1.75mm filaments preserved as-is.
- Tests **364 → 376** (Δ +12). Release APK **6.95 MB** /
  AAB **7.66 MB**. versionCode **101 → 102**, versionName
  **2.0.1 → 2.0.2**.

**Shipped 2026-05-31 (v2.0.1)**: existing-spool Save & Write now invokes
`SpoolmanRepository.applyOverridesToFilamentOfSpool(spoolId, overrides)`
before the UID append. The new field `ExpanderOverrides.variant` carries
the form's Variant value (blank-stripped), which the repo merges into
`PatchFilamentBody.extra` as `{"variant": GSON.toJson(value)}` (preserves
other extra fields the user/another tool may have set). The 5 expander
fields (density, diameter, weight, spool_weight, price) flow through the
same patch. `sparseDiff` collapses no-op patches (form auto-loaded,
nothing edited) to zero HTTP. Failures are logged as warnings but do not
abort the primary write/UID-append flow — a Spoolman hiccup shouldn't
break the user's primary pairing action.

Edge case verified: legacy v1 tags with non-`"Basic"` `subtype` get
*promoted* into Spoolman's `extra.variant` on the first v2 Save & Write
(form fills from the tag fallback in `MainViewModel.applyResult`, then
the patch fires because Spoolman's stored extra differs from the merged
body).

3 new tests in `CreateAndPairUseCaseTest`: existing-spool with variant
typed, existing-spool with no variant typed (no-op), new-spool path
(doesn't trigger this seam). Test count **361 → 364**.

Today, when the user picks a spool from the dropdown:
- Form prefills from Spoolman, including the 5 spool-metadata fields
  (density / diameter / weight / spool weight / price) pulled from the
  parent filament record.
- If the user edits any of those fields and hits Save & Write, the edits
  are silently ignored — the existing-spool path only appends UID, never
  PATCHes the filament.

User direction (2026-05-28): *"if user have entered data in spoolman we
fill it, and when they change anything and click save again, we update
it (let add update feature for next stage)"*.

**Scope clarification (2026-05-28)**: spool↔filament linking stays as-is
(no change to which filament a spool belongs to via this flow). Updates
target only the metadata fields stored on the parent filament:
- Spool-metadata expander: density, diameter, weight, spool weight, price
- Temperature ranges: extruder min/max, bed min/max
- Color hex
- Variant (extra.variant)

Material name + brand/vendor are NOT touched in this stage — those
reshape filament identity and need their own UX (likely "create new
filament instead?" branching).

**Fix scope** (next stage):
1. Snapshot the prefilled spool-metadata values when the spool is picked
   (e.g. `MainUiState.SpoolmanState.prefilledMetadata: ExpanderOverrides?`).
2. On Save & Write with `selectedSpoolId != null`, diff the form's current
   metadata against that snapshot.
3. If anything changed → confirmation dialog: *"Update [filament name] in
   Spoolman? This affects all spools sharing this filament."* Yes →
   `SpoolmanRepository.patchFilament(filamentId, sparseDiff)` before the
   tag write. No → proceed with append-only (current behaviour).
4. Mention "shared across all spools of this filament" because Spoolman
   stores these on the filament, not the spool — users may not realise.

**Files**:
- `MainUiState.kt` — add prefilled-metadata snapshot
- `MainViewModel.onSpoolSelected` — capture snapshot
- `MainViewModel.onWriteTapped` — diff + dispatch
- New confirmation sheet (mirror `RepairConfirmSheet` shape)
- `CreateAndPairUseCase` — accept optional pre-write filament PATCH payload

Note: `SpoolmanRepository.patchFilament` is already implemented (U8 §2.14)
and idempotency-checked, so the repository layer is ready.

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

## UI-14 — Edit a paired spool — design pass needed (post-v2.0 release)

**State**: open (deferred — post-v2.0 release, NOT v2.0 scope)
**Found in**: U9b scope-adjust round, 2026-05-29
**Routing**: post-v2.0 — explicitly NOT U9b, NOT U10. Park behind the v2.0 Play Store testing-track release per user direction "add editing for something later after release" 2026-05-29.

When a user picks an existing spool from the dropdown, today the form
prefills from Spoolman but any edits to fields are silently ignored on
save (the existing-spool path only appends the UID to `extra.card_uids`
and re-writes the tag).

The desired feature is "edit a paired spool" — let the user change values
and have those changes propagate to Spoolman. The scope split:

**Filament-scope edits** (live on the `filament` record in Spoolman, shared
across every spool of that filament):
- `density / diameter / weight / spool_weight / price`
- temp ranges (`extruder min/max`, `bed min/max`)
- `color_hex`
- `extra.variant`

**Spool-scope edits** (live on the `spool` record):
- `remaining_weight`
- `archived` (the standalone "Archive this spool" affordance)

**Identity-affecting fields** (re-shape what filament the spool belongs to):
- material name (PLA / PETG / etc.)
- brand / vendor
- These need a "create new filament instead?" / re-pair flow, not an
  in-place edit.

**Why this needs a design pass, not a polish patch**:

During the U9b scope-adjust round we explored three save-flow models and
none felt right inside U9b's polish framing:
- *Always tap (Save & Write everywhere)* — forces an NFC tap to update
  `remaining_weight`, which doesn't even live on the tag.
- *Conditional label (Save vs Save & Write based on which fields changed)*
  — user pushed back: "with single button it gets confusing when i need
  to tap when its not required example vendor tag, no with this its more
  confusing".
- *Two buttons in the existing-spool path (Save + Write tag)* — close to
  workable but means the main form has two distinct actions whose
  visibility depends on `selectedSpoolId`, and we'd still need a Save
  button inside the expander for spool-scope fields, plus the
  filament-scope fields are pre-filled-but-not-editable… the design grew
  beyond a single-unit polish surface.

**Open questions for the design pass**:
1. Where does the edit live — main form (current shape) or a dedicated
   "Edit spool" screen / sheet?
2. How is the NDEF-vs-Spoolman field split surfaced to the user (or is it
   hidden)?
3. Sibling-spool propagation — confirm dialog every time? Or only when
   N ≥ threshold?
4. Material/brand identity edit — separate "re-pair to different
   filament" flow, or in-place "create new filament" dialog?
5. Does Archive deserve its own sheet or stay a button on the same
   surface?
6. Tag-out-of-sync handling when the user saves filament-scope edits
   without re-writing the tag.

**Holding deltas (do NOT apply until the design pass)**:
- `SpoolmanRepository.patchFilament(id, sparseDiff)` already shipped in
  U8 §2.14. Idempotent. Ready for reuse.
- `SpoolmanService.patchSpool(id, body)` is **not yet implemented** —
  needs adding when the design pass lands.
- `SpoolPatch(remainingWeightG: Float? = null, archived: Boolean? = null)`
  data class is the obvious shape but not committed.

**See also**: `aidlc-docs/audit.md` "U9b scope adjustment" entries for
2026-05-29 — full Q&A trail of the three models we considered + the user
quotes that ruled them out.

---

## UI-15 — Archive a spool from the app (post-v2.0 release)

**State**: open (deferred — post-v2.0 release, NOT v2.0 scope)
**Found in**: U9b scope-adjust round, 2026-05-29
**Routing**: post-v2.0 — bundled with UI-14 in a single "edit a paired
spool" design pass. User direction "add editing for something later after
release" 2026-05-29 parks all editing behind the v2.0 testing-track
release.

Today, archiving a spool means opening Spoolman's web UI on a separate
device. The app should expose a one-tap archive action on a paired spool.

**Mechanics** (already designed in the U9b scope-adjust round before being
deferred):
- Visible only when `selectedSpoolId != null`.
- Confirmation dialog (destructive-ish — recoverable from web UI but not
  from the app).
- PATCH `/spool/{id}` with `archived: true`.
- Success → remove from in-memory list, clear form, snackbar "Archived".
- Failure → snackbar, form state preserved.

**Why deferred from U9b**:

Initially proposed as a tiny standalone in U9b (one button, one PATCH).
After user feedback narrowed editing scope further ("remove archive too"),
archive went out with the rest of editing. Reasoning: any spool-write
PATCH path opens the same "what does Save mean here?" question as the
filament edits, and the `patchSpool` service method doesn't exist yet —
adding one method for a single button felt scope-creepy when the bigger
editing design isn't yet locked.

**Done together with UI-14** when the editing design pass lands.

---

## UI-16 — Filament section: always-open, no expander toggle

**State**: fixed (2026-05-30 install-gate)
**Found in**: U10 install-gate, 2026-05-30
**Routing**: U10 close-out

The U8-Δ-1 collapsed-by-default "Filament ▾" expander hid the picker behind
a header tap, but every flow (create-and-pair, pair-existing, edit) actually
needs the picker visible — collapsing it added a step. User direction:
"keep filament always open, don't want that hide flow."

**Fix shipped**:
- Renamed `FilamentSectionExpander.kt` → `FilamentSection.kt`; rewrote as
  a plain Column hosting the helper text + `FilamentPicker`. No header,
  no chevron, no `AnimatedVisibility`.
- Dropped `FormState.filamentSectionExpanded`, `FormChange.FilamentSectionToggled`,
  `MainViewModel.onFilamentSectionToggled`, the `MainScreen` route hook,
  the `MainViewModelFilamentPickerTest.onFilamentSectionToggled flips...`
  test.
- "Filament" heading: `bodyLarge` SemiBold + primary color (matches Material
  metadata's SectionLabels). Picker textStyle bumped to `titleMedium`
  SemiBold (vs `bodyLarge` SemiBold for the secondary fields) — subtle
  size step that signals "primary pick" without colored fill.

---

## UI-17 — End-of-pair-flow auto-clear + filament-pin reverted

**State**: fixed (2026-05-30 install-gate, reverted)
**Found in**: U10 install-gate, 2026-05-30
**Routing**: U10 close-out

A mid-session iteration added `applyEndOfPairFlow(spoolId)` that, on
PairAnotherTagDismissed and TwoTagResult.SecondTagPaired, cleared the
spool selection and pinned the just-paired filament so the next tap
created a NEW spool against the same filament. User direction:
"feature we added to clear spool when write complete, revert it."

**Fix shipped**: helper deleted; both call sites back to plain
`activeFlow = Idle` transitions. Form keeps spool + filament + everything
else after PairAnother dismissal / second-tag pair, same as v1 behaviour.

---

## UI-18 — Spool dropdown X clears spool only

**State**: fixed (2026-05-30 install-gate)
**Found in**: U10 install-gate, 2026-05-30
**Routing**: U10 close-out

`MainViewModel.onSpoolSelected(null)` previously reset the entire form to
defaults (material/brand/colour/temps/expanders/cardUid) and wiped
ambiguity + observed-tag state. User direction: "make x of spool dropdown
just clear spool not everything, leave filament x as it is."

**Fix shipped**: null-spool branch now only clears `form.selectedSpoolId`
+ `spoolman.selectedSpoolId`; form fields, cardUid, ambiguity, and
observed-tag info are preserved. `MainViewModelFilamentPickerTest.…null
clears form…` renamed and rewritten to assert that material/brand/colour/uid
all survive the clear.

---

## UI-19 — Write-fail snackbar tells user the spool is saved

**State**: fixed (2026-05-30 install-gate)
**Found in**: U10 install-gate, 2026-05-30
**Routing**: U10 close-out

`CreateAndPairResult.VerifyFailed` and `NfcFailed` (non-vendor) both
surfaced "Couldn't write to tag. Try again." That copy hides the fact
that `CreateAndPairUseCase` creates the spool BEFORE the write tap, so
on a write failure the spool is already in Spoolman; the user just needs
to re-tap.

**Fix shipped**:
- Both paths now emit "Saved to Spoolman. Tag write failed. Try again."
- VerifyFailed branch additionally keeps `selectedSpoolId` set to
  `result.spoolId` (was clearing only `activeFlow`) so the UI shows the
  spool that was saved, ready for retry without re-filling the form.
- Vendor sub-case on NfcFailed unchanged — still "Vendor tag. Write blocked."

---

## UI-20 — Write path: pre-read + verify removed (perf, "phone moved" failures)

**State**: fixed (2026-05-30 install-gate)
**Found in**: U10 install-gate, 2026-05-30
**Routing**: U10 close-out

v2's write tap was opening **3 separate Ndef.connect() cycles** on the
same Tag handle: (1) pre-read in `NfcRepository.handleTag` for ambient
classification, (2) actual `writeNdefMessage`, (3) post-write verify
read-back. Combined with Android's system tag-detected haptic firing
after step 1 — which users naturally interpret as "done" and pull away
from the tag — this produced a high rate of "phone moved" failures on
steps 2 or 3. v1.7 production used **one** connect cycle (write straight
through, no pre-read, no verify) and was robust.

**Fix shipped**:
- **Pre-read skipped on Writing-state taps**: `handleTag` now peeks
  `_state.value`; if `is NfcResult.Writing`, synthesises a `RawTagRead`
  from the in-memory `Tag` object only (uid via `CardUid.fromBytes(tag.id)`,
  techList via `tag.techList`, `records = null`). `classify(raw)` already
  handles `records == null` via the techList branch, so this is a clean
  drop-in. Read / Idle paths unchanged.
- **Verify block commented out** in `runWriteThenVerify`. Discussion of
  what it caught:
  - `writeNdefMessage` throwing IOException on phone-moved-during-write
    is already handled — surfaces as `WriteResult.Failed` →
    `CreateAndPairResult.NfcFailed` with the friendly snackbar.
  - Verify only adds value for counterfeit chips that ACK page writes
    silently without persisting. That class of failure is rare on
    genuine NTAG21x and surfaces at the Snapmaker round-trip (§9.2)
    anyway.
- Net: write tap is now `Ndef.connect → writeNdefMessage → close`, same
  as v1.7. The "keep phone steady" window is just the actual write
  duration (~50 ms for our payload).

**Files**:
- `app/src/main/java/com/spoolpainter/app/hardware/nfc/NfcRepository.kt`:
  `handleTag` synthesises `raw` on Writing taps; `runWriteThenVerify`
  has the verify block commented out (kept inline for easy revert if
  the Snapmaker round-trip surfaces a regression).
- `CardUid` import added.

---

## UI-21 — Vendor-tap during Writing state routes through vendor pair path

**State**: fixed (2026-05-30 install-gate)
**Found in**: U10 install-gate §4.2, 2026-05-30
**Routing**: U10 close-out

When the user picks a spool from the dropdown and taps a vendor tag *without
first hitting Read*, `MainViewModel.onWriteTapped` saw `observedTagKind = None`
and routed through the standard create-and-pair path. Vendor chips Android
auto-promotes to NDEF (Bambu/Creality MifareClassic) silently no-op or throw
IOException on write. Result: Spoolman correctly got the UID PATCH (use case's
Step 3) but the user saw the misleading "Saved to Spoolman. Tag write failed"
snackbar from UI-19.

**Fix shipped**:
- `NfcRepository.classify`: MifareClassic in techList → Vendor regardless
  of Ndef presence (was Ndef-wins).
- `NfcRepository.runWriteThenVerify`: pre-block on Vendor classification
  before any NDEF transceive. Emits `vendor-tag protected (FR-4.7): <reason>`.
- `CreateAndPairUseCase`: on `WriteResult.Failed` with vendor reason + UID,
  returns `Success.WrittenAndPaired(isVendorPair = true)` (new field on the
  result type). Spoolman pairing is already complete by then.
- `MainViewModel.applyWriteResult.WrittenAndPaired`: clears observedTagKind +
  observedTagUid (so VendorTagHint chip dismisses) and threads `isVendorPair`
  into `ActiveFlow.PromptingPairAnother`.

---

## UI-22 — PairAnotherTagSheet vendor-aware copy + missing snackbars

**State**: fixed (2026-05-30 install-gate)
**Found in**: U10 install-gate §4.2 follow-up, 2026-05-30
**Routing**: U10 close-out

The PairAnotherTagSheet's body said "We'll write the same data to the second
tag and remember both" — wrong for vendor pairs (we don't write to vendor
tags). Also the second-tag vendor success path was silent (no snackbar).

**Fix shipped**:
- `ActiveFlow.PromptingPairAnother` + `PairAnotherTagUiState` gain
  `isVendorPair` flag.
- `PairAnotherTagSheet`: vendor copy. Title "Tag linked. Pair another tag
  with this spool?" / body "Tap a tag to link it to the same spool."
  Non-vendor copy unchanged.
- `MainViewModel.applyVendorUidOnlyPairResult.Success` and the
  vendor-CreateAndPair synthetic success both set `isVendorPair = true`.
- `onPairAnotherTagDismissed`: snackbar branches — "Vendor tag linked."
  vs "Saved with one tag."
- `applyTwoTagResult.VendorTagRejected` re-route's success branch now
  emits "Both tags paired." (was silent).

---

## UI-23 — NFC status pill (read/write hints unified)

**State**: fixed (2026-05-30 install-gate)
**Found in**: U10 install-gate, 2026-05-30
**Routing**: U10 close-out

`WritingHint` rendered below the form/Save button (out of natural sight line);
`ReadingHint` rendered above as plain centered text that visually competed
with the app-name overlay above it.

**Fix shipped**:
- `WritingHint` moved up next to `ReadingHint` between BannerSlot and the
  Spoolman dropdown card.
- Both rewritten as a shared `NfcStatusPill` composable: centered Surface,
  `RoundedCornerShape(50)`, `primaryContainer` fill, 18dp `Icons.Filled.Nfc`
  + `labelLarge` text in `onPrimaryContainer`.

---

## UI-24 — Brand/material case canonicalised against existing entries

**State**: fixed (2026-05-30 install-gate)
**Found in**: U10 install-gate, 2026-05-30
**Routing**: U10 close-out

When the user typed a brand or material with different case than an existing
entry (e.g. `polymaker` vs existing `Polymaker`), Spoolman's
`resolveOrCreateVendor` / `resolveOrCreateFilament` helpers correctly dedup
the vendor/material row case-insensitively, so the manufacturer column on the
filament stays canonical. But the **filament's `name`** field is built in
`MainViewModel.onWriteTapped` as `"$brand $material $variant"` from the
*user-typed* string, so the case-only differing user input would leak into
the filament name (`"polymaker PLA"` instead of `"Polymaker PLA"`).

**Fix shipped**:
- `MainViewModel.resolveBrandName`: canonicalise raw user input against
  `brands.value` (presets ∪ Spoolman vendors) — first case-insensitive
  match wins; otherwise the raw user input is kept (genuinely new brand).
- `MainViewModel.resolveMaterialName`: symmetric fix against
  `materials.value`.
- Net: typing `polymaker` when `Polymaker` already exists yields a filament
  name `Polymaker PLA`, matching the dedup'd vendor row.

---

## UI-25 — Filament dropdown auto-selects after create

**State**: fixed (2026-05-30 install-gate)
**Found in**: U10 install-gate §5, 2026-05-30
**Routing**: U10 close-out

After a successful create-and-pair, `applyWriteResult.WrittenAndPaired` set
`form.selectedSpoolId` (so the Spool dropdown showed the new spool) but left
`form.selectedFilamentId` untouched. The Filament dropdown stayed empty even
though a brand-new filament was just created and linked to the spool.

**Fix shipped**: both `applyWriteResult.WrittenAndPaired` and
`applyVendorUidOnlyPairResult.UidPaired` now look up the spool's
`filament.id` from the in-memory `state.spoolman.spools` cache (refreshed by
`SpoolmanRepository.refreshAfterWrite()` before the use case returns) and
set `form.selectedFilamentId`. Falls back to the existing
`form.selectedFilamentId` if the new spool isn't in the cache yet (rare).

---

## UI-26 — Filament `extra.variant` not populated on create-and-pair (verify)

**State**: open (suspected — surfaced by Spoolman spool/76 inspection
2026-05-30; needs reproduction)
**Found in**: U10 install-gate §5/§9 cross-check, 2026-05-30
**Routing**: U10 close-out

Manual `curl /api/v1/spool/76` on the test Spoolman instance returned a
filament with `extra: {}` — no `variant` key. v2's create flow is supposed
to write `extra.variant` whenever the form's variant field is non-blank
(per FR-U6b-Δ-4). Spool 76 was created 2026-04-25, before some of this
session's edits, so it may simply pre-date the variant-on-create wiring.

**Reproduction needed**:
1. Pick a fresh form, fill material/brand and a Variant value (e.g.
   `"Matte"`).
2. Save & Write to a blank tag.
3. `curl /api/v1/filament/<new-filament-id>` and confirm
   `extra.variant == "\"Matte\""` (JSON-encoded string).

If reproducible, route to a §5.2/§5.13 fix; if not, this entry can be
closed as a stale data point on the dev Spoolman.

---

## UI-27 — Brand defaults to null + canSubmit requires brand

**State**: fixed (2026-05-30 install-gate)
**Found in**: U10 install-gate, 2026-05-30
**Routing**: U10 close-out

`FormState.brand` defaulted to `Brand("Generic")` so an accidental Save & Write
on a fresh form would create a "Generic" vendor row in Spoolman. User wanted
the button to stay disabled until a brand is explicitly picked.

**Fix shipped**:
- `FormState.brand` default `null` (was `Brand("Generic")`).
- `FormState.canSubmit` requires `brand != null` (in addition to material,
  colour, temps).
- Removed unused `DEFAULT_BRAND` constant.
- Defensive `"Generic"` / `"Unknown"` fallbacks in `MainViewModel` left in
  place — canSubmit prevents reaching them, but keeps the call sites safe.

---

## UI-28 — Read FAB enlarged + Save & Write disabled-state visible

**State**: fixed (2026-05-30 install-gate)
**Found in**: U10 install-gate, 2026-05-30
**Routing**: U10 close-out

`Read tag` FAB used default size + bodyMedium label — felt cramped. Save &
Write button disabled-state was the M3 default (12% alpha background, 38%
alpha content) which read as a blank card with the `main-form-card`
surface, making the button look invisible.

**Fix shipped**:
- `ReadFab`: explicit `Modifier.height(64.dp)` + `titleMedium` text + 8dp
  horizontal padding around label.
- `SaveAndWriteButton`: explicit disabled colours — `primary.copy(alpha = 0.5f)`
  background + full-strength `onPrimary` text. Stays clearly visible (still
  primary-tinted, just dimmer) while Save is gated.

---

## UI-29 — Bottom instruction footer removed

**State**: fixed (2026-05-30 install-gate)
**Found in**: U10 install-gate, 2026-05-30
**Routing**: U10 close-out

The v1-style instruction footer (`InstructionFooter` composable, "• Tap a
tag to read…" / "• Or fill the form…" / "• Press Read tag…") at the bottom
of the screen was redundant once the read/write hints moved to top pills
(UI-23) and the form's own labels speak for themselves.

**Fix shipped**: `InstructionFooter` composable + its call site at
`MainScreen.kt:260` deleted.

---

## UI-30 — Chain-delete orphan vendor/filament/spool when no UID lands

**State**: fixed (2026-05-30 install-gate)
**Found in**: U10 install-gate, 2026-05-30
**Routing**: U10 close-out

`CreateAndPairUseCase` and `VendorUidOnlyPairUseCase` create Spoolman
records (vendor → filament → spool) **before** the user taps a tag. If the
user never taps (timeout), declines move-on-bind, or write fails before
`appendCardUidToSpool` succeeds, the just-created records become orphans
in Spoolman. Pin-for-retry (UI-19/UI-20 follow-up) only helped if the user
actually retried; walking away left the orphan forever.

**Fix shipped**:
- New file `OrphanSpool.kt` — public `OrphanSpool` data class (carries
  `spoolId`, optional `filamentId`, optional `vendorId`); internal
  `Resolved<T>(value, wasCreatedFresh)` for resolve-helper return shape;
  public `NewSpoolBundle` exposing fresh-vs-reused for the chain.
- `SpoolmanApi`: 3 new endpoints — `@DELETE /api/v1/spool/{id}`,
  `@DELETE /api/v1/filament/{id}`, `@DELETE /api/v1/vendor/{id}`.
- `SpoolmanRepository.resolveOrCreateVendor` and `resolveOrCreateFilament`
  now return `Resolved<T>` instead of bare `T`.
- New `createSpoolForNewFilamentBundle(req): SpoolmanOutcome<NewSpoolBundle>`
  threads `wasCreatedFresh` through. Old `createSpoolForNewFilament`
  preserved as a thin wrapper for callers that don't need orphan info.
- New `chainDeleteOrphan(orphan): SpoolmanOutcome<Unit>` — best-effort:
  spool DELETE unconditional; filament DELETE only if its `id` is set on
  the orphan; vendor DELETE only if its `id` is set. 4xx swallowed with
  `Log.w` (e.g. filament still referenced by another spool — that's fine).
  Caches pruned via new `removeSpoolFromCache` / `removeFilamentFromCache`
  / `removeVendorFromCache` helpers.
- `CreateAndPairUseCase.lastResolvedOrphan: OrphanSpool?` (replaces the
  previous `lastResolvedSpoolId`-only field — kept that as a derived
  getter for the pin-for-retry path). Set in `resolveSpool.Created`,
  cleared the moment `appendCardUidToSpool` returns Success. The
  reused-filament-on-fresh-vendor edge collapses correctly: if filament
  reports `wasCreatedFresh=false`, the bundle treats vendor as reused too.
- `VendorUidOnlyPairUseCase.lastResolvedOrphan` — same field on the
  vendor flow's new-spool path.
- `MainViewModel`: new `fireOrphanCleanup(orphan)` helper +
  `private val spoolman` (was implicit constructor param). Wired into:
  - `applyWriteResult.NfcFailed` — chain-delete branch when orphan present;
    falls back to pin-for-retry when not (existing spool / append succeeded).
  - `applyWriteResult.Cancelled` — symmetric to NfcFailed; covers the
    timeout fallback too (Cancelled is what `withTimeoutOrNull` synthesises).
  - `applyVendorUidOnlyPairResult.SpoolmanFailed` / `.MoveOnBindPartial` /
    `.Cancelled` — fire cleanup on each failure branch.
- **NOT triggered on `VerifyFailed`** — UID was already PATCHed to the
  spool before verify ran, so the spool is real.
- **NOT triggered on `Success.WrittenAndPaired`** — UID landed.

**Verification**: ran on moto g stylus 2025 — Save & Write with brand-new
brand+material then walking away (10s timeout) leaves Spoolman counts
unchanged. Logcat shows three sequential DELETE calls. Existing-brand
flow only deletes the orphan spool, leaves vendor + filament intact.

**Test gap**: local `./gradlew :app:testDebugUnitTest` is broken
(JDK reflection issue, separate problem); chain-delete unit coverage
deferred. Manual verification stands in until the env issue is resolved.

---

## UI-31 — Carry-over: known bug deferred to next session

**State**: closed (re-elicited 2026-05-31; turned out to be the U1 round-trip
issue tracked as UI-33 below — not a SpoolPainter bug)
**Found in**: U10 install-gate, 2026-05-30
**Routing**: post-commit follow-up → reclassified as UI-33 (printer-side)

User flagged a remaining bug after UI-30 chain-delete shipped, deferred to
next session: *"we have some bug but we will get to it next"*. Re-elicited
2026-05-31 during U10 install-gate Snapmaker U1 round-trip — root cause was
the U1's `Snapmaker Components > Spoolman Integration` toggle being off
(printer-side firmware config), not a SpoolPainter app bug. SpoolPainter v2
writes the OpenSpool JSON correctly; U1's `openspool_tag_processor` parses
it; spoollink resolves UID → Spoolman → Fluidd once the toggle is on.
Reclassified to UI-33 below.

---

## UI-32 — Test env fix + fixture updates for U10 install-gate

**State**: fixed (2026-05-30 install-gate post-commit)
**Found in**: U10 install-gate close-out, 2026-05-30
**Routing**: U10 close-out (post-commit follow-up)

After committing U10's main bundle, `./gradlew :app:testDebugUnitTest`
was still broken locally — Gradle 8.13 fails to instantiate
`DefaultReportContainer` under JDK 24 with `Type T not present` (Gradle
8.13 predates JDK 24 reflection support).

**Fix shipped**:
- `gradle/wrapper/gradle-wrapper.properties`: Gradle 8.13 → 8.14.3 (JDK 24
  reflection compat).
- Test fixtures updated to match U10 install-gate behaviour:
  - `NfcTestSupport.makeTag()` returns a Tag with non-null UID + Ndef
    techList, so UI-20's synthesised RawTagRead on Writing-state taps
    classifies as Blank instead of Vendor.
  - `sampleUid()` corrected to uppercase (`"%02X"` output of
    `CardUid.fromBytes`); the lowercase-asserting test renamed.
  - `FakeSpoolmanApi` gains DELETE overrides for spool/filament/vendor.
  - `FakeSpoolmanRepository` overrides `createSpoolForNewFilamentBundle`
    (default: both fresh) and `chainDeleteOrphan`; adds
    `chainDeleteOrphanCalls` assertion list.
  - Brand default `Brand("Generic")` → `null` (UI-27) — fixtures in
    `FormMappingTest`, `MainViewModelTest`, `MainViewModelFilamentPickerTest`
    use `assertNull` now.
  - `awaitNonAmbientSnackbar` filters the new ambient strings (`"Blank tag
    detected."`, `"Vendor tag. Press Read to load."`).
  - VerifyFailed / NfcFailed snackbar assertions match UI-19 copy
    (`"Saved to Spoolman. Tag write failed. Try again."`).
  - `verify-mismatch` / `verify-throw` tests rewritten for UI-20 (verify
    block removed; write success no longer depends on readback).
  - Pair-another dismissed snackbar gains trailing period.

**Verification**: 361 / 361 tests green.

**Note**: dedicated chain-delete unit coverage (UI-30) deferred — the
fixture changes prove the use cases compile against the new repo shape,
but assertions on `chainDeleteOrphanCalls` for the failure paths are
worth adding next session.

---

## UI-33 — Snapmaker U1 round-trip: external setup gotchas (known limitation, doc-only)

**State**: closed (doc-only; SpoolPainter v2 unaffected)
**Found in**: U10 install-gate Snapmaker U1 round-trip, 2026-05-31
**Routing**: known-limitation note — printer-side, not an app bug

Two distinct U1-side gotchas surfaced during the install-gate round-trip;
both isolated to the printer stack, not the SpoolPainter app.

**(a) Wiped-tag malformed-NDEF state.** A tag wiped with a non-SpoolPainter
tool (e.g. NFC Tools "Erase") may leave page 4 holding `03 04 D8 00 00 00
FE 00` — a valid NDEF Message TLV pointing at a 4-byte TNF_EMPTY record,
with the prior OpenSpool JSON bytes still after the `FE` terminator. U1's
`openspool_tag_processor` finds the NDEF message but errors with
`NDEF parsing error -3` because there's no `application/json` MIME record
inside; falls through `tigertag_tag_processor`; gives up; firmware logs
`"Detected tag … but failed to read data"`. Slot reads as empty in Fluidd.
**Workaround**: Save & Write a full SpoolPainter payload to overwrite the
malformed TLV (rewrites page 4 with the full NDEF MIME record). Re-tap on
U1 succeeds.

**(b) `Snapmaker Components > Spoolman Integration` toggle off in U1
firmware config.** Even with a correctly-written full OpenSpool tag and
the `paxx12-snapmaker-u1/SnapmakerU1-Extended-Firmware` PR #491 build
installed, Fluidd showed only the bare data `openspool_tag_processor`
parsed (color, hotend min/max, raw `spool_id`) — no Spoolman-derived
spool name / brand / variant / weight / full temps. Cause: the firmware
patches install the `spoollink` agent but leave the integration disabled
by default. Without spoollink, the firmware POSTs to
`/printer/filament_detect/set` with the bare parsed fields and stops
there — no Moonraker `CARD_UID` event subscription, no
`GET /api/v1/spool?limit=1000&allow_archived=true` call to Spoolman, no
`SET_PRINT_FILAMENT_CONFIG` enrichment.
**Fix**: Fluidd → Settings → **Snapmaker Components > Spoolman
Integration**, toggle on, set Spoolman URL. Re-tap. Expected log lines
post-fix: `urllib3.connectionpool: Starting new HTTP connection: <spoolman
host>` + `GET /api/v1/spool?…` + spool data filling in to Fluidd.

**SpoolPainter v2 verdict**: writes correct OpenSpool JSON
(MIME=`application/json`, full envelope per OpenSpool spec); U1's
processor parses it; spoollink resolves UID via `extra.card_uids` (plural,
uppercase hex, comma-separated, double-JSON-encoded — matches our
`ExtraCardUidsCodec`); end-to-end round-trip works once the printer-side
toggle is on. **No app code change needed.**

**Doc impact**: log both gotchas in `aidlc-docs/operations/manual-nfc-checklist.md`
§ Snapmaker U1 round-trip as known-environment notes for testers running
the gate on their own printers.

---

## UI-34 — R8 ParameterizedType crash in release Retrofit calls

**State**: fixed (2026-05-31 install-gate post-build)
**Found in**: U10 install-gate release-APK on-device smoke (item #2 of the
install-gate carry-overs), 2026-05-31
**Routing**: U10 close-out follow-up

First-launch sideload of the signed v2.0 release APK (versionCode 100,
6.9 MB) crashed on the moto g stylus 2025 / Android 16 the moment the
app issued its first Spoolman HTTP call:

```
java.lang.ClassCastException: java.lang.Class cannot be cast to
    java.lang.reflect.ParameterizedType
  at $Proxy2.listSpools (Unknown Source)
  at SpoolmanRepository$findSpoolsByCardUid$$inlined$performHttp$1.invokeSuspend(...)
```

**Root cause**: R8 full mode (AGP 8+ default) aggressively strips
`Signature` generic-type metadata from interfaces it considers
proxy-only. Retrofit reads the response type from a suspend function's
`Continuation<? super Response<List<SpoolmanSpool>>>` parameter at
runtime; without the parameterised Signature, Retrofit's call adapter
sees a raw `Class` where it expects a `ParameterizedType` and throws.

The U10 ProGuard rules already kept `*Annotation*, InnerClasses,
Signature, Exceptions, EnclosingMethod` via `-keepattributes`, but in
R8 full mode that alone isn't enough — R8 still re-emits the interface
without the generic upper bounds. The official `retrofit2`
`consumer-rules.pro` ships a conditional `-if interface … -keep …
interface <1>` rule + a `Continuation` keep that we were missing.

**Fix shipped (`app/proguard-rules.pro`)**: replaced the simpler
Retrofit block with R8-full-mode-safe rules:

- `-if interface * { @retrofit2.http.* <methods>; }` followed by
  `-keep,allowobfuscation,allowshrinking interface <1>` — preserves
  every `@GET/@POST/@PATCH/@DELETE`-annotated interface against R8's
  proxy-only stripping.
- `-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>; }` — preserves the per-method
  signatures that carry the parameterised return types.
- `-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation`
  — keeps the suspend-function Continuation upper-bound generic info
  Retrofit reads on first invocation.
- `-keep,allowobfuscation,allowshrinking interface
    com.spoolpainter.app.data.remote.spoolman.SpoolmanApi { <methods>; }`
  — belt-and-suspenders explicit keep of our one Retrofit interface.
- `-dontwarn javax.annotation.**` / `kotlin.Unit` /
  `retrofit2.KotlinExtensions{,$*}` — silences R8's noise about
  Retrofit-adjacent classes that aren't on the runtime classpath.

**Verification**:
- Rebuilt `assembleRelease` ✅ — APK still 6.9 MB unchanged.
- `adb install -r` on moto g stylus 2025 / Android 16. App PID stable
  through Settings save → Read FAB → tap OpenSpool tag → Save & Write
  → tap writable tag → Saved snackbar. Zero `FATAL EXCEPTION`. App
  process stayed on the same PID throughout.
- Verified at the same time: NFR-5 logcat zero D/I/W from
  `com.spoolpainter.app.*` sources during the smoke (5919 total log
  lines captured; 15 from app PID, all framework-side: InsetsController,
  ImeTracker, WindowOnBackDispatcher; none from our Kotlin code). The
  `-assumenosideeffects android.util.Log` rule is doing its job.

**Why this didn't show up in earlier U10 verification**:
`assembleRelease` builds had been green throughout U10 — but a green
build only means R8 compiled successfully, not that the resulting
classes can be reflected on at runtime. This crash only surfaces on
device the first time the Retrofit proxy is invoked. Items #2 + #3 of
the U10 install-gate carry-overs (release-APK on-device smoke + NFR-5
logcat verify) caught it. Future R8 changes should always include an
on-device smoke before signoff — `assembleRelease` green is necessary
but not sufficient.

---

## UI-35 — Pair-another sheet: no Cancel during second-tag listening

**State**: fixed (uncommitted, U13 install-gate patch 2026-06-06)
**Found in**: U13 §C.3 install-gate, 2026-06-06
**Routing**: U13 (current unit — folded into close-out commit).

When the user taps **Pair another** on the PairAnotherTagSheet, MainViewModel
flips `activeFlow` from `PromptingPairAnother` → `WritingSecondTag` and starts
listening for the second tap. But `MainScreen.pairAnotherState` only built the
sheet's UI state from `PromptingPairAnother`, so the sheet *vanished* during
`WritingSecondTag` — leaving the user with no Cancel surface, no progress
affordance, and no way back to the prompt without tapping a tag (or waiting
out the 15s timeout).

The MainViewModel side was already correct (`onPairAnotherTagAccepted` at
`MainViewModel.kt:1203` toggles between Accept and Cancel based on
`activeFlow`). The bug was purely on the projection layer.

**First-cut fix attempt — rejected**: I initially extended
`PairAnotherTagUiState` with a `secondTagListening` flag and built a
Cancel button (with spinner) *inside* the sheet. User pushback was
correct on two grounds: (1) spinner on a Cancel button breaks the
convention set by the inline `[Read|Write]` row's text-only Cancel
(status lives on the centered `NfcStatusOverlay`); (2) two Cancel
surfaces — sheet's + inline row's — for the same flow is one too many.

**Second-cut fix — shipped**: reuse the existing inline Cancel. The
sheet auto-dismisses during `WritingSecondTag` (BottomSheetHost only
mounts on `PromptingPairAnother`); the standard inline `[Read|Write] →
Cancel` row takes over because `isWriteCancellable` now returns `true`
for `WritingSecondTag` too. `onWriteTapped` recognizes the new state
and routes the cancel through the existing `onPairAnotherTagAccepted`
toggle (which cancels the writeJob + disarms NFC + flips `activeFlow`
back to `PromptingPairAnother`). The sheet re-mounts at its prompt
state with Done + Pair another buttons. One Cancel surface, one
convention.

**Files changed** (2 prod):

1. `MainViewModel.kt` — `isWriteCancellable` predicate now matches
   `WritingForPair || WritingRaw || is WritingSecondTag`.
   `onWriteTapped` detects `WritingSecondTag` and delegates to
   `onPairAnotherTagAccepted`.
2. (no other files changed — `PairAnotherTagUiState` /
   `PairAnotherTagSheet` / `BottomSheetHost` / `MainScreen` reverted
   to pre-bug state. The fix lives entirely in the VM's
   `isWriteCancellable` projection + `onWriteTapped` dispatch.)

Tests held at 403/403. No new tests added — the existing toggle behaviour
was already covered in `MainViewModelTwoTagTest`; the bug was UI projection.


---

## UI-36 — Archive a spool / filament from the app

**State**: open
**Found in**: U13 close-out conversation, 2026-06-07
**Routing**: v2.1+ (not blocking U13).

Today the app can read / pair / edit but not archive. To archive a
spool the user has to open Spoolman web UI and toggle
`spool.archived = true` (Spoolman uses an "archived" flag, not a
delete — soft hide so historical references stay valid).

**Why this is the right shape**:

- v2's `MoveOnBindUseCase` and chain-delete logic both already model
  the "spool is no longer active for this UID" pattern. Archive sits
  alongside.
- Spoolman's data model has `spool.archived` (boolean) and
  `filament.archived` (boolean) — both PATCH-targets via the existing
  PATCH endpoints. No new endpoint needed.
- Listing already filters `allowArchived=true` via
  `SpoolmanRepository.listSpools` (U6a fix); the dropdown filters
  archived rows at the UI layer
  (`MainScreen.SpoolmanDropdown` predicate).
- The UI affordance is small: a long-press on a spool dropdown row,
  or an `Archive` overflow item on the picker rows, or an explicit
  "Archive this spool" action in a follow-up sheet after Save.

**Sketch** (not committed; design later):

1. **Surface**: long-press on a `PickerRow` in the Spool dropdown →
   contextual sheet with "Archive spool" + "Cancel". Archive PATCHes
   `spool.archived = true`; the row disappears from the dropdown on
   the next refresh.
2. **Wire**: extend `SpoolPatchBody` with `archived: Boolean? = null`
   (Gson omits null already). New
   `SpoolmanRepository.archiveSpool(spoolId)` thin wrapper. New
   `ArchiveSpoolUseCase` — single PATCH + cache evict.
3. **Filament archive**: same pattern with `filament.archived` —
   surfaces from the Filament dropdown's long-press. Cascading is
   Spoolman's job (their server semantics: archiving a filament
   doesn't auto-archive its spools).
4. **Undo**: archived spools/filaments stay visible behind a Settings
   toggle "Show archived" → list filters off the predicate. Tapping
   one offers "Unarchive" instead of "Archive".
5. **Test plan**: `ArchiveSpoolUseCaseTest` (1 PATCH + cache evict),
   `MainViewModelArchiveTest` (long-press → sheet → confirm → row
   disappears). Add an install-gate scenario in v2.1.

**Why not now**: U13 already added Save split + radio weight picker +
filament-record unlock + currency dropdown. Tester turnaround on a
v2.0.3 testing-track upload is more valuable than batching archive
into the same release. Carve as a v2.1.x patch after the v2.0.3
push lands.

## UI-37 — Send feedback row in Settings (open-test only)

**State**: closed — kept intentionally (2026-07-25). Decision at the
v2 production launch: feedback links are appropriate in production
(both "Send feedback" and "Report a tag issue" stay). The
`TODO(open-test-only)` marker was removed from `SettingsScreen.kt`.
The "why remove" rationale below is retained for the audit trail but no
longer applies — the personal-Google-Form noise concern was accepted.
**Found in**: U14 close-out, 2026-06-07
**Routing**: ~~remove before promoting v2.1 from Open testing to
production track~~ → superseded; kept.

A "Send feedback" `OutlinedButton` was added to the bottom of
Settings as part of v2.1, opening a Google Form
(`https://forms.gle/Yx94vLHCSaBWRL1m9`) via `Intent.ACTION_VIEW`.
Purpose: give Open testing-track testers a one-tap channel back to
the maintainer.

**Code location**: `app/src/main/java/com/spoolpainter/app/ui/screens/settings/SettingsScreen.kt`
- `FEEDBACK_URL` const (file-private, marked `TODO(open-test-only)`)
- `OutlinedButton` rendered after `SettingsVendorSection`
- Test tag: `settings-feedback`

**Why this needs to come out before prod**:
- The form URL is a personal Google Form tied to a specific account.
  Production users hitting it create noise + can't be triaged at scale.
- "Send feedback" implies a support channel SpoolPainter doesn't have.
  Removing the row keeps user expectations honest.
- The TODO marker in code is the durable reminder; this entry is the
  "don't lose this" record.

**To remove**:
1. Delete the `FEEDBACK_URL` const + the `OutlinedButton` block.
2. Drop the `LocalContext` + `Intent` + `Uri` imports if no other
   call site needs them (currently the only consumer in this file).
3. Drop the test-tag from any test that asserts on it (none today).

---

## UI-38 — Can't edit remaining weight prefilled from Spoolman

**State**: fixed (2026-06-20)
**Found in**: field report on v2.1.2, 2026-06-20
**Routing**: bugfix unit (FD / NFR-R / NFR-D / Infra-D SKIP; Code Gen direct)

**Verbatim**: "still not able to edit remaining weight if the weight is
saved in spoolman, when the weight comes from there, and i try to delete
the numbers to update, delete don't work."

**Root cause (CONFIRMED on-device via logcat, not assumed)**: the weight
field had `if (input.length > 5) return@OutlinedTextField` *before* applying
any edit. A Spoolman-prefilled value can be long (spool #71 showed
`995.56146` = remaining 823.56 + empty 172, computed as a float), and every
backspace produced an 8-char string still `> 5`, so the handler bailed and
the local `text` reverted. logcat proved it: `current text` stayed
`995.56146` across every keystroke. This explains the user's later clues:
"some filaments work, some don't" (short weights like `730` fit under the
cap) and "works initially then stops" (once long, the value is trapped).
The `if (remaining < 0f) return` guard in the VM was a *secondary* swallow,
also removed, but the input-length cap was the primary cause.

NOTE: my first two attempts blamed the VM default / back-solve guard and
"fixed" them without on-device proof — both failed. The fix only landed
after adding logcat instrumentation to the actual text field and reading
the real keystroke trace. Lesson: weight-edit bugs live in the Compose
field, not the VM; unit tests don't exercise it. Verify on-device.

**Fix**: in `WeightMethodRadio.ActiveValueField` (and the shared
`DecimalField` in `MoreDetailsExpander`), the length cap no longer blocks a
deletion — it only rejects growth past the cap (`sanitised.length >
text.length`), never a shrink. Also removed the VM's `if (remaining < 0f)
return` swallow guard; a measured weight below the empty spool now commits
`remainingWeightG = (measured − empty).coerceAtLeast(0f)` and always stashes
the typed `measuredEntry` so the field displays what the user typed. A
rounding experiment (`Math.round`) was tried and REVERTED — it would have
written a rounded value back over Spoolman's exact float and lost precision;
the field now shows Spoolman's value verbatim.

**Code location**: `app/src/main/java/com/spoolpainter/app/ui/components/WeightMethodRadio.kt`
(`ActiveValueField` input cap) + `MoreDetailsExpander.kt` (`DecimalField`
cap) + `MainViewModel.kt` (`onActiveWeightChanged` Measured branch).

**Verification**: on-device logcat on spool #71 — `995.56146 → 995.5614 →
995.561 → 995.56`, each keystroke committed and echoed back cleanly.

**Tests**: `FormMappingTest` +1 (Measured default + remaining prefill);
`MainViewModelMoreDetailsExpanderTest` back-solve-skip test rewritten to
assert clamp-to-0 + entry retained.

---

## UI-39 — Numeric keyboard on numeric fields

**State**: fixed (2026-06-20)
**Found in**: user request, 2026-06-20
**Routing**: bugfix unit (rides UI-38)

**Verbatim**: "for fields that are numeric like temp, weight etc just do
numeric keyboard."

**Root cause**: the temperature field in `TempPanel` had no
`keyboardOptions`, so it popped the full alphanumeric keyboard even though
it only accepts digits (input was already filtered to `isDigit()`).

**Fix**: added `keyboardOptions = KeyboardOptions(keyboardType =
KeyboardType.Number)` to the `TempPanel` field. Audit of the other numeric
fields confirmed they were already correct — weight / empty-spool / filament
weight / density / price (`DecimalField` in `MoreDetailsExpander` +
`WeightMethodRadio`) all use `KeyboardType.Decimal`; the Spoolman URL field
uses `KeyboardType.Uri`; currency + sort are dropdowns; color hex stays
alphanumeric (accepts A–F). So the only gap was the temperature field.

**Code location**: `app/src/main/java/com/spoolpainter/app/ui/components/TempPanel.kt`
(field `keyboardOptions`).

**Tests**: none added — keyboard type is a soft-input hint with no logic
branch (input filtering unchanged).

---

## UI-40 — Empty-spool weight didn't match Spoolman (filament default vs per-spool)

**State**: fixed (2026-06-20)
**Found in**: field report on v2.1.3 (spool #71 screenshot), 2026-06-20
**Routing**: bugfix unit (rides UI-38), v2.1.4

When a spool has both a per-spool `spool.spool_weight` override AND a
`filament.spool_weight` default, the app preferred the per-spool value while
Spoolman's own edit UI shows the filament default. Spool #71 stored
`spool.spool_weight = 172` but `filament.spool_weight = 193`; the app's
Measured (remaining + 172 = 995.56) disagreed with Spoolman's
Measured (remaining + 193 = 1016.56) by 21 g.

**Decision (user)**: match Spoolman. `FormMapping.fromSpoolman` now uses
`spool.filament.spool_weight ?: spool.spool_weight` (filament default first,
per-spool only as fallback). Reverses the v2.0.2 "spool overrides filament"
precedence for the empty-weight read.

**Safety**: both `emptySpoolWeightG` and the `prefilledEmptySpoolWeightG`
snapshot derive from the same source, so an untouched Save produces
`emptyDirty == false` and does NOT clobber the stored per-spool 172.

**Code location**: `app/src/main/java/com/spoolpainter/app/ui/screens/main/FormMapping.kt`
(`effectiveSpoolWeight`).

**Tests**: `FormMappingTest` +2 (filament-default-wins; per-spool fallback
when filament has none).

---

## UI-41 — "What's new" modal showed on every launch

**State**: fixed (2026-06-20)
**Found in**: field report on v2.1.2, 2026-06-20
**Routing**: bugfix unit (rides UI-38), v2.1.4

**Verbatim**: "why the fuck modal that tell about app keep showing up, isnt
that something supposed to happen only once, every time i close app and open
this shows up."

**Root cause (CONFIRMED on-device via logcat)**: `WhatsNewController
.onColdStart` read `settingsRepository.settings.value` synchronously in
`MainActivity.onCreate`. `settings` is `store.data.stateIn(initialValue =
Settings())`, so `.value` returns the eager default (`lastSeenWhatsNewVersion
= 0`) until DataStore's async first read lands. Every cold start raced that
load, saw 0, and `shouldShow(version, 0, false)` returned true → sheet shown.
`markSeen()` then wrote the version, but the next cold start hit the same
race and read 0 again. So it showed on every launch.

**Fix**: added `SettingsRepository.awaitSettings()` which reads the raw
`store.data.first()` (genuinely suspends until disk). `onColdStart` now
launches a coroutine, awaits the real persisted value, then decides.

**Code location**: `SettingsRepository.kt` (`awaitSettings`) +
`WhatsNewController.kt` (`onColdStart`).

**Verification**: on-device logcat — 4 consecutive cold starts all read
`lastSeen=107` and decided `show=false`; modal stayed dismissed.

**Tests**: `WhatsNewControllerTest` updated for the now-async `onColdStart`
(`advanceUntilIdle` before asserting visibility); `FakeSettingsRepository`
gained `awaitSettings()`.

## UI-42 — NTAG213 write-fail copy ignores that the UID still got mapped

**State**: fixed (U16, v2.1.5, 2026-07-04)
**Found in**: field report on v2.1.4, 2026-06-30
**Routing**: next bugfix unit (copy + flow), v2.1.x.

**Fix**: `MainViewModel.applyWriteResult` NfcFailed branch now matches
`reason.contains("too small")` (the string `NfcAdapterWrapper` already tags)
and surfaces "Paired only. This tag is too small to write full data." The UID
append already happened in `CreateAndPairUseCase` step 3 before the outcome was
decided, so the tag is genuinely mapped by serial. Install-gate verified.

When the target tag is too small for our NDEF payload (NTAG213: ~144 B
capacity vs our ~216 B payload), the write fails and we surface a flat
"Tag write failed. Try again." But in the two-button flow the spool's UID
is committed to Spoolman's `extra.card_uids` *before* the NDEF write outcome
is decided, so the tag IS already mapped to the spool by serial. Telling the
user only "write failed" understates what actually happened and reads as a
total failure.

**Fix scope**: when the write fails specifically because the tag is too small
(the capacity branch in `NfcAdapterWrapper.writeViaNdef` already detects this
and tags the message "tag too small: …"), surface copy that says the mapping
succeeded but the on-tag write didn't, e.g. "Tag mapped to this spool, but
it's too small to store the full data." Spoolman-side UID resolution still
works; only the on-tag OpenSpool payload is missing.

**Code locations**:
- `app/src/main/java/com/spoolpainter/app/hardware/nfc/NfcAdapterWrapper.kt`
  (`writeViaNdef`, capacity probe ~line 117-131 — already distinguishes the
  "too small" IOException).
- `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainViewModel.kt`
  (`CreateAndPairResult.NfcFailed` / `VerifyFailed` snackbar branches
  ~line 1108-1163 — needs a too-small-aware message; thread the capacity
  reason through `result.reason`).

**Open question**: does the "too small" case currently route through
`NfcFailed` (with the orphan-cleanup branch) or `VerifyFailed`? If it hits
the orphan chain-delete path, this overlaps with UI-43 — the UID/spool must
NOT be torn down on a too-small write, since the mapping is the useful result.

---

## UI-43 — Remove orphan chain-delete on write failure (two-button flow made it obsolete)

**State**: fixed (U16, v2.1.5, 2026-07-04)
**Found in**: field report on v2.1.4, 2026-06-30
**Routing**: next bugfix unit, v2.1.x. Pairs with UI-42.

**Fix**: orphan chain-delete removed *entirely* as dead code, not just gated —
every Write path now targets an already-existing spool (Save creates it on a
separate button; `canWrite` requires a selected spool), so the `newSpoolPath`
branch that set an orphan is unreachable from Write. Deleted `fireOrphanCleanup`
+ all 5 call sites, `SaveToSpoolmanUseCase`/`VendorUidOnlyPairUseCase.lastResolvedOrphan`,
`SpoolmanRepository.chainDeleteOrphan` + 3 now-unused private cache helpers,
and the `OrphanSpool` type (`NewSpoolBundle` + `Resolved<T>` moved to new
`SpoolmanCreateModels.kt`). Write failure now keeps the spool and pins the
selection so a retry appends. Install-gate verified: failed Write no longer
deletes the spool.

Legacy single-action flow created a spool and wrote the tag in one shot, so a
write failure left an orphan spool with no tag — hence the chain-delete
cleanup (`fireOrphanCleanup` → `SpoolmanRepository.chainDeleteOrphan`, deletes
spool + filament + vendor we created in the same transaction).

With the two-button Save / Write split, Save creates the Spoolman records on
purpose and Write is a separate deliberate action. Deleting the spool when a
Write fails is now wrong: user reports creating a spool, tapping Write, the
write failing, and the spool **disappearing**. They expect the spool to stay
so they can retry Write (or map a different tag).

**Fix scope**: remove (or gate off) the orphan chain-delete on the
write-failure paths. Keep the spool; pin the selection so a retry appends to
the existing record instead of duplicating (the no-orphan branch already does
this). Verify no path still constructs/holds an `OrphanSpool` that would fire
cleanup on a plain Write failure.

**Code locations**:
- `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainViewModel.kt`
  — `fireOrphanCleanup` (~line 1478) and its call sites in
  `CreateAndPairResult.NfcFailed` (~1135), `.Cancelled` (~1169), and the
  `VendorUidOnlyPairResult` failure branches (~1449-1463).
- `app/src/main/java/com/spoolpainter/app/domain/usecases/CreateAndPairUseCase.kt`
  + `SaveToSpoolmanUseCase.kt` (`lastResolvedOrphan` plumbing).
- `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanRepository.kt`
  (`chainDeleteOrphan`, ~line 780) — may become dead code; remove if no
  remaining caller.

**Risk**: confirm the orphan-cleanup wasn't also covering a genuine
"Save succeeded but UID never attached" case that should still clean up. If
it was, narrow the removal to the Write-failure path only, not all of
`NfcFailed`.

---

## UI-44 — End-of-pairing count should reflect actual UIDs in Spoolman, not the flow

**State**: fixed (U16, v2.1.5, 2026-07-04)
**Found in**: field report on v2.1.4, 2026-06-30
**Routing**: next bugfix unit, v2.1.x.

**Fix**: new `MainViewModel.pairedTagCount(spoolId)` counts `extra.card_uids`
(via `ExtraCardUidsCodec`) from `spoolman.spools.value` — the repo StateFlow,
updated synchronously by `appendCardUidToSpool` → `replaceSpoolInCache` right
after the PATCH, so it reflects the just-appended UID (the async VM mirror can
lag a tick). `pairedMessage(spoolId, written)` builds the line with a
**write-aware prefix**: "Tag written and paired." for NDEF writes,
"Vendor tag linked." for UID-only vendor pairs, each followed by
"This spool now has N tag(s)." (clause dropped at count 0). Install-gate
verified for both prefixes + singular/plural.

At the end of a pairing flow we report how many tags were paired based on the
flow that just ran ("Both tags paired", "Saved with one tag", etc.). The user
wants the count to reflect the **actual number of UIDs in the spool's
`extra.card_uids` in Spoolman** — the true total paired with that spool, not
just what this session did. Example desired copy: "Total paired: N tags."

**Fix scope**: on pair completion, read back the spool's `extra.card_uids`
length (decode with `ExtraCardUidsCodec`) and report the total. This also
correctly reflects tags paired in earlier sessions or directly in Spoolman.

**Code locations**:
- `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainViewModel.kt`
  — `onPairAnotherTagDismissed` (~line 1284, "Saved with one tag." /
  "Vendor tag linked."), `applyTwoTagResult.SecondTagPaired` (~1297-1299,
  "Both tags paired"), and the vendor UidPaired branch (~1347).
- `app/src/main/java/com/spoolpainter/app/domain/primitives/ExtraCardUidsCodec.kt`
  (`decode` → count).
- Source the spool from `state.spoolman.spools` by `selectedSpoolId`, or
  re-fetch if the local copy may be stale after the just-completed PATCH.

**Open question**: the local `spools` list may not yet reflect the UID we
just appended (PATCH round-trip). Decide whether to count
`stored card_uids + (this session's new UID if not already present)`, or to
re-read the spool from Spoolman before composing the snackbar.

---

## UI-45 — Pick color hex from the camera

**State**: fixed — shipped as U17 (v2.2.0), 2026-07-04.
**Found in**: feature idea on v2.1.4, 2026-06-30
**Routing**: DONE — feature unit U17.

**Shipped shape** (differs from the original recommendation): went with the
**CameraX live preview** path (per user direction), not the lightweight
image-capture intent. Live preview + fixed center reticle + live `#HEX` chip;
"Use this color" locks the averaged center-patch sample into the Color field.
Sampling math is a pure, unit-tested `ColorSampling` object (averaged N×N
center patch); the CameraX preview/permission flow is verified on-device.
Entry point is a "Scan color" action sharing the "Color Wheel" row in
`ColorPicker.kt`. **CameraX APK-size impact = +0.12 MB only** (7.41 → 7.53 MB
release; R8 strips the unused surface), so the size concern that motivated the
lighter fallback never materialised. `CAMERA` permission + `camera.any`
uses-feature (`required="false"`) added.

---

## UI-46 — Dropdown action row can't be both always-visible and field-adjacent

**State**: fixed — shipped as U18 (rides v2.2.0), 2026-07-04.
**Found in**: U17 install-gate iteration, 2026-07-04
**Routing**: DONE — feature/polish unit U18.

**Shipped shape**: new shared `PinnedActionMenu.kt` — a `Popup` + `Column` with
the action row **pinned** at the field-adjacent edge and a `LazyColumn` list
scrolling between it and the far edge. Open-direction is decided **once** (from
the anchor bounds) and passed to the position provider, so the pinned edge and
the popup placement can never disagree. Extends the existing `LazyDropdownAnchor`
from `LazyDropdownMenu.kt`. All three pickers were rewired to it; the duplicated
"Other +" row was extracted into a single `PinnedOtherAction(label, onClick)`
composable shared by MaterialPicker + BrandPicker (Color's row stays inline — it
carries two distinct tap targets, Color Wheel body + Scan color, not the same
row with different data). `DropdownDirection.kt` **deleted** (no other callers).
On a 30-vendor Brand list flipped upward, "Other" now sits at the bottom edge
next to the field and never scrolls away. Tests held at 514 / 514 (presentation
-only swap); install gate PASSED on moto g stylus 2025 / Android 16.

---

The Material / Brand / Color pickers put a high-value action at one end of the
menu (Material/Brand: "Other"; Color: "Color Wheel" + "Scan color"). U17 added
`rememberDropdownDirection()` (`DropdownDirection.kt`) so the action sits
nearest the anchor field: top when the menu opens down, bottom when it flips
up. That fixed the short menus (Color, Material) but **regressed the long one
(Brand, 30+ vendors)**: when Brand opens upward the action moves to the bottom
of a long scroll, so the user must scroll the whole list to reach "Other".

The fundamental limitation: inside a single scrolling menu you can guarantee
the action is **always visible** (pin it at top) OR **field-adjacent** (put it
at the near end), but not both on a long list.

**Proper fix**: build a shared menu where the action row is **pinned** (does
not scroll with the list) at the field-adjacent edge, with the item list
scrolling underneath. Standard `ExposedDropdownMenu` scrolls its entire content,
so this needs a custom menu (fixed action row + separate scrolling `LazyColumn`
list), used by all three pickers so it stays a single component (no
duplication). Alternatives considered and parked: move the action into the text
field itself (icon), or add type-to-filter search to kill the long-list scroll.

**Code locations**:
- `app/src/main/java/com/spoolpainter/app/ui/components/DropdownDirection.kt`
  (the current reorder helper the pinned menu would replace).
- `ColorPicker.kt`, `MaterialPicker.kt`, `BrandPicker.kt` (call sites).
- `LazyDropdownMenu.kt` (existing custom-menu precedent for the spool/filament
  pickers — the pinned-action menu could extend this pattern).

---

## UI-47 — Camera color sampler: reticle didn't match the analyzed area

**State**: fixed — shipped as U19 (v2.2.1), 2026-07-04.
**Found in**: user review of the U17 camera sampler, 2026-07-04
**Routing**: DONE — bugfix unit U19.

The U17 sampler drew a fixed **64dp** circle reticle but averaged a fixed
**20px square** at the center of the camera *analysis frame*. Those are two
different coordinate spaces (screen dp vs. frame px) and two different shapes
(circle vs. square), so the ring told the user nothing about how big the
sampled region actually was — a WYSIWYG bug. Aim worked (both dead-center under
`PreviewView.ScaleType.FILL_CENTER`), but the circle's *size* was decorative.

**Fix**: couple both to a single fraction of their respective shorter sides.
`ColorSampling.DEFAULT_PATCH_FRACTION` (0.10) now drives:
- the sampled patch — `ColorSampling.patchForFraction(minOf(frameW, frameH))`
  in `CameraColorSampler.sampleCenterHex` (replaces the hardcoded 20px);
- the reticle — `minOf(maxWidth, maxHeight) * DEFAULT_PATCH_FRACTION` via a
  `BoxWithConstraints` wrapping the preview (replaces the hardcoded 64dp).

FILL_CENTER center-crops, so equal fractions of each shorter side map onto the
same real-world spot: the square patch is inscribed in the circle the user aims
with, so everything sampled is visibly inside the ring. Shrinking the fraction
shrinks both together — they can't drift apart. Circle size was taken from 0.18
→ 0.10 per user ("can circle be smaller?").

**Considered and declined**: camera zoom (pinch / slider / fixed default) —
color is a large flat property and patch-averaging is already robust to a spool
filling most of the frame; zoom is a narrow win for multicolor/stripe spools
only, so parked until a real need surfaces. Exact circle==sample match (square
reticle, or circular sampling region) also declined — inscribed square is close
enough for "point and read."

**Code locations**:
- `app/src/main/java/com/spoolpainter/app/domain/primitives/ColorSampling.kt`
  (`DEFAULT_PATCH_FRACTION`, `patchForFraction`).
- `app/src/main/java/com/spoolpainter/app/ui/components/CameraColorSampler.kt`
  (`BoxWithConstraints` reticle size + `sampleCenterHex` patch size).
- `app/src/test/java/com/spoolpainter/app/domain/primitives/ColorSamplingTest.kt`
  (3 new `patchForFraction` cases).

---

## UI-48 — Type-to-search the spool / filament pickers (HIGH PRIORITY)

**State**: fixed (U21, install gate PASSED 2026-07-27) — sticky search box in
the Spool + Filament picker popups; case-insensitive substring over row text +
#id; "No matches" guard; a non-blank query drops the U20 float and shows a flat
filtered list. Material/Brand/Color stay scroll-only; substring (not fuzzy) is
the v1 bar. Mid-gate the U20 scan-time float scorer also gained a variant
signal (weight 1.0, lenient, below color) — committed with U20.
**Routing**: **own follow-up unit** — split OUT of U20 per user direction
2026-07-27 ("we should do search write thing seprate"). Will reuse U20's
`PickerRanking` helper (extend with a `filter(query, rows, textOf)` mode) and
needs the `LazyDropdownMenu` search-slot + empty-result-guard work that U20
intentionally does not touch. See U20 plan §8.

Today the Spool dropdown and Filament picker are scroll-only
(`LazyDropdownMenu` / `PinnedActionMenu` over a `LazyColumn`). With a
large Spoolman inventory (50+ spools), finding an entry means scrolling.

**Ask**: add a text input at the top of the picker that filters the list
as the user types. Match against material / brand / name (and likely the
Spoolman spool id). Case-insensitive substring is the v1 bar; fuzzy match
is a nice-to-have but not required for the first cut.

**Code location (starting points)**:
- `app/src/main/java/com/spoolpainter/app/ui/components/LazyDropdownMenu.kt`
  (the Spool + Filament dropdown host — where the search field would mount).
- `app/src/main/java/com/spoolpainter/app/ui/components/PinnedActionMenu.kt`
  (pinned-action variant used by Material / Brand / Color; may share the
  search affordance or stay scroll-only — Material/Brand lists are short).
- Filtering logic belongs in a pure helper (testable) that takes the query
  + the spool/filament list and returns the filtered, still-sorted list.
  Reuse the existing sort comparators.

**Not**: the `OutlinedTextField`s already in `MaterialPicker.kt` /
`BrandPicker.kt` are the "Other" custom-entry field, not search — do not
conflate them.

---

## UI-49 — Closest-match suggestion on tag read (HIGH PRIORITY)

**State**: **fixed — shipped as U20 (F2), 2026-07-27.** Reframed to a
rendering-only reorder: unpaired read → scorer floats top-3 material/brand/color
matches (best first) to the top of both pickers, thin divider, no label, no
auto-select. On-device rank order verified after an ordering fix (was floating in
picker sort order, not match quality). Version HELD (batched bump later).
**Routing**: U20 (done, uncommitted close-out).

**Reframe 2026-07-27 (supersedes the original chip-based ask below)**: per user
direction, this is a **rendering-only reorder**, not a chip. On an unpaired read
(vendor tag, or OpenSpool tag with no `card_uids` link and no resolvable spool
id), a pure `SpoolMatchScorer` scores the inventory on **material / brand / color
only** (temps excluded per user) and remembers the good-match filament + spool id
sets. **When the user opens** the Spool or Filament picker, the **top 3** matches
float to the top with a **thin divider only (no "Suggested" label)**; the rest of
the list follows in normal sort order. **No confirm chip, no auto-select, no
behaviour change** — purely how the picker renders when opened. If nothing
scores, the pickers look exactly as today. See U20 plan §0 (invariant) + §1 F2 +
§2 LOCKED answers.

**Original ask (kept for record; superseded by the reorder reframe above):**

**Ask**: on a tag Read that is *not* already paired to a spool (blank tag,
or a vendor / OpenSpool tag with no matching `card_uids` link), run a
heuristic over the Spoolman inventory and surface the closest-matching
**spool or filament** so the user can confirm the link in one tap instead
of manually finding it.

**Signal for matching** (the decoded payload / prefilled form already
carries these): material `type`, `brand`, `color_hex`, temperature range
(`min_temp` / `max_temp`), and `subtype`/variant. Rank candidates by a
weighted score (exact material+brand strong; colour-hex distance; temp
proximity) and present the top match (or top few).

**Where it plugs in**:
- The read path resolves in `NfcRepository.handleTag` / classify, and the
  form prefill happens in `MainViewModel.applyResult` (BlankForm / vendor
  `parsedHint` branches). The suggestion is computed after prefill, against
  `spoolman.spools.value` / the filament list.
- Matching logic must be a pure, unit-tested scorer (parallels
  `ColorSampling` / the filament matcher in `SpoolmanRepository`
  `resolveOrCreateFilament` — reuse `ColorHexCodec` for colour distance).
- UI surface: a suggestion chip / row ("Closest match: <spool> — pair?")
  near the picker, non-destructive (never auto-selects; user confirms).

**Relation to existing behaviour**: distinct from `card_uids` exact
resolution (that already auto-selects an already-paired spool) and from
`resolveOrCreateFilament` (server-side exact-identity match on
create-and-pair). This is a *fuzzy* suggestion for the unpaired case.

---

## UI-50 — Multi-color hex + variant field limits (GitHub issue #5)

**State**: open (feasibility confirmed 2026-07-26; requested by user jdkluck)
**Found in**: GitHub issue https://github.com/ni4223/SpoolPainter/issues/5
**Routing**: next feature unit candidate. Two independent asks; the variant
half is a quick win, the multi-color half is a real feature.

### Ask 1 — Multi-color hex (dual/tri-silk, e.g. Polymaker Panchroma)

Support more than one color per filament so multi-color spools show
correctly instead of forcing an averaged/dominant single hex.

**Feasibility: fully supported by the U1 extended firmware, via Spoolman.**
Traced end to end 2026-07-26:
- **Spoolman native field** `multi_color_hexes` — `models.py` `String(128)`,
  added upstream in the 2024-05-28 `415a8f855e14_multi_colors` migration.
  Format is a single comma-separated string, e.g. `"C49449,786BB0"`
  (Spoolman client stores `color_hexes.join(",")`). NOT an extra field, so
  no `ensureExtraFieldsRegistered`-style registration needed (unlike
  `card_uids` / `variant`).
- **U1 firmware SpoolLink** (`spoollink.py`) reads
  `filament.get("multi_color_hexes")`, splits on comma, uppercases, trims to
  6 chars, pads to 5 slots, pushes `RGB_1..RGB_5` to the printer. So the U1
  renders it. This is the path that matters (Spoolman link, not the tag).
- **OpenSpool tag** also defines `additional_color_hexes` (JSON array, up to
  4 additional); firmware `filament_protocol_ndef.py` maps it to
  `RGB_1..RGB_N` with `COLOR_NUMS` derived. Per user direction the tag write
  is only a backup, so this is secondary / later pass.

**Work required (SpoolPainter side):**
- Add `multi_color_hexes: String?` to `SpoolmanFilament` model +
  `CreateFilamentRequest` + `PatchFilamentBody` (plain top-level field).
- `sparseDiff` / `resolveOrCreateFilament` need to account for it (matching,
  patch). Reuse/extend `ColorHexCodec` (currently single-value).
- UI: `ColorPicker.kt` currently captures exactly one 6-char hex
  (`.take(6)`, `singleLine`, `seedColor` single). Needs multi-hex entry +
  a split/gradient swatch to display. This is the real lift.
- Naming mismatch to handle: Spoolman/SpoolLink field is
  `multi_color_hexes` (comma string); OpenSpool tag field is
  `additional_color_hexes` (JSON array). A full impl bridges both.

### Ask 2 — Variant character limit (quick win)

Descriptive variant labels get cut. Real limitation, and it predates v2
(inherited from v1 `5995b3d`; the v1 comment even mislabels it `// Max 10`
next to `.take(25)`).

**Current behaviour** (`FilamentForm.kt` `VariantField`, ~line 186):
```kotlin
val sanitised = input.filter { it.isLetterOrDigit() || it in " -" }
    .take(25)
```
- Hard **25-char cap** (`.take(25)`).
- Character filter allows only letters, digits, space, hyphen. Strips
  parens, `+`, `/`, `#`, `&`, etc. — so "PLA (Matte)" → "PLA Matte",
  "PLA+" → "PLA". (v1 allowed `+`; v2 dropped it — minor regression.)

**Fix:** raise the cap (Spoolman `extra.variant` is generous) and loosen the
filter to allow common punctuation. Small, self-contained change.

**Ask 2 DONE 2026-07-27** (`FilamentForm.kt` `VariantField`): cap 25 → 50;
filter now allows `+ ( )` in addition to letters/digits/space/hyphen, so
"PLA (Matte)" and "PLA+" survive (the exact cases in the issue). Kept the
allowlist tight — no other symbols added. **Ask 1 (multi-color hex) still
open** — that's the real feature lift, left for a later unit.

**User-reply drafted** (not yet posted) acknowledging both asks; v1 history
intentionally left out of the public reply.

---

## UI-51 — NFC stops scanning after opening the camera color picker (BUG)

**State**: NOT REPRODUCED on NTAG (2026-07-27). Reframed → likely a slow/flaky
Snapmaker (MifareClassic) read + weak on-screen feedback, NOT an NFC-dispatch
/ camera-lifecycle bug. See "2026-07-27 investigation" below.

### 2026-07-27 investigation (on-device, moto g stylus 2025 / Android 16)

Ran the camera + permission repro multiple times on the debug build with an
NTAG (uid `0465B693DA2A81`, NfcA/MifareUltralight/Ndef). **Could not
reproduce** any NFC failure:
- Camera permission revoked → cold launch → tap tag (scans) → Color picker →
  Scan color → Allow camera → close → clear form → tap tag again: **scanned
  fine, same data.** `handleTag` fired on every tap.
- Also ran the write path (fill form → camera flow → write to tag → read
  again): **everything worked.**

So the `attach()` guard / camera-lifecycle theory (below) did NOT pan out on
an NTAG. Key realisation: **the reporter's failing tag was a Snapmaker
(MifareClassic) tag; all our tests used a fast NTAG.** The code confirms
MifareClassic reads are inherently slow/flaky:
- `TagFormatParser.MAX_READ_ATTEMPTS = 3` — MifareClassic retries the whole
  read+parse up to 3x (NTAG tries once).
- Each attempt authenticates all 16 sectors trying each vendor's keys in
  registry order (`MifareClassicReader.tryReadRawCountedMulti`). Many NFC
  round-trips; the tag must stay perfectly still the whole time.
- Read timeout 10s; ambient buffer TTL 5s.

**Reinterpreted report**: "Snapmaker tag wouldn't scan" = the slow
MifareClassic read failed (tag lifted early, or no Bambu/Snapmaker key set),
so it felt like "won't scan". The camera-permission mention is most likely
coincidental (nothing in code ties camera perm to NFC read; couldn't repro a
link). Their workaround (scan a non-blank NTAG, edit, write to a blank)
worked because it avoided the MifareClassic read entirely and used fast
NTAGs. The pasted diagnostic (`outcome: Blank`, NfcA/Ultralight/Ndef) is from
that blank NTAG, NOT the Snapmaker tag — the Snapmaker tag never read cleanly
enough to log.

**Reframed fix direction** (not the lifecycle stuff): improve **vendor-read
UX** — clearer/longer "still reading, hold the tag still" feedback during a
multi-second MifareClassic read, and guidance to keep the tag flat/still.
Possibly surface "no key set for this vendor" when a MifareClassic read
auths 0 sectors. Cannot test further without a real Snapmaker/Bambu
MifareClassic tag on hand (user doesn't have one either).

**Old theory (kept for record; did NOT reproduce):**

**Found in**: user feedback-form submission, SpoolPainter 2.2.1 build 111,
Snapmaker tag. Reporter's words: "Would scan initially when opening the app,
after giving it permission to use my camera, would not allow scanning ...
I got it to work by scanning a rfid that was not blank, changing the
elements, then wrote to a blank rfid."

**Severity**: high-ish. Breaks the core NFC flow after a user touches the
U17 camera color scanner. Workaround exists (force a full read/edit/write
cycle) but it's non-obvious.

**Diagnostic from the report** (this part is a red herring — the tag really
is blank, classification is correct):
```
uid: 04275C41C82A81
techList: NfcA, MifareUltralight, Ndef
outcome: Blank
```
The bug is NOT tag parsing. It's NFC foreground-dispatch lifecycle,
regressed by the U17 camera feature.

**Suspected root cause** (two candidate mechanisms, confirm which on-device):
- NFC uses `enableForegroundDispatch` tied to `onResume`/`onPause`
  (`MainActivity.onResume → nfcRepository.attach`,
  `onPause → detach`). `NfcAdapterWrapper.enableForegroundDispatch`.
- Tapping "Scan color" fires the CAMERA permission dialog
  (`CameraColorSampler.kt:111` `permissionLauncher.launch`). On some Android
  versions a permission dialog does NOT deliver a clean onPause/onResume to
  the host activity, so dispatch is left disabled and never re-armed.
- **The guard in `NfcRepository.attach` (line 62) `if (attached === activity)
  return` is the trap**: it assumes "same activity instance ⇒ dispatch still
  armed", but the OS/permission flow can disable dispatch while `attached`
  still points at the same activity. A re-`attach(sameActivity)` then
  no-ops and never calls `enableForegroundDispatch` again.
- Also worth checking: CameraX `bindToLifecycle(lifecycleOwner)` at
  `CameraColorSampler.kt:240` binds to the activity lifecycle; interaction
  with dispatch re-arm timing on the permission-grant resume.

**Repro to run on-device (moto g stylus 2025 / Android 16):**
1. Fresh launch (camera permission NOT yet granted). Confirm a tag scans.
2. Open Color picker → Scan color → grant camera permission when prompted.
3. Close the sampler, tap a tag. Does it scan? (report says no.)
4. Compare against: permission ALREADY granted (no dialog) — does the bug
   still happen? This isolates "permission dialog pause" vs "camera bind".

**Candidate fixes (pick after repro):**
- Make `attach()` idempotent-safe: always call `enableForegroundDispatch`
  even when `attached === activity` (cheap; re-arming is safe), OR drop the
  early-return guard.
- Explicitly disable NFC dispatch while the camera sampler is open and
  re-arm on dismiss (DisposableEffect), so the two subsystems don't race.
- Ensure onResume re-arm survives the permission-dialog return path.


---

## UI-52 — Select a filament → float its spools in the Spool picker

**State**: **fixed — shipped as U20 (F3), 2026-07-27.** On-device VERIFIED
(moto g stylus 2025 / Android 16): B1 multiple spools float, B2 archived excluded,
B3 never auto-selects, B4 clear returns to normal sort. Selecting a spool also
floats its filament's siblings (spool-pick sets selectedFilamentId) — reviewed and
kept intentionally per user ("i actually like all siblings together").
**Routing**: U20 (done, uncommitted close-out).

**Ask**: when a filament is selected, surface the spools that belong to it. This
is the deterministic sibling of UI-49's heuristic reorder (a spool carries
`filament.id`, so "spools of this filament" is an exact lookup, not a guess).

**Behaviour** (rendering-only, matches the UI-49 reframe): selecting a filament
does NOT change the spool selection. **When the user opens** the Spool picker,
that filament's **unarchived** spools float to the top with a **thin divider only
(no label, no main-screen hint)**. The rest of the list follows in normal sort
order. **Never auto-select** (even when the filament has exactly one spool). No
behaviour change to any existing flow. (Q-U20-4 = no hint; Q-U20-1 = no header.)

**Precedence** (both UI-49 and UI-52 can float spools): filament selected →
UI-52 ("This filament"); else scan suggestion active → UI-49 ("Suggested"); else
normal sort. See U20 plan §2 D7/D9.

**Spool-select mirror**: user confirmed 2026-07-27 that selecting a *spool* needs
no distinct behaviour (spool→filament is one-to-one/trivial); "same for spool"
meant applying the same surfacing style, which the shared section-header
rendering already provides.

---

## UI-53 — App stops recognizing tags after 1-2 reads/writes until reopened (BUG)

**State**: open (reported 2026-07-30, tester end-to-end pass on v2.3.0 Open testing)
**Found in**: tester feedback, n=10 tag round-trip test (Spoolman, no U1 yet)
**Severity**: high — breaks the core NFC flow after a couple of taps.

**Report**: consistently, after reading and/or writing a tag 1-2 times, the app
stops recognizing a tag near the reader coil and fails to read. The **phone
still detects the tag** (gentle vibration as it nears the coil), so the tag and
hardware are fine — the app just isn't receiving the dispatch. Closing and
reopening the app fixes it every time. Reproduced 5-6 times, so easily
repeatable.

**Read**: not the tag — the phone-level vibration confirms the OS sees the tag;
the app's foreground NFC dispatch is no longer armed to receive it. Likely the
same class of NFC-dispatch / re-arm lifecycle issue suspected in UI-51 (foreground
dispatch left disabled and never re-`enableForegroundDispatch`'d). Needs on-device
repro to confirm whether it's tied to a write completing, a color/camera picker
open, or just N taps. See UI-51 investigation notes for the `attach()` early-return
trap theory (`if (attached === activity) return`).

**Ask tester**: does it correlate with anything specific (right after a write, or
after opening the color/camera picker)?

---

## UI-54 — Spoolman edit not reflected on the selected spool after pull-to-refresh (BUG)

**State**: open (reported 2026-07-30)
**Found in**: tester feedback, v2.3.0 Open testing
**Severity**: medium — stale data shown; workaround exists but feels buggy.

**Report**: read a tag that matches a spool, then edit that spool in Spoolman
(e.g. material PLA → PETG). The app keeps showing the old value (PLA) no matter
how many times you pull-to-refresh. Only **deselecting + reselecting** the spool
(or closing the app and re-reading the tag) picks up the change.

**Root cause** (traced in code 2026-07-30): the form is a **one-time snapshot**.
`onSpoolSelected` runs `FormMapping.fromSpoolman(...)` once at selection time and
copies the data into `FormState`. `onPullToRefresh` → `spoolman.refreshIfStale(force
= true)` re-fetches the spool-list **cache** but does NOT re-project the fresh
entry onto the already-selected form. Compounded by the same-id early-return in
`onSpoolSelected` (`if (spool.id == _state.value.form.selectedSpoolId) return`),
so re-picking the same spool is a no-op — only clearing the selection first works.

**Fix direction**: after a refresh, if a spool is selected, re-derive the form
from the fresh cache entry (respecting the stale-prefill guard so it doesn't
clobber in-progress edits), or at minimum let re-selecting the same spool
re-derive.

---

## UI-55 — No "colorless / transparent" state; multi-color spools force a wrong single hex

**State**: open (reported 2026-07-30) — related to [[UI-50]] (multi-color hex)
**Found in**: tester feedback, v2.3.0 Open testing
**Severity**: user-story / data-model gap (not a crash; not a hard save gate).

**Report**: clear/transparent and dual/tri-color spools don't fit the flow. You
must pick a color from the short list or the color picker to represent the spool,
which then writes an inaccurate color to the Spoolman record.

**Read**: the form models exactly one color (`FormState.colorHex`, defaults to
`DEFAULT_COLOR_HEX`; the picker seeds `initialColor = colorHex ?: "FF0000"`), so
there's (a) no first-class "no color / transparent" option and (b) no way to
represent more than one hex. Color is NOT a hard save gate (`canSave` doesn't
require it and `toExpanderOverrides` null-guards the hex), so it doesn't "break"
in the crash sense — but the single-hex model can't represent these spools, so
the user is nudged into an arbitrary/wrong color. Fold into the UI-50 multi-color
hex work; add a distinct "no color" state alongside multi-hex.
