# U7 — Code Generation Summary

**Stage**: CONSTRUCTION → Per-Unit Loop → Code Generation Part 2 (U7)
**Unit**: U7 — Side Modes (Raw-Write + Vendor UID-only Pair)
**Generated**: 2026-05-27
**Test result**: **300 / 300** ✅
**Build**: `:app:compileDebugKotlin` ✅, `:app:assembleDebug` ✅ (34 MB, down from 35.5 MB at U6b — within plan)

---

## Files added

| Path | Lines | Purpose |
|---|---|---|
| `domain/usecases/RawWriteUseCase.kt` | 90 | Spoolman-free NDEF write; ctor injects only `NfcRepository`. |
| `domain/usecases/VendorUidOnlyPairUseCase.kt` | 145 | Spoolman pair without NDEF write; ctor injects only `SpoolmanRepository` + `MoveOnBindUseCase` (no `NfcRepository` — type-level invariant). |
| `ui/screens/main/WriteMode.kt` | 18 | Sealed type `Spoolman` / `RawNoUrl` / `RawDisconnected`. |
| `ui/screens/main/ObservedTagKind.kt` | 10 | Sealed type `None` / `Blank` / `OpenSpool` / `Vendor`. |
| `test/.../domain/usecases/RawWriteUseCaseTest.kt` | 100 | 6 cases — happy, payload-omits-spool_id, vendor-rejected, verify-fail, generic-NFC, zero-len UID. |
| `test/.../domain/usecases/VendorUidOnlyPairUseCaseTest.kt` | 120 | 6 cases — existing-spool happy, declined, append-fail, new-spool happy, POST-fail, move-on-bind partial. |
| `test/.../ui/screens/main/MainViewModelRawWriteTest.kt` | 145 | 7 cases — `WriteMode` derivation, dispatch routing, raw success / vendor-rejected, vendor-no-Spoolman refusal, vendor + Spoolman → vendor flow. |
| `test/.../support/FakeRawWriteUseCase.kt` | 18 | Test fake. |
| `test/.../support/FakeVendorUidOnlyPairUseCase.kt` | 22 | Test fake. |

## Files modified

| Path | Change |
|---|---|
| `domain/usecases/MoveOnBindUseCaseImpl.kt` | Skip the final `appendCardUidToSpool` when `targetSpoolId < 0` (sentinel for vendor new-spool path — caller appends to the real new spool). |
| `ui/screens/main/MainUiState.kt` | Added `observedTagKind`, `writeMode` fields to `MainUiState`; `ActiveFlow.WritingRaw` + `PairingVendorUidOnly` variants. |
| `ui/screens/main/MainViewModel.kt` | Inject `RawWriteUseCase` + `VendorUidOnlyPairUseCase`; new derived collectors for `writeMode` and `observedTagKind`; `onWriteTapped` branches by `(writeMode, observedTagKind)`; new `applyRawWriteResult` + `applyVendorUidOnlyPairResult`; new `launchRawWrite` + `launchVendorUidOnlyPair` helpers. |
| `ui/screens/main/MainScreen.kt` | New `RawWriteBanner` + `VendorTagHint` composables; new `saveButtonLabel(mode, observed)` helper; banner-suppression so connectivity banner hides when raw-write banner is up. |
| `ui/components/FilamentForm.kt` | New `saveButtonLabel: String = "Save & Write"` parameter so MainScreen can switch the label by mode. |
| `test/.../ui/screens/main/MainViewModelTest.kt` | Ctor extended with new use-cases; `primeFormForWrite` sets `Settings(url=...)` so tests stay in `WriteMode.Spoolman`. |
| `test/.../ui/screens/main/MainViewModelTwoTagTest.kt` | Same ctor + `primeFormForWrite` URL-prime fix. |

## Files removed

| Path | Reason |
|---|---|
| `ui/components/sheets/VendorOptInViewModel.kt` | U1 placeholder; reframed away (no opt-in sheet). |

---

## Behaviour summary

### Save & Write dispatch

