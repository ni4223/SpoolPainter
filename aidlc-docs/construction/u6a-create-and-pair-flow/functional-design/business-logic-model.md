# U6a — Business Logic Model

**Unit**: U6a — Create-and-Pair Flow
**Approved**: Q-U6a-1..15 = A (FD Part 1, 2026-05-25)

This document specifies the algorithmic flow of `CreateAndPairUseCase`, the supporting `SpoolmanRepository` operations (rewritten per U3-Δ), and the `MainViewModel.onWriteTapped` orchestration. Pseudocode is Kotlin-flavoured but elides null checks, error wrapping, and cancellation propagation for readability — those are specified by the rules in `business-rules.md`.

---

## 1. `CreateAndPairUseCase.invoke()` — top-level

```text
suspend fun invoke(): CreateAndPairResult {
    val state = mainState.value                   // VM passes state read-only
    val uid = state.form.cardUid ?: return Cancelled("uid missing")
    val targetSpoolId = state.form.selectedSpoolId

    // 1. Move-on-bind precheck (no-op in U6a per Q-U6a-4=A)
    moveOnBind.invoke(uid, targetSpoolId ?: -1)

    return if (targetSpoolId != null) existingSpoolPath(state, uid, targetSpoolId)
           else newSpoolPath(state, uid)
}
```

**Note**: `moveOnBind.invoke(...)` returns `Proceed` for U6a's no-op default. U6b's impl may return `RequireConfirmation` and trigger a sheet — but U6a never observes that.

## 2. `existingSpoolPath(state, uid, targetSpoolId)`

```text
private suspend fun existingSpoolPath(
    state: MainUiState, uid: CardUid, targetSpoolId: Int,
): CreateAndPairResult {
    // 2.1 Compute payload bytes
    val payload = OpenSpoolPayloadCodec.toJson(makePayload(state.form, spoolId = targetSpoolId))

    // 2.2 Spoolman first — append UID (idempotent per CP-10)
    val appendOutcome = spoolman.appendCardUidToSpool(targetSpoolId, uid)
    when (appendOutcome) {
        is SpoolmanOutcome.Success -> Unit
        is SpoolmanOutcome.HttpError, is SpoolmanOutcome.NetworkError, is SpoolmanOutcome.ParseError ->
            return SpoolmanFailed(uid, appendOutcome)
    }

    // 2.3 NDEF write
    nfc.arm(NfcIntent.Write(payload))
    val writeResult = awaitTerminalNfc()
    if (writeResult is NfcResult.Error) return NfcFailed(uid, writeResult.reason)

    // 2.4 Verify
    nfc.arm(NfcIntent.Verify(payload))
    val verifyResult = awaitTerminalNfc()
    return when (verifyResult) {
        is NfcResult.Success -> Success.WrittenAndPaired(targetSpoolId, uid, isNewSpool = false)
        is NfcResult.Error -> {
            // Distinguish verify mismatch vs NFC error
            if (verifyResult.reason.contains("verify mismatch", ignoreCase = true))
                VerifyFailed(targetSpoolId, uid, isNewSpool = false, cause = verifyResult.reason)
            else
                NfcFailed(uid, verifyResult.reason)
        }
        else -> NfcFailed(uid, "unexpected non-terminal verify state")
    }
}
```

## 3. `newSpoolPath(state, uid)`

```text
private suspend fun newSpoolPath(state: MainUiState, uid: CardUid): CreateAndPairResult {
    // 3.1 Build request
    val req = NewFilamentRequest.fromForm(state.form, uid)

    // 3.2 FR-7 chain (vendor → filament → spool) inside repo, all with extras
    val createOutcome = spoolman.createSpoolForNewFilament(req)
    val newSpoolId = when (createOutcome) {
        is SpoolmanOutcome.Success -> createOutcome.value
        else -> return SpoolmanFailed(uid, createOutcome)
    }

    // 3.3 Compute payload with the just-assigned spoolId
    val payload = OpenSpoolPayloadCodec.toJson(makePayload(state.form, spoolId = newSpoolId))

    // 3.4 NDEF write
    nfc.arm(NfcIntent.Write(payload))
    val writeResult = awaitTerminalNfc()
    if (writeResult is NfcResult.Error) return NfcFailed(uid, writeResult.reason)

    // 3.5 Verify
    nfc.arm(NfcIntent.Verify(payload))
    val verifyResult = awaitTerminalNfc()
    return when (verifyResult) {
        is NfcResult.Success -> Success.WrittenAndPaired(newSpoolId, uid, isNewSpool = true)
        is NfcResult.Error -> {
            if (verifyResult.reason.contains("verify mismatch", ignoreCase = true))
                VerifyFailed(newSpoolId, uid, isNewSpool = true, cause = verifyResult.reason)
            else
                NfcFailed(uid, verifyResult.reason)
        }
        else -> NfcFailed(uid, "unexpected non-terminal verify state")
    }
}
```

