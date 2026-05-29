# U8 — Domain Entities

**Stage**: CONSTRUCTION → Per-Unit Loop → Functional Design → Domain Entities
**Unit**: U8 — Pickers + Custom Entries + Filament Metadata UX
**Locked**: 2026-05-28

Source plan: `aidlc-docs/construction/plans/u8-pickers-and-filament-metadata-functional-design-plan.md` (FD Part 1, all `[Answer]:` tags filled).

---

## 1. Net-new domain types

### 1.1 `MaterialPresetSource` (FR-8.1; Q-U8-1=A)

Hilt-bound `@Singleton class` that owns the hardcoded material preset list. Replaces v1's `data/local/MaterialDatabase.kt` (`object`-shaped). Same 10 entries, same temp ranges; gains optional `density: Float?` per entry.

```kotlin
package com.spoolpainter.app.data.local.presets

import com.spoolpainter.app.domain.models.Material
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MaterialPresetSource @Inject constructor() {
    val materials: List<Material> = listOf(
        Material("PLA",   190, 220,  40,  65, density = 1.24f),
        Material("ABS",   220, 260,  80, 110, density = 1.04f),
        Material("PETG",  220, 250,  60,  80, density = 1.27f),
        Material("TPU",   210, 230,  40,  60, density = 1.20f),
        Material("ASA",   240, 270, 100, 110, density = 1.07f),
        Material("PC",    270, 310,  80, 110, density = 1.20f),
        Material("Nylon", 240, 280,  60, 100, density = 1.14f),
        Material("PVA",   180, 210,  40,  60, density = 1.19f),
        Material("HIPS",  220, 260, 100, 110, density = 1.04f),
        // "Other" preset removed per Q-U8-2=B; replaced by "➕ Add custom" footer.
    )

    fun getMaterial(name: String): Material? =
        materials.find { it.name.equals(name, ignoreCase = true) }

    companion object {
        const val DEFAULT_DIAMETER_MM         = 1.75f
        const val DEFAULT_FULL_SPOOL_WEIGHT_G = 1000f
        const val PLA_DENSITY_FALLBACK        = 1.24f  // for custom materials w/o density
    }
}
```

### 1.2 `BrandPresetSource` (FR-8.2; Q-U8-1=A)

Hilt-bound `@Singleton class`. Replaces v1's `data/local/BrandDatabase.kt`. "Other" preset removed (replaced by "➕ Add custom" footer).

```kotlin
package com.spoolpainter.app.data.local.presets

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BrandPresetSource @Inject constructor() {
    val brands: List<String> = listOf(
        "Generic",
        "3DHoJor",
        "Bambu Lab",
        "eSUN",
        "Kingroon",
        "SUNLU",
        "Polymaker",
        "TECBEARS",
        "GEEETECH",
        "Elegoo",
        "JAYO",
        // "Other" removed per Q-U8-2=B.
    )
}
```

### 1.3 `CustomMaterials` + `CustomBrands` schema (FR-8.5; Q-U8-3=A, Q-U8-4=A)

JSON-DataStore persisted user-added entries (locked 2026-05-28; switched from proto3 → kotlinx-serialization JSON to match the existing `Settings` DataStore at `data/local/SettingsSerializer.kt`; zero new build infra). Schema lives at `app/src/main/java/com/spoolpainter/app/data/local/userdata/CustomEntries.kt`.

```kotlin
package com.spoolpainter.app.data.local.userdata

import kotlinx.serialization.Serializable

@Serializable
data class CustomMaterial(
    val name: String,                    // sanitised: UPPERCASE, ≤8 chars, alnum + - +
    val defaultExtruderMin: Int,
    val defaultExtruderMax: Int,
    val defaultBedMin: Int,
    val defaultBedMax: Int,
    val createdAtEpochMs: Long,
    val densityGPerCm3: Float? = null,   // optional override; null = inherit fallback
)

@Serializable
data class CustomMaterials(
    val entries: List<CustomMaterial> = emptyList(),
)

@Serializable
data class CustomBrand(
    val name: String,                    // sanitised: TitleCase first letter, ≤10 chars, alnum + space + . + -
    val createdAtEpochMs: Long,
)

@Serializable
data class CustomBrands(
    val entries: List<CustomBrand> = emptyList(),
)
```

Two `Serializer<T>` objects mirror `data/local/SettingsSerializer.kt:10-28` (read empty → `defaultValue`; `Json.decodeFromString` / `encodeToString`; rethrow as `CorruptionException`).

### 1.4 `MaterialBrandLocalStore` (FR-8.5)

