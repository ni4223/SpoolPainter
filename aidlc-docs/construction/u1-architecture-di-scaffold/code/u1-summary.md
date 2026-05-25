# U1 — Architecture & DI Scaffold — Code Generation Summary

**Stage**: CONSTRUCTION → Code Generation Part 2 (Generation) — complete
**Generated**: 2026-05-25
**Plan**: `aidlc-docs/construction/plans/u1-architecture-di-scaffold-code-generation-plan.md`

---

## Files Created

| Path | Purpose | FRs / NFRs / Stories |
|---|---|---|
| `app/src/main/java/com/spoolpainter/app/SpoolPainterApplication.kt` | `@HiltAndroidApp` entry point | NFR-2 |
| `app/src/main/java/com/spoolpainter/app/data/local/Settings.kt` | `Settings` data class + `SortOrder` / `ThemeOverride` enums | NFR-3.1, FR-9.1, FR-9.2, FR-9.3 |
| `app/src/main/java/com/spoolpainter/app/data/local/SettingsSerializer.kt` | DataStore `Serializer<Settings>` (Json) | NFR-3.1 |
| `app/src/main/java/com/spoolpainter/app/data/local/SettingsRepository.kt` | `@Singleton` repo; `StateFlow<Settings>` + suspend setters | NFR-3.1, S-15.1 |
| `app/src/main/java/com/spoolpainter/app/di/DataStoreModule.kt` | Provides `DataStore<Settings>` | NFR-2 |
| `app/src/main/java/com/spoolpainter/app/di/RepositoryModule.kt` | Provides app-scoped `CoroutineScope` | NFR-2 |
| `app/src/main/java/com/spoolpainter/app/di/NetworkModule.kt` | Empty marker — U3 fills | NFR-2 |
| `app/src/main/java/com/spoolpainter/app/di/NfcModule.kt` | Empty marker — U4 fills | NFR-2 |
| `app/src/main/java/com/spoolpainter/app/domain/primitives/NfcResult.kt` | Sealed skeleton: `Idle / Reading / Writing / Verifying` only | NFR-1.4 |
| `app/src/main/java/com/spoolpainter/app/domain/primitives/NfcIntent.kt` | Sealed skeleton: `Read` only | NFR-1.4 |
| `app/src/main/java/com/spoolpainter/app/ui/common/UiEffect.kt` | Shared sealed `UiEffect` | Q-DP3=C |
| `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainUiState.kt` | Placeholder state shape | NFR-1.3 |
| `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainViewModel.kt` | `@HiltViewModel` skeleton with StateFlow + Channel | NFR-1.3, NFR-2 |
| `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainScreen.kt` | Placeholder Composable; `testTag = "main-screen-placeholder-text"` | NFR-1.1 |
| `app/src/main/java/com/spoolpainter/app/ui/screens/settings/SettingsUiState.kt` | url / sortOrder / themeOverride only | NFR-1.3 |
| `app/src/main/java/com/spoolpainter/app/ui/screens/settings/SettingsViewModel.kt` | `@HiltViewModel`; collapses `SettingsRepository.settings` into `SettingsUiState` | NFR-1.3, NFR-2, NFR-3.1 |
| `app/src/main/java/com/spoolpainter/app/ui/components/sheets/RepairConfirmViewModel.kt` | Sheet VM skeleton | FR-5.2 (deferred to U6b) |
| `app/src/main/java/com/spoolpainter/app/ui/components/sheets/VendorOptInViewModel.kt` | Sheet VM skeleton | FR-4.9 (deferred to U7) |
| `app/src/main/java/com/spoolpainter/app/ui/components/sheets/AddCustomMaterialViewModel.kt` | Sheet VM skeleton | FR-8.5 (deferred to U8) |
| `app/src/main/java/com/spoolpainter/app/ui/components/sheets/AddCustomBrandViewModel.kt` | Sheet VM skeleton | FR-8.5 (deferred to U8) |
| `app/src/test/java/com/spoolpainter/app/data/local/SettingsRepositoryTest.kt` | Defaults read + each setter via Turbine | NFR-4.1 |

## Files Modified

