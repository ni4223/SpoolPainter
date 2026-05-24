# Components — SpoolPainter v2

**Stage**: INCEPTION → Application Design (artifact 1/5)
**Source**: `aidlc-docs/inception/plans/application-design-plan.md` (answered)
**Scope**: v2.0 components in detail; v2.1 vendor-decode plugin point
captured at interface level only.
**Detailed business rules**: deferred to Functional Design (per-unit,
CONSTRUCTION phase).

---

## 1. Layered Architecture (NFR-1)

```
┌─────────────────────────────────────────────────────────────┐
│  UI / Compose (ui/screens, ui/components, ui/sheets)        │
│  ↑ collectAsStateWithLifecycle                              │
├─────────────────────────────────────────────────────────────┤
│  ViewModel (per screen + per modal sheet)                   │
│  ↑ StateFlow<UiState>  ← suspend repo calls →               │
├─────────────────────────────────────────────────────────────┤
│  Use-cases (multi-step flows only — Q-S1=B)                 │
├─────────────────────────────────────────────────────────────┤
│  Repository (NfcRepository / SpoolmanRepository / …)        │
│  ↓ withContext(IO) for blocking work only                   │
├─────────────────────────────────────────────────────────────┤
│  Data sources (Retrofit / DataStore / NFC adapter)          │
└─────────────────────────────────────────────────────────────┘
```

UI components SHALL NOT call data sources directly (NFR-1.2 — fixes the
v1 wart where `SpoolmanFilamentDropdown` called `SpoolmanService`).

---

## 2. Component catalogue

### 2.1 UI / Compose

| Component | Package | Responsibility |
|---|---|---|
| `MainScreen` | `ui/screens/main` | Single main screen (FR-13.1). Two primary actions (Read NFC / Write to NFC), Spoolman dropdown, filament form, temperature panel. Hosts the offline banner via `MainViewModel.state.banner`. |
| `SettingsScreen` | `ui/screens/settings` | URL field + connectivity test, sort order, theme override (FR-9.1–9.3). Owns the **Test connection** action (Q-CD1.1=A — refresh lives only here). v2.1 adds Vendor Keys section (FR-9.4). |
| `MainActivity` | `ui/activity` | Single Activity host. Foreground-dispatch lifecycle hooks (`onResume` → `nfcRepository.attach(this)`, `onPause` → `nfcRepository.detach()` per Q-CD4=A). |
| `RepairConfirmSheet` | `ui/components/sheets` | Bottom sheet for FR-5.2 move-on-bind confirmation. Owned by `RepairConfirmViewModel`. |
| `VendorUidOnlyOptInSheet` | `ui/components/sheets` | Bottom sheet for FR-4.9 vendor-tag UID-only pair opt-in. Owned by `VendorOptInViewModel`. |
| `PairAnotherTagSheet` | `ui/components/sheets` | Bottom sheet for FR-6.1 second-tag pair prompt. |
| `AddCustomMaterialSheet` | `ui/components/sheets` | Bottom sheet for FR-8.5 custom material entry. |
| `AddCustomBrandSheet` | `ui/components/sheets` | Bottom sheet for FR-8.5 custom brand entry. |
| `SpoolmanDropdown` | `ui/components` | Spoolman spool dropdown. Reads from `MainViewModel.state.spoolman`; emits selection events. Pre-fills form via FR-3.6 dropdown-driven prefill. |
| `FilamentForm` | `ui/components` | Composable that renders form fields (material, brand, color, variant, temps). Read-only when `state.banner` is offline AND no spool selected. |
| `MaterialPicker` | `ui/components` | Material selector (PLA/ABS/PETG/… + custom). Hardcoded preset list merged with user-added entries from `MaterialBrandLocalStore`. |
| `BrandPicker` | `ui/components` | Brand selector. Hardcoded presets merged with Spoolman vendor list and local user-added entries (FR-8.3, FR-8.5). |
| `ColorPicker` | `ui/components` | RGB hex color input. |
| `TempPanel` | `ui/components` | Min/max extruder + bed temperature inputs. |
| `OfflineBanner` | `ui/components` | Passive banner (Q-CD1.1=A). Shows when `state.banner == Offline` (URL configured AND Spoolman unreachable). Read-only — no action button. Hidden entirely when URL not configured. |

