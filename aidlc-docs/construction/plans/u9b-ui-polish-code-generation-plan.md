# U9b — Code Generation Plan (Part 1)

**Stage**: CONSTRUCTION → Per-Unit Loop → Code Generation Part 1 (U9b)
**Unit**: U9b — UI Polish (pure polish; editing deferred post-v2.0)
**Authored**: 2026-05-29
**Approval gate**: this plan must be approved before Code Gen Part 2 executes the checkboxes below.

**Per-unit gate**: FD SKIP / NFR-R SKIP / NFR-D SKIP / Infra-D SKIP. The 6 Q-U9b-* design choices that would have lived in FD are folded into §0 below.

**Inputs**:
- `aidlc-docs/inception/application-design/unit-of-work.md` §3-U9b (locked 8-item polish scope, "no functional behavior changes" carve-out)
- `aidlc-docs/ui-followups.md` UI-01, UI-02, UI-05, UI-07 (in scope) + UI-14, UI-15 (deferred post-v2.0)
- `aidlc-docs/inception/user-stories/stories.md` S-13.1 (re-validated post-polish)
- v1 reference reads: `git show main:app/src/main/java/com/spoolpainter/app/ui/screens/SpoolPainterScreen.kt`, `…/components/TemperatureCard.kt`

**Branch**: `v2`. Working tree before this plan: 1 commit ahead of `origin/v2` (U9 close-out `055e721`); doc-only deltas this session: `aidlc-state.md`, `audit.md`, `unit-of-work.md` §3-U9b, `ui-followups.md` (UI-14 + UI-15), retired FD plan, this plan.

**Test count target**: U9 closed at **362 / 362**. After U9b: **same or +1** (one optional new test if UI-02 debounce is extracted as a helper). Per-class breakdown in §11.

**Already-shipped reads (no work needed)**:
- ✅ Splash dependency: `androidx.core:core-splashscreen:1.0.1` already in `app/build.gradle.kts`.
- ✅ Splash theme: `Theme.SpoolPainter.Splash` already declared in `app/src/main/res/values/themes.xml` with `windowSplashScreenAnimatedIcon = @drawable/ic_splash_logo`.
- ✅ Manifest: `<activity android:theme="@style/Theme.SpoolPainter.Splash">` already wired.
- ✅ `installSplashScreen()` already called in `MainActivity.onCreate` line 34.
- ✅ Splash logo drawable `ic_splash_logo.xml` present (1024×1024 vector).
- ✅ `SpoolPainterLogo.kt` component already exists; ports v1's logo verbatim. **Just not rendered by `MainScreen`** — that's the only Item 1 work.

This shrinks Item 1 to **one Compose insertion** + a v1-parity check on the splash background colour.

**Out-of-scope guards** (re-stated):
- ❌ Editing a paired spool (UI-13 / UI-14 / UI-15) → post-v2.0
- ❌ Material/brand identity edits → post-v2.0 (bundled with UI-14)
- ❌ NFR-5 release-build log stripping → U10
- ❌ APK size / `material-icons-extended` R8 minify → U10 (`U10-Δ-1`)
- ❌ JDK 17 portability → U10
- ❌ Legacy `sortOrder` JSON key migration → U10 (`U10-Δ-2`)
- ❌ U9b milestone install gate (Q-T2=B → covered by U10 manual matrix) — manual verification covered organically through install-time iteration as in U7 / U8 / U9

---

## §0 — Q-U9b-* design choices (folded in from skipped FD)

Six tight choices. Each has a recommended option flagged. Answer with `[Answer]: <letter>` after each block, or "Go go go!!" / "i trust you" for blanket recommendations. **Plan checkboxes in §2..§9 reference these answers — they must be locked before Code Gen Part 2 executes.**

---

### Q-U9b-1 — Splash screen drawable source

