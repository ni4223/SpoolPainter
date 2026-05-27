# U6b — Code Generation Plan (Part 1)

**Stage**: CONSTRUCTION → Per-Unit Loop → Code Generation Part 1 (U6b)
**Unit**: U6b — Move-on-Bind + Two-Tag Flow
**Approval gate**: this plan must be approved before Code Gen Part 2 executes the checkboxes below.
**Inputs**:
- `aidlc-docs/construction/u6b-move-on-bind-two-tag/functional-design/{domain-entities,business-rules,business-logic-model,frontend-components}.md` (FD Part 2, approved 2026-05-26)
- `aidlc-docs/construction/plans/u6b-move-on-bind-two-tag-functional-design-plan.md` Decision Records Q-U6b-1..15
- `aidlc-docs/inception/application-design/unit-of-work.md` §3-U6b
- `aidlc-docs/inception/requirements/requirements.md` FR-5.1 / FR-5.2 / FR-6.1..6.4 / FR-13.2
- `aidlc-docs/inception/user-stories/stories.md` S-5.1 / S-5.2 / S-6.1 / S-6.2 / S-6.3 / S-6.4

**Branch**: `v2`. Working tree before this plan: 6 commits ahead of `origin/v2` (post-U6a close-out at `bb5dc93`); doc-only deltas in this session: `construction/plans/u6b-...-functional-design-plan.md`, four FD artefacts under `construction/u6b-move-on-bind-two-tag/functional-design/`, this plan, `aidlc-state.md`, `audit.md`.

**Test count target**: U6a closed at **244 / 244**. After U6b:
- +1 file / ~+8 cases (`MoveOnBindUseCaseTest`)
- +1 file / ~+5 cases (`TwoTagUseCaseTest`)
- +1 file / ~+4 cases (`RepairConfirmViewModelTest`)
- +1 file / ~+8 cases (`MainViewModelTwoTagTest`)
- +0..3 cases extending existing `CreateAndPairUseCaseTest` (move-on-bind reorder regression — Q-U6b-1)
- **Estimated total: ~268..272 / ~268..272**. Final count locked at Code Gen Part 2 close-out.

**Out-of-scope guards** (re-stated): no `RawWriteUseCase` / `VendorUidOnlyPairUseCase` (U7); no catalogue-backed pickers (U8); no full Settings UI (sort, theme, banner Retry — U9); no APK size review or JDK 17 portability fix (U10); no DataStore writes for two-tag in-flight state (FR-6.4); no instrumented Compose UI tests for the new sheets — manual verification at the **U6 milestone install gate** at this unit's close-out.

---

## §1 — Build dependencies

- [ ] 1.1 No new third-party dependencies. Coroutines `CompletableDeferred` (used in `MoveOnBindConfirmerImpl`) is already on the classpath via `kotlinx-coroutines-core`. Hilt + Compose Material 3 (`androidx.compose.material3.ModalBottomSheet`) already present from U1/U6a. No `libs.versions.toml` change.
- [ ] 1.2 No `app/build.gradle.kts` change.

---

## §2 — Domain entities (FD `domain-entities.md` §1)

### 2.1 Modify `app/src/main/java/com/spoolpainter/app/domain/usecases/MoveOnBindUseCase.kt`

- [ ] 2.1.1 Replace the existing `Outcome` placeholder with the final shape per FD §1.1:
  - `data object Proceed : Outcome`
  - `data class Moved(val fromSpoolId: Int) : Outcome`
  - `data object Declined : Outcome`
  - `data class Failed(val reason: String, val partiallyModifiedSpoolId: Int?) : Outcome`
  - `data class AmbiguousOwnership(val currentOwners: List<SpoolmanSpool>) : Outcome`
- [ ] 2.1.2 Delete the inline `class NoOp` from this file (replaced by the real impl in §3.1).
- [ ] 2.1.3 Keep the interface signature unchanged: `suspend operator fun invoke(uid: CardUid, targetSpoolId: Int): Outcome`. Imports: add `com.spoolpainter.app.domain.models.SpoolmanSpool`.

### 2.2 Create `app/src/main/java/com/spoolpainter/app/domain/usecases/MoveOnBindConfirmer.kt`

- [ ] 2.2.1 New file. Declares the interface + the `RepairConfirmRequest` data class per FD §1.2:
  ```kotlin
  interface MoveOnBindConfirmer {
      suspend fun confirm(other: SpoolmanSpool, targetSpoolId: Int, uid: CardUid): Boolean
      val pendingRequest: StateFlow<RepairConfirmRequest?>
      fun submitResult(confirm: Boolean)
  }

  data class RepairConfirmRequest(
      val other: SpoolmanSpool,
      val targetSpoolId: Int,
      val uid: CardUid,
  )
  ```

### 2.3 Create `app/src/main/java/com/spoolpainter/app/domain/usecases/TwoTagUseCase.kt`

- [ ] 2.3.1 New file. Declares the use-case **class** (not interface — same shape as `CreateAndPairUseCase`), the `TwoTagInput` input type, and the `TwoTagResult` sealed family per FD §1.3 / §1.4:
  ```kotlin
  data class TwoTagInput(val spoolId: Int)

  sealed interface TwoTagResult {
      sealed interface Success : TwoTagResult {
          data class SecondTagPaired(val spoolId: Int, val uid: CardUid) : Success
      }
      data class VendorTagRejected(val uid: CardUid) : TwoTagResult
      data class VerifyFailed(val uid: CardUid, val cause: String) : TwoTagResult
      data class SpoolmanFailed(val uid: CardUid, val outcome: SpoolmanOutcome<*>) : TwoTagResult
      data class NfcFailed(val uid: CardUid?, val reason: String) : TwoTagResult
      data class Cancelled(val reason: String) : TwoTagResult
      data class MoveOnBindPartial(val uid: CardUid, val partiallyModifiedSpoolId: Int, val reason: String) : TwoTagResult
  }

  open class TwoTagUseCase @Inject constructor(
      protected val nfc: NfcRepository,
      protected val spoolman: SpoolmanRepository,
      protected val moveOnBind: MoveOnBindUseCase,
  ) {
      open suspend operator fun invoke(input: TwoTagInput): TwoTagResult { /* §3.2 */ }
  }
  ```
  `open class` + `protected` field visibility mirrors `CreateAndPairUseCase` so test fakes can subclass.

### 2.4 Modify `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainUiState.kt`

