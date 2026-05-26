# U6a — Code Generation Plan (Part 1)

**Stage**: CONSTRUCTION → Per-Unit Loop → Code Generation Part 1 (U6a)
**Unit**: U6a — Create-and-Pair Flow (with **folded** U2-Δ / U3-Δ / U5-Δ amendments)
**Approval gate**: this plan must be approved before Code Gen Part 2 executes the checkboxes below.
**Inputs**:
- `aidlc-docs/construction/u6a-create-and-pair-flow/functional-design/{domain-entities,business-rules,business-logic-model,frontend-components}.md` (FD Part 2, approved 2026-05-25)
- `aidlc-docs/construction/plans/u6a-create-and-pair-flow-functional-design-plan.md` Decision Records Q-U6a-1..15 = A
- `aidlc-docs/inception/requirements/requirements-delta-extra-fields.md` (approved 2026-05-25)
- `aidlc-docs/inception/application-design/components.md` §2.3 / §2.5 / §2.8, `component-methods.md` §6 / §7 / §8
- `aidlc-docs/inception/application-design/services.md` §3 / §4 / §6
- Spec: `paxx12-snapmaker-u1/spool-link/docs/SPOOLMAN.md` (extras wire format, bootstrap, sync)
- Spoolman source pins: `spool.py:432` (PATCH-replaces-extra), `extra_fields.py:60-66, 134-144` (validator), `field.py:45-72` (registration)

**Branch**: `v2`. Working tree before this plan: 5 commits ahead of `origin/v2`; modified `aidlc-docs/aidlc-state.md` + `audit.md`; new files `requirements-delta-extra-fields.md`, `u6a-...-functional-design-plan.md`, `u6a-create-and-pair-flow/functional-design/*.md`.

**Test count target**: U5 closed at **232 / 232**. After U6a:
- ‑3 files / ‑38 cases (legacy `CardUidEncoding{Decode,Encode,RoundTrip}Test`)
- +1 file / +12 cases (`ExtraCardUidsCodecTest`)
- +1 file / ~+8 cases (`ExtraFieldsRegistrationTest`)
- +25 cases across rewritten `SpoolmanRepository*Test` files (Append, Remove, FindByCardUid, CreateChain, Probe→ConnectionTest)
- +0 net U5-Δ (rename + 1, ‑1 retired)
- +~16 cases U6a use-case + +~8 VM cases + +~4 mapping cases ≈ **+28 U6a body**
- **Estimated total: ~263 / ~263**. Locked at code-gen time.

**Out-of-scope guards** (re-stated for clarity): no `MoveOnBindUseCase` impl beyond `NoOp` (U6b); no `TwoTagUseCase` / sheets (U6b); no `RawWriteUseCase` / `VendorUidOnlyPairUseCase` (U7); no catalogue-backed pickers (U8); no `BannerState` derivation beyond U5 (U9); no instrumented Compose UI tests for `FilamentForm` — manual verification at U6 milestone install gate (end of U6b).

---

## §1 — Build dependencies

- [ ] 1.1 No new third-party dependencies. `gson` (`com.google.code.gson:gson`) is already on the classpath via Retrofit's `converter-gson`; `ExtraCardUidsCodec` reuses that import. `kotlinx-datetime` (added in U4) already covers timing concerns. No `libs.versions.toml` change.
- [ ] 1.2 No `app/build.gradle.kts` change. `testOptions.unitTests.isReturnDefaultValues = true` (U4) remains in place so JVM tests can call `android.util.Log.w(...)` as a no-op when `ExtraCardUidsCodec.decode` logs a skipped invalid-hex entry.

## §2 — U2-Δ: domain primitives

### 2.1 Modify `app/src/main/java/com/spoolpainter/app/domain/primitives/CardUid.kt`

- [ ] 2.1.1 Replace `"%02x"` with `"%02X"` in `fromBytes(bytes)` (U2-Δ-3, FR-2-EXT.8 / U2-Δ-RULE-1).
- [ ] 2.1.2 Add companion `fun normaliseHex(raw: String): String` — `raw.uppercase()`, validate against `Regex("^[0-9A-F]+$")`; throw `IllegalArgumentException("Not valid hex: $raw")` on mismatch (U2-Δ-RULE-2).
- [ ] 2.1.3 Update `toString()` is unchanged (already returns `hex`). No changes to the inline-class shape.

### 2.2 Create `app/src/main/java/com/spoolpainter/app/domain/primitives/ExtraCardUidsCodec.kt`

- [ ] 2.2.1 Single-file `object ExtraCardUidsCodec` with two functions per FD §3.2:
  - `fun encode(uids: List<CardUid>): String` — `Gson().toJson(uids.joinToString(",") { it.hex })`. Empty list → `"\"\""` (Gson encodes empty string as that literal).
  - `fun decode(value: String): List<CardUid>` — defensive parse:
    1. `value.isEmpty()` → `emptyList()`.
    2. Strip surrounding double quotes if present (`removePrefix("\"").removeSuffix("\"")`); if result empty → `emptyList()`.
    3. `split(",")` → `map { it.trim() }` → `filter { it.isNotEmpty() }`.
    4. Each entry: `runCatching { CardUid(CardUid.normaliseHex(entry)) }.getOrNull()`; null entries are skipped (Q-U6a-1=A / U2-Δ-RULE-4). Skipped entries log `android.util.Log.w("ExtraCardUidsCodec", "skipped invalid hex: $entry")` for forensics.
    5. Output: `List<CardUid>` preserving valid input order.
- [ ] 2.2.2 Imports: `com.google.gson.Gson`, `com.spoolpainter.app.domain.primitives.CardUid`, `android.util.Log`.

### 2.3 Modify `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/CardUidEncoding.kt`

- [ ] 2.3.1 **Delete the entire file** (U2-Δ-1 / U2-Δ-RULE-5). The internal constant `PREFIX = "card_uid:"`, the `Decoded` data class, `decode(input)`, `encode(uids, opaque)`, and `isHexChar(c)` all go. After §3 (U3-Δ) and §6 (U5-Δ) finish below, no production caller remains.
- [ ] 2.3.2 Note: the `lot_nr` substring grep `"card_uid:"` MUST be zero across `app/src/main` after this delete (covered by §10 brownfield invariants).

### 2.4 Update `app/src/test/java/com/spoolpainter/app/domain/primitives/CardUidTest.kt`

- [ ] 2.4.1 Existing `fromBytes` cases asserting lowercase `"aabb..."` → flip expected to uppercase `"AABB..."`.
- [ ] 2.4.2 Add 3 new cases:
  - `normaliseHex_uppercases_valid_input` — `"aabbccdd"` → `"AABBCCDD"`.
  - `normaliseHex_passes_already_uppercase_input` — `"AABBCCDD"` → `"AABBCCDD"`.
  - `normaliseHex_throws_on_non_hex` — `"AABBCC??"` → expects `IllegalArgumentException`.

### 2.5 Create `app/src/test/java/com/spoolpainter/app/domain/primitives/ExtraCardUidsCodecTest.kt`

