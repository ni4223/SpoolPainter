# U8 — Code Generation Summary

**Stage**: CONSTRUCTION → Per-Unit Loop → Code Generation Part 2 (U8)
**Unit**: U8 — Pickers + Custom Entries + Filament Metadata UX
**Executed**: 2026-05-28

**Plan**: `aidlc-docs/construction/plans/u8-pickers-and-filament-metadata-code-generation-plan.md` (all checkboxes executed end-to-end).

---

## Files created (13)

| Path | Role |
|---|---|
| `app/src/main/java/com/spoolpainter/app/data/local/presets/MaterialPresetSource.kt` | Hilt-bound preset source for materials; companion holds `DEFAULT_DIAMETER_MM` (1.75), `DEFAULT_FULL_SPOOL_WEIGHT_G` (1000), `PLA_DENSITY_FALLBACK` (1.24) constants + `lookup`/`densityFor` static helpers for non-DI callers (`FormMapping`, `FilamentSpool`). |
| `app/src/main/java/com/spoolpainter/app/data/local/presets/BrandPresetSource.kt` | Hilt-bound preset source for brands. 11 entries (v1's "Other" filtered out per Q-U8-2=B). |
| `app/src/main/java/com/spoolpainter/app/data/local/userdata/CustomEntries.kt` | `@Serializable` data classes: `CustomMaterial`, `CustomMaterials`, `CustomBrand`, `CustomBrands`. |
| `app/src/main/java/com/spoolpainter/app/data/local/userdata/CustomMaterialsSerializer.kt` | JSON DataStore serialiser (mirrors `SettingsSerializer`). |
| `app/src/main/java/com/spoolpainter/app/data/local/userdata/CustomBrandsSerializer.kt` | JSON DataStore serialiser. |
| `app/src/main/java/com/spoolpainter/app/data/local/userdata/MaterialBrandLocalStore.kt` | Thin wrapper around `DataStore<CustomMaterials>` + `DataStore<CustomBrands>`. |
| `app/src/main/java/com/spoolpainter/app/data/local/MaterialBrandRepository.kt` | Merges presets ∪ Spoolman vendors ∪ user store; case-insensitive `distinctBy`; presets enumerate first per Q-U8-9=C / Q-U8-10=A. `open class` for test stand-ins. |
| `app/src/main/java/com/spoolpainter/app/ui/components/FilamentSectionExpander.kt` | Collapsed-by-default "Filament ▾" header + expandable content. |
| `app/src/main/java/com/spoolpainter/app/ui/components/FilamentPicker.kt` | `ExposedDropdownMenu` listing ALL filaments alphabetically; X clears selection. |
| `app/src/main/java/com/spoolpainter/app/ui/components/MoreDetailsExpander.kt` | Collapsed-by-default "More details ▾" header + 5 numeric fields with suffix labels. |
| `app/src/main/java/com/spoolpainter/app/ui/components/sheets/AddCustomMaterialSheet.kt` | `ModalBottomSheet` for adding custom material. |
| `app/src/main/java/com/spoolpainter/app/ui/components/sheets/AddCustomBrandSheet.kt` | `ModalBottomSheet` for adding custom brand. |
| `aidlc-docs/construction/u8-pickers-and-filament-metadata/code/u8-summary.md` | This file. |

## Files modified (16)

| Path | Scope |
|---|---|
| `app/src/main/java/com/spoolpainter/app/domain/models/Material.kt` | +`density: Float?` field (Q-U8-5=A). |
| `app/src/main/java/com/spoolpainter/app/domain/models/SpoolmanModels.kt` | +5 `Float?` fields on `SpoolmanFilament` (`density`, `diameter`, `weight`, `spool_weight`, `price`). |
| `app/src/main/java/com/spoolpainter/app/domain/models/FilamentSpool.kt` | Switched from `MaterialDatabase` → `MaterialPresetSource.lookup`. |
| `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanRequests.kt` | `CreateFilamentRequest` +`spool_weight`/`price`; new `PatchFilamentBody` + `ExpanderOverrides` carrier. |
| `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanApi.kt` | +`@PATCH("api/v1/filament/{id}")`. |
| `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanRepository.kt` | +`patchFilament` (idempotency cache check per Q-U8-13=A); +`createSpoolForExistingFilament` (Q-U8-12=A); `resolveOrCreateFilament` extended to forward `ExpanderOverrides`; retired inline `densityFor` map → reads from `MaterialPresetSource`. |
| `app/src/main/java/com/spoolpainter/app/di/DataStoreModule.kt` | +`provideCustomMaterialsDataStore`/`provideCustomBrandsDataStore`. |
| `app/src/main/java/com/spoolpainter/app/domain/usecases/NewFilamentRequest.kt` | +`expanderOverrides` field; `fromForm` extracts overrides via `FormState.toExpanderOverrides()`. |
| `app/src/main/java/com/spoolpainter/app/domain/usecases/CreateAndPairUseCase.kt` | Branches on `form.selectedFilamentId` → `createSpoolForExistingFilament` (Q-U8-14=A). |
| `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainUiState.kt` | `FormState` +8 new fields (selectedFilamentId, two expander booleans, 5 Float? overrides) + `toExpanderOverrides()` extension; `ActiveFlow` +`AddingCustomMaterial`/`AddingCustomBrand`. |
| `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainViewModel.kt` | Hilt ctor +`materialBrandRepo`; expose `filaments`/`materials`/`brands` flows; +12 handlers (`onFilamentSelected`, `onFilamentSectionToggled`, `onMoreDetailsToggled`, 5×`onXxxChanged`, `onOpenAddCustom*Sheet`, `onAddCustomSheetDismissed`, 2×`onAddCustom*Confirmed`). |
| `app/src/main/java/com/spoolpainter/app/ui/screens/main/FormMapping.kt` | +`fromFilament` helper for filament-pick prefill. |
| `app/src/main/java/com/spoolpainter/app/ui/components/FilamentForm.kt` | +new `FormChange` events (filament select/toggle, expander toggle, 5 float setters); inserts `FilamentSectionExpander` ABOVE Material/Brand/Color/Variant/Temps and `MoreDetailsExpander` BELOW (between Temps and Save button). Layout matches user direction: spool (visible) → filament (hidden) → other stuff → more details (hidden) → save. |
| `app/src/main/java/com/spoolpainter/app/ui/components/MaterialPicker.kt` | Switched to `MaterialPresetSource.PRESETS`; +"➕ Add custom material…" footer. |
| `app/src/main/java/com/spoolpainter/app/ui/components/BrandPicker.kt` | Switched to `BrandPresetSource.PRESETS`; +"➕ Add custom brand…" footer. |
| `app/src/main/java/com/spoolpainter/app/ui/components/sheets/BottomSheetHost.kt` | +2 branches for `AddingCustomMaterial`/`AddingCustomBrand`; +3 callbacks. |
| `app/src/main/java/com/spoolpainter/app/ui/components/sheets/AddCustomMaterialViewModel.kt` | Replaced U1 placeholder with real form VM (input filtering per FD §1.14: UPPERCASE alnum + `-` + `+` + `.take(8)`; min ≤ max validation). |
| `app/src/main/java/com/spoolpainter/app/ui/components/sheets/AddCustomBrandViewModel.kt` | Replaced U1 placeholder (TitleCase alnum + space + `.` + `-` + `.take(10)`). |
| `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainScreen.kt` | Wired `filaments` flow + new VM handlers + new BottomSheetHost params. |

## Files deleted (2)

- `app/src/main/java/com/spoolpainter/app/data/local/MaterialDatabase.kt` (replaced by `MaterialPresetSource`)
- `app/src/main/java/com/spoolpainter/app/data/local/BrandDatabase.kt` (replaced by `BrandPresetSource`)

## Tests added (43 net new — running total 343 / 343)

| Class | Cases |
|---|---|
| `MaterialBrandRepositoryTest` (NEW) | 10 |
| `SpoolmanRepositoryPatchFilamentTest` (NEW) | 6 |
| `SpoolmanRepositoryCreateForExistingFilamentTest` (NEW) | 5 |
| `MainViewModelFilamentPickerTest` (NEW) | 7 |
| `MainViewModelMoreDetailsExpanderTest` (NEW) | 6 |
| `AddCustomMaterialViewModelTest` (replaced U1 placeholder) | 4 |
| `AddCustomBrandViewModelTest` (replaced U1 placeholder) | 3 |
| `CreateAndPairUseCaseTest` extension | 2 |
| **U8 net new** | **43** |

Plus test-harness extensions:
- `FakeMaterialBrandRepository.kt` (NEW)
- `FakeSpoolmanRepository.kt`: +`createSpoolForExistingFilament` + `patchFilament` overrides + call logs.
- `FakeSpoolmanApi.kt`: +`patchFilament` override + `failPatchFilament` toggle.
- `MainViewModelTest.kt` / `MainViewModelTwoTagTest.kt` / `MainViewModelRawWriteTest.kt`: ctor extended with `materialBrandRepo` (no behaviour delta — fake provides stable preset flows).

## Verification

| Command | Outcome |
|---|---|
| `./gradlew :app:compileDebugKotlin` | ✅ |
| `./gradlew :app:testDebugUnitTest` | ✅ **343 / 343** (Δ +43 vs U7's 300; 0 failures) |
| `./gradlew :app:assembleDebug` | ✅ **34 MB** APK (no change vs U7 baseline) |
| `./gradlew :app:installDebug` | not run — Q-T2=B per `unit-of-work.md` §U8; manual verification deferred to U10 install gate. |

## FD delta applied during code-gen

- **`CustomMaterials` / `CustomBrands` wire format**: proto3 → kotlinx-serialization JSON. Matches the existing `Settings` DataStore (`SettingsSerializer.kt:10-28`); zero new build infra (no protobuf-gradle-plugin, no `app/src/main/proto/`). FD `domain-entities.md` §1.3 / §1.4 / §3 updated in-place during this stage.
- **Compose icons**: `Icons.Default.ExpandMore`/`ExpandLess` are in `material-icons-extended` (not on classpath). Substituted with `KeyboardArrowDown`/`KeyboardArrowUp` (in core `material-icons`); zero behaviour delta and avoids adding a new dep. FD §3.1 `frontend-components.md` mentions ExpandMore/Less as the literal but Q-U8-17=B's intent ("Material icon") is honoured.
- **Static accessor on `MaterialPresetSource`**: legacy callers `FormMapping.kt` + `FilamentSpool.kt` are static (no DI). Added `companion object { fun lookup(name); fun densityFor(name) }` so they compile without churn while the Hilt-bound `class` remains the canonical surface for the repository.

## Carry-over to U10 (manual install gate)

Per Q-T2=B no U8 install gate — these manual scenarios land in U10:

- Add custom material → survives app restart.
- Add custom brand → survives app restart.
- Material picker dedup ("pla" + preset "PLA" → one row).
- Brand merge across Spoolman vendors + presets.
- Filament picker — 0-spool filament + 1+-spool deliberate-2nd-spool add.
- Expander prefill from existing filament metadata.
- Expander PATCH idempotency (no HTTP call when nothing changed).
- Expander PATCH applied (changed field rides on wire).
- Both expanders independent.
- Default form layout byte-identical to U7 when both expanders collapsed.
- Custom-material dedup vs preset.
- Add-custom auto-select (Q-U8-15=A).

## Brownfield invariants

- ✅ No `*_modified.kt` / `*_new.kt` / `*.bak` files.
- ✅ `MaterialDatabase.kt` / `BrandDatabase.kt` deleted (not duplicated).
- ✅ All 16 modified files edited in-place.
- ✅ Stable `data-testid` style identifiers (`filament-picker-*`, `more-details-*`, `sheet-add-{material|brand}-*`, etc.) per Automation Friendly Rules.