| Path | Change |
|---|---|
| `gradle/libs.versions.toml` | Added v2 versions/libraries/plugins block: Hilt 2.52, KSP 2.0.21-1.0.27, DataStore 1.1.1, coroutines 1.8.1, kotlinx-serialization 1.7.1, lifecycle-Compose 2.8.4, hilt-navigation-compose 1.2.0, Turbine 1.1.0, MockK 1.13.12 |
| `build.gradle.kts` (root) | Added `apply false` for `ksp`, `hilt.android`, `kotlin.serialization` plugins |
| `app/build.gradle.kts` | Applied new plugins; added Hilt + coroutines + DataStore + kotlinx-serialization + lifecycle-Compose deps; added `kspAndroidTest(libs.hilt.compiler)` for future Hilt instrumented tests; added `kotlinx-coroutines-test`, Turbine, MockK to `testImplementation` |
| `app/src/main/AndroidManifest.xml` | Added `android:name=".SpoolPainterApplication"` to `<application>` |
| `app/src/main/java/com/spoolpainter/app/ui/activity/MainActivity.kt` | Annotated `@AndroidEntryPoint`; dropped v1 `nfcHandler` / `ViewModelProvider` / `setupNfc()` / `setupUI()`; rewrote `onCreate` to render `SpoolPainterTheme { MainScreen() }`; left `onResume` / `onPause` with `// TODO U4` markers for `NfcRepository.attach/detach` |

## Files Deleted (v1 cleanup — Step 9.1)

- `app/src/main/java/com/spoolpainter/app/ui/MainViewModel.kt` *(v1 single-VM stash; replaced by HiltViewModel under `ui/screens/main/`)*
- `app/src/main/java/com/spoolpainter/app/ui/screens/MainScreenContent.kt`
- `app/src/main/java/com/spoolpainter/app/ui/screens/SpoolPainterScreen.kt`
- `app/src/main/java/com/spoolpainter/app/ui/screens/SettingsScreen.kt` *(v1; rebuilt at U9)*
- `app/src/main/java/com/spoolpainter/app/ui/components/SpoolmanFilamentDropdown.kt` *(v1 wart of UI→data-source; fixed at U5)*
- `app/src/main/java/com/spoolpainter/app/ui/components/FilamentForm.kt` *(rebuilt at U5)*
- `app/src/main/java/com/spoolpainter/app/ui/components/NfcStatusCard.kt` *(rebuilt at U5+ if needed)*

## Files Kept Dormant (deleted in later units)

- `hardware/nfc/NfcHandler.kt`, `NfcController.kt`, `NfcManager.kt` → deleted at U4 when `NfcRepository` lands.
- `data/remote/spoolman/SpoolmanService.kt` → replaced/deleted at U3.
- `domain/models/{NfcResult,NfcTag,AppState,OpenSpoolData,FilamentSpool,Material,SpoolmanModels}.kt` → U2 / U3 / U5 migrate or delete.
- `data/local/MaterialDatabase.kt`, `BrandDatabase.kt` → U8 migrates into `MaterialPresetSource` / `BrandPresetSource`.
- `ui/components/{MaterialSelector,BrandSelector,ColorSelector,TemperatureCard,CustomSnackbar,SpoolPainterLogo}.kt` → kept; reused (with light updates) starting at U5.

## Story Coverage

| Story / NFR | Status | Coverage |
|---|---|---|
| NFR-1 (layered MVVM + Repository + Hilt) | ✅ | Layered packages created; UI → VM → Repository wiring instantiated for `SettingsRepository` ↔ `SettingsViewModel` |
| NFR-2 (Hilt only) | ✅ | `@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`, four `@Module @InstallIn(SingletonComponent)` modules; KSP processes Hilt at compile time |
| NFR-3.1 (settings persistence read surface) | ✅ | DataStore-Json-backed `Settings` + `SettingsRepository.settings: StateFlow<Settings>` + suspend setters |
| S-15.1 (package id baseline) | ✅ | `applicationId = "com.spoolpainter.app"` unchanged; debug variant retains `.debug` suffix |

## Public Interfaces Produced (consumed in later units)

```kotlin
class SettingsRepository {
    val settings: StateFlow<Settings>           // read surface — consumed by U3 (URL probe), U9 (full settings UI)
    suspend fun setUrl(url: String)             // U9
    suspend fun setSortOrder(order: SortOrder)  // U9
    suspend fun setThemeOverride(theme: ThemeOverride)  // U9
}

class MainViewModel : ViewModel {
    val state: StateFlow<MainUiState>           // shape stable; fields widen in U5/U6/U7/U9
    val effects: Flow<UiEffect>                 // shared sealed UiEffect type
}

sealed interface NfcResult { Idle; Reading; Writing; Verifying }   // widens in U4
sealed interface NfcIntent { Read }                                // widens in U4
```