**Verify-fail recovery (CP-9)**: when `VerifyFailed(isNewSpool=true)` returns, the Spoolman record persists. A retry of `onWriteTapped` finds the spool via `findSpoolsByCardUid(uid)` (because `createSpoolForNewFilament` already set `extra.card_uids` in step 3.2) and routes through `existingSpoolPath` instead. No code in the use-case handles this — it's an emergent property of the bulk-fetch+filter behaviour after the new spool was committed to Spoolman.

## 4. Helper — `awaitTerminalNfc()`

```text
private suspend fun awaitTerminalNfc(): NfcResult =
    nfc.state.first { it is NfcResult.Success || it is NfcResult.Error }
```

## 5. `SpoolmanRepository.appendCardUidToSpool(spoolId, uid)` (U3-Δ-3)

```text
suspend fun appendCardUidToSpool(spoolId: Int, uid: CardUid): SpoolmanOutcome<SpoolmanSpool> =
    executeWithExtraFieldsBootstrap {
        val spool = api.getSpool(spoolId).orElseReturn()
        val current = ExtraCardUidsCodec.decode(spool.extra?.get("card_uids") ?: "")
        if (uid in current) {
            // CP-10 idempotency — no PATCH
            cache.update(spool)
            return@executeWithExtraFieldsBootstrap SpoolmanOutcome.Success(spool)
        }
        val newUids = current + uid
        val newExtra = (spool.extra ?: emptyMap()) + ("card_uids" to ExtraCardUidsCodec.encode(newUids))
        val patched = api.patchSpool(spoolId, body = SpoolPatchBody(extra = newExtra)).orElseReturn()
        cache.update(patched)
        SpoolmanOutcome.Success(patched)
    }
```

## 6. `SpoolmanRepository.removeCardUidFromSpool(spoolId, uid)` (U3-Δ-4)

```text
suspend fun removeCardUidFromSpool(spoolId: Int, uid: CardUid): SpoolmanOutcome<SpoolmanSpool> =
    executeWithExtraFieldsBootstrap {
        val spool = api.getSpool(spoolId).orElseReturn()
        val current = ExtraCardUidsCodec.decode(spool.extra?.get("card_uids") ?: "")
        if (uid !in current) {
            cache.update(spool)
            return@executeWithExtraFieldsBootstrap SpoolmanOutcome.Success(spool)  // idempotent
        }
        val newUids = current - uid
        // U3-Δ-RULE-4: empty result preserves "\"\"" (does not drop key)
        val newExtra = (spool.extra ?: emptyMap()) +
            ("card_uids" to ExtraCardUidsCodec.encode(newUids))
        val patched = api.patchSpool(spoolId, body = SpoolPatchBody(extra = newExtra)).orElseReturn()
        cache.update(patched)
        SpoolmanOutcome.Success(patched)
    }
```

## 7. `SpoolmanRepository.createSpoolForNewFilament(req)` (U3-Δ-5)

```text
suspend fun createSpoolForNewFilament(req: NewFilamentRequest): SpoolmanOutcome<Int> {
    // 7.1 Vendor — reuse if exists
    val vendorId = ensureVendor(req.vendorName).orElseReturn()

    // 7.2 Filament — POST with optional extra.variant
    val filamentBody = FilamentPostBody(
        name = req.name,
        vendor_id = vendorId,
        material = req.material,
        color_hex = req.colorHex,
        diameter = req.diameter,
        weight = req.weight,
        density = req.density,
        settings_extruder_temp = req.extruderMax,   // representative temp per spec
        settings_bed_temp = req.bedMax,
        extra = req.variant
            ?.takeUnless { it.isBlank() }
            ?.let { mapOf("variant" to Gson().toJson(it)) },
    )
    val filamentId = executeWithExtraFieldsBootstrap {
        api.postFilament(filamentBody).orElseReturn().id
    }.unwrap() ?: return /* the lazy-bootstrap failure */

    // 7.3 Spool — POST with extra.card_uids
    val spoolBody = SpoolPostBody(
        filament_id = filamentId,
        extra = mapOf("card_uids" to ExtraCardUidsCodec.encode(listOf(req.cardUid))),
    )
    val spoolId = executeWithExtraFieldsBootstrap {
        api.postSpool(spoolBody).orElseReturn().id
    }.unwrap() ?: return /* lazy-bootstrap failure */

    cache.refresh()  // patch-in-place per Q-U3-2
    return SpoolmanOutcome.Success(spoolId)
}
```

