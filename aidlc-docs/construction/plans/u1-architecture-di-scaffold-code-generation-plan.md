# U1 — Architecture & DI Scaffold — Code Generation Plan

**Stage**: CONSTRUCTION → Code Generation Part 1 (Planning)
**Unit**: U1 (Architecture & DI Scaffold) — see `aidlc-docs/inception/application-design/unit-of-work.md` §3-U1
**Workspace root**: `/Users/mnipun/AndroidStudioProjects/SpoolPainter`
**Project type**: Brownfield, single-module Android app (`app/`), Kotlin DSL Gradle, version catalog at `gradle/libs.versions.toml`
**Conditional gates skipped** (per `aidlc-state.md`): Functional Design, NFR Requirements, NFR Design, Infrastructure Design

This plan is the single source of truth for U1 Code Generation Part 2.
Each step is checkbox-tracked. Step IDs are stable for cross-references in
`audit.md`.

---

## 0. Unit Context (carry-forward from Application Design)

| Attribute | Value |
|---|---|
| Domain | Cross-cutting (foundation) |
| Stories in scope | NFR-1 (architecture), NFR-2 (Hilt), NFR-3 (settings persistence read-surface), S-15.1 (package id baseline) |
| Public interfaces produced | `SettingsRepository.settings: StateFlow<Settings>`, `MainViewModel.state: StateFlow<MainUiState>`, Hilt module identifiers |
| Entry criteria | Application Design approved (✅ 2026-05-24) |
| Exit criteria | App compiles; debug build runs on device (milestone install gate); `SettingsRepository` default-read tests pass |
| Dependencies | None (foundation unit) |
| Forbidden patterns (from `unit-of-work-dependency.md` §5) | Direct UI→data-source calls; Activity/Context in Hilt graph; raw `withContext(IO)` in VMs; service locator / event bus / non-Hilt singletons |

### Forward-reference policy (U1 ↔ U2/U3/U4)

U1 must compile **without referencing U2/U3/U4 types**. Specifically:
- `NfcResult` and `NfcIntent` skeletons land with case names only — no
  `CardUid`, no `TagClassification`, no `OpenSpoolPayload` parameters.
  U4 widens these in-place when U2's primitives exist.
- `MainUiState` lands as a placeholder shape (no `SpoolmanState`, no
  `NfcState`) — fields are added in U5/U6/U7/U9 as the corresponding
  state arrives.
- `SettingsUiState` lands with `url / sortOrder / themeOverride` only —
  `connectivity` field added in U9 once U3 ships `ConnectivityState`.

---

## 1. Build / Dependency Setup

### Step 1.1 — Update `gradle/libs.versions.toml`
- [x] Add `[versions]` entries: `hilt = "2.52"`, `ksp = "2.0.21-1.0.27"`, `datastore = "1.1.1"`, `coroutines = "1.8.1"`, `kotlinxSerialization = "1.7.1"`, `kotlinSerializationPlugin = "2.0.21"`, `lifecycleViewmodelCompose = "2.8.4"`, `lifecycleRuntimeCompose = "2.8.4"`, `hiltNavigationCompose = "1.2.0"`, `turbine = "1.1.0"`, `mockk = "1.13.12"`, `coroutinesTest = "1.8.1"`.
- [x] Add `[libraries]` entries: `hilt-android`, `hilt-compiler`, `hilt-navigation-compose`, `hilt-android-testing`, `androidx-datastore`, `androidx-datastore-core`, `kotlinx-coroutines-core`, `kotlinx-coroutines-android`, `kotlinx-coroutines-test`, `kotlinx-serialization-json`, `androidx-lifecycle-viewmodel-compose`, `androidx-lifecycle-runtime-compose`, `turbine`, `mockk`.
- [x] Add `[plugins]` entries: `ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }`, `hilt-android = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }`, `kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlinSerializationPlugin" }`.

### Step 1.2 — Update root `build.gradle.kts`
- [x] Add `alias(libs.plugins.ksp) apply false`, `alias(libs.plugins.hilt.android) apply false`, `alias(libs.plugins.kotlin.serialization) apply false`.