### 2.2 ViewModel (NFR-1.3)

Q-CM4=C hybrid: one `MainViewModel` for screen-level state + per-sheet
VMs for modal sub-flows.

| ViewModel | Scope | Responsibility |
|---|---|---|
| `MainViewModel` | `MainScreen` (HiltViewModel) | Owns `StateFlow<MainUiState>` covering form, NFC state, Spoolman dropdown, banner, active flow. Methods: `onReadTapped()`, `onWriteTapped()`, `onSpoolSelected(spool)`, `onMaterialChanged(m)` etc. (Q-DP2=C — methods for primary actions, sealed events for sheet results). Emits `Channel<UiEffect>` for transient snackbar messages (Q-DP3=C). |
| `SettingsViewModel` | `SettingsScreen` (HiltViewModel) | URL field + Test-connection action; sort order; theme override. Calls `spoolmanRepository.probe()` for Test connection. |
| `RepairConfirmViewModel` | bottom-sheet (HiltViewModel) | Owns `StateFlow<RepairConfirmUiState>`. Returns confirm/cancel result via `MainViewModel.onRepairResult(...)`. |
| `VendorOptInViewModel` | bottom-sheet (HiltViewModel) | Owns `StateFlow<VendorOptInUiState>`. Returns "Pair UID only" / "Cancel" result via `MainViewModel.onVendorOptInResult(...)`. |
| `AddCustomMaterialViewModel` | bottom-sheet (HiltViewModel) | Owns custom-material entry state; on confirm, calls `MaterialBrandRepository.addMaterial(...)`. |
| `AddCustomBrandViewModel` | bottom-sheet (HiltViewModel) | Owns custom-brand entry state; on confirm, calls `MaterialBrandRepository.addBrand(...)`. |

### 2.3 Use-cases (Q-S1=B — multi-step flows only)

Use-cases are pure orchestration — no state, no DI of UI types. Each
takes the relevant repositories via constructor injection.

| Use-case | Source FRs | Composes |
|---|---|---|
| `ReadAndPairUseCase` | FR-3 | `NfcRepository` (read) + `SpoolmanRepository` (find by UID + prefill) |
| `CreateAndPairUseCase` | FR-4, FR-7 | `SpoolmanRepository.createSpoolForNewFilament(...)` (Q-S2=A) → `NfcRepository.arm(Write)` → verify → optional PATCH |
| `MoveOnBindUseCase` | FR-5 | `SpoolmanRepository` (find existing UID owner) + two PATCHes (remove from old, add to new). Surfaces partial-commit error per FR-5.2 / FR-7.4. |
| `TwoTagUseCase` | FR-6 | `NfcRepository.arm(Write)` (second tag) → `SpoolmanRepository.appendUid(...)`. |
| `VendorUidOnlyPairUseCase` | FR-4.9 | Same as `CreateAndPairUseCase` minus the NDEF write step. |
| `RawWriteUseCase` | FR-4.8 | `NfcRepository.arm(Write)` only — no Spoolman side effects. |

### 2.4 Repository (NFR-1.2)

| Repository | Responsibility | Hilt scope |
|---|---|---|
| `NfcRepository` | Sealed `NfcResult` state machine (NFR-1.4); `arm(intent)` / `consumeLastSeen(intent)` / `disarm()` (Q-CM1=D); foreground-dispatch lifecycle (`attach(activity)` / `detach()` per Q-CD4=A); write-then-verify per NFR-6; tag classification (blank / OpenSpool / vendor — basis for FR-4.7 / FR-4.9). | `@Singleton` |
| `SpoolmanRepository` | Find-by-UID lookup (FR-3.2 — `lot_nr` substring search); FR-7 create chain `createSpoolForNewFilament(...)` (Q-S2=A); PATCH `lot_nr` for existing-spool path (FR-4.6); vendor/filament/spool list cache (Q-S4=A — `StateFlow<List<…>>`); `connectivity: StateFlow<ConnectivityState>` (Q-CD1=A); `probe()` for Test-connection; sealed `SpoolmanOutcome<T>` return type (Q-CM2=B). | `@Singleton` |
| `SettingsRepository` | DataStore-backed URL / sort / theme (NFR-3.1). v2.1 adds vendor keys via `EncryptedSharedPreferences` (NFR-3.4). | `@Singleton` |
| `MaterialBrandRepository` | DataStore-Proto-backed `MaterialBrandLocalStore` (Q-CD2=A); merges hardcoded presets + Spoolman vendors + user-added entries; case-insensitive dedup. | `@Singleton` |

