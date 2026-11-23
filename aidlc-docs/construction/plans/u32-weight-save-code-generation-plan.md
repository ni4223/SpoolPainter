# U32 — Code Generation Plan

**Unit**: U32 — Weight edits on an existing spool don't reach Spoolman (UI-74)
**Opened**: 2026-11-22 (planning only, Part 1). **Part 2 done 2026-11-22**
(plan approved; build matrix green). On-device S8-S9 moved to the combined install
gate after U28.
**Per-unit gate**: FD / NFR-R / NFR-D / Infra-D **SKIP**. Three bugfixes in the
existing save path, no new components or data model. Same convention as U20-U27.
**Source**: GitHub **#12** (daFreeMan, 2.4.1, Pixel 8, Spoolman 0.27.0), plus the
maintainer's own report 2026-11-22 (Measured weight over 1 kg says "Updated" but
the weight doesn't change).
**This plan is the single source of truth for U32.**

---

## §1 The three bugs (all verified by code read 2026-11-22)

### B1 — Save is greyed out when the filament has no Extruder Temp (#12)

- `canSave` requires `form.canSubmit` (`MainViewModel.kt:127-131`).
- `canSubmit` requires a 6-hex colour **and** non-null nozzle min/max
  (`MainUiState.kt:197-208`).
- `deriveSpoolmanTemps` returns null nozzle temps whenever
  `filament.settings_extruder_temp` is null (`FormMapping.kt:190-204`). Filaments
  created in Spoolman's web UI often leave it empty; filaments SpoolPainter
  creates always set it.
- So a spool on such a filament can't be saved at all, including a pure weight
  edit. Nothing explains why: `onSaveTapped` returns silently
  (`MainViewModel.kt:565`).
- The same gate is on 2.3.1 and 2.4.1.
- A filament with a null `color_hex` is blocked the same way. That contradicts
  UI-55's note "Color is NOT a hard save gate".
- The README screenshot isn't stale: the button reads "Update" when enabled and
  "Save to Spoolman" when disabled (`MainScreen.kt:335-343`).

### B2 — Measured with no empty-spool weight is dropped, and Save says "Updated"

- In Measured mode, remaining is `measured − emptySpoolWeightG`, committed only
  when the empty-spool weight is known (`MainViewModel.kt:1035-1046`). Otherwise
  the old remaining is kept (`:1042`).
- Save still reports success. `patchFilament` returns `Success(current)` without
  sending anything when the diff is empty (`SpoolmanRepository.kt:128-130`), and
  `SaveToSpoolmanUseCase` sets `dirty = true` on that Success (`:72`). So
  "Updated spool #N." (`MainViewModel.kt:1514`) shows even when nothing was
  written, and `NoChanges` (`SaveToSpoolmanUseCase.kt:116`) is effectively
  unreachable for an existing spool.

### B3 — Remaining above the spool's initial weight is silently capped (maintainer)

- The app sends `remaining_weight` (`SaveToSpoolmanUseCase.kt:88-101`).
- Spoolman 0.27 stores it as `used_weight = max(initial_weight − remaining, 0)`
  (Donkie/Spoolman v0.27.0 `spoolman/database/spool.py`, `update()`). So any
  remaining above `initial_weight` (usually 1000 g) is stored as 1000 g, and the
  PATCH still succeeds.
- Example: 800 g remaining before, weighed at 1,250 g gross with a 200 g empty
  spool. The app sends 1,050 g and Spoolman shows 1,000 g. Something changed,
  just not to what the user entered. Overfilled "1 kg" spools (1,020-1,050 g)
  are common.
- The app neither sends nor reads `initial_weight` today: `SpoolPatchBody`
  (`SpoolmanRequests.kt:77-82`) and `SpoolmanSpool` (`SpoolmanModels.kt:3-17`)
  have no such field.

## §2 Decisions

- **B3 — DECIDED 2026-11-22, option A.** When the computed remaining is above
  the spool's `initial_weight`, raise this **spool's** `initial_weight` to match
  in the same PATCH. `initial_weight` is a spool field (Spoolman: "the initial
  weight of the filament on the spool, in grams (net weight)",
  `api/v1/spool.py:111-115`). The filament's `weight` (the template for new
  spools) is not touched. Spoolman's `SpoolParameters` lists `initial_weight`
  (`:111`) before `remaining_weight` (`:123`), so one PATCH with both should
  apply the new initial weight first. That ordering is read from the source and
  must be confirmed on a real server (S9).
