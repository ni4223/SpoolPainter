# U5 — Code Generation Plan

**Stage**: CONSTRUCTION → Per-Unit Loop → Code Generation (U5)
**Unit**: U5 — Read-and-Pair Flow
**Source artefacts**:
- `aidlc-docs/inception/application-design/unit-of-work.md` §3-U5
- `aidlc-docs/inception/application-design/component-methods.md` §5 / §6 / §7
- `aidlc-docs/inception/application-design/services.md` §2
- `aidlc-docs/construction/plans/u5-read-and-pair-flow-functional-design-plan.md` (approved 2026-05-25)
- `aidlc-docs/construction/u5-read-and-pair-flow/functional-design/{domain-entities,business-rules,business-logic-model,frontend-components}.md`

---

## 1. Build / dependencies

- [ ] 1.1 No new runtime dependencies. (Compose Material 3 — already present; Coroutines Flow — present; Hilt — present.)
- [ ] 1.2 No new test dependencies. (Coroutines test, JUnit 4 — already present from U2/U3/U4.)
- [ ] 1.3 No `gradle/libs.versions.toml` changes.

## 2. Domain types

### 2.1 New types

- [ ] 2.1.1 `app/src/main/java/com/spoolpainter/app/domain/models/Brand.kt` — `data class Brand(val name: String)`. (Q-U5-6=A.)
- [ ] 2.1.2 `app/src/main/java/com/spoolpainter/app/domain/models/TempRanges.kt` — `data class TempRanges(val extruderMin: Int? = null, val extruderMax: Int? = null, val bedMin: Int? = null, val bedMax: Int? = null)`.
- [ ] 2.1.3 `app/src/main/java/com/spoolpainter/app/domain/usecases/ReadAndPairResult.kt` — sealed hierarchy from `domain-entities.md` §2.1.

### 2.2 Reused / extended types

- [ ] 2.2.1 `domain/models/Material.kt` — unchanged.
- [ ] 2.2.2 `domain/primitives/CardUid.kt` / `TagClassification.kt` / `OpenSpoolPayload.kt` — unchanged.
- [ ] 2.2.3 `domain/primitives/NfcResult.kt` / `NfcIntent.kt` — unchanged.
- [ ] 2.2.4 `data/remote/spoolman/{SpoolmanRepository, SpoolmanOutcome, ConnectivityState, UrlNotConfiguredException}.kt` — unchanged.
- [ ] 2.2.5 `domain/models/SpoolmanModels.kt` — unchanged.

## 3. Use-case layer

- [ ] 3.1 `app/src/main/java/com/spoolpainter/app/domain/usecases/ReadAndPairUseCase.kt`:
  - `@Inject constructor(private val nfc: NfcRepository, private val spoolman: SpoolmanRepository)`.
  - `suspend operator fun invoke(): ReadAndPairResult` — implements BR-U5-RP-1..12.
  - Helper `private fun branchOnSpoolman(uid: CardUid, classification: TagClassification, outcome: SpoolmanOutcome<List<SpoolmanSpool>>): ReadAndPairResult` — implements BR-U5-RP-5..7.
  - Helper `private suspend fun awaitTerminalRead(): NfcResult` — implements BR-U5-RP-3 via `nfc.state.first { it is NfcResult.Success || it is NfcResult.Error }`.
  - Cancellation: no `try/catch (CancellationException)`.

## 4. ViewModel layer

- [ ] 4.1 `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainUiState.kt` — replace U1 placeholder with finalised shape from `domain-entities.md` §2.2..2.8:
  - `MainUiState(form, spoolman, nfc, banner, activeFlow, ambiguity)`.
  - `FormState`, `SpoolmanState`, `BannerState`, `ActiveFlow`, `AmbiguityState` (file-private siblings or co-located in `MainUiState.kt`).
  - `typealias NfcState = NfcResult` (top-level).