### 2.5 Domain primitives (NFR-1.4)

Q-CI3=C — split.

#### `domain/primitives/`

| Type | Purpose |
|---|---|
| `CardUid` | Value type — canonical lowercase-hex UID string. Constructor `fromBytes(ByteArray)`; `equals` and `toString` defined. (FR-1.2) |
| `TagClassification` | Sealed: `Blank \| OpenSpool(payload) \| Vendor(reason)`. Drives FR-4 branching. |
| `NfcResult` | Sealed: `Idle \| Reading \| Writing \| Verifying \| Success(uid, payload?) \| Error(reason)`. (NFR-1.4) |
| `NfcIntent` | Sealed: `Read \| Write(payload, expectedUid?) \| Verify(expectedPayload)`. Used by `NfcRepository.arm(...)` / `consumeLastSeen(...)`. |

#### `domain/models/` (presentation models — same package as v1)

| Type | Purpose |
|---|---|
| `OpenSpoolPayload` | Existing v1 model — JSON shape for OpenSpool tags. |
| `FilamentSpool` | Existing v1 model — Spoolman spool projection for the dropdown. |
| `Material`, `Vendor`, `Filament` | Existing v1 models. |

### 2.6 Data sources

#### Local (Q-CI2=B — split presets vs. userdata)

| Component | Package | Purpose |
|---|---|---|
| `MaterialPresetSource` | `data/local/presets` | Hardcoded material list (PLA / ABS / PETG / TPU / ASA / PC / Nylon / PVA / HIPS / Other) with default temp ranges. (FR-8.1) |
| `BrandPresetSource` | `data/local/presets` | Hardcoded brand starter list. (FR-8.2) |
| `MaterialBrandLocalStore` | `data/local/userdata` | DataStore-Proto-backed list of user-added materials and brands (FR-8.5). Schema: `CustomMaterials { repeated CustomMaterial entries }` + `CustomBrands { repeated CustomBrand entries }`. |

#### Remote

| Component | Package | Purpose |
|---|---|---|
| `SpoolmanApi` | `data/remote/spoolman` | Retrofit interface — extended for vendor/filament/spool POST chain (FR-7), `lot_nr`-filtered GET (FR-3.2), PATCH `lot_nr` (FR-4.6, FR-5.2). |
| `CardUidEncoding` | `data/remote/spoolman` | Encode/decode rules for `card_uid:<hex>,…,opaque` strings Spoolman currently keeps in `lot_nr`. Survives the FR-2.4 migration to `extra.card_uid` (only the calling Retrofit field changes). |
| `SpoolmanModels` | `data/remote/spoolman` | Existing v1 wire models — `Spool`, `Filament`, `Vendor` (extended for v2 fields). |

#### Hardware

| Component | Package | Purpose |
|---|---|---|
| `NfcAdapterWrapper` | `hardware/nfc` | Thin wrapper around `android.nfc.NfcAdapter`. Owned by `NfcRepository`. v1's `NfcManager` + `NfcController` collapse into this + the repository (no v1 names preserved as public surface — Q-CD4=A). |

### 2.7 Cross-cutting / Hilt DI (NFR-2, Q-CD3=B)

