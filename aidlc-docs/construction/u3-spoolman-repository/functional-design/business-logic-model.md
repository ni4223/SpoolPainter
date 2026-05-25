# U3 — Business Logic Model

**Stage**: CONSTRUCTION → Per-Unit Loop → Functional Design Part 2 (U3)
**Unit**: U3 — Spoolman Client Overhaul
**Companion**: `business-rules.md` (rule numbering) + `domain-entities.md` (type shapes)

---

## 1. Overview

U3 produces the single source of truth for talking to a Spoolman server. Every flow unit (U5..U9) routes through `SpoolmanRepository`. The repository's contract is total: every call returns a sealed `SpoolmanOutcome<T>`, every call updates the observable `connectivity: StateFlow<ConnectivityState>`, and no call ever throws (except `CancellationException`).

Five primary business processes:
1. **Connectivity probe** (S-9.1) — Settings "Test connection".
2. **UID lookup** (S-3.1) — "Is this card already paired?"
3. **Create chain** (S-7.1 / S-7.2 / S-7.3) — Vendor → Filament → Spool POST sequence.
4. **PATCH `lot_nr`** (S-4.5 / S-5.2 / S-6.2) — Read-modify-write of comma-separated UID list.
5. **List refresh** (FR-8.3) — Force-fetch vendors / filaments / spools.

The repository also owns the **base-URL lifecycle**: it observes `SettingsRepository.url` and rebuilds Retrofit when the URL changes.

---

## 2. Process diagrams

### 2.1 UID lookup (S-3.1 / FR-3.2)

```
caller (U5)
   │ findSpoolsByCardUid(uid)
   ▼
[SpoolmanRepository.findSpoolsByCardUid]
   │
   ├─ uid.hex empty? ──yes──► return Success([]) [BR-U3-FIND-1]
   │   (no network)
   │
   ├─ cachedApi == null? ──yes──► connectivity = Unknown
   │                              return NetworkError(UrlNotConfigured) [BR-U3-CFG-1]
   │
   ▼
withContext(IO) { runCatching { api.findSpoolsByLotNr("card_uid:${uid.hex}") } }
   │
   ├─ Success(list) ──► connectivity = Reachable; return Success(list) [BR-U3-CONN-2]
   ├─ HttpError      ──► connectivity = Reachable; return HttpError [BR-U3-CONN-3]
   ├─ IOException    ──► connectivity = Unreachable(reason); return NetworkError [BR-U3-CONN-4]
   └─ JsonSyntaxEx   ──► connectivity unchanged; return ParseError [BR-U3-CONN-6]
```

### 2.2 Create chain (S-7.1 / S-7.2 / S-7.3 / FR-7)

```
caller (U6a)
   │ createSpoolForNewFilament(req)
   ▼
[validate req: vendorName/materialName/cardUid non-empty]   [BR-U3-CHAIN-1]
   │ pass
   ▼
resolveOrCreateVendor(req.vendorName)                       [BR-U3-VEN-*]
   │ flatMap
   ▼
resolveOrCreateFilament(vendor, req)                        [BR-U3-FIL-*]
   │ flatMap
   ▼
createSpoolStep(filament, req.cardUid)                      [BR-U3-SP-*]
   │ flatMap
   ▼
patch caches in place: spools/filaments/vendors             [BR-U3-CHAIN-6 / BR-U3-CACHE-*]
   │
   ▼
return Success(spool)

Any non-Success short-circuits (Q11=A) — no rollback.
```

### 2.3 Append-UID PATCH (S-4.5 / FR-4.6 / S-6.2 / FR-6.2)

```
caller (U6a / U6b)
   │ appendCardUidToSpool(spoolId, uid)
   ▼
[validate uid.hex non-empty]                                [BR-U3-APPEND-8]
   │
   ▼
GET /api/v1/spool/{spoolId}                                  [BR-U3-APPEND-1]
   │
   ▼
decoded := CardUidEncoding.decode(spool.lot_nr ?: "")        [BR-U3-APPEND-2]
   │
   ▼
newUids := decoded.uids + uid (dedup via BR-U2-ENC-1)        [BR-U3-APPEND-3]
encoded := CardUidEncoding.encode(newUids, decoded.opaque)   [BR-U3-APPEND-4]
   │
   ▼
PATCH /api/v1/spool/{spoolId} { "lot_nr": encoded }          [BR-U3-APPEND-5]
   │
   ▼
patch spools cache (replace by id)                           [BR-U3-APPEND-6 / BR-U3-CACHE-1]
   │
   ▼
return Success(updatedSpool)
```

### 2.4 Remove-UID PATCH (S-5.2 / FR-5.2 → consumed by U6b)

