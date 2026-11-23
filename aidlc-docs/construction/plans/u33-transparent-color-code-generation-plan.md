# U33 — Code Generation Plan

**Unit**: U33 — Transparent Spoolman colours are read as the wrong colour (UI-75)
**Opened**: 2026-11-22 (planning only, Part 1). **Part 2 done 2026-11-22**
(plan approved; build matrix green). On-device check moved to the combined install
gate after U28.
**Per-unit gate**: FD / NFR-R / NFR-D / Infra-D **SKIP**. A bugfix in one codec
and the save diff; no new components or data model.
**Source**: a user review 2026-11-22: *"if you adjust the transparency of a color
(to less than 100%) for your filaments in Spoolman, the SpoolPainter app no longer
displays that color. Is this a bug, or am I doing something wrong?"*
**This plan is the single source of truth for U33.**

---

## §1 Root cause (verified by code read 2026-11-22)

- Spoolman 0.27 `color_hex`: *"Hexadecimal color code of the filament, e.g.
  FF0000 for red. Supports alpha channel at the end."*
  (Donkie/Spoolman v0.27.0 `spoolman/api/v1/filament.py:99-106`). So a
  transparent colour is 8 characters: `RRGGBBAA`.
- `ColorHexCodec.canonicalise` keeps the **last** 6 characters of anything longer
  than 6 (`domain/primitives/ColorHexCodec.kt:4-8`). That assumes `AARRGGBB`
  (alpha at the front). Two tests pin that assumption
  (`ColorHexCodecTest.kt:17`, `:41`: `"ffff0000"` → red).
- Result: `FF000080` (red, 50%) shows as `000080` (navy); `FFFFFF80` (white, 50%)
  shows as `FFFF80` (pale yellow). The colour isn't missing, it's wrong.

### Where it spreads

| Use | Site |
|---|---|
| Spool picked | `FormMapping.kt:50` |
| Filament picked | `FormMapping.kt:164` |
| Tag payload read | `FormMapping.kt:128` |
| Filament matching on create | `SpoolmanRepository.kt:602-607` (both sides canonicalised, so consistent) |
| Colour ranking | `ColorHexCodec.toRgb` via `SpoolMatchScorer` |

### It can overwrite the user's data

- On every Save of an existing spool the form sends its colour as a filament
  override when it is valid 6-hex (`MainUiState.kt:156`, then
  `SpoolmanRepository.kt:279`).
- `sparseDiff` compares it with the stored value (`SpoolmanRepository.kt:298`):
  `000080` ≠ `FF000080`, so the PATCH is sent. The user's colour is replaced with
  the wrong one and the transparency is lost.
- A tag write carries the wrong colour too, so the printer shows it wrong.

### Sources checked

All tag parsers emit 6-hex RGB with alpha dropped: Bambu (`BambuFormat.kt:120`),
Snapmaker (`SnapmakerFormat.kt:280`), Anycubic, Elegoo, Qidi, Creality. So no
input the app reads is actually `AARRGGBB`. Still to check: an OpenSpool tag
written by another app could carry an 8-character `color_hex`
(`OpenSpoolPayloadCodec.kt:37` reads it raw).

## §2 Decisions (all DECIDED 2026-11-22 by the maintainer, the recommendations)

- **D1 — transparency in the app: read and keep, don't model.** Show the RGB
  part, and never drop or change the stored alpha unless the user changes the
  colour. (Declined: modelling transparency in the form and colour picker; that
  stays with [[UI-55]].)
- **D2 — a new colour on a transparent filament keeps the existing alpha**: send
  the new `RRGGBB` + the old `AA` (`FF000080` → pick green → `00FF0080`).
  (Declined: the new colour is opaque.)
- **D3 — tags keep getting 6-hex RGB**, unchanged (`FF000080` → `FF0000`).

### Found 2026-11-22 (session 2), added to S1

- The spool and filament **picker rows** pass the raw `color_hex` to the swatch
  (`MainScreen.kt:865`, `FilamentPicker.kt:72`), and `parseSwatchColor` rejects
  anything not exactly 6 characters (`PickerRow.kt:84`). So a transparent
  filament shows a **grey** circle in the lists. That is the most literal match
  for the review's "no longer displays that color". The form's logo tint
  (`MainScreen.kt:661`) gets the canonicalised (wrong) colour instead.