- [ ] 2.4.1 Extend the `ActiveFlow` sealed interface with three new variants per FD §2.1:
  ```kotlin
  sealed interface ActiveFlow {
      data object Idle : ActiveFlow
      data object ReadingForPair : ActiveFlow
      data object WritingForPair : ActiveFlow
      data class PromptingPairAnother(val spoolId: Int) : ActiveFlow
      data class WritingSecondTag(val spoolId: Int) : ActiveFlow
      data class AwaitingRepairConfirmation(
          val uid: CardUid,
          val currentOwner: SpoolmanSpool,
          val targetSpoolId: Int,
      ) : ActiveFlow
  }
  ```
- [ ] 2.4.2 Add import `com.spoolpainter.app.domain.models.SpoolmanSpool` if not already present (it is — line 5).

### 2.5 Modify `app/src/main/java/com/spoolpainter/app/ui/components/sheets/RepairConfirmViewModel.kt`

- [ ] 2.5.1 Replace the placeholder ViewModel + `RepairConfirmUiState(placeholder=true)` with the real types per FD §3.3 / §2.2:
  ```kotlin
  data class RepairConfirmUiState(
      val otherSpoolDisplay: String,
      val otherSpoolId: Int,
      val targetSpoolId: Int,
      val uid: CardUid,
      val visible: Boolean,
  )

  @HiltViewModel
  class RepairConfirmViewModel @Inject constructor(
      private val confirmer: MoveOnBindConfirmer,
      spoolman: SpoolmanRepository,
  ) : ViewModel() {
      val uiState: StateFlow<RepairConfirmUiState>  // see §4.2 for derivation
      fun onConfirm() { confirmer.submitResult(true) }
      fun onDismiss() { confirmer.submitResult(false) }
  }
  ```

### 2.6 Create `app/src/main/java/com/spoolpainter/app/ui/components/sheets/PairAnotherTagViewModel.kt`

- [ ] 2.6.1 New file. Trivial ViewModel — most of the logic is on `MainViewModel`, so this is a thin shell mirroring `RepairConfirmViewModel`'s shape but the sheet's actions delegate directly to `MainViewModel`. **Consideration**: keep this VM **out** if the sheet doesn't need its own state holder. Decision: omit `PairAnotherTagViewModel` entirely; `PairAnotherTagSheet` reads `PairAnotherTagUiState` from `MainViewModel.state.activeFlow` derived state. Mark this step **N/A** in execution. (No file created.)

### 2.7 Define `PairAnotherTagUiState` data class

