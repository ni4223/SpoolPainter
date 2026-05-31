# Screenshots

Placeholder directory for the screenshots referenced from `../README.md`. Real
captures will land here before the Play Store testing-track upload.

## What each slot should show

| Slot | File | Content |
|---|---|---|
| 1 | `01-main.png` | Main screen with a paired spool selected. Logo header, dropdown showing a real spool, form populated, Read FAB visible. Establishes "what the app looks like in normal use." |
| 2 | `02-spool-dropdown.png` | Spool dropdown open showing the v2.0.2 rich rows: 24dp colour swatch + bold "Vendor · Name" first line + faded "Material · Variant · #id" second line. Sells the "find your filament fast" inventory browse. |
| 3 | `03-form-expanded.png` | Form with Filament section + Color picker + "Filament metadata" expander all open, showing temps + Remaining/Measured + filament metadata fields. Sells the v2 form depth. |
| 4 | `04-pair-another.png` | "Pair another tag?" sheet shown right after a successful Save & Write. Sells the multi-tag-per-spool flow that's unique to v2. |
| 5 | `05-settings.png` | Settings screen — Spoolman URL filled, sort dropdowns + segmented Asc/Desc controls, currency segmented row, theme toggle visible on the top bar. Sells "configurable, your-Spoolman." |
| 6 | `06-move-on-bind.png` | Move-on-bind repair-confirm sheet listing a previous owner. Sells the conflict handling. |
| 7 | `07-vendor-chip.png` | Vendor-tag inline chip ("Vendor tag — we can't read this tag's contents…") with the tertiary-tinted Info icon. Sells the v2 honest UX for tags we can't decode yet. |

## Capture recipe

On the moto g (or any USB-connected debug device):

```bash
adb exec-out screencap -p > screenshots/01-main.png
```

Aim for 1080×1920 or higher (the moto g produces 1220×2712 native, which
Play Console will downsize). PNG, no compression, no editing required.

For demo / staging data: use a Spoolman test server with 2–3 well-formed
spools (Polymaker PLA Matte White, Inslogic ASA Black, etc.) and at least
one paired tag UID per spool.

## Why these aren't real images yet

The README's Screenshots section is structured up-front so the testing-track
listing has a placeholder layout. Real captures land in the Play Console
upload commit (or earlier if convenient).