**Already shipped** (per "Already-shipped reads" above): `ic_splash_logo.xml` is a 1024×1024 vector drawable in v2's `app/src/main/res/drawable/`, and `git diff main..v2 -- app/src/main/res/drawable/spool_logo.xml` shows v2 carries v1's vector verbatim. Splash already uses `ic_splash_logo`. **The question is now narrower** — should U9b touch the splash drawable at all?

A. **No-op** — splash is already correct; main-screen logo is the only Item 1 work. (recommended)
B. Re-tune the splash **background colour** for light/dark theme awareness (currently hard-coded `@color/splash_bg`).
C. Swap to a different drawable (re-design).

[Answer]: A (locked 2026-05-29 via "all other good")

---

### Q-U9b-2 — IME-aware snackbar host wiring

For Item 4 (snackbar visibility under keyboard, applied to both `MainScreen` and `SettingsScreen`):

A. **`imePadding()` on `Scaffold` content modifier** — content shifts when IME shows; snackbar (child of Scaffold) shifts with it. Applied symmetrically to both screens. (recommended — simple, single-modifier change)
B. **`imePadding()` on the `SnackbarHost` only** — surgical; content layout doesn't shift, snackbar floats above IME.
C. **Dismiss IME on submit** — focus clear → keyboard hides → snackbar visible in stable space. Independent of `imePadding`.
D. **Combo of B + C** — IME-aware host AND dismiss IME on submit.

[Answer]: A (locked 2026-05-29 via "all other good")

---

### Q-U9b-3 — UI-02 passive-tap prompt — debounce semantics

When an ambient (un-prompted) NFC tap surfaces a UID while idle, the prompt fires:

A. **Once per session** — first ambient tap shows the hint; subsequent ones stay silent until activity recreation. Inline `Boolean` flag in `MainViewModel`. (recommended — least noisy; simplest implementation)
B. **Once per UID** — debounced by tag UID; same tag tapped again is silent, different tag fires the hint again. `Set<String>` in `MainViewModel`.
C. **Always** — every ambient tap shows the hint until user explicitly dismisses once.

[Answer]: A (locked 2026-05-29 via "all other good")

---

### Q-U9b-4 — UI-05 NDEF write-failure copy — LOCKED

**Locked**: `"Couldn't write to tag. Try again."`

Per user direction 2026-05-29 ("just keep it till try again"). No em-dash / en-dash / hyphen as a separator. Short, actionable, no jargon. The §7 broader-copy audit applies the same dash-as-separator constraint symmetrically to every revised string.

[Answer]: locked — no further input needed.

---

### Q-U9b-5 — "Other" + "Color Wheel" affordance pattern

Pick the visual pattern for Item 5 — applied symmetrically to MaterialPicker, BrandPicker, and ColorPicker:

A. **Outlined row with leading icon** — distinct surface, leading `Add` / `ColorLens` icon, body text in `MaterialTheme.colorScheme.primary`. (recommended)
B. **Italic divider + label, current shape** — keep the U8 close-out treatment; just bump font weight / colour.
C. **Trailing-arrow row** — chevron-right on the right-hand side; reads as "navigate into Other".

[Answer]: A (locked 2026-05-29 via "all other good")

---

### Q-U9b-6 — UI-07 broader snackbar copy review — scope

For Item 8 (audit all snackbar strings):

A. **Audit-and-revise pass** — enumerate every existing `_effects.trySend(UiEffect.ShowSnackbar(...))` call site in `MainViewModel` / `SettingsViewModel` / use cases (~14 string literals based on grep), lock the new copy in §7 below, apply during code-gen. (recommended)
B. **Audit-only, no copy changes this round** — just inventory + grade quality; defer revisions to follow-up.
C. **Skip the audit, only fix UI-05's NDEF copy** + the "Spoolman response could not be parsed" string.

[Answer]: A (locked 2026-05-29 via "all other good")

---

## §1 — Build dependencies

