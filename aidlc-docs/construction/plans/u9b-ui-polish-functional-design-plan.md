# U9b — Functional Design Plan

**Stage**: CONSTRUCTION → Per-Unit Loop → Functional Design (U9b)
**Unit**: U9b — UI Polish (pure polish; editing deferred post-v2.0)
**Authored**: 2026-05-29
**Status**: Trimmed 2026-05-29 after editing scope was pulled out (see `audit.md` "U9b scope adjustment — drop archive too" + "add editing for later after release"). Earlier draft of this plan included a 9th item ("edit a paired spool") with 10 `Q-U9b-*` questions across save-flow models, sibling-count copy, Archive placement, etc. — all retired. Editing follow-ups parked at `ui-followups.md` UI-14 / UI-15.

## Per-unit gate assessment

| Stage | Decision | Rationale |
|---|---|---|
| Functional Design | **EXECUTE (very light)** | The 8-item scope is pure visual fit-and-finish. There is **no net-new business logic** — no new repository methods, no new state fields, no new use cases. The case for FD: (a) lock the v1-parity audit checklist (Item 2) so we don't hand-wave it during install-time iteration; (b) lock the copy strings for UI-05 / UI-07 (Items 7–8) so they get reviewed once, not bikeshed during code-gen; (c) lock the UI-02 debounce semantics (Item 6 — once-per-session vs once-per-UID) — if the helper is extracted, it gets a one-test surface. Six tight Q-U9b-* questions cover everything; no entity catalogue, no AC matrix, no business-logic diagram. |
| NFR Requirements | **SKIP** | Pure UX polish. No new performance / security / scalability concerns. Splash dependency (`androidx.core:core-splashscreen`) is well-trodden. |
| NFR Design | **SKIP** | Predicated on NFR-R running. |
| Infrastructure Design | **SKIP** | Per `aidlc-docs/inception/plans/execution-plan.md` — pure Android client; no CDK / Terraform / CloudFormation. |

**Skip-FD-entirely option** — if the user prefers, we can drop FD altogether and go straight to Code Gen Part 1, treating each item as a polish patch. The Q-U9b-* questions below would all become Code-Gen-Part-1 plan choices instead. Either path is defensible — picking FD gives one more reviewable approval gate; skipping it cuts ~10 minutes off the loop.

## Source artefacts

- `aidlc-docs/inception/application-design/unit-of-work.md` §3-U9b (locked 8-item scope; "no functional behavior changes" carve-out restored 2026-05-29 after editing scope withdrawal).
- `aidlc-docs/ui-followups.md` — UI-01 / UI-02 / UI-05 / UI-07 entries (all pulled into U9b this session). UI-13 explicitly NOT in U9b — moved to UI-14 (post-v2.0).
- `aidlc-docs/inception/user-stories/stories.md` — S-13.1 (re-validated post-polish).

### v1 source comparison reads (this session)

- `git show main:app/src/main/java/com/spoolpainter/app/ui/screens/SpoolPainterScreen.kt` — v1 main screen layout: `SpoolPainterLogo` rendered in a Row above the form; only `TemperatureCard` (with elevation) breaks the flat surface inside the form Card.
- `git show main:app/src/main/java/com/spoolpainter/app/ui/components/TemperatureCard.kt` — `Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp))` with `Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp))`.
- `app/src/main/java/com/spoolpainter/app/ui/components/TempPanel.kt` — v2 ports v1's TemperatureCard verbatim.
- `app/src/main/java/com/spoolpainter/app/ui/components/MoreDetailsExpander.kt` — flat `Column` with no `Card` wrapper; **visual root cause** of the "temp card feels like top, more details feels like an afterthought" complaint.

### Existing code touchpoints

| File | Role in U9b |
|---|---|
| `ui/components/SpoolPainterLogo.kt` | Already exists. **Not currently rendered** by `MainScreen.kt`. Restore to a Row at the top of `MainScreen` matching v1's layout (logo centered, settings IconButton on the right). (Item 1.) |
| `ui/screens/main/MainScreen.kt` | Logo Row at top (Item 1); layout audit vs v1 (Item 2); ambient-tap snackbar hookup (Item 6); UI-01 dropdown styling polish (Item 2). |
| `ui/screens/settings/SettingsScreen.kt` | `imePadding()` modifier on `SnackbarHost` so Save / Test connection feedback sits above the IME (Item 4). |
| `ui/components/MoreDetailsExpander.kt` | Wrap content in `Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(4.dp))` with `Column(modifier = Modifier.padding(16.dp), …)` matching `TempPanel` (Item 3). **No rename, no new fields, no Save button** — preserved exactly as U8 shipped it apart from the Card wrapper. |
| `ui/components/MaterialPicker.kt` / `BrandPicker.kt` | Polish "Other" affordance (Item 5). |
| `ui/components/ColorPicker.kt` | Polish "Color Wheel" affordance (Item 5). |
| `ui/activity/MainActivity.kt` + `res/values/themes.xml` + `res/drawable/` | Add `androidx.core:core-splashscreen`; declare `Theme.SpoolPainter.Splash`; pin v1 logo as splash foreground (Item 1). |
| `ui/screens/main/MainViewModel.kt` | UI-02 hookup — debounce-per-session (or per-UID, see Q-U9b-3) for ambient-tap snackbar. UI-05 / UI-07 — copy string changes only. |

