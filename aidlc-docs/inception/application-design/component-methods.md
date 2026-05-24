# Component Methods — SpoolPainter v2

**Stage**: INCEPTION → Application Design (artifact 2/5)
**Source**: `aidlc-docs/inception/plans/application-design-plan.md` (answered)
**Scope**: Method signatures + I/O shapes + high-level purpose. Detailed
business rules (validation, edge cases, error handling) are deferred to
Functional Design (per-unit, CONSTRUCTION phase).

> **Convention**: Suspend functions are *main-safe* (Q-DP4=A). Repos
> internally `withContext(Dispatchers.IO)` only when wrapping blocking
> APIs (NFC); Retrofit + DataStore are already off-main and don't need
> extra wrapping.

---

## 1. NfcRepository (Q-CM1=D, Q-CD4=A)

```kotlin
@Singleton
class NfcRepository @Inject constructor(
    private val adapter: NfcAdapterWrapper,
    private val parser: OpenSpoolPayloadParser,
)
```

| Method | Returns | Purpose |
|---|---|---|
| `val state: StateFlow<NfcResult>` | `Idle \| Reading \| Writing \| Verifying \| Success \| Error` | Live NFC state (NFR-1.4). |
| `val lastSeenTag: StateFlow<TagBuffer?>` | `TagBuffer(uid, classification, payload?, capturedAtEpochMs)?` | TTL-cleared buffer holding the most recent unarmed tap (default TTL ~3-5s). Captures every tap regardless of armed state. |
| `fun attach(activity: ComponentActivity): Unit` | — | Called from `MainActivity.onResume`. Enables foreground dispatch. |
| `fun detach(): Unit` | — | Called from `MainActivity.onPause`. Disables foreground dispatch and releases the activity reference. |
| `suspend fun arm(intent: NfcIntent): Unit` | — | "Next tap = this intent." Transitions `state` to `Reading` / `Writing` accordingly. |
| `suspend fun consumeLastSeen(intent: NfcIntent): NfcResult?` | nullable result | If a fresh tag is buffered (within TTL), execute `intent` against it now and return the result. Else return null and caller falls back to `arm()`. |
| `suspend fun disarm(): Unit` | — | Cancel any armed intent; transition `state` back to `Idle`. |

**Internal**: tag classification (blank / OpenSpool / vendor) happens in
the adapter wrapper after every read; `OpenSpoolPayloadParser` decodes
NDEF if present.

---

## 2. SpoolmanRepository (Q-CM2=B, Q-S4=A, Q-CD1=A)

```kotlin
@Singleton
class SpoolmanRepository @Inject constructor(
    private val api: SpoolmanApi,
    private val urlSource: SettingsRepository,
)
```

### Connectivity (FR-10.2 / Q-CD1.1=A)

| Method | Returns | Purpose |
|---|---|---|
| `val connectivity: StateFlow<ConnectivityState>` | `Unknown \| Reachable \| Unreachable(lastError)` | Updated as a side effect of every Retrofit call; `Unknown` when URL not configured. |
| `suspend fun probe(): SpoolmanOutcome<Unit>` | sealed | Settings "Test connection" action — fires a cheap `GET /api/v1/info` and updates `connectivity`. |

### Lookup / list (cached — Q-S4=A)

| Method | Returns | Purpose |
|---|---|---|
| `val filaments: StateFlow<List<Filament>>` | list | Cached filament list; invalidated on PATCH/POST. |
| `val spools: StateFlow<List<Spool>>` | list | Cached spool list; invalidated on PATCH/POST. |
| `val vendors: StateFlow<List<Vendor>>` | list | Cached vendor list; invalidated on PATCH/POST. |
| `suspend fun refresh(): SpoolmanOutcome<Unit>` | sealed | Force-refresh all three caches. |
| `suspend fun findSpoolsByCardUid(uid: CardUid): SpoolmanOutcome<List<Spool>>` | sealed | FR-3.2 — `GET /api/v1/spool?lot_nr=card_uid:<uid>`. Substring match per Spoolman contract. |

### Mutations