- [ ] 4.2 `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainViewModel.kt` — full rewrite:
  - `@HiltViewModel @Inject constructor(nfc: NfcRepository, spoolman: SpoolmanRepository, settings: SettingsRepository, readAndPair: ReadAndPairUseCase)`.
  - `state: StateFlow<MainUiState>` backed by `_state: MutableStateFlow(MainUiState())`.
  - `effects: Flow<UiEffect>` backed by `_effects: Channel<UiEffect>(Channel.BUFFERED)`.
  - `init { ... }` blocks launching three independent collectors:
    - `nfc.state.collect { _state.update { s -> s.copy(nfc = it) } }`.
    - `spoolman.spools.collect { _state.update { s -> s.copy(spoolman = s.spoolman.copy(spools = it)) } }`.
    - `settings.settings.map { it.url.isNotBlank() }.distinctUntilChanged().collect { _state.update { s -> s.copy(spoolman = s.spoolman.copy(urlConfigured = it)) } }`.
  - `private var readJob: Job? = null` — tracks the in-flight read for re-tap cancellation (BR-U5-VM-2).
  - `fun onReadTapped()` — implements BR-U5-VM-1 + BR-U5-VM-2.
  - `fun onSpoolSelected(spool: SpoolmanSpool?)` — implements BR-U5-VM-5 + BR-U5-VM-11.
  - `fun onSettingsTapped()` — emits `UiEffect.Navigate("settings")` via `_effects.trySend`.
  - Private helpers: `private fun applyResult(result: ReadAndPairResult)` (BR-U5-VM-3), `private fun humanReadable(outcome: SpoolmanOutcome<*>): String` (BR-U5-VM-4), `private fun fromSpoolman(spool: SpoolmanSpool, currentUid: CardUid?, rawWriteMode: Boolean): FormState` (§3.1 of `domain-entities.md`), `private fun fromOpenSpool(uid: CardUid, payload: OpenSpoolPayload, rawWriteMode: Boolean): FormState` (§3.2), `private fun blankForm(uid: CardUid, rawWriteMode: Boolean): FormState` (§3.3).

## 5. Compose / UI layer

- [ ] 5.1 `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainScreen.kt` — full rewrite per `frontend-components.md` §1 hierarchy:
  - `MainScreen(viewModel: MainViewModel = hiltViewModel(), onNavigateToSettings: () -> Unit = {})`.
  - Collects `state` + `effects`.
  - `Scaffold` with `topBar`, `floatingActionButton` (Read FAB), `snackbarHost`.
  - `MainContent(state, callbacks)` lays out `BannerSlot`, `ReadingHint`, `UidRow`, `SpoolmanDropdown`, `AmbiguityBlock`, `FormPreview` in a `Column`.
- [ ] 5.2 `app/src/main/java/com/spoolpainter/app/ui/screens/main/components/MainTopBar.kt` — `TopAppBar` with title + Settings `IconButton`.
- [ ] 5.3 `app/src/main/java/com/spoolpainter/app/ui/screens/main/components/BannerSlot.kt` — renders nothing on `Hidden`, error-tinted card on `Offline`.
- [ ] 5.4 `app/src/main/java/com/spoolpainter/app/ui/screens/main/components/ReadingHint.kt` — `Text("Tap a tag to read…")` when armed.
- [ ] 5.5 `app/src/main/java/com/spoolpainter/app/ui/screens/main/components/UidRow.kt` — selectable `Text("UID: ${uid.hex.uppercase()}")`.
- [ ] 5.6 `app/src/main/java/com/spoolpainter/app/ui/components/SpoolmanDropdown.kt` — Material 3 `ExposedDropdownMenuBox` with "Clear" item and disabled state.
- [ ] 5.7 `app/src/main/java/com/spoolpainter/app/ui/screens/main/components/AmbiguityBlock.kt` — error-tinted card listing matches.
- [ ] 5.8 `app/src/main/java/com/spoolpainter/app/ui/screens/main/components/FormPreview.kt` — read-only label/value rows + colour swatch.
- [ ] 5.9 `app/src/main/java/com/spoolpainter/app/ui/screens/main/components/ReadFab.kt` — `ExtendedFloatingActionButton` with `Icon` + "Read tag" label; small inline progress indicator when armed.

(Component files may collapse into fewer files at code-gen time — final layout is a CG concern. The contracts above are what matters.)

## 6. MainActivity / nav

- [ ] 6.1 `MainActivity.kt` — pass `onNavigateToSettings = { /* TODO U9 */ }` to `MainScreen`. No nav-component dependency required.

## 7. Brownfield migration