```
caller (U6b MoveOnBindUseCase)
   │ removeCardUidFromSpool(spoolId, uid)
   ▼
[validate uid.hex non-empty]                                 [BR-U3-REMOVE-7]
   │
   ▼
GET /api/v1/spool/{spoolId}                                  [BR-U3-REMOVE-1]
   │
   ▼
decoded := CardUidEncoding.decode(spool.lot_nr ?: "")
newUids := decoded.uids - uid                                [BR-U3-REMOVE-2]
encoded := CardUidEncoding.encode(newUids, decoded.opaque)   [BR-U3-REMOVE-3]
   │
   ▼
PATCH /api/v1/spool/{spoolId} { "lot_nr": encoded }          [BR-U3-REMOVE-4 / BR-U3-REMOVE-5]
   │
   ▼
patch spools cache (replace by id)                           [BR-U3-REMOVE-6 / BR-U3-CACHE-1]
   │
   ▼
return Success(updatedSpool)
```

### 2.5 Probe (S-9.1 / FR-10.2)

```
caller (U9 SettingsViewModel)
   │ probe()
   ▼
GET /api/v1/info                                             [BR-U3-PROBE-1]
   │
   ▼
Success body discarded; connectivity = Reachable             [BR-U3-PROBE-2 / BR-U3-CONN-2]
return Success(Unit)

Errors mapped per BR-U3-O-* + BR-U3-CONN-*; caches untouched.[BR-U3-PROBE-4]
```

### 2.6 Refresh (FR-8.3 / NFR-7.2)

```
caller (manual UI / U5 first-fetch)
   │ refresh()
   ▼
listVendors() ─error─► return outcome (vendors cache untouched) [BR-U3-REFRESH-2]
   │ Success(v)
   ▼
listFilaments() ─error─► return outcome (filaments untouched)
   │ Success(f)
   ▼
listSpools() ─error─► return outcome (spools untouched)
   │ Success(s)
   ▼
vendors.value = v; filaments.value = f; spools.value = s     [BR-U3-REFRESH-3]
connectivity = Reachable
return Success(Unit)
```

### 2.7 Base-URL change handling (Q-U3-3=C / Q-U3-11=A)

```
init {
    settings.settings
        .map { it.url }
        .distinctUntilChanged()
        .onEach { url ->                                       [BR-U3-URL-2]
            cachedApi = if (url.isBlank()) null
                        else apiFactory.create(url)
            vendors.value = emptyList()
            filaments.value = emptyList()
            spools.value = emptyList()
            connectivity.value = ConnectivityState.Unknown     [BR-U3-URL-3 / BR-U3-CONN-8]
        }
        .launchIn(scope)
}
```

`scope` is the Hilt `@Singleton CoroutineScope` from U1 — collection lifetime ≡ process lifetime, which is acceptable for a `@Singleton` repository.

### 2.8 ConnectivityState transition table

| Trigger | Pre-state | Post-state | Rule |
|---|---|---|---|
| URL change to blank | * | `Unknown` | BR-U3-CONN-8 |
| Successful HTTP response (any 2xx) | * | `Reachable` | BR-U3-CONN-2 |
| HTTP error (any 4xx/5xx) | * | `Reachable` | BR-U3-CONN-3 |
| IOException (timeout, DNS, …) | * | `Unreachable(reason)` | BR-U3-CONN-4 |
| URL-not-configured short-circuit | * | `Unknown` | BR-U3-CONN-5 |
| ParseError | * | unchanged | BR-U3-CONN-6 |

---

## 3. Behavioural deltas vs. v1

| Aspect | v1 (`SpoolmanService`) | v2 (`SpoolmanRepository`) | Driver |
|---|---|---|---|
| Error surface | `try { … } catch (Exception) { return cached ?: emptyList() }` (silent swallow) | sealed `SpoolmanOutcome<T>` — every failure visible | NFR-7.1 |
| Connectivity reporting | none — UI inferred connectivity from empty list | dedicated `ConnectivityState` `StateFlow` | FR-10.2 / Q-CD1=A |
| HTTP-error semantics | mapped to "empty list" (any 4xx ≡ no spools found) | `HttpError(code)` — code surfaces to UI; connectivity = Reachable | NFR-7.1 |
| Pagination | client-side loop over `?limit=10&offset=N` | none — Spoolman returns full lists at v2 catalogue sizes; pagination becomes a U10 concern if needed | scope reduction; FR-8.3 |
| Caching | local `cachedFilaments` with 30 s TTL | three `MutableStateFlow` caches, never expire; explicit `refresh()` | Q-S4=A / NFR-7.2 |
| Lookup-by-UID | not supported | `findSpoolsByCardUid` (FR-3.2) | NEW for v2 |
| Create chain | not supported | `createSpoolForNewFilament` orchestrating vendor → filament → spool | NEW for v2 |
| PATCH | not supported | `appendCardUidToSpool` / `removeCardUidFromSpool` | NEW for v2 |
| Threading | implicit (Retrofit `suspend`) | explicit `withContext(ioDispatcher)` | NFR-1.2 layer discipline |
| URL change handling | constructor-fixed baseUrl per `SpoolmanService` instance | observed via `SettingsRepository`; live rebuild | FR-9.1 / S-9.1 |
| Logging | none wired | `BASIC` on debug | Q-U3-5=B |