- **D1 — B1 fix: DECIDED 2026-11-22, option A (fill preset defaults).** When the
  filament has no Extruder Temp (or no Bed Temp), the form falls back to the
  material preset's temps, the same way the tag path already does
  (`FormMapping.kt:131`). It applies to both paths that skip it today:
  - picking an existing spool (`deriveSpoolmanTemps`, `FormMapping.kt:194-204`);
  - picking a filament, where only max falls back today (`FormMapping.kt:166-168`).

  **Accepted consequence (stated to the maintainer before the choice):** on Save,
  the preset temps differ from the filament's empty value, so the app writes them
  into that filament in Spoolman. Option B, not requiring temps, was declined.
  When Save is still disabled for another reason, the UI says why.
- **D1b — colour: DECIDED 2026-11-22, don't require colour for an existing
  spool.** Only spools whose Spoolman filament has no `color_hex` are affected.
  The form's white default (`MainUiState.kt:221`) applies to a new form only;
  picking an existing spool replaces it with Spoolman's value, which is null when
  unset (`FormMapping.kt:50`). With no colour, Save works and sends no
  `color_hex` (`MainUiState.kt:156` only sends valid 6-hex). **Write to tag still
  requires a colour** (`canWrite` keeps `canSubmit`). Declined: filling `FFFFFF`
  (writes white into, for example, a multi-colour filament); keeping Save blocked.
- **D2 — B2, the missing empty-spool weight: DECIDED 2026-11-22, the
  recommendation below.** The form already falls back
  filament `spool_weight` → spool `spool_weight` (`FormMapping.kt:45`), so B2 hits
  only when **both** are empty. The vendor's empty-spool weight is not modelled.
  Decided: add the vendor level as a last fallback (as Spoolman's own measure
  does). If none is known, show inline "Enter the empty spool weight to use
  Measured", and never report "Updated" when nothing was saved. (Declined: hide
  Measured until an empty-spool weight is set.)

## §3 Steps

- [x] **S0** — Prerequisite: Android SDK on this Mac (see U28 S0). Baseline suite
  green.
- [x] **S1** — B2 honesty: `dirty` only when an HTTP write actually happened
  (not on the empty-diff short-circuit), so an unchanged Save reports
  `NoChanges`.
- [x] **S2** — B1: apply D1 (preset-temp fallback in `deriveSpoolmanTemps` and the
  filament-pick path) and D1b. Add the "why Save is disabled" text.
- [x] **S3** — B2: apply D2 (empty-spool fallback chain, inline prompt when none).
- [x] **S4** — B3: add `initial_weight` to `SpoolmanSpool` and `SpoolPatchBody`;
  carry the prefilled initial weight in the form. On Save, when remaining exceeds
  it, send `initial_weight = remaining` alongside `remaining_weight`.
- [x] **S5** — Tests:
  - a spool whose filament has `settings_extruder_temp = null` loads the material
    preset's temps and can save (`canSave == true`);
  - picking such a filament fills both min and max from the preset;
  - a filament that has an Extruder Temp keeps today's behaviour;
  - D1b for a null `color_hex`;
  - Measured with no empty-spool weight uses the fallback chain, and with none
    reports no success;
  - an unchanged Save returns `NoChanges`, not "Updated";
  - remaining above `initial_weight` sends both fields; at or below sends
    `remaining_weight` only;
  - non-vacuity: revert S1 and S4 one at a time and confirm their tests fail.
- [x] **S6** — Correct UI-55's "Color is NOT a hard save gate" note.
- [x] **S7** — Build matrix: `compileDebugKotlin`, `testDebugUnitTest`,
  `assembleDebug`.
- [x] **S8** — **Moved to the combined install gate after U28** (maintainer,
  2026-11-22). Install gate on device: #12's case (a spool on a filament with no
  Extruder Temp) saves a weight; Measured with and without an empty-spool weight.
- [x] **S9** — **Combined install gate.** Against a real Spoolman 0.27 server: an over-1 kg remaining lands
  exactly (for example 1,050 g), the spool's initial weight is raised, and the
  filament's weight is unchanged.
- [x] **S10** — Docs: release notes, UI-74 state, `aidlc-state.md`.
- [x] **S11** — GitHub: replied on #12 2026-11-22 (short version, approved; issue left open until the reporter confirms on 2.4.2). Original plan: reply on #12. Until the fix ships, the
  workaround is to set Extruder Temp on the filament in Spoolman, or fill the
  nozzle temps in More details, so Save becomes "Update".

## §3a Implementation record (2026-11-22)

