# U24 — Form-driven filament float (UI-59 reading (a))

**Done**: 2026-08-22 · **install gate PASSED** (moto g stylus 2025 / Android 16, 114 / 2.3.2-DEBUG)
**Plan**: `aidlc-docs/construction/plans/u24-form-driven-float-code-generation-plan.md`
**Version**: **115 / 2.4.0** (joint release for U22 + U23 + U24; 114 skipped, see the release notes)
**Tests**: 592 → **606 ✅** (Δ +14) · `compileDebugKotlin` ✅ · `assembleDebug` ✅ 68.69 MB
**Release**: `assembleRelease` ✅ **7.68 MB** (R8) · `bundleRelease` ✅ **8.45 MB AAB** · 115 / 2.4.0 verified via aapt2 badging · APK signed with the SpoolPainter key

## What shipped

The Filament picker floats scorer-ranked matches built from the form's **own**
fields, so U23's sister-filament flow pays off: pick a filament → tap the X (the
fields stay, only the link drops) → open the picker → the sister and its siblings
are on top under the existing thin divider.

Before this, the float had exactly two triggers, neither reachable by typing: a
tag read, and (for the Spool picker) a filament selection.

## The one real decision — the trigger gate

A fresh form is **not blank**, which is why this needed a design pass rather than
a wiring change:

| Field | Fresh default | As a signal |
|---|---|---|
| material | **PLA** | +3.0 for every PLA filament |
| colorHex | **`FFFFFF`** | white is "close" to a large slice of the palette |
| brand | **`null`** | contributes nothing |
| variant | `null` | contributes nothing |

So `Query(form)` built unconditionally would score > 0 against most of the
inventory on an untouched form — a **permanently** reordered picker, which makes
the divider meaningless. `brand` is the gate (Q-U24-1 = A) because it is the only
identity field defaulting to null, so non-null brand is positive evidence of a
user pick or a prefill. ("Generic" is a save-time fallback in the ViewModel, not
a form default.)

**Accepted cost, stated plainly**: a material-only intent ("show me the PETG")
does not float, because a defaulted PLA and a deliberately-chosen PLA are
indistinguishable without tracking which fields the user touched. Touched-field
tracking was the runner-up (Q-U24-1 = B) and was declined as five state-machine
paths that can silently rot for one extra case.

## Shape of the change

- **`SpoolMatchScorer.formQuery(material, brand, colorHex, variant): Query?`** —
  new, pure, returns null when the gate says no. The rule lives next to the
  weights it interacts with, and is testable with no ViewModel harness.
- **`MainViewModel.suggestedFilamentIds: StateFlow<List<Int>>`** — new derived
  flow following the `canSave` / `canWrite` shape. Precedence in one expression:
  scan set wins → else nothing-selected + `formQuery != null` → else empty.
  Deliberately **derived and never written back into `_state`**, so a
  state → compute → state loop is structurally impossible rather than merely
  avoided.
- **`matchCandidates(inventory)`** extracted and shared, so the two triggers
  can't drift on how a `SpoolmanFilament` becomes a `Candidate` (notably that
  Spoolman keeps the variant JSON-encoded inside `extra`).
- **`FilamentPicker` is untouched** beyond renaming the threaded parameter
  `scanSuggestedFilamentIds` → `suggestedFilamentIds` (4 files). It already took
  an ordered id list; feeding it from a second source needs no rendering change.
  `MainUiState.scanSuggestedFilamentIds` keeps its name on purpose — it really is
  the scan set, and U20's clear-on-selection / clear-on-new-read logic depends on
  that.

## Tests (+14)

`SpoolMatchScorerTest` +5 — the gate returns null for a fresh-form tuple, treats
blank brand as null, carries every signal once brand is set, drops a blank
variant, and ranks the sister first.

New `MainViewModelFormSuggestionTest` +9 — untouched form floats nothing (the
load-bearing one), brand picked floats that brand, colour reorders, material
narrows, **sister flow after `onFilamentSelected(null)`**, `onClearAll` takes the
float away, scan set beats the form, passive invariant (no selection changes),
cap of 3.

`MainViewModelSuggestionTest` — including U20's red → black → white rank
regression — passes **unmodified**.

## Notes

- One self-inflicted failure en route: `assertEquals(3, size.coerceAtMost(3))` in
  the passive-invariant test needs ≥ 3 matches while that fixture has 2 Bambu
  filaments. The assertion was wrong, not the code.
- **Install gate PASSED**, all six steps. Step 1 (untouched form floats nothing)
  was run first because it is the only way this feature could have made the
  picker worse; it passed on a cold start. Colour ranking was verified by
  switching White → Blue and watching the floated set change from #94 / #99 / #20
  to #96 / #97 / #21. The scan-wins step was confirmed by the user with a real
  tag. Full table in the plan's §8.
- Out of scope and still open: **UI-59 reading (b)** (read-derived float in the
  Material / Brand / Colour pickers), and form-driven float in the Spool picker
  (Q-U24-2 = no).