### Existing code seams confirmed (read 2026-05-29)

- `MoreDetailsExpander.kt:53` — top-level `Column` with no `Card` wrapper. Adding `Card(...) { Column(modifier = Modifier.padding(16.dp), ...) { ... } }` is non-breaking; `testTag("more-details")` stays on the outer surface. **No signature change** — same parameters as U8 shipped.
- `TempPanel.kt:40-46` — exact pattern to mirror (shape / elevation / padding).
- `MainScreen.kt` already has a `SnackbarHost` — `imePadding()` is a `Modifier` on either the host's parent `Scaffold` content or the host itself. Trivial wiring change.
- `MainViewModel.observedTagUid` collector lifecycle is well-understood — UI-02's debounce can be a private `MutableStateFlow<Set<String>>` or a single `String?` (last-shown UID) inside `MainViewModel`.

---

## 1. Unit Context

### 1.1 Scope (locked by `unit-of-work.md` §3-U9b 2026-05-29 after two scope-adjust rounds)

#### In scope for U9b — **visual / UX only** (no business-logic change)

1. Branding restore — main-screen logo + Material 3 splash with v1 logo as foreground.
2. v1 main-UI parity audit + UI-01 (Spoolman dropdown styling drift).
3. Temp + More-Details visual fix — both elevated cards (matching shape / elevation / padding).
4. Snackbar visibility under keyboard (Settings + Main).
5. "Other" + "Color Wheel" affordances.
6. UI-02 passive-tap prompt.
7. UI-05 NDEF write-failure copy rewrite.
8. UI-07 broader snackbar copy review.

#### In scope for U9b — **test coverage**

- `AmbientTagDebouncerTest` *(only if extracted as a helper)* — debounce-per-session or per-UID for UI-02. If the debounce is inline inside `MainViewModel`, no new test class — exercised via existing `MainViewModelTest`.
- Pure visual fixes ship without new tests; rely on existing render-stability tests (no `testTag` regressions).

#### Out of scope for U9b — **deferred, with routing**

- **Edit a paired spool** (UI-13 filament-metadata edit + `remaining_weight` field + Archive-this-spool button) → **post-v2.0 release** per user direction "add editing for something later after release". Logged in `ui-followups.md` UI-14 + UI-15. Holding deltas: `SpoolmanRepository.patchFilament(id, sparseDiff)` already shipped at U8; `SpoolmanService.patchSpool(id, body)` not yet implemented; `SpoolPatch(remainingWeightG, archived)` data class proposed but not committed.
- **Material name + brand edits** on a paired spool → reshapes filament identity; future "create new filament instead?" / re-pair flow; bundled with UI-14.
- **NFR-5 release-build log stripping** → U10.
- **APK size review / `material-icons-extended` R8 minify** → U10 (logged as `U10-Δ-1`).
- **JDK 17 portability** → U10.
- **Legacy `sortOrder` JSON key migration** → U10 (logged as `U10-Δ-2`).

### 1.2 Cross-unit consumers

| Unit | Relationship |
|---|---|
| U8 (Filament metadata UX) | U8 shipped `MoreDetailsExpander` flat. U9b wraps it in a Card matching `TempPanel`. No signature / parameter change. |
| U9 (Settings + theming) | Independent. U9b's IME-aware snackbar host applies to Settings too (Item 4) but doesn't change Settings logic. |
| U10 (Release polish) | U10's manual install-gate matrix gains a row per visual fix verified on device. |
| Post-v2.0 (UI-14 / UI-15) | When the editing design pass eventually lands, it builds on top of U9b's polished `MoreDetailsExpander` Card — the Card wrapper makes adding "This spool" sub-headers and an Archive button cleaner later. |

---

## 2. Q-U9b-\* questions for `[Answer]:` tags

Six tight questions. Recommended option flagged. Answer with `[Answer]: <letter>` after each block, or "Go go go!!" / "i trust you" for blanket recommendations.

---

### Q-U9b-1 — Splash screen drawable source

For the v1 logo on the Material 3 splash:

A. **Vector drawable from v1's `res/drawable/`** — pull the existing logo vector verbatim. (recommended — exact v1 parity, theme-aware out of the box)
B. **PNG from v1's `res/mipmap/`** — bitmap; less flexible.
C. **Re-design** — net-new vector drawing tuned for splash dimensions.