### Step 1.3 — Update `app/build.gradle.kts`
- [x] Apply plugins: `ksp`, `hilt.android`, `kotlin.serialization`.
- [x] Add Hilt deps: `implementation(libs.hilt.android)`, `ksp(libs.hilt.compiler)`, `implementation(libs.hilt.navigation.compose)`.
- [x] Add coroutines: `implementation(libs.kotlinx.coroutines.core)`, `implementation(libs.kotlinx.coroutines.android)`.
- [x] Add lifecycle Compose: `implementation(libs.androidx.lifecycle.viewmodel.compose)`, `implementation(libs.androidx.lifecycle.runtime.compose)`.
- [x] Add DataStore: `implementation(libs.androidx.datastore)`, `implementation(libs.androidx.datastore.core)`, `implementation(libs.kotlinx.serialization.json)`.
- [x] Add test deps: `testImplementation(libs.kotlinx.coroutines.test)`, `testImplementation(libs.turbine)`, `testImplementation(libs.mockk)`.
- [x] Existing v1 deps preserved (Retrofit, OkHttp, Material) — U3 will rewire / prune as needed.

### Step 1.4 — Sync Gradle
- [x] Run `./gradlew :app:dependencies --configuration debugRuntimeClasspath > /dev/null` to confirm version catalog resolves.

---

## 2. Application Class + Manifest Wiring

### Step 2.1 — Create `app/src/main/java/com/spoolpainter/app/SpoolPainterApplication.kt`
- [x] `@HiltAndroidApp class SpoolPainterApplication : Application()` — empty body.
- [x] Located at the package root (`com.spoolpainter.app`) so the manifest reference is `.SpoolPainterApplication`.

### Step 2.2 — Modify `app/src/main/AndroidManifest.xml` (in-place)
- [x] Add `android:name=".SpoolPainterApplication"` to the `<application>` element.
- [x] Manifest already declares NFC permission + feature; leave untouched.
- [x] `usesCleartextTraffic="true"` already set (NFR-7 / Spoolman LAN HTTP) — leave untouched.

---

## 3. Package Layout (NFR-1 / `.kiro/steering/structure.md`)

### Step 3.1 — Create new packages (empty placeholder packages are fine — Kotlin packages exist by virtue of files in them; this step creates the *first* file in each new package via the steps below, so this step is a marker, not a `mkdir`)
- [x] `com.spoolpainter.app` — `SpoolPainterApplication.kt` (Step 2.1).
- [x] `com.spoolpainter.app.di` — Hilt modules (Step 5).
- [x] `com.spoolpainter.app.data.local` — `Settings.kt`, `SettingsSerializer.kt`, `SettingsRepository.kt` (Step 4).
- [x] `com.spoolpainter.app.domain.primitives` — `NfcResult.kt`, `NfcIntent.kt` skeletons (Step 6).
- [x] `com.spoolpainter.app.ui.screens.main` — `MainViewModel.kt`, `MainScreen.kt`, `MainUiState.kt` (Step 7).
- [x] `com.spoolpainter.app.ui.screens.settings` — `SettingsViewModel.kt`, `SettingsUiState.kt` (Step 7).
- [x] `com.spoolpainter.app.ui.components.sheets` — sheet VM skeletons (Step 7).
- [x] `com.spoolpainter.app.ui.common` — `UiEffect.kt` (sealed shared type).
- [x] **Pre-existing packages preserved**: `ui/activity`, `ui/components`, `ui/theme`, `domain/models`, `hardware/nfc`, `data/local` (existing material/brand DBs), `data/remote/spoolman`. Files inside them are pruned per Step 9.

---

## 4. Settings Persistence (NFR-3.1 / S-15.1 baseline)

### Step 4.1 — Create `data/local/Settings.kt`
- [x] `@Serializable data class Settings(val url: String = "", val sortOrder: SortOrder = SortOrder.Default, val themeOverride: ThemeOverride = ThemeOverride.System)`.
- [x] `@Serializable enum class SortOrder { Default, Alphabetical, MaterialThenColor }` — exact values per FR-9.2; final list deferred to U9 if needed.
- [x] `@Serializable enum class ThemeOverride { System, Light, Dark }` — per FR-9.3 / FR-12.

