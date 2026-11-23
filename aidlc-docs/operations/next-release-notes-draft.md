# Next release — draft notes: 2.4.2 (versionCode 117)

**Status**: draft. Version decided 2026-11-22 by the maintainer ("just next
version"): **2.4.2 / versionCode 117**, set in `app/build.gradle.kts`. 116 /
2.4.1 is on Open testing; Production is on 2.3.1. Final Play text, character
counts and track split are written at upload time, following
`v2.4.1-release-notes.md`. Not built for release or uploaded.

## Fixes

- **U33 / UI-75 — transparent Spoolman colours.** A filament whose colour has
  transparency below 100% in Spoolman now shows its real colour in the form and
  in the spool and filament lists (was a different colour, or a grey circle).
  Saving no longer overwrites that colour or drops its transparency; picking a
  new colour keeps the transparency. Tags still get the plain colour.
- **U32 / UI-74 — weight edits on an existing spool (#12).** Save is no longer
  greyed out for filaments without a nozzle temperature or colour in Spoolman;
  the material's default temperatures are used. When Save is disabled the app
  says why. Measured uses the vendor's empty-spool weight when the filament and
  spool have none, and asks for one otherwise. A remaining weight above the
  spool's starting weight (overfilled 1 kg spools) is now saved exactly. Save
  says "No changes" instead of "Updated" when nothing changed, and shows an
  error when Spoolman rejects it. Also: spools with no starting weight in
  Spoolman now take weight edits (they were rejected); picking a filament hotter
  than the material's default range no longer blocks Save; and Save no longer
  rewrites a filament's own nozzle or bed temperature with the material default
  (it only sends temperatures you changed).
- **U28 / UI-70 — Snapmaker tags (#10).** New Snapmaker spools (e.g. PETG
  Translucent) read again: the app knows all of Snapmaker's current material
  and type codes, including Starlight and Aero (sub types 9-16 contributed by
  studiofuga, PR #13). A tag with a code the app doesn't know yet still reads;
  for an unknown material it asks you to pick one.

## Changes

- **Settings → Support the project**: the Snapmaker coupon code is removed
  (maintainer, 2026-11-22). The Polymaker and Snapmaker shop links stay. Also
  removed from the README.