- [ ] 7.1 Delete v1 `MainScreenContent.kt` if it still exists. (Confirm with `find`.)
- [ ] 7.2 Confirm v1 `MainViewModel.kt` outside `ui/screens/main/` does not exist. (Already verified.)
- [ ] 7.3 Confirm `FilamentSpool.fromSpoolman` retained — used by U5's mapping logic (or ported into the VM helper).
- [ ] 7.4 Confirm `MaterialDatabase` + `BrandDatabase` retained — U5 reads from them.
- [ ] 7.5 No `OpenSpoolData` references remain in the tree (already verified U2..U4).

## 8. Test plan (Q-T3=B — extensive ViewModel coverage)

### 8.1 Test support

- [ ] 8.1.1 `app/src/test/java/com/spoolpainter/app/support/FakeNfcRepository.kt` — minimal subclass / replacement of `NfcRepository`'s public surface used by the use-case + VM:
  - `state: MutableStateFlow<NfcResult>` (public read; mutable internally).
  - `lastSeenTag: MutableStateFlow<TagBuffer?>`.
  - `consumeLastSeen(intent: NfcIntent): NfcResult?` — returns whatever `nextConsumeLastSeen` is set to, then clears it.
  - `arm(intent: NfcIntent)` — records call; does nothing else (tests will manually push `state` updates).
  - `disarm()` — sets `state` to `Idle`.
  - Counters: `armCalls`, `disarmCalls`, `consumeLastSeenCalls`.
  - Pre-seeded helpers: `simulateBufferedTap(uid, classification)`, `simulateNextRead(NfcResult.Success | Error)`.
