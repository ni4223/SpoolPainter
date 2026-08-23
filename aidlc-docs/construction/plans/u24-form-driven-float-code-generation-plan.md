# U24 — Code Generation Plan (Part 1)

**Unit**: U24 — float matching filaments from the form fields the user picks
**Scope source**: `ui-followups.md` [[UI-59]] **reading (a)** (the active scope as
corrected 2026-08-22); serves the [[UI-57]] sister-filament flow shipped in U23
**Opened**: 2026-08-22
**Per-unit gate**: FD / NFR-R / NFR-D / Infra-D **SKIP** — small feature unit on
an existing pure core; design folds into this plan (same convention as U20 / U21
/ U22 / U23).
**Version**: **BUMPED at close-out to versionCode 115 / versionName 2.4.0** — the
joint release carrying U22 + U23 + U24. 114 (built for U22 as 2.3.2) is skipped
deliberately: we could not confirm whether it reached Play, and reusing a consumed
code is what rejected the first U22 upload. Git tags are not a record of Play
uploads.

---

## §0 Overriding invariants

1. **Rendering-only, at picker-open time.** No auto-select, no change to
   read / prefill / pair / save / write. Same invariant U20 and U21 shipped under.
2. **U20's verified scan float is not disturbed.** When a scan suggestion set
   exists it wins outright; form-driven suggestions only fill in when there is
   no scan set. U20's on-device-verified rank order (red → black → white) must
   still hold, and its regression test must pass untouched.
3. **No permanent floated group.** A form-driven float that is always on is
   noise, not signal — it would make the divider meaningless and permanently
   reorder the picker. The trigger gate in §1 F1 exists solely to prevent this;
   it is the whole reason this unit needed a design decision rather than a
   one-line wiring change.
4. **`MainUiState.scanSuggestedFilamentIds` keeps meaning "the scan set".** The
   form-driven list is a *separate* derived value. Overloading the scan field
   would make U20's clear-on-selection / clear-on-new-read logic wrong.

---

## §1 Scope

### F1 — form-driven filament float (UI-59 reading (a))

**Today**: the Filament picker floats rows for exactly two reasons — a scan
(`MainViewModel.computeScanSuggestions`, `MainViewModel.kt:1111`, wired at
`:1189` / `:1224`) and, for the *Spool* picker, a filament selection (U20 F3).
Manual form edits feed neither. So after the U23 sister-filament flow (pick a
filament → X → fields stay, link drops) the user has a fully populated form and
opening the Filament picker still shows the default sort.

**Wanted**: with no scan set and nothing selected, score the inventory against
the **form's own** material / brand / colour / variant and float the best
matches — so "set brand + material, open the picker, siblings are on top".

**The trigger gate (the one real decision — Q-U24-1).** `SpoolMatchScorer.Query`
maps straight from `FormState`, so this is mostly wiring, *except* that a fresh
form is not blank:

| Field | Fresh-form default | As a query signal |
|---|---|---|
| `material` | **PLA** (`MainUiState.kt:48`, non-null) | matches → +3.0 for every PLA filament |
| `colorHex` | **`FFFFFF`** (`:50`) | white is within `COLOR_MIN_CLOSENESS` of a large slice of the palette |
| `brand` | **`null`** (`:49`) | contributes nothing until the user picks one |
| `variant` | `null` (`:51`) | contributes nothing |

So a naive `Query(form)` scores > 0 for most of the inventory on a form the user
has not touched — invariant 3 violated on first launch.

**`brand` is the natural gate**: it is the only identity field that defaults to
null, so a non-null brand is positive evidence the value came from a user pick or
a prefill rather than from a default. ("Generic" is only a *save-time* fallback —
`MainViewModel.kt:532` etc. — never a form default.) Recommended rule:

> Build a query only when **brand is set**. Once it is, material / colour /
> variant all join the query and contribute their normal weights.

Checked against the flows that matter:

| Flow | Brand | Float? | Right? |
|---|---|---|---|
| Fresh launch, untouched form | null | no | ✅ invariant 3 |
| Sister flow (pick filament → X) | set from the sister | yes | ✅ the point of the unit |
| Manual entry, brand picked | set | yes | ✅ |
| Material changed to PETG, no brand yet | null | no | ⚠️ the known cost |

The ⚠️ row is the honest cost of the simple rule: material-only intent does not
float. Alternatives are in §2 Q-U24-1.

**Suppressed while something is selected.** When `selectedFilamentId != null` or
`selectedSpoolId != null` the identity fields are locked (`MainScreen.kt:249-250`)
and their values are the selection's own, so a float would just re-list the
selection. Today's behaviour in that state is "no float" (U20 clears the scan set
on manual selection, `MainViewModel.kt:720` / `:859`), and this unit keeps it.

### F2 — naming honesty (mechanical)

The list is threaded `MainScreen → FilamentForm → FilamentSection →
FilamentPicker` as `scanSuggestedFilamentIds`. Once it can also carry
form-derived ids that name is a lie, so rename the **parameter** to
`suggestedFilamentIds` in those four files. `MainUiState.scanSuggestedFilamentIds`
is deliberately **not** renamed (invariant 4 — it really is the scan set).