### Step 4.2 — Create `data/local/SettingsSerializer.kt`
- [x] `object SettingsSerializer : Serializer<Settings>` using `kotlinx.serialization.json.Json`.
- [x] `defaultValue = Settings()`.
- [x] `readFrom(input: InputStream): Settings` — try `Json.decodeFromString(...)` from UTF-8; on `SerializationException`, throw `CorruptionException` (DataStore contract).
- [x] `writeTo(t: Settings, output: OutputStream)` — `Json.encodeToString(...)` then `output.write(...)`.

### Step 4.3 — Create `data/local/SettingsRepository.kt`
- [x] `@Singleton class SettingsRepository @Inject constructor(@param:Named("settings") private val store: DataStore<Settings>, private val scope: CoroutineScope)` — or simpler: take only `DataStore<Settings>` and use a private `MainScope()`-equivalent. Resolution in plan: take `DataStore<Settings>` only; expose `val settings: StateFlow<Settings>` derived via `store.data.stateIn(externalScope, SharingStarted.Eagerly, Settings())`. Inject the external scope from a Hilt-provided `@Singleton` `CoroutineScope` (`SupervisorJob() + Dispatchers.Default`) declared in `RepositoryModule`.
- [x] Methods: `suspend fun setUrl(url: String)`, `suspend fun setSortOrder(order: SortOrder)`, `suspend fun setThemeOverride(theme: ThemeOverride)` — all delegate to `store.updateData { it.copy(...) }`.

---

## 5. Hilt Modules (NFR-2, components.md §2.7)

### Step 5.1 — Create `di/DataStoreModule.kt`
- [x] `@Module @InstallIn(SingletonComponent::class) object DataStoreModule`.
- [x] `@Provides @Singleton fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Settings>` — returns `DataStoreFactory.create(serializer = SettingsSerializer, produceFile = { context.dataStoreFile("settings.json") })`.
- [x] Reserve a file-level comment: `// CustomMaterials / CustomBrands DataStores will be added in U8 per components.md §2.6`.

### Step 5.2 — Create `di/RepositoryModule.kt`
- [x] `@Module @InstallIn(SingletonComponent::class) object RepositoryModule`.
- [x] `@Provides @Singleton fun provideAppCoroutineScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)` — used by `SettingsRepository.stateIn(...)`.
- [x] Reserve a file-level comment: `// Repository @Provides land here when bindings need them; class-based repositories with @Inject constructor do not require @Provides.`

### Step 5.3 — Create `di/NetworkModule.kt`
- [x] `@Module @InstallIn(SingletonComponent::class) object NetworkModule` — empty body.
- [x] File-level comment: `// OkHttp / Retrofit / SpoolmanApi providers land in U3.`

### Step 5.4 — Create `di/NfcModule.kt`
- [x] `@Module @InstallIn(SingletonComponent::class) object NfcModule` — empty body.
- [x] File-level comment: `// NfcAdapterWrapper provider lands in U4.`

> Empty Hilt modules are valid. They serve as anchored scaffolding so
> later units drop `@Provides` in without re-creating files.

---

## 6. Domain Primitives — NFC Skeletons (placeholders before U4)

### Step 6.1 — Create `domain/primitives/NfcResult.kt`
- [x] `sealed interface NfcResult { object Idle : NfcResult; object Reading : NfcResult; object Writing : NfcResult; object Verifying : NfcResult }`.
- [x] **Defer** `Success(uid, classification)` and `Error(reason, cause?)` until U2/U4 ship `CardUid` + `TagClassification`. Per `unit-of-work-dependency.md` §3, `NfcResult` is "produced by U4"; U1 only declares the type so downstream Hilt graph references compile.