- [ ] 8.1.2 `app/src/test/java/com/spoolpainter/app/support/FakeSpoolmanRepository.kt` — minimal subclass:
  - `spools: MutableStateFlow<List<SpoolmanSpool>>`.
  - `vendors`, `filaments`, `connectivity` `MutableStateFlow`s (kept for VM init's collectors but not driven).
  - `nextFindSpoolsByCardUidResult: SpoolmanOutcome<List<SpoolmanSpool>>` (default `Success(emptyList())`).
  - `findSpoolsByCardUid(uid)` returns `nextFindSpoolsByCardUidResult`. Records `lastFindUid`.
- [ ] 8.1.3 `app/src/test/java/com/spoolpainter/app/support/FakeSettingsRepository.kt` — exposes a `MutableStateFlow<Settings>` so VM tests can drive `urlConfigured`.

  Note: real `SettingsRepository` is `final class @Singleton @Inject constructor(DataStore<Settings>, CoroutineScope)`. To keep it overridable, either (a) refactor to an interface here, or (b) construct the real `SettingsRepository` in tests with an in-memory `DataStore<Settings>` test double.
  - **Recommendation**: refactor `SettingsRepository` into an interface (`SettingsRepository` interface + `SettingsRepositoryImpl` impl, Hilt provides impl). This keeps brownfield risk low (only the type name changes; consumers continue to call `settings`/`setUrl`/etc.) and gives tests a clean swap-point. Alternative is to keep the class concrete and use a fake that subclasses — feasible because the class is `open`-able by adding `open` modifiers, but interfaces are cleaner.
  - **Decision**: introduce `SettingsRepository` interface + `SettingsRepositoryImpl` in U5. (Small brownfield delta; sets the pattern for U6+.)

### 8.2 Use-case tests

- [ ] 8.2.1 `app/src/test/java/com/spoolpainter/app/domain/usecases/ReadAndPairUseCaseTest.kt`:
  - `tag_first_buffered_OpenSpool_with_zero_matches_returns_PrefillFromTag`.
  - `tag_first_buffered_OpenSpool_with_one_match_returns_PrefillFromSpoolman` (collision rule).
  - `tag_first_miss_falls_back_to_arm_Read`.
  - `arm_Read_then_Blank_with_zero_matches_returns_BlankForm_Blank`.
  - `arm_Read_then_Vendor_with_zero_matches_returns_BlankForm_Vendor` (Spoolman called).
  - `arm_Read_then_OpenSpool_with_two_matches_returns_Ambiguous`.
  - `Spoolman_HttpError_returns_SpoolmanFailed`.
  - `Spoolman_NetworkError_with_UrlNotConfigured_cause_returns_BlankForm`.
  - `Spoolman_NetworkError_other_cause_returns_SpoolmanFailed`.
  - `Spoolman_ParseError_returns_SpoolmanFailed`.
  - `Nfc_Error_short_circuits_no_Spoolman_call`.
  - `zero_length_uid_returns_NfcFailed`.
  - Approx **12 cases**.

### 8.3 ViewModel tests

- [ ] 8.3.1 `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelTest.kt`:
  - `initial_state_is_default_MainUiState`.
  - `nfc_slice_mirrors_NfcRepository_state`.
  - `spools_slice_mirrors_SpoolmanRepository_spools`.
  - `urlConfigured_mirrors_settings_url_blank_status`.
  - `banner_always_Hidden_in_U5`.
  - `onReadTapped_PrefillFromSpoolman_updates_form_and_resets_activeFlow`.
  - `onReadTapped_PrefillFromTag_maps_payload_to_form`.
  - `onReadTapped_BlankForm_clears_form_preserves_rawWriteMode`.
  - `onReadTapped_Ambiguous_populates_AmbiguityState_form_stays_blank`.
  - `onReadTapped_SpoolmanFailed_emits_ShowSnackbar`.
  - `onReadTapped_NfcFailed_emits_ShowSnackbar`.
  - `onReadTapped_while_already_armed_disarms_and_rearms`.
  - `onSpoolSelected_non_null_prefills_form_from_spool`.
  - `onSpoolSelected_null_clears_form_preserves_cardUid`.
  - `onSpoolSelected_same_id_is_idempotent`.
  - `onSpoolSelected_clears_AmbiguityState`.
  - `onSettingsTapped_emits_Navigate_settings`.
  - Approx **17 cases**.

### 8.4 Mapping function tests (form prefill)

- [ ] 8.4.1 `app/src/test/java/com/spoolpainter/app/ui/screens/main/FormMappingTest.kt` — purity tests for the three mapping helpers:
  - `fromSpoolman_with_known_material_uses_material_defaults_when_extruder_temp_in_range`.
  - `fromSpoolman_with_known_material_uses_temp_plus_20_when_out_of_range`.
  - `fromSpoolman_with_unknown_material_falls_back_to_temp_plus_20`.
  - `fromSpoolman_normalises_color_hex` (#-strip, takeLast(6), uppercase).
  - `fromSpoolman_with_null_temps_yields_null_temps`.
  - `fromOpenSpool_parses_int_temps`.
  - `fromOpenSpool_unparseable_temps_fall_back_to_material_defaults`.
  - `fromOpenSpool_subtype_Basic_yields_null_variant`.
  - `fromOpenSpool_subtype_Matte_yields_variant`.
  - `fromOpenSpool_normalises_color_hex`.
  - `fromOpenSpool_with_unknown_type_synthesises_transient_Material`.
  - `blankForm_resets_fields_preserves_cardUid_and_rawWriteMode`.
  - Approx **12 cases**.

### 8.5 Total test count target

**~41 new test cases**; running total **223** (4 U1 + 64 U2 + 64 U3 + 50 U4 + 41 U5).

## 9. Verification commands

- [ ] 9.1 `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:compileDebugKotlin`.
- [ ] 9.2 `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest`.
- [ ] 9.3 `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:assembleDebug`.
- [ ] 9.4 Brownfield invariants:
  - `grep -rn "TODO U5" app/src` → 0.
  - `grep -rn "OpenSpoolData\|class SpoolmanService\|NfcManager\|class NfcController\|class NfcHandler" app/src/main` → 0.

## 10. Story / requirement coverage map

| ID | Code surface | Test surface |
|---|---|---|
| FR-3.1 / S-3.1 | `ReadAndPairUseCase.invoke` calls `findSpoolsByCardUid` | `ReadAndPairUseCaseTest::*` |
| FR-3.2 / S-3.2 | `applyResult(PrefillFromSpoolman)` | `MainViewModelTest::onReadTapped_PrefillFromSpoolman_*` |
| FR-3.3 / S-3.3 | `Ambiguous` branch + `AmbiguityState` slot + no PATCH/POST | `MainViewModelTest::onReadTapped_Ambiguous_*`, `ReadAndPairUseCaseTest::*two_matches*` |
| FR-3.4 / S-3.4 / FR-11.1 | `applyResult(PrefillFromTag)` + `fromOpenSpool` | `MainViewModelTest::onReadTapped_PrefillFromTag_*`, `FormMappingTest::fromOpenSpool_*` |
| FR-3.4 / S-3.5 / FR-11.2 | `applyResult(BlankForm)` for Blank/Vendor classifications | `MainViewModelTest::onReadTapped_BlankForm_*`, `ReadAndPairUseCaseTest::*Blank*`, `*Vendor*` |
| FR-3.6 / S-3.6 | `onSpoolSelected` + `fromSpoolman` mapping | `MainViewModelTest::onSpoolSelected_*`, `FormMappingTest::fromSpoolman_*` |
| FR-10.2 / S-10.2 | `SpoolmanFailed` → `ShowSnackbar` (banner derivation U9) | `MainViewModelTest::*SpoolmanFailed_emits_ShowSnackbar` |
| NFR-1.2 | UI never calls repositories directly | code review + Compose-side prop contracts |
| NFR-1.4 | sealed `NfcResult` mirrored as `NfcState` | reused from U4 |
| NFR-7.1 / NFR-7.4 | error contract `SpoolmanOutcome` is exhaustive | `humanReadable` covers all variants |

## 11. Out-of-scope guards (must not appear in U5 code)

- [ ] 11.1 No `onWriteTapped`, `CreateAndPairUseCase`, `MoveOnBindUseCase`, `TwoTagUseCase`, `RawWriteUseCase`, `VendorUidOnlyPairUseCase` references in U5 code.
- [ ] 11.2 No editable form widgets (TextFields, sliders) — `FormPreview` is read-only.
- [ ] 11.3 No `BannerState.Offline` activation logic — VM emits `Hidden` always.
- [ ] 11.4 No `MaterialBrandRepository` reference — uses `MaterialDatabase` directly.
- [ ] 11.5 No `Spoolman` PATCH / POST / DELETE references in `ReadAndPairUseCase` or `MainViewModel`.

## 12. Summary artefact

- [ ] 12.1 After Code Generation Part 2 executes, write `aidlc-docs/construction/u5-read-and-pair-flow/code/u5-summary.md` with: files created/modified/deleted, test counts, build verification, story coverage, exit-criteria checklist, milestone install gate plan.

## 13. Approval Gate

- Present "Code Generation Plan Complete" workflow message.
- Wait for user approval before executing Part 2.

---

## 14. Q-U5-12=A — `spool_id` fallback addendum (added 2026-05-25 after Code Gen Part 2)

Added during the U5 install-gate iteration. Implementation lands as a follow-up edit before the close-out commit.

- [x] 14.1 `SpoolmanRepository`: promote the existing private `getSpool(id)` helper to `open suspend fun getSpool(id: Int): SpoolmanOutcome<SpoolmanSpool>` (currently used internally by `appendCardUidToSpool` / `removeCardUidFromSpool`).
- [x] 14.2 `FakeSpoolmanRepository`: add `nextGetSpoolResult: SpoolmanOutcome<SpoolmanSpool>` plus override of `getSpool`.
- [x] 14.3 `ReadAndPairUseCase.branchOnMatches` — when 0 matches and classification is `OpenSpool(payload)` and `payload.spoolId?.toIntOrNull() != null`, call `spoolman.getSpool(...)` and re-branch per BR-U5-RP-13.
- [x] 14.4 New `ReadAndPairUseCaseTest` cases:
  - `zero_uid_matches_with_payload_spool_id_resolved_returns_PrefillFromSpoolman`.
  - `zero_uid_matches_with_payload_spool_id_404_falls_back_to_PrefillFromTag`.
  - `zero_uid_matches_with_payload_spool_id_NetworkError_returns_SpoolmanFailed`.
  - `zero_uid_matches_with_null_spool_id_returns_PrefillFromTag` (regression of existing behaviour).
- [x] 14.5 No `MainViewModel` changes required — `applyResult(PrefillFromSpoolman)` already covers the resolved-spool case.
- [x] 14.6 Re-run `:app:testDebugUnitTest` + `:app:installDebug` for the U5 install gate.