- [ ] 2.5.1 12 cases per T-1 in `business-rules.md`:
  - `encode_emptyList_returns_jsonEmptyString` — `encode(emptyList())` → `"\"\""`.
  - `encode_singleUid_returns_jsonWrappedSingle` — `encode(listOf(CardUid("AABBCCDD")))` → `"\"AABBCCDD\""`.
  - `encode_multipleUids_returns_jsonWrappedCommaJoined` — two UIDs → `"\"AABBCCDD,11223344\""`.
  - `decode_emptyString_returns_emptyList` — `decode("")` → `[]`.
  - `decode_jsonEmptyString_returns_emptyList` — `decode("\"\"")` → `[]`.
  - `decode_jsonSingleUid_returns_singleton` — `decode("\"AABBCCDD\"")` → `[CardUid("AABBCCDD")]`.
  - `decode_rawCommaJoined_returns_list` — `decode("AABBCCDD,11223344")` → 2 entries (defensive).
  - `decode_jsonCommaJoined_returns_list` — `decode("\"AABBCCDD,11223344\"")` → 2 entries.
  - `decode_normalisesLowercaseToUppercase` — `decode("\"aabbccdd\"")` → `[CardUid("AABBCCDD")]`.
  - `decode_skipsInvalidHexEntry_keepsValidNeighbours` — `decode("\"AABBCCDD,??ZZ,11223344\"")` → 2 entries (`AABBCCDD`, `11223344`).
  - `decode_handlesWhitespaceAndTrailingCommas` — `decode("\" AABBCCDD , ,11223344,\"")` → 2 entries.
  - `decode_roundTripsEncode` — `decode(encode(twoUids))` returns the original list.

### 2.6 Delete `app/src/test/java/com/spoolpainter/app/data/remote/spoolman/CardUidEncodingDecodeTest.kt`

- [ ] 2.6.1 Delete the file (U2-Δ-RULE-5; ~14 cases retired).

### 2.7 Delete `app/src/test/java/com/spoolpainter/app/data/remote/spoolman/CardUidEncodingEncodeTest.kt`

- [ ] 2.7.1 Delete the file (~12 cases retired).

### 2.8 Delete `app/src/test/java/com/spoolpainter/app/data/remote/spoolman/CardUidEncodingRoundTripTest.kt`

- [ ] 2.8.1 Delete the file (~12 cases retired).

## §3 — U3-Δ: Spoolman repository (DTO + API surface)

### 3.1 Modify `app/src/main/java/com/spoolpainter/app/domain/models/SpoolmanModels.kt`

- [ ] 3.1.1 Add `val extra: Map<String, String>? = null` to `SpoolmanSpool` (U3-Δ-1 / U3-Δ-RULE-1). Keep all other fields including `lot_nr` (DTO fidelity per FR-2-EXT.1 supersession + U3-Δ-RULE-9).
- [ ] 3.1.2 Add `val extra: Map<String, String>? = null` to `SpoolmanFilament`.
- [ ] 3.1.3 No change to `SpoolmanVendor`, `SpoolmanResponse`, `SpoolmanInfo`.

### 3.2 Modify `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanApi.kt`

- [ ] 3.2.1 **Delete** `findSpoolsByLotNr(@Query("lot_nr") lotNrSubstring: String)` — U3-Δ-2's bulk-fetch replaces it.
- [ ] 3.2.2 **Delete** `patchSpoolLotNr(spoolId, body: UpdateSpoolLotNrRequest)` — U3-Δ-3 / U3-Δ-4 use `patchSpool` with the new `SpoolPatchBody`.
- [ ] 3.2.3 Add new endpoint methods:
  ```kotlin
  @GET("api/v1/spool")
  suspend fun listSpools(
      @Query("limit") limit: Int? = null,
      @Query("offset") offset: Int? = null,
      @Query("allow_archived") allowArchived: Boolean? = null,
  ): Response<List<SpoolmanSpool>>
  ```
  (Augment the existing `listSpools(limit, offset)` with `allowArchived` — keep optional Int? for limit/offset; existing call sites passing two args remain valid.)
- [ ] 3.2.4 Add:
  ```kotlin
  @PATCH("api/v1/spool/{id}")
  suspend fun patchSpool(
      @Path("id") spoolId: Int,
      @Body body: SpoolPatchBody,
  ): Response<SpoolmanSpool>
  ```
- [ ] 3.2.5 Add field-registration endpoints (U3-Δ-RULE-6):
  ```kotlin
  @GET("api/v1/field/{entityType}")
  suspend fun listFields(@Path("entityType") entityType: String): Response<List<ExtraFieldDef>>

  @POST("api/v1/field/{entityType}/{key}")
  suspend fun postField(
      @Path("entityType") entityType: String,
      @Path("key") key: String,
      @Body body: ExtraFieldDef,
  ): Response<ExtraFieldDef>
  ```
  `entityType` values: `"spool"` | `"filament"`. Per spec §5, `POST` is upsert.

### 3.3 Modify `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanRequests.kt`

- [ ] 3.3.1 Add `extra: Map<String, String>? = null` to `CreateFilamentRequest` (CP-7).
- [ ] 3.3.2 Add `extra: Map<String, String>? = null` to `CreateSpoolRequest` and **remove** `lot_nr` (CP-8 / U3-Δ-RULE-9 — `createSpoolForNewFilament` SHALL NOT set `lot_nr`).
- [ ] 3.3.3 **Delete** `UpdateSpoolLotNrRequest` (legacy lot_nr PATCH; replaced by `SpoolPatchBody`).
- [ ] 3.3.4 Add new request bodies in the same file:
  ```kotlin
  data class SpoolPatchBody(
      val extra: Map<String, String>? = null,
  )

  data class ExtraFieldDef(
      val key: String? = null,           // populated on GET; not sent on POST (path provides it)
      val name: String,
      val field_type: String,            // "text"
      val order: Int,
      val default_value: String,         // JSON-encoded default; e.g. "\"\"" for text
  )
  ```

### 3.4 Modify `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanRepository.kt`

The file undergoes a substantial rewrite. Sub-steps below mirror the FD §§5–11 pseudocode and U3-Δ rules.

- [ ] 3.4.1 **Imports housekeeping**: drop `CardUidEncoding`, `UpdateSpoolLotNrRequest`. Add `ExtraCardUidsCodec`, `SpoolPatchBody`, `ExtraFieldDef`. Keep DTOs (`SpoolmanSpool/Filament/Vendor`).

- [ ] 3.4.2 **`findSpoolsByCardUid(uid)` rewrite** (U3-Δ-RULE-2 / FR-2-EXT.4):
  ```kotlin
  open suspend fun findSpoolsByCardUid(uid: CardUid): SpoolmanOutcome<List<SpoolmanSpool>> {
      if (uid.hex.isEmpty()) return SpoolmanOutcome.Success(emptyList())
      val api = cachedApi ?: return urlNotConfigured()
      return performHttp("listSpools") {
          api.listSpools(limit = 1000, offset = 0, allowArchived = true)
      }.map { all ->
          all.filter { spool ->
              ExtraCardUidsCodec.decode(spool.extra?.get("card_uids") ?: "").contains(uid)
          }
      }
  }
  ```
  - Bulk fetch with archived; client-side filter on decoded `extra.card_uids`.
  - Keep `open` (test fakes subclass).
  - Comparison is uppercase by virtue of `CardUid.fromBytes` (Δ-3) and `normaliseHex` (Δ-4); `decode` already uppercases.

- [ ] 3.4.3 **`appendCardUidToSpool(spoolId, uid)` rewrite** (U3-Δ-RULE-3 / CP-10):
  ```kotlin
  suspend fun appendCardUidToSpool(spoolId: Int, uid: CardUid): SpoolmanOutcome<SpoolmanSpool> {
      if (uid.hex.isEmpty()) return invalidArg("uid is empty")
      val api = cachedApi ?: return urlNotConfigured()
      return executeWithExtraFieldsBootstrap {
          performHttp("getSpool") { api.getSpool(spoolId) }.flatMap { spool ->
              val current = ExtraCardUidsCodec.decode(spool.extra?.get("card_uids") ?: "")
              if (uid in current) {
                  // CP-10 idempotent — no PATCH; cache stays in sync via the GET above.
                  replaceSpoolInCache(spool)
                  SpoolmanOutcome.Success(spool)
              } else {
                  val newUids = current + uid
                  val newExtra = (spool.extra ?: emptyMap()) +
                      ("card_uids" to ExtraCardUidsCodec.encode(newUids))
                  performHttp("patchSpool") {
                      api.patchSpool(spoolId, SpoolPatchBody(extra = newExtra))
                  }.also { o ->
                      if (o is SpoolmanOutcome.Success) replaceSpoolInCache(o.data)
                  }
              }
          }
      }
  }
  ```

