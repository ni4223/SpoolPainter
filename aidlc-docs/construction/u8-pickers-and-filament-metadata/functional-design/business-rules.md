# U8 — Business Rules

**Stage**: CONSTRUCTION → Per-Unit Loop → Functional Design → Business Rules
**Unit**: U8 — Pickers + Custom Entries + Filament Metadata UX
**Locked**: 2026-05-28

Companion to `domain-entities.md`. Each rule cites its FR / story and the locked Q-U8-* / carve-out decisions.

---

## 1. FR-13 reframe — filament picker (NOT orphan-only)

**Background**: `requirements-delta-orphan-filament-and-extra-fields.md` (approved 2026-05-26) framed Δ-1 as an **orphan-filament picker** that would be sectioned into the spool dropdown. During FD Part 1 review on 2026-05-28 the user reframed.

### BR-U8-1 — Filament picker lists ALL filaments

The filament picker MUST list every filament from `SpoolmanRepository.filaments`, not just orphans (filaments with zero spools). The picker covers two concrete user intents identically:

1. *"This filament has no spool yet — add a spool."* (formerly the "orphan" case)
2. *"This filament already has spool(s); I want a deliberate second spool under the same SKU without retyping the form and risking a duplicate filament."*

**Why**: from the user's perspective both intents are the same workflow ("add a spool under this existing filament"). Splitting them based on Spoolman state produced surprising UX and required client-side derivation.

### BR-U8-2 — Filament picker is hidden by default

The filament picker MUST live inside a collapsed-by-default expander labelled **"Filament ▾"** on the main form. Default UI = byte-identical to U7's form (per AC-14.1, applied to the filament expander as well as the More-details expander).

**Why**: the dominant flow is create-and-pair with a typed form; the filament picker is a power-user shortcut. Default UI stays clean.

### BR-U8-3 — Two independent expanders

The form MUST host two separate, independent collapsed-by-default expanders:

- **"Filament ▾"** — hosts only the filament dropdown.
- **"More details ▾"** — hosts only the five filament-scope metadata override fields.

Each expander toggles independently (Q-U8-18=A). Opening one MUST NOT collapse the other.

**Why**: distinct workflows; mutex behaviour creates surprise UX.

### BR-U8-4 — `MainUiState.orphanFilaments` is dropped

No client-side derivation of "orphan" filaments. `SpoolmanRepository.filaments` flows directly into the picker.

### BR-U8-5 — Spool dropdown remains single-section

The existing `SpoolmanDropdown` keeps its U6a/U6b shape (one section, all spools, archived hidden). No "Filaments without spools" section, no row-level differentiation between filament and spool entries (Q-U8-19 → N/A).

---

## 2. FR-8 — Material / brand presets + custom

### BR-U8-6 — Preset sources are Hilt-bound classes (FR-8.1, FR-8.2; Q-U8-1=A)

`MaterialPresetSource` and `BrandPresetSource` MUST be `@Singleton class`, not `object`. Allows fake-substitution in repository tests via Hilt.

### BR-U8-7 — "Other" preset is dropped (Q-U8-2=B)

Neither `MaterialPresetSource` nor `BrandPresetSource` carries an "Other" entry. The preset dropdowns instead render a footer row **"➕ Add custom material…"** / **"➕ Add custom brand…"** that opens the corresponding bottom sheet. The persistent (DataStore) custom-entry path goes through the sheet.