| Method | Returns | Purpose |
|---|---|---|
| `suspend fun createSpoolForNewFilament(req: NewSpoolRequest): SpoolmanOutcome<Spool>` | sealed | FR-7 chain (Q-S2=A): vendor lookup-or-create → filament lookup-or-create → spool POST with `lot_nr=card_uid:<uid>`. Internal sequencing private. Reuses existing entries (FR-7.5) before creating new. |
| `suspend fun appendCardUidToSpool(spoolId: Int, uid: CardUid): SpoolmanOutcome<Spool>` | sealed | FR-4.6 / FR-6.2 — PATCH `lot_nr` adding `card_uid:<uid>` if not present. Preserves opaque tail (FR-2.2). |
| `suspend fun removeCardUidFromSpool(spoolId: Int, uid: CardUid): SpoolmanOutcome<Spool>` | sealed | FR-5.2 — PATCH `lot_nr` removing only the matched UID; preserves opaque tail and other UIDs (Q6=A in requirements). |
| `suspend fun moveCardUid(fromSpoolId: Int, toSpoolId: Int, uid: CardUid): SpoolmanOutcome<MoveOnBindResult>` | sealed (declared by `MoveOnBindUseCase` if Q-S3=C — see use-case) | *Not on this repository.* Move-on-bind is composed from `removeCardUidFromSpool` + `appendCardUidToSpool` inside `MoveOnBindUseCase` per Q-S3=C. |

### Returned types

```kotlin
data class NewSpoolRequest(
    val vendorName: String,
    val materialName: String,
    val colorHex: String,
    val variant: String?,
    val tempRanges: TempRanges,
    val cardUid: CardUid,
)

sealed interface SpoolmanOutcome<out T> {
    data class Success<T>(val data: T) : SpoolmanOutcome<T>
    data class HttpError(val code: Int, val message: String) : SpoolmanOutcome<Nothing>
    data class NetworkError(val cause: Throwable) : SpoolmanOutcome<Nothing>
    data class ParseError(val cause: Throwable) : SpoolmanOutcome<Nothing>
}
```

---

## 3. SettingsRepository (NFR-3.1)

```kotlin
@Singleton
class SettingsRepository @Inject constructor(
    private val store: DataStore<Settings>,
)
```

| Method | Returns | Purpose |
|---|---|---|
| `val settings: StateFlow<Settings>` | `Settings(url, sortOrder, themeOverride)` | DataStore-backed. |
| `suspend fun setUrl(url: String)` | — | FR-9.1. |
| `suspend fun setSortOrder(order: SortOrder)` | — | FR-9.2. |
| `suspend fun setThemeOverride(theme: ThemeOverride)` | — | FR-9.3. |

v2.1 additions (FR-9.4, NFR-3.4): `addVendorKey`, `removeVendorKey`,
`vendorKeys: Flow<List<VendorKey>>`, all backed by
`EncryptedSharedPreferences`.

---

## 4. MaterialBrandRepository (FR-8.5)

```kotlin
@Singleton
class MaterialBrandRepository @Inject constructor(
    private val materialPresets: MaterialPresetSource,
    private val brandPresets: BrandPresetSource,
    private val userStore: MaterialBrandLocalStore,
    private val spoolman: SpoolmanRepository,
)
```

| Method | Returns | Purpose |
|---|---|---|
| `val materials: StateFlow<List<Material>>` | list | Hardcoded presets ∪ user-added (case-insensitive deduped). |
| `val brands: StateFlow<List<Brand>>` | list | Hardcoded presets ∪ Spoolman vendors ∪ user-added (case-insensitive deduped). |
| `suspend fun addCustomMaterial(name: String, defaultTemps: TempRanges)` | — | FR-8.5. Persists locally; the next FR-7 create chain propagates the name to Spoolman. |
| `suspend fun addCustomBrand(name: String)` | — | FR-8.5. |

---

## 5. Use-cases (Q-S1=B — multi-step flows only)

```kotlin
class ReadAndPairUseCase @Inject constructor(
    private val nfc: NfcRepository,
    private val spoolman: SpoolmanRepository,
)
```

| Method | Returns | Purpose |
|---|---|---|
| `suspend operator fun invoke(): ReadAndPairResult` | sealed | FR-3 flow: read tag → classify → if OpenSpool, lookup by UID → return prefill instructions. |