**Verified in Spoolman v0.27.0 source before coding** (public GitHub, read
2026-11-22):
- Vendors have `empty_spool_weight` (`api/v1/vendor.py:35`), so the D2 vendor
  level was kept.
- A `remaining_weight` PATCH is capped against the **spool's** `initial_weight`
  only; if that is null Spoolman returns 400 ("remaining_weight can only be used
  if initial_weight is set", `database/spool.py:739-742`). The earlier
  assumption of a filament-weight fallback was wrong, so S4 compares against the
  spool's value only.
- PATCH fields are applied in `SpoolParameters` order (`model_dump`), with
  `initial_weight` (`api/v1/spool.py:111`) before `remaining_weight` (`:123`).
  S9 still has to confirm it on a real server.

**Code**
- **S1**: `applyOverridesToFilamentOfSpool` returns `FilamentPatchResult(filament,
  wrote)`. It diffs against the **freshly fetched** filament, not the cache, and
  `wrote` is false when nothing differed. `SaveToSpoolmanUseCase` sets `dirty`
  only on a real write. A failed filament or spool PATCH now returns `Failed`
  instead of being logged and swallowed (addition stated to the maintainer
  before coding). The VM shows "No changes to save for spool #N." for
  `NoChanges` (was silent).
- **S2**: `deriveSpoolmanTemps` falls back to the material's temps when the
  filament has no Extruder or Bed Temp; `fromFilament` fills min as well as max.
  New `FormState.saveBlocker` (reason string or null): same rules as
  `canSubmit`, except an existing spool may have no colour (D1b). `canSave` uses
  it; `canWrite` keeps `canSubmit`. `saveDisabledReason` is shown under the
  disabled Save button (adds "Type the material/brand name." for Other).
- **S3**: `SpoolmanVendor.empty_spool_weight`; `fromSpoolman` falls back filament
  → spool → vendor. With a Measured entry and no empty weight, Save is blocked
  with "Enter the empty spool weight to use Measured." and the Measured field
  shows the same line.
- **S4**: `SpoolmanSpool.initial_weight`, `SpoolPatchBody.initial_weight`,
  `FormState.initialWeightG`. Save sends `initial_weight = remaining` when
  remaining is above it; the VM mirrors the raise after a successful Save.
- **S5 tests** (+19): `FormSaveBlockerTest` (new, 8), `FormMappingTest` (6, one
  replacing the old "null temps yields null temps", which pinned B1),
  `SaveToSpoolmanUseCaseTest` (the old "no edits returns Saved" pinned B2 and was
  rewritten; +5), repository `wrote` flag (1). **Non-vacuity**: S1 reverted →
  the NoChanges test fails; S4 reverted → the raise test fails.
- **S7**: `compileDebugKotlin testDebugUnitTest assembleDebug` green, **676** tests.

**Found, not fixed (outside the approved scope)**: since fixed as FN1 and FN2 (§5).
- `fromFilament` with a filament temp above the preset max (e.g. PLA at 230)
  gives min 230 > max 220, so Save of a new spool on that filament is blocked.
  It now says "Nozzle min is above max in More details." instead of nothing.
- A spool with a null `initial_weight` can't take a remaining edit (Spoolman
  400). Before U32 that showed "Updated"; now it shows the error.

## §4 Release

**Version: 2.4.2 / versionCode 117** (decided 2026-11-22). The next Play upload needs **versionCode 117 or higher**.

## §5 Addendum: the found-not-fixed items (maintainer: "fix those 2 too", 2026-11-22)

All three verified by code read (FN2 also in the Spoolman 0.22.1 and 0.27
source). None seen on a device yet.