| Module | `@InstallIn` | Provides |
|---|---|---|
| `NetworkModule` | `SingletonComponent` | OkHttp client, Retrofit, `SpoolmanApi`, `SpoolmanRepository` |
| `RepositoryModule` | `SingletonComponent` | `MaterialBrandRepository`, `SettingsRepository` |
| `DataStoreModule` | `SingletonComponent` | `DataStore<Settings>`, `DataStore<CustomMaterials>`, `DataStore<CustomBrands>` |
| `NfcModule` | `SingletonComponent` | `NfcAdapterWrapper`, `NfcRepository` |

### 2.8 v2.1 plugin point (interface-level only, Q-CI4=B)

> No interface defined in v2.0. v2.1 introduces `VendorTagDecoder`
> abstraction when it ports OpenRFID parsers. `TagClassification.Vendor`
> is the v2.0 hook — v2.1 will refine it from `Vendor(reason)` into
> `Vendor(decoded: DecodedVendorPayload?)`.

---

## 3. Public interface shapes (signatures only — bodies in Functional Design)

```kotlin
// domain/primitives/CardUid.kt
@JvmInline value class CardUid(val hex: String) {
    companion object {
        fun fromBytes(bytes: ByteArray): CardUid
    }
}

// domain/primitives/TagClassification.kt
sealed interface TagClassification {
    object Blank : TagClassification
    data class OpenSpool(val payload: OpenSpoolPayload) : TagClassification
    data class Vendor(val reason: String) : TagClassification
}

// domain/primitives/NfcResult.kt
sealed interface NfcResult {
    object Idle : NfcResult
    object Reading : NfcResult
    object Writing : NfcResult
    object Verifying : NfcResult
    data class Success(val uid: CardUid, val classification: TagClassification) : NfcResult
    data class Error(val reason: String, val cause: Throwable? = null) : NfcResult
}

// domain/primitives/NfcIntent.kt
sealed interface NfcIntent {
    object Read : NfcIntent
    data class Write(val payload: OpenSpoolPayload, val expectedUid: CardUid? = null) : NfcIntent
}

// data/remote/spoolman/CardUidEncoding.kt
object CardUidEncoding {
    data class Decoded(val uids: List<CardUid>, val opaque: String)
    fun decode(raw: String): Decoded
    fun encode(uids: List<CardUid>, opaque: String = ""): String
}

// hardware/nfc/NfcRepository.kt
@Singleton
class NfcRepository @Inject constructor(
    private val adapter: NfcAdapterWrapper,
) {
    val state: StateFlow<NfcResult>
    val lastSeenTag: StateFlow<TagBuffer?>            // TTL-cleared

    fun attach(activity: ComponentActivity)            // onResume
    fun detach()                                       // onPause

    suspend fun arm(intent: NfcIntent)
    suspend fun consumeLastSeen(intent: NfcIntent): NfcResult?
    suspend fun disarm()
}

// data/remote/spoolman/SpoolmanRepository.kt
@Singleton
class SpoolmanRepository @Inject constructor(
    private val api: SpoolmanApi,
) {
    val connectivity: StateFlow<ConnectivityState>     // Unknown | Reachable | Unreachable
    val filaments: StateFlow<List<Filament>>           // cached
    val spools: StateFlow<List<Spool>>                 // cached

    suspend fun probe(): SpoolmanOutcome<Unit>
    suspend fun findSpoolsByCardUid(uid: CardUid): SpoolmanOutcome<List<Spool>>
    suspend fun createSpoolForNewFilament(req: NewSpoolRequest): SpoolmanOutcome<Spool>  // FR-7 chain
    suspend fun appendCardUidToSpool(spoolId: Int, uid: CardUid): SpoolmanOutcome<Spool>
    suspend fun removeCardUidFromSpool(spoolId: Int, uid: CardUid): SpoolmanOutcome<Spool>
    suspend fun refresh(): SpoolmanOutcome<Unit>       // re-fetch caches
}

sealed interface SpoolmanOutcome<out T> {
    data class Success<T>(val data: T) : SpoolmanOutcome<T>
    data class HttpError(val code: Int, val message: String) : SpoolmanOutcome<Nothing>
    data class NetworkError(val cause: Throwable) : SpoolmanOutcome<Nothing>
    data class ParseError(val cause: Throwable) : SpoolmanOutcome<Nothing>
}

// data/local/SettingsRepository.kt
@Singleton
class SettingsRepository @Inject constructor(
    private val store: DataStore<Settings>,
) {
    val settings: StateFlow<Settings>
    suspend fun setUrl(url: String)
    suspend fun setSortOrder(order: SortOrder)
    suspend fun setThemeOverride(theme: ThemeOverride)
}

// data/local/MaterialBrandRepository.kt
@Singleton
class MaterialBrandRepository @Inject constructor(
    private val presets: MaterialPresetSource,
    private val brandPresets: BrandPresetSource,
    private val userStore: MaterialBrandLocalStore,
    private val spoolman: SpoolmanRepository,
) {
    val materials: StateFlow<List<Material>>            // presets ∪ userStore
    val brands: StateFlow<List<Brand>>                  // presets ∪ spoolman vendors ∪ userStore (deduped)
    suspend fun addCustomMaterial(name: String, defaultTemps: TempRanges)
    suspend fun addCustomBrand(name: String)
}
```

