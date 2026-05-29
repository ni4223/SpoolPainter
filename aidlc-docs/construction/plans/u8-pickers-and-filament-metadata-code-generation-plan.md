# U8 — Code Generation Plan (Part 1)

**Stage**: CONSTRUCTION → Per-Unit Loop → Code Generation Part 1 (U8)
**Unit**: U8 — Pickers + Custom Entries + Filament Metadata UX
**Authored**: 2026-05-28
**Approval gate**: this plan must be approved before Code Gen Part 2 executes the checkboxes below.

**Inputs**:
- `aidlc-docs/construction/u8-pickers-and-filament-metadata/functional-design/{domain-entities,business-rules,business-logic-model,frontend-components}.md` (FD Part 2, approved 2026-05-28)
- `aidlc-docs/construction/plans/u8-pickers-and-filament-metadata-functional-design-plan.md` Q-U8-1..20 ledger + three carve-outs (defaults table; v1-parity input rules; `distinctBy` invariant)
- `aidlc-docs/inception/application-design/unit-of-work.md` §3-U8
- `aidlc-docs/inception/requirements/requirements.md` FR-8.1..FR-8.5
- `aidlc-docs/inception/requirements/requirements-delta-orphan-filament-and-extra-fields.md` FR-13 / FR-14 / FR-15 (reframed §1.4 of FD plan: orphan distinction dropped; filament picker shows ALL filaments; two independent collapsed expanders)
- `aidlc-docs/inception/user-stories/stories.md` S-8.1..S-8.6

**Branch**: `v2`. Working tree before this plan: 1 commit ahead of `origin/v2` (post-U7 close-out at `665b362`); doc-only deltas this session: U8 FD plan, four FD artefacts, `aidlc-state.md`, `audit.md`, this plan.

**Test count target**: U7 closed at **300 / 300**. After U8: **+43 cases ≈ 343 / 343**. Per-class breakdown in §10.

**FD delta — wire format (LOCKED 2026-05-28)**: `CustomMaterials` / `CustomBrands` switch from proto3 → kotlinx-serialization JSON DataStore. Matches the existing `Settings` DataStore pattern (`data/local/SettingsSerializer.kt:10-28`). No protobuf-gradle-plugin, no `app/src/main/proto/`, no generated source set. Schema content is identical; only wire format differs. FD `domain-entities.md` §1.3 / §1.4 / §3 already updated.

**Out-of-scope guards** (re-stated):
- ❌ First-class filament editor screen (delta §3 reject)
- ❌ Spool-scope extra fields (`location`, `comment`)
- ❌ Edit filament metadata without spool-create (deferred per AC-14.5)
- ❌ Settings UI for managing custom entries (post-v2.0)
- ❌ APK size review / JDK 17 portability (U10)
- ❌ U8 milestone install gate (Q-T2=B → covered by U10 manual matrix in §11)
- ❌ New third-party dependencies (no protobuf plugin; Material 3 + DataStore + Hilt + kotlinx-serialization already on classpath)

---

## §1 — Build dependencies

- [ ] 1.1 No new third-party dependencies. `kotlinx-serialization-json` already on classpath via U1; `androidx.datastore` v1.1.1 already wired (`gradle/libs.versions.toml:15`, `app/build.gradle.kts:100-101`). No `libs.versions.toml` change.
- [ ] 1.2 No `app/build.gradle.kts` change.
- [ ] 1.3 No new permissions. NFC + INTERNET unchanged from prior units.

---

## §2 — Domain entities (FD `domain-entities.md` §1)

### 2.1 Modify `app/src/main/java/com/spoolpainter/app/domain/models/Material.kt`

- [ ] 2.1.1 Add `density: Float? = null` field per FD §1.6 (Q-U8-5=A). Field defaults to null so existing callers compile without churn.

### 2.2 Create `app/src/main/java/com/spoolpainter/app/data/local/presets/MaterialPresetSource.kt`

