# U26 — Code Generation Plan

**Unit**: U26 — Typed / picked brand name is written verbatim (UI-63)
**Opened / closed**: 2026-08-27
**Per-unit gate**: FD / NFR-R / NFR-D / Infra-D **SKIP** — one bugfix across two
call sites, design already locked and approved 2026-08-24 (same convention as
U20-U25). No new components, no new data model, no infrastructure.
**Design**: locked in `ui-followups.md` → UI-63 "DESIGN LOCKED 2026-08-24".
This plan implements that design; it does not re-open it.

---

## §1 Scope

Two changes that must land together, because either alone leaves the app
inconsistent with itself.

### F1 — `resolveBrandName` stops canonicalising (write path)

`MainViewModel.resolveBrandName` rewrote the brand to whatever entry in
`brands.value` matched case-insensitively, so a hand-typed "Tecbears" was written
to the tag as the preset spelling "TECBEARS" — and the form went on displaying
"Tecbears". Now a pass-through; only whitespace is trimmed.

Reached from all five call sites (Save, create-and-pair, raw write, vendor
UID-only pair, and the repair path), so all five are fixed by the one change.

### F2 — `mergeBrands` stops letting presets shadow the user's vendors

`(presets + vendors).distinctBy { lowercase() }` kept the **first** occurrence and
presets came first, so a vendor the user had created as "Tecbears" was dropped
from the dropdown in favour of the preset "TECBEARS". Their own spelling was not
merely deprioritised, it was **unpickable**.

Now: server vendors verbatim first, then only those presets with no
case-insensitive server match.

## §2 Verified, not assumed

- **The old justification was false.** `resolveBrandName`'s comment claimed
  "Spoolman dedups the vendor row case-insensitively", which is what made
  canonicalising the *derived filament name* look like the lesser evil. Checked
  against Spoolman's schema (`spoolman/database/models.py`, 2026-08-27):
  `name: Mapped[str] = mapped_column(String(64))` — **no `unique=True`, no
  `index=True`, no `UniqueConstraint`, no collation.** Spoolman dedupes nothing.
  The entry's own "unverified assumption to check before building" is therefore
  **falsified**, and design rule 1 (show case-variant server rows separately) is
  load-bearing rather than hypothetical: a user really can hold both "Tecbears"
  and "TECBEARS" as two rows with two ids.
- **The tests would not have caught either half.** Both behaviour-locking tests
  asserted the *old* intent (`preset spelling wins on collision`, and a
  `distinctBy { lowercase() }` invariant). Confirmed empirically by reverting only
  the two production files and re-running: **8 of the 10 new tests fail**, and the
  2 that pass are the ones deliberately pinning unchanged behaviour.

## §3 Decisions carried from the locked design

- **D1** — Typed input wins outright. Maintainer's rule: "if someone is willing to
  walk past the dropdown and write in Other, then whatever they write wins." So
  the canonicalisation is **deleted**, not redirected at Spoolman vendors.
- **D2** — Case-variant *server* rows are both shown. They are separate records
  with separate ids and `resolveOrCreateVendor` picks among them with an arbitrary
  `firstOrNull`; collapsing them would silently decide which vendor id a filament
  attaches to.
- **D3** — Rows identical once trimmed still collapse (UI-62 stands). Those render
  the same, so choosing between them would be a coin flip with no visible cue.
  This is the line between UI-62 and D2: **distinguishable on screen or not.**
- **D4** — Whitespace is still normalised. It is invisible, and an untrimmed brand
  renders as a double space inside `derivedName`.
- **D5** — Vendor *records* are never renamed. `resolveOrCreateVendor` keeps its
  `ignoreCase = true` match-and-reuse, unchanged, because renaming rewrites a row
  every other filament of that brand points at.
- **D6** — Preset spellings are not changed. `TECBEARS` / `JAYO` / `GEEETECH` are
  those vendors' real styling; the bug was the substitution, not the spellings.

## §4 Steps

- [x] **S1** — `resolveBrandName` → pass-through + trim. KDoc records both reasons
      it is deleted rather than narrowed (false justification; dropdown now yields
      exact server strings).
- [x] **S2** — `mergeBrands` → server-verbatim-wins with the two-key dedupe. KDoc
      states the Spoolman schema finding and the distinguishable-or-not line.
- [x] **S3** — Class KDoc + `MainViewModel.brands` doc corrected; the brand
      invariant weakened from `distinctBy { lowercase() }` to `distinct()` with the
      reason stated inline.