- [ ] 2.7.1 Add `data class PairAnotherTagUiState(val spoolId: Int, val visible: Boolean)` to a small new file `app/src/main/java/com/spoolpainter/app/ui/components/sheets/PairAnotherTagUiState.kt`. (Co-located with the sheet's Compose file in §5.)

---

## §3 — Use-cases (FD `business-rules.md` §1, §2, §4 + `business-logic-model.md` §1, §2)

### 3.1 Create `app/src/main/java/com/spoolpainter/app/domain/usecases/MoveOnBindUseCaseImpl.kt`

- [ ] 3.1.1 New file. Implements `MoveOnBindUseCase` per FD `business-logic-model.md` §1:
  ```kotlin
  class MoveOnBindUseCaseImpl @Inject constructor(
      private val spoolman: SpoolmanRepository,
      private val confirmer: MoveOnBindConfirmer,
  ) : MoveOnBindUseCase {

      override suspend fun invoke(uid: CardUid, targetSpoolId: Int): MoveOnBindUseCase.Outcome {
          val matches = when (val outcome = spoolman.findSpoolsByCardUid(uid)) {
              is SpoolmanOutcome.Success -> outcome.data
              else -> return MoveOnBindUseCase.Outcome.Failed(
                  reason = humanReadable(outcome),
                  partiallyModifiedSpoolId = null,
              )
          }
          return when {
              matches.isEmpty() -> MoveOnBindUseCase.Outcome.Proceed
              matches.size == 1 && matches.single().id == targetSpoolId -> MoveOnBindUseCase.Outcome.Proceed
              matches.size == 1 -> performMove(uid, targetSpoolId, matches.single())
              else -> MoveOnBindUseCase.Outcome.AmbiguousOwnership(matches)
          }
      }

      private suspend fun performMove(uid: CardUid, targetSpoolId: Int, other: SpoolmanSpool): MoveOnBindUseCase.Outcome {
          val confirmed = confirmer.confirm(other, targetSpoolId, uid)
          if (!confirmed) return MoveOnBindUseCase.Outcome.Declined
          val otherId = other.id ?: return MoveOnBindUseCase.Outcome.Failed(
              "owning spool has no id", partiallyModifiedSpoolId = null,
          )
          when (val rmv = spoolman.removeCardUidFromSpool(otherId, uid)) {
              is SpoolmanOutcome.Success -> Unit
              else -> return MoveOnBindUseCase.Outcome.Failed(humanReadable(rmv), partiallyModifiedSpoolId = null)
          }
          when (val apd = spoolman.appendCardUidToSpool(targetSpoolId, uid)) {
              is SpoolmanOutcome.Success -> Unit
              else -> return MoveOnBindUseCase.Outcome.Failed(humanReadable(apd), partiallyModifiedSpoolId = otherId)
          }
          return MoveOnBindUseCase.Outcome.Moved(fromSpoolId = otherId)
      }

      private fun humanReadable(outcome: SpoolmanOutcome<*>): String = when (outcome) {
          is SpoolmanOutcome.HttpError -> "HTTP ${outcome.code}: ${outcome.body}"
          is SpoolmanOutcome.NetworkError -> "Network: ${outcome.cause.message}"
          is SpoolmanOutcome.ParseError -> "Parse: ${outcome.cause.message}"
          is SpoolmanOutcome.Success -> "Success"
      }
  }
  ```
  Imports: `com.spoolpainter.app.data.remote.spoolman.{SpoolmanOutcome, SpoolmanRepository}`, `com.spoolpainter.app.domain.models.SpoolmanSpool`, `com.spoolpainter.app.domain.primitives.CardUid`, `javax.inject.Inject`.

### 3.2 Create `app/src/main/java/com/spoolpainter/app/domain/usecases/MoveOnBindConfirmerImpl.kt`

- [ ] 3.2.1 New file. Implements the singleton confirmer per FD `business-logic-model.md` §3:
  ```kotlin
  @Singleton
  class MoveOnBindConfirmerImpl @Inject constructor() : MoveOnBindConfirmer {
      private val _pendingRequest = MutableStateFlow<RepairConfirmRequest?>(null)
      private var pendingResult: CompletableDeferred<Boolean>? = null

      override val pendingRequest: StateFlow<RepairConfirmRequest?> = _pendingRequest.asStateFlow()

      override suspend fun confirm(other: SpoolmanSpool, targetSpoolId: Int, uid: CardUid): Boolean {
          check(_pendingRequest.value == null && pendingResult == null) {
              "MoveOnBindConfirmer: another request is already pending"
          }
          val deferred = CompletableDeferred<Boolean>()
          pendingResult = deferred
          _pendingRequest.value = RepairConfirmRequest(other, targetSpoolId, uid)
          return try {
              deferred.await()
          } finally {
              pendingResult = null
              _pendingRequest.value = null
          }
      }

      override fun submitResult(confirm: Boolean) {
          pendingResult?.complete(confirm)
      }
  }
  ```
  Imports: `kotlinx.coroutines.CompletableDeferred`, `kotlinx.coroutines.flow.{MutableStateFlow, StateFlow, asStateFlow}`, `com.spoolpainter.app.domain.models.SpoolmanSpool`, `com.spoolpainter.app.domain.primitives.CardUid`, `javax.inject.{Inject, Singleton}`.

### 3.3 Implement `TwoTagUseCase.invoke(...)` in the file from §2.3

- [ ] 3.3.1 Body of `invoke` per FD `business-logic-model.md` §2.2:
  1. `derivePayload(spoolId)` (§3.4) — returns `OpenSpoolPayload` or fails.
  2. Arm Write: `nfc.arm(NfcIntent.Write(payload, expectedUid = null))`.
  3. `awaitTerminalNfc()` — same shape as `CreateAndPairUseCase.awaitTerminalNfc`.
  4. Map `NfcResult.Success / Error` to `TwoTagResult` per BR-U6b-T2-3 / -4.
  5. On non-vendor `Success`, call `moveOnBind.invoke(uid, spoolId)` and branch per BR-U6b-T2-5 (`Proceed | Moved` → continue; `Declined` → `Cancelled("repair declined…")`; `Failed(reason, partial != null)` → `MoveOnBindPartial`; `Failed(reason, null)` → `SpoolmanFailed`; `AmbiguousOwnership` → `SpoolmanFailed`).
  6. `appendCardUidToSpool(spoolId, uid)` — idempotent. Map non-Success to `SpoolmanFailed`.
  7. Return `Success.SecondTagPaired(spoolId, uid)`.
- [ ] 3.3.2 `awaitTerminalNfc()` private helper: `nfc.state.first { it is NfcResult.Success || it is NfcResult.Error }`.

### 3.4 `derivePayload(spoolId)` helper in `TwoTagUseCase.kt`

- [ ] 3.4.1 Private helper per FD `business-logic-model.md` §2.3:
  - Look up spool in `spoolman.spools.value`; on miss, call `spoolman.getSpool(spoolId)` (already exists per `SpoolmanRepository.kt:99`); on non-Success → propagate as `TwoTagResult.SpoolmanFailed`.
  - Look up filament in `spoolman.filaments.value` matching `spool.filament?.id`; on miss, call `spoolman.getFilament(filamentId)` — **NEW THIN HELPER NEEDED**: see §3.6.
  - Look up vendor: prefer `spoolman.vendors.value` matching `filament.vendor?.id`; fallback to the `vendor` sub-doc embedded in `SpoolmanFilament` DTO if present.
  - Build `OpenSpoolPayload` with the same field-mapping convention as `CreateAndPairUseCase.makePayload(...)`:
    - `type = filament.material ?: "PLA"`
    - `colorHex = filament.colorHex`
    - `brand = vendor?.name ?: "Unknown"`
    - `minTemp = filament.settingsExtruderMinTemp?.toString() ?: "190"`
    - `maxTemp = filament.settingsExtruderMaxTemp?.toString() ?: "220"`
    - `bedMinTemp = filament.settingsBedMinTemp?.toString()`
    - `bedMaxTemp = filament.settingsBedMaxTemp?.toString()`
    - `subtype = (spool.extra?.get("variant") ?: filament.extra?.get("variant"))?.takeUnless { it.isBlank() } ?: "Basic"`
    - `spoolId = spool.id?.toString()`
- [ ] 3.4.2 The helper returns either `OpenSpoolPayload` (success) or a `TwoTagResult.SpoolmanFailed(...)` (failure). Caller switches on the return.

### 3.5 Modify `app/src/main/java/com/spoolpainter/app/domain/usecases/CreateAndPairUseCase.kt` (Q-U6b-1 reorder)

- [ ] 3.5.1 In `invoke(...)`, replace the current sequence
  ```
  moveOnBind.invoke(tappedUid, spoolId)              // line 77
  spoolman.appendCardUidToSpool(spoolId, tappedUid)  // step 3
  ```
  with the BR-U6b-CP-1..2 sequence:
  ```kotlin
  // Move-on-bind precheck — runs BEFORE the append on B (S-5.1).
  when (val mob = moveOnBind.invoke(tappedUid, spoolId)) {
      is MoveOnBindUseCase.Outcome.Proceed,
      is MoveOnBindUseCase.Outcome.Moved -> Unit                        // continue
      is MoveOnBindUseCase.Outcome.Declined ->
          return CreateAndPairResult.Cancelled(
              "repair declined — UID still on spool ${(mob as? MoveOnBindUseCase.Outcome.Declined)?.let { "" } ?: ""}"
          ) // see actual reason wiring below
      is MoveOnBindUseCase.Outcome.Failed ->
          return CreateAndPairResult.SpoolmanFailed(
              tappedUid,
              SpoolmanOutcome.ParseError(IllegalStateException(mob.reason)),
          )
      is MoveOnBindUseCase.Outcome.AmbiguousOwnership ->
          return CreateAndPairResult.SpoolmanFailed(
              tappedUid,
              SpoolmanOutcome.ParseError(IllegalStateException(
                  "ambiguous ownership: spool ids " + mob.currentOwners.mapNotNull { it.id }.joinToString(", ")
              )),
          )
  }

  when (val append = spoolman.appendCardUidToSpool(spoolId, tappedUid)) {
      is SpoolmanOutcome.Success -> Unit
      else -> return CreateAndPairResult.SpoolmanFailed(tappedUid, append)
  }
  ```
  **Cancel reason refinement**: extract the source spool id from `mob` when it is `Declined` — but `Declined` carries no payload. Solution: look up the source via `mob` only if `Moved` (which carries `fromSpoolId`); for `Declined`, the use-case does not know the source spool id (the confirmer holds it). Simplest correct copy: `"repair declined — UID still on the originally-paired spool"`. Update the cancel string accordingly so we don't fabricate a wrong id.
- [ ] 3.5.2 Imports unchanged (existing `MoveOnBindUseCase`, `SpoolmanOutcome`, `CardUid`).
- [ ] 3.5.3 Comment cleanup: drop the line-77 paragraph "the same-UID-on-two-spools conflict is the proper job of MoveOnBindUseCase (U6b)" since U6b is now landing.

### 3.6 Add `SpoolmanRepository.getFilament(filamentId)` helper

- [ ] 3.6.1 Modify `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanRepository.kt`. Add a thin method mirroring `getSpool`:
  ```kotlin
  open suspend fun getFilament(filamentId: Int): SpoolmanOutcome<SpoolmanFilament> {
      val api = cachedApi ?: return urlNotConfigured()
      return performHttp("getFilament") { api.getFilament(filamentId) }
  }
  ```
- [ ] 3.6.2 Modify `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanApi.kt`. Add the corresponding endpoint (search for `getSpool` to find the surrounding pattern):
  ```kotlin
  @GET("api/v1/filament/{id}")
  suspend fun getFilament(@Path("id") filamentId: Int): Response<SpoolmanFilament>
  ```

---

## §4 — `MainViewModel` extensions (FD `business-rules.md` §3)

### 4.1 Modify `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainViewModel.kt`

- [ ] 4.1.1 Inject the two new dependencies (alongside existing `nfc`, `readAndPair`, `createAndPair`, `spoolman`, `settings`):
  ```kotlin
  @HiltViewModel
  class MainViewModel @Inject constructor(
      // existing args …
      private val twoTag: TwoTagUseCase,
      private val confirmer: MoveOnBindConfirmer,
  ) : ViewModel()
  ```
- [ ] 4.1.2 Extend `applyWriteResult(WrittenAndPaired)` (line 357) per BR-U6b-MV-2:
  - Replace the existing form-clear-on-success path with a transition to `ActiveFlow.PromptingPairAnother(spoolId = result.spoolId)`. The "Paired and written" snackbar is preserved.
  - **Form is NOT cleared yet** — clearing moves into BR-U6b-MV-4 / -5 reach-points.
- [ ] 4.1.3 Add `fun onPairAnotherTagAccepted()` per BR-U6b-MV-3:
  ```kotlin
  fun onPairAnotherTagAccepted() {
      val current = _state.value.activeFlow as? ActiveFlow.PromptingPairAnother ?: return
      val spoolId = current.spoolId
      _state.update { it.copy(activeFlow = ActiveFlow.WritingSecondTag(spoolId)) }
      viewModelScope.launch {
          val result = withTimeoutOrNull(writeTimeoutMs) {
              twoTag.invoke(TwoTagInput(spoolId))
          } ?: TwoTagResult.Cancelled("timeout")
          applyTwoTagResult(result)
      }
  }
  ```
- [ ] 4.1.4 Add `fun onPairAnotherTagDismissed()` per BR-U6b-MV-4:
  ```kotlin
  fun onPairAnotherTagDismissed() {
      if (_state.value.activeFlow !is ActiveFlow.PromptingPairAnother) return
      _state.update {
          it.copy(
              activeFlow = ActiveFlow.Idle,
              form = FormState(rawWriteMode = it.form.rawWriteMode),
              spoolman = it.spoolman.copy(selectedSpoolId = null),
          )
      }
      _customMaterial.value = ""
      _customBrand.value = ""
      _effects.trySend(UiEffect.ShowSnackbar("Saved with one tag"))
  }
  ```
- [ ] 4.1.5 Add `private fun applyTwoTagResult(result: TwoTagResult)` per BR-U6b-MV-5:
  - `Success.SecondTagPaired(spoolId, uid)` → activeFlow = Idle; clear form (same logic as 4.1.4 minus the snackbar text); `_effects.trySend(UiEffect.ShowSnackbar("Both tags paired"))`.
  - `VendorTagRejected(uid)` → activeFlow = Idle; snackbar "Vendor tag — write blocked"; do NOT clear form.
  - `VerifyFailed(uid, cause)` → activeFlow = Idle; snackbar "Second-tag verify failed: ${cause}".
  - `SpoolmanFailed(uid, outcome)` → activeFlow = Idle; banner via existing `BannerState.Offline(humanReadable(outcome))` path.
  - `MoveOnBindPartial(uid, partial, reason)` → activeFlow = Idle; snackbar "Partial state in Spoolman — UID was removed from spool #${partial}; restore manually if needed".
  - `NfcFailed(uid, reason)` → activeFlow = Idle; snackbar "Tag write failed: ${reason}".
  - `Cancelled(reason)` → activeFlow = Idle; snackbar "Second-tag pairing cancelled (${reason})".
- [ ] 4.1.6 Add `fun onRepairResult(confirm: Boolean) { confirmer.submitResult(confirm) }` (BR-U6b-MV-7).
- [ ] 4.1.7 Add `init { … }` collector for `confirmer.pendingRequest` per BR-U6b-MV-6:
  ```kotlin
  init {
      // existing settings collector …
      viewModelScope.launch {
          confirmer.pendingRequest.collect { req ->
              if (req != null) {
                  priorActiveFlow = _state.value.activeFlow
                  _state.update {
                      it.copy(activeFlow = ActiveFlow.AwaitingRepairConfirmation(
                          uid = req.uid,
                          currentOwner = req.other,
                          targetSpoolId = req.targetSpoolId,
                      ))
                  }
              } else {
                  // request resolved; the use-case continuation will overwrite shortly,
                  // but restore prior state defensively.
                  val prior = priorActiveFlow ?: ActiveFlow.Idle
                  _state.update {
                      if (it.activeFlow is ActiveFlow.AwaitingRepairConfirmation) it.copy(activeFlow = prior) else it
                  }
              }
          }
      }
  }
  private var priorActiveFlow: ActiveFlow? = null
  ```
- [ ] 4.1.8 Update `canRead` / `canWrite` derived predicates (locate via `val canRead` / `val canWrite` definitions; if absent, the gating lives inside `onReadTapped` / `onWriteTapped` `if` guards already — extend those guards to early-return when `activeFlow is PromptingPairAnother | WritingSecondTag | AwaitingRepairConfirmation`).

### 4.2 Modify `app/src/main/java/com/spoolpainter/app/ui/components/sheets/RepairConfirmViewModel.kt`

- [ ] 4.2.1 Real `uiState` derivation. Combines `confirmer.pendingRequest` with the spool list to resolve a display name:
  ```kotlin
  val uiState: StateFlow<RepairConfirmUiState> =
      confirmer.pendingRequest
          .map { req ->
              if (req == null) {
                  RepairConfirmUiState(
                      otherSpoolDisplay = "",
                      otherSpoolId = 0,
                      targetSpoolId = 0,
                      uid = CardUid(""),
                      visible = false,
                  )
              } else {
                  RepairConfirmUiState(
                      otherSpoolDisplay = displayName(req.other),
                      otherSpoolId = req.other.id ?: 0,
                      targetSpoolId = req.targetSpoolId,
                      uid = req.uid,
                      visible = true,
                  )
              }
          }
          .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L),
                  RepairConfirmUiState("", 0, 0, CardUid(""), false))

  private fun displayName(spool: SpoolmanSpool): String {
      val filament = spool.filament
      val vendorName = filament?.vendor?.name?.takeIf { it.isNotBlank() }
      val material = filament?.material?.takeIf { it.isNotBlank() }
      val color = filament?.colorHex?.takeIf { it.isNotBlank() }
      return listOfNotNull(vendorName, material, color)
          .takeIf { it.isNotEmpty() }
          ?.joinToString(" ")
          ?.let { "$it #${spool.id}" }
          ?: "spool #${spool.id}"
  }
  ```
  Imports: `androidx.lifecycle.viewModelScope`, `kotlinx.coroutines.flow.{SharingStarted, StateFlow, map, stateIn}`, `com.spoolpainter.app.domain.models.SpoolmanSpool`, `com.spoolpainter.app.domain.primitives.CardUid`, `com.spoolpainter.app.domain.usecases.MoveOnBindConfirmer`.

---

## §5 — Compose UI (FD `frontend-components.md` §2, §3, §4)

### 5.1 Create `app/src/main/java/com/spoolpainter/app/ui/components/sheets/RepairConfirmSheet.kt`

- [ ] 5.1.1 New file. `@Composable fun RepairConfirmSheet(state, onConfirm, onDismiss)` rendering `ModalBottomSheet` with the concise copy per FD §3.3 / Q-U6b-7=B:
  - Title: `"Re-pair this tag to the selected spool?"` (Material 3 `titleMedium`).
  - Body: `"Currently on: ${state.otherSpoolDisplay}"` (`bodyMedium`).
  - Button row: `TextButton("Cancel")` left + `FilledTonalButton("Move it")` right; 8.dp horizontal arrangement.
  - Sheet's `onDismissRequest` → `onDismiss()`.
- [ ] 5.1.2 `data-testid` equivalents — Compose uses `Modifier.semantics { testTag = "..." }`. Add `testTag = "repair-confirm-sheet-confirm"` and `"repair-confirm-sheet-cancel"` for the two buttons.

### 5.2 Create `app/src/main/java/com/spoolpainter/app/ui/components/sheets/PairAnotherTagSheet.kt`

- [ ] 5.2.1 New file. `@Composable fun PairAnotherTagSheet(state, onAccept, onDismiss)` per FD §3:
  - Title: `"Pair another tag with this spool?"`
  - Body: `"We'll write the same data to the second tag and remember both."`
  - Button row: `TextButton("Done")` + `FilledTonalButton("Pair another")`.
  - Sheet's `onDismissRequest` → `onDismiss()`.
- [ ] 5.2.2 testTags: `"pair-another-tag-sheet-accept"`, `"pair-another-tag-sheet-done"`.

### 5.3 Create `app/src/main/java/com/spoolpainter/app/ui/components/sheets/BottomSheetHost.kt`

- [ ] 5.3.1 New file. Selector composable per FD §4:
  ```kotlin
  @Composable
  fun BottomSheetHost(
      activeFlow: ActiveFlow,
      repairConfirmState: RepairConfirmUiState,
      pairAnotherState: PairAnotherTagUiState?,
      onRepairConfirm: () -> Unit,
      onRepairDismiss: () -> Unit,
      onPairAnotherAccept: () -> Unit,
      onPairAnotherDismiss: () -> Unit,
  ) {
      when (activeFlow) {
          is ActiveFlow.AwaitingRepairConfirmation ->
              if (repairConfirmState.visible) RepairConfirmSheet(repairConfirmState, onRepairConfirm, onRepairDismiss)
          is ActiveFlow.PromptingPairAnother ->
              pairAnotherState?.takeIf { it.visible }?.let { PairAnotherTagSheet(it, onPairAnotherAccept, onPairAnotherDismiss) }
          else -> Unit
      }
  }
  ```

### 5.4 Modify `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainScreen.kt`

- [ ] 5.4.1 Inject the new ViewModel (`hiltViewModel<RepairConfirmViewModel>()`) and collect its `uiState`.
- [ ] 5.4.2 Derive `pairAnotherState` from `state.activeFlow`:
  ```kotlin
  val pairAnotherState by remember(state.activeFlow) {
      derivedStateOf {
          (state.activeFlow as? ActiveFlow.PromptingPairAnother)?.let {
              PairAnotherTagUiState(spoolId = it.spoolId, visible = true)
          }
      }
  }
  ```
- [ ] 5.4.3 Add `BottomSheetHost(...)` invocation near the top of the main `Box` content, wired to the four callbacks:
  - `onRepairConfirm = repairConfirmViewModel::onConfirm`
  - `onRepairDismiss = repairConfirmViewModel::onDismiss`
  - `onPairAnotherAccept = mainViewModel::onPairAnotherTagAccepted`
  - `onPairAnotherDismiss = mainViewModel::onPairAnotherTagDismissed`

---

## §6 — Hilt graph (FD `business-rules.md` §6 / BR-U6b-X-3)

### 6.1 Modify `app/src/main/java/com/spoolpainter/app/di/RepositoryModule.kt`

- [ ] 6.1.1 Change `bindMoveOnBindUseCase`'s parameter type from `MoveOnBindUseCase.NoOp` to `MoveOnBindUseCaseImpl`:
  ```kotlin
  @Binds
  abstract fun bindMoveOnBindUseCase(impl: MoveOnBindUseCaseImpl): MoveOnBindUseCase
  ```
- [ ] 6.1.2 Add a new `@Binds @Singleton` for the confirmer:
  ```kotlin
  @Binds
  @Singleton
  abstract fun bindMoveOnBindConfirmer(impl: MoveOnBindConfirmerImpl): MoveOnBindConfirmer
  ```
- [ ] 6.1.3 Update imports: drop `MoveOnBindUseCase` import unless still referenced; add `MoveOnBindUseCaseImpl`, `MoveOnBindConfirmer`, `MoveOnBindConfirmerImpl`.

---

## §7 — Tests

### 7.1 Create `app/src/test/java/com/spoolpainter/app/support/FakeMoveOnBindConfirmer.kt`

- [ ] 7.1.1 Test fake — drives deterministic confirm/decline/throw responses:
  ```kotlin
  class FakeMoveOnBindConfirmer : MoveOnBindConfirmer {
      private val _pendingRequest = MutableStateFlow<RepairConfirmRequest?>(null)
      override val pendingRequest: StateFlow<RepairConfirmRequest?> = _pendingRequest

      var nextResult: Boolean = true   // tests set this before invoking the use-case
      var lastRequest: RepairConfirmRequest? = null
          private set
      var confirmCalls: Int = 0
          private set

      override suspend fun confirm(other: SpoolmanSpool, targetSpoolId: Int, uid: CardUid): Boolean {
          confirmCalls++
          val req = RepairConfirmRequest(other, targetSpoolId, uid)
          lastRequest = req
          _pendingRequest.value = req
          // synchronous — return immediately for tests, mirroring the resolved-by-UI path.
          return nextResult.also { _pendingRequest.value = null }
      }

      override fun submitResult(confirm: Boolean) { /* no-op for test */ }
  }
  ```

### 7.2 Create `app/src/test/java/com/spoolpainter/app/domain/usecases/MoveOnBindUseCaseTest.kt`

- [ ] 7.2.1 ~8 cases:
  - `proceed_when_no_owners`
  - `proceed_when_uid_already_on_target`
  - `moved_when_owner_is_different_and_user_confirms`
  - `declined_when_user_cancels`
  - `failed_when_remove_fails_no_partial`
  - `failed_with_partial_when_append_fails_after_remove_succeeds`
  - `ambiguous_when_two_owners`
  - `failed_when_findSpoolsByCardUid_returns_http_error`
- [ ] 7.2.2 Multi-UID source preserved: the `removeCardUidFromSpool` invocation uses the existing `FakeSpoolmanRepository` which already exercises full-extra read-modify-write semantics from U6a; assert the surviving `extra.card_uids` of A includes any other UIDs.

### 7.3 Create `app/src/test/java/com/spoolpainter/app/domain/usecases/TwoTagUseCaseTest.kt`

- [ ] 7.3.1 ~5 cases:
  - `success_writes_identical_payload_and_appends_uid`
  - `vendor_tag_rejected_no_append`
  - `cache_miss_falls_back_to_getSpool_round_trip`
  - `move_on_bind_declined_returns_cancelled`
  - `verify_failure_returns_VerifyFailed`

### 7.4 Create `app/src/test/java/com/spoolpainter/app/ui/components/sheets/RepairConfirmViewModelTest.kt`

- [ ] 7.4.1 ~4 cases:
  - `uiState_hidden_when_no_pending_request`
  - `uiState_renders_display_name_and_ids_when_request_pending`
  - `onConfirm_submits_true_to_confirmer`
  - `onDismiss_submits_false_to_confirmer`
- [ ] 7.4.2 Tests use a `FakeMoveOnBindConfirmer` variant that exposes `submitResult` calls (subclass `FakeMoveOnBindConfirmer` for the receive-side). Or use a separate `RecordingConfirmer` if simpler.

### 7.5 Create `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelTwoTagTest.kt`

- [ ] 7.5.1 ~8 cases:
  - `successful_first_pair_transitions_to_PromptingPairAnother`
  - `onPairAnotherTagAccepted_transitions_to_WritingSecondTag_and_invokes_useCase`
  - `onPairAnotherTagDismissed_clears_form_and_returns_to_Idle`
  - `applyTwoTagResult_Success_clears_form_and_emits_Both_tags_paired_snackbar`
  - `applyTwoTagResult_VendorTagRejected_emits_blocked_snackbar_does_not_clear_form`
  - `applyTwoTagResult_MoveOnBindPartial_emits_partial_snackbar_with_spool_id`
  - `confirmer_pendingRequest_emission_transitions_to_AwaitingRepairConfirmation`
  - `onRepairResult_forwards_to_confirmer_submitResult`
- [ ] 7.5.2 Add `FakeTwoTagUseCase` (subclass of `TwoTagUseCase` with overridden `invoke` returning a canned `TwoTagResult`). Co-locate under `app/src/test/java/com/spoolpainter/app/support/FakeTwoTagUseCase.kt` (split into 7.5.3 below).
- [ ] 7.5.3 Create `app/src/test/java/com/spoolpainter/app/support/FakeTwoTagUseCase.kt` mirroring `FakeCreateAndPairUseCase` shape.

### 7.6 Modify `app/src/test/java/com/spoolpainter/app/support/FakeCreateAndPairUseCase.kt`

- [ ] 7.6.1 No behavioural change — but the `MoveOnBindUseCase.NoOp()` call site at line 11 must be removed; pass the test seam differently. Option A: change default arg to `moveOnBind: MoveOnBindUseCase = FakeMoveOnBindUseCase()` where the new test fake returns `Outcome.Proceed`. Option B: drop `NoOp` from production but keep a tiny test-only `object MoveOnBindNoOp : MoveOnBindUseCase { override suspend fun invoke(...) = Proceed }` in the support package.
- [ ] 7.6.2 Pick Option B — minimal blast radius. Create `app/src/test/java/com/spoolpainter/app/support/MoveOnBindNoOp.kt` containing the test-only no-op.

### 7.7 Modify `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelTest.kt`

- [ ] 7.7.1 Add the two new ctor args (`twoTag` + `confirmer`) to every `MainViewModel(...)` instantiation in this file. Use a `FakeTwoTagUseCase` (default) and a `FakeMoveOnBindConfirmer` for both.
- [ ] 7.7.2 Update assertions that touch `applyWriteResult(WrittenAndPaired)` form-clear behaviour. The old behaviour cleared the form on first-pair success; the new behaviour transitions to `PromptingPairAnother` and defers the form-clear. Existing assertions of "form cleared after WrittenAndPaired" must be relaxed to "activeFlow is PromptingPairAnother". (Check exact assertion locations in the existing test file when executing.)

### 7.8 Modify `app/src/test/java/com/spoolpainter/app/domain/usecases/CreateAndPairUseCaseTest.kt`

- [ ] 7.8.1 Add 2 regression cases:
  - `move_on_bind_declined_returns_cancelled_no_append`
  - `move_on_bind_failed_returns_spoolmanFailed_no_append`
- [ ] 7.8.2 Use `FakeMoveOnBindUseCase` (test-only) returning canned `Outcome` to drive the branches.

### 7.9 Create `app/src/test/java/com/spoolpainter/app/support/FakeMoveOnBindUseCase.kt`

- [ ] 7.9.1 Subclass-friendly test fake:
  ```kotlin
  class FakeMoveOnBindUseCase : MoveOnBindUseCase {
      var nextOutcome: MoveOnBindUseCase.Outcome = MoveOnBindUseCase.Outcome.Proceed
      var invokeCalls: Int = 0; private set
      var lastUid: CardUid? = null; private set
      var lastTargetSpoolId: Int? = null; private set
      override suspend fun invoke(uid: CardUid, targetSpoolId: Int): MoveOnBindUseCase.Outcome {
          invokeCalls++
          lastUid = uid; lastTargetSpoolId = targetSpoolId
          return nextOutcome
      }
  }
  ```

---

## §8 — Documentation

### 8.1 Create `aidlc-docs/construction/u6b-move-on-bind-two-tag/code/u6b-summary.md`

- [ ] 8.1.1 Markdown summary of files created / modified, test counts (pre/post), notable risks (single-thread Confirmer, no rollback semantics).

---

## §9 — Verification commands (post-Code-Gen)

- [ ] 9.1 `./gradlew compileDebugKotlin`
- [ ] 9.2 `./gradlew testDebugUnitTest` — expected ~268..272 / ~268..272.
- [ ] 9.3 `./gradlew assembleDebug` — APK monitored; flagged for U10 if size increases >0.5 MB beyond U6a's 34 MB.
- [ ] 9.4 **U6 milestone install gate** — `./gradlew installDebug` to moto g stylus 2025 / Android 16. Manual ACs (S-5.1 / S-5.2 / S-6.1..6.4):
  - First create-and-pair → `PairAnotherTagSheet` shown.
  - "Pair another" → second tap writes; both UIDs visible in Spoolman web UI under same spool.
  - Tap a UID owned by spool A while spool B is selected → `RepairConfirmSheet` shown; confirm → tag write; A→B move; A retains other UIDs.
  - Cancel `RepairConfirmSheet` → no PATCH; snackbar surfaces "UID still on spool…".
  - Re-pair same UID into same spool → no error, no duplicate PATCH (idempotent).
  - Vendor tag presented during second-tag flow → "Vendor tag — write blocked"; no append.

---

## §10 — Brownfield invariants (post-generation checks)

- [ ] 10.1 No `*_modified.kt`, `*_new.kt`, or `*.kt.bak` files in `app/src/`.
- [ ] 10.2 `MoveOnBindUseCase.NoOp` removed from production source (still allowed in `app/src/test/` as `MoveOnBindNoOp`).
- [ ] 10.3 No unstaged IDE noise in `.idea/` or `aidlc-docs/inception/.idea/` (already gitignored).
- [ ] 10.4 No production callers of `MoveOnBindUseCase.NoOp` after the Hilt binding change (grep returns 0 in `app/src/main`).

---

## §11 — Close-out commit (DoD #6)

- [ ] 11.1 After §9 verification passes, bundle a single close-out commit per `unit-of-work.md` §2.1 + `[[feedback-aidlc-unit-close-out-commit]]`:
  - U6b code (use cases + Confirmer + ViewModels + sheets + Hilt binding).
  - U6b tests (5 new test files + modifications to 2 existing).
  - AIDLC artefacts: `aidlc-docs/construction/u6b-move-on-bind-two-tag/{functional-design/*.md, code/u6b-summary.md}` + this plan + the FD plan.
  - `aidlc-docs/aidlc-state.md` + `aidlc-docs/audit.md` updates marking U6b DONE.
  - Commit message via HEREDOC; multi-line body listing scope per §2.1 template; no `--amend`, no `--no-verify`.
- [ ] 11.2 **Do NOT push** to `origin/v2` — push remains a user-owned action.

---

## §12 — Story traceability

| Story | Implemented by |
|---|---|
| S-5.1 (detect UID already paired) | §3.1 `MoveOnBindUseCaseImpl.invoke` step 1 (`findSpoolsByCardUid`) |
| S-5.2 (confirm + atomic move) | §3.1 `performMove` + §3.2 `MoveOnBindConfirmerImpl` + §5.1 `RepairConfirmSheet` |
| S-6.1 (offer "Pair another tag") | §4.1.2 `applyWriteResult` transition + §5.2 `PairAnotherTagSheet` |
| S-6.2 (identical NDEF + append second UID) | §3.3 `TwoTagUseCase.invoke` + §3.4 `derivePayload` + vendor-tag rejection |
| S-6.3 (move-on-bind on second UID) | §3.3 step 5 (`moveOnBind.invoke(uid, spoolId)` on second tap) |
| S-6.4 (re-derived payload, non-persistent) | §3.4 `derivePayload` + BR-U6b-X-1 (no DataStore writes) |

---

## §13 — U6b-Δ-3: NDEF MIME-type write fix

**Source**: `aidlc-docs/inception/requirements/requirements-delta-tag-mime-and-matcher-bugs.md` §2.

**Problem**: v2 writes `application/vnd.openspool+json`; Snapmaker U1 firmware filters by MIME and accepts only `application/json` per [the published spec](https://snapmakeru1-extended-firmware.pages.dev/rfid_support). v1 (`main:app/src/main/java/com/spoolpainter/app/hardware/nfc/NfcManager.kt`) wrote `application/json`. Tags written by v2 are invisible to the printer.

### §13.1 — Code change (single file)

- [ ] 13.1.1 In `app/src/main/java/com/spoolpainter/app/hardware/nfc/NfcRepository.kt`:
  - `encodePayloadRecords` (lines 263-272): replace `type = MIME_OPENSPOOL.toByteArray(Charsets.US_ASCII)` with `type = MIME_JSON.toByteArray(Charsets.US_ASCII)`.
  - **Do NOT** remove `MIME_OPENSPOOL`. Keep the constant — it stays referenced by the read classifier (lines 243-248) so any tag written during the U4..U6a window still classifies as OpenSpool on read.
- [ ] 13.1.2 No other source files touched. `OpenSpoolPayloadCodec.toJson` is unchanged. JSON wire shape unchanged.

### §13.2 — Tests

- [ ] 13.2.1 Locate the existing test that asserts the written record's MIME type — most likely `app/src/test/java/com/spoolpainter/app/hardware/nfc/NfcRepositoryWriteVerifyTest.kt` (or whichever test in U4's suite captured `encodePayloadRecords` output). Update the expected MIME from `application/vnd.openspool+json` to `application/json`.
- [ ] 13.2.2 New test (preferred location: `NfcRepositoryClassifierTest.kt`): a record with `type = "application/vnd.openspool+json"` MUST still classify as OpenSpool on read — covers tags written by intermediate v2 builds before this fix.
- [ ] 13.2.3 New test (same file): a record with `type = "application/json"` carrying a valid OpenSpool JSON payload classifies as OpenSpool.

### §13.3 — Verification

- [ ] 13.3.1 `./gradlew :app:testDebugUnitTest` passes including the updated + new MIME tests.
- [ ] 13.3.2 **U6 install gate addition**: write a fresh tag from the v2.0 build → load the spool into a Snapmaker U1 printer → printer's spool-info screen reads correct material / colour / temps from the tag. **This step is mandatory before U6b can be marked DONE.** Failure means revert and re-investigate.
- [ ] 13.3.3 Optional but recommended: read a tag written by a U4..U6a build (one with `application/vnd.openspool+json`) inside the new build → form prefills correctly (read-side dual accept verified).

---

## §14 — U6b-Δ-4: Filament matcher strictness fix

**Source**: `aidlc-docs/inception/requirements/requirements-delta-tag-mime-and-matcher-bugs.md` §3.

**Problem**: `SpoolmanRepository.resolveOrCreateFilament` uses strict equality on `color_hex` (case + `#` prefix sensitive) and on `extra.variant` (treats `null` and `""` as different). Form-side canonicalisation is correct (`FormMapping.canonicaliseColorHex` lines 109-113) but the server-side matcher is not symmetric. Each retry on the same filament can spawn a duplicate row.

### §14.1 — Shared canonicalisation helper

- [ ] 14.1.1 Promote `FormMapping.canonicaliseColorHex` to a shared codec. Create `app/src/main/java/com/spoolpainter/app/domain/primitives/ColorHexCodec.kt`:
  ```kotlin
  package com.spoolpainter.app.domain.primitives

  object ColorHexCodec {
      fun canonicalise(raw: String?): String? =
          raw?.removePrefix("#")
              ?.let { if (it.length > 6) it.takeLast(6) else it }
              ?.uppercase()
              ?.takeIf { it.isNotEmpty() }
  }
  ```
- [ ] 14.1.2 Update `FormMapping.canonicaliseColorHex` to delegate to `ColorHexCodec.canonicalise`. Keep the `internal` API surface so existing call-sites (`FormMapping.kt:32`, `:90`) and tests are unaffected. (Or replace inline — implementer choice; both compile.)
- [ ] 14.1.3 No other consumers of `FormMapping.canonicaliseColorHex` to update — the helper was already centralised.

### §14.2 — Variant canonicalisation

- [ ] 14.2.1 In `SpoolmanRepository.kt`, add a private helper:
  ```kotlin
  private fun canonVariant(raw: String?): String? =
      raw?.trim()?.takeIf { it.isNotBlank() }
  ```
  Place it near `decodeJsonString` (already at the top of the file).

### §14.3 — Apply normalisation in matcher

- [ ] 14.3.1 In `SpoolmanRepository.resolveOrCreateFilament` (around lines 310-319), rewrite the match predicate:
  ```kotlin
  val targetHex = ColorHexCodec.canonicalise(req.colorHex)
  val targetVariant = canonVariant(variantNormalised)
  val match = list.firstOrNull { f ->
      if (f.vendor?.id != vendorId) return@firstOrNull false
      if (!(f.material ?: "").equals(materialName, ignoreCase = true)) return@firstOrNull false
      if (ColorHexCodec.canonicalise(f.color_hex) != targetHex) return@firstOrNull false
      val existingVariant = canonVariant(decodeJsonString(f.extra?.get("variant")))
      existingVariant.equals(targetVariant, ignoreCase = false /* preserve trim-only path; case kept */) ||
          existingVariant?.equals(targetVariant, ignoreCase = true) == true
  }
  ```
  *Note on case-sensitivity*: §3.3 of the requirements delta specifies case-**insensitive** equality on variant. Final form:
  ```kotlin
  val existingVariant = canonVariant(decodeJsonString(f.extra?.get("variant")))
  if (targetVariant == null && existingVariant == null) {
      // both null/blank — match
  } else if (targetVariant == null || existingVariant == null) {
      return@firstOrNull false
  } else if (!existingVariant.equals(targetVariant, ignoreCase = true)) {
      return@firstOrNull false
  }
  ```
  Pick whichever expression form is clearest in context; behaviour MUST be: trim+null-coalesce both sides, then case-insensitive equality.
- [ ] 14.3.2 The existing `Log.d` debug line (`SpoolmanRepository.kt:321-324`) MAY remain; consider including the canonicalised values to ease future debugging.

### §14.4 — Tests

- [ ] 14.4.1 New test file `app/src/test/java/com/spoolpainter/app/data/remote/spoolman/ResolveOrCreateFilamentTest.kt` (or extend an existing `SpoolmanRepositoryTest`). Cover the six cases enumerated in `requirements-delta-tag-mime-and-matcher-bugs.md` §3.5:
  - `color_hex = "ff0000"` vs req `"FF0000"` → match.
  - `color_hex = "#FF0000"` vs req `"FF0000"` → match.
  - `extra.variant = null`, req variant = null → match.
  - `extra.variant = ""`, req variant = null → match.
  - `extra.variant = "matte"`, req variant = `"Matte"` → match.
  - Different colour or different variant → no match (fresh filament created).

### §14.5 — Verification

- [ ] 14.5.1 `./gradlew :app:testDebugUnitTest` passes including the six new matcher cases.
- [ ] 14.5.2 **U6 install gate addition**: with one pre-existing filament + variant in Spoolman, tap Save & Write twice in a row with the form populated identically → Spoolman web UI shows **one** filament row with **two** spools under it (not two filament rows × one spool each). **Mandatory before U6b DONE.**

### §14.6 — Test count target update

§7's prior target was 244 → ~268..272 at close-out.
With §13 (~+2) and §14 (~+6), revised target: **244 → ~275..280** at close-out.

---

## §15 — Brownfield invariants for §13 + §14 (post-generation checks)

- [ ] 15.1 `grep -rn "application/vnd.openspool+json" app/src/main/` finds 0 references on the **write** path. Read-side classifier reference is allowed.
- [ ] 15.2 No call-site of `(f.color_hex ?: "")` raw equality remains in `SpoolmanRepository.kt`.
- [ ] 15.3 `ColorHexCodec.canonicalise` is the only color-hex canonicalisation path used by both `FormMapping` and `SpoolmanRepository.resolveOrCreateFilament` — no copy-pasted variant of the chain.

---

## §16 — Story traceability for §13 + §14

| Source | Implemented by |
|---|---|
| `requirements-delta-tag-mime-and-matcher-bugs.md` §2 (Bug #1) | §13.1, §13.2, §13.3 |
| `requirements-delta-tag-mime-and-matcher-bugs.md` §3 (Bug #2) | §14.1, §14.2, §14.3, §14.4, §14.5 |
