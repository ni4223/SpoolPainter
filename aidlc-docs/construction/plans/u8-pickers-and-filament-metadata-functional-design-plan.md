# U8 — Functional Design Plan

**Stage**: CONSTRUCTION → Per-Unit Loop → Functional Design (U8)
**Unit**: U8 — Pickers + Custom Entries + Filament Metadata UX
**Authored**: 2026-05-28

## Per-unit gate assessment

| Stage | Decision | Rationale |
|---|---|---|
| Functional Design | **EXECUTE** | Net-new domain types (presets, DataStore-Proto custom store, repository, two add-custom sheets, hidden filament picker, `MoreDetailsExpander`); new business logic (filament-picker selection, PATCH idempotency, expander default fallbacks). |
| NFR Requirements | **SKIP** | No new performance / security / scalability concerns beyond NFR-1..NFR-7 already covered at U1. DataStore IO + Retrofit error envelope already locked. |
| NFR Design | **SKIP** | Predicated on NFR-R running. |
| Infrastructure Design | **SKIP** | Per `aidlc-docs/inception/plans/execution-plan.md` — pure Android client; no CDK / Terraform / CloudFormation. |

## Source artefacts

- `aidlc-docs/inception/application-design/unit-of-work.md` §3-U8 (broadened scope incl. U8-Δ-1/Δ-2/Δ-3)
- `aidlc-docs/inception/application-design/components.md` §2.1 (`MaterialPicker`, `BrandPicker`, `AddCustomMaterialSheet`, `AddCustomBrandSheet`, `SpoolmanDropdown`, `FilamentForm`), §2.2 (`AddCustomMaterialViewModel`, `AddCustomBrandViewModel`), §2.4 (`MaterialBrandRepository`), §2.6 (`MaterialPresetSource`, `BrandPresetSource`, `MaterialBrandLocalStore`)
- `aidlc-docs/inception/application-design/component-methods.md` §6 (`MainViewModel`), §8 (Compose components)
- `aidlc-docs/inception/application-design/services.md` (Spoolman service contract — PATCH filament)
- `aidlc-docs/inception/requirements/requirements.md` — FR-8.1..FR-8.5 (Material/Brand presets + custom)
- `aidlc-docs/inception/requirements/requirements-delta-orphan-filament-and-extra-fields.md` — FR-13, FR-14, FR-15; AC-13.1..AC-13.5; AC-14.1..AC-14.6; S-8.5, S-8.6 (approved 2026-05-26)
- `aidlc-docs/inception/requirements/requirements-delta-extra-fields.md` — `extra.variant` wire format (already shipped U6a)
- `aidlc-docs/inception/user-stories/stories.md` — S-8.1, S-8.2, S-8.3, S-8.4, **S-8.5**, **S-8.6**

### Existing code touchpoints

| File | Role in U8 |
|---|---|
| `data/local/MaterialDatabase.kt` | v1 hardcoded preset list (10 materials with temp ranges). Migrates to `MaterialPresetSource` (rename + relocation; data preserved). |
| `data/local/BrandDatabase.kt` | v1 hardcoded brand list (12 vendors, "Other"/"Generic" included). Migrates to `BrandPresetSource`. |
| `domain/models/Material.kt` | Existing — `name`, `defaultExtruderMin/Max`, `defaultBedMin/Max`. Reused as-is. |
| `data/remote/spoolman/SpoolmanRepository.kt` | Adds `patchFilament(filamentId, body)` (Δ-3) + `createSpoolForExistingFilament` short-circuit OR new path (Δ-1). `resolveOrCreateFilament` extended to forward expander overrides into `CreateFilamentRequest` (Δ-2). |
| `data/remote/spoolman/SpoolmanApi.kt` | Adds `@PATCH api/v1/filament/{id}` endpoint. |
| `data/remote/spoolman/SpoolmanRequests.kt` | `CreateFilamentRequest`: `weight` / `diameter` / `density` switch from required to required-with-call-site-fallback (no schema break — call site still computes the fallback); add `spool_weight: Float?` + `price: Float?`. New `PatchFilamentBody` data class. |
| `domain/models/SpoolmanModels.kt` | `SpoolmanFilament` adds `density`, `diameter`, `weight`, `spool_weight`, `price` (all `Float?`). |
| `ui/screens/main/MainUiState.kt` | `FormState` adds `selectedFilamentId: Int?`, `filamentSectionExpanded: Boolean`, `moreDetailsExpanded: Boolean`, and five nullable Float overrides for the metadata fields. (No `orphanFilaments` — filament picker lists ALL filaments.) |
| `ui/screens/main/MainViewModel.kt` | New `onFilamentSelected(filament: SpoolmanFilament?)`, `onFilamentSectionToggled()`, `onMoreDetailsToggled()`, five `onXxxChanged` handlers for the expander fields. |
| `ui/components/FilamentForm.kt` | Extended with two collapsed-by-default expanders: (1) **"Filament ▾"** hosting the filament dropdown; (2) **"More details ▾"** hosting the five metadata override fields. Default UI = today's form (collapsed both). |
| `ui/components/MaterialPicker.kt` / `BrandPicker.kt` | Already wired in U6a — U8 swaps the data source from `MaterialDatabase` / `BrandDatabase` to `MaterialBrandRepository`. |
| `ui/components/sheets/AddCustomMaterialViewModel.kt` / `AddCustomBrandViewModel.kt` | U1 placeholders — replaced with real state + commit handlers. |
| `ui/components/sheets/BottomSheetHost.kt` | Adds two new branches for the add-custom sheets. |
| `di/DataStoreModule.kt` | Comment "U8 placeholder" lines flip to real `DataStore<CustomMaterials>` + `DataStore<CustomBrands>` providers (Hilt module already declared in U1). |

### Existing code seams confirmed (read 2026-05-28)

- `MaterialDatabase` exposes `materials: List<Material>` + `getMaterial(name): Material?` — direct callers in `MainViewModel` (preset lookup for default temps) + `FilamentForm`. Migration shape: `MaterialPresetSource.materials` retains identical content; `MaterialBrandRepository.materials` is the new VM-facing flow.
- `BrandDatabase.brands: List<String>` — string list. `BrandPresetSource` keeps the same shape; `MaterialBrandRepository.brands: StateFlow<List<String>>` adds Spoolman vendor merge + custom merge.
- `SpoolmanRepository.filaments: StateFlow<List<SpoolmanFilament>>` + `spools: StateFlow<List<SpoolmanSpool>>` already populated by `refresh()`. The filament picker lists **all** filaments (no orphan filter); covers both "filament with no spool yet" and "filament with one or more spools — add another".
- `SpoolmanFilament` is currently shape `(id, name, material, vendor, color_hex, settings_extruder_temp, settings_bed_temp, extra)`. Adding five `Float?` fields. Gson tolerates additional fields in Spoolman responses; missing fields deserialize to null. No schema migration needed.
- `CreateFilamentRequest` currently has `density`, `diameter`, `weight` as non-null `Float`. Switching to non-null with call-site fallback (Δ-2 call-site contract). Adding `spool_weight: Float?` + `price: Float?`. Spoolman accepts unknown extras gracefully; null fields are omitted by Gson default.
- `AddCustomMaterialViewModel` / `AddCustomBrandViewModel` are U1 placeholders (`UiState(placeholder = true)`). Replaced wholesale.
- `BottomSheetHost.kt` selector pattern carried through U6b/U7 — U8 adds two more branches.
- `DataStoreModule.kt:16` already has the comment "CustomMaterials / CustomBrands DataStores will be added in U8" — landing site is locked.
- `MainViewModel` already has `onCustomMaterialChanged(value: String)` / `onCustomBrandChanged` (U6a inline custom field). U8 keeps these for the inline "Other → typed" path; the **add-custom sheets** are a separate persistent path (typed entry → DataStore → reappears in picker on next open).