The inline "Other → typed" affordance from v1 (and U6a's `onCustomMaterialChanged` / `onCustomBrandChanged` handlers) is preserved as a **separate, one-shot path** for users who need to type a value just for this pairing without persisting it. It does not write to DataStore.

### BR-U8-8 — Brand merge precedence (FR-8.3; Q-U8-9=C)

`MaterialBrandRepository.brands` MUST be the case-insensitive distinct merge of `BrandPresetSource.brands` ∪ `SpoolmanRepository.vendors.map { it.name }` ∪ `MaterialBrandLocalStore.brands.toNames()`, **with presets enumerated first**.

Resolution rule when names collide case-insensitively: first occurrence wins. Iteration order: `presets → spoolman vendors → user-added`. Therefore:

- preset "Bambu Lab" + spoolman "bambu lab" → keep preset's "Bambu Lab".
- spoolman "Polymaker" + preset doesn't have it → keep spoolman's "Polymaker".
- user-added "Bambu Lab" → dropped (preset already covers it).

**Why**: stable list across Spoolman fetches (URL changes, refresh races). Spoolman's spelling is data — not authoritative for display.

### BR-U8-9 — Material merge precedence (Q-U8-10=A)

Same shape as brands: case-insensitive dedup, presets first. User-added "pla" deduped against preset "PLA". Preset's `Material` (with full temp ranges + density) wins on equality.

### BR-U8-10 — `addCustomMaterial` / `addCustomBrand` write semantics (Q-U8-11=B)

Repository write methods MUST persist the entry to DataStore unconditionally. Read-time dedup hides duplicates of presets.

**Sheet UI** is responsible for pre-validation: `Save` button greys out when the entered name (post-sanitisation) matches an existing preset or user-stored entry case-insensitively. The sheet shows a hint "Already exists — edit details to override". The repository never throws or rejects.

**Invariant** (asserted in `MaterialBrandRepositoryTest`):

```
materials.distinctBy { it.name.uppercase() }.size == materials.size
brands.distinctBy { it.lowercase() }.size == brands.size
```

This is the carve-out the user called out: *"as long as we are not showing multiple same material in dropdown after"* (Q-U8-11 carve-out, 2026-05-28).

### BR-U8-11 — Add-custom auto-select (Q-U8-15=A)

After `AddCustomMaterialSheet` / `AddCustomBrandSheet` confirm:

1. `MaterialBrandRepository.addCustomMaterial(material)` / `addCustomBrand(name)` writes to DataStore.
2. Sheet dismisses.
3. `MainViewModel` sets the form field to the new entry's name (`form.material = material.name` or `form.brand = name`).
4. Picker dropdown's selected slot reflects the new selection.

**Why**: user just typed it (literally next to the dropdown — Q-U8-15 user note); selection is the obvious next step.

### BR-U8-12 — Input rules — v1 parity (LOCKED 2026-05-28; carve-out)

v1 already applied input filters to its custom-material / custom-brand fields. v2 MUST preserve this behaviour identically across both inline "Other → typed" path AND the new add-custom sheets:

| Field | Allowed chars | Max length | Casing |
|---|---|---|---|
| Material name | `isLetterOrDigit() ‖ '-' ‖ '+'` (no spaces) | 8 | `.uppercase()` |
| Brand name | `isLetterOrDigit() ‖ ' ' ‖ '.' ‖ '-'` | 10 | Title Case first letter |

Enforcement MUST happen inside `OutlinedTextField.onValueChange` (rejected chars never appear in the field). `Save` is disabled while `name.isBlank()` post-sanitisation.

**Source of truth**: v1's `MaterialSelector.kt:75-77` + `BrandSelector.kt:78-81`. v1's `MaterialSelector.kt:74` comment says "Max 5 chars" — the **code** uses 8; we honour the code, not the stale comment.

---

## 3. FR-14 — "More details" expander

### BR-U8-13 — Default state collapsed (AC-14.1, AC-14.2)

`form.moreDetailsExpanded = false` by default. Default form layout (both expanders collapsed) MUST be byte-identical to U7's form.

### BR-U8-14 — Expander row affordance (Q-U8-17=B)

Both `FilamentSectionExpander` and `MoreDetailsExpander` use Material `Icons.Default.ExpandMore` / `ExpandLess` next to plain text labels — not Unicode `▾`/`▴` arrows. (Spec literal in delta §2 used Unicode; we override for rendering consistency.)

### BR-U8-15 — Five filament-scope override fields (AC-14.3)

When `moreDetailsExpanded == true`, the form MUST render exactly these five inline numeric inputs (any other fields require a fresh delta):

1. Empty spool weight (g) → `form.emptySpoolWeightG`
2. Price → `form.priceMajor`
3. Full spool weight (g) → `form.fullSpoolWeightG`
4. Diameter (mm) → `form.diameterMm`
5. Density (g/cm³) → `form.densityGPerCm3`

Each input is `OutlinedTextField` with `KeyboardOptions(keyboardType = Decimal)`. Empty input = "no override, use default" (`form.field = null`).

### BR-U8-16 — Default values when expander blank (carve-out 2026-05-28)

| Form field | Default when blank | Wire effect |
|---|---|---|
| `densityGPerCm3` | per-material density (PLA 1.24, ABS 1.04, …); fallback 1.24 for materials w/o stored density | `CreateFilamentRequest.density = <value>` |
| `diameterMm` | **1.75** mm | `CreateFilamentRequest.diameter = 1.75` |
| `fullSpoolWeightG` | **1000** g | `CreateFilamentRequest.weight = 1000` |
| `emptySpoolWeightG` | **null** | `spool_weight` omitted from wire |
| `priceMajor` | **null** | `price` omitted from wire |

Defaults applied at the call site (`CreateAndPairUseCase.makePayload` / repository's `createSpoolForNewFilament` / `createSpoolForExistingFilament`).

User direction (2026-05-28): *"I want certain defaults [for] everything that is in additional fields or no value if that's an option, but user can edit it."* — captured by the table above. Density / diameter / full-spool-weight have sensible non-null defaults (covering the 90% of users who own 1.75 mm, 1 kg spools); empty-spool-weight + price stay null because there's no useful global default (varies wildly by brand/SKU).

### BR-U8-17 — Prefill from existing filament (AC-14.5 partial)

When the user picks a filament via the filament picker (Δ-1) or selects a spool whose filament has stored values, `FormMapping.fromFilament(filament)` MUST populate the expander fields from `SpoolmanFilament` metadata:

```
form.densityGPerCm3   = filament.density
form.diameterMm       = filament.diameter
form.fullSpoolWeightG = filament.weight
form.emptySpoolWeightG = filament.spool_weight
form.priceMajor       = filament.price
```

Null filament fields → null form fields → call-site falls back to defaults.

### BR-U8-18 — On-tag JSON unaffected (AC-14.6)

`OpenSpoolPayload` (the NDEF JSON written to the tag) MUST NOT carry any of the five expander fields. They are inventory-side metadata only.

---

## 4. FR-15 — PATCH idempotency

### BR-U8-19 — PATCH issued only when fields differ (Q-U8-13=A)

`SpoolmanRepository.patchFilament(filamentId, body)` MUST:

1. Read `_filaments.value.find { it.id == filamentId }` → `cached`.
2. Compute the diff: for each non-null field in `body`, compare against `cached`. Fields that match are dropped from the body.
3. If the resulting diff is empty → return `SpoolmanOutcome.Success(cached)` without an HTTP call.
4. Otherwise → issue `PATCH /api/v1/filament/{id}` with the diff'd body; on success update the cache.

### BR-U8-20 — PATCH wire semantics (Q-U8-8=A)

Spoolman's PATCH convention: present-and-null = clear; absent = leave unchanged. v2.0 only sends non-null fields (Gson default omits null). The "clear" semantics are documented but NOT exercised — `PatchFilamentBody` is constructed to never carry `null` values for fields the user wants to clear.

### BR-U8-21 — PATCH-then-create ordering for existing-filament path

When `selectedFilamentId != null`:

1. `getFilament(id)` (defensive fresh fetch).
2. If expander overrides differ from stored → `patchFilament(id, body)` (idempotency rule).
3. `createSpool(filament_id = id, extra = ...)` for the new spool.
4. UID append via `extra.card_uids` (existing post-create plumbing from U6a).

**Failure handling**:
- Step 2 fails → `SpoolmanOutcome.HttpError`; `createSpool` NOT called (fail-fast).
- Step 3 fails after step 2 succeeded → PATCH already happened; metadata persists as the user intended; surface `SpoolmanOutcome.HttpError` for the spool create. **Documented partial-state behaviour**: filament metadata is sticky on retry; user retry creates the spool only.

---

## 5. FR-13 — selection mutual exclusivity

### BR-U8-22 — Spool / filament selection mutex (Q-U8-7=A)

`form.selectedSpoolId` and `form.selectedFilamentId` MUST NOT be set simultaneously. Enforced at the VM setters:

| Action | Resulting form |
|---|---|
| `onSpoolSelected(s)` | `selectedSpoolId = s.id`, `selectedFilamentId = null` |
| `onFilamentSelected(f)` | `selectedFilamentId = f.id`, `selectedSpoolId = null` |
| Either picker cleared (X) | both null |

### BR-U8-23 — `onWriteTapped` routing (Q-U8-14=A)

```
if      (selectedFilamentId != null) → CreateAndPairUseCase with selectedFilamentId carrier
else if (selectedSpoolId    != null) → existing append-to-spool path
else                                  → existing create-new-filament path
```

Use-case branches internally; UI flow + button copy + post-success behaviour identical across branches.

---

## 6. AC matrix — S-8.1 .. S-8.6

| AC | Story | Rule(s) |
|---|---|---|
| AC-8.1.1 | S-8.1 | BR-U8-6 (preset sources Hilt-bound) |
| AC-8.1.2 | S-8.1 | BR-U8-7 ("Other" → footer) |
| AC-8.2.1 | S-8.2 | BR-U8-8 (Spoolman vendors merged into brand list) |
| AC-8.3.1 | S-8.3 | BR-U8-10 (write semantics + sheet pre-validation) |
| AC-8.3.2 | S-8.3 | BR-U8-11 (auto-select after add) |
| AC-8.3.3 | S-8.3 | BR-U8-12 (v1-parity input rules) |
| AC-8.4.x | S-8.4 | same as S-8.3 (mirror for brands) |
| AC-13.1 | S-8.5 | BR-U8-1 (all filaments listed; not orphan-only) — **reframed** |
| AC-13.2 | S-8.5 | BR-U8-17 (prefill from filament) |
| AC-13.3 | S-8.5 | BR-U8-23 (`onWriteTapped` routing) + BR-U8-21 (PATCH-then-create) |
| AC-13.4 | S-8.5 | BR-U8-22 (selection mutex; X-clear sets both null) |
| AC-13.5 | S-8.5 | (perf) — `ExposedDropdownMenu` virtualisation reused |
| AC-14.1 | S-8.6 | BR-U8-2 + BR-U8-13 (default UI byte-identical) |
| AC-14.2 | S-8.6 | BR-U8-14 (expander icon affordance) |
| AC-14.3 | S-8.6 | BR-U8-15 (five inline fields; position above Save button) |
| AC-14.4 | S-8.6 | BR-U8-16 (defaults table) |
| AC-14.5 | S-8.6 | BR-U8-17 (prefill) + BR-U8-21 (PATCH-then-create) |
| AC-14.6 | S-8.6 | BR-U8-18 (on-tag JSON unaffected) |

---

## 7. Reframe deltas (vs. requirements-delta-orphan-filament-and-extra-fields.md)

The delta document remains as historical record. FD-stage reframes captured here:

| Original delta clause | FD reframe |
|---|---|
| §2 "**Orphan-filament picker**" | Renamed: **"Filament picker"**. Lists ALL filaments. (BR-U8-1) |
| AC-13.1 "Filaments without spools section at top of dropdown" | Filament picker is its own collapsed expander, not a section in the spool dropdown. (BR-U8-2, BR-U8-5) |
| §2 implementation note "client-side `listFilaments() minus { filament.id where listSpools(filterByFilament).size > 0 }`" | No client-side derivation; picker reads `SpoolmanRepository.filaments` directly. (BR-U8-4) |
| §2 "U8-Δ-2 — Inline 'More details ▾' expander on `FilamentForm`" | TWO expanders now: "Filament ▾" + "More details ▾", each independent. (BR-U8-3) |

These reframes do not introduce new requirements; they restate the same intent (FR-13, FR-14, FR-15) with cleaner UX. No requirements-doc edit needed; FD-stage record carries the divergence.