- [ ] 1.1 No new third-party dependencies. `androidx.core:core-splashscreen:1.0.1` already on classpath. No `libs.versions.toml` change. No `app/build.gradle.kts` change.
- [ ] 1.2 No new permissions.

---

## §2 — Item 1: Branding restore (main-screen logo)

### 2.1 Modify `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainScreen.kt`

- [ ] 2.1.1 In `MainTopBar` (line 206-218), add the logo to the `title` slot. Today `title = { Text("SpoolPainter") }`. Replace with the v1-style logo: `title = { SpoolPainterLogo(color = MaterialTheme.colorScheme.primary, modifier = Modifier.heightIn(max = 48.dp)) }`. The 48dp cap fits the TopAppBar's standard height — `SpoolPainterLogo`'s 125dp Image will scale-down per `ContentScale.Fit`.
- [ ] 2.1.2 Add import `androidx.compose.foundation.layout.heightIn` and `com.spoolpainter.app.ui.components.SpoolPainterLogo`.
- [ ] 2.1.3 The Settings IconButton in `actions` and the ThemeCycleIconButton (already present from U9) stay unchanged. The TopAppBar gains: `[logo title] ... [theme icon] [settings icon]`.

### 2.2 Splash background colour (Q-U9b-1 dependent)

- [ ] 2.2.1 If Q-U9b-1=A: **no-op**.
- [ ] 2.2.2 If Q-U9b-1=B: edit `app/src/main/res/values/themes.xml` (and `values-night/themes.xml` if present) to make `windowSplashScreenBackground` resolve to the theme `surface` colour rather than a hard-coded `@color/splash_bg`. Or define `@color/splash_bg` differently per `values` / `values-night`.

### 2.3 Optional shrink — drop "Spool Painter" Text below the logo

- [ ] 2.3.1 `SpoolPainterLogo` body renders both an `Image` and a `Text("Spool Painter")` (lines 51-59). At TopAppBar size the text will likely clip / overflow. Add a `showText: Boolean = true` parameter to `SpoolPainterLogo`; pass `showText = false` from `MainTopBar`. **No call-site breakage** — default preserves v1 usage.

---

## §3 — Item 2: v1 main-UI parity audit + UI-01

### 3.1 Audit checklist (visual diff vs v1's `SpoolPainterScreen` + `MainScreenContent`)

