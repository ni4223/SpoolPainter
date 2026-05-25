# U3 — Domain Entities

**Stage**: CONSTRUCTION → Per-Unit Loop → Functional Design Part 2 (U3)
**Unit**: U3 — Spoolman Client Overhaul
**Source decisions**: `aidlc-docs/construction/plans/u3-spoolman-repository-functional-design-plan.md` (Q-U3-1 .. Q-U3-11)

---

## 1. Type Inventory

### 1.1 Public types (consumed by U5..U9)

| Type | Kind | Package | Source FRs / NFRs |
|---|---|---|---|
| `SpoolmanRepository` | `class` (`@Singleton`, Hilt) | `data.remote.spoolman` | FR-3.2, FR-4.6, FR-5.2, FR-6.2, FR-7.x, FR-8.3, FR-10.x, NFR-1.2, NFR-7.1 |
| `SpoolmanOutcome<out T>` | `sealed interface` | `data.remote.spoolman` | NFR-7.1 — sealed network-error contract |
| `ConnectivityState` | `sealed interface` | `data.remote.spoolman` | FR-10.2, Q-CD1=A — single connectivity source |
| `NewSpoolRequest` | `data class` | `data.remote.spoolman` | FR-7 — DTO for create chain |

### 1.2 Wire-only types (HTTP body shapes, internal to `data/remote/spoolman`)

Per Q-U3-7=C, GET reads keep v1 wire types verbatim; POST/PATCH gain dedicated request DTOs.

| Type | Kind | Purpose |
|---|---|---|
| `SpoolmanSpool` | v1 `data class`, retained | GET `/api/v1/spool` body element + POST/PATCH response |
| `SpoolmanFilament` | v1 `data class`, **extended with `id` field on vendor + `vendor`** | GET / POST response shape |
| `SpoolmanVendor` | v1 `data class`, **extended with `id`** | GET / POST response shape |
| `CreateVendorRequest` | new `data class` | POST `/api/v1/vendor` body |
| `CreateFilamentRequest` | new `data class` | POST `/api/v1/filament` body |
| `CreateSpoolRequest` | new `data class` | POST `/api/v1/spool` body |
| `UpdateSpoolLotNrRequest` | new `data class` | PATCH `/api/v1/spool/{id}` body — `{ "lot_nr": "<encoded>" }` |
| `SpoolmanInfo` | new `data class` | GET `/api/v1/info` body (only `version` field consumed; everything else ignored) |

### 1.3 Test-only types

| Type | Kind | Purpose |
|---|---|---|
| `FakeSpoolmanApi` | test class | In-memory `SpoolmanApi` impl scripted by unit tests; tracks call log + injectable failures |

---

## 2. Detailed type contracts

### 2.1 `SpoolmanOutcome<out T>` (sealed interface)

```kotlin
sealed interface SpoolmanOutcome<out T> {
    data class Success<out T>(val data: T) : SpoolmanOutcome<T>
    data class HttpError(val code: Int, val message: String) : SpoolmanOutcome<Nothing>
    data class NetworkError(val cause: Throwable) : SpoolmanOutcome<Nothing>
    data class ParseError(val cause: Throwable) : SpoolmanOutcome<Nothing>
}

inline fun <T, R> SpoolmanOutcome<T>.flatMap(
    block: (T) -> SpoolmanOutcome<R>,
): SpoolmanOutcome<R> = when (this) {
    is SpoolmanOutcome.Success -> block(data)
    is SpoolmanOutcome.HttpError, is SpoolmanOutcome.NetworkError, is SpoolmanOutcome.ParseError ->
        @Suppress("UNCHECKED_CAST") (this as SpoolmanOutcome<R>)
}

inline fun <T, R> SpoolmanOutcome<T>.map(
    block: (T) -> R,
): SpoolmanOutcome<R> = flatMap { SpoolmanOutcome.Success(block(it)) }
```

**Invariants** (BR-U3-O-*):
- Total over Retrofit responses: every Retrofit call site MUST map to exactly one of the four variants. There is no fifth state.
- `HttpError.code` is the actual HTTP status code (e.g., `404`, `503`).
- `HttpError.message` is the response status line / errorBody snippet (truncated to ≤ 200 chars to keep memory bounded).
- `NetworkError.cause` wraps `IOException` subclasses + a synthetic `UrlNotConfiguredException` (zero-config short-circuit, BR-U3-CFG-1).
- `ParseError.cause` wraps `JsonSyntaxException` + Retrofit conversion exceptions.
- `flatMap` short-circuits on first non-`Success` and propagates the same outcome (covariance preserved via the unchecked cast — safe because non-`Success` carries no `T`).
- `map` is defined in terms of `flatMap` so error short-circuiting carries through transformations without re-wrapping.
- Cancellation never produces an outcome — `CancellationException` is rethrown by every method (BR-U3-CANCEL-1).