- [x] **S4** — Tests: 2 old-intent tests replaced by 6 in
      `MaterialBrandRepositoryTest`; 5 added to `MainViewModelRawWriteTest`
      reproducing the reporter's exact RawNoUrl path.
- [x] **S5** — Validated the tests catch the old behaviour by reverting the two
      production files and re-running (§2).

## §5 Result

Tests **615 → 624** (Δ +9 net: +7 brand merge, +5 raw write, −2 replaced,
−1 replaced-and-split). 0 failures. No production file outside the two named is
touched.

## §6 Behaviour changes a user can see

1. The Brand dropdown now shows **your** vendor spelling, not the built-in one,
   whenever both exist. If you created "Tecbears" on your server, "TECBEARS" no
   longer appears.
2. If your server holds two case-variant vendor rows, **both** now appear. That is
   deliberate (D2) and is the one change that adds a row rather than removing one.
3. A tag round-trip preserves case. Reading a tag another tool wrote as "Jayo" and
   writing it back no longer converts it to "JAYO".

## §7 Risk left open

- **R1 — old filament records keep their old names.** A user whose Spoolman
  already holds `TECBEARS PLA Grau` will not see it renamed; only newly created
  records pick up the corrected spelling. The app must **not** bulk-rename. Say so
  in the release notes rather than letting people wonder why old entries differ.
- **R2 — `resolveMaterialName` still canonicalises**, against the material
  presets, and is untouched here. Deliberate: the locked design is brand-only, and
  material presets are format names (PLA, PETG) where a canonical spelling is far
  more defensible than a vendor's *styling*. Logged as its own follow-up rather
  than folded in silently. The `mergeMaterials` preset-wins asymmetry noted during
  U23 is the same latent item.
- ~~**R3 — not yet device-verified.**~~ **CLOSED — install gate PASSED 2026-08-27**
  on moto g stylus 2025 / Android 16, against the maintainer's live Spoolman
  (22 vendors, 113 spools). See §8.

## §8 Install gate — PASSED 2026-08-27

moto g stylus 2025 / Android 16, debug build, live Spoolman over an OctoEverywhere
tunnel (22 vendors, 113 spools).

### F2 — dropdown (the half a user sees first)

Predicted the expected diff from the real vendor list *before* looking at the
device, then confirmed each row on screen. **Exactly 5 rows change, count holds at
23**, and every change is a server spelling replacing a preset:

| Was (preset) | Now (server) | Vendor id |
|---|---|---|
| `3DHoJor` | `3DHojor` | 3 |
| `Elegoo` | `ELEGOO` | 11 |
| `eSUN` | `eSun` | 10 |
| `JAYO` | `Jayo` | 7 |
| `SUNLU` | `Sunlu` | 15 |

**UI-62 regression guard held**: vendor id=1 is stored as `'TECBEARS '` *with a
trailing space*, and the dropdown renders `TECBEARS` **exactly once** — trimmed,
not duplicated, and not beaten by the preset.

`GEEETECH`, `Generic`, `Bambu Lab`, `Kingroon`, `Polymaker`, `Anycubic`,
`Snapmaker` unchanged (server string already equals the preset). No crashes.

### F1 — typed brand written verbatim

Reproduced the reporter's exact path rather than an approximation: cleared the
Spoolman URL so `writeMode` became `RawNoUrl`, confirmed by the button rendering
**"Write to NFC"** — the same label visible in their screenshot. With no server
vendors the dropdown correctly fell back to preset spellings (`3DHoJor`, `eSUN`,
`Elegoo`), which is precisely the reporter's situation. Picked Brand → Other, typed
lowercase `tecbears` against the preset `TECBEARS`, wrote to a blank NTAG.
**Maintainer confirmed on screen: written all lowercase.** Under the old code this
was the case that produced `TECBEARS`.

The Spoolman URL was backed up byte-exact before clearing and restored afterwards
(verified identical string, then 4× HTTP 200 and 113 spools re-collected). No
Spoolman records were created, modified or deleted at any point.

### Incidental finding — logged to [[UI-53]], not fixed here

Android 16 logged `ActivityTaskManager: Background activity launch blocked!
... BAL_BLOCK` against the NFC foreground-dispatch PendingIntent. The tag still
reached `handleTag`, but only because the activity had a visible window. That is a
concrete, previously-unknown mechanism for UI-53's "OS sees the tag, app does not".