- [ ] 2.2.1 New file. `@Singleton class` with `materials: List<Material>` (10 entries) per FD §1.1:
  ```kotlin
  Material("PLA",   190, 220,  40,  65, density = 1.24f),
  Material("ABS",   220, 260,  80, 110, density = 1.04f),
  Material("PETG",  220, 250,  60,  80, density = 1.27f),
  Material("TPU",   210, 230,  40,  60, density = 1.20f),
  Material("ASA",   240, 270, 100, 110, density = 1.07f),
  Material("PC",    270, 310,  80, 110, density = 1.20f),
  Material("Nylon", 240, 280,  60, 100, density = 1.14f),
  Material("PVA",   180, 210,  40,  60, density = 1.19f),
  Material("HIPS",  220, 260, 100, 110, density = 1.04f),
  ```
  Note: 9 entries (v1's `MaterialDatabase` had 10 incl. "Other"; "Other" filtered per Q-U8-2=B).
- [ ] 2.2.2 Add `fun getMaterial(name: String): Material?` (case-insensitive lookup).
- [ ] 2.2.3 Add `companion object` with three constants per FD §1.1: `DEFAULT_DIAMETER_MM = 1.75f`, `DEFAULT_FULL_SPOOL_WEIGHT_G = 1000f`, `PLA_DENSITY_FALLBACK = 1.24f`.

### 2.3 Create `app/src/main/java/com/spoolpainter/app/data/local/presets/BrandPresetSource.kt`

- [ ] 2.3.1 New file. `@Singleton class` with `brands: List<String>` (11 entries) per FD §1.2. Excludes v1's "Other" (Q-U8-2=B). Order: `Generic, 3DHoJor, Bambu Lab, eSUN, Kingroon, SUNLU, Polymaker, TECBEARS, GEEETECH, Elegoo, JAYO`.

### 2.4 Delete legacy preset objects

- [ ] 2.4.1 Delete `app/src/main/java/com/spoolpainter/app/data/local/MaterialDatabase.kt` (after §6 picker swap migrates callers).
- [ ] 2.4.2 Delete `app/src/main/java/com/spoolpainter/app/data/local/BrandDatabase.kt` (same).
- [ ] 2.4.3 Verify no remaining references via `rg "MaterialDatabase\b|BrandDatabase\b"` (production sources) — the U6a inline `densityFor` map in `SpoolmanRepository.createSpoolForNewFilament` is also retired; density now reads from `MaterialPresetSource.getMaterial(name)?.density` per §3.5.

### 2.5 Create `app/src/main/java/com/spoolpainter/app/data/local/userdata/CustomEntries.kt`

- [ ] 2.5.1 New file. Four `@Serializable data class`es per FD §1.3 (post-JSON-delta):
  - `CustomMaterial(name, defaultExtruderMin, defaultExtruderMax, defaultBedMin, defaultBedMax, createdAtEpochMs, densityGPerCm3: Float? = null)`
  - `CustomMaterials(entries: List<CustomMaterial> = emptyList())`
  - `CustomBrand(name, createdAtEpochMs)`
  - `CustomBrands(entries: List<CustomBrand> = emptyList())`

### 2.6 Create `app/src/main/java/com/spoolpainter/app/data/local/userdata/CustomMaterialsSerializer.kt`

- [ ] 2.6.1 New file. `object CustomMaterialsSerializer : Serializer<CustomMaterials>` mirroring `data/local/SettingsSerializer.kt:10-28`. `defaultValue = CustomMaterials()`; read empty bytes → default; rethrow `SerializationException` as `CorruptionException`.

### 2.7 Create `app/src/main/java/com/spoolpainter/app/data/local/userdata/CustomBrandsSerializer.kt`

- [ ] 2.7.1 New file. Same shape as §2.6 but for `CustomBrands`.

### 2.8 Create `app/src/main/java/com/spoolpainter/app/data/local/userdata/MaterialBrandLocalStore.kt`

- [ ] 2.8.1 New file. `@Singleton class` per FD §1.4 (post-JSON-delta):
  ```kotlin
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
          materialsStore.updateData { it.copy(entries = it.entries + material) }
      }
      suspend fun addCustomBrand(brand: CustomBrand) {
          brandsStore.updateData { it.copy(entries = it.entries + brand) }
      }
  }
  ```

### 2.9 Modify `app/src/main/java/com/spoolpainter/app/di/DataStoreModule.kt`

- [ ] 2.9.1 Replace the U8-marker comment (`// CustomMaterials / CustomBrands DataStores will be added in U8 ...`) with two real `@Provides @Singleton` methods producing `DataStore<CustomMaterials>` (file `custom_materials.json`) + `DataStore<CustomBrands>` (file `custom_brands.json`). Pattern matches the existing `provideSettingsDataStore` (lines 19-26).

### 2.10 Create `app/src/main/java/com/spoolpainter/app/data/local/MaterialBrandRepository.kt`

- [ ] 2.10.1 New file. `@Singleton class` per FD §1.5:
  ```kotlin
  @Singleton
  class MaterialBrandRepository @Inject constructor(
      private val materialPresets: MaterialPresetSource,
      private val brandPresets: BrandPresetSource,
      private val userStore: MaterialBrandLocalStore,
      private val spoolman: SpoolmanRepository,
      @AppScope private val scope: CoroutineScope,
  ) {
      val materials: StateFlow<List<Material>>   // presets ∪ userStore (case-insensitive dedup; presets first)
      val brands: StateFlow<List<String>>        // presets ∪ spoolman.vendors ∪ userStore (case-insensitive dedup; presets first)

      suspend fun addCustomMaterial(material: Material)
      suspend fun addCustomBrand(name: String)
  }
  ```
- [ ] 2.10.2 Implementation: combine flows via `combine(presets, userStore.materials, ...)` then `.distinctBy { it.name.uppercase() }` (Q-U8-10=A); for brands `.distinctBy { it.lowercase() }` (Q-U8-9=C). `stateIn(scope, SharingStarted.Eagerly, initialPresetList)`.
- [ ] 2.10.3 `addCustomMaterial(material)` constructs a `CustomMaterial` (with `createdAtEpochMs = System.currentTimeMillis()`) and delegates to `userStore.addCustomMaterial`. Same for `addCustomBrand`. Per Q-U8-11=B, repository writes are dumb — dedup happens at read time.

### 2.11 Modify `app/src/main/java/com/spoolpainter/app/domain/models/SpoolmanModels.kt`

- [ ] 2.11.1 Extend `data class SpoolmanFilament(...)` with five `Float? = null` fields per FD §1.8: `density`, `diameter`, `weight`, `spool_weight`, `price`. Gson tolerates absent fields; existing call sites compile unchanged.

### 2.12 Modify `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanRequests.kt`

- [ ] 2.12.1 Extend `data class CreateFilamentRequest(...)` with `spool_weight: Float? = null` + `price: Float? = null` per FD §1.9. Existing required fields (`density`, `diameter`, `weight`) keep `Float` non-null type (call-site fallback now in `MaterialPresetSource` + `CreateAndPairUseCase`).
- [ ] 2.12.2 Add `data class PatchFilamentBody(name, settings_extruder_temp, settings_bed_temp, density, diameter, weight, spool_weight, price, extra)` — all fields nullable with default null per FD §1.9. Gson omits null fields by default → matches Q-U8-8=A "absent = leave unchanged" semantics.
- [ ] 2.12.3 Add internal `data class ExpanderOverrides(density, diameter, weight, spoolWeight, price)` carrier per FD §1.11. Used by `createSpoolForNewFilament` + `createSpoolForExistingFilament`. `internal` visibility is enough; not exposed to UI.

### 2.13 Modify `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanApi.kt`

- [ ] 2.13.1 Add `@PATCH("api/v1/filament/{id}") suspend fun patchFilament(@Path("id") filamentId: Int, @Body body: PatchFilamentBody): Response<SpoolmanFilament>` per FD §1.10.

### 2.14 Modify `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanRepository.kt`

- [ ] 2.14.1 Add `suspend fun patchFilament(filamentId: Int, body: PatchFilamentBody): SpoolmanOutcome<SpoolmanFilament>` per FD §1.11 / Q-U8-13=A:
  - Read `_filaments.value.find { it.id == filamentId }` (the cache).
  - Build a sparse `PatchFilamentBody` containing only fields whose cached value differs from the requested value.
  - If sparse body is empty → return `SpoolmanOutcome.Success(currentCachedFilament)` without HTTP.
  - Else call the API; on success update `_filaments.value` to swap in the new filament.
- [ ] 2.14.2 Add `suspend fun createSpoolForExistingFilament(filamentId: Int, expanderOverrides: ExpanderOverrides): SpoolmanOutcome<SpoolmanSpool>` per FD §1.11 / Q-U8-12=A:
  - `getFilament(filamentId)` (defensive fresh fetch); on failure → propagate `HttpError`/`NetworkError` early.
  - If `expanderOverrides` differ → `patchFilament(...)` (idempotency-skipped if equal); fail-fast on non-Success.
  - `createSpool(filament_id = filamentId, weight = expanderOverrides.weight ?: cachedFilament.weight ?: DEFAULT_FULL_SPOOL_WEIGHT_G)` — wraps the same plumbing as `createSpoolForNewFilament` minus the filament POST.
  - Append UID via `extra.card_uids` post-create — same call site contract as `createSpoolForNewFilament` (caller-side append after the tap).
  - **Partial-state contract** (per FD `business-rules.md` §4): if PATCH succeeds and `createSpool` fails, return `HttpError` from `createSpool` — partial state (filament-updated, spool-not-created) is acceptable per delta §2.
- [ ] 2.14.3 Extend `createSpoolForNewFilament` to accept `expanderOverrides: ExpanderOverrides` and forward to `CreateFilamentRequest`. Existing inline `densityFor` map is removed; density resolves via `materialPresets.getMaterial(material)?.density ?: PLA_DENSITY_FALLBACK`. Defaults table: see FD §1.13.
- [ ] 2.14.4 Inject `MaterialPresetSource` into `SpoolmanRepository` ctor (single-line ctor extension; Hilt rebinds; existing tests unaffected once their fakes are extended in §10).

### 2.15 Modify `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainUiState.kt`

- [ ] 2.15.1 Extend `data class FormState(...)` with eight new fields per FD §1.7:
  - `selectedFilamentId: Int? = null`
  - `filamentSectionExpanded: Boolean = false`
  - `moreDetailsExpanded: Boolean = false`
  - `emptySpoolWeightG: Float? = null`
  - `priceMajor: Float? = null`
  - `fullSpoolWeightG: Float? = null`
  - `diameterMm: Float? = null`
  - `densityGPerCm3: Float? = null`
- [ ] 2.15.2 Extend `sealed interface ActiveFlow` with two new variants per FD `frontend-components.md` §1.4:
  - `data object AddingCustomMaterial : ActiveFlow`
  - `data object AddingCustomBrand : ActiveFlow`

### 2.16 Modify `app/src/main/java/com/spoolpainter/app/domain/usecases/CreateAndPairUseCase.kt`

- [ ] 2.16.1 Extend `data class Input(...)` with `selectedFilamentId: Int? = null` per FD §1.12 / Q-U8-14=A.
- [ ] 2.16.2 Extend `Input` with `expanderOverrides: ExpanderOverrides` (default = all-null instance) so the create-new-filament path can also forward expander values.
- [ ] 2.16.3 Branch in `invoke`:
  ```kotlin
  val resolved = when {
      input.selectedFilamentId != null ->
          spoolman.createSpoolForExistingFilament(input.selectedFilamentId, input.expanderOverrides)
      else ->
          spoolman.createSpoolForNewFilament(req, input.expanderOverrides)
  }
  ```
  Downstream NDEF write + verify + UID append unchanged.

---

## §3 — Repository / use-case wiring (FD `business-logic-model.md`)

### 3.1 New `FormMapping.fromFilament(filament: SpoolmanFilament): FormState`

- [ ] 3.1.1 Add new helper to `ui/screens/main/FormMapping.kt` (existing file). Maps:
  - `material` ← `filament.material`
  - `brand` ← `filament.vendor?.name`
  - `colorHex` ← `filament.color_hex` (canonicalised via `ColorHexCodec`)
  - `variant` ← `filament.extra?.get("variant")` (per U6b matcher canonicalisation)
  - `name` ← `filament.name`
  - `temps` ← `filament.settings_extruder_temp` / `settings_bed_temp`
  - 5 expander fields ← `filament.density` / `diameter` / `weight` / `spool_weight` / `price` (preserved as-is — null means "no stored value")
  - `selectedFilamentId` ← `filament.id`
  - `selectedSpoolId` ← null (mutex per Q-U8-7=A)
- [ ] 3.1.2 Existing `FormMapping.fromSpoolman(spool)` unchanged.

### 3.2 No changes to `MoveOnBindUseCase` / `TwoTagUseCase` / `RawWriteUseCase` / `VendorUidOnlyPairUseCase`

- [ ] 3.2.1 U8 is additive at the use-case layer — only `CreateAndPairUseCase` extends. The vendor / raw-write / two-tag paths pick up `expanderOverrides` automatically since they all converge on `createSpoolForNewFilament` → which now accepts `ExpanderOverrides`.

---

## §4 — ViewModel (FD `frontend-components.md`)

### 4.1 Modify `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainViewModel.kt`

- [ ] 4.1.1 Extend ctor with `private val materialBrandRepo: MaterialBrandRepository`. Hilt rebinds.
- [ ] 4.1.2 Expose three new `StateFlow`s (thin re-exports — pickers don't take repository directly):
  - `val filaments: StateFlow<List<SpoolmanFilament>>` re-exports `spoolmanRepo.filaments`
  - `val materials: StateFlow<List<Material>>` re-exports `materialBrandRepo.materials`
  - `val brands: StateFlow<List<String>>` re-exports `materialBrandRepo.brands`
- [ ] 4.1.3 New handler `fun onFilamentSelected(filament: SpoolmanFilament?)`:
  - On null: `_state.update { s -> s.copy(form = s.form.copy(selectedFilamentId = null)) }` (form NOT reset).
  - On non-null: `_state.update { s -> s.copy(form = FormMapping.fromFilament(filament).copy(filamentSectionExpanded = s.form.filamentSectionExpanded, moreDetailsExpanded = s.form.moreDetailsExpanded)) }` (preserve expander toggles; clears `selectedSpoolId` per mutex).
- [ ] 4.1.4 New handlers:
  - `fun onFilamentSectionToggled()` — flips `form.filamentSectionExpanded`.
  - `fun onMoreDetailsToggled()` — flips `form.moreDetailsExpanded`.
- [ ] 4.1.5 Five numeric input handlers — each parses string with `text.takeIf { it.isNotEmpty() }?.toFloatOrNull()`; empty string → null override; non-numeric → keep prior value (no surprise reset). One handler per field:
  - `fun onEmptySpoolWeightChanged(s: String)`
  - `fun onPriceChanged(s: String)`
  - `fun onFullSpoolWeightChanged(s: String)`
  - `fun onDiameterChanged(s: String)`
  - `fun onDensityChanged(s: String)`
- [ ] 4.1.6 Add-custom flow handlers:
  - `fun onOpenAddCustomMaterialSheet()` — `_state.update { it.copy(activeFlow = ActiveFlow.AddingCustomMaterial) }`.
  - `fun onOpenAddCustomBrandSheet()` — `_state.update { it.copy(activeFlow = ActiveFlow.AddingCustomBrand) }`.
  - `fun onAddCustomSheetDismissed()` — `_state.update { it.copy(activeFlow = ActiveFlow.Idle) }`.
  - `fun onAddCustomMaterialConfirmed(material: Material)` — `viewModelScope.launch { materialBrandRepo.addCustomMaterial(material) }`; auto-select via `_state.update { it.copy(form = it.form.copy(material = material.name), activeFlow = ActiveFlow.Idle) }` (Q-U8-15=A).
  - `fun onAddCustomBrandConfirmed(name: String)` — same pattern; sets `form.brand = name`.
- [ ] 4.1.7 Extend `onSpoolSelected(spool)` to clear `selectedFilamentId` on non-null pick (mutex per Q-U8-7=A).
- [ ] 4.1.8 Extend `onWriteTapped` routing to compose `expanderOverrides` from `form` and pass to `CreateAndPairUseCase.Input`. Branch when `form.selectedFilamentId != null` — set `Input.selectedFilamentId`; existing append-to-spool branch (`selectedSpoolId != null`) unchanged.

### 4.2 Replace `app/src/main/java/com/spoolpainter/app/ui/components/sheets/AddCustomMaterialViewModel.kt`

- [ ] 4.2.1 Replace U1 placeholder shape (`UiState(placeholder = true)`) with real form ViewModel. Hilt-bound. Internal state via `MutableStateFlow<UiState>`:
  ```kotlin
  data class UiState(
      val name: String = "",
      val extruderMin: String = "",
      val extruderMax: String = "",
      val bedMin: String = "",
      val bedMax: String = "",
      val density: String = "",        // optional; blank → null
  ) {
      val saveEnabled: Boolean get() =
          name.isNotBlank() &&
          extruderMin.toIntOrNull()?.let { min -> extruderMax.toIntOrNull()?.let { max -> min <= max } == true } == true &&
          bedMin.toIntOrNull()?.let { min -> bedMax.toIntOrNull()?.let { max -> min <= max } == true } == true
  }
  ```
- [ ] 4.2.2 Input filtering for `name` (per FD §1.14): inside `onNameChanged`, sanitise with `it.filter { ch -> ch.isLetterOrDigit() || ch == '-' || ch == '+' }.uppercase().take(8)`.
- [ ] 4.2.3 `fun onSave(onConfirmed: (Material) -> Unit)` — builds a `Material` from the parsed UI state (density parsed via `text.toFloatOrNull()`); invokes `onConfirmed(material)`. Sheet host (in `BottomSheetHost`) wires this to `MainViewModel.onAddCustomMaterialConfirmed`.

### 4.3 Replace `app/src/main/java/com/spoolpainter/app/ui/components/sheets/AddCustomBrandViewModel.kt`

- [ ] 4.3.1 Replace U1 placeholder. State: `data class UiState(val name: String = "")`. `saveEnabled = name.isNotBlank()`.
- [ ] 4.3.2 Input filtering for `name` (per FD §1.14): sanitise with `it.filter { ch -> ch.isLetterOrDigit() || ch == ' ' || ch == '.' || ch == '-' }.replaceFirstChar { it.titlecase() }.take(10)`.
- [ ] 4.3.3 `fun onSave(onConfirmed: (String) -> Unit)`.

---

## §5 — Compose UI (FD `frontend-components.md` §3)

### 5.1 Create `app/src/main/java/com/spoolpainter/app/ui/components/FilamentSectionExpander.kt`

- [ ] 5.1.1 New file. Compose surface per FD `frontend-components.md` §3.1:
  - Header `Row` with `Icons.Default.ExpandMore`/`ExpandLess` (Q-U8-17=B) + `Text("Filament")` + clickable region toggles section.
  - When expanded: hosts `FilamentPicker`.
  - `data-testid` style identifiers: `expander-filament-header`, `expander-filament-content`.

### 5.2 Create `app/src/main/java/com/spoolpainter/app/ui/components/FilamentPicker.kt`

- [ ] 5.2.1 New file. `ExposedDropdownMenuBox` + `ExposedDropdownMenu` listing **all** filaments per §1.4 reframe.
- [ ] 5.2.2 Sort: alphabetical by `vendor.name + " " + filament.name + " " + (extra.variant ?: "")`.
- [ ] 5.2.3 Display row format: `vendor.name · filament.name · variant` (no `[#id]`, no weight).
- [ ] 5.2.4 Tap row → `onFilamentSelected(filament)`. Selected-state: show display string in field + clear-X (`onFilamentSelected(null)`).
- [ ] 5.2.5 `data-testid` identifiers: `filament-picker-input`, `filament-picker-row-{filament.id}`, `filament-picker-clear`.

### 5.3 Create `app/src/main/java/com/spoolpainter/app/ui/components/MoreDetailsExpander.kt`

- [ ] 5.3.1 New file. Per FD §3.2:
  - Header row: same icon style as §5.1 + `Text("More details")`.
  - Body (when expanded): 5 `OutlinedTextField`s (decimal keyboard, suffix labels):
    - "Empty spool weight" (suffix `g`)
    - "Price" (suffix `$`)
    - "Full spool weight" (suffix `g`)
    - "Diameter" (suffix `mm`)
    - "Density" (suffix `g/cm³`)
  - Each text field bound to corresponding `form.*` field; empty input = null override.
- [ ] 5.3.2 `data-testid` identifiers: `more-details-header`, `more-details-{field}` (e.g. `more-details-density`).

### 5.4 Create `app/src/main/java/com/spoolpainter/app/ui/components/sheets/AddCustomMaterialSheet.kt`

- [ ] 5.4.1 New file. `ModalBottomSheet` + form fields per FD §3.3:
  - Title: "Add custom material".
  - Fields: Name, Extruder min/max (°C, integer), Bed min/max (°C, integer), Density (g/cm³, optional).
  - `Button("Save")` (primary, disabled while invalid) + `TextButton("Cancel")`.
  - `data-testid` identifiers: `sheet-add-material-{name|extruder-min|extruder-max|bed-min|bed-max|density|save|cancel}`.

### 5.5 Create `app/src/main/java/com/spoolpainter/app/ui/components/sheets/AddCustomBrandSheet.kt`

- [ ] 5.5.1 New file. `ModalBottomSheet` per FD §3.4:
  - Title: "Add custom brand".
  - Single field: Brand name.
  - `Save` / `Cancel` actions.
  - `data-testid` identifiers: `sheet-add-brand-{name|save|cancel}`.

### 5.6 Modify `app/src/main/java/com/spoolpainter/app/ui/components/sheets/BottomSheetHost.kt`

- [ ] 5.6.1 Add two new branches to the existing `when (state.activeFlow)` selector:
  - `is ActiveFlow.AddingCustomMaterial` → render `AddCustomMaterialSheet` with `hiltViewModel<AddCustomMaterialViewModel>()` + dismiss callback wired to `viewModel.onAddCustomSheetDismissed()`.
  - `is ActiveFlow.AddingCustomBrand` → render `AddCustomBrandSheet`.

### 5.7 Modify `app/src/main/java/com/spoolpainter/app/ui/components/MaterialPicker.kt`

- [ ] 5.7.1 Replace `MaterialDatabase.materials` reference with `materials` flow collected via `collectAsStateWithLifecycle`.
- [ ] 5.7.2 Add footer row "➕ Add custom material…" (Q-U8-20=A) — clickable, calls `onOpenAddCustomMaterialSheet`.
- [ ] 5.7.3 Inline "Other → typed" path (existing U6a) **preserved**: typing in the inline custom field continues to work unchanged. Add-custom sheet is a distinct persistent surface.

### 5.8 Modify `app/src/main/java/com/spoolpainter/app/ui/components/BrandPicker.kt`

- [ ] 5.8.1 Same pattern as §5.7 for brand: swap `BrandDatabase.brands` → `brands` flow; add "➕ Add custom brand…" footer.

### 5.9 Modify `app/src/main/java/com/spoolpainter/app/ui/components/FilamentForm.kt`

- [ ] 5.9.1 Insert `FilamentSectionExpander` between the temp panel and the `MoreDetailsExpander`.
- [ ] 5.9.2 Insert `MoreDetailsExpander` between `FilamentSectionExpander` and the `Save & Write` button.
- [ ] 5.9.3 Both expanders independent (Q-U8-18=A); default both collapsed (AC-14.1).
- [ ] 5.9.4 No layout change to the always-visible portion (spool dropdown + material/brand/color/variant/temps) — visual diff against U7 is byte-identical when both expanders are collapsed.

### 5.10 Modify `MainScreen` / `MainScreenContent` if needed

- [ ] 5.10.1 Likely no change — `FilamentForm` is hosted inside `MainScreenContent` already; no top-level layout shift. Confirm at code-gen time.

---

## §6 — Tests (FD plan §2.4)

### 6.1 Create `app/src/test/java/com/spoolpainter/app/data/local/MaterialBrandRepositoryTest.kt`

- [ ] 6.1.1 **materials merge — presets only** → exactly 9 entries (post "Other"-removal).
- [ ] 6.1.2 **materials merge with custom** — preset list + 2 custom (one duplicate of "PLA" case-mismatched) → 10 entries; no duplicate "PLA"; preset's "PLA" wins.
- [ ] 6.1.3 **brands merge** — presets ∪ Spoolman vendors ∪ user store → deduped (case-insensitive); presets first.
- [ ] 6.1.4 **brands merge — Spoolman vendor matches preset** — "Bambu Lab" present in both → single entry; preset spelling wins.
- [ ] 6.1.5 **addCustomMaterial persistence** — write → read → `materials.value.contains(it)`.
- [ ] 6.1.6 **addCustomBrand persistence** — write → read → `brands.value.contains(it)`.
- [ ] 6.1.7 **addCustomMaterial duplicate of preset** (case-insensitive) — repo persists anyway (Q-U8-11=B); read-side dedup hides it.
- [ ] 6.1.8 **DataStore restart** — round-trip via in-memory `DataStore<CustomMaterials>`; entries preserved.
- [ ] 6.1.9 **distinctBy invariant — materials** — `materials.distinctBy { it.name.uppercase() }.size == materials.size`.
- [ ] 6.1.10 **distinctBy invariant — brands** — same for `lowercase()`.

### 6.2 Create `app/src/test/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanRepositoryPatchFilamentTest.kt`

- [ ] 6.2.1 **patch — all values differ** — PATCH issued; HTTP body contains the diff'd fields only; `_filaments` cache updated.
- [ ] 6.2.2 **patch — all values match** — no HTTP call; returns `Success(currentFilament)`.
- [ ] 6.2.3 **patch — partial diff** (one field changed) — HTTP body contains only the one field.
- [ ] 6.2.4 **patch — 4xx** → `HttpError(code, message)`; cache unchanged.
- [ ] 6.2.5 **patch — 5xx** → `HttpError`; cache unchanged.
- [ ] 6.2.6 **patch — IOException** → `NetworkError`; cache unchanged.

### 6.3 Create `app/src/test/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanRepositoryCreateForExistingFilamentTest.kt`

- [ ] 6.3.1 **happy path — no expander deltas** — `getFilament` once, `createSpool` once, no `patchFilament`; `Success(spool)`.
- [ ] 6.3.2 **happy path — expander deltas** — `getFilament` → `patchFilament` (only changed fields) → `createSpool`; success.
- [ ] 6.3.3 **getFilament 404** → `HttpError(404)`; no spool created.
- [ ] 6.3.4 **patchFilament fails** → `HttpError`; no spool created (fail-fast).
- [ ] 6.3.5 **createSpool fails after PATCH** → `HttpError`; PATCH already applied (partial state acceptable per delta §2).

### 6.4 Create `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelFilamentPickerTest.kt`

- [ ] 6.4.1 **filaments exposed** — VM's `filaments` flow re-emits `spoolmanRepo.filaments` 1:1 (no filtering).
- [ ] 6.4.2 **onFilamentSelected** — sets `form.selectedFilamentId`; clears `form.selectedSpoolId`; prefills material/color/temps/variant/expander values.
- [ ] 6.4.3 **onFilamentSelected(null)** — clears `selectedFilamentId`; form NOT reset.
- [ ] 6.4.4 **onSpoolSelected clears filament selection** — picks spool → `form.selectedFilamentId == null`.
- [ ] 6.4.5 **onWriteTapped routing — filament selected** — `selectedFilamentId != null` → use-case invoked with `Input.selectedFilamentId` carrier; matcher path NOT taken.
- [ ] 6.4.6 **onWriteTapped routing — spool selected** — regression: `selectedSpoolId != null && selectedFilamentId == null` → existing append-to-spool path.
- [ ] 6.4.7 **onFilamentSectionToggled** — flips `filamentSectionExpanded`; does not affect `moreDetailsExpanded`.

### 6.5 Create `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelMoreDetailsExpanderTest.kt`

- [ ] 6.5.1 **default state** — `moreDetailsExpanded == false`; all 5 overrides null.
- [ ] 6.5.2 **toggle** — `onMoreDetailsToggled()` flips boolean.
- [ ] 6.5.3 **value parsing — empty string** — `onPriceChanged("")` → `priceMajor == null`.
- [ ] 6.5.4 **value parsing — valid decimal** — `onDensityChanged("1.21")` → `densityGPerCm3 == 1.21f`.
- [ ] 6.5.5 **value parsing — invalid** — `onDiameterChanged("abc")` → `diameterMm` unchanged from prior value.
- [ ] 6.5.6 **prefill from filament** — `onFilamentSelected(filament with density=1.30f)` → `densityGPerCm3 == 1.30f`.

### 6.6 Replace `app/src/test/java/com/spoolpainter/app/ui/components/sheets/AddCustomMaterialViewModelTest.kt` (existing U1 placeholder)

- [ ] 6.6.1 **default state** — all fields blank; `saveEnabled == false`.
- [ ] 6.6.2 **valid input** — name + temps populated → `saveEnabled == true`.
- [ ] 6.6.3 **temps validation** — `extruderMin > extruderMax` → `saveEnabled == false`.
- [ ] 6.6.4 **input sanitisation** — `onNameChanged("pa-cf 12!#")` → state.name == "PA-CF12" (uppercased, alnum + `-` + `+`, trimmed to 8).

### 6.7 Replace `app/src/test/java/com/spoolpainter/app/ui/components/sheets/AddCustomBrandViewModelTest.kt` (existing U1 placeholder)

- [ ] 6.7.1 **default state** — name blank; `saveEnabled == false`.
- [ ] 6.7.2 **valid input** — name non-blank → `saveEnabled == true`.
- [ ] 6.7.3 **input sanitisation** — `onNameChanged("bambu lab! ®")` → state.name == "Bambu lab " (alnum + space + `.` + `-`, TitleCase first letter, trimmed to 10).

### 6.8 Extend `app/src/test/java/com/spoolpainter/app/domain/usecases/CreateAndPairUseCaseTest.kt`

- [ ] 6.8.1 **selectedFilamentId path — happy** — `Input.selectedFilamentId != null` → `spoolman.createSpoolForExistingFilament` invoked; `spoolman.resolveOrCreateFilament` NOT invoked.
- [ ] 6.8.2 **selectedFilamentId path — PATCH issued** — when `expanderOverrides` non-empty → asserted via call log on fake.

### 6.9 Test fakes / harness extensions

- [ ] 6.9.1 New `FakeMaterialBrandRepository` (in `test/.../testdata/`).
- [ ] 6.9.2 New `FakeMaterialPresetSource`, `FakeBrandPresetSource`, `FakeMaterialBrandLocalStore`.
- [ ] 6.9.3 Extend `FakeSpoolmanRepository` with `patchFilament` + `createSpoolForExistingFilament` + call log for assertions.
- [ ] 6.9.4 Extend `MainViewModelTest` ctor to inject `FakeMaterialBrandRepository`. All existing 300 tests must compile + pass without behaviour change.

### 6.10 Regression check

- [ ] 6.10.1 All 300 existing tests pass after the ctor expansions in §6.9.4 + the `SpoolmanRepository` ctor expansion in §2.14.4. Behaviour is purely additive.

---

## §7 — FormMapping additions

- [ ] 7.1 `FormMapping.fromFilament(filament)` per §3.1.
- [ ] 7.2 No change to `FormMapping.fromSpoolman(spool)`.
- [ ] 7.3 No change to `ColorHexCodec` / `canonVariant` (both U6b, already in place).

---

## §8 — Documentation

- [ ] 8.1 Create `aidlc-docs/construction/u8-pickers-and-filament-metadata/code/u8-summary.md` summarising:
  - Files Created (list with one-line role each).
  - Files Modified (list with one-line scope each).
  - Files Deleted (`MaterialDatabase.kt`, `BrandDatabase.kt`).
  - Tests added (per-class breakdown + final running total).
  - Verification commands run + outcomes.
  - Carry-overs to U10 (manual install gate items per §11).
- [ ] 8.2 No changes to `aidlc-docs/inception/application-design/components.md` / `component-methods.md` (doc-drift carry remains parked for U10 per `aidlc-state.md`).

---

## §9 — Verification commands

- [ ] 9.1 `./gradlew :app:compileDebugKotlin` ✅
- [ ] 9.2 `./gradlew :app:testDebugUnitTest` ✅ — target **343 / 343** (300 + 43 new).
- [ ] 9.3 `./gradlew :app:assembleDebug` ✅ — capture APK size; flag U10 if > 36 MB (current baseline 34 MB after U7).
- [ ] 9.4 No `:app:installDebug` for U8 — Q-T2=B → no install gate; manual verification deferred to U10 per §11.

---

## §10 — Test count rollup

| Class | New cases |
|---|---|
| `MaterialBrandRepositoryTest` | 10 |
| `SpoolmanRepositoryPatchFilamentTest` | 6 |
| `SpoolmanRepositoryCreateForExistingFilamentTest` | 5 |
| `MainViewModelFilamentPickerTest` | 7 |
| `MainViewModelMoreDetailsExpanderTest` | 6 |
| `AddCustomMaterialViewModelTest` (replaces U1 placeholder) | 4 |
| `AddCustomBrandViewModelTest` (replaces U1 placeholder) | 3 |
| `CreateAndPairUseCaseTest` (extension) | 2 |
| **U8 net new** | **43** |
| Running total target | **343** |

---

## §11 — U10 manual-checklist hand-off (no U8 install gate per Q-T2=B)

| Scenario | Expected |
|---|---|
| Add custom material | Sheet → save → material in `MaterialPicker`; survives app restart. |
| Add custom brand | Sheet → save → brand in `BrandPicker`; survives app restart. |
| Material picker dedup | Custom "pla" + preset "PLA" → only one entry visible. |
| Brand merge — Spoolman + preset | Spoolman has "Bambu Lab" + preset has "Bambu Lab" → single entry. |
| Filament picker — 0-spool filament | Expand "Filament ▾" → pick filament F1 (no spools) → form prefills → Save & Write blank tag → exactly 1 new spool under F1 (no duplicate filament). |
| Filament picker — 1+-spool filament (deliberate add-2nd-spool) | Expand "Filament ▾" → pick F2 (already owns S2) → Save & Write blank tag → S3 created under F2; S2 unaffected; no duplicate filament. |
| Filament picker — expander prefill | Pick F1 with `density=1.30 g/cm³` → expand "More details ▾" → field shows 1.30. |
| Expander PATCH idempotency | Pick F1; expand; don't change anything; Save & Write → no PATCH HTTP call (verified via `adb logcat`). |
| Expander PATCH applied | Pick F1 with `weight=1000`; expand; change to 750; Save & Write → PATCH issued; F1's `weight` updated in Spoolman web UI. |
| Both expanders independent | Open "Filament ▾" → "More details ▾" stays collapsed; vice versa. |
| Default form layout | Default form (both expanders collapsed) byte-identical to U7 layout. |
| Custom-material dedup vs preset | Add custom "pla" (lowercase) → picker shows preset "PLA" only. |
| Add-custom auto-select | Add "PA-CF" via sheet → form's material field shows "PA-CF" selected. |
| Material name input rules | Sheet input `pa-cf 12!#` → live-sanitised to `PA-CF12` (8 chars, UPPERCASE, alnum + `-` + `+`). |
| Brand name input rules | Sheet input `bambu lab! ®` → live-sanitised to `Bambu lab ` (10 chars, TitleCase first letter, alnum + space + `.` + `-`). |

---

## §12 — Brownfield invariants (per `code-generation.md` Critical Rules)

- [ ] 12.1 No `*_modified.kt` / `*_new.kt` / `*.bak` files in `app/src/`.
- [ ] 12.2 `MaterialDatabase.kt` / `BrandDatabase.kt` deleted (not duplicated).
- [ ] 12.3 `MaterialPicker.kt` / `BrandPicker.kt` / `FilamentForm.kt` / `BottomSheetHost.kt` / `DataStoreModule.kt` / `MainViewModel.kt` / `MainUiState.kt` / `CreateAndPairUseCase.kt` / `SpoolmanRepository.kt` / `SpoolmanRequests.kt` / `SpoolmanApi.kt` / `SpoolmanModels.kt` / `Material.kt` / `AddCustomMaterialViewModel.kt` / `AddCustomBrandViewModel.kt` modified in-place (15 files).
- [ ] 12.4 `data-testid` style identifiers stable across renders (per Automation Friendly Rules):
  - Filament picker: `filament-picker-input`, `filament-picker-row-{filament.id}`, `filament-picker-clear`.
  - Expanders: `expander-filament-header`, `expander-filament-content`, `more-details-header`, `more-details-{field}`.
  - Add-custom sheets: `sheet-add-material-{field|save|cancel}`, `sheet-add-brand-{field|save|cancel}`.

---

## §13 — Net file impact

| Action | Count | Files |
|---|---|---|
| Created | 13 | `MaterialPresetSource.kt`, `BrandPresetSource.kt`, `CustomEntries.kt`, `CustomMaterialsSerializer.kt`, `CustomBrandsSerializer.kt`, `MaterialBrandLocalStore.kt`, `MaterialBrandRepository.kt`, `FilamentSectionExpander.kt`, `FilamentPicker.kt`, `MoreDetailsExpander.kt`, `AddCustomMaterialSheet.kt`, `AddCustomBrandSheet.kt`, `u8-summary.md` |
| Modified | 15 | `Material.kt`, `SpoolmanModels.kt`, `SpoolmanRequests.kt`, `SpoolmanApi.kt`, `SpoolmanRepository.kt`, `MainUiState.kt`, `MainViewModel.kt`, `CreateAndPairUseCase.kt`, `FormMapping.kt`, `DataStoreModule.kt`, `MaterialPicker.kt`, `BrandPicker.kt`, `FilamentForm.kt`, `BottomSheetHost.kt`, `AddCustomMaterialViewModel.kt`, `AddCustomBrandViewModel.kt` (16 actually — keeping 15 round if `FormMapping.kt` already exists from U6b which it does, count as modified) |
| Deleted | 2 | `MaterialDatabase.kt`, `BrandDatabase.kt` |
| New tests | 6 | `MaterialBrandRepositoryTest`, `SpoolmanRepositoryPatchFilamentTest`, `SpoolmanRepositoryCreateForExistingFilamentTest`, `MainViewModelFilamentPickerTest`, `MainViewModelMoreDetailsExpanderTest`, plus `FakeMaterialBrandRepository` + 3 fake sources |
| Replaced tests | 2 | `AddCustomMaterialViewModelTest`, `AddCustomBrandViewModelTest` |
| Modified tests | 3 | `MainViewModelTest` (+ `MainViewModelTwoTagTest` if ctor flow-through), `CreateAndPairUseCaseTest`, `FakeSpoolmanRepository` |