### 2.2 `ConnectivityState` (sealed interface)

```kotlin
sealed interface ConnectivityState {
    data object Unknown : ConnectivityState
    data object Reachable : ConnectivityState
    data class Unreachable(val reason: String) : ConnectivityState
}
```

**Invariants** (BR-U3-CONN-*):
- `Unknown` ≡ "no URL configured *or* not yet probed". Banner stays hidden in this state (services.md §7).
- `Reachable` ≡ "most recent network call returned 2xx (or any HTTP response — even a 4xx means the server is up)".
- `Unreachable(reason)` ≡ "most recent network call hit `IOException` (transport failure, DNS failure, timeout)". `reason` is a short, user-presentable string (e.g., `"Connection timed out"`, `"Unable to resolve host"`).
- A `ParseError` does NOT change `ConnectivityState` — it's a server-data fault, not a connectivity fault.
- An `HttpError` (any 4xx / 5xx) sets state to `Reachable` (server responded — banner should NOT appear). Caller decides what to do with the HTTP status.
- A `NetworkError(UrlNotConfigured)` sets state to `Unknown`, NOT `Unreachable`.

### 2.3 `NewSpoolRequest` (data class)

```kotlin
data class NewSpoolRequest(
    val vendorName: String,        // free-form; FR-7.1 lookup key (case-insensitive)
    val materialName: String,      // free-form; part of FR-7.2 lookup tuple (case-insensitive)
    val colorHex: String,          // exact hex (already canonical from form); FR-7.2 tuple member
    val variant: String?,          // free-form, optional; null/empty are equivalent for FR-7.2 match
    val tempRanges: TempRanges,    // forward-reference: domain/primitives/TempRanges (lands in U8/U9 if not earlier; U3 declares the data class internally if absent — see invariants)
    val cardUid: CardUid,          // FR-7.3 — embedded into spool's lot_nr as "card_uid:<hex>"
)

data class TempRanges(             // U3-local declaration — promoted to a shared primitive when U8/U9 need it
    val extruderMin: Int?,
    val extruderMax: Int?,
    val bedMin: Int?,
    val bedMax: Int?,
)
```

**Invariants** (BR-U3-REQ-*):
- `cardUid.hex` MUST be non-empty for `createSpoolForNewFilament` (BR-U3-CHAIN-EMPTY); empty is rejected with `NetworkError(IllegalArgumentException(...))` short-circuit before any HTTP fires.
- `vendorName` SHALL be trimmed before lookup; empty after trim is rejected with `NetworkError(IllegalArgumentException(...))`.
- `materialName` SHALL be trimmed; empty after trim is rejected likewise.
- `colorHex` is taken verbatim — repository does not canonicalise (form layer in U6a is responsible for that).
- `variant`: null and empty-string are equivalent for FR-7.2 match (BR-U3-FIL-VARIANT). Stored as null on POST when blank.
- `TempRanges` fields are passed verbatim to the filament POST; nulls are omitted from the JSON body so Spoolman's defaults apply (BR-U3-FIL-TEMP-NULL).

### 2.4 `SpoolmanRepository` (`@Singleton`)

```kotlin
@Singleton
class SpoolmanRepository @Inject constructor(
    private val settings: SettingsRepository,
    private val apiFactory: SpoolmanApiFactory,                  // wraps Retrofit-build per Q-U3-3
    @AppScope private val scope: CoroutineScope,                  // U1's @Singleton scope; @AppScope qualifier introduced in U3
    private val ioDispatcher: CoroutineDispatcher,                // Dispatchers.IO; provided by RepositoryModule
) {
    val connectivity: StateFlow<ConnectivityState>                // initial Unknown (Q-U3-8=A)
    val vendors: StateFlow<List<SpoolmanVendor>>
    val filaments: StateFlow<List<SpoolmanFilament>>
    val spools: StateFlow<List<SpoolmanSpool>>

    suspend fun probe(): SpoolmanOutcome<Unit>
    suspend fun findSpoolsByCardUid(uid: CardUid): SpoolmanOutcome<List<SpoolmanSpool>>
    suspend fun createSpoolForNewFilament(req: NewSpoolRequest): SpoolmanOutcome<SpoolmanSpool>
    suspend fun appendCardUidToSpool(spoolId: Int, uid: CardUid): SpoolmanOutcome<SpoolmanSpool>
    suspend fun removeCardUidFromSpool(spoolId: Int, uid: CardUid): SpoolmanOutcome<SpoolmanSpool>
    suspend fun refresh(): SpoolmanOutcome<Unit>
}
```