- Filament ranking also gets the raw value (`MainViewModel.kt:1212`), but
  `SpoolMatchScorer` goes through `ColorHexCodec.toRgb`, so S1 fixes it.

## §3 Steps

- [x] **S0** — Prerequisite: Android SDK on this Mac (see U28 S0). Baseline suite
  green.
- [x] **S1** — `ColorHexCodec`: for 8 characters, take the **first** 6 (RGB) and
  expose the alpha separately. Update the two tests that pin the old rule.
  Route the picker swatches (§2 "Found") through the codec so 8-character
  colours show their RGB.
- [x] **S2** — Check the OpenSpool tag path (§1, "Sources checked") for 8-character
  input from other writers, and apply the same rule.
- [x] **S3** — Save: the colour override is sent only when the user changed the
  colour, comparing RGB to RGB, so an untouched transparent colour is never
  rewritten. Apply D2 when it is changed.
- [x] **S4** — Tests:
  - `FF000080` → red, alpha 80; `FFFFFF80` → white;
  - Save of an untouched spool with `FF000080` sends no `color_hex`;
  - changing the colour per D2;
  - filament matching still pairs `FF000080` with a red request;
  - non-vacuity: revert S3 and confirm the overwrite test fails.
- [x] **S5** — Build matrix: `compileDebugKotlin`, `testDebugUnitTest`,
  `assembleDebug`.
- [x] **S6** — On device against a real Spoolman: a 50% red filament shows red
  (form and both picker lists); saving a weight edit leaves `color_hex` as
  `FF000080`; picking green saves `00FF0080`; a tag write gets `FF0000`.
  **Moved to the combined install gate after U28** (maintainer, 2026-11-22).
- [x] **S7** — Docs: release notes, UI-75 state, `aidlc-state.md`.
- [ ] **S8** — Reply to the reviewer (the maintainer posts it; draft in §4).

## §3a Implementation record (2026-11-22)

- **S0**: Android SDK installed (Homebrew `android-commandlinetools`, platform 36,
  build-tools 35.0.0, platform-tools). Baseline **643** green.
- **S1**: `ColorHexCodec.canonicalise` keeps the first 6; new `alpha()` and
  `overrideFor(requested, stored)`. `PickerRow.parseSwatchColor` now goes through
  `ColorHexCodec.toRgb`, so the list swatches show `RRGGBBAA` colours.
- **S2**: no code needed. OpenSpool tag colours already pass through
  `canonicalise` (`FormMapping.kt:128`) and the scorer (`toRgb`); tag writes use
  the 6-hex form colour (`RawWriteUseCase.kt:77`), so D3 holds.
- **S3**: `SpoolmanRepository.applyOverridesIfNeeded` sends
  `ColorHexCodec.overrideFor(overrides.colorHex, filament.color_hex)` against the
  freshly fetched filament. It covers both callers (Save of an existing spool and
  a new spool on an existing filament).
- **S4**: tests in `ColorHexCodecTest` (2 rewritten, 5 added), `FormMappingTest`
  (3), `SpoolmanRepositoryCreateChainTest` (1, matcher), and the new
  `SpoolmanRepositoryTransparentColorTest` (5, including the real
  form → overrides Save path). Test fake fix: `FakeSpoolmanApi.patchFilament`
  now applies `color_hex`. **Non-vacuity**: with S3 reverted, 4 of the 5
  repository tests fail (the opaque-colour test passes either way, as it should).
- **S5**: `compileDebugKotlin testDebugUnitTest assembleDebug` green, **657**
  tests.

## §4 Draft reply to the review (not posted)

> Thanks! It's a bug, not you. Spoolman stores transparency at the end of the
> colour code and SpoolPainter reads it from the wrong end, so it shows a
> different colour. A fix is planned. Until then, set transparency back to 100% in
> Spoolman and avoid saving that spool in SpoolPainter, or the wrong colour can be
> written back.

## §5 Release

**Version: 2.4.2 / versionCode 117** (decided 2026-11-22). The next Play upload needs **versionCode 117 or higher**.