```kotlin
class CreateAndPairUseCase @Inject constructor(
    private val nfc: NfcRepository,
    private val spoolman: SpoolmanRepository,
)
```

| Method | Returns | Purpose |
|---|---|---|
| `suspend operator fun invoke(req: CreateAndPairRequest): CreateAndPairResult` | sealed | FR-4 + FR-7 + NFR-6 flow: Spoolman create chain (Q-S2=A) → arm Write → verify → optional PATCH (existing-spool path). |

```kotlin
class MoveOnBindUseCase @Inject constructor(
    private val spoolman: SpoolmanRepository,
)
```

| Method | Returns | Purpose |
|---|---|---|
| `suspend operator fun invoke(uid: CardUid, fromSpoolId: Int, toSpoolId: Int): MoveOnBindResult` | sealed | FR-5.2 — sequence `removeCardUidFromSpool(from)` then `appendCardUidToSpool(to)`. On any HTTP failure, surface `MoveOnBindResult.PartialFailure` per Q11=A (no silent partial commits). |

```kotlin
class TwoTagUseCase @Inject constructor(
    private val nfc: NfcRepository,
    private val spoolman: SpoolmanRepository,
)
```

| Method | Returns | Purpose |
|---|---|---|
| `suspend operator fun invoke(spoolId: Int, expectedPayload: OpenSpoolPayload): TwoTagResult` | sealed | FR-6 — arm Write with second tag's payload (identical to first), verify, then PATCH `lot_nr` to append second UID. |

```kotlin
class VendorUidOnlyPairUseCase @Inject constructor(
    private val spoolman: SpoolmanRepository,
)
```

| Method | Returns | Purpose |
|---|---|---|
| `suspend operator fun invoke(req: VendorUidOnlyRequest): VendorUidOnlyResult` | sealed | FR-4.9 — Spoolman create chain or PATCH (depending on existing-spool vs. new-spool path). NDEF write skipped entirely. |

```kotlin
class RawWriteUseCase @Inject constructor(
    private val nfc: NfcRepository,
)
```

| Method | Returns | Purpose |
|---|---|---|
| `suspend operator fun invoke(payload: OpenSpoolPayload): RawWriteResult` | sealed | FR-4.8 — arm Write with `spool_id` omitted; no Spoolman side effects. |

### Use-case result types (sealed; named non-happy paths surface as variants)

```kotlin
sealed interface CreateAndPairResult {
    data class Success(val spoolId: Int, val uid: CardUid) : CreateAndPairResult
    data class SpoolmanFailed(val outcome: SpoolmanOutcome.HttpError) : CreateAndPairResult
    data class WriteFailed(val reason: String) : CreateAndPairResult
    data class VerifyMismatch(val expected: ByteArray, val actual: ByteArray) : CreateAndPairResult
    data class Cancelled(val reason: String) : CreateAndPairResult
}
// MoveOnBindResult, TwoTagResult, etc. follow the same shape — Success
// + named-non-happy-path variants per Q7=B in stories.
```

---

## 6. ViewModels (Q-CM4=C, Q-DP1=A, Q-DP2=C, Q-DP3=C)

### MainViewModel

```kotlin
@HiltViewModel
class MainViewModel @Inject constructor(
    private val nfc: NfcRepository,
    private val spoolman: SpoolmanRepository,
    private val materials: MaterialBrandRepository,
    private val readAndPair: ReadAndPairUseCase,
    private val createAndPair: CreateAndPairUseCase,
    private val moveOnBind: MoveOnBindUseCase,
    private val twoTag: TwoTagUseCase,
    private val vendorUidOnly: VendorUidOnlyPairUseCase,
    private val rawWrite: RawWriteUseCase,
) : ViewModel()
```