### Out of scope, deliberately

- **UI-59 reading (b)** — floating read-derived values in the Material / Brand /
  Colour pickers. Independent; stays open in `ui-followups.md`.
- **Form-driven float in the Spool picker** (Q-U24-2). U20 F3 already floats the
  selected filament's spools there, and the Spool picker is the top-of-screen
  primary control — extra reordering there is the highest-noise, lowest-value
  half of the idea.
- **`mergeMaterials` untrimmed dedupe key** — same gap as the UI-62 bug, still
  unobserved, still not this unit's business.

---

## §2 Open questions — ALL ANSWERED 2026-08-22

- **Q-U24-1 — the trigger gate. → (A) REQUIRE BRAND SET** (user). **RECOMMEND (A) require brand set**: zero new
  state, uses the fact that brand is the only identity field defaulting to null,
  and it exactly covers the sister-filament flow this feature is for. Cost: a
  material-only intent ("show me PETG") does not float.
  - **(B) track touched fields** — add e.g. `FormState.touchedIdentityFields` and
    build the query from *only* what the user actually changed, trigger on ≥1.
    Most precise (PETG-with-no-brand works, and a defaulted PLA never
    contributes a phantom +3.0), but it is new mutable form state that every
    prefill / reset / re-derive path must maintain correctly — `onClearAll`,
    `onFilamentSelected`, `onSpoolSelected`, `PrefillFromSpoolman`,
    `reDeriveSelectedSpoolForm`. That is five paths that can silently rot.
  - **(C) require ≥2 non-default signals** — cheap, but "non-default" is
    fragile: a user who genuinely wants PLA in white gets nothing, and the rule
    is invisible in the UI.
- **Q-U24-2 — float form-matched spools in the Spool picker too? → NO** (user). **RECOMMEND
  NO** (see "out of scope"). Cheap to add later if wanted.
- **Q-U24-3 — cap. → KEEP 3** (user). **RECOMMEND keep `SUGGESTED_CAP = 3`**, shared with the scan
  path, so both floats look identical. A sister filament usually has few enough
  siblings that 3 covers it; raising the cap only for the form path would make
  the divider position inconsistent between the two triggers.

---

## §3 Design decisions

- **D1** — The gate + query construction land as a new pure
  `SpoolMatchScorer.formQuery(material, brand, colorHex, variant): Query?`
  (null = no float). Keeps the rule in the object that owns `Query`, documented
  next to the weights it interacts with, and testable in the existing
  `SpoolMatchScorerTest` with no VM harness.
- **D2** — Resolution lives in the ViewModel as a **new derived StateFlow**
  `suggestedFilamentIds: StateFlow<List<Int>>`, following the existing
  `canSave` / `canWrite` pattern (`MainViewModel.kt:127` / `:164`):
  `combine(_state.map { signals }.distinctUntilChanged(), spoolman.filaments)`.
  Derived-flow, **not** written back into `_state` — a `_state` → compute →
  `_state` loop is a real hazard here and this shape makes it structurally
  impossible. `distinctUntilChanged` on a small signals tuple keeps a variant
  keystroke from re-scoring the inventory needlessly.
- **D3** — Precedence is explicit and in one expression: `scan set` → else
  `nothing selected && formQuery != null` → else empty. Testable in Kotlin, which
  matters because this module has no Compose UI test source set.
- **D4** — `computeScanSuggestions`'s candidate mapping is extracted to a private
  `matchCandidates(filaments)` and reused, so the two triggers can never drift on
  how a `SpoolmanFilament` becomes a `Candidate` (notably
  `FormMapping.decodeExtraVariant` for the JSON-encoded `extra["variant"]`).
- **D5** — **`FilamentPicker` itself is not touched** beyond the parameter rename.
  It already takes an ordered id list and floats it under a thin divider; feeding
  that list from a second source needs no rendering change. This is what keeps the
  unit's blast radius at "wiring + one pure function".
- **D6** — No label on the floated group, thin divider only — unchanged from
  Q-U20-1.

---

## §4 Steps

- [x] **S1** — `SpoolMatchScorer.formQuery(...)` + the gate per Q-U24-1, with the
      defaults table from §1 in the KDoc so the reason survives.
- [x] **S2** — `SpoolMatchScorerTest`: gate null on a fresh-form tuple (PLA +
      null brand + `FFFFFF`); non-null once brand is set; material / colour /
      variant carried through; blank-vs-null brand treated the same.
- [x] **S3** — `MainViewModel`: extract `matchCandidates`, add the
      `suggestedFilamentIds` derived flow with D3's precedence.
- [x] **S4** — Rename the threaded parameter to `suggestedFilamentIds`
      (`MainScreen`, `FilamentForm`, `FilamentSection`, `FilamentPicker`) and
      pass the collected flow from `MainScreen`.