Thin JSON-DataStore wrapper. The placeholder-comment at `di/DataStoreModule.kt:16` ("CustomMaterials / CustomBrands DataStores will be added in U8") is replaced by two real `@Provides` methods producing `DataStore<CustomMaterials>` + `DataStore<CustomBrands>` against `dataStoreFile("custom_materials.json")` / `dataStoreFile("custom_brands.json")`.

```kotlin
package com.spoolpainter.app.data.local.userdata

@Singleton
class MaterialBrandLocalStore @Inject constructor(
    private val materialsStore: DataStore<CustomMaterials>,
    private val brandsStore: DataStore<CustomBrands>,
) {
    val materials: Flow<List<CustomMaterial>> =
        materialsStore.data.map { it.entries }
    val brands: Flow<List<CustomBrand>> =
        brandsStore.data.map { it.entries }

    suspend fun addCustomMaterial(material: CustomMaterial) {
        materialsStore.updateData { current ->
            current.copy(entries = current.entries + material)
        }
    }

    suspend fun addCustomBrand(brand: CustomBrand) {
        brandsStore.updateData { current ->
            current.copy(entries = current.entries + brand)
        }
    }
}
```

### 1.5 `MaterialBrandRepository` (FR-8.3, FR-8.5; Q-U8-9=C, Q-U8-10=A, Q-U8-11=B)

Public surface consumed by `MainViewModel` (already wired in U6a behind a placeholder; U8 lands the real impl).

```kotlin
package com.spoolpainter.app.data.local

@Singleton
class MaterialBrandRepository @Inject constructor(
    private val materialPresets: MaterialPresetSource,
    private val brandPresets: BrandPresetSource,
    private val userStore: MaterialBrandLocalStore,
    private val spoolman: SpoolmanRepository,
    @AppScope private val scope: CoroutineScope,
) {
    val materials: StateFlow<List<Material>>
    val brands: StateFlow<List<String>>

    suspend fun addCustomMaterial(material: Material)
    suspend fun addCustomBrand(name: String)
}
```

**Merge semantics** (case-insensitive dedup; presets first; user store last):

```kotlin
val materials = (materialPresets.materials + userStore.materials.toMaterials())
    .distinctBy { it.name.uppercase() }                // Q-U8-10=A

val brands = (brandPresets.brands +
              spoolman.vendors.value.map { it.name } +
              userStore.brands.toNames())
    .distinctBy { it.lowercase() }                     // Q-U8-9=C
```

**Invariant** (asserted in tests):

```kotlin
materials.distinctBy { it.name.uppercase() }.size == materials.size
brands.distinctBy { it.lowercase() }.size == brands.size
```

### 1.6 `Material` extension (Q-U8-5=A)

Existing `domain/models/Material.kt` gains `density: Float?`. Migration: every preset gets a real density; "Other" / custom materials default to null (call site falls back to `PLA_DENSITY_FALLBACK = 1.24f`).

```kotlin
data class Material(
    val name: String,
    val defaultExtruderMinTemp: Int,
    val defaultExtruderMaxTemp: Int,
    val defaultBedMinTemp: Int,
    val defaultBedMaxTemp: Int,
    val density: Float? = null,           // NEW for U8
)
```

The existing `densityFor(material: String): Float` map currently inlined inside `SpoolmanRepository.createSpoolForNewFilament` (per the U6a OPEN-1 fix) is **removed** — call site now reads `MaterialPresetSource.getMaterial(name)?.density ?: PLA_DENSITY_FALLBACK`.

### 1.7 `FormState` extensions (Q-U8-6=A, Q-U8-7=A)

```kotlin
data class FormState(
    // ... existing fields up through rawWriteMode ...

    // U8-Δ-1 — filament picker selection (mutually exclusive with selectedSpoolId)
    val selectedFilamentId: Int? = null,
    val filamentSectionExpanded: Boolean = false,

    // U8-Δ-2 — More details expander state + five nullable overrides
    val moreDetailsExpanded: Boolean = false,
    val emptySpoolWeightG: Float? = null,    // → CreateFilamentRequest.spool_weight
    val priceMajor: Float? = null,            // → CreateFilamentRequest.price
    val fullSpoolWeightG: Float? = null,      // → CreateFilamentRequest.weight (default 1000)
    val diameterMm: Float? = null,            // → CreateFilamentRequest.diameter (default 1.75)
    val densityGPerCm3: Float? = null,        // → CreateFilamentRequest.density (default per-material)
)
```

**Mutual exclusivity** (Q-U8-7=A): enforced at the setters in `MainViewModel`:

- `onSpoolSelected(spool)`     → `form.copy(selectedSpoolId = spool?.id, selectedFilamentId = null)`
- `onFilamentSelected(filament)` → `form.copy(selectedFilamentId = filament?.id, selectedSpoolId = null)`

### 1.8 `SpoolmanFilament` extensions