- [ ] 3.4.4 **`removeCardUidFromSpool(spoolId, uid)` rewrite** (U3-Δ-RULE-4):
  - Symmetric to 3.4.3. If `uid !in current`, idempotent no-PATCH `Success(spool)`. If empty list result, encode → `"\"\""` (preserves the key so Spoolman UI keeps showing the field).
  - Wrapped in `executeWithExtraFieldsBootstrap`.

- [ ] 3.4.5 **`createSpoolForNewFilament(req)` rewrite** (U3-Δ-RULE-5 / CP-6 / CP-7 / CP-8):
  - Keep `resolveOrCreateVendor(api, name)` substep — unchanged shape. Vendor record has no `extra` involvement; no bootstrap needed for vendor calls.
  - Rewrite `resolveOrCreateFilament(api, vendor, materialName, req)`:
    - On the create path, build `CreateFilamentRequest(... existing fields ..., extra = ...)`.
    - `extra` resolution: `req.variant?.trim()?.takeUnless { it.isBlank() }?.let { mapOf("variant" to Gson().toJson(it)) }` (CP-7 — omit when null/blank).
    - Wrap **only** the `createFilament` call in `executeWithExtraFieldsBootstrap` (the `listFilaments` GET cannot 400 on extras).
    - Match-existing path unchanged (no extras on read).
  - Rewrite `createSpoolStep(api, filament, uid)`:
    - Body: `CreateSpoolRequest(filament_id = filament.id, extra = mapOf("card_uids" to ExtraCardUidsCodec.encode(listOf(uid))))`.
    - `lot_nr` is NOT set.
    - Wrap the `createSpool` call in `executeWithExtraFieldsBootstrap`.
  - Top-level chain returns `SpoolmanOutcome<SpoolmanSpool>` (unchanged contract).

- [ ] 3.4.6 **`probe()` rename → `testConnection()` rewrite** (U3-Δ-RULE-8 / FR-2-EXT.7):
  ```kotlin
  open suspend fun testConnection(): SpoolmanOutcome<String> {
      val api = cachedApi ?: return urlNotConfigured()
      return performHttp("getInfo") { api.getInfo() }.map { info ->
          info.version ?: "unknown"
      }
  }
  ```
  - Endpoint stays `GET /api/v1/info` (already wired via `SpoolmanApi.getInfo`).
  - Returns version string (not `Unit`), letting `SettingsViewModel` surface "Connected to Spoolman vX.Y.Z" (cosmetic — see §7).
  - **Replaces** the existing `probe(): SpoolmanOutcome<Unit>` API. All call sites (`SettingsViewModel.onTestConnectionTapped`) update accordingly (§7).

- [ ] 3.4.7 **New `ensureExtraFieldsRegistered()`** (U3-Δ-RULE-6):
  ```kotlin
  open suspend fun ensureExtraFieldsRegistered(): SpoolmanOutcome<Unit> {
      val api = cachedApi ?: return urlNotConfigured()
      val spoolFields = performHttp("listFields:spool") { api.listFields("spool") }
      if (spoolFields !is SpoolmanOutcome.Success) {
          @Suppress("UNCHECKED_CAST") return spoolFields as SpoolmanOutcome<Unit>
      }
      if (spoolFields.data.none { it.key == "card_uids" }) {
          val r = performHttp("postField:spool/card_uids") {
              api.postField("spool", "card_uids", ExtraFieldDef(
                  name = "Card UIDs", field_type = "text", order = 1, default_value = "\"\"",
              ))
          }
          if (r !is SpoolmanOutcome.Success) {
              @Suppress("UNCHECKED_CAST") return r as SpoolmanOutcome<Unit>
          }
      }
      val filamentFields = performHttp("listFields:filament") { api.listFields("filament") }
      if (filamentFields !is SpoolmanOutcome.Success) {
          @Suppress("UNCHECKED_CAST") return filamentFields as SpoolmanOutcome<Unit>
      }
      if (filamentFields.data.none { it.key == "variant" }) {
          val r = performHttp("postField:filament/variant") {
              api.postField("filament", "variant", ExtraFieldDef(
                  name = "Variant", field_type = "text", order = 1, default_value = "\"\"",
              ))
          }
          if (r !is SpoolmanOutcome.Success) {
              @Suppress("UNCHECKED_CAST") return r as SpoolmanOutcome<Unit>
          }
      }
      return SpoolmanOutcome.Success(Unit)
  }
  ```

- [ ] 3.4.8 **New `executeWithExtraFieldsBootstrap` helper** (U3-Δ-RULE-7 / FR-2-EXT.3 lazy):
  ```kotlin
  private suspend inline fun <T> executeWithExtraFieldsBootstrap(
      crossinline block: suspend () -> SpoolmanOutcome<T>,
  ): SpoolmanOutcome<T> {
      val first = block()
      if (first is SpoolmanOutcome.HttpError &&
          first.code == 400 &&
          first.message.contains("Unknown extra field", ignoreCase = true)) {
          val bootstrap = ensureExtraFieldsRegistered()
          if (bootstrap !is SpoolmanOutcome.Success) {
              @Suppress("UNCHECKED_CAST") return bootstrap as SpoolmanOutcome<T>
          }
          return block()  // retry once; second 400 propagates
      }
      return first
  }
  ```
  - Note: `flatMap` does not propagate intermediate 400s past the first; the helper wraps the **final** call that touches `extra`. Read the FD §8 pseudocode — `appendCardUidToSpool` wraps the whole GET-then-PATCH block so a 400 on the PATCH still triggers bootstrap-and-retry of the entire GET-then-PATCH (cheap; the GET is idempotent and reads fresh state for the second PATCH).

- [ ] 3.4.9 **Cache invalidation hook** preserved for `appendCardUidToSpool` / `removeCardUidFromSpool` / `createSpoolForNewFilament` (existing `replaceSpoolInCache` / `prependSpool` behaviour; no semantic change).

### 3.5 Rewrite tests for U3-Δ

- [ ] 3.5.1 Modify `app/src/test/java/com/spoolpainter/app/data/remote/spoolman/FakeSpoolmanApi.kt`:
  - Replace `findSpoolsByLotNr(...)` with `listSpools(limit, offset, allowArchived)` returning the in-memory spool list (filter on `archived` flag if `allowArchived == false`; pass-through if `null` or `true`).
  - Replace `patchSpoolLotNr(...)` with `patchSpool(spoolId, body: SpoolPatchBody)` — applies `body.extra` as the new `extra` map (full replace per spec) on the cached spool.
  - Add `listFields(entityType)` and `postField(entityType, key, body)` — backed by two sets the test holds: `spoolExtraFields: MutableSet<String>`, `filamentExtraFields: MutableSet<String>`. `postField` adds; `listFields` returns `ExtraFieldDef` entries.
  - Add `nextSpoolPatchHttpError` / `nextFilamentCreateHttpError` / `nextSpoolCreateHttpError` knobs so a test can stage a 400 "Unknown extra field" once, then expect a subsequent successful retry.