- [x] **S5** — Tests, new `MainViewModelFormSuggestionTest`: fresh form → empty;
      brand picked → matches float in rank order; scan set present → scan wins
      and form is ignored; filament selected → empty; **sister flow** (select →
      `onFilamentSelected(null)`) → form-driven float appears; colour change
      reorders. Confirm `MainViewModelSuggestionTest` (incl. U20's red → black →
      white rank regression) still passes unmodified.
- [x] **S6** — Docs: `ui-followups.md` UI-59 → fixed for reading (a), (b) left
      open; check off this plan; write `u24-summary.md`.
- [x] **S7** — Build matrix: `compileDebugKotlin`, `testDebugUnitTest`,
      `assembleDebug`. Baseline is **592** tests.
- [x] **S8** — On-device install gate **PASSED** 2026-08-22 (moto g stylus 2025 / Android 16, 114 / 2.3.2-DEBUG). All six steps green; see §8.

---

## §5 Install gate script

Device required (moto g stylus 2025 / Android 16 has been the reference).

1. **No noise on a fresh form**: cold start, touch nothing, open the Filament
   picker → default sort, **no divider**. This is invariant 3 and it is the
   thing most likely to be wrong.
2. **Sister flow end-to-end** (the reason for the unit): pick a filament, tap the
   X, open the Filament picker → the sister and its siblings are on top under the
   divider. Change the colour, reopen → the ranking follows the new colour.
3. **Manual entry**: fresh form, pick a brand only → matching filaments float.
   Add a material → ranking tightens.
4. **Scan still wins**: read an unpaired vendor tag, then open the picker →
   U20's floated set and its order, not a form-derived one.
5. **Selected = no float**: with a filament selected, open the picker → no
   divider.
6. **Search unaffected**: type a query with a float active → flat filtered list,
   no divider (U21 Q-U21-1); clear it → the float returns.

---

## §6 Risks

- **R1** — Invariant 3 is the whole risk. If the gate is too loose the picker is
  permanently reordered and the divider stops meaning anything. §5 step 1 is the
  check; it is cheap and must be run first.
- **R2** — Re-scoring on every keystroke. Mitigated by `distinctUntilChanged` on
  a signals tuple, and the inventory is a handful of rows on a phone; if it ever
  matters the fix is to debounce the variant field, not to cache.
- **R3** — The parameter rename touches four files for zero behaviour change. If
  any call site is missed the build fails loudly, which is the good failure mode.
- **R4** — Two float sources means "why is this row on top?" gets harder to
  answer when reporting a bug. Accepted: precedence is a single expression and
  the summary will state it.

---

## §7 Part 2 results (2026-08-22)

- `compileDebugKotlin` ✅ · `testDebugUnitTest` ✅ **606 / 606** (Δ +14 vs 592) ·
  `assembleDebug` ✅ **68.69 MB**. Version HELD at 114 / 2.3.2.
- Shipped exactly as planned; no design drift. New pure
  `SpoolMatchScorer.formQuery` (+5 tests), new derived
  `MainViewModel.suggestedFilamentIds`, extracted `matchCandidates`, private
  `FormSuggestionSignals` projection, parameter renamed across the four UI files.
- One self-inflicted test failure en route: `assertEquals(3, size.coerceAtMost(3))`
  in the passive-invariant test, which needs ≥ 3 matches while that fixture has
  2 Bambu filaments. The assertion was wrong, not the code; replaced with the
  exact expected list.
- **S8 install gate PASSED** — see §8.

---

## §8 Install gate results — PASSED 2026-08-22

moto g stylus 2025 / Android 16, build 114 / 2.3.2-DEBUG, against the user's real
Spoolman inventory.

| § | Check | Result |
|---|---|---|
| 1 | Untouched form (PLA / White / no brand) floats nothing | ✅ no divider, plain default sort |
| 2 | Sister flow: pick #94 → X → reopen | ✅ the three 3DHoJor PLA siblings float above the divider |
| 3 | Brand only (3DHoJor, material still defaulted PLA) | ✅ floats that brand's PLAs |
| 4 | A tag read still wins over the form | ✅ confirmed by the user |
| 5 | Filament selected → no float | ✅ default list, no divider |
| 6 | Search suppresses the float; clearing restores it | ✅ both directions |
| — | Colour drives the rank | ✅ White floated #94 / #99 / #20 (white, cream, pink); switching to Blue floated #96 / #97 / #21 (blue, teal, purple) |

Step 1 was run first on purpose — it is invariant 3, the only way this feature
could have made the picker worse. It passed on a cold start.

Incidental, not a defect: two blind `adb input tap` coordinates of mine landed on
a filament row mid-gate and selected TECBEARS PETG #4. Recovered with the U23
Clear all menu (which also re-verified that path), then the colour test was redone
deliberately.

Also observed, already-known and NOT introduced here: the Brand **field** shows
the preset spelling `3DHoJor` while the picker **rows** show Spoolman's vendor
spelling `3DHojor`. That is the UI-61 display-vs-stored split documented in U23;
this unit changed nothing about casing.