The main differences vs v1 (from this session's `git show main:` reads — most are **already correctly v2-shaped** and not regressions):

- [ ] 3.1.1 **Logo placement** — covered by §2 (Item 1).
- [ ] 3.1.2 **Form Card wrapper** — v1 wraps the entire form (Spoolman dropdown + FilamentForm + TempPanel + InstructionFooter) in one outer `Card(shape = RoundedCornerShape(20.dp), elevation = 5.dp)` with `absolutePadding(5,15,5,5)`. v2's `MainScreen` body (lines 100-180) is a flat `Column` — no outer Card. **Decision needed (Q-U9b-7 below)**.
- [ ] 3.1.3 **Dropdown styling drift (UI-01)** — `SpoolmanDropdown` in `MainScreen.kt:208` is a raw `OutlinedTextField` + `ExposedDropdownMenuBox`. The form's `MaterialPicker` / `BrandPicker` / `ColorPicker` use the same `OutlinedTextField` shape but with `RoundedCornerShape(20.dp)` on the form fields. Bring the dropdown's `OutlinedTextField` to the same shape: `shape = RoundedCornerShape(20.dp)`, `colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outline)`. Same idiom used in `FilamentForm.VariantField`.
- [ ] 3.1.4 **Spacing / vertical-arrangement** — v1 uses `Arrangement.spacedBy(8.dp)` on the outer Column and `Arrangement.spacedBy(16.dp)` on the form's Column. v2 uses `12.dp` outer (`MainScreen.kt:108`) and `16.dp` form (`FilamentForm.kt:83`). Form-internal spacing already matches v1; outer-Column spacing 12 vs 8 is a 4dp delta — leave as-is unless install-time review flags it.
- [ ] 3.1.5 **Button shape** — v1 Read/Write buttons use `RoundedCornerShape(20.dp)` and `height(40.dp / 45.dp)`. v2's `FilamentForm.Save & Write` button already uses `RoundedCornerShape(20.dp)` + `height(45.dp)` (lines 158, 155). v2's `ReadFab` is a Material 3 FAB (different idiom by design — v1 had no FAB). No change.
- [ ] 3.1.6 **InstructionFooter copy** — v1 says "• Configure your filament settings above\n• Tap 'Write to NFC'\n• Use 'Read NFC Tag' to load existing settings". v2 says "• Tap a tag to read its filament settings\n• Or fill the form, then tap Save & Write to write a fresh tag\n• Press Read tag to scan a tag without filling the form first". **v2's copy is more accurate** to the v2 NFC flow; do NOT revert. If the copy review (Q-U9b-6 / §7) revises it, that captures it; otherwise leave.

### Q-U9b-7 — Outer form Card

Should `MainScreen`'s body Column (lines 100-180) be wrapped in an outer `Card(shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(5.dp))` to match v1's "everything sits on one elevated surface" idiom?

A. **No outer Card** — keep v2's current flat-Column layout. Cleaner with the now-elevated TempPanel + MoreDetailsExpander cards (Item 3); two levels of elevation read as visual noise. (recommended — pairs with the Item 3 decision; avoids "card inside card inside card")
B. **Add outer Card** — exact v1 parity. May read as over-bordered with the inner elevated cards.

[Answer]: A (locked 2026-05-29 via "all other good")

### 3.2 Modify `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainScreen.kt`

- [ ] 3.2.1 If Q-U9b-7=A: no outer-Card change.
- [ ] 3.2.2 If Q-U9b-7=B: wrap the body Column (lines 101-180) inside `Card(modifier = Modifier.fillMaxSize().padding(padding), shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(5.dp))` with the inner Column dropping its `.padding(padding)` (Card now owns it).
- [ ] 3.2.3 SpoolmanDropdown styling polish (UI-01): if `SpoolmanDropdown` is a private composable inside `MainScreen.kt`, edit its `OutlinedTextField` directly. Match `shape = RoundedCornerShape(20.dp)` + `colors` from `FilamentForm.VariantField` (FilamentForm.kt:198-201).

---

## §4 — Item 3: Temp + More-Details visual fix (both elevated cards)

### 4.1 Modify `app/src/main/java/com/spoolpainter/app/ui/components/MoreDetailsExpander.kt`

- [ ] 4.1.1 Add imports: `androidx.compose.material3.Card`, `androidx.compose.material3.CardDefaults`, `androidx.compose.foundation.shape.RoundedCornerShape`.
- [ ] 4.1.2 Wrap the existing top-level `Column` (line 53) in a `Card`. Final shape:
  ```kotlin
  Card(
      modifier = modifier
          .fillMaxWidth()
          .testTag("more-details"),
      shape = RoundedCornerShape(16.dp),
      elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
  ) {
      Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
          // existing header Row + AnimatedVisibility(expanded) { ... } body
      }
  }
  ```
- [ ] 4.1.3 Move the existing `testTag("more-details")` from the inner Column to the Card. Header row's `testTag("more-details-header")` stays where it is.
- [ ] 4.1.4 The header `Row` keeps its `clickable(enabled = enabled, onClick = onToggle)` modifier — the whole header row stays the toggle target.
- [ ] 4.1.5 **No signature change** — `MoreDetailsExpander` parameters and `FilamentForm` call site stay byte-identical to U8.
- [ ] 4.1.6 No rename — header still reads "Filament metadata" (the rename to "Spool details" was tied to the editing carve-out, which is deferred).

### 4.2 No `TempPanel.kt` change

- [ ] 4.2.1 `TempPanel` already uses the target shape/elevation/padding. Reference, not target.

---

## §5 — Item 4: Snackbar visibility under keyboard

### 5.1 Modify `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainScreen.kt`

- [ ] 5.1.1 Per Q-U9b-2:
  - If A: add `Modifier.imePadding()` to the inner Column at line 102 (or to the `Scaffold` content's outer `Box`/`Column`). Add import `androidx.compose.foundation.layout.imePadding`.
  - If B: add `Modifier.imePadding()` to the `SnackbarHost` only — `snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.imePadding()) }`.
  - If C: add `LocalSoftwareKeyboardController.current?.hide()` in the `viewModel::onWriteTapped` and `viewModel::onSettingsTapped` paths (or a global `LaunchedEffect(activeFlow)` that hides on transition into a non-Idle state).
  - If D: B + C combined.

### 5.2 Modify `app/src/main/java/com/spoolpainter/app/ui/screens/settings/SettingsScreen.kt`

- [ ] 5.2.1 Mirror §5.1's pick on the Settings Scaffold. Settings has `Save` and `Test connection` (well, `Test connection` was removed at U9-Δ-1; only `Save` remains) — IME visibility matters for the URL field.

---

## §6 — Item 5: "Other" + "Color Wheel" affordances

### 6.1 Locate current affordance render sites

Read targets (the "Other" row in MaterialPicker / BrandPicker, and the "Color Wheel" row in ColorPicker):

- [ ] 6.1.1 `app/src/main/java/com/spoolpainter/app/ui/components/MaterialPicker.kt` — find the `Other` row in the dropdown's body.
- [ ] 6.1.2 `app/src/main/java/com/spoolpainter/app/ui/components/BrandPicker.kt` — same shape, different list.
- [ ] 6.1.3 `app/src/main/java/com/spoolpainter/app/ui/components/ColorPicker.kt` — find the `Color Wheel` affordance.

### 6.2 Apply the locked pattern (Q-U9b-5)

- [ ] 6.2.1 If Q-U9b-5=A — outlined row with leading icon. Build a small private `@Composable AffordanceRow(icon: ImageVector, label: String, onClick: () -> Unit)` helper inside each picker (or extract to `ui/components/AffordanceRow.kt` if reused 3×). MaterialPicker/BrandPicker use `Icons.Outlined.Add`; ColorPicker uses `Icons.Outlined.ColorLens` (or `Palette` if `ColorLens` not on classpath).
- [ ] 6.2.2 If Q-U9b-5=B — bump font weight from `Normal` → `SemiBold` and colour from `onSurfaceVariant` → `primary`. No structural change.
- [ ] 6.2.3 If Q-U9b-5=C — add `Icons.AutoMirrored.Outlined.KeyboardArrowRight` to the trailing edge of each row.
- [ ] 6.2.4 Verify `material-icons-extended` covers the chosen icons; if not, fall back to `material-icons-core` (`Add`, `Palette` are core).

---

## §7 — Items 7 + 8: UI-05 NDEF copy + UI-07 broader snackbar audit

### 7.1 UI-05 NDEF write-failure copy (Q-U9b-4 LOCKED)

- [ ] 7.1.1 Locate the NDEF write-failure emission site. Likely candidates from session grep: `MainViewModel.kt:694` (`"Verify failed. Tap Save to retry."`) and `MainViewModel.kt:707` (`UiEffect.ShowSnackbar(msg)` where `msg` is built upstream — trace to `humanReadable(result.outcome)` or similar).
- [ ] 7.1.2 Replace with the locked Q-U9b-4 string `"Couldn't write to tag. Try again."`. If the message comes from a `humanReadable(...)` mapper, edit the mapper's `Outcome.Failed` / `Outcome.WriteFailure` branch to the new string.

### 7.1.5 Copy style rule (applies to §7.1 + §7.2 + §7.3 + any future snackbar copy)

- [ ] 7.1.5.1 **No em-dash (`—`), en-dash (`–`), or hyphen (`-`) as a sentence separator** in any user-facing snackbar string. Use periods. Per user direction 2026-05-29 (anchor for Q-U9b-4 lock + §7.2 audit constraint). Hyphens inside compound words (e.g. `Save & Write`, `re-pair`) remain fine.

### 7.2 UI-07 broader snackbar copy review (Q-U9b-6)

- [ ] 7.2.1 If Q-U9b-6=A — full audit pass. Inventory all `_effects.trySend(UiEffect.ShowSnackbar(...))` call sites:
  - **Locked-keep** (already user-friendly): "No tag tapped. Try again." (`MainViewModel.kt:288, 717`); "Saved with one tag" (`:741`); "Both tags paired" (`:754`); "Tap the vendor tag again to capture its UID." (`:377`).
  - **Review** (audit + maybe revise): "Vendor tag. Pick a spool first." (`:765`); "Verify failed. Tap Save to retry." (`:694`); the `humanReadable(result.outcome)` outputs (`:661, :698`); the `result.reason` raw bubble-up (`:665`); the `msg` constructed at `:707`.
  - For each "Review" item, propose a new string in §7.3 below.
- [ ] 7.2.2 If Q-U9b-6=B — inventory only; produce a review note in `aidlc-docs/construction/u9b-ui-polish/code/u9b-summary.md` listing each string verbatim with a quality grade. No code changes for copy.
- [ ] 7.2.3 If Q-U9b-6=C — only fix UI-05 (§7.1) + the "Spoolman response could not be parsed" string from the UI-08 entry.

### 7.3 Locked copy table (filled in during Code Gen Part 2 if Q-U9b-6=A; placeholder during Part 1)

| Site | Current | New |
|---|---|---|
| `MainViewModel.kt:707` (write failure) | *(traced from `humanReadable`/`msg`)* | *(per Q-U9b-4)* |
| ... | ... | ... |

---

## §8 — Item 6: UI-02 passive-tap prompt

### 8.1 Modify `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainViewModel.kt`

- [ ] 8.1.1 Per Q-U9b-3:
  - If A (once-per-session): add `private var ambientTapHintShown: Boolean = false` at the ViewModel scope. In the `nfc.lastSeenTag.collect { tag -> ... }` collector (line 221), when `tag != null` AND `state.activeFlow == ActiveFlow.Idle` AND `state.spoolman.selectedSpoolId == null` AND `!ambientTapHintShown` — fire `_effects.trySend(UiEffect.ShowSnackbar("Tag detected. Press Read tag to load."))` and set `ambientTapHintShown = true`.
  - If B (once-per-UID): replace the boolean with `private val seenAmbientUids: MutableSet<String> = mutableSetOf()`. Same conditions; gate on `tag.uid.hex !in seenAmbientUids`; on fire, add to set.
  - If C (always): no gating — every ambient tap fires the snackbar.
- [ ] 8.1.2 If Q-U9b-3=B and the helper is extracted, create `AmbientTagDebouncer` in `domain/repository/spoolman/` or `ui/screens/main/`; default location: inline.

### 8.2 Optional new test

- [ ] 8.2.1 If Q-U9b-3=B and the debouncer is extracted: `AmbientTagDebouncerTest` — first UID fires, second same UID silent, different UID fires, set is bounded (?). Otherwise no new test.

---

## §9 — Cross-cutting hygiene

- [ ] 9.1 No file deletions.
- [ ] 9.2 Run `./gradlew assembleDebug` — APK growth target: ≤ +0.5 MB vs U9's 65 MB baseline. Item 5's icon imports + the splash drawable are already on classpath; no new heavy deps.
- [ ] 9.3 Run `./gradlew testDebugUnitTest` — must end at `362 / 362` (or `363 / 363` if §8.2.1 ships).
- [ ] 9.4 No `LICENSE` or `NOTICE` updates needed (no new third-party deps).
- [ ] 9.5 Brownfield invariant: no `*_modified.kt` / `*_new.kt` / `*.bak` files left behind.

---

## §10 — Brownfield invariants

- [ ] 10.1 No file renames except those explicitly enumerated above (no `MoreDetailsExpander` rename — the editing-carve-out's "Spool details" rename is deferred with UI-14).
- [ ] 10.2 No removal of `testTag` strings — existing tests grep for them.
- [ ] 10.3 No production reference to "edit a paired spool" / `patchSpool` / `SpoolPatch` / Archive — those land with UI-14 / UI-15 post-v2.0.
- [ ] 10.4 Public interfaces declared by prior units (U1..U9) stay byte-identical.

---

## §11 — Test count breakdown (target: 362 / 362, +0 or +1)

- U1..U9 baseline: 362 / 362.
- §8.2.1 (`AmbientTagDebouncerTest`): +1 if Q-U9b-3=B and helper is extracted; else 0.
- §6 visual changes: 0 (pure visual; render-stability tests cover via existing `testTag`s).
- §4 Card wrapper around MoreDetailsExpander: 0 (no signature change; existing `MoreDetailsExpanderTest` cases continue to assert against the same `testTag("more-details")` root).
- §5 IME-aware host: 0 (Compose UI test would be `instrumented`, out of scope per Q-T3 unit-test focus).
- §3 Outer-Card decision: 0.
- §7 copy changes: 0 if grep-based assertions don't pin specific strings; +0..3 if any `MainViewModelTest` cases assert string equality on revised copy. Code Gen Part 2 will surface if test edits are needed.

**Net: 362 / 362 (Q-U9b-3 ∈ {A, C}) or 363 / 363 (Q-U9b-3 = B with extracted helper). Plus any Q-U9b-6=A revision-test churn.**

---

## §12 — Commit & close-out

- [ ] 12.1 Per [[feedback_aidlc_unit_close_out_commit]]: single git commit at the end of U9b's per-unit loop, captures (a) all source/test/config changes, (b) all AIDLC artefacts under `aidlc-docs/construction/u9b-ui-polish/code/u9b-summary.md` + the two plans under `aidlc-docs/construction/plans/`, (c) the `aidlc-state.md` + `audit.md` updates marking U9b DONE.
- [ ] 12.2 Commit message template:
  ```
  feat(v2): close out U9b — UI polish + branding restore

  - Logo on MainScreen TopAppBar (v1 SpoolPainterLogo restored)
  - MoreDetailsExpander wrapped in elevated Card matching TempPanel
  - IME-aware snackbar host on Main + Settings (Q-U9b-2)
  - "Other" / "Color Wheel" affordance polish (Q-U9b-5)
  - UI-02 passive-tap prompt (Q-U9b-3)
  - UI-05 NDEF write-failure copy + UI-07 broader snackbar audit (Q-U9b-4 / -6)
  - UI-01 Spoolman dropdown styling aligned with form fields
  - Tests N / N; APK X MB

  Editing scope (UI-13 / UI-14 / UI-15 — filament-metadata edit, remaining
  weight, archive) explicitly DEFERRED post-v2.0 release. Logged in
  ui-followups.md.
  ```
- [ ] 12.3 No `git push` — close-out commit lands on `v2`, push remains user-owned.

---

## §13 — Plan completion checklist

- [ ] §0 Q-U9b-1..6 + Q-U9b-7 answered with `[Answer]:` tags or "Go go go!!" / "i trust you" blanket.
- [ ] User picks "Continue to Code Gen Part 2" or "Request Changes" via the standardised 2-option gate.
- [ ] On approval, `aidlc-state.md` Current Stage flips to "U9b Code Gen Part 2 EXECUTING".