---

## 4. Traceability

Every component traces to one or more FRs / NFRs / stories. Coverage
map (preview — finalised in Section 4 of `application-design.md`):

| Source | Components |
|---|---|
| FR-1 (UID identity) | `NfcRepository`, `CardUid`, `NfcResult`, `TagClassification` |
| FR-2 (lot_nr ↔ UID format) | `CardUidEncoding`, `SpoolmanRepository` |
| FR-3 (Read-and-Pair) | `MainScreen`, `MainViewModel`, `ReadAndPairUseCase`, `SpoolmanRepository`, `SpoolmanDropdown` |
| FR-4 (Write/Create-and-Pair) | `MainViewModel`, `CreateAndPairUseCase`, `VendorUidOnlyPairUseCase`, `RawWriteUseCase`, `NfcRepository`, `SpoolmanRepository`, `VendorUidOnlyOptInSheet` |
| FR-5 (Move-on-bind) | `MoveOnBindUseCase`, `RepairConfirmSheet`, `RepairConfirmViewModel`, `SpoolmanRepository` |
| FR-6 (Two-tag) | `TwoTagUseCase`, `PairAnotherTagSheet`, `MainViewModel` |
| FR-7 (Spoolman create chain) | `SpoolmanRepository.createSpoolForNewFilament(...)` (Q-S2=A) |
| FR-8 (Material/brand presets + custom) | `MaterialBrandRepository`, `MaterialPresetSource`, `BrandPresetSource`, `MaterialBrandLocalStore`, `MaterialPicker`, `BrandPicker`, `AddCustomMaterialSheet`, `AddCustomBrandSheet` |
| FR-9 (Settings) | `SettingsScreen`, `SettingsViewModel`, `SettingsRepository` |
| FR-10 (Spoolman optional + offline) | `OfflineBanner`, `SpoolmanRepository.connectivity`, `SettingsScreen` (Test connection) |
| FR-11 (Existing-tag display) | `NfcRepository`, `MainViewModel` |
| FR-12 (Theming) | `Theme`, `SettingsRepository.themeOverride` |
| FR-13 (UI shape) | `MainScreen`, `SettingsScreen`, all bottom sheets |
| FR-14 (Tag write content) | `OpenSpoolPayload`, `CreateAndPairUseCase` |
| FR-15 (Naming) | n/a — package id unchanged |
| NFR-1 (Architecture) | All — layered MVVM + Repository + Hilt |
| NFR-2 (Hilt) | `NetworkModule`, `RepositoryModule`, `DataStoreModule`, `NfcModule` |
| NFR-3 (Persistence) | `SettingsRepository`, `MaterialBrandLocalStore` |
| NFR-6 (Write-then-verify) | `NfcRepository.arm(Write)` flow; `CreateAndPairUseCase` |
| NFR-7 (Network) | `SpoolmanOutcome<T>`, `SpoolmanRepository.connectivity` |