`domain/models/SpoolmanModels.kt` extended to expose stored filament metadata for prefill:

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

    // NEW for U8-Δ-2 / Δ-3
    val density: Float? = null,
    val diameter: Float? = null,
    val weight: Float? = null,
    val spool_weight: Float? = null,
    val price: Float? = null,
)
```

### 1.9 `CreateFilamentRequest` + `PatchFilamentBody`

`data/remote/spoolman/SpoolmanRequests.kt` updated:

```kotlin
data class CreateFilamentRequest(
    val name: String?,
    val vendor_id: Int,
    val material: String,
    val color_hex: String,
    val settings_extruder_temp: Int?,
    val settings_bed_temp: Int?,
    val density: Float,        // call-site fallback when override null
    val diameter: Float,       // call-site fallback when override null
    val weight: Float,         // call-site fallback when override null
    val spool_weight: Float? = null,   // NEW — null omitted by Gson
    val price: Float? = null,          // NEW — null omitted by Gson
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

**PATCH semantics** (Q-U8-8=A): Spoolman convention is present-and-null = clear; absent = leave unchanged. In v2.0 we never clear filament metadata via PATCH — we only ADD or UPDATE. Gson's default omits null fields, so only changed-non-null fields ride on the wire. The "clear" path is documented but unexercised.

### 1.10 `SpoolmanApi.patchFilament` endpoint (Δ-3)

```kotlin
@PATCH("api/v1/filament/{id}")
suspend fun patchFilament(
    @Path("id") filamentId: Int,
    @Body body: PatchFilamentBody,
): Response<SpoolmanFilament>
```

### 1.11 `SpoolmanRepository` new methods (Δ-1, Δ-3; Q-U8-12=A, Q-U8-13=A)

```kotlin
// Δ-3: idempotent PATCH (cache-checked); Q-U8-13=A
suspend fun patchFilament(
    filamentId: Int,
    body: PatchFilamentBody,
): SpoolmanOutcome<SpoolmanFilament>

// Δ-1: short-circuit existing-filament path; Q-U8-12=A
suspend fun createSpoolForExistingFilament(
    filamentId: Int,
    expanderOverrides: ExpanderOverrides,   // wraps the five Float? fields
): SpoolmanOutcome<SpoolmanSpool>
```

**Idempotency rule** (Q-U8-13=A): `patchFilament` reads `_filaments.value.find { it.id == filamentId }` and skips fields that already match. If all fields match, returns `SpoolmanOutcome.Success(currentFilament)` without an HTTP call.

**Existing-filament sequence**:

1. `getFilament(id)` — fresh fetch (defensive; cache may be stale).
2. If `expanderOverrides` differ from stored → `patchFilament(id, body)` (idempotency-skipped if equal).
3. `createSpool(filament_id = id, extra = ...)` — appends UID via `extra.card_uids` post-create (same plumbing as `createSpoolForNewFilament`).

`ExpanderOverrides` is a private data carrier:

```kotlin
internal data class ExpanderOverrides(
    val density: Float?,
    val diameter: Float?,
    val weight: Float?,
    val spoolWeight: Float?,
    val price: Float?,
)
```

### 1.12 `CreateAndPairUseCase` extension (Q-U8-14=A)

`Input` data class gains `selectedFilamentId: Int? = null`. The use-case branches:

```kotlin
val resolved = when {
    input.selectedFilamentId != null ->
        spoolman.createSpoolForExistingFilament(input.selectedFilamentId, expanderOverrides)
    else ->
        spoolman.createSpoolForNewFilament(req)   // existing path
}
```

Everything downstream (NDEF write + verify + UID append) is identical regardless of branch.

### 1.13 Defaults table (LOCKED 2026-05-28)

Call-site fallback when the expander field is blank.

| Form field | Spoolman attr | Default when expander blank | Source |
|---|---|---|---|
| `densityGPerCm3` | `filament.density` | per-material map (PLA 1.24, …); fallback 1.24 for unknown materials | `Material.density` (Q-U8-5=A) + `PLA_DENSITY_FALLBACK` |
| `diameterMm` | `filament.diameter` | **1.75** mm | `MaterialPresetSource.Companion.DEFAULT_DIAMETER_MM` |
| `fullSpoolWeightG` | `filament.weight` | **1000** g | `MaterialPresetSource.Companion.DEFAULT_FULL_SPOOL_WEIGHT_G` |
| `emptySpoolWeightG` | `filament.spool_weight` | **null** (omitted from wire) | n/a |
| `priceMajor` | `filament.price` | **null** (omitted from wire) | n/a |

**PATCH path uses the same fallbacks**: a blank expander field is the *default*, not null, when computing the diff against the stored filament. Optional fields (`spool_weight`, `price`) blank = "leave as-is on filament" per Q-U8-8=A absent semantics.

### 1.14 Input rules — v1 parity (LOCKED 2026-05-28)

Verified against `main` branch v1.7 source. Applied identically to inline "Other → typed" path AND `AddCustomMaterialSheet` / `AddCustomBrandSheet`.

| Field | Allowed chars | Max length | Casing | v1 source |
|---|---|---|---|---|
| Material name | `isLetterOrDigit() ‖ '-' ‖ '+'` (no spaces) | **8** | `.uppercase()` | `MaterialSelector.kt:75-77` |
| Brand name | `isLetterOrDigit() ‖ ' ' ‖ '.' ‖ '-'` | **10** | Title Case first letter | `BrandSelector.kt:78-81` |

**Implementation rule**: enforced inside `OutlinedTextField.onValueChange` so the user never sees rejected characters appear and disappear. `Save` button disabled while `name.isBlank()` after sanitisation.

**Note**: v1's `MaterialSelector.kt:74` comment says "Max 5 characters" — the code uses `.take(8)`. Lock the **code** (8). v2 matches v1's actual behaviour.

---

## 2. Removed / superseded entities

| Removed | Reason |
|---|---|
| `data/local/MaterialDatabase` (object) | Replaced by `MaterialPresetSource` (Hilt-bound class). |
| `data/local/BrandDatabase` (object) | Replaced by `BrandPresetSource`. |
| `MainUiState.orphanFilaments` (proposed in plan §2.1.6) | **Dropped per §1.4 reframe** — picker shows ALL filaments; no orphan derivation. |
| `MaterialPresetSource."Other"` entry | Replaced by "➕ Add custom material…" footer (Q-U8-2=B). |
| `BrandPresetSource."Other"` entry | Same — replaced by add-custom footer. |
| `densityFor(material: String): Float` map (currently in `SpoolmanRepository`) | Inlined into `MaterialPresetSource` entries via `Material.density` (Q-U8-5=A). |

---

## 3. Removed / superseded entities — placeholders from U1

| Placeholder | Final shape |
|---|---|
| `AddCustomMaterialViewModel(placeholder = true)` | Real VM with name + temp + optional density state; `onSave()` relays to `MainViewModel.onAddCustomMaterialConfirmed(material)`. |
| `AddCustomBrandViewModel(placeholder = true)` | Real VM with name state; `onSave()` relays to `MainViewModel.onAddCustomBrandConfirmed(name)`. |
| `DataStoreModule.kt:16` U8-marker comment | Real `DataStore<CustomMaterials>` + `DataStore<CustomBrands>` JSON providers with `CustomMaterialsSerializer` + `CustomBrandsSerializer` (kotlinx-serialization JSON, mirroring `SettingsSerializer.kt`). |

---

## 4. Trace summary

| Type | FR | Story | Q answer |
|---|---|---|---|
| `MaterialPresetSource` | FR-8.1 | S-8.1 | Q-U8-1=A, Q-U8-5=A |
| `BrandPresetSource` | FR-8.2 | S-8.1 | Q-U8-1=A |
| `MaterialBrandLocalStore` (JSON DataStore) | FR-8.5 | S-8.3, S-8.4 | Q-U8-3=A, Q-U8-4=A |
| `MaterialBrandRepository` | FR-8.3, FR-8.5 | S-8.1..S-8.4 | Q-U8-9=C, Q-U8-10=A, Q-U8-11=B |
| `FormState.selectedFilamentId` + `filamentSectionExpanded` | FR-13 | S-8.5 | Q-U8-6=A, Q-U8-7=A, §1.4 reframe |
| `FormState.moreDetailsExpanded` + 5 overrides | FR-14 | S-8.6 | Q-U8-2=B (add-custom path), Q-U8-15=A |
| `SpoolmanFilament` extension | FR-15 | S-8.6 | (delta wire-format) |
| `CreateFilamentRequest` extension | FR-15 | S-8.6 | Q-U8-8=A |
| `PatchFilamentBody` (NEW) | FR-15 | S-8.6 | Q-U8-8=A |
| `SpoolmanRepository.patchFilament` | FR-15 | S-8.6 | Q-U8-13=A |
| `SpoolmanRepository.createSpoolForExistingFilament` | FR-13 | S-8.5 | Q-U8-12=A |
| `CreateAndPairUseCase` extension | FR-13 | S-8.5 | Q-U8-14=A |
| Defaults table (1.13) | FR-14, FR-15 | S-8.6 | carve-out 2026-05-28 |
| Input rules (1.14) | FR-8.5 | S-8.3, S-8.4 | carve-out 2026-05-28 (v1 parity) |