- **FN1: filament pick gives min > max.** `fromFilament` takes min from the
  filament (`settings_extruder_temp`) and max from the preset. A PLA filament at
  230 gives 230/220, so the new-spool Save is blocked. The same happens for bed
  (for example 70 against PLA's 65). The filament-pick path doesn't send temps
  to Spoolman (`toExpanderOverrides`), so only the form and the tag are affected.
- **FN2: spool with a null `initial_weight`.** Spoolman `update()` rejects
  `remaining_weight` with 400 when `initial_weight` is null
  (`database/spool.py:227-230` in 0.22.1, `:739-741` in 0.27). Spoolman only
  fills `initial_weight` from `filament.weight` at create time, or on a filament
  change. A spool created while its filament had no weight keeps a null value.
  Display rule: remaining = (`initial_weight` ?: `filament.weight`) − `used_weight`
  (`api/v1/models.py`).
- **FN3 (new): an in-range temp is overwritten on every existing-spool Save.**
  `deriveSpoolmanTemps` shows the preset range when the filament's temp is
  inside it. For example, a PLA filament at 200 shows 190–220. On Save,
  `toExpanderOverrides` sends `extruderTemp = extruderMin` (190), and
  `sparseDiff` sees 190 ≠ 200. So a weight-only Save rewrites the filament to
  190 and reports "Updated". This was there before U32: U32 only added the
  null branch.

### Decisions

- **FN1: DECIDED 2026-11-22, recommended.** When min > max, max = min + 20
  (bed: + 10), the rule `deriveSpoolmanTemps` already uses for out-of-range
  temps. Otherwise keep the preset max.
- **FN2: DECIDED 2026-11-22, recommended.** When `initial_weight` is null and
  remaining is edited, send `initial_weight` in the same PATCH:
  filament weight (form `fullSpoolWeightG`) when known, else `used_weight` +
  new remaining (keeps used history); never below the new remaining.
- **FN3: DECIDED 2026-11-22, fix: send only edited temps.** On an existing
  spool, `extruderTemp`/`bedTemp` go into the filament PATCH only when the
  form's min differs from the prefill snapshot. No snapshot (form not loaded
  from a spool) keeps today's behaviour (send). **Accepted side effect:** a
  filament with no temp no longer gets the preset temps written on Save
  (changes D1's accepted consequence); the form and the tag still use them.

### Steps

- [x] **S12**: FN1 in `fromFilament`.
- [x] **S13**: FN2. A pure `FormState.initialWeightToSend()` used by
  `SaveToSpoolmanUseCase` 1b and by the Saved-path mirror in `MainViewModel`.
  Add `usedWeightG` to `FormState` from `spool.used_weight` if the decision needs it.
- [x] **S14**: FN3, if chosen.
- [x] **S15**: Tests: FN1 ext and bed; FN2 with and without filament weight,
  plus the B3 path unchanged; FN3 if chosen. Non-vacuity: revert each fix and
  confirm its tests fail.
- [x] **S16**: Build matrix (`compileDebugKotlin testDebugUnitTest assembleDebug`).
- [x] **S17**: Device check (needs approval to create test data in Spoolman).
- [x] **S18**: Docs: release notes, §5 record, `aidlc-state.md`, audit.

### §5a Implementation record (2026-11-22)

- **S12 (FN1)**: `FormMapping.fromFilament`: max = min + 20 / bed + 10 only
  when min > max; otherwise the preset max.
- **S13 (FN2)**: `FormState.initialWeightToSend()` (`MainUiState.kt`): null
  unless remaining is edited. Initial null → max(filament weight, else used +
  remaining, remaining). Initial known → remaining if above it (B3, unchanged).
  Used by `SaveToSpoolmanUseCase` 1b and by the Saved-path mirror in
  `MainViewModel` (existing spools only). New `FormState.usedWeightG` from
  `spool.used_weight`.
- **S14 (FN3)**: new `FormState.prefilledTempRanges`, set in `fromSpoolman` and
  refreshed after Saved. `toExpanderOverrides` (existing spool) sends
  `extruderTemp`/`bedTemp` only when the min differs from it.
- **S15**: +8 tests (`FormMappingTest` 4, `SaveToSpoolmanUseCaseTest` 4).
  **Non-vacuity**: FN1 reverted → 1 fails; FN2 reverted → 3 fail; FN3
  reverted → 1 fails.
- **S16**: `compileDebugKotlin testDebugUnitTest assembleDebug` green, **692**
  tests, 0 failures.
- **S17 (device, 2026-11-22, moto g stylus 2025 / Spoolman 0.22.1)**: test
  filament #116 "SpoolPainter test - no weight" (PLA, no weight, 200/50) and spool
  #138 (initial null, used 300) created with approval.
  - FN2 + FN3: #138 Remaining 650 → Update → "Updated spool #138."; one
    `PATCH spool/138` (49-byte body), **no filament PATCH**. Server: initial 950,
    used 300, remaining 650; #116 still 200/50.
  - Follow-up untouched Update: only a GET, no writes. The snackbar was hidden
    by the keyboard and not seen.
  - FN1: #115 temporarily set to 230/70 and picked in the filament picker →
    nozzle 230–250, bed 70–80, "Create spool" enabled, no blocker text. Not
    tapped. #115 restored to 190/40; no spool was created on it.
  - `stay_on_while_plugged_in` set to 3 for the check, back to 0 after.