---

## 1. Unit Context

### 1.1 Scope (locked by Units Generation §3-U8 + delta)

**Original scope (retained)**:
- `MaterialPresetSource` (hardcoded; FR-8.1 / S-8.1) — relocate `MaterialDatabase`'s contents; same 10 materials with temp ranges.
- `BrandPresetSource` (hardcoded; FR-8.2 / S-8.1) — relocate `BrandDatabase`'s 12 entries.
- `MaterialBrandLocalStore` — DataStore-Proto-backed user-added entries (FR-8.5 / S-8.3, S-8.4). Schema: `CustomMaterials { repeated CustomMaterial }` + `CustomBrands { repeated CustomBrand }`.
- `MaterialBrandRepository` — merges presets + Spoolman vendors (brand only) + user-added (case-insensitive dedup; Spoolman entries take precedence; FR-8.3, FR-8.5).
- `AddCustomMaterialSheet` + `AddCustomMaterialViewModel`.
- `AddCustomBrandSheet` + `AddCustomBrandViewModel`.
- `MaterialPicker` / `BrandPicker` — swap data source from `MaterialDatabase` / `BrandDatabase` to `MaterialBrandRepository`.

**Delta scope (added)**:
- **U8-Δ-1 — Hidden filament picker** (FR-13 / S-8.5; **reframed 2026-05-28** — see §1.4). Collapsed-by-default "Filament ▾" expander on the main form; lists ALL filaments (not just orphans). Picking a filament + Save & Write creates a new spool under that filament — works for both filaments with zero spools and filaments that already have spools (so the user can deliberately add a 2nd spool to a SKU they already own without retyping the form and risking a duplicate).
- **U8-Δ-2 — "More details ▾" expander** (FR-14 / S-8.6). Separate collapsed-by-default expander hosting the five filament-scope metadata override fields (empty spool weight, price, full spool weight, diameter, density).
- **U8-Δ-3 — Filament metadata PATCH path** (FR-15).

### 1.4 Reframe note (2026-05-28) — drop "orphan" framing; two hidden expanders

The original `requirements-delta-orphan-filament-and-extra-fields.md` (approved 2026-05-26) framed Δ-1 as an **orphan-filament picker** that would be sectioned into the main spool dropdown. During U8 FD Part 1 review on 2026-05-28 the user reframed:

1. **No orphan/non-orphan distinction**. The reason for picking a filament is the same regardless of whether it currently owns spools: "I want a new spool under this SKU without retyping the form." So the picker lists **all** filaments. Covers both "filament with 0 spools" and "filament with 1+ spools, add another".
2. **Filament picker lives in its own collapsed-by-default expander**, not sectioned into the spool dropdown. Default UI stays clean (just the spool dropdown + form + temps).
3. **Two separate expanders**:
   - **"Filament ▾"** — filament dropdown only.
   - **"More details ▾"** — five metadata override fields only.
   Each expander toggles independently. User direction: *"spool visible, then filament hidden with expandable, other stuff then more detailed hidden menu"*.

**Concrete consequences**:
- **`MainUiState.orphanFilaments` is dropped entirely.** Filament list reads directly from `SpoolmanRepository.filaments`.
- **Sectioned spool dropdown is dropped.** `SpoolmanDropdown` keeps its U6a/U6b shape (one section, all spools).
- **Two new `FormState` boolean flags**: `filamentSectionExpanded`, `moreDetailsExpanded`. Both default false.
- Q-U8-18 / Q-U8-19 (section ordering / row differentiation) → **N/A** — no sections.
- The phrase "orphan filament" is retired from U8 FD documentation; replaced with "filament picker" or "pick existing filament".

The `requirements-delta-orphan-filament-and-extra-fields.md` document remains as historical record; FD-stage delta in `business-rules.md` will document the reframe with rationale + a forward-pointer to keep traceability for FR-13 / S-8.5.

### 1.2 Cross-unit consumers

| Unit | Relationship |
|---|---|
| U6a (Create-and-Pair) | Already wired `MaterialPicker` / `BrandPicker` against `MaterialDatabase` / `BrandDatabase`. U8 swaps the underlying source — no public API change. Inline "Other → custom" field stays. |
| U6b (Move-on-Bind + Two-Tag) | Matcher hardening (Δ-4) is a hard prerequisite for U8-Δ-1 — the filament-picker path bypasses the matcher entirely (calls `getFilament(id)` directly), but Δ-4's `ColorHexCodec` + `canonVariant` helpers stabilise prefill (`FormMapping.fromSpoolman`) so the form populates correctly when the user picks a filament. **Verified**: Δ-4 landed in U6b polish commit `71ecffc`. |
| U7 (Side Modes) | Vendor UID-only new-spool path uses `createSpoolForNewFilament` — picks up Δ-2 expander overrides automatically. Raw-write mode never calls Spoolman, so Δ-2/Δ-3 don't apply. |
| U9 (Settings + Theming + UI Shell) | Independent. `SettingsScreen` does not surface custom-entry management for v2.0 (deferred). |
| U10 (Release polish) | U10's manual install-gate matrix gains: U8-Δ-1 filament-pick round-trip (filament with 0 spools + filament with 1+ spools), U8-Δ-2 expander prefill from existing filament, U8-Δ-3 PATCH idempotency. |

### 1.3 Out of scope (deferred)

- **First-class filament editor screen** — rejected in delta §3 in favour of inline expander.
- **Spool-scope extra fields** (`location`, `comment`) — deferred per delta §3.
- **Editing filament metadata without creating a spool** — deferred per delta §3 / AC-14.5 ("edits ride along with the next Save & Write").
- **Repairing existing duplicate filaments** in Spoolman — user-side dedup task.
- **Settings UI for managing custom entries** (rename / delete / reorder) — deferred to post-v2.0.
- **Per-vendor brand metadata** (logo, colour swatch hint) — out of v2.0 scope.

---

## 2. Plan Steps

### 2.1 Domain entities + Proto schema

#### 2.1.1 `MaterialPresetSource`

- [ ] Lock the shape.

```kotlin
@Singleton
class MaterialPresetSource @Inject constructor() {
    val materials: List<Material> = listOf(
        Material("PLA", 190, 220, 40, 65),
        Material("ABS", 220, 260, 80, 110),
        // ... rest of v1 list verbatim
    )
}
```