---

## 4. Threading model

- **Public surface**: every method is `suspend`. Callers are ViewModels (Main-thread coroutines) or other repositories.
- **HTTP work**: wrapped in `withContext(ioDispatcher) { … }` (BR-U3-RM-1). Dispatcher is provided by `RepositoryModule` and replaceable in tests.
- **State updates**: `MutableStateFlow.value = …` is thread-safe; updates happen on whichever dispatcher the call is on (typically `ioDispatcher`). Observers (`collect`-ing on Main) get updates via flow's built-in cross-context delivery.
- **URL collector**: launched on the Hilt `@Singleton CoroutineScope` (Default dispatcher), runs for app lifetime. Updates the volatile `cachedApi` reference on URL change.

---

## 5. Pseudocode (orchestration only — implementation lives in code-gen)

```kotlin
suspend fun findSpoolsByCardUid(uid: CardUid): SpoolmanOutcome<List<SpoolmanSpool>> {
    if (uid.hex.isEmpty()) return Success(emptyList())          // BR-U3-FIND-1
    val api = cachedApi ?: return urlNotConfigured()             // BR-U3-CFG-1
    return performHttp("findSpools") {
        api.findSpoolsByLotNr("${CardUidEncoding.PREFIX}${uid.hex}")  // BR-U3-FIND-2
    }
}

suspend fun createSpoolForNewFilament(req: NewSpoolRequest): SpoolmanOutcome<SpoolmanSpool> {
    if (req.cardUid.hex.isEmpty()) return invalidArg("cardUid is empty")
    val vendorName = req.vendorName.trim().takeIf { it.isNotEmpty() }
        ?: return invalidArg("vendorName is empty")
    val materialName = req.materialName.trim().takeIf { it.isNotEmpty() }
        ?: return invalidArg("materialName is empty")
    val api = cachedApi ?: return urlNotConfigured()

    return resolveOrCreateVendor(api, vendorName)
        .flatMap { vendor ->
            resolveOrCreateFilament(api, vendor, req.copy(materialName = materialName))
        }
        .flatMap { filament ->
            createSpoolStep(api, filament, req.cardUid)
        }
        .also { outcome ->
            if (outcome is Success) {
                // Cache patch — vendor/filament inserted in inner steps;
                // spool prepended here.
                spools.value = listOf(outcome.data) + spools.value.filter { it.id != outcome.data.id }
            }
        }
}

internal suspend fun resolveOrCreateVendor(
    api: SpoolmanApi, name: String,
): SpoolmanOutcome<SpoolmanVendor> {
    val list = performHttp("listVendors") { api.listVendors() }
    return list.flatMap { vendors ->
        val match = vendors.firstOrNull { it.name.equals(name, ignoreCase = true) }   // BR-U3-VEN-2
        if (match != null && match.id != null) {
            Success(match)
        } else {
            performHttp("createVendor") { api.createVendor(CreateVendorRequest(name)) }
                .also { if (it is Success) prependVendor(it.data) }
        }
    }
}

private suspend inline fun <T> performHttp(
    label: String,
    block: () -> Response<T>,
): SpoolmanOutcome<T> = withContext(ioDispatcher) {
    try {
        val response = block()
        when {
            !response.isSuccessful ->
                httpError(response.code(), response.errorBody()?.string()?.take(200) ?: response.message())
            response.body() == null ->
                ParseError(IllegalStateException("empty body for $label"))
            else ->
                Success(response.body()!!).also { connectivity.value = Reachable }
        }
    } catch (e: CancellationException) {
        throw e                                                 // BR-U3-CANCEL-1
    } catch (e: IOException) {
        connectivity.value = Unreachable(e.message ?: e::class.simpleName ?: "Network error")
        NetworkError(e)
    } catch (e: JsonSyntaxException) {
        ParseError(e)
    }
}

private fun httpError(code: Int, message: String): SpoolmanOutcome<Nothing> {
    connectivity.value = Reachable                              // BR-U3-CONN-3
    return HttpError(code, message)
}

private fun urlNotConfigured(): SpoolmanOutcome<Nothing> {
    connectivity.value = Unknown                                 // BR-U3-CONN-5
    return NetworkError(UrlNotConfiguredException)
}
```