## 8. `executeWithExtraFieldsBootstrap` helper (U3-Δ-RULE-7)

```text
private suspend inline fun <T> executeWithExtraFieldsBootstrap(
    block: () -> SpoolmanOutcome<T>,
): SpoolmanOutcome<T> {
    val first = block()
    if (first is SpoolmanOutcome.HttpError &&
        first.statusCode == 400 &&
        first.body.contains("Unknown extra field", ignoreCase = true)) {
        ensureExtraFieldsRegistered()  // bootstrap (U3-Δ-RULE-6)
        return block()                 // retry once
    }
    return first
}
```

## 9. `SpoolmanRepository.ensureExtraFieldsRegistered()` (U3-Δ-RULE-6)

```text
suspend fun ensureExtraFieldsRegistered(): SpoolmanOutcome<Unit> {
    val spoolFields = api.getFields(EntityType.SPOOL).orElseReturn()
    if (spoolFields.none { it.key == "card_uids" }) {
        api.postField(EntityType.SPOOL, "card_uids", body = ExtraFieldDef(
            name = "Card UIDs",
            field_type = "text",
            order = 1,
            default_value = "\"\"",
        )).orElseReturn()
    }
    val filamentFields = api.getFields(EntityType.FILAMENT).orElseReturn()
    if (filamentFields.none { it.key == "variant" }) {
        api.postField(EntityType.FILAMENT, "variant", body = ExtraFieldDef(
            name = "Variant",
            field_type = "text",
            order = 1,
            default_value = "\"\"",
        )).orElseReturn()
    }
    return SpoolmanOutcome.Success(Unit)
}
```

## 10. `SpoolmanRepository.findSpoolsByCardUid(uid)` (U3-Δ-2)

```text
suspend fun findSpoolsByCardUid(uid: CardUid): SpoolmanOutcome<List<SpoolmanSpool>> {
    if (uid.hex.isEmpty()) return SpoolmanOutcome.Success(emptyList())   // Q-U3-1=A
    val all = api.getAllSpools(limit = 1000, allow_archived = true).orElseReturn()
    val matches = all.filter { spool ->
        val uids = ExtraCardUidsCodec.decode(spool.extra?.get("card_uids") ?: "")
        uid in uids
    }
    return SpoolmanOutcome.Success(matches)
}
```

## 11. `SpoolmanRepository.testConnection()` (U3-Δ-RULE-8)

```text
suspend fun testConnection(): SpoolmanOutcome<String /* version */> = try {
    val info = api.getInfo()  // GET /api/v1/info
    SpoolmanOutcome.Success(info.version)
} catch (e: HttpException) { SpoolmanOutcome.HttpError(e.code(), e.message()) }
  catch (e: IOException)   { SpoolmanOutcome.NetworkError(e.message ?: "network") }
  catch (e: Exception)     { SpoolmanOutcome.ParseError(e.message ?: "parse") }
```

## 12. `MainViewModel.onWriteTapped()` orchestration

```text
fun onWriteTapped() = viewModelScope.launch {
    if (!canWrite.value) return@launch                          // VM-1
    state.update { it.copy(activeFlow = WritingForPair) }       // VM-3.2

    val result = withTimeoutOrNull(WRITE_TIMEOUT_MS) {
        createAndPair.invoke()
    } ?: CreateAndPairResult.Cancelled("timeout")

    when (result) {
        is Success.WrittenAndPaired -> {                        // VM-4
            uiEffects.send(UiEffect.ShowSnackbar("Paired and written"))
            state.update { it.copy(form = FormState(), activeFlow = Idle) }
        }
        is VerifyFailed -> {                                    // VM-5
            uiEffects.send(UiEffect.ShowSnackbar("Verify failed. Tap Save to retry."))
            state.update { it.copy(activeFlow = Idle) }         // form preserved
        }
        is SpoolmanFailed -> {                                  // VM-6
            uiEffects.send(UiEffect.ShowSnackbar(result.outcome.userMessage()))
            state.update { it.copy(activeFlow = Idle) }
        }
        is NfcFailed -> {                                       // VM-7
            uiEffects.send(UiEffect.ShowSnackbar("NFC error: ${result.reason}"))
            state.update { it.copy(activeFlow = Idle) }
        }
        is Cancelled -> {                                       // VM-8
            uiEffects.send(UiEffect.ShowSnackbar("No tag tapped — try again"))
            nfc.disarm()
            state.update { it.copy(activeFlow = Idle) }
        }
    }
}

companion object { const val WRITE_TIMEOUT_MS = 15_000L }       // Q-U6a-8=A
```

