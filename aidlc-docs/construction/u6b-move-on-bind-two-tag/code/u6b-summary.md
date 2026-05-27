# U6b — Code Generation Summary

**Stage**: CONSTRUCTION → Per-Unit Loop → Code Generation Part 2 (U6b) — DONE
**Date**: 2026-05-26
**Plan**: `aidlc-docs/construction/plans/u6b-move-on-bind-two-tag-code-generation-plan.md`
**Test count**: U6a baseline **244 / 244** → U6b close-out **279 / 279** (+35 net).

## Scope landed

- **Move-on-bind use-case** — full implementation (`MoveOnBindUseCaseImpl` + `MoveOnBindConfirmer` + `MoveOnBindConfirmerImpl`). Replaces U6a's interface seam (`MoveOnBindUseCase.NoOp`, deleted from production source). On every Save & Write, the use-case asks Spoolman whether the tapped UID is already paired to a different spool; the user is prompted via `RepairConfirmSheet`; on accept, the UID atomically moves (remove-from-other → append-to-target).
- **Two-tag flow** — `TwoTagUseCase` + `TwoTagInput` / `TwoTagResult` family, plus `MainViewModel.onPairAnotherTagAccepted` / `onPairAnotherTagDismissed` / `applyTwoTagResult`. After the first-tag write, `ActiveFlow.PromptingPairAnother` surfaces the bottom-sheet asking "Pair another tag?". Accept → re-derive payload from Spoolman cache (or `getSpool` + `getFilament` fallback) → arm Write → write second tag → move-on-bind precheck → append second UID → terminal `Both tags paired` snackbar.
- **CreateAndPairUseCase reorder** — move-on-bind now runs BEFORE the append (BR-U6b-CP-1). `Declined` → `Cancelled`; `Failed` → `SpoolmanFailed(ParseError(reason))`; `AmbiguousOwnership` → `SpoolmanFailed(ParseError("ambiguous ownership: ..."))`.
- **NDEF MIME write fix (U6b-Δ-3)** — `NfcRepository.encodePayloadRecords` now writes `application/json` instead of `application/vnd.openspool+json`. Snapmaker U1 firmware filters by MIME and only accepts `application/json`. Read-side classifier dual-accepts both MIMEs so any tag written during the U4..U6a window still round-trips.
- **Filament matcher canonicalisation (U6b-Δ-4)** — new `domain/primitives/ColorHexCodec.kt` is the single source of truth for colour-hex normalisation (used by both `FormMapping` and `SpoolmanRepository.resolveOrCreateFilament`). New `canonVariant` helper trims and treats null/blank as equivalent. Variant equality is now case-insensitive. Each retry on the same form now hits the existing filament row instead of spawning a duplicate.
- **Hilt graph** — `RepositoryModule.RepositoryBindingsModule` rebinds `MoveOnBindUseCase` to `MoveOnBindUseCaseImpl`; new `@Binds @Singleton` for `MoveOnBindConfirmer` → `MoveOnBindConfirmerImpl`.

## Files created

- `app/src/main/java/com/spoolpainter/app/domain/usecases/MoveOnBindConfirmer.kt`
- `app/src/main/java/com/spoolpainter/app/domain/usecases/MoveOnBindConfirmerImpl.kt`
- `app/src/main/java/com/spoolpainter/app/domain/usecases/MoveOnBindUseCaseImpl.kt`
- `app/src/main/java/com/spoolpainter/app/domain/usecases/TwoTagUseCase.kt`
- `app/src/main/java/com/spoolpainter/app/domain/primitives/ColorHexCodec.kt`
- `app/src/main/java/com/spoolpainter/app/ui/components/sheets/PairAnotherTagUiState.kt`
- `app/src/main/java/com/spoolpainter/app/ui/components/sheets/RepairConfirmSheet.kt`
- `app/src/main/java/com/spoolpainter/app/ui/components/sheets/PairAnotherTagSheet.kt`
- `app/src/main/java/com/spoolpainter/app/ui/components/sheets/BottomSheetHost.kt`
- `app/src/test/java/com/spoolpainter/app/support/MoveOnBindNoOp.kt`
- `app/src/test/java/com/spoolpainter/app/support/FakeMoveOnBindConfirmer.kt`
- `app/src/test/java/com/spoolpainter/app/support/FakeMoveOnBindUseCase.kt`
- `app/src/test/java/com/spoolpainter/app/support/FakeTwoTagUseCase.kt`
- `app/src/test/java/com/spoolpainter/app/domain/usecases/MoveOnBindUseCaseTest.kt`
- `app/src/test/java/com/spoolpainter/app/domain/usecases/TwoTagUseCaseTest.kt`
- `app/src/test/java/com/spoolpainter/app/ui/components/sheets/RepairConfirmViewModelTest.kt`
- `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelTwoTagTest.kt`
- `app/src/test/java/com/spoolpainter/app/data/remote/spoolman/ResolveOrCreateFilamentTest.kt`

## Files modified