**Constructor invariants** (BR-U3-CTOR-*):
- `apiFactory` produces a fresh `SpoolmanApi` from a base URL string (Q-U3-3=C); shared `OkHttpClient` is pinned inside the factory.
- `scope` MUST be the Hilt-managed `@Singleton CoroutineScope` from U1's `RepositoryModule` (Q-U3-11=A).
- An `init` block launches a single collector on `settings.settings` that rebuilds the cached `SpoolmanApi` whenever `url` changes (and clears caches + sets `connectivity = Unknown` if the new URL is blank).

**Method invariants** (BR-U3-RM-*):
- Every `suspend` method body runs `withContext(ioDispatcher) { ... }` for HTTP work.
- Every method updates `connectivity` as a side effect of the underlying call (BR-U3-CONN-* rules).
- No method ever throws `IOException`, `JsonSyntaxException`, `HttpException`, or `IllegalArgumentException` to the caller (NFR-7.1 — total error surface).
- `CancellationException` IS rethrown (Kotlin coroutines correctness — never swallow cancellation).

### 2.5 `SpoolmanApi` (Retrofit interface)

```kotlin
interface SpoolmanApi {

    // GET / read --------------------------------------------------------------

    @GET("api/v1/info")
    suspend fun getInfo(): Response<SpoolmanInfo>

    @GET("api/v1/spool")
    suspend fun findSpoolsByLotNr(
        @Query("lot_nr") lotNrSubstring: String,
    ): Response<List<SpoolmanSpool>>

    @GET("api/v1/spool")
    suspend fun listSpools(
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
    ): Response<List<SpoolmanSpool>>

    @GET("api/v1/filament")
    suspend fun listFilaments(): Response<List<SpoolmanFilament>>

    @GET("api/v1/vendor")
    suspend fun listVendors(): Response<List<SpoolmanVendor>>

    // POST / create -----------------------------------------------------------

    @POST("api/v1/vendor")
    suspend fun createVendor(@Body body: CreateVendorRequest): Response<SpoolmanVendor>

    @POST("api/v1/filament")
    suspend fun createFilament(@Body body: CreateFilamentRequest): Response<SpoolmanFilament>

    @POST("api/v1/spool")
    suspend fun createSpool(@Body body: CreateSpoolRequest): Response<SpoolmanSpool>

    // PATCH / update ----------------------------------------------------------

    @PATCH("api/v1/spool/{id}")
    suspend fun patchSpoolLotNr(
        @Path("id") spoolId: Int,
        @Body body: UpdateSpoolLotNrRequest,
    ): Response<SpoolmanSpool>
}
```

**Contract invariants** (BR-U3-API-*):
- `findSpoolsByLotNr.lotNrSubstring` is sent verbatim — encoding is the repository's responsibility; the Retrofit method DOES NOT prepend `card_uid:` (that's a repository policy).
- All response bodies use existing v1 model classes (`SpoolmanSpool`, `SpoolmanFilament`, `SpoolmanVendor`) plus `SpoolmanInfo`.
- All POST/PATCH bodies use new v2 request DTOs (`Create*Request`, `UpdateSpoolLotNrRequest`).

### 2.6 Wire model extensions (extending v1 types in `domain/models/SpoolmanModels.kt`)

```kotlin
// v2-extended fields are nullable to stay backward-compatible with v1 instances.
data class SpoolmanSpool(
    val id: Int? = null,
    val filament: SpoolmanFilament,
    val remaining_weight: Float? = null,
    val used_weight: Float = 0f,
    val location: String? = null,
    val lot_nr: String? = null,
    val archived: Boolean = false,
    // v2 fields below (none currently required — placeholder for U6a/U8 if they need extras)
)

data class SpoolmanFilament(
    val id: Int,
    val name: String? = null,
    val material: String? = null,
    val vendor: SpoolmanVendor? = null,
    val color_hex: String? = null,
    val settings_extruder_temp: Int? = null,
    val settings_bed_temp: Int? = null,
)

data class SpoolmanVendor(
    val id: Int? = null,                 // v2 — required for FR-7.2 filament join key
    val name: String,
)

data class SpoolmanInfo(
    val version: String? = null,
)
```