| Method / Property | Returns | Purpose |
|---|---|---|
| `val state: StateFlow<MainUiState>` | single state | Q-DP1=A. Combines form, NFC state, Spoolman cache, banner, active flow. |
| `val effects: Flow<UiEffect>` | `Channel<UiEffect>` flow | Q-DP3=C. Transient snackbar / nav events. |
| `fun onReadTapped()` | — | Triggers `ReadAndPairUseCase`. |
| `fun onWriteTapped()` | — | Branches by tag classification: blank/OpenSpool → `CreateAndPairUseCase`; vendor → opens `VendorUidOnlyOptInSheet`. |
| `fun onSpoolSelected(spool: Spool)` | — | FR-3.6 dropdown-driven prefill. |
| `fun onMaterialChanged(m: Material)` | — | Form edit. |
| `fun onBrandChanged(b: Brand)` | — | Form edit. |
| `fun onColorChanged(hex: String)` | — | Form edit. |
| `fun onTempChanged(panel: TempPanelEdit)` | — | Form edit. |
| `fun onPairAnotherTagTapped()` | — | FR-6.1. Triggers `TwoTagUseCase`. |
| `fun onRawWriteToggled(enabled: Boolean)` | — | FR-4.8. |
| `fun onRepairResult(event: RepairResult)` | — | Q-DP2=C — sealed event from `RepairConfirmSheet`. |
| `fun onVendorOptInResult(event: VendorOptInResult)` | — | Q-DP2=C — sealed event from `VendorUidOnlyOptInSheet`. |
| `fun onSettingsTapped()` | — | Nav → `SettingsScreen`. |

### SettingsViewModel

```kotlin
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val spoolman: SpoolmanRepository,
) : ViewModel()
```

| Method / Property | Returns | Purpose |
|---|---|---|
| `val state: StateFlow<SettingsUiState>` | single state | URL field + connectivity + sort + theme. |
| `val effects: Flow<UiEffect>` | flow | Snackbar for Test-connection result. |
| `fun onUrlChanged(url: String)` | — | FR-9.1. Saves on focus loss. |
| `fun onTestConnectionTapped()` | — | Q-CD1.1=A — owns the only refresh action. Calls `spoolman.probe()`. |
| `fun onSortOrderChanged(order: SortOrder)` | — | FR-9.2. |
| `fun onThemeOverrideChanged(theme: ThemeOverride)` | — | FR-9.3. |

### Sheet ViewModels

Each owns a small `StateFlow<…UiState>` and exposes confirm/cancel
methods that terminate the sheet and return a result via the parent VM
(Q-DP2=C).

```kotlin
@HiltViewModel class RepairConfirmViewModel : ViewModel()       // FR-5.2
@HiltViewModel class VendorOptInViewModel : ViewModel()         // FR-4.9
@HiltViewModel class AddCustomMaterialViewModel : ViewModel()   // FR-8.5
@HiltViewModel class AddCustomBrandViewModel : ViewModel()      // FR-8.5
```

---

## 7. UiState shapes

```kotlin
data class MainUiState(
    val form: FormState,
    val spoolman: SpoolmanState,                     // dropdown + cache
    val nfc: NfcState,                               // mirrors NfcRepository.state
    val banner: BannerState,                         // Hidden | Offline (Q-CD1.1=A — passive)
    val activeFlow: ActiveFlow,                     // sealed: Idle | ReadingForPair | Writing | Verifying | Repairing | TwoTag | VendorOptIn | RawWriting
)

data class FormState(
    val material: Material?,
    val brand: Brand?,
    val colorHex: String,
    val variant: String?,
    val tempRanges: TempRanges,
    val selectedSpoolId: Int?,
    val rawWriteMode: Boolean,
)

sealed interface BannerState {
    object Hidden : BannerState                      // URL not configured OR Spoolman reachable
    data class Offline(val lastError: String?) : BannerState   // URL configured AND last call failed
}

sealed interface UiEffect {
    data class ShowSnackbar(val message: String) : UiEffect
    data class Navigate(val destination: String) : UiEffect
}
```

---

## 8. Threading rules (Q-DP4=A)

- VM coroutines launched on `viewModelScope` (Main by default).
- Repository methods are `suspend` and main-safe.
- `withContext(Dispatchers.IO)` only inside repositories, only around
  blocking APIs (NFC). Retrofit + DataStore are already off-main.
- Tests use `runTest` + `Dispatchers.setMain(testDispatcher)`. No
  injected `DispatcherProvider`.