- `app/src/main/java/com/spoolpainter/app/domain/usecases/MoveOnBindUseCase.kt` — Outcome shape rewritten (Proceed / Moved / Declined / Failed / AmbiguousOwnership); `NoOp` deleted.
- `app/src/main/java/com/spoolpainter/app/domain/usecases/CreateAndPairUseCase.kt` — move-on-bind precheck reordered to run before the append.
- `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanApi.kt` — added `getFilament(id)` endpoint.
- `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanRepository.kt` — added `getFilament` helper; matcher rewrite using `ColorHexCodec` + `canonVariant`.
- `app/src/main/java/com/spoolpainter/app/hardware/nfc/NfcRepository.kt` — write MIME flipped to `application/json`.
- `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainUiState.kt` — `ActiveFlow` extended with three new variants.
- `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainViewModel.kt` — `twoTag` + `confirmer` injected; new handlers; `applyWriteResult(WrittenAndPaired)` transitions to `PromptingPairAnother`; `init` collector for `confirmer.pendingRequest`.
- `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainScreen.kt` — `BottomSheetHost` wired in; `WritingHint` extended to cover `WritingSecondTag`.
- `app/src/main/java/com/spoolpainter/app/ui/screens/main/FormMapping.kt` — `canonicaliseColorHex` delegates to `ColorHexCodec`.
- `app/src/main/java/com/spoolpainter/app/ui/components/sheets/RepairConfirmViewModel.kt` — placeholder replaced with real `uiState` derivation.
- `app/src/main/java/com/spoolpainter/app/di/RepositoryModule.kt` — Hilt bindings updated.
- `app/src/test/java/com/spoolpainter/app/support/FakeCreateAndPairUseCase.kt` — uses `MoveOnBindNoOp` instead of removed `MoveOnBindUseCase.NoOp`.
- `app/src/test/java/com/spoolpainter/app/support/FakeSpoolmanRepository.kt` — added `setFilaments`, `setVendors`, `getFilament` override.
- `app/src/test/java/com/spoolpainter/app/data/remote/spoolman/FakeSpoolmanApi.kt` — added `getFilament` route.
- `app/src/test/java/com/spoolpainter/app/hardware/nfc/FakeNfcAdapterWrapper.kt` — `lastWrittenRecords` exposed for MIME assertion.
- `app/src/test/java/com/spoolpainter/app/hardware/nfc/NfcRepositoryWriteVerifyTest.kt` — added MIME flip assertion.
- `app/src/test/java/com/spoolpainter/app/hardware/nfc/NfcRepositoryStandaloneVerifyTest.kt` — happy-path readback uses `jsonMimeRecords`.
- `app/src/test/java/com/spoolpainter/app/domain/usecases/CreateAndPairUseCaseTest.kt` — 2 regression cases for move-on-bind branches; uses `MoveOnBindNoOp`.
- `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelTest.kt` — ctor args extended; `WrittenAndPaired` assertions relaxed to accept `PromptingPairAnother`.

## Verification

- `./gradlew :app:compileDebugKotlin` ✅
- `./gradlew :app:testDebugUnitTest` ✅ **279 / 279**
- `./gradlew :app:assembleDebug` ✅ — 35.5 MB APK (+1.5 MB vs U6a's 34 MB; flagged for U10 polish)
- **U6 milestone install gate** — pending. Manual verification required on moto g stylus 2025 / Android 16:
  - First create-and-pair → `PairAnotherTagSheet` shown.
  - "Pair another" → second tap writes; both UIDs visible in Spoolman web UI under same spool.
  - Tap a UID owned by spool A while spool B is selected → `RepairConfirmSheet` shown; confirm → tag write; A→B move; A retains other UIDs.
  - Cancel `RepairConfirmSheet` → no PATCH; snackbar surfaces "UID still on the originally-paired spool".
  - Re-pair same UID into same spool → no error, no duplicate PATCH (idempotent).
  - Vendor tag presented during second-tag flow → "Vendor tag — write blocked"; no append.
  - **Snapmaker U1 printer**: tag written by v2.0 build round-trips through printer's spool-info screen.
  - Tap Save & Write twice on identical form → 1 filament + 2 spools in Spoolman (matcher canonicalisation).

## Notable risks / known limitations

- **Single-thread Confirmer** — `MoveOnBindConfirmerImpl.confirm` enforces one-pending-request via `check(...)`. Concurrent flows would throw. Acceptable for v2.0 single-foreground-flow UX; revisit if background flows ever need confirmation.
- **No rollback** — when remove-then-append fails after remove succeeds, the source spool has already lost the UID. We surface `MoveOnBindPartial` (with the orphaned spool id) so the user can correct manually in Spoolman web UI. A formal rollback would need transaction support that Spoolman doesn't expose.
- **No DataStore writes for two-tag in-flight state** (FR-6.4 deferred) — process death between tag 1 and tag 2 returns the user to a clean form; per Q-U6b-13, acceptable for v2.0.
- **APK size +1.5 MB** vs the +0.5 MB target in plan §10.3 — flagged for U10 polish.
- **JDK 17 required** — durable fix deferred to U10.

## Story traceability

| Story | Implemented by |
|---|---|
| S-5.1 (detect UID already paired) | `MoveOnBindUseCaseImpl.invoke` step 1 (`findSpoolsByCardUid`) |
| S-5.2 (confirm + atomic move) | `MoveOnBindUseCaseImpl.performMove` + `MoveOnBindConfirmerImpl` + `RepairConfirmSheet` |
| S-6.1 (offer "Pair another tag") | `MainViewModel.applyWriteResult` transition + `PairAnotherTagSheet` |
| S-6.2 (identical NDEF + append second UID) | `TwoTagUseCase.invoke` + `derivePayload` + vendor-tag rejection |
| S-6.3 (move-on-bind on second UID) | `TwoTagUseCase.invoke` step 5 (`moveOnBind.invoke(uid, spoolId)`) |
| S-6.4 (re-derived payload, non-persistent) | `TwoTagUseCase.derivePayload` (no DataStore writes) |
| FR-U6b-Δ-3 (Snapmaker U1 MIME) | `NfcRepository.encodePayloadRecords` |
| FR-U6b-Δ-4 (matcher canonicalisation) | `ColorHexCodec` + `canonVariant` + `resolveOrCreateFilament` rewrite |