### 2.7 Request DTOs (new in `data/remote/spoolman/SpoolmanRequests.kt`)

```kotlin
data class CreateVendorRequest(
    val name: String,
)

data class CreateFilamentRequest(
    val name: String?,                   // optional — Spoolman auto-generates if absent
    val vendor_id: Int,
    val material: String,
    val color_hex: String,
    val settings_extruder_temp: Int?,
    val settings_bed_temp: Int?,
)

data class CreateSpoolRequest(
    val filament_id: Int,
    val lot_nr: String,                  // exactly "card_uid:<hex>"
)

data class UpdateSpoolLotNrRequest(
    val lot_nr: String,                  // pre-encoded by CardUidEncoding
)
```

### 2.8 `SpoolmanApiFactory`

```kotlin
@Singleton
class SpoolmanApiFactory @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
) {
    fun create(baseUrl: String): SpoolmanApi { /* Retrofit.Builder(...) */ }
}
```

**Invariants** (BR-U3-FAC-*):
- Returned `SpoolmanApi` MUST share the single `OkHttpClient` instance (connection-pool reuse).
- `baseUrl` is normalised to end in `/` (BR-U3-URL-1).
- Construction is pure — no I/O until the API is actually called.

---

## 3. Lifecycle / state diagrams

### 3.1 `ConnectivityState` transitions

```
                    ┌─────────────────┐
   URL blank ─────► │     Unknown     │ ◄────── ParseError (no transition; redrawn for clarity)
                    └────────┬────────┘
                             │  any HTTP response received
                             ▼
                    ┌─────────────────┐
                    │    Reachable    │ ◄──── HttpError(any code)
                    └────────┬────────┘
                             │  IOException
                             ▼
                    ┌─────────────────┐
   URL blank ────►  │ Unreachable(r)  │
                    └─────────────────┘
```

Transitions are atomic (single `MutableStateFlow.value =` assignment per call).

### 3.2 FR-7 chain state diagram (per-call lifetime)

```
START
  │
  ▼
[GET vendors]──────error──► return outcome (cache untouched)
  │
  vendor matched (case-insensitive name)
  │  no match
  │  └──[POST vendor]──error──► return outcome (no rollback)
  │
  ▼ (vendor_id known)
[GET filaments]──error──► return outcome
  │
  filament matched on (vendor_id, material(ci), color_hex, variant(null≡""))
  │  no match
  │  └──[POST filament]──error──► return outcome (vendor stays in Spoolman; no rollback)
  │
  ▼ (filament_id known)
[POST spool with lot_nr=card_uid:<hex>]──error──► return outcome (vendor + filament stay; no rollback)
  │
  ▼
patch caches in-place (vendors / filaments / spools)
  │
  ▼
return Success(spool)
```

Q11=A is reflected by "no rollback": the outcome's `HttpError.message` carries enough to surface the partial commit to the user; retry routes through whichever step now finds a hit.

---

## 4. Cross-references

- **Builds on U2**: `CardUid` (`domain/primitives/CardUid.kt`), `CardUidEncoding` (`data/remote/spoolman/CardUidEncoding.kt`), `CardUidEncoding.PREFIX` (= `"card_uid:"`).
- **Builds on U1**: `@Singleton CoroutineScope` provided by `RepositoryModule`; `SettingsRepository` (read of `url`).
- **Consumed by**:
  - U5 — `findSpoolsByCardUid`, `connectivity`, `filaments`, `spools`, `SpoolmanOutcome`.
  - U6a — `createSpoolForNewFilament`, `appendCardUidToSpool` (existing-spool path).
  - U6b — `removeCardUidFromSpool`, `appendCardUidToSpool`.
  - U7 — none (raw write is Spoolman-free; U7 must NOT inject `SpoolmanRepository`).
  - U8 — `vendors`, `filaments` (precedence merge for material/brand pickers).
  - U9 — `connectivity`, `probe()` (Settings Test connection + banner).