(Final code lives in `app/src/main/.../SpoolmanRepository.kt`; this snippet is illustrative for reviewers.)

---

## 6. Acceptance-criteria coverage

| Story | AC | Where covered |
|---|---|---|
| **S-3.1** | `GET /api/v1/spool?lot_nr=card_uid:<uid>` | BR-U3-FIND-2 |
| **S-3.1** | `card_uid:` prefix included in search | BR-U3-FIND-2 (uses `CardUidEncoding.PREFIX`) |
| **S-3.1** | HTTP / connection errors surface | BR-U3-O-* + BR-U3-CONN-* |
| **S-4.5** | PATCH body computed via `parse(existingLotNr) ∪ card_uid:<uid>` (idempotent) | BR-U3-APPEND-2 .. APPEND-7 |
| **S-4.5** | PATCH HTTP errors ⇒ visible banner | BR-U3-CONN-4 (banner driven by `connectivity`) |
| **S-7.1** | Vendor lookup case-insensitive | BR-U3-VEN-2 |
| **S-7.1** | No-match POST `/api/v1/vendor` | BR-U3-VEN-4 |
| **S-7.1** | HTTP errors ⇒ no further step | BR-U3-CHAIN-5 (flatMap short-circuit) |
| **S-7.2** | Equality on (vendor_id, material(ci), color_hex, variant) | BR-U3-FIL-2 |
| **S-7.2** | Variant null/empty equivalent | BR-U3-FIL-3 / BR-U3-REQ-VARIANT |
| **S-7.2** | No-match POST `/api/v1/filament` | BR-U3-FIL-5 |
| **S-7.3** | POST body sets filament_id + lot_nr=card_uid:<uid> | BR-U3-SP-1 / BR-U3-SP-2 |
| **S-7.3** | HTTP errors ⇒ no partial commit visible to caller (sealed outcome) | BR-U3-CHAIN-7 + BR-U3-O-* |
| **S-9.1** | Settings save triggers connectivity check | BR-U3-PROBE-* (called from Settings VM in U9) |
| **S-10.2** | Network failure ⇒ banner | BR-U3-CONN-4 (consumer reads `connectivity`) |
| **S-10.2** | Cached data may remain visible | BR-U3-CACHE-6 (caches untouched on transient errors; only URL change clears them) |
| **NFR-7.1** | No silent swallowing | BR-U3-O-1 .. O-8 |
| **NFR-7.2** | Pull-to-refresh equivalent | BR-U3-REFRESH-* |
| **NFR-1.2** | UI cannot bypass repository | enforced by U3 producing the only `SpoolmanApi`-coupled type and U7's "no Spoolman" rule (BR-U3 cross-unit notes in `domain-entities.md` §4) |

---

## 7. Risks + mitigations

| Risk | Mitigation |
|---|---|
| FR-7 chain leaves orphan vendor/filament on partial failure (Q11=A) | Documented behaviour; retry path through existing-spool branch reuses orphan via FR-7.5 lookup. Captured in BR-U3-CHAIN-7. |
| `lot_nr` PATCH races against concurrent edits | v2 is single-user (NFR-1 implicit); accept last-write-wins. BR-U3 does not introduce ETag handling — would be a v2.1+ concern. |
| Spoolman returns paginated body for `findSpoolsByLotNr` (no pagination support in the v2 query) | Spoolman's contract per FR-3.2 returns the full match set as a flat list; if a server returns paginated bodies later, U3's listing methods need pagination — out of scope. |
| Empty-UID query would match every `card_uid:`-prefixed spool | Defended by BR-U3-FIND-1 short-circuit (Q-U3-1=A). |
| Logging interceptor leaks data on a careless `BODY` upgrade | BR-U3-LOG-4 — `BODY` level is forbidden by default; any change requires explicit review. |
| Cache + server drift if a partial PATCH response arrives | BR-U3-CACHE-1 uses the response body as the new source-of-truth; Spoolman's API returns full rows. Test coverage (BR-U3-T-10) locks the invariant. |

---

## 8. Out-of-scope (deferred to later units)

- ViewModel integration of `connectivity` into the offline banner — U9 (`OfflineBanner` Compose component).
- `MoveOnBindUseCase` orchestration of `remove + append` for two different spools — U6b.
- `MaterialBrandRepository` precedence merge of `vendors` / `filaments` — U8.
- ETag / optimistic-concurrency on PATCH — out of scope (single-user assumption).
- Spoolman authentication — out of scope per NFR-7.4.
- Cleartext-traffic config audit — U10.