- [ ] 3.5.2 Rewrite `app/src/test/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanRepositoryFindByCardUidTest.kt`:
  - 4 cases per T-3.1..T-3.3:
    - `findSpoolsByCardUid_returnsMatch_byDecodedExtraCardUids` — single matching spool.
    - `findSpoolsByCardUid_includesArchivedSpools` — flag passes through; archived match returned.
    - `findSpoolsByCardUid_returnsEmpty_whenNoMatch`.
    - `findSpoolsByCardUid_normalisesUidCaseAtCompareTime` — `extra.card_uids = "\"aabbccdd\""` matches `CardUid("AABBCCDD")`.

- [ ] 3.5.3 Rewrite `SpoolmanRepositoryAppendCardUidTest.kt`:
  - 6 cases per T-3.4..T-3.6:
    - `appendCardUidToSpool_emitsFullExtraPatch` — PATCH body has `extra.card_uids = "\"AABBCCDD\""`.
    - `appendCardUidToSpool_idempotent_whenUidAlreadyPresent` — no PATCH sent; Success returned (CP-10).
    - `appendCardUidToSpool_preservesOtherExtraKeys` — pre-existing `extra.foo = "bar"` echoed back in PATCH body.
    - `appendCardUidToSpool_lazyBootstrap_on400UnknownField` — first PATCH → 400 → POST `/field/spool/card_uids` → retry → success.
    - `appendCardUidToSpool_propagates400AfterBootstrap` — second 400 returned as `HttpError`.
    - `appendCardUidToSpool_returnsErrorOnGetFailure` — initial GET fails → `HttpError` propagated.

- [ ] 3.5.4 Rewrite `SpoolmanRepositoryRemoveCardUidTest.kt`:
  - 4 cases per T-3.7:
    - Happy path: removes UID from list.
    - Empty result preserves `card_uids = "\"\""` (does not drop key).
    - Idempotent when UID absent.
    - Lazy-bootstrap retry on 400.

- [ ] 3.5.5 Rewrite `SpoolmanRepositoryCreateChainTest.kt`:
  - 6 cases per T-3.8..T-3.10:
    - `createSpoolForNewFilament_happyPath_emitsExtraVariantOnFilamentAndExtraCardUidsOnSpool`.
    - `createSpoolForNewFilament_omitsExtraVariant_whenVariantNullOrBlank` (CP-7).
    - `createSpoolForNewFilament_reusesExistingVendor`.
    - `createSpoolForNewFilament_lazyBootstrap_onFilament400`.
    - `createSpoolForNewFilament_lazyBootstrap_onSpool400`.
    - `createSpoolForNewFilament_doesNotSetLotNr` — `CreateSpoolRequest.lot_nr` no longer exists; assert request body shape via Gson serialisation.

- [ ] 3.5.6 Create `SpoolmanRepositoryEnsureExtraFieldsTest.kt`:
  - 4 cases per T-3.11:
    - Both fields already registered → zero POSTs.
    - `card_uids` missing → one POST.
    - `variant` missing → one POST.
    - Both missing → two POSTs in order (spool first, then filament).

- [ ] 3.5.7 Rename `SpoolmanRepositoryProbeTest.kt` → `SpoolmanRepositoryConnectionTestTest.kt`:
  - 4 cases per T-3.12 / T-3.13:
    - `testConnection_returnsVersion_onInfo200`.
    - `testConnection_returnsHttpError_onInfo5xx`.
    - `testConnection_returnsNetworkError_onIoException`.
    - `testConnection_returnsParseError_onJsonSyntaxException`.
  - Delete `SpoolmanRepositoryProbeTest.kt`.

- [ ] 3.5.8 Update `SpoolmanRepositoryCacheInvalidationTest.kt` / `SpoolmanRepositoryRefreshTest.kt` / `SpoolmanRepositoryUrlChangeTest.kt` / `ConnectivityStateTransitionTest.kt`:
  - Adjust DTO builders to use `extra = null` (default) — no behavioural test change.
  - `refresh()` uses `listSpools(limit=null, offset=null, allowArchived=null)` (existing call site keeps default args). No behavioural change.

- [ ] 3.5.9 Update `SpoolmanRepositoryTestSupport.kt`:
  - Builders for `SpoolmanSpool` and `SpoolmanFilament` accept optional `extra` map (default null). Tests can compose spools with `extra.card_uids` populated.

## §4 — U6a body — domain types

### 4.1 Modify `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/NewSpoolRequest.kt`

- [ ] 4.1.1 The file already has `NewSpoolRequest(vendorName, materialName, colorHex, variant, tempRanges, cardUid)` and `TempRanges`. Migrate naming to align with FD's `NewFilamentRequest`:
  - **Rename file** to `app/src/main/java/com/spoolpainter/app/domain/usecases/NewFilamentRequest.kt` (move into domain layer per FD §1.2).
  - **Rename type** `NewSpoolRequest` → `NewFilamentRequest`.
  - Add fields `name: String` (filament name, FD §1.2) and align temp pair handling. Keep optional `variant: String?`.
  - Drop the local `TempRanges` from this file (already exists in `domain/models/TempRanges.kt`); import from there.
  - Add `companion object { fun fromForm(form: FormState, name: String, vendorName: String, uid: CardUid): NewFilamentRequest }` per FD §1.2 — populates `material`/`colorHex`/`tempRanges`/`variant` from the form; `name`+`vendorName` are composable-local strings (per FE-1 follow-up).
- [ ] 4.1.2 Rewire all call sites in `SpoolmanRepository` and tests to import from the new package.

### 4.2 Create `app/src/main/java/com/spoolpainter/app/domain/usecases/CreateAndPairResult.kt`

- [ ] 4.2.1 Sealed interface per FD §1.1:
  ```kotlin
  sealed interface CreateAndPairResult {
      sealed interface Success : CreateAndPairResult {
          val spoolId: Int
          val uid: CardUid
          data class WrittenAndPaired(
              override val spoolId: Int,
              override val uid: CardUid,
              val isNewSpool: Boolean,
          ) : Success
      }
      data class VerifyFailed(
          val spoolId: Int,
          val uid: CardUid,
          val isNewSpool: Boolean,
          val cause: String,
      ) : CreateAndPairResult
      data class SpoolmanFailed(
          val uid: CardUid,
          val outcome: SpoolmanOutcome<*>,
      ) : CreateAndPairResult
      data class NfcFailed(
          val uid: CardUid?,
          val reason: String,
      ) : CreateAndPairResult
      data class Cancelled(val reason: String) : CreateAndPairResult
  }
  ```
- [ ] 4.2.2 No `Success.PairedNoWrite` (Q-U6a-3=A → U7).
- [ ] 4.2.3 No `MoveOnBindRequired` (Q-U6a-4=A → no-op proceed).

### 4.3 Create `app/src/main/java/com/spoolpainter/app/domain/usecases/MoveOnBindUseCase.kt`

- [ ] 4.3.1 Per FD §6:
  ```kotlin
  interface MoveOnBindUseCase {
      suspend operator fun invoke(uid: CardUid, targetSpoolId: Int): Outcome
      sealed interface Outcome {
          data object Proceed : Outcome
          // U6b adds: RequireConfirmation, ConfirmedAndMoved, Declined
      }
      class NoOp @Inject constructor() : MoveOnBindUseCase {
          override suspend fun invoke(uid: CardUid, targetSpoolId: Int): Outcome = Outcome.Proceed
      }
  }
  ```

### 4.4 Modify `app/src/main/java/com/spoolpainter/app/di/UseCaseModule.kt` (or create if missing)