### Step 6.2 — Create `domain/primitives/NfcIntent.kt`
- [x] `sealed interface NfcIntent { object Read : NfcIntent }`.
- [x] **Defer** `Write(payload, expectedUid?)` and `Verify(expectedPayload)` to U4 (depend on U2's `OpenSpoolPayload` + `CardUid`).

> Note: existing v1 `domain/models/NfcResult.kt` lives in a different
> package (`com.spoolpainter.app.domain.models`) and remains in place
> while v1 NFC files (dormant after Step 9.4) still reference it. U4
> deletes the v1 model when the v1 NFC layer is replaced.

---

## 7. ViewModel Skeletons + UiState (Q-DP1=A, Q-DP2=C, Q-DP3=C)

### Step 7.1 — Create `ui/common/UiEffect.kt`
- [x] `sealed interface UiEffect { data class ShowSnackbar(val message: String) : UiEffect; data class Navigate(val destination: String) : UiEffect }`.

### Step 7.2 — Create `ui/screens/main/MainUiState.kt`
- [x] `data class MainUiState(val placeholder: Boolean = true)` — placeholder shape.
- [x] File-level comment: `// Real fields (form, spoolman, nfc, banner, activeFlow) land in U5/U6/U7/U9.`

### Step 7.3 — Create `ui/screens/main/MainViewModel.kt`
- [x] `@HiltViewModel class MainViewModel @Inject constructor() : ViewModel()`.
- [x] `private val _state = MutableStateFlow(MainUiState()); val state: StateFlow<MainUiState> = _state.asStateFlow()`.
- [x] `private val _effects = Channel<UiEffect>(Channel.BUFFERED); val effects: Flow<UiEffect> = _effects.receiveAsFlow()`.
- [x] No methods yet — flow-action methods (`onReadTapped`, `onWriteTapped`, etc.) added across U5/U6a/U6b/U7.

### Step 7.4 — Create `ui/screens/settings/SettingsUiState.kt`
- [x] `data class SettingsUiState(val url: String = "", val sortOrder: SortOrder = SortOrder.Default, val themeOverride: ThemeOverride = ThemeOverride.System)`.
- [x] File-level comment: `// connectivity field added in U9 (depends on U3 ConnectivityState).`

### Step 7.5 — Create `ui/screens/settings/SettingsViewModel.kt`
- [x] `@HiltViewModel class SettingsViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel()`.
- [x] `val state: StateFlow<SettingsUiState>` derived from `settings.settings.map { SettingsUiState(it.url, it.sortOrder, it.themeOverride) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())`.
- [x] `val effects: Flow<UiEffect>` (Channel-backed).
- [x] No action methods — `onUrlChanged`, `onTestConnectionTapped`, etc. added in U9.

### Step 7.6 — Sheet VM skeletons (one file per VM)
- [x] `ui/components/sheets/RepairConfirmViewModel.kt` — `@HiltViewModel`, empty state class `data class RepairConfirmUiState(val placeholder: Boolean = true)`, no methods.
- [x] `ui/components/sheets/VendorOptInViewModel.kt` — same shape.
- [x] `ui/components/sheets/AddCustomMaterialViewModel.kt` — same shape.
- [x] `ui/components/sheets/AddCustomBrandViewModel.kt` — same shape.
- [x] Each file gets a one-line comment naming the FR(s) the VM will own (FR-5.2 / FR-4.9 / FR-8.5 / FR-8.5).

---

## 8. MainActivity + Stub MainScreen

### Step 8.1 — Modify `ui/activity/MainActivity.kt` (in-place)
- [x] Annotate `@AndroidEntryPoint`.
- [x] Drop v1 `nfcHandler`, `ViewModelProvider(this)[MainViewModel::class.java]`, `viewModel.loadSpoolmanUrl(this)`, and v1 setupNfc() / setupUI() helpers.
- [x] `onCreate`: `installSplashScreen(); super.onCreate(...); enableEdgeToEdge(); setContent { SpoolPainterTheme { MainScreen() } }`.
- [x] `onResume`/`onPause`: leave a `// TODO U4: nfcRepository.attach(this) / detach()` comment block — no body until U4 wires `NfcRepository`.
- [x] No `onNewIntent` override yet — added in U4 when foreground dispatch lands.

### Step 8.2 — Create `ui/screens/main/MainScreen.kt`
- [x] `@Composable fun MainScreen(viewModel: MainViewModel = hiltViewModel())`.
- [x] Collect state via `viewModel.state.collectAsStateWithLifecycle()` (smoke-tests the StateFlow wiring).
- [x] Render `Scaffold { padding -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { Text("SpoolPainter v2 — under construction", modifier = Modifier.testTag("main-screen-placeholder-text")) } }`.
- [x] `data-testid` equivalent: `Modifier.testTag(...)` per Compose automation conventions (Code Generation Critical Rule §Automation Friendly Code).

---

## 9. v1 File Cleanup (Brownfield deletes)

> Per Critical Rules / Brownfield File Modification Rules: never create
> `*_modified.kt` shadows. Files that depend on the removed v1
> `MainViewModel` API and would otherwise fail to compile are deleted
> outright in U1; their v2 replacements ship in later units.

### Step 9.1 — Delete v1 files that depend on the removed v1 `MainViewModel` API
- [x] `app/src/main/java/com/spoolpainter/app/ui/MainViewModel.kt` — replaced by `ui/screens/main/MainViewModel.kt` (Step 7.3).
- [x] `app/src/main/java/com/spoolpainter/app/ui/screens/MainScreenContent.kt` — rebuilt across U5/U6a/U6b/U7/U9.
- [x] `app/src/main/java/com/spoolpainter/app/ui/screens/SpoolPainterScreen.kt` — rebuilt at U9.
- [x] `app/src/main/java/com/spoolpainter/app/ui/screens/SettingsScreen.kt` — rebuilt at U9 (new `SettingsViewModel` already exists; the screen Composable is rewritten when the full settings UI lands).
- [x] `app/src/main/java/com/spoolpainter/app/ui/components/SpoolmanFilamentDropdown.kt` — replaced by `SpoolmanDropdown` in U5 (the v1 wart of UI→data-source is fixed there).
- [x] `app/src/main/java/com/spoolpainter/app/ui/components/FilamentForm.kt` — rebuilt at U5.
- [x] `app/src/main/java/com/spoolpainter/app/ui/components/NfcStatusCard.kt` — rebuilt at U5+ if needed.

### Step 9.2 — Keep neutral v1 files (no v1-VM dependency)
- [x] Leave intact for v2 reuse: `ui/components/MaterialSelector.kt`, `BrandSelector.kt`, `ColorSelector.kt`, `TemperatureCard.kt`, `CustomSnackbar.kt`, `SpoolPainterLogo.kt`, `theme/Theme.kt`. Verify each does not import `com.spoolpainter.app.ui.MainViewModel` (v1) or any deleted file. If a kept file does import a deleted file, downgrade it to "delete in U1" and reintroduce in U5.

### Step 9.3 — Keep dormant (delete in later units)
- [x] `hardware/nfc/NfcHandler.kt`, `NfcController.kt`, `NfcManager.kt` — no longer referenced by `MainActivity` after Step 8.1; deleted in U4 when `NfcRepository` lands.
- [x] `data/remote/spoolman/SpoolmanService.kt` — no longer referenced after v1 UI deletion; replaced/deleted in U3.
- [x] `domain/models/NfcResult.kt`, `NfcTag.kt`, `AppState.kt`, `OpenSpoolData.kt`, `FilamentSpool.kt`, `Material.kt`, `SpoolmanModels.kt` — all dormant after Step 9.1; U2 (primitives) and U3/U5 (rewires) decide which to migrate vs. delete.
- [x] `data/local/MaterialDatabase.kt`, `BrandDatabase.kt` — dormant; U8 migrates them into `MaterialPresetSource` / `BrandPresetSource`.

### Step 9.4 — Compilation guard
- [x] After deletes (Step 9.1), run `./gradlew :app:compileDebugKotlin` to confirm no dangling v1 imports anywhere in surviving sources. If a dangling import surfaces, the offending kept file is moved to "delete in U1" (extension of Step 9.1) and listed in the U1 summary.

---

## 10. Tests (Q-T1=B DoD; no ViewModel tests in U1 per `unit-of-work.md` §3-U1)

### Step 10.1 — Create `app/src/test/java/com/spoolpainter/app/data/local/SettingsRepositoryTest.kt`
- [x] Use `runTest` (kotlinx-coroutines-test).
- [x] Construct a `DataStore<Settings>` over a temp file (`tempFolder.newFile("settings.json")`) using `DataStoreFactory.create(SettingsSerializer, ...)` and inject into `SettingsRepository(store, TestScope)`.
- [x] Test: `settings.value` (after first emission) equals `Settings()` defaults — `url == ""`, `sortOrder == SortOrder.Default`, `themeOverride == ThemeOverride.System`.
- [x] Test: `setUrl("http://nas.local:7912")` → next emission has `url == "http://nas.local:7912"`. Use `turbine.test { … }` for emission assertions.
- [x] Test: `setSortOrder` and `setThemeOverride` similarly.
- [x] **No Hilt-graph instrumented test in U1** — defer to U10's installable validation.

### Step 10.2 — Test runner config
- [x] No new instrumentation runner needed; `androidx.test.runner.AndroidJUnitRunner` already configured for future Hilt instrumented tests in later units.

---

## 11. Documentation Summary

### Step 11.1 — Create `aidlc-docs/construction/u1-architecture-di-scaffold/code/u1-summary.md`
- [x] Sections: **Files Created**, **Files Modified**, **Files Deleted**, **Story Coverage**, **Public Interfaces Produced**, **Exit-Criteria Checklist**, **Forward References Deferred** (table of types whose final shape lands in U2/U3/U4/U9), **Milestone Install Gate** (note that U1 is one of the four install gates per Q-T2=B).
- [x] Include FR/NFR/Story IDs per file (NFR-1, NFR-2, NFR-3, S-15.1).

---

## 12. Build Verification (smoke; full build & test stage runs after all units)

### Step 12.1 — Compile
- [x] `./gradlew :app:compileDebugKotlin` — must pass.

### Step 12.2 — Unit tests
- [x] `./gradlew :app:testDebugUnitTest --tests "com.spoolpainter.app.data.local.SettingsRepositoryTest"` — must pass.

### Step 12.3 — Hilt graph compile check
- [x] `./gradlew :app:assembleDebug` — must pass (this exercises Hilt KSP processing and confirms the DI graph is valid even with empty `NetworkModule` / `NfcModule`).

### Step 12.4 — Milestone install gate (Q-T2=B; user-driven)
- [x] User installs the resulting `app-debug.apk` (`com.spoolpainter.app.debug`) on a physical device and confirms it launches to the "SpoolPainter v2 — under construction" placeholder. **This is the U1 install gate** and is recorded in the U1 summary. — **PASSED 2026-05-25**

---

## 13. Story Traceability

| Story / NFR | Covered by step(s) |
|---|---|
| NFR-1 (layered architecture) | Step 3.1, Step 7, Step 8.2 |
| NFR-2 (Hilt) | Step 1.3, Step 2, Step 5, Step 7.3 / 7.5 / 7.6, Step 8.1 |
| NFR-3 (settings persistence — read surface) | Step 4 |
| S-15.1 (package id baseline) | unchanged from v1 (`com.spoolpainter.app`); no step needed beyond confirming `app/build.gradle.kts` `applicationId` (audit in Step 11.1) |

---

## 14. Out-of-Scope for U1 (parking lot for later units)

- `CardUid`, `TagClassification`, `OpenSpoolPayload`, `CardUidEncoding` — **U2**.
- `SpoolmanRepository`, `SpoolmanOutcome`, `SpoolmanApi`, `ConnectivityState` — **U3**.
- `NfcRepository`, `NfcAdapterWrapper`, `OpenSpoolPayloadParser`, full `NfcResult` / `NfcIntent` cases — **U4**; v1 NFC layer deleted at U4.
- `ReadAndPairUseCase`, `MainScreen` real composition — **U5**.
- `CreateAndPairUseCase`, `MoveOnBindUseCase` interface — **U6a**.
- `MoveOnBindUseCase` impl, `TwoTagUseCase`, `RepairConfirmSheet`, `PairAnotherTagSheet` — **U6b**.
- `RawWriteUseCase`, `VendorUidOnlyPairUseCase`, `VendorUidOnlyOptInSheet` — **U7**.
- `MaterialBrandRepository`, presets, custom-add sheets — **U8**.
- `SettingsScreen` UI, `OfflineBanner`, theme override application — **U9**.
- Release polish, signing-config audit, testing-track artefact — **U10**.

---

## 15. Approval

- [x] User approves this plan (Code Generation Part 1 Step 7) — approved 2026-05-25.
- [x] Approval response logged in `aidlc-docs/audit.md` with ISO 8601 timestamp.
- [x] On approval, `aidlc-state.md` Current Status updates to "U1 Code Generation Part 2 — generation in progress".
- [x] **U1 final approval (Code Generation Part 2 Step 15)** — user confirmed install-gate placeholder render on device 2026-05-25 and approved with "mark u1 done". U1 closed.