```
canWrite:false → no-op
canWrite:true:
  Vendor + RawNoUrl       → snackbar "Spoolman needed to save vendor tag — connect and try again"
  Vendor + RawDisconnected → snackbar "Spoolman not reachable — try again when connected"
  Vendor + Spoolman       → VendorUidOnlyPairUseCase (no NDEF write)
  RawNoUrl/RawDisconnected → RawWriteUseCase (no Spoolman calls)
  otherwise                → CreateAndPairUseCase (existing U6a path)
```

### Save-button label by `(writeMode, observedTagKind)`

| WriteMode | Observed | Label |
|---|---|---|
| Spoolman | None / Blank / OpenSpool | "Save & Write" |
| Spoolman | Vendor | "Save" |
| RawNoUrl / RawDisconnected | (any non-vendor) | "Write to NFC" (matches v1.7) |

### Banner copy

| WriteMode | Banner |
|---|---|
| Spoolman | (hidden — connectivity banner takes over if needed) |
| RawNoUrl | "Writing tag only — Spoolman not configured" |
| RawDisconnected | "Writing tag only — not connected to Spoolman" |

### Vendor-tag chip

Renders only when `observedTagKind == Vendor && form.cardUid != null`:
```
[⚠] Vendor tag — content unreadable
Fill in the details below to link this tag.
```

---

## Brownfield invariants (verified)

- ✅ No `*_modified` / `*_new` / `*.bak` files introduced.
- ✅ No `.idea/` or `app/build/` artifacts staged.
- ✅ `FormState.rawWriteMode` field retained (U1 skeleton — still on the data class to avoid downstream churn) but never read by U7 code; can be removed in U10 cleanup.
- ✅ `humanReadable(outcome)` reused — no copy duplication.
- ✅ `MoveOnBindConfirmer` reused — vendor flow inherits the existing sheet routing.
- ✅ `PairAnotherTagSheet` reused — vendor success transitions to `PromptingPairAnother` per Q-U7-9.

## Test coverage

- **Total**: 300 / 300 passing (was 281 at U6b close-out).
- **Δ**: +19 cases (RawWriteUseCaseTest 6 + VendorUidOnlyPairUseCaseTest 6 + MainViewModelRawWriteTest 7).
- **Existing tests unaffected**: U6a/U6b regression cases unchanged; only the URL-prime helper required adjustment to keep the existing tests in `WriteMode.Spoolman` (they were inadvertently entering raw-write mode under the new dispatch).

## Out-of-scope guards (re-checked)

- ❌ No `RawWriteUseCase` Settings persistence (FR-4.9 / S-4.7 AC).
- ❌ No vendor decoding (U11/v2.1).
- ❌ No `OpenSpoolPayload.fromForm` factory refactor (U10 cleanup).
- ❌ No new install gate (U10 covers manual NFC verification per `unit-of-work.md` §U7 exit criteria).

## Manual verification (for U10 install gate)

The U10 install gate will exercise:

1. **Raw-write — blank tag**: `settings.url=""` → tap blank tag → "Write to NFC" → tag written; no Spoolman side-effect.
2. **Raw-write — vendor tag**: `settings.url=""` → tap vendor tag → snackbar "Spoolman needed to save vendor tag…". Form preserved.
3. **Vendor pair — existing spool**: configure Spoolman → tap vendor tag → chip + helper appear → pick existing spool → "Save" → spool's `extra.card_uids` includes the UID; no NDEF write.
4. **Vendor pair — new spool**: configure Spoolman → tap vendor tag → chip + helper appear → fill form (no spool selected) → "Save" → new spool created with `extra.card_uids = [<uid>]`; no NDEF write.
5. **Vendor pair + move-on-bind**: vendor tag UID previously paired to spool A → tap, opt to pair to B → RepairConfirmSheet → confirm → A loses UID, B gains UID, no NDEF write.
6. **Raw mode + verify-fail**: simulate verify mismatch → snackbar "Verify failed. Tap Write to retry."

---

## Next stage gate

`unit-of-work.md` §U7 exit criteria met. Awaiting Code Generation Part 2 stage-gate approval before U7 close-out commit.