- [ ] 4.4.1 Verify whether a `UseCaseModule` exists. If not, create `app/src/main/java/com/spoolpainter/app/di/UseCaseModule.kt` with:
  ```kotlin
  @Module
  @InstallIn(SingletonComponent::class)
  abstract class UseCaseModule {
      @Binds
      abstract fun bindMoveOnBindUseCase(impl: MoveOnBindUseCase.NoOp): MoveOnBindUseCase
  }
  ```
  If `RepositoryModule` (already added in U5 via `RepositoryBindingsModule`) is the single home for `@Binds`, add the binding there to avoid module sprawl. **Pick the module that already follows the project's pattern** at code-gen time.

### 4.5 Create `app/src/main/java/com/spoolpainter/app/domain/usecases/CreateAndPairUseCase.kt`

- [ ] 4.5.1 Constructor signature:
  ```kotlin
  class CreateAndPairUseCase @Inject constructor(
      private val nfc: NfcRepository,
      private val spoolman: SpoolmanRepository,
      private val moveOnBind: MoveOnBindUseCase,
      private val mainState: StateFlow<MainUiState>,  // ← ViewModel-scoped; see 4.5.2
  )
  ```
  **State plumbing**: U5's `ReadAndPairUseCase` does NOT receive `mainState` directly; it returns a result and `MainViewModel` reads VM state. Mirror that: `CreateAndPairUseCase.invoke(snapshot: MainUiState): CreateAndPairResult` — VM passes the snapshot at call time. This keeps the use-case pure and the VM responsible for state ownership. Lock this shape (no `StateFlow` in the constructor; matches the U5 pattern in `ReadAndPairUseCase.kt`).

- [ ] 4.5.2 Final signature:
  ```kotlin
  class CreateAndPairUseCase @Inject constructor(
      private val nfc: NfcRepository,
      private val spoolman: SpoolmanRepository,
      private val moveOnBind: MoveOnBindUseCase,
  ) {
      suspend operator fun invoke(snapshot: CreateAndPairInput): CreateAndPairResult
  }
  data class CreateAndPairInput(
      val form: FormState,
      val newFilamentName: String,
      val newFilamentVendor: String,
  )
  ```
  - VM constructs `CreateAndPairInput` from `state.value` + composable-local name/vendor strings before calling the use-case.

- [ ] 4.5.3 Branching logic — implement FD §§1, 2, 3:
  - Validate `form.cardUid != null` (else `Cancelled("uid missing")`).
  - `moveOnBind.invoke(uid, targetSpoolId ?: -1)` — discard outcome (no-op).
  - If `form.selectedSpoolId != null` → `existingSpoolPath`.
  - Else → `newSpoolPath`.

- [ ] 4.5.4 `existingSpoolPath` — per FD §2:
  - Compute payload via `OpenSpoolPayloadCodec.toJson(makePayload(snapshot, spoolId = targetSpoolId))`. **`makePayload` does not yet exist** — see 4.5.6.
  - `appendCardUidToSpool` → on failure, `SpoolmanFailed(uid, outcome)`.
  - `nfc.arm(NfcIntent.Write(payload.toByteArray()))` → wait for terminal via `awaitTerminalNfc()`.
  - On `NfcResult.Error.Write` (or any error reason matching `"verify mismatch"` if surfaced from Verify) → `NfcFailed(uid, reason)` for write-stage errors.
  - `nfc.arm(NfcIntent.Verify(payload.toByteArray()))` → terminal; map verify mismatch reason to `VerifyFailed`, other errors to `NfcFailed`.
  - Success → `Success.WrittenAndPaired(targetSpoolId, uid, isNewSpool=false)`.

- [ ] 4.5.5 `newSpoolPath` — per FD §3:
  - `req = NewFilamentRequest.fromForm(form, name, vendor, uid)` (4.1.1 helper).
  - `createSpoolForNewFilament(req)` → on failure, `SpoolmanFailed(uid, outcome)`. Capture `newSpoolId = outcome.data.id ?: return SpoolmanFailed(uid, ParseError("no spool id"))`.
  - Compute payload with `spoolId = newSpoolId`.
  - Same NDEF write+verify sequence as `existingSpoolPath`.
  - Success → `Success.WrittenAndPaired(newSpoolId, uid, isNewSpool=true)`.

- [ ] 4.5.6 **Add `makePayload(snapshot, spoolId)`** — internal helper in this file:
  ```kotlin
  private fun makePayload(snapshot: CreateAndPairInput, spoolId: Int): OpenSpoolPayload {
      val form = snapshot.form
      val material = form.material?.name ?: "PLA"
      return OpenSpoolPayload(
          type = material,
          colorHex = form.colorHex,                     // hex without '#'
          brand = snapshot.newFilamentVendor.ifBlank { form.brand?.name ?: "Unknown" },
          minTemp = form.tempRanges.extruderMin?.toString() ?: "190",
          maxTemp = form.tempRanges.extruderMax?.toString() ?: "220",
          bedMinTemp = form.tempRanges.bedMin?.toString(),
          bedMaxTemp = form.tempRanges.bedMax?.toString(),
          subtype = form.variant?.takeUnless { it.isBlank() } ?: "Basic",
          spoolId = spoolId.toString(),
      )
  }
  ```
  - `lot_nr` is intentionally never set (codec also never emits it; see `OpenSpoolPayloadCodec.toJson`).
  - Defaults are conservative; the form-validation gate in VM-2 ensures all required temps are populated before `canWrite` is true, so the `?: "190"` fall-throughs are dead code in practice.