## 13. Sequence diagram — existing-spool happy path

```text
User                    MainScreen      MainViewModel       CreateAndPairUseCase     SpoolmanRepository       NfcRepository
 |                          |                 |                     |                        |                       |
 |--Tap Save--------------->|                 |                     |                        |                       |
 |                          |--onWriteTapped->|                     |                        |                       |
 |                          |                 |--invoke()---------->|                        |                       |
 |                          |                 |                     |--moveOnBind.invoke---->| (NoOp → Proceed)      |
 |                          |                 |                     |--appendCardUidToSpool->|                       |
 |                          |                 |                     |                        |--GET /spool/42------->|
 |                          |                 |                     |                        |--PATCH /spool/42----->|
 |                          |                 |                     |                        |--SUCCESS-------------> |
 |                          |                 |                     |--arm(Write(payload))------------------------>|
 |                          |--Tap NFC tag--->|                     |                        |                       |
 |                          |                 |                     |<-state=Success------------------------------|
 |                          |                 |                     |--arm(Verify(payload))------------------------>|
 |                          |--Tap NFC tag--->|                     |                        |                       |
 |                          |                 |                     |<-state=Success------------------------------|
 |                          |                 |<--Success.WrittenAndPaired                    |                       |
 |                          |<-snackbar+reset-|                     |                        |                       |
```

## 14. Sequence diagram — new-spool with bootstrap retry

```text
User              MainViewModel       UseCase       Repository (createSpoolForNewFilament)        NfcRepository
 |                     |                 |                          |                                   |
 |--Tap Save---------->|                 |                          |                                   |
 |                     |--invoke()------>|--createSpoolForNewFilament---------------------->|           |
 |                     |                 |                          |--POST /vendor (reuse if found)--> |
 |                     |                 |                          |--POST /filament w/ extra.variant->|
 |                     |                 |                          |<-400 "Unknown extra field"--------|
 |                     |                 |                          |--POST /field/filament/variant--->|
 |                     |                 |                          |<-success---------------------------|
 |                     |                 |                          |--POST /filament (retry)---------> |
 |                     |                 |                          |<-200 {id: F}-----------------------|
 |                     |                 |                          |--POST /spool w/ extra.card_uids-->|
 |                     |                 |                          |<-400 "Unknown extra field"--------|
 |                     |                 |                          |--POST /field/spool/card_uids----->|
 |                     |                 |                          |<-success---------------------------|
 |                     |                 |                          |--POST /spool (retry)------------->|
 |                     |                 |                          |<-200 {id: S}-----------------------|
 |                     |                 |<-Success(S)              |                                   |
 |                     |                 |--arm(Write(payload(S))---------------------------------------->|
 |                     |                 |--arm(Verify)--------------------------------------------------->|
 |                     |<-WrittenAndPaired(S, uid, isNewSpool=true) |                                   |
```

## 15. Failure-mode summary table

| Failure | Outcome | Form preserved? | Spoolman state | Snackbar |
|---|---|---|---|---|
| Spoolman PATCH/POST 4xx/5xx | `SpoolmanFailed` | ✓ | unchanged (or partially committed in chain — see CP-9) | outcome msg |
| Spoolman 400 "Unknown extra field" first time | bootstrap+retry transparent | n/a | bootstrap completes | n/a (transparent) |
| Spoolman 400 "Unknown extra field" after bootstrap | `SpoolmanFailed` | ✓ | bootstrap was no-op | "Server error: 400" |
| NFC arm error (NFC disabled, etc.) | `NfcFailed` | ✓ | already PATCHed (existing-spool) or chain committed (new-spool) | "NFC error: …" |
| NDEF write error | `NfcFailed` | ✓ | already PATCHed | "NFC error: …" |
| Verify mismatch | `VerifyFailed` | ✓ | already PATCHed | "Verify failed. Tap Save to retry." |
| 15 s timeout | `Cancelled` | ✓ | already PATCHed | "No tag tapped — try again" |
| Concurrent re-tap | (silently dropped) | ✓ | unchanged | n/a |
| `WrittenAndPaired` (success) | success | ✗ (cleared) | committed | "Paired and written" |

## 16. Totality table — all `CreateAndPairResult` cases reach a VM handler

| Result variant | VM rule | Form fate |
|---|---|---|
| `Success.WrittenAndPaired` | VM-4 | cleared |
| `VerifyFailed` | VM-5 | preserved |
| `SpoolmanFailed` | VM-6 | preserved |
| `NfcFailed` | VM-7 | preserved |
| `Cancelled` | VM-8 | preserved |

Every variant is handled. There is no fall-through path.