> **Q-U8-1** — `MaterialPresetSource` shape: `@Singleton class` with `val materials: List<Material>`, or `object` (like v1's `MaterialDatabase`)?
> - **A.** `@Singleton class` injected into `MaterialBrandRepository`. Symmetric with `BrandPresetSource`; testable via fakes.
> - **B.** `object` (singleton). Less DI plumbing; matches v1 idiom. ⭐
>
> **My pick:** A — repository takes both presets + the user store via constructor injection, so they should all be Hilt-bound for fake-substitution in tests.

#### 2.1.2 `BrandPresetSource`

- [ ] Lock the shape.

Same DI shape as `MaterialPresetSource`. `val brands: List<String>` retained verbatim from `BrandDatabase` (v1 list of 12 entries including "Other"/"Generic").

> **Q-U8-2** — Should "Other" stay in the **preset** list, or be filtered out and rendered as a synthetic dropdown footer ("➕ Add custom brand…")?
> - **A.** Keep "Other" as a preset entry — matches v1 exactly. Selecting it triggers the inline custom-brand input (already wired in U6a).
> - **B.** Filter "Other" out of the preset list; render a footer row "➕ Add custom brand…" that opens `AddCustomBrandSheet`. Same idea for "Other" material → `AddCustomMaterialSheet`. ⭐
>
> **My pick:** B — the inline "Other → typed" path is one-shot (form-scope; doesn't persist). The add-custom sheet is the **persistent** path. Two distinct UX surfaces serve different intents. v1 conflated them; v2's separation is cleaner.
>
> Implication: keep both paths. "Other → typed" still works for one-off entries (no DataStore write); "➕ Add custom" footer commits to DataStore so the entry reappears in future sessions.

#### 2.1.3 Proto schema — `CustomMaterials` + `CustomBrands`

- [ ] Lock the schema.

```proto
syntax = "proto3";
package com.spoolpainter.app.data.local.userdata;

message CustomMaterial {
  string name = 1;
  int32 default_extruder_min = 2;
  int32 default_extruder_max = 3;
  int32 default_bed_min = 4;
  int32 default_bed_max = 5;
  int64 created_at_epoch_ms = 6;  // sort order; user-add timestamp
}

message CustomMaterials {
  repeated CustomMaterial entries = 1;
}

message CustomBrand {
  string name = 1;
  int64 created_at_epoch_ms = 2;
}

message CustomBrands {
  repeated CustomBrand entries = 1;
}
```

> **Q-U8-3** — `CustomMaterial` payload: store full `Material` (with temp ranges), or just the **name** and synthesise temps from a fallback?
> - **A.** Full payload (name + four temp ints). User can specify accurate temps when adding; preserved across sessions. ⭐
> - **B.** Name only. Temps fall back to `MaterialDatabase("Other")` ranges (200–220 / 50–70). Less metadata; lighter UX.
>
> **My pick:** A — adding a material the user knows about (e.g. "PA-CF", "PPS") implies they know its print temps. Asking for them in the sheet is cheap; not asking forces them to look it up later when the form prefills with junk defaults.

> **Q-U8-4** — `created_at_epoch_ms` field — needed?
> - **A.** Keep — sort order = newest-first by add time; eases identification of "the one I just added". ⭐
> - **B.** Drop — sort alphabetically; no timestamp. Schema simpler.
>
> **My pick:** A — alphabetical sort merged with presets is fine, but for **diagnostics** the timestamp is cheap insurance. Not exposed in UI; preserved in DataStore.

#### 2.1.4 `Material` model — touch-up?

- [ ] Decide whether `Material` gains a `density: Float?` field for U8-Δ-2 expander default.

> **Q-U8-5** — How does the expander get its per-material density default?
> - **A.** Add `density: Float?` to `domain/models/Material.kt` (PLA 1.24, ABS 1.04, PETG 1.27, TPU 1.20, ASA 1.07, PC 1.20, Nylon 1.14, PVA 1.19, HIPS 1.04, Other null). Wired into `MaterialPresetSource`. ⭐
> - **B.** Keep `Material` unchanged; ship `densityFor(material: String): Float` extension function in U8's repository / domain helpers. (Currently lives inside `SpoolmanRepository.createSpoolForNewFilament` per the U6a OPEN-1 fix — see `aidlc-state.md` line 67.)
>
> **My pick:** A — density is intrinsic to the material; the data class is the right home. Migrate the existing `densityFor` map (already inlined in `SpoolmanRepository`) into `MaterialPresetSource`'s entries. `CustomMaterial` proto gains an optional `density` field too.

#### 2.1.5 `FormState` extensions

- [ ] Lock the new fields.

```kotlin
data class FormState(
    val cardUid: CardUid? = null,
    // ... existing fields ...
    val rawWriteMode: Boolean = false,

    // U8-Δ-1: when set, Save & Write creates a new spool under this filament
    // (skips the matcher / no filament POST). Mutually exclusive with
    // selectedSpoolId.
    val selectedFilamentId: Int? = null,
    val filamentSectionExpanded: Boolean = false,

    // U8-Δ-2: expander state + five nullable overrides. null = "no override,
    // use default". Persisted only on the form (no DataStore).
    val moreDetailsExpanded: Boolean = false,
    val emptySpoolWeightG: Float? = null,
    val priceMajor: Float? = null,
    val fullSpoolWeightG: Float? = null,
    val diameterMm: Float? = null,
    val densityGPerCm3: Float? = null,
)
```

> **Q-U8-6** — `selectedFilamentId` placement: on `FormState` (alongside `selectedSpoolId`), or on `MainUiState.spoolman` (alongside `spools` cache)?
> - **A.** On `FormState`. Symmetric with `selectedSpoolId`; clears with the form. ⭐
> - **B.** On `MainUiState.spoolman.selectedFilamentId`. Symmetric with the cache; doesn't pollute `FormState`.
>
> **My pick:** A — selection is per-pairing intent (clears on Save & Write success; clears with form-clear). Form-scope is the right boundary.

> **Q-U8-7** — `selectedSpoolId` and `selectedFilamentId` mutual exclusivity: enforce by precondition (one nullable XOR), or rely on UI to never set both?
> - **A.** Validate in `MainViewModel.onSpoolSelected` / `onFilamentSelected` — clearing the other on every set. ⭐
> - **B.** Let both exist; resolve at write time (filament wins if both set).
>
> **My pick:** A — the dropdown selection is single-pick by design; explicit clears keep state legible. `onSpoolSelected(s)` sets `selectedSpoolId = s.id, selectedFilamentId = null`; `onFilamentSelected(f)` sets `selectedFilamentId = f.id, selectedSpoolId = null`. Dropdown clear sets both to null.

#### 2.1.6 `MainUiState` extensions

- [ ] No new derived state on `MainUiState` for the filament picker.

The filament dropdown collects `SpoolmanRepository.filaments` directly via `collectAsStateWithLifecycle` (or the VM exposes a thin wrapper `filaments: StateFlow<List<SpoolmanFilament>>`). All filaments are listed — no orphan filter, no client-side derivation.

> **Reframe note**: original plan proposed `MainUiState.orphanFilaments` derived state. Dropped per §1.4 reframe — picker shows all filaments.

#### 2.1.7 `SpoolmanFilament` extensions (delta §6)

- [ ] Add five `Float?` fields per delta wire-format summary.

```kotlin
data class SpoolmanFilament(
    val id: Int,
    val name: String? = null,
    val material: String? = null,
    val vendor: SpoolmanVendor? = null,
    val color_hex: String? = null,
    val settings_extruder_temp: Int? = null,
    val settings_bed_temp: Int? = null,
    val extra: Map<String, String>? = null,
    // NEW for U8-Δ-2 / Δ-3:
    val density: Float? = null,
    val diameter: Float? = null,
    val weight: Float? = null,
    val spool_weight: Float? = null,
    val price: Float? = null,
)
```

#### 2.1.8 `CreateFilamentRequest` + `PatchFilamentBody`

- [ ] Lock both shapes.

```kotlin
data class CreateFilamentRequest(
    val name: String?,
    val vendor_id: Int,
    val material: String,
    val color_hex: String,
    val settings_extruder_temp: Int?,
    val settings_bed_temp: Int?,
    val density: Float,           // call-site computes fallback when override null
    val diameter: Float,
    val weight: Float,
    val spool_weight: Float? = null,   // NEW — null omitted by Gson default
    val price: Float? = null,          // NEW
    val extra: Map<String, String>? = null,
)

data class PatchFilamentBody(
    val name: String? = null,
    val settings_extruder_temp: Int? = null,
    val settings_bed_temp: Int? = null,
    val density: Float? = null,
    val diameter: Float? = null,
    val weight: Float? = null,
    val spool_weight: Float? = null,
    val price: Float? = null,
    val extra: Map<String, String>? = null,
)
```

> **Q-U8-8** — `PatchFilamentBody` semantics — does present-and-null mean "clear" (Spoolman convention) or "leave unchanged"?
> - **A.** Present-and-null = clear; absent = leave unchanged. Matches Spoolman PATCH convention. ⭐
> - **B.** Present-and-null = leave unchanged; only non-null fields applied.
>
> **My pick:** A — Spoolman's PATCH convention is documented; we use Gson's default (`@SerializedName(...) val x: Foo? = null` is omitted from the JSON when null). To **clear** a field we'd need to send `"x": null` explicitly, which requires either a `JsonObject` builder or `@JsonAdapter` toggle. **For v2.0 we never clear filament metadata via PATCH** — we only ADD or UPDATE values. So practically: only non-null fields go on the wire (Gson's default). The "clear" semantics distinction is documented in the FD but not exercised.

#### 2.1.9 `MaterialBrandRepository` interface

- [ ] Lock the public surface.

```kotlin
@Singleton
class MaterialBrandRepository @Inject constructor(
    private val materialPresets: MaterialPresetSource,
    private val brandPresets: BrandPresetSource,
    private val userStore: MaterialBrandLocalStore,
    private val spoolman: SpoolmanRepository,
    @AppScope private val scope: CoroutineScope,
) {
    val materials: StateFlow<List<Material>>            // presets ∪ userStore (case-insensitive dedup)
    val brands: StateFlow<List<String>>                 // presets ∪ spoolman.vendors.map { it.name } ∪ userStore (dedup)

    suspend fun addCustomMaterial(material: Material)
    suspend fun addCustomBrand(name: String)
}
```

> **Q-U8-9** — Brand merge precedence (FR-8.3): when "Bambu Lab" exists in both presets AND Spoolman vendors, which entry wins?
> - **A.** Spoolman wins (precedence per components.md §2.4). Display = Spoolman's spelling/casing.
> - **B.** Preset wins. Display = preset's spelling.
> - **C.** Case-insensitive dedup; first occurrence wins (presets enumerated first, so preset wins). ⭐
>
> **My pick:** C — case-insensitive dedup with **presets first** keeps the merged list stable across Spoolman fetches (e.g. `vendors` clears on URL change → list shouldn't reshape). User's typed entries from `userStore` come last in iteration order; dropped if they match (case-insensitive) any earlier entry. Note: components.md §2.4 says "Spoolman entries take precedence" — I'm proposing to relax this to "presets first" because **stability** matters more than "Spoolman is the source of truth" for a string-comparison field. If you disagree, swap the order in the merge function and update components.md.

> **Q-U8-10** — Material merge: presets vs userStore — same dedup rule?
> - **A.** Case-insensitive; presets first (same as brands). User-added "pla" gets deduped against preset "PLA". ⭐ also check v1, we were storing some data in capital and even UI was writing stuff in capital only and had some limot sin chravterd for each feild
> - **B.** Case-sensitive; user can have "pla" (lowercase) as a distinct entry from preset "PLA".
>
> **My pick:** A — typing "pla" when the preset list already has "PLA" should be treated as the same material. The temp ranges from the preset apply (preset wins on equality).

> **Q-U8-11** — `addCustomMaterial(material)` when the material name matches a preset (case-insensitive) — silent no-op, error, or persist anyway?
> - **A.** Silent no-op; UI surfaces a toast "Already exists" and the sheet dismisses. dont presist, keep list clean
> - **B.** Persist anyway; let dedup handle it at read time. ⭐
> - **C.** Throw `IllegalStateException`; sheet shows error.
>
> **My pick:** B — repository writes are dumb; dedup happens at read time. Sheet UI does its own pre-validation (e.g. greys out the Save button when name matches an existing entry). Lets the user re-save the same name with different temps without a fight.

#### 2.1.10 Repository implementation seam — `createSpoolForExistingFilament` vs short-circuit

- [ ] Lock the impl shape.

> **Q-U8-12** — Where does the existing-filament path live in `SpoolmanRepository`?
> - **A.** New method `SpoolmanRepository.createSpoolForExistingFilament(filamentId, expanderOverrides): SpoolmanOutcome<SpoolmanSpool>` — distinct from `createSpoolForNewFilament`. Each method has a clean single responsibility. ⭐
> - **B.** Extend `createSpoolForNewFilament(req: NewFilamentRequest)` to short-circuit when `req.selectedFilamentId != null`. One method; one call site in the use-case.
>
> **My pick:** A — existing-filament path is **semantically different** from create-and-pair (no matcher, no resolve, no filament POST). Two methods make the call site read clearly. Tests stay unentangled.

> **Q-U8-13** — Δ-3 PATCH idempotency check — repository-level (compare against cached `filaments` `StateFlow`) or call-site (VM passes pre-computed delta)?
> - **A.** Repository-level: `patchFilament(filamentId, body)` reads `_filaments.value.find { it.id == filamentId }` and skips fields that match. Idempotent skip is automatic. ⭐
> - **B.** Call-site: VM diffs against the form's prefill snapshot; passes only changed fields. Repository PATCHes whatever it gets.
>
> **My pick:** A — the cache is the source of truth (always fresh after `refresh()`). VM doesn't need to remember the prefill snapshot. PATCH issues only when at least one field differs; if all fields match, `patchFilament` returns `Success(currentFilament)` without an HTTP call.

#### 2.1.11a Defaults table (LOCKED 2026-05-28)

Call-site fallback when the expander field is blank. User can override any via the expander; the override is what gets sent to Spoolman.

| Form / proto field | Spoolman attr | Default when expander blank | Source of default |
|---|---|---|---|
| `densityGPerCm3` | `filament.density` | per-material map (PLA 1.24, ABS 1.04, PETG 1.27, TPU 1.20, ASA 1.07, PC 1.20, Nylon 1.14, PVA 1.19, HIPS 1.04, Other null) | `Material.density: Float?` (Q-U8-5=A) |
| `diameterMm` | `filament.diameter` | **1.75** mm | `MaterialPresetSource.Companion.DEFAULT_DIAMETER_MM` |
| `fullSpoolWeightG` | `filament.weight` | **1000** g | `MaterialPresetSource.Companion.DEFAULT_FULL_SPOOL_WEIGHT_G` |
| `emptySpoolWeightG` | `filament.spool_weight` | **null** (no value sent) | n/a — null omitted by Gson |
| `priceMajor` | `filament.price` | **null** (no value sent) | n/a — null omitted by Gson |

**Constants (locked)**:

```kotlin
object MaterialPresetSource.Companion {
    const val DEFAULT_DIAMETER_MM = 1.75f
    const val DEFAULT_FULL_SPOOL_WEIGHT_G = 1000f
}
```

**Call-site rule** — `CreateAndPairUseCase.makePayload` / `SpoolmanRepository.createSpoolForNewFilament` compute the actual `CreateFilamentRequest` values:

```kotlin
val req = CreateFilamentRequest(
    name = ..., vendor_id = ..., material = m, color_hex = ...,
    settings_extruder_temp = ..., settings_bed_temp = ...,
    density  = form.densityGPerCm3 ?: (Material.lookup(m)?.density ?: PLA_DENSITY_FALLBACK),
    diameter = form.diameterMm ?: DEFAULT_DIAMETER_MM,
    weight   = form.fullSpoolWeightG ?: DEFAULT_FULL_SPOOL_WEIGHT_G,
    spool_weight = form.emptySpoolWeightG,   // null → not on wire
    price        = form.priceMajor,          // null → not on wire
    extra = ...,
)
```

`PLA_DENSITY_FALLBACK = 1.24f` — used when material is "Other" (null density) or a custom material whose density wasn't set when added.

**FD note**: PATCH path (Δ-3) uses the same fallback rules — when computing the diff against the cached filament, a blank expander field is the *default*, not null. This preserves the v2.0 invariant "Spoolman never sees a null required field". For optional fields (`spool_weight`, `price`), blank means "leave as-is on the filament" (Q-U8-8=A semantics: absent = leave unchanged).

#### 2.1.11b Input rules — v1 parity (LOCKED 2026-05-28)

Verified against `main` branch v1.7 source (`MaterialSelector.kt`, `BrandSelector.kt`). Applied identically to **both** the inline "Other → typed" path AND the new `AddCustomMaterialSheet` / `AddCustomBrandSheet` paths.

| Field | Allowed chars | Max length | Casing | Source |
|---|---|---|---|---|
| Material name | `isLetterOrDigit() ‖ '-' ‖ '+'` (no spaces) | 8 | `.uppercase()` | `MaterialSelector.kt:75-77` |
| Brand name | `isLetterOrDigit() ‖ ' ' ‖ '.' ‖ '-'` | 10 | `.replaceFirstChar { it.titlecase() }` (Title Case first letter) | `BrandSelector.kt:78-81` |

**Implementation**: enforced inside `OutlinedTextField.onValueChange` so the user never sees rejected characters. Sheet `Save` button disabled while `name.isBlank()` after sanitisation.

**Note** — v1's `MaterialSelector.kt` comment says "Max 5 characters" but the code uses `.take(8)`. Lock the **code** (8), not the comment. v2 matches v1's actual behaviour.

#### 2.1.11c `MaterialBrandRepository` dedup guarantee (Q-U8-11 carve-out)

To honour the user's concern *"as long as we are not showing multiple same material in dropdown after"*: the merged `materials` flow MUST satisfy `materials.distinctBy { it.name.uppercase() }.size == materials.size`. Same invariant for brands (`name.lowercase()` for brands since brand casing varies — "Bambu Lab" vs "bambu lab" must dedup).

**Test added** (folded into `MaterialBrandRepositoryTest`):

```kotlin
@Test fun materials_never_show_duplicates_after_merge() { … }
@Test fun brands_never_show_duplicates_after_merge() { … }
```

Belt-and-suspenders against any future regression in the dedup logic.

---

#### 2.1.11 `CreateAndPairUseCase` impact

- [ ] Lock the existing-filament wiring.

> **Q-U8-14** — `CreateAndPairUseCase` extension for the existing-filament path: extend the existing use-case, or new `AddSpoolToExistingFilamentUseCase`?
> - **A.** Extend `CreateAndPairUseCase` — branches on `input.selectedFilamentId != null`. Save & Write press routes through the same use-case regardless of new-vs-existing-filament. ⭐
> - **B.** New `AddSpoolToExistingFilamentUseCase` — narrower input contract (no `name`, no `colorHex` etc., since the filament already exists); cleaner SRP.
>
> **My pick:** A — UI flow is the same: Save & Write → write tag → append UID → done. The branch is internal: skip `resolveOrCreateFilament`; call `createSpoolForExistingFilament`; everything else identical. Less duplication. The use-case's `Input` data class gains `selectedFilamentId: Int? = null`.

---

### 2.2 ViewModel — `MainViewModel` extensions

#### 2.2.1 New handlers

- [ ] `onFilamentSelected(filament: SpoolmanFilament?)` — sets `form.selectedFilamentId = filament?.id`, clears `form.selectedSpoolId`. On non-null, prefill form via `FormMapping.fromFilament(filament)` (new helper) — material, vendor, color, variant, temps, expander values from filament metadata.
- [ ] `onFilamentSectionToggled()` — flips `form.filamentSectionExpanded`.
- [ ] `onMoreDetailsToggled()` — flips `form.moreDetailsExpanded`.
- [ ] `onEmptySpoolWeightChanged(s: String)` / `onPriceChanged(s: String)` / `onFullSpoolWeightChanged(s: String)` / `onDiameterChanged(s: String)` / `onDensityChanged(s: String)` — each parses the string to `Float?` (empty → null; non-numeric → keep prior); updates the corresponding `FormState` field.
- [ ] `onAddCustomMaterialConfirmed(material: Material)` — calls `materialBrandRepo.addCustomMaterial(material)`, dismisses sheet, **selects** the newly added material in the form.
- [ ] `onAddCustomBrandConfirmed(name: String)` — calls `materialBrandRepo.addCustomBrand(name)`, dismisses sheet, **selects** the newly added brand.

> **Q-U8-15** — After confirming an add-custom sheet, do we auto-select the new entry in the form?
> - **A.** Yes — user just typed it; selection is the obvious next step. ⭐
> - **B.** No — sheet just commits; user picks from the dropdown next.
>
> **My pick:** A — eliminates the "I added it but where is it" UX. Dropdown re-opens with the new entry highlighted (or simply `form.material = name`).

#### 2.2.2 `onSpoolSelected` extension

- [ ] On `spool != null`: also clear `form.selectedFilamentId = null`.

#### 2.2.3 `onWriteTapped` (existing) — branch routing

- [ ] Wire the existing-filament branch.

```text
if (form.selectedFilamentId != null) → CreateAndPairUseCase with selectedFilamentId carrier
else if (form.selectedSpoolId != null) → existing append-to-spool path
else → existing create-new-filament path
```

#### 2.2.4 Filament-list flow

- [ ] Locked: VM exposes `filaments: StateFlow<List<SpoolmanFilament>>` as a thin re-export of `SpoolmanRepository.filaments`. No filtering, no sorting client-side beyond the picker's display sort (handled inside the `FilamentPicker` Composable).

> **Q-U8-16 — REMOVED** by §1.4 reframe. Original question (archive filter for orphan derivation) is moot now that there's no orphan list. Archived spools handling for the **spool** dropdown is unchanged from U6a (archived hidden).

#### 2.2.5 Test-only injection

- [ ] `FakeMaterialBrandRepository`, `FakeMaterialPresetSource`, `FakeBrandPresetSource`, `FakeMaterialBrandLocalStore`.
- [ ] `MainViewModelTest` ctor extended.

---

### 2.3 Compose UI

**Form layout (post-U8)** — corrected 2026-05-28 to match user direction *"spool visible, then filament hidden with expandable, other stuff then more detailed hidden menu"*:

```
[ ▼ Spool ]                            ← MainScreen (always visible — existing, unchanged)
─── Filament ▾ ───                     ← hidden expander #1, ABOVE the form
[ ▼ Filament ]                         ← lists ALL filaments
[ Material ] [ Brand ]                 ← "other stuff"
[ Color ]
[ Variant ]
[ Temps ]
─── More details ▾ ───                 ← hidden expander #2, BELOW
[ Empty spool weight ]
[ Price ]
[ Full spool weight ]
[ Diameter ]
[ Density ]
[ Save & Write ]
```

(Earlier ASCII in this plan placed the Filament expander between TempPanel and the More details expander — that was inconsistent with the user-direction quote in §1.4. The user-direction wins; the install-time check on 2026-05-28 caught the drift.)

**Default UI**: byte-identical to today's form (both expanders collapsed). Per delta AC-14.1: "Default (collapsed) form layout is byte-identical to U6a."

#### 2.3.1 `FilamentSectionExpander` (NEW, U8-Δ-1)

- [ ] Lock the Compose surface.

- Header row: `Icon(Icons.Default.ExpandMore / ExpandLess)` + `Text("Filament")` + clickable region toggles `form.filamentSectionExpanded`.
- When expanded: a single `FilamentPicker` Composable hosting `ExposedDropdownMenu`.
- `FilamentPicker` lists **all** filaments from `SpoolmanRepository.filaments`, ordered alphabetically by `vendor.name + " " + filament.name + " " + extra.variant` (matches `requirements-delta-orphan-filament-and-extra-fields.md` AC-13.1's ordering, but applied to the full list — not orphan-only).
- Display row format: `vendor.name · filament.name · variant` (no `[#id]`, no weight — those are spool-scope).
- Tapping a row → `MainViewModel.onFilamentSelected(filament)` (sets `form.selectedFilamentId`; clears `form.selectedSpoolId`; prefills form via `FormMapping.fromFilament`).
- Selected-state indicator: when `form.selectedFilamentId != null`, show the picked filament's display string in the picker's "selected" slot + an "X" affordance to clear (`onFilamentSelected(null)`).
- Position: between `TempPanel` and the "More details ▾" expander.
- Default state = collapsed.

#### 2.3.2 `MoreDetailsExpander` (U8-Δ-2)

- [ ] Lock the Compose surface.

- Header row: `Icon(Icons.Default.ExpandMore / ExpandLess)` + `Text("More details")` + clickable region toggles `form.moreDetailsExpanded`.
- When expanded: 5 inline numeric fields, each `OutlinedTextField` with label + suffix (g, $, g, mm, g/cm³) + `KeyboardOptions(keyboardType = Decimal)`.
- Empty input = "no override, use default".
- Default state = collapsed (per AC-14.1 + AC-14.2). State on `FormState.moreDetailsExpanded`.
- Position: between the "Filament ▾" expander and the Save & Write button.

> **Q-U8-17** — Expander row icon — expand-arrow + text, or just text?
> - **A.** `▾`/`▴` Unicode arrow embedded in the text label per delta §2 ("More details ▾" / "More details ▴"). Matches the spec literally.
> - **B.** Material `Icons.Default.ExpandMore` / `ExpandLess` next to plain "More details" text. More native Android idiom. ⭐
>
> **My pick:** B — Material icon scales/rotates with theming; Unicode arrows can render inconsistently across emoji fonts. Same icon style applied to both `FilamentSectionExpander` and `MoreDetailsExpander` for consistency.
>
> Note: AC-14.2 is satisfied either way ("More details" + visible toggle indicator).

#### 2.3.2a Two expanders open at once?

> **Q-U8-18** — Should expanding one section auto-collapse the other?
> - **A.** Independent — both can be open simultaneously. User toggles each manually. ⭐
> - **B.** Mutex — opening "Filament ▾" auto-collapses "More details ▾" and vice-versa.
>
> **My pick:** A — independent. The two sections are distinct workflows; constraining them creates surprise UX (toggle hides the other section the user was looking at). Scrolling handles vertical density.

#### 2.3.2b — REMOVED (was: section ordering in dropdown)

> **Q-U8-19 — REMOVED** by §1.4 reframe. Original question (visual differentiation between filament and spool rows in a sectioned dropdown) is moot now that the spool dropdown stays single-section.

#### 2.3.3 `AddCustomMaterialSheet`

- [ ] Lock the Compose surface.

- `ModalBottomSheet`. Title: "Add custom material".
- Fields:
  - Name (required)
  - Extruder min / max (°C, integer; required, validated min ≤ max)
  - Bed min / max (°C, integer; required, validated min ≤ max)
  - Density (g/cm³, decimal; **optional**, falls back to "Other" preset's null/density-default)
- Actions: `Button("Save")` (primary, disabled while invalid) / `TextButton("Cancel")`.
- Save → `AddCustomMaterialViewModel.onSave()` → `MainViewModel.onAddCustomMaterialConfirmed(material)`.

#### 2.3.4 `AddCustomBrandSheet`

- [ ] Lock the Compose surface.

- `ModalBottomSheet`. Title: "Add custom brand".
- Single field: Brand name (required, non-empty after trim).
- Actions: `Button("Save")` (primary, disabled while invalid) / `TextButton("Cancel")`.
- Save → `AddCustomBrandViewModel.onSave()` → `MainViewModel.onAddCustomBrandConfirmed(name)`.

#### 2.3.5 Sheet hosting — `BottomSheetHost`

- [ ] Add two new branches: `AddCustomMaterialSheet` (when `state.activeFlow is AddingCustomMaterial`) and `AddCustomBrandSheet` (when `state.activeFlow is AddingCustomBrand`).

> **Q-U8-20** — Trigger for opening the add-custom sheets:
> - **A.** Footer row in the picker dropdown ("➕ Add custom material…" / "➕ Add custom brand…"), as already proposed in Q-U8-2 option B. ⭐
> - **B.** Long-press on "Other" preset entry.
> - **C.** Settings-screen entry only.
>
> **My pick:** A — discoverable in the same surface where the user is making a picker selection; consistent with Q-U8-2.

#### 2.3.6 FAB + form gating

- [ ] No new gates beyond existing predicates. The add-custom sheets do not block the form; they only block the picker dropdown they were opened from (sheet is modal over the picker; main form remains editable underneath).
- [ ] Expander toggle is always enabled (form-scope, no IO).

#### 2.3.7 Picker swap — `MaterialPicker` / `BrandPicker`

- [ ] Lock the data-source swap.

- Replace `MaterialDatabase.materials` reference with `materialBrandRepo.materials` (collected via `collectAsStateWithLifecycle`).
- Replace `BrandDatabase.brands` reference with `materialBrandRepo.brands`.
- Add footer row "➕ Add custom material…" (per Q-U8-20).
- `MainViewModel` exposes `materials: StateFlow<List<Material>>` + `brands: StateFlow<List<String>>` thin wrappers around the repository (so the picker components don't take the repository directly).

---

### 2.4 ViewModel test plan (Q-T3=B style; no install gate per Q-T2 — covered by U10)

#### 2.4.1 `MaterialBrandRepositoryTest`

- [ ] **materials merge** — presets only (no user store) → exactly 10 entries.
- [ ] **materials merge with custom** — preset list + 2 custom (one duplicate of "PLA" case-mismatched) → 11 entries; no duplicate "PLA"; preset's "PLA" wins (case-insensitive dedup; presets first).
- [ ] **brands merge** — presets ∪ Spoolman vendors ∪ user store → deduped (case-insensitive); presets first.
- [ ] **brands merge — Spoolman vendor matches preset** — "Bambu Lab" present in both → single entry; preset spelling wins (per Q-U8-9 outcome).
- [ ] **addCustomMaterial persistence** — write → read → `materials.value.contains(it)`.
- [ ] **addCustomBrand persistence** — write → read → `brands.value.contains(it)`.
- [ ] **addCustomMaterial duplicate of preset** (case-insensitive) — repo persists anyway (Q-U8-11=B); read-side dedup hides it.
- [ ] **DataStore restart** — round-trip via in-memory `DataStore<CustomMaterials>` test instance; entries preserved.

#### 2.4.2 `SpoolmanRepositoryPatchFilamentTest`

- [ ] **patch — all values differ** — PATCH issued; HTTP body contains the diff'd fields only; `SpoolmanFilament` updated in cache.
- [ ] **patch — all values match** — no HTTP call; returns `SpoolmanOutcome.Success(currentFilament)`.
- [ ] **patch — partial diff** (one field changed) — HTTP body contains only the one field.
- [ ] **patch — 4xx** → `SpoolmanOutcome.HttpError(code, message)`; cache unchanged.
- [ ] **patch — 5xx** → `SpoolmanOutcome.HttpError`; cache unchanged.
- [ ] **patch — IOException** → `SpoolmanOutcome.NetworkError`; cache unchanged.

#### 2.4.3 `SpoolmanRepositoryCreateForExistingFilamentTest`

- [ ] **happy path — no expander deltas** — `getFilament` called once, `createSpool` called once, no `patchFilament`; `Success(spool)`.
- [ ] **happy path — expander deltas** — `getFilament` → `patchFilament` (only changed fields) → `createSpool`; success.
- [ ] **getFilament 404** → `SpoolmanOutcome.HttpError(404)`; no spool created.
- [ ] **patchFilament fails** → `SpoolmanOutcome.HttpError`; no spool created (fail-fast — `createSpool` not called).
- [ ] **createSpool fails after patch** → `SpoolmanOutcome.HttpError`; PATCH already happened (documented behaviour — partial state is acceptable per delta §2 implementation note: "The spool create is the dependent step; if it fails after PATCH, the filament metadata is already updated, which is the user's intent").

#### 2.4.4 `MainViewModelFilamentPickerTest`

- [ ] **filaments exposed** — VM's `filaments` flow re-emits `SpoolmanRepository.filaments` 1:1 (no filtering, no orphan derivation).
- [ ] **onFilamentSelected** — sets `form.selectedFilamentId`; clears `form.selectedSpoolId`; prefills material/color/temps/variant/expander values from filament metadata.
- [ ] **onFilamentSelected(null)** — clears `form.selectedFilamentId`; form does NOT reset (selection clear is distinct from form clear).
- [ ] **onSpoolSelected clears filament selection** — picks spool → `form.selectedFilamentId == null`.
- [ ] **onWriteTapped routing — filament selected** — `selectedFilamentId != null` → use-case invoked with `selectedFilamentId` carrier; matcher path NOT taken (asserted via fake repository call log).
- [ ] **onWriteTapped routing — spool selected** — `selectedSpoolId != null && selectedFilamentId == null` → existing append-to-spool path (regression test).
- [ ] **onFilamentSectionToggled** — flips `form.filamentSectionExpanded`; does not affect `moreDetailsExpanded`.

#### 2.4.5 `MainViewModelMoreDetailsExpanderTest`

- [ ] **default state** — `form.moreDetailsExpanded == false`; `form.emptySpoolWeightG == null`; etc.
- [ ] **toggle** — `onMoreDetailsToggled()` flips boolean.
- [ ] **value parsing — empty string** — `onPriceChanged("")` → `form.priceMajor == null`.
- [ ] **value parsing — valid decimal** — `onDensityChanged("1.21")` → `form.densityGPerCm3 == 1.21f`.
- [ ] **value parsing — invalid** — `onDiameterChanged("abc")` → `form.diameterMm` unchanged from prior value.
- [ ] **prefill from filament** — `onFilamentSelected(filament with density=1.30)` → `form.densityGPerCm3 == 1.30f`.

#### 2.4.6 `AddCustomMaterialViewModelTest`

- [ ] **default state** — all fields blank; Save disabled.
- [ ] **valid input** — name + temps populated → Save enabled.
- [ ] **temps validation** — `extruderMin > extruderMax` → Save disabled.
- [ ] **onSave** — relays to `MainViewModel.onAddCustomMaterialConfirmed(material)` with the entered values.

#### 2.4.7 `AddCustomBrandViewModelTest`

- [ ] **default state** — name blank; Save disabled.
- [ ] **valid input** — name non-blank → Save enabled.
- [ ] **onSave** — relays to `MainViewModel.onAddCustomBrandConfirmed(name)`.

#### 2.4.8 `CreateAndPairUseCaseTest` extension

- [ ] **selectedFilamentId path — happy** — `input.selectedFilamentId != null` → `spoolman.createSpoolForExistingFilament` invoked; `spoolman.resolveOrCreateFilament` NOT invoked.
- [ ] **selectedFilamentId path — PATCH issued** — when expander deltas present → asserted via call log.

#### 2.4.9 Regression — existing tests

- [ ] All 300 existing tests still pass; U8 introduces no contract changes to U6a/U6b/U7 use-cases beyond the additive `selectedFilamentId` carrier on `CreateAndPairInput`.

---

### 2.5 Verification commands (post-Code-Gen)

- [ ] `./gradlew compileDebugKotlin` ✅
- [ ] `./gradlew testDebugUnitTest` ✅ — running total target: **300 (U7) + ~25–30 (U8) ≈ 325–330 / 325–330**.
- [ ] `./gradlew assembleDebug` ✅ — APK size monitored; flagged for U10 if >36 MB (current baseline 34 MB after U7).
- [ ] **No U8 milestone install gate** (Q-T2=B per `unit-of-work.md` §U8). Manual NFC + Spoolman verification deferred to U10 install gate.

#### U10 manual checklist (will be created in U10) covers

| Scenario | Expected |
|---|---|
| Add custom material | Sheet → save → material appears in `MaterialPicker`; survives app restart. |
| Add custom brand | Sheet → save → brand appears in `BrandPicker`; survives app restart. |
| Material picker dedup | Custom "pla" + preset "PLA" → only one entry visible. |
| Brand merge — Spoolman + preset | Spoolman has "Bambu Lab" + preset has "Bambu Lab" → single entry. |
| Filament picker — filament with 0 spools | Expand "Filament ▾" → pick filament F1 (currently no spools) → form prefills → Save & Write blank tag → exactly 1 new spool created under F1 (no duplicate filament). |
| Filament picker — filament with 1+ spools (deliberate 2nd-spool add) | Expand "Filament ▾" → pick filament F2 (already owns spool S2) → form prefills → Save & Write blank tag → new spool S3 created under F2 (S2 unaffected); no duplicate filament. |
| Filament picker — expander prefill | Pick filament F1 with `density=1.30 g/cm³` stored → expand "More details ▾" → field prefilled with 1.30. |
| Expander PATCH idempotency | Pick filament F1; expand; don't change anything; Save & Write → no PATCH HTTP call (verified via `adb logcat`). |
| Expander PATCH applied | Pick filament F1 with `weight=1000`; expand; change to 750; Save & Write → PATCH issued; F1's `weight` updated in Spoolman web UI. |
| Both expanders independent | Open "Filament ▾" → "More details ▾" stays collapsed; open "More details ▾" → "Filament ▾" stays open. |
| Default form layout | Default form (both expanders collapsed) byte-identical to U7 layout (visual diff). |
| Custom-material dedup vs preset | Add custom "pla" (lowercase) → picker shows preset "PLA" only (no duplicate). |
| Add-custom auto-select | Add "PA-CF" via sheet → form's material field shows "PA-CF" selected. |

---

### 2.6 Out-of-scope guards (explicit for U8)

- ❌ First-class filament editor screen (rejected in delta §3)
- ❌ Spool-scope extra fields (`location`, `comment`)
- ❌ Filament metadata edit without spool-create (deferred per AC-14.5)
- ❌ Settings UI for managing custom entries (rename / delete / reorder) — post-v2.0
- ❌ Spoolman duplicate-filament repair tooling (user-side dedup)
- ❌ APK size review / JDK 17 portability (U10)
- ❌ New install gate (U10 covers manual verification)

---

## 3. Stage-Gate Action

After all `[Answer]:` tags below are filled, generate FD artefacts under `aidlc-docs/construction/u8-pickers-and-filament-metadata/functional-design/`:

| Artefact | Contents |
|---|---|
| `domain-entities.md` | `MaterialPresetSource`, `BrandPresetSource`, `MaterialBrandLocalStore`, `MaterialBrandRepository`, `Material` extension (+density), `FormState` extensions (`selectedFilamentId`, `filamentSectionExpanded`, `moreDetailsExpanded`, five Float overrides), `SpoolmanFilament` extensions, `CreateFilamentRequest` + `PatchFilamentBody`, Proto schemas. |
| `business-rules.md` | FR-8.1..FR-8.5 (presets + custom + dedup), FR-13 (filament picker — reframed; see §1.4), FR-14 (More details expander), FR-15 (PATCH); AC matrix S-8.1..S-8.6; explicit reframe note: orphan/non-orphan distinction dropped, picker shows all filaments, two separate hidden expanders. |
| `business-logic-model.md` | Sequence diagrams (mermaid) for: filament pick → write → exactly-one-spool-created (works for both 0-spool and 1+-spool filaments); expander PATCH idempotency; add-custom-material round-trip; brand merge precedence. |
| `frontend-components.md` | `FilamentSectionExpander` + `FilamentPicker`, `MoreDetailsExpander`, `SpoolmanDropdown` (unchanged from U6a — single-section spool list), `AddCustomMaterialSheet`, `AddCustomBrandSheet`, picker swap, form gating, two-expanders-independent rule. |

Then present the standardized 2-option completion message (Request Changes / Continue to Next Stage) per `construction/functional-design.md`.

---

## 4. Questions — answer block

Fill `[Answer]: ____` for each. ⭐ marks my recommendation.

### Q-U8-1 — `MaterialPresetSource` shape

- A. ⭐ `@Singleton class` injected via Hilt; symmetric with `BrandPresetSource`.
- B. `object` (singleton); matches v1 idiom.

**Answer:** ____ A

### Q-U8-2 — "Other" preset vs add-custom footer

- A. Keep "Other" as preset entry; selecting triggers inline custom field (v1 behaviour).
- B. ⭐ Filter "Other" out of preset list; render "➕ Add custom" footer that opens add-custom sheet. Two distinct UX surfaces.

**Answer:** ____ B

### Q-U8-3 — `CustomMaterial` payload shape

- A. ⭐ Full payload (name + four temp ints + optional density). User specifies temps when adding.
- B. Name only; temps fall back to "Other" preset's defaults.

**Answer:** ____A

### Q-U8-4 — `created_at_epoch_ms` timestamp on custom entries

- A. ⭐ Keep — used as tiebreaker / diagnostic. Not exposed in UI.
- B. Drop — sort alphabetically; lighter schema.

**Answer:** ____ A

### Q-U8-5 — Per-material density default location

- A. ⭐ Add `density: Float?` to `domain/models/Material.kt`; wired into `MaterialPresetSource`. `CustomMaterial` proto gains optional density too.
- B. Keep `Material` unchanged; ship `densityFor(material: String): Float` extension function.

**Answer:** ____ A(also let talk about weigh part too, we might have to do some dfaults there too)

### Q-U8-6 — `selectedFilamentId` placement

- A. ⭐ On `FormState` (alongside `selectedSpoolId`).
- B. On `MainUiState.spoolman` (alongside cache).

**Answer:** ____ A

### Q-U8-7 — `selectedSpoolId` / `selectedFilamentId` mutual exclusivity

- A. ⭐ Validate in setters — clearing the other on every set.
- B. Allow both; resolve at write time.

**Answer:** ____ A

### Q-U8-8 — `PatchFilamentBody` null semantics

- A. ⭐ Present-and-null = clear; absent = leave unchanged. (v2.0 only sends non-null fields, so the "clear" path is documented but unexercised.)
- B. Present-and-null = leave unchanged; only non-null fields applied.

**Answer:** ____ A

### Q-U8-9 — Brand merge precedence — preset vs Spoolman

- A. Spoolman wins (per components.md §2.4 literal).
- B. Preset wins.
- C. ⭐ Case-insensitive dedup; first occurrence wins (presets enumerated first → preset wins). Stable across Spoolman fetches.

**Answer:** ____ C

### Q-U8-10 — Material merge dedup case-sensitivity

- A. ⭐ Case-insensitive; presets first. "pla" deduped against preset "PLA".
- B. Case-sensitive; user can have "pla" as distinct from "PLA".

**Answer:** ____A(check v1 we had some logic on what user could add in UI like only capital and also limot on chracters and some stuff we would capitaliz)

### Q-U8-11 — `addCustomMaterial` when name matches preset (case-insensitive)

- A. Silent no-op + toast "Already exists".
- B. ⭐ Persist anyway; dedup at read time.
- C. Throw error.

**Answer:** ____  B as long as we are not showing multiple same material in dropdown after

### Q-U8-12 — Existing-filament short-circuit location

- A. ⭐ New method `SpoolmanRepository.createSpoolForExistingFilament(...)`; clean SRP.
- B. Extend `createSpoolForNewFilament` with `selectedFilamentId` short-circuit; one method, one call site.

**Answer:** ____ A

### Q-U8-13 — PATCH idempotency check location

- A. ⭐ Repository-level: read cache, skip matching fields, no HTTP call when nothing changed.
- B. Call-site (VM) diffs against prefill snapshot; passes only changed fields.

**Answer:** ____ A

### Q-U8-14 — `CreateAndPairUseCase` extension vs new use-case

- A. ⭐ Extend `CreateAndPairUseCase`; branches on `input.selectedFilamentId != null`.
- B. New `AddSpoolToExistingFilamentUseCase` — narrower input contract.

**Answer:** ____ A

### Q-U8-15 — Auto-select after add-custom confirm

- A. ⭐ Yes — sheet commits; form's material/brand auto-selects the new entry.
- B. No — sheet only commits; user picks from dropdown.

**Answer:** ____ A(user is literally typing next to dropdown, how would they pick more?)

### Q-U8-16 — REMOVED by §1.4 reframe

Original question (archive filter for orphan derivation) is moot — there's no orphan list. The spool-dropdown's existing archive filter (from U6a polish) is unchanged.

**Answer:** N/A

### Q-U8-17 — Expander row icon

- A. Unicode `▾`/`▴` arrows in the label text (delta literal).
- B. ⭐ Material `Icons.Default.ExpandMore` / `ExpandLess` next to plain text.

**Answer:** ____ B

### Q-U8-18 — Two expanders open simultaneously?

- A. ⭐ Independent — both can be open at once.
- B. Mutex — opening one auto-collapses the other.

**Answer:** ____ A

### Q-U8-19 — REMOVED by §1.4 reframe

Original question (filament/spool row visual differentiation in a sectioned dropdown) is moot — the spool dropdown stays single-section.

**Answer:** N/A

### Q-U8-20 — Add-custom sheet trigger

- A. ⭐ Footer row in picker dropdown ("➕ Add custom material…" / "➕ Add custom brand…").
- B. Long-press on "Other" preset entry.
- C. Settings-screen entry only.

**Answer:** ____ A

---

## 5. Test count target (post-FD-locked)

| Bucket | Tests |
|---|---|
| `MaterialBrandRepositoryTest` | 10 |
| `SpoolmanRepositoryPatchFilamentTest` | 6 |
| `SpoolmanRepositoryCreateForExistingFilamentTest` | 5 |
| `MainViewModelFilamentPickerTest` | 7 |
| `MainViewModelMoreDetailsExpanderTest` | 6 |
| `AddCustomMaterialViewModelTest` | 4 |
| `AddCustomBrandViewModelTest` | 3 |
| `CreateAndPairUseCaseTest` extension | 2 |
| **U8 net new** | **~43** |

Running total target after U8: **300 + ~43 = ~343 / ~343**. Final count locked at Code Generation Part 1.