- [ ] 4.5.7 Helper `awaitTerminalNfc(): NfcResult` — collect `nfc.state.first { it is NfcResult.Success || it is NfcResult.Error }` (FD §4 / U5's pattern).

### 4.6 Modify `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainUiState.kt`

- [ ] 4.6.1 Add `WritingForPair` to `ActiveFlow`:
  ```kotlin
  sealed interface ActiveFlow {
      data object Idle : ActiveFlow
      data object ReadingForPair : ActiveFlow
      data object WritingForPair : ActiveFlow   // NEW (U6a)
  }
  ```
- [ ] 4.6.2 No `FormState` shape change (`variant: String?` already present per Q-U6a-2 verification).
- [ ] 4.6.3 Add `val canSubmit: Boolean` computed property on `FormState`:
  ```kotlin
  val FormState.canSubmit: Boolean
      get() = cardUid != null &&
          material != null &&
          !colorHex.isNullOrBlank() && colorHex.matches(Regex("^[0-9A-Fa-f]{6}$")) &&
          tempRanges.extruderMin != null && tempRanges.extruderMax != null &&
          tempRanges.bedMin != null && tempRanges.bedMax != null &&
          tempRanges.extruderMin!! <= tempRanges.extruderMax!! &&
          tempRanges.bedMin!! <= tempRanges.bedMax!!
  ```
  - Place as a top-level extension property in `MainUiState.kt` (file-local; not in `FormState` data class body to keep `data class` minimal). Per VM-2: `name`/`vendorName` validation lives in the screen-local fields (FE-1 follow-up); `canWrite` will combine `canSubmit` with non-blank name + vendor at the VM layer.

## §5 — U6a body — `MainViewModel.onWriteTapped`

### 5.1 Modify `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainViewModel.kt`

- [ ] 5.1.1 Add constructor parameter `private val createAndPair: CreateAndPairUseCase`. The Hilt entry-point already supports use-case injection (see `ReadAndPairUseCase` in U5).

- [ ] 5.1.2 Add screen-local form fields managed by VM (FE-1 follow-up): expose `nameField: StateFlow<String>` and `vendorField: StateFlow<String>` plus `onNameChanged(s)` / `onVendorChanged(s)` setters. **Decision**: keep these as VM-owned `MutableStateFlow<String>` (not `FormState`) so reset on `WrittenAndPaired` is one-line; VM tests can assert via flow value.

- [ ] 5.1.3 Add `canWrite: StateFlow<Boolean>` derived flow:
  ```kotlin
  val canWrite: StateFlow<Boolean> = combine(
      _state.map { it.form.canSubmit && it.activeFlow == ActiveFlow.Idle }.distinctUntilChanged(),
      _nameField.map { it.isNotBlank() }.distinctUntilChanged(),
      _vendorField.map { it.isNotBlank() }.distinctUntilChanged(),
  ) { formOk, nameOk, vendorOk -> formOk && nameOk && vendorOk }
   .stateIn(viewModelScope, SharingStarted.Eagerly, false)
  ```

- [ ] 5.1.4 Implement `fun onWriteTapped()` per FD §12 / VM-3..VM-9:
  ```kotlin
  fun onWriteTapped() {
      if (!canWrite.value) return
      _state.update { it.copy(activeFlow = ActiveFlow.WritingForPair) }
      viewModelScope.launch {
          val input = CreateAndPairInput(
              form = _state.value.form,
              newFilamentName = _nameField.value.trim(),
              newFilamentVendor = _vendorField.value.trim(),
          )
          val result = withTimeoutOrNull(WRITE_TIMEOUT_MS_DEFAULT) {
              createAndPair.invoke(input)
          } ?: CreateAndPairResult.Cancelled("timeout")
          applyWriteResult(result)
      }
  }
  ```
  - Add constant `private const val WRITE_TIMEOUT_MS_DEFAULT = 15_000L` (Q-U6a-8=A) in companion (next to `READ_TIMEOUT_MS_DEFAULT`).

- [ ] 5.1.5 Implement `private fun applyWriteResult(result: CreateAndPairResult)` per VM-4..VM-8:
  - `Success.WrittenAndPaired` → snackbar `"Paired and written"`; reset form to `FormState()`; reset `_nameField`/`_vendorField` to ""; `activeFlow = Idle` (Q-U6a-9=A).
  - `VerifyFailed` → snackbar `"Verify failed. Tap Save to retry."`; keep form; `activeFlow = Idle`.
  - `SpoolmanFailed` → snackbar via `humanReadable(result.outcome)` (existing helper); keep form; `activeFlow = Idle`.
  - `NfcFailed` → snackbar `"NFC error: ${result.reason}"`; keep form; `activeFlow = Idle`.
  - `Cancelled` → snackbar `"No tag tapped — try again"`; `nfc.disarm()` defensively; `activeFlow = Idle`.

- [ ] 5.1.6 **Concurrency gate (VM-9)**: `onReadTapped` and `onWriteTapped` already gate on `activeFlow` indirectly (they set it on entry); add an explicit guard at the top of `onReadTapped` that returns early if `_state.value.activeFlow != ActiveFlow.Idle`. Same for `onWriteTapped` (already covered via `canWrite.value` check).

### 5.2 `MainViewModelTest` additions

- [ ] 5.2.1 Modify `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelTest.kt` — add a `FakeCreateAndPairUseCase` (or extend `FakeNfcRepository`/`FakeSpoolmanRepository` so the real use-case can run with stubs). Recommend a fake use-case to keep VM tests isolated from use-case branching:
  ```kotlin
  class FakeCreateAndPairUseCase(
      override val nfc: NfcRepository,
      override val spoolman: SpoolmanRepository,
      override val moveOnBind: MoveOnBindUseCase,
  ) : CreateAndPairUseCase(nfc, spoolman, moveOnBind) {
      var nextResult: CreateAndPairResult = ...
      override suspend fun invoke(snapshot: CreateAndPairInput) = nextResult
  }
  ```
  - To keep the `CreateAndPairUseCase` constructor open enough for fakes, mirror U5's pattern: declare the class `open` and the `invoke` `open`.

- [ ] 5.2.2 New test cases per T-4.5..T-4.13 (8 cases):
  - `onWriteTapped_whenCanWriteFalse_isNoOp_andDoesNotChangeActiveFlow`.
  - `onWriteTapped_existingSpool_emitsSnackbarAndResetsFormOnSuccess`.
  - `onWriteTapped_newSpool_emitsSnackbarAndResetsFormOnSuccess`.
  - `onWriteTapped_verifyFailed_keepsFormAndEmitsSnackbar`.
  - `onWriteTapped_spoolmanFailed_keepsFormAndEmitsHumanReadable`.
  - `onWriteTapped_nfcFailed_keepsFormAndEmitsSnackbar`.
  - `onWriteTapped_timeout15s_disarmsAndEmitsSnackbar`.
  - `onWriteTapped_concurrentReadTapped_isDropped` — VM-9.

- [ ] 5.2.3 Update existing `onSpoolSelected_*` cases for U5-Δ-RULE-1 (see §6).

### 5.3 `CreateAndPairUseCaseTest` (new)

- [ ] 5.3.1 Create `app/src/test/java/com/spoolpainter/app/domain/usecases/CreateAndPairUseCaseTest.kt` per T-5 (8 cases):
  - `existingSpool_happyPath_appendsThenWritesThenVerifies`.
  - `newSpool_happyPath_runsCreateChainThenWritesThenVerifies`.
  - `newSpool_verifyFailed_recordPersistsForRetry` — assert `findSpoolsByCardUid(uid)` after this returns the new spool.
  - `existingSpool_idempotentAppend_secondInvokeNoPatchSent`.
  - `moveOnBind_NoOp_proceedsWithoutBranch`.
  - `spoolmanAppendError_returnsSpoolmanFailedBeforeWrite`.
  - `nfcWriteError_returnsNfcFailed`.
  - `verifyMismatch_returnsVerifyFailedWithCause`.

- [ ] 5.3.2 Use the existing `FakeNfcRepository` and `FakeSpoolmanRepository` (U5 support files); extend with `appendCardUidToSpool` / `createSpoolForNewFilament` / `removeCardUidFromSpool` stubs returning configurable `SpoolmanOutcome<*>` values. Stage NFC state transitions for write→verify sequence.

## §6 — U5-Δ: read-flow tweaks

### 6.1 Modify `app/src/main/java/com/spoolpainter/app/ui/screens/main/FormMapping.kt`

- [ ] 6.1.1 Rename enum value `SpoolmanUidSource.FromLotNrOrClear` → `FromCardUidsOrClear` (U5-Δ-RULE-2).
- [ ] 6.1.2 Update the branch in `fromSpoolman(...)`:
  ```kotlin
  SpoolmanUidSource.FromCardUidsOrClear ->
      ExtraCardUidsCodec.decode(spool.extra?.get("card_uids") ?: "").firstOrNull()
  ```
- [ ] 6.1.3 Drop the import of `CardUidEncoding` (file deleted in §2.3).
- [ ] 6.1.4 `PreserveCurrent` semantics unchanged.

### 6.2 Modify `MainViewModel.onSpoolSelected(spool)` (U5-Δ-RULE-1)

- [ ] 6.2.1 Already calls `FormMapping.fromSpoolman(..., uidSource = SpoolmanUidSource.FromLotNrOrClear)` — update the enum reference to `FromCardUidsOrClear`. Behaviour change is internal to `FormMapping` (decode source switches to `extra.card_uids`).

### 6.3 Update `FormMappingTest.kt`

- [ ] 6.3.1 Rename existing case `onSpoolSelected_non_null_with_lot_nr_decodes_UID_into_form` → `..._with_card_uids_decodes_UID_into_form`. Test data: `SpoolmanSpool(... extra = mapOf("card_uids" to ExtraCardUidsCodec.encode(listOf(CardUid("AABBCCDD")))) )`. Assertion: form's `cardUid == CardUid("AABBCCDD")`.
- [ ] 6.3.2 Existing case `onSpoolSelected_non_null_without_lot_nr_clears_UID` → `..._without_card_uids_clears_UID`. Spool has `extra = null` or `extra = mapOf("card_uids" to "\"\"")`; form's `cardUid == null`.
- [ ] 6.3.3 Add new case `onSpoolSelected_non_null_multiUid_in_card_uids_picks_first_UID` (T-4.2 / U5-Δ-RULE-3 closure of parked bug). 3-UID encoded value → form's `cardUid` is the first.

## §7 — `SettingsViewModel` integration

### 7.1 Modify `app/src/main/java/com/spoolpainter/app/ui/screens/settings/SettingsViewModel.kt`

- [ ] 7.1.1 `onTestConnectionTapped()` — replace `spoolman.probe()` with `spoolman.testConnection()` (U6a-Δ-4 / FR-2-EXT.7):
  - On `Success(version)` → `_effects.trySend(UiEffect.ShowSnackbar("Connected to Spoolman v$version"))`.
  - Then call `spoolman.ensureExtraFieldsRegistered()`:
    - On `Success` → optionally append `" • fields ready"` to the snackbar (or fire a second snackbar `"Spoolman fields ready"`). **Lock the simpler shape**: append `" • fields ready"` to the existing message **only when both calls succeed**; otherwise show the connection-test result alone (avoid two-snackbar UX cascade).
  - Other outcomes unchanged.

### 7.2 Settings tests

- [ ] 7.2.1 No new `SettingsViewModelTest` exists today (U5 didn't ship one). Defer adding one to U9. Just keep the existing snackbar contract intact via `onUrlSaved`/`onRefreshTapped` — those don't change.

## §8 — Compose UI

### 8.1 Create `app/src/main/java/com/spoolpainter/app/ui/components/FilamentForm.kt`

- [ ] 8.1.1 Top-level composable per FE-1 / FE-7:
  ```kotlin
  @Composable
  fun FilamentForm(
      state: FormState,
      nameField: String,
      vendorField: String,
      enabled: Boolean,
      canSave: Boolean,
      onChange: (FormChange) -> Unit,
      onSave: () -> Unit,
      modifier: Modifier = Modifier,
  )
  sealed interface FormChange {
      data class Name(val value: String) : FormChange
      data class Vendor(val value: String) : FormChange
      data class MaterialPicked(val value: Material?) : FormChange
      data class BrandPicked(val value: Brand?) : FormChange
      data class ColorHex(val value: String?) : FormChange
      data class Variant(val value: String?) : FormChange
      data class TempRangesChanged(val value: TempRanges) : FormChange
  }
  ```
- [ ] 8.1.2 Layout per FE-1 / FE-3 — `Column(verticalScroll, padding 16.dp, spacing 12.dp)` with children: Name/Vendor `OutlinedTextField`s, `MaterialPicker`, `BrandPicker`, `ColorPicker`, `TempPanel`, inline `VariantField`, full-width `Button("Save & Write")`.
- [ ] 8.1.3 Read-only mode: when `enabled = false`, all `OutlinedTextField`s pass `enabled = false`; the Save button is **hidden** (FE-7 §2.5: hidden, not greyed).
- [ ] 8.1.4 Inline `private @Composable VariantField(value, enabled, onChange)` per FE-6 (max 64 chars, blank → null upstream).

### 8.2 Create `app/src/main/java/com/spoolpainter/app/ui/components/MaterialPicker.kt`

- [ ] 8.2.1 `ExposedDropdownMenuBox` over `MaterialDatabase.materials` (`materials` already exposes the list; reuse). Items: `material.name`. "Custom…" item opens an inline `OutlinedTextField`; on confirm, `onSelect(Material(name = typed, defaultMinTemp = 0, defaultMaxTemp = 0, defaultBedMinTemp = 0, defaultBedMaxTemp = 0))` (placeholder; U8 will replace with catalogue entry that carries proper defaults).
- [ ] 8.2.2 Clear button (X icon) → `onSelect(null)`.

### 8.3 Create `app/src/main/java/com/spoolpainter/app/ui/components/BrandPicker.kt`

- [ ] 8.3.1 Same shape as `MaterialPicker` over `BrandDatabase` (assume `BrandDatabase.brands: List<Brand>`; verify at code-gen — if API differs, mirror `MaterialDatabase.materials` pattern).

### 8.4 Create `app/src/main/java/com/spoolpainter/app/ui/components/ColorPicker.kt`

- [ ] 8.4.1 `Row { OutlinedTextField (label "Color (hex)", maxLength=6, ASCII keyboard); Spacer 8.dp; Box(48.dp, background = parsedColor ?: checkerPattern) }`.
- [ ] 8.4.2 Filter input to `[0-9A-Fa-f]`. On every change, emit `onChange(text.takeIf { it.length == 6 } ?: null)`.
- [ ] 8.4.3 Swatch parses hex via `Color(0xFF000000.toLong() or hex.toLong(16))` when `hex.length == 6`; else show a checker pattern (Material 3 surface variant for simplicity — the spec allows either).

### 8.5 Create `app/src/main/java/com/spoolpainter/app/ui/components/TempPanel.kt`

- [ ] 8.5.1 Two `IntField` rows (extruder min/max, bed min/max). Numeric-only input filter; empty → null in `TempRanges`.
- [ ] 8.5.2 "Use material defaults" `TextButton` — disabled when `materialDefaults == null`. On click → `onChange(materialDefaults)`.
- [ ] 8.5.3 Per-row red border when `min > max`.

### 8.6 Delete v1 component carcasses

- [ ] 8.6.1 Delete `app/src/main/java/com/spoolpainter/app/ui/components/MaterialSelector.kt` (FE-9).
- [ ] 8.6.2 Delete `app/src/main/java/com/spoolpainter/app/ui/components/BrandSelector.kt`.
- [ ] 8.6.3 Delete `app/src/main/java/com/spoolpainter/app/ui/components/ColorSelector.kt`.
- [ ] 8.6.4 Delete `app/src/main/java/com/spoolpainter/app/ui/components/TemperatureCard.kt`.
- [ ] 8.6.5 Verify no remaining call sites: `grep -rn "MaterialSelector\|BrandSelector\|ColorSelector\|TemperatureCard" app/src/main` should return only the deletions; the `sheets/AddCustom*ViewModel` files are unrelated and stay.

### 8.7 Modify `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainScreen.kt`

- [ ] 8.7.1 Replace `FormPreview(state.form)` block with `FilamentForm(...)` (FE-8). Read-flow renders form with `enabled = (state.activeFlow == ActiveFlow.Idle)`.
- [ ] 8.7.2 Insert `WritingHint(visible = state.activeFlow == ActiveFlow.WritingForPair && state.nfc is NfcResult.Writing /* or Verifying — see NFC state name pin */)`. Implement `WritingHint` as a private composable per FD §8 frontend-components.md.
  - **Pin the NFC state names**: U4 ships `NfcResult.Reading`, `NfcResult.Writing`, `NfcResult.Verifying`, `NfcResult.Success`, `NfcResult.Error`, `NfcResult.Idle`. Confirm at code-gen by reading `app/src/main/java/com/spoolpainter/app/domain/primitives/NfcResult.kt`. Adjust `WritingHint` predicate to match.
- [ ] 8.7.3 Delete the existing `FormPreview`, `PreviewRow`, `ColorPreviewRow`, `parseHex`, `tempRangeText` helpers — fully superseded by `FilamentForm`.
- [ ] 8.7.4 Wire `onChange` from `FilamentForm` to new `MainViewModel` setters: `onNameChanged`, `onVendorChanged`, `onMaterialPicked`, `onBrandPicked`, `onColorHexChanged`, `onVariantChanged`, `onTempRangesChanged`. Add these setters in `MainViewModel.kt`.
- [ ] 8.7.5 Wire `onSave` to `viewModel::onWriteTapped`. Wire `canSave` to `canWrite.value` collected via `collectAsStateWithLifecycle`.

### 8.8 Compose tests

- [ ] 8.8.1 No new instrumented Compose tests (out-of-scope guard 2.11.6 — U6 install gate at end of U6b covers this manually).

## §9 — Brownfield migration / reference updates

### 9.1 Audit + delete legacy `lot_nr` references

- [ ] 9.1.1 `grep -rn "lot_nr" app/src/main` — expected matches after this plan: only `SpoolmanModels.kt` (DTO field for fidelity), `OpenSpoolPayload.kt` (read-side carrier), `OpenSpoolPayloadCodec.kt` (`fromJson` keeps reading `lot_nr` for v1 tag fidelity but `toJson` already does NOT emit; comment unchanged). No app-logic reads.
- [ ] 9.1.2 `grep -rn "card_uid:" app/src/main` — expected zero matches after `CardUidEncoding.kt` deletion.
- [ ] 9.1.3 `grep -rn "%02x" app/src/main` — expected zero in `domain/primitives/CardUid.kt` (other files like NFC byte hex dumps may legitimately use `%02x` for log formatting; if any exist, leave them — only the UID hex emitter is required to use `%02X`).
- [ ] 9.1.4 `grep -rn "CardUidEncoding\." app/src` — expected zero. If any test still imports it, that test was missed in §2.6/2.7/2.8 and must be removed.
- [ ] 9.1.5 `grep -rn "findSpoolsByLotNr\|patchSpoolLotNr\|UpdateSpoolLotNrRequest" app/src` — expected zero (replaced in §3.2 / §3.3).

### 9.2 Documentation drift carry

- [ ] 9.2.1 `aidlc-docs/inception/application-design/component-methods.md` and `unit-of-work.md` carry pre-delta language (referencing `lot_nr` decoding, `Material`/`Brand` types, full Settings UI as U9 scope). **Do not edit** in U6a — drift sync deferred to U10 per existing state-file carry-over note. The U6a summary (§13) explicitly notes the unresolved drift.

## §10 — Verification commands (post-Code-Gen)

- [ ] 10.1 `./gradlew :app:compileDebugKotlin` — clean build, zero new warnings beyond v1 deprecations already present.
- [ ] 10.2 `./gradlew :app:testDebugUnitTest` — full suite passes; expected count ≈ **263 / 263** (locked at code-gen). Track delta against U5's 232.
- [ ] 10.3 `./gradlew :app:assembleDebug` — APK builds. Size growth from `FilamentForm`/pickers expected; should stay under +0.5 MB from U5's ≈33.6 MB.
- [ ] 10.4 Brownfield invariant greps per §9.1 — record results in the U6a summary.
- [ ] 10.5 **No milestone install gate at U6a end** (per Q-T2=B / `unit-of-work.md` §2). Install gate is end-of-U6b; the `installDebug` step is **not run** at U6a close-out, but the APK must build cleanly.

## §11 — Story / requirement coverage map

| Story / FR | Code/test owner |
|---|---|
| S-4.1 (Save button gated on UID + form) | VM-1 / `canWrite` (5.1.3) + FE-7 (8.1.3) |
| S-4.2 (Existing-spool path) | `existingSpoolPath` (4.5.4) + T-5.1 (5.3.1) |
| S-4.3 (New-spool path, FR-7 chain) | `newSpoolPath` (4.5.5) + `createSpoolForNewFilament` (3.4.5) + T-5.2 |
| S-4.4 (Verify after write) | 4.5.4 / 4.5.5 verify steps + T-5.8 |
| S-4.5 (Verify-fail recovery) | CP-9 emergent property (FD §3 commentary) + T-5.3 |
| S-7.1 / S-7.2 / S-7.3 (filament fields persisted) | `NewFilamentRequest` (4.1) + `CreateFilamentRequest.extra` (3.3.1) + T-3.8 / T-3.9 |
| FR-2-EXT.1 | `ExtraCardUidsCodec` (2.2) + 3.4.2 / 3.4.3 / 3.4.5 |
| FR-2-EXT.2 | `extra.variant` in 3.4.5 + `VariantField` (8.1.4) |
| FR-2-EXT.3 | `ensureExtraFieldsRegistered` (3.4.7) + `executeWithExtraFieldsBootstrap` (3.4.8) + Settings wiring (7.1) |
| FR-2-EXT.4 | `findSpoolsByCardUid` rewrite (3.4.2) |
| FR-2-EXT.5 | full-`extra` PATCH bodies in 3.4.3 / 3.4.4 |
| FR-2-EXT.7 | `testConnection()` (3.4.6) |
| FR-2-EXT.8 | `CardUid.fromBytes` casing (2.1.1) + `normaliseHex` (2.1.2) |

## §12 — Out-of-scope guards (re-stated)

- [ ] 12.1 No `MoveOnBindUseCase` impl beyond `NoOp`. The interface and `NoOp` ship in 4.3; U6b will provide a real binding via Hilt.
- [ ] 12.2 No `TwoTagUseCase`, no sheets (`PairAnotherTagSheet`, `RepairConfirmSheet`).
- [ ] 12.3 No `RawWriteUseCase`, no `VendorUidOnlyPairUseCase`, no `VendorOptInViewModel` changes (sheets directory left untouched). U7 owns these.
- [ ] 12.4 No catalogue-driven `MaterialPicker` / `BrandPicker` (U8). Pickers are string-list based.
- [ ] 12.5 No full `BannerState` derivation (U9). U6a leaves `BannerState.Hidden` as-is.
- [ ] 12.6 No instrumented Compose UI tests (U6b install gate covers manual UX verification of `FilamentForm`).
- [ ] 12.7 No application-design doc-drift sync (deferred to U10).

## §13 — Summary artefact

- [ ] 13.1 At Code-Gen Part 2 close, write `aidlc-docs/construction/u6a-create-and-pair-flow/code/u6a-summary.md` capturing:
  - Files created / modified / deleted, grouped by area (U2-Δ / U3-Δ / U5-Δ / U6a body / Compose / DI).
  - Final test count delta vs U5's 232.
  - Build + test verification log.
  - Brownfield invariant grep results.
  - Mid-gate scope changes (if any).
  - Doc-drift carry items unchanged from U5 (handed to U10).

## §14 — Approval gate

This Code Generation Part 1 plan is pending user approval. On approval, Code Generation Part 2 executes every checkbox above in order, then:

1. Generates `aidlc-docs/construction/u6a-create-and-pair-flow/code/u6a-summary.md`.
2. Runs `compileDebugKotlin` + `testDebugUnitTest` + `assembleDebug` and records results.
3. Presents the standard 2-option close-out message ("Request Changes" / "Continue to Next Stage").
4. On user approval → marks U6a `[x]` in `aidlc-state.md` → close-out commit per `unit-of-work.md` §2.1 (bundles U6a code + tests + AIDLC artefacts + the U2/U3/U5 amendment code + tests + the requirements delta).

The user may, at the approval prompt for this plan:
- **Request Changes** — describe edits to the plan (re-scope / re-order / question a step). Plan revision happens before any code is written.
- **Approve as-is** — proceeds straight to Code Gen Part 2 execution.