[Answer]:A

---

### Q-U9b-2 — IME-aware snackbar host wiring

For Item 4 (snackbar visibility under keyboard):

A. **`imePadding()` on the `Scaffold` content modifier** — content shifts when IME shows. Snackbar is a child of Scaffold so it shifts too. Simple. (recommended)
B. **`imePadding()` on the `SnackbarHost` only** — more surgical; content layout doesn't shift, but snackbar floats above the IME.
C. **Dismiss IME on Save / Test connection tap** — focus clear → keyboard hides → snackbar is visible in stable space. Independent of `imePadding`.
D. **Combo of B + C** — IME-aware host AND dismiss IME on submit. Belt-and-braces but possibly redundant.

[Answer]: A

---

### Q-U9b-3 — UI-02 passive-tap prompt — once-per-session vs once-per-UID

When an ambient (un-prompted) NFC tap surfaces a UID while idle, the prompt fires:

A. **Once per session** — first ambient tap shows the hint; subsequent ones stay silent until the activity is recreated. (recommended — least noisy)
B. **Once per UID** — debounced by tag UID; same tag tapped again is silent, but a different tag fires the hint again.
C. **Always** — every ambient tap shows the hint until the user explicitly dismisses it once.

[Answer]: A

---

### Q-U9b-4 — UI-05 NDEF write-failure copy

Pick the new copy when `NfcRepository` returns a write failure:

A. **"Couldn't write to tag. Try again — hold the phone steady on the tag until done."** (recommended — actionable, no jargon)
B. **"Tag write failed. Make sure the tag is NFC-writable and not locked."** (more diagnostic)
C. **"Write failed. Tap Read tag to check tag state."** (terse, redirects to read)

[Answer]: A, simpler dont use - in error messages or any message

---

### Q-U9b-5 — "Other" + "Color Wheel" affordance pattern

Pick the visual pattern for items 5 (each picker shares the same affordance pattern):

A. **Outlined row with leading icon** — distinct surface, leading `Add` / `ColorLens` icon, body text in `MaterialTheme.colorScheme.primary`. (recommended)
B. **Italic divider + label, current shape** — keep the U8 close-out treatment; just bump font weight / color so it stops feeling passive.
C. **Trailing-arrow row** — chevron-right on the right-hand side; reads as "navigate into Other".

[Answer]: A

---

### Q-U9b-6 — UI-07 broader snackbar copy review — scope of the audit

For Item 8 (audit all snackbar strings):

A. **Audit-and-revise pass** — read every existing snackbar emission across `MainViewModel` / `SettingsViewModel` / use cases, lock the new copy in `frontend-components.md`, apply during code-gen. (recommended)
B. **Audit-only, no copy changes this round** — just inventory the strings and note rough quality issues; defer revisions to a follow-up round.
C. **Skip the audit, only fix the obvious wins** (UI-05's NDEF copy + the "Spoolman response could not be parsed" string from UI-08).

[Answer]: A, idk what it is explain then do whatever yiu reccomend

---

## 3. Out-of-band edits

If during FD Part 2 a Q-U9b-* answer needs revision, log it in `audit.md` under "Q-U9b-N revised" with the rationale. No silent edits to this plan.

## 4. Plan completion checklist

- [ ] §2 questions answered with `[Answer]:` tags or "Go go go!!" / "i trust you" blanket.
- [ ] FD Part 2 artefacts authored under `aidlc-docs/construction/u9b-ui-polish/functional-design/`:
  - [ ] `frontend-components.md` — `MoreDetailsExpander` Card-wrapped layout (no signature change); logo Row in `MainScreen`; IME-aware snackbar host pattern; "Other" / "Color Wheel" affordance pattern; UI-02 ambient-tap snackbar contract; UI-05 + UI-07 final copy strings.
  - [ ] `business-rules.md` — only if Q-U9b-3 picks B (once-per-UID) and the debouncer is extracted as a helper. Otherwise skipped — no new business rules.
  - [ ] No `domain-entities.md`, no `business-logic-model.md` — pure UX polish, no entity / state-flow changes.
- [ ] FD Part 2 standardised 2-option completion message presented; user picks "Continue to Next Stage" or "Request Changes".
- [ ] On approval, `aidlc-state.md` Current Stage flips to "U9b Code Gen Part 1 authoring".

## 5. Alternative path — SKIP FD entirely

If the user prefers, this whole plan can be retired and U9b can go direct to Code Gen Part 1. The six Q-U9b-* questions move into the Code Gen Part 1 plan as design choices. Justification: the 8 items are visually verifiable on-device, the only "logic" is UI-02's debounce (already trivial), and there's no entity / state surface that benefits from a separate FD approval gate.

To take this path, answer "skip FD" in the next round and I'll author the Code Gen Part 1 plan directly with the same six choices folded in.