Hilt module identifiers (`DataStoreModule`, `RepositoryModule`, `NetworkModule`, `NfcModule`) are now resolvable for `@Inject` in downstream units.

## Forward References Deferred

| Type / API | Lands in | Reason |
|---|---|---|
| `NfcResult.Success(uid, classification)`, `NfcResult.Error(reason, cause)` | U4 | Depends on U2's `CardUid` + `TagClassification` |
| `NfcIntent.Write(payload, expectedUid?)`, `NfcIntent.Verify(expectedPayload)` | U4 | Depends on U2's `OpenSpoolPayload` + `CardUid` |
| `MainUiState` real fields (form, spoolman, nfc, banner, activeFlow) | U5 / U6 / U7 / U9 | Depend on U2..U8 types |
| `SettingsUiState.connectivity` | U9 | Depends on U3's `ConnectivityState` |
| `MainActivity` foreground-dispatch (`NfcRepository.attach/detach`) | U4 | Depends on U4's `NfcRepository` |
| Sheet VM real state + methods | U6b / U7 / U8 | Owned by their respective domain units |

## Build & Test Verification

| Task | Outcome |
|---|---|
| `./gradlew :app:compileDebugKotlin` | ✅ Pass (only pre-existing Compose deprecation warnings on neutral v1 components — addressed when those Composables are reworked in U5+) |
| `./gradlew :app:testDebugUnitTest --tests SettingsRepositoryTest` | ✅ Pass (4/4 tests) |
| `./gradlew :app:assembleDebug` | ✅ Pass — Hilt KSP graph valid; produces `app/build/outputs/apk/debug/app-debug.apk` (~34 MB) |

### JDK note (workspace environment)

Gradle 8.13 + JDK 24 throws `Type T not present` at `AndroidUnitTest` task instantiation (Gradle 8.13 generic-types reflection predates JDK 21+ hardening). Workaround used here: invoke Gradle with `JAVA_HOME` pinned to JDK 17 (Amazon Corretto 17 already installed on this machine):

```
JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-17.jdk/Contents/Home ./gradlew <task>
```

This is **not** a code change for U1 — it's a developer-environment note. If you want this codified, the durable fix is either:
1. Add `org.gradle.java.home=/Library/Java/JavaVirtualMachines/amazon-corretto-17.jdk/Contents/Home` to `gradle.properties` (machine-specific path, so risky to check in unless every dev has Corretto 17 at the same path), or
2. Add a `.gradle/config.properties` with `daemon.jvm.criteria` (Gradle 8.8+) so the daemon auto-resolves a compatible JDK.

Either is a U10 release-polish concern, not U1 scope. For now, builds work as long as `JAVA_HOME` is JDK 17.

## Exit-Criteria Checklist (per `unit-of-work.md` §3-U1)

- [x] App compiles (`compileDebugKotlin` succeeded)
- [x] Hilt graph compiles (`assembleDebug` exercised KSP)
- [x] `SettingsRepository` reading defaults — unit test passes
- [x] Hilt graph compiles (no `@Provides` mismatches)
- [ ] **Milestone install gate (Q-T2=B)** — *user-driven*: install `app/build/outputs/apk/debug/app-debug.apk` on a physical device, confirm app launches to "SpoolPainter v2 — under construction (placeholder=true)" placeholder text.

## Forbidden Patterns Audit

| Pattern (from `unit-of-work-dependency.md` §5) | Status |
|---|---|
| UI → data source direct calls | ✅ none — `MainScreen` only collects `MainViewModel.state` |
| Activity / Context in Hilt graph | ✅ only `@ApplicationContext Context` in `DataStoreModule` (allowed) |
| Use-cases holding state | ✅ no use-cases yet (U5+) |
| Service locator / event bus | ✅ none |
| Repositories outside Hilt `SingletonComponent` | ✅ `SettingsRepository` is `@Singleton`; provided by Hilt via constructor injection |
| Raw `withContext(IO)` in VMs | ✅ none |
