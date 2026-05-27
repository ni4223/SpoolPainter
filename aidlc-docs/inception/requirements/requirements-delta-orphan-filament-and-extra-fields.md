# SpoolPainter v2 — Requirements Delta: Orphan-Filament Picker + Extra Fields (Inline Expander)

**Status**: Approved 2026-05-26 by user direction ("add to all docs no approval required") during U6b Code Gen Part 1 pause. Folds into U8 construction scope. v2.0 only.

**Drafted**: 2026-05-26
**Affects units**: **U8** (now broadened from "Material/Brand catalogue" to "Material/Brand catalogue + Filament metadata UX"). U6b is unaffected.
**Stories**: introduces **S-8.5** (orphan-filament picker), **S-8.6** (extra-fields inline expander). Story IDs reserved in this delta; story bodies appear in §6 below.

---

## 1. Why this delta exists

Two related gaps surfaced during the U6b pause on 2026-05-26:

1. **Orphan filaments are unreachable from the UI**. The main-screen dropdown lists *spools*, not filaments. A filament that exists in Spoolman with zero spools cannot be selected. A user who wants to add a spool to such a filament has only one path today: re-type every form field hoping it byte-matches the stored filament. If any composite-key field mismatches (see `requirements-delta-tag-mime-and-matcher-bugs.md` Bug #2), the typed-form path silently creates a duplicate filament. Even after Bug #2's matcher hardening, the user still has no *intentional* way to pick "this orphan filament, add a spool to it".
2. **Spoolman has rich per-filament metadata that v2 does not surface**. The user explicitly called out empty-spool weight (`filament.spool_weight` — the cardboard mass), price (`filament.price`), and full-spool weight (`filament.weight`, currently hard-defaulted to 1000 g). These are filament-scope SKU attributes, not per-spool — they belong on the filament record, edited once, reused across spools.

The user's framing: keep the default form clean, let the user *opt in* to seeing/editing the rest.

---

## 2. New functional requirements

### FR-13 — Orphan-filament picker

The main-screen filament/spool picker MUST surface filaments that exist in Spoolman with zero associated spools (orphan filaments) alongside spools, so a user can pick an orphan filament and create a new spool under it.

**Acceptance criteria**:
- AC-13.1: When the user taps the picker, orphan filaments appear in a visually distinct **"Filaments without spools"** section at the top of the dropdown, ordered alphabetically by `vendor.name + " " + filament.name + " " + extra.variant`.
- AC-13.2: Selecting an orphan filament seeds the form (material, vendor, color, variant, temps from `settings_extruder_temp` / `settings_bed_temp`) and sets `selectedSpoolId = null`. The form's "selected filament" indicator MUST show the picked filament so the user knows the next Save & Write will create a spool *under* this filament, not a fresh one.
- AC-13.3: Tapping Save & Write with an orphan filament selected MUST resolve to the existing filament (not create a duplicate), append the tag UID, and create exactly one new spool under it. After success, the new spool replaces the orphan-filament selection in the picker (it has a spool now → it's a spool entry).
- AC-13.4: If an orphan filament's selection is cleared (via the same "X" affordance the dropdown already provides for spools), the form returns to blank.
- AC-13.5: Picker layout MUST handle ≥100 orphan filaments + ≥1000 spools without UI lag (re-uses existing `ExposedDropdownMenu` virtualisation from U6a).

**Implementation note** (informative): The orphan list is computed client-side: `listFilaments()` minus `{ filament.id where listSpools(filterByFilament).size > 0 }`. Reuses the bulk-fetch pattern from U3.

### FR-14 — Inline "More details ▾" expander on the main form

The main form MUST grow an inline **"More details ▾"** expander, collapsed by default, that surfaces additional filament-scope fields when expanded. The default (collapsed) form MUST remain visually identical to the U6a layout — the expander is opt-in, hidden until the user clicks.

**Fields surfaced in the first cut** (user choice 2026-05-26 — answers to multi-select Q4 = options "1" and "3"):

| Field | Spoolman attribute | Scope | Default if unset | Notes |
|---|---|---|---|---|
| Empty spool weight | `filament.spool_weight` (g) | Filament | unset (Spoolman accepts null) | Cardboard-only mass. The field the user explicitly called out. |
| Price | `filament.price` (currency-naive number) | Filament | unset | User-typed; Spoolman stores as a `Decimal`. |
| Full spool weight | `filament.weight` (g) | Filament | 1000 | Currently hard-coded to 1000 in `CreateFilamentRequest`. Override surfaced here. |
| Diameter | `filament.diameter` (mm) | Filament | 1.75 | Currently hard-coded to 1.75. Override for 2.85 / 3.0 mm filament. |
| Density | `filament.density` (g/cm³) | Filament | per-material (PLA 1.24, …) | Currently per-material default. Override for brand-specific calibration. |

**Fields NOT in the first cut** (deferred or never): `spool.location`, `spool.comment`, `spool.first_used`, `spool.last_used`, `filament.comment`, `filament.external_id`. May land in a later delta if user requests.

**Acceptance criteria**:
- AC-14.1: Default (collapsed) form layout is byte-identical to U6a — same fields, same order, same visual weight.
- AC-14.2: Expander row label is **"More details ▾"** (collapsed) / **"More details ▴"** (expanded). Tapping toggles. State is per-form-instance — no need to persist across app launches.
- AC-14.3: When expanded, the five fields appear inline below the standard form, above the Save & Write button. Each is a single-line numeric input (decimal allowed where the unit takes decimals — diameter, density, price, weights). Empty input = "no override" (server-default behaviour preserved).
- AC-14.4: Save & Write reads the expander values: if a field is non-empty, it overrides the current default in `CreateFilamentRequest`. If a field is empty, the existing default applies (`filament.weight = 1000`, `filament.diameter = 1.75`, `filament.density = densityFor(material)`, `filament.spool_weight = null`, `filament.price = null`).
- AC-14.5: When the form is seeded from an existing filament (orphan-filament path FR-13, or a spool the user picked), the expander values prefill from the filament record. User edits update the filament on the next Save & Write that creates a *new* spool *under that filament* — i.e. the matcher resolves to the same filament; metadata edits PATCH the filament record before the create-spool step. Out of scope for v2.0: editing a filament's metadata without creating a spool.
- AC-14.6: All expander fields are filament-scope. They do **not** land on `OpenSpoolPayload` (the on-tag JSON); the on-tag JSON shape is unchanged.

### FR-15 — Filament metadata write path

`SpoolmanRepository.createSpoolForNewFilament` (and `resolveOrCreateFilament`) MUST forward expander values into `CreateFilamentRequest` when present:

- `req.weight` ← form override or 1000
- `req.diameter` ← form override or 1.75
- `req.density` ← form override or `densityFor(material)`
- `req.spool_weight` ← form override or null (NEW field on `CreateFilamentRequest`)
- `req.price` ← form override or null (NEW field on `CreateFilamentRequest`)

Where the matcher resolves to an **existing** filament (not a new one), and the user has edited expander values, the repository MUST issue a `PATCH /api/v1/filament/{id}` to update the existing record before creating the spool. Idempotent fields only — if the form value equals the stored value, skip the PATCH.

The Spoolman models (`SpoolmanFilament`) MUST be extended to include `spool_weight: Float?`, `price: Float?`, `weight: Float?`, `diameter: Float?`, `density: Float?` so reads can prefill the expander.

---

## 3. Out of scope for this delta

- **Spool-scope extra fields** (`location`, `comment`). Tracked but deferred — may land in a follow-up delta if user requests after v2.0 testing-track validation.
- **First-class filament editor screen** (long-press the dropdown row → dedicated screen). Considered, rejected — user picked the inline-expander UX (option 3 of the UX decision) for v2.0. The dedicated screen pattern is preserved as a **post-v2.0 candidate** in case the inline expander gets visually crowded as more fields are added.
- **Repairing existing duplicate filaments** in Spoolman (left over from Bug #2's behaviour pre-fix). User-side dedup if needed.
- **Filament metadata edit without spool create**. No "edit filament" surface — edits ride along with the next Save & Write that lands on the filament.

---

## 4. UX decisions on record (2026-05-26)

| Question | User answer |
|---|---|
| Bug routing | Both bugs as U6b-Δ-3 / U6b-Δ-4 |
| Unit scope for orphan filaments + extras | Extend U8 |
| UX pattern | Inline "More details ▾" expander (option 3) — *"i want 3, i am thinking something that will expand in existing UI so default UI looks clean on start and user can choose to expand"* |
| First-cut fields | Empty spool weight + price + full weight (filament-scope) **AND** diameter + density overrides (filament-scope) |

Not chosen: bottom sheet "Advanced" (rejected — user wanted inline); separate filament editor screen (rejected — user wanted to keep default UI clean *and* the expander pattern); spool-scope `location` / `comment` (deferred — not in user's pick).

---

## 5. Construction-unit deltas

### U8 — broadened scope

Original U8 scope (per `unit-of-work.md` §3-U8): "Pickers + Custom Entries" — `MaterialPresetSource`, `BrandPresetSource`, `MaterialBrandLocalStore`, `MaterialBrandRepository`, `AddCustomMaterialSheet`, `AddCustomBrandSheet`, `MaterialPicker` / `BrandPicker` hardening.

Broadened U8 scope (this delta):

- **U8-Δ-1 — Orphan-filament picker** (FR-13). Modifies the existing main-screen dropdown:
  - `MainViewModel` exposes `orphanFilaments: StateFlow<List<SpoolmanFilament>>` derived from `spoolman.filaments` minus filaments that have at least one spool in `spoolman.spools`.
  - Dropdown composable (current name: `SpoolDropdown` or similar — confirm in code) is split into two sections: "Filaments without spools" (top), "Spools" (existing).
  - New use-case OR repository method: `createSpoolForExistingFilament(filamentId, expanderOverrides): SpoolmanOutcome<SpoolmanSpool>` — bypasses `resolveOrCreateFilament`, calls `getFilament(id)` (already added in U6b plan §3), optionally PATCHes filament metadata if expander values changed, then `createSpoolStep`. Either a thin wrapper around `createSpoolForNewFilament` with `selectedFilamentId` short-circuit, or a separate path. Implementer choice.
  - New `MainViewModel.onFilamentSelected(filament: SpoolmanFilament)` analogous to `onSpoolSelected`.

- **U8-Δ-2 — Inline "More details" expander** (FR-14, FR-15). Net-new Compose surface:
  - `FilamentForm` extended with a `MoreDetailsExpander` Composable holding the five fields. State lives on `FormState` (new fields: `emptySpoolWeightG: Float?`, `priceMajor: Float?`, `fullSpoolWeightG: Float?`, `diameterMm: Float?`, `densityGPerCm3: Float?`). Default value = null (= "no override").
  - `CreateFilamentRequest` gets `spool_weight: Float?` + `price: Float?` (new). `weight`, `diameter`, `density` switch from required-with-default to optional-with-fallback (the fallback computed at the call site, not by Spoolman).
  - `SpoolmanFilament` gets `spool_weight: Float?`, `price: Float?`, `weight: Float?`, `diameter: Float?`, `density: Float?` so prefill works.
  - `FormMapping.fromSpoolman` reads filament metadata into the new `FormState` fields.
  - `MainViewModel.onWriteTapped` snapshot includes the expander values; routes through `CreateAndPairUseCase` (existing) or `createSpoolForExistingFilament` (new).

- **U8-Δ-3 — Filament metadata PATCH path** (FR-15). New `SpoolmanRepository.patchFilament(filamentId, body)` + matching API endpoint. Called when matcher resolves to existing filament AND any expander value differs from stored.

**Tests** (additions over the original U8 plan):
- `MainViewModelOrphanFilamentTest` — orphan list derivation; `onFilamentSelected` seeds form correctly; `onWriteTapped` resolves to existing filament (no duplicate).
- `MoreDetailsExpanderTest` (Compose UI test) — toggle visibility; values bind to `FormState`; default-collapsed state.
- `SpoolmanRepositoryPatchFilamentTest` — PATCH issued only when values differ; idempotent skip when equal; 4xx/5xx surface `SpoolmanOutcome.HttpError`.

**Stories added**:

### S-8.5 — Pick an orphan filament and add a spool to it

> **As a** SpoolPainter user with an existing filament in Spoolman that has no spools yet,
> **I want** to pick that filament from the main-screen picker and tap Save & Write,
> **so that** a new spool gets added to the filament record (instead of accidentally duplicating the filament because I retyped one field slightly differently).

**Acceptance criteria**: AC-13.1 .. AC-13.5 above.

### S-8.6 — Edit empty-spool weight, price, and other extra fields without cluttering the default form

> **As a** SpoolPainter user who tracks spool cost and weight,
> **I want** an opt-in "More details" expander on the main form where I can enter empty-spool weight, price, full weight, diameter, and density,
> **so that** my spool records carry accurate cost/weight metadata while my normal create-and-pair flow stays as compact as it is today.

**Acceptance criteria**: AC-14.1 .. AC-14.6, FR-15 behaviour above.

---

## 6. Wire-format / API surface summary

```
CreateFilamentRequest (post-delta)
  name: String?
  vendor_id: Int
  material: String
  color_hex: String                  // canonicalised — see Bugs delta
  settings_extruder_temp: Int?
  settings_bed_temp: Int?
  density: Float                     // form override OR densityFor(material)
  diameter: Float                    // form override OR 1.75
  weight: Float                      // form override OR 1000
  spool_weight: Float?               // NEW — form override OR null (Spoolman default)
  price: Float?                      // NEW — form override OR null
  extra: Map<String, String>?        // unchanged (variant)

PatchFilamentBody (NEW)
  name: String?
  settings_extruder_temp: Int?
  settings_bed_temp: Int?
  density: Float?
  diameter: Float?
  weight: Float?
  spool_weight: Float?
  price: Float?
  extra: Map<String, String>?
  // Send only fields that differ from the stored record. Spoolman PATCH
  // semantics: present-and-null = clear; absent = leave unchanged.

SpoolmanFilament (post-delta)
  id, name, material, vendor, color_hex,
  settings_extruder_temp, settings_bed_temp,
  density: Float?               // NEW — exposes stored value for prefill
  diameter: Float?              // NEW
  weight: Float?                // NEW
  spool_weight: Float?          // NEW
  price: Float?                 // NEW
  extra: Map<String, String>?
```

OpenSpoolPayload + on-tag JSON: **unchanged**. Empty-spool weight, price, etc. are inventory-side metadata, not on-tag.

---

## 7. Trace summary

| Story | Functional requirement | Construction unit | Plan section |
|---|---|---|---|
| S-8.5 | FR-13 | U8 | U8-Δ-1 |
| S-8.6 | FR-14, FR-15 | U8 | U8-Δ-2, U8-Δ-3 |

Bug #1 + Bug #2 fixes (separate delta `requirements-delta-tag-mime-and-matcher-bugs.md`) provide the foundation Bug #2's matcher hardening — without it, the orphan-filament path would still occasionally produce duplicates if the user typed before picking.

---

## 8. Approval gate

User authorised on 2026-05-26 ("ask whatever question you want and then add to all docs no approval required") after multi-question UX walkthrough (bug routing, unit scope, UX pattern, field selection). Decisions captured in §4. This delta is not subject to the usual standardised 2-option requirements approval — user directed direct application.

Test count target update: U8's prior plan target was not yet fixed. With FR-13 + FR-14 + FR-15, U8's test target lifts by ~10-15 cases (orphan list derivation + expander UI + PATCH idempotency + new round-trip integration).
